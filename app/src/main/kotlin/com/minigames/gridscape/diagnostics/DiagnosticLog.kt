package com.minigames.gridscape.diagnostics

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.util.Log
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Small persistent crash/breadcrumb log for diagnosing device-only failures.
 * The log is stored in app-private files and can be viewed from the app's
 * Diagnostics screen. No network access or external storage permission needed.
 */
object DiagnosticLog {
    private const val TAG = "GridscapeDiag"
    private const val FILE_NAME = "gridscape-diagnostics.log"
    private const val MAX_BYTES = 512 * 1024
    @Volatile private var installed = false
    private lateinit var appContext: Context

    @Synchronized
    fun install(context: Context) {
        appContext = context.applicationContext
        if (!installed) {
            val previous = Thread.getDefaultUncaughtExceptionHandler()
            Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
                error("UNCAUGHT on thread=${thread.name}", throwable)
                previous?.uncaughtException(thread, throwable)
            }
            installed = true
        }
        info("Diagnostic logger ready; sdk=${Build.VERSION.SDK_INT}; device=${Build.MANUFACTURER} ${Build.MODEL}")
        collectPreviousExitInfo()
    }

    fun info(message: String) = write("INFO", message)
    fun warn(message: String, throwable: Throwable? = null) = write("WARN", message, throwable)
    fun error(message: String, throwable: Throwable? = null) = write("ERROR", message, throwable)

    @Synchronized
    private fun write(level: String, message: String, throwable: Throwable? = null) {
        if (!::appContext.isInitialized) return
        val stamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(Date())
        val trace = throwable?.let {
            StringWriter().also { writer -> it.printStackTrace(PrintWriter(writer)) }.toString()
        }.orEmpty()
        val entry = "[$stamp] [$level] [${Thread.currentThread().name}] $message" +
            if (trace.isEmpty()) "\n" else "\n$trace\n"
        try {
            val file = File(appContext.filesDir, FILE_NAME)
            if (file.exists() && file.length() + entry.toByteArray().size > MAX_BYTES) {
                val tail = file.readText().takeLast(MAX_BYTES / 2)
                file.writeText("[log rotated]\n$tail")
            }
            file.appendText(entry)
        } catch (failure: Exception) {
            Log.e(TAG, "Could not persist diagnostic log", failure)
        }
        when (level) {
            "ERROR" -> Log.e(TAG, message, throwable)
            "WARN" -> Log.w(TAG, message, throwable)
            else -> Log.i(TAG, message)
        }
    }

    fun read(context: Context): String = runCatching {
        File(context.applicationContext.filesDir, FILE_NAME).takeIf { it.exists() }?.readText()
            ?: "Még nincs naplóbejegyzés."
    }.getOrElse { "A napló nem olvasható: ${it.message}" }

    fun clear(context: Context) {
        runCatching { File(context.applicationContext.filesDir, FILE_NAME).writeText("") }
        info("Diagnostic log cleared by user")
    }

    private fun collectPreviousExitInfo() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
        try {
            val manager = appContext.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val exits = manager.getHistoricalProcessExitReasons(appContext.packageName, null, 5)
            exits.forEach { exit ->
                val description = "Previous process exit: reason=${exit.reason}, status=${exit.status}, " +
                    "importance=${exit.importance}, timestamp=${exit.timestamp}, description=${exit.description}"
                info(description)
                exit.traceInputStream?.bufferedReader()?.use { reader ->
                    val trace = reader.readText()
                    if (trace.isNotBlank()) info("Previous process trace:\n$trace")
                }
            }
        } catch (failure: Exception) {
            warn("Could not read historical process exit info", failure)
        }
    }
}
