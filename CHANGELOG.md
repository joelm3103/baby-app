# BabyLog changelog

> [Keep a Changelog](https://keepachangelog.com/en/1.1.0/); this repo has no release
> cadence, so **sections are dated rather than versioned** - newest first, one section
> per day the work landed, instead of everything accumulating in one unreleased block.
>
> Two conventions, so an entry is never read as more than it is:
>
> - An entry stays under the date the work **landed**, even where the artifact was
>   revised later; any later measurement or rewrite is named inside the entry.
> - An entry recorded after the fact **says so**, so a retrospective note cannot be
>   mistaken for a same-day record.

## [2026-10-14]

**In short:**

- Bottle, diaper and solids log real values through full-screen sheets, and every log row now names the child and the amount instead of repeating a brand.
- Both-mode logging writes one row per child with identical timestamps, and History shows calendar-day totals with a configurable day/night split and filter chips.
- Settings is a real screen — units, children, backup/restore and bottle presets — laid out as distinct alternating cards.
- The shell's fade follows the active tab and content no longer runs under the system navigation bar, while the sheets gained an editable date and now say who they log for.
- **Later the same day:** the activity cards were corrected after use — a card now lists today's entries (up to five, and the feed behind the big number is one of them), `See all ›` opens every entry of that activity grouped by day, and any row can be edited in place or long-pressed to delete with an Undo. Verified on the S23 in the same pass; the capture is `docs/timeline/2026-10-14-phase9.1/`, including the three `before/` shots that reported the defects.

### Added

- `docs/timeline/`: the Phase 6, 7, 8 and 9 device captures — 32 screenshots under `docs/timeline/2026-10-14-phase6/` through `-phase9/` (9 + 8 + 6 + 9), with `index.html` sections 07-10 and the matching rows in `docs/timeline/README.md` (`a6407a0`, `11a6d4d`, `9011f7e`, `e8922dc`).
- `PLAN.md` gains **Phase 7 — Settings** (appearance, units, children, export) and **Phase 13 — Update delivery** (release signing plus delivery without a cable). The latter precedes the ship step because a release-signed APK is what gets delivered.
- `MaintenanceRepository` (`data/repo/MaintenanceRepository.kt`): read-all including tombstones, replace-everything import in one transaction, archive a child, permanently delete a child with their events and outbox rows.
- `Backup.kt` (`data/backup/Backup.kt`): a versioned (`schema: 1`), indented JSON envelope of children, every event including tombstones, and the foods table including custom foods, with `UnsupportedBackupException` for a newer schema.
- `SettingsScreen` and `SettingsViewModel` (`ui/settings/`): one scrolling screen for appearance, units, children and data, with Storage Access Framework `CreateDocument`/`OpenDocument` for export and import — no permission and no FileProvider.
- A semantic `danger` token (`#FF7A7A` dark, `#B3261E` light), used only for permanent deletion; measured at 6.85:1 and 6.19:1 on `background`, with `onAccent` text on the fill at 6.85:1 and 6.54:1.
- `ChildRepository.add`, and the DAO surface it needs: `allIncludingDeleted`, `deleteAll`, `deleteByChild`, `softDeleteByChild`, `idsForChild`, `hardDelete`, `maxSortOrder`, `totalCount`, plus outbox `deleteByEntityIds` and `deleteEverything`.
- 15 tests: `BackupTest` (4), `MaintenanceRepositoryTest` (6), `ChildSeedOnceTest` (2) and three more in `ChildSelectionTest`; the suite is 136.
- `domain/Totals.kt`: pure, `zone`-explicit `dayRange`, `isNight` and `totalsFor`, plus `ActivityTotal`, `HistoryRange` (1d/7d/14d) and `NightWindow`. Totals are deliberately not SQL aggregates, because a day boundary and the day/night split are local-timezone facts.
- `ui/history/HistoryScreen.kt` and `HistoryViewModel.kt`, and `ui/home/ScreenHeader.kt` extracted from `ActivityScreen` so Activity and History share one header and child switcher.
- `EventDao.observeBetween` and the `EventRepository` wrapper, so History reflects a write on the Activity tab without a relaunch. An empty child list short-circuits, because Room expands `IN (:childIds)` into `IN ()`, which SQLite rejects.
- `SettingsRepository.nightWindow` with `setNightStartHour`/`setNightEndHour` (defaults 18 and 6, clamped to 0-23), and a **Day & night** section in Settings using two hour steppers.
- `EVENT_DISPLAY_ORDER` in the domain, now read by both `CardBuilder` and `Totals`, so the dashboard and History cannot disagree about card order.
- 13 more tests: `TotalsTest` (10, including a hand-counted day, a non-UTC boundary, night wrap-around and `start == end`) and `SettingsNightWindowTest` (3); the suite is 149.
- `ui/home/ChildBadge.kt` — a neutral circle with a genuinely centred initial, shared by the child switcher and the log rows.
- `SettingsRepository.bottlePresets` (ordered canonical ml, defaults 60/90/120/150, add/remove, sorted on read) and a **Bottle presets** section in Settings.
- `dateOn(instantMs, epochDay, zone)` in `domain/Formatting.kt`, the companion of `timeOfDayOn`, so a sheet's Date row and Time row compose without fighting each other.
- An optional `None` option on `ChipRow`, used so the solids reaction can be cleared.
- 9 more tests: `FormattingTest` (+2), `CardSummaryTest` (+3) and `SettingsRepositoryTest` (7, replacing `SettingsNightWindowTest`); the suite is 158.

- `ActivitySheet`, a full-screen logging frame with an activity-tinted title bar (`X` / title / `Save`), hairline `InputRow`s, and a `BackHandler` so system back dismisses the sheet rather than leaving the app (`app/src/main/java/app/babylog/ui/home/ActivitySheet.kt`).
- Bottle, Diaper and Solids logging sheets (`ui/home/BottleSheet.kt`, `ui/home/DiaperSheet.kt`, `ui/home/SolidsSheet.kt`) and their shared controls (`ui/home/SheetControls.kt`).
- `FoodPicker` (`ui/home/FoodPicker.kt`): catalogue search, multi-select, recent-first ordering, and adding a food the catalogue does not have.
- A curated 91-food catalogue shipped as a bundled asset, `app/src/main/assets/foods/foods.json`, with a stable `id` slug per food and no honey (under-12-months safety).
- `FoodRepository` (`data/repo/FoodRepository.kt`) and `FoodDao.observeCatalogue()`, seeding the asset insert-if-absent and ordering the picker by `lastUsedAt` so logging a food moves it to the top.
- `FoodCatalogue` / `FoodCatalogueParser` (`data/local/FoodCatalogue.kt`) and `foodSlug` / `uniqueFoodSlug` (`domain/FoodSlug.kt`).
- `fromMillilitres` (`domain/Units.kt`), so the bottle amount stepper converts once on save rather than on every press.
- `timeOfDayOn` (`domain/Formatting.kt`), which keeps the local day when a sheet's time is edited.
- 18 unit tests: `FoodSlugTest` (8), `FoodRepositoryTest` (8, against a real Room database under Robolectric), and one each in `FormattingTest` and `UnitsTest`; the suite is 121 tests.
- `EventListOverlay` (`ui/home/EventListOverlay.kt`): the full-screen "See all" list — every entry of one activity for the current child/both selection, newest first, grouped by day (`Today` / `Yesterday` / `Wed 12 Mar`). Drawn as a sibling of the `Scaffold` so it covers the bottom navigation bar, with its own `BackHandler`.
- `EntryRow` (`ui/home/EntryRow.kt`): one logged entry as a tappable row, shared by the cards and the overlay so the two renderings cannot drift. Meta text picks its token by surface — `textOnCard` on a tinted card, `textSecondary` on the overlay's plain background.
- `domain/EventOrder.kt`: one newest-first comparator, with the configured child order as the tie-break, used by the card builder and the day grouping.
- `EventDraft.applyTo(existing, updatedAt)` (`data/repo/EventDraft.kt`): the edit path, implemented as `toEvent` with the identity filled in from the existing row rather than a second field-by-field copy. `EventRepository.update` existed until now with no UI on top of it.
- Editing and deleting reach the UI: tap a row to open its sheet pre-filled and save back onto the same row (same `id`, child and creator — `EventRepository.update`), long-press to soft delete with a `Undo` snackbar (`softDelete` / `undoDelete`), both confirmed first. The snackbar host lives in the shell's `Box`, drawn above the overlay and the bottom bar, because the overlay is a sibling of the `Scaffold` and would otherwise cover it.
- 20 unit tests: `EventOrderTest` (5 — identical timestamps, both input orders, archived child, reversed child order, timestamps still winning), `EventDraftTest` (6 — identity preserved, the child taken from the row, cleared values staying cleared, a draft that cannot retype a row, a tombstone staying deleted), 6 more in `CardBuilderTest` (today-only rows, the five cap, no padding from yesterday, the headline in the rows, nothing-left-over meaning no footer, a local-midnight boundary) and 2 in `EventRepositoryTest` (an edit updating in place with exactly one queue entry; an edit that cannot move a row to the other child). The suite is 178 tests.

### Changed

- The Phase-5 `quickLog` stub is replaced by sheet-driven writes: a sheet's draft goes through one `EventRepository.log(childIds, draft, CREATED_BY)` call, so Both mode still writes one row per child with identical timestamps (`ui/home/HomeViewModel.kt`). Verified on device, read from the Room database: child 1 + child 2 `BOTTLE` at 13:59:53 UTC (120 ml), `SOLIDS` at 14:01:00 (`mango-lassi` / `BREAKFAST` / `LOVED_IT`), `DIAPER` at 14:03:37 (`MIXED`, `rash=1`).
- Card action labels are context-aware from `HomeUiState.childLabel`: `+ Log feed for Child 1` / `+ Log feed for both` (`ui/home/ActivityCard.kt`).
- `AppScaffold` hosts the sheet as a sibling of the `Scaffold`, drawn over the bottom navigation bar so a full-screen sheet is never clipped by it (`ui/AppScaffold.kt`).
- `AppRoot` seeds the food catalogue alongside the two children (`ui/AppRoot.kt`), with `AppContainer` exposing `foods` (`di/AppContainer.kt`); `AppStartupTest` seeds the catalogue through the real container asset path.
- `README.md` and `PLAN.md` status corrected, and `PLAN.md` gained a Phase 6 device-verification section.
- `PLAN.md` phases renumbered to seat the two new ones: History 7→8, Growth 8→9, Health 9→10, Trends 10→11, Two accounts 11→12, Ship 12→14, with the Growth tasks renamed 8.1-8.5 → 9.1-9.5. Live references updated in `ui/home/ActivityCard.kt`, `ui/AppScaffold.kt`, `ui/home/HomeViewModel.kt`, `data/local/OutboxDao.kt`, `data/local/OutboxEntity.kt`, `data/repo/EventRepository.kt`, `ui/Placeholders.kt`, `README.md`, `research/growth-data-sources.md`, `.hermes/skills/babylog-ui/references/components.md` and `docs/timeline/index.html`.
- `PLAN.md` records sleep's hue as settled (it stays `#63C8FF`) and drops the stale `no export` line from the do-not-build list, since a JSON export now belongs to Phase 7.
- First-run seeding moved behind a one-way `SettingsRepository.seedChildrenOnce` flag. The old guard was `childDao.count()`, which filters `deleted = 0`, so removing every child made the app look fresh and would resurrect the twins on the next launch.
- The **Both** pill now appears only at exactly two children. `ChildSelection.resolve` falls back to the first child in sort order, `isValidFor`/`fallbackFor` express the rule, and `HomeViewModel` persists the fallback when a child is added or removed.
- `AppScaffold` renders the real Settings screen, owns the export/import launchers, and adds the bottom navigation inset for Settings, which has no bottom bar of its own; `AppContainer` exposes `maintenance`; the Settings placeholder is gone from `ui/Placeholders.kt`.
- `.hermes/skills/babylog-ui/references/design-tokens.md`: documents `danger`, and the stale "cards are NOT tinted" section was corrected to the shipped 26% tint with its measured derivations.
- History replaced its placeholder: a `1d · 7d · 14d` calendar-day range selector, multi-select filter chips (session-only, all shown by default), and six total cards that carry a per-child line in Both mode. Growth and Health report the latest measurement in range rather than a sum.
- `ActivityScreen` no longer builds its own header; both Activity and History use `ScreenHeader`, per the contract's "keep it identical on Activity and History".
- Log rows show the child badge and the VOLUME for a bottle. `recentRowLabel` is deliberately a separate function from `detailLine`, which still shows the brand under a card's big value — the two questions differ.
- The bottle amount is free text in the display unit plus preset chips; the stepper is gone, because it cannot express 135 ml.
- Bottle, Diaper and Solids sheets gained a Date row and a `for Child 1` / `for both` subtitle.
- History filter chips use `accentFor(type)` when selected, matching the card each chip controls.
- Settings is one card per section, alternating `surface` / `surfaceRaised`, plus the Bottle presets section.
- The shell hoists a scroll state per destination so the scrim follows the ACTIVE tab; the bottom bar gained a divider; Settings' bottom inset moved outside its scroll.
- Phases renumbered: this polish pass is 9, Growth 10, Health 11, Trends 12, Two accounts 13, Update delivery 14, Ship 15.

### Removed

- `HomeViewModel.quickLog`, the Phase-5 stub that logged a bare timestamp for Bottle, Solids and Diaper.
- The Growth and Health action buttons' valueless stub writes; the buttons are now disabled with a phase note until those sheets exist (`ui/home/ActivityCard.kt`).

### Fixed

- The Rash toggle and the food rows in the picker drew a `›` chevron, which the design contract reserves for rows that push a screen. `InputRow` now takes `chevron` (default `onClick != null`) and the in-place controls pass `chevron = false` (`ui/home/SheetControls.kt`, `ui/home/FoodPicker.kt`). Found on device, not by the build.
- `ChildSelection.resolve` returned **all** children for a stale single-child selection, which would have shown a combined view that the switcher no longer offers once there are three. It now falls back to the first child in sort order.
- The skill's `design-tokens.md` still asserted "cards are NOT tinted" and described the rejected accent bar, contradicting the shipped 26% tint in `Tokens.kt` and `ActivityCard`.
- `PLAN.md`'s verification line still read **121/121** and listed only the Phase 5 and 6 device passes; corrected to 149 and Phases 5-8.
- **Opening the bottle sheet and pressing Save silently rewrote the amount.** 90 ml renders as "3" fl oz, and re-parsing "3" stored 89 ml. An untouched prefill now keeps its stored value.
- `SettingsScreen` applied its insets INSIDE `verticalScroll`, so they scrolled away with the content and rows ran under the transparent system navigation bar.
- Settings and History had no top fade, because `scrimAlpha` read Activity's list state rather than the active destination's.
- The child switcher's initial was not centred in its circle: a `Text`'s line box is taller than its glyph.

These four were found on the device and are the reason this day has a second entry:

- **`See all ›` did nothing.** `ActivityScreen` passed `onSeeAll = {}`, and the footer only rendered when a card happened to hold three events of a type, so in the ordinary case the link was not even there. It now opens the full-screen entry list.
- **A card could never show more than two rows.** `CardBuilder` kept the newest three events of each type and `ActivityCard` drew `recent.drop(1)`, because the big number consumed the newest of them; and those three were "the last three ever", printed with a bare clock time, so a feed from yesterday was indistinguishable from one an hour ago. Cards now list today's entries, newest first, capped at five, with the big number's own entry included.
- **The Both card's numbers read as wrong, and the data was fine.** the second twin's 14:14 feed was the headline on the second twin's card and a row on the Both card, and the two lists could not be compared because the headline had swallowed one entry. The headline now carries the child's badge and the absolute time whenever more than one child is in scope, and the rows include it, so the second twin's 13:09 feed is visible in Both mode.
- **Rows wasted their space** — 8dp of vertical padding around a 20dp line of text in a 35dp row, plus an 18dp `K` badge on every row of a single-child card. Now 5dp, a 16dp badge in Both mode only, and an 8dp gap above the block.
- **Two rows written by one Both-mode tap could swap places.** They share a `startedAt`, and `ORDER BY startedAt DESC` alone left their order to SQLite; one comparator now resolves ties by the configured child order.
- **The delete Undo was unreachable.** `SnackbarDuration.Short` is four seconds, and it expired between a UI dump and a tap, which landed on the Diaper card underneath — nothing was written, but a finger has the same four seconds. It is now `Long` (ten), per Material's own guidance for a snackbar carrying an action, and the Undo was re-verified inside the longer window. Found during the device pass, not by the build.

## [2026-10-12]

**In short:**

- The project went from an empty repo to a device-verified Activity screen in one day: toolchain, scaffold, domain, Room v1, repository, and the first visible build.
- Two bugs that no JVM or Robolectric test could catch were found by putting the app on a phone, and both were fixed that afternoon.
- The design contract was corrected against real screenshots, and the card tint was dropped then restored at a measured 26%.
- The record became durable: `docs/timeline/` with the rejected accent treatments kept alongside the accepted one, and `AGENTS.md` so an outside agent can pick the repo up.

### Added

- The repository: `chore: initialize repository`, a 27-line `.gitignore` for environment secrets, dependencies, build output and editor/OS cruft (`6da08ae`).
- `research/`: the the free alternative and the incumbent tracker teardowns from 24 Play Store listing frames, source URLs with the failed de-skew attempt recorded, and the design system derived from them as a loadable project skill at `.hermes/skills/babylog-ui/` (`88f121f`).
- `README.md`'s v1 scope decision, and `PLAN.md` — the phased plan with Phase 0's toolchain in it (`88f121f`).
- Toolchain record: AGP 9.4.1, Gradle 9.8.1, Compose BOM 2026.09.00, JDK = Studio's bundled JBR 25.0.3, and the three AGP 9 facts that cost build cycles — the `kotlin.android` plugin must NOT be applied, `compileSdkMinor` is required for android-37.2, and `jvmToolchain(17)` fails on a JBR-only machine (`475bc42`, recorded in `research/toolchain.md`).
- `scripts/android-env.ps1` and `sdkmanager.ps1`, so the toolchain environment is reproducible without quoting gymnastics (`475bc42`).
- `domain/` — `EventType` and `BabyEvent`, relative-time and duration formatting, and day grouping. Formatting is pure (`now` and `zone` are passed in, never read from the clock), so it is testable headlessly and cannot fail at midnight. 12 tests green (`ed8a2a2`).
- `domain/Units.kt` — the three independently selectable unit families (length in/cm, weight lb&oz / kg, volume fl oz / ml) with Metric/Imperial/Custom presets.
  - Storage stays canonical metric and conversion happens only at the display and input edge, so changing the setting never rewrites stored values and percentiles always compute in one system; the preset is derived from the three choices rather than stored.
  - Celsius only, deliberately: there is no Fahrenheit path, because a temperature in F is not a unit preference, it is a different number waiting to be misread during a fever.
  - 25 tests green, including the compound-unit carry that 440 g → "1 lb 0 oz" depends on (`d3aa8e6`).
- Room schema v1 — four entities (children, events, foods, outbox), four DAOs, pure entity↔domain mappers and a food-id codec, with the exported schema committed as source at `app/schemas/app.babylog.data.local.AppDatabase/1.json` (`aba1573`).
  - 50 tests green, the DAO ones on the JVM via Robolectric 4.17 — which is what keeps the deliberately skipped emulator from leaving the schema unverified, since a schema mistake would otherwise only surface on a phone.
- `EventRepository`, the only writer, plus `SettingsRepository` over DataStore and `Age.kt`; 78 tests green (`6b80b60`).
  - `log()` takes a **list** of child ids, so Both mode is one draft → N rows sharing an identical timestamp, with a test that duplicate ids in the request do not double-write.
  - `ageDaysForCharts()` is a named function rather than an inline subtraction, because for the twins at premature the adjusted offset is 70 days — the difference between ~9m1w and ~7m1d, and getting it wrong yields a confident wrong percentile with no error.
- `AppScaffold`, `ActivityScreen`, `ChildSwitcher`, `ActivityCard`, `HomeViewModel`, a manual DI container, and a first-run seed of the two boys (`f0d7c18`).
  - Recorded at the time as layout-**UNVERIFIED**: it compiles and the APK builds, but overflowing cards, clipped text and a wrapping pill row all compile cleanly, so a green build says nothing.
- An adaptive launcher icon, and the four tests that cover what a build cannot: `AppStartupTest`, `MainActivityLaunchTest`, `CardBuilderTest`, `CardSummaryTest`; 97 tests green (`d161b82`).
- `docs/timeline/` — a durable visual history with an `index.html` gallery and `scripts/capture-timeline.ps1`, keeping the two **rejected** accent treatments as well as the accepted one (`1a95606`, `50328a1`).
  - The scratch directory those screenshots had been written to was pruned after 24h, so nothing was durable before this.
- `AGENTS.md` — the repo now carries its own agent instructions: the stack, the measured design contract, the device-verification loop, and the traps that have already cost us, so an outside agent can pick this up without the session context (`1a95606`).
- `research/growth-data-sources.md` — WHO's igrowup LMS tables verified by downloading and parsing, not by recall, with row counts, sex codes and sha256 recorded (`9e42886`).
  - The age axis is **days** (0-1856), proven by spot-check: boys weight-for-age at "age 12" gives M=3.671 kg, which is a 12-day-old. Reading that axis as months would produce catastrophically wrong charts, so the unit is documented explicitly.

### Changed

- The design system was corrected against 15 real the incumbent tracker screenshots, which falsified two conclusions from the marketing-frame teardown (`d13eb53`):
  - the incumbent tracker runs DARK, so the original light-only, white-card palette described an app that does not exist. Dark values are now sampled by pixel clustering; light is designed and labelled as designed rather than presented as measured.
  - The diaper accent is a muted sage, not yellow.
- Scope was rewritten for twins: two children (the twins, a date set in Settings, editable) with a header child switcher and a **Both** mode that logs to both in one action, per-child premature weeks and adjusted age, and bottle/solids/diaper/sleep/growth/health (`d13eb53`).
  - Three the incumbent tracker defects became hard design REQUIREMENTS rather than polish: navigation-bar insets (the incumbent tracker clips its Child screen), a `BackHandler` on every sheet (the incumbent's back exits the app), and both children visible.
- `PLAN.md` was rewritten in the same pass — completed Phases 0-2 collapsed to status rows and the plan renumbered, which is why the plan body now begins at Phase 3 (`d13eb53`).
- The six activity accents were replaced with six well-separated hues, chroma up 2-5x (`bf4ce7c`).
  - Measured on the sampled the incumbent tracker palette, Solids (hue 35) sat 8 degrees from Bottle's gold (43) and Growth (85) sat 9 degrees from Diaper (76): two of six activity colours were effectively the same colour as another.
- The card tint went and came back, both times by measurement rather than taste (`bf4ce7c`, `1a95606`).
  - Dropped because on a dark surface a tint collapses the accents to chroma 0.093 — the olive-brown and maroon that was reported.
  - Restored at **26%**, which is measured: 0.16 is only dE 7.7 apart (barely separable), 0.26 is dE 12.5, and above 0.30 the accent-filled button starts washing out.
  - The raw accent is NOT legible as a label on its own tint (3.4-4.3:1), so labels use the new `labelFor()` (6.4:1) and meta text the new `textOnCard` token (4.7-5.8:1, against `textSecondary`'s 3.06:1). A 4dp accent bar and a 6dp top band were both built and rejected first.
- Stray `MaterialTheme.colorScheme` defaults were removed from `RelativeTimeText`, `LiveTimerText` and `BigValue`, because they are tuned for a neutral surface and measured under 4.5:1 on a tinted card — that was the dim "just now" on the olive and green cards (`1a95606`).
- Content scrolled under the transparent status bar and collided with the clock, because the insets are applied as `LazyColumn` contentPadding and items therefore scroll THROUGH the inset region rather than stopping below it (`1a95606`).
  - The list state is now hoisted into `AppScaffold` and a scrim fades in behind the status bar once the list has moved: solid across the bar, fading out below it, no hard seam. Blur was considered and deferred.
- Post-build change cost became a constraint the plan has to satisfy, not a chat answer (`df9cfb0`): additive-only migrations from schema v1, export/backup in Settings as the highest-value insurance against a bad migration, and update delivery by tap rather than adb on two phones.
- Photos and widgets were settled as permanently out of scope, which removes the last expensive retrofit and keeps the schema, the sync payload and the backup text-only (`aba1573`).

### Removed

- `HomeViewModelTest` and `kotlinx-coroutines-test` (`9d10090`).
  - The test hung: Robolectric's paused main looper never pumps Room's invalidation callbacks, so a database-backed Flow cannot be collected in that harness. Better deleted than left as a 4-minute no-op that looks like coverage.
- `EventType` nursing and pumping, removed rather than reserved: an unused variant with no accent, no card and no sheet is dead weight, and adding one later is an enum value plus UI (`aba1573`).

### Fixed

- **Crash on launch, on every start, on the device** (`9d10090`).
  - `ChildSelection.default` was a stored companion `val`. On Android, ART initialises the enclosing interface — and therefore its companion — WHILE `Both` is still being constructed, so the initialiser read `Both`'s static `INSTANCE` field before it was assigned and stored null permanently; `resolve()` then died with an NPE on every start.
  - Fixed by making it computed: `val default: ChildSelection get() = Both`. HotSpot initialises superinterfaces more lazily and sees the field already assigned, so this was invisible to all 97 JVM and Robolectric tests. Only the phone caught it, and it is now recorded in `AGENTS.md` as a trap.
- **A live timer on every card** (`9d10090`). `isRunning` was `endedAt == null`, which conflates "still in progress" with "a point event that has no end", so every logged bottle, diaper, weigh-in and temperature rendered a count-up timer with a Stop button. Fixed with `EventType.hasDuration` (true for SLEEP alone) and `isRunning = type.hasDuration && endedAt == null`.
- A guard that was silently compiled away: `combined.second ?: ChildSelection.Both` is constant-folded to nothing when the left operand's declared type is non-null, so the first attempt at the check did not exist in bytecode. Widening to a nullable local first makes it real; noted in the code so nobody repeats it (`9d10090`).
- The app shipped with **no launcher icon**: `res/` held only `themes.xml`, the manifest had no `android:icon`, and `aapt` reported `icon=''` (`d161b82`). Added an adaptive icon — bottle silhouette in the feed gold on the real background `#181A26`.
- `buildCards()` assumed its input arrived newest-first, as the DAO happens to return it, so a wrong "last event" was wrong on the card and invisible in review. It now sorts internally, with a test asserting both orderings agree (`d161b82`).
- `elapsed()` rendered sub-minute durations as "0m 45s"; the assertion was right and the implementation changed (`ed8a2a2`).
- `capture-timeline.ps1` resolved the phone's serial as `'R'`, from two stacked bugs (`a2098d1`): a PowerShell pipeline that yields ONE item assigns a scalar, not an array, so `$devices[0]` returned its first CHARACTER while `$devices.Count` still read 1 and the ambiguity guard passed; and nothing re-validated the serial, so `adb -s R` blocked until the caller timed out.
  - Wrapped in `@()`, every serial is now checked against `adb devices` so a bad one fails immediately, and screenshots go to `/sdcard` to come back via `adb pull`, because PowerShell decodes a native command's stdout as text and corrupts the PNG.
- Two of our own test expectations were wrong and the implementation right (`d3aa8e6`): 4500 g is 158.73 oz, rounding to 159 = **9 lb 15 oz**, not 9 lb 14 oz; and 440 g rounds to 16 oz and therefore MUST carry to "1 lb 0 oz", now covered on both sides of the boundary (430 g stays "0 lb 15 oz").

Verified on a Samsung S23 Ultra (Android 16 / API 36): launches clean, taps log correctly, no timer on point events, the Sleep card clears the nav bar at full scroll, and system back returns to Activity instead of exiting. 103 tests green at the end of the day.
