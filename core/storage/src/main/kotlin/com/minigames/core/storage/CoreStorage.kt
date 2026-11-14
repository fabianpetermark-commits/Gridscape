package com.minigames.core.storage

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.minigames.core.input.ControlMode
import com.minigames.core.models.Difficulty
import com.minigames.core.models.GameId
import com.minigames.core.models.GameStats
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

// ============================================================================
// 1. User Settings Model & Repository
// ============================================================================

data class UserSettings(
    val controlMode: ControlMode = ControlMode.HYBRID,
    val hapticsEnabled: Boolean = true,
    val showControlHints: Boolean = true,
)

interface SettingsRepository {
    val settings: Flow<UserSettings>
    suspend fun setControlMode(mode: ControlMode)
    suspend fun setHapticsEnabled(enabled: Boolean)
    suspend fun setShowControlHints(enabled: Boolean)
}

// ============================================================================
// 2. Per-Game, Per-Difficulty Statistics Repository
// ============================================================================

interface StatsRepository {
    fun observeStats(gameId: GameId, difficulty: Difficulty): Flow<GameStats>

    fun observeAllStatsForGame(gameId: GameId): Flow<Map<Difficulty, GameStats>>

    suspend fun recordGameResult(
        gameId: GameId,
        difficulty: Difficulty,
        won: Boolean,
        completionTimeMs: Long?,
    )

    suspend fun clearStats(gameId: GameId)
}

// ============================================================================
// 3. Jetpack Preferences DataStore Implementations
// ============================================================================

private val Context.miniGamesDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "mini_games_preferences",
)

class DataStoreSettingsRepository(
    private val dataStore: DataStore<Preferences>,
) : SettingsRepository {

    private object Keys {
        val CONTROL_MODE = stringPreferencesKey("settings_control_mode")
        val HAPTICS_ENABLED = booleanPreferencesKey("settings_haptics_enabled")
        val SHOW_CONTROL_HINTS = booleanPreferencesKey("settings_show_control_hints")
    }

    override val settings: Flow<UserSettings> = dataStore.data.map { prefs ->
        val rawMode = prefs[Keys.CONTROL_MODE]
        val parsedMode = rawMode
            ?.let { runCatching { ControlMode.valueOf(it) }.getOrNull() }
            ?: ControlMode.HYBRID

        UserSettings(
            controlMode = parsedMode,
            hapticsEnabled = prefs[Keys.HAPTICS_ENABLED] ?: true,
            showControlHints = prefs[Keys.SHOW_CONTROL_HINTS] ?: true,
        )
    }

    override suspend fun setControlMode(mode: ControlMode) {
        dataStore.edit { prefs ->
            prefs[Keys.CONTROL_MODE] = mode.name
        }
    }

    override suspend fun setHapticsEnabled(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[Keys.HAPTICS_ENABLED] = enabled
        }
    }

    override suspend fun setShowControlHints(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[Keys.SHOW_CONTROL_HINTS] = enabled
        }
    }
}

class DataStoreStatsRepository(
    private val dataStore: DataStore<Preferences>,
) : StatsRepository {

    private class StatKeys(gameId: GameId, difficulty: Difficulty) {
        private val prefix = "stats_${gameId.value}_${difficulty.name.lowercase()}"
        val played = intPreferencesKey("${prefix}_played")
        val wins = intPreferencesKey("${prefix}_wins")
        val losses = intPreferencesKey("${prefix}_losses")
        val bestTimeMs = longPreferencesKey("${prefix}_best_time_ms")
    }

    override fun observeStats(gameId: GameId, difficulty: Difficulty): Flow<GameStats> {
        val keys = StatKeys(gameId, difficulty)
        return dataStore.data.map { prefs ->
            readStats(prefs, keys)
        }
    }

    override fun observeAllStatsForGame(gameId: GameId): Flow<Map<Difficulty, GameStats>> {
        val keysByDifficulty = Difficulty.entries.associateWith { StatKeys(gameId, it) }
        return dataStore.data.map { prefs ->
            keysByDifficulty.mapValues { (_, keys) -> readStats(prefs, keys) }
        }
    }

    override suspend fun recordGameResult(
        gameId: GameId,
        difficulty: Difficulty,
        won: Boolean,
        completionTimeMs: Long?,
    ) {
        val keys = StatKeys(gameId, difficulty)
        dataStore.edit { prefs ->
            val currentPlayed = prefs[keys.played] ?: 0
            val currentWins = prefs[keys.wins] ?: 0
            val currentLosses = prefs[keys.losses] ?: 0
            val currentBest = prefs[keys.bestTimeMs]

            prefs[keys.played] = currentPlayed + 1
            if (won) {
                prefs[keys.wins] = currentWins + 1
                if (completionTimeMs != null && completionTimeMs > 0L) {
                    val updatedBest = if (currentBest == null) {
                        completionTimeMs
                    } else {
                        minOf(currentBest, completionTimeMs)
                    }
                    prefs[keys.bestTimeMs] = updatedBest
                }
            } else {
                prefs[keys.losses] = currentLosses + 1
            }
        }
    }

    override suspend fun clearStats(gameId: GameId) {
        val allKeys = Difficulty.entries.map { StatKeys(gameId, it) }
        dataStore.edit { prefs ->
            for (keys in allKeys) {
                prefs.remove(keys.played)
                prefs.remove(keys.wins)
                prefs.remove(keys.losses)
                prefs.remove(keys.bestTimeMs)
            }
        }
    }

    private fun readStats(prefs: Preferences, keys: StatKeys): GameStats = GameStats(
        gamesPlayed = prefs[keys.played] ?: 0,
        wins = prefs[keys.wins] ?: 0,
        losses = prefs[keys.losses] ?: 0,
        bestCompletionTimeMs = prefs[keys.bestTimeMs],
    )
}

// ============================================================================
// 4. Pure-Kotlin In-Memory Implementations (For Unit Tests & Previews)
// ============================================================================

class InMemorySettingsRepository(
    initial: UserSettings = UserSettings(),
) : SettingsRepository {
    private val _settings = MutableStateFlow(initial)
    override val settings: Flow<UserSettings> = _settings.asStateFlow()

    override suspend fun setControlMode(mode: ControlMode) {
        _settings.update { it.copy(controlMode = mode) }
    }

    override suspend fun setHapticsEnabled(enabled: Boolean) {
        _settings.update { it.copy(hapticsEnabled = enabled) }
    }

    override suspend fun setShowControlHints(enabled: Boolean) {
        _settings.update { it.copy(showControlHints = enabled) }
    }
}

class InMemoryStatsRepository : StatsRepository {
    private val _records = MutableStateFlow<Map<Pair<GameId, Difficulty>, GameStats>>(emptyMap())

    override fun observeStats(gameId: GameId, difficulty: Difficulty): Flow<GameStats> =
        _records.map { it[gameId to difficulty] ?: GameStats() }

    override fun observeAllStatsForGame(gameId: GameId): Flow<Map<Difficulty, GameStats>> =
        _records.map { map ->
            Difficulty.entries.associateWith { difficulty ->
                map[gameId to difficulty] ?: GameStats()
            }
        }

    override suspend fun recordGameResult(
        gameId: GameId,
        difficulty: Difficulty,
        won: Boolean,
        completionTimeMs: Long?,
    ) {
        _records.update { current ->
            val key = gameId to difficulty
            val existing = current[key] ?: GameStats()
            val updated = if (won) {
                val nextBest = if (completionTimeMs != null && completionTimeMs > 0L) {
                    existing.bestCompletionTimeMs?.let { minOf(it, completionTimeMs) } ?: completionTimeMs
                } else {
                    existing.bestCompletionTimeMs
                }
                existing.copy(
                    gamesPlayed = existing.gamesPlayed + 1,
                    wins = existing.wins + 1,
                    bestCompletionTimeMs = nextBest,
                )
            } else {
                existing.copy(
                    gamesPlayed = existing.gamesPlayed + 1,
                    losses = existing.losses + 1,
                )
            }
            current + (key to updated)
        }
    }

    override suspend fun clearStats(gameId: GameId) {
        _records.update { current ->
            current.filterKeys { (id, _) -> id != gameId }
        }
    }
}

// ============================================================================
// 5. Storage Container Factory
// ============================================================================

class MiniGamesStorage(
    val settingsRepository: SettingsRepository,
    val statsRepository: StatsRepository,
) {
    companion object {
        fun create(context: Context): MiniGamesStorage {
            val store = context.applicationContext.miniGamesDataStore
            return MiniGamesStorage(
                settingsRepository = DataStoreSettingsRepository(store),
                statsRepository = DataStoreStatsRepository(store),
            )
        }
    }
}
