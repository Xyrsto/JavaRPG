---
title: World
---

# World

## `GET /api/v1/world`

Returns the static village map with its gates and characters. It is the same for every player, so a client can load it once. Where a particular player stands and which gates are open is in the [player view](players.md).

```bash
curl http://127.0.0.1:8080/api/v1/world
```

**Response `200`**

```json
{
  "id": "village", "width": 12, "height": 9,
  "rows": ["############", "#..........#", "#..........#", "#..........#", "#..........#",
           "#..........#", "#####A######", "#..........B", "############"],
  "legend": {"#": "wall", ".": "floor", "A": "gate", "B": "gate"},
  "heroStart": {"x": 5, "y": 4},
  "gates": [{"id": "A", "x": 5, "y": 6}, {"id": "B", "x": 11, "y": 7}],
  "npcs": [
    {"id": "elder", "name": "Elder Mira", "x": 3, "y": 2, "questId": "V01"},
    {"id": "smith", "name": "Smith Orin", "x": 8, "y": 2, "questId": "V02"}
  ]
}
```

Each character of a row is a tile, explained by `legend`. A gate is a wall until the quest that opens it is completed. This operation has no failure responses specific to it.

## `POST /api/v1/players/{playerId}/move`

Moves the hero one step at a time through the given directions and stops at the first wall, closed gate or character. Between 1 and 32 steps are accepted per call. The server decides where the hero ends up; the client only draws it.

```bash
curl -X POST http://127.0.0.1:8080/api/v1/players/PLAYER_ID/move \
  -H "Content-Type: application/json" -d '{"directions":["LEFT","LEFT","UP"]}'
```

**Request body**

```json
{"directions": ["LEFT", "LEFT", "UP"]}
```

Each direction is `UP`, `DOWN`, `LEFT` or `RIGHT`.

**Response `200`**

```json
{"x": 3, "y": 3, "applied": 3, "blocked": false}
```

`applied` is how many steps were taken. `blocked` is `true` when the walk stopped early.

**Failures**

| Status | Code | When |
|---|---|---|
| `400` | `BAD_REQUEST` | No directions, more than 32, an unknown direction, or a body that is not valid JSON. |
| `404` | `PLAYER_NOT_FOUND` | No player has this id. |
| `415` | `BAD_REQUEST` | The request was not sent as JSON. |

## `POST /api/v1/players/{playerId}/interact`

Talks to the character standing next to the hero. The reply has the character's lines and the state of the quest they give, which tells the client whether to open the quest panel. After the quest is completed the lines are the thank-you lines.

```bash
curl -X POST http://127.0.0.1:8080/api/v1/players/PLAYER_ID/interact
```

**Response `200`**

```json
{
  "npcId": "elder", "npcName": "Elder Mira", "questId": "V01", "questStatus": "AVAILABLE",
  "lines": ["Traveller! The north gate is sealed by an old spell.",
            "Only a hero who can write their name in Java may pass."]
}
```

`questStatus` is `LOCKED`, `AVAILABLE` or `COMPLETED`.

**Failures**

| Status | Code | When |
|---|---|---|
| `404` | `PLAYER_NOT_FOUND` | No player has this id. |
| `409` | `NO_NPC_ADJACENT` | No character is next to the hero. Walk next to one first. |
