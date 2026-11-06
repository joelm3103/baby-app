# BabyLog visual timeline

Every screenshot taken while verifying the app on a real device, with what changed and why. The point
is to see the app's evolution, and to keep the rejected options - the two accent treatments below were
built, looked at, and thrown away, and that reasoning is worth more than the outcome alone.

Device: Samsung Galaxy S23 Ultra, Android 16, dark mode. Screenshots include the phone's status bar.

| # | Stage | Commit | Shots |
|---|---|---|---|
| 01 | First build that actually ran | `f0d7c18` | [01-first-running-build](2026-10-12-phase5/01-first-running-build.png) |
| 02 | Timer bug and bottom-inset fix verified | `9d10090` | [02-timer-fix](2026-10-12-phase5/02-timer-fix.png) [02-insets-fix](2026-10-12-phase5/02-insets-fix.png) |
| 03 | Brighter accents | `bf4ce7c` | [03-brighter-accents](2026-10-12-phase5/03-brighter-accents.png) |
| 04 | Left accent bar - rejected | `(not committed)` | [04-left-bar-rejected](2026-10-12-phase5/04-left-bar-rejected.png) |
| 05 | Top accent band - rejected | `(not committed)` | [05-top-band-rejected](2026-10-12-phase5/05-top-band-rejected.png) |
| 06 | Tinted sections - accepted | `1a95606` | [06-tinted-sections-top](2026-10-12-phase5/06-tinted-sections-top.png) [06-tinted-sections-bottom](2026-10-12-phase5/06-tinted-sections-bottom.png) |
| 07 | Phase 6 logging sheets | `feat/phase6-logging-sheets` | [01-activity](2026-10-14-phase6/01-activity.png) [02-both-selected](2026-10-14-phase6/02-both-selected.png) [03-bottle-sheet](2026-10-14-phase6/03-bottle-sheet.png) [04-bottle-saved-both](2026-10-14-phase6/04-bottle-saved-both.png) [05-solids-sheet](2026-10-14-phase6/05-solids-sheet.png) [06-food-picker-recent-first](2026-10-14-phase6/06-food-picker-recent-first.png) [07-diaper-sheet](2026-10-14-phase6/07-diaper-sheet.png) [08-activity-bottom](2026-10-14-phase6/08-activity-bottom.png) [09-back-dismisses](2026-10-14-phase6/09-back-dismisses.png) |
| 08 | Settings: units, children, backup | `feat/settings` | [01-settings-top](2026-10-14-phase7/01-settings-top.png) [02-units-oz-display](2026-10-14-phase7/02-units-oz-display.png) [03-children-data](2026-10-14-phase7/03-children-data.png) [04-three-children-no-both](2026-10-14-phase7/04-three-children-no-both.png) [05-both-returns](2026-10-14-phase7/05-both-returns.png) [06-backup-exported](2026-10-14-phase7/06-backup-exported.png) [07-delete-confirm](2026-10-14-phase7/07-delete-confirm.png) [08-import-confirm](2026-10-14-phase7/08-import-confirm.png) |
| 09 | History: totals, night split, filters | `feat/history` | [01-history-1d](2026-10-14-phase8/01-history-1d.png) [02-history-7d](2026-10-14-phase8/02-history-7d.png) [03-filter-diaper-off](2026-10-14-phase8/03-filter-diaper-off.png) [04-history-live-update](2026-10-14-phase8/04-history-live-update.png) [05-night-window](2026-10-14-phase8/05-night-window.png) [06-night-split-moved](2026-10-14-phase8/06-night-split-moved.png) |
| 10 | Daily-use polish | `feat/polish` | [01-rows-with-badges](2026-10-14-phase9/01-rows-with-badges.png) [02-badge-zoom](2026-10-14-phase9/02-badge-zoom.png) [03-bottle-sheet](2026-10-14-phase9/03-bottle-sheet.png) [04-date-backdated](2026-10-14-phase9/04-date-backdated.png) [05-history-filters](2026-10-14-phase9/05-history-filters.png) [06-history-fade](2026-10-14-phase9/06-history-fade.png) [07-settings-cards](2026-10-14-phase9/07-settings-cards.png) [08-settings-presets](2026-10-14-phase9/08-settings-presets.png) [09-reaction-cleared](2026-10-14-phase9/09-reaction-cleared.png) |
| 11 | Card entries, See all, in-place edits | `fix/activity-card-entries` | [before: 01-kai](2026-10-14-phase9.1/before/01-before-kai.jpg) [02-levi](2026-10-14-phase9.1/before/02-before-levi.jpg) [03-both](2026-10-14-phase9.1/before/03-before-both.jpg) · [01-activity-after](2026-10-14-phase9.1/01-activity-after.png) [02-activity-both](2026-10-14-phase9.1/02-activity-both.png) [03-see-all-overlay](2026-10-14-phase9.1/03-see-all-overlay.png) [04-logged-both](2026-10-14-phase9.1/04-logged-both.png) [05-edit-sheet-prefilled](2026-10-14-phase9.1/05-edit-sheet-prefilled.png) [06-after-edit-twin-unchanged](2026-10-14-phase9.1/06-after-edit-twin-unchanged.png) [07-delete-confirm](2026-10-14-phase9.1/07-delete-confirm.png) [08-deleted-undo-snackbar](2026-10-14-phase9.1/08-deleted-undo-snackbar.png) [09-activity-restored](2026-10-14-phase9.1/09-activity-restored.png) [10-cards-scrolled](2026-10-14-phase9.1/10-cards-scrolled.png) |
| 12 | Growth: WHO percentiles, chart, trends tab | `feat/growth-percentiles` | [01-growth-chart-weight](2026-10-15-phase10/01-growth-chart-weight.png) [04-growth-sheet](2026-10-15-phase10/04-growth-sheet.png) [08-growth-card-columns](2026-10-15-phase10/08-growth-card-columns.png) [09-trends-growth-tab](2026-10-15-phase10/09-trends-growth-tab.png) |
| 13 | Health: records, vaccines and doses | `feat/phase11-health` | [01-health-record-both-mode](2026-10-18-phase11/01-health-record-both-mode.png) [02-history-fever-medication-dose](2026-10-18-phase11/02-history-fever-medication-dose.png) [03-health-card-both-mode-dose](2026-10-18-phase11/03-health-card-both-mode-dose.png) [04-medication-dropdown](2026-10-18-phase11/04-medication-dropdown.png) [05-dose-and-hint](2026-10-18-phase11/05-dose-and-hint.png) [06-fever-pill-both-mode](2026-10-18-phase11/06-fever-pill-both-mode.png) [07-custom-medication-blank-focused](2026-10-18-phase11/07-custom-medication-blank-focused.png) [08-medication-menu-scrollable](2026-10-18-phase11/08-medication-menu-scrollable.png) |
| 14 | Trends: per-day charts, and Health as a table | `feat/phase12-trends` | [01-trends-bottle-14d](2026-10-18-phase12/01-trends-bottle-14d.png) [02-trends-bottle-7d](2026-10-18-phase12/02-trends-bottle-7d.png) [03-trends-diaper-30d](2026-10-18-phase12/03-trends-diaper-30d.png) [04-trends-solids-count-axis](2026-10-18-phase12/04-trends-solids-count-axis.png) [05-trends-sleep-30d](2026-10-18-phase12/05-trends-sleep-30d.png) [06-trends-health-table](2026-10-18-phase12/06-trends-health-table.png) [07-trends-growth-percentiles](2026-10-18-phase12/07-trends-growth-percentiles.png) [08-trends-bottle-light](2026-10-18-phase12/08-trends-bottle-light.png) [09-trends-bottle-font130-scrolled](2026-10-18-phase12/09-trends-bottle-font130-scrolled.png) |

| 15 | Two accounts, one shared log (Phase 13) | `0464d78` | [01-launch](2026-10-22-phase13/p13-01-launch.png) [03-home](2026-10-22-phase13/p13-03-home.png) [04-settings](2026-10-22-phase13/p13-04-settings.png) [05-settings-sync](2026-10-22-phase13/p13-05-settings-sync.png) [06-back-to-activity](2026-10-22-phase13/p13-06-back-to-activity.png) [07-bottle-logged](2026-10-22-phase13/p13-07-bottle-logged.png) [08-children](2026-10-22-phase13/p13-08-children.png) [09-children2](2026-10-22-phase13/p13-09-children2.png) [10-before-delete](2026-10-22-phase13/p13-10-before-delete.png) [11-after-delete](2026-10-22-phase13/p13-11-after-delete.png) [12-relaunch-after-delete](2026-10-22-phase13/p13-12-relaunch-after-delete.png) [13-clean-relaunch](2026-10-22-phase13/p13-13-clean-relaunch.png) [14-configured-launch](2026-10-22-phase13/p13-14-configured-launch.png) [15-sync-configured](2026-10-22-phase13/p13-15-sync-configured.png) [16-signin-wrong-creds](2026-10-22-phase13/p13-16-signin-wrong-creds.png) [17-back-to-activity](2026-10-22-phase13/p13-17-back-to-activity.png) [18-ready-for-signin](2026-10-22-phase13/p13-18-ready-for-signin.png) [19-signed-in-after-drain](2026-10-22-phase13/p13-19-signed-in-after-drain.png) [20-three-waiting](2026-10-22-phase13/p13-20-three-waiting.png) [21-offline-pending](2026-10-22-phase13/p13-21-offline-pending.png) [22-one-waiting-offline](2026-10-22-phase13/p13-22-one-waiting-offline.png) [23-sync-now-offline](2026-10-22-phase13/p13-23-sync-now-offline.png) |
| 16 | After the round trip, then a wipe | `(verification sweep)` | [01-activity](2026-10-22-late-sweep/01-activity.png) [02-settings](2026-10-22-late-sweep/02-settings.png) [03-settings-sync](2026-10-22-late-sweep/03-settings-sync.png) [03b-settings-sync](2026-10-22-late-sweep/03b-settings-sync.png) [04-summary](2026-10-22-late-sweep/04-summary.png) [05-trends](2026-10-22-late-sweep/05-trends.png) [06-after-delete](2026-10-22-late-sweep/06-after-delete.png) [07-after-clear](2026-10-22-late-sweep/07-after-clear.png) [08-sync-form](2026-10-22-late-sweep/08-sync-form.png) |
| 17 | Emulator VM, light mode, nothing logged | `(not committed)` | [082222-run](2026-10-23-emulator-vm/082222-run.png) [082315-babylog-home](2026-10-23-emulator-vm/082315-babylog-home.png) |
| 18 | A glyph per activity, tiled behind each card | `786674d` | [glyph-phone-today](2026-10-23-glyph-reach/glyph-phone-today.png) [glyph-phone-trends](2026-10-23-glyph-reach/glyph-phone-trends.png) +7 |
| 19 | Time and temperature preferences (13.1) | `aaf6271` | [04-fahrenheit-inline-validation](2026-10-23-time-temperature-closeout/04-fahrenheit-inline-validation.png) [health-fahrenheit](2026-10-23-time-temperature/health-fahrenheit.png) +18 |
| 20 | Temperature bands, marked by a shape (13.2) | `e2e6d2c` | [01-activity-all-bands](2026-10-24-fever-bands-closeout/01-activity-all-bands.png) [04-trends-flagged-readings](2026-10-24-fever-bands-closeout/04-trends-flagged-readings.png) +3 |
| 21 | Activity reminders, per child (13.4) | `0a420ce` | [13.4-03-reminder-sheet](2026-10-24-reminders-reference/13.4-03-reminder-sheet.png) [13.4-04-remind-at](2026-10-24-reminders-reference/13.4-04-remind-at.png) [shot-reminder-sheet](2026-10-28-reminder-sheet/shot-reminder-sheet.png) +42 |
| 22 | Twelve-hour clock is the default (13.1.1) | `ab00331` | [01-fallback-12h-night-window](2026-10-24-twelve-hour-default-closeout/01-fallback-12h-night-window.png) +3 |
| 23 | Diaper detail: colour and consistency (13.3) | `3994ca0` | [13.3-03-sheet-wet](2026-10-25-diaper-reference/13.3-03-sheet-wet.png) [13.3-04-sheet-dirty-on](2026-10-25-diaper-reference/13.3-04-sheet-dirty-on.png) +16 |
| 24 | Glyph reach, and the Phase 14 polish | `3190f78` | [glyph-reach](2026-10-25-glyph-reach-options/glyph-reach.png) [metric-marks](2026-10-25-glyph-reach-options/metric-marks.png) [stool-silhouettes](2026-10-25-glyph-reach-options/stool-silhouettes.png) +16 |
| 25 | Growth dataset update channel (13.5) | `82aaebe` | [01-home](2026-10-26-phase13.5/01-home.png) [02-growth](2026-10-26-phase13.5/02-growth.png) +18 |
| 26 | Reset to defaults (Phase 15) | `0a4dea0` | [01-data-card](2026-10-26-phase15/01-data-card.png) [06-refused-offline](2026-10-26-phase15/06-refused-offline.png) [05-activity-empty](2026-10-28-phase15-patch/05-activity-empty.png) +26 |
| 27 | The Huckleberry import | `9ee2295` | [01-import-confirm](2026-10-28-huckleberry-import/01-import-confirm.png) [03-after-import](2026-10-28-huckleberry-import/03-after-import.png) [reference-live-home](2026-10-28-huckleberry-import/reference-live-home.png) +18 |
| 28 | First release, `1.0.0` (Phase 16) | `d7d23d2` | [screen](2026-10-29-phase16/screen.png) [shot-a3-notification](2026-10-29-phase16/shot-a3-notification.png) [shot-accept-2026-11](2026-10-29-phase16/shot-accept-2026-11.png) +1 |
| 29 | Five `todo.txt` items (Phase 17) | `f410062` | [sheet](2026-10-30-phase17/sheet.png) [sheet2](2026-10-30-phase17/sheet2.png) |
| 30 | Published food catalogue (Phase 18) | `c55fc3d` | [phone-food-picker](2026-10-31-phase18/phone-food-picker.png) [phase18-device-home](2026-10-31-phase18/phase18-device-home.png) +1 |
| 31 | Settings as a menu of eleven groups (Phase 19) | `75dfd39` | [02-settings-menu](2026-11-02-phase19/02-settings-menu.png) [03-children](2026-11-02-phase19/03-children.png) [02-menu](2026-11-02-phase19-close/02-menu.png) +60 |
| 32 | Four tracking changes (Phase 20) | `9ee233e` | [01-activity](2026-11-02-phase20/01-activity.png) [02-both](2026-11-02-phase20/02-both.png) [04-reminder-sheet](2026-11-02-phase20/04-reminder-sheet.png) +11 |
| 33 | One time dialog, three inputs (Phase 21) | `9901268` | [03-dial](2026-11-02-phase21/03-dial.png) [04-wheel](2026-11-02-phase21/04-wheel.png) [05-text](2026-11-02-phase21/05-text.png) +57 |
| 34 | Filled Soft card surface (Phase 22) | `b52e2a2` | [dark-activity](2026-11-02-phase22/dark-activity.png) [v2-light-summary](2026-11-02-phase22/v2-light-summary.png) [art](2026-11-02-soft-art/art.png) +27 |
| 35 | The 48dp band - rejected | `(not committed)` | [band-activity-dark](2026-11-03-phase22-band/band-activity-dark.png) [empty-cards-dark-left-light-right](2026-11-03-phase22-band/empty-cards-dark-left-light-right.png) +8 |
| 36 | Update check, and delivery by GitHub (Phase 23) | `494e052` | [about-dark](2026-11-04-phase23/about-dark.png) [about-light](2026-11-04-phase23/about-light.png) |
| 37 | The bottle calculator (Phase 24) | `2a6bac4` | [p24-01-sum-preview-dark](2026-11-04-phase24/p24-01-sum-preview-dark.png) [p24b-02-resolved](2026-11-04-phase24/p24b-02-resolved.png) +10 |
| 38 | Left behind, and what was wasted (Phase 26) | `8ad2785` | [02-sheet](2026-11-05-phase26/02-sheet.png) [14-note-row](2026-11-05-phase26/14-note-row.png) +17 |
| 39 | A note on the entry row (Phase 27) | `f243d2e` | [r03-sheet-dark](2026-11-05-phase27/r03-sheet-dark.png) [r05-light-activity](2026-11-05-phase27/r05-light-activity.png) +5 |

Stages 18-39 were added on 2026-11-06 by one migration: the screenshots existed, in six folders outside this
repo and in the repo's own `logs/`, and none of them was here. Every file that travelled was counted against its
source first (the reconciliation is quoted in `CHANGELOG.md`); what did not travel was ad-hoc helper scripts,
pulled APKs, a stray headless-Chrome profile, and one `emulator.log`. **Phase 25 (Ship) has no capture of its
own** — it delivers whichever build is landed — and Phase 24's shots are kept even though its capability was
withdrawn a version later, because a rejected option is the thing this folder exists to remember.


## Entries

### 01 - First build that actually ran (f0d7c18)

**Changed:** Phase 5: activity screen, child switcher, six cards. Muted Nara-sampled palette, cards tinted at 14%.

**Why:** First time the app launched at all. This is the baseline the rest is measured against.

![01-first-running-build.png](2026-10-12-phase5/01-first-running-build.png)

### 02 - Timer bug and bottom-inset fix verified (9d10090)

**Changed:** isRunning no longer true for point events; last card clears the nav bar.

**Why:** Every card showed a live timer after logging, and the bottom bar clipped the last card - both Joel's own finds.

![02-timer-fix.png](2026-10-12-phase5/02-timer-fix.png)
![02-insets-fix.png](2026-10-12-phase5/02-insets-fix.png)

### 03 - Brighter accents (bf4ce7c)

**Changed:** Accents raised to 6 well-separated hues; two of the old six were near-duplicates (8-9 degrees of hue apart).

**Why:** Requested: 'slightly brighter distinct colours would be better'. Solids and Bottle were effectively the same colour.

![03-brighter-accents.png](2026-10-12-phase5/03-brighter-accents.png)

### 04 - Left accent bar - rejected ((not committed))

**Changed:** Card tint removed, replaced by a 4dp full-chroma bar down each card's leading edge.

**Why:** The tint was capping label contrast at 4.58:1. REJECTED: a 4dp bar cannot follow the card's 20dp corner radius, so it looked pasted on with a dark wedge at the corner.

![04-left-bar-rejected.png](2026-10-12-phase5/04-left-bar-rejected.png)

### 05 - Top accent band - rejected ((not committed))

**Changed:** Bar moved to a 6dp full-width band across the card's top edge.

**Why:** Clipped cleanly at full resolution (verified at 3x zoom), but Joel preferred full-section tinting: 'easier to distinguish sections fully with tinted background'.

![05-top-band-rejected.png](2026-10-12-phase5/05-top-band-rejected.png)

### 06 - Tinted sections - accepted ((this commit))

**Changed:** Card tint at 26% (was 14%), label lifted toward white, new textOnCard token for meta text.

**Why:** Joel's choice, done properly: 26% is the strongest tint that keeps every text element at AA. The label had to stop being the raw accent (3.98:1 on its own tint) and become a lighter cousin of it (6.4:1).

![06-tinted-sections-top.png](2026-10-12-phase5/06-tinted-sections-top.png)
![06-tinted-sections-bottom.png](2026-10-12-phase5/06-tinted-sections-bottom.png)

### 07 - Phase 6 logging sheets (`feat/phase6-logging-sheets`, uncommitted)

**Changed:** Full-screen logging sheets with an activity-tinted title bar (X / title / Save) for Bottle,
Diaper and Solids; a seeded 91-food catalogue with custom foods added from the picker; the Phase-5
`quickLog` stub replaced by one `EventRepository.log(childIds, draft)` call; Growth and Health buttons
disabled until their sheets exist.

**Why:** This fixes Joel's third Nara complaint — system back must dismiss a sheet, never exit the app.
Verified on device: back closed the food picker, then the sheet, and the app stayed open. Both mode was
checked in the Room database rather than only on screen — kai and levi each got a row with an identical
timestamp (bottle 13:59:53, solids 14:01:00, diaper 14:03:37 UTC), and the custom food `mango-lassi`
sorted to the top of the picker once used.

**Found and fixed while looking at it:** the Rash toggle drew a `›` chevron, which the design contract
reserves for rows that push a screen. Toggles and select-a-food rows now pass `chevron = false`.

![01-activity](2026-10-14-phase6/01-activity.png)
![02-both-selected](2026-10-14-phase6/02-both-selected.png)
![03-bottle-sheet](2026-10-14-phase6/03-bottle-sheet.png)
![04-bottle-saved-both](2026-10-14-phase6/04-bottle-saved-both.png)
![05-solids-sheet](2026-10-14-phase6/05-solids-sheet.png)
![06-food-picker-recent-first](2026-10-14-phase6/06-food-picker-recent-first.png)
![07-diaper-sheet](2026-10-14-phase6/07-diaper-sheet.png)
![08-activity-bottom](2026-10-14-phase6/08-activity-bottom.png)
![09-back-dismisses](2026-10-14-phase6/09-back-dismisses.png)

### 08 - Settings: units, children, backup (`feat/settings`)

**Changed:** The Settings placeholder became a real screen: appearance (System/Light/Dark), the three
unit families with Metric/Imperial presets, children you can add, edit, archive or permanently delete,
and a versioned JSON backup you can export and import. A semantic `danger` token is the only colour
added, used solely for permanent deletion.

**Why:** Export/backup is the plan's highest-value insurance against a bad migration, and it needed a
home. Verified on device rather than assumed: switching ml to fl oz left the stored bottle row
byte-identical (`amountMl` 90, same `updatedAt`) while the card read oz; adding a third child removed
the **Both** pill and archiving brought it back; the export file (19,364 bytes) carried all 3 children
including the tombstone, 31 events and 92 foods including the custom `mango-lassi`; permanently
deleting Levi removed his row and his 15 events (31 → 16); and importing the file restored all three.

![01-settings-top](2026-10-14-phase7/01-settings-top.png)
![02-units-oz-display](2026-10-14-phase7/02-units-oz-display.png)
![03-children-data](2026-10-14-phase7/03-children-data.png)
![04-three-children-no-both](2026-10-14-phase7/04-three-children-no-both.png)
![05-both-returns](2026-10-14-phase7/05-both-returns.png)
![06-backup-exported](2026-10-14-phase7/06-backup-exported.png)
![07-delete-confirm](2026-10-14-phase7/07-delete-confirm.png)
![08-import-confirm](2026-10-14-phase7/08-import-confirm.png)

### 09 - History: totals, night split, filters (`feat/history`)

**Changed:** The History placeholder became a real screen: the shared header with the child switcher, a
`1d · 7d · 14d` calendar-day range selector, multi-select filter chips, and six total cards. The
day/night diaper split is driven by a configurable window (default 18:00-06:00) added to Settings.

**Why:** The contract already called for a range selector and per-activity totals, and the plan's whole
argument for computing them in Kotlin rather than SQL is the local day boundary. Verified on device
against a hand-count read from the Room database in the device's own timezone: `1d` showed 3 bottles /
330 ml = 11.2 oz and 2 diapers; `7d` showed 7 sessions and 20 diapers; and the per-child line summed to
the combined headline. Logging a bottle on Activity moved History from 7 to 9 sessions WITHOUT a
relaunch, and changing the night window to 06:00-18:00 flipped the diaper split from Day 20 / Night 0 to
Day 0 / Night 20.

![01-history-1d](2026-10-14-phase8/01-history-1d.png)
![02-history-7d](2026-10-14-phase8/02-history-7d.png)
![03-filter-diaper-off](2026-10-14-phase8/03-filter-diaper-off.png)
![04-history-live-update](2026-10-14-phase8/04-history-live-update.png)
![05-night-window](2026-10-14-phase8/05-night-window.png)
![06-night-split-moved](2026-10-14-phase8/06-night-split-moved.png)

### 10 - Daily-use polish (`feat/polish`)

**Changed:** The pass that came out of actually using the app. Recent rows now show a child badge and the
VOLUME instead of a brand that was identical on every bottle; a shared `ChildBadge` fixes the letter that
sat low in its circle; the bottle amount is free text plus editable presets; the sheets say who they log
for and gained a Date row; the solids reaction can be cleared; History filter chips take their activity's
colour; Settings is one alternating card per section; and the top fade now follows the ACTIVE
destination, with Settings' bottom inset no longer scrolling away.

**Why:** Each item was reported after real use, and two of them were bugs rather than tastes. Opening a
sheet and pressing Save silently rewrote the amount — 90 ml renders as "3" fl oz and re-parsing stored
89 — and Settings' insets were applied inside its scroll, so content ran under the transparent system
navigation bar. Both are fixed and verified: the untouched prefill now stores 90, and the bottom of
Settings is background and the nav bar's own scrim, never content.

![01-rows-with-badges](2026-10-14-phase9/01-rows-with-badges.png)
![02-badge-zoom](2026-10-14-phase9/02-badge-zoom.png)
![03-bottle-sheet](2026-10-14-phase9/03-bottle-sheet.png)
![04-date-backdated](2026-10-14-phase9/04-date-backdated.png)
![05-history-filters](2026-10-14-phase9/05-history-filters.png)
![06-history-fade](2026-10-14-phase9/06-history-fade.png)
![07-settings-cards](2026-10-14-phase9/07-settings-cards.png)
![08-settings-presets](2026-10-14-phase9/08-settings-presets.png)
![09-reaction-cleared](2026-10-14-phase9/09-reaction-cleared.png)

### 11 - Card entries, See all, in-place edits (`fix/activity-card-entries`)

**Changed:** The activity cards became the app's entry surface. A card now lists TODAY's entries for
its activity - newest first, up to five, and including the feed behind the big number - and `See all ›`
opens a full-screen list of every entry of that activity for the current Kai/Levi/Both selection, grouped
by `Today` / `Yesterday` / date. Every row is tappable to edit in place (the activity's sheet opens
pre-filled and saves back onto that same row) and long-pressable to delete, with an Undo snackbar. In
Both mode the big number now carries the child's badge and the absolute time, the child initial is shown
on rows only in Both mode, and rows are tighter (5dp of padding, a 16dp badge, an 8dp gap above the
block).

**Why:** Reported after real use, four separate ways, and one of them was misread by both us and Joel.
`See all ›` was a no-op (`onSeeAll = {}`) that only rendered when a card happened to hold three events;
a card could never show more than two rows because `buildCards` kept the last three of each type and the
headline consumed the newest of them, with no filtering to today at all - so a feed from yesterday looked
like one from an hour ago. The Both card looked *wrong* but was right: Levi's 14:14 feed was the headline
on Levi's card and a row on the Both card, so the two lists were offset by one and its 13:09 feed looked
missing. The evidence for all four is the three `before/` shots above, taken from the Phase-9 build.

One fix came out of the device pass itself: the Undo snackbar used `SnackbarDuration.Short` (4s) and
expired between a UI dump and the tap, so the tap landed on the Diaper card underneath; it is now `Long`
(10s), which is Material's own guidance for a snackbar carrying an action.

![01-activity-after](2026-10-14-phase9.1/01-activity-after.png)
![02-activity-both](2026-10-14-phase9.1/02-activity-both.png)
![03-see-all-overlay](2026-10-14-phase9.1/03-see-all-overlay.png)
![04-logged-both](2026-10-14-phase9.1/04-logged-both.png)
![05-edit-sheet-prefilled](2026-10-14-phase9.1/05-edit-sheet-prefilled.png)
![06-after-edit-twin-unchanged](2026-10-14-phase9.1/06-after-edit-twin-unchanged.png)
![07-delete-confirm](2026-10-14-phase9.1/07-delete-confirm.png)
![08-deleted-undo-snackbar](2026-10-14-phase9.1/08-deleted-undo-snackbar.png)
![09-activity-restored](2026-10-14-phase9.1/09-activity-restored.png)
![10-cards-scrolled](2026-10-14-phase9.1/10-cards-scrolled.png)

### 12 - Growth (`feat/growth-percentiles`)

**Changed:** Growth arrived: WHO's LMS reference ships inside the APK, a sheet logs weight, length and head
circumference, and a percentile is read back with its age and the dataset that produced it. The chart lives
in **Trends**, as one tab of a per-activity tab row, and each child's measurements are joined into their own
line over the reference curves — the join into a pre-term point dashed, because the birth point sits at
adjusted day 0, which is the due date.

**Why:** The heaviest feature in the plan, and the one most likely to be confidently wrong: a percentile
depends on the dataset, the child's sex, and whether the age is adjusted. The bundled parameters were
checked against WHO's **own** published tables rather than the mirror they came from, and every failure mode
found on the phone was a silent one — a button that did nothing, a label that clamped an off-chart value
into "1st percentile", and a highlight box that showed one twin's number as if it spoke for both.

![01-growth-chart-weight](2026-10-15-phase10/01-growth-chart-weight.png)
![02-length-empty](2026-10-15-phase10/02-length-empty.png)
![03-activity-after-back](2026-10-15-phase10/03-activity-after-back.png)
![04-growth-sheet](2026-10-15-phase10/04-growth-sheet.png)
![05-chart-with-birth-row](2026-10-15-phase10/05-chart-with-birth-row.png)
![06-chart-trajectories](2026-10-15-phase10/06-chart-trajectories.png)
![07-growth-see-all](2026-10-15-phase10/07-growth-see-all.png)
![08-growth-card-columns](2026-10-15-phase10/08-growth-card-columns.png)
![09-trends-growth-tab](2026-10-15-phase10/09-trends-growth-tab.png)
![10-trends-other-tab](2026-10-15-phase10/10-trends-other-tab.png)
![11-trends-growth-bottom](2026-10-15-phase10/11-trends-growth-bottom.png)

### 13 - Health: records, vaccines and doses (`feat/phase11-health`)

**Changed:** Health became a working activity: a temperature, a medication picked from a bundled list with
its dose typed beside it, and vaccines on their own form behind their own button. Both mode shows one
column per child — that child's newest temperature, their newest medication and their newest vaccine, the
latest of each kind — and a reading at or above 38.0 °C carries a filled **Fever** pill on the card, in
the entry list and in History. The dose is real data, so this is the app's first schema change (v1 → v2,
additive), which is what lets History name the medicines without carrying the amounts.

**Why:** It was the last card that could not write anything, and the two records it holds have nothing to
do with each other — a vaccine is entered from a red book weeks later, a fever is what a parent reaches
for at 3am. The picker offers names and never a dose, because an infant dose depends on weight and age and
a number this app proposed would be plausible, confident and possibly wrong.

**And what the phone caught:** the first pass marked a fever by tinting the number, and it could not be
seen — measurably legible at 5.20:1 and still invisible as a signal, because a pastel red on a magenta
card is not a shape. Worse, the card was holding only one "record" slot, so a medication logged after a
fever took the fever off the card entirely; only History still showed it. Both fixed, plus a medication
menu that filled the whole screen and a "type another name" field that kept the previous selection.

![01-health-record-both-mode](2026-10-18-phase11/01-health-record-both-mode.png)
![02-history-fever-medication-dose](2026-10-18-phase11/02-history-fever-medication-dose.png)
![03-health-card-both-mode-dose](2026-10-18-phase11/03-health-card-both-mode-dose.png)
![04-medication-dropdown](2026-10-18-phase11/04-medication-dropdown.png)
![05-dose-and-hint](2026-10-18-phase11/05-dose-and-hint.png)
![06-fever-pill-both-mode](2026-10-18-phase11/06-fever-pill-both-mode.png)
![07-custom-medication-blank-focused](2026-10-18-phase11/07-custom-medication-blank-focused.png)
![08-medication-menu-scrollable](2026-10-18-phase11/08-medication-menu-scrollable.png)

### 14 - Trends: per-day charts, and Health as a table (`feat/phase12-trends`)

**Changed:** Five of the six activity tabs now chart a per-day number — bottle volume, solids, diapers,
sleep (all on a 7/14/30-day window, default 14) and Growth's percentiles, which keep their own pane because
they are plotted against age and not against the last N days. Health is a dated list of fever readings with
a count of fever DAYS, deliberately not a chart: a temperature is a reading, not a daily amount, and
averaging two readings is a number nobody should act on. Both children are on one chart — solid for the
first, dashed for the second, because two shades of one hue were not enough to tell the twins apart and the
design contract does not allow a second hue.

**Why:** Every chart in the app needed one home, and a chart is something a parent goes to look at rather
than something to trip over while logging a feed. It was built last for the reason the plan gave: it needs
real data before the design can be judged.

**And what the phone caught, after the tests were green:** the average day printed as `0` beside a non-zero
total (13 nappies over 30 days, 2 solids over 30 days) — a contradiction a parent would not trust, now
`0.4` and `0.1`, with `< 0.1` where the average genuinely rounds away; a count axis ticked in fractions
(`0.2`, `0.4` meals); and two lines that were only shades of each other. A fourth defect never reached the
phone — a unit test found that a label appended to the newest day sat one day from its neighbour on a
14-day window and would have overlapped.

**Verified against the device's own database:** every figure on screen was recomputed from the phone's
`events` table — Kai 27.7 oz over 3 of 14 days with a best day of 24.7 oz on 14 Oct, 13 diapers on 2 of 30
days, 13m 5s of sleep, 2 temperatures of which 2 were fevers on 1 day.

![01-trends-bottle-14d](2026-10-18-phase12/01-trends-bottle-14d.png)
![02-trends-bottle-7d](2026-10-18-phase12/02-trends-bottle-7d.png)
![03-trends-diaper-30d](2026-10-18-phase12/03-trends-diaper-30d.png)
![04-trends-solids-count-axis](2026-10-18-phase12/04-trends-solids-count-axis.png)
![05-trends-sleep-30d](2026-10-18-phase12/05-trends-sleep-30d.png)
![06-trends-health-table](2026-10-18-phase12/06-trends-health-table.png)
![07-trends-growth-percentiles](2026-10-18-phase12/07-trends-growth-percentiles.png)
![08-trends-bottle-light](2026-10-18-phase12/08-trends-bottle-light.png)
![09-trends-bottle-font130-scrolled](2026-10-18-phase12/09-trends-bottle-font130-scrolled.png)

### 15 - Two accounts, one shared log (`0464d78`)

**Changed:** The app learned to sync. An `outbox` table is the only queue and stores intent rather than a
payload; a drain sends it, an inbound listener merges the other phone's rows by `updatedAt` with a deletion
winning a tie, and a permanent delete travels as an id-and-timestamp tombstone. Settings gained a Sync card
with both sign-in routes. The first pass ran on a build with **no** Firebase configuration at all, which is
exactly what a fresh clone builds.

**Why:** Phase 13's acceptance was never a green build — it was two phones: log on A and see it on B, and
three airplane-mode writes on A arriving exactly once, with no duplicates.

**What the three passes showed** (recorded in `PLAN.md`, these shots are that record):

- **Unconfigured build** (01-13): it launches, the Sync card says the log stays on this phone and offers no
  button that cannot work, logging still writes, **every** write path queues — including children, which
  had been the one path that never did — and a permanent delete queues `DELETE` intents while dropping the
  dead `UPSERT`s.
- **Configured build** (14-23): sign-in both ways, a wrong password answered in a sentence, the first-sign-in
  seed drained, a live write acked in under five seconds, and three offline writes queued as **exactly
  three** intents, then delivered as five events under five distinct ids.
- **Two findings left open, deliberately** and visible here rather than tidied away: **`Sync now` offline is
  silent** (23) — the write suspends and the button looks inert while a stale *"Sent 1 change(s)."* sits
  beside a live *"1 change waiting to sync."*; nothing is lost, it drains on reconnect. And the **seed waits
  for a drain** (19, 20) — after a first sign-in the two `CHILD` rows sat queued until something tapped
  `Sync now`.

The A↔B half is not in this folder: it was two devices (`<handset>` and `emulator-5554`) and its evidence
is the row ids in `PLAN.md` plus the database states under `evidence/databases/`.

![p13-01-launch](2026-10-22-phase13/p13-01-launch.png)
![p13-03-home](2026-10-22-phase13/p13-03-home.png)
![p13-04-settings](2026-10-22-phase13/p13-04-settings.png)
![p13-05-settings-sync](2026-10-22-phase13/p13-05-settings-sync.png)
![p13-06-back-to-activity](2026-10-22-phase13/p13-06-back-to-activity.png)
![p13-07-bottle-logged](2026-10-22-phase13/p13-07-bottle-logged.png)
![p13-08-children](2026-10-22-phase13/p13-08-children.png)
![p13-09-children2](2026-10-22-phase13/p13-09-children2.png)
![p13-10-before-delete](2026-10-22-phase13/p13-10-before-delete.png)
![p13-11-after-delete](2026-10-22-phase13/p13-11-after-delete.png)
![p13-12-relaunch-after-delete](2026-10-22-phase13/p13-12-relaunch-after-delete.png)
![p13-13-clean-relaunch](2026-10-22-phase13/p13-13-clean-relaunch.png)
![p13-14-configured-launch](2026-10-22-phase13/p13-14-configured-launch.png)
![p13-15-sync-configured](2026-10-22-phase13/p13-15-sync-configured.png)
![p13-16-signin-wrong-creds](2026-10-22-phase13/p13-16-signin-wrong-creds.png)
![p13-17-back-to-activity](2026-10-22-phase13/p13-17-back-to-activity.png)
![p13-18-ready-for-signin](2026-10-22-phase13/p13-18-ready-for-signin.png)
![p13-19-signed-in-after-drain](2026-10-22-phase13/p13-19-signed-in-after-drain.png)
![p13-20-three-waiting](2026-10-22-phase13/p13-20-three-waiting.png)
![p13-21-offline-pending](2026-10-22-phase13/p13-21-offline-pending.png)
![p13-22-one-waiting-offline](2026-10-22-phase13/p13-22-one-waiting-offline.png)
![p13-23-sync-now-offline](2026-10-22-phase13/p13-23-sync-now-offline.png)

### 16 - After the round trip, then a wipe (`(verification sweep)`)

**Changed:** Nothing in the app. This is a sweep of every screen on the phone at the end of 22 Oct, once
the two-device round trip had closed Phase 13 — and then the same phone wiped with `pm clear` and launched
again.

**Why:** The last look at each screen holding **real synced data** rather than test rows, and then proof
that a wiped install comes back as a working empty app: the two children re-seeded, every card showing its
empty state, nothing half-drawn. Also the last record of the signed-in Sync card and of the signed-out form
side by side.

**What it shows:** five 60 ml feeds for the first child across 09:48-09:50 — the airplane-mode trio plus the
earlier two, which is the exactly-once result seen from the other side; Trends reading `Bottle feed · 14
days` with `Days with no records count as zero.`; and after the wipe, `+ Log feed for both` over empty cards
with both children present.

![01-activity](2026-10-22-late-sweep/01-activity.png)
![02-settings](2026-10-22-late-sweep/02-settings.png)
![03-settings-sync](2026-10-22-late-sweep/03-settings-sync.png)
![03b-settings-sync](2026-10-22-late-sweep/03b-settings-sync.png)
![04-summary](2026-10-22-late-sweep/04-summary.png)
![05-trends](2026-10-22-late-sweep/05-trends.png)
![06-after-delete](2026-10-22-late-sweep/06-after-delete.png)
![07-after-clear](2026-10-22-late-sweep/07-after-clear.png)
![08-sync-form](2026-10-22-late-sweep/08-sync-form.png)

The uiautomator dumps behind this sweep are in `evidence/uiautomator/2026-10-22-late-sweep/`, and the
database states in `evidence/databases/`.

### 17 - Emulator VM, light mode, nothing logged (`(not committed)`)

**Changed:** Nothing. The same `0.9.0` build launched in an emulator VM on 23 Oct: the launch screen, then
Activity in **light** mode with the two default children and no records at all.

**Why:** It is the only capture of the whole shell in light mode with an empty log, which is the state a
fresh install or a second device starts in. Phase 12 checked light mode on one chart pane; this shows the
cards, the switcher and the bottom bar. Kept as the emulator's own evidence: per `AGENTS.md` the emulator is
a second sync client, and layout and contrast judgements stay on the phone.

![082222-run](2026-10-23-emulator-vm/082222-run.png)
![082315-babylog-home](2026-10-23-emulator-vm/082315-babylog-home.png)

## Entries, stages 18-39 — one line each

These are the stages recorded after 2026-10-23, added in the 2026-11-06 migration. One `Changed:` line and one
`Why:` line each, taken from the `CHANGELOG.md` entry for the build that produced them; the long form is there.
The images are two or three per stage and the rest are in the folder beside them.

### 18 - A glyph per activity (`786674d`)

**Changed:** Six outline glyphs — bottle, bowl-and-spoon, nappy, ruler, thermometer, moon — tiled tone-on-tone
behind each card and drawn solid inside the chips that control it, at 26dp on a 64dp pitch.

**Why:** Activity was identifiable by hue alone. The pattern is the card's own hue pushed one measured step and
never white: white on a 26% tint measures dE 3 at 4.2:1 and is invisible in the light theme. The first diaper
glyph read as a letter `M` repeated down the card and was redrawn as a waistband above a rounded pad.

![glyph-phone-today.png](2026-10-23-glyph-reach/glyph-phone-today.png)
![glyph-phone-trends.png](2026-10-23-glyph-reach/glyph-phone-trends.png)

### 19 - Time and temperature preferences (`aaf6271`)

**Changed:** Settings gains independent `12-hour`/`24-hour` and `°C`/`°F` choices. Storage is untouched — epoch
milliseconds and Celsius, with `FEVER_C` still the fever boundary — and only display and input convert.

**Why:** Three different owners of the clock: `0.13.0`'s era is 24-hour on this phone, and the family reads a
thermometer in Fahrenheit. The Fahrenheit path needed its own shot because `100.4 °F` has to raise the same fever
pill that `38.0 °C` does, on the same card.

![04-fahrenheit-inline-validation.png](2026-10-23-time-temperature-closeout/04-fahrenheit-inline-validation.png)
![health-fahrenheit.png](2026-10-23-time-temperature/health-fahrenheit.png)

### 20 - Temperature bands, marked by a shape (`e2e6d2c`)

**Changed:** A reading is classified LOW / NORMAL / MILD_FEVER / FEVER from the value rounded to its displayed
decimal, and marked by a shape — a filled pill for FEVER, an outline for the other two — rather than a hue.

**Why:** The first attempt tinted the number and it could not be seen: measurably legible at 5.20:1 and invisible
as a signal on a magenta card. And `37.99` prints as `38.0`, so classifying the stored value would let the word
contradict the number printed beside it.

![01-activity-all-bands.png](2026-10-24-fever-bands-closeout/01-activity-all-bands.png)
![04-trends-flagged-readings.png](2026-10-24-fever-bands-closeout/04-trends-flagged-readings.png)

### 21 - Activity reminders, per child (`0a420ce`)

**Changed:** Bottle feed, Solids, Diaper and Health can each remind one child N after that child's last log, with
a night window and day-of-week chips. The card carries a chip per child with the time it will ring, and tapping
that chip edits that child's rule.

**Why:** Set from the activity's own card, never from Settings, because a Settings section put the setting a
screen away from the thing it is about and a rule shared by both children hid which twin was overdue. Alarms are
exact after `dumpsys alarm` showed a 37-minute window on the inexact ones.

![13.4-03-reminder-sheet.png](2026-10-24-reminders-reference/13.4-03-reminder-sheet.png)
![shot-reminder-sheet.png](2026-10-28-reminder-sheet/shot-reminder-sheet.png)

### 22 - Twelve-hour clock is the default (`ab00331`)

**Changed:** A fresh install starts on the 12-hour clock; a saved choice still wins.

**Why:** The default is the one the family has to change, and the fallback path had to be shown working rather
than assumed — the night window's labels are the place a clock format is most likely to lie.

![01-fallback-12h-night-window.png](2026-10-24-twelve-hour-default-closeout/01-fallback-12h-night-window.png)

### 23 - Diaper detail: colour and consistency (`3994ca0`)

**Changed:** Wet / Dirty / Mixed became **Wet** and **Dirty** toggles that can both be true, plus an optional colour
(six literal swatches) and consistency (seven silhouettes).

**Why:** A nappy answers two questions and can answer both; the colour is what the parent SAW, so it is a literal
colour and not an activity hue. `MIXED` has been in the column since schema v1, so the two-part answer cost no
migration — and a quick pill must never write it, because one tap cannot answer two questions.

![13.3-03-sheet-wet.png](2026-10-25-diaper-reference/13.3-03-sheet-wet.png)
![13.3-04-sheet-dirty-on.png](2026-10-25-diaper-reference/13.3-04-sheet-dirty-on.png)

### 24 - Glyph reach, and the Phase 14 polish (`3190f78`)

**Changed:** The glyph reaches sheet title bars, entry rows and Both-mode column heads. Growth's three metric
chips carry their own marks — a scale, an up arrow off a floor, a head with a curl — and the range chips carry
none and read `D · 1W · 2W`. Two consistency silhouettes were redrawn, and `Sync now` offline now answers.

**Why:** A metric chip with the activity's glyph says nothing about which metric is selected, and a range is a
span rather than an activity. `Mucousy` read as a letter `T` at 16dp and `Runny` merged into `Mushy`: a path that
is right at 48dp says nothing about 16dp.

![glyph-reach.png](2026-10-25-glyph-reach-options/glyph-reach.png)
![metric-marks.png](2026-10-25-glyph-reach-options/metric-marks.png)
![stool-silhouettes.png](2026-10-25-glyph-reach-options/stool-silhouettes.png)

### 25 - Growth dataset update channel (`82aaebe`)

**Changed:** A newer WHO dataset can arrive from Firestore, checksum-verified and cached, with the bundled set as
the floor on every failure. The pass fixed the Settings identity line that kept reading `v2026.10` beside
`Updated to v2026.11` until the screen was left and re-entered.

**Why:** A percentile is only as good as its reference, and correcting one should not need an APK — but a channel
that can change a number a parent acts on needs a way back, which is why `withdrawn: true` exists.

![01-home.png](2026-10-26-phase13.5/01-home.png)
![02-growth.png](2026-10-26-phase13.5/02-growth.png)

### 26 - Reset to defaults (`0a4dea0`)

**Changed:** Settings → Data gains *Reset local data* and *Delete all server data*, each with its own
confirmation. Every removed row leaves as a tombstone queued in the same transaction that deletes it.

**Why:** The one destructive path in the app needed a way back that the other phone treats as an instruction.
The pass found a reset that partially undid itself — the inbound listener applied a snapshot taken before the
deletes landed — so it now cancels **and joins** that listener around the wipe, and the empty state is one card,
one line and one button.

![01-data-card.png](2026-10-26-phase15/01-data-card.png)
![06-refused-offline.png](2026-10-26-phase15/06-refused-offline.png)
![05-activity-empty.png](2026-10-28-phase15-patch/05-activity-empty.png)

### 27 - The Huckleberry import (`9ee2295`)

**Changed:** A converter turns Huckleberry's per-child CSV export into a BabyLog backup. Both children's history
now runs from birth to today — 221 events, grown from the 10-day window the raw export covered by transcribing
four screenshots by hand.

**Why:** The app this one replaces held the real log. Starting from an empty database would have thrown it away,
and `reference-live-home.png` is the live Huckleberry home screen the mapping was read from.

![01-import-confirm.png](2026-10-28-huckleberry-import/01-import-confirm.png)
![reference-live-home.png](2026-10-28-huckleberry-import/reference-live-home.png)

### 28 - First release, `1.0.0` (`d7d23d2`)

**Changed:** A release keystore the family owns, delivery by GitHub Release (App Distribution retired), the
certificate's SHA-1 on the Firebase Android app, and `1.0.0` installed **in place** over the previous build.

**Why:** Android installs an update only when the signing certificate matches, so the key is the app's identity
rather than a build detail. `versionCode 26` reached the phone over the air and came back with all 219 live
events — an update, not a reinstall.

![shot-a3-notification.png](2026-10-29-phase16/shot-a3-notification.png)
![screen.png](2026-10-29-phase16/screen.png)

### 29 - Five `todo.txt` items (`f410062`)

**Changed:** Trends gains a child switcher and the ranges `1d · 1W · 2W · 1M · 1Y · All`, an About card naming
the build, per-activity hiding, and haptics from one helper — including the bottom tab bar, which was the one
surface without one.

**Why:** Items from `todo.txt`. `1Y` counts weeks and `All` counts months from the first event, so a year is not
a year of days and the axis caption has to name its own bucket.

![sheet.png](2026-10-30-phase17/sheet.png)
![sheet2.png](2026-10-30-phase17/sheet2.png)

### 30 - Published food catalogue (`c55fc3d`)

**Changed:** `reference/food_catalogue` is one owner-written document that overrides the bundled asset;
`foods_pending/{id}` carries phone-typed additions, `foods_proposals/{id}` records a rename suggestion, and
schema v4 adds local `localName` and `hidden`.

**Why:** A food's name should be correctable without a new APK. Only the owner publishes, because a rename is an
opinion — so the phone proposes rather than changes, and the picker says so.

![phone-food-picker.png](2026-10-31-phase18/phone-food-picker.png)
![phase18-device-home.png](2026-10-31-phase18/phase18-device-home.png)

### 31 - Settings as a menu of eleven groups (`75dfd39`)

**Changed:** Settings became an index of eleven groups, each menu row carrying a one-line summary of its group's
current value, and back means "up one level" before it means "leave Settings".

**Why:** The answer to "which units am I on" should not need a tap. The phone found two defects in the new rows
themselves: the Children row printed `2 childs`, and a summary long enough to wrap made its row taller than every
other row in the menu.

![02-settings-menu.png](2026-11-02-phase19/02-settings-menu.png)
![03-children.png](2026-11-02-phase19/03-children.png)

### 32 - Four tracking changes (`9ee233e`)

**Changed:** One-tap bottle presets on the card, the child's name instead of an initial on Both-mode cards and
rows, a reminder sheet that sets a rule without asking whether to notify, and posted reminders cleared when the
feed is logged or the rule goes off.

**Why:** Logging a feed was three taps for something done a dozen times a day. The `Notify` toggle was a silent
trap: the sheet opened with it off, `Set` wrote a rule that looked saved and armed nothing, and tapping the
disabled button just closed the sheet — which is what made it read as "saving does nothing".

![01-activity.png](2026-11-02-phase20/01-activity.png)
![04-reminder-sheet.png](2026-11-02-phase20/04-reminder-sheet.png)

### 33 - One time dialog, three inputs (`9901268`)

**Changed:** One dialog, three ways in — Material3's dial, our own wheels, and the two-field keyboard — with the
switch inside the dialog rather than a Settings row, and nothing persisted.

**Why:** Material3 ships no wheel, and hosting the platform `TimePicker` would have put OS chrome inside a BabyLog
sheet. The keyboard stays because it is the only input that can type an exact minute without scrolling.

![03-dial.png](2026-11-02-phase21/03-dial.png)
![04-wheel.png](2026-11-02-phase21/04-wheel.png)
![05-text.png](2026-11-02-phase21/05-text.png)

### 34 - Filled Soft card surface (`b52e2a2`)

**Changed:** Every tinted card carries a vertical gradient fill, its activity's filled illustration anchored to
the right edge as a sticker, and scattered shapes behind the content — nine filled drawables, `ART_STEP` 0.14, and
`PatternPaletteTest` rewritten rather than deleted.

**Why:** The stroke-glyph watermark read as texture rather than life. Two placement defects were found by counting
the phone's own pixels: shapes drawn inside the opaque sticker and behind the quick-log pill row were drawn, and
invisible. `art.png` is the illustration sheet the nine drawables were cut from.

![dark-activity.png](2026-11-02-phase22/dark-activity.png)
![v2-light-summary.png](2026-11-02-phase22/v2-light-summary.png)
![art.png](2026-11-02-soft-art/art.png)

### 35 - The 48dp band - rejected (`(not committed)`)

**Changed:** A 48dp band was reserved above a card's first control, so that a card with nothing logged would show
its sticker instead of drawing it under the full-width log button.

**Why:** Built, installed, measured on the phone and **rejected on sight** in favour of the previous, more compact
cards. The empty-card limitation is therefore accepted knowingly: the sticker's top and its clamp following the
card's first control was written down as the fix that would cost no height, and not built.

![band-activity-dark.png](2026-11-03-phase22-band/band-activity-dark.png)
![empty-cards-dark-left-light-right.png](2026-11-03-phase22-band/empty-cards-dark-left-light-right.png)

### 36 - Update check, and delivery by GitHub (`494e052`)

**Changed:** The app asks GitHub for the newest release when it opens, at most once a day. Nothing is downloaded
or installed, and Settings → About states the answer and how old it is.

**Why:** A sideloaded build had no store to announce a newer one. It is the only request the app makes that is not
Firebase or a food catalogue read, which is why the About promise was rewritten to name it instead of implying
the phone talks only to Firebase.

![about-dark.png](2026-11-04-phase23/about-dark.png)
![about-light.png](2026-11-04-phase23/about-light.png)

### 37 - The bottle calculator (`2a6bac4`)

**Changed:** The bottle sheet draws its own pad — `1-9`, the point, a backspace and `÷ × − +` — and `=` puts the
answer in the amount field, which is then what is stored. No software keyboard opens over the amount.

**Why:** Arithmetic on the way in is a capability the app did not have. It was **withdrawn one version later**:
a parent could type `6-2` and the sheet stored neither operand, so nothing could report what was wasted. These
shots are the record of the capability rather than of the behaviour, which is why they stay.

![p24-01-sum-preview-dark.png](2026-11-04-phase24/p24-01-sum-preview-dark.png)
![p24b-02-resolved.png](2026-11-04-phase24/p24b-02-resolved.png)

### 38 - Left behind, and what was wasted (`8ad2785`)

**Changed:** A bottle keeps two numbers — what was drunk and what was left behind — and the sheet says
`Consumed: 5 oz (6 − 1)` before saving. Summary's Bottle card and Trends' Bottle tab both report the wastage from
one implementation. Schema v5 adds a nullable `leftBehindMl` and back-fills nothing.

**Why:** The calculator threw both operands away; a leftover that is recorded is a fact the app can report on.
NULL is not zero — "nobody measured a leftover" and "nothing was left" are different claims, and a one-tap preset
cannot record a leftover because one tap cannot answer two questions.

![02-sheet.png](2026-11-05-phase26/02-sheet.png)
![14-note-row.png](2026-11-05-phase26/14-note-row.png)

### 39 - A note on the entry row (`f243d2e`)

**Changed:** Every entry row now shows the note the parent wrote, after the record — `6 oz · Spit up` — in the
surface's meta colour, sharing one `Text` with the label so the pair ellipsise together.

**Why:** Notes had been stored since the first sheet and drawn nowhere. They stay a separate colour because they
are different claims: one colour for both would stop the amount being the loudest thing on its own row, and
whitespace is not a note, so no row ends in a dangling `·`.

![r03-sheet-dark.png](2026-11-05-phase27/r03-sheet-dark.png)
![r05-light-activity.png](2026-11-05-phase27/r05-light-activity.png)

## Adding an entry

1. Capture with `scripts/capture-timeline.ps1 -Label <stage-name>` - it installs the current APK,
   launches, screenshots the top and each scroll position into `docs/timeline/<date>-<label>/`.
   `-Purpose marketing` writes to `docs/marketing/` instead; that set is committed and this one is not.
   `-DryRun` prints the directory it would use and needs no phone.
2. Add an entry to the table above with the commit hash, what changed, and **why**.
3. Full-resolution PNGs are kept deliberately: this is a design record, and downscaled shots hide the
   things that were actually wrong (a 4dp bar's corner wedge is invisible at 50%).
4. uiautomator dumps and pulled databases go to `evidence/uiautomator/<date>-<label>/` and
   `evidence/databases/<date>-<label>/`, never into the dated folder itself.

**The PNGs are gitignored; this README is tracked.** A phone can regenerate any screenshot, and nothing can
regenerate the paragraph explaining which two treatments were rejected first — which is why the file-level
ignore keeps the text and drops the images. `index.html` beside it is the rendered view and is ignored too.
