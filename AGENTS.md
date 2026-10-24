# AGENTS.md — BabyLog

Project instructions for any AI coding agent working in this repo (Claude Code, Codex, OpenCode,
Hermes). Read this before changing anything.

## What this is

A private, offline-first Android baby tracker for **one family**: two parents and their twins.
Two users, sideloaded release APK, no Play Store, no monetisation, no analytics.
It is deliberately small: it copies the core of an existing tracker and nothing else, and the whole point
is that it does *less* than the apps it replaces.

Data is the user's children's health log. Treat it as sensitive.

## Stack

Kotlin (AGP 9 **built-in** Kotlin — do NOT apply an `org.jetbrains.kotlin.android` plugin) ·
Jetpack Compose + Material 3 · Room (source of truth) · DataStore (preferences) · Firebase
Auth/Firestore (sync, later phases). `compileSdk 37` + `compileSdkMinor 2`, `targetSdk 36`,
`minSdk 26`, JDK = Android Studio's bundled JBR.

Three AGP 9 facts that have already cost build cycles: the `kotlin.android` plugin must **not** be
applied (AGP 9 carries Kotlin itself); `compileSdkMinor` is required to express `android-37.2`; and
`jvmToolchain(17)` fails on a JBR-only machine, so the toolchain is the bundled JDK 25.

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

## Git workflow

**One branch per phase or task, cut from `main`, and `main` is never edited directly unless I say so for
that task.** Creating the branch needs no approval — it publishes nothing — but commits, merges and
pushes do.

- **Name it `<type>/<slug>`**, with the type prefix from the commit convention this repo already uses:
  `feat/phase12-trends`, `fix/fever-pill-legibility`, `docs/git-workflow-rule`, `chore/release-signing`,
  `refactor/…`, `test/…`. Use the phase number when the work is a phase, a short slug otherwise.
- **Land it with a fast-forward merge, only after I approve:** `git switch main && git merge --ff-only
  <branch>`, then `git push origin main`. No merge commits — `main` stays linear, which is what makes
  `git log --oneline` still readable as a history of phases.
- **Delete the branch AFTER the push lands, never before** — `git branch -d <branch>`. Deleting first
  drops the only reference to work that is not yet on the remote.
- A phase's `CHANGELOG.md` entry and its `PLAN.md` update ride the same branch, in the same pass as the
  phase commit (see the table under *Where things are*).
- `origin/Timothys-Take` is a collaborator's branch. It is not mine to merge, rebase, or delete.

## Verify on the device

**The app id is `app.babylog`.** A build from this history is a *different app* to one installed from
before that id, so it installs beside the old one and the existing log moves across with
Settings → Data → Export, then Import in the new build. Never run both side by side against the same
log — they keep separate databases, and only one of them is the one being logged into.

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
- Screenshots are captured by `scripts/capture-timeline.ps1` into a folder beside the repo, not committed.

## Design contract — measured, not taste

`app/src/main/java/app/babylog/ui/theme/Tokens.kt` is the source of truth, and the measured derivations
are recorded in the comments there and in `PLAN.md`. Key decisions:

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
- **A temperature band is marked by a SHAPE, not a hue.** `domain/Temperature.kt` classifies a reading
  into LOW (`< 36.5`), NORMAL (`36.5–37.5`), MILD_FEVER (`37.6–37.9`) and FEVER (`>= 38.0`, i.e.
  `FEVER_C`), always from the value **as displayed** — rounded to one decimal first — so a pill can never
  disagree with the number beside it (`37.99` prints `38.0` and is therefore a fever). `TemperatureBadge`
  gives the three non-normal bands ONE geometry so no row shifts: FEVER is filled `feverOnCard` with the
  word in `onAccent` ("Fever"), MILD_FEVER and LOW are outlined ~1.5dp in `feverOnCard` with the word in
  `feverOnCard` ("Mild", "Low"). Only FEVER moves the number off `textPrimary` — one strong cue per row.
  `danger` is NOT usable on a tinted card: it measures 3.76:1 on the 26% tint, below AA — so
  `feverOnCard` exists for tinted surfaces (fill and outline both 5.71:1 on the Health card, 4.80:1 worst
  of the six; 10.39:1 for the word on the fill). A tinted number was measured legible at 5.20:1 and was
  still invisible on the phone, which is why the marker has a shape and the word inside it.
- **Each card is watermarked with its activity's OWN GLYPH, tiled tone-on-tone.** Six stroke drawables
  (`res/drawable/ic_glyph_*`) go through `EventType.glyphRes()`; `Modifier.activityPattern()` fills the card
  with the glyph repeated at 26dp on a 64dp pitch, alternate rows offset half a pitch, and the same glyphs
  sit solid at 14dp inside the activity chips. The pattern colour is `patternFor(accent)` — the card fill
  pushed **0.14** toward its own accent (dE 7–11 dark, 7–10 light), NEVER white: white over a 26% tint
  measures dE 3 and 4.2:1 at alpha 0.04, and is invisible in the light theme. Because the pattern sits
  behind every word, `textOnCard` was lifted (`#DCDEE7` / `#404E69`) so meta text still clears 4.5:1 on a
  tile; `PatternPaletteTest` asserts all of it for six accents × both themes. Raising the alpha, retuning an
  accent, or lowering the tint goes through that test.

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
- **A day with no records is a zero, and the screen says so.** Trends draws a missing day as 0 and
  captions it `Days with no records count as zero.` The app cannot tell "no nappies" from "nobody logged
  one", so it must not draw a gap and imply it knows. The same rule forbids guessing a running sleep's
  duration or an unrecorded bottle's volume: an average that is real but rounds away reads `< 0.1`, never
  `0`.
- **Two children on one chart are told apart by shape as well as shade** — the first line solid, the
  second dashed. Two shades of one accent were not enough to tell the twins apart on a phone, and a
  second hue is reserved for a second activity.
- **Adjusted age is a named function on purpose** (`ageDaysForCharts()`). The twins were born
  prematurely; using chronological age for a percentile chart yields a confident wrong
  answer with no error.
- **Screen off if intended: outbox stores intent, not payload** — a stored JSON copy drifts as soon as
  the row is edited again; the drain re-reads by id.
- Soft-delete only (`deleted` tombstone); sync must propagate deletions.
- **A dose is two columns that must move together.** `doseAmount` and `doseUnit` are both set or both
  null, added as nullable columns in the v1 → v2 migration — the app's first schema change. Half a dose
  is not a dose, and a restore that lost the unit would restore "2.5" of nothing.
- **The bundled medicine list is NAMES ONLY.** `MedicationCatalogueTest` fails the build if any entry
  contains a digit, and the health sheet itself says `Names only — this app does not suggest a dose`. An
  infant dose depends on weight and age, so the app must never propose one.

## Sync rules (Phase 13) that will cost you if you forget them

- **Later `updatedAt` wins, and a deletion wins a tie.** `domain/SyncMerge.kt` is the only place that
  decides it, and it is pure. A tie going to the live row resurrects a deleted bottle with nobody having
  logged it.
- **The outbox stores intent, never a payload**, and the drain re-reads the row by id at send time. The
  one exception is a *permanent* delete, whose row is already gone: it travels as an id-and-timestamp
  tombstone, which is why `OutboxDrainer` tries the row first and falls back to the tombstone.
- **A tombstone for an id this phone never had is not an instruction to create a row.** There is nothing
  to delete, and inventing a child from a document with no name would corrupt the percentile maths.
- **Firestore's disk cache is off** (`MemoryCacheSettings`). With the default cache, `set()` acks as soon
  as the write is in Firestore's local cache, so an offline drain would delete the outbox row while
  nothing had reached the server. Room + `outbox` is the only queue.
- **Every write path queues.** `EventRepository`, `ChildRepository`, `MaintenanceRepository`. Children
  once did not, and a rename would have stayed on the phone that made it.
- **`google-services.json` and `firebase.properties` are local-only and gitignored.** The build reads the
  four values into `BuildConfig` instead of using the `google-services` plugin, because that plugin fails
  the build when its JSON is missing — which would break a fresh clone's test run.
- **An import re-seeds the queue** (`reseedAfterRestore`). `replaceAll` wipes the tables *and* the outbox,
  so without this the next snapshot would merge the pre-restore log back in.

## Tests

417 unit tests, all on the JVM (Robolectric for Room/Android). Run them with the build command above.

**Firestore cannot be unit-tested here** (no emulator, and Robolectric cannot reach it), so sync logic is
written against `data/sync/SyncGateway` and `AccountAuthenticator` and driven by `FakeSyncGateway` /
`FakeAuthenticator`. Only Firestore itself needs two real phones.

**Robolectric limit:** it cannot collect a Room-backed Flow — the paused main looper never pumps
Room's invalidation callbacks, so a test like that hangs. Test ViewModels against fake DAOs, not Room.

## Versioning

**One version per phase, set at the first commit of that phase's work.** A phase is delivered as one
branch, so every build made while it is under way then says which phase produced it — which is the only
question this number has ever been asked. `0.MINOR` is a new capability, `0.PATCH` is fixes and polish
only, and `1.0.0` is the first release.

| Phase | Version | Phase | Version |
|---|---|---|---|
| 1 scaffold | `0.1.0` | 9.1 cards and edits | `0.5.0` |
| 5 launch-crash fix | `0.1.1` | 10 Growth | `0.6.0` |
| 6 logging sheets | `0.2.0` | 11 Health | `0.7.0` |
| 7 Settings | `0.3.0` | 12 Trends | `0.8.0` |
| 8 Summary | `0.4.0` | 13 two accounts | `0.9.0` |
| 9 daily-use polish | `0.4.1` | 13.1 time/temp preferences | `0.10.0` |
| | | 13.1.1 twelve-hour default | `0.10.1` |
| | | 13.2 temperature bands | `0.11.0` |
| | | 13.3 dataset channel | `0.12.0` |
| | | 13.4 reset to defaults | `0.13.0` |
| | | 14/15 release | `1.0.0` |

- **`versionCode` is a sequential counter**, one per rung (`0.1.0` → `1` … `0.8.0` → `10`). It only ever
  goes up, because App Distribution decides "newer" from it.
- **Each dated `CHANGELOG.md` section carries a `### Versions` list** naming the versions that landed
  that day, directly after `**In short:**`. That file is the ladder's record — do not put a second copy
  in the README.
- **Nothing is tagged until a release.** Tags are for released builds, so the first tag is `v1.0.0` when
  the release APK is delivered in Phase 14. A phase ending is not a release.

## Where things are

| Path | What |
|---|---|
| `PLAN.md` | Phase plan, scope decisions, and the log of bugs found on-device |
| `CHANGELOG.md` | Dated record of every day of work — a phase's entries go under the day it landed, written in the same pass as the phase commit |
| `scripts/` | `android-env.ps1`, `sdkmanager.ps1`, `capture-timeline.ps1`, `build-growth-assets.py` |
| `firebase/firestore.rules` | Phase 13 security rules — pasted into the console by hand, **not** deployed by a tool |
| `docs/firebase-setup.md` | Phase 13's console runbook: the project, the two accounts, the four `firebase.properties` values, and what each misconfiguration looks like on screen |

## Time and temperature preferences (owner override, 2026-10-23)

The owner requested explicit 12/24-hour and Celsius/Fahrenheit preferences. This supersedes
older Celsius-only guidance. Store temperatures in Celsius and timestamps unchanged; convert
only at display/input boundaries. Fever classification continues to use `FEVER_C`.
