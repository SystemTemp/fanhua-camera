package com.fanhua.camera.camera.filters

import androidx.annotation.StringRes
import com.fanhua.camera.R

enum class FilterPreset(@StringRes val labelRes: Int) {
    ORIGINAL(R.string.filter_original),
    DREAM(R.string.filter_dream),
    LEICA_MOMENT(R.string.filter_leica),
    LEICA_MONO(R.string.filter_leica_mono),
    LEICA_PORTRAIT(R.string.filter_leica_portrait),
    APPLE_LOG(R.string.filter_apple_log);

    companion object {
        fun fromKey(key: String?): FilterPreset = entries.firstOrNull { it.name == key } ?: DREAM
    }
}