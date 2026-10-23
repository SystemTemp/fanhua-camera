package com.fanhua.camera.debug

import android.util.Log
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object DebugLog {

    @Volatile
    var enabled: Boolean = false

    private const val MAX_CHARS = 240_000
    private val lock = Any()
    private val buffer = StringBuilder()
    private val timeFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)

    private fun timestamp(): String = timeFormat.format(Date())

    fun log(tag: String, message: String) {
        if (!enabled) return
        val line = "${timestamp()} [${tag}] $message\n"
        synchronized(lock) {
            buffer.append(line)
            if (buffer.length > MAX_CHARS) {
                buffer.delete(0, buffer.length - MAX_CHARS)
            }
        }
        Log.d("FanhuaDbg", "[$tag] $message")
    }

    fun snapshot(): String = synchronized(lock) { buffer.toString() }

    fun clear() {
        synchronized(lock) { buffer.setLength(0) }
        Log.d("FanhuaDbg", "debug log cleared")
    }
}
