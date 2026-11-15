# Leave training

`train_leaves.py` trains *superleaves*, the table of what every possible leave
(the tiles kept after a play) is worth, by repeated self-play. The engine adds a
play's leave value to its score to pick moves, so this table is most of the
static player's strategy, and the starting point for the simulating players.

It is set up for Lexulous with the Collins (csw24) dictionary, where no table
exists yet: an 8-tile rack means leaves of up to 7 tiles (4,100,231 of them),
and the 40/50-point bonuses, tile values and 89-tile bag all change what a leave
is worth. Until a trained table is installed, Lexulous falls back to the
Scrabble Collins table.

## Running

Build the harness without sanitizers, optimised:

```sh
cmake -S quacker -B build-release -GNinja -DCMAKE_BUILD_TYPE=Release
cmake --build build-release --target libquackle_test makegaddag
```

Then, from wherever the working files should go:

```sh
python3 test/leavetraining/train_leaves.py --bin build-release
```

Defaults: Lexulous rules, csw24, 8 iterations of 1,000,000 games, one job per
CPU, working directory `./leavetraining-work`. Python 3 alone is enough; there
are no packages to install.

Rough cost, measured on one core of a 2026 cloud VM: 40 games a second, so a
million games is about 7 core-hours (26 minutes on 16 cores), giving about 10
million observations. Fitting takes about 10 seconds and a few hundred MB of
memory. Each iteration leaves its statistics (about 1 MB per 8,000 games in
testing, growing more slowly than the game count) and a 39 MB table in its
directory.

Stopping and restarting with the same command resumes: finished iterations are
kept and an unfinished one is redone from the start.

## Watching it

Each iteration prints one summary:

```
iteration 3: 1000000 games, 10100000 observations, 58.20 points per turn; 26.4 min
  single tiles: ? 24.100 S 8.300 ...
```

- **Points per turn** is the average score in self-play. It should rise over
  the first few iterations as the players keep better tiles, then level off.
- **Single tiles** are the fitted values of one-tile leaves, best first. Once
  they stop moving by more than a few tenths of a point between iterations,
  more iterations of the same size won't help much; more games per iteration
  will.

Per-job logs, the merged statistics, and `leavefit.log` (observations per
leave size, the noise level, the fitted single tiles) are in each
`iteration-NNN/` directory.

## Installing the result

The run ends by printing the command to copy the last table into
`data/strategy/lexulous_csw24/superleaves`. The engine looks there first
whenever it plays Lexulous with csw24, so the app and `libquackle_test
--rules=lexulous` both pick it up; the build copies it with the rest of
`data/strategy`. At 39 MB it is under GitHub's 50 MB warning size.

## How the values are fitted

`libquackle_test --mode=leavegen` self-plays with Speedy Player and the
current table. Each time a player keeps a leave and draws back to a full rack,
it records that player's score on their next turn. Games stop once the bag can
no longer refill a rack, since no later turn can be recorded.

`libquackle_test --mode=leavefit` merges the statistics and, for every leave:

1. takes the average recorded value, minus the average after keeping nothing
   (so the empty leave stays at 0, as the engine assumes);
2. builds an estimate from the leave's sub-leaves one and two tiles smaller,
   which is exact when a leave's value is a sum of per-tile and per-pair terms,
   and adds whatever the table the games were played with knows beyond that
   kind of estimate (QU's synergy, say), so knowledge accumulates from one
   iteration to the next instead of being relearned;
3. blends the two, weighting the estimate as if it were a number of
   observations equal to (noise sd / `--prior-sd`)². The noise sd is about
   28 points, so that is about 31 observations at the default `--prior-sd 5`. Lower `--prior-sd` trusts the
   sub-leaf estimate more, higher trusts sparse data more.

Rare leaves, most 7-tile ones included, therefore get values built from
well-observed smaller leaves and from earlier iterations, and a leave never
seen still gets a sensible value. The first iteration inherits the Scrabble
Collins table's synergies this way; to train from nothing instead, pass an
empty file as `--start`.

## Limits

- A leave is credited only with its owner's next score. Tiles whose benefit
  outlasts one turn, the blank above all, come out somewhat undervalued: in a
  short trial the blank settled near 23 points against 28 in the Scrabble
  table. Crediting the next score plus the next leave's value removes that
  bias but feeds the table's own noise back into it. In the same trial, that
  inflated flexible leaves (?S went from 35 to 52) and lowered scoring, so it
  isn't used.

- Self-play is greedy: players only ever keep the leaves the current table
  likes, so a badly undervalued leave is seen rarely and corrects slowly. The
  sub-leaf estimate limits the damage.
- Points per turn compares iterations only roughly. A head-to-head match
  between two tables would be a better test, but the engine holds one table at
  a time, so that needs more work.
