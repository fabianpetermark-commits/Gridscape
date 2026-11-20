package com.minigames.gridscape.libgdx

import android.util.Log
import com.badlogic.gdx.Game
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.InputAdapter
import com.badlogic.gdx.ScreenAdapter
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.OrthographicCamera
import com.badlogic.gdx.graphics.g2d.BitmapFont
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.minigames.core.engine.GameSession
import com.minigames.core.models.Difficulty
import com.minigames.core.models.Direction
import com.minigames.core.models.GamePhase
import com.minigames.core.models.GridPoint
import com.minigames.games.gridhunter.logic.EnemyRole
import com.minigames.games.gridhunter.logic.GridHunterCommand
import com.minigames.games.gridhunter.logic.GridHunterConfig
import com.minigames.games.gridhunter.logic.GridHunterEngine
import com.minigames.games.gridhunter.logic.GridHunterState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

private const val TAG = "GridHunterLibGdx"

class GridHunterLibGdxGame(
    private val difficulty: Difficulty,
    private val onExit: () -> Unit,
) : Game() {

    override fun create() {
        Log.d(TAG, "Game.create() — GL context is now available")
        setScreen(GridHunterPrototypeScreen(difficulty, onExit))
    }

    override fun dispose() {
        Log.d(TAG, "Game.dispose()")
        super.dispose()
    }
}

private class GridHunterPrototypeScreen(
    private val difficulty: Difficulty,
    private val onExit: () -> Unit,
) : ScreenAdapter() {

    // -------------------------------------------------------------------------
    // Game state — read on the render thread, written by coroutines via
    // Gdx.app.postRunnable so all mutations happen on the GL thread.
    // -------------------------------------------------------------------------
    private val session = GameSession(
        engine = GridHunterEngine(),
        initialConfig = GridHunterConfig.forDifficulty(difficulty),
    )
    private var state: GridHunterState? = session.state.value

    // Coroutine scope is created in show() and cancelled in hide()/dispose().
    // Using Dispatchers.Default keeps coroutines off the GL thread; state
    // mutations are posted back via Gdx.app.postRunnable.
    private var scope: CoroutineScope? = null

    // -------------------------------------------------------------------------
    // GL resources — MUST be created in show() after the GL context is ready.
    // Creating them as class-level properties or in init {} causes an immediate
    // crash because OpenGL is not yet initialised at that point.
    // -------------------------------------------------------------------------
    private lateinit var camera: OrthographicCamera
    private lateinit var shapes: ShapeRenderer
    private lateinit var batch: SpriteBatch
    private lateinit var font: BitmapFont

    // Colours are plain data; they are safe to create here.
    private val background = Color(0.035f, 0.047f, 0.078f, 1f)
    private val gridLine = Color(0.12f, 0.17f, 0.25f, 1f)
    private val cyan = Color(0f, 0.96f, 1f, 1f)
    private val green = Color(0f, 0.9f, 0.46f, 1f)
    private val red = Color(1f, 0.16f, 0.37f, 1f)
    private val amber = Color(1f, 0.57f, 0f, 1f)
    private val obstacle = Color(0.19f, 0.25f, 0.36f, 1f)

    private var boardLeft = 0f
    private var boardBottom = 0f
    private var cell = 0f
    private var touchStartX = 0f
    private var touchStartY = 0f

    // -------------------------------------------------------------------------
    // show() — called by libGDX once the GL context is ready.
    // All GL object creation and input processor registration happen here.
    // -------------------------------------------------------------------------
    override fun show() {
        Log.d(TAG, "show() — creating GL resources and starting coroutines")

        // Create GL resources only now that the context is guaranteed to be ready.
        camera = OrthographicCamera()
        shapes = ShapeRenderer()
        batch = SpriteBatch()
        font = BitmapFont()

        // Start coroutines that keep the state snapshot current and drive the
        // simulation clock. Using Dispatchers.Default to stay off the GL thread;
        // writes back to 'state' via Gdx.app.postRunnable so they are safely
        // consumed on the render thread.
        val newScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        scope = newScope

        newScope.launch {
            session.state.collect { newState ->
                // Post state update to the GL/render thread.
                Gdx.app.postRunnable { state = newState }
            }
        }

        // Dedicated 16 ms simulation clock — matches the Compose host's loop cadence.
        newScope.launch {
            while (isActive) {
                session.stepClock()
                kotlinx.coroutines.delay(16L)
            }
        }

        // Register the input processor here, not in init{}, because Gdx.input
        // is only guaranteed to be valid after show() is called.
        Gdx.input.inputProcessor = object : InputAdapter() {
            override fun touchDown(screenX: Int, screenY: Int, pointer: Int, button: Int): Boolean {
                touchStartX = screenX.toFloat()
                touchStartY = screenY.toFloat()
                return true
            }

            override fun touchUp(screenX: Int, screenY: Int, pointer: Int, button: Int): Boolean {
                val dx = screenX - touchStartX
                val dy = touchStartY - screenY
                val swipeThreshold = 24f
                val isSwipe = dx * dx + dy * dy >= swipeThreshold * swipeThreshold
                val snapshot = state ?: return true

                // UI taps are handled before board gestures so controls never move the player.
                if (!isSwipe) {
                    val worldY = Gdx.graphics.height - screenY.toFloat()
                    if (snapshot.phase == GamePhase.PLAYING && isInside(
                            screenX.toFloat(), worldY, pauseButtonBounds()
                        )
                    ) {
                        session.pause()
                        return true
                    }
                    if (snapshot.phase == GamePhase.PAUSED) {
                        when {
                            isInside(screenX.toFloat(), worldY, primaryButtonBounds()) -> session.resume()
                            isInside(screenX.toFloat(), worldY, secondaryButtonBounds()) ->
                                session.reset(GridHunterConfig.forDifficulty(difficulty))
                            isInside(screenX.toFloat(), worldY, tertiaryButtonBounds()) -> onExit()
                        }
                        return true
                    }
                    if (snapshot.phase == GamePhase.GAME_OVER || snapshot.phase == GamePhase.VICTORY) {
                        when {
                            isInside(screenX.toFloat(), worldY, primaryButtonBounds()) ->
                                session.reset(GridHunterConfig.forDifficulty(difficulty))
                            isInside(screenX.toFloat(), worldY, secondaryButtonBounds()) -> onExit()
                        }
                        return true
                    }
                    if (snapshot.phase == GamePhase.READY) session.start()
                    return true
                }

                if (snapshot.phase != GamePhase.READY && snapshot.phase != GamePhase.PLAYING) {
                    return true
                }

                val direction = if (kotlin.math.abs(dx) > kotlin.math.abs(dy)) {
                    if (dx > 0f) Direction.RIGHT else Direction.LEFT
                } else {
                    if (dy > 0f) Direction.UP else Direction.DOWN
                }
                if (snapshot.phase == GamePhase.READY) session.start()
                session.dispatchCommand(GridHunterCommand.Move(direction))
                return true
            }
        }
    }

    // -------------------------------------------------------------------------
    // render() — called on the GL thread; all GL objects are ready.
    // -------------------------------------------------------------------------
    override fun render(delta: Float) {
        val snapshot = state ?: return
        Gdx.gl.glClearColor(background.r, background.g, background.b, 1f)
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT)
        camera.setToOrtho(false, Gdx.graphics.width.toFloat(), Gdx.graphics.height.toFloat())
        camera.update()

        val width = Gdx.graphics.width.toFloat()
        val height = Gdx.graphics.height.toFloat()
        val boardSize = minOf(width - 40f, height - 230f).coerceAtLeast(160f)
        cell = boardSize / snapshot.bounds.width
        boardLeft = (width - boardSize) / 2f
        boardBottom = (height - boardSize) / 2f - 10f

        shapes.projectionMatrix = camera.combined
        shapes.begin(ShapeRenderer.ShapeType.Filled)
        shapes.color = Color(0.045f, 0.065f, 0.1f, 1f)
        shapes.rect(boardLeft, boardBottom, boardSize, boardSize)
        snapshot.obstacles.forEach { drawCell(it, obstacle, 0.1f) }
        drawCell(snapshot.exitPosition, green, 0.18f)
        snapshot.enemies.forEach { enemy ->
            drawCell(enemy.position, if (enemy.role == EnemyRole.INTERCEPTOR) amber else red, 0.17f)
        }
        drawCell(snapshot.player.position, cyan, 0.17f)
        shapes.end()

        shapes.begin(ShapeRenderer.ShapeType.Line)
        shapes.color = gridLine
        for (index in 0..snapshot.bounds.width) {
            val x = boardLeft + index * cell
            shapes.line(x, boardBottom, x, boardBottom + boardSize)
        }
        for (index in 0..snapshot.bounds.height) {
            val y = boardBottom + index * cell
            shapes.line(boardLeft, y, boardLeft + boardSize, y)
        }
        shapes.color = cyan
        shapes.rect(boardLeft, boardBottom, boardSize, boardSize)
        shapes.end()

        batch.projectionMatrix = camera.combined
        batch.begin()
        font.color = Color.WHITE
        font.data.setScale(1.25f)
        font.draw(batch, "GRID HUNTER  ·  ${snapshot.config.difficulty}", 20f, height - 28f)
        font.data.setScale(0.9f)
        font.draw(batch, "MOVES ${snapshot.moveCount}     EXITS ${snapshot.exitsReached}", 20f, height - 54f)
        val hint = when (snapshot.phase) {
            GamePhase.READY -> "SWIPE TO START"
            GamePhase.PLAYING -> "SWIPE TO MOVE"
            else -> "GRID HUNTER"
        }
        font.draw(batch, hint, 20f, boardBottom - 28f)
        batch.end()
        if (snapshot.phase == GamePhase.PLAYING) {
            drawButtonLabel("Ⅱ  PAUSE", pauseButtonBounds(), cyan)
        }

        if (snapshot.phase == GamePhase.PAUSED ||
            snapshot.phase == GamePhase.GAME_OVER ||
            snapshot.phase == GamePhase.VICTORY
        ) {
            drawPhaseOverlay(snapshot.phase, width, height)
        }
    }

    private data class UiBounds(val x: Float, val y: Float, val width: Float, val height: Float)

    private fun pauseButtonBounds() = UiBounds(
        x = Gdx.graphics.width - 132f,
        y = Gdx.graphics.height - 58f,
        width = 112f,
        height = 38f,
    )

    private fun primaryButtonBounds() = UiBounds(
        x = Gdx.graphics.width / 2f - 92f,
        y = Gdx.graphics.height / 2f + 36f,
        width = 184f,
        height = 44f,
    )

    private fun secondaryButtonBounds() = UiBounds(
        x = Gdx.graphics.width / 2f - 92f,
        y = Gdx.graphics.height / 2f - 20f,
        width = 184f,
        height = 44f,
    )

    private fun tertiaryButtonBounds() = UiBounds(
        x = Gdx.graphics.width / 2f - 92f,
        y = Gdx.graphics.height / 2f - 76f,
        width = 184f,
        height = 44f,
    )

    private fun isInside(x: Float, y: Float, bounds: UiBounds) =
        x >= bounds.x && x <= bounds.x + bounds.width &&
            y >= bounds.y && y <= bounds.y + bounds.height

    private fun drawButtonLabel(label: String, bounds: UiBounds, color: Color) {
        shapes.projectionMatrix = camera.combined
        shapes.begin(ShapeRenderer.ShapeType.Filled)
        shapes.color = color.cpy().also { it.a = 0.18f }
        shapes.rect(bounds.x, bounds.y, bounds.width, bounds.height)
        shapes.end()
        batch.projectionMatrix = camera.combined
        batch.begin()
        font.color = color
        font.data.setScale(1f)
        font.draw(batch, label, bounds.x + 18f, bounds.y + 27f)
        font.color = Color.WHITE
        batch.end()
    }

    private fun drawPhaseOverlay(phase: GamePhase, width: Float, height: Float) {
        val panelWidth = minOf(width - 40f, 300f)
        val panelHeight = if (phase == GamePhase.PAUSED) 252f else 196f
        val panelX = (width - panelWidth) / 2f
        val panelY = (height - panelHeight) / 2f
        shapes.projectionMatrix = camera.combined
        shapes.begin(ShapeRenderer.ShapeType.Filled)
        shapes.color = Color(0.008f, 0.016f, 0.03f, 0.94f)
        shapes.rect(panelX, panelY, panelWidth, panelHeight)
        shapes.color = when (phase) {
            GamePhase.PAUSED -> amber
            GamePhase.GAME_OVER -> red
            else -> green
        }
        shapes.rect(panelX, panelY + panelHeight - 3f, panelWidth, 3f)
        shapes.end()

        batch.projectionMatrix = camera.combined
        batch.begin()
        font.color = when (phase) {
            GamePhase.PAUSED -> amber
            GamePhase.GAME_OVER -> red
            else -> green
        }
        font.data.setScale(1.55f)
        val title = when (phase) {
            GamePhase.PAUSED -> "PAUSED"
            GamePhase.GAME_OVER -> "CAUGHT"
            else -> "VICTORY"
        }
        font.draw(batch, title, panelX + 24f, panelY + panelHeight - 30f)
        font.color = Color.WHITE
        font.data.setScale(0.85f)
        val subtitle = when (phase) {
            GamePhase.PAUSED -> "Game clock is stopped"
            GamePhase.GAME_OVER -> "The hunters caught you"
            else -> "Exit reached"
        }
        font.draw(batch, subtitle, panelX + 24f, panelY + panelHeight - 58f)
        batch.end()

        drawButtonLabel(
            if (phase == GamePhase.PAUSED) "▶  RESUME" else "↻  PLAY AGAIN",
            primaryButtonBounds(),
            cyan,
        )
        if (phase == GamePhase.PAUSED) {
            drawButtonLabel("↻  RESTART", secondaryButtonBounds(), amber)
            drawButtonLabel("MAIN MENU", tertiaryButtonBounds(), Color.WHITE)
        } else {
            drawButtonLabel("MAIN MENU", secondaryButtonBounds(), Color.WHITE)
        }
    }

    private fun drawCell(point: GridPoint, color: Color, insetRatio: Float) {
        val inset = cell * insetRatio
        val x = boardLeft + point.x * cell + inset
        val y = boardBottom + (7 - point.y) * cell + inset
        shapes.color = color
        shapes.rect(x, y, cell - inset * 2f, cell - inset * 2f)
    }

    // -------------------------------------------------------------------------
    // Lifecycle
    // -------------------------------------------------------------------------

    override fun pause() {
        Log.d(TAG, "ScreenAdapter.pause() — backgrounding session")
        session.onHostBackgrounded()
    }

    override fun resume() {
        Log.d(TAG, "ScreenAdapter.resume() — foregrounding session")
        session.onHostForegrounded()
    }

    override fun hide() {
        Log.d(TAG, "hide() — cancelling coroutine scope and clearing input processor")
        scope?.cancel()
        scope = null
        Gdx.input.inputProcessor = null
    }

    override fun dispose() {
        Log.d(TAG, "dispose() — releasing GL resources")
        // hide() is always called before dispose() by the libGDX screen lifecycle,
        // but cancel scope defensively in case dispose() is called directly.
        scope?.cancel()
        scope = null
        Gdx.input.inputProcessor = null
        if (::shapes.isInitialized) shapes.dispose()
        if (::batch.isInitialized) batch.dispose()
        if (::font.isInitialized) font.dispose()
    }
}
