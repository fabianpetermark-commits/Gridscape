package com.minigames.games.gridhunter.logic

import com.minigames.core.engine.GameEngine
import com.minigames.core.engine.GameState
import com.minigames.core.engine.IntervalScheduler
import com.minigames.core.models.Difficulty
import com.minigames.core.models.Direction
import com.minigames.core.models.GameDescriptor
import com.minigames.core.models.GameId
import com.minigames.core.models.GamePhase
import com.minigames.core.models.GridBounds
import com.minigames.core.models.GridPoint
import kotlin.random.Random

// ============================================================================
// 1. Metadata, Configuration & Commands
// ============================================================================

object GridHunterMeta {
    val ID = GameId("grid-hunter")

    val DESCRIPTOR = GameDescriptor(
        id = ID,
        title = "Grid Hunter",
        subtitle = "Outsmart neon hunters on a 6×6 grid",
        supportedDifficulties = Difficulty.entries,
    )
}

enum class EnemyRole {
    PURSUER,
    INTERCEPTOR,
}

data class GridHunterConfig(
    val difficulty: Difficulty,
    val bounds: GridBounds = GridBounds(width = 6, height = 6),
    val pursuerIntervalMs: Long = 750L,
    val interceptorIntervalMs: Long = 750L,
    val playerMoveCooldownMs: Long = 80L,
    val obstacleCountRange: IntRange = 0..0,
) {
    init {
        require(pursuerIntervalMs > 0L) { "pursuerIntervalMs must be > 0" }
        require(interceptorIntervalMs > 0L) { "interceptorIntervalMs must be > 0" }
        require(playerMoveCooldownMs >= 0L) { "playerMoveCooldownMs must be >= 0" }
    }

    companion object {
        fun forDifficulty(difficulty: Difficulty): GridHunterConfig = when (difficulty) {
            Difficulty.EASY -> GridHunterConfig(
                difficulty = Difficulty.EASY,
                pursuerIntervalMs = 750L,
                interceptorIntervalMs = 750L,
                obstacleCountRange = 0..0,
            )

            Difficulty.MEDIUM -> GridHunterConfig(
                difficulty = Difficulty.MEDIUM,
                pursuerIntervalMs = 350L,
                interceptorIntervalMs = 350L,
                obstacleCountRange = 0..0,
            )

            Difficulty.HARD -> GridHunterConfig(
                difficulty = Difficulty.HARD,
                pursuerIntervalMs = 500L,
                interceptorIntervalMs = 500L,
                obstacleCountRange = 0..0,
            )

            Difficulty.INSANE -> GridHunterConfig(
                difficulty = Difficulty.INSANE,
                pursuerIntervalMs = 450L,
                interceptorIntervalMs = 500L,
                obstacleCountRange = 5..8,
            )
        }
    }
}

sealed interface GridHunterCommand {
    data class Move(val direction: Direction) : GridHunterCommand
    data object ClearBufferedMove : GridHunterCommand
}

// ============================================================================
// 2. Entities & Immutable Game State
// ============================================================================

data class PlayerEntity(
    val position: GridPoint,
    val previousPosition: GridPoint = position,
    val lastMoveDirection: Direction? = null,
)

data class EnemyEntity(
    val id: Int,
    val role: EnemyRole,
    val position: GridPoint,
    val previousPosition: GridPoint = position,
    val scheduler: IntervalScheduler,
)

data class GridHunterState(
    val config: GridHunterConfig,
    override val phase: GamePhase,
    override val elapsedPlayTimeMs: Long = 0L,
    val player: PlayerEntity,
    val enemies: List<EnemyEntity>,
    val exitPosition: GridPoint,
    val obstacles: Set<GridPoint> = emptySet(),
    val moveCount: Int = 0,
    val playerCooldownRemainingMs: Long = 0L,
    val bufferedDirection: Direction? = null,
    val seed: Long = 0L,
) : GameState {
    val bounds: GridBounds
        get() = config.bounds
}

// ============================================================================
// 3. Deterministic BFS Pathfinder
// ============================================================================

object GridPathfinder {

    fun findShortestPath(
        bounds: GridBounds,
        start: GridPoint,
        goal: GridPoint,
        blockedCells: Set<GridPoint> = emptySet(),
    ): List<GridPoint> {
        if (!bounds.contains(start) || !bounds.contains(goal)) return emptyList()
        if (start in blockedCells || goal in blockedCells) return emptyList()
        if (start == goal) return listOf(start)

        val queue = ArrayDeque<GridPoint>()
        val parentByPoint = HashMap<GridPoint, GridPoint?>(bounds.cellCount)

        queue.addLast(start)
        parentByPoint[start] = null

        while (queue.isNotEmpty()) {
            val current = queue.removeFirst()
            if (current == goal) {
                return reconstructPath(parentByPoint, goal)
            }

            for (next in bounds.validNeighbors(current)) {
                if (next !in blockedCells && next !in parentByPoint) {
                    parentByPoint[next] = current
                    queue.addLast(next)
                }
            }
        }

        return emptyList()
    }

    fun nextStep(
        bounds: GridBounds,
        start: GridPoint,
        goal: GridPoint,
        blockedCells: Set<GridPoint> = emptySet(),
    ): GridPoint? {
        val path = findShortestPath(bounds, start, goal, blockedCells)
        return if (path.size >= 2) path[1] else null
    }

    fun reachableCells(
        bounds: GridBounds,
        start: GridPoint,
        blockedCells: Set<GridPoint> = emptySet(),
    ): Set<GridPoint> {
        if (!bounds.contains(start) || start in blockedCells) return emptySet()

        val visited = LinkedHashSet<GridPoint>(bounds.cellCount)
        val queue = ArrayDeque<GridPoint>()

        visited.add(start)
        queue.addLast(start)

        while (queue.isNotEmpty()) {
            val current = queue.removeFirst()
            for (next in bounds.validNeighbors(current)) {
                if (next !in blockedCells && visited.add(next)) {
                    queue.addLast(next)
                }
            }
        }

        return visited
    }

    private fun reconstructPath(
        parentByPoint: Map<GridPoint, GridPoint?>,
        goal: GridPoint,
    ): List<GridPoint> {
        val path = ArrayList<GridPoint>()
        var cursor: GridPoint? = goal
        while (cursor != null) {
            path.add(cursor)
            cursor = parentByPoint[cursor]
        }
        path.reverse()
        return path
    }
}

// ============================================================================
// 4. Difficulty-Aware AI (Pursuer, Interceptor, Flanking & Dynamic Roles)
// ============================================================================

object GridHunterAi {

    fun chooseNextPosition(
        state: GridHunterState,
        enemy: EnemyEntity,
    ): GridPoint {
        val bounds = state.bounds
        val otherEnemyPositions = state.enemies
            .asSequence()
            .filter { it.id != enemy.id }
            .map { it.position }
            .toSet()

        val blocked = state.obstacles + otherEnemyPositions
        val effectiveRole = resolveEffectiveRole(state, enemy)

        val targetCell = when (effectiveRole) {
            EnemyRole.PURSUER -> selectPursuerTarget(state, enemy, blocked)
            EnemyRole.INTERCEPTOR -> selectInterceptorTarget(state, enemy, blocked, otherEnemyPositions)
        }

        val step = GridPathfinder.nextStep(
            bounds = bounds,
            start = enemy.position,
            goal = targetCell,
            blockedCells = blocked,
        ) ?: GridPathfinder.nextStep(
            bounds = bounds,
            start = enemy.position,
            goal = state.player.position,
            blockedCells = blocked,
        )

        if (step != null) return step

        return bounds.validNeighbors(enemy.position)
            .filter { it !in blocked }
            .minByOrNull { it.manhattanDistanceTo(state.player.position) }
            ?: enemy.position
    }

    private fun resolveEffectiveRole(
        state: GridHunterState,
        enemy: EnemyEntity,
    ): EnemyRole {
        if (state.config.difficulty != Difficulty.INSANE || state.enemies.size < 2) {
            return enemy.role
        }

        val other = state.enemies.firstOrNull { it.id != enemy.id } ?: return enemy.role
        val myDist = enemy.position.manhattanDistanceTo(state.player.position)
        val otherDist = other.position.manhattanDistanceTo(state.player.position)

        return if (myDist < otherDist && myDist <= 3) {
            EnemyRole.PURSUER
        } else if (otherDist < myDist && otherDist <= 3) {
            EnemyRole.INTERCEPTOR
        } else {
            enemy.role
        }
    }

    private fun selectPursuerTarget(
        state: GridHunterState,
        enemy: EnemyEntity,
        blocked: Set<GridPoint>,
    ): GridPoint {
        val playerPos = state.player.position
        if (state.config.difficulty == Difficulty.HARD || state.config.difficulty == Difficulty.INSANE) {
            val flankingSpot = state.bounds.validNeighbors(playerPos)
                .filter { it !in blocked && it != enemy.position }
                .minByOrNull { it.manhattanDistanceTo(enemy.position) }

            if (flankingSpot != null && enemy.position.manhattanDistanceTo(playerPos) in 2..3) {
                return flankingSpot
            }
        }
        return playerPos
    }

    private fun selectInterceptorTarget(
        state: GridHunterState,
        enemy: EnemyEntity,
        blocked: Set<GridPoint>,
        otherEnemyPositions: Set<GridPoint>,
    ): GridPoint {
        val playerPos = state.player.position
        val distToPlayer = enemy.position.manhattanDistanceTo(playerPos)

        if (distToPlayer <= 2) {
            return playerPos
        }

        val escapePath = GridPathfinder.findShortestPath(
            bounds = state.bounds,
            start = playerPos,
            goal = state.exitPosition,
            blockedCells = state.obstacles,
        )

        if (escapePath.size >= 3) {
            val leadIndex = (escapePath.size / 2).coerceIn(1, escapePath.size - 2)
            val chokePoint = escapePath[leadIndex]
            if (chokePoint !in blocked && chokePoint !in otherEnemyPositions) {
                return chokePoint
            }
        }

        val dir = state.player.lastMoveDirection
        if (dir != null) {
            val twoAhead = playerPos.step(dir).step(dir)
            if (state.bounds.contains(twoAhead) && twoAhead !in blocked) {
                return twoAhead
            }
            val oneAhead = playerPos.step(dir)
            if (state.bounds.contains(oneAhead) && oneAhead !in blocked) {
                return oneAhead
            }
        }

        return playerPos
    }
}

// ============================================================================
// 5. Randomized & Fairness-Validated Level Generator
// ============================================================================

object GridHunterLevelGenerator {

    private const val MAX_GENERATION_ATTEMPTS = 250
    private const val MIN_PLAYER_EXIT_DISTANCE = 4
    private const val MIN_PLAYER_ENEMY_DISTANCE = 4

    fun generate(
        config: GridHunterConfig,
        seed: Long = System.nanoTime(),
    ): GridHunterState {
        val random = Random(seed)
        val allPoints = config.bounds.allPoints()

        repeat(MAX_GENERATION_ATTEMPTS) {
            val candidate = tryGenerateCandidate(config, allPoints, random, seed)
            if (candidate != null) {
                return candidate
            }
        }

        return createDeterministicFallback(config, seed)
    }

    private fun tryGenerateCandidate(
        config: GridHunterConfig,
        allPoints: List<GridPoint>,
        random: Random,
        seed: Long,
    ): GridHunterState? {
        val bounds = config.bounds
        val shuffled = allPoints.shuffled(random)

        val playerPos = shuffled.first()

        val exitPos = shuffled.firstOrNull { candidate ->
            candidate != playerPos &&
                candidate.manhattanDistanceTo(playerPos) >= MIN_PLAYER_EXIT_DISTANCE
        } ?: return null

        val enemySpecs = buildEnemyRoles(config.difficulty)
        val chosenEnemyPositions = ArrayList<GridPoint>(enemySpecs.size)

        for (i in enemySpecs.indices) {
            val enemyPos = shuffled.firstOrNull { candidate ->
                candidate != playerPos &&
                    candidate != exitPos &&
                    candidate !in chosenEnemyPositions &&
                    candidate.manhattanDistanceTo(playerPos) >= MIN_PLAYER_ENEMY_DISTANCE
            } ?: return null
            chosenEnemyPositions.add(enemyPos)
        }

        val reserved = buildSet {
            add(playerPos)
            add(exitPos)
            addAll(chosenEnemyPositions)
        }

        val targetObstacleCount = if (config.obstacleCountRange.last > 0) {
            config.obstacleCountRange.random(random)
        } else {
            0
        }

        val obstacles = if (targetObstacleCount > 0) {
            shuffled.asSequence()
                .filter { it !in reserved }
                .take(targetObstacleCount)
                .toSet()
        } else {
            emptySet()
        }

        if (!isBoardValidAndFair(bounds, playerPos, exitPos, chosenEnemyPositions, obstacles)) {
            return null
        }

        val enemies = enemySpecs.mapIndexed { index, role ->
            val interval = when (role) {
                EnemyRole.PURSUER -> config.pursuerIntervalMs
                EnemyRole.INTERCEPTOR -> config.interceptorIntervalMs
            }
            val pos = chosenEnemyPositions[index]
            EnemyEntity(
                id = index + 1,
                role = role,
                position = pos,
                previousPosition = pos,
                scheduler = IntervalScheduler(intervalMs = interval),
            )
        }

        return GridHunterState(
            config = config,
            phase = GamePhase.READY,
            elapsedPlayTimeMs = 0L,
            player = PlayerEntity(position = playerPos, previousPosition = playerPos),
            enemies = enemies,
            exitPosition = exitPos,
            obstacles = obstacles,
            seed = seed,
        )
    }

    internal fun isBoardValidAndFair(
        bounds: GridBounds,
        playerPos: GridPoint,
        exitPos: GridPoint,
        enemyPositions: List<GridPoint>,
        obstacles: Set<GridPoint>,
    ): Boolean {
        val openNeighbors = bounds.validNeighbors(playerPos).count { it !in obstacles }
        if (openNeighbors < 2) return false

        val reachableFromPlayer = GridPathfinder.reachableCells(
            bounds = bounds,
            start = playerPos,
            blockedCells = obstacles,
        )

        if (exitPos !in reachableFromPlayer) return false
        if (enemyPositions.any { it !in reachableFromPlayer }) return false

        return true
    }

    private fun buildEnemyRoles(difficulty: Difficulty): List<EnemyRole> = when (difficulty) {
        Difficulty.EASY -> listOf(EnemyRole.PURSUER)
        Difficulty.MEDIUM -> listOf(EnemyRole.INTERCEPTOR)
        Difficulty.HARD, Difficulty.INSANE -> listOf(EnemyRole.PURSUER, EnemyRole.INTERCEPTOR)
    }

    private fun createDeterministicFallback(
        config: GridHunterConfig,
        seed: Long,
    ): GridHunterState {
        val w = config.bounds.width
        val h = config.bounds.height
        val playerPos = GridPoint(0, 0)
        val exitPos = GridPoint(w - 1, h - 1)

        val roles = buildEnemyRoles(config.difficulty)
        val fallbackPositions = listOf(
            GridPoint(w - 1, 0),
            GridPoint(0, h - 1),
        )

        val enemies = roles.mapIndexed { index, role ->
            val pos = fallbackPositions[index % fallbackPositions.size]
            val interval = when (role) {
                EnemyRole.PURSUER -> config.pursuerIntervalMs
                EnemyRole.INTERCEPTOR -> config.interceptorIntervalMs
            }
            EnemyEntity(
                id = index + 1,
                role = role,
                position = pos,
                previousPosition = pos,
                scheduler = IntervalScheduler(intervalMs = interval),
            )
        }

        return GridHunterState(
            config = config,
            phase = GamePhase.READY,
            player = PlayerEntity(position = playerPos, previousPosition = playerPos),
            enemies = enemies,
            exitPosition = exitPos,
            obstacles = emptySet(),
            seed = seed,
        )
    }
}

// ============================================================================
// 6. Grid Hunter Deterministic GameEngine Implementation
// ============================================================================

class GridHunterEngine : GameEngine<GridHunterState, GridHunterCommand, GridHunterConfig> {

    override fun createInitialState(
        config: GridHunterConfig,
        seed: Long,
    ): GridHunterState = GridHunterLevelGenerator.generate(config = config, seed = seed)

    override fun start(state: GridHunterState): GridHunterState =
        if (state.phase == GamePhase.READY) {
            state.copy(phase = GamePhase.PLAYING)
        } else {
            state
        }

    override fun pause(state: GridHunterState): GridHunterState =
        if (state.phase == GamePhase.PLAYING) {
            state.copy(phase = GamePhase.PAUSED)
        } else {
            state
        }

    override fun resume(state: GridHunterState): GridHunterState =
        if (state.phase == GamePhase.PAUSED) {
            state.copy(phase = GamePhase.PLAYING)
        } else {
            state
        }

    override fun onCommand(
        state: GridHunterState,
        command: GridHunterCommand,
    ): GridHunterState {
        if (state.phase != GamePhase.PLAYING) return state

        return when (command) {
            GridHunterCommand.ClearBufferedMove -> state.copy(bufferedDirection = null)

            is GridHunterCommand.Move -> {
                if (state.playerCooldownRemainingMs > 0L) {
                    state.copy(bufferedDirection = command.direction)
                } else {
                    executePlayerStep(
                        state = state.copy(bufferedDirection = null),
                        direction = command.direction,
                        playerMovedThisTick = false,
                    )
                }
            }
        }
    }

    override fun onTick(
        state: GridHunterState,
        deltaMillis: Long,
    ): GridHunterState {
        if (state.phase != GamePhase.PLAYING || deltaMillis <= 0L) return state

        var current = state.copy(
            elapsedPlayTimeMs = state.elapsedPlayTimeMs + deltaMillis,
        )
        var playerMovedThisTick = false

        if (current.playerCooldownRemainingMs > 0L) {
            val remaining = (current.playerCooldownRemainingMs - deltaMillis).coerceAtLeast(0L)
            current = current.copy(playerCooldownRemainingMs = remaining)

            if (remaining == 0L && current.bufferedDirection != null) {
                val nextDir = current.bufferedDirection
                current = executePlayerStep(
                    state = current.copy(bufferedDirection = null),
                    direction = nextDir,
                    playerMovedThisTick = false,
                )
                playerMovedThisTick = true
                if (current.phase != GamePhase.PLAYING) {
                    return current
                }
            }
        }

        var enemies = current.enemies
        for (index in enemies.indices) {
            val enemy = enemies[index]
            val advanceResult = enemy.scheduler.advance(deltaMillis)

            if (advanceResult.stepsToExecute <= 0) {
                enemies = enemies.replaceAt(index, enemy.copy(scheduler = advanceResult.scheduler))
                continue
            }

            val stateViewForAi = current.copy(enemies = enemies)
            val nextPos = GridHunterAi.chooseNextPosition(stateViewForAi, enemy)
            val movedEnemy = enemy.copy(
                previousPosition = enemy.position,
                position = nextPos,
                scheduler = advanceResult.scheduler,
            )
            enemies = enemies.replaceAt(index, movedEnemy)
            current = current.copy(enemies = enemies)

            if (hasPlayerEnemyCollision(current.player, movedEnemy, playerMovedThisTick)) {
                return current.copy(phase = GamePhase.GAME_OVER)
            }
        }

        if (isPlayerOnUnoccupiedExit(current)) {
            return current.copy(phase = GamePhase.VICTORY)
        }

        return current
    }

    private fun executePlayerStep(
        state: GridHunterState,
        direction: Direction,
        playerMovedThisTick: Boolean,
    ): GridHunterState {
        val candidatePos = state.player.position.step(direction)
        val isBlocked = !state.bounds.contains(candidatePos) || candidatePos in state.obstacles

        val nextPos = if (isBlocked) state.player.position else candidatePos
        val moved = nextPos != state.player.position

        val updatedPlayer = state.player.copy(
            previousPosition = state.player.position,
            position = nextPos,
            lastMoveDirection = direction,
        )

        val updatedState = state.copy(
            player = updatedPlayer,
            moveCount = if (moved) state.moveCount + 1 else state.moveCount,
            playerCooldownRemainingMs = if (moved) state.config.playerMoveCooldownMs else 0L,
        )

        val collided = updatedState.enemies.any { enemy ->
            hasPlayerEnemyCollision(
                player = updatedPlayer,
                enemy = enemy,
                simultaneousUpdate = playerMovedThisTick,
            )
        }
        if (collided) {
            return updatedState.copy(phase = GamePhase.GAME_OVER)
        }

        if (isPlayerOnUnoccupiedExit(updatedState)) {
            return updatedState.copy(phase = GamePhase.VICTORY)
        }

        return updatedState
    }

    private fun hasPlayerEnemyCollision(
        player: PlayerEntity,
        enemy: EnemyEntity,
        simultaneousUpdate: Boolean,
    ): Boolean {
        if (player.position == enemy.position) return true

        if (simultaneousUpdate &&
            player.previousPosition == enemy.position &&
            player.position == enemy.previousPosition
        ) {
            return true
        }

        return false
    }

    private fun isPlayerOnUnoccupiedExit(state: GridHunterState): Boolean =
        state.player.position == state.exitPosition &&
            state.enemies.none { it.position == state.exitPosition }

    private fun <T> List<T>.replaceAt(index: Int, item: T): List<T> =
        mapIndexed { i, existing -> if (i == index) item else existing }
}
