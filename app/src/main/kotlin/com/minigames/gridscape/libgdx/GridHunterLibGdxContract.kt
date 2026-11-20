package com.minigames.gridscape.libgdx

/**
 * Shared intent contract constants for the libGDX prototype Activity.
 *
 * Extracted into a plain Kotlin object (no Android dependencies) so that
 * JVM unit tests can reference the extra key without loading the Activity
 * class — which extends AndroidApplication and cannot be instantiated on
 * the JVM test runner.
 */
object GridHunterLibGdxContract {
    /**
     * Intent extra key used to pass the selected [com.minigames.core.models.Difficulty]
     * from the Compose caller to [GridHunterLibGdxActivity].
     * Value is the enum's [Enum.name] string.
     */
    const val EXTRA_DIFFICULTY = "com.minigames.gridscape.extra.GRID_HUNTER_DIFFICULTY"
}
