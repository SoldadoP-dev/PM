package com.example.pm.utils

import android.content.Context
import android.content.res.Configuration
import java.util.Locale

object LocaleHelper {
    fun updateLocale(context: Context): Context {
        val sharedPrefs = context.getSharedPreferences("Settings", Context.MODE_PRIVATE)
        val isEnglish = sharedPrefs.getBoolean("is_english", false)
        val locale = if (isEnglish) Locale("en") else Locale("es")
        
        Locale.setDefault(locale)
        val config = Configuration(context.resources.configuration)
        config.setLocale(locale)
        
        return context.createConfigurationContext(config)
    }
}
