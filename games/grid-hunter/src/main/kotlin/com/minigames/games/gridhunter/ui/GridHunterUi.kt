package com.minigames.games.gridhunter.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.graphics.StrokeCap
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.minigames.core.engine.GameSession
import com.minigames.core.input.ControlMode
import com.minigames.core.input.cardinalSwipeInput
import com.minigames.core.models.Difficulty
import com.minigames.core.models.Direction
import com.minigames.core.models.GamePhase
import com.minigames.core.models.GridPoint
import com.minigames.core.rendering.LocalMiniGamesColors
import com.minigames.core.rendering.calculateGridViewport
import com.minigames.core.rendering.drawNeonEntitySquare
import com.minigames.core.rendering.drawNeonGridBoard
import com.minigames.core.rendering.drawNeonObstacleCell
import com.minigames.core.rendering.rememberAnimatedGridPoint
import com.minigames.core.rendering.rememberNeonPulse
import com.minigames.games.gridhunter.logic.EnemyRole
import com.minigames.games.gridhunter.logic.GridHunterCommand
import com.minigames.games.gridhunter.logic.GridHunterConfig
import com.minigames.games.gridhunter.logic.GridHunterEngine
import com.minigames.games.gridhunter.logic.GridHunterState
import java.util.Locale
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.random.Random
import kotlin.math.sin

@Composable
fun GridHunterScreen(
    difficulty: Difficulty,
    progressiveMode: Boolean = false,
    controlMode: ControlMode = ControlMode.HYBRID,
    hapticsEnabled: Boolean = true,
    onGameOver: (elapsedMs: Long, moves: Int, exitsReached: Int) -> Unit = { _, _, _ -> },
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalMiniGamesColors.current
    val haptics = LocalHapticFeedback.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val initialConfig = if (progressiveMode) GridHunterConfig.forProgressiveLevel(1) else GridHunterConfig.forDifficulty(difficulty)
    val session = remember(difficulty, progressiveMode) {
        GameSession(
            engine = GridHunterEngine(),
            initialConfig = initialConfig,
        )
    }

    val state by session.state.collectAsStateWithLifecycle()
    var gameOverReported by remember { mutableStateOf(false) }

    // Lifecycle pause/resume
    DisposableEffect(lifecycleOwner, session) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE, Lifecycle.Event.ON_STOP -> session.onHostBackgrounded()
                Lifecycle.Event.ON_RESUME -> session.onHostForegrounded()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Dedicated game loop coroutine – runs for the entire lifetime of the session,
    // never cancelled and restarted mid-game. It calls stepClock every 16ms and
    // simply skips ticks when the game is not in PLAYING state. This avoids all
    // race conditions with flow operators that could interrupt the loop.
    LaunchedEffect(session) {
        while (isActive) {
            session.stepClock()
            delay(16L)
        }
    }

    // Report GAME_OVER once per session
    LaunchedEffect(state.phase) {
        if (state.phase == GamePhase.GAME_OVER && !gameOverReported) {
            gameOverReported = true
            if (hapticsEnabled) haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            onGameOver(state.elapsedPlayTimeMs, state.moveCount, state.exitsReached)
        }
    }

    val handleDirectionInput: (Direction) -> Unit = { direction ->
        if (hapticsEnabled) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        if (state.phase == GamePhase.READY) session.start()
        session.dispatchCommand(GridHunterCommand.Move(direction))
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        GridHunterTopBar(
            difficulty = state.config.difficulty,
            progressiveMode = progressiveMode,
            levelNumber = state.exitsReached + 1,
            elapsedTimeMs = state.elapsedPlayTimeMs,
            moveCount = state.moveCount,
            exitsReached = state.exitsReached,
            canPause = state.phase == GamePhase.PLAYING,
            onPauseClick = { session.pause() },
            onBackClick = onNavigateBack,
        )

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            contentAlignment = Alignment.Center,
        ) {
            GridHunterBoardCanvas(
                state = state,
                swipeEnabled = controlMode != ControlMode.DPAD &&
                    (state.phase == GamePhase.READY || state.phase == GamePhase.PLAYING),
                onDirectionSwipe = handleDirectionInput,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f),
            )

            if (state.phase == GamePhase.READY ||
                state.phase == GamePhase.PAUSED ||
                state.phase == GamePhase.GAME_OVER
            ) {
                GridHunterPhaseOverlay(
                    state = state,
                    onStart = { session.start() },
                    onResume = { session.resume() },
                    onRestart = {
                        gameOverReported = false
                        session.reset(if (progressiveMode) GridHunterConfig.forProgressiveLevel(1) else GridHunterConfig.forDifficulty(difficulty))
                    },
                    onExit = onNavigateBack,
                )
            }
        }

        if (controlMode != ControlMode.SWIPE) {
            NeonTouchDpad(
                enabled = state.phase == GamePhase.READY || state.phase == GamePhase.PLAYING,
                onDirection = handleDirectionInput,
                modifier = Modifier.padding(bottom = 8.dp),
            )
        } else {
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun GridHunterTopBar(
    difficulty: Difficulty,
    progressiveMode: Boolean,
    levelNumber: Int,
    elapsedTimeMs: Long,
    moveCount: Int,
    exitsReached: Int,
    canPause: Boolean,
    onPauseClick: () -> Unit,
    onBackClick: () -> Unit,
) {
    val colors = LocalMiniGamesColors.current

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        IconButton(onClick = onBackClick) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                tint = colors.textPrimary,
            )
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            if (progressiveMode) {
                val accent = when (difficulty) {
                    Difficulty.EASY -> colors.exitGreen
                    Difficulty.MEDIUM -> colors.playerCyan
                    Difficulty.HARD -> colors.accentAmber
                    Difficulty.INSANE -> colors.enemyRed
                }
                Surface(
                    color = accent.copy(alpha = 0.18f),
                    shape = RoundedCornerShape(4.dp),
                    border = BorderStroke(0.8.dp, accent.copy(alpha = 0.75f)),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(difficulty.name, color = accent, fontSize = 9.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 0.6.sp)
                        Text(levelNumber.toString().padStart(3, '0'), color = colors.textPrimary, fontSize = 9.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                    }
                }
            } else {
                Text(
                    text = "GRID HUNTER \u00b7 ${difficulty.name}",
                    color = colors.playerCyan,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.4.sp,
                )
            }
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "${formatDurationSeconds(elapsedTimeMs)}  \u2022  \u29bf$exitsReached  \u2022  ${moveCount}mv",
                color = colors.textSecondary,
                fontSize = 13.sp,
                fontFamily = FontFamily.Monospace,
            )
        }

        IconButton(onClick = onPauseClick, enabled = canPause) {
            Icon(
                imageVector = Icons.Filled.Pause,
                contentDescription = "Pause",
                tint = if (canPause) colors.textPrimary else colors.boardGridLine,
            )
        }
    }
}

@Composable
fun GridHunterBoardCanvas(
    state: GridHunterState,
    swipeEnabled: Boolean,
    onDirectionSwipe: (Direction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalMiniGamesColors.current

    var exploredCells by remember(state.seed) {
        mutableStateOf(setOf(state.player.position))
    }
    LaunchedEffect(state.seed, state.player.position) {
        exploredCells = exploredCells + state.player.position
    }

    var gridDistorted by remember { mutableStateOf(false) }
    LaunchedEffect(state.phase, state.seed) {
        if (state.phase == GamePhase.PLAYING) {
            while (isActive) {
                delay(Random.nextLong(2400L, 5200L))
                gridDistorted = true
                delay(Random.nextLong(90L, 190L))
                gridDistorted = false
            }
        } else {
            gridDistorted = false
        }
    }

    // The exit destabilizes when a hunter gets close to the exit itself.
    val nearestEnemyToExit = remember(state.enemies, state.exitPosition) {
        state.enemies.minOfOrNull {
            it.position.manhattanDistanceTo(state.exitPosition)
        } ?: Int.MAX_VALUE
    }
    val targetExitVisibility = when {
        nearestEnemyToExit <= 1 -> 0.12f
        nearestEnemyToExit <= 2 -> 0.28f
        nearestEnemyToExit <= 3 -> 0.58f
        else -> 1f
    }
    val exitVisibility by animateFloatAsState(
        targetValue = targetExitVisibility,
        animationSpec = tween(durationMillis = 250),
        label = "exitVisibility"
    )

    val animatedPlayer = rememberAnimatedGridPoint(target = state.player.position, durationMs = 65)
    val animatedEnemies = state.enemies.map { enemy ->
        key(enemy.id) {
            enemy to rememberAnimatedGridPoint(target = enemy.position, durationMs = 95)
        }
    }

    val playerPulse by rememberNeonPulse(minScale = 0.90f, maxScale = 1.06f, durationMs = 950)
    val exitPulse by rememberNeonPulse(minScale = 0.88f, maxScale = 1.12f, durationMs = 1900)
    var vortexRotation by remember(state.seed) {
        mutableFloatStateOf(0f)
    }
    var particleTime by remember(state.seed) {
        mutableFloatStateOf(0f)
    }

    LaunchedEffect(state.phase, state.seed) {
        if (state.phase == GamePhase.PLAYING) {
            var lastFrameNanos = withFrameNanos { it }
            while (true) {
                val frameNanos = withFrameNanos { it }
                val deltaSeconds = (frameNanos - lastFrameNanos) / 1_000_000_000f
                lastFrameNanos = frameNanos

                vortexRotation = (vortexRotation + 156.25f * deltaSeconds) % 7200f
                particleTime = (particleTime + deltaSeconds) % 3600f
            }
        } else {
            vortexRotation = 0f
            particleTime = 0f
        }
    }

    Canvas(
        modifier = modifier.cardinalSwipeInput(
            enabled = swipeEnabled,
            threshold = 24.dp,
            allowContinuousStepping = false,
            onDirection = onDirectionSwipe,
        ),
    ) {
        val viewport = calculateGridViewport(
            canvasSize = size,
            bounds = state.bounds,
            paddingPx = 6f,
        )

        // Clean near-black background: no ambient glow behind the grid.
        drawRect(color = Color(0xFF020509), size = size)

        drawExploredGrid(
            viewport = viewport,
            lineColor = Color(0xFF263D50),
            distorted = gridDistorted,
        )

        // Insane mode keeps every obstacle fixed and visible from the start.
        val alwaysShowObstacles = state.config.difficulty == Difficulty.INSANE
        for (obstacle in state.obstacles) {
            val discovered = obstacle in exploredCells ||
                state.bounds.validNeighbors(obstacle).any { it in exploredCells }
            if (alwaysShowObstacles || discovered) {
                drawNeonObstacleCell(viewport = viewport, point = obstacle, colors = colors)
            }
        }

        if (exitVisibility > 0f) {
            val exitCenter = viewport.cellCenter(state.exitPosition)
            val cell = viewport.cellSizePx
            val vortexScale = exitPulse

            // Keep every ring, particle and glow safely inside this one grid cell.
            val outerRadius = cell * (0.265f + 0.025f * vortexScale)
            val middleRadius = cell * 0.185f
            val innerRadius = cell * 0.105f

            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(0xFF00151C).copy(alpha = exitVisibility),
                        Color(0xCC002B35).copy(alpha = exitVisibility),
                        Color(0x6600F5FF).copy(alpha = exitVisibility),
                        Color.Transparent,
                    ),
                    center = exitCenter,
                    radius = outerRadius * 1.28f,
                ),
                radius = outerRadius * 1.28f,
                center = exitCenter,
            )

            drawCircle(
                color = Color(0xFF001016).copy(alpha = 0.96f * exitVisibility),
                radius = innerRadius * 0.92f,
                center = exitCenter,
            )

            drawArc(
                color = Color(0xFF00F5FF).copy(alpha = 0.92f * exitVisibility),
                startAngle = vortexRotation,
                sweepAngle = 238f,
                useCenter = false,
                topLeft = Offset(exitCenter.x - outerRadius, exitCenter.y - outerRadius),
                size = Size(outerRadius * 2f, outerRadius * 2f),
                style = Stroke(width = cell * 0.055f, cap = StrokeCap.Round),
            )

            drawArc(
                color = Color(0xFF63FFFF).copy(alpha = 0.78f * exitVisibility),
                startAngle = -vortexRotation * 1.35f + 112f,
                sweepAngle = 196f,
                useCenter = false,
                topLeft = Offset(exitCenter.x - middleRadius, exitCenter.y - middleRadius),
                size = Size(middleRadius * 2f, middleRadius * 2f),
                style = Stroke(width = cell * 0.045f, cap = StrokeCap.Round),
            )

            drawArc(
                color = Color(0xFF00BFD4).copy(alpha = 0.95f * exitVisibility),
                startAngle = vortexRotation * 1.8f + 205f,
                sweepAngle = 252f,
                useCenter = false,
                topLeft = Offset(exitCenter.x - innerRadius, exitCenter.y - innerRadius),
                size = Size(innerRadius * 2f, innerRadius * 2f),
                style = Stroke(width = cell * 0.035f, cap = StrokeCap.Round),
            )

            val particleCount = 18
            val startRadius = outerRadius * 1.04f
            val endRadius = innerRadius * 0.35f

            for (i in 0 until particleCount) {
                val phaseOffset = (i * 0.6180339f) % 1f
                val baseAngleDeg = (i * 137.508f) % 360f
                val speed = when (i % 3) {
                    0 -> 0.5f
                    1 -> 0.75f
                    else -> 1.0f
                }

                val progress = (particleTime * speed + phaseOffset) % 1f
                val radialEase = progress.pow(1.45f)
                val currentRadius = startRadius + (endRadius - startRadius) * radialEase

                val angleDeg = baseAngleDeg + 290f * progress.pow(1.35f)
                val angleRad = angleDeg * (PI.toFloat() / 180f)

                val particlePos = Offset(
                    x = exitCenter.x + cos(angleRad) * currentRadius,
                    y = exitCenter.y + sin(angleRad) * currentRadius,
                )

                val fadeIn = (progress / 0.2f).coerceIn(0f, 1f)
                val fadeOut = ((1f - progress) / 0.22f).coerceIn(0f, 1f)
                val particleAlpha = fadeIn * fadeOut * exitVisibility

                if (particleAlpha > 0.01f) {
                    val baseDotRadius = cell * (0.012f + 0.005f * (i % 3))
                    val dotRadius = baseDotRadius * (1f - 0.45f * progress)

                    drawCircle(
                        color = Color(0xFF00F5FF).copy(alpha = 0.28f * particleAlpha),
                        radius = dotRadius * 2.4f,
                        center = particlePos,
                    )

                    drawCircle(
                        color = Color(0xFFB8FFFF).copy(alpha = 0.95f * particleAlpha),
                        radius = dotRadius,
                        center = particlePos,
                    )
                }
            }

            drawCircle(
                color = Color(0xFF00F5FF).copy(alpha = 0.8f * exitVisibility),
                radius = cell * 0.025f,
                center = exitCenter,
            )
        }

        for ((enemy, coords) in animatedEnemies) {
            drawNeonEntitySquare(
                viewport = viewport,
                gridX = coords.x,
                gridY = coords.y,
                coreColor = colors.enemyRed,
                glowColor = colors.enemyRedGlow,
                pulseScale = 1f,
                insetRatio = 0.18f,
                innerAccentColor = if (enemy.role == EnemyRole.INTERCEPTOR) colors.enemyInterceptorAccent else null,
            )
        }

        drawNeonEntitySquare(
            viewport = viewport,
            gridX = animatedPlayer.x,
            gridY = animatedPlayer.y,
            coreColor = Color(0xFF3688FF),
            glowColor = Color(0x4D3688FF),
            pulseScale = playerPulse,
            insetRatio = 0.17f,
        )
    }
}

@Composable
private fun GridHunterPhaseOverlay(
    state: GridHunterState,
    onStart: () -> Unit,
    onResume: () -> Unit,
    onRestart: () -> Unit,
    onExit: () -> Unit,
) {
    val colors = LocalMiniGamesColors.current

    val title: String
    val accent: Color
    val subtitle: String

    when (state.phase) {
        GamePhase.READY -> {
            title = "READY"
            accent = colors.playerCyan
            subtitle = "Reach the green exit. Avoid the red hunters."
        }
        GamePhase.PAUSED -> {
            title = "PAUSED"
            accent = colors.accentAmber
            subtitle = "${formatDurationSeconds(state.elapsedPlayTimeMs)}  \u2022  \u29bf${state.exitsReached}"
        }
        GamePhase.GAME_OVER -> {
            title = "CAUGHT"
            accent = colors.enemyRed
            subtitle = "Survived ${formatDurationSeconds(state.elapsedPlayTimeMs)}  \u2022  \u29bf${state.exitsReached}  \u2022  ${state.moveCount}mv"
        }
        else -> return
    }

    Surface(
        color = colors.surface.copy(alpha = 0.92f),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.5.dp, accent.copy(alpha = 0.7f)),
        modifier = Modifier.padding(20.dp),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(text = title, color = accent, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 2.sp)
            Text(text = subtitle, color = colors.textSecondary, fontSize = 13.sp)
            Spacer(modifier = Modifier.height(4.dp))

            when (state.phase) {
                GamePhase.READY -> {
                    Button(
                        onClick = onStart,
                        colors = ButtonDefaults.buttonColors(containerColor = colors.playerCyan, contentColor = colors.background),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Filled.PlayArrow, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("START", fontWeight = FontWeight.Bold)
                    }
                }
                GamePhase.PAUSED -> {
                    Button(
                        onClick = onResume,
                        colors = ButtonDefaults.buttonColors(containerColor = colors.playerCyan, contentColor = colors.background),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Filled.PlayArrow, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("RESUME", fontWeight = FontWeight.Bold)
                    }
                    OutlinedButton(onClick = onRestart, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Filled.Refresh, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("RESTART")
                    }
                    OutlinedButton(onClick = onExit, modifier = Modifier.fillMaxWidth()) {
                        Text("MAIN MENU")
                    }
                }
                GamePhase.GAME_OVER -> {
                    Button(
                        onClick = onRestart,
                        colors = ButtonDefaults.buttonColors(containerColor = colors.enemyRed, contentColor = colors.background),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Filled.Refresh, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("PLAY AGAIN", fontWeight = FontWeight.Bold)
                    }
                    OutlinedButton(onClick = onExit, modifier = Modifier.fillMaxWidth()) {
                        Text("MAIN MENU")
                    }
                }
                else -> Unit
            }
        }
    }
}

@Composable
fun NeonTouchDpad(
    enabled: Boolean,
    onDirection: (Direction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val buttonSize = 68.dp
    val spacing = 10.dp

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(spacing),
    ) {
        DpadArrowButton(icon = Icons.Filled.KeyboardArrowUp, contentDescription = "Up", enabled = enabled, onClick = { onDirection(Direction.UP) }, modifier = Modifier.size(buttonSize))
        Row(horizontalArrangement = Arrangement.spacedBy(spacing), verticalAlignment = Alignment.CenterVertically) {
            DpadArrowButton(icon = Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Left", enabled = enabled, onClick = { onDirection(Direction.LEFT) }, modifier = Modifier.size(buttonSize))
            Spacer(modifier = Modifier.size(buttonSize))
            DpadArrowButton(icon = Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Right", enabled = enabled, onClick = { onDirection(Direction.RIGHT) }, modifier = Modifier.size(buttonSize))
        }
        DpadArrowButton(icon = Icons.Filled.KeyboardArrowDown, contentDescription = "Down", enabled = enabled, onClick = { onDirection(Direction.DOWN) }, modifier = Modifier.size(buttonSize))
    }
}

@Composable
private fun DpadArrowButton(
    icon: ImageVector,
    contentDescription: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalMiniGamesColors.current
    FilledTonalButton(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, colors.boardOuterBorder),
        colors = ButtonDefaults.filledTonalButtonColors(
            containerColor = colors.surfaceElevated,
            contentColor = colors.playerCyan,
            disabledContainerColor = colors.surface,
            disabledContentColor = colors.boardGridLine,
        ),
        contentPadding = PaddingValues(0.dp),
        modifier = modifier,
    ) {
        Icon(imageVector = icon, contentDescription = contentDescription, modifier = Modifier.size(34.dp))
    }
}

private fun formatDurationSeconds(durationMs: Long): String {
    val seconds = durationMs / 1000.0
    return String.format(Locale.US, "%.2fs", seconds)
}


/**
 * Draws a consistently visible, subdued grid over the full board.
 * Rare distortion briefly offsets the otherwise steady grid lines.
 */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawExploredGrid(
    viewport: com.minigames.core.rendering.GridViewport,
    lineColor: Color,
    distorted: Boolean,
) {
    val cell = viewport.cellSizePx
    if (cell <= 0f) return

    fun drawSegment(start: Offset, end: Offset, seed: Int, horizontal: Boolean) {
        val displacement = if (distorted) {
            (sin(seed * 1.73) * cell * 0.035).toFloat()
        } else {
            0f
        }
        val offset = if (horizontal) Offset(0f, displacement) else Offset(displacement, 0f)
        drawLine(
            color = lineColor,
            start = start + offset,
            end = end + offset,
            strokeWidth = if (distorted) 2.2f else 1.6f,
        )
    }

    // Horizontal boundaries: draw a segment if either adjacent cell is explored.
    for (y in 0..viewport.bounds.height) {
        for (x in 0 until viewport.bounds.width) {
            val px = viewport.originOffset.x + x * cell
            val py = viewport.originOffset.y + y * cell
            drawSegment(Offset(px, py), Offset(px + cell, py), x * 31 + y * 17, true)
        }
    }

    // Vertical boundaries.
    for (x in 0..viewport.bounds.width) {
        for (y in 0 until viewport.bounds.height) {
            val px = viewport.originOffset.x + x * cell
            val py = viewport.originOffset.y + y * cell
            drawSegment(Offset(px, py), Offset(px, py + cell), x * 19 + y * 37 + 11, false)
        }
    }
}
