package com.luckyunders.app

import android.content.Context
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object CrashLog {
    private const val FILE_NAME = "last_crash.txt"

    fun install(context: Context) {
        val appCtx = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                write(appCtx, thread, throwable)
            } catch (_: Throwable) { /* swallow — we're already crashing */ }
            previous?.uncaughtException(thread, throwable)
        }
    }

    private fun file(context: Context): File = File(context.filesDir, FILE_NAME)

    private fun write(context: Context, thread: Thread, throwable: Throwable) {
        val ts = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
        val sw = StringWriter()
        throwable.printStackTrace(PrintWriter(sw))
        val body = buildString {
            appendLine("Lucky Unders crash @ $ts")
            appendLine("Thread: ${thread.name}")
            appendLine("Android: ${android.os.Build.VERSION.RELEASE} (SDK ${android.os.Build.VERSION.SDK_INT})")
            appendLine("Device: ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}")
            appendLine("--")
            append(sw.toString())
        }
        file(context).writeText(body)
    }

    fun read(context: Context): String? {
        val f = file(context)
        return if (f.exists()) f.readText() else null
    }

    fun clear(context: Context) {
        file(context).delete()
    }
}
