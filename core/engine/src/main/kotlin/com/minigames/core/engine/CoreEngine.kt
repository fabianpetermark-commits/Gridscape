package com.minigames.core.engine

import com.minigames.core.models.GamePhase
import java.util.concurrent.atomic.AtomicLong
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

interface GameEngine<S : GameState, C, Config> {
    fun createInitialState(config: Config, seed: Long = System.nanoTime()): S
    fun start(state: S): S
    fun pause(state: S): S
    fun resume(state: S): S
    fun onCommand(state: S, command: C): S
    fun onTick(state: S, deltaMillis: Long): S
}

private const val NO_TICK = -1L

/**
 * Lifecycle-aware session coordinator.
 *
 * KEY FIX: [lastTickMillis] is an [AtomicLong] read and written OUTSIDE [_state.update].
 * The update lambda is retriable on CAS conflict — mutating external state inside it
 * caused the classic "delta = 0 every tick" bug where onTick was never called with a
 * non-zero delta, so enemies never moved.
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

    private val lastTickMillis = AtomicLong(NO_TICK)

    fun reset(config: Config, seed: Long = System.nanoTime()) {
        lastTickMillis.set(NO_TICK)
        _state.value = engine.createInitialState(config, seed)
    }

    fun start() {
        _state.update { current ->
            if (current.phase == GamePhase.READY) {
                lastTickMillis.set(timeSource.nowMillis())
                engine.start(current)
            } else {
                current
            }
        }
    }

    fun pause() {
        _state.update { current ->
            if (current.phase == GamePhase.PLAYING) {
                lastTickMillis.set(NO_TICK)
                engine.pause(current)
            } else {
                current
            }
        }
    }

    fun resume() {
        _state.update { current ->
            if (current.phase == GamePhase.PAUSED) {
                lastTickMillis.set(timeSource.nowMillis())
                engine.resume(current)
            } else {
                current
            }
        }
    }

    fun onHostBackgrounded() { pause() }

    fun onHostForegrounded() { lastTickMillis.set(NO_TICK) }

    fun dispatchCommand(command: C) {
        _state.update { current ->
            if (current.phase == GamePhase.PLAYING) engine.onCommand(current, command) else current
        }
    }

    /**
     * Advances the simulation by one clamped time step.
     * [lastTickMillis] is read/written outside [_state.update] — the lambda never
     * mutates external state and can safely retry without corrupting the clock.
     */
    fun stepClock() {
        if (_state.value.phase != GamePhase.PLAYING) {
            lastTickMillis.set(NO_TICK)
            return
        }

        val now = timeSource.nowMillis()
        val previous = lastTickMillis.getAndSet(now)

        if (previous == NO_TICK) return  // first tick after start, skip to avoid huge delta

        val delta = (now - previous).coerceIn(0L, maxDeltaMillis)
        if (delta == 0L) return

        _state.update { s ->
            if (s.phase == GamePhase.PLAYING) engine.onTick(s, delta) else s
        }
    }

    suspend fun runLoop(stepCadenceMillis: Long = 16L) {
        while (coroutineContext.isActive && _state.value.phase == GamePhase.PLAYING) {
            stepClock()
            delay(stepCadenceMillis)
        }
    }
}
