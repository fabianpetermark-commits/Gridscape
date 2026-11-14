package com.minigames.games.gridhunter.ui

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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
import com.minigames.core.models.isTerminal
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

@Composable
fun GridHunterScreen(
    difficulty: Difficulty,
    controlMode: ControlMode = ControlMode.HYBRID,
    hapticsEnabled: Boolean = true,
    bestCompletionTimeMs: Long? = null,
    onGameFinished: (won: Boolean, completionTimeMs: Long?) -> Unit = { _, _ -> },
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalMiniGamesColors.current
    val haptics = LocalHapticFeedback.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val session = remember(difficulty) {
        GameSession(
            engine = GridHunterEngine(),
            initialConfig = GridHunterConfig.forDifficulty(difficulty),
        )
    }

    val state by session.state.collectAsStateWithLifecycle()
    var resultReportedForSeed by remember { mutableStateOf<Long?>(null) }

    DisposableEffect(lifecycleOwner, session) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE, Lifecycle.Event.ON_STOP -> session.onHostBackgrounded()
                Lifecycle.Event.ON_RESUME -> session.onHostForegrounded()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    LaunchedEffect(session, state.phase) {
        if (state.phase == GamePhase.PLAYING) {
            session.runLoop(stepCadenceMillis = 16L)
        }
    }

    LaunchedEffect(state.phase, state.seed) {
        if (state.phase.isTerminal && resultReportedForSeed != state.seed) {
            resultReportedForSeed = state.seed
            val won = state.phase == GamePhase.VICTORY
            if (hapticsEnabled) {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            }
            onGameFinished(won, if (won) state.elapsedPlayTimeMs else null)
        }
    }

    val handleDirectionInput: (Direction) -> Unit = { direction ->
        if (hapticsEnabled) {
            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
        if (state.phase == GamePhase.READY) {
            session.start()
        }
        session.dispatchCommand(GridHunterCommand.Move(direction))
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        GridHunterTopBar(
            difficulty = difficulty,
            elapsedTimeMs = state.elapsedPlayTimeMs,
            moveCount = state.moveCount,
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

            if (state.phase != GamePhase.PLAYING) {
                GridHunterPhaseOverlay(
                    state = state,
                    bestCompletionTimeMs = bestCompletionTimeMs,
                    onStart = { session.start() },
                    onResume = { session.resume() },
                    onRestart = {
                        resultReportedForSeed = null
                        session.reset(GridHunterConfig.forDifficulty(difficulty))
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
    elapsedTimeMs: Long,
    moveCount: Int,
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
            Text(
                text = "GRID HUNTER \u00b7 ${difficulty.name}",
                color = colors.playerCyan,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.4.sp,
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "${formatDurationSeconds(elapsedTimeMs)}   |   MOVES: $moveCount",
                color = colors.textSecondary,
                fontSize = 13.sp,
                fontFamily = FontFamily.Monospace,
            )
        }

        IconButton(
            onClick = onPauseClick,
            enabled = canPause,
        ) {
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

    val animatedPlayer = rememberAnimatedGridPoint(target = state.player.position, durationMs = 65)
    val animatedEnemies = state.enemies.map { enemy ->
        key(enemy.id) {
            enemy to rememberAnimatedGridPoint(target = enemy.position, durationMs = 95)
        }
    }

    val playerPulse by rememberNeonPulse(minScale = 0.90f, maxScale = 1.06f, durationMs = 950)
    val exitPulse by rememberNeonPulse(minScale = 0.94f, maxScale = 1.03f, durationMs = 1300)

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

        drawNeonGridBoard(viewport = viewport, colors = colors)

        for (obstacle in state.obstacles) {
            drawNeonObstacleCell(
                viewport = viewport,
                point = obstacle,
                colors = colors,
            )
        }

        drawNeonEntitySquare(
            viewport = viewport,
            gridX = state.exitPosition.x.toFloat(),
            gridY = state.exitPosition.y.toFloat(),
            coreColor = colors.exitGreen,
            glowColor = colors.exitGreenGlow,
            pulseScale = exitPulse,
            insetRatio = 0.16f,
        )

        for ((enemy, coords) in animatedEnemies) {
            val accent = if (enemy.role == EnemyRole.INTERCEPTOR) {
                colors.enemyInterceptorAccent
            } else {
                null
            }
            drawNeonEntitySquare(
                viewport = viewport,
                gridX = coords.x,
                gridY = coords.y,
                coreColor = colors.enemyRed,
                glowColor = colors.enemyRedGlow,
                pulseScale = 1f,
                insetRatio = 0.18f,
                innerAccentColor = accent,
            )
        }

        drawNeonEntitySquare(
            viewport = viewport,
            gridX = animatedPlayer.x,
            gridY = animatedPlayer.y,
            coreColor = colors.playerCyan,
            glowColor = colors.playerCyanGlow,
            pulseScale = playerPulse,
            insetRatio = 0.17f,
        )
    }
}

@Composable
private fun GridHunterPhaseOverlay(
    state: GridHunterState,
    bestCompletionTimeMs: Long?,
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
            subtitle = "Time: ${formatDurationSeconds(state.elapsedPlayTimeMs)}"
        }

        GamePhase.VICTORY -> {
            title = "VICTORY"
            accent = colors.exitGreen
            val timeStr = formatDurationSeconds(state.elapsedPlayTimeMs)
            val isNewRecord = bestCompletionTimeMs == null || state.elapsedPlayTimeMs <= bestCompletionTimeMs
            subtitle = if (isNewRecord) {
                "Cleared in $timeStr \u00b7 NEW BEST!"
            } else {
                "Cleared in $timeStr \u00b7 Best: ${formatDurationSeconds(bestCompletionTimeMs)}"
            }
        }

        GamePhase.GAME_OVER -> {
            title = "CAUGHT"
            accent = colors.enemyRed
            subtitle = "Survived ${formatDurationSeconds(state.elapsedPlayTimeMs)} (${state.moveCount} moves)"
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
            Text(
                text = title,
                color = accent,
                fontSize = 24.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 2.sp,
            )

            Text(
                text = subtitle,
                color = colors.textSecondary,
                fontSize = 13.sp,
            )

            Spacer(modifier = Modifier.height(4.dp))

            when (state.phase) {
                GamePhase.READY -> {
                    Button(
                        onClick = onStart,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = colors.playerCyan,
                            contentColor = colors.background,
                        ),
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
                        colors = ButtonDefaults.buttonColors(
                            containerColor = colors.playerCyan,
                            contentColor = colors.background,
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Filled.PlayArrow, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("RESUME", fontWeight = FontWeight.Bold)
                    }

                    OutlinedButton(
                        onClick = onRestart,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Filled.Refresh, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("RESTART")
                    }

                    OutlinedButton(
                        onClick = onExit,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("MAIN MENU")
                    }
                }

                GamePhase.VICTORY, GamePhase.GAME_OVER -> {
                    Button(
                        onClick = onRestart,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = accent,
                            contentColor = colors.background,
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Filled.Refresh, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("PLAY AGAIN", fontWeight = FontWeight.Bold)
                    }

                    OutlinedButton(
                        onClick = onExit,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("DIFFICULTY / MENU")
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
        DpadArrowButton(
            icon = Icons.Filled.KeyboardArrowUp,
            contentDescription = "Up",
            enabled = enabled,
            onClick = { onDirection(Direction.UP) },
            modifier = Modifier.size(buttonSize),
        )

        Row(
            horizontalArrangement = Arrangement.spacedBy(spacing),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DpadArrowButton(
                icon = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                contentDescription = "Left",
                enabled = enabled,
                onClick = { onDirection(Direction.LEFT) },
                modifier = Modifier.size(buttonSize),
            )

            Spacer(modifier = Modifier.size(buttonSize))

            DpadArrowButton(
                icon = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = "Right",
                enabled = enabled,
                onClick = { onDirection(Direction.RIGHT) },
                modifier = Modifier.size(buttonSize),
            )
        }

        DpadArrowButton(
            icon = Icons.Filled.KeyboardArrowDown,
            contentDescription = "Down",
            enabled = enabled,
            onClick = { onDirection(Direction.DOWN) },
            modifier = Modifier.size(buttonSize),
        )
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
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            modifier = Modifier.size(34.dp),
        )
    }
}

private fun formatDurationSeconds(durationMs: Long): String {
    val seconds = durationMs / 1000.0
    return String.format(Locale.US, "%.2fs", seconds)
}
