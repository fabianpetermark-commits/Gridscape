package com.minigames.gridscape.libgdx

import android.content.Context
import android.content.Intent
import android.os.Bundle
import com.badlogic.gdx.backends.android.AndroidApplication
import com.badlogic.gdx.backends.android.AndroidApplicationConfiguration
import com.minigames.core.models.Difficulty

/**
 * Isolated libGDX entry point. The existing Compose MainActivity remains the launcher.
 * This activity is intentionally not wired into production navigation yet.
 */
class GridHunterLibGdxActivity : AndroidApplication() {
    companion object {
        const val EXTRA_DIFFICULTY = "com.minigames.gridscape.extra.GRID_HUNTER_DIFFICULTY"

        fun createIntent(context: Context, difficulty: Difficulty): Intent =
            Intent(context, GridHunterLibGdxActivity::class.java)
                .putExtra(EXTRA_DIFFICULTY, difficulty.name)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val config = AndroidApplicationConfiguration().apply {
            useImmersiveMode = true
            useWakelock = true
        }
        val difficulty = intent.getStringExtra(EXTRA_DIFFICULTY)
            ?.let { requested -> Difficulty.values().firstOrNull { it.name == requested } }
            ?: Difficulty.EASY
        initialize(GridHunterLibGdxGame(difficulty) { finish() }, config)
    }
}
