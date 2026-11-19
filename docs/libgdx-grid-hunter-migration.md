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

The pause, game-over, and victory overlays now expose a Main Menu action. Because this prototype remains a separately launched Android Activity, the action finishes that Activity and returns to its caller; it does not introduce a production navigation dependency or change the Compose launcher. The Activity still uses EASY as a temporary prototype default until difficulty selection is wired deliberately.
