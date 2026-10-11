package com.fanhua.camera.data

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat

object LocaleManager {
    const val SYSTEM = "system"

    private const val PREFS = "fanhua_locale"
    private const val KEY_LANGUAGE = "language_tag"

    val supportedTags: List<String> = listOf("zh-CN", "zh-TW", "en", "ja", "ko", "es", "fr", "de", "ru")

    fun storedTag(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_LANGUAGE, SYSTEM) ?: SYSTEM

    fun applyStored(context: Context) {
        apply(context, storedTag(context), persist = false)
    }

    fun apply(context: Context, tag: String, persist: Boolean = true) {
        if (persist) {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_LANGUAGE, tag)
                .apply()
        }
        val locales = if (tag == SYSTEM) {
            LocaleListCompat.getEmptyLocaleList()
        } else {
            LocaleListCompat.forLanguageTags(tag)
        }
        AppCompatDelegate.setApplicationLocales(locales)
    }
}