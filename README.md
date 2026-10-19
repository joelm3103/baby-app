# Babylog

A private baby tracker for Android, for two parents and **twin boys, the twins** (born a date set in Settings).
No store listing, no subscription, no account tiers.

**Premature at birth.** Both born prematurely — so adjusted age is a
first-class concept throughout, not a checkbox bolted on later.

**Why this exists:** the free alternative and the incumbent tracker are both good at the core job and both hostile to it — they
change under you, gate the useful parts, and put content feeds and upsells where the three actions we
actually perform should be. Babylog is the part we use, owned by us, that will work the same in two
years.

**Status:** through Phase 12. Toolchain, theme, domain, Room, repository/settings, the Activity screen,
the logging sheets, Settings, Summary, Growth, Health and Trends are built, and every screen is
device-verified. All six activities log real data: Growth ships WHO's LMS reference for percentile charts,
Health records a temperature, a medication with its dose and vaccines, and Trends charts bottle volume,
solids, diapers and sleep per day over a 7/14/30-day window (Health is a dated fever table rather than a
chart, because a temperature is a reading and not a daily amount). The shared log across two accounts is
Phase 13. See `PLAN.md`.

---

## Scope

Shaped by 15 real screenshots of the incumbent tracker plus three bugs the owner hit in it, not by guesswork.

| # | Feature | Notes |
|---|---|---|
| 1 | **Two children, switcher in the header** | `Child 1 · Child 2 · Both`. Both-mode interleaves both children's data |
| 2 | **Log for both in one action** | With Both selected, one tap writes a row per child |
| 3 | **Bottle** | Amount + formula brand + time |
| 4 | **Solids** | Meal slot (breakfast/lunch/dinner/snack), food catalogue with search, per-food reaction/allergy flags |
| 5 | **Diaper** | `Wet / Dirty / Mixed` as one-tap pills, plus a rash toggle |
| 6 | **Sleep** | Present with a running timer even though it's rarely used — explicitly requested |
| 7 | **Growth** | Weight / height / head size, WHO percentile charts |
| 8 | **Health** | Temperature, medication (from a bundled names-only list) with its dose, and vaccines |
| 9 | **Summary** | 1d / 7d / 14d totals per activity, with activity filter chips and one `All` / `None` bulk chip |
| 10 | **Trends** | Charts over time: bottle volume, solids, diapers and sleep per day (7d / 14d / 30d), one line per child; Growth's percentiles keep their own tab, and Health is a dated fever table |
| 11 | **Premature / adjusted age** | **Both born prematurely**. Gestational age per child + a "use adjusted age" toggle; age shown both ways |
| 11b | **Units: Metric / Imperial / Custom** | Length in/cm, weight lb&oz / kg, volume fl oz / ml — each independently selectable. Temperature is **always °C** |
| 12 | **Three tabs** | Activity · Summary · Trends. Settings is a header icon |
| 13 | **Follow the system theme** | Dark (sampled from real screenshots) and light (designed) |
| 14 | **Two accounts, one shared log**, offline-first | Local DB is the source of truth |
| 15 | **Export / backup** | Settings → Data: the whole log as readable JSON, and a restore. Deleted rows ride along as tombstones, so a restore can neither resurrect them nor lose them |

### Post-build changes

Changes after v1 are expected, and the architecture is chosen to keep them cheap: canonical metric
storage, a design-token file rather than hardcoded styling, pure tested logic, and an additive-only
migration rule. The two things that are **not** cheap to retrofit are **photos/attachments** (schema +
sync + backup all change at once) and renaming what a stored value means — so those need deciding
before v1, not after. See the table in `PLAN.md`.

### Deliberately not built

Guides, an Account tab, subscription/trial/paywall, the shop, Stop Tracking, nursing/pumping (not
logged), the clinical diaper taxonomy, pregnancy/postpartum modes, customizable home buttons,
analytics. Full list with evidence in `.hermes/skills/babylog-ui/references/style-guide.md`.

`Export` is no longer on this list: it is row 15 above, built in Phase 7 and device-verified — the JSON
comes back in through `Import`, so a bad migration cannot lose the log and a replacement phone stays
restorable.

### Staged on purpose

The full set is a real project, not a weekend. Staging so it becomes useful early:

- **Stage 1 — the 3am app.** Two children + switcher, Bottle, Diaper, Solids, Sleep, Summary totals,
  system theme, correct insets and back handling.
- **Stage 2 — Growth and Health.** WHO percentile curves (a real reference dataset) and the medical
  records. Growth is the single heaviest item; it lands after the app is already in daily use.
- **Stage 3 — Firebase sync** across the two phones, then over-the-air update delivery.

---

## The three bugs in the incumbent tracker we are designing against

the owner reported these unprompted, so they are requirements, not polish:

1. **Content clipped by the system navigation bar** (the incumbent's Child screen, screenshot #15) — "it cannot
   scroll down any more". We consume `WindowInsets.navigationBars` and pad the scrollable content.
2. **System back exits the app instead of closing a sheet** — every sheet gets a `BackHandler`.
3. **Only one child visible at a time** — hence the switcher and the Both view.

---

## Stack

**Kotlin + Jetpack Compose (Material 3) · Room · Firebase Auth + Firestore · sideloaded APK.**

| Choice | Why |
|---|---|
| Kotlin + Compose, single `app/` module | Android-only; multi-module buys nothing here |
| Room as the source of truth | Must work in a car park, a clinic waiting room, a corridor. Local-first is not optional |
| Firestore for sync, offline persistence on | Deletes a whole class of sync bugs. Free tier covers two users by three orders of magnitude |
| Google sign-in | Two people, two phones, no password reset flow to build |
| Sideload the release APK | Free. A Play listing costs $25 and adds review latency for zero benefit |
| No server to run | An app the second parent depends on should not page me because a container is down |

**Rejected:** self-hosting on the work server (company machine, the family's health log, and the second parent's
phone would need network reach into it) · Supabase (close call — free and self-hostable, but no
built-in offline persistence; documented escape hatch) · forking an open-source tracker (inherits
someone else's feature set, which is the problem being escaped).

**One flag, stated once:** Firestore holds our children's feeding/sleep/diaper log on Google
infrastructure. It is private to our two accounts and Google already hosts our mail. If that ever
stops being acceptable, Supabase is the documented exit — and because Room is the source of truth, the
data is never *only* there.

---

## Repo layout

```
baby-app/
├── README.md                  # this file — scope, stack, decisions
├── PLAN.md                    # implementation plan, phased
├── .hermes/skills/babylog-ui/ # the design system, loadable as a project skill
├── scripts/                   # android-env.ps1, sdkmanager.ps1 (toolchain, re-runnable)
└── research/
    ├── SOURCES.md             # marketing-frame provenance + fidelity caveat
    ├── toolchain.md           # exact versions + AGP 9 / KSP traps
    ├── screenshots/           # raw Play Store frames + raw/ (15 real ones, gitignored)
    ├── design-refs/           # the approved mockup
    └── findings/              # teardowns, shared design language, reference-app-a-palette.md
```

**Note on which evidence wins:** the marketing frames produced two outright errors (light surfaces,
yellow diaper) that 15 real screenshots corrected. When the two disagree, the screenshots win, and
`sleep`'s hue is still an acknowledged guess because no sleep screen was ever captured.
