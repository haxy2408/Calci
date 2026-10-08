package com.example.database

import androidx.room.TypeConverter

class Converters {
    @TypeConverter
    fun fromMediaType(value: MediaType): String {
        return value.name
    }

    @TypeConverter
    fun toMediaType(value: String): MediaType {
        return try {
            MediaType.valueOf(value)
        } catch (e: Exception) {
            MediaType.PHOTO
        }
    }
}
