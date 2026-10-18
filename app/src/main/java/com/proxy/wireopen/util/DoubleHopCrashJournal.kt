package com.proxy.wireopen.util

import android.content.Context
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Small fsync-backed journal for double-hop startup. It survives a native
 * libbox crash, unlike LogManager's in-memory buffer.
 */
object DoubleHopCrashJournal {
    private const val FILE_NAME = "double-hop-startup.log"
    private val lock = Any()
    @Volatile private var restored = false

    @JvmStatic
    fun begin(context: Context, message: String) {
        synchronized(lock) {
            restored = false
            writeLine(context, message, truncate = true)
        }
    }

    @JvmStatic
    fun record(context: Context, message: String) {
        synchronized(lock) {
            writeLine(context, message, truncate = false)
        }
    }

    @JvmStatic
    fun restore(context: Context) {
        synchronized(lock) {
            if (restored) return
            restored = true
            val file = journalFile(context)
            if (!file.isFile) return
            try {
                file.readLines(Charsets.UTF_8).takeLast(120).forEach { line ->
                    LogManager.warn("DoubleHopCrashJournal", line)
                }
            } catch (e: Exception) {
                Log.e("DoubleHopCrashJournal", "读取双跳启动记录失败", e)
            }
        }
    }

    private fun writeLine(context: Context, message: String, truncate: Boolean) {
        try {
            val file = journalFile(context)
            file.parentFile?.mkdirs()
            val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(Date())
            val line = "$timestamp $message\n".toByteArray(Charsets.UTF_8)
            FileOutputStream(file, !truncate).use { output ->
                output.write(line)
                output.fd.sync()
            }
        } catch (e: Exception) {
            Log.e("DoubleHopCrashJournal", "写入双跳启动记录失败", e)
        }
    }

    private fun journalFile(context: Context): File {
        val directory = context.getExternalFilesDir(null) ?: context.filesDir
        return File(directory, FILE_NAME)
    }
}
