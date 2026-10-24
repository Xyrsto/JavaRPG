---
title: Quests
---

# Quests

A quest is **LOCKED** until the one before it is completed, **AVAILABLE** when it can be played, and **COMPLETED** once it has been solved. See [concepts](../concepts.md) for run versus submit, hints and hidden data.

## `GET /api/v1/players/{playerId}/quests`

Returns every quest in teaching order with its state for this player.

```bash
curl http://127.0.0.1:8080/api/v1/players/PLAYER_ID/quests
```

**Response `200`**

```json
{
  "quests": [
    {"id": "V01", "region": "V", "regionName": "The Village of First Steps",
     "title": "The Adventurer's Name", "concept": "Variables and String", "status": "AVAILABLE", "xp": 100},
    {"id": "V02", "region": "V", "regionName": "The Village of First Steps",
     "title": "The Four Elements", "concept": "Primitive types", "status": "LOCKED", "xp": 100}
  ]
}
```

**Failures:** `404` `PLAYER_NOT_FOUND` when no player has this id.

## `GET /api/v1/players/{playerId}/quests/{questId}`

Returns the story, goal, starter code, visible tests and unlocked hints of one quest. Hidden tests are never listed. The `explanation`, `referenceSolution` and `worldEffect` are `null` until the player has completed the quest, after which they are filled in. Opening a locked quest is refused.

```bash
curl http://127.0.0.1:8080/api/v1/players/PLAYER_ID/quests/V01
```

**Response `200`** (before completion)

```json
{
  "id": "V01",
  "title": "The Adventurer's Name",
  "concept": "Variables and String",
  "story": "Elder Mira squints at you. A hero with no name cannot cross the gate. Write it down in Java, and say it aloud.",
  "goal": "Create a String variable called name holding Adventurer, then print Hello, Adventurer! using System.out.println and your variable.",
  "kind": "STDOUT", "profile": "BASIC", "status": "AVAILABLE", "xp": 100,
  "starterCode": "public class Main {\n    public static void main(String[] args) {\n    }\n}",
  "lastSource": null,
  "visibleTests": [
    {"name": "prints the greeting", "call": null, "expected": "Hello, Adventurer!"},
    {"name": "declares a String variable called name", "call": null, "expected": null}
  ],
  "hintsTotal": 3, "hintsUnlocked": 0, "hints": [], "failedAttempts": 0,
  "explanation": null, "referenceSolution": null, "worldEffect": null
}
```

`kind` is `STDOUT` (the program's printed output is graded) or `METHOD_RETURN` (the values your methods return are graded). `lastSource` is the last code this player ran or submitted, so an editor can resume.

**Failures**

| Status | Code | When |
|---|---|---|
| `400` | `BAD_REQUEST` | The player id or the quest id is not a valid id. |
| `403` | `QUEST_LOCKED` | The quest before this one has not been completed yet. |
| `404` | `QUEST_NOT_FOUND` / `PLAYER_NOT_FOUND` | No quest or no player has this id. |

## `POST /api/v1/players/{playerId}/quests/{questId}/run`

Compiles and runs the code in the isolated sandbox and evaluates the **visible tests only**. Nothing is saved as progress and no hidden data is returned. Compiler and runtime messages are returned as they are, with friendly advice beside them. One run or submit per player at a time.

**Request body**

```json
{"source": "public class Main {\n    public static void main(String[] args) {\n        String name = \"Adventurer\";\n        System.out.println(\"Hello, \" + name + \"!\");\n    }\n}"}
```

`source` is the whole program as one string with line breaks, at most 20000 characters. The class that runs is `Main` for `STDOUT` quests and `Solution` for `METHOD_RETURN` quests.

**Response `200`** (whether or not the code is correct)

```json
{
  "questId": "V01", "scope": "VISIBLE", "passed": true, "status": "OK",
  "compilerOutput": "", "stdout": "Hello, Adventurer!\n", "stderr": "",
  "summary": "All visible tests pass. Press Submit to finish the challenge.",
  "friendlyHints": [],
  "tests": [
    {"name": "prints the greeting", "hidden": false, "passed": true,
     "expected": "Hello, Adventurer!", "actual": "Hello, Adventurer!", "message": "Passed."},
    {"name": "declares a String variable called name", "hidden": false, "passed": true,
     "expected": null, "actual": null, "message": "Passed."}
  ],
  "durationMillis": 1215
}
```

`status` says how the sandbox run ended: `OK`, `COMPILE_ERROR`, `TIMEOUT`, `OUTPUT_LIMIT`, `MEMORY_LIMIT`, `RUNTIME_ERROR` or `SANDBOX_ERROR`. Compile errors arrive in `compilerOutput`; a code mistake is not an HTTP error.

**Failures**

| Status | Code | When |
|---|---|---|
| `400` | `BAD_REQUEST` | The body is not valid JSON, an id is not valid, or the code is empty. |
| `403` | `QUEST_LOCKED` | The quest is locked. |
| `404` | `QUEST_NOT_FOUND` / `PLAYER_NOT_FOUND` | No quest or no player has this id. |
| `413` | `SOURCE_TOO_LARGE` | More than 20000 characters of code, or a request larger than 64 KiB. |
| `415` | `BAD_REQUEST` | The request was not sent as JSON. |
| `422` | `QUEST_PROFILE_UNSUPPORTED` | The quest needs a sandbox profile this server does not offer yet. |
| `429` | `RUN_IN_PROGRESS` / `RATE_LIMITED` | A run of this player is still going, or the per-minute limit was reached. |
| `503` | `SERVER_BUSY` | The server is running as many sandboxes as it allows. |

## `POST /api/v1/players/{playerId}/quests/{questId}/submit`

Runs the code like a trial run but evaluates **all** tests, hidden ones included. A pass completes the quest and, the first time, awards experience points and applies the world effect. A failure returns which tests failed without revealing hidden expectations, and unlocks the next hint. The request body and the failures are the same as for `run`.

```powershell
Invoke-RestMethod -Method Post -Uri "http://127.0.0.1:8080/api/v1/players/$id/quests/V01/submit" -ContentType "application/json" -Body $body
```

**Response `200`** (a passing submit)

```json
{
  "questId": "V01", "scope": "ALL", "passed": true, "status": "OK",
  "compilerOutput": "", "stdout": "Hello, Adventurer!\n", "stderr": "",
  "summary": "Challenge complete!", "friendlyHints": [],
  "tests": [
    {"name": "prints the greeting", "hidden": false, "passed": true,
     "expected": "Hello, Adventurer!", "actual": "Hello, Adventurer!", "message": "Passed."},
    {"name": "Hidden test 1", "hidden": true, "passed": true,
     "expected": null, "actual": null, "message": "Passed."}
  ],
  "durationMillis": 817,
  "completion": {
    "firstCompletion": true, "xpAwarded": 100, "totalXp": 100, "level": 1, "levelUp": false,
    "worldEffect": {"type": "OPEN_GATE", "target": "A", "message": "The north gate creaks open."},
    "explanation": "String name = \"Adventurer\"; creates a variable: a named place in memory that holds text.",
    "unlockedQuestId": "V02"
  }
}
```

`completion` is only present when the submit passed; otherwise it is `null`. Hidden tests only say whether they passed: their `expected` and `actual` are always `null`.

## `POST /api/v1/players/{playerId}/quests/{questId}/hints/next`

Unlocks and returns the next hint of a quest. Hints become available after the first failed submit and go from a gentle nudge to nearly the answer. Unlocking is saved, so the hint also appears in the quest view afterwards. There is no request body.

```bash
curl -X POST http://127.0.0.1:8080/api/v1/players/PLAYER_ID/quests/V01/hints/next
```

**Response `200`**

```json
{"hintNumber": 1, "hint": "A variable is a labelled box. The type String is the box for text.", "hintsUnlocked": 1, "hintsTotal": 3}
```

**Failures**

| Status | Code | When |
|---|---|---|
| `400` | `BAD_REQUEST` | An id is not valid. |
| `404` | `QUEST_NOT_FOUND` / `PLAYER_NOT_FOUND` | No quest or no player has this id. |
| `409` | `HINT_NOT_AVAILABLE` / `NO_MORE_HINTS` | No failed submit yet, or all hints are already unlocked. |
