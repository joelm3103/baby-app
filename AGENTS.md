# AGENTS.md — BabyLog

Project instructions for any AI coding agent working in this repo (Claude Code, Codex, OpenCode,
Hermes). Read this before changing anything.

## What this is

A private, offline-first Android baby tracker for **one family**: two parents and their twins.
Two users, sideloaded release APK, no Play Store, no monetisation, no analytics.
It is deliberately small: it copies the core of an existing tracker and nothing else, and the whole point
is that it does *less* than the apps it replaces.

Data is the user's children's health log. Treat it as sensitive.

## Stack

Kotlin (AGP 9 **built-in** Kotlin — do NOT apply an `org.jetbrains.kotlin.android` plugin) ·
Jetpack Compose + Material 3 · Room (source of truth) · DataStore (preferences) · Firebase
Auth/Firestore (sync, later phases). `compileSdk 37` + `compileSdkMinor 2`, `targetSdk 36`,
`minSdk 26`, JDK = Android Studio's bundled JBR.

Three AGP 9 facts that have already cost build cycles: the `kotlin.android` plugin must **not** be
applied (AGP 9 carries Kotlin itself); `compileSdkMinor` is required to express `android-37.2`; and
`jvmToolchain(17)` fails on a JBR-only machine, so the toolchain is the bundled JDK 25.

Read `app/src/main/java/app/babylog/` in this order: `domain/` → `data/local/` →
`data/repo/` → `ui/`.

## Commands

```bash
export JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'   # or Studio's JBR path
./gradlew :app:testDebugUnitTest :app:assembleDebug                   # ~1-4 min
```

**Never report a phase done on a green build.** A green build proves the code type-checks and nothing
about runtime or layout. See "Verify on the device" below — that is not optional.

### Reading a Gradle result

The log line is the truth, not the process exit code:

```bash
./gradlew :app:testDebugUnitTest :app:assembleDebug > logs/test.log 2>&1
RES=$?; echo "GRADLE_EXIT=$RES" >> logs/test.log; exit $RES
grep -a "GRADLE_EXIT" logs/test.log            # -a: Gradle logs look binary to grep
grep -a -oE 'tests="[0-9]+" skipped="[0-9]+" failures="[0-9]+" errors="[0-9]+"' \
    app/build/test-results/testDebugUnitTest/*.xml    # authoritative counts
```

Do not append `echo` after the build when running it in the background — the process status then
reflects the echo and a FAILED build reports exit 0.

## Git workflow

**One branch per phase or task, cut from `main`, and `main` is never edited directly unless I say so for
that task.** Creating the branch needs no approval — it publishes nothing — but commits, merges and
pushes do.

- **Name it `<type>/<slug>`**, with the type prefix from the commit convention this repo already uses:
  `feat/phase12-trends`, `fix/fever-pill-legibility`, `docs/git-workflow-rule`, `chore/release-signing`,
  `refactor/…`, `test/…`. Use the phase number when the work is a phase, a short slug otherwise.
- **Land it with a fast-forward merge, only after I approve:** `git switch main && git merge --ff-only
  <branch>`, then `git push origin main`. No merge commits — `main` stays linear, which is what makes
  `git log --oneline` still readable as a history of phases.
- **Delete the branch AFTER the push lands, never before** — `git branch -d <branch>`. Deleting first
  drops the only reference to work that is not yet on the remote.
- A phase's `CHANGELOG.md` entry and its `PLAN.md` update ride the same branch, in the same pass as the
  phase commit (see the table under *Where things are*).
- **`main` is the only branch on `origin`.** A collaborator's branch, `Timothys-Take`, used to exist;
  it was removed when the repo was sanitised — the history was rewritten and the fork deleted. Its
  absence is that removal, not a deletion by an agent, and it is not to be recreated from an old clone.

## Verify on the device

**The app id is `app.babylog`.** A build from this history is a *different app* to one installed from
before that id, so it installs beside the old one and the existing log moves across with
Settings → Data → Export, then Import in the new build. Never run both side by side against the same
log — they keep separate databases, and only one of them is the one being logged into.

The emulator was deliberately skipped. A real phone is the feedback loop (~2 min per cycle):

```bash
ADB="C:/Users/<you>/AppData/Local/Android/Sdk/platform-tools/adb.exe"   # NATIVE path
"$ADB" install -r app/build/outputs/apk/release/app-release.apk   # release-signed: a debug APK will NOT install over 1.0.0
"$ADB" shell am start -n app.babylog/.MainActivity
"$ADB" logcat -d | grep -a -A30 "FATAL EXCEPTION"
"$ADB" exec-out screencap -p > shot.png
```

Rules learned the hard way:

- **Check the foreground first, and refuse to send input if it is not the app.** `dumpsys activity
  activities | grep topResumedActivity`. A scripted `input swipe` once landed on an active phone call.
- **Tap real coordinates from `uiautomator dump`, never guessed pixels.** Icon buttons appear as
  `content-desc`, not `text` — matching on text alone silently misses every icon.
- **`adb` is a native binary: `/c/Users/...` fails with `failed to stat`.** Pass `C:/Users/...`.
- `logcat -b crash -d` still holds a crash trace **after the app is uninstalled**.
- **Pull the database with its `-wal` and `-shm`, or `am force-stop` first.** A bare `babylog.db` reports
  the state before the last transaction: one pull read `outbox 30` while the truth was 0, a later one `74`
  while the truth was 15 — twice enough to conclude something false. A force-stop checkpoints the WAL into
  the main file, which is the cheap unambiguous read.
- **A release build cannot be read that way at all.** `run-as app.babylog` answers
  `package not debuggable`, so from `1.0.0` onward a pass that needs counts asks the app for its own
  **Settings → Data → Export** JSON and counts there — save it under a new name, because the file picker
  offers today's date and will happily overwrite the backup a previous pass made.
- **`adb shell` mangles `/sdcard/...` into a Windows path under Git Bash.** Prefix the command with
  `MSYS_NO_PATHCONV=1`, or the dump lands in `C:/Program Files/Git/sdcard/` and reads back 68 bytes.
- **Screenshots are captured by `scripts/capture-timeline.ps1`** into a folder beside the repo, not committed.
- **A pass now installs a RELEASE-signed build.** `1.0.0` was the first release-signed APK, so `app-debug.apk`
  has a different certificate and `adb install -r` answers `INSTALL_FAILED_UPDATE_INCOMPATIBLE`. Build
  `:app:assembleRelease` (its `versionCode` is above what is installed) and `adb install -r` that; never
  uninstall to make a debug build fit, because that discards the log and the outbox.

## Release signing and delivery (Phase 16)

- **The release certificate IS the app's identity.** Android installs a build as an update only when the
  signing certificate matches the one on the device, so the key — not the package name or the version — is
  what a phone recognises. Sign with a different key and the install fails
  `INSTALL_FAILED_UPDATE_INCOMPATIBLE`, which means uninstall, which means the local database goes.
- **The keystore is never in the repo, is never regenerated, and lives outside the Syncthing folder:**
  `C:\Users\<you>\keystores\babylog-release.jks` (alias `babylog`, `CN=BabyLog`, RSA 4096, valid to
  2054) with `keystore.properties` beside it and the password in the owner's password manager. `*.jks` and
  `keystore.properties` are both gitignored. `docs/release-signing.md` is the runbook and carries the
  certificate's SHA-1 and SHA-256.
- **`scripts/create-release-keystore.ps1` refuses to overwrite an existing key**, because a second key is a
  second app identity and would orphan both phones. The owner types the password at its prompt; the script
  passes it to `keytool` through `-storepass:env`, so it never enters argv or a log.
- **A clone without the key must still build.** `app/build.gradle.kts` creates the `release` signing config
  only when `keystore.properties` exists (repo root, then `%USERPROFILE%\keystores`). Without it,
  `:app:testDebugUnitTest` and `:app:assembleDebug` are unaffected and `:app:assembleRelease` emits an
  **unsigned** APK that fails `apksigner verify` — a loud outcome, never a silent wrong signature.
- **The certificate's SHA-1 must be registered on the Firebase Android app entry**, or Google Sign-In
  refuses every release build. Email/password is unaffected, which is why it stays the primary route. The
  debug SHA-1 stays registered too.
- **Delivery is a GitHub Release, and this reverses the console upload** (ADR-0001's 2026-11-04 amendment).
  A tag with the signed APK attached *is* the delivery — no Gradle plugin, no `firebase-tools`, no console
  trip. App Distribution is no longer the channel, so "a build that was not uploaded through the console has
  not been delivered" is retired with it: a release page with the artifact and its digest is the delivery.
- **The APK on the page must be the built artifact, not a rebuild.** Same key, and the recorded digest is what
  a phone's copy is compared against; a second build signed by the same certificate is still a different file.
- **`versionCode` is still a contract**: it only rises, and a landed rung is never renumbered. Nothing
  automates the comparison any more, which is exactly why the number must not be handled loosely.
- **The app asks GitHub for its newest release when it opens, once a day, and it is the only request that is
  not Firebase or a food catalogue read** (ADR-0003). One unauthenticated `GET`, `User-Agent: BabyLog`, no
  account, no id, nothing about the babies — which is what keeps the About screen's "no analytics, no
  trackers, no ads" true, and the About text now states this one request instead of implying the phone talks
  only to Firebase.
  - **Off means no request at all**, decided in `UpdateChecker` and not in the row; **a day is the interval**,
    so a parent who reopens the app twelve times before lunch is not twelve requests at GitHub.
  - **Nothing is downloaded or installed.** The check can name a newer version and open the release page; the
    install stays a tap on the system installer, because sideloading cannot do better.
  - **A failed check is never alarming**: unreachable, no release published and "not checked yet" are three
    sentences, and none of them claims the log is at risk. An unreadable tag counts as up to date.
  - **A Robolectric class in this repo must pin `@Config(sdk = [34])`.** Without it the sandbox dies in
    `ApplicationSharedMemory` with "Failed to interact with raw FileDescriptor internals" before a test body
    runs, on this machine's JDK.
- **A tag is cut only on a delivered build**, after a phone reports the version, never when a phase ends.

## Design contract — measured, not taste

`app/src/main/java/app/babylog/ui/theme/Tokens.kt` is the source of truth, and the measured derivations
are recorded in the comments there and in `PLAN.md`. Key decisions:

- **One hue per activity type, never reused.** Dark accents: Bottle `#FFC857`, Solids `#FF7A5C`,
  Diaper `#5AD98A`, Growth `#B49CFF`, Health `#FF7BC0`, Sleep `#63C8FF`.
- **Cards ARE tinted** (accent at 26% over `surface`), because full-section tint is what identifies a
  section at a glance. 26% is measured: 0.16 is only dE 7.7 apart (barely separable), 0.26 is dE 12.5,
  and above 0.30 the accent-filled button starts washing out.
- **The raw accent is NOT legible as a label on its own tint** (3.4-4.3:1). Labels use
  `BabyLogColors.labelFor(accent)` (accent lifted toward white on dark, deepened on light) — 6.4:1.
- **Meta text on a card uses `textOnCard`, never `textSecondary`** (which measures 3.06:1 on the
  stronger tints) and never a `MaterialTheme.colorScheme.*` default.
- Action buttons are filled with the **raw accent** and labelled in `onAccent` (5.9-11.2:1).
- Verify any colour change numerically before shipping it. Do not eyeball.
- **A temperature band is marked by a SHAPE, not a hue.** `domain/Temperature.kt` classifies a reading
  into LOW (`< 36.5`), NORMAL (`36.5–37.5`), MILD_FEVER (`37.6–37.9`) and FEVER (`>= 38.0`, i.e.
  `FEVER_C`), always from the value **as displayed** — rounded to one decimal first — so a pill can never
  disagree with the number beside it (`37.99` prints `38.0` and is therefore a fever). `TemperatureBadge`
  gives the three non-normal bands ONE geometry so no row shifts: FEVER is filled `feverOnCard` with the
  word in `onAccent` ("Fever"), MILD_FEVER and LOW are outlined ~1.5dp in `feverOnCard` with the word in
  `feverOnCard` ("Mild", "Low"). Only FEVER moves the number off `textPrimary` — one strong cue per row.
  `danger` is NOT usable on a tinted card: it measures 3.76:1 on the 26% tint, below AA — so
  `feverOnCard` exists for tinted surfaces (fill and outline both 5.71:1 on the Health card, 4.80:1 worst
  of the six; 10.39:1 for the word on the fill). A tinted number was measured legible at 5.20:1 and was
  still invisible on the phone, which is why the marker has a shape and the word inside it.
- **A card is FILLED SOFT: a vertical gradient fill, the activity's illustration as a sticker, and
  scattered shapes behind it across the whole card (Phase 22, 2026-11-02).** Nine filled drawables
  (`res/drawable/ic_soft_*`) go through `EventType.artRes()` — bottle, bowl and spoon, diaper, giraffe,
  thermometer, moon, plus the shared motifs heart, pacifier and baby face — and `Modifier.cardArtwork(type,
  accent)` draws two layers: the **sticker** in front of the decoration, the **scatter** behind it, both
  behind the card's content.
  - The fill is `cardGradientFor(accent)`: the top stop is the 26% tint `cardFor(accent)` unchanged, and
    the bottom stop is that fill pushed **`GRADIENT_STEP` 0.06** further toward the same accent (dE 3.2-4.8,
    so it reads as a fall-off rather than a band). Text is safe at any step up to `ART_STEP`, because text
    contrast falls monotonically as the fill moves toward its accent and the furthest point is already
    pinned at 4.5:1.
  - Both layers are drawn OPAQUE in the ONE tone `artFor(accent)` — `ART_STEP` **0.14** toward the accent,
    the measured direction the tiled pattern used (dE 7.4-10.9 dark, 7.5-10.4 light) — NEVER white: white
    over a 26% tint measures dE 3 and 4.2:1 at alpha 0.04 and is invisible in the light theme.
  - **The one tone is not laziness, and the reason is measured.** The ceiling is the same for both layers,
    because the scatter covers the whole card, so the big value and a FEVER number can sit on a scattered
    shape exactly as they can on the sticker. The largest step keeping every card token at 4.5:1 is 0.14 on
    BOTH themes, and the binding pair is the Health card's fever number (4.59:1), not the meta text — so a
    stronger "sticker" would weaken the one cue that marks a fever. **The hierarchy between the two layers
    is therefore SIZE AND ORDER, never tone.**
  - **The sticker is a whole object, not a cropped watermark.** It is anchored to the card's RIGHT edge with
    a margin (`CardSticker.rightMarginDp`) so it stays inside on every screen width, starts below the title
    row (`topDp` 46) so it never puts its tone behind the activity label or the relative time, and is clamped
    to the card's height by `stickerSideDp` — a ~110dp Summary total card carries a smaller WHOLE sticker
    rather than the top half of a big one. The first build anchored it as a fraction of the width and let it
    run off the card edge; the owner rejected that as lifeless on 2026-11-02.
  - **A card with nothing logged draws that illustration UNDERNEATH its first control, knowingly.** On an
    empty card the only content is the full-width log button, which starts at ~50-58dp — above the sticker's
    46dp anchor — so the sticker is drawn and invisible (measured 2026-11-03: 12dp of it visible on Solids and
    Health, 4dp on Sleep and Growth). A 48dp band reserved above the first control fixed it, was installed and
    measured on the phone, and the owner **rejected it on sight in favour of the previous, more compact cards**,
    so it must not be re-added. The fix that costs no height — the sticker's `topDp` and its clamp following the
    card's FIRST CONTROL instead of the card's top edge and height — was written down in `PLAN.md` and **accepted
    as a limitation on 2026-11-03** rather than built: the owner prefers the compact cards, and a decoration that
    only shows until an activity's first log is not worth changing a card's geometry for.
  - **The scatter covers the whole card.** Each shape's `xFraction`/`yFraction` are fractions of the card,
    not dp offsets, because one list serves a ~110dp callout and a ~400dp feed card: dp offsets that graced
    the tall card fell off the short one, and the first build's 76dp "safe band" left the decoration in the
    title area only — the defect the owner found on 2026-11-02. `CardArtworkTest` now asserts at least six
    shapes with one in each third of the card's height.
  - **A scattered shape may be covered by the card's content, and that is what "at the back" means.** The
    set is spread rather than clustered, so no single opaque control can hide the effect; the one rule kept
    from the first build's device pass is that at most two shapes may fall inside the sticker's footprint,
    because a shape drawn under the opaque sticker is drawn and invisible.
  - Because the artwork sits behind every word, `textOnCard` (`#DCDEE7` / `#404E69`) stays where it was
    lifted to; `PatternPaletteTest` asserts the artwork's visibility, the gradient's perceptibility, and
    every text pairing for six accents × both themes. Raising `ART_STEP`, retuning an accent, widening the
    tint or moving a placement goes through those tests.
- **Three kinds of chip carry three different pictures (Phase 14, 2026-10-25).** *Activity* chips carry the
  activity's glyph. *Growth metric* chips carry their OWN mark — `GrowthMetric.iconRes()`: a scale for
  Weight, an up arrow off a floor for Length, a head with a curl for Head — because the activity glyph would
  be identical on all three and a chip that says nothing about which one is selected is decoration.
  *Range* chips carry **no glyph at all**: `1d · 7d · 14d` became `D · 1W · 2W` on Summary and `1W · 2W · 1M`
  on Trends, because a range is a span and not an activity — the `navActive` fill they use has been the
  evidence of that all along. Each short label carries a `spoken` form as its `contentDescription`; `2W` read
  aloud is not an instruction. The owner asked for the full glyph reach on 2026-10-25 (sheet title bars,
  entry rows, Both-mode column heads), so do not "restore" the old rule that the glyph belongs on cards and
  activity chips only. The stroke glyphs are the CONTROL family and never the card's decoration: since
  Phase 22 a card is decorated by the filled illustrations and the gradient above, and
  `ActivityGlyphTest`/`CardArtworkTest` pin that a chip's glyph and a card's artwork can never be the same
  asset. Raising an alpha, retuning an accent or lowering the tint goes through `PatternPaletteTest`.
- **A nappy's colour is a LITERAL colour, not an activity accent.** Yellow, Brown, Black, Green, Red and
  White are what the parent SAW (`DiaperSwatch` in `Tokens.kt`), so the one-hue-per-activity rule does not
  reach them — a brown stool is not "BabyLog's anything" — and `danger` (a destructive action) and
  `background` (the app's own surface) are never reused as "red" and "black". The palette test asserts the
  last part explicitly.
- **Every colour swatch carries a permanent 1dp hairline ring.** Black against the dark surface is 1.10:1
  and White against the light one 1.13:1: unringed, one of the two is a blank space in one of the two
  themes. The ring is a dedicated per-theme colour (`#D5D8E2` dark, `#3C4350` light) and NOT
  `textSecondary`, because no single grey clears 3:1 against a fill set that runs black to white (the
  mid-greys measure 1.0–2.9:1 against the ends). `DiaperPaletteTest` enforces the rule that matters at each
  edge — the fill clears 3:1 against the sheet OR the ring clears 3:1 against the fill — for six fills ×
  both themes. **Selection adds a ring; it never changes a swatch's size**, so a row cannot shift as boxes
  are ticked.
- **A record with a missing number names the gap, never its own activity.** A bottle can be logged with a
  blank amount, and the app must not invent a volume — so the card says `No amount recorded` (row:
  `No amount`) rather than falling back to the word `Bottle`, which repeated the card's title and showed no
  number. Found on the phone in Phase 14.10. The same rule applies to anything else that can be recorded
  without its value.
- **Consistency is identified by SHAPE + word, never by colour** — the same rule the fever band follows, so
  the field survives a colour-blind parent. Its seven silhouettes (`res/drawable/ic_stool_*`, mapped by
  `DiaperConsistency.stoolRes()`) are FILLED, unlike the stroke line-art of the activity glyphs: at 16dp
  inside a chip a 1.7dp outline muddies, and what these have to show is a form (pebbles are pebbles).
- **One time dialog, three inputs, one switch** (Phase 21). `TimeOfDayDialog` in `ui/home/SheetControls.kt` is the
  only place a time is edited — every logging sheet arrives through `TimeRow` and the reminder sheet arrives for a
  fixed-time rule — so a change there moves the whole app. The dial is Material3's `TimePicker`; the wheels are
  ours (`WheelTimePicker`), because Material3 ships no wheel and hosting the platform `android.widget.TimePicker`
  would have put the OS's chrome inside a BabyLog sheet; the two-field keyboard stays as the third, since it is the
  only input that can type an exact minute without scrolling. The switch is IN THE DIALOG rather than a Settings
  row (the owner's choice) and nothing is persisted, so it opens on the dial every time. Its button names where the
  tap LEADS (`Switch to wheels`), never where it is. **One pair of numbers, two objects**: the dialog's
  `hour`/`minute` are the wheel's value and the hand-off; `TimePickerState` is the Material modes' value, and the
  pair keys it — because `TimePickerState.hour` has no public setter and re-creating the state is the supported way
  to move it. A wheel column reports only a move it was NOT built with: its first emission is the position it was
  created at, and that can arrive before the list has measured. The AM/PM column reports the HALF OF THE DAY, not
  an hour.
- **Bottle presets are one-tap log pills on the card, and the full-width button stays** (Phase 20). The tap IS
  the record — `HomeViewModel.logBottle` borrows the Diaper path exactly, so Both mode writes one row per child
  with an identical timestamp — and the button underneath remains the route to a custom amount, a brand, a note
  or a feed that happened earlier. The label is composed in the display unit from a value stored in ml, so the
  same preset reads `4 oz` or `120 ml` without moving. An empty list draws no pills at all rather than an empty
  row, the row is a `FlowRow` because Settings lets a parent add as many sizes as they like, and
  `SettingsRepository.MAX_BOTTLE_PRESET_ML` is the one cap both the screen that stores a preset and the card
  that logs one read — two gates that must not drift about what a bottle is.
- **A Both-mode card names the child; a badge stays where a name does not fit** (Phase 20). The name replaces
  the initial on the headline that owns the big number and on every entry row, and also in the Growth and Health
  columns, which were already headed by the name — the same fact twice at the cost of the width. The switcher,
  the reminder chips, the Growth chart and the fever table keep `ChildBadge`, so `ChildBadge` itself is not
  dead code and must not be deleted.
- **A silhouette must be checked at 16dp, and two of them were wrong.** `Mucousy` shipped as a wide bar over
  a stem with a dot under it, which is a letter `T` at chip size; `Runny` was three heavy circles on an
  ellipse that merged into `Mushy`. Both were redrawn in Phase 14 and rendered at 16/24/48dp before and after
  — a path that is right at 48dp says nothing about 16dp. `StoolAndMetricGlyphTest` pins all seven as distinct
  drawables, because a mapping with no test is how a `T` shipped.
- **`DiaperKind.MIXED` is how "both" is spelled**, and `DiaperKind.of(wet, dirty)` / `toggle(...)` are the
  only places that decide it. The column has carried WET/DIRTY/MIXED since schema v1, so a two-part answer
  costs no migration and no existing row, sync document or export changes meaning. A quick pill on the card
  must never write `MIXED`: one tap cannot answer two questions (`DIAPER_QUICK_KINDS` is asserted for that).
- **Colour and consistency are null unless the nappy was dirty**, like `rash` — "not recorded" is the truth
  and the line says nothing rather than "unknown". The sheet clears both when Dirty is off, and
  `diaperDetail` ignores them for a wet-only row so a synced row cannot disagree with its own card.
- **Settings is an index of eleven groups, not a scroll of eleven sections** (Phase 19, `SettingsSection.kt`).
  Each menu row carries a one-line summary of its group's current value — the answer to "which units am I on"
  should not need a tap — and each name opens that group alone. A group's name exists once, as `title`, and is
  the menu row, the submenu heading and what the back arrow returns to; a second label beside it is how the two
  come to disagree. **Back means "up one level" before it means "leave Settings"**, and that rule is
  `SettingsNav.back()` — a tested value, not an `if` in the handler, because a menu that swallowed back traps
  the parent and a submenu that passes it through dumps them on Activity. Two defects came off the phone: an
  appended `s` printed `2 childs`, so `counted()` takes its plural explicitly, and a summary long enough to wrap
  made its row taller than the rest, so a value that cannot fit one line belongs in the submenu instead.

## Domain rules that already cost us

- **`endedAt == null` does NOT mean "running".** Only duration-capable types can run:
  `isRunning = type.hasDuration && endedAt == null`. Treating a null end as "in progress" put a live
  timer on every card, because a point event has no end either.
- **Never store a nested `object` in a companion `val`.** `val default: ChildSelection = Both` read
  **null** on Android: ART initialises the enclosing interface (and its companion) *while* `Both` is
  still being constructed, so it captured the not-yet-assigned INSTANCE. HotSpot does not do this, so
  **no JVM or Robolectric test can catch it** — it crashed on launch with 97 tests green. Use
  `val default: ChildSelection get() = Both`.
- **`x ?: fallback` is compiled away when `x`'s declared type is non-null.** Widen to a nullable local
  first or the guard does nothing.
- **Totals are NOT computed in SQL.** Day boundaries and the day/night diaper split depend on the
  local timezone, which SQLite cannot resolve; aggregating in SQL would silently file 8pm diapers under
  the wrong day. Totals belong in pure Kotlin.
- **A day with no records is a zero, and the screen says so.** Trends draws a missing day as 0 and
  captions it `Days with no records count as zero.` The app cannot tell "no nappies" from "nobody logged
  one", so it must not draw a gap and imply it knows. The same rule forbids guessing a running sleep's
  duration or an unrecorded bottle's volume: an average that is real but rounds away reads `< 0.1`, never
  `0`.
- **Two children on one chart are told apart by shape as well as shade** — the first line solid, the
  second dashed. Two shades of one accent were not enough to tell the twins apart on a phone, and a
  second hue is reserved for a second activity.
- **Adjusted age is a named function on purpose** (`ageDaysForCharts()`). The twins were born
  prematurely; using chronological age for a percentile chart yields a confident wrong
  answer with no error.
- **A growth dataset can be withdrawn, and only on a check.** An optional `withdrawn: true` on
  `reference/growth_manifest` makes the phone delete its cached copy, clear `growth.dataset.version` and
  `growth.dataset.sha256`, reset the in-memory `cachedReference` (no force-stop needed) and report
  `Dataset withdrawn — back to the bundled reference`. It names exactly ONE version: a retraction of a dataset
  this phone never accepted is a no-op, never an instruction to revert something newer. Nothing can be pushed
  to a phone that never checks, so the flag lands on the next silent or manual check.
- **Screen off if intended: outbox stores intent, not payload** — a stored JSON copy drifts as soon as
  the row is edited again; the drain re-reads by id.
- Soft-delete only (`deleted` tombstone); sync must propagate deletions.
- **A dose is two columns that must move together.** `doseAmount` and `doseUnit` are both set or both
  null, added as nullable columns in the v1 → v2 migration — the app's first schema change. Half a dose
  is not a dose, and a restore that lost the unit would restore "2.5" of nothing.
- **The bundled medicine list is NAMES ONLY.** `MedicationCatalogueTest` fails the build if any entry
  contains a digit, and the health sheet itself says `Names only — this app does not suggest a dose`. An
  infant dose depends on weight and age, so the app must never propose one.

## Reminder rules (Phase 13.4) that will cost you if you forget them

- **A reminder fires ONCE per anchor.** `AFTER_LAST` arms nothing after it fires: the anchor has not moved, so
  re-arming would nag a parent who simply has not fed yet. The next log moves the anchor. `FIXED_TIMES` arms the
  following time of day, because that rule is about the clock rather than about the last log.
- **An overdue reminder is not scheduled.** A phone that was off overnight must not buzz the instant the app
  opens; being in the app IS the reminder.
- **`Reminders.refresh()` is the only recompute path** — app start, every event write, every rule save, boot and
  app update. Five callers, one implementation. A second "compute the next reminder" would drift from the first.
- **Switching a rule off must CANCEL its alarm**, not merely skip it. A notification the parent has turned off is
  worse than no notification at all.
- **Alarms are EXACT when the system permits it.** `setExactAndAllowWhileIdle`, with `USE_EXACT_ALARM` declared
  (granted at install) and `SCHEDULE_EXACT_ALARM` for API 31-32. This reverses the first build, which used
  `setAndAllowWhileIdle` on the reasoning that "a few minutes late is fine": `dumpsys alarm` showed the armed
  alarms carrying `window=+37m47s`, so a few minutes could be most of an hour, and the owner reported reminders
  arriving late. When exactness is revoked the alarm is still armed inexactly — late is better than silent — and
  `ReminderScheduler.canBeExact()` is the one place that decides.
- **A health reminder names the ACTIVITY, never a medicine and never a dose.** The same rule as the names-only
  medicine list, applied to the one surface the parent sees without opening the app. `ReminderNotificationsTest`
  checks it against the whole bundled catalogue.
- **Setting a reminder turns it ON, and there is no `Notify` toggle to leave off** (Phase 20). The toggle was
  a silent trap: the sheet opened with it off, so `Set` wrote a rule that looked saved on the card, armed
  nothing, and said nothing about why — and tapping the disabled `Set` then just closed the sheet, which is what
  made it read as "saving does nothing".  A rule the parent wants is a rule they want to hear; one they do not
  want is REMOVED, not stored switched off. `sheetSaveConfig` is the one place that decides, and
  `ReminderSaveTest` pins it. Removal lives at the bottom of the sheet through the same destructive row every
  other editing sheet offers, labelled `Remove notification` because a button that says `Delete this entry` in a
  sheet that created no entry is a false label on the one control that cannot be undone.
- **A posted reminder is cleared when it stops being true, and `Reminders.refresh()` is what clears it**
  (Phase 20). `ReminderNotifications` could post and nothing else, so a night's reminders sat in the shade all
  morning, each still saying `3h 5m since the last feed`. Two facts make one false — the rule is gone or
  switched off, or the activity has been logged since it was posted — and both are decided by the pure
  `shouldClearPostedNotification`, comparing the notification's own `postTime` (read back from the shade, never
  remembered in preferences) with the child's newest row of that activity. That comparison is wall-clock, so a log
  written after a manual BACKWARDS clock change does not clear it; **accepted as a limitation on 2026-11-03**,
  because a correct fix needs a monotonic write sequence — a storage change — for a failure that leaves a stale
  notification rather than ever clearing a live one. The shade is read through
  `ReminderNotifier`, the third interface of the `ReminderDeps` family, so the decision is testable with no
  device. Clearing is in `refresh()` and nowhere else, for the same reason re-arming is.
- **A reminder is set from its activity's card, per child — never from Settings, never for "Both".** The `⋮` menu
  exists only on the four reminder-capable cards. A shared rule hid which twin was overdue, and a Settings section put
  the setting a screen away from the thing it is about.
- **A reminder chip shows the TIME it will ring** (`Every 3h (4:45 PM)`), and with both children on screen there is
  ONE CHIP PER CHILD, each carrying that child's badge — a single sentence naming two times made the parent work out
  which twin was which. Tapping a chip edits THAT CHILD's rule — the action carries the chip's child id, because a
  sheet that falls back to the first child is a reminder edited for the wrong twin. A rule with no logged anchor is
  anchored on when it was set (`anchorAt`), and an overdue interval rings a minute later — returning null in either
  case is a reminder that quietly stops reminding.
- **A reminder for an activity that child has never logged states the fact** (`No nappy logged yet`), it does not
  instruct. It used to read `Time to log ${displayName().lowercase()}`, which printed `Time to log diaper` —
  `displayName` where `noun()` was the right word. It claims no interval, because there is no log to measure
  from, and a future anchor (only reachable through a clock change) says `Logged for 1:00 PM` rather than
  inventing one. **Accepted, not to be fixed (2026-11-03):** such a rule re-arms a minute after every app start,
  because its `anchorAt` never moves and `refresh()` reads it as overdue — recorded in `PLAN.md`, 2026-10-29, and
  closed by decision rather than by a fix. Do not schedule one without being asked: the failure is a reminder that
  repeats while the parent is already looking at the app, never a missed one.
- **Anything that opens a reminder must carry the child it is about.** A chip and an Upcoming row both name a child;
  an action that carries only the activity falls back to the first child, which is a reminder edited for the wrong
  twin. Both had that bug before it was seen on the phone.
- **Every editing sheet offers `Delete this entry`**, routed through the same confirm-then-Undo path as a long-press.
  A parent who opened a record to change it should not have to close it and hunt for the row again.
- **The Activity screen's "Upcoming reminders" card reads the same `nextReminderAt` as the scheduler.** It must
  never be given its own idea of when a reminder is due: a card that promises a reminder the alarm will not make
  is worse than no card. It is absent when nothing is armed, ticks every 30 seconds, and NAMES the child whenever
  a row does not apply to both children — an unnamed row reads as "both", which the phone caught.
- **Only FOUR activities carry a reminder** — Bottle feed, Solids, Diaper, Health (`REMINDER_TYPES`). Growth is
  a periodic weigh-in and Sleep has its own running timer; neither is forgotten, so neither gets a notification.
  The engine, the Settings rows and the summary all read that one list, and a rule for a removed activity is
  cancelled rather than left armed.
- **A stepper whose values change width needs `valueWidth`.** `1h` and `1h 30m` are different widths, so without a
  fixed slot the −/+ buttons move under the parent's finger as the value changes. Found on the phone, in the reminder
  sheet, and the same trap waits for any future stepper.
- **A reminder time is shown in the parent's 12/24-hour format.** `clockLabel(minutes, format)`, never a
  hardcoded `HH:mm`: the picker and the notification follow the preference, so a row that does not is the same
  clock in two conventions in one sheet — which is exactly what the phone caught.
- **Reminders are per-device and never synced.** They live in DataStore, never in Room, and never in the outbox:
  each phone has its own idea of when to be woken, and a shared rule would be a second source of truth.
- **Alarms survive neither a reboot nor an app update**, which is why `ReminderBootReceiver` exists. Do not
  delete it because "the app re-arms on launch" — the point is the launch that never happens.

## Sync rules (Phase 13) that will cost you if you forget them

- **Later `updatedAt` wins, and a deletion wins a tie.** `domain/SyncMerge.kt` is the only place that
  decides it, and it is pure. A tie going to the live row resurrects a deleted bottle with nobody having
  logged it.
- **A drain that cannot reach the server must say so, and must let go of the lock.** Firestore with no
  network closes its write stream `UNAVAILABLE` and leaves the `Task` pending, so `awaitTask()` never
  returns: `syncNow()` never returned and `drainLock` stayed held, which is why `Sync now` looked inert and
  could not resolve itself. `OutboxDrainer` bounds each write with `writeTimeoutMs`, so a silent server is an
  ordinary failure with a `remaining` count, and `NetworkStatus` picks between *Waiting for a network
  connection* and *The server did not answer* — do not claim the phone is offline on four bars.
- **A sign-in that already succeeded is applied under `NonCancellable`.** The attach that seeds, drains and
  listens happens after a suspension point, so a `viewModelScope` cancelled in that window (the parent
  leaving Settings) lost the listener *and* the drain, and the seeded rows waited for the next write. The
  listener also drains once after its first snapshot, which is what makes "the queue empties on its own" true
  *after* the listener rather than only before it.
- **The outbox stores intent, never a payload**, and the drain re-reads the row by id at send time. The
  one exception is a *permanent* delete, whose row is already gone: it travels as an id-and-timestamp
  tombstone, which is why `OutboxDrainer` tries the row first and falls back to the tombstone.
- **A tombstone for an id this phone never had is not an instruction to create a row.** There is nothing
  to delete, and inventing a child from a document with no name would corrupt the percentile maths.
- **Firestore's disk cache is off** (`MemoryCacheSettings`). With the default cache, `set()` acks as soon
  as the write is in Firestore's local cache, so an offline drain would delete the outbox row while
  nothing had reached the server. Room + `outbox` is the only queue.
- **Every write path queues.** `EventRepository`, `ChildRepository`, `MaintenanceRepository`. Children
  once did not, and a rename would have stayed on the phone that made it.
- **`google-services.json` and `firebase.properties` are local-only and gitignored.** The build reads the
  four values into `BuildConfig` instead of using the `google-services` plugin, because that plugin fails
  the build when its JSON is missing — which would break a fresh clone's test run.
- **An import re-seeds the queue** (`reseedAfterRestore`). `replaceAll` wipes the tables *and* the outbox,
  so without this the next snapshot would merge the pre-restore log back in.
- **A reset pauses the inbound listener, or it undoes itself.** `SyncRepository.withoutInboundApply {}`
  cancels and **joins** the listener around the wipe and re-attaches after. Found on the phone in Phase 15:
  the wipe and its tombstone drain both worked, and the listener then applied a snapshot taken *before* the
  deletes landed — 15 events back in an emptied database with an empty outbox, so the phone showed a log
  the server no longer had and would never send again. `attach()` drains before it observes, so a tombstone
  the wipe queued travels before any snapshot can be applied.
- **A reset tombstones the two seeded children like any other row, and this is a reversal.** The first build
  of the reset re-created `first-twin` / `second-twin` with the SAME ids, so `resetLocal` subtracted
  `SEED_CHILD_IDS` from the tombstone pass and dropped any intent for them — a DELETE that drained would
  have erased them on the other phone while this one kept a copy. Once the reset stopped re-creating them
  (Phase 15, `0.18.1`) that exemption became the bug: the twins stayed on the server, `withoutInboundApply`
  re-attached after the wipe, and the snapshot put both children back into an emptied phone, so the
  `No children yet` state was unreachable on any signed-in phone. `SEED_CHILD_IDS` is now a list for tests
  and reading, never a rule, and `settings.markChildSeedDone()` is what keeps `AppRoot` from seeding them.
- **A reset is refused when the queue cannot travel, and "could not travel" includes being offline.**
  `SyncRepository.resetGate()` probes with a drain, but an EMPTY queue drains "successfully" without making a
  network call, so a connectivity check is what stops a reset on a plane from queuing deletions that never
  leave. *Delete all server data* is the deliberate escape and is offered only while signed in.

- **The food catalogue is published, not row-synced (ADR-0002).** `reference/food_catalogue` is one owner-written
  document, read-only to clients; it wins over the bundled `foods/foods.json` asset, and stable ids preserve
  old meals across a rename. `foods_pending/{id}` carries parent additions through the outbox to both phones;
  promotion is a console copy into the published catalogue, after which the pending document is removed.
  `foods_proposals/{id}` records a rename suggestion, never a change: only a publish changes the shared
  name. `localName`, `hidden` and `lastUsedAt` are per-device opinions and never travel. Missing pending
  documents hide the local row rather than deleting it, so an old meal still has a readable name.
- **A reset withdraws every pending food and clears local food opinions.** It pauses both inbound listeners;
  then the local catalogue is wiped and the bundled asset seeds back on next launch. There are no food
  tombstones and no merged `foods` server collection.

## Tests

677 unit tests through Phase 23 (`1.5.0`), all on the JVM (Robolectric for Room/Android). Run them with the build command above.

**Firestore cannot be unit-tested here** (no emulator, and Robolectric cannot reach it), so sync logic is
written against `data/sync/SyncGateway` and `AccountAuthenticator` and driven by `FakeSyncGateway` /
`FakeAuthenticator`. Only Firestore itself needs two real phones.

**Robolectric limit:** it cannot collect a Room-backed Flow — the paused main looper never pumps
Room's invalidation callbacks, so a test like that hangs. Test ViewModels against fake DAOs, not Room.

## Versioning

**One version per phase, set at the first commit of that phase's work.** A phase is delivered as one
branch, so every build made while it is under way then says which phase produced it — which is the only
question this number has ever been asked. `0.MINOR` is a new capability, `0.PATCH` is fixes and polish
only, and `1.0.0` is the first release.

| Phase | Version | Phase | Version |
|---|---|---|---|
| 1 scaffold | `0.1.0` | 9.1 cards and edits | `0.5.0` |
| 5 launch-crash fix | `0.1.1` | 10 Growth | `0.6.0` |
| 6 logging sheets | `0.2.0` | 11 Health | `0.7.0` |
| 7 Settings | `0.3.0` | 12 Trends | `0.8.0` |
| 8 Summary | `0.4.0` | 13 two accounts | `0.9.0` |
| 9 daily-use polish | `0.4.1` | 13.1 time/temp preferences | `0.10.0` |
| | | 13.1.1 twelve-hour default | `0.10.1` |
| | | 13.2 temperature bands | `0.11.0` |
| | | 13.3 diaper detail | `0.12.0` |
| | | 13.4 activity reminders | `0.13.0` |
| | | 14 Polish/Misc changes | `0.16.0` |
| | | 13.5 dataset channel | `0.17.1` |
| | | 15 reset to defaults | `0.18.0` |
| | | 15's own pass (fixes) | `0.18.1` |
| | | *not a phase:* three recorded gaps closed | `0.19.0` |
| | | 16 update delivery (first release) | `1.0.0` |
| | | 17 five todo items | `1.1.0` |
| | | 18 published food catalogue | `1.2.0` |
| | | 19 settings submenus | `1.2.1` |
| | | 20 tracking changes | `1.3.0` |
| | | 21 time picker | `1.4.0` |
| | | 22 Filled Soft redesign | `1.4.1` |
| | | 23 update check | `1.5.0` |
| | | 24 Ship | uses delivered release |

- **`0.19.0` / versionCode 25 is not a phase and takes a rung anyway.** Work that ships application code takes
  a number even when it closes defects rather than opening a phase (2026-10-29: dataset withdrawal, the
  never-logged reminder line, the flat-zero Trends axis). The Huckleberry import of 2026-10-28 moved no rung
  because it shipped none — that is the distinction, not whether the work felt big.

- **`versionCode` rises for every new version** (`0.1.0` → `1` … `0.19.0` → 25, `1.0.0` → 26); it never goes
  backwards, because App Distribution decides "newer" from it. The unused `0.14.0` and `0.15.0` are **not
  reserved**: unfinished phases get versions and codes above the latest delivered build when they land, in
  landing order. If they land after `1.0.0`, use a version newer than `1.0.0` rather than a pre-release
  number.
- **Phases are numbered in the order they land (re-ruled 2026-10-26).** A phase's number is fixed when its
  first commit lands: work that has not started takes the next free integer above the last landed phase, and
  may be renumbered freely while it still has no commits. **Decimal sub-phases are retired** — `9.1`, `9.2`,
  `13.1`, `13.1.1` and `13.2`–`13.5` stay as history and nothing new is numbered `13.7`. Phase 13.5 keeps its
  number because three commits on `main` already say `phase13.5`, and its rung is `0.17.1` after its own device
  pass; a landed rung is never renumbered to match a tidier label. Phase 16 delivered `1.0.0`; **Phase 17 is
a batch of five `todo.txt` items on `1.1.0`**, **Phase 18 is the published food catalogue on `1.2.0`**,
**Phase 19 is the Settings submenus on `1.2.1`** — a PATCH, because it moved where every setting is reached
and changed none of them — **Phase 20 is the four tracking changes on `1.3.0`**, a MINOR because a preset that
logs a feed in one tap is a capability the app did not have — **Phase 21 is the time picker on `1.4.0`**, a
MINOR because the dial and the wheels are two input modes the app did not have — **Phase 22 is the Filled
Soft redesign on `1.4.1`**, a PATCH because the card surface changed and no capability, control, copy or
stored byte did — **Phase 23 is the update check on `1.5.0`**, a MINOR because a build delivered by hand had
no way to say a newer one existed — and **Phase 24 is Ship**, which delivers `1.5.0`. Ship has no commits and
moves freely until its work starts.
- **A version after `1.0.0` is newer than `1.0.0`, never a `0.x`.** The ladder reached its release on
  2026-10-29, so a new capability takes `1.MINOR` (`1.1.0` first) and a fix takes `1.0.PATCH`. A `0.20.0`
  would install fine by `versionCode` and still read as going backwards on the phone.
- **Each dated `CHANGELOG.md` section carries a `### Versions` list** naming the versions that landed
  that day, directly after `**In short:**`. That file is the ladder's record — do not put a second copy
  in the README.
- **Nothing is tagged until a release.** Tags are for released builds, so `v1.0.0` is cut on the release
  APK delivered in Phase 16 — after a phone reports `versionCode 26`, never when a phase ends. Every rung
  from `0.1.0` to `0.19.0` stays untagged for that reason.

## Where things are

| Path | What |
|---|---|
| `PLAN.md` | Phase plan, scope decisions, and the log of bugs found on-device |
| `CHANGELOG.md` | Dated record of every day of work — a phase's entries go under the day it landed, written in the same pass as the phase commit |
| `scripts/` | `android-env.ps1`, `sdkmanager.ps1`, `capture-timeline.ps1`, `build-growth-assets.py`, `create-release-keystore.ps1` |
| `firebase/firestore.rules` | Phase 13 security rules — pasted into the console by hand, **not** deployed by a tool |
| `docs/firebase-setup.md` | Phase 13's console runbook: the project, the two accounts, the four `firebase.properties` values, and what each misconfiguration looks like on screen |
| `docs/release-signing.md` | Phase 16's runbook: where the key lives, the certificate's fingerprints, how to build and verify, upload to App Distribution, and cut a tag |
| `docs/DECISIONS.md` | ADRs — why a choice was made, one record per hard-to-reverse decision, starting with release-key custody and the manual delivery channel |

## Time and temperature preferences (owner override, 2026-10-23)

The owner requested explicit 12/24-hour and Celsius/Fahrenheit preferences. This supersedes
older Celsius-only guidance. Store temperatures in Celsius and timestamps unchanged; convert
only at display/input boundaries. Fever classification continues to use `FEVER_C`.
