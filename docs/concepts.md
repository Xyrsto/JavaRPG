---
title: Concepts
---

# Concepts

These are the ideas behind the API. Each one says where it shows up in the responses.

## Players and the player id
There are no accounts. Creating a player gives you a **player id** (a UUID), and that id is the only credential: anyone who has it controls that player. It goes in the address of every player operation. Keep it private and store it on the client so a reload does not lose the game.

## The world, gates and the hero
The world is a tile map, the same for everyone ([`GET /api/v1/world`](api/world.md)). Each character of a row is a tile: walls, floor, **gates** (capital letters) and so on, explained by the `legend`. The hero has a position, and walking is decided by the server: a wall, a closed gate or a character stops the walk (`blocked: true`). A gate is a wall until the quest that opens it is completed. Which gates are open for a player is `openGates` in the player view.

## Characters and quests
Each character gives one quest. Talking to the character next to the hero ([`POST .../interact`](api/world.md)) returns their lines and the state of their quest. A **quest** teaches exactly one Java concept: it has a story, a goal, starter code, tests and hints.

Quest ids are a region letter plus a number, for example `V01`. The planned curriculum has seven regions, from Java fundamentals (V) up to backend engineering (A), and quests are taught in a fixed order; a quest only uses ideas taught before it. Which quests exist today is whatever [`GET .../quests`](api/quests.md) returns.

A quest is **LOCKED** until the one before it is completed, **AVAILABLE** when it can be played, and **COMPLETED** after it has been solved.

## Run and submit
- **Run** compiles and runs your code and evaluates the **visible tests** only. It saves nothing, so you can try as often as you like (within the rate limit).
- **Submit** also evaluates the **hidden tests**. A pass completes the quest. The first completion awards experience points and applies the quest's **world effect**.

Two ways of grading exist, shown as `kind` in the quest view: `STDOUT` grades what your program prints, and `METHOD_RETURN` grades the values your methods return. Some tests check the shape of the code instead of its output, for example that you declared a variable.

## Hints and friendly messages
Hints unlock one at a time after a failed submit, from a gentle nudge to nearly the answer ([`POST .../hints/next`](api/quests.md)). Compiler and runtime messages are always shown as Java produced them. Beside them, `friendlyHints` gives plain-language advice for common mistakes.

## Experience, levels and the world effect
Completing a quest for the first time awards experience points (`xp`). Levels come from total experience; the player view shows `level`, `levelFloorXp` and `nextLevelXp` so a client can draw a progress bar. A completed quest's world effect, for example opening a gate, is listed in `worldEffects` so the client can redraw the world after a reload.

## The sandbox
Player code is untrusted, so it never runs inside the server. Each run happens in a disposable Docker container with **no network**, **no access to the host's files**, a read-only filesystem, no extra privileges, and limits on time, memory, processes and output. Runs that go over a limit end with a status such as `TIMEOUT` or `OUTPUT_LIMIT` instead of hurting anything. The `/health` operation shows whether the server runs the safe Docker sandbox.

## Hidden data
The server is authoritative and the client never decides results. To keep the game fair some data is never sent early:
- the **expected and actual values of hidden tests** are never filled in;
- the **explanation**, the **reference solution** and the **world effect** of a quest are empty until that quest is completed.

The API description marks these fields, and automatic tests check on every change that they are really withheld.

## Limits
Code is limited to 20000 characters and a request to 64 KiB. Creating players, running and submitting have per-minute limits per address (by default 5, 10 and 10), and a player can have one run or submit going at a time. Breaking a limit gives a clear [error](api/errors.md).
