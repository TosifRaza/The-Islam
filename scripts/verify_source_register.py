#!/usr/bin/env python3
"""Verify that the Phase 2 source register CSV and Markdown are reconciled.

Checks (v0.1.1 corrective action for the owner's M1 review, item 4):
  1. CSV has exactly 19 unique records SR-01..SR-19.
  2. Every CSV status falls into exactly one bucket: Verified / Conditional / Unverified.
  3. Every SR id appears in the Markdown tables with EXACTLY the same status
     string as the CSV (no divergence between the two register files).
  4. The Markdown summary table's counts and ID lists match the CSV buckets.
  5. Gate column: every content row is bound to OD-05 / G-3 except the two
     non-content rows (SR-17 library, SR-19 reference data).
  6. No row anywhere claims an import has happened (OD-05 / G-3 remains closed).
"""
import csv
import re
import sys
from pathlib import Path

BASE = Path(__file__).resolve().parent.parent / "docs-m1"
csv_path = BASE / "phase2-source-register.csv"
md_path = BASE / "phase2-source-register.md"

failures = []


def check(label, ok, detail=""):
    print(f"{'PASS' if ok else 'FAIL'}  {label}" + (f" — {detail}" if detail else ""))
    if not ok:
        failures.append(label)


rows = list(csv.DictReader(csv_path.open(encoding="utf-8")))
ids = [r["id"] for r in rows]
check("CSV has 19 records", len(rows) == 19, f"got {len(rows)}")
check("CSV ids unique & sequential SR-01..SR-19",
      ids == [f"SR-{i:02d}" for i in range(1, 20)], f"got {ids}")

bucket_of = {}
for r in rows:
    st = r["license_status"].strip()
    if st.startswith("Verified"):
        bucket_of[r["id"]] = "Verified"
    elif st.startswith("Conditional"):
        bucket_of[r["id"]] = "Conditional"
    elif st.startswith("Unverified"):
        bucket_of[r["id"]] = "Unverified"
    else:
        bucket_of[r["id"]] = None
bad = {k: v for k, v in bucket_of.items() if v is None}
check("All 19 CSV statuses map to a known bucket", not bad, str(bad))

counts = {b: sorted([i for i in ids if bucket_of[i] == b]) for b in ("Verified", "Conditional", "Unverified")}
check("Buckets sum to 19", sum(len(v) for v in counts.values()) == 19,
      f"Verified={len(counts['Verified'])} Conditional={len(counts['Conditional'])} Unverified={len(counts['Unverified'])}")

# Gate column
expected_gate = {i: "none (library)" if i == "SR-17" else ("none" if i == "SR-19" else "OD-05 / G-3") for i in ids}
gate_bad = {r["id"]: r["gate"] for r in rows if r["gate"].strip() != expected_gate[r["id"]]}
check("Gate column: content rows bound to OD-05/G-3; SR-17/SR-19 non-content", not gate_bad, str(gate_bad))

# Markdown: extract status per id from section tables
md = md_path.read_text(encoding="utf-8")
md_status = {}
for line in md.splitlines():
    if line.startswith("| SR-"):
        cells = [c.strip() for c in line.split("|")]
        # cells[0]='' , cells[1]=id, ..., cells[4]=status
        if len(cells) >= 5:
            sid = cells[1]
            status = cells[4].strip().strip("*").strip()
            md_status[sid] = status
missing = [i for i in ids if i not in md_status]
check("All 19 ids present in Markdown tables", not missing, f"missing {missing}")
diverge = {i: (rows[ids.index(i)]["license_status"].strip(), md_status.get(i))
           for i in ids if md_status.get(i) != rows[ids.index(i)]["license_status"].strip()}
check("Markdown status strings identical to CSV for all 19 records", not diverge, str(diverge))

# Markdown summary table
summary = md.split("## 10.")[1] if "## 10." in md else ""
sum_counts = {}
for m in re.finditer(r"\|\s*(Verified|Conditional|Unverified)\s*\|\s*(\d+)\s*\|([^|]*)\|", summary):
    sum_counts[m.group(1)] = (int(m.group(2)),
                              sorted(re.findall(r"SR-\d{2}", m.group(3))))
check("Summary table covers all three buckets", len(sum_counts) == 3, str(list(sum_counts)))
for b, (n, lst) in sum_counts.items():
    check(f"Summary {b} count matches CSV", n == len(counts[b]), f"table={n} csv={len(counts[b])}")
    check(f"Summary {b} id list matches CSV", lst == counts[b], f"table={lst} csv={counts[b]}")
total = re.search(r"\|\s*\*{0,2}Total\*{0,2}\s*\|\s*\*{0,2}19\*{0,2}\s*\|", summary)
check("Summary total row says 19", bool(total))

# No import anywhere
import_claims = re.findall(r"(?i)(imported|imported content|content was imported)", md)
claims_ok = all("No content has been imported" in md or "No content was imported" in md or True for _ in [0])
check("Register states no content imported", ("No content has been imported" in md) and not re.search(r"content (was|has been) imported", md.replace("No content has been imported", "").replace("no content was imported", ""), re.I))

print()
if failures:
    print(f"SOURCE REGISTER CHECK: FAILED ({len(failures)} failures: {failures})")
    sys.exit(1)
print("SOURCE REGISTER CHECK: ALL PASS — CSV and Markdown reconciled 1:1 (19 records)")
