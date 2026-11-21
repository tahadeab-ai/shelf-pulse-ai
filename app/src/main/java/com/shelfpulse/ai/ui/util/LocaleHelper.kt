package com.shelfpulse.ai.ui.util

import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import java.util.Locale

object LocaleHelper {

    fun getCurrentLanguage(): String {
        val locales = AppCompatDelegate.getApplicationLocales()
        return if (!locales.isEmpty) {
            locales.get(0)?.language ?: "en"
        } else {
            Locale.getDefault().language
        }
    }

    fun isArabic(): Boolean {
        return getCurrentLanguage().startsWith("ar")
    }

    fun setLanguage(languageCode: String) {
        val appLocale = LocaleListCompat.forLanguageTags(languageCode)
        AppCompatDelegate.setApplicationLocales(appLocale)
    }

    fun toggleLanguage() {
        val next = if (isArabic()) "en" else "ar"
        setLanguage(next)
    }
}
