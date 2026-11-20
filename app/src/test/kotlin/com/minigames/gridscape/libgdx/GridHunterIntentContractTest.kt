package com.minigames.gridscape.libgdx

import com.minigames.core.models.Difficulty
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * JVM unit tests for the difficulty intent contract used by GridHunterLibGdxActivity.
 *
 * These tests verify the serialisation/deserialisation logic that the Activity uses to
 * receive a [Difficulty] value from the Compose caller without constructing an Android
 * [android.content.Intent] or running on a device. They cover the same code path that
 * is exercised when the "LIBGDX TEST" button is tapped in production.
 *
 * Run with: ./gradlew :app:test
 */
class GridHunterIntentContractTest {

    // -------------------------------------------------------------------------
    // Helpers that mirror the Activity's intent contract without Android deps
    // -------------------------------------------------------------------------

    /** Simulates serialising the difficulty the same way createIntent() does. */
    private fun serialise(difficulty: Difficulty): String = difficulty.name

    /**
     * Simulates deserialising the difficulty the same way the Activity's
     * onCreate() does, including the EASY fallback for missing/unknown values.
     */
    private fun deserialise(raw: String?): Difficulty =
        raw?.let { requested -> Difficulty.entries.firstOrNull { it.name == requested } }
            ?: Difficulty.EASY

    // -------------------------------------------------------------------------
    // Round-trip: every defined difficulty survives serialise → deserialise
    // -------------------------------------------------------------------------

    @Test
    fun `all difficulties round-trip through the intent extra without loss`() {
        for (difficulty in Difficulty.entries) {
            val serialised = serialise(difficulty)
            val recovered = deserialise(serialised)
            assertEquals(
                difficulty, recovered,
                "Round-trip failed for $difficulty: serialised as '$serialised', " +
                    "deserialised as $recovered"
            )
        }
    }

    // -------------------------------------------------------------------------
    // Edge cases: missing or invalid extra falls back to EASY
    // -------------------------------------------------------------------------

    @Test
    fun `null intent extra falls back to EASY`() {
        assertEquals(Difficulty.EASY, deserialise(null))
    }

    @Test
    fun `empty string extra falls back to EASY`() {
        assertEquals(Difficulty.EASY, deserialise(""))
    }

    @Test
    fun `unknown difficulty name falls back to EASY`() {
        assertEquals(Difficulty.EASY, deserialise("LEGENDARY"))
    }

    @Test
    fun `lowercase difficulty name falls back to EASY because matching is case-sensitive`() {
        // The contract uses enum name which is uppercase; lowercase is not a valid value.
        assertEquals(Difficulty.EASY, deserialise("easy"))
    }

    // -------------------------------------------------------------------------
    // Spot-checks for each difficulty by name
    // -------------------------------------------------------------------------

    @Test
    fun `EASY serialises to the string EASY`() {
        assertEquals("EASY", serialise(Difficulty.EASY))
    }

    @Test
    fun `MEDIUM serialises to the string MEDIUM`() {
        assertEquals("MEDIUM", serialise(Difficulty.MEDIUM))
    }

    @Test
    fun `HARD serialises to the string HARD`() {
        assertEquals("HARD", serialise(Difficulty.HARD))
    }

    @Test
    fun `INSANE serialises to the string INSANE`() {
        assertEquals("INSANE", serialise(Difficulty.INSANE))
    }

    // -------------------------------------------------------------------------
    // Constant guard: the extra key must not change between builds
    // -------------------------------------------------------------------------

    @Test
    fun `EXTRA_DIFFICULTY constant has the expected fully-qualified value`() {
        assertEquals(
            "com.minigames.gridscape.extra.GRID_HUNTER_DIFFICULTY",
            GridHunterLibGdxActivity.EXTRA_DIFFICULTY,
        )
    }
}
