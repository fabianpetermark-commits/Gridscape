package com.minigames.gridscape.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.minigames.core.input.ControlMode
import com.minigames.core.models.Difficulty
import com.minigames.core.models.GameDescriptor
import com.minigames.core.models.GameId
import com.minigames.core.models.GameStats
import com.minigames.core.rendering.LocalMiniGamesColors
import com.minigames.core.storage.MiniGamesStorage
import com.minigames.core.storage.UserSettings
import com.minigames.games.gridhunter.logic.GridHunterMeta
import com.minigames.games.gridhunter.ui.GridHunterScreen
import java.util.Locale
import kotlinx.coroutines.launch

private sealed interface AppRoute {
    data object MainMenu : AppRoute
    data object Settings : AppRoute
    data object DifficultySelect : AppRoute
    data class Playing(val difficulty: Difficulty) : AppRoute
}

@Composable
fun GridscapeApp(
    storage: MiniGamesStorage,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val settings by storage.settingsRepository.settings.collectAsStateWithLifecycle(
        initialValue = UserSettings(),
    )
    val gridHunterStats by storage.statsRepository
        .observeAllStatsForGame(GridHunterMeta.ID)
        .collectAsStateWithLifecycle(initialValue = emptyMap())

    var route by remember { mutableStateOf<AppRoute>(AppRoute.MainMenu) }

    BackHandler(enabled = route != AppRoute.MainMenu) {
        route = when (route) {
            AppRoute.MainMenu -> AppRoute.MainMenu
            AppRoute.Settings, AppRoute.DifficultySelect -> AppRoute.MainMenu
            is AppRoute.Playing -> AppRoute.DifficultySelect
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(LocalMiniGamesColors.current.background)
            .safeDrawingPadding(),
    ) {
        when (val current = route) {
            AppRoute.MainMenu -> {
                MainMenuScreen(
                    onStart = { route = AppRoute.DifficultySelect },
                    onOpenSettings = { route = AppRoute.Settings },
                    onExit = { context.findActivity()?.finish() },
                )
            }

            AppRoute.Settings -> {
                SettingsScreen(
                    settings = settings,
                    onControlModeSelected = { mode ->
                        scope.launch { storage.settingsRepository.setControlMode(mode) }
                    },
                    onHapticsChanged = { enabled ->
                        scope.launch { storage.settingsRepository.setHapticsEnabled(enabled) }
                    },
                    onShowHintsChanged = { enabled ->
                        scope.launch { storage.settingsRepository.setShowControlHints(enabled) }
                    },
                    onBack = { route = AppRoute.MainMenu },
                )
            }

            AppRoute.DifficultySelect -> {
                DifficultySelectScreen(
                    game = GridHunterMeta.DESCRIPTOR,
                    statsByDifficulty = gridHunterStats,
                    showControlHints = settings.showControlHints,
                    controlMode = settings.controlMode,
                    onStartDifficulty = { difficulty ->
                        route = AppRoute.Playing(difficulty)
                    },
                    onResetStats = {
                        scope.launch { storage.statsRepository.clearStats(GridHunterMeta.ID) }
                    },
                    onBack = { route = AppRoute.MainMenu },
                )
            }

            is AppRoute.Playing -> {
                GridHunterScreen(
                    difficulty = current.difficulty,
                    controlMode = settings.controlMode,
                    hapticsEnabled = settings.hapticsEnabled,
                    onGameOver = { elapsedMs, _, _ ->
                        scope.launch {
                            storage.statsRepository.recordGameResult(
                                gameId = GridHunterMeta.ID,
                                difficulty = current.difficulty,
                                won = false,
                                completionTimeMs = elapsedMs,
                            )
                        }
                    },
                    onNavigateBack = {
                        route = AppRoute.DifficultySelect
                    },
                )
            }
        }
    }
}

@Composable
private fun MainMenuScreen(
    onStart: () -> Unit,
    onOpenSettings: () -> Unit,
    onExit: () -> Unit,
) {
    val colors = LocalMiniGamesColors.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 28.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = "GRIDSCAPE",
            color = colors.playerCyan,
            fontSize = 34.sp,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = 4.sp,
        )
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = "ONE GRID. ONE CHALLENGE.",
            color = colors.textSecondary,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 1.8.sp,
        )
        Spacer(modifier = Modifier.height(52.dp))

        Button(
            onClick = onStart,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                containerColor = colors.playerCyan,
                contentColor = colors.background,
            ),
            contentPadding = PaddingValues(vertical = 16.dp),
        ) {
            Icon(
                imageVector = Icons.Filled.PlayArrow,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text("INDÍTÁS", fontWeight = FontWeight.ExtraBold, letterSpacing = 1.2.sp)
        }

        Spacer(modifier = Modifier.height(14.dp))

        OutlinedButton(
            onClick = onOpenSettings,
            modifier = Modifier.fillMaxWidth(),
            border = BorderStroke(1.dp, colors.boardOuterBorder),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.textPrimary),
            contentPadding = PaddingValues(vertical = 15.dp),
        ) {
            Icon(
                imageVector = Icons.Filled.Settings,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text("BEÁLLÍTÁSOK", fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
        }

        Spacer(modifier = Modifier.height(14.dp))

        TextButton(onClick = onExit) {
            Text(
                text = "KILÉPÉS",
                color = colors.textSecondary,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 1.2.sp,
            )
        }
    }
}

@Composable
private fun DifficultySelectScreen(
    game: GameDescriptor,
    statsByDifficulty: Map<Difficulty, GameStats>,
    showControlHints: Boolean,
    controlMode: ControlMode,
    onStartDifficulty: (Difficulty) -> Unit,
    onResetStats: () -> Unit,
    onBack: () -> Unit,
) {
    val colors = LocalMiniGamesColors.current
    var showConfirmReset by remember { mutableStateOf(false) }

    if (showConfirmReset) {
        AlertDialog(
            onDismissRequest = { showConfirmReset = false },
            containerColor = colors.surface,
            title = { Text("Reset Statistics?", color = colors.textPrimary) },
            text = {
                Text(
                    "Clear all records for ${game.title}?",
                    color = colors.textSecondary,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showConfirmReset = false
                        onResetStats()
                    },
                ) {
                    Text("RESET", color = colors.enemyRed, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmReset = false }) {
                    Text("CANCEL", color = colors.textSecondary)
                }
            },
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = colors.textPrimary,
                )
            }

            Text(
                text = "NEHÉZSÉG VÁLASZTÁSA",
                color = colors.playerCyan,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.5.sp,
            )

            IconButton(onClick = { showConfirmReset = true }) {
                Icon(
                    imageVector = Icons.Filled.DeleteOutline,
                    contentDescription = "Reset Stats",
                    tint = colors.textSecondary,
                )
            }
        }

        if (showControlHints) {
            Spacer(modifier = Modifier.height(8.dp))
            Surface(
                color = colors.surfaceElevated,
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, colors.boardGridLine),
                modifier = Modifier.fillMaxWidth(),
            ) {
                val hintText = when (controlMode) {
                    ControlMode.HYBRID -> "Controls: Swipe on board or use D-Pad. First move starts the clock."
                    ControlMode.SWIPE -> "Controls: Swipe anywhere on the 8\u00d78 grid to move."
                    ControlMode.DPAD -> "Controls: Tap the directional D-Pad below the grid."
                }
                Text(
                    text = hintText,
                    color = colors.textSecondary,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(bottom = 24.dp),
        ) {
            items(game.supportedDifficulties, key = { it.name }) { difficulty ->
                val stats = statsByDifficulty[difficulty] ?: GameStats()
                val accent = difficultyAccentColor(difficulty)

                Surface(
                    color = colors.surface,
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.2.dp, accent.copy(alpha = 0.55f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onStartDifficulty(difficulty) },
                ) {
                    Row(
                        modifier = Modifier
                            .padding(16.dp)
                            .fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = difficulty.name,
                                color = accent,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = 1.4.sp,
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = gridHunterDifficultySummary(difficulty),
                                color = colors.textSecondary,
                                fontSize = 12.sp,
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "PLAYED: ${stats.gamesPlayed}  \u2022  BEST: ${stats.bestCompletionTimeMs?.let { String.format(Locale.US, "%.2fs", it / 1000.0) } ?: "--"}",
                                color = colors.textPrimary,
                                fontSize = 12.sp,
                                fontFamily = FontFamily.Monospace,
                            )
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        OutlinedButton(
                            onClick = { onStartDifficulty(difficulty) },
                            border = BorderStroke(1.dp, accent),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = accent),
                        ) {
                            Text("START", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsScreen(
    settings: UserSettings,
    onControlModeSelected: (ControlMode) -> Unit,
    onHapticsChanged: (Boolean) -> Unit,
    onShowHintsChanged: (Boolean) -> Unit,
    onBack: () -> Unit,
) {
    val colors = LocalMiniGamesColors.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = colors.textPrimary,
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "SETTINGS",
                color = colors.playerCyan,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.6.sp,
            )
        }

        Surface(
            color = colors.surface,
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, colors.boardOuterBorder),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = "INPUT MODE",
                    color = colors.textPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ControlMode.entries.forEach { mode ->
                        val selected = settings.controlMode == mode
                        FilterChip(
                            selected = selected,
                            onClick = { onControlModeSelected(mode) },
                            label = {
                                Text(
                                    text = mode.name,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = colors.playerCyan,
                                selectedLabelColor = colors.background,
                                containerColor = colors.surfaceElevated,
                                labelColor = colors.textSecondary,
                            ),
                        )
                    }
                }
            }
        }

        Surface(
            color = colors.surface,
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, colors.boardOuterBorder),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                SettingsToggleRow(
                    title = "Haptic Feedback",
                    subtitle = "Vibrate on directional steps and game over",
                    checked = settings.hapticsEnabled,
                    onCheckedChange = onHapticsChanged,
                )

                SettingsToggleRow(
                    title = "Control Hints",
                    subtitle = "Display input tips before starting a round",
                    checked = settings.showControlHints,
                    onCheckedChange = onShowHintsChanged,
                )
            }
        }
    }
}

@Composable
private fun SettingsToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    val colors = LocalMiniGamesColors.current

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, color = colors.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Text(text = subtitle, color = colors.textSecondary, fontSize = 12.sp)
        }
        Spacer(modifier = Modifier.width(12.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = colors.background,
                checkedTrackColor = colors.playerCyan,
            ),
        )
    }
}

@Composable
private fun difficultyAccentColor(difficulty: Difficulty): Color {
    val colors = LocalMiniGamesColors.current
    return when (difficulty) {
        Difficulty.EASY -> colors.exitGreen
        Difficulty.MEDIUM -> colors.playerCyan
        Difficulty.HARD -> colors.accentAmber
        Difficulty.INSANE -> colors.enemyRed
    }
}

private fun gridHunterDifficultySummary(difficulty: Difficulty): String = when (difficulty) {
    Difficulty.EASY -> "1 Pursuer (750ms) \u00b7 Open 8\u00d78 grid"
    Difficulty.MEDIUM -> "1 Fast Interceptor (350ms) \u00b7 Cuts off escape path"
    Difficulty.HARD -> "2 Hunters (500ms) \u00b7 Coordinated pursuit & flank"
    Difficulty.INSANE -> "2 Dynamic Hunters (450\u2013500ms) \u00b7 5\u201310 Obstacles"
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
