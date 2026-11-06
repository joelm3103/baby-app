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

**No real child, no real log, no real notification.** Every frame is captured on a wiped install:

1. Settings → Data → *Delete all data* (this clears the log and the outbox; it does not clear the marketing
   folder, which is on this laptop).
2. Rename the two seeded children to neutral names — `Baby A`, `Baby B`. A marketing shot carrying a real
   twin's name is a photograph of a medical record, and it cannot be edited out afterwards.
3. Log four or five entries so the cards have something honest to show (a 120 ml bottle, two diapers, a 37.4 °C
   reading, a solids meal). Values may be invented; they just have to look plausible and be consistent.
4. Turn on Do Not Disturb, so no notification icon, no banner and no caller lands in the frame.
5. Clear the app from Recents and launch it fresh, so no sheet is open and no keyboard is up.

A set that fails any of those is **deleted and re-captured**, never cropped or retouched. The status bar clock
and battery are the only unavoidable personal-ish detail, and they are generic.

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

```powershell
powershell -NoProfile -File scripts/capture-timeline.ps1 `
  -Label marketing-dark -Purpose marketing -Scrolls 0
```

`-Purpose marketing` writes to `docs/marketing/<date>-<label>/` instead of `docs/timeline/…`; `-Scrolls 0`
takes the top of the screen only, because a marketing frame is one screen and not a survey. The remaining
frames are taken with `adb exec-out screencap -p` while driving the app by hand, then renamed to the table
above with their `NN-` prefix — `Save-Shot`'s `01-top` name is a timeline name and says nothing to a reader
here.

Check each file at full resolution before committing it. Two defects have shipped past a green build in this
repo and both were visible in a screenshot: a 4dp bar's corner wedge, and a fever number that measured 5.20:1
and was still invisible. A thumbnail would have hidden both.

## What is committed

`README.md` (this file) and the PNGs. Not the uiautomator dumps, not the databases, not an APK — those belong
under `docs/timeline/evidence/` with the pass that produced them.
