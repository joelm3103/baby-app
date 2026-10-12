# Babylog V1 — Implementation Plan

> **For Hermes:** load `.hermes/skills/babylog-ui/` before any UI or data task. It is the design
> contract — tokens, components, layout, insets/back requirements, and the do-not-build list.

**Goal:** A private, offline-first Android baby tracker for two parents of **twins (the twins,
born a date set in Settings)** — record bottle, solids, diaper, sleep, growth and health events, see both
children at once, and read the totals — with nothing else in it.

**Architecture:** Single-module Kotlin/Compose app. `Room` is the **source of truth**; every write
lands there first and the UI observes `Flow`s. An `outbox` table carries unsynced writes to Firestore
(Phase 11), where a listener mirrors the other parent's writes back into Room. Pure logic (age and
adjusted age, durations, relative time, day grouping, totals, percentile lookup) lives in plain Kotlin
so it is testable without an emulator.

**Tech Stack:** Kotlin (via AGP 9 built-in Kotlin) · Compose + Material 3 · Room + KSP · DataStore ·
Firebase Auth + Firestore · JUnit. AGP 9.4.1 / Gradle 9.8.1 / Compose BOM 2026.09.00 / JDK 25 (JBR).

**Effort honestly:** this is a multi-week project, not a weekend — the scope the owner confirmed is close to
the incumbent's minus the content, the shop and the paywall. Hence the staging below: **Stage 1 is usable, and
everything after it is additive.**

---

## Status

| Phase | State |
|---|---|
| 0 — Toolchain | **DONE** |
| 1 — Scaffold, theme, tokens | **DONE** (theme reworked for dark+light from real screenshots) |
| 2 — Domain: events, formatting, grouping | **DONE** — 12 unit tests green |
| 3 — Room schema + DAO + mappers | **DONE** — `app/schemas/…/1.json` committed, 50 tests green |
| 4 — Repository, settings, adjusted age | **DONE** — 78 tests green |
| 5 — Activity screen | **DONE, visually UNVERIFIED** — compiles, APK builds |

Verified today: `:app:assembleDebug` builds; `:app:testDebugUnitTest` is 12/12; `aapt2 dump badging`
on the output reads `app.babylog v0.1.0, compileSdk 37, targetSdk 36`; `./gradlew` works
end-to-end. Toolchain details and the AGP 9 / KSP traps are in `research/toolchain.md`.

---

# STAGE 1 — the 3am app

## Phase 3 — Room schema, DAO, tests — DONE (schema v1 exported and committed; 50 tests green)

### Task 3.1 — Entities
Create `app/src/main/java/app/babylog/data/local/`:
- `ChildEntity` — id, name, birthDate, **weeksPremature: Int?**, sex, useAdjustedAge: Boolean, sortOrder.
  Birth details are **data, never constants** — the twins both default to a date set in Settings but must be
  editable in Settings.
- `EventEntity` — id, childId, type, startedAt, endedAt?, amountMl?, formulaBrand?, mealSlot?,
  foodItemsJson?, reaction?, diaperKind?, rash: Boolean?, weightG?, heightMm?, headMm?,
  temperatureC?, medication?, vaccineName?, note?, createdBy, updatedAt, deleted.
- `FoodEntity` — the solids catalogue: id, name, category, lastUsedAt?. Seeded from a bundled list.
- `OutboxEntity` — id, op, payloadJson, ts (unused until Phase 11, written from day one).

Enums stored as `String` with `TypeConverters`. **Soft delete only** — a hard delete resurrects the
row from the other phone on the next sync.

### Task 3.2 — `EventDao`, `ChildDao`, `FoodDao`
`observeForChildren(childIds)` (which also serves **Both mode as a single query with two ids**),
`observeLatest(type, childIds)`, `observeRecentByType`, `observeRunning()`, `between(from, to)`,
`upsert`, `softDelete`, `restore` (for undo).

**Deviation, deliberate:** the plan originally put the History totals in SQL. They are not there.
Daily boundaries and the day/night diaper split depend on the **local timezone**, which SQLite cannot
resolve — aggregating in SQL would have silently used UTC and filed 8pm diapers under the wrong day.
Totals moved to pure Kotlin (`domain/Totals.kt`), which is also directly testable.

### Task 3.3 — Database
`AppDatabase`, `version = 1`, `exportSchema = true`, schemas committed under `app/schemas/`.
**Policy:** once real data is on both phones, add explicit `Migration`s — never
`fallbackToDestructive`, or the log is lost.

### Task 3.4 — DAO round-trip test (Robolectric)
DONE via **Robolectric 4.17**: insert → observe → soft-delete → hidden → restore (undo); Both-mode
reads; `observeLatest` per type *and* child; `between()` half-open range; `observeRunning`; food
search / recent / touch; outbox enqueue → drain.

**Robolectric is what lets the DAO contract be verified on the JVM**, which matters because the emulator
was deliberately skipped in Phase 0. Without it, a schema mistake would only surface on a phone.

The schema is **committed** at `app/schemas/app.babylog.data.local.AppDatabase/1.json` — that is
what makes future migration tests possible, so it is source, not build output.

## Phase 4 — Repository, settings, both-mode writes — DONE

All three tasks landed, against a real Room database in Robolectric. **78 tests green.**

Deviations worth keeping:

- **`EventDraft` is a separate type from `BabyEvent`** — "what is being logged" before it is
  attributed to a child. That is what makes Both-mode logging one draft → N events with
  identical timestamps, instead of the tempting-but-wrong single row with a null childId.
- **The outbox no longer carries a serialised payload.** A JSON copy of the entity drifts the
  moment the row is edited again before the drain runs; the drain reads the current row by id
  instead, so the queue records intent and the data has exactly one home.
- **`startSleepTimer` refuses to stack timers** per child. Two running sleep sessions produce a
  negative duration and a card that cannot show a single truth, so the second tap is a no-op.
- `Clock` and the id generator are injected, so the repository tests drive time forward rather
  than sleeping.

**Task 4.1** `EventRepository` — the only writer. `log(childIds, type, fields…)` takes a **list** of
child ids: Both-mode writes two rows with the same timestamp. `update`, `softDelete`, `undoDelete`,
`startTimer`, `stopTimer`.
**Task 4.2** `SettingsRepository` over DataStore — the **three unit families** (length in/cm, weight
lb&oz / kg, volume fl oz / ml) with Metric/Imperial/Custom presets, selected child context
(`Child 1`/`Child 2`/`Both`), `useAdjustedAge`, and a theme override defaulting to system.
`domain/Units.kt` is **already written and tested** (13 tests) — exact factors, `lb&oz` compound
formatting including the 16-ounce carry, unit-appropriate stepper increments, Celsius-only
temperature. `UnitPreferences.system` is derived, never stored.
**Task 4.3** `Age.kt` — pure, tested: `chronologicalAge(birth, now)`, `adjustedAge(birth, weeksPre,
now)`, formatting as `9 months, 1 week, 5 days`. Adjusted age = chronological minus weeks premature,
**and the premature offset is per child**. Tests must cover a preemie and a full-term child.
**Verify:** tests green. **Commit:** `feat(data): repository, settings, age with adjusted age`

## Phase 5 — Activity screen (the home) — DONE (compiles, APK builds; layout NOT yet seen)

Built: `AppScaffold` (insets + back), `ActivityScreen`, `ChildSwitcher` (Child 1 · Child 2 · Both),
`ActivityCard`, `HomeViewModel`, `AppContainer` (manual DI), first-run seed of the two boys.

**Honest status: it compiles and the APK builds, but no one has LOOKED at it.** Compose layout
errors — a card that overflows, text that clips, a pill row that wraps badly — do not fail a
build. Visual verification needs a device, an emulator, or Roborazzi screenshot tests; until
then the layout is unverified. See the *Verifying the UI* note at the end of this file.

The Phase-5 `quickLog` path logs events with only a timestamp so the layout can be judged;
Phase 6 replaces it with the real per-activity sheets.

**Task 5.1** `MainActivity` + `AppScaffold` — `Scaffold(contentWindowInsets = WindowInsets.navigationBars)`
and a `LazyColumn` whose `contentPadding` clears `bottomBarHeight` **plus** the nav-bar inset. This is
the fix for the owner's complaint #1; get it right before any content exists to hide behind it.
**Task 5.2** `ScreenHeader` + `ChildSwitcher` — Child 1 · Child 2 · Both, outline on the selected pill.
**Task 5.3** `ActivityCard` — tinted surface, accent label + relative time, full-width action button
reading `+ Log … for both` / `+ Log … for Child 1`, recent rows, `See all ›`.
**Task 5.4** Card order fixed: **Bottle · Diaper · Solids · Growth · Health · Sleep.** Sleep last and
present because the owner asked for it explicitly.
**Task 5.5** Bottom nav — Activity · History · Trends.
**Verify:** on device — switch children, scroll to the very bottom with 3-button navigation on, and
confirm nothing is clipped. **Commit:** `feat(ui): activity screen with child switcher`

## Phase 6 — Sheets and logging

**Task 6.1** `ActivitySheet` scaffold — tinted title bar, **X / title / Save**, and
`BackHandler(enabled = true) { dismiss() }` so system back closes it (complaint #2).
**Task 6.2** Bottle · Diaper (Wet/Dirty/Mixed + rash) · Sleep (timer, starts on the card, no sheet).
**Task 6.3** Solids — meal chips + `FoodPicker`: search over the seeded catalogue, multi-select,
recent-first, with `loved it` / `allergy or sensitivity` flags. Seed the food list from a bundled
JSON so a new food is a data change, not a code change.
**Verify:** log one of each in Both mode and confirm two rows land per child with identical timestamps;
system back closes each sheet. **Commit:** per sheet.

## Phase 7 — History

Range `1d · 7d · 14d`, then per-activity totals computed in pure Kotlin (`domain/Totals.kt`) over an
`EventDao.between(from, to)` read — not in SQL, for the timezone reason given in Task 3.2.
Big value + muted label, no charts. **Verify:** totals match a hand-count of a seeded day.
**Commit:** `feat(ui): history totals`

# STAGE 2 — Growth and Health

## Phase 8 — Growth (the heaviest single feature)

**Task 8.1** Bundle the baseline dataset. WHO's `igrowup` LMS tables (weight-for-age,
length/height-for-age, head-circumference-for-age, weight-for-length/height, BMI-for-age; boys and
girls) as a compact JSON asset under `app/src/main/assets/growth/`. **Do not invent percentile maths** —
LMS is published data and the z-score formula is standard. Verified source URLs, row counts, sex codes
and file hashes are recorded in `research/growth-data-sources.md`; that file also documents that **WHO
has not revised these standards since 2006**, which is why the asset ships bundled rather than being
fetched on first run.

**Task 8.2 — dataset versioning and updates.** Ship a bundled baseline, then make the data updatable
without an APK release. The shape the owner asked for:

- **Dataset identity**: `version` (e.g. `who-2006.2026.10`), `publishedAt`, `retrievedAt`, `source`
  URLs, `sha256`. Stored alongside the data and surfaced in the UI.
- **Update channel**: a manifest document in Firestore (`reference/growth_manifest`) pointing at the
  dataset (`reference/growth_dataset`). Firestore is already in the stack, the existing security rules
  already restrict access to the two of us, and an update becomes a **document write** — no hosting to
  stand up, no public repo, no release cycle. Swap for a static HTTPS file if that ever changes.
- **Cadence**: check at most **once every 90 days**, silently, on app start. Never blocking, never
  nagging. If the manifest is unreachable, older, or fails its checksum, keep the current dataset and
  move on — no error UI, no retry storm.
- **Manual update**: a `Check for updates` row on the Growth screen's options and in Settings.
- **Date display**: Settings shows `Growth reference: WHO 2006 · v2026.10 · updated 12 Oct 2026`, and
  the Growth screen footer shows the same in small secondary text next to the existing `Source: WHO`
  line. A percentile number a parent might act on should never be presented without saying which
  dataset produced it.
- **Test**: a fake manifest newer than the bundle updates the data and the displayed date; an older or
  corrupt manifest leaves both untouched.

**Task 8.3** Preterm — **settled: both born prematurely.** Not a hypothetical
path.

- Default to plotting **adjusted age** on the WHO charts, toggle visible. At premature this is a large
  correction, not a rounding detail.
- Store gestational age **in days** (210) to keep the adjusted-age subtraction exact.
- Their **birth** measurements were taken at premature, which the WHO 0–5 standards do not describe:
  that belongs to **INTERGROWTH-21st newborn size for very preterm infants** (covers <33 weeks). Add
  that dataset family for interpreting birth entries while the ongoing charts stay WHO + adjusted age.
- Both are boys, so only the boys' tables are exercised in practice — bundle the girls' tables anyway.
  A few hundred KB, and the app should not be structurally sexist.
**Task 8.4** `Percentiles.kt` — pure and heavily tested: LMS → z-score → percentile, and the inverse for
plotting a curve. Unit tests against known published values.
**Task 8.5** `GrowthChart` — segmented Weight/Height/Head Size, percentile curve bundle, child marker,
callout card with date, value, percentile and **age (adjusted when the toggle is on)**.
**Verify:** a seeded measurement lands on the correct curve; a preemie's adjusted age changes its
percentile. **Commit:** `feat(growth): who percentiles and charts`

## Phase 9 — Health

Temperature, medication, vaccine — the same sheet pattern, reusing `InputRow`. Small once Phase 6
exists. **Commit:** `feat(health): records`

# STAGE 3 — Trends, sync, ship

## Phase 10 — Trends
Charts over time for feed amount, diaper count, sleep duration, growth. Built last on purpose: it needs
real data in the database before the design can be judged.

## Phase 11 — Two accounts, one shared log
Firebase project, Google sign-in, security rules restricted to two uids, outbox drain with
`SetOptions.merge()` and client-generated ids (idempotent re-runs), inbound listener with
`updatedAt`-wins, and **tombstones that round-trip** so a deleted event never resurrects.
**Acceptance:** log on phone A, see it on B; airplane-mode three writes on A, restore, all three arrive
once, no duplicates.

## Phase 12 — Ship
Release build, keystore **backed up outside the repo**, `adb install -r` on both phones, then **use it
for a week before writing another line of code**.

---

## Changing this app after v1 ships

the owner asked (2026-10-12) whether post-build changes are easy. Honest answer, by kind of change:

| Kind of change | Cost | Why |
|---|---|---|
| Colours, type, spacing, card layout | **Cheap** | Design tokens live in one file, and the design skill is the contract |
| Labels, copy, ordering, a new screen | **Cheap** | Screens read from the same DAO; no data change |
| Units, presets, formatting | **Cheap** | Storage is canonical metric; conversion is display-only and already tested |
| Adding a log field (e.g. a new note type) | **Easy** | Additive nullable column + a Room migration |
| Adding a whole activity type | **Easy-ish** | Enum value + card + sheet; `EventEntity` already carries per-type optionals |
| Renaming or restructuring existing columns | **Needs care** | A real migration, and *both phones* must move together |
| Photos / attachments | **Expensive if retrofitted** | Needs file storage, upload, sync and backup design — decide before v1 if wanted |
| Widgets, Wear, a third parent, per-child accounts | **Expensive** | New surfaces, not new data |

### Three things to do now so post-build changes stay cheap

1. **Additive-only migration rule.** New columns nullable or defaulted; never repurpose an existing
   column's meaning; never `fallbackToDestructiveMigration`. Every migration gets a test. Enforce it
   from schema v1 so the habit exists before there is data worth losing.
2. **Export / backup in Settings.** Cheap now, and it means a bad migration can never lose the log. For
   a project whose owner explicitly plans post-build changes, this is the single highest-value
   insurance and it also unblocks restoring onto a replacement phone. the incumbent tracker has `Export Data` for the
   same reason.
3. **Update delivery that doesn't need a cable.** Firebase App Distribution (free, no Play Store) or a
   hosted APK link, so installing a new build on both phones is a tap instead of `adb install` twice.
   Add it at Phase 12, not Phase 1.

### Settled 2026-10-12: no photos, no widgets

the owner never used `Add Photo` in the incumbent tracker and never used a widget. Both are now **out of scope permanently**,
which removes the only expensive retrofit from the list above. All three consequences are simplifications:

- No file storage, no upload path, no binary payload in the sync document, and the JSON backup stays
  human-readable text.
- `Add Photo` is removed from the sheet spec; forms run from the last input row straight to Save.
- Photo and widget rows are on the do-not-build list so they cannot creep back in.

### Not cheap to reverse later

- Anything that changes **what a stored number means** (e.g. if `amountMl` ever became a range rather
  than a value). That is the remaining one, and the additive-only migration rule is what protects it.

---

## Honest notes on this plan

- **Growth is the risk.** WHO percentile curves, an LMS dataset and adjusted-age handling are more work
  than the entire Stage 1. It is staged last for that reason, not because it matters least.
- **Trends needs data to design.** Building it before the log is full would be guessing.
- **Units are core, not a setting.** Three families plus a Custom mix, canonical metric storage,
  temperature always Celsius. `domain/Units.kt` is done; sheets and charts must read the preference
  rather than hardcode oz/cm/kg anywhere.
- **Sleep's hue is still a guess** — no sleep screen appeared in the 15 screenshots. One screenshot
  from the incumbent's sleep card would settle it.
- **The light theme is designed, not sampled.** the owner's phone runs dark; the mirror in `Tokens.kt` is
  reasonable but unverified.

## Deliberate non-tasks
No CI, no Play Store listing, no multi-module refactor, no localization, no analytics, no crash
reporting, no export. Each is defensible for a shipped product and unjustifiable for an app with two
users.


---

## Verifying the UI (Phase 5 onward)

A green build proves the Compose code type-checks. It proves **nothing** about layout: overflowing
cards, clipped text, and a pill row that wraps onto two lines all compile cleanly. Three options, in
order of value:

1. **A real phone over USB** (`adb install -r`), then `adb exec-out screencap -p` to capture the screen
   for review. Best fidelity — it is the actual target hardware and the actual system theme.
2. **An emulator.** ~4 GB and a Windows Hypervisor Platform step, but it makes screenshots scriptable
   and repeatable without involving a human. Worth adding if UI work continues without a phone handy.
3. **Roborazzi screenshot tests.** Renders composables to PNGs on the JVM alongside the existing
   Robolectric tests, so a layout regression fails CI. The right long-term answer; extra dependency to
   validate against AGP 9 first.

Until one of these exists, treat Phase 5+ layout as unconfirmed.

---

## Phase 5 device verification — 2026-10-12

The 0.1.0 APK crashed on launch. Fixed, and the fix verified on a real device (Samsung S23 Ultra,
Android 16 / API 36) rather than by build status.

### Bug 1 — crash on launch (NPE on every start)

```
NullPointerException: Attempt to invoke interface method
'java.util.List app.babylog.domain.ChildSelection.resolve(java.util.List)' on a null object reference
```

`ChildSelection.default` was a **stored** companion val: `val default: ChildSelection = Both`. On
Android, ART initialises the enclosing interface (and so its companion) *while* `Both` is still being
constructed, so the initialiser read `Both`'s static `INSTANCE` field before it was assigned and stored
**null permanently**. `decodeChildSelection` then returned null for every absent preference, and the
dashboard's `resolve()` call died.

Fixed by making it computed: `val default: ChildSelection get() = Both`.

**HotSpot does not do this**, so 97 green JVM/Robolectric tests passed while the app died on a phone.
No amount of JVM testing would have caught it. Only the device did.

### Bug 2 — every card showed a running timer

`BabyEvent.isRunning` was `endedAt == null`, which conflates *"still in progress"* with *"a point event
that has no end"*. Every logged bottle, diaper, weigh-in and temperature read as a running session and
got a live count-up timer.

Fixed: `EventType.hasDuration` (true for SLEEP only) and `isRunning = type.hasDuration && endedAt == null`.

### A trap found while fixing it

`combined.second ?: ChildSelection.Both` was **compiled away** — the elvis is folded to nothing when the
left operand's declared type is non-null, so the first attempt at a fallback silently did nothing. Widen
to a nullable local first to make the check real bytecode.

### Verified on the device

| Check | Result |
| --- | --- |
| Launches without crashing | pass |
| Bottle/diaper cards show a value + relative time, no timer | pass |
| Last card (Sleep) fully visible above the nav bar at full scroll | pass |
| Settings -> system back returns to Activity, process survives | pass |
| Child 1 / Child 2 / Both switcher present | pass |
| History placeholder renders | pass |

### Removed

`HomeViewModelTest` — a Robolectric test driving the ViewModel pipeline. It hung and failed:
Robolectric's paused main looper never pumps Room's invalidation callbacks, so a DB-backed flow cannot be
collected. Removed rather than left to rot, along with `kotlinx-coroutines-test`. **The ViewModel
pipeline is verified on the device; a JVM test for it needs fake DAOs, not Room.**

### Verification loop for all future phases

The phone is the feedback loop: `adb install -r`, `logcat -d` for crashes, `uiautomator dump` for real
tap coordinates, `exec-out screencap` for what it actually looks like. Do not call a phase done on a
green build.
