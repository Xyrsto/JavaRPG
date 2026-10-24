---
title: API reference
---

# API reference

The game server speaks JSON over HTTP. This page covers the rules that apply to every operation; the operations themselves are grouped on their own pages.

| Group | Operations |
|---|---|
| [Health](health.md) | Is the server up, and how does it run player code? |
| [World](world.md) | The map, walking the hero, talking to characters |
| [Players](players.md) | Creating a player and reading a player's state |
| [Quests](quests.md) | Listing, opening, running and submitting quests, and hints |
| [Errors](errors.md) | The error body and every error code |

The complete, machine-readable description is [openapi.yaml](openapi.yaml). It is generated from the server's code and checked by the tests on every change, so it is always exact. A running server also serves it at `/v3/api-docs.yaml` and a page where you can try every call at `/docs`; both are on by default when the server listens only on your own machine and off when it listens on other machines (switch with the setting `javarpg.docs.enabled`).

## Conventions

- **Base address.** `http://127.0.0.1:8080` by default. Operations live under `/api/v1`, and `/health` is at the root. `v1` is the API version.
- **Format.** Requests with a body are JSON and must be sent with `Content-Type: application/json`. Responses are JSON.
- **The player id is the only credential.** There are no accounts. `POST /api/v1/players` returns an id and every player operation has it in the address as `{playerId}`. Treat it like a password.
- **The server decides.** The client sends intentions (walk, run this code) and draws what comes back.
- **Hidden data.** Hidden test expectations are never returned. A quest's explanation, reference solution and world effect are empty until the quest is completed. See [concepts](../concepts.md).
- **Failures** always have the same shape, described in [Errors](errors.md), with a stable `code` to branch on and a friendly `message` to show.
- **Limits.** Code up to 20000 characters, requests up to 64 KiB, per-minute limits on creating players, running and submitting, and one run or submit at a time per player.
- **CORS.** Browsers on `localhost` and `127.0.0.1` may call the API; only `GET` and `POST` are used.

## Examples in these pages
Examples use the sample quest `V01` and a made-up player id, `3f2b8c1e-5a47-4d0e-9c1a-7b6d2e8f4a10`. In your own calls use the id you got from creating a player. Commands are shown for `curl` and, where the syntax differs, PowerShell.
