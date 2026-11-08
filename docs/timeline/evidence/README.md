# Evidence — raw device state behind the timeline

The screenshots in this folder's parent are what the screens looked like. These files are what the
**database** held at the same moments, read off the device with `run-as app.babylog` rather than inferred
from the screen. They are here because the Phase 12 and 13 write-ups in `PLAN.md` quote numbers — "five
events, five distinct ids", "outbox 0", "13 events, 12 live" — and a claim is only worth what its evidence
is worth.

Nothing here is needed to build or run the app. Nothing here is imported by the app either: an export file
(`Settings → Data → Export`) is the supported restore path, not these.

## `databases/2026-10-22-phase13/` — the Phase 13 passes, in capture order

`user_version` is 2 in every one of them (the Phase 11 schema), and every captured file was a consistent
snapshot rather than a live WAL pair.

| File | events | live | children | outbox | What it is |
|---|---|---|---|---|---|
| `phase13-01.db` | 2 | 2 | 2 | **4** | The unconfigured build after a Both-mode bottle: two event rows plus **two child UPSERTs queued** — the defect Phase 13 found in `ChildRepository`, visible as a count |
| `phase13-02.db` | 1 | 1 | 1 | 4 | After *Delete permanently* on the second child: the child row and their bottle hard-deleted, the two dead UPSERTs dropped, the DELETEs queued |
| `phase13-03.db` | 0 | 0 | 2 | 0 | After `pm clear` and a relaunch: the seed re-created both children, nothing logged, nothing queued |
| `phase13-04.db` | 2 | 2 | 2 | 0 | The configured build, signed in, one Both-mode write acked — outbox already empty |
| `phase13-05.db` | 5 | 5 | 2 | **3** | Airplane mode: the earlier two rows plus three offline writes queued as exactly three intents |
| `phase13-06.db` | 5 | 5 | 2 | **0** | Reconnected: five events under five distinct ids, no duplicates — the exactly-once result |
| `phase13-07.db` | 6 | 6 | 2 | 0 | One further offline write, drained by itself on reconnect |
| `phase13-08.db` | 7 | 7 | 2 | 0 | The state the second device (`emulator-5554`) pulled and matched by row id |

`phase13-05` and `phase13-06` are the pair that proves the acceptance criterion: the same five rows before
and after reconnect, with the queue drained and nothing duplicated.

## `databases/2026-10-22-late-sweep/` — the end-of-day sweep

| File | Contents | What it is |
|---|---|---|
| `sweep-baseline.db` | `android_metadata` only, `user_version` 0, **no app tables** | The file present on the device *before* the log was there — captured so the sweep could prove the difference rather than assume it |
| `sweep-baseline2.db.tar` | v2, 7 events, 7 live, outbox 0 + `-wal` + `-shm` | The post-round-trip state with its WAL. Logically the same as `phase13-08`, but kept as a tar because it carries the write-ahead log |
| `sweep-after-delete.db` | v2, 7 events, **6 live**, outbox 0 | After one row was soft-deleted through the app: the tombstone is in the data, not removed from it |
| `sweep-after-delete.db.tar` | same state + `-wal` + `-shm` | The tar form of the row above |

**One file was deliberately dropped as an exact duplicate.** The sweep also left an extracted
`baseline2/databases/babylog.db` whose bytes were identical to `phase13-08.db` (md5 `06e965b4…`); the copy
inside `sweep-baseline2.db.tar` is a different file because of the WAL, so the tar was kept and the
redundant extracted copy was not.

## `uiautomator/2026-10-22-late-sweep/` — the accessibility tree behind each tap

Twelve `uiautomator dump` files: `ui-home`, `ui-home2`, `ui-settings`, `ui-summary`, `ui-trends`,
`ui-sync`, `ui-sync-form`, `ui-sync-filled`, `ui-delete`, `ui-after-delete`, `ui-after-clear`, `ui-back`.
These are how every tap coordinate in that sweep was obtained — a dump, never a guess — and they are the
only machine-readable record of what the screen *said*, down to a button's `content-desc`.

## `uiautomator/2026-11-07-phase28/` — the tree behind the pull-to-sync pass

Four dumps from the Phase 28 device pass on `1.7.2` / versionCode 37: `p28-a` (Today before any gesture),
`p28-after-back` (Today after system `BACK` closed the bottle sheet), and `p28-pull1` / `p28-pull2` (the two
pulls). The pair is the proof that `BACK` wrote nothing — the same nine `oz` rows appear in `p28-a` and
`p28-after-back` — and the pulls carry the snackbar text the screen showed, `Please wait before syncing again.`
then `Up to date.` after the cooldown had run out. The card's `+ Log feed for both` button was tapped at
`540,1329`, taken from `p28-a` rather than estimated.

## `release/` — the build the passes used

`babylog-0.9.0-debug-configured.apk` (19 MB) is the configured `0.9.0` debug build the Phase 13 device
passes ran on. It is reproducible from commit `0464d78` **with this machine's** `firebase.properties` and
debug key, so this copy is the reference for "what was on the phone", not a distribution artifact. A debug
key is per machine, which is why the same source built elsewhere is a different installable file.
