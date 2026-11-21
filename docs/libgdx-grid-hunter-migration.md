# Grid Hunter libGDX Migration Plan

## Goal

Introduce libGDX as an alternative rendering and input host for Grid Hunter while preserving the existing Kotlin game rules, state model, engine, difficulty configuration, persistence, and test suite.

This is an incremental migration. The current Jetpack Compose implementation remains the production default until the libGDX prototype passes parity and device testing.

## Current architecture findings

- `:core:models` contains shared value types such as `GridPoint`, `Direction`, `Difficulty`, and `GamePhase`.
- `:core:engine` exposes the generic `GameEngine<S, C, Config>` contract and `GameSession`. The session owns the immutable state as a `StateFlow`, accepts commands, advances the simulation through `stepClock()`, and supports pause/resume/reset.
- `:games:grid-hunter` contains `GridHunterEngine`, `GridHunterState`, `GridHunterCommand`, AI, pathfinding, level generation, and difficulty configuration.
- The existing Compose screen owns a dedicated 16 ms clock loop; the libGDX prototype now mirrors this with its own coroutine, independent of render frame rate.
- `:core:rendering` is currently Compose-specific and should not become a dependency of the libGDX renderer.

## Non-negotiable parity rules

1. Do not change `GridHunterLogic.kt` gameplay rules as part of the renderer migration.
2. Preserve all four difficulty configurations and progressive-level thresholds.
3. Preserve the existing deterministic logic tests and add adapter/render-host tests where practical.
4. Keep the Compose implementation available as a fallback until the new host is verified on a real Android device.
5. Rendering frame rate must never determine enemy movement speed or game rules. The existing `GameSession` clock remains the simulation authority.
6. Translate input into existing `GridHunterCommand.Move(Direction)` commands; do not implement movement or AI in libGDX.

## Target boundary

- Compose remains responsible for the app shell: main menu, difficulty selection, settings, and navigation.
- A dedicated libGDX host is responsible for the Grid Hunter play surface: board, entities, obstacles, exit, HUD, pause/game-over overlays, touch controls, and visual effects.
- The host reads immutable `GridHunterState` snapshots from the existing `GameSession.state` flow and dispatches existing commands.
- The host must call `stepClock()` on a controlled cadence while playing, and pause/resume the session with Android lifecycle events.

## Delivery phases

### Phase 1 — isolated rendering spike

- Add a dedicated libGDX Android host/module without replacing the current screen.
- Render the existing 8x8 board geometry and current state entities.
- Add basic touch/D-pad command mapping.
- Verify Android build, app launch, lifecycle pause/resume, and frame pacing.
- Keep this phase visually simple; prove the integration boundary first.

### Phase 2 — gameplay parity

- Connect the real `GridHunterEngine` and `GameSession`.
- Render player, Pursuer, Interceptor, exit, and obstacles from the live state.
- Implement ready, paused, victory, and game-over overlays.
- Verify all existing difficulty modes and progressive mode without modifying their configuration.
- Compare behavior against the Compose implementation.

### Phase 3 — visual treatment

- Add the neon board frame, entity treatments, glow, exit animation, movement trails, particles, and restrained screen feedback.
- Keep effects cosmetic and avoid changing collision geometry or timing.
- Test performance on representative Android hardware and provide a reduced-effects fallback if needed.

### Phase 4 — controlled rollout

- Add an explicit app-level choice or internal feature flag for the libGDX host.
- Keep Compose as fallback until device testing and regression checks pass.
- Switch the default only after approval and successful CI/device verification.

## Acceptance criteria

- Existing Grid Hunter logic tests pass unchanged.
- The libGDX host uses the existing engine, state, and command types.
- No duplicate AI, pathfinder, level generator, or difficulty implementation is introduced.
- Pause/background/foreground behavior does not cause catch-up bursts or unintended enemy moves.
- Touch input maps to the same four cardinal directions and respects game phase.
- The existing Compose route remains usable as a fallback.
- Android debug build and relevant automated tests pass before any production switch.

## Current status

Branch created from `main` for the isolated prototype. The clock audit found that libGDX was calling `stepClock()` from `render()`, unlike Compose's dedicated 16 ms loop. This was corrected: libGDX now runs a dedicated 16 ms clock coroutine, separate from rendering. No gameplay logic or production rendering has been changed.


## Lifecycle parity audit (follow-up)

The Compose and libGDX hosts were compared directly:

- Both use the same `GameSession.onHostBackgrounded()` behavior, which pauses a PLAYING session.
- Both call `onHostForegrounded()` to reset the clock timestamp on return; neither automatically resumes a PAUSED game.
- Both use a dedicated 16 ms clock coroutine and do not tie simulation time to rendering.
- The libGDX tap action resumes from PAUSED, matching the Compose overlay's Resume action.
- Because `stepClock()` clears its timestamp whenever the session is not PLAYING, background time cannot accumulate into a catch-up burst.

No lifecycle code change was needed in this audit.

### Remaining parity gaps identified (not changed)

- The libGDX prototype has no explicit pause button; Compose does. Android backgrounding still pauses correctly.
- The prototype's PAUSED and GAME_OVER/Victory interaction is tap-based and visually minimal; Compose uses explicit overlay actions.
- The prototype has no dedicated victory overlay, although the state/hint reports victory and tap resets.
- The isolated Activity currently starts with EASY hardcoded. It is not yet wired to the production difficulty-selection/navigation flow.

These are prototype/UI integration gaps, not gameplay-rule changes. Device testing remains required.

## Explicit pause and phase overlays

The isolated libGDX host now has an on-screen Pause control while PLAYING. PAUSED displays explicit Resume and Restart hit targets; GAME_OVER and VICTORY display distinct status panels with a Play Again target. UI taps are checked before board gestures, so tapping a control cannot become a movement command. All actions call the existing `GameSession` methods and preserve the shared engine/state model. The overlay work does not change enemy behavior, movement, difficulty, or level generation.

The prototype intentionally does not expose a Main Menu action because it is still an isolated Activity and is not connected to production navigation. Device-level touch/layout verification remains outstanding.

### Return navigation in the isolated host

The pause, game-over, and victory overlays now expose a Main Menu action. Because this prototype remains a separately launched Android Activity, the action finishes that Activity and returns to its caller; it does not introduce a production navigation dependency or change the Compose launcher. The Activity uses EASY only when it is launched without a valid difficulty extra.

### Difficulty handoff contract

The isolated Activity now accepts the selected difficulty through `GridHunterLibGdxActivity.createIntent(context, difficulty)`. The value is serialized by enum name and validated on receipt; missing or unknown values fall back to EASY. This establishes a typed launch contract for a future app-shell integration without changing the current Compose production route or launcher. The prototype remains independently launchable for isolated testing.


### Internal prototype test entry

The Compose difficulty-selection screen now exposes a small **LIBGDX TEST** action on each individual difficulty card. It launches the isolated Activity with that card's exact difficulty through the typed intent helper. The normal card tap still launches the existing Compose game, and progressive mode remains Compose-only. This is a deliberate test entry point, not a production renderer switch; the Compose launcher and gameplay route remain the default. The Activity is registered as non-exported, so it can only be launched from inside the app. Device verification of the actual handoff and play session is still required.

---

## Crash fix audit — 2026-11-20

### Root cause (confirmed by static analysis)

`GridHunterPrototypeScreen` declared `ShapeRenderer`, `SpriteBatch`, `BitmapFont`, and
`OrthographicCamera` as class-level `val` properties. In Kotlin, class-level `val` properties
are initialised inside the class initialiser block (`init {}`). This runs synchronously during
the `GridHunterPrototypeScreen` constructor call, which is triggered by `setScreen()` inside
`GridHunterLibGdxGame.create()`. At this point in the libGDX lifecycle the OpenGL context has
**not** yet been established; calling the `ShapeRenderer()`, `SpriteBatch()`, or `BitmapFont()`
constructors without a live GL context causes an immediate native crash — the first OpenGL call
into the driver returns without a valid surface, corrupting the rendering state and crashing the
process.

A secondary contributor: the coroutine scope used `Dispatchers.Main.immediate`. On the libGDX
render thread this dispatcher requires an Android `Looper`, which is not guaranteed to be
available. The `inputProcessor` was also set inside `init {}` before `Gdx.input` was ready.

### Changes made

| File | Change |
|---|---|
| `GridHunterLibGdxGame.kt` | GL objects (`ShapeRenderer`, `SpriteBatch`, `BitmapFont`, `OrthographicCamera`) promoted to `lateinit var`; created in `show()` where GL context is guaranteed. `inputProcessor` moved from `init {}` to `show()`. Coroutine scope moved to `show()` / cancelled in `hide()` and `dispose()`. Dispatcher changed from `Dispatchers.Main.immediate` to `Dispatchers.Default`; state updates posted back to the GL thread via `Gdx.app.postRunnable`. `dispose()` guards each GL object with `isInitialized`. Logcat logging added with `GridHunterLibGdx` TAG. |
| `AndroidManifest.xml` | `GridHunterLibGdxActivity` given `android:theme="@android:style/Theme.Black.NoTitleBar.Fullscreen"` (required by the libGDX Android backend for correct `GLSurfaceView` setup). Added `android:configChanges` for orientation, keyboard, and density — libGDX handles these internally. |
| `GridHunterLibGdxActivity.kt` | `Difficulty.values()` → `Difficulty.entries`. Lifecycle Logcat logging added. |
| `app/build.gradle.kts` | JUnit 5 unit test support added (`testOptions`, `testImplementation`, `testRuntimeOnly`). |
| `app/src/test/…/GridHunterIntentContractTest.kt` | New JVM unit test covering the difficulty intent serialisation contract (all four values round-trip, null/unknown/empty/lowercase fall back to EASY, constant guard). |

### Classification of each issue

| Issue | Classification |
|---|---|
| GL objects created before GL context | **Confirmed crash cause** — static analysis, libGDX lifecycle documentation |
| Theme incompatibility | **Strong technical suspicion** — well-documented libGDX requirement; cannot confirm without device log |
| `inputProcessor` in `init {}` | **Strong technical suspicion** — `Gdx.input` not guaranteed ready at that point |
| `Dispatchers.Main.immediate` | **Strong technical suspicion** — requires Android Looper, absent on libGDX render thread |
| `Difficulty.values()` deprecation | **Minor / confirmed** — Kotlin API; replaced with `Difficulty.entries` |

### What still requires a real device to verify

- That the Activity starts and the board renders without further errors.
- That touch input (swipe and tap) correctly controls the game.
- That Android lifecycle (home button → reopen, rotation lock, back button) behaves correctly.
- That `Gdx.app.postRunnable` state updates do not introduce frame stutter at 60 fps.
- That the Theme.Black.NoTitleBar.Fullscreen theme does not cause a visual regression
  (status bar, navigation bar, edge-to-edge layout) compared with the Compose MainActivity.

### No-change guarantees

- `GridHunterEngine`, `GridHunterState`, AI, pathfinding, level generation — untouched.
- All four difficulty configurations — untouched.
- Compose `MainActivity` and `GridHunterScreen` — untouched.
- Progressive mode — untouched.
- Existing `GridHunterEngineTest` suite — untouched and unaffected.
