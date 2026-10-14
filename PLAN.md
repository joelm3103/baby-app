# Babylog V1 — Implementation Plan

> **For Hermes:** load `.hermes/skills/babylog-ui/` before any UI or data task. It is the design
> contract — tokens, components, layout, insets/back requirements, and the do-not-build list.

**Goal:** A private, offline-first Android baby tracker for two parents of **twins (the twins,
born a date set in Settings)** — record bottle, solids, diaper, sleep, growth and health events, see both
children at once, and read the totals — with nothing else in it.

**Architecture:** Single-module Kotlin/Compose app. `Room` is the **source of truth**; every write
lands there first and the UI observes `Flow`s. An `outbox` table carries unsynced writes to Firestore
(Phase 12), where a listener mirrors the other parent's writes back into Room. Pure logic (age and
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
| 5 — Activity screen | **DONE, device-verified** — tinted sections, top scroll scrim, no timer on point events |
| 6 — Sheets and logging | **DONE, device-verified** — Bottle/Diaper/Solids sheets, seeded food catalogue, 121 tests green |
| 7 — Settings | **NEXT** — units, theme, children, export |
| 8 — History | not started |
| 9 — Growth | not started |
| 10 — Health | not started |
| 11 — Trends | not started |
| 12 — Two accounts, one shared log | not started |
| 13 — Update delivery | not started |
| 14 — Ship | not started |

Verified: `:app:assembleDebug` builds; `:app:testDebugUnitTest` is **121/121** with 0 failures. The
Phase-5 layout, the two launch bugs, and now the Phase-6 sheets were confirmed on a real device (see the
device-verification sections at the end of this file) — a green build proves nothing about layout or
about system back. Toolchain details and the AGP 9 / KSP traps are in `research/toolchain.md`.

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
- `OutboxEntity` — id, op, payloadJson, ts (unused until Phase 12, written from day one).

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

## Phase 5 — Activity screen (the home) — DONE (device-verified)

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

## Phase 6 — Sheets and logging — DONE, device-verified

Built: `ActivitySheet` (full-screen, activity-tinted title bar with X / title / Save, hairline
`InputRow`s, `BackHandler` so system back dismisses), Bottle (time, amount stepper in the display unit,
formula brand, notes), Diaper (time, Wet/Dirty/Mixed, rash, notes — the card keeps its one-tap pills),
and Solids (meal chips, `FoodPicker` with search / multi-select / recent-first / custom add, meal-level
reaction, notes). The Phase-5 `quickLog` stub is gone: every write is now one
`EventRepository.log(childIds, draft, CREATED_BY)` call, so Both mode still writes one row per child
with identical timestamps.

Foods: a curated ~90-food catalogue ships as `app/src/main/assets/foods/foods.json` and is seeded
insert-if-absent into the previously empty `foods` table, so a later asset adds foods without an APK
release and can never clobber a parent's custom entry or a touched `lastUsedAt`. No Room migration was
needed — `FoodEntity` already carried `custom` and `lastUsedAt`. Reactions stay **per meal** (the
schema's existing `FoodReaction`); the per-food join-table escape hatch remains documented in
`domain/FoodIds.kt`.

Growth and Health action buttons are **disabled** with a phase note rather than writing a valueless
stub event. Their sheets are Phases 9-10.

**Task 6.1** `ActivitySheet` scaffold — tinted title bar, **X / title / Save**, and
`BackHandler(enabled = true) { dismiss() }` so system back closes it (complaint #2).
**Task 6.2** Bottle · Diaper (Wet/Dirty/Mixed + rash) · Sleep (timer, starts on the card, no sheet).
**Task 6.3** Solids — meal chips + `FoodPicker`: search over the seeded catalogue, multi-select,
recent-first, with `loved it` / `allergy or sensitivity` flags. Seed the food list from a bundled
JSON so a new food is a data change, not a code change.
**Verify:** log one of each in Both mode and confirm two rows land per child with identical timestamps;
system back closes each sheet. **Commit:** per sheet.

## Phase 7 — Settings

The screen behind the header's settings icon. `SettingsRepository` (units, theme, selected child) and
`ChildRepository` (name, birth date, gestational age, adjusted-age flag) **already exist and are
tested**, so this phase is UI plus one export path — no new storage and no schema change.

- **Appearance**: System / Light / Dark, wired to the stored `ThemeMode`.
- **Units**: length (in / cm), weight (lb&oz / kg) and volume (fl oz / ml) as three independently
  selectable families, with Metric / Imperial / Custom presets. **Custom is derived, never stored**, so
  re-selecting a preset stays predictable. Temperature remains Celsius with no option.
- **Children**: name, birth date, sex, gestational age (stored in days, shown as weeks) and the
  per-child `Use adjusted age` toggle. Birth details are **data, never constants** — the twins
  default to a date set in Settings and 210 days, and every field is editable.
- **Export / backup**: a human-readable JSON export of the whole log to the phone (share sheet / SAF).
  Photos are permanently out of scope, so the export stays plain text. This is the single highest-value
  insurance in the plan: it means a bad migration can never lose the log, and it unblocks restoring
  onto a replacement phone. It also retires the stale `no export` entry on the do-not-build list.
- The **growth-reference line** (`WHO 2006 · v2026.10 · updated …`) joins this screen once Phase 9 lands.

**Verify:** switching a unit family changes the display and leaves the stored `events` row byte-identical
(the stored amount must not be rewritten); editing a birth date changes the age on the Activity screen;
an export opens as readable JSON and round-trips the row count.
**Commit:** `feat(settings): units, theme, children and export`

## Phase 8 — History

Range `1d · 7d · 14d`, then per-activity totals computed in pure Kotlin (`domain/Totals.kt`) over an
`EventDao.between(from, to)` read — not in SQL, for the timezone reason given in Task 3.2.
Big value + muted label, no charts. **Verify:** totals match a hand-count of a seeded day.
**Commit:** `feat(ui): history totals`

# STAGE 2 — Growth and Health

## Phase 9 — Growth (the heaviest single feature)

**Task 9.1** Bundle the baseline dataset. WHO's `igrowup` LMS tables (weight-for-age,
length/height-for-age, head-circumference-for-age, weight-for-length/height, BMI-for-age; boys and
girls) as a compact JSON asset under `app/src/main/assets/growth/`. **Do not invent percentile maths** —
LMS is published data and the z-score formula is standard. Verified source URLs, row counts, sex codes
and file hashes are recorded in `research/growth-data-sources.md`; that file also documents that **WHO
has not revised these standards since 2006**, which is why the asset ships bundled rather than being
fetched on first run.

**Task 9.2 — dataset versioning and updates.** Ship a bundled baseline, then make the data updatable
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

**Task 9.3** Preterm — **settled: both born prematurely.** Not a hypothetical
path.

- Default to plotting **adjusted age** on the WHO charts, toggle visible. At premature this is a large
  correction, not a rounding detail.
- Store gestational age **in days** (210) to keep the adjusted-age subtraction exact.
- Their **birth** measurements were taken at premature, which the WHO 0–5 standards do not describe:
  that belongs to **INTERGROWTH-21st newborn size for very preterm infants** (covers <33 weeks). Add
  that dataset family for interpreting birth entries while the ongoing charts stay WHO + adjusted age.
- Both are boys, so only the boys' tables are exercised in practice — bundle the girls' tables anyway.
  A few hundred KB, and the app should not be structurally sexist.
**Task 9.4** `Percentiles.kt` — pure and heavily tested: LMS → z-score → percentile, and the inverse for
plotting a curve. Unit tests against known published values.
**Task 9.5** `GrowthChart` — segmented Weight/Height/Head Size, percentile curve bundle, child marker,
callout card with date, value, percentile and **age (adjusted when the toggle is on)**.
**Verify:** a seeded measurement lands on the correct curve; a preemie's adjusted age changes its
percentile. **Commit:** `feat(growth): who percentiles and charts`

## Phase 10 — Health

Temperature, medication, vaccine — the same sheet pattern, reusing `InputRow`. Small once Phase 6
exists. **Commit:** `feat(health): records`

# STAGE 3 — Trends, sync, ship

## Phase 11 — Trends
Charts over time for feed amount, diaper count, sleep duration, growth. Built last on purpose: it needs
real data in the database before the design can be judged.

## Phase 12 — Two accounts, one shared log
Firebase project, Google sign-in, security rules restricted to two uids, outbox drain with
`SetOptions.merge()` and client-generated ids (idempotent re-runs), inbound listener with
`updatedAt`-wins, and **tombstones that round-trip** so a deleted event never resurrects.
**Acceptance:** log on phone A, see it on B; airplane-mode three writes on A, restore, all three arrive
once, no duplicates.

## Phase 13 — Update delivery

Getting a new build onto both phones without a cable. This has to precede the ship step, because a
release-signed APK is the thing being delivered.

- **Release signing**: generate the keystore, wire `signingConfigs`, and back the keystore up **outside
  the repo**. It never enters git.
- **Delivery**: Firebase App Distribution (free, no Play Store) on the Firebase project from Phase 12,
  or a hosted APK link. Installing a new build becomes a tap on both phones instead of running
  `adb install -r` twice.

**Verify:** a build reaches both phones over the air, without a cable.
**Commit:** `chore(release): signed builds and over-the-air delivery`

## Phase 14 — Ship
`adb install -r` the release APK on both phones — the last time a cable is needed — then **use it for a
week before writing another line of code**.

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
2. **Export / backup in Settings (Phase 7).** Cheap now, and it means a bad migration can never lose the log. For
   a project whose owner explicitly plans post-build changes, this is the single highest-value
   insurance and it also unblocks restoring onto a replacement phone. the incumbent tracker has `Export Data` for the
   same reason.
3. **Update delivery that doesn't need a cable.** Firebase App Distribution (free, no Play Store) or a
   hosted APK link, so installing a new build on both phones is a tap instead of `adb install` twice.
   Add it at Phase 13, not Phase 1.

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
- **Sleep's hue is settled as shipped** (`#63C8FF` in `Tokens.kt`). No the incumbent tracker sleep screen was ever
  captured, so it started as a designed guess; the owner confirmed on 2026-10-14 that it stays as-is.
- **The light theme is designed, not sampled.** the owner's phone runs dark; the mirror in `Tokens.kt` is
  reasonable but unverified.

## Deliberate non-tasks
No CI, no Play Store listing, no multi-module refactor, no localization, no analytics, no crash
reporting. Each is defensible for a shipped product and unjustifiable for an app with two
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

---

## Phase 6 device verification — 2026-10-14

Samsung S23 Ultra (SM-S918U1), Android 16, dark mode. Screenshots: `docs/timeline/2026-10-14-phase6/`.

| Check | Result |
| --- | --- |
| Launches without crashing; app is the current activity | pass |
| Card action label is context-aware (`+ Log feed for Child 1` / `... for both`) | pass |
| Bottle sheet: tinted title bar, time, amount stepper, brand, notes; no bottom nav | pass |
| **Both mode writes two rows with identical timestamps** | pass — child 1 + child 2 BOTTLE at 13:59:53 UTC, 120 ml (read from the Room DB) |
| Solids sheet: meal chips, food picker, per-meal reaction, notes | pass |
| Food picker: full catalogue, search, custom add, recent-first | pass — custom `mango-lassi` created, then sorted above the alphabetical list |
| Both mode for solids and diaper | pass — SOLIDS child 1+child 2 at 14:01:00, DIAPER child 1+child 2 at 14:03:37, rash = 1 |
| Seed is additive on an existing database (no clobber) | pass — 91 seeded + 1 custom; `lastUsedAt` preserved |
| **System back dismisses the picker, then the sheet, without exiting** | pass — app stayed foreground through both |
| Growth / Health buttons disabled, not writing valueless events | pass |

### Found on device, not by the build

- The Rash toggle (and the food rows in the picker) drew a `›` chevron, which the design contract
  reserves for rows that push a screen. Fixed: `InputRow` takes `chevron` (default `onClick != null`),
  and the in-place controls pass `chevron = false`.
- Cosmetic, not fixed: the Solids card's detail line renders the food **id** rather than its catalogue
  name (`mango lassi`, lowercase). `CardSummary.detailLine` works from `foodIds` and has no access to
  the catalogue; showing the display name is a small follow-up, not a Phase 6 blocker.
