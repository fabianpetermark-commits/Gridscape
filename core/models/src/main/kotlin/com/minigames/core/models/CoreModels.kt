package com.minigames.core.models

import kotlin.math.abs

@JvmInline
value class GameId(val value: String) {
    init {
        require(value.isNotBlank()) { "GameId must not be blank." }
    }
}

enum class Difficulty {
    EASY,
    MEDIUM,
    HARD,
    INSANE,
}

enum class GamePhase {
    MAIN_MENU,
    DIFFICULTY_SELECT,
    READY,
    PLAYING,
    PAUSED,
    VICTORY,
    GAME_OVER,
}

val GamePhase.isTerminal: Boolean
    get() = this == GamePhase.VICTORY || this == GamePhase.GAME_OVER

enum class Direction(val dx: Int, val dy: Int) {
    UP(0, -1),
    DOWN(0, 1),
    LEFT(-1, 0),
    RIGHT(1, 0);

    fun opposite(): Direction = when (this) {
        UP -> DOWN
        DOWN -> UP
        LEFT -> RIGHT
        RIGHT -> LEFT
    }
}

data class GridPoint(val x: Int, val y: Int) {

    fun step(direction: Direction): GridPoint =
        GridPoint(x = x + direction.dx, y = y + direction.dy)

    fun manhattanDistanceTo(other: GridPoint): Int =
        abs(x - other.x) + abs(y - other.y)

    fun isOrthogonallyAdjacentTo(other: GridPoint): Boolean =
        manhattanDistanceTo(other) == 1

    fun cardinalNeighbors(): List<GridPoint> =
        Direction.entries.map(::step)
}

data class GridBounds(val width: Int, val height: Int) {
    init {
        require(width > 0 && height > 0) {
            "GridBounds must have positive dimensions, got ${width}x$height"
        }
    }

    val cellCount: Int
        get() = width * height

    fun contains(point: GridPoint): Boolean =
        point.x in 0 until width && point.y in 0 until height

    fun validNeighbors(point: GridPoint): List<GridPoint> =
        point.cardinalNeighbors().filter(::contains)

    fun allPoints(): List<GridPoint> = buildList(cellCount) {
        for (y in 0 until height) {
            for (x in 0 until width) {
                add(GridPoint(x, y))
            }
        }
    }
}

data class GameStats(
    val gamesPlayed: Int = 0,
    val wins: Int = 0,
    val losses: Int = 0,
    val bestCompletionTimeMs: Long? = null,
)

/**
 * Metadata contract registered by each game module so the launcher shell
 * can discover and launch games without depending on game internals.
 */
data class GameDescriptor(
    val id: GameId,
    val title: String,
    val subtitle: String,
    val supportedDifficulties: List<Difficulty> = Difficulty.entries,
)
