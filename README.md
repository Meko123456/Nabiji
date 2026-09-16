# Nabiji 👣

[![CI](https://github.com/Meko123456/Nabiji/actions/workflows/ci.yml/badge.svg)](https://github.com/Meko123456/Nabiji/actions/workflows/ci.yml)
[![License: MIT](https://img.shields.io/badge/License-MIT-green.svg)](LICENSE)

**ნაბიჯი** (*nabiji* — Georgian for "step") — a quiet daily activity dashboard built on
**Health Connect**: today's steps against your goal, the last 26 weeks of activity as a heatmap, your
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

## Features

- 👣 **Today** — steps, distance and active calories against a goal you set, with a progress ring.
- 🔥 **Goal streak** — consecutive days you hit the target. A goal not yet met *today* doesn't
  wipe the streak; it is measured to yesterday until the day is done.
- 🟩 **Activity heatmap** — the last 26 weeks of daily steps, drawn with the `heatmap` library.
- 📊 **This week** — total, daily average, best day, and how many of the seven days met the goal.
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

Kotlin 2.4.10, AGP 9.1.1, Gradle 9.7.1, Compose BOM 2026.06, minSdk 26, Health Connect client 1.1, Glance 1.1.

## Building a release

The release build is minified and resource-shrunk by R8. Health Connect and Glance both resolve
classes reflectively, so it is the only build where stripping can break the app — CI therefore
runs `:app:assembleRelease` and `:app:lintVitalRelease` on every push rather than debug alone.
No app-specific keep rules are needed; the libraries' own consumer rules cover it.

For a **signed** APK, copy `keystore.properties.example` to `keystore.properties` (git-ignored)
and point it at your keystore, or set the `NABIJI_KEYSTORE_*` environment variables. With
neither present the release build still compiles — it just produces an unsigned APK, so a fresh
checkout and CI both keep working.

Releases are cut by tag: pushing `v0.1.0` runs `.github/workflows/release.yml`, which derives
`versionName` and `versionCode` from the tag, builds and signs the APK, refuses to continue if
it came out unsigned or unaligned, keeps the R8 `mapping.txt` for 90 days, and attaches the APK
to a GitHub release. `workflow_dispatch` does the same as a dry run without publishing anything.
It needs four repository secrets: `NABIJI_KEYSTORE_BASE64`, `NABIJI_KEYSTORE_STORE_PASSWORD`,
`NABIJI_KEYSTORE_KEY_ALIAS` and `NABIJI_KEYSTORE_KEY_PASSWORD`.

## License

[MIT](LICENSE) © 2026 Merab Kochlamazashvili
