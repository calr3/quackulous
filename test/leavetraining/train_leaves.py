#!/usr/bin/env python3
"""Train superleaves (leave values) by repeated self-play.

Each iteration:
  1. runs `libquackle_test --mode=leavegen` in --jobs parallel processes, each
     self-playing its share of --games with the current leave values and
     recording every kept leave with its owner's next score;
  2. runs `libquackle_test --mode=leavefit` over all of their statistics to fit
     a value for every possible leave and write a superleaves file;
  3. installs that file so the next iteration plays with it.

Everything lives under --work, one directory per iteration. A finished
iteration (one with a superleaves file) is never redone, so an interrupted run
picks up where it stopped when started again with the same arguments.

See README.md in this directory for how long a run takes and how to install
the result.
"""

import argparse
import os
import re
import shutil
import subprocess
import sys
import time
from pathlib import Path

SCRIPT_DIR = Path(__file__).resolve().parent
sys.dont_write_bytecode = True  # keep __pycache__ out of test/regression
sys.path.insert(0, str(SCRIPT_DIR.parent / "regression"))
import run_regression as rr  # noqa: E402  (shared binary/data/gaddag helpers)

ALPHABETS = {"lexulous": "lexulous", "scrabble": "english"}


def strategy_dir_name(rules, lexicon):
    """The strategy directory the engine reads first for these rules."""
    return lexicon if rules == "scrabble" else f"{rules}_{lexicon}"


def prepare_work(work, binary, lexicon, alphabet):
    """Lay out <work>/data -> repo data and <work>/run, with a gaddag if needed."""
    work.mkdir(parents=True, exist_ok=True)
    if not (work / "data").exists():
        rr.link_data_dir(work / "data")
    run = work / "run"
    run.mkdir(exist_ok=True)

    gaddag = run / "lexica" / f"{lexicon}.gaddag"
    if not gaddag.exists():
        built = rr.build_gaddag(binary, lexicon, alphabet, work)
        if built:
            gaddag.parent.mkdir(exist_ok=True)
            shutil.copyfile(built, gaddag)
        elif not (rr.DATA_DIR / "lexica" / f"{lexicon}.gaddag").exists():
            print(f"warning: no gaddag for {lexicon}; games will be much slower", file=sys.stderr)
    return run


def run_generation(binary, run, iteration_dir, args, iteration):
    """Run the leavegen jobs; return their stats files and a summary line."""
    jobs = min(args.jobs, args.games)
    shares = [args.games // jobs + (1 if j < args.games % jobs else 0) for j in range(jobs)]
    procs = []
    for j, games in enumerate(shares):
        seed = args.seed * 1_000_000 + iteration * 1_000 + j
        stats = iteration_dir / f"stats-{j:03d}.txt"
        log = open(iteration_dir / f"leavegen-{j:03d}.log", "w")
        argv = [str(binary), "--mode=leavegen", f"--rules={args.rules}", f"--lexicon={args.lexicon}",
                f"--seed={seed}", f"--repetitions={games}", f"--out={stats}"]
        procs.append((subprocess.Popen(argv, cwd=run, stdout=log, stderr=subprocess.STDOUT,
                                       env=rr.child_env(binary)), log, stats))

    try:
        for proc, log, _ in procs:
            proc.wait()
            log.close()
    except KeyboardInterrupt:
        for proc, _, _ in procs:
            proc.kill()
        raise

    games = turns = observations = 0
    points = 0.0
    for j, (proc, _, stats) in enumerate(procs):
        text = (iteration_dir / f"leavegen-{j:03d}.log").read_text(errors="replace")
        m = re.search(r"leavegen: (\d+) games, (\d+) turns, (\d+) observations, \d+ distinct leaves, ([\d.]+) points", text)
        if proc.returncode != 0 or not stats.exists() or not m:
            sys.exit(f"leavegen job {j} failed; see {iteration_dir / f'leavegen-{j:03d}.log'}")
        games += int(m.group(1))
        turns += int(m.group(2))
        observations += int(m.group(3))
        points += int(m.group(2)) * float(m.group(4))
    summary = f"{games} games, {observations} observations, {points / max(turns, 1):.2f} points per turn"
    return [stats for _, _, stats in procs], summary


def run_fit(binary, run, iteration_dir, stats_files, args):
    """Run leavefit; return the finished superleaves path and leavefit's output."""
    partial = iteration_dir / "superleaves.partial"
    argv = [str(binary), "--mode=leavefit", f"--rules={args.rules}", f"--lexicon={args.lexicon}",
            f"--out={partial}", f"--prior-sd={args.prior_sd}"] + [f"--stats={s}" for s in stats_files]
    fit = subprocess.run(argv, cwd=run, capture_output=True, text=True, env=rr.child_env(binary))
    (iteration_dir / "leavefit.log").write_text(fit.stdout + fit.stderr)
    if fit.returncode != 0 or "wrote " not in fit.stdout or not partial.exists():
        sys.exit(f"leavefit failed; see {iteration_dir / 'leavefit.log'}")
    final = iteration_dir / "superleaves"
    partial.replace(final)
    return final, fit.stdout


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--bin", help="libquackle_test, or a build directory containing test/libquackle_test")
    parser.add_argument("--work", default="leavetraining-work", help="working directory (default: %(default)s)")
    parser.add_argument("--rules", default="lexulous", choices=sorted(ALPHABETS), help="default: %(default)s")
    parser.add_argument("--lexicon", default="csw24", help="default: %(default)s")
    parser.add_argument("--iterations", type=int, default=8, help="default: %(default)s")
    parser.add_argument("--games", type=int, default=1_000_000, help="self-play games per iteration (default: %(default)s)")
    parser.add_argument("--jobs", type=int, default=os.cpu_count() or 1, help="parallel processes (default: all CPUs)")
    parser.add_argument("--prior-sd", type=float, default=5.0,
                        help="how far, in points, a leave may stray from the estimate built from its sub-leaves "
                             "(default: %(default)s)")
    parser.add_argument("--seed", type=int, default=1, help="base random seed (default: %(default)s)")
    parser.add_argument("--start", help="superleaves file to play the first iteration with "
                                        "(default: whatever the data directory provides)")
    args = parser.parse_args()

    binary = rr.resolve_binary(args.bin)
    if binary is None:
        sys.exit("libquackle_test not found; pass --bin")

    work = Path(args.work).resolve()
    run = prepare_work(work, binary, args.lexicon, ALPHABETS[args.rules])
    installed = run / "strategy" / strategy_dir_name(args.rules, args.lexicon) / "superleaves"
    installed.parent.mkdir(parents=True, exist_ok=True)

    current = Path(args.start).resolve() if args.start else None
    print(f"training {args.rules} leaves for {args.lexicon} in {work}: {args.iterations} iterations of "
          f"{args.games} games on {args.jobs} jobs", flush=True)

    for iteration in range(1, args.iterations + 1):
        iteration_dir = work / f"iteration-{iteration:03d}"
        finished = iteration_dir / "superleaves"
        if finished.exists():
            print(f"iteration {iteration}: already done", flush=True)
            current = finished
            continue

        if current:
            shutil.copyfile(current, installed)
        elif installed.exists():
            installed.unlink()

        if iteration_dir.exists():
            shutil.rmtree(iteration_dir)
        iteration_dir.mkdir()

        started = time.monotonic()
        stats_files, summary = run_generation(binary, run, iteration_dir, args, iteration)
        current, fit_output = run_fit(binary, run, iteration_dir, stats_files, args)
        minutes = (time.monotonic() - started) / 60
        singles = next((line for line in fit_output.splitlines() if line.startswith("single tiles:")), "")
        print(f"iteration {iteration}: {summary}; {minutes:.1f} min\n  {singles}", flush=True)

    if current:
        shutil.copyfile(current, installed)
        target = rr.DATA_DIR / "strategy" / strategy_dir_name(args.rules, args.lexicon)
        print(f"\nfinal leaves: {current}\n"
              f"install with: mkdir -p {target} && cp {current} {target}/superleaves")


if __name__ == "__main__":
    try:
        main()
    except KeyboardInterrupt:
        sys.exit("interrupted; rerun the same command to resume")
