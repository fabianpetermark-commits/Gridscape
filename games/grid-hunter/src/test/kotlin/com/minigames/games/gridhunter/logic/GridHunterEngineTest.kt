package com.minigames.games.gridhunter.logic

import com.minigames.core.models.Difficulty
import com.minigames.core.models.Direction
import com.minigames.core.models.GamePhase
import com.minigames.core.models.GridBounds
import com.minigames.core.models.GridPoint
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource

class GridHunterEngineTest {

    private val engine = GridHunterEngine()

    @ParameterizedTest
    @EnumSource(Difficulty::class)
    fun `generated levels satisfy fairness and reachability invariants across seeds`(
        difficulty: Difficulty,
    ) {
        val config = GridHunterConfig.forDifficulty(difficulty)

        for (seed in 1L..50L) {
            val state = engine.createInitialState(config, seed = seed)

            assertEquals(GamePhase.READY, state.phase)
            assertEquals(8, state.bounds.width)
            assertEquals(8, state.bounds.height)

            // Player, exit, and enemies must be distinct and within bounds
            assertTrue(state.bounds.contains(state.player.position))
            assertTrue(state.bounds.contains(state.exitPosition))
            assertNotEquals(state.player.position, state.exitPosition)
            assertTrue(state.player.position.manhattanDistanceTo(state.exitPosition) >= 5)

            for (enemy in state.enemies) {
                assertTrue(state.bounds.contains(enemy.position))
                assertNotEquals(state.player.position, enemy.position)
                assertNotEquals(state.exitPosition, enemy.position)
                assertTrue(state.player.position.manhattanDistanceTo(enemy.position) >= 5)
            }

            // Obstacles must match difficulty rules and never overlap entities
            if (difficulty == Difficulty.INSANE) {
                assertTrue(state.obstacles.size in 5..10)
            } else {
                assertTrue(state.obstacles.isEmpty())
            }
            assertFalse(state.player.position in state.obstacles)
            assertFalse(state.exitPosition in state.obstacles)
            assertTrue(state.enemies.none { it.position in state.obstacles })

            // Player must have at least 2 open adjacent cells and a valid path to exit
            val openNeighbors = state.bounds.validNeighbors(state.player.position)
                .count { it !in state.obstacles }
            assertTrue(openNeighbors >= 2)

            val pathToExit = GridPathfinder.findShortestPath(
                bounds = state.bounds,
                start = state.player.position,
                goal = state.exitPosition,
                blockedCells = state.obstacles,
            )
            assertTrue(pathToExit.isNotEmpty())
        }
    }

    @Test
    fun `player movement respects grid boundaries and obstacles`() {
        val baseState = GridHunterState(
            config = GridHunterConfig.forDifficulty(Difficulty.EASY),
            phase = GamePhase.PLAYING,
            player = PlayerEntity(position = GridPoint(0, 0)),
            enemies = emptyList(),
            exitPosition = GridPoint(7, 7),
            obstacles = setOf(GridPoint(1, 0)),
        )

        // Moving LEFT out of bounds keeps player at (0, 0) without incrementing moveCount
        val afterOutOfBounds = engine.onCommand(baseState, GridHunterCommand.Move(Direction.LEFT))
        assertEquals(GridPoint(0, 0), afterOutOfBounds.player.position)
        assertEquals(0, afterOutOfBounds.moveCount)

        // Moving RIGHT into obstacle at (1, 0) is blocked
        val afterObstacle = engine.onCommand(baseState, GridHunterCommand.Move(Direction.RIGHT))
        assertEquals(GridPoint(0, 0), afterObstacle.player.position)
        assertEquals(0, afterObstacle.moveCount)

        // Moving DOWN to open cell (0, 1) succeeds
        val afterValidMove = engine.onCommand(baseState, GridHunterCommand.Move(Direction.DOWN))
        assertEquals(GridPoint(0, 1), afterValidMove.player.position)
        assertEquals(1, afterValidMove.moveCount)
    }

    @Test
    fun `reaching unoccupied exit starts next round with exitsReached incremented`() {
        val state = GridHunterState(
            config = GridHunterConfig.forDifficulty(Difficulty.EASY),
            phase = GamePhase.PLAYING,
            player = PlayerEntity(position = GridPoint(6, 7)),
            enemies = emptyList(),
            exitPosition = GridPoint(7, 7),
        )

        val result = engine.onCommand(state, GridHunterCommand.Move(Direction.RIGHT))
        // Exit reached: continuous play — no VICTORY, new round spawned in PLAYING
        assertEquals(GamePhase.PLAYING, result.phase)
        assertEquals(1, result.exitsReached)
        // New round: player position is reset (not on old exit)
        assertNotEquals(GridPoint(7, 7), result.player.position)
    }

    @Test
    fun `enemy stepping onto player transitions to GAME_OVER`() {
        val config = GridHunterConfig.forDifficulty(Difficulty.EASY)
        val initial = engine.createInitialState(config, seed = 42L)
        val scenario = initial.copy(
            phase = GamePhase.PLAYING,
            player = PlayerEntity(position = GridPoint(2, 2)),
            enemies = listOf(
                initial.enemies.first().copy(
                    role = EnemyRole.PURSUER,
                    position = GridPoint(2, 1),
                    previousPosition = GridPoint(2, 1),
                ),
            ),
            exitPosition = GridPoint(7, 7),
            obstacles = emptySet(),
        )

        val afterTick = engine.onTick(scenario, deltaMillis = config.pursuerIntervalMs)
        assertEquals(GridPoint(2, 2), afterTick.enemies.first().position)
        assertEquals(GamePhase.GAME_OVER, afterTick.phase)
    }

    @Test
    fun `pathfinder routes around walls cleanly`() {
        val bounds = GridBounds(4, 4)
        val obstacles = setOf(
            GridPoint(1, 0),
            GridPoint(1, 1),
            GridPoint(1, 2),
        )

        val path = GridPathfinder.findShortestPath(
            bounds = bounds,
            start = GridPoint(0, 0),
            goal = GridPoint(2, 0),
            blockedCells = obstacles,
        )

        assertEquals(GridPoint(0, 0), path.first())
        assertEquals(GridPoint(2, 0), path.last())
        assertTrue(path.none { it in obstacles })
        // Must go down to row 3 around the wall
        assertEquals(9, path.size)
    }
}
