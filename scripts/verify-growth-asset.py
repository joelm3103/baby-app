#!/usr/bin/env python3
"""Check the bundled WHO asset against WHO's OWN published tables.

`build-growth-assets.py` proves the asset matches what the research doc recorded, and the research doc
recorded a mirror (`ki-tools/growthstandards`). Neither of those is the publisher. This script closes
that gap: it downloads WHO's expanded z-score tables from who.int and checks, row by row, that

  * the L, M and S parameters in the bundled asset are WHO's parameters, and
  * the values the app derives from them reproduce WHO's printed SD columns.

That second check is the one that matters: it tests the z-score formula itself against the publisher's
own arithmetic, at every age where WHO prints a value, rather than at a handful of spot values.

    python scripts/verify-growth-asset.py

Exit code is non-zero if any measure fails, so this is usable as a gate rather than a report nobody
reads. Measures whose WHO filename could not be resolved are printed as NOT VERIFIED rather than
silently skipped - an unverified measure must not look like a verified one.
"""

from __future__ import annotations

import json
import math
import re
import sys
import tempfile
import urllib.request
import zipfile
from pathlib import Path

ASSET = Path(__file__).resolve().parent.parent / "app/src/main/assets/growth/who-igrowup.json"
BASE = "https://cdn.who.int/media/docs/default-source/child-growth/child-growth-standards/indicators"

# measure -> (WHO url template, axis is length rather than days, SD tolerance)
#
# The tolerance is the publisher's OWN rounding: WHO prints its SD columns to four significant figures,
# which is ~0.005 kg on a 3 kg baby and ~0.06 on a circumference or BMI. A larger gap is drift.
#
# The two odd-looking paths are WHO's, not typos: length/height-for-age lives under `expandable-tables`
# (the only measure spelled that way), and weight-for-length's file is `...-table.xlsx` (singular).
MEASURES = {
    "WEIGHT_FOR_AGE": (
        BASE + "/weight-for-age/expanded-tables/wfa-{sex}-zscore-expanded-tables.xlsx",
        False,
        0.006,
    ),
    "LENGTH_HEIGHT_FOR_AGE": (
        BASE + "/length-height-for-age/expandable-tables/lhfa-{sex}-zscore-expanded-tables.xlsx",
        False,
        0.06,
    ),
    "HEAD_CIRCUMFERENCE_FOR_AGE": (
        BASE + "/head-circumference-for-age/expanded-tables/hcfa-{sex}-zscore-expanded-tables.xlsx",
        False,
        0.06,
    ),
    "BMI_FOR_AGE": (
        BASE + "/body-mass-index-for-age/expanded-tables/bfa-{sex}-zscore-expanded-tables.xlsx",
        False,
        0.06,
    ),
    "WEIGHT_FOR_LENGTH_HEIGHT": (
        BASE + "/weight-for-length-height/expanded-tables/wfl-{sex}-zscore-expanded-table.xlsx",
        True,
        0.06,
    ),
}

Z_ORDER = [-4.0, -3.0, -2.0, -1.0, 0.0, 1.0, 2.0, 3.0]

# z = -4 is where the chart never goes (its curves run from the 2nd to the 98th percentile, |z| <= 2.06),
# and where WHO's own printed columns stop agreeing with WHO's own printed L/M/S, because the transform
# amplifies the rounding of the parameters at the extreme tail. Measured and reported here, but not part
# of the gate - the gate's band is what the app actually derives values from.
TAIL_Z = [-4.0]

USER_AGENT = (
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
    "(KHTML, like Gecko) Chrome/131.0 Safari/537.36"
)


def value_at_z(z: float, l: float, m: float, s: float) -> float:
    """The app's inverse LMS transform, reimplemented here so it fails independently."""
    if abs(l) < 1e-9:
        return m * math.exp(s * z)
    return m * (1.0 + l * s * z) ** (1.0 / l)


def load_asset() -> dict:
    return json.loads(ASSET.read_text(encoding="utf-8"))


def fetch_xlsx(url: str, dest: Path) -> None:
    # WHO's CDN answers 403 to a bare urllib user agent, so a browser one is sent. This is a public
    # download link, not a scrape.
    request = urllib.request.Request(url, headers={"User-Agent": USER_AGENT})
    with urllib.request.urlopen(request, timeout=120) as response:
        if response.status != 200:
            raise SystemExit(f"FAIL {url}: HTTP {response.status}")
        dest.write_bytes(response.read())


def parse_xlsx(path: Path) -> list[list[float]]:
    """Minimal xlsx reader: sheet1's rows as floats, shared strings resolved.

    Deliberately dependency-free - this repo has no Python requirements file, and a verification script
    that needs installing something is a script that does not get run.
    """
    with zipfile.ZipFile(path) as archive:
        shared = re.findall(
            r"<si>(.*?)</si>",
            archive.read("xl/sharedStrings.xml").decode("utf-8", "ignore"),
            re.S,
        )
        strings = ["".join(re.findall(r"<t[^>]*>(.*?)</t>", item, re.S)) for item in shared]
        sheet = archive.read("xl/worksheets/sheet1.xml").decode("utf-8", "ignore")

    out: list[list[float]] = []
    for _, body in re.findall(r'<row[^>]*r="(\d+)"[^>]*>(.*?)</row>', sheet, re.S):
        values: list[float] = []
        for _, attrs, raw in re.findall(r'<c r="([A-Z]+)\d+"([^>]*)>(?:<v>(.*?)</v>)?', body):
            if raw is None:
                values.append(float("nan"))
            elif 't="s"' in attrs:
                values.append(float("nan"))  # a label cell, e.g. the header "Day"
            else:
                values.append(float(raw))
        # The interesting columns are A..L: axis, L, M, S, SD4neg..SD3.
        out.append(values[:12])
    return out


def check(measure: str, url_template: str, length_axis: bool, tolerance: float, asset: dict) -> bool:
    subject = "length (cm)" if length_axis else "day"
    print(f"\n{measure}  [WHO expanded table, z = -4..+3, axis = {subject}]")

    ok = True
    for sex_code, sex_name in (("1", "boys"), ("2", "girls")):
        url = url_template.format(sex=sex_name)
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp) / "who.xlsx"
            try:
                fetch_xlsx(url, path)
                rows = parse_xlsx(path)
            except Exception as error:  # noqa: BLE001 - any failure here is a verification failure
                print(f"  [{sex_name}] NOT VERIFIED: {error}")
                ok = False
                continue

        published = {}
        for row in rows[1:]:
            if len(row) < 12 or math.isnan(row[0]):
                continue
            axis = round(row[0], 1)
            published[axis] = row[1:12]

        ours = {
            round(point[0], 1): point[1:4]
            for point in asset["measures"][measure][sex_code]
        }

        shared_axes = sorted(set(published) & set(ours))
        if not shared_axes:
            print(f"  [{sex_name}] FAIL: no overlapping axes ({len(published)} published, {len(ours)} bundled)")
            ok = False
            continue

        max_param = 0.0
        max_sd = 0.0
        max_tail = 0.0
        worst = None
        for axis in shared_axes:
            l, m, s = ours[axis]
            pub_l, pub_m, pub_s = published[axis][0:3]
            max_param = max(max_param, abs(l - pub_l), abs(m - pub_m), abs(s - pub_s))
            for index, z in enumerate(Z_ORDER):
                # Columns are L, M, S then SD4neg..SD3; z = -4 lands at index 3.
                printed = published[axis][3 + index]
                derived = value_at_z(z, l, m, s)
                diff = abs(printed - derived)
                if z in TAIL_Z:
                    max_tail = max(max_tail, diff)
                    continue
                if diff > max_sd:
                    max_sd = diff
                    worst = (axis, z, printed, derived)

        sd_ok = max_sd <= tolerance
        param_ok = max_param < 1e-9
        ok = ok and sd_ok and param_ok
        flag = "OK  " if (sd_ok and param_ok) else "FAIL"
        print(
            f"  [{flag}] {sex_name:5s} rows compared {len(shared_axes):4d}  "
            f"max |dLMS| {max_param:.2e}  max |dSD| z=-3..3 {max_sd:.4f} (tol {tolerance})  "
            f"z=-4 tail {max_tail:.4f}",
        )
        if worst and not sd_ok:
            axis, z, printed, derived = worst
            print(f"         worst: {subject} {axis}, z={z:+.0f}, printed {printed}, derived {derived:.4f}")
        if not param_ok:
            print("         L/M/S disagree with WHO - the asset is not WHO's parameters")
    return ok


def main() -> int:
    asset = load_asset()
    print(f"asset {ASSET.name}: version {asset['version']}, retrieved {asset['retrievedAt']}")
    print(f"WHO tables from {BASE}")

    results = {
        measure: check(measure, *spec, asset)
        for measure, spec in MEASURES.items()
    }

    print()
    failed = [measure for measure, passed in results.items() if not passed]
    for measure, passed in results.items():
        print(f"  {'PASS' if passed else 'FAIL'}  {measure}")
    print(
        "\nGate band: z = -3..+3, the range the app derives percentiles and curves from "
        "(its curves run from the 2nd to the 98th percentile, |z| <= 2.06).",
    )
    print("z = -4 is measured and printed above, but sits outside that band and outside the chart.")
    return 1 if failed else 0


if __name__ == "__main__":
    sys.exit(main())
