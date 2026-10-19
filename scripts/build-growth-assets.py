#!/usr/bin/env python3
"""Generate the bundled WHO growth reference asset.

The app never fetches growth data at runtime: the LMS tables ship inside the APK, and the identity
block next to them records where each byte came from. This script is the auditable half of that
arrangement - it is committed so the asset can be regenerated and re-verified rather than trusted.

    python scripts/build-growth-assets.py

It downloads WHO's `igrowup` tables from the MIT-licensed `ki-tools/growthstandards` mirror, verifies
each file's sha256 against the values recorded on 2026-10-12 (and inlined below), asserts
the row counts and axis shape, and writes `app/src/main/assets/growth/who-igrowup.json`.

Nothing about the percentile maths happens here. This is transcription with a checksum, deliberately:
the LMS parameters are published data, and the z-score formula lives in `domain/Percentiles.kt`.

WHO has not revised these standards since 2006, which is why the asset is bundled rather than fetched
on first run. The checksums below were verified on 2026-10-12 by downloading and hashing the files.
"""

from __future__ import annotations

import hashlib
import json
import sys
import urllib.request
from datetime import date
from pathlib import Path

BASE_URL = "https://raw.githubusercontent.com/ki-tools/growthstandards/master/data_preprocess/data/igrowup_R/"
OUT_PATH = Path(__file__).resolve().parent.parent / "app/src/main/assets/growth/who-igrowup.json"

# Dataset identity. `publishedAt` is WHO's publication date of the Child Growth Standards; the version
# string is ours, and `retrievedAt` is the honest date to show a parent - WHO's own revision date has
# not moved since 2006.
DATASET_VERSION = "who-2006.2026.10"
PUBLISHED_AT = "2006-04-27"
CITATION = "WHO Child Growth Standards (2006), igrowup LMS tables"

# Order matters: it is the order the parser and the UI read the measures in.
MEASURES = [
    # (key, source file, axis, rows per sex, sex-code convention)
    ("WEIGHT_FOR_AGE", "weianthro.txt", "AGE_DAYS", 1857),
    ("LENGTH_HEIGHT_FOR_AGE", "lenanthro.txt", "AGE_DAYS", 1857),
    ("HEAD_CIRCUMFERENCE_FOR_AGE", "hcanthro.txt", "AGE_DAYS", 1857),
    ("BMI_FOR_AGE", "bmianthro.txt", "AGE_DAYS", 1857),
    ("WEIGHT_FOR_LENGTH_HEIGHT", "wflanthro.txt", "LENGTH_CM", 651),
]

# sha256 prefixes verified on 2026-10-12 by downloading and hashing the files (research doc).
EXPECTED_SHA_PREFIX = {
    "weianthro.txt": "2e2e5c5c1d447f50",
    "lenanthro.txt": "7cf462b2a4f8c539",
    "hcanthro.txt": "16ee70120c9c77de",
    "wflanthro.txt": "ad470cf41b147bd2",
    "bmianthro.txt": "e858a5b67d612b93",
}

# WHO's convention in these files: 1 = boys, 2 = girls. Both are bundled - the app is used by two boys,
# but a dataset that only describes boys would be structurally wrong.
SEX_CODES = ("1", "2")


def fetch(name: str) -> bytes:
    url = BASE_URL + name
    with urllib.request.urlopen(url, timeout=60) as response:
        if response.status != 200:
            raise SystemExit(f"FAIL {name}: HTTP {response.status}")
        return response.read()


def parse(blob: bytes, name: str, axis: str) -> dict[str, list[list[float]]]:
    """Parse a tab-separated `sex, <axis>, l, m, s` table into per-sex row arrays.

    The axis column is named `age` in the age-keyed tables and `length` in the weight-for-length one,
    which is the only structural difference between them. Later columns (WHO's `loh` / `lorh`
    measurement-type flags) are ignored: they select length versus height at the 24-month transition,
    which changes the chart's label, not the maths.
    """
    expected_axis_column = "age" if axis == "AGE_DAYS" else "length"
    text = blob.decode("utf-8-sig", errors="replace")
    lines = [line for line in text.splitlines() if line.strip()]
    header = lines[0].split("\t")
    if header[:5] != ["sex", expected_axis_column, "l", "m", "s"]:
        raise SystemExit(
            f"FAIL {name}: header is {header}, expected sex/{expected_axis_column}/l/m/s first",
        )

    by_sex: dict[str, list[list[float]]] = {code: [] for code in SEX_CODES}
    for line in lines[1:]:
        parts = line.split("\t")
        sex = parts[0].strip()
        if sex not in by_sex:
            raise SystemExit(f"FAIL {name}: unexpected sex code {sex!r}")
        axis_value, l, m, s = (float(parts[i]) for i in range(1, 5))
        by_sex[sex].append([axis_value, l, m, s])
    return by_sex


def check_axis(name: str, axis: str, by_sex: dict[str, list[list[float]]], per_sex: int) -> None:
    """Assert the shape the app's lookup logic depends on.

    These are the two facts that would silently produce a wrong percentile rather than an error: the
    age-keyed tables are DAILY from 0 to 1856 (so day == row index, no interpolation), and the
    weight-for-length table is keyed by centimetres.
    """
    for sex, rows in by_sex.items():
        if len(rows) != per_sex:
            raise SystemExit(f"FAIL {name}: sex {sex} has {len(rows)} rows, expected {per_sex}")
        ages = [row[0] for row in rows]
        if axis == "AGE_DAYS":
            if ages[0] != 0 or ages[-1] != 1856 or len(set(ages)) != len(ages):
                raise SystemExit(f"FAIL {name}: sex {sex} is not daily 0..1856 (got {ages[0]}..{ages[-1]})")
            if any(int(a) != i for i, a in enumerate(ages)):
                raise SystemExit(f"FAIL {name}: sex {sex} age does not equal row index")
        else:
            steps = {round(ages[i + 1] - ages[i], 4) for i in range(len(ages) - 1)}
            if steps != {0.1}:
                raise SystemExit(f"FAIL {name}: sex {sex} steps are {sorted(steps)}, expected 0.1 cm")
            if sex == SEX_CODES[0]:
                print(f"    NOTE {name}: axis is {ages[0]:.1f}-{ages[-1]:.1f} cm in 0.1 cm steps")


def main() -> int:
    retrieved_at = date.today().isoformat()
    files: list[dict[str, object]] = []
    measures: dict[str, dict[str, list[list[float]]]] = {}

    print(f"WHO igrowup -> {OUT_PATH.name} in {OUT_PATH.parent}")
    print(f"dataset {DATASET_VERSION}  retrieved {retrieved_at}\n")

    for key, name, axis, per_sex in MEASURES:
        blob = fetch(name)
        digest = hashlib.sha256(blob).hexdigest()
        prefix = digest[:16]
        expected = EXPECTED_SHA_PREFIX[name]
        status = "OK  " if prefix == expected else "DRIFT"
        if prefix != expected:
            raise SystemExit(
                f"FAIL {name}: sha256 {prefix} != research-recorded {expected}. "
                "The upstream file changed - re-verify before regenerating, do not just accept it."
            )

        by_sex = parse(blob, name, axis)
        check_axis(name, axis, by_sex, per_sex)
        measures[key] = by_sex

        total = sum(len(rows) for rows in by_sex.values())
        print(f"  [{status}] {name:16s} {key:26s} {axis:10s} rows={total:5d} sha256={prefix}")
        files.append(
            {
                "name": name,
                "measure": key,
                "axis": axis,
                "rowsPerSex": per_sex,
                "sha256": digest,
                "url": BASE_URL + name,
            },
        )

    asset = {
        "dataset": "WHO Child Growth Standards",
        "version": DATASET_VERSION,
        "publishedAt": PUBLISHED_AT,
        "retrievedAt": retrieved_at,
        "citation": CITATION,
        "license": "WHO Child Growth Standards; source files mirrored by ki-tools/growthstandards (MIT)",
        "sexCodes": {"1": "boys", "2": "girls"},
        "measures": measures,
        "files": files,
    }

    OUT_PATH.parent.mkdir(parents=True, exist_ok=True)
    payload = json.dumps(asset, separators=(",", ":"), sort_keys=False)
    OUT_PATH.write_text(payload, encoding="utf-8")

    asset_digest = hashlib.sha256(payload.encode("utf-8")).hexdigest()
    print()
    print(f"wrote {OUT_PATH}  {len(payload):,} bytes")
    print(f"asset sha256 {asset_digest}")
    for record in files:
        print(f"  {record['measure']:26s} {record['sha256']}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
