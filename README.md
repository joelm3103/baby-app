# BabyLog

no subscription, no account tiers.

**Why this exists:** the free alternative and the incumbent tracker are both good at the core job and both hostile to it — they
change under you, gate the useful parts behind subscriptions, and bury the three actions we actually
perform at 3 AM under tabs, content feeds and AI features. This app is the 20% we use, owned by us,
that will still look and work the same in two years.

**Status:** research and design complete. No application code yet. Toolchain not installed yet
(see `PLAN.md` Phase 0).

---

## Scope

### V1 — build this

The test for every item: *does this help at 3 AM, or does it help the company retain a user?*

| # | Feature | Notes |
|---|---|---|
| 1 | **Four one-tap loggers** — Feed, Sleep, Diaper, Pump | Feed splits into bottle (amount) and nursing (L/R timer). Diaper is Pee/Poo/Both. |
| 2 | **Home dashboard** — four banded category cards, fixed order | Each card answers "when was the last one, and how much?" in one glance, and logs in one tap |
| 3 | **Live timer** for sleep and nursing | Start/stop from the card. Recomputes from stored `startedAt`, survives process death |
| 4 | **Today's timeline** below the cards | Day-grouped, newest first, edit/delete |
| 5 | **Two accounts, one shared log** | Me and the second parent see the same data; sign-in via Google account |
| 6 | **Offline-first** | Works with no connectivity; writes queue and sync. The local DB is the source of truth |
| 7 | **oz/mL preference**, timezone-correct display | Settings is nearly empty by design |
| 8 | Single child, with the schema able to hold more | The header shows a name, not a switcher, until a second child exists |

### V1.1 candidates (deliberately deferred)

- One ongoing-timer notification (the single most defensible notification in this app).
- A one-line "last 24h" summary: feeds, sleep total, diapers.
- Solids logging — when solids actually start.

### Not building — with evidence

Full list and the on-screen evidence for each in
`.hermes/skills/babylog-ui/references/style-guide.md` → *the do-not-build list*.
Headlines: AI nap prediction, LLM chat assistant, sleep-training programs, tips/insights content feed,
reports & trends & percentile charts, subscriptions, the shop, the pregnancy/postpartum mode, clinical
diaper taxonomy, caregiver roles, customizable home buttons.

---

## Stack decision

**Kotlin + Jetpack Compose (Material 3) · Room · Firebase Auth + Firestore · sideloaded APK.**

| Choice | Why |
|---|---|
| Kotlin + Compose, single `app/` module | Android-only app for two devices; multi-module and Kotlin Multiplatform are cost with no payoff here |
| Room as the source of truth | The app must work in a basement, a car and a hospital corridor. Local-first is non-negotiable |
| Firestore for sync, offline persistence on | Removes an entire class of bugs: no hand-written sync, no merge logic, no polling. Free tier covers two users by three orders of magnitude (a busy day is ~40 events; the free tier allows 20,000 writes/day) |
| Google sign-in | Two people, two phones. No password to manage, no reset flow to build |
| Sideload the release APK | Free. Both phones are ours; a Play listing costs $25 and adds review latency for zero benefit. Revisit only if someone else ever needs it |
| No server to run | An app the second parent depends on at 3 AM should not page me because a container is down |

### Alternatives considered and rejected

- **Self-hosted backend on the work server (`internal.apps`, 10.10.0.120).** Rejected on three counts:
  it is a **company machine** and this is the family's health log; the second parent's phone would need network
  reach into it; and it makes the app's uptime my problem. Self-hosting wins on ownership and loses on
  every other axis for this particular app.
- **Supabase (Postgres + auth + realtime).** Genuinely close — free, self-hostable later, data stays
  in a real Postgres. Rejected for v1 because it has no equivalent of Firestore's built-in offline
  persistence, so I would hand-write the write queue and conflict handling. **This is the escape hatch**
  if the Google-hosting objection below ever outweighs the engineering cost.
- **Google Sheets / a shared note as the "app".** Fails the one-tap test and has no timers.
- **Forking an existing open-source tracker.** Would inherit someone else's feature set — the exact
  problem this project exists to escape.

### One flag, stated once

Firestore stores our child's feeding/sleep/diaper log on Google infrastructure. That is the price of
the recommendation above. It is private to our two accounts, it is not health-record data in any
regulated sense, and Google already hosts our mail. If that trade stops feeling acceptable later,
Supabase is the documented exit and the local Room DB means the data is never *only* there.

---

## Repo layout

```
baby-app/
├── README.md                  # this file — scope and stack decisions
├── PLAN.md                    # implementation plan, phased, task-by-task
├── .hermes/skills/babylog-ui/ # the design system, loadable as a skill while building
│   ├── SKILL.md
│   └── references/{design-tokens,components,layout,style-guide}.md
└── research/
    ├── SOURCES.md             # where the screenshots came from + fidelity caveat
    ├── screenshots/           # 24 Play Store frames (gitignored, regenerable)
    └── findings/              # 4 teardown reports: reference-app-b-1/2, reference-app-a, design-language
```

## The one input that would improve the design work

The findings are built from **Play Store marketing frames** — rotated phone mockups on pastel
backgrounds. Structure, navigation, feature inventory and interaction patterns came out reliable;
**every colour value in them is an eyeballed approximation**. The palette in
`.hermes/skills/babylog-ui/references/design-tokens.md` is therefore *our own chosen* palette built to
the verified hue grammar, not a copy of either app.

To make the pixel layer trustworthy: 6–10 raw screenshots per app from a real device
(`adb exec-out screencap -p > screen.png`), status bar cropped. Worth doing once the toolchain in
Phase 0 exists, since that is also how the app gets screenshotted for review.
