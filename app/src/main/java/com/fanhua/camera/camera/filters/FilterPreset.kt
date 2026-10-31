package com.fanhua.camera.camera.filters

import androidx.annotation.StringRes
import com.fanhua.camera.R

/**
 * 滤镜预设。
 *
 * 滤镜分为四组，滤镜条按组呈现：
 * - 基础：原图、繁花如梦
 * - 徕卡：一瞬 / 鲜艳 / 自然 / 经典 / 单色 / 单色HC / 漂棕 / 单色蓝 / 人像
 * - 胶片：富士正片 / 柯达波特拉 / 胶片颗粒
 * - 视频：青橙电影 / 电影质感 / Apple Log / SDR 标准
 * - 自定义：导入的 .cube LUT
 */
enum class FilterPreset(@StringRes val labelRes: Int) {
    ORIGINAL(R.string.filter_original),
    DREAM(R.string.filter_dream),

    LEICA_MOMENT(R.string.filter_leica),
    LEICA_VIVID(R.string.filter_leica_vivid),
    LEICA_NATURAL(R.string.filter_leica_natural),
    LEICA_CLASSIC(R.string.filter_leica_classic),
    LEICA_MONO(R.string.filter_leica_mono),
    LEICA_BW_HC(R.string.filter_leica_bw_hc),
    LEICA_BROWN(R.string.filter_leica_brown),
    LEICA_BW_BLUE(R.string.filter_leica_bw_blue),
    LEICA_PORTRAIT(R.string.filter_leica_portrait),

    FUJI_CLASSIC(R.string.filter_fuji_classic),
    KODAK_PORTRA(R.string.filter_kodak_portra),
    FILM_GRAIN(R.string.filter_film_grain),

    TEAL_ORANGE(R.string.filter_teal_orange),
    CINEMATIC(R.string.filter_cinematic),
    APPLE_LOG(R.string.filter_apple_log),
    SDR(R.string.filter_sdr),

    CUSTOM_LUT(R.string.filter_custom_lut);

    val group: FilterGroup
        get() = when (this) {
            ORIGINAL, DREAM -> FilterGroup.BASIC
            LEICA_MOMENT, LEICA_VIVID, LEICA_NATURAL, LEICA_CLASSIC, LEICA_MONO,
            LEICA_BW_HC, LEICA_BROWN, LEICA_BW_BLUE, LEICA_PORTRAIT -> FilterGroup.LEICA
            FUJI_CLASSIC, KODAK_PORTRA, FILM_GRAIN -> FilterGroup.FILM
            TEAL_ORANGE, CINEMATIC, APPLE_LOG, SDR -> FilterGroup.VIDEO
            CUSTOM_LUT -> FilterGroup.CUSTOM
        }

    /** 黑白滤镜只保留亮度通道，可跳过饱和度/色相计算。 */
    val monochrome: Boolean
        get() = this == LEICA_MONO || this == LEICA_BW_HC ||
            this == LEICA_BROWN || this == LEICA_BW_BLUE

    companion object {
        fun fromKey(key: String?): FilterPreset = entries.firstOrNull { it.name == key } ?: DREAM

        fun ofGroup(group: FilterGroup): List<FilterPreset> = entries.filter { it.group == group }
    }
}

enum class FilterGroup(@StringRes val labelRes: Int) {
    BASIC(R.string.filter_original),
    LEICA(R.string.filter_leica_group),
    FILM(R.string.filter_group_film),
    VIDEO(R.string.mode_video),
    CUSTOM(R.string.filter_custom_lut)
}
