---
title: Health
---

# Health

## `GET /health`

Returns `UP` together with how player code is run and how many quests were loaded. Use it as a start-up check and to see whether the server runs the safe Docker sandbox. It needs no player id and is not rate limited. It is at the root of the server, not under `/api/v1`.

```bash
curl http://127.0.0.1:8080/health
```

**Response `200`**

```json
{"status": "UP", "sandbox": "docker", "sandboxSafe": true, "questsLoaded": 2}
```

| Field | Meaning |
|---|---|
| `status` | `UP` when the server answers. |
| `sandbox` | How player code is run. `docker` is the safe mode. |
| `sandboxSafe` | `true` when player code runs in the isolated container. |
| `questsLoaded` | How many quests were loaded from the content files. |
| `warning` | Only present when the sandbox mode is not the safe one. |

There are no failure responses specific to this operation.
