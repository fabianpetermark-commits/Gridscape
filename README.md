# Gridscape

A single-player Android grid strategy game built with **Kotlin**, **Jetpack Compose**, and **Android Canvas**.

## Architecture

- `:app` – Main menu, settings, difficulty selection, and gameplay navigation
- `:core:models` – Pure-Kotlin domain models (`GridPoint`, `Direction`, `GamePhase`, `Difficulty`, `GameStats`, `GameDescriptor`)
- `:core:engine` – Deterministic `GameEngine` state machine, `IntervalScheduler`, and lifecycle-aware `GameSession`
- `:core:input` – Single-slot command buffer and cardinal touch/swipe gesture modifiers
- `:core:rendering` – Shared neon theme tokens and Canvas grid scaling utilities
- `:core:storage` – Local persistence for settings and per-difficulty statistics
- `:games:grid-hunter` – Gridscape game engine and presentation (8×8 grid, BFS pathfinding, Pursuer/Interceptor AI, procedural level generator)

## CI / Automated APK Build

GitHub Actions (`.github/workflows/android-build.yml`) automatically runs unit tests and builds a sideloadable debug APK on every push to `main`/`master`, pull request, or manual `workflow_dispatch` run. Download the compiled `gridscape-debug-apk` artifact directly from the **Actions** tab.
