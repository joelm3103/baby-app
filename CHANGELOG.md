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

- Bottle, diaper and solids are logged with real values through full-screen sheets, instead of the timestamp-only stub.
- Both-mode logging writes one row per child with identical timestamps, confirmed in the Room database, and the bundled 91-food catalogue accepts custom foods ordered by recent use.
- Settings is now a real screen: appearance, the three unit families, children you can add, edit, archive or permanently delete, and a JSON backup you can export and restore.
- System back closes a sheet instead of leaving the app, and the plan gained a Settings phase plus an Update delivery phase.

### Added

- `PLAN.md` gains **Phase 7 — Settings** (appearance, units, children, export) and **Phase 13 — Update delivery** (release signing plus delivery without a cable). The latter precedes the ship step because a release-signed APK is what gets delivered.
- `MaintenanceRepository` (`data/repo/MaintenanceRepository.kt`): read-all including tombstones, replace-everything import in one transaction, archive a child, permanently delete a child with their events and outbox rows.
- `Backup.kt` (`data/backup/Backup.kt`): a versioned (`schema: 1`), indented JSON envelope of children, every event including tombstones, and the foods table including custom foods, with `UnsupportedBackupException` for a newer schema.
- `SettingsScreen` and `SettingsViewModel` (`ui/settings/`): one scrolling screen for appearance, units, children and data, with Storage Access Framework `CreateDocument`/`OpenDocument` for export and import — no permission and no FileProvider.
- A semantic `danger` token (`#FF7A7A` dark, `#B3261E` light), used only for permanent deletion; measured at 6.85:1 and 6.19:1 on `background`, with `onAccent` text on the fill at 6.85:1 and 6.54:1.
- `ChildRepository.add`, and the DAO surface it needs: `allIncludingDeleted`, `deleteAll`, `deleteByChild`, `softDeleteByChild`, `idsForChild`, `hardDelete`, `maxSortOrder`, `totalCount`, plus outbox `deleteByEntityIds` and `deleteEverything`.
- 15 tests: `BackupTest` (4), `MaintenanceRepositoryTest` (6), `ChildSeedOnceTest` (2) and three more in `ChildSelectionTest`; the suite is 136.

- `ActivitySheet`, a full-screen logging frame with an activity-tinted title bar (`X` / title / `Save`), hairline `InputRow`s, and a `BackHandler` so system back dismisses the sheet rather than leaving the app (`app/src/main/java/app/babylog/ui/home/ActivitySheet.kt`).
- Bottle, Diaper and Solids logging sheets (`ui/home/BottleSheet.kt`, `ui/home/DiaperSheet.kt`, `ui/home/SolidsSheet.kt`) and their shared controls (`ui/home/SheetControls.kt`).
- `FoodPicker` (`ui/home/FoodPicker.kt`): catalogue search, multi-select, recent-first ordering, and adding a food the catalogue does not have.
- A curated 91-food catalogue shipped as a bundled asset, `app/src/main/assets/foods/foods.json`, with a stable `id` slug per food and no honey (under-12-months safety).
- `FoodRepository` (`data/repo/FoodRepository.kt`) and `FoodDao.observeCatalogue()`, seeding the asset insert-if-absent and ordering the picker by `lastUsedAt` so logging a food moves it to the top.
- `FoodCatalogue` / `FoodCatalogueParser` (`data/local/FoodCatalogue.kt`) and `foodSlug` / `uniqueFoodSlug` (`domain/FoodSlug.kt`).
- `fromMillilitres` (`domain/Units.kt`), so the bottle amount stepper converts once on save rather than on every press.
- `timeOfDayOn` (`domain/Formatting.kt`), which keeps the local day when a sheet's time is edited.
- 18 unit tests: `FoodSlugTest` (8), `FoodRepositoryTest` (8, against a real Room database under Robolectric), and one each in `FormattingTest` and `UnitsTest`; the suite is 121 tests.

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

### Removed

- `HomeViewModel.quickLog`, the Phase-5 stub that logged a bare timestamp for Bottle, Solids and Diaper.
- The Growth and Health action buttons' valueless stub writes; the buttons are now disabled with a phase note until those sheets exist (`ui/home/ActivityCard.kt`).

### Fixed

- The Rash toggle and the food rows in the picker drew a `›` chevron, which the design contract reserves for rows that push a screen. `InputRow` now takes `chevron` (default `onClick != null`) and the in-place controls pass `chevron = false` (`ui/home/SheetControls.kt`, `ui/home/FoodPicker.kt`). Found on device, not by the build.
- `ChildSelection.resolve` returned **all** children for a stale single-child selection, which would have shown a combined view that the switcher no longer offers once there are three. It now falls back to the first child in sort order.
- The skill's `design-tokens.md` still asserted "cards are NOT tinted" and described the rejected accent bar, contradicting the shipped 26% tint in `Tokens.kt` and `ActivityCard`.
