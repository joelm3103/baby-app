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
| 3 — Room schema + DAO | next |

Verified today: `:app:assembleDebug` builds; `:app:testDebugUnitTest` is 12/12; `aapt2 dump badging`
on the output reads `app.babylog v0.1.0, compileSdk 37, targetSdk 36`; `./gradlew` works
end-to-end. Toolchain details and the AGP 9 / KSP traps are in `research/toolchain.md`.

---

# STAGE 1 — the 3am app

## Phase 3 — Room schema, DAO, tests

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
`observeForChildren(childIds): Flow<List<EventEntity>>`, `observeLatest(type, childIds)`,
`observeRunning()`, `upsert`, `softDelete`, plus totals queries for History
(`sumAmountBetween`, `countDiapersBetween`) so totals are computed in SQL, not in Kotlin.

### Task 3.3 — Database
`AppDatabase`, `version = 1`, `exportSchema = true`, schemas committed under `app/schemas/`.
**Policy:** once real data is on both phones, add explicit `Migration`s — never
`fallbackToDestructive`, or the log is lost.

### Task 3.4 — DAO round-trip test (Robolectric)
Insert → observe → soft-delete → hidden; a Both-mode write produces two rows; totals respect date
ranges and exclude deleted rows.
**Verify:** `./gradlew :app:testDebugUnitTest` **Commit:** `feat(data): room schema, DAO, tests`

## Phase 4 — Repository, settings, both-mode writes

**Task 4.1** `EventRepository` — the only writer. `log(childIds, type, fields…)` takes a **list** of
child ids: Both-mode writes two rows with the same timestamp. `update`, `softDelete`, `undoDelete`,
`startTimer`, `stopTimer`.
**Task 4.2** `SettingsRepository` over DataStore — unit (oz/mL), selected child context
(`Child 1`/`Child 2`/`Both`), theme override (system default).
**Task 4.3** `Age.kt` — pure, tested: `chronologicalAge(birth, now)`, `adjustedAge(birth, weeksPre,
now)`, formatting as `9 months, 1 week, 5 days`. Adjusted age = chronological minus weeks premature,
**and the premature offset is per child**. Tests must cover a preemie and a full-term child.
**Verify:** tests green. **Commit:** `feat(data): repository, settings, age with adjusted age`

## Phase 5 — Activity screen (the home)

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

Range `1d · 7d · 14d`, then per-activity totals driven by the SQL aggregate queries from Task 3.2.
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

**Task 8.3** Preterm: WHO 0–5 charts assume term birth. Confirm how many weeks premature the twins
were. Default behaviour is the incumbent's — plot **adjusted age** on WHO charts, with the per-child toggle. Add
INTERGROWTH-21st/Fenton only if the preterm growth *trajectory* itself is wanted; it is a second dataset
family and a second licence to check.
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

## Honest notes on this plan

- **Growth is the risk.** WHO percentile curves, an LMS dataset and adjusted-age handling are more work
  than the entire Stage 1. It is staged last for that reason, not because it matters least.
- **Trends needs data to design.** Building it before the log is full would be guessing.
- **Sleep's hue is still a guess** — no sleep screen appeared in the 15 screenshots. One screenshot
  from the incumbent's sleep card would settle it.
- **The light theme is designed, not sampled.** the owner's phone runs dark; the mirror in `Tokens.kt` is
  reasonable but unverified.

## Deliberate non-tasks
No CI, no Play Store listing, no multi-module refactor, no localization, no analytics, no crash
reporting, no export. Each is defensible for a shipped product and unjustifiable for an app with two
users.
