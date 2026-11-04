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
| 13 — Two accounts, one shared log | **DONE, device-verified** — two accounts on one shared log, an outbox that is the only queue, an inbound listener that applies another device's rows, and tombstones that stay dead across the hop; 398 tests green. Three passes on 2026-10-22: an unconfigured build (`<handset>`) proved launch, the "not set up" card, every write path queueing and a permanent delete queueing DELETE intents; the configured build proved sign-in, the seed draining, a live ack in under five seconds, and three airplane-mode writes arriving exactly once; then a wipe plus a fresh sign-in pulled the whole log back by row id, and a **second device signed in with the second account** (`emulator-5554`, uid `B43h…`) received the phone's six rows, took a bottle each way, took an offline trio as exactly three rows with no duplicates, and lost a row when the phone deleted it — still gone after that device's process restarted. Runbook: `docs/firebase-setup.md`. The later offline `Sync now` and first-sign-in seed findings were fixed in Phase 14 |
| 13.1 — Time and temperature preferences | **DONE, device-verified** — independent 12/24-hour and °C/°F display/input choices, canonical storage unchanged, 409 tests green |
| 13.1.1 — Twelve-hour default | **DONE, device-verified** — fresh installs and no-choice fallbacks use 12-hour time without migrating an existing saved choice; `0.10.1`; 409 tests green |
| 13.2 — Temperature bands | **DONE, device-verified** — four bands marked by shape (filled `Fever`, outlined `Mild` / `Low`, no pill on normal), classified from the value as displayed; Trends lists and counts mild readings too; `0.11.0`; 417 tests green |
| 13.3 — Diaper detail: Wet/Dirty toggles, colour, consistency | **DONE, device-verified** — two independent answers, colour as six ringed circles, consistency as seven silhouettes, schema v3 additive migration; the `Mucousy` glyph read as a `T` at 16dp, kept open here and redrawn in Phase 14.6; 444 tests green |
| 13.4 — Activity reminders | **DONE, device-verified** — a reminder per child on the four activities that get forgotten (Bottle feed, Solids, Diaper, Health), set from a `⋮` on the card, one chip per child showing the time it will ring, an `Upcoming reminders` card, and an exact notification that names the child and opens that child's sheet; 515 tests green |
| 13.5 — Growth dataset update channel | **DONE, device-verified** — manifest-gated fetch, SHA-256 verification, app-private cache, 90-day silent check, manual check rows on Growth and Settings; bundled dataset stays on every failure; `0.17.0`, and `0.17.1` for the defect the pass found; 538 tests green. **No prerequisite.** Keeps this number: three commits on `main` already say `phase13.5`, so renaming it would mean rewriting history — see *Renumbering* below. **Known gap: an accepted dataset cannot be withdrawn** — see the note under the phase |
| 14 — Polish/Misc changes | **DONE, device-verified** — the eight findings the owner ruled on: the silent `Sync now` and the seed that waited for a drain (both fixed and tested), the `Both` wrap at 130 %, the solids food id, the Trends chip gap, two redrawn consistency silhouettes, the activity glyph on five surfaces, per-metric marks on Growth's chips, and `D · 1W · 2W` / `1W · 2W · 1M` on the two range rows, plus 14.10 — an amount-less bottle says `No amount recorded` instead of repeating its own title; `0.16.0`; **532 tests green**. A3 deferred, A2/A4/B7-clipping/C12 closed. **No prerequisite** |
| 15 — Reset to defaults | **DONE, device-verified** — two buttons in Settings → Data: *Reset local data* (tombstones for every row, hard delete, preferences and reminder rules cleared, alarms cancelled) and *Delete all server data* (tombstones every id the server reports, then the same reset, offered only while signed in); `0.18.0` / versionCode 23, and `0.18.1` / versionCode 24 for the five defects its own two passes found; **549 tests green as it landed** (the suite is 658 today — see *Verified*). **No prerequisite.** Was 13.6. Its passes found the inbound-listener race (`withoutInboundApply`), the drain that stopped at the first slow write, the twins that came back from the server into an emptied phone, and the dead cards under the empty state |
| 16 — Update delivery | **DONE, device-verified** — `1.0.0` / versionCode 26 is the first release: a release keystore the family owns (created by `scripts/create-release-keystore.ps1`, password typed at a prompt, kept outside the repo and outside Syncthing), a `release` signing config that only exists when `keystore.properties` does — so a clone without the key still tests and builds, and `assembleRelease` degrades to an *unsigned* APK that fails `apksigner verify` — a manual Firebase App Distribution upload, and the certificate's SHA-1 registered on the `app.babylog` Android app. The phone installed `versionCode 26` over the air from the App Distribution package installer, byte-identical to the built APK (SHA-256 `4dac7cfb…43bca`), signed `CN=BabyLog` / `07:66:EE:…:4F`, with all 219 live events restored on sign-in. First tag: `v1.0.0`. **565 tests green, unchanged — this phase shipped no new Kotlin.** Google Sign-In was confirmed on the release build by the owner at close-out, and the second phone's install stays the standing gap |
| 17 — Five todo items | **DONE, device-verified** — a child selector and `1Y` (52 weeks) / `All` (months, from the first event) buckets on Trends, an About card reading `BuildConfig`, per-activity hiding whose reminder prompt cancels the rule only when accepted, and haptics from one helper; `1.1.0` / versionCode 27, installed in place over `1.0.0` with the log intact at 219 live events; **584 tests green**. Two pre-existing defects found and recorded: the reminder sheet opens with `Notify` off, and tapping the disabled `Set` closes it. **Was 18 in the planning of it** — see *Renumbering, 2026-10-29* |
| 18 — Published food catalogue | **DONE, device-verified** — `1.2.0` / versionCode 28; published owner catalogue overrides the bundled list, pending additions sync through the outbox, local names/hidden state stay local, and schema v4 keeps those opinions through backup/reset; **611 tests green**. Single-phone UI verified; Firebase publication and second-phone sync remain unverified. Was 19 in the planning of it |
| 19 — Settings submenus | **DONE, device-verified** — `1.2.1` / versionCode 29; the eleven settings groups became a category menu, each row summarising its own current value, and each name opens that group alone. Back now means "up one level" before it means "leave Settings", and the rule lives in `SettingsNav` rather than in the handler. No setting moved between groups and no control changed; 620 tests green. **Two defects its own pass found**, both fixed and re-verified on the phone: `2 childs` on the Children row, and a growth-reference value that wrapped to two lines and made its row taller than the rest. Installed in place over `1.2.0` with the log, both children, the presets and the sign-in intact |
| 20 — Tracking changes | **DONE, device-verified** — `1.3.0` / versionCode 30; bottle presets are one-tap log pills on the card (the Diaper path, borrowed) with the full-width button kept for a custom amount, a Both-mode card and its rows name the child instead of showing `K`, the reminder sheet drops `Notify` and offers `Remove notification`, and a posted reminder is cleared when its activity is logged or its rule goes off; **635 tests green**. Its pass was read-only on purpose — one tap writes a real feed, so that check is the owner's. Was 20 before the Settings redo |
| 21 — Time picker | **DONE, device-verified** — `1.4.0` / versionCode 31; the one time dialog in the app gains a switch between the clock dial and a wheel built here in Compose, keeping the two-field keyboard it shipped with as the third; **645 tests green**. The switch is in the dialog rather than in Settings (the owner's choice) and nothing is persisted, so it opens on the dial every time. The dial's width worry is settled by measurement: complete at font scale 1.0 and 1.3. Was the Filled Soft redesign before the owner asked for this first |
| 22 — Filled Soft redesign | **DONE, device-verified** — `1.4.1` / versionCode 32; the tiled stroke-glyph watermark is gone, replaced by a per-activity vertical gradient card fill and a two-layer decoration: the activity's illustration as a whole sticker, with scattered silhouettes and hearts spread across the entire card behind it (nine new `ic_soft_*` drawables); **658 tests green**. Two decoration-placement defects were found by measuring the phone's own pixels rather than by looking (a pacifier drawn inside the illustration's silhouette, a heart behind the opaque pill row), and the owner then corrected the design itself on 2026-11-02: the scatter must cover the whole card, not a 76dp band, and the sticker must read as a whole object. The revision was verified on the phone on 2026-11-03 in both themes — every card's geometry read from a `uiautomator` dump and its artwork counted by colour in a `screencap`. **One defect is knowingly open:** on a card with nothing logged the illustration is drawn underneath its first control, because the 48dp band that fixed it was rejected in favour of the previous, more compact cards. See the Phase 22 section |
| 23 — Ship | not started — publish the Phase 18 Firestore rules/catalogue, deliver `1.4.1` over the air, verify both phones, then use it for a week before more code. Was 20, then 21, then 22 |


**Off the ladder:** the Huckleberry import of 2026-10-28 has its own dated section below and **no row here**,
because it shipped no application code and so carries no version rung — a phase number whose build does not
exist would break the one thing this table is for. It is recorded in full under
*Huckleberry import — data and tooling*.

**Versions: one rung per delivered phase**, and the ladder is a fact of the history rather than a
reservation for unstarted work — `versionCode`/`versionName` were rewritten so each phase's commits
carry that phase's rung, set at the **first commit of the phase's work** so a build on a phone says which
phase produced it. `0.MINOR` is a new capability, `0.PATCH` is fixes and polish only, and `1.0.0` is
the first release. **Landed rungs are never renumbered**, even where a phase's number moved afterwards:
Phase 14 is `0.16.0` / versionCode 20, Phase 13.5 is `0.17.1` / versionCode 22 and Phase 15 is `0.18.0` /
versionCode 23 with `0.18.1` / versionCode 24 as its two passes' fixes, so the ladder rises in **landing**
order rather than in phase-number order — and a phase that is amended by its own device pass keeps the one
rung, because a second build from one phase's work is not a second phase. **Work that ships application code
takes a rung even when it is not a phase**: `0.19.0` / versionCode 25 (2026-10-29) closed three recorded
defects and carries the next number, while the Huckleberry import moved none because it shipped no code. **The
ladder now stands at the first release**: Phase 16 delivered `1.0.0` / versionCode 26 on 2026-10-29, and it is
the first and only tagged build — `v1.0.0`, cut on the APK that reached the phone. Landed rungs are listed in
dated `CHANGELOG.md` sections under `### Versions`; the rule is in `AGENTS.md`. Nothing before it is tagged,
because a phase ending is not a release. The original scheme note
that assumed this work happened per-release ("versionName is 0.1.1 today") was corrected here.

### Renumbering, 2026-10-26 — the ladder reads in landing order

The owner asked for the phase numbers to run in the order the work actually happens. Three phases had
**no commits**, so they were renumbered for free; nothing else moved.

| Deliverable | Was | Now | Version |
|---|---|---|---|
| Reset to defaults | 13.6 | **15** | `0.18.0` / code 22, at its first commit |
| Update delivery (first release) | 15 | **16** | `1.0.0` |
| Ship | 16 | **17** | uses the delivered release |

- **Phase 13.5 keeps its number**, and its rung is `0.17.1` after its own device pass. Three commits on
  `main` already say `phase13.5` (`29ccd6c`, `ba4bbb4`, `848c924`), and renaming a landed phase to match a
  tidier label would mean rewriting history. It sits below Phase 14 on purpose, and the property that
  actually matters — the version ladder rising in landing order — still holds.
- **The rule is now: a phase's number is fixed when its first commit lands.** Work that has not started
  takes the next free integer above the last landed phase, and may be renumbered freely while it still
  has no commits. This supersedes the 2026-10-26 ruling *phases land in the order they are done* (C12 in
  Phase 14's table); that day's `CHANGELOG.md` section records the ruling as it was closed and this
  reversal beneath it, rather than being edited to agree with the future.
- **Decimal sub-phases are retired.** `9.1`, `9.2`, `13.1`, `13.1.1` and `13.2`–`13.5` stay as the history
  they are. Nothing new is ever numbered `13.7`; the next piece of work is a whole number.
- **Live policy documents follow; dated records do not.** This note, the Status table, `AGENTS.md`,
  `docs/firebase-setup.md` and the 2026-10-26 `CHANGELOG.md` section use the new numbers. Older
  `CHANGELOG.md` sections, the `docs/timeline` screenshot directories and the git commit messages keep the
  numbers they were written with — they record what happened, which is not the same job as the plan.

### Renumbering, 2026-10-29 — a feature phase lands before Ship

Phase 17 was "Ship — use the delivered release for a week". The owner asked for the remaining `todo.txt`
items instead, on the day `1.0.0` was delivered, which waives that week for this purpose. Ship has **no
commits**, so it moves for free under the rule above: this work takes **17** and Ship becomes **18**. Nothing
that landed was renumbered, and `v1.0.0` is untouched.

The rung is `1.1.0` / versionCode 27, not a `0.20.0`: the ladder reached its release, so the next name must be
newer than the release already on the phone (`AGENTS.md` rule), even though this work is a batch of small
capabilities rather than one new subsystem.

### Renumbering, 2026-11-01 — the food catalogue takes 18 before Ship

Phase 18 — Ship had no commits. The owner-published food catalogue is the next landed capability, so it
uses **Phase 18** and Ship moves freely to **Phase 19**. Phase 17 and its `1.1.0` rung are unchanged.

### Renumbering, 2026-11-02 — the time picker lands next, and Ship moves again

The owner asked for the time input to become switchable before the Filled Soft redesign started, so that work
took **21** and the redesign — which had held 21 with no commits — moved to **22**, with Ship following to
**23**. Nothing that had landed was renumbered: Phase 20 stayed 20 and its `1.3.0` rung is untouched. The rung
is `1.4.0` / code 31, a MINOR because two input modes the app did not have are more than a fix.

### Renumbering, 2026-11-02 — two phases land before Ship

The Settings redo took **19** and the tracking changes took **20**, both on 2026-11-02, and Ship moved from 20
to 21 for the second time in a day. Both new phases had no commits when they were numbered and Ship still has
none, so all three moved for free under the rule above. The rungs are `1.2.1` / code 29 (a PATCH — the redo
changed where settings are reached and nothing they do) and `1.3.0` / code 30 (a MINOR — a one-tap feed is a
capability the app did not have).

### Renumbering, 2026-11-02 — the Settings redo takes 19 before Ship

Phase 19 — Ship had no commits. The Settings redo (`todo.txt`: *Settings Tab may need a redo, all settings
would be in sub menu under their respective names*) is the next landed change, so it uses **Phase 19** and Ship
moves freely to **Phase 20**. Phase 18 and its `1.2.0` rung are unchanged.

The rung is `1.2.1` / versionCode 29 — a PATCH rather than a MINOR, because this phase adds no capability and
changes no stored data: every setting the app had is still there, under the name it had, with only the way to
reach it changed.

Test counts in the table are the suite size **when that phase landed**, so they trail the live total
below — Phase 11 landed at 308, for instance. They are evidence at the time, not a running count.

Verified (current state, 2026-11-02): `:app:testDebugUnitTest`, `:app:assembleDebug` and
`:app:assembleRelease` succeeded. The release APK is `1.4.1` / versionCode 32, signed `CN=BabyLog`, SHA-1
`0766ee2f85c2ed2c8e2fc5b6b05b908dd4545f4f`, and installed on `<handset>` over `1.4.0`. The suite is
**658/658**, 0 failures, 0 errors and 0 skipped, summed from `app/build/test-results/testDebugUnitTest/*.xml`
(66 files). The last thirteen came from Phase 22: eight `CardArtworkTest` and five more in the rewritten
`PatternPaletteTest`. On 2026-11-03 a 48dp artwork band took the suite to 659 and was reverted the same day at
the owner's request, so 658 is the rung's count again. The previous close-out was `1.4.0` / versionCode 31 at 645/645; the last nine of those came from
Phase 20 — five `PostedNotificationTest`, four `ReminderNotificationIdTest`, three `BottleQuickLogTest`, three
`ReminderSaveTest` — the ten of Phase 21's `WheelTimePickerTest`, and the eleven before them from Phase 19's
`SettingsNavTest` and `SettingsSummaryTest`. The ladder behind that number: 554 at the Phase-15/Huckleberry close, +11 in
`0.19.0`: two `GrowthDatasetManifestTest`, five `GrowthDatasetRepositoryTest` withdrawal and message cases,
two net in `ReminderTextTest` — one test replaced by three — and two in `TrendChartAxisTest`; the XML file count
rose 54 → 55 for the one new class. That rung's withdrawal is **device-verified** — see its own section);
549 at the Phase-15 close, +4 in `0e0c744` and
+1 in the pass that fixed what the pass found; 538 at the Phase-13.5 close, +11 in Phase 15:
four `MaintenanceRepositoryTest`, two `SettingsRepositoryTest`, five `SyncRepositoryTest`). (537 at the
Phase-13.5 code close, +1 for the
identity-line defect its device pass found). (532 at the Phase-14 close, +5 in Phase 13.5: manifest and
repository tests). (515 at the Phase-13.4 close, +17 in Phase 14: two new
`OutboxDrainerTest`, five `SyncRepositoryTest`, three `StoolAndMetricGlyphTest`, five `RangeLabelTest`
and two `CardSummaryTest`; the old amount-less-bottle test was changed, not added). The 515 was: `:app:testDebugUnitTest` was **515/515**
with 0 failures, 0 errors and 0 skipped, summed from `app/build/test-results/testDebugUnitTest/*.xml`
(51 XML files) — the 444 of the Phase-13.3 close plus the 52 of Phase 13.4's first build and the 19 the
reminder UI added while it was reshaped on the phone and corrected for exact alarms and `Remind at`. The 444 were the 417 of the
temperature-band close plus the 27 of Phase 13.3. The 417 were the 409 of the time
and temperature-preference close plus 8 temperature-band cases (`TemperatureBandTest` 6 and `TrendsTest` 2).
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
  exists in Phase 16**, so that step is a Phase 16 checklist item rather than a Phase 13 one.
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

## Phase 13.1.1 — Twelve-hour default — DONE, device-verified (`0.10.1`)

**As built — 2026-10-24.** The owner's preferred clock became the fallback for a fresh install or any
installation with no saved `time.format`: `SettingsRepository`, the no-argument `clockTime()` path,
`AppRoot`'s first composition and `LocalTimeFormat` all default to `TWELVE_HOUR`. Timestamps remain epoch
milliseconds, Celsius remains the temperature default, and a user who already saved 24-hour time keeps it.
`SettingsRepositoryTest` and `DisplayPreferencesTest` changed their expectations without adding test
methods, so the phase landed with the same 409-test suite as Phase 13.1.

### As built, where it differed from the plan

- Phase 13.1 originally shipped with a 24-hour fallback because that matched the app's earlier formatting.
  The owner override made 12-hour the default the following day; this is a fallback correction, not a new
  preference, migration or storage-format change.
- The four fallback sites moved together. Changing only DataStore would still show 24-hour during the first
  composition, while changing only `clockTime()` would leave persisted no-choice flows inconsistent.
- This is a `0.PATCH` rung (`0.10.1` / versionCode 14), not another capability. The setting itself already
  existed in `0.10.0`.
- Commit `123062b` was created while closing the following temperature-band branch, so the dedicated
  `feat/twelve-hour-default` branch was later fast-forwarded to that history rather than duplicating the
  code. This close-out adds the phase's own record and device evidence only.

**Validation:** the landing suite remained **409/409** because the two existing preference tests changed
expectations rather than adding methods. The current full-suite artifact and counts are recorded in the
live `Verified:` line above after the close-out build.

### Device pass — 2026-10-24

Samsung S23 Ultra (`<handset>`), Android 16. The APK was installed in place with the native Windows
ADB path. `topResumedActivity` showed `app.babylog/.MainActivity` before every input sequence, and each tap
came from a fresh `uiautomator dump` node or its clickable ancestor (the Settings icon exposes
`content-desc`, not `text`).

| Check | What the phone showed |
|---|---|
| APK identity | `install -r` succeeded. Package Manager reported `versionName=0.11.0`, `versionCode=15` — the branch was fast-forwarded to `main` before this close-out, so the installed APK carries the later Phase 13.2 rung while containing this phase's `0.10.1` code change (commit `123062b`). |
| No-choice fallback | The `time.format` key was stripped from `settings.preferences_pb` (four other keys preserved). On relaunch, Settings → Time format showed both chips and the Day & night labels read `Night starts 6:00 PM` / `Night ends 6:00 AM` — the 12-hour fallback, not `18:00` / `06:00`. |
| Clock surfaces | The Health sheet's time row read `8:28 PM` on the same no-choice install, confirming the fallback reaches the sheet time row as well as Settings' night-window labels. |
| Saved choice not migrated | Tapping `24-hour` immediately changed the night window to `18:00` / `06:00`. Force-stop + relaunch showed `18:00` / `06:00` and `settings.preferences_pb` held `TWENTY_FOUR_HOUR` — the new default did not overwrite a user's saved choice. |
| Preference round-trip | Tapping `12-hour` immediately restored `6:00 PM` / `6:00 AM`; force-stop + relaunch kept `6:00 PM` / `6:00 AM` and `settings.preferences_pb` held `TWELVE_HOUR`. |
| System back | Back from Settings returned to Activity with the app still foreground. Back with the Health sheet open dismissed the sheet and kept Activity foreground. Back on Activity's root screen exits to the launcher, which is expected Android behaviour for a root screen and not a defect. |
| No crash | The final `logcat -b crash -d` contained **0** `FATAL EXCEPTION` entries. |

**Note on the fallback test method.** The first attempt to strip `time.format` from the protobuf matched the
map entry's inner header instead of its outer length-delimited wrapper, truncating the file and crashing the
app on launch (`InvalidProtocolBufferException`). The original file was restored byte-for-byte from a local
copy, the parser was corrected to span the full 30-byte outer entry, and the test re-ran cleanly. The
crash buffer was cleared before the final check; the incident was a test-method error, not an app defect.

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

## Phase 13.3 — Diaper detail: Wet/Dirty toggles, colour, consistency — DONE, device-verified (`0.12.0`)

**Status: built, green and driven on the phone** (`<handset>`, 2026-10-24). The pass is at the end of this
section, including the two things it found and the owner chose to leave open.

### What the owner asked for, 2026-10-24

Three answers (Wet / Dirty / Mixed) become **two that can both be on**, plus two multi-select sub-answers
for a dirty nappy: **colour** (Yellow, Brown, Black, Green, Red, White — filled circles) and
**consistency** (Solid, Mushy, Runny, Mucousy, Hard, Pebbles, Diarrhea — a silhouette each).
Reference captured from the incumbent tracker on `<handset>`:
`../babylog-refshots/2026-10-25-diaper-reference/huckleberry-01.png` (beside the repo, not committed).

Decisions taken with the owner at the same time:

- **The card keeps one-tap pills, but only Wet and Dirty.** "Both" costs the sheet. `DIAPER_QUICK_KINDS`
  is the list the pills iterate, and it is asserted (`DiaperQuickKindsTest`) because
  `DiaperKind.entries` is the one-liner that would quietly put a third pill back and let a single tap
  answer a two-part question.
- **Colour and consistency are OPTIONAL**, like `rash`: unrecorded stays null, and the line says nothing
  rather than printing "unknown".
- **Rows show a short form** — kind, consistency words, rash — with the colour as the same small circles
  the selector draws, never as a colour name.

### Storage: no row changes meaning

`diaperKind` keeps its three values and **`MIXED` is how "both" is spelled**. The column has carried those
names since schema v1, so no row, sync document or export moves — and `DiaperKind.of(wet, dirty)` /
`DiaperKind.toggle(...)` make the two-part answer a pure, tested function instead of a composable's side
effect. Two additive nullable columns (`diaperColoursCsv`, `diaperConsistencyCsv`) arrive in
**`MIGRATION_2_3`**, with `3.json` exported and committed and a v2 → v3 test built from `2.json` itself.
No back-fill: every nappy written before this phase recorded neither, and a default colour would be an
invented clinical observation.

The v1 test's `assertEquals(2, version)` became `assertEquals(3, ...)`. It was **corrected, not deleted**:
its claim — a v1 log survives every migration this build carries — is unchanged.

`Mappers`, `SyncDocuments` and `Backup` each gained the two fields in both directions, and each has a test
for the LEGACY input: a v2 row (NULL columns), a document from an older build (keys absent) and a
`0.11.0` export (keys absent) all read as "not recorded" rather than failing.

### UI

- Sheet order: Date · Time · **Contents** · **Colour** · **Consistency** · Rash · Notes. The two detail
  sections are **absent** while Dirty is off, not disabled, and the save writes both null in that case, so
  unticking Dirty cannot smuggle a colour onto a wet-only row.
- **Contents** is two independent `ChoiceChip`s. The last remaining one cannot be switched off
  (`DiaperKind.toggle`), because "neither" has no encoding and a disabled Save would have to explain itself.
- **Colour** is six 36dp circles, no text labels, in a `FlowRow`, each with a 44dp tap target and
  `contentDescription` + `selected` semantics. 6 x 36dp + 5 x 10dp = 266dp inside the 328dp a 360dp phone
  leaves after the gutter.
- **THE RING.** Every swatch carries a permanent 1dp hairline, because Black measures 1.10:1 against the
  dark surface and White 1.13:1 against the light one — unringed, one of the two disappears in one of the
  two themes. The ring is a **dedicated colour per theme** (near-white `#D5D8E2` dark, near-black `#3C4350`
  light) rather than `textSecondary`, and the reason is measured: a fill set spanning black to white has no
  single grey that clears 3:1 against all six (mid-greys measured 1.0-2.9:1 against the ends). The rule
  `DiaperPaletteTest` enforces is therefore **at each edge something clears 3:1** — either the fill against
  the sheet or the ring against the fill — plus the ring against the sheet (10.67:1 dark, 9.95:1 light).
  Only Brown (2.60:1 fill, 4.10:1 ring) and Black (1.10 / 11.74) rely on the ring on dark, and Yellow
  (1.69 / 5.88) and White (1.13 / 8.81) on light.
- Selection is a 2dp accent ring OUTSIDE the hairline and never a size change, so the row does not shift as
  boxes are ticked — the rule the three temperature bands share one geometry for.
- **Consistency** is seven `MultiChoiceChip`s in a `FlowRow`, each with a **filled** 16dp silhouette *and*
  its word. Filled, not the 1.7dp stroke the activity glyphs use: at 16dp an outline muddies, and the point
  of these seven is to show a FORM. Shape + word means the field never depends on colour.
- `EntryRow` and the card's headline detail share one line: `diaperDetail()` (`Wet` / `Dirty` /
  `Wet + dirty`, then `· mushy, pebbles`, then `· rash`) plus `diaperColors()` replayed as 8dp dots from
  the SAME painter as the selector. "Both" reads **`Wet + dirty`**, not `Mixed`: the parent answered two
  questions, and "mixed" invited the reading "some wet, some dirty", which was never what it meant.

### Tests: 417 -> 444

27 new cases: `DiaperKindTest` 5, `CodedValuesTest` 5, `DiaperPaletteTest` 4, `DiaperQuickKindsTest` 2,
`CardSummaryTest` 4 (consistency without colour, nothing when unrecorded, dropped for a wet-only row, the
dots' source), `MappersTest` 2, `MigrationTest` 2 (v2 -> v3 survives; the upgraded database accepts the new
columns), `EventDraftTest` 2, `BackupTest` 2 (round trip; an older export still restores), `SyncDocumentsTest`
2 (round trip; an older document reads as not recorded). Two existing assertions were rewritten because the
displayed word changed from `Mixed` to `Wet + dirty`.

### Device pass, 2026-10-24 — `<handset>`

In-place `install -r` of the `0.12.0` debug APK (`versionCode=16` read back from `dumpsys package`), the
foreground checked before the first tap. Screenshots are in `../babylog-refshots/2026-10-25-diaper-reference/`
(`13.3-01` … `13.3-16`, beside the repo, not committed).

| Check | What the phone showed |
|---|---|
| Two quick pills, one tap each | The card offers `Wet` and `Dirty` only. A tap on `Wet` logged it: the headline became `Wet` with `9:51 PM` and a new row appeared |
| Both chips at once | Tapping `Dirty` with `Wet` on left BOTH filled, and the Colour and Consistency sections appeared |
| The last one cannot be switched off | Tapping `Wet` off left `Dirty` on; tapping `Dirty` off then did nothing |
| Sections only when dirty | With `Wet` alone the sheet is Date · Time · Contents · Rash · Notes — no Colour, no Consistency |
| Multi-select, no shift | `Brown` + `Black` each gained an accent ring, and no circle moved; `Mushy` + `Pebbles` filled together |
| A dirty save reads back | `Wet + dirty · mushy, pebbles` with two dots, on the card's headline detail AND on its row; the dots carry `contentDescription="Brown, Black"` |
| A wet-only reopen | The `9:51 PM` row opened with `Wet` on, `Dirty` off, and neither section present |
| An old row after the migration | The `8:55 PM` row (a `MIXED` row written by `0.11.0`) opened as "Edit diaper" with BOTH chips on and no colour or consistency — the v2 → v3 migration, on the real database |
| Black on dark, White on light | Dark: `Black` reads as a ringed dark circle. Light: `White` reads as a ringed off-white circle on the white sheet. The ring is doing exactly what it was measured for |
| 130% font scale, light theme | The headline detail holds one line; the row ellipsises to `Wet + dirty · mushy, pe…` and KEEPS its dots and its time, which is the rule the row was built to |
| No crash | `logcat -b crash -d` held **0** `FATAL EXCEPTION` across the install, the sheet, both themes and both font scales |

**Found on the phone, and NOT yet fixed:** the **Mucousy** silhouette reads as a letter `T` at 16dp — its
bar and its stem plus the droplets merge into a glyph that looks like type rather than a stool. It needs
redrawing (a wavy streak, or a blob with a single tail, would read as a form). Second weakest is **Solid**,
whose peak is pointy enough to read as a plain triangle; `Mushy` and `Runny` are distinguishable but close.
The rest — `Hard`, `Pebbles`, `Diarrhea` — read correctly at the size they are drawn.

**A recorded mistake, not a defect in the app.** Cleaning the two test rows off the card afterwards, the
second long-press landed on the wrong row: deleting the first row shifted the list up, so the `8:55 PM`
original was soft-deleted instead of the `9:51 PM` test row, and the 10-second Undo had expired before the
next dump was read. The row is a tombstone, not a hard delete, so it is still in the database and can be
restored by flipping `deleted` back (or by re-logging the change at 8:55 PM). **The owner chose to leave it
deleted (2026-10-24)** — it was a record made while testing the previous build — so the tombstone stands.
The lesson belongs with the `uiautomator` trap Phase 9.2 recorded: **re-dump after any change that removes a
row, before the next long-press.**

**Owner's decisions on the two findings, 2026-10-24:** the glyphs **ship as they are**, with the `Mucousy`
`T` kept as an open finding rather than redrawn now; and the soft-deleted `8:55 PM` row **stays deleted**.

## Phase 13.4 — Activity reminders — DONE, device-verified (`0.13.0`)

**Status: built, green, and driven on the phone** (`<handset>`, 2026-10-24 and 2026-10-25). The pass is recorded
as a table at the end of this section, including the notification that fired unprompted and the three rounds of UI
changes it produced.

### What was asked for, 2026-10-24

A local notification reminding a parent to log any of the six activities, decided with the owner in four
questions:

| Question | Answer |
|---|---|
| Which activities | **Four**: Bottle feed, Solids, Diaper, Health. Growth and Sleep were removed at the owner's instruction — a weigh-in is periodic and Sleep has its own running timer, so neither is something a parent forgets to log |
| Rule shape | **Both modes**: `Remind in` (N after that child's last log) and `Remind at` (fixed times, several a day) |
| Twins | **Separate reminder per child**; the notification names the child |
| Extras | **Skip the night window**, **Su–Sa day chips**, **tap opens the logging sheet**. **No snooze** |
| Repeat | **Fire once per anchor** — an ignored reminder does not nag |

Reference: the incumbent tracker's *Feed reminders* screen, captured on `<handset>` at
`../babylog-refshots/2026-10-24-reminders-reference/` (beside the repo, not committed) — `Remind at` / `Remind in`,
an interval row, a night switch, day chips and an add-time list. The shape is copied because it is one a parent
already knows; `For` is not, because this app has twins.

### The rules, in one pure function

`domain/Reminders.kt` — `nextReminderAt(config, anchorMs, now, zone, night)`. Everything else in the feature only
asks it "when?", so the awkward cases are JVM tests rather than 3am discoveries. Null is a real answer four times
over, and they are deliberately not collapsed:

- the rule is **disabled**;
- `AFTER_LAST` with **no anchor** — that child has never had this activity logged;
- the fire time is **overdue**. A phone that was off for eight hours must not buzz the instant the app opens:
  being in the app IS the reminder, and the next log re-anchors it;
- `AFTER_LAST` landing on a **day that is switched off**. `FIXED_TIMES` never hits this because it walks forward
  to a selected day instead — filtering the result afterwards would leave a selected day entirely unarmed.

`skipNight` moves a fire time inside the existing Day & night window to the window's end (`NightWindow`, the same
one Summary's day/night split uses, so there is no second notion of night). A reminder pushed to 06:00 says
`7h since the last feed`, which is why a delayed reminder cannot be misread as a fresh one.

### Storage and scheduling

- Configs live in DataStore, one encoded string per `reminder.<ACTIVITY>.<childId>` (`domain/ReminderCodec.kt`,
  forgiving decode: an unknown mode or day from a newer build falls back rather than throwing). **No Room
  schema change and no migration**, and nothing goes in the outbox: a reminder is one phone's business, and the
  other parent's phone has its own ideas about when to be woken.
- `AlarmManager.setAndAllowWhileIdle`, one alarm per (activity, child) — at most twelve — with `FIXED_TIMES`
  arming only the next occurrence so the ceiling holds.
- **Exact alarms, after the phone disproved the first decision.** The build shipped with `setAndAllowWhileIdle`
  and the reasoning "a feed nudge a few minutes late is worth not asking for a special-access screen". `dumpsys
  alarm` then showed the armed alarms carrying **`window=+37m47s`** — "a few minutes" could be most of an hour — and
  the owner reported reminders arriving late. The manifest now declares `USE_EXACT_ALARM` (granted at install) and
  `SCHEDULE_EXACT_ALARM` for API 31-32, the scheduler uses `setExactAndAllowWhileIdle` when
  `canScheduleExactAlarms()` allows it, and falls back to the inexact call rather than dropping the reminder. The
  device now reports `window=0`.
- `Reminders.refresh()` is the single recompute path — app start, every event write (`EventRepository.onLogged`),
  every rule save, `BOOT_COMPLETED` and `MY_PACKAGE_REPLACED`, because alarms survive neither a reboot nor an
  update. Switching a rule off **cancels** its alarm rather than skipping it.
- `Reminders.fire()` posts the notification and then arms the next `FIXED_TIMES` occurrence; `AFTER_LAST` arms
  nothing, which is the no-nagging rule.

### Where a reminder lives: on its activity's card, per child

The first build put reminders in **Settings** and let one rule cover **Both** children. Both were wrong for this app
and were replaced after the owner's second mockup (2026-10-25):

- **The Settings section is gone.** A reminder belongs to the activity it is about, so it is set from that activity's
  card: a `⋮` menu on the four reminder-capable cards (Bottle feed, Solids, Diaper, Health) holding `Set reminder`
  (or `Edit reminder`) and `Remove reminder`. Growth and Sleep pass no handler and therefore have no menu at all — a
  menu whose only useful item is missing is worse than no menu.
- **Per child, never Both.** A reminder happens to a person: First Twin's 1:00 PM nappy is not Second Twin's, and a
  shared rule hid which twin was actually overdue. When two children are on screen the sheet asks which one.
- **The sheet is `Set reminder — Diaper`**: `For` (children only), `Mode`, `Time`, `Notify`, `Skip night` and `Days`.
  The mockup's `Start`, `End` and `Snooze (10 min)` are dropped at the owner's instruction, and its `Repeat…` item
  with them because a repeat is what an interval already is.
- **`Remind at` came back.** The redesign left only an interval, and the owner asked for the fixed clock times again
  ("Remind me at option is missing"): the first build had them, and a day with a rhythm wants `At 8:00 AM` rather
  than "three hours after the last feed". So `Mode` is a two-chip row — `Remind in` (interval, anchored on that
  child's last log) and `Remind at` (a list of times, each editable through the shared picker, plus `+ Add time`) —
  and the chip follows the mode: `Every 3h (1:00 PM)` or `At 8:00 AM`.
- **The interval stepper is standardised**: `StepperRow` gained a `valueWidth`, so `1h` and `1h 30m` no longer shuffle
  the −/+ buttons sideways as the value changes — found on the phone.
- **The card shows its own reminder** as a chip under the actions: bell, `Every 3h · Next: 1:00 PM`, or
  `Reminders differ` when the two children's intervals are not the same number (never one child's interval presented
  as the twins').
- **Saving confirms on the Activity screen** (`Diaper reminder added`) through the snackbar the delete flow already
  uses, and `Remove reminder` writes a disabled rule rather than deleting the key, so the interval is still there next
  time.

### Three things the third round of phone testing changed

- **The chip says WHEN it will ring**, `Every 3h (4:45 PM)`, not just the rule. A chip that said only `Every 3h` told
  the parent the rule but not when they would be interrupted, which is the thing they wanted to know.
- **Both children get a chip each, with their badge**: `[F] Every 3h (4:45 PM)` and `[S] Every 4h (5:45 PM)`, stacked
  and wrapping rather than clipped. The first version said `Reminders differ` — announcing that something was different
  without saying what — and the second squashed both into one sentence, which the parent had to read to work out which
  twin was which. A badge per chip answers that at a glance, and follows the rule the entry rows already use: the badge
  appears in Both mode only, because a badge on every row of a single-child card says nothing.
- **The chips fit one row.** Measured: at 9dp of side padding, `labelMedium` and a 12dp bell each chip was 458px and
  two of them plus the gap (933) did not fit the 900px inside the card's padding, so they wrapped. At 7dp, `labelSmall`
  and an 11dp bell they are 387px and 385px and sit side by side.
- **An Upcoming row opens the child it names.** The row carried only the activity, so `Bottle feed · Second Twin` opened
  the first twin's rule — the same bug the chips had, in the other place.
- **A chip opens ITS child's rule.** The chip carried only the action at first, so the sheet fell back to the first
  child and the second twin's chip opened the first twin's reminder — found on the phone. The action now carries the
  chip's child id, the sheet is keyed on (activity, child), and `Remove reminder` on a Both card clears every child in
  scope rather than silently picking one.
- **Tapping the chip edits the reminder.** It is the thing showing the rule, so it is the thing to tap; going through
  the `⋮` menu for the same job was a step nobody needs.
- **A reminder with nothing to measure from now has one.** Two separate holes, both found by a chip that showed a rule
  with no time: a rule for an activity that child had never logged had no anchor at all, so it never armed; and an
  interval whose target had passed returned null, so it stopped reminding exactly when it was most overdue. The rule
  now stores `anchorAt` (stamped when it is set, and migrated once for rules written before the field existed), and an
  overdue interval rings a minute later instead of vanishing.
- **A rule left in the old fixed-times mode is rescued.** The sheet edits an interval now, so such a rule could
  neither fire nor be corrected from the UI; the refresh converts it, keeping its interval.
- **Delete from inside the edit sheet.** Long-press still deletes a row, but a parent who has opened a record to change
  it should not have to close it and hunt for the row again: every editing sheet ends with `Delete this entry`, which
  runs the same confirm-then-Undo path the long-press uses.

### The "Upcoming reminders" card

Asked for from the owner's mockup (2026-10-25) after the reminder sheet existed: the Activity screen's top card
lists what is actually armed — bell, `Upcoming reminders (n)`, then one row per reminder with the activity's
glyph in its accent, its name, the clock time in the parent's format and a coarse countdown
(`Diaper  8:00 AM (in 19h 55m)`), each tapping through to that activity's reminder sheet.

- It reads the SAME `nextReminderAt` the scheduler does, so the card cannot promise a reminder the alarm will not
  make, and `upcomingReminders()` is a pure function with its own tests.
- Absent when nothing is armed: a card saying "nothing" is what its absence already says.
- Twins on the same rule are ONE row (`Diaper 8:00 AM`); twins on different rules are one row each, named; and a
  rule only one child has is named too — an unnamed row reads as "both children", which the phone caught.
- The countdown ticks every 30 seconds, not every second: it is written in minutes, so a faster tick would redraw
  the same text. A row whose time has passed drops out on the next tick rather than reading `in 0m`.
- **Collapsible**: tapping the header folds it, and the chevron points at what the tap will do. Expanded by default,
  because the card exists to be read at a glance; the fold lasts for the session rather than being a stored setting.
- Capped at five rows, and the count in the title is the number of rows, so the card can never say "(3)" over two.

### Two things the device pass changed

- **The four activities, not six.** Growth and Sleep had rows in Settings and rules in the engine; both were
  removed at the owner's request, and `REMINDER_TYPES` is now the single list the engine, the Settings rows and
  the summary read — so an activity cannot have a row the scheduler ignores, or an alarm with no row. A rule
  that still exists for a removed activity is CANCELLED by the refresh rather than left armed.
- **Reminder times follow the 12/24-hour preference.** The first build hardcoded `HH:mm` for the row and the
  Settings summary while the picker and the notification used the preference, so one sheet showed `11:00 PM` in
  the picker and `23:00` in the row beside it. Found on the phone, not by a build. `clockLabel(minutes, format)`
  now formats exactly like `clockTime`, the summary takes the format from `SettingsViewModel`, and a rule with
  more than two times names two and counts the rest (`7:00 AM, 1:00 PM +1`) so the row cannot run off the card.

### The notification

One channel, **Activity reminders**, `IMPORTANCE_DEFAULT` — not six channels for a two-parent app whose activities
are already switchable in Settings. The small icon is a solid bell, not the activity glyph: those are 1.7dp
stroke line-art and would be a smudge in a 24dp status bar, so the activity is carried by `setColor(accentFor)`
and by the title. Ids are `type.ordinal * 31 + childId.hashCode()`, so two children due at once are two
notifications rather than one replacing the other.

**A health reminder says the activity and nothing else.** `Time to log a health entry`, never a medicine, never a
dose — a reminder naming Calpol would be the app appearing to suggest one, which `AGENTS.md` forbids.
`ReminderNotificationsTest` asserts that against every name in the bundled medicine catalogue, plus a
number-with-a-unit dose pattern.

Tapping it opens that activity's logging sheet **for that child** (`singleTop` + `onNewIntent` →
`AppContainer.pendingSheetRequest` → `HomeViewModel.openSheetFor`), because the header may be showing the other
twin and a sheet that logs for the wrong child is worse than no shortcut.

### Tests: 444 -> 496

52 new cases: `RemindersTest` 20 (every null, the night window including `start == end`, per-child anchors,
fixed-time roll-over, day walking, the summary line), `ReminderCodecTest` 9, `ReminderSchedulerTest` 9
(Robolectric `ShadowAlarmManager`: one alarm at the computed time, two children = two alarms from their own
anchors, disabling cancels, fixed times, night window, the intent's extras, and that the alarm is the inexact
one), `ReminderNotificationsTest` 6 (title, text, two children, fixed-time wording, the health rule against the
real catalogue, channel and colour), `ReminderTextTest` 7, and one addition to `AppStartupTest` that drives the
container's own adapters end to end — a rule in DataStore, an anchor from Room, an alarm in AlarmManager.

### Device pass, 2026-10-25 — `<handset>`

In-place `install -r` of the `0.13.0` debug APK (`versionCode=17` read back from `dumpsys package`), the foreground
checked before the first tap, every coordinate taken from a `uiautomator dump`. Screenshots are in
`../babylog-refshots/2026-10-24-reminders-reference/` (`13.4-01` … `13.4-28`), beside the repo and not committed.

| Check | What the phone showed |
|---|---|
| The permission flow | Settings was still carrying the row at that point: `Allow` raised the system dialog, granting it made the row disappear, and `dumpsys package` then read `POST_NOTIFICATIONS: granted=true` |
| A menu only where it belongs | `⋮` (`content-desc="Reminder options"`) on Bottle feed, Solids, Diaper and Health; **none** on Growth or Sleep |
| The menu's states | `Set reminder` with no rule; `Edit reminder` + `Remove reminder` once one exists |
| Per child, never Both | `Set reminder — Diaper` shows `For` with the two children and no `Both` chip, and the subtitle names the child |
| The stepper does not move | Measured: at `3h` the −/+ are at `[567,673][592,726]` / `[972,673][997,726]`; at `1h 30m` they are **identical**, while the value slot widens |
| Saving | Snackbar `Diaper reminder added`, and the card gained its chip |
| The chip says when | `[F] 🔔 Every 3h (4:45 PM)` and `[S] 🔔 Every 4h (5:45 PM)` on **one row** (387px + 385px inside the 900px the card leaves) |
| A chip edits ITS child | The second chip opened `Set reminder — Bottle feed` for **Second Twin, 4h**; the first for **First Twin, 3h** |
| An Upcoming row opens its child | `Bottle feed · Second Twin` opened the sheet for **Second Twin**, not the first |
| The Upcoming card folds | Tapping the header collapsed it and the chevron became `Expand` |
| A rule is cancelled, not left armed | `Remove reminder` cleared the chip and the card; `dumpsys alarm` held **0 live** BabyLog alarms afterwards, only `alarm_cancelled` history |
| Reminders left Settings | The Settings list has no `Reminders` section and no reminder rows |
| Delete from inside the sheet | `Edit feed` → `Delete this entry` → *"Delete this bottle feed?"* → `Delete` → snackbar `Bottle feed deleted` with `Undo` |
| **The notification fires on its own** | A First Twin Bottle feed reminder whose 3h target had passed re-armed for a minute later and fired: the shade showed **`First Twin · Bottle feed` — `Time to log bottle feed`**, bottle glyph, `color=0xffffc857`, `channel=reminders`, `category=reminder` |
| **The notification's tap** | It opened the app with the **Bottle feed sheet already up, for First Twin** — the child the notification named, not the one the header showed |
| **The alarm is exact, not deferred** | After the fix, `dumpsys alarm` reports the armed reminder as `RTC_WAKEUP … 08:00:00.000 window=0 reason=policy_permission` — against the `window=+37m47s` of the first build |
| **`Remind at` is back** | The sheet shows `Mode` with `Remind in` / `Remind at`; `Remind at` lists `8:00 AM` with `Remove`, plus `+ Add time`, and the card's chip then reads **`At 8:00 AM`** |
| No crash | `logcat -b crash -d` held **0** `FATAL EXCEPTION` across installs, the sheet, both themes, 130% font scale, the alarm and the notification |

**Dated note, 2026-10-25.** Five rounds of phone testing changed this phase after it first went green, and each change
is recorded above with the reason the phone gave for it:

1. Reminders moved out of Settings onto each activity's card, and the `Both` scope went away.
2. `Start`, `End` and `Snooze` were dropped; the sheet kept `Time`, `Notify`, the night window and the days.
3. The chip gained the time it would ring, then split into one chip per child with that child's badge, and the edit
   sheet gained `Delete this entry`.
4. **Reminders arrived late** — the inexact-alarm decision was measured wrong on the device (`window=+37m47s`) and
   replaced with exact alarms, keeping an inexact fallback rather than dropping a reminder.
5. **`Remind at` came back**, because a day with a rhythm wants a clock time and not "three hours after the last
   feed".

A green build approved every one of those five versions. That is the cost of judging a notification feature without a
phone, and it is why the pass below is a table rather than a sentence.

**Open, deliberately:** the notification says `Time to log bottle feed` when that child has never had the activity
logged, because there is no log to measure from — the rule's own anchor drives the alarm, but claiming `3h since the
last feed` when no feed exists would be a lie. Also `Reminders differ` is gone by design: two children get two chips.

## Phase 13.5 — the growth dataset update channel — DONE, device-verified (`0.17.0`, `0.17.1`)

Split out of Phase 13 on 2026-10-19, not dropped: Phase 13 is the half that two phones must prove first.
The Firebase project already exists by then, which is the only precondition this channel ever had.

### As built — 2026-10-26

- `GrowthDatasetGateway` reads `reference/growth_manifest` and `reference/growth_dataset`; Firestore rules
  allow the two accounts to read those documents and never write them.
- `GrowthDatasetRepository` keeps the bundled dataset as the fallback, caches an accepted update in
  app-private storage, verifies the manifest's SHA-256 against the payload, and refuses stale versions.
- A 90-day silent check runs on app start; manual `Check for updates` rows exist on the Growth tab and
  in Settings, with short status text. Automatic checks fail silently.
- `GrowthReference.identityLine` is unchanged; the repository supplies the current reference.
- Tests: `GrowthDatasetRepositoryTest` (5 cases) plus the existing Growth tests; 537 total, 0 failures.

### As built, where the device pass changed it — 2026-10-26

Two things came out of the phone, one of them a defect the suite could not see:

- **The check was blocked by deployment, not by code.** `reference/growth_manifest` read returned
  `Status{code=PERMISSION_DENIED}` while `events` synced normally. `firebase/firestore.rules` in the repo
  carries `match /reference/{documentId}` from `ba4bbb4`, but rules are pasted into the console by hand and
  the deployed set predated that commit. Nothing in the app needed to change; the rules and the two
  documents were published and the same tap then reported `Updated to who-2006.2026.11`.
- **The Settings identity line did not move when the dataset did** — `0.17.1`. After a successful check the
  card printed `Updated to who-2006.2026.11` above a line still reading `v2026.10`; it only corrected itself
  after leaving and re-entering Settings. `SettingsViewModel.checkGrowthUpdates()` called
  `container.growth()` under a comment claiming that refreshed the line, but the value was discarded, and
  `AppScaffold` re-read it inside a `produceState` keyed on the destination. `GrowthDatasetRepository
  .identityLine` is now a `StateFlow` that an accepted update publishes to, the screen collects it, the dead
  call is gone, and so is the `container` parameter it was the last user of. The phone re-proved it: one tap,
  same screen, `v2026.10` → `v2026.11`.

The bundled dataset and percentile maths are unchanged. The reference published for the pass is the bundled
JSON with only `version` and `retrievedAt` bumped (`who-2006.2026.11`, `2026-10-26`) and the SHA-256
recomputed — it proves fetch → verify → cache → identity line, and its LMS tables are byte-identical to the
bundled ones. The four datasets' curation still matters: a fix to our own bundled data should not need an
APK release.

### Device pass, 2026-10-26 — `<handset>` (SM-S918U1), `0.17.1`

Every tap coordinate came from `uiautomator dump`, the foreground was confirmed before any input, and the
crash buffer was read at the end.

| # | Check | Result |
|---|---|---|
| 1 | `install -r` `0.17.1` (versionCode 22) over `0.17.0`, launch, foreground before input | `topResumedActivity=app.babylog/.MainActivity` ✅ |
| 2 | Home renders after the install | six cards, glyph tiles, both children, today's rows intact ✅ |
| 3 | Growth cache file removed, so the app falls back to the bundled dataset | `WHO 2006 · v2026.10 · updated 15 Oct 2026` ✅ |
| 4 | Settings → *Check for updates*, then read the **same screen** without navigating | `Updated to who-2006.2026.11`, line now `WHO 2006 · v2026.11 · updated 26 Oct 2026` ✅ (this is the `0.17.1` fix) |
| 5 | Second tap | `Already up to date`, line unchanged, no re-fetch ✅ |
| 6 | Accepted dataset written to app-private storage | `files/growth/updated.json`, 488 449 bytes ✅ |
| 7 | Trends → Growth → *Check for updates* | `Already up to date` ✅ |
| 8 | Rules not yet published (earlier in the same pass) | `Could not check`, bundled line kept, app usable, `PERMISSION_DENIED` in logcat ✅ |
| 9 | Force-stop → relaunch (the 90-day silent-check path) | launches, check fails silently, no crash ✅ |
| 10 | `logcat -b crash -d` | **0** `FATAL EXCEPTION` ✅ |

Not exercised on the phone: the Growth tab's identity line and percentile chart, because that child has no
measurements yet and the empty state replaces the chart body. The line for that surface is the same
`StateFlow` read, and it is covered by `GrowthDatasetRepositoryTest` rather than by a measurement being
invented for the test — the app must not record a number nobody took.

### Known gap — an accepted dataset cannot be withdrawn (recorded 2026-10-26, not built)

`isNewer()` refuses anything that is not newer, and `deleteCache()` is reached only when the cached file is
*corrupt*. So a reference a phone has accepted can be superseded but never recalled: publishing an older,
corrected manifest returns `NoUpdate` and leaves the phone reading the copy you were trying to withdraw. The
only recovery today is clearing the app's data — the server copy survives, so a wipe plus sign-in restores the
log (Phase 13 proved that twice), but that is a rescue, not a rollback.

The pass above turned that from a thought into a task. To prove the channel end to end it published
`who-2006.2026.11` — the bundled LMS rows with only `version` and `retrievedAt` bumped — and the identity line
then named a version that does not exist upstream. Restoring the shipped reference took **two** steps, and
either alone was insufficient:

1. Correct `reference/growth_manifest` and `reference/growth_dataset` back to `who-2006.2026.10` / `2026-10-15`
   with the bundled asset's own SHA-256 (`2df3475…18cb5c`, byte-identical to `assets/growth/who-igrowup.json`,
   488 449 bytes).
2. Force-stop the app, delete `files/growth/updated.json`, relaunch. The force-stop is part of it: the parsed
   reference is cached in memory for the life of the process.

Done in the other order, a check in the gap re-adopts the synthetic dataset. Verified afterwards: the line reads
`WHO 2006 · v2026.10 · updated 15 Oct 2026`, a manual check reports `Already up to date`, and no cache file is
written — nothing was accepted, so the cloud side is telling the truth again.

A `withdrawn` flag on the manifest, or treating a manifest equal to the bundled version as *drop the cache*,
would close this. **It is not built.** It belongs to whichever phase next touches the growth reference; nothing
in Phases 15-17 depends on it, which is why it is recorded here rather than opened as a phase on its own.

> **CLOSED 2026-10-29, in `0.19.0`** — the `withdrawn` flag was the route taken, not the equal-to-bundled
> heuristic: a dataset that is neither bundled nor cached has no way to be recalled under a heuristic, and
> "go back to bundled" would be inferred rather than stated. A withdrawn manifest now deletes the cache file,
> clears both DataStore keys, resets the in-memory reference **and so needs no force-stop**, reverts the
> identity line and reports *"Dataset withdrawn — back to the bundled reference"*. It names exactly one
> version, so a retraction for a dataset this phone never accepted is a no-op. The two-step rescue described
> above is history, not the procedure: see `docs/firebase-setup.md`.
> **Device-verified 2026-10-29**, after this note was written: the bumped copy (`who-2006.2026.11`, `2026-11-01`,
> SHA-256 `7317…cbf9`) was published from the Firestore console and accepted (`Updated to who-2006.2026.11`,
> cached file byte-identical to the published bytes), then the same manifest with `"withdrawn": true` took it
> back: `Dataset withdrawn — back to the bundled reference`, the identity line returned to `v2026.10`, the cache
> file was deleted, both DataStore keys were gone, and a relaunch did not re-adopt it. **The force-stop this
> note describes is no longer needed** — the in-memory reference is reset. See the section below the phase list.

## Phase 14 — Polish/Misc changes (`0.16.0`) — DONE, device-verified

Eight findings the owner ruled on while closing Phase 13.4, given one phase and one branch because each is
too small to be a phase on its own and together they are a week of small, real defects. The rulings are
recorded so a later pass does not re-open them:

| # | Item | Ruling |
|---|---|---|
| 14.1 | `Sync now` with no network was silent and held the drain lock (B5) | **fixed** |
| 14.2 | The first-sign-in seed waited for a write or a tap (B6) | **fixed** |
| 14.3 | `Both` wrapped to `Bot` / `h` at 130 % (B8) | **fixed** |
| 14.4 | A solids card showed the food id, not its name (B9) | **fixed** |
| 14.5 | Trends' pinned activity chips touched the range chips (B7) | **fixed** |
| 14.6 | Consistency silhouettes that do not read as forms (A1) | **redrawn** |
| 14.7 | Activity glyph on sheet title bars, entry rows, Both-mode columns (C10) | **added** |
| 14.8 | Growth metric chips carry per-metric marks (C10) | **added** |
| 14.9 | Range chips: `D · 1W · 2W` and `1W · 2W · 1M` (C10, revised) | **relabelled** |
| 14.10 | A bottle card with no amount read `Bottle` over `Bottle feed` (found on the phone, 2026-10-26) | **fixed** |
| — | The chip clipping was fine (B7); `Reminders differ` stays gone (A4); the `8:55 PM` tombstone stays deleted (A2); phases land in the order they are done (C12) | **closed, no code** |
| — | The "never logged this activity" notification wording (A3) | **deferred, unchanged** — *closed 2026-10-29 in `0.19.0`: now states the fact (`No nappy logged yet`) through `noun()` instead of printing `Time to log diaper`* |

### As built

- **14.1 — a drain that cannot reach the server now says so, and lets go of the lock.** `OutboxDrainer`
  bounds each write with `writeTimeoutMs` (10 s). Firestore with no network closes its write stream
  `UNAVAILABLE` and leaves the `Task` pending, so `awaitTask()` never returned, `syncNow()` never returned,
  and the `Mutex` stayed held — which is why the button looked inert and could not resolve itself. The
  timeout lives in the **drainer**, not the gateway, so `FakeSyncGateway` can prove it without a Firestore
  client. `DrainResult` gained `remaining` and `timedOut`, and `NetworkStatus` (new, one method, real
  implementation over `ConnectivityManager`) picks between two honest sentences: *Waiting for a network
  connection* and *The server did not answer* — the second matters, because four bars is not an offline
  phone. Four tests, including one proving a second `Sync now` after a timeout returns instead of queueing
  behind a dead one.
- **14.2 — the seed drains itself.** `attach()` already drained before attaching the listener, so the fix
  is at the seam the finding actually names: the sign-in result is applied under `NonCancellable` (a
  `viewModelScope` cancelled in that window used to lose the listener *and* the drain), and the drain runs
  again once the first inbound snapshot has been applied. The test fails without the second drain: the fake
  gateway fails one write, and the queue still empties with nobody tapping anything.
- **14.3 — pills wrap, words do not.** `ChildSwitcher` is a `FlowRow`, each label is `maxLines = 1` and
  `softWrap = false`, so 130 % gets a second line of whole pills instead of a chopped `Bot` / `h`.
- **14.4 — a meal reads as its name.** `HomeViewModel` observes the catalogue it already had and carries
  `foodNames`; `detailLine` and `recentRowLabel` take the map, and an id the catalogue no longer has still
  renders as words rather than a blank.
- **14.5 — the two chip rows have a gap.** The tab row is pinned above the pane and the pane drops its own
  top padding, so the pinned row and the pane's first item (the range chips) touched. One 8 dp bottom inset
  on the pinned row.
- **14.6 — two silhouettes redrawn.** `Mucousy` was a wide bar over a stem with a dot under it: a letter `T`
  at 16 dp, beside six shapes that read as forms. It is now two slanted streaks — stringy, which is what the
  word means. `Runny` was three heavy circles on an ellipse that merged into `Mushy` at 16 dp; it is now a
  small mass sinking into a **wide flat pool** with shoulders on both sides, which is the difference the
  field is asking a parent to record. Both were rendered at 16/24/48 dp and compared before and after; the
  other five were checked and left alone. `StoolAndMetricGlyphTest` now pins all seven as distinct
  drawables — the mapping had no test before, which is how a `T` shipped.
- **14.7 — the glyph reaches three more surfaces.** `ActivitySheet` takes the **activity** rather than an
  accent and draws its glyph at 16 dp in `labelFor(accent)` with a mirrored 22 dp spacer so the title stays
  centred; entry rows draw it at 14 dp in the raw accent, derived from the event so a caller cannot hand a
  row the wrong one; Both-mode column heads draw the card's own glyph before the child's name.
- **14.8 — Growth's chips carry metric marks.** Three new stroke drawables — a scale, an up arrow off a
  floor, a head with a curl — plus `GrowthMetric.iconRes()` / `iconPainter()`. Not the Growth activity
  glyph: it would be identical on all three chips, which is the one thing a chip must not be. Local
  drawables rather than `material-icons-extended`, matching the six activity glyphs (the project depends on
  `material-icons-core` only). `BMI_FOR_AGE` is in the bundled dataset but has no chip, so nothing was left
  blank.
- **14.9 — range chips are labels, not glyphs.** `HistoryRange` is `D · 1W · 2W` (1/7/14 days) and
  `TrendRange` is `1W · 2W · 1M` (7/14/30 days) — two rows, because they are two different windows, and
  `1M` names the 30-day window it draws rather than claiming a calendar month. **No window changed.** Both
  enums gained a `spoken` form (`"1 day"`, `"2 weeks"`) carried as the chips' `contentDescription`, because
  `2W` read aloud is not an instruction. `RangeLabelTest` pins every label to its own `days`.
- **14.10 — an amount-less bottle names the gap.** Found during this phase's device pass, not in the
  original eight: a feed logged without a volume read `Bottle feed` over `Bottle`, because `detailLine`'s
  fallback for a bare bottle was the word `Bottle` — the card named itself twice and showed no number.
  A blank amount is a legitimate record (`BottleSheet` allows it, and the app must not invent a volume), so
  the line now says `No amount recorded` and the row says `No amount`. A brand the parent typed still wins
  the line, and a bottle that HAS a volume is unchanged. The existing test that pinned `Bottle` was
  rewritten to pin the new behaviour, with the reason in it.
- **Design-contract reversal, deliberate:** the activity glyph now appears on five surfaces, the range chips
  carry no glyph at all, and the Growth metric chips carry their own marks. This reverses the earlier
  reasoning that the glyph belongs only on cards and activity chips — the owner asked for it on 2026-10-25
  after seeing the five surfaces rendered side by side. Do not "restore" the old rule.

**Verified in code:** `:app:assembleDebug` builds (`0.16.0`, versionCode 20) and `:app:testDebugUnitTest`
is **532/532**, 0 failures, 0 errors, 0 skipped — 515 when the phase started, +17 across two drainer,
five sync-repository, three glyph, five range-label and two card-summary tests. The amount-less-bottle
test was updated to assert the new wording. The
device pass is recorded at the end of this file, on `<handset>`.

**Prerequisite:** none.

## Phase 15 — Reset to defaults — DONE, device-verified (`0.18.0`, amended by `0.18.1`)

Asked for while closing out Phase 13: **Settings → Data has no way to return the app to first-run state.**
Export and Import are the only rows there, and Import needs a file it does not have. This is scoped now so
it is not lost, and **deliberately not started in the session that scoped it.**

Shape agreed with the owner (re-scoped 2026-10-25 — **two buttons, not one row and a checkbox**):

- **Local reset** — wipes **events**, **children** (re-seeding *First Twin* / *Second Twin*), and
  **settings and presets** — theme, units, night window, bottle and medication presets. Export files are
  not touched. Its confirmation names what will go.
  *Amended 2026-10-28: the re-seed was dropped at the owner's request after `0.18.0`, so a reset leaves no
  children at all and the Activity screen answers with its `No children yet` card. The scope line above is
  the 2026-10-25 agreement, kept so the change of mind is visible rather than silently rewritten.*
- **Delete all server data** — removes the log from the server *and* resets locally, because a server wipe
  against a live local copy would simply be re-uploaded by the seed. Its confirmation says plainly that the
  data **cannot be recovered from the server**, and it is offered only while signed in: a button that
  cannot work is a button that lies.
- Two buttons rather than one plus an opt-in switch, so the destructive-and-unrecoverable action is its own
  visible choice rather than a checkbox a parent can leave ticked by accident.

The reason that last point is not optional detail: **sync makes a local-only wipe a trap.** `replaceAll`
wipes the tables *and* the outbox, so a reset that did not queue DELETEs would leave Firestore holding the
whole log, and the next snapshot would merge it straight back in — the exact defect Phase 13 fixed for
Import with `reseedAfterRestore`. **Local reset therefore pushes tombstones for every row and drains, and is
refused while a drain cannot reach the server**; *Delete all server data* is the deliberate escape from that
refusal.

Reuse, not reinvention: `MaintenanceRepository` already owns the wipe-in-one-transaction pattern
(`replaceAll`) and `OutboxDrainer` already knows how to travel a permanent delete as an id-and-timestamp
tombstone. The tests to write mirror the ones there: the wipe is atomic, the queue is left consistent, and
a reset while signed out cannot resurrect rows on the next sign-in.

**Prerequisite:** none. It carried 13.6 until 2026-10-26, when it took the first free number above the last
landed phase (14) so the ladder reads in the order the work happens — see *Renumbering* near the Status
table. It does not wait on 13.5; it waits on nothing.

### As built — 2026-10-27

Two rows in Settings → Data, each with its own confirmation, built on machinery that already existed:

- **`MaintenanceRepository.resetLocal(at)`** — one transaction: queue a DELETE intent for every child and
  every event **first**, then `deleteAll()` on both tables, then `deleteUpserts` for the ids that just
  vanished. Intents before deletes for the same reason `deleteChildPermanently` does it: the other
  direction leaves a window where the rows are gone and nothing remembers them.
- **The two seeded children were the trap, and the code said so — then the exemption became the bug.**
  `ChildRepository.seedIfEmpty` re-created `first-twin` / `second-twin` with the *same* ids, so a queued
  DELETE for them would have erased them on the server the moment it drained. `SEED_CHILD_IDS` was
  subtracted from the tombstone pass and `deleteByEntityIds(SEED_CHILD_IDS)` dropped any intent that
  survived; the re-seed then queued the UPSERTs that replaced them. **This is the `0.18.0` shape and it was
  reversed in `0.18.1`** once the reset stopped re-creating them: the twins stayed on the server, the
  listener re-attached, and the snapshot put them back into an emptied phone. See the 2026-10-28 pass below.
- **`SettingsRepository.clearForReset()`** — drops this phone's choices and every reminder key (found by
  shape with `decodeReminderKey`, since the keys are dynamic), and deliberately keeps `sync.seeded` (a reset
  must not re-upload a log it just deleted) and `growth.dataset.*` (not the family's log).
- **`Reminders.refresh()` cancels the alarms** the cleared rules owned, which is the same pass that keeps a
  switched-off rule silent.
- **`SyncRepository.resetGate()`** — allowed unless signed in AND the probe drain failed or the phone is
  offline. The connectivity check is not redundant: an **empty** queue drains "successfully" without making
  a network call, so the probe alone would allow a reset on a plane and queue deletions that cannot travel.
- **`SyncRepository.queueTombstonesForEveryRemoteRow()`** — `fetchAll()` then one DELETE intent per remote
  id, including rows this phone never received. No new gateway method, because the outbox already travels
  tombstones by id. A bulk collection delete would not do: the other phone's listener does not read
  "document absent" as a deletion.
- **The device pass found a real defect, and it is the reason `withoutInboundApply` exists.** The first
  reset wiped the tables and drained its tombstones correctly, and then the inbound listener applied a
  snapshot it had taken *before* the deletes landed — re-inserting 15 events into the emptied database, with
  an empty outbox, so the phone showed a log the server no longer had and would never send again. Pulling
  the database (not reading the screen) is what proved it: `events 15 live, children 2, outbox []`.
  `SyncRepository.withoutInboundApply {}` cancels **and joins** the listener around the wipe and re-attaches
  afterwards, and `attach()` drains before it observes, so a tombstone the wipe queued travels before any
  snapshot can be applied.

### Device pass, 2026-10-27 — `<handset>` (SM-S918U1), `0.18.0`

| # | Check | Result |
|---|---|---|
| 1 | `install -r` `0.18.0` (versionCode 23), launch, foreground before any input | `topResumedActivity=app.babylog/.MainActivity` ✅ |
| 2 | Local reset, with the listener fix in place | message `This phone is back to first run. Nothing is left waiting to sync.` ✅ |
| 3 | Database pulled right after, and again at +10 s and +30 s | `events 0, children 2 (re-seeded), outbox 0` at every point — where the pre-fix build had re-inserted 15 rows ✅ |
| 4 | Airplane mode on (verified `airplane_mode_on=1` **before** tapping), 2 events in the log, reset attempted | `Nothing was deleted: Waiting for a network connection. Use Delete all server data if the log must go anyway.`; database still `2 events, 2 children` ✅ |
| 5 | *Delete all server data*, online, with real rows present | `The log is gone from this phone and the server. Nothing is left waiting to sync.` **118 row(s) only the server knew about were queued** ✅ |
| 6 | Database after the drain | `events 0, children 2, outbox 0` ✅ |
| 7 | Reminder alarms after the reset | 29 `app.babylog.reminders.FIRE` records, **all under `Removal history`** — none armed ✅ |
| 8 | `logcat -b crash -d` | **0** `FATAL EXCEPTION` ✅ |

Not performed: the second phone (only `<handset>` was attached), and the theme/units defaults by eye —
`SettingsRepositoryTest` asserts `clearForReset` drops them, and re-driving that screen risked another
blind tap on a destructive row.

One honest note on this pass: the first attempt at check 4 was lost to an ADB disconnect mid-sequence, and
the taps landed online — so the reset *executed* instead of refusing. That is what surfaced the listener
race in the first place. The check was re-run with the radio state verified immediately before the tap.

### Device pass, 2026-10-28 — `<handset>`, `0.18.1`

`0.18.1` was three fixes the owner hit on `0.18.0` — a drain that gave up at the first slow write, a reset
that re-seeded the twins, an import that merged instead of replacing — and this pass was for the state it
claimed to create. It found two more, both in that state, and the second only because the first was fixed.

| # | Check | Result |
|---|---|---|
| 1 | `install -r` `0.18.1` (versionCode 24), foreground before any input | `topResumedActivity=app.babylog.MainActivity`, `versionName=0.18.1 versionCode=24` ✅ |
| 2 | *Reset local data* twice on the **pre-fix** build, with an event in the log | events deleted, outbox 0, summary truthful — but `children 2` returned carrying the **server's** `updatedAt`, and the empty state never rendered ❌ |
| 3 | Same reset after tombstoning the seeded ids | db pulled with its WAL: `events 0, children 0, outbox 0` ✅ |
| 4 | Activity screen in that state | `No children yet` → `Add a child in Settings and the cards will appear.` → `Add a child`; no switcher, **and no cards** ✅ |
| 5 | Add-a-child from the card | Settings' `Add child` created `Test Child`, outbox drained, and the Activity screen came back usable: `T · Test Child`, `+ Log feed for Test Child` ✅ |
| 6 | A real write then | 120 ml bottle → `events 1, outbox 0`, card reads `120 ml · 12:34 PM` ✅ |
| 7 | `logcat -b crash -d` | **0** `FATAL EXCEPTION` across two installs, two resets, the empty state and the writes ✅ |

What the pass taught, recorded because both cost time:

- **A reset cannot leave zero children while the seeded ids are exempt from the tombstone pass.** They stay
  on the server, `withoutInboundApply` re-attaches after the wipe, and the snapshot restores them. The
  giveaway was the `updatedAt` they came back with: the server's, not today's. Removing the exemption is
  what made the empty state reachable at all — so `MaintenanceRepository.SEED_CHILD_IDS` is now a list for
  tests and reading, not a rule.
- **An empty app must not render work it cannot do.** With the state reachable, the six cards were still
  there, spelling `+ Log feed for ` with the name missing and opening sheets titled `for `; `Save` was a
  silent no-op (`HomeViewModel.save` returns when `childIds` is empty). `ActivityScreen` skips the cards
  when there are no children.
- **Pull the database with `-wal` and `-shm`.** A bare `babylog.db` reported `outbox 30` and `children 2`
  while the WAL said 0 and 0 — the first pull lied, and a conclusion drawn from it would have been wrong.
- **`ChildEditor` saves on every keystroke**, so the `Add child` button beneath it is not its save button;
  tapping it creates a second child.

Not performed: the import path on the phone (it needs a backup file and rows from the other account; the
reconcile sequence is covered by `SyncRepositoryTest`), and the second phone, which was not attached.

**Second look, same day and same build** (`0.18.1` / versionCode 24, installed 12:27), after the changelog
record was split by landing date — documentation only, no code change, so the APK on the phone is still the
one built from the branch tip. Summary and Trends were driven with the single child the pass created:
Summary showed `Test Child`, the `D · 1W · 2W` range chips, `120 ml` / `Sessions 1` / `Average bottle
120 ml` and `Time between feeds —` (a gap named rather than a zero invented); Trends showed `1W · 2W · 1M`,
one solid line, `8.6 ml/day`, `1 of 14 days logged` and `Days with no records count as zero.` System back
from the Activity root left the app instead of landing somewhere unexplained, and `logcat -b crash -d` stayed
at **0**. Nothing was written in this second look, and the reset itself was **not** re-run: it would have
destroyed the child and the event the first half of the pass recorded in order to prove itself again.

## Huckleberry import — data and tooling, 2026-10-28 — DONE, device-verified

**This is not a numbered phase and it moves no version rung, on purpose.** It shipped no application code:
the only new file is `scripts/huckleberry-import.py`, so the APK on both phones is byte-identical to
`0.18.1` / versionCode 24 and a new rung would describe a build that does not exist. Phase 16 and 17 keep
their numbers and their order; nothing here competes with them.

**Why it exists:** the family's real log had to come out of Huckleberry, the app this one replaces, and the
app had no path for that beyond its own backup format. The converter turns a Huckleberry CSV export into a
BabyLog backup (schema 1), which is applied through Settings → Data → **Import backup** — the app's own
restore path, so the import is proven by the same code that would restore a backup.

### As built

- **Huckleberry reuses four columns across every activity type**, which is the whole difficulty: on a growth
  row `Start Condition` is weight, `Start Location` is length and `End Condition` is head; on a nappy row
  the same three are consistency, contents and — via `Duration` — stool colour. The mapping therefore lives
  in the script with its reasoning beside each rule, and the script prints a report of what mapped, what was
  skipped and what the schema cannot hold.
- **Units are decided from the app's own wording, not guessed.** Huckleberry formats `18.44lbs.oz` and
  `2.4ft.in`, and its display for one 21 Oct measurement reads *"12lbs 11.5oz, 2ft 2.8in, 16.5in head"* — so
  the dot separates units. Trailing digits are read as: one digit = whole units, several digits with no
  decimal point = tenths, an explicit decimal = as written. That is the only reading under which every
  weight and length in both exports is monotone; the alternative makes one twin lose 224 g in a week.
- **The owner's three rulings are recorded in the script as overrides, not lost in conversation:** the
  2026-03-11 measurement's weight corrected from `3.78lbs.oz` to 3.53 kg; `Loose` consistency mapped to
  `MUSHY`; a `Dry` nappy skipped because `DiaperKind.of()` has no "neither" state.
- **What the schema cannot hold, named rather than dropped silently:** nappy volumes (`:small|medium|large`,
  25 rows), `HATED` as a solids reaction (no enum member — carried in the note), sleep mood and location
  (3 rows, into the note), meal slots (not in the export, left unset rather than inferred from the clock),
  and a bottle logged with no volume (imported with none, so the card says *No amount recorded*).
- **Imported rows carry `createdBy = "huckleberry"` and the import time as `updatedAt`**, so provenance is
  visible and a corrected value always outranks the copy already on the server.

### Device pass, 2026-10-28 — `<handset>`, `0.18.1`

| # | Check | Result |
|---|---|---|
| 1 | `install -r`; foreground checked before any input | `topResumedActivity=app.babylog/.MainActivity`, `0.18.1` / versionCode 24 ✅ |
| 2 | Import through Settings → Data → *Import backup* → `Replace`, twice | 151 events from the raw export, then 221 with the screenshots added ✅ |
| 3 | Database after the second import | `events 221` (BOTTLE 104, DIAPER 80, GROWTH 19, SOLIDS 14, SLEEP 4), `children 2`, `foods 94` after the bundled catalogue re-seeded, `outbox 0` ✅ |
| 4 | Activity screen | newest feed reads `9h 37m ago`, not `9d ago`; both children's 4:35 AM entries carry `K`/`L` badges ✅ |
| 5 | Summary, 2-week window | `14 of 14 days logged` per child, against 4 from the raw export ✅ |
| 6 | Totals recomputed by hand from the CSVs | 2066 ml / best 856 ml on 18 Oct, and 2560 ml / best 903 ml on 17 Oct — the app agreed to the millilitre ✅ |
| 7 | Trends → Growth | 8 and 11 measurements plotted with adjusted ages; the screenshot-transcribed 21 Oct row shows as `12.7 lb · <1st percentile` ✅ |
| 8 | Settings opens, system back returns to Activity with the log intact | ✅ |
| 9 | `logcat -b crash -d` | **0** `FATAL EXCEPTION` across two imports, four screens and the back press ✅ |

Not performed: the second phone was not attached, so the imported log was never observed there — only that
the outbox drained to 0, which is what sends it.

### Device pass, 2026-10-28 — the 2026-03-11 correction

The table above records the import as it landed. This one covers the correction made the same day, on the
same build, and exists to prove the drop was **field-scoped** rather than a deleted event.

| # | Check | Result |
|---|---|---|
| 1 | `install -r`, foreground before any input | `topResumedActivity=app.babylog/.MainActivity`, `0.18.1` / versionCode 24 ✅ |
| 2 | Trends → Growth → **Length**, measurements list | 37.0 (31 Dec 25), 50.0 (27 Mar), 59.0 (29 Apr), 55.8 (6 May), 66.0 (10 Jul), 71.0 (30 Sep), 71.1 cm (9 Oct) — **no 11 March 2026 row** ✅ |
| 3 | Trends → Growth → **Weight**, same child | `11 Mar 2026 · W 7.8 lb` still listed — the 3.53 kg that was kept, which is why the row was not deleted ✅ |
| 4 | System back from Trends | returns to Activity with the child switcher and today's cards intact ✅ |
| 5 | `logcat -b crash -d` | **0** `FATAL EXCEPTION` across the install and the Growth screens ✅ |

A trap worth naming for the next pass: swiping through a long measurements list scrolls the
**Weight / Length / Head** chip row off the top, so a tap at the chip's old coordinates lands on the list
instead. Dump the coordinates again after scrolling, or scroll back to the top first — the same class of
mistake as the stale `uiautomator` tree recorded under Phase 9.

### What the source data still gets wrong, and is not ours to fix

- **The 2026-03-11 measurement was closed on 2026-10-28 by keeping only its weight.** As first imported the
  row carried `3.78lbs.oz`, `1.39ft.in` and `32cm`: a length that grows 404 → 498 mm and a head 320 → 380 mm
  inside 16 days, which is not growth, so the row is either mis-dated or mistyped. The weight was corrected
  to 3.53 kg by the owner and sits smoothly between 1409 g at birth and 3867 g on 27 March; the two
  impossible measurements are now **absent**, which is what the app already means by "not recorded", rather
  than present and wrong on a percentile chart. `DROP_FIELDS` in the converter carries the rule and the reason.
- **Length readings still wobble in the source** — 59.0 → 55.8 cm across a week for one twin, 44.0 → 41.0 cm
  and 56.0 → 54.0 cm for the other. Those are plausible measurement error between two people and one
  wriggling baby, not impossible values, so they are imported as recorded: suppressing them would be editing
  the log. They show as jitter on the Length chart and are worth re-measuring, not deleting.
- **The CSV export covers 9–18 Oct only**; 19–28 Oct were transcribed from four screenshots by hand, which
  is why the converter accepts repeated `--kai` / `--levi` files in the same shape.
- **A bottle with no volume** (second twin, 21 Oct 06:00) stays volumeless: the app must not invent one.
- **The second phone had never been attached** (true of every pass until 2026-11-03 — closed below), so the
  imported log was only *inferred* to be there from an outbox that drained to 0. Closing it needed that device
  connected, not another code change.
  > **CLOSED 2026-11-03 — by the owner, by hand.** They attached the second phone and verified it themselves.
  > No agent-side pass backs this: no dump, screenshot or sign-in trace was captured, so the evidence is
  > testimony rather than measurement, and a future reader should treat it as such. What it does not claim: the
  > tombstones written during the 2026-11-03 pass were never checked on that device.

## Dataset withdrawal, reminder wording and the Trends axis — 2026-10-29 (`0.19.0`) — DONE, device-verified

**Not a phase, and it takes a rung anyway.** This closed three defects the plan had already written down — the
known gap above, A3 in Phase 14's table, and the `0.1 / 0 / 0` axis recorded out of scope during Phase 12 — so
it carries no phase number. It ships application code, so it takes `0.19.0` / versionCode 25; the Huckleberry
import moved no rung because it shipped none. One branch, `feat/growth-withdrawal-and-copy`.

| # | What | Ruling |
|---|---|---|
| 1 | A dataset a phone accepted cannot be withdrawn | **`withdrawn` flag on the manifest** — not the equal-to-bundled heuristic |
| 2 | The never-logged reminder line (A3) | **states the fact** — `No solids logged yet`, not `Time to log solids` |
| 3 | A volume series with nothing recorded | **one gridline at zero**, and no axis may repeat a label |

### As built

- **A withdrawal is a third outcome of the check, not a version comparison.** `GrowthDatasetUpdateResult`
  gained `Withdrawn(version)`, handled before `isNewer()` because a retraction names the version the phone
  already holds. `withdraw()` deletes the cache file, removes `growth.dataset.version` and
  `growth.dataset.sha256`, resets the in-memory `cachedReference` and re-emits `identityLine`; `markChecked()`
  runs either way, so a retraction that does not apply cannot make the next check re-fetch in a loop. Only the
  version the manifest NAMES is recalled.
- **The sentence lives in one function.** `GrowthDatasetUpdateResult.message()` sits beside the sealed interface
  and both `GrowthViewModel` and `SettingsViewModel` call it; the two duplicated `when`s it replaced are gone.
  The fourth branch went into both `when`s first, to keep the tree compiling, and the shared function removed
  the duplication in the same work — so no state of this branch failed to type-check.
- **The never-logged reminder names the fact.** `AFTER_LAST` with no anchor returns `No ${noun(type)} logged
  yet`, and the separate case of an anchor LATER than now (reachable only through a clock change) says
  `Logged for 1:00 PM` rather than claiming an interval that has not elapsed.
- **The axis cannot fabricate a scale.** `yRangeFor` returns a zero-span range when every value is zero and
  `floorAtZero` is set, which `yTicks` collapses to one line at zero; and a new shared
  `ticksWithDistinctLabels` drops gridlines whose formatted labels repeat. **Applied by Trends only** — Growth's
  percentiles draw `yTicks` through `unit.format` and still have no repeat filter: recorded here as a gap, not
  fixed, because it is outside the work this rung was approved for.

### Device pass, 2026-10-29, `<handset>` — DONE, device-verified

`0.19.0` / versionCode 25 installed over `0.18.1`; `topResumedActivity=app.babylog/.MainActivity` before any
input; tap coordinates from `uiautomator dump`; `adb` called with Windows-native paths.

The withdrawal was exercised **against the real server**, with the bumped copy published by hand from the
Firestore console: `reference/growth_dataset` carrying `logs/bumped-2026.11.json` (488 449 bytes, `version`
`who-2006.2026.11`, `retrievedAt` `2026-11-01`, SHA-256 `7317…cbf9`), then the same manifest with
`"withdrawn": true` added.

| # | Check | Result |
|---|---|---|
| 1 | Install and version | `versionName=0.19.0 versionCode=25` from the installed package ✅ |
| 2 | The never-logged reminder fires, and says the fact | A 30 m Health reminder for Kai (Health has **zero** rows: `BOTTLE 104, DIAPER 80, GROWTH 19, SOLIDS 14, SLEEP 4`) armed as `RTC_WAKEUP origWhen=2026-10-29 00:02:34.690 window=0 exactAllowReason=policy_permission`, fired at 12:02 AM, and the notification read **`Kai · Health` / `No health entry logged yet`** — the A3 wording, from the real alarm path ✅ |
| 3 | **The accept path** — the bumped manifest, no `withdrawn` field | Row `Updated to who-2006.2026.11`; identity line `WHO 2006 · v2026.11 · updated 1 Nov 2026`; `files/growth/updated.json` on disk at 488 449 bytes whose SHA-256 is `7317…cbf9`, **byte-identical to what was published** ✅ |
| 4 | **The withdrawal** — `"withdrawn": true` added to that same manifest | Row **`Dataset withdrawn — back to the bundled reference`**; identity line back to **`WHO 2006 · v2026.10 · updated 15 Oct 2026`**; and it did so **in the same process** — the force-stop the 2026-10-26 note required is gone ✅ |
| 5 | The disk really cleared, not just the screen | `files/growth/` left empty (`updated.json` deleted), and `settings.preferences_pb` no longer holds `growth.dataset.version` or `growth.dataset.sha256` — while `growth.dataset.lastChecked` **is** present, i.e. the check was marked and only the cache forgotten ✅ |
| 6 | A relaunch does not re-adopt it | force-stop + relaunch → identity line still `v2026.10`, no message ✅ |
| 7 | The Trends axis on real data | Bottle feed 2W `30 / 20 / 10 / 0`, 1W `25 / 20 / 15 / 10 / 5 / 0` — no two gridlines read the same number ✅ |
| 8 | The all-zero volume window | **Not reproducible on this log**: both children have a volume in every offered window (Kai's newest feed 19 h ago, Levi's 18 h 58 m). The single-`0` axis is covered by `TrendChartAxisTest` and by nothing on the phone ⚠️ |
| 9 | Both check rows still report correctly after the refactor | Settings → Growth reference → *Check for updates* → `Already up to date`, and the same from the Growth pane ✅ |
| 10 | The log is intact | database pulled with its `-wal` and `-shm` after a force-stop: `events 221` (the import's own counts), `children 2`, `outbox 0`, `user_version 3` ✅ |
| 11 | `logcat -b crash -d` | **0** `FATAL EXCEPTION` across the installs, the reminder, the charts, the publish/withdraw pair, the relaunch and the database pull ✅ |
| 12 | The cloud side put back | both documents restored to the bundled reference, then a check from Settings → `Already up to date`, identity line `WHO 2006 · v2026.10 · updated 15 Oct 2026`, and `files/growth/` still empty — the refused manifest downloaded nothing ✅ |

The reminder set for the pass was removed afterwards: its `⋮` then offered only `Set reminder`, the
`Upcoming reminders` card was gone and `dumpsys alarm` held no babylog alarm.

### Found on the phone, not fixed here

- **A never-logged reminder re-fires about a minute after every app start.** It rang at 00:02 with the app
  closed, then again at 00:07 about a minute after a force-stop and relaunch. `Reminders.fire()` arms nothing
  for `AFTER_LAST`, but `refresh()` recomputes `anchorAt + interval`, which is already in the past because the
  set-time anchor never moves, so the overdue branch rings a minute later. That is the nagging `AGENTS.md`
  rules out ("fires ONCE per anchor … re-arming would nag a parent who simply has not fed yet") and it needs a
  decision, not a quiet fix: the honest options are to remember the last fire per key, or to stop treating an
  anchor that is the set time as overdue.
- **Growth's percentile axis can still draw two gridlines reading the same number** — see *As built* above.

### Not performed, and why

- The second phone, as in every pass since the import.
- The all-zero volume window, for want of data.

### Restored afterwards, 2026-10-29

Both `reference` documents were put back to the bundled reference (`who-2006.2026.10` / `2026-10-15` /
`2df3475…18cb5c`, and the asset's own bytes in `growth_dataset`), and a check reports `Already up to date` with
nothing downloaded. The caution that outlives the test: **never flip `withdrawn` to `false` while
`datasetVersion` still names a newer version** than the bundled one — the phone would re-accept the synthetic
dataset, and because only a *newer* version is accepted, getting back to bundled would then need another
withdrawal. Withdraw it, or restore the version with the flag.

## Phase 16 — Update delivery — DONE, device-verified (`1.0.0`, first release)

Getting a new build onto the phones without a cable. Done in one branch, `chore/phase16-release-signing`.

### As built

- **The signing config is conditional, and that is the whole design.** `app/build.gradle.kts` reads
  `keystore.properties` from the repo root, else from `%USERPROFILE%\keystores`, and creates the `release`
  signing config **only when one is found**. Without it the release build type has no `signingConfig`, so
  `:app:testDebugUnitTest` and `:app:assembleDebug` are untouched and `:app:assembleRelease` emits
  `app-release-unsigned.apk`. Measured, not assumed: with the file moved aside, `apksigner verify` answers
  **`DOES NOT VERIFY`**. A clone without the family's key can build and test; it cannot sign, and it says so.
- **The key is created by the owner, not by an agent.** `scripts/create-release-keystore.ps1` prompts for the
  password twice with `Read-Host -AsSecureString`, hands it to `keytool` through `-storepass:env` so it never
  enters argv, a shell history or a transcript, writes `keystore.properties` beside the `.jks`, and refuses to
  overwrite an existing key. It also writes the fingerprints to `babylog-release.fingerprints.txt`, which is
  how the SHA-1 below reached the console without the password ever being repeated.
- **Everything lives outside the repository and outside `Documents\Syncthing`:**
  `C:\Users\<you>\keystores\babylog-release.jks`, alias `babylog`, `CN=BabyLog, OU=Family, O=BabyLog,
  C=TT`, RSA 4096, PKCS12, valid to 2054-03-16; SHA-1 `07:66:EE:2F:85:C2:ED:2C:8E:2F:C5:B6:B0:5B:90:8D:D4:54:5F:4F`,
  SHA-256 `50:D9:C8:…:9C:04`.
- **Delivery is a manual App Distribution upload** — no `com.google.firebase.appdistribution` plugin, no
  service-account JSON, no `firebase-tools`, which is one fewer long-lived secret for a release that happens
  every few weeks. `docs/DECISIONS.md` ADR-0001 records the choice and its cost.
- **The rung is `1.0.0` / versionCode 26**, which is also the first build whose `versionCode` decides anything
  outside a human's head: App Distribution compares it to decide what is newer.
- **Records:** `docs/release-signing.md` (the runbook: what the key is for, where it lives, the fingerprints,
  build/verify commands, the one-time phone move, the upload steps, the tag rule); `docs/DECISIONS.md`
  ADR-0001 (key custody and the manual channel); `docs/firebase-setup.md`'s "What is still Phase 16" closed
  out; `README.md`; `AGENTS.md`'s release rules, ladder and counts.

### Device pass, 2026-10-29, `<handset>` (SM-S918U1) — DONE, device-verified

`1.0.0` / versionCode 26 installed over the air, not by cable: `installerPackageName=com.google.android.packageinstaller`,
`firstInstallTime = lastUpdateTime = 13:16:03`, i.e. a fresh install rather than an update — which is what a
signature change from the debug key forces.

| # | Check | Result |
|---|---|---|
| 1 | The phone runs the release | `dumpsys package app.babylog` → `versionCode=26`, `versionName=1.0.0`, `minSdk=26`, `targetSdk=36` ✅ |
| 2 | It is signed by the release key | the installed `base.apk` pulled back and read with `apksigner`: `CN=BabyLog, OU=Family, O=BabyLog, C=TT`, SHA-1 `0766ee2f85c2ed2c8e2fc5b6b05b908dd4545f4f` — the keystore's own, not the debug `24:E3:…` ✅ |
| 3 | What was delivered is what was built | SHA-256 of the built `app-release.apk` and of the installed APK are both `4dac7cfb823a1c61d532a21a9226783aa1c0941ac5576d319de26c4f23b43bca`, 14 675 571 bytes ✅ |
| 4 | It launches | `topResumedActivity=app.babylog/.MainActivity` after `am start` ✅ |
| 5 | Sync came back after the wipe | Settings → Sync reads `Signed in as joelm3103@gmail.com`, `Everything here is on the server.`, `Up to date.` ✅ |
| 6 | The log is intact, counted from the app's own export | exported at 13:34 through Settings → Data → Export: **219 live events** — BOTTLE 104, DIAPER 80, GROWTH 19, SLEEP 2, SOLIDS 14 — and 2 children (`kai`, `levi`). The export made before the uninstall at 13:12 held the same 219 live rows (221 including 2 tombstones) ✅ |
| 7 | The app shows the restored history | Activity screen for Kai: Bottle `1d ago` 6 oz, Diaper `1d ago` Wet, Solids `10d ago` Carrot, Pumpkin, Chicken, Broccoli ✅ |
| 8 | No crash | `logcat -b crash -d` → **0** `FATAL EXCEPTION` across the launch, the Settings walk and the export ✅ |
| 9 | Google Sign-In on a release build | **Verified by the owner at close-out**: signed out of Sync, tapped `Sign in with Google` and was signed back in — so the registered release SHA-1 is accepted on a `CN=BabyLog` build ✅ |
| 10 | The second phone | Not attached during this pass, as in every pass since the import ⚠️ (verified by the owner 2026-11-03) |
| 11 | System back, checked at close-out | Back from Settings returned to Activity with the app still foreground — `topResumedActivity=app.babylog/.MainActivity`, and the screen behind it was `Today` with the three cards ✅ |
| 12 | A logging sheet, opened and dismissed without saving | `+ Log feed for Kai` opened the Bottle feed sheet (`Bottle feed`, `for Kai`, date `29 Oct 2026`, time `1:42 PM`, the amount presets, `Save`); back dismissed it, `Save` was gone and Activity was still foreground ✅ |

The database itself could not be read at all: `run-as app.babylog` answers **`package not debuggable`**, so a
release build has no data directory `adb` will open and the counts above come from the app's own Export
instead. That is the one place the "pull the database with its `-wal` and `-shm`" rule stops working, and
`AGENTS.md` now says so.

### Found on the phone, not fixed here

- **A custom food is local-only, and a reinstall loses it.** `FoodRepository.addCustom` writes the row and
  queues nothing — the outbox is fed by `EventRepository`, `ChildRepository` and `MaintenanceRepository`
  only — so after the reinstall the three genuinely custom foods (`Callaloo`, `Greek Yogurt`, `Pancake`)
  were gone, while the eight that came back are names the bundled asset also carries. Worse in principle
  than in this pass: a synced event references a custom food **by id**, so the other phone holds the
  reference and never the name. Pre-existing, not introduced by this phase, and it needs a decision rather
  than a quiet fix — either foods join the outbox, or an event stores the names it displayed.
- **Two `SLEEP` tombstones did not come back, and that is by design.** `applyEvent` ignores a tombstone for
  an id this phone has never seen, because there is nothing to delete. Recorded so the next pass does not
  read it as data loss: 219 live rows went out and 219 came back.
- **A release build cannot be read over `adb`,** which is the one place the "pull the database with its
  `-wal` and `-shm`" rule in `AGENTS.md` stops working. Export is the substitute, and it is in `AGENTS.md`
  now.

**Not performed, and why:** the second phone (not attached). Google Sign-In was tested by the owner at
close-out rather than in this pass, which is why row 9 says so.

**Tag:** `v1.0.0`, annotated, cut on the delivered build — the first tag in the repository, and the only one:
`0.1.0` … `0.19.0` stay untagged because a phase ending is not a release.

## Phase 17 — Five todo items — DONE, device-verified (`1.1.0`)

The `todo.txt` batch, renumbered to 17 on 2026-10-29 when the owner chose to land features before Ship
(see *Renumbering, 2026-10-29*). One branch, `feat/phase17-todo-batch`.

### As built

- **A child switcher on Trends**, the only tab without one. The selection was already global and
  `TrendsViewModel` already resolved it, so the work was the shared `ScreenHeader` plus its callback — no new
  stored state, and choosing a child on Trends changes the same selection Activity and Summary read.
- **`1Y` and `All` as buckets, not a denser line.** `TrendRange` gained a `bucket` (`DAY`/`WEEK`/`MONTH`);
  `1Y` draws the last 52 weekly buckets ending with this week's Monday, and `All` draws one bucket per calendar
  month **starting at the first event** — `EventDao.earliestStartedAt` answers when the log begins, because a
  window from epoch would draw months of zeros before either twin was born and average them into every
  headline number. Every caption that counted days now counts the bucket it draws (`oz per week`,
  `Months with no records count as zero`, `Best week`, `4 of 52 weeks logged`), which is a rule rather than
  polish: a chart of weeks that said "7 of 30 days" would be describing a different chart. `trendAxisUnit`
  was caught still saying `per day` above weekly buckets **on the phone**, in the first build of this pass.
- **An About card** — `domain/About.kt`'s `aboutVersionLine` reads `BuildConfig`, so the row a parent would
  quote in a bug report cannot disagree with the installed APK; the credits name the two children; the
  repository opens with an `ACTION_VIEW` intent (guarded); the WHO 2006 attribution travels with it, and one
  line states there are no analytics and no trackers.
- **Per-activity hiding on the Activity tab** — `activities.hidden` in DataStore (a sorted set, unknown names
  dropped), six toggles in Settings → Activities, `HomeUiState.shownCards`, and an all-hidden empty state that
  names the way back. Hiding one that has a reminder asks first and cancels the rule with the hide
  (`reminderKeysToCancelOnHide`, every child's rule for that activity, because the card is the only place a
  rule can be edited). A reset clears the set, so first run shows everything.
- **Haptics from one helper** (`ui/common/Haptics.kt`) with four meanings — tap, toggle, confirm, long press —
  wired at `ChoiceChip`, `PillButton`, `SwitchRow`, `EntryRow`, `SwitcherPill`, the sheet's Save and the bottom
  `NavItem` — the last one added after the owner tried the first build, because switching tabs was the most
  repeated action in the app and the only surface left silent. Re-tapping the tab already on screen does not
  buzz: that is not a switch. No preference: `View.performHapticFeedback` already honours the device's
  touch-feedback setting.
- **Review before merge found two things the pass could not.** `All` threw when the FIRST event was dated in
  the future (a mistyped year puts it there), because the window took its start from a bucket list that can be
  empty; the monthly buckets are now clamped to the current month, so every range draws at least one bucket.
  And `setActivityHidden` wrote the hidden flag only after sweeping the rules, so a rule saved in between
  survived a hide; it now writes the flag first, re-reads, then sweeps. Both have tests.
- **The rung is `1.1.0` / versionCode 27**, not the `0.20.0` the scope first said: after `1.0.0` the ladder
  cannot go back to a `0.x` name, and a `0.20.0` would install by versionCode and still read as going
  backwards on the phone.

### Device pass, 2026-10-30, `<handset>` (SM-S918U1) — DONE, device-verified

Installed **in place over the release**: `:app:assembleRelease` + `adb install -r` with the same key, which is
what the Phase 16 rule requires now that a debug APK can no longer install over `1.0.0`.

| # | Check | Result |
|---|---|---|
| 1 | An update, not a reinstall | `versionCode=27 versionName=1.1.0`; `firstInstallTime` still 2026-10-29 13:16, `lastUpdateTime` 2026-10-30 12:59 ✅ |
| 2 | Signed by the family's key | `apksigner` → `CN=BabyLog, OU=Family, O=BabyLog, C=TT`, SHA-1 `0766ee2f…5f4f` ✅ |
| 3 | The log survived the update | the app's own Export at 13:28: 221 rows, **219 live** (BOTTLE 106, DIAPER 80, GROWTH 19, SOLIDS 14), 2 children, 91 foods ✅ |
| 4 | The Trends switcher works and changes the data | header carries `Kai`/`Levi`/`Both`; the caption follows it, and the totals differ (`273 oz` for Kai against `274.6 oz` for Levi) ✅ |
| 5 | Five range chips, one row | `1W · 2W · 1M · 1Y · All`, each with its spoken form (`1 week` … `all time`), no wrap on this phone ✅ |
| 6 | `1Y` counts weeks | `Bottle feed · the last 52 weeks · oz per week`, `Weeks with no records count as zero.`, `4 of 52 weeks logged`, `Best week 127 oz · 12 Oct` ✅ |
| 7 | `All` counts months, from the first event | `Bottle feed · all time · oz per month`, `Months with no records count as zero.`, `1 of 11 months logged`, `Best month 274.6 oz · Oct` — 11 buckets, not a window from 1970 ✅ |
| 8 | The per-day ranges are unchanged | 2W still reads `the last 14 days` / `Days with no records count as zero.` / `13 of 14 days logged` / `Best day 30.5 oz · 17 Oct` ✅ |
| 9 | Hiding and restoring an activity | `Solids` hidden in Settings → Activities disappeared from Activity; shown again, the card came back ✅ |
| 10 | The hide-with-reminder prompt, both answers | with a Health rule armed (`Every 3h (4:31 PM)`), hiding asked `Hide Health?`; **Keep it visible** left card and rule untouched, **Hide and cancel the reminder** removed the card and left `dumpsys alarm` with no future babylog alarm ✅ |
| 11 | The About card | `Version 1.1.0 (build 27) · release build`, `Built by joelm3103, for Kai and Levi.`, `Open the repository`, the WHO 2006 line and the no-analytics line ✅ |
| 12 | `logcat -b crash -d` | **0** `FATAL EXCEPTION` across two installs, the reminder-sheet work, three hide/restore cycles and the export ✅ |
| 13 | Haptics | **Judged by the owner** — the feel is right on card buttons, chips, toggles, long-press delete and Save. The tab bar was missing one; `NavItem` now taps the same helper, and the three tabs were switched on the phone afterwards with 0 `FATAL EXCEPTION` ✅ |
| 14 | System back, checked at close-out | Back from Settings returned to Activity with the app still foreground; back with the Bottle feed sheet open dismissed the sheet and left Activity in front, `topResumedActivity=app.babylog/.MainActivity` ✅ |

### Found on the phone, not fixed here

- **The reminder sheet opens with `Notify` OFF even when notifications are granted.** The permission had just
  been allowed (the sheet's own "Notifications are off" banner was gone), yet `Notify` still started off, so
  `Set` saved a **disabled** rule: no chip, no alarm, and nothing on screen saying why. Turning `Notify` on made
  the same rule work (`Every 3h (4:31 PM)`). Pre-existing — this phase did not touch the sheet — but it is the
  worst kind of quiet: the parent believes a reminder exists.
- **Tapping the disabled `Set` button dismisses the sheet.** `saveEnabled = childId.isNotEmpty()` until a child
  is picked, and the tap falls through to the sheet's dismiss handler, so Save appears to do nothing except
  close the sheet. Also pre-existing.
- **A disabled rule is indistinguishable from no rule.** The `⋮` still reads `Set reminder`, which is what
  concealed the first finding for three attempts.
- **The second phone was still not attached during this pass**, as in every pass since the import — the owner
  verified it by hand on 2026-11-03 (see the closed note in the import section). (Haptics WERE judged by the
  owner — see row 13 — and the tab bar was added because of it.)

## Phase 18 — Published food catalogue — DONE, device-verified (`1.2.0`)

One branch, `feat/phase18-food-catalogue`; version `1.2.0` / versionCode 28. The work adds a shared
owner-published catalogue without making the bundled list or either parent's picker dependent on a live
server.

### As built

- `reference/food_catalogue` is the owner-published canonical list. It overrides the bundled asset by stable
  id; clients read it but cannot publish it. Parent additions travel as `foods_pending/{id}` through the
  existing outbox, while `foods_proposals/{id}` records a rename request for the owner.
- `localName`, `hidden` and `lastUsedAt` remain per-device opinions. Renaming a published food changes only
  this phone's picker and queues a proposal; pending additions can be renamed or deleted. Missing pending
  rows are hidden rather than hard-deleted so older meals retain their food name.
- Schema v4 adds `localName` and `hidden`, with a v3 migration and exported schema. Reset withdraws pending
  additions and clears local curation; bundled foods seed again on the next launch. The remote Solids summary
  now says `Unknown food` instead of showing an unknown id as a name.
- `docs/DECISIONS.md` records ADR-0002: the catalogue is published, not row-synced. The Firestore rules and
  `docs/firebase-setup.md` describe the published, pending and proposal documents.

### Device pass, 2026-11-01 — `<handset>` (SM-S918U1), `1.2.0`

Installed the release-signed APK (the debug APK is not an update for the installed release identity). The
package reported versionCode 28 / versionName 1.2.0. `topResumedActivity` was checked before the first tap;
all taps below came from the current `uiautomator dump`. No food or log entry was changed during the pass.

| # | Check | Result |
|---|---|---|
| 1 | Install/launch and foreground before input | `adb install -r` succeeded; `topResumedActivity=app.babylog/.MainActivity`; `versionCode=28 versionName=1.2.0` ✅ |
| 2 | Activity screen after update | Kai, Levi and Both selectors, today's Activity cards and the existing reminder rows rendered ✅ |
| 3 | Solids sheet and food picker | Opened Solids for Kai; picker listed `Salmon`, `Sweet potato`, `Almond butter`, `Apple`, and other foods ✅ |
| 4 | Per-food actions and rename disclosure | Salmon's menu offered `Rename` and `Hide from the list`; the rename dialog said it changes the name on this phone while the shared catalogue stays unchanged. Dialog dismissed without saving ✅ |
| 5 | System back | Back dismissed the rename dialog and picker/sheet layers; the app remained foreground on Activity ✅ |
| 6 | Crash buffer | `logcat -b crash -d`: **0** `FATAL EXCEPTION` ✅ |

**Not verified on-device in this pass:** live Firestore rules deployment and a second phone's read. (The second
phone was verified by the owner by hand on 2026-11-03 — see the closed note in the import section — so what
remains here is the console rules deployment, which no pass has exercised.) An existing
capture, `logs/phone-food-picker.png`, shows `Console Test Food` in the picker, but it does not establish a fresh
server read in this pass or verify the other phone. Cross-device Firestore behavior remains a Phase 20 acceptance
check. The rename dialog was dismissed without changing data.

**Evidence files:** `logs/phase18-closeout-build.log`, `logs/phase18-closeout-picker.xml`,
`logs/phase18-closeout-crash.log`, and `logs/phone-food-picker.png`.

## Phase 19 — Settings submenus — DONE, device-verified (`1.2.1`)

One branch, `feat/settings-submenus`; version `1.2.1` / versionCode 29. The work changes how Settings is
reached and nothing about what it holds.

### As built

- `ui/settings/SettingsSection.kt` is the whole of the new shape: an enum whose declaration order is the menu
  order and whose `title` is the only name a group has — the menu row, the submenu heading and what the back
  arrow returns to — plus `SettingsNav`, the two-level value that decides what a back press does.
- `SettingsScreen` renders one view at a time: the category menu, where each of the eleven rows carries a
  one-line summary of its group's current value, or that group's existing content under a back arrow. Every
  control, validation, confirmation, gating rule and destructive dialog is the original code, moved rather
  than rewritten; nothing was dropped and nothing moved between groups.
- The alternating card shading is gone with the second card it existed to separate: there is one card per view
  now, so there is no boundary for a shade to fall between.
- `AppScaffold` owns which submenu is open, because the shell has to answer two questions about it — what back
  does, and which list the top scrim follows. Settings therefore keeps two scroll states: the menu returns to
  its own row, and each submenu opens at its own top. Leaving Settings resets to the menu, so a submenu is
  never restored without its parent.
- `SettingsNav.back()` returns null at the menu, which the shell reads as "leave Settings": the back rule is a
  tested value rather than an `if` in a handler, because a menu that swallowed back would trap the parent and a
  submenu that passed it through would dump them on Activity from a screen they had just opened.

### Device pass, 2026-11-02 — `<handset>` (SM-S918U1), `1.2.1`

Two passes were made the same day against the same artifact: one that found the two defects below, and this
one, which re-checked everything after the fix. Every tap coordinate came from a fresh `uiautomator dump` and
`topResumedActivity` was checked before the first input. **No setting was changed during either pass** — the
defects were found by reading the screen, not by flipping a control.

The release APK is installed, not the debug one: `adb install -r app-debug.apk` answers
`INSTALL_FAILED_UPDATE_INCOMPATIBLE` because `1.0.0` onward is release-signed, and uninstalling to make the
debug build fit would discard the log. AGENTS.md records that rule; this pass exercised it.

| # | Check | Result |
|---|---|---|
| 1 | An update, not a reinstall | `versionCode=29 versionName=1.2.1`; `firstInstallTime` unchanged at 2026-10-29 13:16, `lastUpdateTime` 2026-11-02 09:49 ✅ |
| 2 | The pass ran against this exact artifact | the installed `base.apk`, pulled back off the phone, is byte-identical to the built APK — both SHA-256 `4666322e…3670` ✅ |
| 3 | Signed by the family's key | `CN=BabyLog, OU=Family, O=BabyLog, C=TT`, SHA-1 `0766ee2f…5f4f` — the same certificate as `1.2.0` ✅ |
| 4 | The debug APK is refused, as expected | `INSTALL_FAILED_UPDATE_INCOMPATIBLE`; the release APK was installed instead and nothing was uninstalled ✅ |
| 5 | Foreground before any input | `topResumedActivity=app.babylog/.MainActivity` ✅ |
| 6 | The menu lists all eleven groups with summaries | `Appearance · System`, `Units · Custom`, `Day & night · 6:00 PM – 4:00 AM`, `Bottle presets · 3 presets`, `Medication presets · None`, `Children · 2 children`, `Activities · 2 hidden`, `Data · Backup & reset`, `Sync · Signed in`, `Growth reference · WHO 2006 · v2026.10`, `About · 1.2.1` ✅ |
| 7 | No bottom navigation bar in Settings | the menu dump held no `Activity` / `Summary` / `Trends` node; all three came back on leaving ✅ |
| 8 | Back is "up one level" | from **all eleven** submenus, back returned to the menu; a second back left Settings for Activity ✅ |
| 9 | Each submenu names itself | every submenu dump held the back arrow (`content-desc="Back to Settings"`) then its own title as the first two labels ✅ |
| 10 | Every group is reachable, its content present | Appearance (`System` filled), Units (six chip rows), Day & night (`6:00 PM` / `4:00 AM`), Bottle presets (`5 oz` / `5.5 oz` / `6 oz`, each removable), Medication presets (empty), Children (both editors, archive/delete, `Add child`), Activities (six switches), Data (export/import/reset, *Delete all server data* only while signed in), Sync (signed in, `Everything here is on the server.`, `Up to date.`), Growth reference (full line + `Check for updates`), About (`Version 1.2.1 (build 29) · release build`) — presence and content only; no control was toggled ✅ |
| 11 | The summary agrees with the control behind it | `System` is the filled chip in Appearance; neither `Metric` nor `Imperial` is filled while the row reads `Custom` — both read off a zoomed screenshot, not assumed ✅ |
| 12 | A long submenu clears the system bar | the nav-bar inset is 135 px (`[0,2181][1080,2316]`); after scrolling the Children submenu to its end, `Add child` finished at y=2111 — 70 px clear ✅ |
| 13 | The top fade follows the open submenu | behind the clock and status icons the background is **opaque** on a scrolled submenu — the bug the shell's one-scrim rule was written for ✅ |
| 14 | Nothing was written | the menu summaries after the pass read exactly as before it: `System`, `Custom`, `3 presets`, `None`, `2 children`, `2 hidden`, `Signed in` ✅ |
| 15 | Crash buffer | `logcat -b crash -d`: **0** `FATAL EXCEPTION` across the install, all eleven submenus and the back navigation ✅ |

**A script artifact worth recording, because it looked like a defect:** the first sweep reported the
`Day & night` row missing. The dump escapes `&` as `&amp;`, so the label match failed; the row and its summary
were present, and it was opened by coordinate. Nothing in the app was wrong.

**Found on the phone, fixed in the same pass, then re-verified on it:**

- **`2 childs` on the Children row.** The summary appended an `s` to `child`. `counted()` now takes its plural
  explicitly, and `SettingsSummaryTest` pins `1 child` / `2 children`. The row reads `2 children` on the phone.
- **The growth-reference value wrapped to two lines** and, being a left-aligned block in a column of right-aligned
  one-line values, made its row taller than every other row in the menu. The row now shows `WHO 2006 · v2026.10`
  (`datasetSummary()` drops the update date, not the version, because the version is what identifies the tables),
  and the submenu still shows the full line. Re-measured on the phone after the fix: one line, 49 px tall like
  its neighbours.

Both were in code that had never been committed, so the phase's first commit carries the corrected rows rather
than shipping the two defects and repairing them in a second commit.

**Checked because a review flagged it:** system back with a Settings confirmation dialog open dismisses the
**dialog**, not the submenu: it does not reset `settingsSection` underneath a still-mounted dialog. Driven on the
phone from Settings → Data → *Import backup*, back closed the dialog and left the Data submenu in place, and a
second back returned to the menu. The dialog owns the back press through `AlertDialog`'s default
`dismissOnBackPress`, so the shell's handler never runs while one is up. `logcat -b crash -d` stayed at **0**.

**Not verified in this pass:** no setting was toggled, so no write path was driven end to end. Every callback the
submenus render is the original, unmoved code, but this pass proves the screens, not the writes.

## Phase 20 — Tracking changes — DONE, device-verified (`1.3.0`)

One branch, `feat/phase20-tracking-changes`; version `1.3.0` / versionCode 30. Four changes the owner asked for,
and nothing else: a feed in one tap, the child's name where an initial was, a reminder sheet that sets rather
than asks, and a notification that leaves when its reason does.

### As built

- **The Bottle card borrows the Diaper path deliberately.** `HomeViewModel.logBottle(amountMl)` is
  `logDiaper(kind)` with a different draft, so Both mode writes one row per child with an identical timestamp and
  the write path is the one already covered against a real database. `quickBottleDraft` holds the whole of the
  quick-log decision — activity, amount, moment — and exists because `HomeViewModel.state` is Room-backed, so a
  test that went through the ViewModel would hang rather than assert.
- **The presets come from Settings, through the state.** `HomeUiState.bottlePresets` is fed by
  `settings.bottlePresets`, so a preset added in Settings appears on the card and the card never learns that
  Settings exists. Labelled in the selected unit, stored in ml.
- **Both-mode names.** `EntryRow` takes a `childName` instead of `initials`, and the card's headline shows the
  name beside the big number. The Growth and Health columns lost their badge as well, because that column is
  already headed by the name — the same fact twice, paid for in width.
- **The reminder sheet's `Notify` toggle is gone and its save is unconditional** (`sheetSaveConfig`), because a
  rule the parent set is a rule they want to hear. Removal moved in beside it through `ActivitySheet`'s existing
  destructive row, which needed one new parameter (`deleteLabel`) so the button does not claim to delete an
  entry that was never created.
- **Clearing is a pure rule plus a seam.** `shouldClearPostedNotification` compares the live notification's own
  `postTime`, read from the shade, with the child's newest row of that activity — so the app never has to
  remember what it posted, and a notification swiped away by hand is simply absent. `ReminderNotifier` is the
  third interface of the `ReminderDeps` family, and `Reminders.refresh()` is where it is called, because that is
  the one function every write, rule change, launch and boot already goes through.
- **A build-time surprise worth recording:** Kotlin's typed `combine` overloads stop at five flows, and the sixth
  silently resolves to the `Array<Any>` vararg form — which compiles as a type error several lines away from the
  cause. The state is now two `combine` calls, the inner one keeping every type named.

### Device pass, 2026-11-02 — `<handset>` (SM-S918U1), `1.3.0`

**Read-only, with one exception**: the child switcher was moved to Both and back to Kai, which is a stored
display preference. Nothing was written to the log. One tap writes a real feed, and this log is the children's
health record, so that check belongs to the owner rather than to a script.

| # | Check | Result |
|---|---|---|
| 1 | An update, not a reinstall | `versionCode=30 versionName=1.3.0`; `firstInstallTime` unchanged at 2026-10-29 13:16, `lastUpdateTime` 2026-11-02 10:54 ✅ |
| 2 | The pass ran against this exact artifact | the installed `base.apk` pulled back is byte-identical to the built APK, both SHA-256 `145b5109…34f2` — re-checked after the review's id fix, which rebuilt it to `60546a1f…b2b9` and reinstalled it the same day ✅ |
| 3 | Foreground before any input | `topResumedActivity=app.babylog/.MainActivity` ✅ |
| 4 | Preset pills on the card | `5 oz`, `5.5 oz`, `6 oz` — the three stored presets, labelled in fl oz — tappable above the log button ✅ |
| 5 | The pills did not replace the sheet | `+ Log feed for Kai` still full width beneath, reading `for both` in Both mode ✅ |
| 6 | A Both-mode card names the child | `Kai` beside the big number, with the time beside it, where a `K` badge used to be ✅ |
| 7 | Both-mode rows name the child | `Kai 6 oz 5:39 AM` and `Levi 4 oz 5:05 AM` ✅ |
| 8 | Reminder chips kept their badge | the two Bottle chips still carry `K` and `L` — a chip has no room for a name ✅ |
| 9 | No `Notify` toggle anywhere in the sheet | `For`, `Mode`, `Time`, `Skip night`, `Days` — and no switch ✅ |
| 10 | Save reads as an action, not a question | `Update` where the child has a rule, `Set` where they do not ✅ |
| 11 | Removal only when there is something to remove | `Remove notification` on the Bottle sheet; absent on the Diaper sheet, where neither child has a rule ✅ |
| 12 | Dismissing writes nothing | back closed both sheets, Activity stayed in front, the log and the rules were unchanged ✅ |
| 13 | `logcat -b crash -d` | **0** `FATAL EXCEPTION` across the install, both selections, two sheets and the back presses ✅ |

**Known, unfixed — the one gap a review found that is not worth a schema change:** the clearing rule compares
wall-clock values (an event's `startedAt` against the notification's own `postTime`), so a log written *after* a
manual BACKWARDS clock change is stamped earlier than the post and does not clear the notification. Nothing the
other way round: moving the clock forward changes neither value, so a notification with no log behind it stays
up, and every other way the comparison can hold involves an actual log of that activity. The failure mode is
therefore the pre-Phase-20 behaviour — a stale notification the parent swipes — never a wrong clear. Fixing it
properly needs a monotonic write sequence rather than a timestamp, which is a storage change this phase's scope
excludes, and the app orders events by wall clock everywhere else (relative times, day grouping, totals).
**Accepted as a limitation on 2026-11-03**, so it is not to be fixed without being asked.

**Left to the owner, and confirmed by them the same day:** the one-tap feed — the write itself, which the pass
declined to make because a test feed is a record that never happened in this log — and the notification
clearing, which needs an alarm to have actually fired. Both work. The clearing decision is pinned by
`PostedNotificationTest` as well, and the posting path is unchanged.

## Phase 21 — Time picker — DONE, device-verified (`1.4.0`)

One branch, `feat/phase21-time-picker`; version `1.4.0` / versionCode 31. The owner asked for it before the
redesign started, and it changed one composable and everything that reaches it.

### As built

- **`TimeOfDayDialog` is the only place a time is edited** — every logging sheet arrives through `TimeRow`, and the
  reminder sheet arrives for a fixed-time rule — so the switch reached the whole app through one change.
- **Three inputs, one control.** The dial is Material3's `TimePicker`; the wheels are `WheelTimePicker`, written
  here because Material3 has no wheel and the alternative was hosting the platform `android.widget.TimePicker`,
  which would have been the one control in a sheet wearing the OS's chrome rather than the app's. The two-field
  keyboard stays as the third, because it is the only input that can type an exact minute without scrolling.
- **The switch is in the dialog, not in Settings** (the owner's choice): one button cycling dial → wheel →
  keyboard, its `contentDescription` naming where the tap LEADS rather than where it is. Nothing is persisted, so
  the dialog opens on the dial every time rather than on last week's habit.
- **One pair of numbers, two objects.** The dialog's `hour`/`minute` are the wheel's value and the hand-off between
  modes; the `TimePickerState` is the Material modes' value. Leaving a Material mode copies its numbers into the
  pair, and the pair keys the state — so cycling never loses an edit, and `TimePickerState.hour` never needs a
  setter it does not have.
- **The wheel is three snapping `LazyColumn`s**, with the selection read from the first visible index and the
  vertical padding placing that row on the band. Two fixes came from using it rather than only writing it: a
  column reported the position it was CREATED at before the list had measured — which can say `00` to a wheel
  showing midday, leaving the dialog holding a number nobody chose — so a column now reports only a move it was
  not built with; and the AM/PM column routed the hour back through an index the parent may already have moved, so
  it now reports the half of the day and the dialog does the shift on the number that owns it.
- **The test caught its own author.** The first version of the AM/PM test asserted that switching halves and back
  was identity. It is not — flipping the half is the entire point of the control — so the test was rewritten to
  pin what is true instead: the half flips, the hand never moves, and each shift is idempotent.
- **The one width worry in the code was measured rather than inherited.** The comment above `TimeRow` said the dial
  had been avoided because "the dial does not fit a phone dialog at small widths". It fits, at 1.0 and at 1.3, and
  the comment now says so.

### Device pass, 2026-11-02 — `<handset>` (SM-S918U1), rows run on the rung-less build, re-checked on `1.4.0`

**Every row in the table below was run on the rung-less build** (`versionCode 30`), which is the behaviour the
version fields do not change; the post-rung re-check on `1.4.0` covered rows 0, 2 and 5 rather than all of
them, and the note underneath says exactly which. Read-only apart from two stored values used to reach the
states under test, both restored: the app's Time format (12 → 24 → 12-hour) and the system font scale
(1.0 → 1.3 → 1.0). **Nothing was written to the log.**

The release-signed APK is installed, not the debug one: `adb install -r app-debug.apk` answers
`INSTALL_FAILED_UPDATE_INCOMPATIBLE` because `1.0.0` onward is release-signed, and uninstalling to make the
debug build fit would discard the log.

**On the version the phone reported, recorded because the ordering was not clean.** The fourteen rows below
were driven on this phase's source *before* its rung was set, so while they ran the phone reported
`versionCode 30 versionName 1.3.0` — the source was identical and only the two version fields differed. After
the rung was set, the built APK was re-installed on the same phone and the pass re-checked against it: the
package reports `versionCode=31 versionName=1.4.0`, the installed `base.apk` is byte-identical to the build
(SHA-256 `bfa8cb20…23eb`), `firstInstallTime` is still 2026-10-29, the dialog opens on the dial, the switch
cycles `Switch to wheels` → `Switch to keyboard` → `Switch to clock face`, and the crash buffer is **0**.
The rows themselves are the behaviour, which the version fields do not change.

| # | Check | Result |
| 0 | Install, and the version the phone reports | the pinned debug install is refused (`INSTALL_FAILED_UPDATE_INCOMPATIBLE`); the release APK installs over `1.3.0` and the package then reports `versionCode=31 versionName=1.4.0` with `firstInstallTime` unchanged at 2026-10-29 ✅ |
|---|---|---|
| 1 | Opens on the clock face | the switch offers `Switch to wheels` on the first frame ✅ |
| 2 | The switch names its destination | `Switch to wheels` → `Switch to keyboard` → `Switch to clock face` ✅ |
| 3 | The dial is complete, not clipped | twelve hour labels, x 244–922 of 1080 ✅ |
| 4 | The dial survives a large font | at **1.3**× all twelve still render, x 241–903, fields and buttons intact ✅ |
| 5 | Three columns, no overlap | row pitch 124 px against 67 px of text at 1.3× ✅ |
| 6 | The wheel opens on the dial's value | dial 12:13 PM → band `12 : 13 : PM` ✅ |
| 7 | Scrolling changes the value | one row up → minute band `14`, column `13, 14, 15` ✅ |
| 8 | The value survives a mode change | wheel `12:14` → keyboard fields `12` and `14` ✅ |
| 9 | The half-day column leaves the hand alone | swiping AM/PM moved the band `PM` → `AM` with the hour column still on `12` ✅ |
| 10 | 12-hour shows AM/PM and the inner ring | `Select AM or PM` on the dial; three wheel columns ✅ |
| 11 | 24-hour drops both | the dial lists **0–23 hours** with no AM/PM field; the wheel drops to two columns showing `11, 12, 13` ✅ |
| 12 | The keyboard is unchanged | two fields and the AM/PM toggle ✅ |
| 13 | Dismissing writes nothing | Cancel and back left the sheet and the log untouched ✅ |
| 14 | `logcat -b crash -d` | **0** `FATAL EXCEPTION` across both font scales, both clock formats and ~20 mode switches ✅ |

## Phase 22 — Filled Soft redesign — DONE, device-verified (`1.4.1`)

One branch, `feat/phase22-filled-soft`; version `1.4.1` / versionCode 32 — a PATCH, because the card surface
changed and no capability, control, copy or stored byte did. The owner's "Filled Soft" reference is applied
across every tinted card: the tiled stroke-glyph watermark is retired in favour of a vertical gradient fill,
one large filled illustration per activity, and scattered filled silhouettes and hearts. Measured from the
reference PNG: it draws Bottle `#614C2A`, Diaper `#1C5745`, Solids `#623639`, Growth `#3E3969` — around
today's 26% tints, so the tint stayed the anchor and the work was the gradient plus the artwork, not a
repaint.

### As built — 2026-11-02. Read this before the numbers.

**The tokens.** `cardFor(accent)` (the 26% tint) is unchanged and is now the gradient's TOP stop.
`cardStopsFor`/`cardGradientFor` add a bottom stop pushed `GRADIENT_STEP` **0.06** toward the same accent,
and `artFor(accent)` is the fill pushed `ART_STEP` **0.14** toward it — the same direction and the same 0.14
the tiled pattern used, so the old measurements carried over. Both numbers were chosen by measurement rather
than taste: at 0.04 the gradient is dE 2.1 on the worst card (invisible), at 0.06 it is dE 3.2-4.8 while the
artwork is still dE 4.2-6.2 from the lower stop, and by 0.14 the artwork's colour IS the lower stop's colour
and vanishes there. Text holds at any step up to 0.14, because contrast falls monotonically toward the accent
and the furthest point is already pinned at 4.5:1. `PatternPaletteTest` was REWRITTEN, never deleted, and is
now eight tests: artwork visibility against the fill (dE >= 7), against the gradient's lower stop (dE >= 4),
the gradient's own perceptibility (dE >= 3), the top stop pinned equal to `cardFor`, every text token at
4.5:1 on the artwork and on BOTH stops, and the unchanged chip-glyph 3:1 bar. Measured minima: 7.4, 4.2, 3.2
and 4.50:1 (the dark Bottle card's artwork, the same value that was already pinned).

**The nine drawables.** `ic_soft_bottle`, `ic_soft_bowl`, `ic_soft_diaper`, `ic_soft_giraffe`,
`ic_soft_thermometer`, `ic_soft_moon`, `ic_soft_heart`, `ic_soft_pacifier`, `ic_soft_baby_face` — filled
(`#FFFFFFFF` fills, tinted at runtime), the same construction the seven consistency silhouettes use and the
opposite of the stroke line-art the chips keep. Before any of them was wired in, all nine were rendered
through headless Chrome at 150/48/24/16dp and read back: the moon is a crescent (not a disc), the pacifier's
handle ring has its hole, the giraffe is recognisable, the baby face has a curl, and at 16dp the giraffe,
nappy, pacifier and head merge into featureless blobs while the heart, bowl, crescent and thermometer still
read. That check fixed three of them — a self-intersecting curl that rendered as a hole, a thermometer bulb
barely fatter than its stem, and a pacifier teat that was a 2px stub.

**The reach.** The gradient lands on all seven `cardFor` call sites. The artwork lands on the five card
surfaces that carry an activity and a value: the six dashboard cards, Summary's total cards, Trends' summary
card, Health's fever-days card and Growth's callout. The two ~56dp title bars (the logging sheet and the
entry-list overlay) take the gradient only, because a large illustration cannot fit a title strip.

**The chip glyphs are untouched.** `glyphRes()`/`glyphPainter()` and the six `ic_glyph_*` stroke drawables
remain for the controls — Summary's filter row, Trends' tabs, the sheet title bars, the entry rows — and
`Modifier.activityPattern`, its tile machinery (`buildTile`, the 64dp pitch, the 26dp glyph) and
`patternFor`/`PATTERN_ALPHA` are gone. `grep -rn "activityPattern\|patternFor\|PATTERN_ALPHA" app/src/main`
returns nothing, and `grep -rn "cardFor(" app/src/main` returns only `Tokens.kt`.

**Tests: 645 -> 658.** Ten `CardArtworkTest` cases (one illustration per activity and never shared, the card
artwork never being the chip glyph, a deterministic decoration, a sticker that is a whole object inside the card
and below the title row, a sticker that SHRINKS on a short card rather than being cropped, at least six scattered
shapes with one in each third of the card's height, at most two of them inside the sticker's footprint, no two
overlapping, a measured size floor per kind, and every shape staying on the card) and three more in the rewritten
`PatternPaletteTest` (5 -> 8).

### Two defects the phone found, and how they were found

**Half the decoration on the Bottle card was drawn and invisible, and no screenshot would have caught it.**
Measuring the phone's own pixels — connected components of the exact artwork colour in a `screencap` — showed
three shapes where the list declared five. The pacifier was drawn *inside* the large bottle's silhouette: two
shapes in the same tone are one shape. One heart was drawn *under* the opaque quick-log pill row. The same
method showed the Diaper card's baby face behind its Wet/Dirty pills. Both were placement bugs, and the first
build's answer was to keep every scattered shape above the band where a card fills with opaque controls —
`CARD_ART_CONTROL_BAND_DP`, 76dp, the tightest of the six cards (the Diaper value is followed by its Wet/Dirty
pills at ~80dp, while the Bottle pills do not begin until 137dp).

The fix also removed a silent-skip API: the painter lookup was a map with an `if (containsKey)` guard whose
`?:` turned a missing entry into an undrawn shape instead of a crash. It is now an index-parallel list with
nothing to skip.

### The owner's correction, 2026-11-02 — across the whole card, sticker in front

**The 76dp band was the wrong fix, and the owner said so on the phone: "the rendered shapes only apply to the
title area and not the entire activity card… the image should look more like [the reference] with a sticker
like render first and then random shapes at the back, gives the app more life."** Both halves of that were
acted on, and it changed the shape of the feature rather than a number in it:

- **The scatter now covers the whole card.** `CardScatter` positions each shape by `xFraction`/`yFraction` of
the card instead of a dp offset from the top. That is the only way one placement list can serve a ~110dp
Summary total and a ~400dp feed card: a dp offset that graced the tall card fell off the short one, and the
band rule had confined the decoration to the top quarter of every card. `CardArtworkTest` now asserts at
least six shapes with one in each third of the card's height, which is the property the owner was asking for
stated as a test.
- **The sticker is a whole object.** It is anchored to the card's right edge with a 10dp margin, so it stays
inside on every screen width — the first build anchored it as a fraction of the width and let it run off the
card, which reads as a cropped watermark. It starts at 46dp, below the title row, so its tone never sits
behind the activity label or the relative time. And `stickerSideDp` clamps it to the card's height: a ~110dp
Summary card carries a smaller WHOLE sticker (56dp) rather than the top half of a 124dp one.
- **The tone did not change, and the reason is measured.** The ceiling is the same for both layers, because
the scatter is now everywhere, so the big value and a FEVER number can sit on a scattered shape as easily as
on the sticker. The largest step that keeps every card token at 4.5:1 is **0.14 on both themes**, and the
binding pair is the Health card's fever number (4.59:1) — not the meta text, which would have allowed 0.28 if
`textOnCard` were lifted to pure white. Raising the tone for a punchier sticker would weaken the one cue that
marks a fever, so the hierarchy between the two layers is **size and order, never tone**: one sticker at
124dp in front, eight shapes at 22-30dp behind it.
- **What replaced the band rule.** A scattered shape may now be covered by the card's content — that is what
"at the back" means, and the set is spread so no single opaque control can hide the effect. The one rule kept
from the device pass is that **at most two shapes may fall inside the sticker's footprint**, because a shape
drawn under the opaque sticker is drawn and invisible.

The revision was measured the same way as the first build (component analysis of the phone's own pixels) and
was **device-verified on 2026-11-03**, in both themes: see the pass at the end of this section.

### The empty-card defect: a 48dp band was built, installed and rejected on sight, 2026-11-03

The owner reported from the phone that the decoration showed on Bottle feed and Diaper only — the other four
cards stayed bare until something was logged. **Cause, measured from the pass's own `uiautomator` dump and
its own pixels:** the sticker is anchored at `STICKER_TOP_DP` 46 and clamped by the card's HEIGHT, so nothing
in the placement knows where the card's first opaque control begins. On a card with nothing logged the only
content is the full-width log button at ~50-58dp, i.e. above the sticker's top edge, so the illustration was
drawn entirely underneath it: 12dp visible on Solids and Health, 4dp on Sleep and Growth, and 0dp of the
sticker's own body on Sleep once measured by colour rather than by eye. A Bottle card, whose pills start at
154dp, showed 108dp.

**A band was then built and it did what it said:** `CARD_ART_BAND_DP` 48dp reserved above the first control
(`cardArtBandDp`, `stickerClearDp`, one new `CardArtworkTest` case, 659 tests), installed on `<handset>`,
and measured there at 199.7dp/50.4dp visible on the empty Health card and 140.6dp/38.0dp on the empty Sleep
card, with artwork pixels present in both themes. **The owner saw it and preferred the previous, more compact
cards, and asked for the change to be reverted** — so it is gone from the working tree: the constant, the two
helpers, the spacer, the test case and the doc entries were removed and the rung is unchanged.

**The defect therefore stands, knowingly.** A card with nothing logged draws its illustration under the log
button. If it is ever revisited, the shape of the fix is that the sticker's TOP and CLAMP follow the card's
first control rather than the card's top edge and height — which needs no extra height, and is what the
rejected band bought with 48dp. Nothing was written toward it.

### Device pass, 2026-11-02 — `<handset>` (SM-S918U1), `1.4.1`, release-signed install in place

`adb install -r` over the delivered `1.4.0` (`versionCode=32 versionName=1.4.1` read back from
`dumpsys package`), with the foreground checked before every input and every tap coordinate taken from a
`uiautomator dump`. The pixel tools live in `logs/phase22-device/` (gitignored): `pixels.js` reports the
dominant colour per band and counts target colours, `components.js` finds connected components of one
colour so a shape's exact box can be compared with the placement maths. This table describes the **first
build** — the one-tone tile replacement with the scatter in the top band; the revised two-layer decoration
was installed and looked at afterwards, and is described in the note below.

| Check | What the phone showed |
|---|---|
| The gradient is a gradient, not a band | The Bottle card's bands step `#5b4f3d` → `#63553f` down the card (the two token stops), and the entry-list overlay's title bar steps the same way |
| Every declared shape is drawn where the maths says | Component boxes matched the computed placement boxes within anti-aliasing, on the dashboard cards for Bottle/Diaper/Solids/Growth, on Summary's Bottle and Diaper totals, on Trends' summary card, on the Health fever-days card and on the Growth callout (1 large + 4 scattered on each) |
| The artwork is visible in BOTH themes | Light theme: the Bottle card's artwork (`#DACAA3` on `#E5D9BD`) is present with the same 1 large + 4 scattered components: the theme the measurement said was most at risk |
| Text stays legible | Read off the screenshots: every label, number and button legible on all six tinted cards in both themes, with the gold button and the raw-accent pills unchanged |
| The log is untouched | The visible entry times are byte-identical to the dump taken before the first install (`5:39 AM` x2) — the phase's own claim is "no stored-data change", and this is what checking it looks like |
| `logcat -b crash -d` | **0** `FATAL EXCEPTION` across two installs, ~10 screens, both themes, scrolling and a tab switch |
| The owner's settings were left as found | The theme was switched to Light for the pass and returned to `System`, and `Settings -> About` reads `1.4.1` |

**Left open:** whether the scatter is now *enough* texture at the size the phone renders it, and whether the
light theme carries it — genuinely the owner's eye, and now a question about coverage rather than about tone,
which the measurement above closes.

**Was left open, now run.** The revision's own phone pass began on 2026-11-02 and the device then dropped off
USB mid-pass — first no serial (`(no serial number) device`), then `offline`, which is a device-side state
(re-plug, or accept the USB debugging prompt) that no host command fixes. It was completed on 2026-11-03 on the
final build; nothing in the revised layout now rests on a browser preview.

### Device pass, 2026-11-03 — the revised decoration, both themes

Same device, same method, on the final build. `adb devices` reported `unauthorized` until the prompt on the
handset was accepted, which is what had blocked this pass. Geometry comes from a `uiautomator dump` and the
artwork from counting its exact colour in a `screencap`, with that colour derived from `artFor(accent)` in
`Tokens.kt` rather than sampled by eye. The captures and dumps are in `logs/phase22-band/` (gitignored).

| Check | What the phone showed |
|---|---|
| The install and the app | `adb install -r` → `Success`; `dumpsys package` reads `versionCode=32 versionName=1.4.1`; the foreground was `app.babylog/.MainActivity` before every input, and every coordinate came from a dump |
| The decoration is there on a card that has a log | The Growth card, 183.4dp tall, carries **9897** artwork-coloured pixels above its first control in dark and **9910** in light |
| A card with nothing logged | Health 154.7dp and Sleep 95.6dp, with **1973/2031** and **1127/1109** artwork pixels above the button — the thin scatter strip only. The sticker itself is drawn underneath: this is the knowingly-open defect below, measured rather than assumed |
| The band, before it was reverted | The same two cards at 199.7dp and 140.6dp, with **8982** and **11040** artwork pixels above the button, so the revert gives back about 45dp and takes 78% of Health's and 90% of Sleep's visible artwork |
| Both themes | Every measurement repeated with the system night mode toggled through `cmd uimode night` and then restored to dark. The layout did not move a single dp between themes, as expected: nothing in the placement reads the theme |
| `logcat -b crash -d` | **0** `FATAL EXCEPTION` across both installs and every screen they were driven through |
| What was NOT re-measured | Tone, contrast and the gradient's steps: no token changed on 2026-11-03, and `PatternPaletteTest` pins all of them |

**Knowingly open when this pass ended, and closed by decision on 2026-11-03 rather than by a fix:**

- **A card with nothing logged draws its illustration underneath its first control** (the band that fixed it is
  above). The shape of a fix costing no height is stated there.
- **On a short card the clamped sticker is inset from the right edge further than its 10dp margin.** The draw
  offset is computed from the PREFERRED `sticker.sizeDp` while the drawn side is the clamped
  `stickerSideDp` value, so a card that clamps to 86.6dp measured a right margin of 47.4dp instead of 10 — the
  object is not drifting off the card, it is adrift from its own documented anchor. Found while measuring this
  pass, and **accepted as a limitation on 2026-11-03**: nothing is cropped and nothing leaves the card, so a few
  dp of inset on the one card that clamps is not worth touching the cards again.

## Closed by decision, 2026-11-03 — two open items retired, four limitations accepted

The open-item list was reviewed at the owner's request and six entries left it. Nothing in `app/src` changed, so
this is a record rather than a phase and it takes no version rung.

| Item | Was recorded as | Closed as | Why this is the right close |
|---|---|---|---|
| The second phone had never been attached | open in every pass since the 2026-10-28 import | **verified by the owner, by hand** | the gap was a missing device, not a missing change; see the note in the import section for what the evidence is and is not |
| `todo.txt`'s Settings redo ("all settings in submenus") | a note predating Phase 19 | **already delivered** | `SettingsSection.kt` defines all eleven groups and `SettingsNav.back()` returns up one level; the note is deleted rather than restated |
| A reminder for a never-logged activity re-arms a minute after every app start | known, unfixed since 2026-10-29 | **accepted limitation** | the rule has no log to anchor on, so any fix invents one; the failure is a repeated reminder while the parent is in the app, which is where reminders are not needed |
| Notification clearing compares wall-clock values | known, unfixed since Phase 20 | **accepted limitation** | a correct fix is a monotonic write sequence, i.e. a storage change, and the failure is a stale notification the parent swipes — never a wrong clear |
| An empty card draws its illustration under its log button | knowingly open since 2026-11-03 | **accepted limitation** | the 48dp band was rejected on sight for height, and the owner prefers the compact cards; the no-height route changes card geometry for decoration alone |
| A clamped sticker's right margin measures 47.4dp, not its 10dp | knowingly open since 2026-11-03 | **accepted limitation** | nothing is cropped or off the card |

**What "accepted" means:** the defect stays where it is, its cause and the shape of a fix stay written down
beside it, and it is not to be scheduled without being asked. The purpose of recording the fix shapes is that a
later decision is a decision rather than a rediscovery.

## Phase 23 — Ship
Publish the Phase 18 Firestore rules and catalogue from the Firebase console, deliver the `1.4.1` release
APK to both phones through App Distribution, verify the shared catalogue and pending-food flow on both, then
use the delivered build for **a week before writing another line of code**. Both phones exist and are known
good as of 2026-11-03, so this is about `1.4.1` arriving on the second one, not about first contact.

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
   Add it at Phase 16, not Phase 1.

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

## Phase 14 device verification — 2026-10-26, `<handset>`

**Status: PASSED**, on the S23 Ultra (`<handset>`, Android 16) with the `0.16.0` APK installed over the
`0.13.0` one — same signing key, so the log and its ids survived the install. The app was foreground
(`topResumedActivity=app.babylog/.MainActivity`) before any input was sent, and `logcat -b crash -d` held
**0** `FATAL EXCEPTION` across the whole pass.

| Check | What was seen | Result |
|---|---|---|
| `Sync now` with no network | Airplane mode on, one diaper logged: the card set **`Waiting for a network connection — 1 change queued.`** by itself, beside `1 change waiting to sync.`, and the count did not move across two taps 13 s apart | **pass** |
| The drain lock is released | Network restored, `Sync now` → **`Up to date.`** and the pending line gone within seconds. If the timed-out drains had held `drainLock`, this could never have reported | **pass** |
| The seed drains itself | Sign-in drained the queue with no tap: the card went from the sign-in form to `Signed in as …` with no `N changes waiting` line at all | **pass** |
| `Both` at 130 % | `font_scale 1.3`: `First Twin`/`Second Twin` on one line, `Both` wrapped to a second line **as a whole pill** — no `Bot` / `h` | **pass** |
| Consistency silhouettes | All seven read as forms; `Mucousy` is two slanted streaks (no `T`), `Runny` is a mound with a wide pool and no longer a copy of `Mushy` | **pass** |
| Sheet title glyph | `✕ · ⟨diaper⟩ Diaper · Save`, and the title stayed centred — title centre 525 px, available-space centre 525 px | **pass** |
| Solids name | Logged `Banana` through the sheet: card detail line `Banana`, entry row `⟨glyph⟩ Banana · 9:58 AM`. Deleted afterwards (`Solids deleted` + Undo); the card went back to empty | **pass** |
| Trends chip gap | ~8 dp between the pinned activity chips and the range chips below (text bounds 171 → 250 px), where there was none | **pass** |
| Range labels | Summary `D · 1W · 2W`, Trends `1W · 2W · 1M` — and Trends' `2W` matched its own `14 days` caption | **pass** |
| Growth metric marks | Three chips carrying a scale, an up arrow and a head — not the growth glyph | **pass** |
| 14.10, amount-less bottle | A feed logged with a blank amount read `Bottle feed` over `Bottle` on this phone; after the fix it reads **`No amount recorded`** (re-installed and re-checked) | **found + fixed in this pass** |
| No crash | 0 `FATAL EXCEPTION`; font scale and airplane mode restored afterwards (1.0, off) | **pass** |

**Data written during the pass, and removed:** one solids meal and one Wet diaper, both logged on purpose
and both deleted through the app. They remain as soft-deleted tombstones by design, and both drained
(`Up to date.` with an empty queue at the end). The second phone was not checked during this pass, so
cross-device arrival of these two tombstones is not claimed. The owner's 25 Oct 6:55 PM bottle with no amount was left alone, on instruction, and is what
prompted 14.10.

**Found on the phone, not by any test:** a bottle card whose record has no amount used to repeat its own
activity name instead of naming the gap — see 14.10 above. The unit tests pinned the old word, which is
why it survived: `a bottle row without an amount falls back rather than showing nothing` asserted
`Bottle`, and that case now asserts `No amount`.

**Also seen, out of scope and not fixed:** on Trends, a bottle series with no volume in the window draws
its y-axis as `0.1 / 0 / 0` — three tiers of which two read `0`. Nothing in Phase 14 touches the axis
maths Phase 12 signed off, so this is recorded rather than changed.
