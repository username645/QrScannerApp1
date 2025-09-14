package com.example.qrscannerapp

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.compose.runtime.mutableStateListOf
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "scanned_codes_store")

enum class ActiveTab {
    SCOOTERS,
    BATTERIES
}

class QrScannerViewModel(application: Application) : AndroidViewModel(application) {
    private val SESSIONS_KEY = stringPreferencesKey("scan_sessions_list_json")
    private val gson = Gson()

    // Активная, несохраненная сессия
    private val _scooterCodes = mutableStateListOf<ScanItem>()
    val scooterCodes: List<ScanItem> = _scooterCodes

    private val _batteryCodes = mutableStateListOf<ScanItem>()
    val batteryCodes: List<ScanItem> = _batteryCodes

    // Сохраненные сессии
    private val _scanSessions = mutableStateListOf<ScanSession>()
    val scanSessions: List<ScanSession> = _scanSessions

    private val _activeTab = MutableStateFlow(ActiveTab.SCOOTERS)
    val activeTab = _activeTab.asStateFlow()

    private val _statusMessage = MutableStateFlow("Наведите камеру на QR-код")
    val statusMessage = _statusMessage.asStateFlow()

    private val _scanEffectChannel = Channel<Unit>()
    val scanEffect = _scanEffectChannel.receiveAsFlow()

    init {
        viewModelScope.launch {
            loadSessions()
        }
    }

    fun onCodeScanned(rawCode: String, thumbnail: ByteArray?) {
        var extractedScooterCode: String? = null
        if (rawCode.contains("number=")) {
            try { extractedScooterCode = rawCode.substringAfter("number=").split('&', '?', '#').firstOrNull() } catch (e: Exception) { Log.e("VM_SCAN", "Parse error", e) }
        }
        if (extractedScooterCode == null && rawCode.contains('/')) {
            try { val segment = rawCode.split('/').lastOrNull { it.isNotEmpty() }; if (segment?.all { it.isDigit() } == true) { extractedScooterCode = segment } } catch (e: Exception) { Log.e("VM_SCAN", "Parse error", e) }
        }
        if (extractedScooterCode == null && rawCode.all { it.isDigit() }) { extractedScooterCode = rawCode }

        if (extractedScooterCode != null) {
            if (_scooterCodes.any { it.code == extractedScooterCode }) {
                updateStatus("Самокат $extractedScooterCode уже в списке.", isError = true)
            } else {
                val newItem = ScanItem(code = extractedScooterCode, thumbnail = thumbnail, isNew = true)
                _scooterCodes.add(0, newItem)
                updateStatus("Самокат $extractedScooterCode добавлен!")
                viewModelScope.launch { _scanEffectChannel.send(Unit) }
            }
        } else {
            val batteryCode = rawCode
            if (_batteryCodes.any { it.code == batteryCode }) {
                updateStatus("АКБ $batteryCode уже в списке.", isError = true)
            } else {
                val newItem = ScanItem(code = batteryCode, thumbnail = thumbnail, isNew = true)
                _batteryCodes.add(0, newItem)
                updateStatus("АКБ $batteryCode добавлен!")
                if (_activeTab.value == ActiveTab.SCOOTERS) {
                    _activeTab.value = ActiveTab.BATTERIES
                }
                viewModelScope.launch { _scanEffectChannel.send(Unit) }
            }
        }
    }

    fun addManualCode(code: String) {
        when (_activeTab.value) {
            ActiveTab.SCOOTERS -> {
                if (code.isBlank() || !code.all { it.isDigit() }) {
                    updateStatus("Ошибка: Код самоката должен состоять только из цифр.", isError = true)
                    return
                }
                if (_scooterCodes.any { it.code == code }) {
                    updateStatus("Самокат $code уже в списке.", isError = true)
                    return
                }
                val newItem = ScanItem(code = code, thumbnail = null, isNew = true)
                _scooterCodes.add(0, newItem)
                updateStatus("Самокат $code успешно добавлен!")
            }
            ActiveTab.BATTERIES -> {
                if (code.isBlank()) {
                    updateStatus("Ошибка: Код АКБ не может быть пустым.", isError = true)
                    return
                }
                if (_batteryCodes.any { it.code == code }) {
                    updateStatus("АКБ $code уже в списке.", isError = true)
                    return
                }
                val newItem = ScanItem(code = code, thumbnail = null, isNew = true)
                _batteryCodes.add(0, newItem)
                updateStatus("АКБ $code успешно добавлен!")
            }
        }
    }

    fun removeCode(item: ScanItem) {
        _scooterCodes.remove(item)
        _batteryCodes.remove(item)
    }

    fun clearList() {
        when (_activeTab.value) {
            ActiveTab.SCOOTERS -> {
                _scooterCodes.clear()
                updateStatus("Список самокатов очищен")
            }
            ActiveTab.BATTERIES -> {
                _batteryCodes.clear()
                updateStatus("Список АКБ очищен")
            }
        }
    }

    // --- ИЗМЕНЕНИЕ: Функция теперь принимает опциональное имя сессии ---
    fun saveCurrentSession(name: String?) {
        viewModelScope.launch {
            val (listToSave, sessionType) = when (_activeTab.value) {
                ActiveTab.SCOOTERS -> _scooterCodes.toList() to SessionType.SCOOTERS
                ActiveTab.BATTERIES -> _batteryCodes.toList() to SessionType.BATTERIES
            }

            if (listToSave.isEmpty()) {
                updateStatus("Нечего сохранять, список пуст.", isError = true)
                return@launch
            }

            // --- ИЗМЕНЕНИЕ: Передаем имя при создании сессии. Если имя пустое, оно будет null. ---
            val sessionName = name?.takeIf { it.isNotBlank() }
            val newSession = ScanSession(name = sessionName, type = sessionType, items = listToSave)
            _scanSessions.add(0, newSession)
            saveSessions()
            clearList()
            updateStatus("Сессия сохранена в историю!")
        }
    }

    fun deleteSession(sessionId: String) {
        viewModelScope.launch {
            _scanSessions.removeAll { it.id == sessionId }
            saveSessions()
        }
    }

    fun deleteItemFromSession(sessionId: String, itemToDelete: ScanItem) {
        viewModelScope.launch {
            val sessionIndex = _scanSessions.indexOfFirst { it.id == sessionId }
            if (sessionIndex != -1) {
                val oldSession = _scanSessions[sessionIndex]
                val updatedItems = oldSession.items.filter { it.id != itemToDelete.id }

                if (updatedItems.isEmpty()) {
                    deleteSession(sessionId)
                } else {
                    val updatedSession = oldSession.copy(items = updatedItems)
                    _scanSessions[sessionIndex] = updatedSession
                    saveSessions()
                }
            }
        }
    }

    fun onTabSelected(tab: ActiveTab) {
        _activeTab.value = tab
    }

    fun markAsOld(item: ScanItem) {
        val scooterIndex = _scooterCodes.indexOf(item)
        if (scooterIndex != -1) {
            _scooterCodes[scooterIndex] = item.copy(isNew = false)
            return
        }

        val batteryIndex = _batteryCodes.indexOf(item)
        if (batteryIndex != -1) {
            _batteryCodes[batteryIndex] = item.copy(isNew = false)
        }
    }

    fun updateStatus(message: String, isError: Boolean = false) {
        viewModelScope.launch {
            _statusMessage.value = message
            delay(2000)
            _statusMessage.value = "Наведите камеру на QR-код"
        }
    }

    private suspend fun saveSessions() {
        val jsonString = gson.toJson(_scanSessions.toList())
        getApplication<Application>().dataStore.edit { preferences ->
            preferences[SESSIONS_KEY] = jsonString
        }
    }

    private suspend fun loadSessions() {
        val preferences = getApplication<Application>().dataStore.data.first()
        val type = object : TypeToken<List<ScanSession>>() {}.type
        val json = preferences[SESSIONS_KEY]
        if (!json.isNullOrBlank()) {
            try {
                val sessions = gson.fromJson<List<ScanSession>>(json, type)
                _scanSessions.clear()
                _scanSessions.addAll(sessions)
            } catch (e: Exception) {
                Log.e("QrScannerViewModel", "Failed to load/parse sessions", e)
            }
        }
    }
}