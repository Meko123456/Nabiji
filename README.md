# Nabiji 👣

[![CI](https://github.com/Meko123456/Nabiji/actions/workflows/ci.yml/badge.svg)](https://github.com/Meko123456/Nabiji/actions/workflows/ci.yml)
[![License: MIT](https://img.shields.io/badge/License-MIT-green.svg)](LICENSE)

**ნაბიჯი** (*nabiji* — Georgian for "step") — a quiet daily activity dashboard built on
**Health Connect**: today's steps against your goal, a year of activity as a heatmap, your
goal streak, and a home-screen widget. No account, no cloud, no step-counting of its own —
it reads what your phone and watch already record.

## Why this app exists

Every step app wants an account, a subscription and a social feed. I want one screen: did I
move today, and am I keeping it up? Health Connect already holds the data that Samsung Health,
Fitbit and the phone's own sensor write, so the honest app is a *reader* — no background
service, no battery drain, no second source of truth.

It is also the second consumer of my published
[`heatmap`](https://github.com/Meko123456/heatmap-compose) library, which draws the year of
daily activity.

## Features (planned — see the [issues](https://github.com/Meko123456/Nabiji/issues))

- 👣 **Today** — steps, distance and active calories against a goal you set, with a progress ring.
- 🔥 **Goal streak** — consecutive days you hit the target. A goal not yet met *today* doesn't
  wipe the streak; it is measured to yesterday until the day is done.
- 🟩 **Activity heatmap** — a year of daily steps drawn with the `heatmap` library.
- 📊 **Week & month** — totals, daily average, best day, and how many days met the goal.
- 📱 **Glance widget** — today's steps and ring on the home screen.
- 🔐 **Permission-first** — reads nothing until you grant it in Health Connect, and degrades to a
  clear explanation when Health Connect is unavailable or permissions are refused.
- 🔒 **Private** — data never leaves the device; the app has no network permission at all.
- 🎨 **Material 3** — dynamic color, light/dark, edge-to-edge.

## Architecture

```
domain/   pure Kotlin, unit-tested — StepGoal (progress/streak arithmetic), DayActivity,
          ActivitySummary (totals, averages, best day, goal streak, heatmap counts, gap filling)
data/     Health Connect client wrapper + DataStore preferences (the goal)
ui/       Compose — dashboard, permission flow, settings
widget/   Glance home-screen widget
```

The `domain/` layer has no Health Connect imports: it works on plain `DayActivity` values, so
every number on the dashboard — streaks, averages, progress — is testable without a device.

## Health Connect notes

- Health Connect is **part of the framework from Android 14**; below that it is a separate app,
  so the UI has an "unavailable" state rather than assuming it is there.
- Permissions (`READ_STEPS`, `READ_DISTANCE`, `READ_TOTAL_CALORIES_BURNED`) are granted inside
  Health Connect itself, not with a normal runtime dialog.

## Building

```
./gradlew :app:assembleDebug :app:testDebugUnitTest
```

Kotlin 2.3, AGP 9, Compose BOM 2026.06, minSdk 26, Health Connect client 1.1, Glance 1.1.

## License

[MIT](LICENSE) © 2026 Merab Kochlamazashvili
