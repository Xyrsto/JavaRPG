---
title: Players
---

# Players

## `POST /api/v1/players`

Starts a new game: the hero is at the start position with no experience. The returned `playerId` is the only credential, so the client must keep it and send it with every later call. The number of players is capped and creation is rate limited per address.

```bash
curl -X POST http://127.0.0.1:8080/api/v1/players
```

```powershell
Invoke-RestMethod -Method Post -Uri http://127.0.0.1:8080/api/v1/players
```

**Response `201`**

```json
{
  "playerId": "3f2b8c1e-5a47-4d0e-9c1a-7b6d2e8f4a10",
  "xp": 0, "level": 1, "levelFloorXp": 0, "nextLevelXp": 230,
  "completedQuestIds": [],
  "hero": {"x": 5, "y": 4},
  "openGates": [],
  "worldEffects": []
}
```

| Field | Meaning |
|---|---|
| `playerId` | The player id, a UUID. Keep it private. |
| `xp`, `level` | Experience points earned and the level they give. |
| `levelFloorXp`, `nextLevelXp` | Experience at which this level began and at which the next one starts, for a progress bar. |
| `completedQuestIds` | Quests completed, in order. |
| `hero` | Where the hero stands (`x` counts columns from the left, `y` rows from the top, both from 0). |
| `openGates` | Gates that are open for this player. |
| `worldEffects` | The effects of the completed quests, so the client can redraw the world. |

**Failures**

| Status | Code | When |
|---|---|---|
| `429` | `RATE_LIMITED` | Too many players were created from this address in the last minute. |
| `503` | `SERVER_BUSY` | The server already holds as many players as it allows. |

## `GET /api/v1/players/{playerId}`

Returns the current state of one player, which is what a client needs to draw the screen again after a reload.

```bash
curl http://127.0.0.1:8080/api/v1/players/3f2b8c1e-5a47-4d0e-9c1a-7b6d2e8f4a10
```

**Response `200`** has the same fields as above. After completing the first quest:

```json
{
  "playerId": "3f2b8c1e-5a47-4d0e-9c1a-7b6d2e8f4a10",
  "xp": 100, "level": 1, "levelFloorXp": 0, "nextLevelXp": 230,
  "completedQuestIds": ["V01"],
  "hero": {"x": 5, "y": 4},
  "openGates": ["A"],
  "worldEffects": [{"type": "OPEN_GATE", "target": "A", "message": "The north gate creaks open."}]
}
```

**Failures**

| Status | Code | When |
|---|---|---|
| `400` | `BAD_REQUEST` | The player id is not a valid id. |
| `404` | `PLAYER_NOT_FOUND` | No player has this id. |
