package com.fanhua.camera.camera.gl

import android.graphics.Bitmap
import com.fanhua.camera.camera.filters.FilterPreset
import com.fanhua.camera.lut.LutData

interface GlFramePipeline {
    fun setPreset(preset: FilterPreset, intensity: Float)
    fun setSuperResolutionEnabled(enabled: Boolean)
    fun setStabilizationMatrix(matrix: FloatArray?, crop: Float = 1f)
    fun setLut(lut: LutData?)
    fun drawOesFrame(
        textureId: Int,
        textureMatrix: FloatArray,
        inputWidth: Int,
        inputHeight: Int,
        outputWidth: Int,
        outputHeight: Int,
        timestampNs: Long
    )

    fun drawBitmapFrame(
        textureId: Int,
        inputWidth: Int,
        inputHeight: Int,
        outputWidth: Int,
        outputHeight: Int
    )

    fun createTextureFromBitmap(bitmap: Bitmap): Int
    fun deleteTexture(textureId: Int)
    fun release()
}