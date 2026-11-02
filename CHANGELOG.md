# BabyLog changelog

All notable changes to BabyLog are recorded here. Format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/); this repo has no release
cadence, so **sections are dated rather than versioned** - newest first, one section
per day the work landed, instead of everything accumulating in one unreleased block.

> Two conventions, so an entry is never read as more than it is:
>
> - An entry stays under the date the work **landed**, even where the artifact was
>   revised later; any later measurement or rewrite is named inside the entry.
> - An entry recorded after the fact **says so**, so a retrospective note cannot be
>   mistaken for a same-day record.
>
> Versions: each dated section lists the versions that landed that day under `### Versions`, directly
> after `**In short:**`. The headings stay dated, so the ladder is read from those lists rather than from
> version headers. Until `1.0.0`, a `0.MINOR` bump is a new capability and a `0.PATCH` bump is fixes and
> polish only, with `versionCode` sequential per rung. Nothing is tagged: tags are kept for releases.

## [2026-11-02]

**In short:**

- **Settings became a category menu with a submenu per group, and back means "up one level" before it means "leave Settings"** — a tested value (`SettingsNav.back()`), not an `if` in the handler. No setting moved between groups and no control changed, only how they are reached.
- **Four tracking changes landed: one-tap bottle presets on the card, the child's name instead of an initial on Both-mode cards and rows, a reminder sheet that sets and removes without asking whether to notify, and posted reminders cleared when the feed is logged or the rule goes off.**
- **The Phase 19 pass found two defects in its own new rows, both fixed and re-verified on the phone:** the Children row said `2 childs`, and the growth-reference value wrapped to two lines and made its row taller than every other row in the menu.
- **The Phase 20 pass wrote nothing to the log, so the one-tap feed was left to the owner — who tested it and confirmed it works.** The pass could not make that write itself: a test feed is a record that never happened in the children's health log.
- **The one dialog a time is edited in can now switch between the clock face, the wheels and the keyboard** — a control in the dialog rather than a setting, so every logging sheet and the reminder sheet moved together.

### Versions

- `1.2.1` / versionCode 29 — **Phase 19, Settings submenus**. A PATCH rung: no capability and no stored data changed, only the way every setting is reached. Ship moves to Phase 20.
- `1.3.0` / versionCode 30 — **Phase 20, the tracking changes**. A MINOR rung: a preset that logs a feed in one tap is a capability the app did not have. Ship moves to Phase 21.
- `1.4.0` / versionCode 31 — **Phase 21, the time picker**. A MINOR rung: two input modes the app did not have, and a control that reaches them. Ship moves to Phase 23, because the Filled Soft redesign holds Phase 22.

### Added

- **`SettingsSection`** names each of the eleven settings groups once, and its declaration order is the menu order.
  - A group's `title` is the menu row, the submenu heading and what the back arrow returns to. A second label beside it is how the two come to disagree.
  - `Why:` the shape it replaces was one long list of eleven section cards, where reaching bottle presets meant scrolling past six unrelated groups.
- **`SettingsNav`** is the two-level value that decides what a back press does.
  - It replaces rather than nests, so opening a second group from a submenu lands there instead of two backs away from the menu.
  - `back()` answers null at the menu, which the shell reads as "leave Settings".
- **A category menu replaces the single scroll of eleven cards.** Each row carries a one-line summary of its group's current value, so which units or theme the app is on is legible without opening anything.
- **Bottle presets are one-tap quick-log pills on the Bottle feed card**, the path the Diaper pills already use: a tap writes `EventDraft(BOTTLE, now, amountMl)` for the current selection, so Both mode writes one row per child with an identical timestamp. The full-width `+ Log feed for X` button stays for a custom amount, a brand, a note or a feed that happened earlier. An empty preset list draws no pills at all.
  - Labeled in the display volume unit but stored canonically in ml, so the same preset reads `4 oz` or `120 ml` without the stored value moving.
  - `FlowRow`, because Settings lets a parent add as many sizes as they like and a fixed row would clip the fifth one off the card.
- **A third time input, and a switch that reaches all three.** `TimeOfDayDialog` gains the clock dial (Material3's `TimePicker`) and a rolling wheel built here in Compose, beside the two-field keyboard it already had.
  - `Why:` Material3 ships no wheel. The alternative was hosting `android.widget.TimePicker` in spinner mode, which matches the reference exactly and would have been the one control in a sheet wearing the OS's chrome instead of the app's.
  - The wheel is three snapping `LazyColumn`s — hours, minutes, and AM/PM in 12-hour mode — so it follows the app's tokens, both themes and the reading format.
  - The switch is IN THE DIALOG, not a Settings preference: the owner's choice, and nothing is persisted, so the dialog opens on the dial every time rather than on last week's habit.
- **The pure rule for when a posted reminder has stopped being true** (`shouldClearPostedNotification`) and **a `ReminderNotifier` seam** with `cancel`/`postedAt` beside the existing `ReminderStore`/`ReminderAnchors`, so the decision is testable with no device and no notification shade.

### Changed

- **Settings renders one view at a time** — the menu, or one group under a back arrow.
  - Every control, validation, confirmation, gating rule and destructive dialog is the original code, moved rather than rewritten. Nothing was dropped and nothing moved between groups.
  - The old alternating `surfaceRaised` shading is gone: it existed to separate consecutive cards down one long scroll, and one card per view has no boundary for a shade to fall between.
- **`AppScaffold` owns which submenu is open**, because the shell has to answer two questions about it: what a back press does, and which list the top scrim follows.
  - Settings therefore keeps two scroll states. The menu returns to its own row, and each submenu opens at its own top.
  - Leaving Settings resets to the menu, so a submenu is never restored without its parent on screen.
- **`counted()` takes its plural explicitly** instead of appending an `s`, and **`datasetSummary()`** shortens the growth identity line to name and version for the menu row.
- **A Both-mode card names the child rather than showing their initial**, on the headline that owns the big number and on every entry row. The badge stays where a name does not fit: the switcher, the reminder chips, the Growth chart and the fever table.
  - `Why:` `K` over a column of amounts is a code the parent has to hold in their head, and the name was already in the state.
  - The Growth and Health Both-mode columns lose the badge too, because that column is already headed by the name — the same fact twice, at the cost of the width.
- **The reminder sheet saves an ENABLED rule, whatever the stored one said.** There is no `Notify` toggle to leave off, so setting a reminder is turning it on.
- **The reminder sheet removes rules itself**, through the same bottom destructive row every other editing sheet offers, drawn only when the child on screen actually has one and labelled `Remove notification` rather than `Delete this entry`.
- **`ActivitySheet` takes a `deleteLabel`**, because a button reading `Delete this entry` in a sheet that never created an entry is a false label on the one control that cannot be undone.
- **`Reminders.refresh()` clears the shade as well as the alarms**, which is the one path every write, rule change, launch and boot already goes through.
- **The dialog's one button names where it LEADS**, not where it is: its content description reads `Switch to wheels`, `Switch to keyboard` or `Switch to clock face`, so a tap is an instruction rather than a state.
- **Three authored marks** — `ic_time_mode_dial`, `ic_time_mode_wheel`, `ic_time_mode_text` — because `material-icons-core` carries no dial, wheel or keyboard glyph, and a mode hidden behind a generic chevron is a mode nobody finds.
- **The AM/PM column reports the half of the day, not an hour**, and the dialog does the shift on the number that owns it. The column first routed back through `unWheelHour(hourIndex, ...)`, which made two columns each compute a value from the other's possibly-stale half.
- **A wheel column reports only a move it was not created with** (`drop(1)`). The first emission is the position the column was built at, which the parent already knows — and on the hour column it could arrive before the list had measured, so it said `00` to a wheel showing midday and the dialog rebuilt its state from a number nobody chose.

### Fixed

- **The Children settings row printed `2 childs`.** It reads `2 children`.
- **The growth-reference row printed the full identity line**, which wrapped to two lines and, being left-aligned among right-aligned values, made that row taller than every other row in the Settings menu.
  - The row now shows `WHO 2006 · v2026.10`; the submenu still shows the update date, because the version is what identifies the tables and the date is not.
- **A reminder could be saved silently switched off.** The sheet opened with `Notify` off, so `Set` wrote a rule that looked saved on the card, armed nothing and said nothing about why; tapping the disabled `Set` then just closed the sheet, which is what made it read as "saving does nothing". Recorded against Phase 17 on 2026-10-30 and fixed here.
- **A posted reminder notification outlived the thing it was about.** Nothing cleared it — not logging the feed, not opening the app, not removing the rule — so a night's reminders were still in the shade in the morning, each still saying `3h 5m since the last feed`. `ReminderNotifications` could post and nothing else; it now also cancels, and `refresh()` decides when.

### Verified on the phone — `<handset>` (SM-S918U1), `1.2.1`, 2026-11-02

| # | Check | Result |
|---|---|---|
| 1 | An update, not a reinstall | `versionCode=29 versionName=1.2.1`; `firstInstallTime` unchanged at 2026-10-29 13:16, `lastUpdateTime` 2026-11-02 09:49 ✅ |
| 2 | The pass ran against this exact artifact | the installed `base.apk` pulled back is byte-identical to the built APK, both SHA-256 `4666322e…3670` ✅ |
| 3 | Signed by the family's key | `CN=BabyLog, OU=Family, O=BabyLog, C=TT`, SHA-1 `0766ee2f…5f4f` ✅ |
| 4 | The debug APK is refused | `adb install -r app-debug.apk` answered `INSTALL_FAILED_UPDATE_INCOMPATIBLE`, so the release APK was installed — as it must be, and never by uninstalling ✅ |
| 5 | Foreground checked before any input | `topResumedActivity=app.babylog/.MainActivity` ✅ |
| 6 | The menu lists all eleven groups | `Appearance, Units, Day & night, Bottle presets, Medication presets, Children, Activities, Data, Sync, Growth reference, About`, each with a summary ✅ |
| 7 | No bottom bar in Settings | the menu dump held no `Activity` / `Summary` / `Trends` node; all three came back on leaving ✅ |
| 8 | Back is "up one level" | from **all eleven** submenus, back returned to the menu; a second back left Settings for Activity ✅ |
| 9 | Each submenu names itself | every dump held the back arrow (`content-desc=Back to Settings`) then its own title as the first two labels ✅ |
| 10 | The summary agrees with the control behind it | `System` is the filled chip in Appearance; neither `Metric` nor `Imperial` is filled while the row reads `Custom` ✅ |
| 11 | A long submenu clears the system bar | the nav-bar inset is 135 px (`[0,2181][1080,2316]`); the last element after scrolling, `Add child`, ended at y=2111 — 70 px clear ✅ |
| 12 | The top fade follows the open submenu | behind the clock and status icons the background is **opaque** on a scrolled submenu ✅ |
| 13 | Nothing was written | the menu summaries after the pass read exactly as before it: `System`, `Custom`, `3 presets`, `None`, `2 children`, `2 hidden`, `Signed in` ✅ |
| 14 | `logcat -b crash -d` | **0** `FATAL EXCEPTION` across the install, all eleven submenus and the back navigation ✅ |

**Found on the phone, fixed and re-verified on it:** the two `### Fixed` entries above. The order matters to
read the history honestly — both were in the new code and neither existed in a commit, so the first commit of
this phase carries the corrected code rather than a broken row followed by a repair.

**A note on the sweep, not a defect:** the first sweep reported the `Day & night` row missing because the dump escapes
`&` as `&amp;`, so it was opened by coordinate instead. The row and its summary (`6:00 PM – 4:00 AM`) were
present all along.

### Verified on the phone — `<handset>` (SM-S918U1), `1.3.0`, 2026-11-02

The release APK was installed in place over `1.2.1` and the pass was **read-only with one exception**: the
child switcher was moved to Both and back to Kai, which is a stored display preference. Nothing was written to
the log — tapping a preset is the one check left to the owner, because this log is the children's health record
and a test feed would be a record that never happened.

| # | Check | Result |
|---|---|---|
| 1 | An update, not a reinstall | `versionCode=30 versionName=1.3.0`; `firstInstallTime` unchanged at 2026-10-29 13:16, `lastUpdateTime` 2026-11-02 10:54 ✅ |
| 2 | The pass ran against this exact artifact | the installed `base.apk` pulled back is byte-identical to the built APK, both SHA-256 `145b5109…34f2` ✅ |
| 3 | Foreground before any input | `topResumedActivity=app.babylog/.MainActivity` ✅ |
| 4 | The preset pills are on the card | `5 oz`, `5.5 oz` and `6 oz` render as tappable pills above `+ Log feed for Kai`, from the three presets stored in Settings — labelled in the selected unit (fl oz) ✅ |
| 5 | The pills do not replace the sheet | `+ Log feed for Kai` is still full width beneath them, and reads `for both` in Both mode ✅ |
| 6 | A Both-mode card names the child | the headline beside the big number reads `Kai`, not `K`, with the time beside it ✅ |
| 7 | Both-mode rows name the child | `Kai 6 oz 5:39 AM` and `Levi 4 oz 5:05 AM` — two rows that used to be told apart by one initial ✅ |
| 8 | The reminder chips keep their badge | the Bottle card's two chips still carry `K` and `L`, as chosen: a chip has no room for a name ✅ |
| 9 | The reminder sheet has no `Notify` toggle | the sheet lists `For`, `Mode`, `Time`, `Skip night` and `Days` — no toggle anywhere ✅ |
| 10 | Saving is an enable, not a question | the Bottle sheet's button reads `Update` for a child who has a rule and `Set` for one who does not; neither offers a switch to leave off ✅ |
| 11 | Removal is offered only when there is a rule | the Bottle sheet shows `Remove notification` at the bottom; the Diaper sheet, with no rule for either child, does not show it at all ✅ |
| 12 | Dismissing writes nothing | system back closed both sheets and left Activity in front, with the log and the rules unchanged ✅ |
| 13 | `logcat -b crash -d` | **0** `FATAL EXCEPTION` across the install, both child selections, two sheets and the back presses ✅ |

**Confirmed by the owner the same day, from the app on the phone:** the two checks the pass deliberately left
to them — the one-tap feed and the reminder behaviour around it — both work. The pass itself could not make
that write, because a test feed is a record that never happened in this log, and the notification clearing
needs an alarm to have actually fired; its decision is pinned by `PostedNotificationTest` meanwhile, and the
posting path is unchanged.

### Verified on the phone — `<handset>` (SM-S918U1), `1.4.0`, 2026-11-02

Every check below was read off the screen; **nothing was written to the log**. Two stored values were changed to
reach the states under test and both were restored: the app's Time format (12 → 24 → 12-hour) and the system
font scale (1.0 → 1.3 → 1.0).

| # | Check | Result |
|---|---|---|
| 1 | The dialog opens on the clock face | first screen after tapping a Time row is the dial, and the switch offers `Switch to wheels` ✅ |
| 2 | The switch names where it leads | the same button reads `Switch to wheels`, then `Switch to keyboard`, then `Switch to clock face` — the destination, never the current state ✅ |
| 3 | The dial is COMPLETE, not clipped | all twelve hour labels present and inside the dialog, x 244–922 of a 1080-wide screen — the recorded worry that "the dial does not fit a phone dialog at small widths" is not borne out at this width ✅ |
| 4 | The dial survives a large font | at font scale **1.3** all twelve labels still render, x 241–903, with the hour/minute/AM-PM fields and both buttons intact ✅ |
| 5 | The wheels render as three columns | hours (11, 12), minutes (12, 13, 14) and AM/PM, each a snapping column, pitch 124 px against 67 px of text at 1.3 — no overlap, no clipping ✅ |
| 6 | The wheel opens on the dial's value | dial 12:13 PM → wheel band on hour `12`, minute `13`, half `PM` ✅ |
| 7 | Scrolling the wheel changes the value | one row up the minute column → band `14`, column `13, 14, 15` ✅ |
| 8 | The value survives a mode change | wheel `12:14` → keyboard fields `12` and `14` — the hand-off is exact, both directions ✅ |
| 9 | The half-day column does not move the hand | swiping AM/PM moved the band from `PM` to `AM` while the hour column stayed on `12` ✅ |
| 10 | 12-hour shows the AM/PM column and the inner ring | dial has `Select AM or PM`; wheel has three columns ✅ |
| 11 | 24-hour drops both | dial lists **0–23 hours** and has no AM/PM field; wheel drops to two columns and shows `11, 12, 13` ✅ |
| 12 | The keyboard is still the original picker | the two fields and the AM/PM toggle, unchanged ✅ |
| 13 | Dismissing writes nothing | Cancel and system back left the sheet and the log untouched; the sheet's Time row still read the event's own time ✅ |
| 14 | `logcat -b crash -d` | **0** `FATAL EXCEPTION` across both font scales, both clock formats and roughly twenty mode switches ✅ |

**Found and fixed before the phone saw it:** the test written for the AM/PM shift failed on its own first
assertion, which claimed switching halves and back was identity. It is not — that is the point of the control —
and the test now pins what is actually true instead: the half flips, the hand does not move, and each shift is
idempotent.

### Tests

- **611 → 645: thirty-four cases in seven new classes, none of them about layout.** Phase 19 added `SettingsNavTest` (5) and `SettingsSummaryTest` (4); Phase 20 added `PostedNotificationTest` (5), `ReminderNotificationIdTest` (4), `BottleQuickLogTest` (3) and `ReminderSaveTest` (3).
- `SettingsNavTest` (5) pins what a build cannot see: that the menu is the root and its back answer is null,
  that every submenu's back lands on the menu, that opening from a submenu replaces rather than nests, and that
  the eleven menu entries exist in their order with unique non-blank names.
  - `Why:` the two failure modes are a Settings that traps the parent and a group that loses its only way in,
    and neither shows up as a compile error or a failed screenshot.
- `SettingsSummaryTest` (4) pins the two defects the phone found, so `2 childs` and a two-line menu value
  cannot come back: the plural for children, the default appended `s`, the shortened dataset line, and that a
  line in an unrecognised shape still renders rather than vanishing.
- `PostedNotificationTest` (5) pins when a posted reminder has stopped being true: a disabled rule, a log newer
  than the post, a log older than it, a log at the same instant, no post at all, and an enabled rule with no log.
  - `Why:` a notification that clears itself on the wrong write and one that never clears look identical until
    somebody is awake at the wrong time of night.
- `BottleQuickLogTest` (3) pins what one tap writes — the activity, the amount, the moment, and nothing invented
  — and that a nought, a negative and an over-cap amount are refused rather than written.
- `ReminderSaveTest` (3) pins that saving brings a stored disabled rule back on without resetting the interval
  the parent chose, which is the defect this phase fixes.
- `WheelTimePickerTest` (10) pins the wheel's arithmetic and the mode cycle. A twelve-hour wheel shows `12`
  twice — midnight and noon — so every conversion is checked in both directions at exactly the two hours that
  break naive arithmetic, plus the invariants that make the AM/PM column safe: flipping the half changes the
  half and never moves the hand, and doing it twice is doing it once.
- `ReminderNotificationIdTest` (4) pins that the id under which a notification is posted, replaced and cancelled
  is unique per (activity, child). It was `type.ordinal * 31 + childId.hashCode()`, which gave two children whose
  ids share a `String.hashCode()` (`Aa` / `BB`) exactly the same id — harmless while an id only decided which
  notification replaced which, and destructive once a refresh could CANCEL by it.

## [2026-11-01]

**In short:**

- **The `1.2.0` food picker was installed and exercised on the real phone.** The local catalogue and rename disclosure rendered; no food or log entry was changed.
- **The Phase 18 checks passed:** 611 JVM tests and debug/release builds. The Firestore console publication and second-phone read remain outstanding.

### Versions

- **No rung moved.** `1.2.0` / versionCode 28 is Phase 18's code; this date records its device pass, not another version.

### Verification

- `:app:testDebugUnitTest` — 611 tests, 0 failures, 0 errors, 0 skipped across 58 XML files under `app/build/test-results/testDebugUnitTest/`.
- `:app:assembleDebug` and `:app:assembleRelease` succeeded. The release APK verified as `CN=BabyLog`, SHA-1 `0766ee2f85c2ed2c8e2fc5b6b05b908dd4545f4f`.
- Installed the release-signed APK (not debug) on `<handset>`: versionCode 28 / versionName 1.2.0. Activity and the Solids food picker rendered; Salmon's action menu exposed Rename/Hide, and the rename dialog disclosed that a catalogue rename is local and proposes a change to the owner.
- System back dismissed the dialog and sheet layers without leaving the app. `logcat -b crash -d` contained 0 `FATAL EXCEPTION`.
- This pass did not check live Firestore rules deployment or a second-phone read. An earlier `logs/phone-food-picker.png` capture shows `Console Test Food` in the picker, but is not a fresh server or cross-device check. This phone pass did not change a food or log entry.

## [2026-10-31]

**In short:**

- **`1.2.0` / versionCode 28 adds a shared, owner-published food catalogue.** The bundled list stays the offline floor; phone-typed additions travel through `foods_pending`, and rename proposals wait for the owner.

### Versions

- `1.2.0` / versionCode 28 — **Phase 18, published food catalogue**. It has not yet been delivered to either phone.

### Added

- **`reference/food_catalogue`** lets the owner correct or add a stable-id food name without a new APK. It overrides the bundled asset; clients can read but cannot write it.
- **`foods_pending/{id}`** carries parent-typed additions through the outbox to both phones. **`foods_proposals/{id}`** records a local rename suggestion for the owner rather than changing the shared name.
- The picker now supports local hide/show, renaming, and deleting pending additions. Unknown food ids say `Unknown food` instead of printing a slug; hidden or removed custom-food rows stay available to name older meals.
- Schema v4 adds local `localName` and `hidden` fields, with a v3 migration and exported schema.

### Changed

- **Reset includes custom foods.** It withdraws pending additions and clears local curation; the bundled list returns on the next seed.
- **ADR-0002 records why the published catalogue replaced the first proposed row-sync design.**

### Fixed

- **A remote solids entry without a local food row no longer renders its identifier as if it were a name.**

### Verification

- `:app:testDebugUnitTest` — 611 tests, 0 failures, 0 errors, 0 skipped across 58 XML files.
- `:app:assembleDebug` and `:app:assembleRelease` succeeded; release APK verification reported the existing `CN=BabyLog` certificate.
- Device install, Firebase console publication/rules, and the second-phone read were not performed: no Android device is attached, and console publication remains an owner action.

## [2026-10-30]

**In short:**

- **`1.1.0` reached the phone and Phase 17 is closed.** Installed **in place over `1.0.0`** — same signing key,
  `versionCode 27` — so the log survived an update rather than a reinstall: 219 live events, 2 children, no
  crash.
- **The five `todo.txt` items were seen working**: the Trends switcher and its five chips, `1Y` counting weeks
  and `All` counting months from the first event, the About card naming the build it is, an activity hidden and
  restored, and the hide-with-reminder prompt answered both ways.
- **Two pre-existing defects were found and recorded rather than fixed.** The reminder sheet opens with
  `Notify` **off** even when notifications are granted — so `Set` saves a rule that is silently disabled and
  the card shows nothing — and tapping that disabled `Set` dismisses the sheet instead of doing nothing.

### Versions

- **No rung moved.** `1.1.0` / versionCode 27 landed on 2026-10-29 with the code; this section is its device
  pass, and a pass does not open a second rung for one phase's work.

### Fixed

- **The bottom tab bar was the one surface with no haptic.** Every chip, card button, toggle, long press and
  sheet Save buzzed; switching between Activity, Summary and Trends — the most repeated action in the app —
  did not. `NavItem` now taps the same helper, and only when the destination actually changes: re-tapping the
  tab already on screen is not a switch and stays silent.
- **`trendAxisUnit` named the day above a chart of weeks or months.** The first 1.1.0 build on the phone read
  `Bottle feed · the last 52 weeks · oz per day` — the headline under it already said `oz / week`, so one
  caption disagreed with the chart and with the line above it. Found by reading the screen, not by a test;
  now `trendAxisUnit(metric, units, bucket)` and pinned by three assertions in `TrendsTest`.

### Verified on the phone — `<handset>` (SM-S918U1), `1.1.0`, 2026-10-30

| # | Check | Result |
|---|---|---|
| 1 | An update, not a reinstall | `versionCode=27 versionName=1.1.0`; `firstInstallTime` unchanged at 2026-10-29 13:16, `lastUpdateTime` 2026-10-30 12:59 ✅ |
| 2 | Signed by the family's key | `CN=BabyLog, OU=Family, O=BabyLog, C=TT`, SHA-1 `0766ee2f…5f4f` ✅ |
| 3 | The log came through the update | the app's own Export at 13:28: 221 rows, **219 live** (BOTTLE 106, DIAPER 80, GROWTH 19, SOLIDS 14), 91 foods ✅ |
| 4 | The Trends switcher changes the data | `Kai`/`Levi`/`Both` in the header, caption follows it, totals differ: `273 oz` against `274.6 oz` ✅ |
| 5 | Five range chips on one row | `1W · 2W · 1M · 1Y · All`, each carrying its spoken form ✅ |
| 6 | `1Y` counts weeks | `the last 52 weeks · oz per week`, `Weeks with no records count as zero.`, `4 of 52 weeks logged`, `Best week 127 oz · 12 Oct` ✅ |
| 7 | `All` counts months from the first event | `all time · oz per month`, `Months with no records count as zero.`, `1 of 11 months logged`, `Best month 274.6 oz · Oct` ✅ |
| 8 | Per-day ranges unchanged | 2W reads `the last 14 days` / `Days with no records count as zero.` / `Best day 30.5 oz · 17 Oct` ✅ |
| 9 | Hiding and restoring an activity | `Solids` gone from Activity after the toggle, back after untoggling ✅ |
| 10 | The hide-with-reminder prompt, both answers | with a Health rule armed (`Every 3h (4:31 PM)`): **Keep it visible** changed nothing; **Hide and cancel the reminder** removed the card and left no future babylog alarm ✅ |
| 11 | The About card | `Version 1.1.0 (build 27) · release build`, the credits, the repository button, the WHO 2006 line, the no-analytics line ✅ |
| 12 | `logcat -b crash -d` | **0** `FATAL EXCEPTION` across two installs, the sheet work, three hide/restore cycles and the export ✅ |
| 13 | Haptics | **Judged by the owner**: the feel reads right on buttons, chips, toggles, long presses and Save. The tab bar was the one surface missing one, and now taps the same helper ✅ |
| 14 | System back | Back from Settings returned to Activity, app still foreground; back with the Bottle feed sheet open dismissed it and left Activity in front ✅ |

**Found on the phone and not fixed:** the reminder sheet opens with `Notify` **off** even after the permission
is granted, so `Set` writes a **disabled** rule — no chip, no alarm, and nothing on screen to say why; the same
rule works when `Notify` is turned on. And tapping the disabled `Set` (enabled only once a child is picked)
falls through to the sheet's dismiss handler, so Save appears to do nothing except close the sheet. Both
pre-date this phase, and the second is what made the first look like "saving does nothing". A disabled rule is
also indistinguishable from no rule — the `⋮` still reads `Set reminder` — which is what concealed it.

### Tests

- **579 → 584: five cases, all about rules rather than screens.** Three for the rule a hide must follow: `reminderKeysToCancelOnHide` is now a pure
  function rather than a loop inside the ViewModel, and the tests pin that hiding an activity cancels that
  activity's rules **for every child** (a twin left armed would be unreachable, because the card is the only
  place a rule can be edited), that a hide with no rules cancels nothing, and that an already-disabled key is
  still removed — "set but off" and "never set" look the same to a parent, so a leftover key would resurrect
  the old interval. Two more came out of the review before merge: an `All` range whose FIRST event is dated in
  the future draws one month instead of throwing, and every range draws at least one bucket whether the log is
  empty, absent or future-dated.

## [2026-10-29]

**In short:**

- **Three defects the plan had already written down are closed, and not one of them was found by a test.** A
  growth dataset a phone had accepted could be superseded but never recalled; the never-logged reminder said
  `Time to log diaper`; and a volume series with nothing recorded drew an axis reading `0.1 / 0 / 0`.
- **A dataset can now be withdrawn.** An optional `withdrawn: true` on `reference/growth_manifest` makes the
  phone drop its cached copy, clear the two preferences that remembered it, reset the in-memory reference and
  report `Dataset withdrawn — back to the bundled reference` — where the only rescue used to be clearing the
  app's data and signing in again.
- **One rung, no phase number.** `0.19.0` / versionCode 25: the first build here that closes defects rather
  than opening a phase, and it says so in `AGENTS.md`'s ladder.
- **The device pass ended fully verified**: the reminder wording on the phone, both check rows, and then the
  withdrawal itself against the real server — the bumped copy accepted, the retraction taken, the cache file and
  both preferences gone, and the dataset not re-adopted on relaunch.
- **Then `1.0.0` shipped, later the same day.** Phase 16 closed: a release keystore the family owns, delivery
  by a manual Firebase App Distribution upload, the certificate's SHA-1 on the Firebase Android app, and the
  first tag in the repository. The phone took `versionCode 26` over the air — a 14 675 571-byte APK whose
  SHA-256 matches the one that was built — and came back with all 219 live events.

### Versions

- `0.19.0` / `versionCode 25`. A `0.MINOR` rung because withdrawal is a new capability in the update channel,
  not a fix to an existing one — and because a build goes to the phone either way. No phase number: it closes
  three recorded defects, and a phase number whose scope is "the things we already wrote down" would make the
  Status table mean nothing. The next rung is still the first release, `1.0.0`, in Phase 16.
- `1.1.0` / `versionCode 27` — **Phase 17, five `todo.txt` items**: a child selector and `1Y`/`All` ranges
  on Trends, an About card, per-activity hiding, and haptics. A `MINOR` rung because it adds capability, and
  the first rung ABOVE the release: after `1.0.0` the ladder cannot go back to a `0.x` name, which is why
  this is not the `0.20.0` it was first written as.
- `1.0.0` / `versionCode 26` — **Phase 16, update delivery, and the first release.** A `MINOR` rung by the
  letter of the rule (`1.0.0` is where the ladder was headed) and the end of the ladder as planned: the
  release build is signed by a key the family owns, delivered by hand through Firebase App Distribution, and
  tagged `v1.0.0` once a phone reported the version. Phase 17 is "use it for a week", not a build.

### Added

- **`withdrawn` on the growth manifest, and `GrowthDatasetUpdateResult.Withdrawn`.** `GrowthDatasetManifest`
  gained `withdrawn: Boolean = false`, read with a default so every manifest published before today keeps
  parsing. `checkForUpdates()` handles it **before** `isNewer()`: a retraction names the version the phone
  already holds, which is the one case a version comparison refuses. `withdraw()` deletes the cache file,
  removes `growth.dataset.version` and `growth.dataset.sha256`, resets the in-memory `cachedReference` and
  re-emits `identityLine`; `markChecked()` runs either way so a retraction that does not apply cannot loop.
  It recalls **only the version it names** — a notice for a dataset this phone never accepted is a no-op, never
  an instruction to revert something newer.
- **`GrowthDatasetUpdateResult.message()`, beside the sealed interface.** `GrowthViewModel` and
  `SettingsViewModel` both ran their own `when` over the same four results; they now call one function, because
  a duplicated result→sentence mapping is how two screens stop agreeing about the same event.
- **`ticksWithDistinctLabels()` in the shared axis maths.** Gridlines whose formatted labels repeat are
  dropped rather than relabelled. **Applied by Trends only** — Growth's percentile axis still draws `yTicks`
  through `unit.format` with no such filter, which is recorded in `PLAN.md` as a gap rather than silently
  left to look fixed.
- **`docs/firebase-setup.md`: how to withdraw a dataset by hand** — the field, the three things worth knowing
  (it names one version, it only lands on a check, and how to prove the path end to end), and the note that
  the old two-step rescue is history.
- **`scripts/create-release-keystore.ps1`** — creates the release keystore outside the repo. It prompts for the
  password twice with `Read-Host -AsSecureString`, passes it to `keytool` through `-storepass:env` so it never
  enters argv, a shell history or a transcript, writes the `keystore.properties` Gradle reads beside the `.jks`,
  writes the fingerprints to a text file, and **refuses to overwrite an existing key** — a second key is a
  second app identity and would orphan both phones.
- **A conditional `release` signing config** in `app/build.gradle.kts`, plus `keystore.properties` in
  `.gitignore`. The config is created only when the file exists (repo root, else `%USERPROFILE%\keystores`), so
  a clone without the key still runs `:app:testDebugUnitTest` and `:app:assembleDebug`, and
  `:app:assembleRelease` emits an unsigned APK instead of failing. Measured: with the file moved aside,
  `apksigner verify` answers `DOES NOT VERIFY`.
- **`docs/release-signing.md`** — the runbook: why the certificate (not the package or the version) is the
  app's identity, where the key and its metadata live and why neither is in git or in Syncthing, the SHA-1 and
  SHA-256, the build/verify commands, the one-time phone move, the App Distribution steps, and the rule that a
  tag is cut on a delivered build.
- **`docs/DECISIONS.md`, starting with ADR-0001** — release-key custody and the deliberately manual delivery
  channel: no Gradle plugin, no service-account key, no `firebase-tools`, and what that costs. The repo's first
  genuinely hard-to-reverse decision, written down where a future reader will look for it. Amended after
  publication: the GitHub release for `v1.0.0` also carries the APK (`baby-log-v1.0.0.apk`, 14 675 571 bytes,
  `sha256:4dac7cfb…43bca`), as a copy for a channel that cannot serve rather than a second release process.

- **`1Y` and `All` on Trends, as buckets rather than a denser line.** `TrendRange` gained a `bucket`
  (`DAY`/`WEEK`/`MONTH`); `1Y` draws the last 52 weekly buckets ending with this week's Monday, and `All`
  draws one bucket per calendar month **starting at the first event** — `EventDao.earliestStartedAt` answers
  when the log begins, because a window from epoch would draw months of zeros before either twin was born and
  average them into every headline number. `trendHeadlineUnit` names the bucket (`per week`), and the axis
  thins out past a month instead of drawing ten labels into 294dp.
- **The child switcher on Trends**, the only tab that had none. The selection was already global and
  `TrendsViewModel` already resolved it, so this is the shared `ScreenHeader` plus its callback — no new
  stored state — and picking a child on Trends changes the same selection Activity and Summary read.
- **`domain/About.kt` and an About card in Settings** — `aboutVersionLine(name, code, debug)` reads
  `BuildConfig`, so the row a parent would quote in a bug report cannot disagree with the installed APK;
  `ABOUT_CREDITS` names the two children the app is for; the repository opens with an `ACTION_VIEW` intent
  (guarded, because a credit must never be able to take Settings down); the WHO 2006 attribution travels with
  it, and one line says there are no analytics and no trackers.
- **Per-activity hiding on the Activity tab** — `activities.hidden` in DataStore (a sorted, comma-separated
  set; an unknown name written by a newer build is dropped rather than thrown on), six toggles in Settings →
  Activities, `HomeUiState.shownCards` for the filtered list, and an all-hidden empty state that names the way
  back. A reset clears the set, because first run shows everything.
- **`ui/common/Haptics.kt`** — one helper owning the whole vocabulary instead of a `HapticFeedbackType` chosen
  per call site: `tap` for something that acts, `toggle` for a yes/no, `confirm` for a save, `longPress` for
  the press that starts a delete. Wired at `ChoiceChip`, `PillButton`, `SwitchRow`, `EntryRow`, `SwitcherPill`
  and the sheet's Save, which covers every chip row, card button, quick pill, toggle and long-press in the app.
  No preference: `View.performHapticFeedback` already honours the device's touch-feedback setting.

### Changed

- **A device pass now installs a RELEASE-signed APK.** `1.0.0` was the first release-signed build, so a debug
  APK has a different certificate and cannot install over it — `adb install -r` answers
  `INSTALL_FAILED_UPDATE_INCOMPATIBLE`. A pass builds `:app:assembleRelease` (its `versionCode` is above what
  is installed) and installs that; uninstalling to make a debug build fit would discard the log.
- **Every caption on a Trends chart now names the bucket it counts.** "7 of 30 days logged", "Days with no
  records count as zero" and "Best day" became bucket-derived, because a year of weeks that counted days would
  be describing a different chart from the one drawn.
- **A new build now reaches the phone as a tap, not as `adb install -r`.** The release APK is uploaded by hand
  to Firebase App Distribution and installed from the notification; the phone's own installer records it
  (`installerPackageName=com.google.android.packageinstaller`).
- **`versionCode` stopped being only a label.** App Distribution compares it to decide what is newer, so it is
  a contract from here on: 26 is the first number something outside a human reads.
- **The release signature changed, and the phones pay for it once.** `1.0.0` is signed by the family's key, not
  the debug key, so it cannot install over an existing debug build — uninstall, reinstall, sign in, and the log
  comes back from Firestore. `docs/firebase-setup.md`'s "what is still Phase 16" section is closed out with
  that, the SHA-1 registration and the delivery channel.
- **`README.md` and `AGENTS.md` follow** — the ladder's last rung, the release rules (never commit or regenerate
  the key, a missing key must degrade to unsigned rather than to a wrong signature, the SHA-1 is required for
  Google Sign-In), and the device rule that a release build has no debuggable directory, so counts come from
  the app's own Export.

- **A volume series with nothing recorded draws one gridline at zero, not a fabricated scale.** `yRangeFor`
  invented a 1.0 span for flat all-zero data, padded it to `0f..0.08f`, and `yTicks` then stepped by 0.02 —
  three gridlines of which two rounded to the same number. It now returns a zero-span range when `floorAtZero`
  is set and nothing was recorded, which `yTicks` collapses to a single line at zero.
- **The `0.19.0` rung is recorded as a rung, not a phase** — in `AGENTS.md`'s ladder, in `PLAN.md`'s versions
  paragraph, and under its own dated section in `PLAN.md`, because "work that ships application code takes a
  number" is a rule that was only implied by the Huckleberry import moving none.

### Fixed

- **The reminder for an activity that child has never logged now states the fact: `No health entry logged
  yet`.** It used to read `Time to log ${displayName().lowercase()}`, which printed `Time to log diaper` and
  called a nappy a diaper; the app's own `noun()` already existed for that sentence. It claims no interval,
  because there is no log to measure from. The case of an anchor later than now — reachable only through a
  clock change — says `Logged for 1:00 PM` instead of asserting that nothing was ever logged.
- **A comment that was wrong about its own fix.** The shared guard's KDoc claimed Growth's percentiles were
  covered too; `PercentileChart.kt` draws `yTicks` through `unit.format` with no such filter. The comment was
  corrected and the gap recorded instead of widening what the rung was approved to do — a comment that
  overstates its reach is how the next reader concludes a defect is already handled.

### Verified on the phone — `<handset>` (SM-S918U1), `0.19.0`, 2026-10-29

| # | Check | Result |
|---|---|---|
| 1 | `install -r`; foreground before any input | `topResumedActivity=app.babylog/.MainActivity`; `versionName=0.19.0 versionCode=25` ✅ |
| 2 | A 30 m Health reminder for a child with **zero** health rows | `dumpsys alarm` → `RTC_WAKEUP origWhen=2026-10-29 00:02:34.690 window=0 exactAllowReason=policy_permission`; it rang at 12:02 AM ✅ |
| 3 | The notification's words | **`Kai · Health` / `No health entry logged yet`**, colour `0xffff7bc0` (the Health accent), `channel=reminders`, `category=reminder` ✅ |
| 4 | The accept path, against the real server | A bumped manifest (`who-2006.2026.11`, `2026-11-01`, SHA-256 `7317…cbf9`) published from the console → row `Updated to who-2006.2026.11`, line `WHO 2006 · v2026.11 · updated 1 Nov 2026`, and `files/growth/updated.json` on disk hashed to the published `7317…cbf9` ✅ |
| 5 | The withdrawal, same manifest plus `"withdrawn": true` | Row **`Dataset withdrawn — back to the bundled reference`**, line back to `WHO 2006 · v2026.10 · updated 15 Oct 2026`, **in the same process — no force-stop** ✅ |
| 6 | The disk cleared, not just the screen | `files/growth/` empty; `settings.preferences_pb` holds neither `growth.dataset.version` nor `growth.dataset.sha256`, while `growth.dataset.lastChecked` is present ✅ |
| 7 | A relaunch does not re-adopt the withdrawn copy | force-stop + relaunch → still `v2026.10` ✅ |
| 8 | The Trends axis on real data | Bottle feed 2W `30 / 20 / 10 / 0`; 1W `25 / 20 / 15 / 10 / 5 / 0` — no two gridlines read the same number ✅ |
| 9 | The all-zero volume window | **Not reproducible on this log** — both children have a volume in every offered window, so the single-`0` axis rests on `TrendChartAxisTest` alone ⚠️ |
| 10 | Both check rows after the refactor | Settings → Growth reference → *Check for updates* → `Already up to date`; the same from the Growth pane ✅ |
| 11 | The log after the pass | database pulled with its `-wal` and `-shm` following a force-stop: `events 221`, `children 2`, `outbox 0`, `user_version 3` ✅ |
| 12 | `logcat -b crash -d` | **0** `FATAL EXCEPTION` across the installs, the reminder, the charts, the publish/withdraw pair, the relaunch and the database pull ✅ |
| 13 | The cloud side restored to the bundled reference | *Check for updates* → `Already up to date`, identity line `WHO 2006 · v2026.10 · updated 15 Oct 2026`, `files/growth/` still empty ✅ |

The reminder set for the pass was removed afterwards: its `⋮` offered only `Set reminder`, the Upcoming card
was gone and `dumpsys alarm` held no babylog alarm. **Found on the phone and not fixed:** a never-logged
`AFTER_LAST` rule re-arms a minute after every app start, because its `anchorAt` never moves and `refresh()`
reads it as overdue — it rang at 00:02 with the app closed and again at 00:07, a minute after a relaunch.
That is the nagging `AGENTS.md` rules out, and it is recorded for a decision rather than quietly patched.

### Verified on the phone — `<handset>` (SM-S918U1), `1.0.0`, 2026-10-29

`1.0.0` / versionCode 26 installed **over the air**, not by cable (`com.google.android.packageinstaller`,
`firstInstallTime = lastUpdateTime = 13:16:03` — a fresh install, which is what the signature change forces).

| # | Check | Result |
|---|---|---|
| 1 | The phone runs the release | `dumpsys package app.babylog` → `versionCode=26`, `versionName=1.0.0`, targetSdk 36 ✅ |
| 2 | Signed by the family's key, not the debug one | the installed `base.apk` pulled back and read with `apksigner`: `CN=BabyLog, OU=Family, O=BabyLog, C=TT`, SHA-1 `0766ee2f…5f4f` ✅ |
| 3 | What was delivered is what was built | SHA-256 `4dac7cfb…43bca` on both the built and the installed APK, 14 675 571 bytes each ✅ |
| 4 | It launches, and sync came back | `topResumedActivity=app.babylog/.MainActivity`; Settings → Sync: `Signed in as joelm3103@gmail.com`, `Everything here is on the server.`, `Up to date.` ✅ |
| 5 | The log is intact, counted from the app's own Export | 13:34 export: **219 live events** — BOTTLE 104, DIAPER 80, GROWTH 19, SLEEP 2, SOLIDS 14 — 2 children. The pre-uninstall export at 13:12 held the same 219 live rows (221 including 2 tombstones) ✅ |
| 6 | The screen agrees | Kai: Bottle `1d ago` 6 oz, Diaper `1d ago` Wet, Solids `10d ago` Carrot, Pumpkin, Chicken, Broccoli ✅ |
| 7 | `logcat -b crash -d` | **0** `FATAL EXCEPTION` across the launch, the Settings walk and the export ✅ |
| 8 | Google Sign-In on a release build | **Verified by the owner at close-out** — signed out of Sync, tapped `Sign in with Google`, signed back in; the registered release SHA-1 is accepted on a `CN=BabyLog` build ✅ |
| 9 | The second phone | Not attached, as in every pass since the import ⚠️ |
| 10 | System back, checked at close-out | Back from Settings returned to Activity, app still foreground, `Today` behind it ✅ |
| 11 | A logging sheet, opened and dismissed | `+ Log feed for Kai` rendered the Bottle feed sheet (`for Kai`, date, time, amount presets, `Save`) and back closed it without saving, Activity still foreground ✅ |

**The pass could not read the database at all, and that is new.** `run-as app.babylog` answers `package not
debuggable` — a release build has no debuggable data directory — so the counts above come from the app's own
Settings → Data → Export rather than from a pulled `babylog.db`. `AGENTS.md` now says so, and says to save the
export under a new name because the file picker offers today's date and will overwrite a previous pass's backup.

**Found on the phone and not fixed:** a **custom food is local-only**. `FoodRepository.addCustom` writes the row
and queues nothing, so the reinstall lost `Callaloo`, `Greek Yogurt` and `Pancake` — the eight custom rows that
came back are names the bundled asset also carries. A synced event references a custom food **by id**, so the
other phone would hold the reference and never the name. Pre-existing, not introduced here, and it needs a
decision (foods in the outbox, or an event storing the names it displayed) rather than a quiet patch. The two
`SLEEP` tombstones that did not come back are **by design** — `applyEvent` ignores a tombstone for an id this
phone has never seen — and are recorded so a later pass does not read them as lost writes.

### Tests

- **554 → 565: eleven net, and one test replaced rather than added.** Two in `GrowthDatasetManifestTest` (a
  three-field document still parses with the flag absent, and an explicit `false` agrees with it; then a `true`
  is read as a retraction carrying its version), five in `GrowthDatasetRepositoryTest` (the revert clears the
  file and both keys from a **second** instance, the revert needs no restart, a withdrawal for a
  never-accepted dataset is a no-op, a newer dataset is not recalled by an older notice, and all four
  `message()` sentences), **two net** in `ReminderTextTest` (the fact form, the four activities by name, and
  the future-anchor case — one test was replaced by three), and two in `TrendChartAxisTest` (one zero gridline
  for an all-zero volume series; eight ranges whose kept labels are all distinct).
- **Every fix was red first.** The withdrawal cases failed 3 of 11 against a disabled branch; the three
  reminder cases failed against the old `ReminderText`; both axis cases failed with the two mechanisms disabled.
  The suite is 565 with 0 failures, 0 errors and 0 skipped, summed from the 55 XML files under
  `app/build/test-results/testDebugUnitTest/`, and the file count rose 54 → 55 for the one new class.
- **One "red" was not one.** The first attempt at the withdrawal red failed to *compile* (a missing
  `kotlinx.coroutines.flow.first` import), which proves nothing about behaviour either way; it was fixed and
  the red re-taken, and only the second run is counted above. The fourth withdrawal case is a guard rather
  than a detector — it passes against the disabled branch because its expected outcome coincides with it.
- **The withdrawal no longer needs a test standing in for it.** Four `GrowthDatasetRepositoryTest` cases prove
  the repository reverts; the phone pass above proves the server can issue the retraction, that the cached
  bytes matched the published ones, and that both the file and the preferences are gone afterwards.
- **565 → 579 in Phase 17: fourteen, and each one is about a new rule rather than a new screen.** Five in
  `TrendsTest` (52 week buckets ending on this week's Monday; a week's bottles summed into one bucket with an
  empty week still drawn; `All` starting at the first event's month; an empty log drawing one month rather
  than a window from 1970; the all-time window's bounds). One in `RangeLabelTest` (each range names the bucket
  it draws). Three in `AboutTest` (the version line comes from its arguments — version, code and flavour — and
  the credits and URL are what they claim). Five in `SettingsRepositoryTest` (nothing hidden by default, a
  round trip as a set, hiding twice is not a duplicate, an unknown activity name is dropped instead of thrown
  on, and a reset shows everything again).
- **565 → 565 in Phase 16: no test was added, because the phase shipped no Kotlin.** The re-run for `1.0.0`
  produced the same 565/565 — 0 failures, 0 errors, 0 skipped over the same 55 XML files. What this phase
  needed proved was not a unit test: it was that the artifact is signed by a key the store agrees with, that a
  clone without the key degrades instead of breaking, and that a phone installs the thing over the air.

## [2026-10-28]

**In short:**

- **The `0.18.1` device pass found two more defects, both in the exact state that fix was written for.**
  The twins were still exempt from the reset's tombstone pass, so on a signed-in phone the server handed
  them straight back and the `No children yet` card was unreachable on every phone this app runs on.
- **With that reachable, the state itself was wrong:** six dead activity cards under it, their buttons
  reading `+ Log feed for ` with the name missing, their sheets titled `for `, and Save a silent no-op.
- **A reset now really empties the log**, and the empty state is one card, one line and one button.
- **The log was rebuilt from Huckleberry**, the app it replaces: a converter turns Huckleberry's per-child
  CSV export into a BabyLog backup, and both children's history now runs from birth to today — 221 events,
  grown from the 10-day window the raw export covered by transcribing four screenshots by hand.

### Versions

- **No rung moved.** `0.18.1` / `versionCode 24` is one phase's work, and this pass amended that work
  rather than starting more, so the build on the phone still says `0.18.1` — a second number for one phase
  would make two builds from one phase look like two phases. The next version is still the first release,
  `1.0.0`, in Phase 16.
- **The Huckleberry import below moved no rung either, and is not a phase.** It shipped no application code
  — only a script under `scripts/` — so the APK on both phones is byte-identical to the one above, and a
  new `versionCode` would have described a build that does not exist.

### Added

- **`scripts/huckleberry-import.py`** — converts a Huckleberry CSV export (and any hand-transcribed rows in
  the same shape) into a BabyLog backup file, schema 1. Huckleberry reuses four columns across every
  activity type — `Start Condition` / `Start Location` / `End Condition` / `Duration` mean weight, length,
  head circumference on one row and stool colour, consistency, contents on the next — so the mapping lives
  in the script with the reasoning beside each rule rather than in a table nobody can check later. It
  writes the backup outside the repo and prints a report of what mapped, what was skipped and what the
  schema cannot hold; the file is then applied through Settings → Data → Import backup, the app's own path.
- **`.gitignore` entries for that converter's output** (`babylog-import*.json`, `babylog-import-report.md`).
  Both files contain the family's real health log — names, weights, measurements — and a run started from
  the repo root had already written one report into it, where `git add .` would have committed it.

### Changed

- **Every imported measurement is derived under one unit convention, decided from the app's own wording.**
  Huckleberry writes `18.44lbs.oz` and `2.4ft.in`, and its display for the 21 Oct measurement reads
  *"12lbs 11.5oz, 2ft 2.8in, 16.5in head"* — so the dot separates units and the trailing digits are a
  fraction of the smaller one. A single digit is whole (`1.5lbs.oz` is 1 lb 5 oz, a birth weight), several
  digits with no decimal point are tenths (`18.44` is 18 lb 4.4 oz, `1.77ft.in` is 1 ft 7.7 in), and an
  explicit decimal is taken as written. That is the only reading under which every weight and length in
  both exports rises monotonically; reading `4.13` as thirteen ounces makes one twin *lose* 224 g in a week.
- **Imported rows carry the import time as `updatedAt`, not their own start.** Re-importing corrected
  values under unchanged ids would otherwise tie with the copies already on the server, and
  `SyncMerge`'s tie rule could keep the stale row — so the other phone would never see the fix.
- **An import that would have been ~1% light on every weight is corrected.** The first conversion read
  `18.44lbs.oz` as decimal pounds; under pounds.ounces the two series now read 1409 → 3530 → 3867 → 5151
  → 5568 → 6878 → 8233 → 8289 g and 595 → 1851 → 1959 → 2373 → 2880 → 2951 → 3328 → 4090 → 5120 → 5310 →
  5769 g, each strictly increasing, and the first twin's percentile moved 53rd → 50th.

### Fixed

- **A reset could not leave a phone with no children, because the seeded ids were still exempt.**
  `resetLocal` subtracted `first-twin` / `second-twin` from the tombstone pass — correct while the reset
  re-created them, wrong the moment it stopped. The twins stayed on the server, `withoutInboundApply`
  re-attached after the wipe, and the snapshot put both children back into an emptied phone. Every child is
  tombstoned now; `SEED_CHILD_IDS` is a list for tests and reading, never a rule, and
  `settings.markChildSeedDone()` is what keeps `AppRoot` from seeding them again. The retired exemption test
  was inverted rather than deleted, with the defect in its comment.
- **Two strings still promised the re-seed.** The Data caption read "Both of these put First Twin and Second
  Twin back", and the *Reset local data* confirmation said the same — both false once the reset stopped
  re-creating them, and both read at the moment a parent needs the truth.
- **The empty state offered work it could not do.** Six activity cards were rendered under it whose buttons
  read `+ Log feed for ` (the name an empty string) and whose sheets were titled `for `. Saving there was a
  silent no-op — `HomeViewModel.save` returns when `childIds` is empty — so a parent could fill a form and be
  told nothing. `ActivityScreen` now skips the cards entirely while there are no children, and the card's
  second line says the cards will appear.
- **The 2026-03-11 measurement is now only its weight.** That row also claimed a length growing 404 → 498 mm
  and a head 320 → 380 mm in 16 days, which no baby does; the weight stays at the 3.53 kg the owner ruled
  and the two impossible measurements are dropped rather than imported-and-wrong. `DROP_FIELDS` in the
  converter holds the rule, and the Length chart no longer plots a point the source cannot support. On
  `<handset>`: Length lists 37.0 → 71.1 cm with **no 11 March row**, Weight still lists
  `11 Mar 2026 · 7.8 lb` (3.53 kg) — so the drop is field-scoped rather than a deleted event — system back
  returns to Activity, and `logcat -b crash` is 0.

### Verified on the phone — `<handset>` (SM-S918U1), `0.18.1`, 2026-10-28

| # | Check | Result |
|---|---|---|
| 1 | `install -r` `0.18.1` (versionCode 24), launch, foreground before any input | `topResumedActivity=app.babylog/.MainActivity`; `versionName=0.18.1 versionCode=24` ✅ |
| 2 | *Reset local data* on the **pre-fix** build, twice, with an event in the log | events deleted, outbox drained to 0, summary `This phone is empty. Every deletion reached the server.` — truthful — but `children 2` came back carrying the **server's** `updatedAt` (`2026-10-27 09:21:53`), and `No children yet` never appeared ❌ |
| 3 | Same reset on the **fixed** build | db pulled with the WAL: `events 0, children 0, outbox 0` ✅ |
| 4 | Activity screen after it | `No children yet` → `Add a child in Settings and the cards will appear.` → `Add a child`; child switcher absent, **no activity cards**, no dead buttons ✅ |
| 5 | Add-a-child from that card | opens Settings; `Add child` creates `Test Child` (new id, birth date, 40 weeks, adjusted age), outbox drains to 0, back on Activity the switcher shows `T · Test Child` and the cards are labelled `+ Log feed for Test Child` ✅ |
| 6 | A real write afterwards | a 120 ml bottle on the card, `events 1`, `outbox 0`, row `('BOTTLE', <child id>, 120, 0)` ✅ |
| 7 | `logcat -b crash -d` | **0** `FATAL EXCEPTION` across installs, two resets, the empty state and the writes ✅ |

Two things this pass had to learn the hard way, recorded so the next one does not: **pull the database with
its `-wal` and `-shm`**, or a bare `babylog.db` reports the state before the last transaction (it showed
`outbox 30` while the truth was 0); and a renamed child is saved on **every keystroke** in `ChildEditor`,
so the `Add child` button under it is not its save button — tapping it creates a second child.

Not performed on the day: the import path on the phone (it needs a backup file and two accounts' worth of
rows; it is covered by `SyncRepositoryTest`'s reconcile tests) — **that was driven later the same day**, see
the Huckleberry pass below — and the second phone, which was never attached.

### Verified on the phone — Huckleberry import, `<handset>`, 2026-10-28

`0.18.1` / versionCode 24, unchanged build. The import runs through the app's own *Import backup*, so this
pass is the proof that a converted file is a real restore and not a hopeful JSON document.

| # | Check | Result |
|---|---|---|
| 1 | `install -r`, foreground before any input | `topResumedActivity=app.babylog/.MainActivity`, `versionName=0.18.1 versionCode=24` ✅ |
| 2 | *Import backup* → the file → `Replace` (twice: the raw export, then export + screenshots) | first pass wrote 151 events, second 221; both from 2 children and 11 custom foods ✅ |
| 3 | Database after the second import | `events 221` (BOTTLE 104, DIAPER 80, GROWTH 19, SOLIDS 14, SLEEP 4), `children 2` (Kai, Levi), `foods 94` once the bundled catalogue re-seeded itself, `outbox 0` ✅ |
| 4 | Activity screen | `9h 37m ago` on the feed card, not `9d ago` — the log reaches today; both children's 4:35 AM entries visible with `K`/`L` badges ✅ |
| 5 | Summary, 2-week window | `14 of 14 days logged` for each child, where the raw export covered 4 ✅ |
| 6 | Totals against the source, recomputed by hand from the CSVs | first twin 2066 ml / best day 856 ml on 18 Oct; second 2560 ml / best day 903 ml on 17 Oct — the app agreed exactly ✅ |
| 7 | Trends → Growth | percentile chart plots 8 and 11 measurements with adjusted ages; the 21 Oct measurement transcribed from a screenshot appears as `12.7 lb · <1st percentile` ✅ |
| 8 | Settings opens, system back returns to Activity with the log intact | ✅ |
| 9 | `logcat -b crash -d` | **0** `FATAL EXCEPTION` across two imports, four screens and the back press ✅ |

What the pass taught, recorded so the next import does not repeat it: **pull `babylog.db` together with its
`-wal` and `-shm`** or the pull reports the state before the last transaction — a bare copy showed
`outbox 30` while the truth was 0, and later `outbox 74` while the truth was 15; and **a force-stop before
pulling** checkpoints the WAL, which is the cheap way to get an unambiguous read.

Not performed: the second phone was not attached, so its copy of the imported log was not observed — only
that the outbox drained to 0, which is what sends it there.

### Tests

- **553 → 554: one added.** `a reset tombstones the seeded twins too, or the server sends them back` — the
  regression for the defect the pass found, naming the phone evidence in its comment, and the seed-exemption
  test beside it lost its exemption assertion and gained the opposite one. The suite is 554 with 0 failures,
  0 errors and 0 skipped, summed from 54 XML files under `app/build/test-results/testDebugUnitTest/`.
- **The Huckleberry import added no tests, because it added no application code.** The suite is unchanged at
  554, 0 failures, 0 errors, 0 skipped across the same 54 XML files; `:app:testDebugUnitTest` reports itself
  `UP-TO-DATE` on this branch, which is the artifact agreeing that nothing it covers has moved. The
  converter's rules are exercised by its own report and by the phone pass above, not by the JVM suite — a
  limitation worth knowing before trusting a future mapping change.

## [2026-10-27]

**In short:**

- **Phase 15: reset to defaults** — Settings → Data gains the two buttons the owner scoped on 2026-10-25,
  one branch, `0.18.0`. *Reset local data* and *Delete all server data*, each with its own confirmation.
- **The reset tells the server what it deleted.** Every row travels as a tombstone queued in the same
  transaction that removes it, which is the only thing the other phone treats as an instruction.
- **The device pass found a reset that partially undid itself**, and the fix is `withoutInboundApply`:
  the listener is cancelled and *joined* around the wipe, so a snapshot taken before the deletes cannot put
  the log back. Found by pulling the database, not by reading the screen.
- **`0.18.1` fixed the three defects that same pass left behind, and it landed the same day** (`0e0c744`) —
  a drain that gave up at the first slow write, a reset that re-seeded the twins, and an import that merged
  instead of replacing. **Recorded retrospectively:** those commits shipped without a changelog entry, so
  their record is written here, under the day they landed; the device pass that amended them is under
  2026-10-28.

### Versions

- `0.18.0` / `versionCode 23`. Phase 15 is a new capability — two destructive paths that did not exist — so
  it takes a `0.MINOR` rung. The next version is the first release, `1.0.0`, in Phase 16.
- `0.18.1` / `versionCode 24`. The three fixes that pass left behind, so a `0.PATCH` bump on the `0.18.0`
  rung — and no second rung was opened when the next day's pass amended them, because one phase's work
  takes one version.

### Added

- **Reset local data** (`MaintenanceRepository.resetLocal`) — one transaction: queue a DELETE intent for
  every child and every event, then `deleteAll()` on both tables, then drop the now-orphaned UPSERTs. Then
  `SettingsRepository.clearForReset()` clears this phone's choices and every reminder rule, both twins are
  re-seeded *(no longer: removed later the same day in `0.18.1`, see *Fixed* below)*, and
  `Reminders.refresh()` cancels the alarms the cleared rules owned.
- **Delete all server data** (`SyncRepository.queueTombstonesForEveryRemoteRow`) — `fetchAll()` first, then a
  DELETE intent per id the server reports, *including rows this phone never received*, then the same local
  reset and one drain. Offered only while signed in. No `SyncGateway` change: the outbox already travels
  tombstones by id, and a bulk collection delete would be ignored by the other phone's listener.
- **`SyncRepository.resetGate()`** — refuses a reset when the probe drain fails **or the phone is offline**.
  The connectivity check is not redundant: an empty queue drains successfully without a network call, so the
  probe alone would allow a reset on a plane. Signed out is allowed; the tombstones wait for the next sign-in.
- **The `No children yet` empty state** (`ui/home/ActivityScreen.kt`, added with `0.18.1`) — the reset stopped
  re-creating the twins, which left the Activity screen with nobody to log for while its `Setting up…`
  placeholder stayed on screen as a permanent lie. The card names the state and carries an `Add a child`
  button, and the child switcher hides while there is nothing to switch between.

### Changed

- **The two seeded children are exempt from the tombstone pass.** `seedIfEmpty` re-creates `first-twin` /
  `second-twin` with the same ids, so a DELETE that drained would erase them on the other phone. Their intents
  are dropped and the re-seed queues the UPSERTs instead. *(This exemption was the `0.18.0` shape and is the
  defect `0.18.1`'s pass found on 2026-10-28: the seeds were removed from the reset, the exemption stayed,
  and the server restored both children into an emptied phone.)*
- **`clearForReset` keeps two things on purpose:** `sync.seeded` (a reset must not re-upload the log it just
  deleted) and `growth.dataset.*` (which reference produced a percentile is not the family's log).

### Fixed

- **A reset could partially undo itself.** The wipe emptied the tables and the tombstone drain deleted the
  server rows, and then the inbound listener applied a snapshot it had taken *before* the deletes landed —
  re-inserting 15 events into the emptied database with an empty outbox, so the phone showed a log the server
  no longer had and would never send again. `SyncRepository.withoutInboundApply {}` cancels and **joins** the
  listener around the wipe and re-attaches after; `attach()` drains before it observes. A test pushes a stale
  snapshot mid-wipe through a new `FakeSyncGateway.observeFlow` hook and asserts the tables stay empty.
- **Both resets put First Twin and Second Twin back.** The owner asked for an emptied app, not twins that
  reappear: `wipePhone` no longer calls `seedIfEmpty`, and `clearForReset` keeps `children.seeded` so
  `AppRoot`'s first-run seed cannot fire again. `0.18.1`.
- **A drain stopped at the first slow write.** `OutboxDrainer` returned at the first timeout, so one slow
  write on a cold connection left the other ~159 intents unanswered under a summary that already claimed the
  server was clear — the owner's `~160 still queued` after *Delete all server data*. A timed-out intent is
  now deferred to the end of the pass and retried once, so the rest drain; an exception still ends the pass.
  `SyncRepository.drainUntilIdle` bounds repeat passes at 3 and reports the real remaining count, and every
  summary is derived from that result — "from the server too" is only said when nothing is left queued.
  Resets also stop double-queueing: `enqueueTombstones` skips ids that already have a DELETE queued, while
  an UPSERT is still replaced. `0.18.1`.
- **Import merged instead of replacing.** It wiped the phone but told the server nothing, so the pre-import
  children came back beside the imported ones. Both the reset and the import now run one sequence through
  `SyncRepository.reconcileRemote(keepChildIds, keepEventIds)`: tombstone every remote row outside the keep
  set — empty for a reset, the file's ids for an import — inside `withoutInboundApply`, then drain. The
  import carries a confirmation that names the other phone. `0.18.1`.

### Verified on the phone — `<handset>`, `0.18.0`, 2026-10-27

- **A local reset held.** 0 events at +10 s and +30 s after the message, both twins re-seeded, outbox empty —
  where the pre-fix build had 15 rows back within seconds.
- **The refusal is real.** With `airplane_mode_on=1` verified immediately before the tap and two events in the
  log, the reset reported `Nothing was deleted: Waiting for a network connection. Use Delete all server data
  if the log must go anyway.` and the database still held both events.
- **The server wipe found the rows this phone never had:** `118 row(s) only the server knew about were
  queued`, then `events 0, children 2, outbox 0`.
- **29 `app.babylog.reminders.FIRE` records sat under `Removal history`** and none were armed, which is the
  cancelled-alarm evidence rather than an assumption. Crash buffer stayed at 0 `FATAL EXCEPTION`.
- **The second phone was not checked** — only one device was attached. Its copies are handled by the same
  tombstones, which is what the `118 rows` case proves, but it was not observed.

### Tests

- **538 → 549: 11 added.** Four in `MaintenanceRepositoryTest` (a tombstone per event before the rows go, no
  delete for the seed ids, an added child still tombstoned, an UPSERT replaced by its DELETE); two in
  `SettingsRepositoryTest` (`clearForReset` drops choices and reminders; it keeps `sync.seeded` and keys
  outside this repository); five in `SyncRepositoryTest` (signed-out reset allowed, refused when the queue
  cannot travel, refused offline with an empty queue, remote-only ids tombstoned, and a stale snapshot during
  a wipe does not resurrect rows). The suite is 549 with 0 failures, 0 errors and 0 skipped, summed from 54
  XML files under `app/build/test-results/testDebugUnitTest/`.
- **`0e0c744` then added four net tests, 549 → 553** — `a reset does not queue the same row twice`
  (`MaintenanceRepositoryTest`), `a reset keeps the first-run children flag` (`SettingsRepositoryTest`),
  `one write that never answers does not block the ones behind it` (`OutboxDrainerTest`) and `reconciling
  keeps exactly the ids it is given` (`SyncRepositoryTest`) — plus `a reset leaves no children at all`, which
  replaced the seed-exemption test rather than being added beside it. 553 with 0 failures, 0 errors, 0
  skipped.

## [2026-10-26]

**In short:**

- **Phase 14: Polish/Misc changes** — the eight findings the owner ruled on at the end of Phase 13.4, one branch,
  `0.16.0`. Two sync defects, two layout defects, two redrawn silhouettes, and the activity glyph's reach.
- **`Sync now` offline now answers, and lets go of the lock.** A drain that could not reach Firestore used to
  suspend forever with the `Mutex` held, so the button looked inert and could not recover. Each write is now
  bounded, and the card says either *Waiting for a network connection* or *The server did not answer* — the
  second because four bars is not an offline phone.
- **The first-sign-in seed drains itself.** The sign-in result is applied under `NonCancellable`, and the
  listener drains once after its first snapshot, so the "N changes waiting to sync" card empties without a tap.
- **Two consistency silhouettes redrawn.** `Mucousy` read as a letter `T` at 16dp on the phone; it is two slanted
  streaks now. `Runny` merged into `Mushy` at the same size; it is a mass sinking into a wide flat pool.
- **The glyph reaches five surfaces, and two chip rows stop borrowing it.** Sheet title bars, entry rows and
  Both-mode column heads carry the activity glyph; Growth's three metric chips carry their own marks (a scale, an
  up arrow, a head); the range chips carry no glyph and read `D · 1W · 2W` and `1W · 2W · 1M`. No window changed.
- **Four findings closed without code**, by the owner's ruling: the `8:55 PM` tombstone stays deleted, `Reminders
  differ` stays gone, the Trends chip clipping was fine, and phases land in the order they are done.
- **Phase 13.5 lands the growth dataset update channel, device-verified.** A newer WHO dataset can be fetched
  from Firestore, checksum-verified, and cached without an APK release; the bundled dataset stays on every
  failure. The pass also found a real defect — the Settings identity line kept reading `v2026.10` directly
  beside `Updated to v2026.11` until the screen was left and re-entered — fixed here as `0.17.1`.
- **The phase ladder is renumbered so it reads in landing order** (docs only, no build): reset-to-defaults
  13.6 → 15, update delivery 15 → 16, ship 16 → 17. 13.5 keeps its number and its `0.17.0` — now `0.17.1`,
  the rung its device pass took — because three commits already carry `phase13.5`. This supersedes the
  `phases land in the order they are done` ruling closed earlier the same day, below.

### Versions

- `0.16.0` / `versionCode 20`. Phase 14 is polish and fixes, but it also adds two capabilities the owner asked for
  (per-metric marks and the wider glyph reach), so it takes the next `0.MINOR` rung rather than a `0.PATCH`. The
  unused `0.14.0` / `0.15.0` rungs are not reserved for unfinished phases; they receive increasing versions
  and codes when they land. `1.0.0` is the first delivered release, with Ship as the last step.
- `0.17.0` / `versionCode 21`. Phase 13.5 adds the growth dataset update channel. Built, unit-tested and
  device-verified on 2026-10-26 — see the phone pass below.
- `0.17.1` / `versionCode 22`. Phase 13.5's device pass. The Settings identity line is now observed through a
  `StateFlow` instead of being read once on entry, so an accepted update changes it on the screen that ran the
  check. A fix found by the pass, so a `0.PATCH` rung and not a new capability.

### Changed

- **`Sync now` reports instead of hanging.** `OutboxDrainer` bounds every write with `writeTimeoutMs` (10 s);
  `DrainResult` now carries `remaining` and `timedOut`. The timeout lives in the drainer rather than the Firestore
  gateway, so `FakeSyncGateway` can prove it — and `hangForever` was added to it for exactly that.
- **`NetworkStatus`** (`data/sync/NetworkStatus.kt`, `AndroidNetworkStatus.kt`) is a one-method seam over
  `ConnectivityManager`, so the two failure sentences are provable on the JVM. `ACCESS_NETWORK_STATE` was already
  declared.
- **A sign-in that succeeded is applied under `NonCancellable`**, and `attach()` drains once after the listener's
  first snapshot. `FakeSyncGateway.failOnce` exists so a test can fail the pre-listener drain and still expect an
  empty queue.
- **`ChildSwitcher` is a `FlowRow`**, and its labels are `maxLines = 1, softWrap = false`: at 130 % the pills wrap
  as whole pills instead of breaking `Both` into `Bot` / `h`.
- **A solids meal reads as its name.** `HomeViewModel` carries `foodNames` from the catalogue it already observed;
  `detailLine` and `recentRowLabel` take the map. An id the catalogue no longer has still renders as words.
- **Trends' pinned activity row has an 8 dp bottom inset**, so it no longer touches the range chips in the pane
  below it.
- **`ActivitySheet` takes the activity, not an accent**, and draws its glyph at 16 dp with a mirrored spacer so the
  title stays centred. Entry rows draw the glyph at 14 dp, and Both-mode column heads draw the card's own glyph
  before the child's name.
- **Growth's metric chips carry their own marks** — `ic_metric_weight` (a scale), `ic_metric_length` (an up arrow
  off a floor), `ic_metric_head` (a head with a curl) — via `GrowthMetric.iconRes()`. Local stroke drawables, not
  `material-icons-extended`; the project depends on `material-icons-core` only. `BMI_FOR_AGE` is a bundled dataset
  measure with no chip, so nothing was left blank.
- **An amount-less bottle now names the gap.** `detailLine`'s fallback for a bare bottle was the word
  `Bottle`, so a feed logged without a volume read `Bottle feed` over `Bottle` — the card naming itself twice
  and showing no number at all. It now says `No amount recorded` (the row says `No amount`). A brand the
  parent typed still wins the line, and a bottle with a volume is unchanged. Found on the phone during this
  phase's device pass, not in the original eight findings, and added as 14.10 on the owner's instruction.
- **Range chips are labels.** `HistoryRange` is `D · 1W · 2W`, `TrendRange` is `1W · 2W · 1M` — two rows because
  they are two different windows, and `1M` names the 30-day window it actually draws. Both enums gained a `spoken`
  form used as the chips' `contentDescription`.

### Verified on the phone — `<handset>`, 2026-10-26

- **Installed `0.16.0` over `0.13.0`** with the same signing key, so the log and its row ids survived; the app
  was foreground before any input was sent, and the crash buffer stayed at 0 `FATAL EXCEPTION` throughout.
- **`Sync now` with no network** set `Waiting for a network connection — 1 change queued.` by itself and left
  the queue untouched across two taps; restoring the network and tapping again reported `Up to date.` with the
  queue empty — which the old code could not have done, because the timed-out drain held the lock.
- **Sign-in drained the seed with no tap.** `font_scale 1.3` wrapped `Both` to a second line **as a whole
  pill**. All seven consistency silhouettes read as forms, `Mucousy` no longer a `T`. A sheet's title bar
  carried its glyph with the title still centred (525 px of 525 px).
- **`Banana`** logged through the solids sheet showed as `Banana` on the card and on the row, then was deleted;
  a throwaway Wet diaper proved the offline sync message and was deleted too. Both left tombstones and
  the first phone reported an empty queue after draining; the second phone was not checked in this pass.
- **A defect no test caught, found here:** a feed with no amount read `Bottle feed` over `Bottle`. Fixed as
  14.10 (`No amount recorded`), and the test that had pinned the old word now pins the new one.

### Verified on the phone — Phase 13.5, `0.17.1`, `<handset>`, 2026-10-26

- **The check could not work at first, and the phone said why.** A read of `reference/growth_manifest` returned
  `Status{code=PERMISSION_DENIED}` while `events` synced normally: the repo's `firebase/firestore.rules`
  carries the `match /reference/{documentId}` block from `ba4bbb4`, but rules are pasted into the console by
  hand and the deployed set predated it. With the rules published and the two documents in place, the same
  tap reported `Updated to who-2006.2026.11`.
- **The fallback is real, not theoretical.** Across every failing check the Growth reference line stayed at
  `WHO 2006 · v2026.10 · updated 15 Oct 2026` and the app kept working: a permission error, a missing document
  and a checksum mismatch all end as `Could not check` with the bundled tables still in use.
- **The update path, end to end.** Cache file removed so the app fell back to the bundled dataset, then one tap:
  `Updated to who-2006.2026.11`, the line became `WHO 2006 · v2026.11 · updated 26 Oct 2026` **on the same
  screen**, and `files/growth/updated.json` (488 449 bytes) was written. A second tap reported
  `Already up to date` with no re-fetch. The dataset published for the pass carries the bundled LMS numbers with
  only `version` and `retrievedAt` bumped — it proves fetch → checksum → cache → identity line, not a new WHO
  release, and the percentile tables are byte-identical to the bundled ones.
- **A defect no unit test caught, found here:** after a successful update the Settings card printed
  `Updated to who-2006.2026.11` above a line still reading `v2026.10`. `SettingsViewModel` called
  `container.growth()` under a comment claiming it refreshed that line, but the value was discarded, and
  `AppScaffold` only re-read it when the destination changed. Fixed in `0.17.1`; the line now moves on the
  screen that triggered the check, which is what this pass re-verified.
- **Both manual rows work** — Settings and Trends → Growth each ran the check — and the crash buffer stayed at
  0 `FATAL EXCEPTION` across the install, the relaunch and every tap.

### Fixed

- **The Settings identity line did not move when the dataset did.** `SettingsViewModel.checkGrowthUpdates()`
  ended with `container.growth()` under the comment *Refresh the identity line shown in Settings after a
  successful update* — a suspending call whose result was discarded, so nothing recomposed. The line came from a
  `produceState` keyed on the destination in `AppScaffold`, which only re-runs when the screen is re-entered.
  `GrowthDatasetRepository.identityLine` is now a `StateFlow` that the accepted update publishes to, the screen
  collects it, and the dead call (and the now-unused `container` parameter) are gone. Found on the phone, not by
  a test; `GrowthDatasetRepositoryTest` now pins that a collector sees the new line.
- **Two consistency silhouettes.** `Mucousy` was a wide bar over a stem with a dot under it — a `T` at 16 dp, found
  by the owner on the phone and kept open rather than redrawn in the moment. `Runny` was three heavy circles on an
  ellipse that merged into `Mushy` at the same size. Both redrawn, and both were rendered at 16/24/48 dp before and
  after rather than judged at 48 dp.
- **A `T` could ship because no test looked at the mapping.** `StoolAndMetricGlyphTest` now asserts all seven
  consistencies and all three metrics resolve to distinct, non-zero drawables, and that the metric marks are not
  activity glyphs.

### Closed, by the owner's ruling — no code

- **The soft-deleted `8:55 PM` bottle row stays deleted.** It was a test record tombstoned by a mis-aimed
  long-press; the owner chose to leave the tombstone standing (2026-10-24) and confirmed it closes here (2026-10-25).
- **`Reminders differ` stays gone by design** — two children get two chips, so a comparison sentence has nothing
  left to say.
- **The Trends chip clipping was fine.** The owner looked at it and closed it; what was actually wrong was the
  missing gap *below* the chip row, which is now item 14.5.
- **Phases land in the order they are done.** The question of which of 13.5 and 13.6 comes first is closed: each
  phase states its prerequisite before it starts, and there is no imposed sequence beyond that.
- **Deferred, unchanged:** the notification says `Time to log bottle feed` when that child has never logged the
  activity. Kept, because there is no log to measure from and claiming an interval would be a lie.

### Tests

- **515 → 532: 17 added tests.** Two in `OutboxDrainerTest` cover timeout and recovery; five in
  `SyncRepositoryTest` cover seed draining, offline/connected failure messages, retry after timeout and
  queue-count wording; three in `StoolAndMetricGlyphTest` check the stool and metric mappings; five in
  `RangeLabelTest` pin the labels, windows and spoken forms; two in `CardSummaryTest` check catalogue-name
  fallbacks. The existing amount-less-bottle test was rewritten to pin `No amount`, not counted as new.
- **532 → 537.** Five in `GrowthDatasetRepositoryTest`: newer dataset with matching checksum, stale version,
  checksum mismatch, unreachable gateway, and corrupt cache fallback.
- **537 → 538.** One in `GrowthDatasetRepositoryTest` for the defect this pass found: `identityLine` is a
  `StateFlow`, so a collector sees `who-2006.2026.12` after an accepted update instead of the version it read on
  entry. The suite is 538 with 0 failures, 0 errors and 0 skipped, summed from 54 XML files under
  `app/build/test-results/testDebugUnitTest/`.

### Changed — the phase ladder is renumbered (documents only)

No code, no schema, no version bump: the build on the phone remains `0.17.0` / versionCode 21. Recorded here
because it reverses a ruling closed above, not because it ships anything.

- **Renumbered: 13.6 reset to defaults → 15, 15 update delivery → 16, 16 ship → 17.** Those three had no
  commits, so the change cost nothing but prose; the documents that carry it are `PLAN.md`, `AGENTS.md` and
  `docs/firebase-setup.md`.
- **13.5 keeps its number and its `0.17.0`.** Three commits on `main` already say `phase13.5`
  (`29ccd6c`, `ba4bbb4`, `848c924`); renaming a landed phase would mean rewriting history to match a label.
- **Supersedes *Phases land in the order they are done* (C12, closed above).** The new rule: a phase's number
  is fixed when its first commit lands, unstarted work takes the next free integer above the last landed
  phase, and decimal sub-phases are retired — `9.1`, `9.2`, `13.1`, `13.1.1` and `13.2`–`13.5` are history.
  The ruling above is left as it was closed, so the reversal is visible rather than silently edited away.
- **Also sorted:** `PLAN.md`'s phase sections were physically out of order (13.2 before 13.1.1 before 13.1)
  and are now in numeric order — a pure block move, proven by an identical sorted-line multiset.
- **Not touched:** older dated entries in this file, the `docs/timeline` screenshot directories and their
  links, and every git commit message. They record what happened; the plan records what will happen.

### Changed — the shipped growth dataset is restored

The Phase 13.5 pass published `who-2006.2026.11` — the bundled LMS rows with only `version` and `retrievedAt`
bumped — so the update path could be proven end to end. That version does not exist upstream and the identity
line named it, so both `reference` documents went back to the shipped `who-2006.2026.10` / `2026-10-15` with the
bundled asset's own SHA-256 (`2df3475…18cb5c`, byte-identical to `assets/growth/who-igrowup.json`, 488 449
bytes). No percentile ever changed: only the two label fields were synthetic.

**The order was not optional.** `isNewer()` refuses anything not newer, so a corrected (older) manifest returns
`NoUpdate` and a phone that had accepted `v2026.11` would have kept it indefinitely. The documents were corrected
first; then this phone was force-stopped, `files/growth/updated.json` deleted, and the app relaunched. It now reads
`WHO 2006 · v2026.10 · updated 15 Oct 2026`, a manual check reports `Already up to date`, no cache file is written,
and the crash buffer stayed at 0. The second phone never adopted `v2026.11` in the first place.

**Recorded, not built:** an accepted dataset can never be withdrawn, because `deleteCache()` fires only for a
corrupt file — so recalling a bad reference today means clearing app data. The mechanism, the two-step restore and
the two candidate fixes are written up under Phase 13.5 in `PLAN.md`.

## [2026-10-25]

**In short:**

- **Phase 13.4 lands: activity reminders**, set per child from the activity's own card. Four activities — Bottle
  feed, Solids, Diaper and Health — can each nudge one child `N` after that child's last log, with a night window
  to stay quiet in and day-of-week chips. A reminder fires once and never nags.
- **The card shows its reminder** as a chip per child carrying that child's badge and the time it will ring
  (`[F] Every 3h (4:45 PM)`), and tapping the chip edits that child's rule. An `Upcoming reminders` card at the top
  of Activity lists what is armed and folds away when you want the screen back.
- **The notification names the child and the activity**, wears the activity's accent, and opens that child's logging
  sheet when tapped — verified firing unprompted on the phone, not by injection.

### Versions

- `0.13.0` / `versionCode 17`. Phase 13.4 adds activity reminders — a capability, with no schema change. The dataset
  channel moves to `0.14.0` and reset-to-defaults to `0.15.0`. **Recorded retrospectively:** the first half of this
  work was written and recorded under `[2026-10-24]`; both halves land as commits on 2026-10-25.

### Added

- `domain/Reminders.kt` — `REMINDER_TYPES` (the four activities), `ReminderConfig`, `ReminderKey`,
  `nextReminderAt(config, anchorMs, now, zone, night)` as the single "when does it ring" answer, plus
  `upcomingReminders()` and `reminderChipText()`. `domain/ReminderCodec.kt` stores one rule as one forgiving string.
- `data/reminders/` — `Reminders` (refresh + fire), `ReminderScheduler` (`setAndAllowWhileIdle`, one alarm per
  activity and child), `ReminderNotifications` (one channel, bell icon, the activity's accent, an id per child),
  `ReminderReceiver`, `ReminderBootReceiver`, and `ReminderText`.
- **A `⋮` menu on the four reminder-capable cards** holding `Set reminder` / `Edit reminder` and `Remove reminder`;
  the sheet is `Set reminder — <Activity>` with `For` (one child), `Time` (an interval), `Notify`, `Skip night` and
  `Days`.
- **A reminder chip per child** on the card, `Every 3h (4:45 PM)`, badged in Both mode; tapping it edits that child's
  rule. An **`Upcoming reminders (n)` card** above the activities, collapsible, each row opening the child it names.
- **`Delete this entry`** at the end of every editing sheet, routed through the same confirm-then-Undo path as a
  long-press.
- `ReminderConfig.anchorAt`: a rule for an activity that child has never logged is anchored on when it was set, so it
  rings once instead of never.

### Changed

- **Reminders are per child only, and are set from the activity's card — not from Settings.** A shared rule hid which
  twin was overdue, and a Settings section put the setting a screen away from the thing it is about. Growth and Sleep
  carry no reminder: a weigh-in is periodic and Sleep has its own timer.
- **`EventRepository` gained `onLogged`** beside `onQueued`, and `EventType.displayName()` moved into the domain so a
  notification can name an activity without Compose.
- **Inexact alarms, no privilege**: `setAndAllowWhileIdle`, with no `SCHEDULE_EXACT_ALARM` and no `USE_EXACT_ALARM`.
- **A health reminder names the activity and nothing else** — never a medicine, never a dose — asserted against the
  whole bundled catalogue.
- **The interval stepper is standardised** (`StepperRow`'s `valueWidth`), so `1h` and `1h 30m` no longer shuffle the
  −/+ buttons sideways. Reminder times follow the app's 12/24-hour preference everywhere.
- **`Remind at` is a first-class mode again**, and `ReminderScheduler` gained a `canBeExact` seam so the
  exact/inexact branch is unit-tested instead of only discovered on a phone.

### Fixed

- **Reminders arrived late.** The first build armed inexact alarms (`setAndAllowWhileIdle`) on the reasoning that a
  few minutes late is fine; `dumpsys alarm` showed `window=+37m47s`, so "a few minutes" could be most of an hour. The
  manifest now declares `USE_EXACT_ALARM` (granted at install) plus `SCHEDULE_EXACT_ALARM` for API 31-32, and the
  scheduler arms `setExactAndAllowWhileIdle` — `window=0` on the phone — falling back to the inexact call rather than
  dropping the reminder if the parent revokes exactness.
- **`Remind at` was missing.** The redesign left only the interval; fixed clock times are back as a `Mode` row, with
  an editable list of times and `+ Add time`, and the chip says `At 8:00 AM` instead of pretending a fixed time is an
  interval.
- **A reminder that had nothing to measure from never rang**: a rule for an activity that child had never logged had
  no anchor, and an interval whose target had passed returned nothing — so it stopped reminding exactly when it was
  most overdue. Found on the phone as a chip reading `Every 3h` with no time.
- **Tapping a chip or an Upcoming row edited the FIRST child's rule**, whatever the row said: the action carried the
  activity but not the child.
- **A rule left in the retired fixed-times mode** could neither fire nor be corrected from the UI; the refresh now
  converts it to an interval, keeping the interval it had.
- **Two chips did not fit one row** at the first size (458px each against 900px available); tightened to 7dp side
  padding, `labelSmall` and an 11dp bell (387px + 385px).

## [2026-10-24]

**In short:**

- **`12-hour` is now the default clock on a fresh install.** Existing saved choices remain unchanged;
  timestamps still use epoch milliseconds, and Celsius remains the temperature default.
- **A temperature now carries one of four bands, marked by shape rather than hue.** LOW (`< 36.5`),
  NORMAL (`36.5–37.5`), MILD_FEVER (`37.6–37.9`) and FEVER (`>= 38.0`, still `FEVER_C`) are classified
  from the value rounded to the displayed decimal, so the word can never contradict the number beside it.
- **Trends now lists Mild and Fever readings with their own words.** Low remains marked on Activity and
  Summary but stays out of the over-time table.
- **A nappy now answers two questions that can both be true.** Wet / Dirty / Mixed became **Wet** and
  **Dirty** toggles, and a dirty change can carry a **colour** (six filled circles) and a **consistency**
  (seven silhouettes, multi-select). Both are optional: unrecorded stays empty, exactly like `rash`.
- **Four activities can remind you to log them** — Bottle feed, Solids, Diaper and Health — **set from the activity's
  own card** (`⋮` → `Set reminder`) and **per child**, N after that child's last log, with a night-window silence and
  day-of-week chips. A reminder fires once and never nags, tapping it opens the right logging sheet for the right
  child, and the card then shows its own reminder (`Every 3h · Next: 1:00 PM`). Growth and Sleep have no reminder: a
  weigh-in is periodic and Sleep has its own timer.

### Versions

- `0.13.0` / `versionCode 17`. Phase 13.4 adds activity reminders — a capability, no schema change. The dataset
  channel moves to `0.14.0` and reset-to-defaults to `0.15.0`.
- `0.12.0` / `versionCode 16`. Phase 13.3 is diaper detail — two-part answers plus colour and consistency,
  and a third schema version, so a capability rung. The dataset channel moves to `0.13.0` and
  reset-to-defaults to `0.14.0`.
- `0.11.0` / `versionCode 15`. Phase 13.2 adds the four temperature bands; the planned dataset channel
  moves to `0.12.0` and reset-to-defaults to `0.13.0` (both since moved one rung further by Phase 13.3).
- `0.10.1` / `versionCode 14`. A `0.PATCH` bump for making 12-hour the fresh-install default before the
  Phase 13.2 capability rung.

### Added

- `domain/Temperature.kt` — `NORMAL_MIN_C`, `MILD_FEVER_MIN_C`, `TemperatureBand`, `temperatureBand()`,
  `markedBand()` and an `isFever()` derived from the FEVER band rather than a separate comparison.
- `ui/home/TemperatureBadge.kt` replaces `FeverBadge.kt` with one stable pill geometry: filled for FEVER,
  outlined for MILD_FEVER and LOW, and absent for NORMAL.
- `TemperatureBandTest` adds six cases for every boundary and a 35.00–39.00 °C sweep; two new
  `TrendsTest` cases pin Mild-day counting and the displayed `37.54` / `37.56` boundary. The suite moves
  from 409 to **417**, with 0 failures, 0 errors and 0 skipped across 42 XML result files.

### Added — Phase 13.3, diaper detail

- `domain/BabyEvent.kt` — `DiaperColor` (Yellow, Brown, Black, Green, Red, White) and
  `DiaperConsistency` (Solid, Mushy, Runny, Mucousy, Hard, Pebbles, Diarrhea), plus `DiaperKind.wet/dirty`,
  `DiaperKind.of(wet, dirty)` and `DiaperKind.toggle(...)`. Storage is unchanged in meaning: `MIXED` is how
  "both" is spelled, and it has meant that since schema v1.
- `domain/CodedValues.kt` — `encodeCodes` / `decodeCodes`, the `FoodIds` pattern for a small ordered enum
  set: empty encodes to NULL, and an unknown name from a newer build is dropped rather than failing the row.
- Schema **v3**: two additive nullable columns (`diaperColoursCsv`, `diaperConsistencyCsv`), `MIGRATION_2_3`,
  and `schemas/…/3.json` committed. No back-fill — a colour nobody recorded would be an invented
  observation. `MigrationTest` gains a v2 → v3 case built from the committed `2.json`, and its v1 case now
  asserts version 3.
- The two fields travel: `Mappers`, `SyncDocuments` and `Backup`, each with a legacy-input test (a v2 row, a
  document and a `0.11.0` export all read as "not recorded" rather than failing).
- `ui/home/DiaperSheet.kt` rebuilt: **Contents** (two independent chips), **Colour** (`DiaperColorRow`) and
  **Consistency** (`MultiChipRow`) — the last two absent while Dirty is off. `MultiChoiceChip` /
  `MultiChipRow` are the multi-select siblings of `ChoiceChip` / `ChipRow`.
- Seven filled silhouettes, `res/drawable/ic_stool_*.xml`, mapped by `DiaperConsistency.stoolRes()`.
- `CardSummary.diaperDetail` / `diaperColors`, `DiaperColorDots`, and `DiaperSwatch` in `Tokens.kt`. The
  suite moves from 417 to **444**, with 0 failures, 0 errors and 0 skipped.

### Added — Phase 13.4, activity reminders

- `domain/Reminders.kt` — `ReminderMode`, `ReminderConfig`, `ReminderKey` and the single pure function
  `nextReminderAt(config, anchorMs, now, zone, night)`. Null is a real answer for a disabled rule, a child with
  no anchor, an overdue reminder and an interval that lands on a day switched off.
- `domain/ReminderCodec.kt` — one string per rule, decoded forgivingly so a value from a newer build falls back
  to defaults rather than throwing.
- `data/reminders/` — `Reminders` (refresh + fire), `ReminderScheduler` (`setAndAllowWhileIdle`, one alarm per
  activity and child), `ReminderNotifications` (one channel, bell icon, the activity's accent, ids per child),
  `ReminderReceiver`, `ReminderBootReceiver`, and `ReminderText`.
- The reminder entry point is a `⋮` menu on the four reminder-capable cards (`Set reminder` / `Edit reminder`,
  `Remove reminder`), and the sheet is `Set reminder — <Activity>` with `For` (one child), `Time` (an interval),
  `Notify`, `Skip night` and `Days`. `POST_NOTIFICATIONS` is requested from that sheet, never on launch.
- The card's own reminder chips — one per child in scope, each with that child's badge and the time it will ring
  (`[F] Every 3h (4:45 PM)`, `[S] Every 4h (5:45 PM)`) — each opening THAT child's rule, which is also the way to edit
  it; a confirmation snackbar on save; and a collapsible Upcoming reminders card.
- `ReminderConfig.anchorAt`: a rule for an activity that child has never logged is anchored on when it was set, so it
  rings once instead of never. An overdue interval now rings a minute later rather than returning null, and a rule left
  in the retired fixed-times mode is converted to an interval on the next refresh.
- Every editing sheet ends with **`Delete this entry`**, using the same confirm-then-Undo path as a long-press.
- **An "Upcoming reminders (n)" card at the top of the Activity screen** (the owner's mockup): one row per armed
  reminder with its activity's glyph and accent, the time in the chosen format and a countdown, tapping through to
  that activity's reminder sheet. It reads the same `nextReminderAt` as the scheduler, is absent when nothing is
  armed, ticks every 30 seconds, and names the child whenever a row does not apply to both children.
- `MainActivity` is `singleTop` with `onNewIntent`, and a notification's tap opens that activity's sheet **for
  that child** through `AppContainer.pendingSheetRequest`. The suite moves from 444 to **496**, with 0
  failures, 0 errors and 0 skipped.

### Changed — Phase 13.4

- **Reminders are inexact on purpose**: `setAndAllowWhileIdle` and no `SCHEDULE_EXACT_ALARM` or
  `USE_EXACT_ALARM`. A feed nudge a few minutes late is worth neither a special-access screen nor a privilege
  reserved for alarm clocks.
- **`EventRepository` gained `onLogged`** beside `onQueued`: a reminder must re-arm after a write even on a build
  with no sync configured, so "there is something for the sync queue" and "an anchor moved" are now separate
  callbacks that happen to fire in the same place.
- **`EventType.displayName()` moved from `ui/theme` to the domain**, so the notification can name an activity
  without a Compose dependency and there is still exactly one copy of those six strings.
- **The application object does nothing on start.** Reminders are armed from the first frame and from the boot
  receiver, so the container stays lazy and a process started for a broadcast does not open the database.
- **Three changes came out of the device pass.** Growth and Sleep lost their reminder rows (see above), and
  reminder times now follow the 12/24-hour preference: the first build formatted the row and the Settings
  summary with a hardcoded `HH:mm` while the picker and the notification used the preference, so one sheet
  showed `11:00 PM` and `23:00` at once. `clockLabel(minutes, format)` now formats like `clockTime`, and a rule
  with more than two times names two and counts the rest. The third is the card's child naming: with a Solids
  rule for First Twin alone the row read a bare `Solids`, which reads as "both children will be reminded" when
  only one will.
- **The notification was seen firing on the device, unprompted.** A First Twin Bottle feed reminder whose target had
  passed re-armed itself for a minute later, fired, and the shade showed `First Twin · Bottle feed — Time to log bottle
  feed` in the bottle accent; tapping it opened the Bottle feed sheet already up for First Twin. The chips were also
  tightened to 7dp/`labelSmall`/11dp so both children's fit on one row (387px + 385px inside the 900px available), and
  an Upcoming row now opens the child it names.
- **Superseded:** the earlier note that the alarm-to-notification hop was unproven. It is proven; only the two-phone
  Firestore behaviour of reminders remains out of scope, because reminders are per-device and never synced.
- **Recorded honestly, previously:** The pass confirmed the
  permission flow, the armed alarms in `dumpsys alarm` (distinct times per child and per activity, inexact
  windows), and the Settings summaries; the alarm-to-notification hop is covered by
  `ReminderNotificationsTest` but has not yet been observed on the phone, because a broadcast to the app's
  non-exported receiver cannot be injected from a shell.

### Changed — Phase 13.3

- **The Diaper card's quick pills are Wet and Dirty only.** "Both" now costs the sheet: one tap cannot
  answer a two-part question, so `DIAPER_QUICK_KINDS` is an explicit two-item list and a test pins it
  against the temptation of `DiaperKind.entries`.
- **A diaper row reads `Wet + dirty`, not `Mixed`**, and carries its colour as small circles rather than a
  colour name — the parent answered two questions, and "mixed" invited the reading "some wet, some dirty".
  Two existing assertions were rewritten for the new words; no behaviour moved.
- Every colour swatch carries a permanent 1dp hairline ring, and selection adds a ring rather than changing
  a size. The ring is a dedicated per-theme colour because no single grey clears 3:1 against a fill set
  running from black to white; `DiaperPaletteTest` asserts the edge rule for six fills × both themes.
- **Driven on a real phone (`<handset>`, in-place `install -r`, `0.12.0` / `versionCode 16`).** Two quick
  pills only and one tap logs; both chips can be on at once and the last one cannot be switched off; the
  Colour and Consistency sections appear only with Dirty on; multi-select rings a swatch without moving it;
  a wet-only row reopens with no detail; a dirty save reads `Wet + dirty · mushy, pebbles` with its dots on
  both the headline and the row; `Black` is visible on the dark theme and `White` on the light one; a
  `MIXED` row written by `0.11.0` opens with both chips on and no detail, which is the v2 → v3 migration on
  the real database; the row ellipsises but keeps its dots and time at 130% font scale; and
  `logcat -b crash -d` held **0** `FATAL EXCEPTION`.
- **Open, found by that pass and kept open by the owner:** the `Mucousy` silhouette reads as a letter `T`
  at 16dp, with `Solid`'s pointy peak the next weakest. Both ship as they are; the finding is recorded in
  `PLAN.md` rather than fixed here. The screenshots are in
  `../babylog-refshots/2026-10-25-diaper-reference/`, beside the repo.
- **Recorded mistake, not an app defect:** while deleting the two test rows, the second long-press landed on
  the row below because the first deletion had shifted the list, so the pre-existing `8:55 PM` nappy was
  soft-deleted (a tombstone, recoverable) instead of the test row. **The owner chose to leave it deleted.**
  `PLAN.md` records both the incident and the rule it teaches: re-dump after any change that removes a row,
  before the next long-press.

### Changed

- `SettingsRepository`, `clockTime`, `AppRoot` and `LocalTimeFormat` now default to `TWELVE_HOUR` when no
  choice has been stored. `SettingsRepositoryTest` and `DisplayPreferencesTest` pin the fallback; existing
  installs with a stored choice are not migrated. **Recorded retrospectively on 2026-10-24:** the code
  landed earlier that day in commit `123062b`, before the dedicated `feat/twelve-hour-default` close-out.
- The fallback was driven on a real phone (`<handset>`): with the `time.format` key stripped from the
  DataStore, Settings' night window read `6:00 PM` / `6:00 AM` and the Health sheet's time row read
  `8:28 PM`; toggling to `24-hour` switched the labels to `18:00` / `06:00` and survived a relaunch, and
  toggling back to `12-hour` did the same in reverse. System back from Settings and from the Health sheet
  both returned to Activity without leaving the app. The final `logcat -b crash -d` held **0**
  `FATAL EXCEPTION` entries.
- The boolean `fever` flag became a nullable `TemperatureBand` through `ChildLine`, `ActivityTotal`,
  `EntryRow`, `FeverReading`, Activity, Summary, the entry list and Trends. NORMAL collapses to null at the
  source because a normal reading has no pill; only FEVER recolours the number.
- Bands are computed from the number as displayed: `37.99` prints `38.0` and is FEVER, while `37.94`
  prints `37.9` and is MILD_FEVER. The same rule controls Trends inclusion, preventing a displayed Mild
  reading near `37.6` from being omitted by a raw-value comparison.
- Trends → Health now says `Flagged readings`, lists MILD_FEVER and FEVER with their own badges, counts
  their distinct days, quotes both thresholds in the selected unit, and deliberately excludes LOW.
- Device verification used `<handset>` (S23 Ultra, Android 16) with `0.11.0` / versionCode 15. Activity
  showed Low / no marker / Mild / Fever for 36.2 / 37.1 / 37.6 / 38.4; Summary carried the latest Fever;
  and Trends listed only Mild and Fever (`4` temperatures, `2` flagged, `1` day). Stored 37.94 displayed
  `37.9` / Mild and 37.99 displayed `38` / Fever. System back returned Trends to Activity and dismissed
  the Health sheet; all six close-out rows were soft-deleted through the app, and the crash buffer ended
  with 0 `FATAL EXCEPTION` entries. The evidence table is in `PLAN.md`; screenshots remain beside the repo.

### Fixed

- Trends no longer filters flagged readings with the raw `temperatureC >= 37.6` comparison. It derives
  `TemperatureBand` first, so a stored `37.56` that displays as `37.6` is listed as Mild, while `37.54`
  displays as `37.5` and remains unlisted. A focused `TrendsTest` regression pins both sides.

## [2026-10-23]

**In short:**

- **Time and temperature now follow explicit family preferences.** Settings offers independent
  `12-hour` / `24-hour` and `°C` / `°F` choices, persisted locally. Timestamps and stored readings are
  unchanged: time remains epoch milliseconds, temperature remains Celsius, and fever classification
  remains the canonical `FEVER_C` boundary. The preference changes the night-window labels, sheet rows
  and picker, card and entry times, Health input, Summary, and the Trends fever list.
- **Every activity now has its own glyph, on its card and on the chips that control it.** Six outline
  drawables — bottle, bowl-and-spoon, nappy, ruler, thermometer, moon — are tiled **tone-on-tone** as a
  watermark filling each dashboard card (and each Summary total card), and drawn small and solid inside the
  activity chips on Summary's filter row and Trends' tabs. Activity is now identifiable by shape as well as
  hue, which is the same argument that gave a fever a pill instead of a red number.
- **The pattern is the card's own hue, one measured step stronger — not white.** White is what the owner's
  infographic reference uses, and on our 26% tint it fails both ways: dE 3 at alpha 0.04 with `textOnCard`
  at 4.2:1, dE 12.5 and 3.1:1 at alpha 0.16, and effectively invisible in the light theme (dE 1-4).
  `patternFor` pushes the fill 0.14 toward its accent instead — dE 7-11 dark, 7-10 light — and the cost was
  paid where it lands: **`textOnCard` moved to `#DCDEE7` dark / `#404E69` light**, which holds 4.5:1 on the
  worst pattern tile and 5.9:1 / 5.5:1 on the plain tint.
- **Checked on the phone, not in the abstract:** dark, light and 1.3x font scale on `<handset>`, with
  `logcat -b crash` holding **0** `FATAL EXCEPTION` and system back from Settings returning to Today. The
  first diaper glyph read as a letter `M` repeated down the card and was redrawn as a waistband above a
  rounded pad; at 18dp the glyphs were too small to read as texture and they now draw at 26dp on a 64dp
  pitch.
- **A pre-existing bug found while looking:** at 1.3x font scale the `Both` pill in the child switcher wraps
  to `Bot` / `h`. Not caused by this work and deliberately not fixed here.

### Versions

- `0.10.0` / `versionCode 13`. Phase 13.1 is a new display/input capability, so it takes the next
  `0.MINOR` rung; the planned growth dataset channel and reset-to-defaults work move forward one rung.
- `0.9.1` / `versionCode 12`. A `0.PATCH` bump because this is polish on the 0.9.0 rung, not a capability;
  the ladder's next rung is still `0.10.0` for Phase 13.1. The `2026-10-22` entry declined a bump for a
  device pass — that was documentation of an existing build, this is new code, so a build on a phone can
  still say which work produced it.

### Added

- Settings → Units now includes independent Temperature (`°C` / `°F`) and Time format (`12-hour` /
  `24-hour`) chip rows. `SettingsRepository` persists both choices with 24-hour and Celsius defaults —
  the clock default was later flipped to `12-hour` on 2026-10-24;
  changing Metric / Imperial / Custom leaves the temperature choice alone.
- `ui/DisplayPreferences.kt` provides the two choices through Compose, and four JVM tests cover clock
  boundaries, Fahrenheit-to-Celsius input, exact fever classification, untouched-prefill precision, and
  persistence independent of the unit presets.
- Six stroke-style vector drawables, `res/drawable/ic_glyph_{bottle,solids,diaper,growth,health,sleep}.xml`,
  on a 24dp viewport with no colour of their own — the tint arrives at runtime, because one glyph has to be
  a card's pattern colour and a chip's accent.
- `ui/common/ActivityGlyph.kt` — the exhaustive `EventType.glyphRes()` mapping, `glyphPainter()` for the
  chips, and `Modifier.activityPattern()`: a tile rasterised **once** per type, tint and density
  (`ImageBitmap` + `ImageShader`, repeated at 1:1 device pixels) rather than ~24 vector draws per card per
  frame. The second row is baked into the tile at half a pitch, which is what makes the repeat read as a
  diagonal field rather than a grid.
- `PatternPaletteTest` (5 cases) and `ActivityGlyphTest` (2) — the suite is **405, 0 failures**. The palette
  test asserts, for all six accents in both themes, that the pattern is visible (dE >= 7), that `textOnCard`,
  the label and `textPrimary` all clear 4.5:1 **on the pattern**, and that the chip glyphs clear 3:1 both
  ways. The glyph test asserts the mapping is total and one-to-one.

### Changed

- Temperature converts only at the input/display boundary. Health accepts the selected unit but stores
  Celsius; an untouched Fahrenheit prefill keeps the original stored precision, and `isFever` still
  compares that canonical Celsius value against `FEVER_C`.
- Cards, entry rows, Both-mode Health columns, Summary totals, Trends fever readings and threshold copy,
  sheet time rows, the Material time picker, and Settings' night-window labels now read the selected
  display preferences. There is no Room migration and no sync-document change.
- `versionCode` is 13 and `versionName` is `0.10.0`.
- `Tokens.kt` gained `PATTERN_ALPHA = 0.14f` and `patternFor(accent)`, with the measurements in the comments,
  and `textOnCard` was lifted in both palettes for the reason above.
- `ActivityCard` and History's `TotalCard` draw the pattern after the fill and before the padding, so it sits
  under every label, value and row.
- `ChoiceChip` takes an optional `icon`, null by default, so the range chips, meal slots, diaper kinds and
  Growth's metric chips are unchanged; only the activity chips pass one, tinted with the raw accent when off
  and `onAccent` when on.
- `versionCode` 12 / `versionName` 0.9.1.

### Fixed

- Health's temperature parse/range warning now sits directly beneath the Temperature field, before
  Medication, instead of being grouped with dose validation at the bottom of the form. The empty-form
  hint stays hidden while either field is invalid.
- **The diaper glyph read as a letter.** The first version was one silhouette — two tabs and a centre dip —
  and on the phone it looked like a row of `M`s rather than nappies. It is now a waistband bar above a
  rounded pad, with the bar deliberately wider than the pad so it cannot be confused with the Solids bowl.

### Notes

- Device-verified on `<handset>` with the `0.10.0` / versionCode 13 APK. A synthetic `100.4 °F`
  reading rendered as `100.4 °F` with the `Fever` pill on Activity, Summary and Trends, while the phone's
  Room row held exactly `38.0` Celsius. Trends read `23 Oct 10:39 PM` and named the threshold as
  `100.4 °F`; system back returned to Activity and `logcat -b crash -d` held 0 `FATAL EXCEPTION`. The
  test row was soft-deleted through the app and remains only as a tombstone. Screenshots are beside the
  repository, not committed.
- Deliberately **not** included, and left as the owner's call: the sheet title bars, the entry rows, Growth's
  metric chips, the `1d·7d·14d` range chips, and Both-mode column glyphs.
- The phone's `0.9.0` install had to be uninstalled first (`INSTALL_FAILED_UPDATE_INCOMPATIBLE`, a different
  signing key from this machine's), which is why the log was pulled back by signing in afterwards. Same
  per-machine debug keystore consequence `docs/firebase-setup.md` already records.

## [2026-10-22]

**In short:**

- **Phase 13's device pass starts, and the half that needs no Firebase is done.** A phone was attached this time (`<handset>`, clean install of the `0.9.0` debug APK) but the build still carried no `firebase.properties`, so what ran on hardware was the single-phone shape: launch without a crash, the Sync card saying it is not set up and offering no dead buttons, every write path queueing an outbox intent, and a child's permanent delete queueing DELETE intents while dropping the dead UPSERTs. **The two-phone round trip is still outstanding** — it needs the project's values and a second phone.
- **The `.babylog` outbox was read back off the phone, not inferred from the screen.** `run-as` + the WAL: a 90 ml bottle for both children = two `events` rows and four outbox intents including both `CHILD` UPSERTs (the write path that queued nothing before Phase 13); deleting *Second Twin* left `CHILD/second-twin/DELETE` and their event's DELETE queued at one timestamp with the two dead UPSERTs gone.
- **`docs/firebase-setup.md`** is the console runbook the owner asked for: the four values and where they live in `google-services.json`, the two accounts, the rules paste, and a table of what each misconfiguration looks like on screen. The debug certificate's SHA-1 is recorded there for the Google Sign-In client.
- **Then the console steps were done, and the drain went live and was proved on the phone.** A configured build (`0.9.0`, values compiled into `BuildConfig`) signed in with email/password, pushed the seeded children, wrote a 60 ml bottle for both children and had it acked inside five seconds, then took **three writes in airplane mode as exactly three queued intents** and delivered them on reconnect as **five events, five distinct ids, no duplicates and nothing lost**. `logcat -b crash -d` stayed empty throughout and system back from Settings returned to Today. **Still outstanding: the two-phone run and the inbound listener** — only one phone was reachable, and the pull half needs either a second device or a data wipe plus a fresh sign-in.
- **A finding the device exposed and the build could not:** pressing **Sync now** with no network gives no feedback at all. Firestore's `WriteStream` closes `UNAVAILABLE`, the write stays pending, and `syncNow()` therefore never returns — the card keeps the previous drain's message ("Sent 1 change(s).") beside a live "1 change waiting to sync.". Nothing is lost — the intent survives and drains within seconds of the network returning — but the button looks inert, and the suspended drain holds the `Mutex`, so later triggers queue behind it rather than re-reporting. Recorded as an open issue in `PLAN.md`, not fixed here, because a connectivity-aware "waiting for network" state is its own change with its own tests.
- Two stale numbers were corrected in `PLAN.md` while writing the pass up: the suite is **398** (not the 339 the "Verified" paragraph still quoted), and the Phase-13 row now says what was actually put on a phone instead of "device pass pending" without qualification.
- **A reset to defaults is scoped, not built.** Asked for while closing this out: Settings → Data has no way to return the app to first-run state. It is now `PLAN.md` Phase 13.2 (`0.11.0`) with the shape agreed — events, children re-seeded, and settings, with **server deletion as a separate, explicitly warned choice**, because a local-only wipe would be merged back by the next snapshot.
- **Phase 13 closed, on hardware, with two accounts — written up retrospectively.** The gap the entries above leave open (the inbound listener, and a tombstone surviving the hop) was closed in two parts. First `pm clear app.babylog` on `<handset>`: after signing back in, the **same six row ids** returned and the row deleted before the wipe did **not** — the tombstone beat the stale server copy. Then `emulator-5554` joined as a second sync client signed in with the **second account** (uid `B43h…`, after the owner replaced the unused Firebase account and the rules and `firebase.properties` moved to it): it pulled the phone's six rows by id, took a 90 ml bottle from the phone and had its own 120 ml bottle appear on the phone, queued **exactly three intents** in airplane mode that arrived on the phone as **exactly three rows with three distinct ids and no duplicates**, and lost `e416186c` when the phone deleted it — still deleted after its process restarted. `logcat -b crash -d` was **0** on both devices.

### Versions

- No rung moved. This was a device pass and documentation; Phase 13 shipped at `0.9.0` and a `0.9.1` would have made two builds from one phase look like two phases. `1.0.0` belongs to the release (Phase 14/15), so the ladder stands where it was.

### Added

- `docs/firebase-setup.md` — the Phase 13 console runbook: what is already known (`baby-log-app-119e4`, the Android app id, the debug SHA-1), how to read `apiKey` and `webClientId` out of `google-services.json` (`client_type` `3`, not `1`), creating the two email/password accounts (the app has no sign-up on purpose), pasting `firebase/firestore.rules` with the two uids, the `firebase.properties` template, and a symptom table — "not set up on this build" means the values were missing **at build time**, `CONFIGURATION_NOT_FOUND` means the provider is off, `PERMISSION_DENIED` means the rules or a uid.
- `PLAN.md` Phase 13.2 — the **reset to defaults** item, scoped so it is not lost: events, children (re-seeded), and settings, with server deletion behind its own confirmation that says the data cannot be recovered.

### Changed

- `firebase/firestore.rules` now carries the two real uids instead of the placeholders, and `PLAN.md` records them beside the phase, as the file's own header asks.
- `PLAN.md`'s Phase 13 section gained a dated device-pass record with the evidence table, the behaviour worth naming (an unconfigured build never drains, so its outbox accumulates and that accumulated queue *is* the first sign-in's seed), and an explicit list of what the pass cannot prove.
- Two stale claims corrected in the same pass: the suite is **398**, not the 339 the `Verified:` line still quoted, and `README.md` now says the build reads `firebase.properties` rather than implying it reads `app/google-services.json` — the plugin that would have read that file is deliberately not applied, so the JSON is only where the values are copied from.
- `README.md`'s repo-layout block named the folder `first-twinpp/` and omitted `CHANGELOG.md`, `docs/` and `firebase/`; it now matches the repository.
- `firebase/firestore.rules` carries the **current** uid pair — `REPLACE_WITH_FIRST_UID` and `REPLACE_WITH_SECOND_UID` — after the owner replaced the unused second Firebase account. The old `wJjDVdK1…` uid is gone from the rules, from `firebase.allowedUids` and from the console, so the deleted account can no longer reach the log.
- `docs/firebase-setup.md` now records a second consequence of the per-machine debug keystore: a rebuild on another computer is signed differently, so `adb install -r` answers `INSTALL_FAILED_UPDATE_INCOMPATIBLE` and the app must be uninstalled first — which discards the local database and any **still-queued** outbox rows. Drain before switching a phone to a build from a different machine. This machine's print (`F8:27:D1:F3…`) is recorded beside the original.
- `PLAN.md` Phase 13 is closed: the status row and heading read **DONE, device-verified**, a dated write-up of the hop and the two-account round trip sits under the phase, and a three-pass table summarises what each pass actually proved. The earlier passes' own sections are left as they were written — a verification table records what was true that day.

### Fixed

- Nothing in the app's code changed today. What is recorded here is the **verification** of two Phase 13 fixes that no JVM test could reach, both read back from the phone's own database rather than inferred from a screen:
  - **Children now queue an outbox intent.** `ChildRepository` queued nothing before this phase, so a renamed or added child would have stayed on the phone that made it. On the device the outbox held `CHILD/first-twin/UPSERT` and `CHILD/second-twin/UPSERT` beside the two `EVENT` UPSERTs.
  - **A permanent delete travels as DELETE intents.** Deleting *Second Twin* hard-deleted the child and their bottle, **dropped the two dead UPSERTs**, and kept `CHILD/second-twin/DELETE` plus the event's DELETE at one shared timestamp, leaving *First Twin* untouched. After a cold relaunch the UI had degraded to a single child pill without a crash.

#### What the phone showed, pass by pass

- **Unconfigured build** (`0.9.0`, no `firebase.properties`): launches with no crash; Settings → Sync renders the `NotConfigured` body — "Sync is not set up on this build, so the log stays on this phone. Logging works normally." — with no email/password fields and no Google button, so the build cannot offer a button that cannot work; a 90 ml bottle for both children wrote two `events` rows and showed `just now`, then `2m ago` after a relaunch; `pragma user_version` = 2; the phone was then cleared with `pm clear app.babylog`.
- **Configured build:** `BuildConfig` carries the project id, app id, API key, web client id and both uids at `0.9.0` / versionCode 11; the Sync card switched to the sign-in form and the **Google button appeared**, which is what proves `webClientId` is being read; an empty submit answered "Enter the account's email and password." and a wrong credential answered "Wrong email or password." from Firebase itself; sign-in succeeded as `joelm3103@gmail.com`, the card read "Everything here is on the server." and the outbox was already empty — the first-sign-in seed had drained; a 60 ml bottle for both children left **0 queued** within five seconds, and a row is only deleted from `outbox` on ack, so both writes reached Firestore; three bottles logged in airplane mode queued **exactly three intents** and none drained; on reconnect the queue fell to 0 with **five events and five distinct ids** — no duplicates, nothing lost; one further offline write showed "1 change waiting to sync." and drained by itself on reconnect (`events` 6 → 7, outbox 0). Firestore logged only `UNAVAILABLE` stream closures during the offline window — no permission or auth errors — and `logcat -b crash -d` held **0** `FATAL EXCEPTION` across the whole session.
- **Two accounts, two devices (added retrospectively).** The emulator signed in as the second account and pulled the phone's **six** rows by id with no prior state, which is the inbound listener crossing accounts. A 90 ml bottle on the phone produced `60b95a2c` and `e390a804` on the emulator; a 120 ml bottle on the emulator produced `661e6748` and `1d3a752b` on the phone. Airplane mode on the emulator queued **exactly three intents** (`d636207e`, `391c8b77`, `e416186c`) while the phone stayed at 10 events; on reconnect the phone gained exactly those three, both devices settled at **13 events with 13 distinct ids and an empty outbox**. Long-pressing an entry row on the phone deleted `e416186c`; the emulator marked that id deleted (12 live), and after `am force-stop` plus a relaunch it was **still** deleted with nothing resurrected.

#### Found on the phone, left open

- **"Sync now" offline is silent.** The drain suspends on a write Firestore cannot deliver, so the action returns nothing: the card keeps the previous message beside a live pending count, and because the suspended drain holds the `Mutex`, repeated taps queue rather than re-report. Reconnect proves the queue is safe (it empties within seconds, exactly once). The fix is a connectivity-aware state — "waiting for a network connection" — and it belongs with its own tests rather than bolted onto a close-out. See `PLAN.md`, Phase 13's device-pass section.
- **The first-sign-in seed waits for a drain.** On a fresh install, after signing in with the network up, the two seeded `CHILD` UPSERTs sat in `outbox` and the card read "2 changes waiting to sync." for thirty seconds and more. Tapping **Sync now** drained them at once ("Sent 2 change(s).") and the queue went to 0, so nothing was stuck or lost — there is simply no drain between sign-in and the next write. The natural fix is one drain when the inbound listener finishes its first snapshot; it is left open with the `Sync now` finding rather than patched into a close-out pass.

### Security

- The Google provider being enabled does not open the log: account creation is open to any Google user, but `firebase/firestore.rules` allows reads and writes only from the two uids, and the app signs a third account straight back out on its own allow-list so it cannot be shown an empty log and mistake it for data loss. `firebase.properties` stays gitignored; the API key in an Android APK identifies the project rather than granting access, and restricting it to `app.babylog` + the registered SHA-1s is left as an optional hardening note in the runbook.
- The replaced Firebase account is **out of the log for good**: its uid was removed from `firebase/firestore.rules`, from `firebase.allowedUids` and from the console, so even a correct password for it now gets `PERMISSION_DENIED` rather than data. The pair that remains is the one the devices actually ran on.

## [2026-10-19]

**In short:**

- **Two accounts, one shared log.** Phase 13 lands the sync layer: Firebase Auth with email + password as the primary route and Google Sign-In as a second button, an outbox drain to Firestore, and an inbound listener that applies `updatedAt`-wins. **398 tests green** (349 → 398). It is **built and tested but not yet on a phone** — no device was attached and the Firebase project does not exist yet, so the two-phone acceptance run is still outstanding.
- **The Phase 7 permanent-delete defect is fixed.** Deleting a child now queues a DELETE intent for the child and every one of their events inside the same transaction that hard-deletes them, so the deletion reaches the other phone instead of being handed back on the next merge. No schema change: `schemas/` still holds only `1.json` and `2.json`.
- A defect found while wiring, not on a phone: **`ChildRepository` never queued an outbox intent** — children were the one write path sync would have silently missed. Every write path queues now.
- **A badge is numbered when two children share a first letter**, so `Kai` and `Kim` read `K1` and `K2` instead of both reading `K`. One function now feeds every badge in the app, and `ChildBadge` draws two glyphs as a pill of the same height so nothing shrinks and no row moves. **349 tests green** (339 → 349).
- The app id is `app.babylog` for both `namespace` and `applicationId`, and the default children are **First Twin** and **Second Twin**, so a fresh install shows initials that can be told apart (`F` / `S`) instead of two the same.
- The docs were tightened in the same pass: `PLAN.md`'s Phase 0 row states the toolchain it was read from rather than pointing elsewhere, the growth provenance cites `scripts/build-growth-assets.py` where the checksums actually live, and `README.md`'s layout block matches what is in the repo.

### Versions

- `0.9.0` — Phase 13, two accounts and one shared log

### Added

- `domain/SyncMerge.kt` — `resolveSync()`: the whole merge rule as a pure function. Later `updatedAt` wins, **a deletion wins a tie** (a tie going to the live row resurrects a deleted bottle with nobody having logged it), and a row this phone has never seen is taken. 9 tests.
- `data/sync/` — the sync layer, each piece separable from Firestore:
  - `SyncDocuments.kt`: the wire mapping to and from a plain `Map`, so the round trip is proved on the JVM (Firestore's `DocumentSnapshot` cannot be constructed in a test). Includes the reduced tombstone a permanent delete sends.
  - `SyncGateway.kt` / `FirestoreGateway.kt`: two collections, `children` and `events`, document id = the row's own client-generated id, every write `SetOptions.merge()`.
  - `OutboxDrainer.kt`: re-reads the row by id at send time, deletes the intent only on ack, stops at the first failure rather than reordering the queue, and falls back to a tombstone when the row is already gone.
  - `AccountAuthenticator.kt`: email/password, Google Sign-In, and the two-uid allow-list as a separate `Refused` result — a refused account is signed straight back out.
  - `SyncRepository.kt`: first-sign-in seeding, the inbound merge, sign-in states, and one drain at a time.
  - `FirebaseSetup.kt`: builds `FirebaseOptions` from `BuildConfig`, so no `google-services.json` is needed to build.
- `firebase/firestore.rules` — read and write only for the two uids, and only for `children` and `events`; nothing else is matchable, so a typo'd collection fails closed.
- Settings → **Sync**: email and password, `Sign in with Google` (hidden until an OAuth client id exists), the signed-in account, a pending-write count, `Sync now`, and `Sign out`.
- 49 new tests, all JVM: `SyncMergeTest` (9), `SyncDocumentsTest` (12), `OutboxDrainerTest` (11), `SyncRepositoryTest` (16), plus one in `MaintenanceRepositoryTest`.

### Changed

- `ChildRepository`, `MaintenanceRepository` and `EventRepository` all queue an outbox intent and poke the drain after a write. Children queued nothing before this.
- `deleteChildPermanently` queues DELETEs **before** hard-deleting, then drops the dead UPSERTs. `archiveChild` queues its tombstones too.
- An import re-seeds the queue (`reseedAfterRestore`): `replaceAll` wipes the tables *and* the outbox, so without it the next snapshot would merge the pre-restore log back in. Stated limitation: server rows the backup does not contain are not deleted, because absence in a file is not a delete instruction.
- Firestore runs with a **memory-only cache**, so an offline `set()` does not ack and the outbox row survives. Room + `outbox` stays the only queue.
- `TextFieldRow` gained an `isPassword` mask, used only by the sync password field.

- `namespace` and `applicationId` moved together, with the Kotlin packages, imports, both source roots, the committed Room schema directory and the `MigrationTest` path that reads it.
- **Two children whose names start with the same letter no longer wear the same badge.** `badgesFor()` in `domain/ChildBadges.kt` is the single source for the switcher pills, the card and recent-row marks, the event list, the Growth chart's markers and legend, and the fever table's child column; it numbers a collision group in list order (`Kai`, `Kim` -> `K1`, `K2`) and leaves a letter that occurs once alone. The Both pill keeps the two raw initials (`KL`) — its label already says Both — and the seeded children still render `F`/`S`. Numbers follow the switcher's order (`sortOrder, name`), accepted as it stands: a child inserted earlier in that order renumbers the group it lands in.
- `ChildBadge` renders one glyph as the 20dp circle it always was, and two glyphs as a rounded pill of the same height with the type unchanged. Its doc comment claimed the children differ by `K` and `L`, which the numbering made false. The launcher label is unchanged. **A build from here is a new install identity**: it installs beside any earlier one, so an existing log moves across with Settings → Data → Export, then Import.
- The seeded children are `first-twin` / `second-twin`, named *First Twin* and *Second Twin*. The Growth badge is the name's first letter, so two names sharing an initial would have made the Both-mode badges identical.
- `Tokens.kt`'s palette provenance and `GrowthReference.kt`'s data citation now describe where the evidence sits without pointing at a path the app does not need.

## [2026-10-18]

**In short:**

- Health is real: a temperature, a medication picked from a bundled list with a typed dose, and vaccines on their own form. All six activities now write data, and the disabled `+ Add health … arrive in Phase 11` button is gone.
- **The schema changed for the first time, v1 → v2** — additive `doseAmount`/`doseUnit` columns, so History can name the medicines without carrying the amounts. The device's own database migrated with all 69 rows intact.
- **The app never suggests a dose.** The bundled list is names only, enforced by a test; the amount is always typed, and the last one is offered as a grey hint. A fever at ≥ 38.0 °C gets a filled `Fever` pill.
- **308 tests green** (255 → 308). The owner's own pass found the card **hiding a fever behind a newer medication** and the medication menu filling the whole screen; a new test caught `1 drops`. The pass is recorded in `PLAN.md`.
- **Trends is real too, the same day.** Five tabs now chart a per-day number over a `7d · 14d · 30d` window — bottle volume, solids, diapers and sleep, plus Growth's percentiles, which keep their own pane because they are plotted against age rather than against the last N days. Health gets a dated list of fever readings and a count of fever *days* instead of a chart. Both children share one chart: **solid for the first, dashed for the second**.
- **`0` beside a total of 13** was the first thing the phone found, and the screen was wrong: a count average kept no decimal. Now `0.4`, `0.1`, or `< 0.1` when the average genuinely rounds away. A count axis also ticked in fractions (`0.2` meals), and two shades of one hue did not tell the twins apart.
- **339 tests green** (308 → 339). The axis maths is shared with Growth in one file rather than copied, and every figure on the Trends screens was recomputed from the phone's own `events` table.

### Versions

- `0.8.0` — Phase 12, Trends
- `0.7.0` — Phase 11, Health

### Added

- `ui/home/HealthSheet.kt` — `Health record` / `Edit health record`: date, time, temperature (°C), a medication picker, a dose (amount + unit), notes. Save stays disabled while the record has nothing in it, and says which field is missing.
- `ui/home/VaccineSheet.kt` — `Vaccine` / `Edit vaccine`: date, time, the vaccine's name, notes. The name is required and is the whole record, because a vaccine has no reading to carry it.
- `ui/home/HealthEntry.kt` — everything that decides what a health form **stores**, as pure Kotlin. Plausibility guards of 30.0–45.0 °C, so a Fahrenheit reading typed into the Celsius box is refused rather than stored. The at-least-one-field rule, the seed-equality rule that keeps a stored value when a prefill is untouched, and `lastDoses()`, which finds the parent's own last dose for a medicine per child.
- `app/src/main/assets/medications/medications.json` — 14 commonly used infant medicines as **names only** (generic with a common brand in brackets), including the preemie supplements.
  - The asset's own `note` records that it is not clinically reviewed, and that it could not be checked against a live source in this session. Every name in it stays the owner's to change.
  - There are no doses, no frequencies and no schedules in the file. `MedicationCatalogueTest` fails the build if any entry contains a digit, and the sheet itself says `Names only — this app does not suggest a dose`.
- `data/medications/MedicationCatalogue.kt` — the `org.json` parser, with the reason a dose list must never exist written next to it.
- `domain/Dose.kt` — `DoseUnit` (ml, mg, drops, sachet, tablet, other) carrying a picker label and the word that fits the amount, plus `formatDose`/`doseNumber`.
- Medication presets in Settings (`health.medicationPresets`), a second editable list beside the bottle presets with the same add/remove shape.
- `feverOnCard` in the token set — `danger` lifted toward white 0.45 (dark) / black 0.30 (light) — and `FeverBadge`, the filled pill that carries the word. Both are used on the card headline, the Both-mode columns, the entry-list rows and History's Health total, with the measured ratios recorded in `design-tokens.md`.
- `MigrationTest` — builds a **v1** database from the committed `schemas/…/1.json`, so the test cannot drift from the schema it upgrades. It opens that database with the current Room build and asserts the old rows survive with empty dose columns.
- 53 tests, all green: `HealthEntryTest` (20), `DoseTest` (6), `MedicationCatalogueTest` (3) and `MigrationTest` (3) are new, and `CardSummaryTest`, `TotalsTest`, `SettingsRepositoryTest`, `MappersTest` and `BackupTest` were extended. The suite is 308.

#### Phase 12 — Trends

- `domain/Trends.kt` — `TrendRange` (`7d`/`14d`/`30d`, default 14), `TrendMetric` (volume, count, duration), `TrendPoint`/`TrendLine`/`TrendSeries`, `trendSeries()`, and `healthTrend()` for the fever table. Pure Kotlin taking `zone` and `now` explicitly, for the same reason `Totals.kt` does: a day boundary is the **local** calendar day and SQLite cannot resolve one, so bucketing in SQL would file an evening feed under tomorrow and draw a confidently wrong chart.
- `ui/trends/TrendChart.kt` — the per-day chart, drawn by hand like `PercentileChart`: one line per child, a dot per day, sparse date labels anchored to the newest day, and a zero floor.
- `ui/trends/TrendPanes.kt`, `ui/trends/HealthTrendPane.kt`, `ui/trends/TrendsViewModel.kt` — the chart pane with its range chips, legend and summary card; the Health table; and the state that observes the same stored child selection Activity and Summary use.
- `ui/charts/ChartAxis.kt` — `yRangeFor` and `yTicks` **moved** out of `ui/growth/PercentileChart.kt`, joined by `X_AXIS_GUTTER`/`Y_AXIS_GUTTER`. Two charts asking the same two questions now have one answer — the point `PercentileChart`'s own comment made when it predicted this phase.
- 28 new JVM tests: `TrendsTest` (23) and `TrendChartAxisTest` (5). The suite is 339.

### Changed

- `EventEntity`/`BabyEvent`/`EventDraft`/`Mappers`/`Backup` gained `doseAmount: Double?` and `doseUnit: String?`.
  - The backup carries both. A restore that lost the unit would restore `2.5` of nothing.
- `AppDatabase` is **version 2** with `MIGRATION_1_2`, two `ALTER TABLE … ADD COLUMN` statements, and a committed `2.json`. No destructive migration, as ever.
- The Health card's disabled pill became an accent-filled `+ Add health record for $childLabel` plus a neutral `+ Vaccine`.
- Both mode shows **one column per child** on the Health card too: that child's newest temperature, their newest medication and their newest vaccine.
  - These are the **latest of each kind**, not the newest entry, with the reading and the medication combined into one line when they came from the same record.
  - The column renderer is shared with Growth rather than copied.
- `recentRowLabel(HEALTH)` shows the reading and the medicine (`38.5 °C · Calpol 2.5 ml`), so a health row is self-describing and the entry behind the headline is one of the rows. The fever marker is not part of that string; it comes from `EntryRow`'s own flag.
- History's Health total reports `Records`, `Medication`, `Dose`, `Vaccine` and `Measured`, with the fever flag. A range holding only a medication is no longer reported as "Nothing logged".
  - Its rows are the latest of each kind too, so the reading and the dose shown can come from different records. That is the phase's own rule, and the card's row list is where the individual records are read.
- `decimalInput()` moved into `SheetControls`. The growth sheet and the health sheet were carrying the same three lines of keypad filtering.
- Docs updated in the same pass: `PLAN.md` (status row, as-built notes, device pass), the design documentation and `components.md`, the device screenshots and its `README.md` table with eight captures, and `AGENTS.md`'s stale unit-test count and two new rules.
- The middle tab `History` is now **`Summary`** — the same screen, the same `1d · 7d · 14d` ranges and the same `DateRange` icon. It reads as a summary over a range, and per-activity history is one tap away on each card's `See all ›`, so the old name described the one thing the tab was not. Four strings changed (`Destination`'s label and the delete confirmation in `AppScaffold`, `HistoryScreen`'s header, the Settings night-window caption), plus the tab name in the design documentation; `Destination.HISTORY`, `ui/history/`, `HistoryScreen`, `HistoryViewModel` and `HistoryRange` are deliberately unchanged, so no DAO, schema or test moved.
- The Summary filter row gained **one bulk chip**, first in the row, and `ui/history/HistoryFilter.kt` holds the rule for it: it reads `None` while every activity is shown and `All` once anything is hidden, so a **partial** selection reads `All` — reading `None` there would leave "show every activity" unreachable from a mixed row and the three states would stop cycling. Emptying the screen is now one tap, and the totals area answers with a plain card (`No activities selected` / `Pick at least one above.`) rather than a bare line that read as a section that failed to load. Session-only, like the six activity chips, and pinned by `HistoryFilterTest` (3 tests).
- The entry-delete confirmation's second sentence is now `You can undo it from the next message.` The wording asked for was "This action cannot be undone.", which would have been **false** while the 10-second Undo snackbar exists — the dialog names the Undo instead, and the Undo stays. The one dialog where that sentence is already true, Settings' `Delete <child> permanently`, was left alone.

#### Phase 12 — Trends

- `ui/trends/TrendsScreen.kt` is now the six-tab row over five chart panes and one table. The `Charts for … arrive in Phase 12` placeholder is **gone**, which supersedes the sentence in the Phase-11 verification bullet above: that was the state of the app when that pass was run, and this is the state now. The tab row, the scroll-state reset on a tab change and the insets are unchanged.
- `yRangeFor` gained `floorAtZero`: a per-day chart must start at zero, because eight nappies on a Tuesday and none on a Wednesday is a shape, while a weight curve starting at zero would flatten every curve into a line. Growth passes the default and behaves exactly as before — the two tests covering the axis maths now import it from `ui/charts/`.
- Count metrics take `integerTicks` rather than `yTicks`; see the fix below.
- Docs updated in the same pass: `PLAN.md` (Phase-12 as-built section and status row), `README.md`'s status line and scope row, the design documentation (`TrendChart` and the Health table) and `layout.md` (the Trends screen), and the device screenshots and `index.html` with nine captures.
- **The version ladder is now per phase.** `versionCode`/`versionName` had sat at `2` / `0.1.1` since the launch-crash fix, so eight phases of work reported one build identity and the number could not answer the only question it is ever asked — which build is on the phone. The history was rewritten so each phase's commits carry its own rung (`0.2.0` for Phase 6 through `0.8.0` for Phase 12, on sequential `versionCode` 3–10), leaving `0.1.0` and `0.1.1` alone because history already agreed with them. `versionCode` stays a sequential counter, so App Distribution will still see every build as newer. Nothing is tagged: tags are for releases, so the first will be `v1.0.0` when the release build is delivered, and until then the ladder lives in these `### Versions` lists.
- `scripts/android-env.ps1` and `scripts/sdkmanager.ps1` now **derive the SDK path** — `ANDROID_SDK_ROOT`, then `ANDROID_HOME`, then `%LOCALAPPDATA%\Android\Sdk` — instead of hard-coding one account's profile path.

### Removed

- **The do-not-build list is retired** — from `README.md`, `PLAN.md` (`Deliberate non-tasks` and the "settled: no photos, no widgets" decision with its three conclusions), `AGENTS.md` and the design documentation, along with the two pointers to it left in the design skill. The scope is still moving while the app is unreleased, so nothing is recorded as permanently excluded any more. The decisions worth keeping were design facts rather than prohibitions and they survive where they already were: temperature in °C only, `Wet / Dirty / Mixed` with a rash toggle, and an export that stays plain text.

### Fixed

- **`1 drops`.** The unit label was doing two jobs, the picker's and the dose's, so a dose of one read `1 drops` on a fever card.
  - `DoseUnit` now carries a picker label plus singular and plural. A fraction takes the singular (`0.5 sachet`) and anything above one takes the plural (`1.5 sachets`, `2 sachets`). A new `DoseTest` pins it.
- An amount typed with **no unit** is an error rather than a silent drop. The parent typed 2.5, and saving the record without it would be the log changing under their hands. A unit with no amount is simply no dose, so a half-filled picker can never block a save.
- **The owner's own pass found four defects, all reported and fixed the same day.**
  - **The card was hiding the fever.** Its summary held ONE "record" slot, temperature OR medication, so a medication logged at 12:27 took it from the fever logged at 12:08. The fever was not dim on the card, it was absent from it, and only History still showed it. It is now the latest of **three** kinds, pinned by `a later medication does not hide an earlier fever`.
  - **The marker was a tinted number, and he could not see it.** A pastel red on a magenta card, beside a label that is itself a lifted magenta, is not a signal. `feverOnCard` had been derived for that tint — `danger` fails AA on it at 26%, measuring 3.76:1 dark and 4.28:1 light, while `feverOnCard` clears 4.80:1 on all six tints — and it was still invisible. `FeverBadge` puts the word in a `feverOnCard`-filled pill — 10.39:1 dark / 10.30:1 light for the word on the fill, 5.71:1 / 6.75:1 for the fill against the card — and the word is no longer part of any label string, so a narrow row cannot ellipsise it away. On the phone the same band went from 3,762 pixels of tinted text, with no `textPrimary` pixels in it, to **10,800 pixels of `#FFB6B6`** plus 928 of `onAccent` for the word inside.
  - **`Type another name…` kept the previously selected name and left the cursor nowhere.** It now clears the field and requests focus. Verified by typing with no tap: the field's own `EditText` node reports `focused=true` and the text lands in it.
  - **The medication menu filled the screen.** Fourteen shipped names plus the family's own ran from the status bar to the navigation bar and hid the form it belonged to. It is capped at 320dp, about five rows, and scrolls inside that; two swipes still reach the last name and `Type another name…`.
- **The first build of this phase failed with 59 "unresolved reference" errors** in files nobody had touched. The incremental Kotlin cache had gone stale, and clearing it reproduced a clean build. Recorded because the errors named `ActivitySheet`, `rememberNow`, `accentFor` and `ChildBadge`: they looked like a catastrophe and were a cache.
- **Verified on the S23 Ultra (Android 16, dark), and the pass is recorded in `PLAN.md`:** `:app:assembleDebug` builds and `:app:testDebugUnitTest` is **308/308** with 0 failures and 0 errors. Both-mode health and vaccine saves each wrote **two rows with an identical timestamp**. The medication dropdown lists the shipped names plus `Type another name…`. The last-dose hint reads `Last recorded — First Twin 2.5 ml · Second Twin 2.5 ml` while leaving the dose field **empty**. History shows the fever reading in `feverOnCard` with separate Medication and Dose rows. Trends' Health tab keeps its honest Phase 12 sentence. System back closes both sheets, and `logcat -b crash` is empty.
- The device's own database was read before and after the install: `user_version` 1 → 2, 22 → 24 columns, **69 rows before and after**, and the two legacy health rows still read `38.5 °C` / `Calpol 2.5 ml` with `doseAmount = NULL` rather than `0`.
- **`PLAN.md` read two ways at once.** The Status table's Phase-11 row said **308 tests green** while the Verified line under the same table said **311/311**, and Phases 0-2 had carried no evidence at all since their body sections were collapsed into that table. The table's counts are now named for what they are — the suite size when each phase landed, with the three `HistoryFilterTest` cases named as the delta — and a **Phases 0-2 evidence** section points at the research notes, the scaffold and domain commits (the "feat: scaffold Compose app, get a green debug build" commit, the "feat(domain): event model, relative-time/duration formatting, day grouping" commit, the "feat(domain): unit system with exact conversions and compound lb/oz formatting" commit) and the theme files, so those three phases are traceable again without un-collapsing the table.

#### Phase 12 — Trends, found on the phone after 338 tests were green

- **`0` printed beside a non-zero total.** Thirteen nappies across thirty days averaged to `0 per day`; two solids over thirty days read the same way. A summary card contradicting its own total is how a parent stops trusting the screen, and it was one line of formatting: a count was rounded to a whole number in the average as well as on the axis.
  - The average now keeps one decimal (`0.4`, `0.1`), and a non-zero average that still rounds away prints `< 0.1` instead of claiming nothing happened. A count of exactly zero still prints `0`.
  - Pinned by `a count keeps a decimal in the average, so a real number never prints as zero`.
- **A count axis ticked in fractions.** With one meal in a 14-day window `yTicks` chose a 0.2 step, so the axis read `0.2`, `0.4`, `0.6` meals — numbers that cannot exist. `integerTicks` keeps whole gridlines and falls back to `0` and the rounded maximum when the whole ticks collapse below two lines.
- **Two shades of one hue did not tell the twins apart.** Both lines are the activity's own accent, one lifted, and on the phone the difference was not readable at a glance. A second hue is not available — an accent means one activity and nothing else — so the distinction is carried by **shape**: the first child's line is solid, the second's is dashed, and the legend draws a sample of the line rather than a dot so the swatch cannot hide half the cue. Same reasoning as the fever pill.
- **A label-spacing bug the unit test caught before the phone did.** Appending the newest day on top of a 2-day step put two labels one day apart on a 14-day window — 21dp apart for a label that measures ~33dp — so `11 Oct` and `12 Oct` would have overlapped. Labels are now counted **back from the newest day**, which keeps them evenly spaced and always names today.
- **Every figure was then checked against the device's own database**, not just the screen: `run-as` read the phone's `events` table and recomputed the totals, the days logged, the best days and the fever days, and all of them matched. Two earlier attempts to read that file were misled by the tooling and both are worth recording: a copy taken without its `-wal` file replays as `malformed database schema`, and `adb shell cat` corrupts a binary with CRLF translation — `adb exec-out` is the binary-safe path.

## [2026-10-15]

**In short:**

- Growth became real: WHO's LMS reference ships inside the APK, and a measurement now reads back as a percentile with its age, on a chart.
- The bundled parameters were checked against WHO's **own** published tables, not against the mirror they were downloaded from — every row, both sexes: the L/M/S are identical, and the app's transform reproduces WHO's printed SD columns to 0.0005.
- **INTERGROWTH-21st was dropped**, not deferred: its data carries reserved rights rather than an open licence, so a measurement taken before term gets no percentile and a sentence saying why.
- The dataset update channel moved to Phase 13, because the Firestore it assumed does not exist yet — nothing was stubbed or faked in its place.
- Seeing it on the phone changed the design: the chart moved into **Trends** as a tab per activity, the Both-mode summary became **one column per child** instead of a combined headline, values gained `W`/`L`/`H` codes, and the highlight box became one per child rather than whichever child won a name-order tie.
- Three layout defects were found only by looking at the screen — an untappable chip row under the status bar, a pane overlapping it whose end could not be scrolled to, and a button that silently did nothing. **255 tests green**, and the pass is recorded in `PLAN.md`.

### Versions

- `0.6.0` — Phase 10, Growth

### Added

- `app/src/main/assets/growth/who-igrowup.json`: WHO's five `igrowup` LMS tables (weight-for-age, length/height-for-age, head-circumference-for-age, BMI-for-age, weight-for-length/height), both sexes, 16,158 rows as compact arrays with an identity block (`version`, `publishedAt`, `retrievedAt`, citation, per-file `sha256` and source URLs). 488,449 bytes, asset sha256 `2df347597249d27490e73ae7ca8cce956d5ee1141dfcf1a84fec7fa1f518cb5c`.
- `scripts/build-growth-assets.py`: downloads the five tables from the MIT-licensed `ki-tools/growthstandards` mirror, verifies each file's sha256 against the research notes, asserts the shape the lookup depends on (age tables daily 0..1856 with day == row index; the weight-for-length table 45.0-110.0 cm in 0.1 cm steps) and fails loudly on drift rather than regenerating over it. Committed so the asset is regenerable and auditable, the same reason the Room schema is.
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
- The notes behind the growth data were corrected on three points: the INTERGROWTH repo path (`Yannngn/pygrowthstandards`, not a `pygrowthstandards` org that 404s), the `wflanthro` axis (45.0-110.0 cm in 0.1 cm steps, where the file's own header says something else), and that those tables are SD columns rather than LMS.

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
- **Later the same day:** the activity cards were corrected after use — a card now lists today's entries (up to five, and the feed behind the big number is one of them), `See all ›` opens every entry of that activity grouped by day, and any row can be edited in place or long-pressed to delete with an Undo. Verified on the S23 in the same pass; the capture is the device screenshots, including the three `before/` shots that reported the defects.

### Versions

- `0.5.0` — Phase 9.1, cards, See all and in-place edits
- `0.4.1` — Phase 9, daily-use polish
- `0.4.0` — Phase 8, Summary totals
- `0.3.0` — Phase 7, Settings
- `0.2.0` — Phase 6, the logging sheets

### Added

- The Phase 6, 7, 8 and 9 device screenshots — 32 captures (9 + 8 + 6 + 9), with `index.html` sections 07-10 and the matching rows in its `README.md`.
- The Phase 9.1 device screenshots — 10 captures plus the three `before/` JPGs that came from the phone as the defect report, with `index.html` section 11 and the matching rows in its `README.md` (the "docs(timeline): record the Phase 9.1 device pass" commit).
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
- Card action labels are context-aware from `HomeUiState.childLabel`: `+ Log feed for First Twin` / `+ Log feed for both` (`ui/home/ActivityCard.kt`).
- `AppScaffold` hosts the sheet as a sibling of the `Scaffold`, drawn over the bottom navigation bar so a full-screen sheet is never clipped by it (`ui/AppScaffold.kt`).
- `AppRoot` seeds the food catalogue alongside the two children (`ui/AppRoot.kt`), with `AppContainer` exposing `foods` (`di/AppContainer.kt`); `AppStartupTest` seeds the catalogue through the real container asset path.
- `README.md` and `PLAN.md` status corrected, and `PLAN.md` gained a Phase 6 device-verification section.
- `PLAN.md` phases renumbered to seat the two new ones: History 7→8, Growth 8→9, Health 9→10, Trends 10→11, Two accounts 11→12, Ship 12→14, with the Growth tasks renamed 8.1-8.5 → 9.1-9.5. Live references updated in `ui/home/ActivityCard.kt`, `ui/AppScaffold.kt`, `ui/home/HomeViewModel.kt`, `data/local/OutboxDao.kt`, `data/local/OutboxEntity.kt`, `data/repo/EventRepository.kt`, `ui/Placeholders.kt`, `README.md`, the research notes, the design documentation and the device screenshots.
- `PLAN.md` records sleep's hue as settled (it stays `#63C8FF`) and drops the stale `no export` line from the do-not-build list, since a JSON export now belongs to Phase 7.
- First-run seeding moved behind a one-way `SettingsRepository.seedChildrenOnce` flag. The old guard was `childDao.count()`, which filters `deleted = 0`, so removing every child made the app look fresh and would resurrect the twins on the next launch.
- The **Both** pill now appears only at exactly two children. `ChildSelection.resolve` falls back to the first child in sort order, `isValidFor`/`fallbackFor` express the rule, and `HomeViewModel` persists the fallback when a child is added or removed.
- `AppScaffold` renders the real Settings screen, owns the export/import launchers, and adds the bottom navigation inset for Settings, which has no bottom bar of its own; `AppContainer` exposes `maintenance`; the Settings placeholder is gone from `ui/Placeholders.kt`.
- The design documentation: `danger` is documented, and the stale "cards are NOT tinted" section was corrected to the shipped 26% tint with its measured derivations.
- History replaced its placeholder: a `1d · 7d · 14d` calendar-day range selector, multi-select filter chips (session-only, all shown by default), and six total cards that carry a per-child line in Both mode. Growth and Health report the latest measurement in range rather than a sum.
- `ActivityScreen` no longer builds its own header; both Activity and History use `ScreenHeader`, per the contract's "keep it identical on Activity and History".
- Log rows show the child badge and the VOLUME for a bottle. `recentRowLabel` is deliberately a separate function from `detailLine`, which still shows the brand under a card's big value — the two questions differ.
- The bottle amount is free text in the display unit plus preset chips; the stepper is gone, because it cannot express 135 ml.
- Bottle, Diaper and Solids sheets gained a Date row and a `for First Twin` / `for both` subtitle.
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
- **The Both card's numbers read as wrong, and the data was fine.** Second Twin's 14:14 feed was the headline on Second Twin's card and a row on the Both card, and the two lists could not be compared because the headline had swallowed one entry. The headline now carries the child's badge and the absolute time whenever more than one child is in scope, and the rows include it, so Second Twin's 13:09 feed is visible in Both mode.
- **Rows wasted their space** — 8dp of vertical padding around a 20dp line of text in a 35dp row, plus an 18dp `K` badge on every row of a single-child card. Now 5dp, a 16dp badge in Both mode only, and an 8dp gap above the block.
- **Two rows written by one Both-mode tap could swap places.** They share a `startedAt`, and `ORDER BY startedAt DESC` alone left their order to SQLite; one comparator now resolves ties by the configured child order.
- **The delete Undo was unreachable.** `SnackbarDuration.Short` is four seconds, and it expired between a UI dump and a tap, which landed on the Diaper card underneath — nothing was written, but a finger has the same four seconds. It is now `Long` (ten), per Material's own guidance for a snackbar carrying an action, and the Undo was re-verified inside the longer window. Found during the device pass, not by the build.

## [2026-10-12]

**In short:**

- The project went from an empty repo to a device-verified Activity screen in one day: toolchain, scaffold, domain, Room v1, repository, and the first visible build.
- Two bugs that no JVM or Robolectric test could catch were found by putting the app on a phone, and both were fixed that afternoon.
- The design contract was corrected against real screenshots, and the card tint was dropped then restored at a measured 26%.
- The record became durable: the screenshots, with the rejected accent treatments kept alongside the accepted one, and `AGENTS.md`, so an outside agent can pick the repo up.

### Versions

- `0.1.1` — Phase 5, the launch-crash fix
- `0.1.0` — Phase 1, the first scaffold

### Added

- The repository: the initial commit, a 27-line `.gitignore` for environment secrets, dependencies, build output and editor/OS cruft.
- The research notes behind the design: teardowns of the two apps this one is measured against, from 24 Play Store listing frames, source URLs with the failed de-skew attempt recorded, and the design system derived from them as a loadable project skill (the "docs: research teardowns, design system, scope decision and v1 plan" commit).
- `README.md`'s v1 scope decision, and `PLAN.md` — the phased plan with Phase 0's toolchain in it (the "docs: research teardowns, design system, scope decision and v1 plan" commit).
- Toolchain record: AGP 9.4.1, Gradle 9.8.1, Compose BOM 2026.09.00, JDK = Studio's bundled JBR 25.0.3, and the three AGP 9 facts that cost build cycles — the `kotlin.android` plugin must NOT be applied, `compileSdkMinor` is required for android-37.2, and `jvmToolchain(17)` fails on a JBR-only machine (the "feat: scaffold Compose app, get a green debug build" commit, recorded in the research notes).
- `scripts/android-env.ps1` and `sdkmanager.ps1`, so the toolchain environment is reproducible without quoting gymnastics (the "feat: scaffold Compose app, get a green debug build" commit).
- `domain/` — `EventType` and `BabyEvent`, relative-time and duration formatting, and day grouping. Formatting is pure (`now` and `zone` are passed in, never read from the clock), so it is testable headlessly and cannot fail at midnight. 12 tests green (the "feat(domain): event model, relative-time/duration formatting, day grouping" commit).
- `domain/Units.kt` — the three independently selectable unit families (length in/cm, weight lb&oz / kg, volume fl oz / ml) with Metric/Imperial/Custom presets.
  - Storage stays canonical metric and conversion happens only at the display and input edge, so changing the setting never rewrites stored values and percentiles always compute in one system; the preset is derived from the three choices rather than stored.
  - Celsius only, deliberately: there is no Fahrenheit path, because a temperature in F is not a unit preference, it is a different number waiting to be misread during a fever.
  - 25 tests green, including the compound-unit carry that 440 g → "1 lb 0 oz" depends on (the "feat(domain): unit system with exact conversions and compound lb/oz formatting" commit).
- Room schema v1 — four entities (children, events, foods, outbox), four DAOs, pure entity↔domain mappers and a food-id codec, with the exported schema committed as source at `app/schemas/app.babylog.data.local.AppDatabase/1.json` (the "feat(data): room schema, DAOs, mappers and 50 passing tests" commit).
  - 50 tests green, the DAO ones on the JVM via Robolectric 4.17 — which is what keeps the deliberately skipped emulator from leaving the schema unverified, since a schema mistake would otherwise only surface on a phone.
- `EventRepository`, the only writer, plus `SettingsRepository` over DataStore and `Age.kt`; 78 tests green (the "feat(data): repository, settings, and adjusted age - 78 tests green" commit).
  - `log()` takes a **list** of child ids, so Both mode is one draft → N rows sharing an identical timestamp, with a test that duplicate ids in the request do not double-write.
  - `ageDaysForCharts()` is a named function rather than an inline subtraction, because born early the adjusted offset is 70 days — the difference between ~9m1w and ~7m1d, and getting it wrong yields a confident wrong percentile with no error.
- `AppScaffold`, `ActivityScreen`, `ChildSwitcher`, `ActivityCard`, `HomeViewModel`, a manual DI container, and a first-run seed of the two children (the "feat(ui): activity screen with child switcher - first visible build" commit).
  - Recorded at the time as layout-**UNVERIFIED**: it compiles and the APK builds, but overflowing cards, clipped text and a wrapping pill row all compile cleanly, so a green build says nothing.
- An adaptive launcher icon, and the four tests that cover what a build cannot: `AppStartupTest`, `MainActivityLaunchTest`, `CardBuilderTest`, `CardSummaryTest`; 97 tests green (the "fix(app): add the missing launcher icon; test the paths a build cannot check" commit).
- A durable visual history of the device screenshots, with an `index.html` gallery and `scripts/capture-timeline.ps1`, keeping the two **rejected** accent treatments as well as the accepted one (the "feat(ui): tinted sections, on-card text tokens, top scroll scrim" commit, the "chore(scripts): add capture-timeline.ps1 referenced by AGENTS.md and the timeline README" commit).
  - The scratch directory those screenshots had been written to was pruned after 24h, so nothing was durable before this.
- `AGENTS.md` — the repo now carries its own agent instructions: the stack, the measured design contract, the device-verification loop, and the traps that have already cost us, so an outside agent can pick this up without the session context (the "feat(ui): tinted sections, on-card text tokens, top scroll scrim" commit).
- The provenance notes for WHO's igrowup LMS tables, verified by downloading and parsing rather than by recall, with row counts, sex codes and sha256 recorded (the "docs(growth): record verified WHO/INTERGROWTH data sources and design the dataset update path" commit).
  - The age axis is **days** (0-1856), proven by spot-check: boys weight-for-age at "age 12" gives M=3.671 kg, which is a 12-day-old. Reading that axis as months would produce catastrophically wrong charts, so the unit is documented explicitly.

### Changed

- The design system was corrected against 15 real screenshots of an existing tracker, which falsified two conclusions from the marketing-frame teardown (the "docs: correct the design system against real screenshots; rescope for twins" commit):
  - The existing app runs dark, so the original light-only, white-card palette described an app that does not exist. Dark values are now sampled by pixel clustering; light is designed and labelled as designed rather than presented as measured.
  - The diaper accent is a muted sage, not yellow.
- Scope was rewritten for twins: two children (the twins, a date set in Settings, editable) with a header child switcher and a **Both** mode that logs to both in one action, per-child premature weeks and adjusted age, and bottle/solids/diaper/sleep/growth/health (the "docs: correct the design system against real screenshots; rescope for twins" commit).
  - Three defects in the existing app became hard design REQUIREMENTS rather than polish: navigation-bar insets (it clips its Child screen), a `BackHandler` on every sheet (the incumbent's back exits the app), and both children visible.
- `PLAN.md` was rewritten in the same pass — completed Phases 0-2 collapsed to status rows and the plan renumbered, which is why the plan body now begins at Phase 3 (the "docs: correct the design system against real screenshots; rescope for twins" commit).
- The six activity accents were replaced with six well-separated hues, chroma up 2-5x (the "feat(theme): brighter, distinct accents; drop the card tint" commit).
  - Measured on the sampled palette, Solids (hue 35) sat 8 degrees from Bottle's gold (43) and Growth (85) sat 9 degrees from Diaper (76): two of six activity colours were effectively the same colour as another.
- The card tint went and came back, both times by measurement rather than taste (the "feat(theme): brighter, distinct accents; drop the card tint" commit, the "feat(ui): tinted sections, on-card text tokens, top scroll scrim" commit).
  - Dropped because on a dark surface a tint collapses the accents to chroma 0.093 — the olive-brown and maroon that was reported.
  - Restored at **26%**, which is measured: 0.16 is only dE 7.7 apart (barely separable), 0.26 is dE 12.5, and above 0.30 the accent-filled button starts washing out.
  - The raw accent is NOT legible as a label on its own tint (3.4-4.3:1), so labels use the new `labelFor()` (6.4:1) and meta text the new `textOnCard` token (4.7-5.8:1, against `textSecondary`'s 3.06:1). A 4dp accent bar and a 6dp top band were both built and rejected first.
- Stray `MaterialTheme.colorScheme` defaults were removed from `RelativeTimeText`, `LiveTimerText` and `BigValue`, because they are tuned for a neutral surface and measured under 4.5:1 on a tinted card — that was the dim "just now" on the olive and green cards (the "feat(ui): tinted sections, on-card text tokens, top scroll scrim" commit).
- Content scrolled under the transparent status bar and collided with the clock, because the insets are applied as `LazyColumn` contentPadding and items therefore scroll THROUGH the inset region rather than stopping below it (the "feat(ui): tinted sections, on-card text tokens, top scroll scrim" commit).
  - The list state is now hoisted into `AppScaffold` and a scrim fades in behind the status bar once the list has moved: solid across the bar, fading out below it, no hard seam. Blur was considered and deferred.
- Post-build change cost became a constraint the plan has to satisfy, not a chat answer (the "docs: define what post-build changes cost, and constrain v1 accordingly" commit): additive-only migrations from schema v1, export/backup in Settings as the highest-value insurance against a bad migration, and update delivery by tap rather than adb on two phones.
- Photos and widgets were settled as permanently out of scope, which removes the last expensive retrofit and keeps the schema, the sync payload and the backup text-only (the "feat(data): room schema, DAOs, mappers and 50 passing tests" commit).

### Removed

- `HomeViewModelTest` and `kotlinx-coroutines-test` (the "fix: launch crash on device, and a timer on every card" commit).
  - The test hung: Robolectric's paused main looper never pumps Room's invalidation callbacks, so a database-backed Flow cannot be collected in that harness. Better deleted than left as a 4-minute no-op that looks like coverage.
- `EventType` nursing and pumping, removed rather than reserved: an unused variant with no accent, no card and no sheet is dead weight, and adding one later is an enum value plus UI (the "feat(data): room schema, DAOs, mappers and 50 passing tests" commit).

### Fixed

- **Crash on launch, on every start, on the device** (the "fix: launch crash on device, and a timer on every card" commit).
  - `ChildSelection.default` was a stored companion `val`. On Android, ART initialises the enclosing interface — and therefore its companion — WHILE `Both` is still being constructed, so the initialiser read `Both`'s static `INSTANCE` field before it was assigned and stored null permanently; `resolve()` then died with an NPE on every start.
  - Fixed by making it computed: `val default: ChildSelection get() = Both`. HotSpot initialises superinterfaces more lazily and sees the field already assigned, so this was invisible to all 97 JVM and Robolectric tests. Only the phone caught it, and it is now recorded in `AGENTS.md` as a trap.
- **A live timer on every card** (the "fix: launch crash on device, and a timer on every card" commit). `isRunning` was `endedAt == null`, which conflates "still in progress" with "a point event that has no end", so every logged bottle, diaper, weigh-in and temperature rendered a count-up timer with a Stop button. Fixed with `EventType.hasDuration` (true for SLEEP alone) and `isRunning = type.hasDuration && endedAt == null`.
- A guard that was silently compiled away: `combined.second ?: ChildSelection.Both` is constant-folded to nothing when the left operand's declared type is non-null, so the first attempt at the check did not exist in bytecode. Widening to a nullable local first makes it real; noted in the code so nobody repeats it (the "fix: launch crash on device, and a timer on every card" commit).
- The app shipped with **no launcher icon**: `res/` held only `themes.xml`, the manifest had no `android:icon`, and `aapt` reported `icon=''` (the "fix(app): add the missing launcher icon; test the paths a build cannot check" commit). Added an adaptive icon — bottle silhouette in the feed gold on the real background `#181A26`.
- `buildCards()` assumed its input arrived newest-first, as the DAO happens to return it, so a wrong "last event" was wrong on the card and invisible in review. It now sorts internally, with a test asserting both orderings agree (the "fix(app): add the missing launcher icon; test the paths a build cannot check" commit).
- `elapsed()` rendered sub-minute durations as "0m 45s"; the assertion was right and the implementation changed (the "feat(domain): event model, relative-time/duration formatting, day grouping" commit).
- `capture-timeline.ps1` resolved the phone's serial as `'R'`, from two stacked bugs (the "fix(scripts): device detection returned a scalar, so adb waited forever" commit): a PowerShell pipeline that yields ONE item assigns a scalar, not an array, so `$devices[0]` returned its first CHARACTER while `$devices.Count` still read 1 and the ambiguity guard passed; and nothing re-validated the serial, so `adb -s R` blocked until the caller timed out.
  - Wrapped in `@()`, every serial is now checked against `adb devices` so a bad one fails immediately, and screenshots go to `/sdcard` to come back via `adb pull`, because PowerShell decodes a native command's stdout as text and corrupts the PNG.
- Two of our own test expectations were wrong and the implementation right (the "feat(domain): unit system with exact conversions and compound lb/oz formatting" commit): 4500 g is 158.73 oz, rounding to 159 = **9 lb 15 oz**, not 9 lb 14 oz; and 440 g rounds to 16 oz and therefore MUST carry to "1 lb 0 oz", now covered on both sides of the boundary (430 g stays "0 lb 15 oz").

Verified on a Samsung S23 Ultra (Android 16 / API 36): launches clean, taps log correctly, no timer on point events, the Sleep card clears the nav bar at full scroll, and system back returns to Activity instead of exiting. 103 tests green at the end of the day.
