# Ask the Book casebook

Every way Ask the Book has gone wrong is kept here as a case, with the verdict the book's checks should give. The
casebook replays them all on a test server, so a change to the book can be checked against every past failure before
it ships. A fix that breaks an older one shows up as a FAIL.

## Running it

```
server/install-server.sh /path/to/test-server     # once; any throwaway server folder
tools/bridge-mod/build.sh /path/to/test-server    # builds the Bridge jar into pack/mods
tools/book-casebook/run.sh /path/to/test-server [CASE]
```

`run.sh` copies the Bridge jar into the server, switches it to a flat offline world called `casebook`, starts it,
runs `/fieldguide casebook run <this folder>`, prints the report (also written to `casebook-report.md` here, which git
ignores) and stops the server. No API key is needed, because the replay runs the book's checks and tools without a
model. On a running server an operator (or the console) can run `/fieldguide casebook run "<folder>" [case]` directly.

Exit status 0 means nothing regressed.

| Verdict | Meaning |
|---|---|
| PASS | The checks give the verdict the case expects. |
| FAIL | A case that used to pass doesn't any more. This is a regression. |
| OPEN | A failure the book still has. It is expected to fail until the work named in `fixed_by` lands. |
| FIXED | An open case now passes. Remove its `open` flag so it guards the fix from then on. |
| SKIPPED | A question case. These need a model and belong to the answer eval, not the replay. |

## Cases

One JSON file per case in `cases/`, named `<kind letter><number>-<what>.json`.

| Field | |
|---|---|
| `title` | What the book must get right, in one line. |
| `from` | Where the failure came from (pack version and PR, or the report). |
| `roots` | Root causes from the rethink: R1 words never checked, R2 looking is optional, R3 checks hunt faults instead of proving the goal, R4 the player is missing from the checks, R5 the loop runs on reminders, R6 fixes leave no tests. |
| `open`, `fixed_by` | A known failure and the work that should fix it. |
| `kind` | `model`, `world`, `tool` or `question` (see below). |
| `expect` | `built` (models: built or refused), `has` (text the result must hold) and `lacks` (text it must not hold). Text matching ignores case. |

- **model**: `model` is a build_model input exactly as the book would send it. `inventory` (optional) gives the
  stand-in player items, for the materials tally.
- **world**: `setup` is a list of commands run from the case's spot high above the world. `~` works, and `{x}`,
  `{y+2}` and so on give absolute coordinates (belts need their controller's). After `ticks` (default 40) the live machine
  check runs on every block in `box` (`[x1,y1,z1,x2,y2,z2]` relative to the spot), or `tool` runs there instead. The
  box is cleared afterwards.
- **tool**: `tool` and `input` are one tool call, made as a stand-in player holding `inventory`.
- **question**: `question`, `player` (the situation in words) and `rubric` (what a good answer does). The answer eval
  uses these with a model. To set the asking player up for real, `world` is a list of commands run at a spot of its own
  (as `setup`, with `{x}` and the like), `box` what they build (cleared before the next question), `built` the part of
  the box that counts as the player's own build (default: all of it; leave trees and ground out), `stand`
  (`[dx, dy, dz, yaw, pitch]`) where the player stands and looks, `inventory` what they hold, `advancements` the
  advancements they have (ids, or `"create:*"` for all of a mod's) and `crafted` and `placed` (`{item id: count}`) their
  statistics.

## The answer eval

`standin.sh` turns a test server into the asking player, so a model (or a person) can play the book against it with
the book's real prompt and tools, and the answers can be graded against each case's rubric:

```
tools/book-casebook/standin.sh start /path/to/test-server   # writes the prompt to standin/ (persona, reference, tools)
tools/book-casebook/standin.sh as cases/q05-fan-line-works.json standin/q05.txt   # the question, as the book gets it
tools/book-casebook/standin.sh call trace_build in.json out.txt                   # one tool call
tools/book-casebook/standin.sh stop
```

`as ... plain` asks the way the book did before the player snapshot and design briefs, for comparing.

## Fixing the book

Add the case first, then fix the book so it passes. Fix it in a check, the player snapshot or a design brief. Add a
prompt sentence only as a last resort.
