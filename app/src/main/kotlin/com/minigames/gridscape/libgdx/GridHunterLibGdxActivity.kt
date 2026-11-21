package com.minigames.gridscape.libgdx

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.os.Bundle
import com.badlogic.gdx.backends.android.AndroidApplication
import com.badlogic.gdx.backends.android.AndroidApplicationConfiguration
import com.minigames.core.models.Difficulty
import com.minigames.gridscape.diagnostics.DiagnosticLog

class GridHunterLibGdxActivity : AndroidApplication() {

    companion object {
        fun createIntent(context: Context, difficulty: Difficulty): Intent =
            Intent(context, GridHunterLibGdxActivity::class.java)
                .putExtra(GridHunterLibGdxContract.EXTRA_DIFFICULTY, difficulty.name)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        DiagnosticLog.install(applicationContext)
        DiagnosticLog.info("LibGDX Activity onCreate: entering; savedState=${savedInstanceState != null}")
        super.onCreate(savedInstanceState)
        DiagnosticLog.info("LibGDX Activity onCreate: AndroidApplication super.onCreate completed")

        val config = AndroidApplicationConfiguration().apply {
            useImmersiveMode = true
            useWakelock = true
        }
        val rawDifficulty = intent.getStringExtra(GridHunterLibGdxContract.EXTRA_DIFFICULTY)
        val difficulty = rawDifficulty
            ?.let { requested -> Difficulty.entries.firstOrNull { it.name == requested } }
            ?: Difficulty.EASY
        DiagnosticLog.info("LibGDX Activity: difficulty extra=${rawDifficulty ?: "<missing>"}, resolved=$difficulty")

        try {
            DiagnosticLog.info("LibGDX Activity: calling initialize()")
            initialize(GridHunterLibGdxGame(difficulty) { finish() }, config)
            DiagnosticLog.info("LibGDX Activity: initialize() returned")
        } catch (failure: Throwable) {
            DiagnosticLog.error("LibGDX Activity: initialize() threw", failure)
            AlertDialog.Builder(this)
                .setTitle("GridHunter indítási hiba")
                .setMessage("${failure::class.java.name}: ${failure.message}\n\nA részletes napló a főmenü Diagnosztika pontjában található.")
                .setPositiveButton("OK") { _, _ -> finish() }
                .setCancelable(false)
                .show()
        }
    }

    override fun onResume() {
        DiagnosticLog.info("LibGDX Activity onResume")
        super.onResume()
    }

    override fun onPause() {
        DiagnosticLog.info("LibGDX Activity onPause")
        super.onPause()
    }

    override fun onDestroy() {
        DiagnosticLog.info("LibGDX Activity onDestroy")
        super.onDestroy()
    }
}
