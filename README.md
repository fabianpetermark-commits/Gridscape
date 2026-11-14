# mini-games

Modular Android arcade & logic game collection built with **Kotlin**, **Jetpack Compose**, and **Android Canvas**.

## Architecture

- `:app` – Navigation shell, menus, stats, and game launcher registry
- `:core:models` – Pure-Kotlin domain models (`GridPoint`, `Direction`, `GamePhase`, `Difficulty`, `GameStats`, `GameDescriptor`)
- `:core:engine` – Deterministic `GameEngine` state machine, `IntervalScheduler`, and lifecycle-aware `GameSession`
- `:core:input` – Single-slot command buffer and cardinal touch/swipe gesture modifiers
- `:core:rendering` – Shared neon theme tokens and Canvas grid scaling utilities
- `:core:storage` – Local persistence for settings and per-difficulty statistics
- `:games:grid-hunter` – Reference game implementation (6×6 grid, BFS pathfinding, Pursuer/Interceptor AI, procedural level generator)
