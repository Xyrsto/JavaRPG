---
title: Quick start
---

# Quick start

This takes about ten minutes and ends with you solving the first quest through the API.

## 1. What you need

- **JDK 21** (any distribution, for example Temurin or Zulu).
- **Maven 3.9** or newer.
- **Docker** (Docker Desktop on Windows and macOS), running. The sandbox that runs player code is a container, so `run` and `submit` need Docker.

## 2. Build

From the repository root:

```bash
docker build -t javarpg-runner:1 runner/docker
```

```bash
mvn -q install -DskipTests
```

The first command builds the sandbox image. The second builds all modules into your local Maven repository, which the server module needs. To also run every test, use `mvn -q verify`, and `mvn -q verify -Ddocker.tests=true` for the tests that use the real sandbox.

## 3. Start the server

```bash
mvn -q -pl server spring-boot:run
```

The server listens on `http://127.0.0.1:8080` only. In another terminal check that it is up:

```bash
curl http://127.0.0.1:8080/health
```

You should see `"status":"UP"`, `"sandbox":"docker"` and `"sandboxSafe":true`. Only one server can use the data folder at a time, so stop an old server before starting a new one.

> On Windows PowerShell `curl` is an alias for another command. Use `curl.exe`, or `Invoke-RestMethod`, as in the PowerShell examples below.

## 4. Try the API in the browser

Open [http://127.0.0.1:8080/docs](http://127.0.0.1:8080/docs). It lists every operation with examples and a **Try it out** button that sends real requests to your server.

## 5. Solve the first quest from a terminal

**Create a player.** The answer contains the player id, which is the only credential. Keep it for the next steps.

```bash
curl -X POST http://127.0.0.1:8080/api/v1/players
```

```powershell
$id = (Invoke-RestMethod -Method Post -Uri http://127.0.0.1:8080/api/v1/players).playerId
```

**Read the quest.** Replace `PLAYER_ID` with your id (in PowerShell use `$id`).

```bash
curl http://127.0.0.1:8080/api/v1/players/PLAYER_ID/quests/V01
```

The answer has the story, the goal, the starter code and the tests you can see. The explanation and the model solution are empty until you complete the quest.

**Run your solution** (nothing is saved, only the visible tests are checked):

```bash
curl -X POST http://127.0.0.1:8080/api/v1/players/PLAYER_ID/quests/V01/run \
  -H "Content-Type: application/json" \
  -d '{"source":"public class Main {\n    public static void main(String[] args) {\n        String name = \"Adventurer\";\n        System.out.println(\"Hello, \" + name + \"!\");\n    }\n}"}'
```

```powershell
$source = @'
public class Main {
    public static void main(String[] args) {
        String name = "Adventurer";
        System.out.println("Hello, " + name + "!");
    }
}
'@
$body = @{ source = $source } | ConvertTo-Json
Invoke-RestMethod -Method Post -Uri "http://127.0.0.1:8080/api/v1/players/$id/quests/V01/run" -ContentType "application/json" -Body $body
```

**Submit it.** A submit also runs the hidden tests. If everything passes you earn experience, the north gate opens and the next quest unlocks.

```powershell
Invoke-RestMethod -Method Post -Uri "http://127.0.0.1:8080/api/v1/players/$id/quests/V01/submit" -ContentType "application/json" -Body $body
```

**See what changed:**

```powershell
Invoke-RestMethod -Uri "http://127.0.0.1:8080/api/v1/players/$id"
```

You should see `"completedQuestIds":["V01"]` and gate `A` in `openGates`.

## 6. Where next

- Walk the hero and talk to characters: [world operations](api/world.md).
- Break the code on purpose to see compiler messages and hints: [quest operations](api/quests.md).
- Learn how errors look: [errors](api/errors.md).
- Understand the rules behind it all: [concepts](concepts.md).
