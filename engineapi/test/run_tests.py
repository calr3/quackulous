#!/usr/bin/env python3
"""Tests for the engine API, through quackleapi_tool.

usage: run_tests.py <path to quackleapi_tool>

Rebuilds the csw24 lexicon from a word list dumped from Quackle's shipped DAWG
and checks both files against what Quackle's own tools produce from the same
words, then checks analysis and input validation.
"""

import hashlib
import subprocess
import sys
import tempfile
from pathlib import Path

REPO = Path(__file__).resolve().parents[2]
DATA = REPO / "data"
# sha256 of the GADDAG that makegaddag writes from the same csw24 word list.
CSW24_GADDAG_SHA256 = "1c68e5896dbca258d55c81c46b830d7030cc6a90d0fc76939ac6aadde0054758"

failures = 0


def check(name, condition, detail=""):
    global failures
    print(f"{'PASS' if condition else 'FAIL'}  {name}" + (f": {detail}" if detail and not condition else ""))
    if not condition:
        failures += 1


def run(tool, *args):
    return subprocess.run([str(tool), *map(str, args)], capture_output=True, text=True, timeout=600)


def main():
    tool = Path(sys.argv[1]).resolve()
    with tempfile.TemporaryDirectory(prefix="quackleapi_") as tmp:
        data = Path(tmp)
        (data / "alphabets").mkdir()
        (data / "alphabets" / "lexulous.quackle_alphabet").write_bytes((DATA / "alphabets" / "lexulous.quackle_alphabet").read_bytes())
        (data / "strategy").symlink_to(DATA / "strategy")

        words = data / "csw24.txt"
        dump = run(tool, "dump", data, DATA / "lexica" / "csw24.dawg", words)
        check("dump the shipped csw24 DAWG", dump.returncode == 0 and words.exists(), dump.stderr)

        build = run(tool, "build", data, words, "csw24")
        check("build csw24", build.returncode == 0, build.stderr)
        check("build report", "words 280887 duplicates 0 unencodable 0 hash 7f0e9ef8fde8d6ef986acb7de33b1c98" in build.stdout,
              build.stdout)
        check("DAWG matches the shipped file", (data / "lexica" / "csw24.dawg").read_bytes() == (DATA / "lexica" / "csw24.dawg").read_bytes())
        gaddag_hash = hashlib.sha256((data / "lexica" / "csw24.gaddag").read_bytes()).hexdigest()
        check("GADDAG matches makegaddag", gaddag_hash == CSW24_GADDAG_SHA256, gaddag_hash)

        opening = run(tool, "analyze", data, "csw24", "ABDEIMRR")
        check("opening move", opening.stdout.startswith("8H IMBARRED score 140 equity 140 leave -"), opening.stdout + opening.stderr)

        board = data / "board.txt"
        rows = ["." * 15 for _ in range(15)]
        rows[7] = "......." + "IMBARRED"
        for i, letter in enumerate("OXEN"):
            rows[8 + i] = "." * 9 + letter + "." * 5
        board.write_text("\n".join(rows) + "\n")
        middle = run(tool, "analyze", data, "csw24", "AEINST?Q", board, "--seed=3")
        check("mid-game move", middle.stdout.startswith("K8 ANTIQuES score 118"), middle.stdout + middle.stderr)

        simulated = run(tool, "analyze", data, "csw24", "AEINST?Q", board, "--seed=3", "--sim=20")
        lines = simulated.stdout.splitlines()
        check("simulation reports wins", simulated.returncode == 0 and len(lines) == 10 and all(" over 20" in l for l in lines),
              simulated.stdout + simulated.stderr)

        too_many = run(tool, "analyze", data, "csw24", "ZZ")
        check("rack with more tiles than the set", too_many.returncode == 1 and "use 2 of Z, but the set only has 1" in too_many.stderr,
              too_many.stderr)
        bad_rack = run(tool, "analyze", data, "csw24", "AB3")
        check("rack with a non-tile", bad_rack.returncode == 1 and "isn't a tile" in bad_rack.stderr, bad_rack.stderr)

        messy = data / "messy.txt"
        messy.write_text("# a comment line\n\naa\nAA\nQI\ta Chinese life force\nR2D2\nzzz9\nCAFÉ\n  za  \n")
        report = run(tool, "build", data, messy, "messy")
        check("messy word list", "words 3 duplicates 1 unencodable 3" in report.stdout, report.stdout + report.stderr)

        empty = data / "empty.txt"
        empty.write_text("# nothing here\n123\n")
        nothing = run(tool, "build", data, empty, "empty")
        check("word list with no words", nothing.returncode == 1 and "no words" in nothing.stderr, nothing.stderr)

    print(f"\n{'all passed' if failures == 0 else f'{failures} failed'}")
    return 1 if failures else 0


if __name__ == "__main__":
    sys.exit(main())
