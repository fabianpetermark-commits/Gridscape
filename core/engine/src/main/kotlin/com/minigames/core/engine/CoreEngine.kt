package com.minigames.core.engine

import com.minigames.core.models.GamePhase
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive

fun interface TimeSource {
    fun nowMillis(): Long
}

object MonotonicTimeSource : TimeSource {
    override fun nowMillis(): Long = System.nanoTime() / 1_000_000L
}

/**
 * Immutable discrete timer accumulator for scheduled game events (such as enemy steps).
 * Clamps single-tick execution to [maxStepsPerTick] to prevent multi-cell jumps on lag spikes.
 */
data class IntervalScheduler(
    val intervalMs: Long,
    val accumulatedMs: Long = 0L,
    val maxStepsPerTick: Int = 1,
) {
    init {
        require(intervalMs > 0L) { "intervalMs must be > 0, got $intervalMs" }
        require(maxStepsPerTick >= 1) { "maxStepsPerTick must be >= 1, got $maxStepsPerTick" }
    }

    data class AdvanceResult(
        val scheduler: IntervalScheduler,
        val stepsToExecute: Int,
    )

    fun advance(deltaMillis: Long): AdvanceResult {
        if (deltaMillis <= 0L) {
            return AdvanceResult(scheduler = this, stepsToExecute = 0)
        }

        val total = accumulatedMs + deltaMillis
        val rawSteps = (total / intervalMs).toInt()
        val executedSteps = rawSteps.coerceAtMost(maxStepsPerTick)
        val remainder = if (rawSteps > maxStepsPerTick) {
            0L
        } else {
            total % intervalMs
        }

        return AdvanceResult(
            scheduler = copy(accumulatedMs = remainder),
            stepsToExecute = executedSteps,
        )
    }

    fun reset(): IntervalScheduler = copy(accumulatedMs = 0L)
}

interface GameState {
    val phase: GamePhase
    val elapsedPlayTimeMs: Long
}

/**
 * Pure, deterministic state transition contract implemented by every game module.
 */
interface GameEngine<S : GameState, C, Config> {

    /** Creates a new session in [GamePhase.READY]. */
    fun createInitialState(config: Config, seed: Long = System.nanoTime()): S

    /** Transitions from [GamePhase.READY] to [GamePhase.PLAYING]. */
    fun start(state: S): S

    /** Transitions from [GamePhase.PLAYING] to [GamePhase.PAUSED]. */
    fun pause(state: S): S

    /** Transitions from [GamePhase.PAUSED] to [GamePhase.PLAYING]. */
    fun resume(state: S): S

    /** Applies a discrete player command (e.g., directional move). */
    fun onCommand(state: S, command: C): S

    /** Advances time-driven simulation (enemy AI ticks, timers, collision checks). */
    fun onTick(state: S, deltaMillis: Long): S
}

/**
 * Lifecycle-aware session coordinator that decouples logic ticks from Compose Canvas FPS.
 *
 * Host lifecycle rules:
 * - [onHostBackgrounded] transitions active play into [GamePhase.PAUSED] and clears the frame clock.
 * - [onHostForegrounded] resets the frame clock without advancing simulation time, keeping the game
 *   in [GamePhase.PAUSED] until the player explicitly calls [resume] so no catch-up spike occurs.
 */
class GameSession<S : GameState, C, Config>(
    private val engine: GameEngine<S, C, Config>,
    initialConfig: Config,
    initialSeed: Long = System.nanoTime(),
    private val timeSource: TimeSource = MonotonicTimeSource,
    private val maxDeltaMillis: Long = 100L,
) {
    private val _state = MutableStateFlow(engine.createInitialState(initialConfig, initialSeed))
    val state: StateFlow<S> = _state.asStateFlow()

    private var lastTickMillis: Long? = null

    fun reset(config: Config, seed: Long = System.nanoTime()) {
        lastTickMillis = null
        _state.value = engine.createInitialState(config, seed)
    }

    fun start() {
        _state.update { current ->
            if (current.phase == GamePhase.READY) {
                lastTickMillis = timeSource.nowMillis()
                engine.start(current)
            } else {
                current
            }
        }
    }

    fun pause() {
        _state.update { current ->
            if (current.phase == GamePhase.PLAYING) {
                lastTickMillis = null
                engine.pause(current)
            } else {
                current
            }
        }
    }

    fun resume() {
        _state.update { current ->
            if (current.phase == GamePhase.PAUSED) {
                lastTickMillis = timeSource.nowMillis()
                engine.resume(current)
            } else {
                current
            }
        }
    }

    fun onHostBackgrounded() {
        pause()
    }

    fun onHostForegrounded() {
        lastTickMillis = null
    }

    fun dispatchCommand(command: C) {
        _state.update { current ->
            if (current.phase == GamePhase.PLAYING) {
                engine.onCommand(current, command)
            } else {
                current
            }
        }
    }

    /**
     * Advances the simulation by a single clamped time step.
     * Useful both inside [runLoop] and in deterministic unit tests.
     */
    fun stepClock() {
        val now = timeSource.nowMillis()
        _state.update { current ->
            if (current.phase != GamePhase.PLAYING) {
                lastTickMillis = null
                return@update current
            }

            val previous = lastTickMillis
            lastTickMillis = now
            if (previous == null) {
                return@update current
            }

            val delta = (now - previous).coerceIn(0L, maxDeltaMillis)
            if (delta == 0L) current else engine.onTick(current, delta)
        }
    }

    /**
     * Runs the fixed-cadence logic loop while the caller's coroutine scope is active.
     * Pair with Jetpack Compose `LaunchedEffect(session, phase)` or `repeatOnLifecycle`.
     */
    suspend fun runLoop(stepCadenceMillis: Long = 16L) {
        while (coroutineContext.isActive && _state.value.phase == GamePhase.PLAYING) {
            stepClock()
            delay(stepCadenceMillis)
        }
    }
}
