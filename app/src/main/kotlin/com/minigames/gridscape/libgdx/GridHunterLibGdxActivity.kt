package com.minigames.gridscape.libgdx

import android.os.Bundle
import com.badlogic.gdx.backends.android.AndroidApplication
import com.badlogic.gdx.backends.android.AndroidApplicationConfiguration
import com.minigames.core.models.Difficulty

/**
 * Isolated libGDX entry point. The existing Compose MainActivity remains the launcher.
 * This activity is intentionally not wired into production navigation yet.
 */
class GridHunterLibGdxActivity : AndroidApplication() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val config = AndroidApplicationConfiguration().apply {
            useImmersiveMode = true
            useWakelock = true
        }
        initialize(GridHunterLibGdxGame(Difficulty.EASY), config)
    }
}
