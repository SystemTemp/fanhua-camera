package com.fanhua.camera.camera.filters

import androidx.annotation.StringRes
import com.fanhua.camera.R

/**
 * 滤镜预设。
 *
 * 分四组：
 * - **基础**：原图 / 繁花如梦（源自小米相机电影模式 LUT）
 * - **徕卡**：源自「徕卡一瞬」App 的真实 3D LUT（`res/raw/lut_l_*.cube`）
 * - **电影**：源自「徕卡一瞬」的其它真实 LUT（`lut_effect_*` / `lut_normal_*` / `lut_portrait_*`）
 * - **风格**：本项目自研的参数化调色
 *
 * 带 [lutAsset] 的滤镜走真实 3D LUT 采样；为 null 的走 [FilterGrade] 参数化调色。
 */
enum class FilterPreset(
    @StringRes val labelRes: Int,
    /** res/raw 下的 .lut 资源名（不含扩展名），null 表示用参数化调色。 */
    val lutAsset: String? = null,
    /** 真实 LUT 的默认强度（1 = 100%）。 */
    val lutStrength: Float = 1f,
    /** drawable-nodpi 下的实拍预览图资源名，null 表示无预览图（回退到色卡渲染）。 */
    val previewAsset: String? = null
) {
    // ───────── 基础 ─────────
    ORIGINAL(R.string.filter_original),
    DREAM(R.string.filter_dream, "lut_effect_fbld", 0.7f),

    // ───────── 徕卡 Looks（真实 LUT，来自徕卡一瞬 App 的 LC_* 系列） ─────────
    LEICA_IA(R.string.filter_leica_ia, "lut_l_ia", previewAsset = "look_ia1"),
    LEICA_CLASSIC(R.string.filter_leica_classic, "lut_l_cls", previewAsset = "look_classic1"),
    LEICA_CHROME(R.string.filter_leica_chrome, "lut_l_chr", previewAsset = "look_chrome1"),
    LEICA_CONTEMPORARY(R.string.filter_leica_contemporary, "lut_l_cnt", previewAsset = "look_contemporary1"),
    LEICA_ETERNAL(R.string.filter_leica_eternal, "lut_l_etn", previewAsset = "look_eternal1"),
    LEICA_BRASS(R.string.filter_leica_brass, "lut_l_brs", previewAsset = "look_brass1"),
    LEICA_TEAL(R.string.filter_leica_teal, "lut_l_tel", previewAsset = "look_teal1"),
    // 徕卡经典机身色彩：无 3D LUT，用参数化调色还原机型特征
    // M9：CCD 感光元件，色彩浓郁偏暖，红色厚重，暗部微绿，高光柔和
    LEICA_M9(R.string.filter_leica_m9),
    // M3：早期胶片机身风格，低饱和、暖调、宽容度高、轻微褪色
    LEICA_M3(R.string.filter_leica_m3),

    // ───────── 徕卡影调（来自「风味相机」10.1.0 的 cameralook 真实影调 LUT） ─────────
    // 这 23 个 .lut 从 APK 的 assets/cameralook/tone-*.lut 提取并转码：
    // 原格式是 CLU1 头 + 33³ 大端 float32，转成本项目 BinaryLutLoader 的
    // 4 字节 size + RGB888 布局。它们是该相机「影调」功能的实际调色表，
    // 与上面 lut_l_* 那批 look LUT 不重复。
    TONE_0(R.string.filter_tone_0, "tone_0"),
    TONE_1(R.string.filter_tone_1, "tone_1"),
    TONE_2(R.string.filter_tone_2, "tone_2"),
    TONE_3(R.string.filter_tone_3, "tone_3"),
    TONE_4(R.string.filter_tone_4, "tone_4"),
    TONE_5(R.string.filter_tone_5, "tone_5"),
    TONE_6(R.string.filter_tone_6, "tone_6"),
    TONE_7(R.string.filter_tone_7, "tone_7"),
    TONE_8(R.string.filter_tone_8, "tone_8"),
    TONE_9(R.string.filter_tone_9, "tone_9"),
    TONE_10(R.string.filter_tone_10, "tone_10"),
    TONE_11(R.string.filter_tone_11, "tone_11"),
    TONE_12(R.string.filter_tone_12, "tone_12"),
    TONE_13(R.string.filter_tone_13, "tone_13"),
    TONE_14(R.string.filter_tone_14, "tone_14"),
    TONE_15(R.string.filter_tone_15, "tone_15"),
    TONE_16(R.string.filter_tone_16, "tone_16"),
    TONE_17(R.string.filter_tone_17, "tone_17"),
    TONE_18(R.string.filter_tone_18, "tone_18"),
    TONE_19(R.string.filter_tone_19, "tone_19"),
    TONE_20(R.string.filter_tone_20, "tone_20"),
    TONE_21(R.string.filter_tone_21, "tone_21"),
    TONE_22(R.string.filter_tone_22, "tone_22"),

    // ───────── 电影 LUT（真实 LUT） ─────────
    FILM_VIVID(R.string.filter_film_vivid, "lut_normal_distinct"),
    FILM_CLASSIC(R.string.filter_film_classic, "lut_normal_classic"),
    FILM_NATURE(R.string.filter_film_nature, "lut_normal_nature"),
    FILM_NEUTRAL(R.string.filter_film_neutral, "lut_normal_neutral"),
    FILM_FRESH(R.string.filter_film_fresh, "lut_normal_freshness"),
    FILM_CLEAR(R.string.filter_film_clear, "lut_normal_clearness"),
    FILM_WHITENING(R.string.filter_film_whitening, "lut_normal_whitening"),
    FILM_COLD_WHITE(R.string.filter_film_cold_white, "lut_normal_cold_white"),
    FILM_BLACKGOLD(R.string.filter_film_blackgold, "lut_normal_blackgold"),
    FILM_RED(R.string.filter_film_red, "lut_effect_rh"),
    FILM_TEAL_BLUE(R.string.filter_film_teal_blue, "lut_effect_tc"),
    FILM_HIGH_CONTRAST(R.string.filter_film_high_contrast, "lut_portrait_style_high_contrast"),
    FILM_TEXTURE(R.string.filter_film_texture, "lut_portrait_style_high_texture"),
    FILM_BW(R.string.filter_film_bw, "lut_portrait_style_black_white"),

    // ───────── 自研参数化 ─────────
    FUJI_CLASSIC(R.string.filter_fuji_classic),
    KODAK_PORTRA(R.string.filter_kodak_portra),
    FILM_GRAIN(R.string.filter_film_grain),
    TEAL_ORANGE(R.string.filter_teal_orange),
    CINEMATIC(R.string.filter_cinematic),
    APPLE_LOG(R.string.filter_apple_log),
    SDR(R.string.filter_sdr),

    // ───────── 用户导入 ─────────
    CUSTOM_LUT(R.string.filter_custom_lut);

    val group: FilterGroup
        get() = when (this) {
            ORIGINAL, DREAM -> FilterGroup.BASIC
            LEICA_IA, LEICA_CLASSIC, LEICA_CHROME, LEICA_CONTEMPORARY,
            LEICA_ETERNAL, LEICA_BRASS, LEICA_TEAL, LEICA_M9, LEICA_M3 -> FilterGroup.LEICA
            TONE_0, TONE_1, TONE_2, TONE_3, TONE_4, TONE_5, TONE_6, TONE_7,
            TONE_8, TONE_9, TONE_10, TONE_11, TONE_12, TONE_13, TONE_14,
            TONE_15, TONE_16, TONE_17, TONE_18, TONE_19, TONE_20, TONE_21,
            TONE_22 -> FilterGroup.TONE
            FILM_VIVID, FILM_CLASSIC, FILM_NATURE, FILM_NEUTRAL, FILM_FRESH,
            FILM_CLEAR, FILM_WHITENING, FILM_COLD_WHITE, FILM_BLACKGOLD,
            FILM_RED, FILM_TEAL_BLUE, FILM_HIGH_CONTRAST, FILM_TEXTURE, FILM_BW -> FilterGroup.FILM
            FUJI_CLASSIC, KODAK_PORTRA, FILM_GRAIN, TEAL_ORANGE,
            CINEMATIC, APPLE_LOG, SDR -> FilterGroup.STYLE
            CUSTOM_LUT -> FilterGroup.CUSTOM
        }

    /** 该滤镜是否基于真实 3D LUT。 */
    val usesRealLut: Boolean get() = lutAsset != null

    companion object {
        fun fromKey(key: String?): FilterPreset = entries.firstOrNull { it.name == key } ?: DREAM

        fun ofGroup(group: FilterGroup): List<FilterPreset> = entries.filter { it.group == group }
    }
}

enum class FilterGroup(@StringRes val labelRes: Int) {
    BASIC(R.string.filter_group_basic),
    LEICA(R.string.filter_leica_group),
    TONE(R.string.filter_group_tone),
    FILM(R.string.filter_group_film),
    STYLE(R.string.filter_group_style),
    CUSTOM(R.string.filter_custom_lut)
}
