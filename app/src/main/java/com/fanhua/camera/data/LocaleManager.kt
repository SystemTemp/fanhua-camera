package com.fanhua.camera.data

import android.content.Context
import android.os.Build
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import java.util.Locale

/**
 * 界面语言管理。
 *
 * ## 为什么不用「空 locale 列表 = 跟随系统」
 *
 * `AppCompatDelegate.setApplicationLocales(空列表)` 会走 AppCompat 的自动匹配：
 * 拿设备 locale 列表逐个去匹配 `localeConfig` 里声明的语种。
 * 在多语言设备上（例如 locale 列表为 `zh-Hans-CN,zh-Hant-CN`）这一步可能匹配到
 * `zh-TW`，导致**简体中文设备显示繁体**——这在 v0.1.21 就出现过，
 * 用户手动切「中文（中国）」也无法纠正。
 *
 * 因此这里改成**显式解析**：自己把系统语言映射到受支持的具体语种，
 * 再以非空列表下发给 AppCompat，行为完全确定。
 */
object LocaleManager {
    const val SYSTEM = "system"

    private const val PREFS = "fanhua_locale"
    private const val KEY_LANGUAGE = "language_tag"

    val supportedTags: List<String> = listOf(
        "zh-CN", "zh-TW", "en", "ja", "ko", "es", "fr", "de", "ru"
    )

    fun storedTag(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_LANGUAGE, SYSTEM) ?: SYSTEM

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
        val resolved = if (tag == SYSTEM) resolveSystemTag(context) else tag
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(resolved))
    }

    /**
     * 把系统语言解析为一个受支持的具体语种。
     *
     * 关键点：中文要区分简繁，不能只看 `language == "zh"`。
     * 判断优先级：
     * 1. 脚本标记（Hans / Hant）——最准确
     * 2. 地区（CN/SG → 简，TW/HK/MO → 繁）
     * 3. 都没有则按语言代码匹配
     */
    fun resolveSystemTag(context: Context): String {
        val systemLocales = LocaleListCompat.getDefault()
        val primary = systemLocales.get(0) ?: Locale.getDefault()
        return resolveTag(primary)
    }

    /** 把一个 [Locale] 映射到受支持语种。 */
    fun resolveTag(locale: Locale): String {
        val language = locale.language.lowercase(Locale.US)
        if (language != "zh") {
            return supportedTags.firstOrNull { it.startsWith("$language-") }
                ?.takeIf { it == "en" || it.substringAfter('-') in REGION_TO_TAG.values }
                ?: language
        }
        // 中文：脚本优先，其次地区
        val script = locale.script.uppercase(Locale.US)
        val region = locale.country.uppercase(Locale.US)
        if (script == "HANT") return "zh-TW"
        if (script == "HANS") return "zh-CN"
        return when (region) {
            "TW", "HK", "MO" -> "zh-TW"
            "CN", "SG" -> "zh-CN"
            else -> {
                // 没有地区信息时，用系统默认 locale 再判一次，
                // 仍不确定则默认简体（中文用户绝大多数是简体环境）
                val fallback = Locale.getDefault()
                if (fallback.country.uppercase(Locale.US) in setOf("TW", "HK", "MO")) {
                    "zh-TW"
                } else {
                    "zh-CN"
                }
            }
        }
    }

    /** 系统语言对应的语种，用于设置页展示当前状态。 */
    fun systemTagLabel(context: Context): String = resolveSystemTag(context)

    private val REGION_TO_TAG = mapOf(
        "CN" to "zh-CN", "SG" to "zh-CN",
        "TW" to "zh-TW", "HK" to "zh-TW", "MO" to "zh-TW"
    )
}
