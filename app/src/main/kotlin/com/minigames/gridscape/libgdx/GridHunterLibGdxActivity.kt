package com.minigames.gridscape.libgdx

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import com.badlogic.gdx.backends.android.AndroidApplication
import com.badlogic.gdx.backends.android.AndroidApplicationConfiguration
import com.minigames.core.models.Difficulty

class GridHunterLibGdxActivity : AndroidApplication() {

    companion object {
        private const val TAG = "GridHunterLibGdx"

        fun createIntent(context: Context, difficulty: Difficulty): Intent =
            Intent(context, GridHunterLibGdxActivity::class.java)
                .putExtra(GridHunterLibGdxContract.EXTRA_DIFFICULTY, difficulty.name)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        Log.d(TAG, "onCreate start")

        // Global uncaught exception handler — megmutatja a hibát dialógusban,
        // így Logcat nélkül is látható mi omlott össze és miért.
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            Log.e(TAG, "UNCAUGHT EXCEPTION on thread ${thread.name}", throwable)
            val message = buildString {
                append("Thread: ${thread.name}\n\n")
                append("${throwable::class.java.simpleName}: ${throwable.message}\n\n")
                throwable.stackTrace.take(8).forEach { append("  at $it\n") }
                var cause = throwable.cause
                while (cause != null) {
                    append("\nCaused by: ${cause::class.java.simpleName}: ${cause.message}\n")
                    cause.stackTrace.take(4).forEach { append("  at $it\n") }
                    cause = cause.cause
                }
            }
            try {
                runOnUiThread {
                    AlertDialog.Builder(this)
                        .setTitle("GridHunter LibGDX Crash")
                        .setMessage(message)
                        .setPositiveButton("OK") { _, _ -> defaultHandler?.uncaughtException(thread, throwable) }
                        .setCancelable(false)
                        .show()
                }
            } catch (e: Exception) {
                defaultHandler?.uncaughtException(thread, throwable)
            }
        }

        super.onCreate(savedInstanceState)

        val config = AndroidApplicationConfiguration().apply {
            useImmersiveMode = true
            useWakelock = true
        }

        val difficulty = intent.getStringExtra(GridHunterLibGdxContract.EXTRA_DIFFICULTY)
            ?.let { requested -> Difficulty.entries.firstOrNull { it.name == requested } }
            ?: Difficulty.EASY

        Log.d(TAG, "Starting with difficulty=$difficulty")

        try {
            initialize(GridHunterLibGdxGame(difficulty) { finish() }, config)
        } catch (t: Throwable) {
            Log.e(TAG, "initialize() failed", t)
            val message = buildString {
                append("initialize() dobott kivételt:\n\n")
                append("${t::class.java.simpleName}: ${t.message}\n\n")
                t.stackTrace.take(10).forEach { append("  at $it\n") }
                var cause = t.cause
                while (cause != null) {
                    append("\nCaused by: ${cause::class.java.simpleName}: ${cause.message}\n")
                    cause.stackTrace.take(5).forEach { append("  at $it\n") }
                    cause = cause.cause
                }
            }
            AlertDialog.Builder(this)
                .setTitle("GridHunter LibGDX Crash")
                .setMessage(message)
                .setPositiveButton("OK") { _, _ -> finish() }
                .setCancelable(false)
                .show()
        }

        Log.d(TAG, "onCreate complete")
    }

    override fun onDestroy() {
        Log.d(TAG, "onDestroy")
        super.onDestroy()
    }
}
