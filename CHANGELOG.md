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

## [2026-10-18]

**In short:**

- Health is real, and it is the last card that was still a placeholder: a temperature, a medication with its dose, and vaccines — so all six activities now write data and the disabled `+ Add health … arrive in Phase 11` button is gone.
- **A vaccine is not a field on the fever form.** It got its own sheet and its own button, because a vaccine is entered from a red book weeks after the event and a fever is what a parent reaches for at 3am.
- The medication picker offers **names from a bundled list plus this family's own**, and it **never suggests a dose**. An infant dose depends on weight and age and changes as they grow, so the amount is always typed; the app only remembers the last one as a grey hint. The list is names only, and a test enforces that no entry in it contains a digit.
- **The schema changed for the first time, v1 → v2**, because a dose became real data (amount + unit, so History can say *which medicines* without carrying the amounts). Additive nullable columns, a committed `2.json`, and a migration test built from the committed v1 schema. Then the real one: the S23's database went v1 → v2 with **all 69 rows intact** and the new columns `NULL` rather than `0`.
- A fever is marked at ≥ 38.0 °C, and the marker had to be **measured and then corrected**: `danger` fails AA on the 26% card tint (3.76:1 dark, 4.28:1 light), so `feverOnCard` was derived and clears **4.80:1 on all six tints** — and when the owner still could not see it, the tinted number became a filled **`Fever` pill** (10.39:1 for the word on the fill, 5.71:1 for the fill against the card).
- **308 tests green** (255 → 308). Found by a test, not the phone: `1 drops`, a unit label that never pluralised. Found by the phone, not a test: the card was **hiding the fever altogether**, and the medication menu filled the whole screen.

### Added

- `ui/home/HealthSheet.kt` — `Health record` / `Edit health record`: date, time, temperature (°C), a medication picker, a dose (amount + unit), notes. Save stays disabled while the record has nothing in it, and says which field is missing.
- `ui/home/VaccineSheet.kt` — `Vaccine` / `Edit vaccine`: date, time, the vaccine's name, notes. The name is required and is the whole record.
- `ui/home/HealthEntry.kt` — everything that decides what a health form **stores**, as pure Kotlin: plausibility guards (30.0–45.0 °C, so a Fahrenheit reading typed into the Celsius box is refused), the at-least-one-field rules, the seed-equality rule that keeps a stored value when a prefill is untouched, and `lastDoses()`, which finds the parent's own last dose for a medicine per child.
- `app/src/main/assets/medications/medications.json` — 14 commonly used infant medicines as **names only** (generic with a common brand in brackets), including the preemie supplements. The asset's own `note` records that it is not clinically reviewed and could not be checked against a live source in this session.
- `data/medications/MedicationCatalogue.kt` — the `org.json` parser, with the reason a dose list must never exist written next to it.
- `domain/Dose.kt` — `DoseUnit` (ml, mg, drops, sachet, tablet, other) with a picker label and a dose word per amount, and `formatDose`/`doseNumber`.
- Medication presets in Settings (`health.medicationPresets`), a second editable list beside the bottle presets, with the same add/remove shape.
- `feverOnCard` in the token set — `danger` lifted toward white 0.45 (dark) / black 0.30 (light) — and `FeverBadge`, the filled pill that carries the word, used on the card headline, the Both-mode columns, the entry-list rows and History's Health total. Measured ratios are recorded in `design-tokens.md`.
- `MigrationTest` — builds a **v1** database from the committed `schemas/…/1.json` (so the test cannot drift from the schema it upgrades), opens it with the current Room build, and asserts the old rows survive with empty dose columns.

### Changed

- `EventEntity`/`BabyEvent`/`EventDraft`/`Mappers`/`Backup` gained `doseAmount: Double?` and `doseUnit: String?`; the backup carries both, because a restore that lost the unit would restore `2.5` of nothing.
- `AppDatabase` is **version 2** with `MIGRATION_1_2` (two `ALTER TABLE … ADD COLUMN`). No destructive migration, as ever.
- The Health card's disabled pill became an accent-filled `+ Add health record for $childLabel` plus a neutral `+ Vaccine`.
- Both mode now shows **one column per child** on the Health card too — that child's newest temperature, their newest medication and their newest vaccine: the **latest of each kind**, with the reading and the medication combined into one line when they came from the same record. The column renderer is shared with Growth rather than copied.
- `recentRowLabel(HEALTH)` shows the reading: `38.2 °C · fever · Calpol 2.5 ml`, so a health row is self-describing and the entry behind the headline is one of the rows.
- History's Health total reports `Records`, `Medication`, `Dose`, `Vaccine` and `Measured`, with the fever flag; a range holding only a medication is no longer reported as "Nothing logged".
- `decimalInput()` moved into `SheetControls` — the growth sheet and the health sheet were carrying the same three lines of keypad filtering.

### Fixed

- **`1 drops`.** The unit label was used for both the picker and a formatted dose, so a dose of one read `1 drops` on a fever card. `DoseUnit` now carries a picker label plus singular/plural, and a fraction takes the singular (`0.5 sachet`) while anything above one takes the plural (`1.5 sachets`, `2 sachets`). A new `DoseTest` pins it.
- An amount typed with **no unit** is an error rather than a silent drop: the parent typed 2.5, and saving the record without it would be the log changing under their hands. A unit with no amount is simply no dose, so a half-filled picker can never block a save.
- The first build of this phase failed with 59 "unresolved reference" errors in files nobody had touched; the incremental Kotlin cache had gone stale, and clearing it reproduced a clean build. Recorded because the errors named `ActivitySheet`, `rememberNow`, `accentFor` and `ChildBadge` — i.e. they looked like a catastrophe and were a cache.

### Fixed after the owner's review — "Fever isn't highlighted in any way"

Amended into this entry rather than given its own date, because it is the same day's work. The report was
accurate and the cause was worse than styling:

- **The card was hiding the fever.** Its summary held ONE "record" slot (temperature OR medication), so a
  medication logged at 12:27 took it from the fever logged at 12:08 — the fever was not dim on the card,
  it was absent from it, and only History still showed it. It is now the latest of **three** kinds
  (temperature, medication, vaccine), pinned by `a later medication does not hide an earlier fever`.
- **The marker is a filled pill, not a tinted number.** A pastel red on a magenta card, beside a label that
  is itself a lifted magenta, is not a signal. `FeverBadge` puts the word in a `feverOnCard`-filled pill —
  10.39:1 dark / 10.30:1 light for the word on the fill, 5.71:1 / 6.75:1 for the fill against the card — and
  the word is no longer part of any label string, so a narrow row cannot ellipsise it away. On the phone
  the same band went from 3,762 pixels of tinted text to **10,800 pixels of `#FFB6B6`** plus 928 of
  `onAccent` for the word inside it.
- **`Type another name…` gives an empty, focused field.** It used to keep the previously selected name and
  leave the cursor nowhere, so it had to be cleared before use. Verified by typing with no tap: the field's
  own `EditText` node reports `focused=true` and the text lands in it.
- **The medication menu no longer fills the screen.** Fourteen names plus the family's own ran from the
  status bar to the navigation bar and hid the form it belonged to; it is capped at 320dp (about five rows)
  and scrolls inside that — two swipes still reach the last name and `Type another name…`.

### Verified

- `:app:assembleDebug` builds and `:app:testDebugUnitTest` is **308/308**, 0 failures, 0 errors.
- On the S23 Ultra (Android 16, dark): Both-mode health and vaccine saves each wrote **two rows with an identical timestamp**; the fever marker renders as **3,762 pixels of exactly `#FFB6B6`** with no `textPrimary` pixels in that band; the medication dropdown lists the shipped names plus `Type another name…`; the last-dose hint reads `Last recorded — Child 1 2.5 ml · Child 2 2.5 ml` while leaving the dose field **empty**; History shows the fever reading in `feverOnCard` with separate Medication and Dose rows; Trends' Health tab keeps its honest Phase 12 sentence; system back closes both sheets; `logcat -b crash` is empty.
- The device's own database was read before and after the install: `user_version` 1 → 2, 22 → 24 columns, **69 rows before and after**, and the two legacy health rows still read `38.5 °C` / `Calpol 2.5 ml` with `doseAmount = NULL`.

### Notes

- For a *summary*, History's Health card shows the latest reading beside the latest medication and dose, which need not come from the same record. That matches what the phase was asked for ("the latest of each kind"), and the card's row list is where the actual records are read.
- The bundled medicine list is the one thing here carrying a safety claim. It has no doses in it, a test enforces that, and the sheet says `Names only — this app does not suggest a dose` — but it is not clinically reviewed, and it should be checked against a pharmacist's list before it is trusted.

## [2026-10-15]

**In short:**

- Growth became real: WHO's LMS reference ships inside the APK, and a measurement now reads back as a percentile with its age, on a chart.
- The bundled parameters were checked against WHO's **own** published tables, not against the mirror they were downloaded from — every row, both sexes: the L/M/S are identical, and the app's transform reproduces WHO's printed SD columns to 0.0005.
- **INTERGROWTH-21st was dropped**, not deferred: its data carries reserved rights rather than an open licence, so a measurement taken before term gets no percentile and a sentence saying why.
- The dataset update channel moved to Phase 13, because the Firestore it assumed does not exist yet — nothing was stubbed or faked in its place.
- Seeing it on the phone changed the design: the chart moved into **Trends** as a tab per activity, the Both-mode summary became **one column per child** instead of a combined headline, values gained `W`/`L`/`H` codes, and the highlight box became one per child rather than whichever child won a name-order tie.
- Three layout defects were found only by looking at the screen — an untappable chip row under the status bar, a pane overlapping it whose end could not be scrolled to, and a button that silently did nothing. **255 tests green**, and the pass is recorded in `PLAN.md`.

### Added

- `app/src/main/assets/growth/who-igrowup.json`: WHO's five `igrowup` LMS tables (weight-for-age, length/height-for-age, head-circumference-for-age, BMI-for-age, weight-for-length/height), both sexes, 16,158 rows as compact arrays with an identity block (`version`, `publishedAt`, `retrievedAt`, citation, per-file `sha256` and source URLs). 488,449 bytes, asset sha256 `2df347597249d27490e73ae7ca8cce956d5ee1141dfcf1a84fec7fa1f518cb5c`.
- `scripts/build-growth-assets.py`: downloads the five tables from the MIT-licensed `ki-tools/growthstandards` mirror, verifies each file's sha256 against `research/growth-data-sources.md`, asserts the shape the lookup depends on (age tables daily 0..1856 with day == row index; the weight-for-length table 45.0-110.0 cm in 0.1 cm steps) and fails loudly on drift rather than regenerating over it. Committed so the asset is regenerable and auditable, the same reason the Room schema is.
- `scripts/verify-growth-asset.py`: the publisher-side check. Downloads WHO's own expanded z-score tables from `cdn.who.int` and compares every row, per measure per sex, reporting max |dLMS| and max |dSD| and exiting non-zero on any mismatch. Found two unguessable URL traps (length/height-for-age is served from `expandable-tables`; weight-for-length's file is `wfl-…-table.xlsx`, singular).
- `data/growth/GrowthReference.kt`: dataset identity, the `org.json` loader, `GrowthMeasure`/`GrowthAxis` so a table's axis travels with it, `lms(measure, sex, axis)` returning null outside a published range instead of extrapolating, `curve()` for plotting, and `identityLine` deriving `WHO 2006 · v2026.10 · updated 15 Oct 2026`.
- `domain/Percentiles.kt`: LMS to z-score to percentile, the standard normal CDF (Abramowitz & Stegun 26.2.17 — the JDK has no `erf`), the inverse for curve bundles, the seven published normal quantiles, and the ordinal label. Functions that can be asked a meaningless question return null, because "0 kg" has no percentile and a silent NaN would eventually be rendered as text.
- `ui/home/GrowthEntry.kt` + `ui/home/GrowthSheet.kt`: logging weight (kg, or the compound lb/oz pair), length, head, date, time and a note. All three measurements are optional but at least one is required, because the Phase-5 stub's valueless rows are still in the log as a reminder of what a sheet that can save nothing does.
- `ui/growth/PercentileChart.kt`: one hand-drawn Compose Canvas chart primitive — curves, markers, two ranges, two label functions — with no charting dependency, written to be reused by Phase 12's Trends rather than reimplemented.
- `ui/growth/GrowthChartData.kt`: the pure rules behind the screen (which measurement gets a percentile, what the axis means, both children on one bundle, display-unit axes with metric maths), plus `GrowthViewModel` and `GrowthScreen`.
- 77 tests, all green: `PercentilesTest` (12, pure JVM, against WHO's published values), `GrowthReferenceTest` (11, Robolectric, against the real bundled asset), `GrowthEntryTest` (16), `GrowthChartTest` (24: trajectories, the pre-term rule, per-child callouts, the tick chooser), `GrowthChartAssetTest` (5, the shipped asset driving the real chart), and 9 more across `CardSummaryTest` and `CardBuilderTest`. The suite is 255.
- `GrowthChartAssetTest` exists for the seam the other tests cannot see: a measure key spelled differently in the asset, a sex key the chart never looks up, or an axis read in the wrong unit would each produce an empty chart on a phone while every unit test stayed green.
- The WHO xlsx sources are recorded in the script with the reasoning for each URL shape, so a future update does not have to rediscover that `wfh` is a different table from `wfl`.

### Changed

- **The Activity Growth card is per child in Both mode, and every value says what it is.** The summary is now one column per child headed by the child's name, one measurement per line (`W 5.4 kg` / `L 66 cm` / `H 42 cm`), and the W/L/H codes appear wherever a growth value is shown — the card summary, its entry rows, the overlay list, the chart's list — because a bare "42 cm" cannot be told from a length or a head circumference. A combined headline had no owner: "6 kg" in Both mode was one child's weight dressed up as the twins'.
- **`See all ›` shows as soon as an activity has one entry, for EVERY card**, not only when an entry was off-card. The old rule left the link invisible exactly when the full list was wanted, and it was the reason the Growth card had no route to its own data. It opens the same entry-list overlay as the other cards.
- **The percentile chart moved into Trends**, as one tab of a per-activity tab row over every type in `EVENT_DISPLAY_ORDER`. Growth is functional; the rest say honestly that their charts arrive in Phase 12, so nothing is faked. This brings Trends' shell forward from Phase 12 while its feed, diaper, solids and sleep charts stay there. `GrowthScreen` is now `GrowthChartPane` (the tab's content, with no Close button of its own), `HomeViewModel.openGrowth`/`dismissGrowth`/`openAllFor` are gone, `ui/Placeholders.kt` is deleted, and Trends gained its own scroll state so it fades like the other destinations.
- **The chart's highlight box is one per child in Both mode.** It used to be whichever measurement was newest in scope, ties broken by child name — so both twins measured on the same day meant the first twin's number, picked alphabetically, shown as if it spoke for both.
- `SettingsScreen` gains a **Growth reference** section naming the bundled dataset, loaded only when Settings is actually shown.
- `AppContainer` exposes the growth reference as a suspending, cached loader rather than a `by lazy` val, for the same reason — and cached so a percentile cannot come from two copies of the truth in one session.
- `domain/Units.kt`: `MM_PER_INCH` and `GRAMS_PER_POUND` are now public, because the chart converts continuous axis values and a second set of factors would be a second source of truth.
- `PLAN.md` Phase 10 rewritten as built, with the update channel moved into Phase 13 and INTERGROWTH-21st recorded as evaluated-and-dropped. The status row reads **DONE, device-verified** with the pass recorded at the end of the file.
- `research/growth-data-sources.md` corrected on three points: the INTERGROWTH repo path (`Yannngn/pygrowthstandards`, not a `pygrowthstandards` org that 404s), the `wflanthro` axis (45.0-110.0 cm in 0.1 cm steps, where the file's own header says something else), and that those tables are SD columns rather than LMS.

### Fixed

- **`+ Add measurement` on the Growth card did nothing.** `HomeViewModel.openSheet` guarded on a private `SHEET_TYPES` set that still listed only the three Phase-6 sheets, so the tap was silently dropped — a live-looking button with the right label and no effect. The guard is gone, and the sheet host names every type explicitly with no `else`, so adding an EventType without deciding its sheet is now a compile error. Found on the S23.
- **The Trends tab row was drawn from y=0, under the 94 px status bar**, so the top of every chip was untappable: tapping a chip's centre did nothing and only the strip below the status bar worked. The row now applies the shell's top inset itself, and the pane's padding drops the top it has already consumed.
- **The Trends pane overlapped the chips and its end could not be scrolled to.** It used `fillMaxSize()` inside the screen's `Column`, claiming the whole height instead of the space below the chips. It now takes the remaining height with `weight(1f)`; the last content ends at y=1919, clear of the navigation bar at y=2095. Both found by looking at the screen, not by the build.
- **The chart's y-axis could be reduced to two gridlines.** `yTicks` rounded the ideal step (span / 4) up to the next "nice" value, and the ladder's jump from 5 to 10 gave a head circumference spanning ~31-52 cm a 10-unit step and only two lines. The chooser now takes the smallest round step that fits within six ticks, so the axis reads as a scale at any span. Found by the asset-driven chart test, not by the build.
- The kg box in a measurement sheet now shows 10 g, not 100 g. `formatWeight`'s one decimal is right for a card but wrong for the sheet where the number is entered: 6350 g displayed as "6.4" kg and a parent could neither see nor correct the 50 g between them. Inches gained a second decimal for the same reason — 0.01 in is 0.254 mm, so the box is now finer than the millimetre stored behind it.
- **A measurement taken before term no longer gets a percentile.** A 30-week baby's 1.37 kg plotted at "day 0" of a term-baby chart sits far below the 2nd percentile, which reads as a growth failure rather than as immaturity. It now shows the value, no percentile, and the reason.

## [2026-10-14]

**In short:**

- Bottle, diaper and solids log real values through full-screen sheets, and every log row now names the child and the amount instead of repeating a brand.
- Both-mode logging writes one row per child with identical timestamps, and History shows calendar-day totals with a configurable day/night split and filter chips.
- Settings is a real screen — units, children, backup/restore and bottle presets — laid out as distinct alternating cards.
- The shell's fade follows the active tab and content no longer runs under the system navigation bar, while the sheets gained an editable date and now say who they log for.
- **Later the same day:** the activity cards were corrected after use — a card now lists today's entries (up to five, and the feed behind the big number is one of them), `See all ›` opens every entry of that activity grouped by day, and any row can be edited in place or long-pressed to delete with an Undo. Verified on the S23 in the same pass; the capture is `docs/timeline/2026-10-14-phase9.1/`, including the three `before/` shots that reported the defects.

### Added

- `docs/timeline/`: the Phase 6, 7, 8 and 9 device captures — 32 screenshots under `docs/timeline/2026-10-14-phase6/` through `-phase9/` (9 + 8 + 6 + 9), with `index.html` sections 07-10 and the matching rows in `docs/timeline/README.md` (`a6407a0`, `11a6d4d`, `9011f7e`, `e8922dc`).
- `docs/timeline/2026-10-14-phase9.1/`: the Phase 9.1 device capture — 10 screenshots plus the three `before/` JPGs that came from the phone as the defect report, with `index.html` section 11 and the matching rows in `docs/timeline/README.md` (`6ebadab`).
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

These were found on the device and are the reason this day has a second entry — the first four as reported after the Phase-9 build, the last two found while fixing and verifying them:

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
