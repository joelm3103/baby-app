---
description: Close out a Babylog phase — plan, changelog, counts, device pass, then stop before merge
argument-hint: "[phase number]"
---
Close out ${1:-the current phase} for **Babylog**. This repo's conventions are pinned below; `AGENTS.md`
is the source of truth and wins wherever these disagree. Do all of this in order:

1. **Changelog** — `CHANGELOG.md`, following the `changelog` skill: dated sections newest-first, one per
   day the work landed, `Added` → `Changed` → `Fixed` → `Security`, and a retrospective entry says so in
   its own text. Never open a second section for a day that already has one — add to it instead.

2. **Plan** — `PLAN.md`: the phase's status-table row, its heading marker (`— DONE, device-verified`), the
   as-built notes where the build differed from the plan, and a dated note under the phase. Rename nothing
   else, and do not rewrite earlier phases' text: a verification table records what was true that day.

3. **Counts — three places, read from the artifact.** Totals come from
   `app/build/test-results/testDebugUnitTest/*.xml`, summing the `tests=` / `failures=` / `skipped=` /
   `errors=` attributes. Never from memory, never from the last chat message.
   - `PLAN.md`'s status-table `Verified:` line — **current state: it must move whenever the suite grows**,
     including growth that came from a later, unrelated change;
   - `AGENTS.md`'s Tests section ("N unit tests, all on the JVM") — also current state, also moves;
   - the per-phase status rows — **historical: leave them alone.** A phase keeps the count it landed with.

4. **Device pass** — a green build is not evidence of layout or of system back. Drive the real phone:
   `"$ADB" install -r app/build/outputs/apk/debug/app-debug.apk`, then check the foreground *before*
   sending any input (`dumpsys activity activities | grep topResumedActivity`), take every tap coordinate
   from `uiautomator dump` — icon buttons appear as `content-desc`, not `text` — and finish with
   `logcat -b crash -d` for a zero `FATAL EXCEPTION` count. `adb` is a native binary: pass `C:/Users/...`,
   never `/c/Users/...`. Record the pass as a table under the phase in `PLAN.md`.

5. **Commits** — small and scoped; the changelog and plan updates ride the same branch as the code.

6. **Report** — the branch, the commit hashes, the test count with the XML it came from, what the phone
   actually showed, and exactly what you propose to merge.

Then STOP. Do **not** merge into `main` and do **not** push until I approve. The branch is
`<type>/<slug>` cut from `main` (see `AGENTS.md` §Git workflow) and is deleted only after the push lands.
