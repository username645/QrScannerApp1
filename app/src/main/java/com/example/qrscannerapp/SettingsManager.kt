package com.example.qrscannerapp

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

// Создаем экземпляр DataStore на уровне всего приложения.
// 'name = "settings"' - это имя файла, в котором будут храниться данные.
private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class SettingsManager(private val context: Context) {

    // companion object используется для хранения констант, в нашем случае - ключей для настроек.
    // Это гарантирует, что мы всегда будем использовать одну и ту же строку для доступа к настройке.
    companion object {
        val IS_SOUND_ENABLED = booleanPreferencesKey("is_sound_enabled")
        val IS_VIBRATION_ENABLED = booleanPreferencesKey("is_vibration_enabled")
    }

    // --- ЧТЕНИЕ НАСТРОЕК ---

    // Создаем Flow (поток данных), который будет автоматически сообщать об изменении настройки звука.
    // Если настройка еще не была сохранена, мы возвращаем значение по умолчанию 'true'.
    val isSoundEnabledFlow: Flow<Boolean> = context.dataStore.data
        .catch { exception ->
            // При ошибках чтения (например, если файл поврежден) выдаем пустые настройки,
            // чтобы приложение не упало.
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            preferences[IS_SOUND_ENABLED] ?: true // Значение по умолчанию: звук включен
        }

    // Аналогичный поток для настройки вибрации.
    val isVibrationEnabledFlow: Flow<Boolean> = context.dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            preferences[IS_VIBRATION_ENABLED] ?: true // Значение по умолчанию: вибрация включена
        }


    // --- ЗАПИСЬ НАСТРОЕК ---

    // Функция для изменения состояния настройки звука.
    // 'suspend' означает, что эта функция асинхронная и не будет блокировать главный поток.
    suspend fun setSoundEnabled(isEnabled: Boolean) {
        context.dataStore.edit { settings ->
            settings[IS_SOUND_ENABLED] = isEnabled
        }
    }

    // Функция для изменения состояния настройки вибрации.
    suspend fun setVibrationEnabled(isEnabled: Boolean) {
        context.dataStore.edit { settings ->
            settings[IS_VIBRATION_ENABLED] = isEnabled
        }
    }
}