---
title: Errors
---

# Errors

Every failure of every operation has the same body:

```json
{"error": {"code": "PLAYER_NOT_FOUND", "message": "We could not find that player. Start a new game."}}
```

- `code` is stable and written in capital letters. **Branch on it in programs.**
- `message` is a friendly sentence. **Show it to the player.** It never contains internal details such as stack traces or file paths.

A code mistake in the player's Java is **not** an HTTP error. A compile error or a failing test comes back as a normal `200` answer to `run` or `submit` with `status`, `compilerOutput` and `tests` filled in.

## Error codes

| Code | HTTP status | Meaning |
|---|---|---|
| `BAD_REQUEST` | 400 | The request was not understood: a malformed id, a body that is not valid JSON, a move with no directions, or empty code. A method that the address does not offer (405) and a body that is not sent as JSON (415) use this code too. |
| `PLAYER_NOT_FOUND` | 404 | No player has this id. Create one with `POST /api/v1/players`. |
| `QUEST_NOT_FOUND` | 404 | There is no quest with this id. |
| `QUEST_LOCKED` | 403 | The quest before this one has not been completed yet. |
| `QUEST_PROFILE_UNSUPPORTED` | 422 | The quest needs a sandbox profile this server does not offer yet. |
| `SOURCE_TOO_LARGE` | 413 | The code is longer than 20000 characters, or the request is larger than 64 KiB. |
| `HINT_NOT_AVAILABLE` | 409 | Hints unlock after a failed submit. Try the quest first. |
| `NO_MORE_HINTS` | 409 | All hints of this quest are already unlocked. |
| `RUN_IN_PROGRESS` | 429 | A run or submit of this player is still going. Wait for it to finish. |
| `SERVER_BUSY` | 503 | The server is running as many sandboxes as it allows, or holds as many players as it allows. Try again later. |
| `NO_NPC_ADJACENT` | 409 | No character is next to the hero. Walk next to one first. |
| `INTERNAL` | 500 | Something went wrong on the server. Progress is not lost. |
| `NOT_FOUND` | 404 | There is no such address. |
| `FORBIDDEN` | 403 | The server only answers requests addressed to localhost when it runs in the unsafe development mode. |
| `RATE_LIMITED` | 429 | Too many requests from this address. Creating players, running and submitting have per-minute limits. The `Retry-After` header says how many seconds to wait. |

This table is generated from the same source as the API description (`x-error-codes` of the `ErrorBody` schema in [openapi.yaml](openapi.yaml)), and a test checks that every status here is the one the server really answers.

## Tips for client authors
- Treat an unknown `code` as a generic failure and show `message`; new codes may be added.
- On `429` wait for the time in `Retry-After` (when present) before trying again.
- On `503` with `SERVER_BUSY` retry after a short pause; the player's progress is unaffected.
