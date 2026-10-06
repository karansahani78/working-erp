#!/usr/bin/env python3
"""Check every hand-written SQL string against the real schema.

A query that parses is not a query that runs: the earlier column check passed while the
admissions tile asked for a column that had never existed. PostgreSQL's PREPARE resolves
names at prepare time, so replacing the named parameters with typed NULLs and preparing each
query is a real name-resolution check rather than a grammar one.
"""
import re
import subprocess
import sys
from pathlib import Path

JAVA = Path(sys.argv[1])
DB = ["psql", "-h", "localhost", "-U", "postgres", "-d", "erp_test", "-v", "ON_ERROR_STOP=1",
      "-t", "-A"]

# :name -> a typed NULL of the kind the query is likely comparing against.
TYPES = {
    "from": "::date", "to": "::date", "today": "::date", "month": "::date",
    "since": "::date", "until": "::date", "on": "::date",
    "limit": "::int", "offset": "::int",
}


def sql_strings(text):
    """Every run of text between quotes that looks like a query."""
    out = []
    for match in re.finditer(r'"""(.*?)"""|"((?:[^"\\]|\\.)*)"', text, re.S):
        raw = match.group(1) if match.group(1) is not None else match.group(2)
        if not raw:
            continue
        candidate = " ".join(raw.split())
        # Only a string that opens with a SQL verb is a query. Prose such as "measured from the
        # enrolment date" also contains " from ", and used to be reported as a broken query.
        if candidate.lower().startswith(("select ", "insert ", "update ", "delete ", "with ")):
            out.append(candidate)
    return out


def prepare(sql):
    params = set(re.findall(r":(\w+)", sql))
    for param in sorted(params, key=len, reverse=True):
        sql = re.sub(rf"(?<![:\w]):{param}\b", f"NULL{TYPES.get(param, '::text')}", sql)
    script = f"PREPARE schema_probe AS {sql};"
    result = subprocess.run(DB + ["-c", script], capture_output=True, text=True)
    return result.returncode == 0, (result.stderr or "").strip().splitlines()[:3]


def main():
    source = JAVA.read_text()
    queries = sql_strings(source)
    failures = 0
    # A query assembled with String.format cannot be resolved before its placeholders are filled,
    # so it is reported as skipped rather than quietly counted as a pass.
    templates = [sql for sql in queries if "%s" in sql]
    queries = [sql for sql in queries if "%s" not in sql]
    for sql in templates:
        print(f"SKIP (template, checked where it is formatted): {sql[:90]}")
    for sql in queries:
        ok, error = prepare(sql)
        if not ok:
            failures += 1
            print(f"FAIL: {sql[:110]}")
            for line in error:
                print(f"      {line}")
    print(f"{len(queries) - failures}/{len(queries)} queries resolve against the schema"
          f" ({len(templates)} templates skipped)")
    return 1 if failures else 0


if __name__ == "__main__":
    sys.exit(main())