# Babylog V1 — Implementation Plan

> **For Hermes:** load `.hermes/skills/babylog-ui/` before any UI or data task. It is the design
> contract — tokens, components, layout, insets/back requirements, and the do-not-build list.

**Goal:** A private, offline-first Android baby tracker for two parents of **twins (the twins,
born a date set in Settings)** — record bottle, solids, diaper, sleep, growth and health events, see both
children at once, and read the totals — with nothing else in it.

**Architecture:** Single-module Kotlin/Compose app. `Room` is the **source of truth**; every write
lands there first and the UI observes `Flow`s. An `outbox` table carries unsynced writes to Firestore
(Phase 13), where a listener mirrors the other parent's writes back into Room. Pure logic (age and
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
| 7 — Settings | **DONE, device-verified** — appearance, units, children (add/archive/delete), export + import, 136 tests green |
| 8 — History | **DONE, device-verified** — calendar-day totals, configurable day/night split, filters, 149 tests green |
| 9 — Daily-use polish | **DONE, device-verified** — child badges, free-text amount + presets, Date rows, alternating Settings cards, shell fades and insets, 158 tests green |
| 10 — Growth | NEXT |
| 11 — Health | not started |
| 12 — Trends | not started |
| 13 — Two accounts, one shared log | not started |
| 14 — Update delivery | not started |
| 15 — Ship | not started |

Verified: `:app:assembleDebug` builds; `:app:testDebugUnitTest` is **158/158** with 0 failures. The
Phase-5 layout, the two launch bugs, and the Phase-6, 7, 8 and 9 screens were all confirmed on a real
device (see the device-verification sections at the end of this file) — a green build proves nothing
about layout or about system back. Toolchain details and the AGP 9 / KSP traps are in
`research/toolchain.md`.

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
- `OutboxEntity` — id, op, payloadJson, ts (unused until Phase 13, written from day one).

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
stub event. Their sheets are Phases 10-11.

**Task 6.1** `ActivitySheet` scaffold — tinted title bar, **X / title / Save**, and
`BackHandler(enabled = true) { dismiss() }` so system back closes it (complaint #2).
**Task 6.2** Bottle · Diaper (Wet/Dirty/Mixed + rash) · Sleep (timer, starts on the card, no sheet).
**Task 6.3** Solids — meal chips + `FoodPicker`: search over the seeded catalogue, multi-select,
recent-first, with `loved it` / `allergy or sensitivity` flags. Seed the food list from a bundled
JSON so a new food is a data change, not a code change.
**Verify:** log one of each in Both mode and confirm two rows land per child with identical timestamps;
system back closes each sheet. **Commit:** per sheet.

## Phase 7 — Settings — DONE, device-verified

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
- The **growth-reference line** (`WHO 2006 · v2026.10 · updated …`) joins this screen once Phase 10 lands.

**Verify:** switching a unit family changes the display and leaves the stored `events` row byte-identical
(the stored amount must not be rewritten); editing a birth date changes the age on the Activity screen;
an export opens as readable JSON and round-trips the row count.
**Commit:** `feat(settings): units, theme, children and export`

## Phase 8 — History — DONE, device-verified

Built: `domain/Totals.kt` (pure, `zone`-explicit `dayRange` / `isNight` / `totalsFor`), a shared
`ui/home/ScreenHeader.kt` now used by Activity **and** History, `ui/history/HistoryScreen.kt` +
`HistoryViewModel.kt`, and `EventDao.observeBetween` so the range read is live rather than a snapshot.

Settled during planning: ranges are CALENDAR days; Both mode adds a per-child line; the day/night split
uses a CONFIGURABLE window (default 18:00-06:00, edited in Settings → Day & night); all six activity
cards appear, with multi-select filter chips (session-only). Growth and Health are "latest in range"
rather than sums, because a total weight is meaningless. `EVENT_DISPLAY_ORDER` moved into the domain so
the dashboard and History cannot disagree about card order.

**Commit:** `feat(ui): history totals`

## Phase 9 — Daily-use polish — DONE, device-verified

Fixes and small features reported after using the Phase 8 build. Cross-cutting by nature: it touches the
logging sheets, the cards, History, Settings and the shell, so it is not a stage of its own — it is the
pass that makes Stage 1's screens comfortable to actually live with.

- Logging rows show a NEUTRAL child badge and the VOLUME. The brand is identical on every bottle and
  distinguished nothing, so Both mode used to draw two identical rows.
- A shared `ChildBadge` with the initial genuinely centred — it was not, because a `Text`'s line box is
  taller than its glyph.
- The bottle amount is free text plus EDITABLE presets stored canonically in ml (Settings → Bottle
  presets). The stepper is gone: it cannot express 135 ml.
- Sheets say who they log for, and Bottle/Diaper/Solids gained a Date row, so a 1am feed can be filed on
  the previous day instead of silently landing on the new one.
- The solids reaction can be cleared.
- History filter chips take their activity's own accent.
- Settings is one CARD per section, so the boundaries are unmissable.
- The top fade follows the ACTIVE destination's scroll — Settings and History had none, because the scrim
  read Activity's list — and Settings' bottom inset no longer scrolls, so content cannot pass under the
  transparent system navigation bar.

**Commit:** `feat(ui): daily-use polish`

# STAGE 2 — Growth and Health

## Phase 10 — Growth (the heaviest single feature)

**Task 10.1** Bundle the baseline dataset. WHO's `igrowup` LMS tables (weight-for-age,
length/height-for-age, head-circumference-for-age, weight-for-length/height, BMI-for-age; boys and
girls) as a compact JSON asset under `app/src/main/assets/growth/`. **Do not invent percentile maths** —
LMS is published data and the z-score formula is standard. Verified source URLs, row counts, sex codes
and file hashes are recorded in `research/growth-data-sources.md`; that file also documents that **WHO
has not revised these standards since 2006**, which is why the asset ships bundled rather than being
fetched on first run.

**Task 10.2 — dataset versioning and updates.** Ship a bundled baseline, then make the data updatable
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

**Task 10.3** Preterm — **settled: both born prematurely.** Not a hypothetical
path.

- Default to plotting **adjusted age** on the WHO charts, toggle visible. At premature this is a large
  correction, not a rounding detail.
- Store gestational age **in days** (210) to keep the adjusted-age subtraction exact.
- Their **birth** measurements were taken at premature, which the WHO 0–5 standards do not describe:
  that belongs to **INTERGROWTH-21st newborn size for very preterm infants** (covers <33 weeks). Add
  that dataset family for interpreting birth entries while the ongoing charts stay WHO + adjusted age.
- Both are boys, so only the boys' tables are exercised in practice — bundle the girls' tables anyway.
  A few hundred KB, and the app should not be structurally sexist.
**Task 10.4** `Percentiles.kt` — pure and heavily tested: LMS → z-score → percentile, and the inverse for
plotting a curve. Unit tests against known published values.
**Task 10.5** `GrowthChart` — segmented Weight/Height/Head Size, percentile curve bundle, child marker,
callout card with date, value, percentile and **age (adjusted when the toggle is on)**.
**Verify:** a seeded measurement lands on the correct curve; a preemie's adjusted age changes its
percentile. **Commit:** `feat(growth): who percentiles and charts`

## Phase 11 — Health

Temperature, medication, vaccine — the same sheet pattern, reusing `InputRow`. Small once Phase 6
exists. **Commit:** `feat(health): records`

# STAGE 3 — Trends, sync, ship

## Phase 12 — Trends
Charts over time for feed amount, diaper count, sleep duration, growth. Built last on purpose: it needs
real data in the database before the design can be judged.

## Phase 13 — Two accounts, one shared log
Firebase project, Google sign-in, security rules restricted to two uids, outbox drain with
`SetOptions.merge()` and client-generated ids (idempotent re-runs), inbound listener with
`updatedAt`-wins, and **tombstones that round-trip** so a deleted event never resurrects.

**Caveat carried over from Phase 7 — must be revisited here.** "Delete permanently" on a child
HARD-deletes the child row and their events (`MaintenanceRepository.deleteChildPermanently`), so there is
no tombstone for sync to propagate and the other phone could resurrect them on the next merge. Either
route a permanent deletion through the outbox as an explicit delete document, or make it a

**Acceptance:** log on phone A, see it on B; airplane-mode three writes on A, restore, all three arrive
once, no duplicates.

## Phase 14 — Update delivery

Getting a new build onto both phones without a cable. This has to precede the ship step, because a
release-signed APK is the thing being delivered.

- **Release signing**: generate the keystore, wire `signingConfigs`, and back the keystore up **outside
  the repo**. It never enters git.
- **Delivery**: Firebase App Distribution (free, no Play Store) on the Firebase project from Phase 13,
  or a hosted APK link. Installing a new build becomes a tap on both phones instead of running
  `adb install -r` twice.

**Verify:** a build reaches both phones over the air, without a cable.
**Commit:** `chore(release): signed builds and over-the-air delivery`

## Phase 15 — Ship
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
   Add it at Phase 14, not Phase 1.

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

---

## Phase 7 device verification — 2026-10-14

Samsung S23 Ultra (SM-S918U1), Android 16, dark mode. Screenshots: `docs/timeline/2026-10-14-phase7/`.

| Check | Result |
| --- | --- |
| Settings opens from the header icon; system back returns to Activity | pass |
| Appearance, Units, Children and Data all render; the last row clears the nav bar | pass |
| **Changing a unit is display-only** | pass — the bottle row's `amountMl` (90) and `updatedAt` were byte-identical before and after switching ml → fl oz, and the card then read oz |
| Add child → 3 children → the **Both** pill disappears | pass — three pills, no Both; selection fell back to the first child |
| Archive a child | pass — the row survives as a tombstone (`deleted = 1`), the child leaves the switcher, and Both returns at two |
| Export | pass — `babylog-backup-2026-10-14.json`, 19,364 bytes: schema 1, 3 children (including the tombstone), 31 events, 92 foods, custom `mango-lassi` included |
| Delete permanently | pass — the confirmation names the child and is highlighted in `danger`; afterwards the second twin's row and his 15 events were gone (31 → 16) |
| Import | pass — the replace-everything confirmation offers **Export first**; after Replace, children (3), events (31) and foods (92) all matched the file |
| No crash at any point | pass |

### Found while writing it

- **The first-run seed could resurrect removed children.** `seedIfEmpty` guarded on `childDao.count()`,
  which filters `deleted = 0`, so removing every child made the app look fresh. Seeding now runs behind
  a one-way `SettingsRepository` flag (`seedChildrenOnce`), and an import marks it done. `ChildSeedOnceTest`
  pins it.
- **`ChildSelection.resolve` fell back to *all* children** for a missing child, which would have shown a
  combined view for three children that the switcher no longer offers. It now falls back to the first
  child in sort order, and `Both` is honoured only at exactly two.
- **The skill's `design-tokens.md` was stale**: it still asserted "cards are NOT tinted" and described
  the rejected accent bar, contradicting the shipped 26% tint. Corrected in the same pass, with the
  measured derivations.

---

## Phase 8 device verification — 2026-10-14

Samsung S23 Ultra (SM-S918U1), Android 16, dark mode, device timezone `America/Port_of_Spain`.
Screenshots: `docs/timeline/2026-10-14-phase8/`.

Every number below was hand-counted from the Room database in the DEVICE'S timezone first, then compared
against the screen. The totals are not trusted because they look plausible.

| Check | Result |
| --- | --- |
| History opens from the nav tab with the SAME header and child switcher as Activity | pass |
| `1d` totals match a hand-count | pass — 3 bottles / 330 ml = **11.2 oz**, average 110 ml = **3.7 oz**, 2 diapers (day 2, night 0) |
| `7d` totals match a hand-count | pass — 7 sessions / 330 ml = 11.2 oz, 20 diapers (day 20, night 0) |
| Per-child line in Both mode | pass — Child 1 7.1 oz + Child 2 4.1 oz = the 11.2 oz combined headline |
| **Live update**: log on Activity, History follows without a relaunch | pass — 7 → **9 sessions** and 11.2 → **17.2 oz**, matching the DB after the write |
| Filter chips hide a card | pass — turning Diaper off left the chip but removed the card and its Day/Night rows |
| **Configurable night window moves the split** | pass — 18:00-06:00 gave Day 20 / Night 0; 06:00-18:00 gave Day 0 / Night 20, exactly as predicted from the diaper hours (10:00 and 15:00) |
| No crash at any point | pass |

### Notes

- **The average bottle is the mean of bottles that RECORDED an amount.** Four of the nine 7d sessions have
  a null amount, so 510 ml over 5 measured bottles is 102 ml (3.4 oz), not 57 ml. Averaging the nulls in
  as zeros would have produced a confidently wrong number.
- `Time between feeds` reuses `elapsed`, so it reads `7h 12m 36s`; the seconds are noise in a summary.
  Left as-is rather than adding an unplanned formatter — a candidate polish item.
- The window is computed when the range is subscribed. Calendar days are stable for the day, so only an
  app left open across midnight would show a stale `1d`.

---

## Phase 9 device verification — 2026-10-14

Samsung S23 Ultra (SM-S918U1), Android 16, dark mode. Screenshots: `docs/timeline/2026-10-14-phase9/`.

| Check | Result |
| --- | --- |
| Recent rows show the child and the volume | pass — `L · 3 oz · 13:09` and `K · 3 oz · 10:31`; the diaper rows read `L · Wet` / `K · Dirty` |
| The child badge letter is centred | pass — verified at 6x zoom (`02-badge-zoom.png`) |
| The bottle sheet states who it logs for | pass — subtitle `for both` |
| Free-text amount plus presets | pass — an `Amount (fl oz)` field and chips `2 / 3 / 4.1 / 5.1 oz`, which are the 60/90/120/150 ml defaults rendered in the selected unit |
| The Date row back-dates | pass — set to 13 Oct, saved, and the DB shows two rows at `2026-10-13 14:11` local with the time-of-day preserved |
| **An untouched prefill keeps its stored value** | pass — 90 ml displays as "3" oz; saving without editing now stores **90**, where before the fix it stored **89** |
| The solids reaction can be cleared | pass — the Reaction row now offers `None` |
| History filter chips are colour-coded | pass — Bottle gold, Diaper green, Solids coral, Growth violet, Health magenta, Sleep sky, matching their cards |
| Top fade on History | pass — scrolled content dims out under the status bar instead of colliding with the clock |
| Settings sections alternate shades | pass — sampled pixels: `surface` (34,37,52) → gap → `surfaceRaised` (45,49,61) → gap → `surface` |
| No content under the system navigation bar on Settings | pass — content ends at y=2102 of 2316; below it is the background and then the nav bar's own scrim, never content |
| The bottom bar reads solid | pass — a divider above it and an opaque `surface` fill |
| No crash at any point | pass |

### Found on device

- **Opening a sheet and pressing Save silently rewrote the amount.** 90 ml renders as "3" fl oz, and
  re-parsing "3" stored 89 ml — so merely visiting the sheet changed the log by a millilitre. Fixed by
  keeping the stored value while the prefilled text is untouched.
- The Settings gear lives in the SCROLLING header, so it scrolls out of reach; reaching Settings from a
  scrolled Activity or History needs a scroll back to the top first. Not changed — recorded as a UX
  wrinkle rather than fixed in this pass.
