#!/usr/bin/env python3
"""Convert Huckleberry CSV exports into a BabyLog backup file (`Backup.kt`, schema 1).

Huckleberry exports one CSV per child, with the same nine columns for every activity type, reusing
"Start Condition" / "Start Location" / "End Condition" / "Duration" differently per type:

    Feed    Start Condition = Formula          Start Location = Bottle      End Condition = "6oz"
    Solids  Start Condition = foods eaten      End Condition  = reaction      Notes
    Diaper  Start Condition = consistency      End Condition  = what was in it Duration = stool colour
    Growth  Start Condition = weight           Start Location = length        End Condition = head
    Sleep   Start/End = the interval           Start Condition = mood         Start Location = where

Every conversion below is deliberate and reversible by reading this file. What the schema cannot hold is
counted and printed in the report rather than silently dropped.

Units, and why: Huckleberry writes "18.44lbs.oz" and "2.4ft.in", i.e. the dot SEPARATES two units, it is
not a decimal point. Proof from the data itself — one child's length appears as "2.4ft.in" on one row and
"71cm" on another, and 2 ft 4 in is 71.1 cm. The same reading is applied to pounds.ounces.

Usage:
    python scripts/huckleberry-import.py --kai Kai-log.csv --levi Levi-log.csv --out babylog-import.json
"""

from __future__ import annotations

import argparse
import csv
import json
import re
import unicodedata
import uuid
from collections import Counter, defaultdict
from datetime import datetime, timedelta
from zoneinfo import ZoneInfo

# ---- children -------------------------------------------------------------------------------------

# Birth date and gestation come from the app's own seeded values, which the owner set; the export does
# not carry either. Sex is not in the export either.
CHILDREN = {
    "kai": {
        "id": "kai",
        "name": "Kai",
        "sex": "BOY",
        "birthDateEpochDay": 20453,  # 2025-12-31
        "gestationalAgeDays": 210,  # 30 weeks
        "sortOrder": 0,
    },
    "levi": {
        "id": "levi",
        "name": "Levi",
        "sex": "BOY",
        "birthDateEpochDay": 20453,  # 2025-12-31
        "gestationalAgeDays": 210,  # 30 weeks
        "sortOrder": 1,
    },
}

ZONE = ZoneInfo("America/Port_of_Spain")  # the phone's zone: UTC-4, no DST
# Imported rows carry the import time as updatedAt, not their own start. A corrected value has to be
# NEWER than the copy already on the server, or SyncMerge's tie rule can keep the old one.
IMPORTED_AT = int(datetime.now(tz=ZONE).timestamp() * 1000)
CREATED_BY = "huckleberry"  # provenance, so an imported row is never mistaken for one typed in-app

# The app's own plausibility guards (ui/home/GrowthEntry.kt). A converted row outside these would be
# rejected by the sheet, so the importer refuses it too rather than writing a value the UI calls invalid.
MIN_WEIGHT_G, MAX_WEIGHT_G = 300, 30_000
MIN_LENGTH_MM, MAX_LENGTH_MM = 100, 1_500

OZ_ML = 29.5735
LB_G = 453.59237
IN_MM = 25.4

# Huckleberry's consistency words -> the app's seven silhouettes. "Loose" has no exact member; the owner
# chose MUSHY (soft/pasty) over RUNNY, and the report says so.
CONSISTENCY = {"solid": "SOLID", "loose": "MUSHY", "pebbles": "PEBBLES"}

# Owner-ruled corrections, keyed by (child, date, field). Each one is a judgement about the SOURCE data,
# never a conversion, so it lives here with its reason rather than inside the parser.
#
# Kai 2026-03-11 is impossible as written: 3.78 lb (1715 g) followed 16 days later by 8.84 lb (4010 g),
# with the head growing 320 -> 380 mm in the same fortnight. Read as 7.78 lb (3530 g) the series is smooth.
OVERRIDES = {
    ("kai", "2026-03-11", "weightGrams"): 3530,
}

# Fields to leave OUT of a row whose source value cannot be true. The 2026-03-11 measurement is the case:
# its length and head describe a baby 6-8 weeks younger (404 -> 498 mm and 320 -> 380 mm head inside 16
# days is not growth), so the row is either mis-dated or was mistyped. Its weight is kept — corrected to
# 3.53 kg it sits smoothly between 1409 g at birth and 3867 g on 27 March — while the two impossible
# measurements stay absent, which is what the app already means by "not recorded".
DROP_FIELDS = {
    ("kai", "2026-03-11"): ("lengthMm", "headMm"),
}

# The app has two reactions; Huckleberry's "HATED" has no home and is carried into the note instead.
REACTION = {"loved": "LOVED_IT", "allergic": "ALLERGY_OR_SENSITIVITY"}

COLOUR = {"yellow": "YELLOW", "brown": "BROWN", "green": "GREEN", "black": "BLACK",
          "red": "RED", "white": "WHITE"}

problems: list[str] = []
growth_raw: dict[str, str] = {}
unmapped: Counter = Counter()


def note_problem(msg: str) -> None:
    problems.append(msg)


# ---- unit parsing ---------------------------------------------------------------------------------

# Huckleberry's pair units separate the two units with the dot: "18.44lbs.oz" is 18 lb 4.4 oz, and
# "2.4ft.in" is 2 ft 4 in. Taken from the app's own wording on an Oct 21 measurement — "weighed 12lbs
# 11.5oz, 2ft 2.8in, 16.5in head" — which is the same value the CSV would export.
#
# The trailing digits drop the decimal point when they need to: 4.4 oz exports as "44", 11.5 as "11.5".
# So a minor value above the legal range is read as tenths (44 -> 4.4 oz, 77 -> 7.7 in). Anything else
# would make "1.77ft.in" equal 1 ft 77 in.
PAIR_LB = re.compile(r"^(\d+)\.([\d.]+)lbs\.oz$", re.I)
PAIR_FT = re.compile(r"^(\d+)\.([\d.]+)ft\.in$", re.I)
WEIGHT_KG = re.compile(r"^([\d.]+)kg$", re.I)
WEIGHT_G = re.compile(r"^(\d+)g$", re.I)
LENGTH_CM = re.compile(r"^([\d.]+)cm$", re.I)
LENGTH_IN = re.compile(r"^([\d.]+)in$", re.I)
LENGTH_MM = re.compile(r"^(\d+)mm$", re.I)

OZ_G = 28.349523125


def _minor(raw: str) -> float:
    """The trailing part of a pair unit.

    One digit is whole units ("1.5lbs.oz" is 1 lb 5 oz, a birth weight); several digits with no decimal
    point are tenths ("18.44" is 18 lb 4.4 oz, "1.77ft.in" is 1 ft 7.7 in); an explicit decimal is
    taken as written ("12.11.5" is 11.5 oz). This is the only reading under which every weight and
    length in both exports rises monotonically — 13-as-ounces makes Levi lose 224 g in a week.
    """
    return float(raw) / 10 if len(raw) > 1 and "." not in raw else float(raw)


def weight_grams(text: str) -> int | None:
    """Pounds.ounces, kilograms, or an explicit gram value."""
    text = (text or "").strip()
    if not text:
        return None
    if m := WEIGHT_G.match(text):
        return int(m.group(1))
    if m := PAIR_LB.match(text):
        pounds, ounces = int(m.group(1)), _minor(m.group(2))
        return round(pounds * LB_G + ounces * OZ_G)
    if m := WEIGHT_KG.match(text):
        return round(float(m.group(1)) * 1000)
    note_problem(f"weight not understood: {text!r}")
    unmapped["weight:unparsed"] += 1
    return None


def length_mm(text: str) -> int | None:
    """Feet.inches, centimetres, inches, or an explicit millimetre value."""
    text = (text or "").strip()
    if not text:
        return None
    if m := LENGTH_MM.match(text):
        return int(m.group(1))
    if m := PAIR_FT.match(text):
        feet, inches = int(m.group(1)), _minor(m.group(2))
        return round((feet * 12 + inches) * IN_MM)
    if m := LENGTH_CM.match(text):
        return round(float(m.group(1)) * 10)
    if m := LENGTH_IN.match(text):
        return round(float(m.group(1)) * IN_MM)
    note_problem(f"length not understood: {text!r}")
    unmapped["length:unparsed"] += 1
    return None


def ounces_ml(text: str) -> int | None:
    m = re.match(r"^([\d.]+)oz$", (text or "").strip(), re.I)
    if not m:
        return None
    return round(float(m.group(1)) * OZ_ML)


# ---- generic --------------------------------------------------------------------------------------

def start_millis(row: dict) -> int:
    stamp = datetime.strptime(row["Start"].strip(), "%Y-%m-%d %H:%M").replace(tzinfo=ZONE)
    return int(stamp.timestamp() * 1000)


def end_millis(row: dict) -> int | None:
    text = (row.get("End") or "").strip()
    if not text:
        return None
    stamp = datetime.strptime(text, "%Y-%m-%d %H:%M").replace(tzinfo=ZONE)
    return int(stamp.timestamp() * 1000)


def food_slug(name: str) -> str:
    """Mirror of domain/FoodSlug.kt so an imported food lands on the same id a retyped one would."""
    folded = unicodedata.normalize("NFD", name.strip())
    folded = "".join(c for c in folded if not unicodedata.combining(c))
    return re.sub(r"[^a-z0-9]+", "-", folded.lower()).strip("-")


def event_id(child_id: str, kind: str, started_at: int, salt: str = "") -> str:
    """Stable across re-runs, so re-importing does not duplicate the log."""
    return str(uuid.uuid5(uuid.NAMESPACE_URL, f"huckleberry:{child_id}:{kind}:{started_at}:{salt}"))


def events_from_csv(child_id: str, path: str) -> tuple[list[dict], Counter]:
    rows = list(csv.DictReader(open(path, encoding="utf-8-sig")))
    out: list[dict] = []
    counts: Counter = Counter()

    for index, row in enumerate(rows):
        kind = (row["Type"] or "").strip()
        started = start_millis(row)
        base = {
            "id": event_id(child_id, kind, started, str(index)),
            "childId": child_id,
            "startedAt": started,
            "createdBy": CREATED_BY,
            "updatedAt": IMPORTED_AT,
            "deleted": False,
        }
        if note_text := (row.get("Notes") or "").strip():
            base["note"] = note_text

        if kind == "Feed":
            amount = ounces_ml(row["End Condition"])
            if amount is None:
                note_problem(f"{child_id} {row['Start']}: feed with no volume ({row['End Condition']!r})")
                counts["feed:novolume"] += 1
            base |= {"type": "BOTTLE"}
            if amount is not None:
                base["amountMl"] = amount
            # Huckleberry's "Formula" is the feed kind, not a brand, so formulaBrand stays unset.
            out.append(base)
            counts["BOTTLE"] += 1

        elif kind == "Solids":
            foods = [f.strip() for f in (row["Start Condition"] or "").split(",") if f.strip()]
            ids = [food_slug(f) for f in foods if food_slug(f)]
            base |= {"type": "SOLIDS"}
            if ids:
                base["foodIdsCsv"] = ",".join(dict.fromkeys(ids))
            reaction_word = (row["End Condition"] or "").strip().lower()
            if reaction_word in REACTION:
                base["reaction"] = REACTION[reaction_word]
            elif reaction_word:
                # "HATED" has no enum member: the feeling is kept in the note rather than invented away.
                base["note"] = (base.get("note", "") + " " if base.get("note") else "") + \
                    reaction_word.capitalize()
                unmapped["solids:reaction-into-note"] += 1
            out.append(base)
            counts["SOLIDS"] += 1
            counts["foods_referenced"] += len(ids)

        elif kind == "Diaper":
            what = (row["End Condition"] or "").strip().lower()
            wet = "pee" in what
            dirty = "poo" in what
            if not wet and not dirty:
                # "Dry" is not representable: DiaperKind.of() requires wet or dirty, and the app refuses
                # to record a change that was neither. Reported, not fudged into WET.
                note_problem(f"{child_id} {row['Start']}: nappy with neither pee nor poo ({what!r}) — skipped")
                unmapped["diaper:dry-skipped"] += 1
                counts["DIAPER:skipped"] += 1
                continue
            base |= {"type": "DIAPER",
                     "diaperKind": "MIXED" if (wet and dirty) else ("WET" if wet else "DIRTY")}
            if re.search(r":(small|medium|large)", what):
                unmapped["diaper:volume-dropped"] += 1
            colour = (row.get("Duration") or "").strip().lower()
            if colour in COLOUR:
                base["diaperColoursCsv"] = COLOUR[colour]
            elif colour:
                note_problem(f"{child_id} {row['Start']}: nappy colour not understood ({colour!r})")
                unmapped["diaper:colour-unparsed"] += 1
            consistency = (row.get("Start Condition") or "").strip().lower()
            if consistency:
                if consistency in CONSISTENCY:
                    base["diaperConsistencyCsv"] = CONSISTENCY[consistency]
                    if consistency == "loose":
                        unmapped["diaper:loose->mushy"] += 1
                else:
                    note_problem(f"{child_id} {row['Start']}: consistency not understood ({consistency!r})")
                    unmapped["diaper:consistency-unparsed"] += 1
            out.append(base)
            counts["DIAPER"] += 1

        elif kind == "Growth":
            grams = weight_grams(row["Start Condition"])
            millimetres = length_mm(row["Start Location"])
            head = length_mm(row["End Condition"])
            base |= {"type": "GROWTH"}
            growth_raw[base["id"]] = " ".join(
                x.strip() for x in (row["Start Condition"], row["Start Location"], row["End Condition"]) if (x or "").strip()
            )
            if grams is not None:
                if not (MIN_WEIGHT_G <= grams <= MAX_WEIGHT_G):
                    note_problem(f"{child_id} {row['Start']}: weight {grams} g outside the app's guard")
                    unmapped["growth:weight-rejected"] += 1
                else:
                    base["weightGrams"] = grams
            if millimetres is not None:
                if not (MIN_LENGTH_MM <= millimetres <= MAX_LENGTH_MM):
                    note_problem(f"{child_id} {row['Start']}: length {millimetres} mm outside the app's guard")
                    unmapped["growth:length-rejected"] += 1
                else:
                    base["lengthMm"] = millimetres
            if head is not None:
                if not (MIN_LENGTH_MM <= head <= MAX_LENGTH_MM):
                    note_problem(f"{child_id} {row['Start']}: head {head} mm outside the app's guard")
                    unmapped["growth:head-rejected"] += 1
                else:
                    base["headMm"] = head
            day = datetime.fromtimestamp(started / 1000, ZONE).strftime("%Y-%m-%d")
            for field in ("weightGrams", "lengthMm", "headMm"):
                if (fixed := OVERRIDES.get((child_id, day, field))) is not None:
                    base[field] = fixed
                    unmapped[f"growth:override-{field}"] += 1
                if field in DROP_FIELDS.get((child_id, day), ()):
                    base.pop(field, None)
                    unmapped[f"growth:dropped-{field}"] += 1
            if "weightGrams" not in base and "lengthMm" not in base and "headMm" not in base:
                note_problem(f"{child_id} {row['Start']}: measurement with nothing readable — skipped")
                counts["GROWTH:skipped"] += 1
                continue
            out.append(base)
            counts["GROWTH"] += 1

        elif kind == "Sleep":
            ended = end_millis(row)
            if ended is None:
                note_problem(f"{child_id} {row['Start']}: sleep with no end — skipped")
                unmapped["sleep:open-skipped"] += 1
                counts["SLEEP:skipped"] += 1
                continue
            base |= {"type": "SLEEP", "endedAt": ended}
            # Mood/where are not in the schema; "under_10_minutes" is a real fact, so it rides in the note.
            extras = [w.replace("_", " ") for w in
                      (row.get("Start Condition"), row.get("Start Location"), row.get("End Condition"))
                      if (w or "").strip()]
            if extras:
                base["note"] = (base.get("note", "") + " " if base.get("note") else "") + \
                    "; ".join(dict.fromkeys(extras))
                unmapped["sleep:mood-where-into-note"] += 1
            out.append(base)
            counts["SLEEP"] += 1

        else:
            note_problem(f"{child_id} {row['Start']}: unknown type {kind!r} — skipped")
            unmapped[f"type:{kind or 'blank'}"] += 1

    return out, counts


# ---- report ---------------------------------------------------------------------------------------

def build(kai_csv: list[str] | None, levi_csv: list[str] | None) -> tuple[dict, str]:
    inputs = {"kai": kai_csv or [], "levi": levi_csv or []}
    children, all_events, per_child = [], [], {}
    foods: dict[str, dict] = {}

    for key, spec in CHILDREN.items():
        paths = inputs[key]
        if not paths:
            continue
        events, counts = [], Counter()
        for path in paths:
            batch, part = events_from_csv(spec["id"], path)
            # Same event reached twice (an export that overlaps a screenshot) keeps one copy.
            seen = {e["id"] for e in events}
            events.extend(e for e in batch if e["id"] not in seen)
            counts.update(part)
        per_child[spec["name"]] = (counts, events, ", ".join(paths))
        all_events.extend(events)
        children.append({
            "id": spec["id"], "name": spec["name"], "birthDateEpochDay": spec["birthDateEpochDay"],
            "gestationalAgeDays": spec["gestationalAgeDays"], "sex": spec["sex"],
            "useAdjustedAge": True, "sortOrder": spec["sortOrder"], "deleted": False,
            "updatedAt": int(datetime(2026, 10, 28, tzinfo=ZONE).timestamp() * 1000),
        })
        for event in events:
            for food_id in (event.get("foodIdsCsv") or "").split(","):
                if food_id and food_id not in foods:
                    foods[food_id] = {"id": food_id, "name": food_id.replace("-", " ").title(),
                                      "category": "Other", "custom": True}

    snapshot = {
        "app": "babylog",
        "schema": 1,
        "exportedAt": int(datetime(2026, 10, 28, tzinfo=ZONE).timestamp() * 1000),
        "children": children,
        "events": sorted(all_events, key=lambda e: e["startedAt"]),
        "foods": sorted(foods.values(), key=lambda f: f["id"]),
    }
    return snapshot, render_report(snapshot, per_child)


def render_report(snapshot: dict, per_child: dict) -> str:
    lines: list[str] = []
    add = lines.append
    add("# Huckleberry → BabyLog: what mapped\n")

    totals = Counter()
    for name, (counts, events, path) in per_child.items():
        add(f"## {name} — `{path}`\n")
        add(f"- events written: **{len(events)}**")
        for kind in ("BOTTLE", "SOLIDS", "DIAPER", "GROWTH", "SLEEP"):
            made = sum(1 for e in events if e["type"] == kind)
            if made:
                totals[kind] += made
                span = [e["startedAt"] for e in events if e["type"] == kind]
                lo = datetime.fromtimestamp(min(span) / 1000, ZONE).date()
                hi = datetime.fromtimestamp(max(span) / 1000, ZONE).date()
                add(f"  - {kind}: {made}  ({lo} → {hi})")
        for skipped, count in sorted(counts.items()):
            if skipped.endswith(":skipped") or skipped.endswith(":novolume"):
                add(f"  - ⚠ {skipped}: {count}")
        add("")

    add("## Totals\n")
    for kind, count in sorted(totals.items()):
        add(f"- {kind}: {count}")
    add(f"- children: {len(snapshot['children'])}")
    add(f"- custom foods created: {len(snapshot['foods'])}")
    add(f"- total events: {len(snapshot['events'])}")
    add("")

    add("## Field mapping in force\n")
    add("| Huckleberry | BabyLog | Note |")
    add("|---|---|---|")
    add("| `Feed` (Formula/Bottle, `6oz`) | `BOTTLE`, `amountMl` | oz → ml at 29.5735; brand unset, `Formula` is a kind not a brand |")
    add("| `Solids` foods list | `SOLIDS`, `foodIdsCsv` + custom foods | ids are `foodSlug()` of each name, as the app derives them |")
    add("| `Solids` `LOVED` / `ALLERGIC` | `reaction` | `LOVED_IT` / `ALLERGY_OR_SENSITIVITY` |")
    add("| `Solids` `HATED` | note | no enum member exists |")
    add("| `Diaper` `Pee` / `Poo` / `Both` | `DIAPER`, `WET`/`DIRTY`/`MIXED` | `:small|medium|large` volumes dropped |")
    add("| `Diaper` `Duration` = `yellow`/`green` | `diaperColoursCsv` | six literal colours |")
    add("| `Diaper` `Solid`/`Loose`/`Pebbles` | `SOLID`/`MUSHY`/`PEBBLES` | `Loose` has no exact member; owner ruled MUSHY |")
    add("| `Growth` `18.44lbs.oz` | `weightGrams` | **pounds.ounces** — 18 lb 4.4 oz; the app's own Oct 21 wording proves the dot separates units |")
    add("| `Growth` `2.4ft.in` / `71cm` / `18.11in` | `lengthMm` / `headMm` | feet.inches (2 ft 4 in = 71 cm), cm and inches all seen |")
    add("| `Sleep` Start/End | `startedAt`/`endedAt` | mood/where preserved in the note |")
    add("| `Notes` | `note` | verbatim |")
    add("| `Logged By` | `createdBy` = `huckleberry` | provenance, not a person |")
    add("")

    add("## Outstanding — needs your decision or is not representable\n")
    for kind, count in sorted(unmapped.items()):
        add(f"- `{kind}`: {count}")
    if problems:
        add("")
        add("### Row-level notes\n")
        for p in problems:
            add(f"- {p}")
    add("")
    add("## Growth series as converted (check these by eye)\n")
    add("| child | date | weight g | length mm | head mm | raw |")
    add("|---|---|---|---|---|---|")
    for event in snapshot["events"]:
        if event["type"] != "GROWTH":
            continue
        child = next(c["name"] for c in snapshot["children"] if c["id"] == event["childId"])
        when = datetime.fromtimestamp(event["startedAt"] / 1000, ZONE).strftime("%Y-%m-%d")
        add(f"| {child} | {when} | {event.get('weightGrams', '—')} | {event.get('lengthMm', '—')} | "
            f"{event.get('headMm', '—')} | {growth_raw.get(event['id'], '')} |")
    add("")
    return "\n".join(lines)


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--kai", action="append", help="a CSV for Kai; repeat for extra files")
    parser.add_argument("--levi", action="append", help="a CSV for Levi; repeat for extra files")
    parser.add_argument("--out", default="babylog-import.json")
    parser.add_argument("--report", default="babylog-import-report.md")
    args = parser.parse_args()

    snapshot, report = build(args.kai, args.levi)
    with open(args.out, "w", encoding="utf-8", newline="\n") as handle:
        json.dump(snapshot, handle, indent=2, ensure_ascii=False)
    with open(args.report, "w", encoding="utf-8", newline="\n") as handle:
        handle.write(report)
    print(report)


if __name__ == "__main__":
    main()
