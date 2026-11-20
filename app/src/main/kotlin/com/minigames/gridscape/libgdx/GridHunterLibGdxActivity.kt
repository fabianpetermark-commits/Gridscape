package com.minigames.gridscape.libgdx

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import com.badlogic.gdx.backends.android.AndroidApplication
import com.badlogic.gdx.backends.android.AndroidApplicationConfiguration
import com.minigames.core.models.Difficulty

/**
 * Isolated libGDX entry point. The existing Compose MainActivity remains the launcher.
 * This activity is intentionally not wired into production navigation yet.
 *
 * Theme note: the Activity declares Theme.Black.NoTitleBar.Fullscreen in the manifest,
 * which is required by the libGDX Android backend for correct GLSurfaceView setup.
 * The app-level Theme.Material.NoActionBar is NOT compatible with libGDX on all devices.
 */
class GridHunterLibGdxActivity : AndroidApplication() {

    companion object {
        private const val TAG = "GridHunterLibGdx"

        fun createIntent(context: Context, difficulty: Difficulty): Intent =
            Intent(context, GridHunterLibGdxActivity::class.java)
                .putExtra(GridHunterLibGdxContract.EXTRA_DIFFICULTY, difficulty.name)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        Log.d(TAG, "onCreate start")
        super.onCreate(savedInstanceState)
        val config = AndroidApplicationConfiguration().apply {
            useImmersiveMode = true
            useWakelock = true
        }
        // Difficulty.entries replaces the deprecated Difficulty.values()
        val difficulty = intent.getStringExtra(GridHunterLibGdxContract.EXTRA_DIFFICULTY)
            ?.let { requested -> Difficulty.entries.firstOrNull { it.name == requested } }
            ?: Difficulty.EASY
        Log.d(TAG, "Starting GridHunterLibGdxGame with difficulty=$difficulty")
        try {
            initialize(GridHunterLibGdxGame(difficulty) { finish() }, config)
        } catch (t: Throwable) {
            Log.e(TAG, "initialize() threw an exception — full stack trace follows", t)
            throw t
        }
        Log.d(TAG, "onCreate complete")
    }

    override fun onDestroy() {
        Log.d(TAG, "onDestroy")
        super.onDestroy()
    }
}
