---
title: JavaRPG
---

# JavaRPG

JavaRPG is a browser RPG in which you **learn Java by writing real Java code**. You walk a village, talk to its characters, and every character gives you a small quest: write a few lines of Java, run them, and when they are right the world changes (a gate opens, a new quest unlocks).

This repository holds the **game server**: the rules of the game, the sandbox that safely runs the code players write, and the HTTP API that a browser client talks to. The browser client itself is planned and not part of this repository yet.

## How it fits together

| Part | What it does |
|---|---|
| `game-core` | The rules: experience and levels, which quests unlock, how the hero moves. Plain Java with no framework and no input or output, so it is easy to test. |
| `runner` | Compiles and runs the code a player writes inside a locked-down Docker container (no network, no access to your files, limited time, memory and output), then grades the result. |
| `server` | The Spring Boot application. It exposes the HTTP API, stores progress, and decides every outcome. A client only draws what the server tells it. |
| `content` | The quests and the world map, as plain JSON files that the server reads at start-up. |

Real Java is compiled and run by a real JVM. Compiler errors are shown to the player as they are, with friendly advice beside them, never instead of them.

## Where to go next

- **Run it** and make your first API calls: [Quick start](quick-start.md).
- **Understand the game** (quests, hints, experience, gates, the sandbox, hidden data): [Concepts](concepts.md).
- **Use the API**: [API reference](api/index.md). The machine-readable description is [openapi.yaml](api/openapi.yaml); a running server also offers a page where you can try every call, at `/docs`.
- **See how changes are checked**: [Continuous integration](ci.md).
