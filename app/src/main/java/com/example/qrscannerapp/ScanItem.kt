package com.example.qrscannerapp

import android.graphics.Bitmap
import java.util.UUID

/**
 * Модель данных для одного отсканированного элемента.
 * @param id Уникальный идентификатор для стабильной работы со списком (например, для удаления).
 * @param code Отсканированный код (значение).
 * @param scannedAt Временная метка сканирования (Unix time in milliseconds).
 * @param thumbnail Миниатюра кадра в виде массива байтов. Может быть null, если код добавлен вручную.
 * @param isNew Флаг для анимации подсветки только что добавленного элемента.
 */
data class ScanItem(
    val id: String = UUID.randomUUID().toString(),
    val code: String,
    val scannedAt: Long = System.currentTimeMillis(),
    val thumbnail: ByteArray? = null,
    var isNew: Boolean = false
) {
    // Добавляем функции equals и hashCode, чтобы ByteArray корректно сравнивались
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as ScanItem

        if (id != other.id) return false
        if (code != other.code) return false
        if (scannedAt != other.scannedAt) return false
        if (thumbnail != null) {
            if (other.thumbnail == null) return false
            if (!thumbnail.contentEquals(other.thumbnail)) return false
        } else if (other.thumbnail != null) return false
        if (isNew != other.isNew) return false

        return true
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + code.hashCode()
        result = 31 * result + scannedAt.hashCode()
        result = 31 * result + (thumbnail?.contentHashCode() ?: 0)
        result = 31 * result + isNew.hashCode()
        return result
    }
}

/**
 * Определяет тип элементов в сессии.
 */
enum class SessionType {
    SCOOTERS,
    BATTERIES
}

/**
 * Модель данных для одной сессии сканирования, которая будет сохраняться в историю.
 * @param id Уникальный идентификатор сессии.
 * @param name Опциональное имя/комментарий сессии, заданное пользователем.
 * @param timestamp Временная метка сохранения сессии.
 * @param type Тип сессии (самокаты или АКБ).
 * @param items Список отсканированных элементов в этой сессии.
 */
data class ScanSession(
    val id: String = UUID.randomUUID().toString(),
    // --- НОВОЕ ПОЛЕ: Добавляем опциональное имя для сессии ---
    val name: String? = null,
    val timestamp: Long = System.currentTimeMillis(),
    val type: SessionType,
    val items: List<ScanItem>
)