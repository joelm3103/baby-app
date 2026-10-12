# BabyLog V1 — Implementation Plan

> **For Hermes:** load `.hermes/skills/babylog-ui/` before any task in Phases 1–7 (it is the design
> contract: tokens, components, layout, and the do-not-build list). Run `hermes skills trust` once in
> this project directory so repo-local skills load.

**Goal:** A private, offline-first Android baby tracker for two parents (the two parents) with four
one-tap loggers, a glanceable four-card dashboard, a shared synced log, and nothing else.

**Architecture:** Single-module Kotlin/Compose app. Local `Room` database is the **source of truth**;
every write lands there first and is mirrored to the UI by a `Flow`. A separate `outbox` table carries
unsynced writes to Firestore, where a realtime listener mirrors the other parent's writes back into
Room. UI is a small Compose tree driven by one `HomeViewModel`; pure logic (durations, relative-time
formatting, day grouping, outbox state) is extracted into plain Kotlin functions so it is testable
without an emulator.

**Tech Stack:** Kotlin 2.0.x · Jetpack Compose (BOM) + Material 3 · Room + KSP · DataStore (prefs) ·
Firebase Auth (Google sign-in) + Cloud Firestore · Gradle Kotlin DSL · JUnit + Robolectric.

**Granularity note (read this):** tasks below are 5–30 minutes, not the 2–5-minute ideal — several are
"create this file with this content", which is one action however long the file is. Complete code is
given for **every load-bearing file** (data model, DAO, theme, the card composable, the timer, the sync
worker). Boilerplate that `Android Studio → New Project` generates is specified, not transcribed.

**Effort, honestly:** Phases 0–7 (a working single-phone app) is roughly 2–3 focused days. Phase 8
(sync) is the longest and least predictable, mostly in Firebase console setup and auth wiring.

---

## Phase 0 — Toolchain (DONE 2026-10-12 — versions in `research/toolchain.md`)

**Resolved.** Android Studio **2026.2.1.8** ("Rabbit 1") is installed and the SDK is in place
(`platform-tools` 37.0.1, `platforms;android-36`, `build-tools;36.0.0`). Task 0.5 (the emulator) was
dropped by choice — we test on the real phones.

**What the machine actually looks like, because it differs from the original assumptions:**

| Fact | Consequence for this plan |
|---|---|
| Studio bundles **OpenJDK 25.0.3** (JBR), not 21 | JDK 25 is the Gradle JVM. **Do not pin old AGP/Gradle/Kotlin versions** (see Task 1.1). No standalone JDK is installed and none is needed |
| `java` on the command line still reports **1.8.0_461** | It resolves to `C:\Program Files (x86)\Common Files\Oracle\Java\java8path\java.exe`, an Oracle shim in the **machine** PATH. Machine entries precede user entries, so nothing at user scope can override it. Harmless: **Gradle and Studio both use `JAVA_HOME`**, which now points at the JBR. Do not "fix" this by editing PATH |
| `sdkmanager` reports itself **deprecated** | Studio 2026 ships a newer `android` CLI (`android sdk …`) in the same `cmdline-tools/latest/bin`. `sdkmanager` still works (v22.0); prefer `android sdk` for new work |
| Highest stable platform is **android-36** | `compileSdk`/`targetSdk` = 36. `build-tools;37.0.0` exists but there is no `platforms;android-37` yet |
| **No emulator or system image installed** | Deliberate. Test on the physical phones over `adb` — the real target hardware, no hypervisor setup, and the same path Phase 9 uses |

Reusable scripts live in `scripts/`: `android-env.ps1` (sets `JAVA_HOME`/`ANDROID_HOME` and fixes the
user PATH, idempotent, no admin, deliberately not `setx`) and `sdkmanager.ps1` (runs `sdkmanager` with
the right env so no quoting gymnastics are ever needed again).

Version numbers below are current-at-time-of-writing; **confirm with `winget show <id>`** rather than
trusting them, and treat the exact versions as swappable.

### Task 0.1 — Install Android Studio (brings its own JDK + SDK manager)

```bash
winget install --id Google.AndroidStudio -e --accept-package-agreements --accept-source-agreements
```
Expected: ~4–8 GB install. Launch once so it completes first-run setup and downloads a platform.

### Task 0.2 — ~~Install a standalone JDK 21~~ — SKIPPED, deliberately

Studio's bundled JBR is a complete JDK 25 and `JAVA_HOME` (user scope) now points at it:
`C:\Program Files\Android\Android Studio\jbr`. Verified: `java -version` → 25.0.3, `javac` → 25.0.3.

Install Temurin 21 only if the JBR ever needs to be decoupled from Studio's update cycle. If it is
installed later, note that the Temurin MSI writes to the **machine** PATH and that is what makes the
bare `java -version` report change.

### Task 0.3 — Set `ANDROID_HOME` and get platform tools on `PATH`

Set per-user (PowerShell, once):
```powershell
[Environment]::SetEnvironmentVariable("ANDROID_HOME", "$env:LOCALAPPDATA\Android\Sdk", "User")
```
Add `%ANDROID_HOME%\platform-tools` and `%ANDROID_HOME%\cmdline-tools\latest\bin` to the user `PATH`.

**Verify (new shell):** `adb version` and `sdkmanager --version` both respond.

### Task 0.4 — Accept licences and install the SDK pieces

```bash
sdkmanager --licenses
scripts\sdkmanager.ps1 "platform-tools" "platforms;android-36" "build-tools;36.0.0"
```
Expected: each prints `done` / `100%`.

### Task 0.5 — SKIPPED: use a real phone instead

An emulator costs ~4 GB plus a Windows Hyper-V/WHPX configuration step, and it is not the target
hardware. Instead: enable **Developer options → USB debugging** on your phone, plug it in, then

```bash
adb devices         # expect <serial>  device   (accept the RSA prompt on the phone)
```

If the phone never appears, it is a driver/authorization problem, not an SDK problem. Install an
emulator later only if a second device is genuinely needed:
`scripts\sdkmanager.ps1 "emulator" "system-images;android-36;google_apis;x86_64"`.

### Task 0.6 — Commit a toolchain record

Write the exact versions installed to `research/toolchain.md` (so a future rebuild is reproducible),
then commit. Don't guess them later — read them off `java -version`, `sdkmanager --list_installed`.

---

## Phase 1 — Project skeleton + theme

### Task 1.1 — Create the Gradle project — DONE 2026-10-12 (hand-scaffolded)

Android Studio → New Project → *Empty Activity* (Compose), name `BabyLog`,
package `app.babylog`, save **into this existing repo directory** (do not let Studio create a
nested folder or re-init git). minSdk **26**, `compileSdk`/`targetSdk` **36**.

**DONE — hand-scaffolded, no wizard.** The scaffold was written directly and builds green, so
Task 1.1's GUI step is no longer needed for a rebuild; the files are in git. The version set that
actually works, all read from the live repos rather than from memory:

| Component | Version | Why |
|---|---|---|
| AGP | **9.4.1** | Latest stable. 9.5.x are alphas — do not use |
| Gradle | **9.8.1** | AGP 9 requires >= 9.1.0 |
| Compose BOM | **2026.09.00** | Ships Compose 1.12.1, which requires compiling against API 37+ |
| `compileSdk` / `compileSdkMinor` | **37 / 2** | `platforms;android-37.2` is the installed platform. `targetSdk` stays 36 |
| Kotlin | *not declared* | — see the AGP 9 facts below |
| JDK | JBR **25.0.3** | AGP 9 needs >= 17; Studio's bundled JBR is what `JAVA_HOME` points at |

**Three AGP 9 facts that cost build cycles to discover** (details in `research/toolchain.md`):

1. **Do not apply `org.jetbrains.kotlin.android`.** AGP 9.0+ has built-in Kotlin and fails the build
   with *"The 'org.jetbrains.kotlin.android' plugin is no longer required for Kotlin support since
   AGP 9.0"*. AGP supplies KGP itself (>= 2.2.10).
2. **`compileSdkMinor` exists** (confirmed with `javap` on `gradle-api-9.4.1.jar`), which is how you
   target `android-37.2`. `compileSdk = 37` alone looks for a `platforms;android-37` that is not
   published.
3. **Do not use `jvmToolchain(17)`.** It makes Gradle hunt for a JDK 17 install that does not exist
   here. Set `compilerOptions.jvmTarget` instead.

Files produced: `settings.gradle.kts`, `build.gradle.kts`, `gradle/libs.versions.toml`,
`app/build.gradle.kts`, `app/src/main/AndroidManifest.xml`, `MainActivity.kt`.

**Verified:** `:app:assembleDebug` → `BUILD SUCCESSFUL`; `aapt2 dump badging` on the output read
`package: app.babylog versionName='0.1.0' compileSdkVersion='37' targetSdkVersion:'36'`.

### Task 1.2 — Add the dependencies — DONE for Compose/Room/KSP/JUnit; DataStore still to add (Task 4.2)

In `gradle/libs.versions.toml` + `app/build.gradle.kts`, add: `room-runtime`, `room-ktx`,
`room-compiler`(ksp), `ksp` plugin, `datastore-preferences`, `lifecycle-viewmodel-compose`,
`kotlinx-coroutines-play-services` (later, Phase 8). Firebase deps stay out until Phase 8.

**Verify:** `./gradlew :app:assembleDebug` still succeeds.

### Task 1.3 — Encode the design tokens as Kotlin — DONE (`ui/theme/Tokens.kt`)

Create: `app/src/main/java/app/babylog/ui/theme/Tokens.kt`

```kotlin
package app.babylog.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

// Surface + text (see .hermes/skills/babylog-ui/references/design-tokens.md)
val Bg           = Color(0xFFF7F9FA)
val Surface      = Color(0xFFFFFFFF)
val Divider      = Color(0xFFE6EAEF)
val TextPrimary  = Color(0xFF1F3A5F)
val TextSecondary= Color(0xFF6B7C8E)
val NavInactive  = Color(0xFF9AA6B2)
val NavActive    = Color(0xFF2E5B8A)

// One hue per event type. Never reuse these for non-event UI.
val AccentSleep   = Color(0xFF5BA8D9)
val AccentFeed    = Color(0xFFF5A623)
val AccentNursing = Color(0xFFEF8A5E)
val AccentDiaper  = Color(0xFFF2C438)
val AccentPump    = Color(0xFFB08BE0)

object Spacing {
    val gutter = 16.dp; val cardPadding = 16.dp; val cardGap = 12.dp
    val sectionGap = 28.dp; val bandHeight = 44.dp
}
object Radius { val card = 20.dp; val pill = 999.dp }
```

**Verify:** compiles. **Commit:** `feat(theme): add babylog design tokens`.

### Task 1.4 — Wire `Theme.kt` and `Type.kt` to the tokens — DONE

`Theme.kt` builds a Material 3 `lightColorScheme` from `Bg`/`Surface`/`TextPrimary`/`NavActive` and sets
`background = Bg`. `Type.kt` defines the six roles from the design-tokens table (big value 34/40 Bold,
screen title 20/26 SemiBold, card title 16/22 SemiBold, body 15/20 Medium, meta 13/18 Regular, nav label
11/14 Medium) — nothing else, no fourth weight.

**Commit:** `feat(theme): Material3 scheme + type ramp from tokens`.

---

## Phase 2 — Domain model and pure logic (TDD, no Android needed)

### Task 2.1 — `EventType` and `BabyEvent` — DONE

Create: `app/src/main/java/app/babylog/domain/BabyEvent.kt`

```kotlin
enum class EventType { FEED_BOTTLE, FEED_NURSING, SLEEP, DIAPER, PUMP }
enum class NursingSide { LEFT, RIGHT, BOTH }
enum class DiaperKind { PEE, POO, BOTH }

data class BabyEvent(
    val id: String,
    val childId: String,
    val type: EventType,
    val startedAt: Long,          // epoch millis, UTC
    val endedAt: Long? = null,    // null => running
    val amountMl: Int? = null,    // FEED_BOTTLE, PUMP
    val side: NursingSide? = null,// FEED_NURSING
    val diaperKind: DiaperKind? = null,
    val note: String? = null,
    val createdBy: String,        // auth uid
    val updatedAt: Long,
    val deleted: Boolean = false, // tombstone, for sync
)
```

### Task 2.2 — Duration + relative time — DONE (12 tests green)

Test first: `app/src/test/java/app/babylog/domain/FormattingTest.kt`

```kotlin
class FormattingTest {
    @Test fun `relative time under a minute reads just now`() =
        assertEquals("just now", relativeTime(now = 10_000L, then = 10_000L))
    @Test fun `relative time minutes`() =
        assertEquals("7m ago", relativeTime(now = 1_000_000L, then = 1_000_000L - 7 * 60_000L))
    @Test fun `relative time hours and minutes`() =
        assertEquals("2h 10m ago", relativeTime(now = N, then = N - (2*60+10)*60_000L))
    @Test fun `elapsed formats h m s`() =
        assertEquals("2h 12m 6s", elapsed(2*3600_000L + 12*60_000L + 6_000L))
    @Test fun `elapsed under an hour omits hours`() = assertEquals("12m 6s", elapsed(12*60_000L + 6_000L))
}
```
Run: `./gradlew :app:testDebugUnitTest --tests '*FormattingTest*'` → expect FAIL (unresolved).

Then implement in `domain/Formatting.kt` and re-run → 5 passed.
**Commit:** `feat(domain): relative-time and elapsed formatting (+tests)`.

### Task 2.3 — Day grouping — DONE

Test: given events across three local days, `groupByDay(events, zone)` yields groups labelled `Today`,
`Yesterday`, then `EEE d MMM`, newest first, each group's events newest first. Use `java.time` with an
explicit `ZoneId` — **never** the device default implicitly, so the unit test is deterministic.
**Commit:** `feat(domain): day grouping (+tests)`.

### Task 2.4 — Derive the card summary

Test: `summarize(events, type, now)` returns `CardSummary(lastEvent, relativeLabel, bigValueText, unitText)`
for each type: bottle → `"6"`/`"oz"`; sleep → duration or running timer; diaper → `"Poo"`; pump → amount.
Empty → a null summary (the card renders its empty state).
**Commit:** `feat(domain): per-card summary derivation (+tests)`.

---

## Phase 3 — Room persistence

### Task 3.1 — Entities + converter

Create `data/local/EventEntity.kt`, `ChildEntity.kt`, and `OutboxEntity.kt` (id, op, payloadJson, ts).
Enums stored as `String` via `TypeConverters`. Same fields as `BabyEvent` plus `syncedAt: Long?`.

### Task 3.2 — `EventDao`

`data/local/EventDao.kt`

```kotlin
@Dao interface EventDao {
    @Query("SELECT * FROM events WHERE deleted = 0 ORDER BY startedAt DESC")
    fun observeAll(): Flow<List<EventEntity>>

    @Query("SELECT * FROM events WHERE type = :type AND deleted = 0 ORDER BY startedAt DESC LIMIT 1")
    fun observeLatest(type: String): Flow<EventEntity?>

    @Query("SELECT * FROM events WHERE endedAt IS NULL AND deleted = 0")
    fun observeRunning(): Flow<List<EventEntity>>

    @Upsert suspend fun upsert(e: EventEntity)
    @Query("UPDATE events SET deleted = 1, updatedAt = :now WHERE id = :id")
    suspend fun softDelete(id: String, now: Long)
}
```

### Task 3.3 — Database + a migration policy

`data/local/AppDatabase.kt`, `version = 1`, `exportSchema = true`, schemas committed to
`app/schemas/`. **Rule for later:** once this app is on both phones with real data, a destructive
migration would lose the log — add explicit `Migration`s from v2 onward, never `fallbackToDestructive`.

### Task 3.4 — DAO round-trip test (Robolectric)

Test that upsert → observeAll returns it, soft-delete hides it, and `observeLatest` respects type.
Run `./gradlew :app:testDebugUnitTest` → pass. **Commit:** `feat(data): Room entities, DAO, database`.

---

## Phase 4 — Repository and one-tap logging (single device, no sync yet)

### Task 4.1 — `EventRepository`

`data/repo/EventRepository.kt` — the only place that writes events. Methods:
`logNow(type, amountMl?, side?, diaperKind?)`, `startTimer(type)`, `stopTimer(id)`,
`update(event)`, `softDelete(id)`, `undoDelete(event)`. Every mutation also appends to the outbox
(Phase 8 consumes it; in Phase 4 it just accumulates).

### Task 4.2 — `SettingsRepository` (oz/mL)

`data/repo/SettingsRepository.kt` over DataStore: `unit: Flow<Unit>` (`OZ`|`ML`), `setUnit()`.
Default OZ. Conversions live in `domain/Units.kt` with tests (`1 oz = 29.5735 mL`, round-half-up on display).

### Task 4.3 — `HomeViewModel`

`ui/home/HomeViewModel.kt` exposing a single `StateFlow<HomeUiState>` built from `combine(...)` of the
DAO flows + a 1-second ticker while anything is running:

```kotlin
data class HomeUiState(
    val childName: String,
    val cards: List<CardUi>,     // exactly 4, fixed order: Feed, Sleep, Diaper, Pump
    val timeline: List<DayGroup>,
)
```
The ticker must be scoped to `viewModelScope` and cancelled when nothing runs — a permanently running
1 Hz loop is a battery bug.

---

## Phase 5 — Home screen UI

### Task 5.1 — `BigValue`, `RelativeTime`

`ui/common/BigValue.kt`, `ui/common/RelativeTime.kt` — thin composables over the Phase-2 functions.
`BigValue` never truncates: shrink the unit, not the number.

### Task 5.2 — `AddCircle`

`ui/common/AddCircle.kt` — solid accent circle, ~44.dp, white `+`/`−` glyph, `Modifier.clickable` with
a 48.dp minimum touch target even at 44.dp visual size.

### Task 5.3 — `CategoryCard`

`ui/home/CategoryCard.kt` — the core component. Banded card per
`.hermes/skills/babylog-ui/references/components.md` §1, with the `AddCircle` **overlapping the band's
bottom edge** into the card body (the signature detail). Three states: resting / running / empty.

```kotlin
@Composable fun CategoryCard(
    title: String, accent: Color, card: CardUi,
    onAdd: () -> Unit, onStop: () -> Unit, onShowMore: () -> Unit,
)
```

### Task 5.4 — `LiveTimerChip`

`ui/common/LiveTimerChip.kt` — count-up from `card.runningStartedAt` (never an in-memory counter), plus
a filled **Stop** pill in the accent.

### Task 5.5 — `HomeScreen`

`ui/home/HomeScreen.kt` — one `LazyColumn`: header (child name + date) → four `CategoryCard`s in fixed
order → "Today" section header → `TimelineRow`s. Single scroll container; do not nest scrollables.

**Verify:** run on the emulator and check the 3 AM criteria: with the app cold-started, a sleep timer
starts in **one** tap from Home; the last feed's amount and relative time are legible without scrolling.
**Commit:** `feat(ui): home dashboard with category cards and live timer`.

---

## Phase 6 — Logging sheets

### Task 6.1 — `EventSheet` scaffold

`ui/sheet/EventSheet.kt` — `ModalBottomSheet`, banded header matching the event accent, X left / title
centre / Save right, hairline-divided sections, full-width primary pill CTA. Reuses `HeaderBand`.

### Task 6.2 — Per-event forms

`ui/sheet/DiaperForm.kt` (three big segments, **defaults to Pee**, save is the second tap),
`BottleForm.kt` (amount stepper prefilled from the last bottle of that type), `NursingForm.kt`
(L/R/Both), `PumpForm.kt` (amount).
**Sleep has no form** — tapping `+` starts it; see the 3 AM rules in
`.hermes/skills/babylog-ui/references/layout.md`.
**Commit:** each form separately, `feat(sheet): <event> entry sheet`.

---

## Phase 7 — Timeline, editing, undo

### Task 7.1 — `TimelineRow` + swipe actions

`ui/timeline/TimelineRow.kt` — time left, accent icon, label, detail, value right. Swipe-to-reveal
Edit / Delete.

### Task 7.2 — Undo

Deleting shows a snackbar with **Undo** for 5 seconds; the delete is a soft-delete so Undo is a flag
flip, not a re-insert. Test in `EventRepository` (Robolectric): delete → undo → event visible again.

### Task 7.3 — `TimelineScreen` + `SettingsScreen`

`ui/timeline/TimelineScreen.kt` (full history, day-grouped, collapsed beyond yesterday),
`ui/settings/SettingsScreen.kt` (oz/mL toggle, sign-in state, version). Settings stays this small —
if it grows past one screen, re-read the do-not-build list.

**Verify:** `./gradlew test` all green; manual pass on emulator: log one of each type, edit one, delete
one, undo it, restart the app and confirm state survives.
**Commit:** `feat(ui): timeline, edit, delete with undo`.

🎉 **At this point the app is genuinely useful on one phone.** Phase 8 is what makes it work for two
people; do not start it until Phases 0–7 are on a real device.

---

## Phase 8 — Two accounts, one shared log

### Task 8.1 — Firebase project (manual, in console)

Create project `babylog`, add two Android apps? **One** app entry is enough — `app.babylog`;
both phones install the same APK and sign in as different users. Enable **Authentication → Google**,
create **Firestore** in production mode. Download `google-services.json` into `app/`.

**Security note:** `google-services.json` contains API keys but is not a secret by Google's own design —
still, add `app/google-services.json` to `.gitignore` only if this repo ever goes public. Currently
private: committing it is normal and keeps the rebuild reproducible. Decide once, note the decision here.

### Task 8.2 — Firestore data shape

```
children/{childId}                      { name, birthDate, createdBy }
events/{eventId}                        { ...BabyEvent fields, updatedAt }
```
Security rules: allow read/write only when `request.auth.uid` is in a hardcoded two-uid allowlist for
the child. **Write the rules before writing the sync code** — this is the only place access is enforced.

### Task 8.3 — Auth

`data/remote/AuthRepository.kt` using Credential Manager + Google ID token → `FirebaseAuth`.
Two Google accounts: the two parents's. `ui/settings/SignInScreen.kt` gates the app until signed in.

### Task 8.4 — Outbox drain

`sync/SyncWorker.kt` — a `WorkManager` job on a network-available constraint that reads unsynced
`OutboxEntity` rows and writes them to Firestore with **`SetOptions.merge()`** and a client-generated
`eventId`, then marks them synced. Re-running a drain must be idempotent: same id + merge =
no duplicate. Test the idempotency explicitly.

### Task 8.5 — Inbound mirror

A Firestore realtime listener per child, `updatedAt`-wins on conflict, writing into Room. **Delete
tombstones must round-trip** — a soft-deleted event is pushed with `deleted = true` and the receiving
phone hides it; never hard-delete, or the other phone resurrects it on next sync.

### Task 8.6 — Second-phone verification (this is the real acceptance test)

Install on both phones. Log a feed on phone A → appears on phone B within seconds. Put phone A in
airplane mode, log three diapers, restore connectivity → all three land on B with correct times and no
duplicates. Force-stop both apps and reopen → nothing lost, nothing duplicated.
**Commit:** `feat(sync): firestore auth, outbox drain, inbound mirror`.

---

## Phase 9 — Ship it to the two of us

### Task 9.1 — Release build

`./gradlew :app:assembleRelease` with a keystore created once and **backed up outside the repo**
(losing it means losing the ability to update the installed app without uninstalling). Set
`minifyEnabled true` with a Room/Firestore keep-rule check.

### Task 9.2 — Sideload both phones

`adb install -r app-release.apk`, sign in with each Google account, run the Phase 8.6 acceptance test on
the real devices. Then use it for a week before writing another line of code.

### Task 9.3 — Record what actually happened

Update `README.md` status, log any deviation from this plan, and note the next change in a
`CHANGELOG.md`. Then decide the V1.1 items from evidence (one week of real use), not from anticipation.

---

## Deliberate non-tasks

No CI pipeline, no Play Store listing, no multi-module refactor, no dark theme, no charts, no
localization, no analytics, no crash reporting. Each is defensible for a shipping product and
unjustifiable for an app with two users — and each is exactly the kind of thing the source apps used
to justify the complexity we are escaping.
