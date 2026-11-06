package com.fanhua.camera

import android.app.Application
import android.os.Build
import com.fanhua.camera.data.LocaleManager
import com.fanhua.camera.data.UpdateManager
import com.fanhua.camera.debug.DebugLog
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class FanhuaApp : Application() {

    override fun onCreate() {
        super.onCreate()
        installCrashHandler()
        LocaleManager.applyStored(this)
        UpdateManager(this).cleanup()
    }

    /**
     * 全局未捕获异常处理器。
     *
     * release 包闪退时系统日志对普通用户不可见，导致「一打开就闪退」这类问题
     * 无法定位。这里把崩溃堆栈同时写入：
     * - `getExternalFilesDir("crashes")/crash_<时间>.txt`（可用文件管理器直接取）
     * - `DebugLog` 缓冲区（若已启用调试）
     *
     * 写入后仍然交回默认处理器，保证系统行为（闪退、上报）不变。
     */
    private fun installCrashHandler() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            try {
                val sw = StringWriter()
                PrintWriter(sw).use { pw ->
                    pw.println("时间: " + SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date()))
                    pw.println("线程: " + thread.name)
                    pw.println("机型: " + Build.MANUFACTURER + " " + Build.MODEL)
                    pw.println("系统: Android " + Build.VERSION.RELEASE + " (API " + Build.VERSION.SDK_INT + ")")
                    pw.println("应用: " + appVersionName())
                    pw.println("--- 堆栈 ---")
                    error.printStackTrace(pw)
                }
                val text = sw.toString()
                DebugLog.log("Crash", text)

                val dir = getExternalFilesDir("crashes") ?: File(filesDir, "crashes")
                if (!dir.exists()) dir.mkdirs()
                val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
                File(dir, "crash_$stamp.txt").writeText(text)

                // 只保留最近 20 个崩溃文件，避免无限增长
                dir.listFiles()?.sortedByDescending { it.name }?.drop(20)?.forEach { it.delete() }
            } catch (ignore: Throwable) {
                // 崩溃处理本身绝不能再抛异常
            }
            previous?.uncaughtException(thread, error)
        }
    }

    private fun appVersionName(): String = try {
        packageManager.getPackageInfo(packageName, 0).versionName ?: "?"
    } catch (e: Exception) {
        "?"
    }
}
