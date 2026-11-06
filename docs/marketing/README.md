# Marketing screenshots

The generic set: what the app looks like when somebody is deciding whether it is for them. A handful of
clean frames, one theme per set, taken from a fresh install.

**Not the same thing as `docs/timeline/`.** The timeline answers *how did this change, and what was rejected*,
so it keeps every frame including the ugly ones. This folder answers *what does it look like now*, and a
rejected option has no place in it. When the two disagree about a screen, both are right — they were taken
for different reasons.

**These are committed to git.** That is the difference from the timeline, whose PNGs are ignored because they
are regenerable from a phone and this set is not: it is a curated selection, and the choice of frames is the
asset. It is only safe to commit because of the rule below — nothing in here belongs to a real baby.

## The rule this folder lives or dies by

**No real child, no real log, no real notification.** Every frame is captured on an install that has never
held a real entry:

1. Start from an install with no log on it, and nobody signed in. A fresh install with no account is empty;
   signing in is what pulls an existing log back down, so never sign in with the family's account (see
   *Where*). A device that has been logged into before is cleared with Settings → Data → **Reset local data**
   (the row whose value reads *Erases this log*) — and then only if it is not the family phone. `Delete all
   data` is not a control this app has, and the reset is not the first step of a capture on a clean device.
2. Rename the two seeded children to neutral names — `Baby A`, `Baby B`. A fresh install seeds
   `First Twin` / `Second Twin` (`ChildRepository.seedIfEmpty`), so nothing real is present by default; the
   rename is for the look, not the safety. A marketing shot carrying a real twin's name is a photograph of a
   medical record, and it cannot be edited out afterwards.
3. Log four or five entries so the cards have something honest to show (a 120 ml bottle, two diapers, a 37.4 °C
   reading, a solids meal). Values may be invented; they just have to look plausible and be consistent.
4. Turn on Do Not Disturb, so no notification icon, no banner and no caller lands in the frame — then check
   each frame anyway, because a setting is not proof that nothing slipped through.
5. Clear the app from Recents and launch it fresh, so no sheet is open and no keyboard is up.

**Where — and this is the rule that matters more than the other five.** Never capture on the phone that holds
the real log, and never sign the emulator or spare handset in with the family's account: a signed-in install
pulls the log down from Firestore, so the frames would carry it and the "wiped install" above would have
bought nothing. `Reset local data` runs `sync.withoutInboundApply { wipePhone(); sync.drainUntilIdle() }`: the
deleted rows are queued as tombstones and drained to Firestore, so a reset on a signed-in phone empties the
other parent's copy too. A marketing set is not worth the family's log. Capture on an emulator —
`pixel_api36` exists on this machine — or on a spare handset, signed out.

A set that fails any of those is **deleted and re-captured**, never cropped or retouched. Some chrome cannot
be removed and is not a defect: the clock and the battery, and on an emulator its own status and navigation
bars plus the USB-debugging icon. Prefer a handset if the set is going anywhere public.

## The set

Ten frames, dark mode unless stated. One set per `versionName`, re-captured when a screen in it changes shape
— not on every rung.

| # | Screen | Why it is in the set |
|---|---|---|
| 01 | Activity, both children, a few entries | The home screen: tinted cards, the child switcher, the artwork |
| 02 | Activity, dark, scrolled to Growth and Health | Shows the whole card stack rather than the top two |
| 03 | Summary, 1W | The numbers a parent actually shows someone |
| 04 | Trends, Bottle feed · 1W | The chart, both children on one axis |
| 05 | Trends, Growth tab | WHO percentiles — the feature nothing else in this space does |
| 06 | Bottle sheet, with `Consumed: …` and a preset | The two-number bottle and the one-tap pills |
| 07 | Diaper sheet, dirty, consistency selected | The seven silhouettes, which are shape and not colour |
| 08 | Health card with a fever pill | The temperature band, marked by a shape |
| 09 | Settings menu, light | The eleven groups with their one-line summaries |
| 10 | About | The version row and the credit handle — the only identity the build carries |

Light mode is a *second* set, not a mixture: mixing the two in one folder makes the section look accidental.
Capture it as its own `<date>-<label>`.

## How to capture

Install the build you are photographing, then let the script take the first frame:

```powershell
# The path is native (`C:/…`), not a Git Bash `/c/…`; adb is a native binary and rejects the POSIX form.
$ADB = "C:/Users/$env:USERNAME/AppData/Local/Android/Sdk/platform-tools/adb.exe"
$OUT = "docs\marketing\$(Get-Date -Format yyyy-MM-dd)-marketing-dark"
& $ADB install -r app/build/outputs/apk/release/BabyLog-1.7.1.apk   # or whichever build you are photographing
powershell -NoProfile -File scripts/capture-timeline.ps1 `
  -Label marketing-dark -Purpose marketing -Scrolls 0 -SkipInstall
```

`-Purpose marketing` writes to `docs/marketing/<date>-<label>/` instead of `docs/timeline/…`; `-Scrolls 0`
takes the top of the screen only, because a marketing frame is one screen and not a survey.

**`-SkipInstall` is not optional when the device already carries a release build.** Without it the script
installs the newest APK in `app\build\outputs\apk\debug` — on this machine a stale debug `1.7.0` — over the
release-signed build that is already there (the artifact is named `BabyLog-<versionName>.apk` in both
directories). The two are signed with different certificates, so `adb install -r` answers
`INSTALL_FAILED_UPDATE_INCOMPATIBLE` and the script stops with `APK install failed`. Install the build you
actually want to photograph, keep it installed, and pass `-SkipInstall`.

**The script captures frame 01 only.** `Save-Shot "01-top"` is a timeline name and says nothing to a reader
here, so rename it to `01-activity-both` from the table. The remaining nine are taken by hand, one command per
frame, while you drive the app: navigate to the screen, run the command with that frame's `NN-` name from the
table, and repeat. Ten files in the folder is the finished state.

```bash
# Git Bash. Its own assignment — $ADB from the PowerShell block above does not exist in this shell.
ADB="C:/Users/$USERNAME/AppData/Local/Android/Sdk/platform-tools/adb.exe"
OUT="docs/marketing/$(date +%F)-marketing-dark"   # the folder the script just created

# byte-exact: `exec-out` sends the PNG straight to the file with no decoding in between.
"$ADB" exec-out screencap -p > "$OUT/06-bottle-sheet.png"
```

```powershell
# PowerShell instead decodes a native command's stdout as TEXT, which corrupts the PNG — so write on the
# device and pull the bytes back. That is exactly what Save-Shot does, and why.
& $ADB shell screencap -p /sdcard/shot.png
& $ADB pull /sdcard/shot.png "$OUT\06-bottle-sheet.png"
```

In Git Bash an unquoted `/sdcard/…` is rewritten into a Windows path before `adb` ever sees it, so prefix the
call with `MSYS_NO_PATHCONV=1` — under PowerShell the same path needs no such guard. Both forms write into a
folder that already exists; if you are capturing on a later day than the script ran, `-Label` and `$OUT` must
name the same one.

Check each file at full resolution before committing it. Two defects have shipped past a green build in this
repo and both were visible in a screenshot: a 4dp bar's corner wedge, and a fever number that measured 5.20:1
and was still invisible. A thumbnail would have hidden both.

## What is committed

`README.md` (this file) and the PNGs. Not the uiautomator dumps, not the databases, not an APK — those belong
under `docs/timeline/evidence/` with the pass that produced them.
