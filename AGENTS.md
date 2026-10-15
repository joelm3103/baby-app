# AGENTS.md — BabyLog

Project instructions for any AI coding agent working in this repo (Claude Code, Codex, OpenCode,
Hermes). Read this before changing anything.

## What this is

A private, offline-first Android baby tracker for **one family**: the owner, the second parent and their twins
**the twins**. Two users, sideloaded release APK, no Play Store, no monetisation, no analytics.
It is deliberately small: the model to copy is **the incumbent tracker**, and the whole point is that it does
*less* than the apps it replaces.

Data is the user's children's health log. Treat it as sensitive.

## Stack

Kotlin (AGP 9 **built-in** Kotlin — do NOT apply an `org.jetbrains.kotlin.android` plugin) ·
Jetpack Compose + Material 3 · Room (source of truth) · DataStore (preferences) · Firebase
Auth/Firestore (sync, later phases). `compileSdk 37` + `compileSdkMinor 2`, `targetSdk 36`,
`minSdk 26`, JDK = Android Studio's bundled JBR.

Read `app/src/main/java/app/babylog/` in this order: `domain/` → `data/local/` →
`data/repo/` → `ui/`.

## Commands

```bash
export JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'   # or Studio's JBR path
./gradlew :app:testDebugUnitTest :app:assembleDebug                   # ~1-4 min
```

**Never report a phase done on a green build.** A green build proves the code type-checks and nothing
about runtime or layout. See "Verify on the device" below — that is not optional.

### Reading a Gradle result

The log line is the truth, not the process exit code:

```bash
./gradlew :app:testDebugUnitTest :app:assembleDebug > logs/test.log 2>&1
RES=$?; echo "GRADLE_EXIT=$RES" >> logs/test.log; exit $RES
grep -a "GRADLE_EXIT" logs/test.log            # -a: Gradle logs look binary to grep
grep -a -oE 'tests="[0-9]+" skipped="[0-9]+" failures="[0-9]+" errors="[0-9]+"' \
    app/build/test-results/testDebugUnitTest/*.xml    # authoritative counts
```

Do not append `echo` after the build when running it in the background — the process status then
reflects the echo and a FAILED build reports exit 0.

## Verify on the device

The emulator was deliberately skipped. A real phone is the feedback loop (~2 min per cycle):

```bash
ADB="C:/Users/<you>/AppData/Local/Android/Sdk/platform-tools/adb.exe"   # NATIVE path
"$ADB" install -r app/build/outputs/apk/debug/app-debug.apk
"$ADB" shell am start -n app.babylog/.MainActivity
"$ADB" logcat -d | grep -a -A30 "FATAL EXCEPTION"
"$ADB" exec-out screencap -p > shot.png
```

Rules learned the hard way:

- **Check the foreground first, and refuse to send input if it is not the app.** `dumpsys activity
  activities | grep topResumedActivity`. A scripted `input swipe` once landed on an active phone call.
- **Tap real coordinates from `uiautomator dump`, never guessed pixels.** Icon buttons appear as
  `content-desc`, not `text` — matching on text alone silently misses every icon.
- **`adb` is a native binary: `/c/Users/...` fails with `failed to stat`.** Pass `C:/Users/...`.
- `logcat -b crash -d` still holds a crash trace **after the app is uninstalled**.
- Screenshots are kept in `docs/timeline/` — see `scripts/capture-timeline.ps1`.

## Design contract — measured, not taste

`app/src/main/java/app/babylog/ui/theme/Tokens.kt` is the source of truth, and
`.hermes/skills/babylog-ui/references/design-tokens.md` holds the derivations. Key decisions:

- **One hue per activity type, never reused.** Dark accents: Bottle `#FFC857`, Solids `#FF7A5C`,
  Diaper `#5AD98A`, Growth `#B49CFF`, Health `#FF7BC0`, Sleep `#63C8FF`.
- **Cards ARE tinted** (accent at 26% over `surface`), because full-section tint is what identifies a
  section at a glance. 26% is measured: 0.16 is only dE 7.7 apart (barely separable), 0.26 is dE 12.5,
  and above 0.30 the accent-filled button starts washing out.
- **The raw accent is NOT legible as a label on its own tint** (3.4-4.3:1). Labels use
  `BabyLogColors.labelFor(accent)` (accent lifted toward white on dark, deepened on light) — 6.4:1.
- **Meta text on a card uses `textOnCard`, never `textSecondary`** (which measures 3.06:1 on the
  stronger tints) and never a `MaterialTheme.colorScheme.*` default.
- Action buttons are filled with the **raw accent** and labelled in `onAccent` (5.9-11.2:1).
- Verify any colour change numerically before shipping it. Do not eyeball.

## Domain rules that already cost us

- **`endedAt == null` does NOT mean "running".** Only duration-capable types can run:
  `isRunning = type.hasDuration && endedAt == null`. Treating a null end as "in progress" put a live
  timer on every card, because a point event has no end either.
- **Never store a nested `object` in a companion `val`.** `val default: ChildSelection = Both` read
  **null** on Android: ART initialises the enclosing interface (and its companion) *while* `Both` is
  still being constructed, so it captured the not-yet-assigned INSTANCE. HotSpot does not do this, so
  **no JVM or Robolectric test can catch it** — it crashed on launch with 97 tests green. Use
  `val default: ChildSelection get() = Both`.
- **`x ?: fallback` is compiled away when `x`'s declared type is non-null.** Widen to a nullable local
  first or the guard does nothing.
- **Totals are NOT computed in SQL.** Day boundaries and the day/night diaper split depend on the
  local timezone, which SQLite cannot resolve; aggregating in SQL would silently file 8pm diapers under
  the wrong day. Totals belong in pure Kotlin.
- **Adjusted age is a named function on purpose** (`ageDaysForCharts()`). the twins were born at 30
  weeks (premature); using chronological age for a percentile chart yields a confident wrong
  answer with no error.
- **Screen off if intended: outbox stores intent, not payload** — a stored JSON copy drifts as soon as
  the row is edited again; the drain re-reads by id.
- Soft-delete only (`deleted` tombstone); sync must propagate deletions.

## Do not build

Photos · homescreen widgets · nursing/pumping tracking · content feeds · a shop · any paywall or
subscription · analytics · a clinical taxonomy of diaper or food types. All were explicitly rejected.
the incumbent's 3.58/5 rating is driven by a forced subscription; that is the failure mode to avoid.

## Tests

178 unit tests, all on the JVM (Robolectric for Room/Android). Run them with the build command above.

**Robolectric limit:** it cannot collect a Room-backed Flow — the paused main looper never pumps
Room's invalidation callbacks, so a test like that hangs. Test ViewModels against fake DAOs, not Room.

## Where things are

| Path | What |
|---|---|
| `PLAN.md` | Phase plan, scope decisions, and the log of bugs found on-device |
| `CHANGELOG.md` | Dated record of every day of work — a phase's entries go under the day it landed, written in the same pass as the phase commit |
| `docs/timeline/` | Screenshot history — open `docs/timeline/index.html` |
| `research/` | the incumbent tracker/the free alternative teardown, WHO growth data, toolchain traps |
| `.hermes/skills/babylog-ui/` | The design contract (tokens, components, layout, style guide) |
| `scripts/` | `android-env.ps1`, `sdkmanager.ps1`, `capture-timeline.ps1` |
