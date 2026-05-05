package com.example.pm.utils

import com.google.firebase.Timestamp
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

object TimeUtils {
    fun formatTimestamp(timestamp: Timestamp): String {
        val now = System.currentTimeMillis()
        val time = timestamp.toDate().time
        val diff = now - time

        return when {
            diff < TimeUnit.MINUTES.toMillis(1) -> {
                "Hace un momento"
            }
            diff < TimeUnit.HOURS.toMillis(1) -> {
                val minutes = TimeUnit.MILLISECONDS.toMinutes(diff)
                "Hace $minutes ${if (minutes == 1L) "minuto" else "minutos"}"
            }
            diff < TimeUnit.DAYS.toMillis(1) -> {
                val hours = TimeUnit.MILLISECONDS.toHours(diff)
                "Hace $hours ${if (hours == 1L) "hora" else "horas"}"
            }
            else -> {
                val sdf = SimpleDateFormat("d 'de' MMMM", Locale("es", "ES"))
                sdf.format(timestamp.toDate())
            }
        }
    }

    fun formatTimestampShort(timestamp: Timestamp): String {
        val now = System.currentTimeMillis()
        val time = timestamp.toDate().time
        val diff = now - time

        return when {
            diff < TimeUnit.MINUTES.toMillis(1) -> "1m"
            diff < TimeUnit.HOURS.toMillis(1) -> "${TimeUnit.MILLISECONDS.toMinutes(diff)}m"
            diff < TimeUnit.DAYS.toMillis(1) -> "${TimeUnit.MILLISECONDS.toHours(diff)}h"
            diff < TimeUnit.DAYS.toMillis(7) -> "${TimeUnit.MILLISECONDS.toDays(diff)}d"
            else -> {
                val sdf = SimpleDateFormat("d/M/yy", Locale.getDefault())
                sdf.format(timestamp.toDate())
            }
        }
    }
}
