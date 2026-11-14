package com.minigames.core.input

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.minigames.core.models.Direction
import kotlin.math.abs
import kotlin.math.hypot

enum class ControlMode {
    DPAD,
    SWIPE,
    HYBRID,
}

/**
 * Bounded single-slot input buffer (maximum capacity = 1).
 *
 * When a new command arrives before the previous buffered command is consumed,
 * the buffer replaces the stale command with the latest player input.
 */
class SingleSlotCommandBuffer<T> {
    private var bufferedCommand: T? = null

    @Synchronized
    fun offer(command: T) {
        bufferedCommand = command
    }

    @Synchronized
    fun poll(): T? {
        val next = bufferedCommand
        bufferedCommand = null
        return next
    }

    @Synchronized
    fun peek(): T? = bufferedCommand

    @Synchronized
    fun hasPending(): Boolean = bufferedCommand != null

    @Synchronized
    fun clear() {
        bufferedCommand = null
    }
}

/**
 * Pure-Kotlin gesture vector resolver that maps drag deltas to strictly
 * four-directional cardinal commands once [thresholdPx] is exceeded.
 */
object SwipeDirectionResolver {

    fun resolve(dx: Float, dy: Float, thresholdPx: Float): Direction? {
        if (hypot(dx, dy) < thresholdPx) return null

        return if (abs(dx) >= abs(dy)) {
            if (dx >= 0f) Direction.RIGHT else Direction.LEFT
        } else {
            if (dy >= 0f) Direction.DOWN else Direction.UP
        }
    }
}

/**
 * Detects directional swipes on any Compose surface (such as the game board)
 * and emits cardinal [Direction] commands with low latency.
 *
 * When [allowContinuousStepping] is false, each finger drag triggers at most one
 * cardinal move until the finger is lifted, preventing accidental double-steps.
 */
fun Modifier.cardinalSwipeInput(
    enabled: Boolean = true,
    threshold: Dp = 28.dp,
    allowContinuousStepping: Boolean = false,
    onDirection: (Direction) -> Unit,
): Modifier {
    if (!enabled) return this

    return this.pointerInput(enabled, threshold, allowContinuousStepping) {
        val thresholdPx = threshold.toPx()
        var accumulatedX = 0f
        var accumulatedY = 0f
        var gestureConsumed = false

        detectDragGestures(
            onDragStart = {
                accumulatedX = 0f
                accumulatedY = 0f
                gestureConsumed = false
            },
            onDragEnd = {
                accumulatedX = 0f
                accumulatedY = 0f
                gestureConsumed = false
            },
            onDragCancel = {
                accumulatedX = 0f
                accumulatedY = 0f
                gestureConsumed = false
            },
            onDrag = { change, dragAmount ->
                change.consume()
                if (gestureConsumed && !allowContinuousStepping) return@detectDragGestures

                accumulatedX += dragAmount.x
                accumulatedY += dragAmount.y

                val direction = SwipeDirectionResolver.resolve(
                    dx = accumulatedX,
                    dy = accumulatedY,
                    thresholdPx = thresholdPx,
                )

                if (direction != null) {
                    onDirection(direction)
                    accumulatedX = 0f
                    accumulatedY = 0f
                    gestureConsumed = true
                }
            },
        )
    }
}
