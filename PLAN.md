# Babylog V1 — Implementation Plan

> The design contract — tokens, components, layout, insets/back requirements and the measured colour
> rules — is in `AGENTS.md`. Read it before any UI or data task.

**Goal:** A private, offline-first Android baby tracker for two parents of **twins born early** —
record bottle, solids, diaper, sleep, growth and health events, see both
children at once, and read the totals — with nothing else in it.

**Architecture:** Single-module Kotlin/Compose app. `Room` is the **source of truth**; every write
lands there first and the UI observes `Flow`s. An `outbox` table carries unsynced writes to Firestore
(Phase 13), where a listener mirrors the other parent's writes back into Room. Pure logic (age and
adjusted age, durations, relative time, day grouping, totals, percentile lookup) lives in plain Kotlin
so it is testable without an emulator.

**Tech Stack:** Kotlin (via AGP 9 built-in Kotlin) · Compose + Material 3 · Room + KSP · DataStore ·
Firebase Auth + Firestore · JUnit. AGP 9.4.1 / Gradle 9.8.1 / Compose BOM 2026.09.00 / JDK 25 (JBR).

**Effort honestly:** this is a multi-week project, not a weekend — the scope the owner confirmed is close to
the two apps it replaces, minus the content, the shop and the paywall. Hence the staging below: **Stage 1 is usable, and
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
| 8 — Summary (was History) | **DONE, device-verified** — calendar-day totals, configurable day/night split, filters, 149 tests green |
| 9 — Daily-use polish | **DONE, device-verified** — child badges, free-text amount + presets, Date rows, alternating Settings cards, shell fades and insets, 158 tests green |
| 9.1 — Card entries, See all, in-place edits | **DONE, device-verified** — today's rows, full entry list, tap-to-edit, long-press-to-delete, Both-mode headline badge + deterministic ordering, 178 tests green |
| 9.2 — Activity glyphs on cards and chips | **DONE, device-verified** — a glyph per activity, tiled tone-on-tone behind each card and on Summary's total cards, and drawn small inside the activity chips; `textOnCard` lifted to keep meta text at AA on the pattern; 405 tests green |
| 10 — Growth | **DONE, device-verified** — WHO LMS checked against WHO's own tables, growth sheet, per-child summary with W/L/H codes, trajectory lines, percentile chart as a Trends tab, 255 tests green |
| 11 — Health | **DONE, device-verified** — temperature, medication picker + dose, vaccines as their own form, a measured Fever pill, schema v2 additive migration, 308 tests green |
| 12 — Trends | **DONE, device-verified** — five per-day charts on a 7/14/30-day window, one line per child (solid/dashed), and a fever table for Health instead of a chart; axis maths shared with Growth, 339 tests green |
| 13 — Two accounts, one shared log | **DONE, device-verified** — two accounts on one shared log, an outbox that is the only queue, an inbound listener that applies another device's rows, and tombstones that stay dead across the hop; 398 tests green. Three passes on 2026-10-22: an unconfigured build (`<handset>`) proved launch, the "not set up" card, every write path queueing and a permanent delete queueing DELETE intents; the configured build proved sign-in, the seed draining, a live ack in under five seconds, and three airplane-mode writes arriving exactly once; then a wipe plus a fresh sign-in pulled the whole log back by row id, and a **second device signed in with the second account** (`emulator-5554`, uid `B43h…`) received the phone's six rows, took a bottle each way, took an offline trio as exactly three rows with no duplicates, and lost a row when the phone deleted it — still gone after that device's process restarted. Runbook: `docs/firebase-setup.md`. Two open findings: "Sync now" is silent offline, and the first-sign-in seed sits queued until a drain is triggered |
| 13.1 — Time and temperature preferences | **DONE, device-verified** — independent 12/24-hour and °C/°F display/input choices, canonical storage unchanged, 409 tests green; `0.10.1` makes 12-hour the default |
| 13.2 — Temperature bands | **DONE, device-verified** — four bands marked by shape (filled `Fever`, outlined `Mild` / `Low`, no pill on normal), classified from the value as displayed; Trends lists and counts mild readings too; `0.11.0`; 417 tests green |
| 13.3 — Growth dataset update channel | not started — split out of 13, `0.12.0` |
| 13.4 — Reset to defaults | not started — scoped 2026-10-22, `0.13.0`; **not started in this session, by the owner's instruction** |
| 14 — Update delivery | not started |
| 15 — Ship | not started |

**Versions: one rung per phase**, and the ladder is a fact of the history rather than a paragraph about
it — `versionCode`/`versionName` were rewritten so each phase's commits carry that phase's rung, set at
the **first commit of the phase's work** so a build on a phone says which phase produced it. `0.MINOR` is
a new capability, `0.PATCH` is fixes and polish only, and `1.0.0` is the first release. The rungs are
listed in every dated `CHANGELOG.md` section under `### Versions`, and the rule is in `AGENTS.md`.
Nothing is tagged: tags are for releases, so the first is `v1.0.0` at Phase 14. The original scheme note
that assumed this work happened per-release ("versionName is 0.1.1 today") was corrected here.

Test counts in the table are the suite size **when that phase landed**, so they trail the live total
below — Phase 11 landed at 308, for instance. They are evidence at the time, not a running count.

Verified: `:app:assembleDebug` builds; `:app:testDebugUnitTest` is **417/417** with 0 failures, 0 errors
and 0 skipped, summed from `app/build/test-results/testDebugUnitTest/*.xml` — the 409 of the time and
temperature-preference close plus 8 temperature-band cases (`TemperatureBandTest` 6 and `TrendsTest` 2).
The 409 were the 405 of the activity-glyph close plus 4 preference tests on 2026-10-23
(`DisplayPreferencesTest` 3 and `SettingsRepositoryTest` 1). The 405 were the 398 of the Phase-13 close plus the 7 activity-glyph tests
(`PatternPaletteTest` 5, `ActivityGlyphTest` 2); the 398 were
the 339 of Phase 12 (the 308 the Phase-11 row quotes, plus the three `HistoryFilterTest` cases that arrived
with the Summary rename and its All/None filter chips, plus the 28 `TrendsTest` and
`TrendChartAxisTest` cases) and the 49 sync tests Phase 13 added (the "feat(sync): two accounts, one
shared log, and an outbox that is the only queue" commit). The
Phase-5 layout, the two launch bugs, and the Phase-6, 7, 8, 9, 9.1, 10, 11 and 12 screens were all confirmed on a
real device (see the device-verification sections at the end of this file), as was Phase 13 on
2026-10-22, the glyphs and preferences on 2026-10-23, and the temperature bands on 2026-10-24 — a green
build proves nothing about layout or about system back. Toolchain versions and the AGP 9 / KSP traps that cost build
cycles are in `AGENTS.md` and in the Phase 0 row below.

## Phase 9.2 — Activity glyphs on cards and chips — DONE, device-verified (`0.9.1`)

**As built — 2026-10-23. Read this before the numbers.**

Each of the six activities now carries its own glyph, in three places: tiled **tone-on-tone** as a
watermark filling its dashboard card, on Summary's per-activity total cards, and small and solid inside the
activity chips (Summary's filter row, Trends' tabs). Shape now identifies an activity as well as hue, for
the same reason a fever got a pill rather than a red number. `EventType.glyphRes()` is the single mapping;
`Modifier.activityPattern()` draws the tile.

**Where the design came from, measured rather than chosen.** A live Huckleberry screenshot taken from the
phone (`com.huckleberry_labs.app` on `<handset>`, saved beside the repo, not committed) draws one large
glyph per card, clipped by the card's left edge, in its own fill darkened to **dE 9-18**. The owner's
infographic reference (`Three Options Infographic Cards.png`) tiles **white** glyphs at ~8% of card width
over a saturated fill — a recipe that fails on our cards in both directions: over the 26% tint a white
pattern measures dE 3 at alpha 0.04 and drops `textOnCard` to **4.2:1**, and at alpha 0.16 it is dE 12.5
and 3.1:1; on the light theme white over a near-white tint is **invisible** (dE 1-4 at every alpha tried).

So the pattern is the card fill pushed **0.14** toward its own accent (`patternFor`), which is dE 7-11 on
dark and 7-10 on light, and the one cost was paid in the token it lands on: **`textOnCard` moved from
`#C2C6D6` to `#DCDEE7`** (dark) and `#41506B` to `#404E69` (light), because the pattern sits behind that
text — 3.2:1 before, 4.5:1 on the worst tile and 5.9:1 on the plain tint after. `PatternPaletteTest`
asserts it for all six accents in both themes, so a retuned accent or a "subtler" alpha fails the build
instead of shipping an unreadable card.

### As built, where it differed from the plan

- **The glyph is 26dp on a 64dp pitch, not 18dp on 46dp.** The first pass was built at 18dp and the owner
  read it on the phone as too small to work as texture. The pitch is what the seamlessness constraint sets,
  not taste: with the second row offset half a pitch, a glyph may be at most half a pitch wide, so 26dp
  needs a pitch of 52dp or more and 64dp was chosen for the gap.
- **Tiling is an `ImageShader` over a pre-rasterised `ImageBitmap`, not `BitmapPainter` + `ContentScale.Tile`.**
  Tile scales a bitmap by the painter's intrinsic size, so the pattern's spacing would follow the density
  rather than the dp grid. The shader repeats the tile at 1:1 device pixels, and the tile is built once per
  type, tint and density and held in `remember`.
- **The diaper glyph was redrawn after the phone pass.** The first version was one silhouette — two tabs and
  a centre dip — and on the card it read as a letter `M` repeated down the screen. It is now a waistband bar
  above a rounded pad, with the bar wider than the pad so it cannot be confused with the Solids bowl.
- **The phone had to be uninstalled before this build would install.** `install -r` answered
  `INSTALL_FAILED_UPDATE_INCOMPATIBLE` — the installed `0.9.0` carried a different signing key — so the local
  database was wiped and the log came back by signing in, which is the per-machine debug keystore consequence
  `docs/firebase-setup.md` already records. The owner approved the wipe before it was done.
- **Scoped as polish and closed as a sub-phase.** It was planned as `0.9.1` polish on the Phase-13 rung, so
  it carries a `0.PATCH` bump and the next capability rung is still `0.10.0` (Phase 13.1). The row and the
  heading marker were added when it was closed out.

### Device pass, 2026-10-23

`<handset>`, in-place `install -r` of the `0.9.1` debug APK (`versionCode=12`, `versionName=0.9.1`, read
back from `dumpsys package`). Every tap coordinate came from a `uiautomator dump`, and the foreground was
checked before the first input: `dumpsys activity activities` reported
`Resumed: ActivityRecord{… app.babylog/.MainActivity t1919}`.

| Check | What the phone showed |
|---|---|
| The pattern fills all six dashboard cards | Today, dark: bottle, nappy, bowl, ruler, thermometer and moon each tiled in their own hue at 26dp, visible without reading the label |
| Summary's total cards carry the same pattern | The `Bottle feed` and `Diaper` total cards are patterned exactly as their dashboard cards |
| The activity chips carry the glyph | Summary's filter row shows a glyph in all six activity chips; the bulk `None` chip correctly has none. Trends' tabs show `Bottle feed` with the glyph in `onAccent` (selected) and `Diaper` / `Solids` in their accents |
| The light theme is not a smudge | Settings → Theme → `Light`: the pattern is visible on every card and every label still reads — the theme the measurement said was most at risk (dE 7-10) |
| Settings opens from the header icon | The `Settings` node (`content-desc="Settings"`, clickable ancestor `[900,289][1035,424]`) opened Settings |
| System back returns to Today | `input keyevent 4` from Settings returned to Today with the switcher and all cards intact |
| No crash | `logcat -b crash -d` held **0** `FATAL EXCEPTION` across the install, the navigation and the theme change |

**A trap worth naming for the next pass:** one `uiautomator dump` returned the *previous* screen's tree while
Settings was already open, so the dump said "Today" and the tap that had worked looked like it had failed.
The screenshot was the source of truth for which screen was up; the dump is only good for coordinates taken
immediately before a tap.

**Found on the phone, not fixed here:** at 1.3x font scale the `Both` pill in the child switcher wraps to
`Bot` / `h`. Pre-existing, unrelated to the glyphs, and its own change.

**Open question left for the owner:** whether the glyph should also reach the sheet title bars, the entry
rows, Growth's metric chips and the range chips. Deliberately not included here.

## Phases 0-2 — where the evidence is

Their body sections were collapsed into the Status table during the twins rescope (the "docs: correct
the design system against real screenshots; rescope for twins" commit and its follow-up), which left
the table as their only trace. So the trace, concretely — these three phases
were never device-verified and do not claim to be; their evidence is artifacts, commits and tests:

| Phase | What landed | Evidence |
|---|---|---|
| 0 — Toolchain | Studio, SDK and the environment, read off the machine on 2026-10-12 | Studio 2026.2.1.8, JBR 25.0.3, platform-tools 37.0.1, `android-36`, and **no emulator, deliberately** (Task 0.5 dropped: test on the real phone). `scripts/android-env.ps1` and `scripts/sdkmanager.ps1` are its reusable output |
| 1 — Scaffold, theme, tokens | The hand-scaffolded Gradle set (no wizard) and the token/theme/type files | the "feat: scaffold Compose app, get a green debug build" commit — green debug build, and `aapt2 dump badging` then read `package: app.babylog versionName='0.1.0' compileSdkVersion='37' targetSdkVersion:'36'` (the ladder's first rung, and no longer "0.1.1 today"). `gradle/libs.versions.toml` pins AGP 9.4.1 / Compose BOM 2026.09.00, the wrapper pins Gradle 9.8.1, and
`app/build.gradle.kts` sets `compileSdk 37 + compileSdkMinor 2` on JDK 25 (JBR). `ui/theme/Tokens.kt`, `Theme.kt`, `Type.kt` — dark sampled by pixel clustering from 15 real the incumbent tracker screenshots, light designed and labelled as designed, reworked in the "docs: correct the design system against real screenshots; rescope for twins" commit |
| 2 — Domain | Event model and the pure logic, TDD with no Android in the loop | the "feat(domain): event model, relative-time/duration formatting, day grouping" commit and the "feat(domain): unit system with exact conversions and compound lb/oz formatting" commit. `domain/` — `BabyEvent.kt`, `Formatting.kt`, `EventOrder.kt`, `Units.kt`, `Age.kt`, `Totals.kt`; 12 tests green at the phase, now the suite under `app/src/test/java/app/babylog/domain/` |

---

# STAGE 1 — the 3am app

## Phase 3 — Room schema, DAO, tests — DONE (schema v1 exported and committed; 50 tests green)

### Task 3.1 — Entities
Create `app/src/main/java/app/babylog/data/local/`:
- `ChildEntity` — id, name, birthDate, **weeksPremature: Int?**, sex, useAdjustedAge: Boolean, sortOrder.
  Birth details are **data, never constants** — birth dates are set in Settings and must be
  editable there.
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
(`First Twin`/`Second Twin`/`Both`), `useAdjustedAge`, and a theme override defaulting to system.
`domain/Units.kt` is **already written and tested** (13 tests) — exact factors, `lb&oz` compound
formatting including the 16-ounce carry, unit-appropriate stepper increments, Celsius-only
temperature. `UnitPreferences.system` is derived, never stored.
**Task 4.3** `Age.kt` — pure, tested: `chronologicalAge(birth, now)`, `adjustedAge(birth, weeksPre,
now)`, formatting as `9 months, 1 week, 5 days`. Adjusted age = chronological minus weeks premature,
**and the premature offset is per child**. Tests must cover a preemie and a full-term child.
**Verify:** tests green. **Commit:** `feat(data): repository, settings, age with adjusted age`

## Phase 5 — Activity screen (the home) — DONE (device-verified)

Built: `AppScaffold` (insets + back), `ActivityScreen`, `ChildSwitcher` (First Twin · Second Twin · Both),
`ActivityCard`, `HomeViewModel`, `AppContainer` (manual DI), first-run seed of the two children.

**Honest status: it compiles and the APK builds, but no one has LOOKED at it.** Compose layout
errors — a card that overflows, text that clips, a pill row that wraps badly — do not fail a
build. Visual verification needs a device, an emulator, or Roborazzi screenshot tests; until
then the layout is unverified. See the *Verifying the UI* note at the end of this file.

The Phase-5 `quickLog` path logs events with only a timestamp so the layout can be judged;
Phase 6 replaces it with the real per-activity sheets.

**Task 5.1** `MainActivity` + `AppScaffold` — `Scaffold(contentWindowInsets = WindowInsets.navigationBars)`
and a `LazyColumn` whose `contentPadding` clears `bottomBarHeight` **plus** the nav-bar inset. This is
the fix for the owner's complaint #1; get it right before any content exists to hide behind it.
**Task 5.2** `ScreenHeader` + `ChildSwitcher` — First Twin · Second Twin · Both, outline on the selected pill.
**Task 5.3** `ActivityCard` — tinted surface, accent label + relative time, full-width action button
reading `+ Log … for both` / `+ Log … for First Twin`, recent rows, `See all ›`.
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
  per-child `Use adjusted age` toggle. Birth details are **data, never constants** — the birth date
  and gestational age are editable per child and default to the values the Settings screen holds.
- **Export / backup**: a human-readable JSON export of the whole log to the phone (share sheet / SAF).
  The export stays plain text by design — no binary payloads. This is the single highest-value
  insurance in the plan: it means a bad migration can never lose the log, and it unblocks restoring
  onto a replacement phone.
- The **growth-reference line** (`WHO 2006 · v2026.10 · updated …`) joins this screen once Phase 10 lands.

**Verify:** switching a unit family changes the display and leaves the stored `events` row byte-identical
(the stored amount must not be rewritten); editing a birth date changes the age on the Activity screen;
an export opens as readable JSON and round-trips the row count.
**Commit:** `feat(settings): units, theme, children and export`

## Phase 8 — Summary (was History) — DONE, device-verified

Built: `domain/Totals.kt` (pure, `zone`-explicit `dayRange` / `isNight` / `totalsFor`), a shared
`ui/home/ScreenHeader.kt` now used by Activity **and** History, `ui/history/HistoryScreen.kt` +
`HistoryViewModel.kt`, and `EventDao.observeBetween` so the range read is live rather than a snapshot.

Settled during planning: ranges are CALENDAR days; Both mode adds a per-child line; the day/night split
uses a CONFIGURABLE window (default 18:00-06:00, edited in Settings → Day & night); all six activity
cards appear, with multi-select filter chips (session-only). Growth and Health are "latest in range"
rather than sums, because a total weight is meaningless. `EVENT_DISPLAY_ORDER` moved into the domain so
the dashboard and History cannot disagree about card order.

**Added 2026-10-18** (a second pass on this screen, after the tab was renamed): one **bulk chip** leads
the filter row — `None` while every activity is shown, `All` once anything is hidden, so a partial
selection reads `All` and the three states cycle. The rule lives in the pure `ui/history/HistoryFilter.kt`
with `HistoryFilterTest`, not in the composable. With every activity off the totals area shows a neutral
card, `No activities selected` / `Pick at least one above.`, with no button: the chip that caused it is
directly above. Filtering stays session-only. The entry-delete confirmation was reworded in the same pass
to `You can undo it from the next message.` — the wording first asked for, "This action cannot be undone.",
would have been false while the 10-second Undo snackbar exists.

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

## Phase 9.1 — Card entries, See all, in-place edits — DONE, device-verified

Four defects reported after using the Phase 9 build, all of them on the Activity cards. Treated like
Phase 9: a pass in place, not a new stage. No schema change, no DAO change, no new query —
`HomeViewModel` already loads every event for the selection, so the cards and the entry list are two
views of the same list.

- **`See all ›` did nothing.** `ActivityScreen` passed `onSeeAll = {}`, and the footer only rendered
  when a card happened to hold three events of a type, so in the ordinary case it was not even drawn.
  It now opens `EventListOverlay`: every entry of that activity for the current child/both selection,
  all time, newest first, grouped by day (`Today` / `Yesterday` / `Wed 12 Mar`).
- **A card could never show more than two rows.** `CardBuilder` kept the newest three of each type and
  `ActivityCard` drew `recent.drop(1)` because the big number had consumed the newest of those — and the
  three were "the last three ever", printed with a bare clock time, so yesterday's feed was
  indistinguishable from one an hour ago. Cards now list **today's** entries, newest first, capped at
  five, **including the entry behind the big number**. 4 today + 1 yesterday shows 4.
- **The Both card's numbers read as wrong; the data was fine.** Second Twin's 14:14 feed was the headline on
  Second Twin's card and a row on the Both card, so the two lists could not be compared and its 13:09 feed
  looked missing. The headline keeps its meaning (the newest event of that type in scope) and now
  carries the child's badge and the absolute time when the scope is both children, and the rows include
  it — so the per-child list and the Both list finally agree.
- **Rows wasted their space:** 8dp of vertical padding around a 20dp line of text in a 35dp row, an 18dp
  badge that said `K` on every row of a single-child card, and a 12dp gap above the block. Now 5dp, a
  16dp badge in Both mode only, and 8dp — about 27dp per row. The tokens are `Spacing.rowVertical`,
  `rowBlockTop` and `rowBadge` so the numbers live with the rest of the measured values.
- **Rows are editable and deletable** (the data layer already had both paths with no UI on them): tap a
  row — on a card or in the list — and that activity's sheet opens pre-filled for **that row's own
  child**, saving back onto the same row via `EventDraft.applyTo` (same `id`, child and creator), so
  editing the first twin's feed never rewrites the second twin's. Long-press confirms a soft delete and offers `Undo`.
- **Ordering is deterministic.** A Both-mode save writes two rows with an identical `startedAt`, and
  `ORDER BY startedAt DESC` alone leaves their order to SQLite. `domain/EventOrder.kt` is now the single
  comparator — newest first, then the configured child order — used by the cards and by the day grouping.

**Verified:** `:app:assembleDebug` builds and `:app:testDebugUnitTest` is **178/178** with 0 failures
(20 new tests: today-only rows and the five cap, the tie-break rules, the edit path's identity
guarantees, and an in-place edit against a real Room database), and the whole set was confirmed on the
S23 — see `## Phase 9.1 device verification` at the end of this file, including the one defect this pass
found in its own new code (the 4-second Undo window).
**Commit:** `fix(cards): today's entries, a working See all, and in-place edits`

# STAGE 2 — Growth and Health

## Phase 10 — Growth (the heaviest single feature) — DONE, device-verified

### As built — 2026-10-15. Read this before the tasks below.

Shipped: the WHO dataset, the percentile maths, the growth logging sheet, the per-child card summary and
the chart.
**255/255 tests green**, and the dataset is verified against WHO's own published tables (every measure,
both sexes: L/M/S identical to WHO's, and the app's own transform reproduces WHO's printed SD columns to
0.0005 across z = −3..+3 — see `scripts/verify-growth-asset.py`).

Three changes to what is written below, each for a reason worth keeping:

- **The update channel (Task 10.2) is NOT in this phase.** It says "Firestore is already in the stack";
it is not — Phase 13 has no Firebase project, no dependencies and no `google-services.json`. The manifest,
checksum gate, 90-day cadence and `Check for updates` rows moved to Phase 13, where the project they need
actually exists. What ships now is the bundled dataset plus the identity line the contract requires.
- **INTERGROWTH-21st (Task 10.3) was evaluated and DROPPED.** Its data is not openly licensed: the mirror
repo defers to the Project, and the Project's only terms (The Global Health Network's, Oxford as rights
holder) reserve rights and permit non-commercial education use. No redistribution grant exists, so it is
not bundled. Consequence: a measurement taken before term has **no percentile**, with a sentence saying
why, instead of a percentile from a standard that does not describe it.
- **A growth logging sheet was added** because the plan's tasks chart data and never log it. `ui/home/GrowthSheet.kt`
with the pure `GrowthEntry.kt` under it: weight (kg, or the compound lb/oz pair), length, head, date, time
and a note. The Phase-6 rule holds — an untouched prefill keeps its stored value, so 6355 g displayed as
"6.36" kg cannot be rewritten to 6360 g by opening and saving.

As built: `app/src/main/assets/growth/who-igrowup.json` (488 KB, five measures × both sexes, generated by
the committed `scripts/build-growth-assets.py` with per-file sha256); `data/growth/GrowthReference.kt`;
`domain/Percentiles.kt` (LMS, the normal CDF, the inverse for curves, the `L = 0` log case);
`ui/home/GrowthSheet.kt` + `GrowthEntry.kt`; `ui/growth/PercentileChart.kt` (one hand-drawn Compose
Canvas primitive, no charting dependency, reusable by Phase 12), `GrowthChartData.kt` (the pure rules),
`GrowthViewModel.kt` and `GrowthScreen.kt` — the last of which is now `GrowthChartPane`, the Growth tab's
content rather than a screen of its own. Settings names the dataset. No schema change: `weightGrams`,
`lengthMm` and `headMm` have existed since v1.

What the owner changed after seeing it, all of it in this phase:

- **The chart moved into Trends**, as one tab of a per-activity tab row over every type in
  `EVENT_DISPLAY_ORDER` (Growth functional; the rest say honestly that their charts arrive in Phase 12).
  A chart is something you go to look at, not something to trip over while logging a feed. This brings
  Trends' shell forward from Phase 12; its feed, diaper, solids and sleep charts stay there.
- **The card's summary is per child, one measurement per line.** A combined headline had no owner: "6 kg"
  in Both mode was one child's weight dressed up as the twins'. Each column is that child's own newest
  weight, length and head — and one per line, because three values plus their codes wrapped into an
  unreadable block at half a card's width.
- **Values carry W/L/H codes** wherever they appear, because a bare "42 cm" cannot be told from a length
  or a head circumference. `W 6 kg · L 66 cm · H 42 cm`, in the sheet's field order.
- **Each child's measurements are joined into their own line** over the reference curves, with the join
  into a pre-term point dashed: the birth point sits at adjusted day 0, which is the DUE DATE, so that
  slope is not growth that happened.
- **`See all ›` shows as soon as an activity has one entry, for every card** (previously only when an
  entry was off-card, which left the link invisible exactly when the full list was wanted), and it opens
  the same entry-list overlay as the others.

**Device pass: done, 2026-10-15** — see `## Phase 10 device verification` at the end of this file. The
sheet, the card's per-child summary, the W/L/H labels, the overlay list, the Trends tabs and the chart were
all confirmed on the S23, including three layout defects the phone found and unit tests could not.
**One wrinkle, known and deliberately NOT fixed:** the last Trends chip is clipped at the right edge of the
tab row — six activity chips do not fit a phone's width. Recorded, not changed.

### The original tasks

**Task 10.1** Bundle the baseline dataset. WHO's `igrowup` LMS tables (weight-for-age,
length/height-for-age, head-circumference-for-age, weight-for-length/height, BMI-for-age; boys and
girls) as a compact JSON asset under `app/src/main/assets/growth/`. **Do not invent percentile maths** —
LMS is published data and the z-score formula is standard. Verified source URLs, row counts, sex codes
and file hashes are recorded, with the checksums inlined, in `scripts/build-growth-assets.py`; that script
also records that **WHO
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

- Default to plotting **adjusted age** on the WHO charts, toggle visible. Born early, this is a large
  correction, not a rounding detail.
- Store gestational age **in days** (210) to keep the adjusted-age subtraction exact.
- Their **birth** measurements were taken before term, which the WHO 0–5 standards do not describe:
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

## Phase 11 — Health — DONE, device-verified

As built. The original one-line scope ("temperature, medication, vaccine — the same sheet pattern") grew
in two places, both requested after the first sheet worked:

- **A vaccine is its own sheet and its own button.** A vaccine is entered from a red book, weeks after
the event; a fever is what a parent reaches for at 3am. Putting the vaccine field on the fever form put
an irrelevant question in front of them at the worst moment. `SheetTarget(type, vaccine)` carries the
distinction into the shell, whose sheet `when` still has no `else`.
- **Medication became a picker with a dose**, and the dose became schema. See below.

Built: `ui/home/HealthSheet.kt` (date, time, temperature, medication picker, dose amount + unit, notes),
`ui/home/VaccineSheet.kt` (date, time, name, notes), and `ui/home/HealthEntry.kt` — the pure half that
decides what is stored, mirroring `GrowthEntry.kt`: 30.0–45.0 °C plausibility, the at-least-one-field
rules, the untouched-prefill rule, and `lastDoses()`. `domain/Dose.kt` holds `DoseUnit` and the
dose formatter; `app/src/main/assets/medications/medications.json` holds 14 common infant medicine
**names**; `data/medications/MedicationCatalogue.kt` parses them; Settings grew a `Medication presets`
section beside the bottle presets.

**The dose is real data, so this is the first schema change: v1 → v2.** `EventEntity` gained nullable
`doseAmount`/`doseUnit`, `MIGRATION_1_2` adds both columns, `app/schemas/…/2.json` is committed, and
`MigrationTest` builds a v1 database from the committed `1.json` rather than from a hand-copied CREATE
TABLE, so the test cannot drift from what it upgrades. Additive-only, as the rule requires: no column
was repurposed, nothing is defaulted to zero, and a legacy row's dose is NULL because no dose was
recorded — which is not the same claim as a dose of nothing.

**The medication list never suggests a dose.** That is a safety decision, not a scoping one: an infant
dose depends on weight and age, changes as the children grow, and differs between formulations of the
same drug, so a number this app proposed would be plausible, confident and possibly wrong. The list
offers names; the sheet says `Names only — this app does not suggest a dose`; `MedicationCatalogueTest`
enforces that no entry contains a digit; and the last dose given is shown as grey text from the parent's
own record, never pre-filled. A test is not a clinical review, and the asset says so in its own `note`.

Deviation worth recording: the plan's verification said to read rows back through Settings → Export. A
stronger path existed on this build — `run-as` can read the device database directly — so the stored
dose and unit were read from the DB itself, and the export round trip is covered by `BackupTest`.

### Fixed in this phase

- **`1 drops`.** `DoseUnit` had one label doing two jobs, so a dose of one rendered `1 drops` on a fever
  card. It now carries a picker label plus singular/plural, a fraction takes the singular (`0.5 sachet`)
  and anything above one the plural (`1.5 sachets`). Caught by a new test, not by the phone.
- An amount with no unit is an error rather than a silent drop; a unit with no amount is simply no dose.
- The growth sheet and the health sheet were carrying the same keypad filter; `decimalInput()` now lives
  in `SheetControls`.

### Found by the owner after the first pass — "Fever isn't highlighted in any way"

He was right, and the reason was worse than styling. The card's summary held ONE "record" slot
(temperature OR medication), so the 12:27 paracetamol had taken it from the 12:08 fever: the fever was
not dimmed on the card, it was **absent from it**. Only History still showed it. Three fixes, all
re-verified on the phone:

- **Latest of each kind means three kinds.** `healthLatest` now tracks the newest temperature, the newest
  medication and the newest vaccine (it held two slots, one of which conflated the first two). Each
  child's column shows the reading, then what was given, then the vaccine, with the reading and the
  medication combined into one line when they came from the same record. Pinned by
  `a later medication does not hide an earlier fever`.
- **The marker is now a filled pill, not a tinted number.** A pastel red on a magenta card, beside a label
  that is itself a lifted magenta, is not a signal. `FeverBadge` puts the word in a `feverOnCard`-filled
  pill, measured at **10.39:1 dark / 10.30:1 light** for the label on the fill and **5.71:1 / 6.75:1** for
  the fill against the card tint; the word is no longer part of any label string, so a narrow row cannot
  ellipsise it away. Measured on the phone: **10,800 pixels of `#FFB6B6`** where the tinted-text version had
  3,762.
- **`Type another name…` now gives an empty, focused field.** It used to keep the previously selected name
  and leave the cursor nowhere, so the parent had to clear it and tap again. It clears the field and
  requests focus; verified by typing with no tap landing in that field (its own `EditText` node reads
  `focused=true`).
- **The medication menu no longer fills the screen.** Fourteen names plus the family's own ran from the
  status bar to the navigation bar and hid the form it belonged to; it is capped at
  `MEDICATION_MENU_MAX_HEIGHT` (320dp, ~5 rows) and scrolls inside that — two swipes reach
  `Antibiotic (prescribed)` and `Type another name…`, so nothing became unreachable.

### Original scope, for the record

Temperature, medication, vaccine — the same sheet pattern, reusing `InputRow`. Small once Phase 6
exists. **Commit:** `feat(health): records`

# STAGE 3 — Trends, sync, ship

## Phase 12 — Trends — DONE, device-verified

As built. The plan's one line ("charts over time for feed amount, diaper count, sleep duration, growth")
became five charts and one table, with two scope decisions taken up front rather than assumed:

- **Five tabs chart a per-day number; Health is a table.** Bottle (volume), Solids (meals), Diaper
  (count) and Sleep (duration) join Growth's percentiles, which keep their own pane because they are
  plotted against AGE and not against the last N days. Health is a dated list of fever readings plus a
  count of fever DAYS: a temperature is a reading at a point in time, and averaging two readings is a
  number nobody should act on. A line drawn between two readings three days apart is a line through
  nothing.
- **A 7 / 14 / 30-day window, default 14.** One chip row, the same idiom Summary's range chips use. 14
  days gives twins' feeding and sleep rhythm a visible shape without a wall of thirty thin bars; 30 is
  there for the longer view.

Built: `domain/Trends.kt` (the whole calculation, pure), `ui/trends/TrendsViewModel.kt`,
`ui/trends/TrendChart.kt`, `ui/trends/TrendPanes.kt`, `ui/trends/HealthTrendPane.kt`, and the rewritten
`ui/trends/TrendsScreen.kt` (the six-tab row is unchanged; the "arrive in Phase 12" placeholder is gone).

**Bucketing rules, all of them day-boundary rules.** A day is the LOCAL calendar day, which is why this
is Kotlin and not SQL — the same reason `Totals.kt` exists. A day with no records is **zero and drawn as
zero**, and every pane says so in words under the chart: the app cannot tell "no nappies" apart from
"nobody logged one", so it must not draw a hole and imply it knows the difference, and it must not claim
a zero it did not observe without saying which it is. A running sleep contributes no duration yet —
counting it as "until now" would make the newest bar grow while nobody is looking, exactly as History
refuses to guess. A bottle logged with no amount is a day RECORDED with a volume of zero, because
"was anything logged" and "how much" are different questions.

**Every chart is per child.** Both is the default mode and therefore the default chart: one line per
child, so a merged average can never be presented as if it spoke for both — the rule the Health card's
columns and Growth's callouts already follow. The two lines are **solid for the first child and dashed
for the second**: two shades of one hue turned out not to be enough to tell the twins apart on the phone,
and a second hue is not available, so the distinction is carried by shape — the same reasoning that gave
the fever marker a pill instead of a colour.

**The axis maths is now shared.** `yRangeFor` and `yTicks` moved out of `PercentileChart.kt` into
`ui/charts/ChartAxis.kt`, and the two growth tests import them from there. Behaviour is unchanged (the
same functions, moved), and Growth's chart was re-verified on the phone afterwards. `yRangeFor` gained a
`floorAtZero` flag: a per-day chart must start at zero — eight nappies on a Tuesday and none on a
Wednesday is a shape — while a weight curve starting at zero would flatten every curve into a line.

**What the phone caught, after all 338 tests were green** — the reason this phase is not done on a build:

| Found | Fix |
|---|---|
| **`0` beside a non-zero total.** 13 nappies over 30 days averaged to `0 per day`; 2 solids read the same. A summary card contradicting its own total is how a parent stops trusting the screen | The average keeps one decimal (`0.4`, `0.1`), and a non-zero average that still rounds away says `< 0.1` rather than claiming nothing happened |
| **A count axis ticked in fractions** — `0.2`, `0.4` meals, which is a category error rather than precision | `integerTicks` for count metrics: whole numbers only, falling back to `0` and the rounded maximum when the whole ticks collapse below two lines |
| **Two lines that were only shades of each other** | Solid vs dashed, carried into the legend as a line sample rather than a dot, so the swatch cannot hide half the cue |

A fourth defect never reached the phone: the new unit test on the x-axis labels found that appending the
newest day on top of a 2-day step put two labels one day apart on a 14-day window — 21dp apart for a
label that measures ~33dp — so they would have overlapped. The labels are now counted back from the
newest day, which keeps them evenly spaced and always names today.

**Verified against the device's own database, not just the screen.** Every figure was recomputed from the
phone's `events` table with `run-as` (a stronger check than reading the screen, and the same path Phase 11
used): First Twin 819 ml = 27.7 oz over 3 of 14 days with a best day of 730 ml = 24.7 oz on 14 Oct;
Second Twin 566 ml = 19.1 oz, best 477 ml = 16.1 oz on 14 Oct; 13 diapers each on 2 of 30 days with 9 on 12 Oct;
2 solids each; one 13m 5s sleep on 15 Oct; 2 temperatures, both 38.5 °C, 1 fever day. All match the screen
exactly. Note the 30-day window carries no more data than the 14-day one on this install — every logged
row falls inside the last 14 days — so the 30-day range was exercised for its bucketing and its labels,
not for extra events.

**Also exercised on the phone:** the light scheme (the charts are the first thing drawn from tokens that
had never been seen in light mode), and a 1.3× font scale, where the summary card grows past the viewport,
the list scrolls, and the last row clears the navigation bar. Not exercised: the empty state for a type
with no records in the window, because every activity has data inside the last 7 days on this device. That
branch is covered by `TrendsTest` instead, and by the empty states Summary and Growth already ship.

**Tests:** 28 new on the JVM — `TrendsTest` (23), which pins the local-day buckets, the zero-fill, the
per-child split, the running-sleep rule and the fever boundary at exactly 38.0, and `TrendChartAxisTest`
(5) for the x-axis labels, the count ticks and the dashed second child. The suite is **339, 0 failures**.

## Phase 13 — Two accounts, one shared log — DONE, device-verified

**As built — 2026-10-19. Read this before the original scope below.**

Six decisions were taken before any code, because each one changes the shape of the work:

- **Email + password is the primary sign-in; Google Sign-In is the second button.** Email/password works
  with whatever key the APK was signed with. Google Sign-In needs an OAuth client plus a registered
  certificate SHA-1 — this machine's debug print is recorded in `docs/firebase-setup.md` and has to be
  added to the Android app in the console, which nobody has confirmed yet — and **the release key only
  exists in Phase 14**, so that step is a Phase 14 checklist item rather than a Phase 13 one.
- **No `com.google.gms.google-services` plugin.** That plugin fails the build when
  `app/google-services.json` is missing, and that file is gitignored because it carries the project's
  keys — so a fresh clone could not even run the tests. The same values are read from an optional local
  `firebase.properties` into `BuildConfig` and handed to `FirebaseOptions`. With no properties file the
  app starts as the single-phone logger it has always been, and Settings → Sync says exactly that.
- **Permanent delete is fixed with DELETE intents, not a new column.** A delete already carries its id,
  so the existing `outbox` shape was enough: `deleteChildPermanently` queues a DELETE for the child and
  for every event id *inside the same transaction* that hard-deletes them, then drops the dead UPSERTs
  and keeps the DELETEs. No schema change — `schemas/` still holds only `1.json` and `2.json`.
- **The drain is in-process, no WorkManager.** Triggers: app start, after every queued write, and the
  `Sync now` row. An unacked intent stays in `outbox`, so a killed process loses nothing.
- **Firestore's cache is memory-only.** With the default persistent cache `set()` completes once the
  write is in Firestore's own local cache, which would make an offline drain look acknowledged and
  delete the outbox row while nothing had reached the server. Room + `outbox` is this app's only queue.
- **The growth dataset channel moved to Phase 13.1.** Two features in one phase would have had two
  device passes in one branch, and sync is the half that two phones must prove first.

Built: `domain/SyncMerge.kt` (the merge rule, pure), `data/sync/` (`SyncDocuments.kt` wire mapping,
`SyncGateway.kt`, `FirestoreGateway.kt`, `OutboxDrainer.kt`, `AccountAuthenticator.kt` + the Firebase and
unconfigured implementations, `SyncRepository.kt`, `FirebaseSetup.kt`), a `Sync` card in Settings, and
`firebase/firestore.rules` (two uids, pasted into the console by hand).

**A defect found while wiring, not on a phone:** `ChildRepository` was the one write path that never
queued an outbox intent — `EventRepository` had queued from day one, children had not. A renamed or added
child would have stayed on the phone that did it. Children now queue like everything else, and archive
and permanent delete queue their tombstones too.

**Also fixed here because sync made it dangerous:** `replaceAll` (import) wipes the tables *and* the
queue. Without a re-seed the restored log would sit on this phone while Firestore still held the
pre-restore version, and the next snapshot would merge the old rows back in. Import now resets the seed
flag, re-queues every restored row and drains once. Known limitation, stated rather than hidden: server
documents for rows the restore does not contain are not deleted — absence of a row in a backup file does
not mean "delete it on the server".

**Acceptance, unchanged, and not yet met on hardware.** Log on A, see it on B; three airplane-mode writes
on A arrive exactly once after restore. Every claim above is a JVM test or an inspection; at the time of
writing nothing had been on a phone, because no device was attached and the Firebase project did not
exist yet. What the phones must still prove: both sign-in routes, a real round trip, the airplane-mode
case, and a permanent delete that does not come back on the other phone. The Firebase-free half of that
was taken on 2026-10-22 — see the next section — and the rest needs a second phone and the project's
values in a local `firebase.properties`.

**Tests:** 49 new on the JVM — `SyncMergeTest` (9, the tie-breaking rules), `SyncDocumentsTest` (12, the
wire round trip including a dose, a temperature and a tombstone with no payload), `OutboxDrainerTest`
(11, ack/retry/idempotency against a fake server), `SyncRepositoryTest` (16, the seed, the inbound merge
and the sign-in states), and one in `MaintenanceRepositoryTest` for the delete intents. The suite is
**398, 0 failures**.

### Device pass, 2026-10-22 — the half that needs no Firebase

A phone was attached (`<handset>`, Android with the 3-button nav bar) and had no `app.babylog` on
it, so this was a clean install of the `0.9.0` debug APK. **No `firebase.properties` existed on the
build machine**, so the build was the deliberately-supported single-phone shape: `BuildConfig`'s four
values blank, `FirebaseSetup.isConfigured` false, `UnconfiguredAuthenticator` in place. That is exactly
the state a fresh clone builds, which is why it is worth a phone pass rather than an assumption.

What was confirmed **on the hardware**, with the database read back through `run-as app.babylog`
rather than inferred from the screen:

| Check | Evidence |
|---|---|
| Launches with no Firebase configured, no crash | `topResumedActivity=app.babylog/.MainActivity`, `logcat -b crash -d` empty across launch, force-stop/relaunch, and the delete below |
| The Sync card tells the truth instead of offering a button that cannot work | Renders *"Sync is not set up on this build, so the log stays on this phone. Logging works normally."* and offers **no** email/password fields and no Google button. The `NotConfigured` state is not the `SignedOut` form with a broken button — it is a different body entirely |
| Logging still works after Phase 13 rewrote every write path | A 90 ml bottle for both children landed as two `events` rows and appeared on the card immediately (`2m ago` after a relaunch) |
| **Both children queue intents now** — the defect found while wiring | `outbox` held `CHILD/first-twin/UPSERT` and `CHILD/second-twin/UPSERT` alongside the two `EVENT` UPSERTs. Before this phase `ChildRepository` queued nothing at all |
| **The permanent-delete fix works on a real database** | Deleting *Second Twin* hard-deleted the child row and their bottle, **dropped the two dead UPSERTs**, and left `CHILD/second-twin/DELETE` + `EVENT/86f9a2e5…/DELETE` queued with one shared timestamp. *First Twin* and their bottle were untouched |
| The UI degrades to one child cleanly | After a cold relaunch the switcher shows a single *First Twin* pill, the buttons read "+ Log feed for First Twin", and the Both pill is gone |
| Schema v2 on a real device | `pragma user_version` = 2, `events`/`children`/`outbox` all present |

The pre-compact device state was reset afterwards with `pm clear app.babylog`, so the two-phone run starts
from a clean database.

**One behaviour worth naming, not a defect:** on a build with no Firebase the outbox is never drained, so
it keeps one row per write for as long as the phone stays unconfigured. It is small (an id and an
operation), and it is not wasted work — `seedIfNeeded` skips ids already queued, so the accumulated
queue *is* the first sign-in's seed. The consequence to know about is the other end: the first sign-in on
a phone that has logged for months pushes that whole backlog in one drain, because a drain is sequential
and stops at the first failure.

**What this pass does not prove, and cannot:** both sign-in routes, the outbound drain against a real
Firestore, the inbound listener, the airplane-mode exactly-once case, and a permanent delete *staying*
deleted on the other phone. All of that needs the project's values in a local `firebase.properties` and a
second phone. Steps and failure symptoms are in `docs/firebase-setup.md`.

### Device pass, part two — the configured build (still 2026-10-22)

The same phone, the same day, after the console steps in `docs/firebase-setup.md` were done and the five
values were written to `firebase.properties`: project `baby-log-app-119e4`, the Android app
`1:705332594857:android:71a9c8f699a0effd7041b8`, and two uids — `wJjDVdK1FVRkSoSwBo5LOc8GGJt1` and
`REPLACE_WITH_SECOND_UID` — now in `firebase/firestore.rules` and `firebase.allowedUids`.

**The values are compiled in, so a reinstall is not enough.** `BuildConfig` was checked first, because
every failure mode here looks like a console problem: `FIREBASE_PROJECT_ID`, `FIREBASE_APP_ID`,
`FIREBASE_API_KEY`, `FIREBASE_WEB_CLIENT_ID` and `FIREBASE_ALLOWED_UIDS` all populated, at `0.9.0` /
versionCode 11.

| Check | What the phone showed |
|---|---|
| The card leaves single-phone mode | Email and Password fields, `Sign in`, and — new — **`Sign in with Google`**, which only appears when `webClientId` is non-blank. That button is the proof the OAuth client id reached the build |
| Auth is reachable, and errors are sentences | Empty submit → *"Enter the account's email and password."*; a wrong credential → *"Wrong email or password."* from Firebase itself. Neither crashed |
| Sign-in, and the first-sign-in seed | `Signed in as joelm3103@gmail.com`, *"Everything here is on the server."*, and the outbox was **already empty** — the seeded children had been queued and acked |
| A live write reaches Firestore | A 60 ml bottle for both children → two `events` rows, and **0 rows queued within ~5 s**. A row is deleted from `outbox` only on ack, so both writes were confirmed by the server |
| **Airplane mode, three writes** | Airplane mode on (`Network is unreachable`, verified): three bottles queued **exactly three intents**, and none drained |
| **Exactly once on reconnect** | Airplane mode off → outbox **0**, **five events, five distinct ids** — 2 earlier + 3 offline, no duplicates, nothing lost, no user action needed |
| The pending count tells the truth | One further offline write: *"1 change waiting to sync."*, which drained by itself on reconnect (`events` 6 → 7, outbox 0) |
| System back | Back from Settings returns to Today with both children |
| No crash, no rule errors | `logcat -b crash -d` held **0** `FATAL EXCEPTION` across the session; Firestore logged only `UNAVAILABLE` stream closures during the offline window — no `PERMISSION_DENIED`, no auth errors |

#### Found on the phone, left open: "Sync now" offline is silent

With no network, pressing **Sync now** gives no feedback at all. The write suspends — Firestore's
`WriteStream` closes `UNAVAILABLE` and the `Task` never completes — so `syncNow()` never returns, the
message line keeps whatever the last completed drain said (*"Sent 1 change(s)."*) beside a live
*"1 change waiting to sync."*, and the button looks inert. Nothing is lost: the intent stays in the
outbox and drains within seconds of the network returning, as verified above. Two details make it worth
naming rather than shrugging at:

- `drainLock` is a `Mutex`, so the suspended drain **holds the lock**; later triggers wait behind it
  instead of reporting again. That is the right call for safety — two interleaved drains could reorder
  one queue — but it means the silence cannot resolve itself.
- The fix is a connectivity-aware state, *"waiting for a network connection"*, with its own tests. It is
  deliberately **not** bolted onto this pass.

#### What this pass still cannot prove

The A↔B round trip, the inbound listener applying **another phone's** rows, and a tombstone surviving
that hop. One phone was reachable, and the pull half cannot be faked on it without wiping its data and
signing in again — worth doing as the next step, but not to a working log without asking first.

### Device pass, part three — the hop, and the A↔B round trip (2026-10-22)

**Written up after the fact**, in the same dated section as the two passes above. This is the pass that
closes the gap the previous two sections name: the inbound listener applying **another device's** rows,
and a tombstone surviving the hop. It ran in two parts.

**Part one — the wipe and re-sign-in, on the phone alone.** `pm clear app.babylog` emptied the local
database; the log survived because Firestore held it. Signing back in as `joelm3103@gmail.com` restored
**the same six row ids** that were on the phone before the wipe (`920c4d6f`, `37a12b78`, `adf27e6b`,
`b5c9f5fb`, `a464cd4f`, `b4532613`), and the row deleted *before* the wipe (`d02ad63f`) did **not**
return — the tombstone won over the stale server document. Read from the database through `run-as`, not
from the screen; `outbox` was 0 and `logcat -b crash -d` stayed empty.

**Part two — two devices, two accounts.** `emulator-5554` (Android 16) joined `<handset>` as the second
sync client. The owner replaced the unused Firebase account, so the allow-list and the rules moved to
the new uid `REPLACE_WITH_FIRST_UID` beside `REPLACE_WITH_SECOND_UID`, the app was rebuilt with
that configuration, and both devices were reinstalled and signed in — the phone as `TSz2…`, the emulator
as `B43h…`. **The emulator is a second sync client only:** it proved the wire, and layout questions were
settled on the phone, per `AGENTS.md`.

| Check | Evidence |
|---|---|
| **A different uid reads the whole log** | On a fresh install, the emulator signed in as `B43h…` and pulled the phone's **six rows by id** — the inbound listener, across accounts, with no prior state |
| **Log on A, see it on B** | A 90 ml bottle for both children on the phone → two rows on the phone, then the **same two ids** (`60b95a2c`, `e390a804`) on the emulator, shown as `just now` |
| **Log on B, see it on A** | A 120 ml bottle for both on the emulator → `661e6748` and `1d3a752b` on the emulator, then on the phone |
| **Airplane-mode trio, exactly once** | Airplane mode on the emulator (`Network is unreachable`, verified): three single-child bottles queued **exactly three intents** and the phone stayed at 10 events. On reconnect the phone gained **exactly three rows, three distinct ids** (`d636207e`, `391c8b77`, `e416186c`) — no duplicates — and both devices settled at **13 events, 13 distinct ids, outbox 0** |
| **A deletion crosses devices** | Long-pressing an entry row on the phone deleted `e416186c` (soft delete, `deleted=1`); the emulator marked **that id** deleted and dropped to 12 live rows |
| **It stays gone** | `am force-stop` + relaunch on the emulator: still 13 events, 12 live, `e416186c` still `deleted=1`, outbox 0 — nothing resurrected the row |
| No crash on either device | `logcat -b crash -d` → **0** `FATAL EXCEPTION` on `<handset>` *and* `emulator-5554` |

**As built, where it differed from the plan.** Three things worth recording, because each cost time and
none of them is visible from a green build:

- **A debug APK is per build machine.** The rebuild here is signed with this machine's debug key, so
  `install -r` failed on both devices with `INSTALL_FAILED_UPDATE_INCOMPATIBLE` and the app had to be
  uninstalled first — which discards the local database. Nothing was lost because Firestore held the
  log, and the reinstall then re-proved the restore path on both devices. `docs/firebase-setup.md` says
  a debug keystore is per machine; it now also says what that costs at install time.
- **The second account was replaced, not added.** The first attempt signed the second device in as the
  same account as the phone, which proves device-to-device sync but not *two accounts* — so the run was
  redone with a genuinely different uid. The uids recorded in the part-two table above are the ones in
  use that day; the current pair is in `firebase/firestore.rules`, which is the file that has to be
  pasted into the console.
- **The emulator is not a phone.** It is a second sync client: fine for row ids, outbox counts and
  tombstones, and not a substitute for the layout and contrast judgements, which stayed on the phone.

#### Second finding, left open: the seed waits for a drain

After a fresh install and the first sign-in, the two seeded `CHILD` UPSERTs stayed in `outbox` — and the
card read *"2 changes waiting to sync."* — for at least thirty seconds with the network up. Tapping
**Sync now** drained them immediately (*"Sent 2 change(s)."*) and the queue went to 0, so nothing was
stuck or lost: the drain simply had no trigger between sign-in and the next write. It is the mirror of
the `Sync now` finding above and is deliberately not fixed in a close-out pass — the honest fix is to
drain once when the inbound listener finishes its first snapshot, which deserves its own test.

### The three passes at a glance

| Pass | Device | Proved |
|---|---|---|
| Non-destructive (unconfigured build) | `<handset>` | Launch without Firebase, the "not set up" card, every write path queueing, permanent delete queueing DELETEs |
| Configured build, outbound | `<handset>` | Sign-in both routes offered, live ack in under five seconds, three airplane-mode writes delivered exactly once |
| Wipe + re-sign-in, then A↔B | `<handset>` + `emulator-5554` | The inbound listener restoring the log by row id, a tombstone not returning, and **two accounts** round-tripping a bottle each way, an offline trio, and a cross-device delete that stayed deleted |

### Original scope, for the record

**Carried over from Phase 10 — the dataset update channel.** Task 10.2 assumed Firestore was already in
the stack; it is not, which is why the channel lives here. When the Firebase project exists: a
`reference/growth_manifest` document pointing at `reference/growth_dataset`, a **checksum gate** (a
manifest that fails its sha256, is older, or is unreachable keeps the current dataset and shows nothing),
a **90-day** silent check on app start, and `Check for updates` rows on the Growth screen and in Settings.
The dataset identity to display is already built and tested (`GrowthReference.identityLine`) — this adds
the fetch, not the format. The four datasets' curation still matters here: a fix to our own bundled data
should not need an APK release.

Firebase project, Google sign-in, security rules restricted to two uids, outbox drain with
`SetOptions.merge()` and client-generated ids (idempotent re-runs), inbound listener with
`updatedAt`-wins, and **tombstones that round-trip** so a deleted event never resurrects.

**Caveat carried over from Phase 7 — must be revisited here.** "Delete permanently" on a child
HARD-deletes the child row and their events (`MaintenanceRepository.deleteChildPermanently`), so there is
no tombstone for sync to propagate and the other phone could resurrect them on the next merge. Either
route a permanent deletion through the outbox as an explicit delete document, or make it a
tombstone-only purge the sync layer understands. Archive (soft delete) is already sync-safe.

**Acceptance:** log on phone A, see it on B; airplane-mode three writes on A, restore, all three arrive
once, no duplicates.

## Phase 13.2 — Temperature bands — DONE, device-verified (`0.11.0`)

**As built — 2026-10-24.** The owner asked for the single fever threshold to become four bands, marked by
shape and judged from a rendered mockup before code was written. `domain/Temperature.kt` classifies LOW
(`< 36.5`), NORMAL (`36.5–37.5`), MILD_FEVER (`37.6–37.9`) and FEVER (`>= FEVER_C`) from the value **as
displayed** — rounded to one decimal first, exactly as `trimZero(_, 1)` rounds. A marker can therefore
never contradict the number beside it: `37.99` prints `38.0` and is FEVER; `37.94` prints `37.9` and is
MILD_FEVER. `isFever()` is defined from that band, so the pill and number treatment cannot drift.

`TemperatureBadge` replaces `FeverBadge` with one geometry for the three marked bands: FEVER filled in
`feverOnCard` with the word in `onAccent`, and MILD_FEVER / LOW outlined in `feverOnCard`. Only FEVER moves
a number off `textPrimary`. The nullable band replaces the boolean through `ChildLine`, `ActivityTotal`,
`EntryRow`, `FeverReading`, Activity, Summary, the entry list and Trends; NORMAL collapses to null at the
source because a normal reading has no pill.

Trends lists MILD_FEVER and FEVER readings with their words and counts their distinct days. LOW is
intentionally absent from Trends: it remains labelled on Activity and Summary, but the over-time table is
for a raised temperature. Inclusion uses `temperatureBand()` rather than a second raw threshold, so values
that round onto the displayed boundary stay consistent across every surface.

### As built, where it differed from the plan

- Shape carries the bands rather than adding more hues. The original fever-only marker had already proved
  that a tinted number was invisible on the Health card; Low and Mild therefore use an outlined version of
  the same pill geometry, while Fever keeps the filled pill.
- Classification happens after one-decimal display rounding rather than on the raw stored double. The
  first Trends implementation accidentally kept a raw `>= 37.6` filter; close-out replaced it with the
  shared `TemperatureBand` rule and added a `37.54` / `37.56` regression.
- Trends broadened from Fever-only to Mild and Fever, but Low deliberately stays out. Activity, entry rows
  and Summary still mark Low, so the omission is scope rather than lost information.
- The owner also changed the fresh-install clock fallback to 12-hour on this branch. That is separate
  polish, recorded immediately before the phase as `0.10.1` / versionCode 14; Phase 13.2 remains the
  `0.11.0` / versionCode 15 capability rung.

**Validation:** `:app:assembleDebug` builds; `:app:testDebugUnitTest` is **417/417** with 0 failures, 0
errors and 0 skipped, summed from 42 XML files under `app/build/test-results/testDebugUnitTest/`.
`TemperatureBandTest` contributes six cases, and two new `TrendsTest` cases cover Mild-day counting and
the displayed `37.54` / `37.56` boundary; `TotalsTest` and `CardSummaryTest` cover band propagation.

### Device pass — 2026-10-24

Samsung S23 Ultra (`<handset>`), Android 16. The APK was installed in place with the native Windows
ADB path. Before each input sequence `topResumedActivity` showed `app.babylog/.MainActivity`; every tap
came from the matching node or clickable ancestor in a fresh `uiautomator dump`.

| Check | What the phone showed |
|---|---|
| APK identity | `install -r` succeeded; Package Manager reported `versionName=0.11.0`, `versionCode=15`. The Health sheet used the fresh-install fallback and read `6:00 PM`, not `18:00`. |
| Activity headline and rows | With 36.2, 37.1, 37.6 and 38.4 °C logged in order, the latest headline was `38.4` with a filled `Fever` pill. The rows showed Low outlined, Normal unmarked, Mild outlined and Fever filled; the three marked entry badges all occupied the same-height bounds. Only Fever changed the number treatment. |
| Displayed-value boundaries | A stored 37.94 displayed as `37.9 °C` with `Mild`; 37.99 displayed as `38 °C` with `Fever`. The shape therefore followed the rendered number at both sides of the Fever boundary. |
| Summary | The Health total showed the latest `38.4` reading with its filled `Fever` pill. |
| Trends | Health showed `Temperatures 4`, `Flagged readings 2`, and `Fever days 1`; only 38.4/Fever and 37.6/Mild appeared. 37.1 and 36.2 were excluded, and the caption quoted both 37.6 and 38 °C thresholds. |
| System back | Back from Trends returned to Activity without leaving the app; back with the Health sheet open dismissed the sheet and kept Activity foreground. |
| Test-row cleanup | Six close-out readings (the four bands plus 37.94/37.99) were long-pressed and confirmed through the app. No matching live row remained; the latest six rows were soft-deleted tombstones. Ten matching tombstones existed in total because the earlier exploratory device pass had already created and removed the same four-band set. |
| No crash | The final `logcat -b crash -d` contained **0** `FATAL EXCEPTION` entries. |

## Phase 13.1 — Time and temperature preferences — DONE, device-verified (`0.10.0`)

**As built — 2026-10-23.** The owner requested this capability before the growth dataset update channel.
Settings now offers independent `12-hour` / `24-hour` and `°C` / `°F` choices, persisted in DataStore
with the previous behaviour as the defaults: 24-hour and Celsius. The owner later chose `12-hour` as the
clock default on a fresh install (2026-10-24, `0.10.1`); Celsius remains the temperature default.

The preferences are display/input choices, not stored-data choices. Timestamps remain epoch milliseconds;
temperatures remain Celsius in Room, backups and sync documents; and fever classification remains the
canonical `FEVER_C` comparison. Fahrenheit input converts to Celsius before validation/storage, while an
untouched Fahrenheit prefill keeps the exact stored reading rather than rounding it through the displayed
text. Cards, entry rows, Both-mode Health columns, Summary, Trends' fever list and threshold, sheet time
rows, the Material time picker, and Settings' night-window labels all follow the selected preferences.
`LocalTimeFormat` and `LocalTemperatureUnit` carry the choices through Compose. There is no Room schema
change and no sync-document change.

### As built, where it differed from the plan

- This supersedes the earlier Celsius-only rule. Temperature remains independent of the Metric / Imperial /
  Custom presets, so changing a preset cannot silently change a health-entry field.
- The capability took the next `0.MINOR` rung and was inserted before the already-planned dataset channel;
  that channel and Reset to defaults moved forward one phase/version without changing scope.
- The first phone pass showed that a temperature error at the bottom of the form was disconnected from the
  field that caused it. The final layout puts temperature parse/range warnings directly beneath
  Temperature, keeps dose validation separate, and hides the empty-form hint while either field is invalid.

**Validation:** `:app:assembleDebug` builds; the JVM suite is **409/409** with 0 failures, 0 errors and 0
skipped, read from `app/build/test-results/testDebugUnitTest/*.xml`. The four new cases cover 12-hour clock
boundaries, Fahrenheit input and the exact fever threshold, untouched-prefill precision, and persistence
independent of the three unit presets.

### Device pass — 2026-10-23

Samsung S23 Ultra (`<handset>`), in-place install of the `0.10.0` / versionCode 13 debug APK. The app
was confirmed foreground before every input sequence, and every tap coordinate came from a fresh
`uiautomator dump`. Screenshots are stored beside the repository under
`babylog-refshots/2026-10-23-time-temperature-closeout/`, not committed.

| Check | What the phone showed |
|---|---|
| Settings controls | Temperature and Time format render together without clipping. The original `°C` / `12-hour` choices changed to `°C` / `24-hour` (night window `6:00 PM` / `6:00 AM` → `18:00` / `06:00`), then to `°F` / `12-hour`, and finally back to the owner's original `°C` / `12-hour` |
| Persistence | `°C` / `24-hour` survived force-stop/relaunch. `°F` / `12-hour` then survived another relaunch and opened Health as `Temperature (°F)` with a `10:39 PM` row. After final restoration and relaunch, Health read `Temperature (°C)` and `10:48 PM` |
| Fahrenheit input guard | An unsaved `38` produced `Temperature must be between 86 and 113 °F.` directly beneath Temperature and above Medication |
| Canonical storage | A synthetic `100.4 °F` reading saved as exactly `38.0` in the phone's Room `temperatureC` column; `PRAGMA user_version` remained 2, so no schema migration was involved |
| Activity card and entry | The Health card showed `100.4 °F` with the filled `Fever` pill; the entry row also read `100.4 °F` with its own `Fever` pill |
| Summary | Health showed `100.4 °F`, the `Fever` pill, `Records 1`, and `Measured 23 Oct` |
| Trends | Health showed `100.4 °F`, `Fever`, `23 Oct 10:39 PM`, and `A reading at or above 100.4 °F is marked as a fever.` |
| System back | System back from Trends returned to Activity without leaving the app; the Health card and bottom navigation remained intact |
| Test-row cleanup | The synthetic row was soft-deleted through its long-press confirmation. It disappeared from the UI and remains only as the expected Room tombstone (`deleted = 1`) |
| No crash | The final `logcat -b crash -d` buffer was empty: 0 `FATAL EXCEPTION` |

## Phase 13.3 — the growth dataset update channel — not started (`0.12.0`)

Split out of Phase 13 on 2026-10-19, not dropped: Phase 13 is the half that two phones must prove first.
The Firebase project already exists by then, which is the only precondition this channel ever had.

- A `reference/growth_manifest` document pointing at `reference/growth_dataset`.
- A **checksum gate**: a manifest that fails its sha256, is older than the bundled dataset, or is
  unreachable keeps the current dataset and shows nothing. A failed check is silent, not an error dialog.
- A **90-day** silent check on app start, and `Check for updates` rows on the Growth screen and in
  Settings.
- `GrowthReference.identityLine` already exists for the display line — this adds the fetch, not the
  format.

The four datasets' curation still matters: a fix to our own bundled data should not need an APK release.

## Phase 13.4 — Reset to defaults — scoped 2026-10-22, not started (`0.13.0`)

Asked for while closing out Phase 13: **Settings → Data has no way to return the app to first-run state.**
Export and Import are the only rows there, and Import needs a file it does not have. This is scoped now so
it is not lost, and **deliberately not started in the session that scoped it.**

Shape agreed with the owner:

- A **Reset to defaults** row in Settings → Data, behind a confirmation that names what will go.
- It wipes **events**, **children** (re-seeding *First Twin* / *Second Twin*), and **settings and presets**
  — theme, units, night window, bottle and medication presets. Export files are not touched.
- **Server deletion is a separate, opt-in choice**, and its own confirmation must say plainly that the
  data will be removed from the server and **cannot be recovered**. Off by default.

The reason that last point is not optional detail: **sync makes a local-only wipe a trap.** `replaceAll`
wipes the tables *and* the outbox, so a reset that did not queue DELETEs would leave Firestore holding the
whole log, and the next snapshot would merge it straight back in — the exact defect Phase 13 fixed for
Import with `reseedAfterRestore`. A reset therefore has to either push tombstones for every row and drain,
or refuse while signed in. Pick one deliberately; do not let it be an accident of implementation order.

Reuse, not reinvention: `MaintenanceRepository` already owns the wipe-in-one-transaction pattern
(`replaceAll`) and `OutboxDrainer` already knows how to travel a permanent delete as an id-and-timestamp
tombstone. The tests to write mirror the ones there: the wipe is atomic, the queue is left consistent, and
a reset while signed out cannot resurrect rows on the next sign-in.

## Phase 14 — Update delivery

Getting a new build onto both phones without a cable. This has to precede the ship step, because a
release-signed APK is the thing being delivered.

- **Release signing**: generate the keystore, wire `signingConfigs`, and back the keystore up **outside
  the repo**. It never enters git.
- **Delivery**: Firebase App Distribution (free, no Play Store) on the Firebase project from Phase 13,
  or a hosted APK link. Installing a new build becomes a tap on both phones instead of running
  `adb install -r` twice.
- **Register the release certificate's SHA-1 with the Google Sign-In OAuth client.** Phase 13 added Google
  Sign-In, and it only accepts a build whose signing certificate the console knows. Email/password keeps
  working either way, which is why it is the primary route.
- **The first tag lands here.** All phase rungs stay untagged on purpose — tags are for released builds,
  and a phase ending is not a release. `v1.0.0` is cut when the signed build is delivered, which is also
the point at which `versionCode` starts mattering to something other than a human: App Distribution
compares it to decide whether a build is newer, which is why it has been a rising counter all along.

**Verify:** a build reaches both phones over the air, without a cable.
**Commit:** `chore(release): signed builds and over-the-air delivery`

## Phase 15 — Ship
`adb install -r` the release APK on both phones — the last time a cable is needed — then **use it for a
week before writing another line of code**.

---

## Changing this app after v1 ships

The owner asked (2026-10-12) whether post-build changes are easy. Honest answer, by kind of change:

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
   insurance and it also unblocks restoring onto a replacement phone. The apps this one replaces offer the
   same thing for the same reason.
3. **Update delivery that doesn't need a cable.** Firebase App Distribution (free, no Play Store) or a
   hosted APK link, so installing a new build on both phones is a tap instead of `adb install` twice.
   Add it at Phase 14, not Phase 1.

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
- **Sleep's hue is settled as shipped** (`#63C8FF` in `Tokens.kt`). No existing tracker's sleep screen was ever
  captured, so it started as a designed guess; the owner confirmed on 2026-10-14 that it stays as-is.
- **The light theme is designed, not sampled.** The owner's phone runs dark; the mirror in `Tokens.kt` is
  reasonable but unverified.

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
| First Twin / Second Twin / Both switcher present | pass |
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

Samsung S23 Ultra (SM-S918U1), Android 16, dark mode.

| Check | Result |
| --- | --- |
| Launches without crashing; app is the current activity | pass |
| Card action label is context-aware (`+ Log feed for First Twin` / `... for both`) | pass |
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

Samsung S23 Ultra (SM-S918U1), Android 16, dark mode.

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

Every number below was hand-counted from the Room database in the DEVICE'S timezone first, then compared
against the screen. The totals are not trusted because they look plausible.

| Check | Result |
| --- | --- |
| History opens from the nav tab with the SAME header and child switcher as Activity | pass |
| `1d` totals match a hand-count | pass — 3 bottles / 330 ml = **11.2 oz**, average 110 ml = **3.7 oz**, 2 diapers (day 2, night 0) |
| `7d` totals match a hand-count | pass — 7 sessions / 330 ml = 11.2 oz, 20 diapers (day 20, night 0) |
| Per-child line in Both mode | pass — First Twin 7.1 oz + Second Twin 4.1 oz = the 11.2 oz combined headline |
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

Samsung S23 Ultra (SM-S918U1), Android 16, dark mode.

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

---

## Phase 9.1 device verification — 2026-10-14

Samsung S23 Ultra (SM-S918U1), Android 16, dark mode. Screenshots:
The three `before/` shots below are the defect report as it was seen — they were sent from the phone
before any code changed.

Every value and time below was read back with `uiautomator dump`, not by eye, and the coordinates for
every tap came from a dump rather than from the screenshots.

| Check | Result |
| --- | --- |
| A card lists today's entries, up to five, newest first | pass — the second twin's bottle card: `3 oz 14:14`, `3 oz 13:09`, `4.1 oz 09:59`; Both: exactly five rows and no sixth |
| The entry behind the big number is one of the rows | pass — the headline read `3 oz` with `4h 54m ago` (= 14:14) and the first row was `3 oz 14:14`. Before this pass the rows started at 13:09, which is why it looked like a feed was missing |
| Both mode puts the child's badge and the absolute time on the big number | pass — `5.5 oz` with a `K` badge and `15:21`, where the old card showed an unattributed `5.5 oz` |
| Both mode now lists the second twin's 13:09 feed | pass — `K 5.5 oz 15:21`, `K 3 oz 14:14`, `L 3 oz 14:14`, `K 3 oz 13:09`, `L 3 oz 13:09` |
| Identically timestamped Both-mode rows keep First Twin above Second Twin | pass — `K 3 oz 14:14` precedes `L 3 oz 14:14`, and again at 13:09; the comparator, not SQLite, decided |
| The child badge is hidden outside Both mode | pass — both children's cards show bare rows; Both shows `K`/`L` on every row |
| See all opens the full list, grouped by day | pass — header `Bottle feed` / `for both · tap to edit, long-press to delete`, groups `Today`, `Yesterday`, `Mon 12 Oct`, and it covers the bottom navigation bar |
| System back closes the overlay and returns to Activity | pass |
| Tap a row → the sheet opens pre-filled for THAT row's child | pass — `Edit feed`, `for First Twin` while the switcher was on Both, Date `14 Oct 2026`, Time `19:12`, Amount `4` |
| **An edit saves in place and leaves the Both-mode twin alone** | pass — logged 4 oz for both, edited the first twin's row to 5 oz: `K 5 oz 19:12` beside `L 4 oz 19:12`, still two rows rather than four, same timestamp |
| Long-press → confirm → delete | pass — `Delete this bottle feed?`; confirming removed the row and the headline fell back to the newest remaining row |
| Undo restores the row | pass — the 19:20 row came back and the headline returned to it |
| A card with everything already shown has no See all footer | **not exercised** — every card in this log has entries older than today, so all six showed the link. Covered by `CardBuilderTest` instead (three today and nothing older → no footer) |
| No crash at any point | pass — `logcat -b crash` empty, `grep -c "FATAL EXCEPTION"` = 0, process still alive at the end |

### Found on device (this pass)

- **The Undo snackbar was too short to use.** `SnackbarDuration.Short` is four seconds, and it expired
  between a UI dump and the tap, so the tap landed on the Diaper card underneath — nothing was written
  (that card's rows were unchanged and no diaper event appeared), but a real finger has the same four
  seconds. It is now `Long` (ten seconds), which is Material's own guidance for a snackbar carrying an
  action, and the Undo was re-verified inside the longer window.
- Pre-Phase-6 rows read `Bottle` with no amount, because the Phase-5 `quickLog` stub wrote events with no
  values. Visible in the overlay's `Mon 12 Oct` group. The label falling back is correct; the rows
  themselves are junk from a retired code path, and deleting a parent's historical rows is not a call to
  make inside a polish pass, so they were left alone.
- **Test data is left as tombstones.** Four rows created for this pass (4 oz for both at 19:12, the first twin's row
  edited to 5 oz, and 4 oz for both at 19:20) were soft-deleted afterwards, so the visible log is exactly
  the rows it had before the pass. They remain in the database as tombstones — which is what a soft delete
  means, and why History, the cards and the lists show nothing of them. They will round-trip to sync in
  Phase 13 like any other delete.

---

## Phase 10 device verification — 2026-10-15

Samsung S23 Ultra (SM-S918U1), Android 16, dark mode.
(eleven captures, plus the three `before/` defects reported from the phone). Every tap coordinate came from
`uiautomator dump`, never from a guess, and every value was read back either from the tree or from the Room
database.

| Check | Result |
| --- | --- |
| The app launches and stays foreground; no `FATAL EXCEPTION` at any point | pass |
| `+ Add measurement` opens the sheet: Measurement, `for both`, Date, Time, Weight (kg), Length (cm), Head circumference (cm), Notes | pass |
| With nothing entered, Save is disabled and the sheet says "Enter at least one measurement" | pass |
| Both mode writes **two rows with identical timestamps** — read from the database: the twins, 5400 g at 15:09:16 UTC | pass |
| The saved value reaches the card: headline `5.4` + `kg`, and a row per child | pass |
| Growth values carry W/L/H wherever they appear — card summary, entry rows, the `See all` list, and the chart's list | pass |
| Both-mode summary is **one column per child**, name on top, one measurement per line | pass |
| The summary is the latest of EACH measurement, not the newest entry — assembled from three single-field saves into `W 6 kg` / `L 66 cm` / `H 42 cm` | pass |
| `See all ›` on the Growth card opens the same entry-list overlay as every other card, labelled and grouped by day | pass |
| Long-press deletes with a confirmation naming the entry, and offers Undo | pass |
| Trends shows a tab per activity; Growth opens the percentile chart with segments, the axis caption, the dataset line and the measurement list | pass |
| The other activity tabs state honestly that their charts arrive in Phase 12 | pass |
| The chart draws curves, a hollow marker per measurement, and each child's own line — 105 near-full-alpha accent pixels where the pre-trajectory capture had 3 | pass |
| The chart's list says why a row has no percentile (`1.4 kg · no percentile`), and an off-chart value reads `<1st`/`>99th` rather than being clamped | pass |
| Summary back-navigation: the chart is reached by tab, and system back from Trends returns to Activity without leaving the app | pass |
| Scroll to the very bottom of the Growth tab: the last content ends at y=1919, clear of the navigation bar at y=2095 | pass |

### Found on the device, fixed in this pass

Three of the six defects below are layout, which is exactly what a green build cannot see:

- **`+ Add measurement` did nothing.** `HomeViewModel.openSheet` guarded on a private `SHEET_TYPES` set that
  still listed only the three Phase-6 sheets, so the tap was silently dropped: a live-looking button with
  the right label and no effect. The guard is gone, and the sheet host now names every type explicitly with
  no `else`, so adding an EventType without deciding its sheet is a compile error.
- **The chart had no route from the card.** The footer only rendered when an entry was not already on the
  card, so with every measurement from today there was no link at all. `See all` is now shown whenever an
  activity has one entry — for every card, not just growth.
- **The Trends tab row was drawn from y=0**, under the 94 px status bar, so the top of every chip was
  untappable and tapping a chip's centre did nothing. The row applies the shell's top inset itself.
- **The Trends pane used `fillMaxSize()` inside the screen's Column**, so it claimed the whole height
  instead of the space below the chips: it overlapped them, and the end of its list could not be scrolled
  to. It now takes the remaining height with `weight(1f)`.
- **The percentile label clamped off-chart values into a percentile.** 5.4 kg at 218 adjusted days is
  z = −3.9, which the old clamp printed as `1st percentile` — claiming the child sat ON the 1st line rather
  than below every curve. Now `<1st` / `>99th`.
- **The kg box showed 100 g.** 6350 g rendered as "6.4" kg, hiding 50 g a parent could neither see nor
  correct. The sheet now shows 10 g in kg and 0.01 in in inches, finer than the millimetre stored behind it,
  while an untouched box still keeps its stored grams.

### Notes

- **The owner changed the design after seeing it**, and the changes are in this phase: the chart lived on
  the Activity card and moved into Trends; the Both-mode summary was one combined headline and became one
  column per child; values gained W/L/H codes; and the highlight box stopped picking one child by name order
  and became one box per child.
- **Test data is left in place, deliberately.** This pass created growth rows on the phone (1370 g dated
  to the children's birth date, 5400 g, and 6 kg / 66 cm / 42 cm as three single-field saves). They are plausible enough to
  keep while the screens are still being looked at, and one impossible row — a 25 kg weight created by a
  stray scripted tap — was soft-deleted through the app's own long-press path, leaving a tombstone.
- **`adb` was unauthorized for part of the pass** and the device dropped off once mid-run. Both were
  environment, not app: the first needed the RSA prompt accepted on the phone, the second a re-plug. Worth
  recording because the pass cannot distinguish "no device" from "no crash".

---

## Phase 11 device verification — 2026-10-18

Samsung S23 Ultra (SM-S918U1), Android 16, dark mode. Screenshots:
Every tap coordinate came from `uiautomator dump`,
never from a guess, and the stored values were read from the device's own database — this debug build
allows `run-as`, which is a stronger check than the export path the plan named.

### The migration, on the real database

The phone was carrying a v1 database from Phase 10 with 69 event rows on it, including two health rows
whose `medication` text was `Calpol 2.5 ml` — written before a dose had a column. It was copied off the
device, the new build installed over it, the app launched, and it was copied back.

| Check | Result |
| --- | --- |
| `PRAGMA user_version` before / after | 1 → **2** |
| `events` columns before / after | 22 → **24** (`doseAmount`, `doseUnit` added) |
| Event rows before / after | **69 before, 69 after** (64 live, 5 tombstones) |
| Legacy health rows after the upgrade | pass — still `38.5 °C` and `Calpol 2.5 ml`, with `doseAmount = NULL` and `doseUnit = NULL`, not `0` |
| App launches after the upgrade; `logcat -b crash` | pass — empty, and `grep -c "FATAL EXCEPTION"` = 0 |

### The screens

| Check | Result |
| --- | --- |
| Health card, Both mode, no data | pass — `First Twin` / `Second Twin` columns each reading `No records yet` |
| The two actions | pass — accent-filled `+ Add health record for both` and a neutral `+ Vaccine` beneath it |
| Health sheet, empty | pass — `Health record` / `for both`, Date, Time, Temperature (°C), Medication `Choose or type`, Dose, Dose unit `Choose`, `Names only — this app does not suggest a dose.`, Save disabled with `Enter a temperature or a medication.` |
| Medication dropdown | pass — `COMMON` header + all 14 shipped names + `Type another name…`; no `YOURS` group while no presets exist |
| Picking a medicine | pass — the row reads `Paracetamol (Calpol)`, the free-text field hides, and Save becomes enabled |
| Dose unit dropdown | pass — exactly `ml`, `mg`, `drops`, `sachet`, `tablet`, `other` |
| **Both mode writes two rows with one timestamp** | pass — `K ... 12:27` and `L ... 12:27`, and the DB shows two rows sharing `startedAt` per save |
| Stored dose, read from the DB | pass — `medication = Paracetamol (Calpol)`, `doseAmount = 2.5`, `doseUnit = MILLILITRES` |
| **The last-dose hint** | pass — `Last recorded — First Twin 2.5 ml · Second Twin 2.5 ml`, with the dose field still **empty** and the unit still `Choose` |
| Vaccine sheet | pass — `Vaccine` / `for both`, Date, Time, Vaccine, `Enter the vaccine's name.`; saved row reads `6-in-1 vaccine` and the DB row carries `vaccineName = 6-in-1` with no dose |
| The card after both kinds | pass — each child's column shows `Paracetamol (Calpol) 2.5 ml` and `6-in-1 vaccine` on separate lines |
| Legacy rows still display | pass — `38.5 °C · Calpol 2.5 ml`, the free-text medicine from before the migration |
| History | pass — headline `38.5` `°C` with the `Fever` pill, rows `Records 6`, `Medication Paracetamol (Calpol)`, `Dose 2.5 ml`, `Vaccine 6-in-1`, `Measured 18 Oct`, per-child `First Twin 3` / `Second Twin 3` |
| Fever marker, in pixels | pass — **10,800 pixels of exactly `#FFB6B6`** (the pill fills) with **928 pixels of `onAccent`** for the word inside them. Before the pill, the same band held 3,762 pixels of tinted text and the owner could not see it. |
| Trends' Health tab | pass — still `Charts for health arrive in Phase 12…`; the chip is only reachable after scrolling the chip row (see below) |
| System back closes the sheet, app stays foreground | pass |
| No crash at any point | pass |

### Found on the phone, and in its own tests

- **`1 drops`.** The unit label was doing two jobs — picker word and dose word — so a dose of one read
  `1 drops` where the card shows it. A new `DoseTest` caught it before the phone did. `DoseUnit` now has
  a picker label plus singular and plural.
- **The Health chip needed a horizontal scroll to reach.** Six activity chips do not fit the Trends tab
  row — the same clipping Phase 10 recorded, now with a sixth chip. The row already scrolls, so nothing
  is unreachable; recorded rather than redesigned, because shrinking the labels would be worse.
- **History's summary mixes the latest of each kind.** The headline reading is 12:08 while the medication
  and dose rows are from 12:27, because each line is the latest of its own kind. That is what the phase
  asked for, and the card's row list is where actual records are read — noted here so the juxtaposition
  is not mistaken for a single event.
- **Legacy rows carry their dose inside the medication text** (`Calpol 2.5 ml`), because they predate the
  column. They render honestly and were left alone: rewriting a parent's historical rows is not a call to
  make inside a phase, and the data is not wrong, only unseparated.

## Phase 12 device verification — 2026-10-18

Samsung S23 Ultra (SM-S918U1), Android 16, dark mode unless stated. Screenshots:
Every tap
coordinate came from `uiautomator dump`, never from a guess, and the foreground was checked before the
first input — the app resumed a stale task with the Growth entry list open and a delete snackbar on
screen, which was closed without acting on it.

The device carried **73 event rows** (68 live, 5 tombstones) at `user_version` 2 — the 69 Phase 11 left
plus four growth measurements logged at 11:41 the same day. No phase-12 work touched the schema: this
phase reads and derives, it stores nothing new.

### The charts, against the phone's own numbers

Every figure below was recomputed from the device's `events` table with `run-as`, then compared with what
the screen showed. The window is the **local** day (`America/Port_of_Spain` on this phone).

| Check | On screen | Re-read from the DB | Result |
| --- | --- | --- | --- |
| Bottle, Both, 14d | First Twin `27.7 oz`, `3 of 14 days logged`, best `24.7 oz · 14 Oct` | 819 ml = 27.7 oz, 3 days, best 730 ml = 24.7 oz on 14 Oct | pass |
| Bottle, Both, 14d | Second Twin `19.1 oz`, best `16.1 oz · 14 Oct` | 566 ml = 19.1 oz, best 477 ml = 16.1 oz on 14 Oct | pass |
| The 7d chip | `Bottle feed · 7 days`, averages `4` and `2.7` oz/day | 27.7/7 = 3.96, 19.1/7 = 2.73 | pass |
| Diaper, Both, 30d | `0.4 per day`, `Total 13`, best `9 · 12 Oct`, `2 of 30 days` | 13 each on 2 days, 9 of them on 12 Oct | pass |
| Solids, Both, 30d | `0.1 per day`, `Total 2`, best `1 · 12 Oct` | 2 meals each on 2 days | pass |
| Sleep, Both, 30d | `< 0.1 hours / day`, `Total 13m 5s · 15 Oct` | one 13m 5s session on 15 Oct | pass |
| Health, Both, 30d | `Fever days 1`, `Temperatures 2`, `Fever readings 2`, `38.5 °C` + pill, `18 Oct 12:08` | 2 temperatures, both 38.5, 1 distinct fever day | pass |
| Growth tab | Percentile chart, curves, `K` markers, callouts, dataset line | unchanged from Phase 10 (behaviour-identical move of the axis maths) | pass |

Note honestly: on this install every logged row falls inside the last 14 days, so the 30-day window was
exercised for its bucketing, its labels and its averages, **not** for extra events — 14d and 30d show the
same totals.

### The screens

| Check | Result |
| --- | --- |
| Six activity tabs, the row still scrolls to reach Sleep and Health | pass — `Bottle feed · Diaper · Solids · Growth`, then `Solids · Growth · Health · Sleep` after one swipe |
| Range chips `7d · 14d · 30d`, default 14, on every pane | pass |
| Both mode legend names both children and samples the line style | pass — `— First Twin` solid, `- - - Second Twin` dashed |
| A count axis ticks in whole numbers | pass — Solids shows `0` and `1`, not the `0.2` steps the first build drew |
| The light scheme (the charts are the first token-drawn surfaces seen in light mode) | pass — Bottle pane in light: dashed line, legend, `labelFor` headline and `textOnCard` rows all legible |
| System back from Trends returns to Activity; back from Activity exits | pass, unchanged from earlier phases |
| 1.3× font scale | pass — the summary card grows past the viewport, the pane scrolls, and the last row (`Best day`, bottom 1874) clears the app's navigation bar (top 2090) |
| Bottom inset at the default scale | pass — content bottom at 1850-1897, nav bar at 2095; the trend panes fit the viewport on this data, so they do not scroll |
| `logcat -b crash` / `FATAL EXCEPTION` | pass — empty, and `grep -c` = 0 after the whole sweep |
| Light mode restored to the owner's setting | pass — the theme was set to Light for the check and put back to `SYSTEM` (verified by reading `settings.preferences_pb`), and the system `font_scale` was restored to `1.0` after the accessibility test |

### Found on the phone

- **`0` beside a non-zero total.** 13 diapers over 30 days read `0 per day`, and 2 solids over 30 days
  read the same. Fixed in the average's formatting: one decimal, and `< 0.1` where a non-zero average
  genuinely rounds away.
- **A count axis ticked in fractions** — `0.2`, `0.4` meals. `integerTicks` now keeps whole gridlines.
- **Two lines that were only shades of each other.** Now solid and dashed, with the legend drawing a line
  sample.

And one the phone never saw, because the unit test did: appending the newest day to a 2-day step put two
date labels 21dp apart on a 14-day window, for labels that measure ~33dp. The labels are now counted back
from the newest day.

**Not exercised on the device:** the empty state for an activity with no records inside the window —
every activity has data within the last 7 days on this phone. The branch is covered by `TrendsTest`
(`hasData` false) and mirrors the empty states Summary and Growth already ship.
