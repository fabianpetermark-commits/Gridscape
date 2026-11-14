package com.minigames.core.rendering

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import com.minigames.core.models.GridBounds
import com.minigames.core.models.GridPoint
import kotlin.math.min

// ============================================================================
// 1. Minimalist Neon Color System & Theme
// ============================================================================

@Immutable
data class MiniGamesNeonColors(
    val background: Color = Color(0xFF090C14),
    val surface: Color = Color(0xFF111726),
    val surfaceElevated: Color = Color(0xFF182236),
    val boardCellFill: Color = Color(0xFF0D1320),
    val boardGridLine: Color = Color(0xFF1F2B42),
    val boardOuterBorder: Color = Color(0xFF334463),
    val playerCyan: Color = Color(0xFF00F5FF),
    val playerCyanGlow: Color = Color(0x4D00F5FF),
    val exitGreen: Color = Color(0xFF00E676),
    val exitGreenGlow: Color = Color(0x4D00E676),
    val enemyRed: Color = Color(0xFFFF2A5F),
    val enemyRedGlow: Color = Color(0x4DFF2A5F),
    val enemyInterceptorAccent: Color = Color(0xFFFF9100),
    val obstacleFill: Color = Color(0xFF25324A),
    val obstacleBorder: Color = Color(0xFF52668A),
    val textPrimary: Color = Color(0xFFF8FAFC),
    val textSecondary: Color = Color(0xFF94A3B8),
    val accentAmber: Color = Color(0xFFFFC400),
)

val LocalMiniGamesColors = staticCompositionLocalOf { MiniGamesNeonColors() }

@Composable
fun MiniGamesTheme(
    neonColors: MiniGamesNeonColors = MiniGamesNeonColors(),
    content: @Composable () -> Unit,
) {
    val colorScheme = darkColorScheme(
        primary = neonColors.playerCyan,
        onPrimary = neonColors.background,
        secondary = neonColors.exitGreen,
        onSecondary = neonColors.background,
        tertiary = neonColors.accentAmber,
        error = neonColors.enemyRed,
        background = neonColors.background,
        onBackground = neonColors.textPrimary,
        surface = neonColors.surface,
        onSurface = neonColors.textPrimary,
        surfaceVariant = neonColors.surfaceElevated,
        onSurfaceVariant = neonColors.textSecondary,
        outline = neonColors.boardOuterBorder,
    )

    CompositionLocalProvider(LocalMiniGamesColors provides neonColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            content = content,
        )
    }
}

// ============================================================================
// 2. Responsive Canvas Grid Geometry & Coordinate Mapping
// ============================================================================

@Immutable
data class GridViewport(
    val bounds: GridBounds,
    val cellSizePx: Float,
    val originOffset: Offset,
) {
    val boardWidthPx: Float
        get() = cellSizePx * bounds.width

    val boardHeightPx: Float
        get() = cellSizePx * bounds.height

    val boardSize: Size
        get() = Size(width = boardWidthPx, height = boardHeightPx)

    fun cellTopLeft(point: GridPoint): Offset =
        cellTopLeft(point.x.toFloat(), point.y.toFloat())

    fun cellTopLeft(gridX: Float, gridY: Float): Offset = Offset(
        x = originOffset.x + gridX * cellSizePx,
        y = originOffset.y + gridY * cellSizePx,
    )

    fun cellCenter(point: GridPoint): Offset =
        cellCenter(point.x.toFloat(), point.y.toFloat())

    fun cellCenter(gridX: Float, gridY: Float): Offset = Offset(
        x = originOffset.x + (gridX + 0.5f) * cellSizePx,
        y = originOffset.y + (gridY + 0.5f) * cellSizePx,
    )
}

fun calculateGridViewport(
    canvasSize: Size,
    bounds: GridBounds,
    paddingPx: Float = 0f,
): GridViewport {
    val availableWidth = (canvasSize.width - paddingPx * 2f).coerceAtLeast(0f)
    val availableHeight = (canvasSize.height - paddingPx * 2f).coerceAtLeast(0f)

    val cellSize = min(
        availableWidth / bounds.width.toFloat(),
        availableHeight / bounds.height.toFloat(),
    )

    val boardWidth = cellSize * bounds.width
    val boardHeight = cellSize * bounds.height

    val origin = Offset(
        x = (canvasSize.width - boardWidth) / 2f,
        y = (canvasSize.height - boardHeight) / 2f,
    )

    return GridViewport(
        bounds = bounds,
        cellSizePx = cellSize,
        originOffset = origin,
    )
}

// ============================================================================
// 3. Smooth Visual Interpolation & Pulsing Animation Helpers
// ============================================================================

@Immutable
data class AnimatedGridCoords(
    val x: Float,
    val y: Float,
)

/**
 * Smoothly interpolates visual coordinates between discrete [GridPoint] steps
 * without coupling game logic to rendering frame rate.
 */
@Composable
fun rememberAnimatedGridPoint(
    target: GridPoint,
    durationMs: Int = 70,
): AnimatedGridCoords {
    val animX by animateFloatAsState(
        targetValue = target.x.toFloat(),
        animationSpec = tween(durationMillis = durationMs, easing = FastOutSlowInEasing),
        label = "gridX",
    )
    val animY by animateFloatAsState(
        targetValue = target.y.toFloat(),
        animationSpec = tween(durationMillis = durationMs, easing = FastOutSlowInEasing),
        label = "gridY",
    )
    return AnimatedGridCoords(x = animX, y = animY)
}

/**
 * Infinite subtle breathing scale factor for active neon entities.
 */
@Composable
fun rememberNeonPulse(
    minScale: Float = 0.92f,
    maxScale: Float = 1.06f,
    durationMs: Int = 1050,
): State<Float> {
    val transition = rememberInfiniteTransition(label = "neonPulse")
    return transition.animateFloat(
        initialValue = minScale,
        targetValue = maxScale,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = durationMs, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulseScale",
    )
}

// ============================================================================
// 4. Shared Neon Canvas Drawing Primitives (No Labels Inside Grid)
// ============================================================================

fun DrawScope.drawNeonGridBoard(
    viewport: GridViewport,
    colors: MiniGamesNeonColors,
) {
    if (viewport.cellSizePx <= 0f) return

    val cornerRadius = CornerRadius(viewport.cellSizePx * 0.14f, viewport.cellSizePx * 0.14f)

    // Board base fill
    drawRoundRect(
        color = colors.boardCellFill,
        topLeft = viewport.originOffset,
        size = viewport.boardSize,
        cornerRadius = cornerRadius,
    )

    // Interior grid lines
    for (col in 1 until viewport.bounds.width) {
        val x = viewport.originOffset.x + col * viewport.cellSizePx
        drawLine(
            color = colors.boardGridLine,
            start = Offset(x, viewport.originOffset.y),
            end = Offset(x, viewport.originOffset.y + viewport.boardHeightPx),
            strokeWidth = 2f,
        )
    }

    for (row in 1 until viewport.bounds.height) {
        val y = viewport.originOffset.y + row * viewport.cellSizePx
        drawLine(
            color = colors.boardGridLine,
            start = Offset(viewport.originOffset.x, y),
            end = Offset(viewport.originOffset.x + viewport.boardWidthPx, y),
            strokeWidth = 2f,
        )
    }

    // Crisp outer frame
    drawRoundRect(
        color = colors.boardOuterBorder,
        topLeft = viewport.originOffset,
        size = viewport.boardSize,
        cornerRadius = cornerRadius,
        style = Stroke(width = 3f),
    )
}

fun DrawScope.drawNeonObstacleCell(
    viewport: GridViewport,
    point: GridPoint,
    colors: MiniGamesNeonColors,
) {
    val cell = viewport.cellSizePx
    if (cell <= 0f) return

    val inset = cell * 0.10f
    val topLeft = viewport.cellTopLeft(point) + Offset(inset, inset)
    val size = Size(cell - inset * 2f, cell - inset * 2f)
    val radius = CornerRadius(cell * 0.14f, cell * 0.14f)

    drawRoundRect(
        color = colors.obstacleFill,
        topLeft = topLeft,
        size = size,
        cornerRadius = radius,
    )
    drawRoundRect(
        color = colors.obstacleBorder,
        topLeft = topLeft,
        size = size,
        cornerRadius = radius,
        style = Stroke(width = 2.5f),
    )
}

fun DrawScope.drawNeonEntitySquare(
    viewport: GridViewport,
    gridX: Float,
    gridY: Float,
    coreColor: Color,
    glowColor: Color,
    pulseScale: Float = 1f,
    insetRatio: Float = 0.17f,
    cornerRadiusRatio: Float = 0.20f,
    innerAccentColor: Color? = null,
) {
    val cell = viewport.cellSizePx
    if (cell <= 0f) return

    val center = viewport.cellCenter(gridX, gridY)
    val baseSide = cell * (1f - insetRatio * 2f)
    val coreSide = (baseSide * pulseScale).coerceIn(cell * 0.35f, cell * 0.88f)
    val glowSide = (coreSide * 1.26f).coerceAtMost(cell * 0.96f)

    // Outer neon bloom
    drawRoundRect(
        color = glowColor,
        topLeft = Offset(center.x - glowSide / 2f, center.y - glowSide / 2f),
        size = Size(glowSide, glowSide),
        cornerRadius = CornerRadius(glowSide * cornerRadiusRatio, glowSide * cornerRadiusRatio),
    )

    // Solid neon square
    drawRoundRect(
        color = coreColor,
        topLeft = Offset(center.x - coreSide / 2f, center.y - coreSide / 2f),
        size = Size(coreSide, coreSide),
        cornerRadius = CornerRadius(coreSide * cornerRadiusRatio, coreSide * cornerRadiusRatio),
    )

    // Optional geometric role indicator (pure shape, no text labels)
    if (innerAccentColor != null) {
        val innerSide = coreSide * 0.36f
        drawRoundRect(
            color = innerAccentColor,
            topLeft = Offset(center.x - innerSide / 2f, center.y - innerSide / 2f),
            size = Size(innerSide, innerSide),
            cornerRadius = CornerRadius(innerSide * 0.25f, innerSide * 0.25f),
        )
    }
}
