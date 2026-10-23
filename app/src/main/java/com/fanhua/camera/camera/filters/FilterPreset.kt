package com.fanhua.camera.camera.filters

import androidx.annotation.StringRes
import com.fanhua.camera.R

enum class FilterPreset(@StringRes val labelRes: Int) {
    ORIGINAL(R.string.filter_original),
    DREAM(R.string.filter_dream),
    LEICA_MOMENT(R.string.filter_leica),
    LEICA_VIVID(R.string.filter_leica_vivid),
    LEICA_NATURAL(R.string.filter_leica_natural),
    LEICA_MONO(R.string.filter_leica_mono),
    LEICA_BW_HC(R.string.filter_leica_bw_hc),
    LEICA_BROWN(R.string.filter_leica_brown),
    LEICA_BW_BLUE(R.string.filter_leica_bw_blue),
    LEICA_PORTRAIT(R.string.filter_leica_portrait),
    SDR(R.string.filter_sdr),
    APPLE_LOG(R.string.filter_apple_log),
    CUSTOM_LUT(R.string.filter_custom_lut);

    companion object {
        fun fromKey(key: String?): FilterPreset = entries.firstOrNull { it.name == key } ?: DREAM
    }
}