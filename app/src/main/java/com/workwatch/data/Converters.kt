package com.workwatch.data

import androidx.room.TypeConverter
import java.util.Base64

class Converters {
    @TypeConverter
    fun fromByteArray(value: ByteArray?): String? {
        return value?.let { Base64.getEncoder().encodeToString(it) }
    }

    @TypeConverter
    fun toByteArray(value: String?): ByteArray? {
        return value?.let { Base64.getDecoder().decode(it) }
    }
}
