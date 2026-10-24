# JavaRPG
[![CI](https://github.com/Xyrsto/JavaRPG/actions/workflows/ci.yml/badge.svg?branch=main)](https://github.com/Xyrsto/JavaRPG/actions/workflows/ci.yml)

A browser RPG where you learn Java by writing real Java code. This repository holds the backend; specs and tooling live in the sibling `SDD` folder.

## Structure

```
JavaRPG/
├── pom.xml                      parent POM (Java 21, Spring Boot 3.3.4 BOM)
├── content/                     game data read at runtime
│   ├── quests/                  one JSON file per quest (V01.json, ...)
│   └── world/                   tile map and NPCs
├── game-core/                   pure Java rules and shared contracts (no Spring, no I/O)
│   └── src/main/java/dev/javarpg/core/
│       ├── contract/            interfaces and records shared by all modules
│       ├── rules/               XP, levels, unlocks, curriculum
│       └── world/               movement and interaction rules
├── runner/                      sandboxed compile, run and grade
│   ├── docker/                  sandbox image (Dockerfile, entrypoint.sh)
│   └── src/main/java/dev/javarpg/runner/
│       ├── docker/              Docker executor and command builder
│       ├── grading/             grader and pipeline
│       ├── guard/               source guard (defense in depth)
│       ├── harness/             test harness generation and protocol
│       ├── local/               UNSAFE dev-only executor
│       └── fake/                in-memory executor for tests
└── server/                      Spring Boot application
    └── src/main/java/dev/javarpg/server/
        ├── JavaRpgApplication.java
        ├── config/              beans and properties
        ├── controller/          REST controllers
        ├── dto/                 request and response records
        ├── exception/           exceptions and the global handler
        ├── filter/              servlet filters (body size, rate limit, host)
        ├── repository/          persistence and content loading
        ├── service/             application logic
        └── util/                validation and small helpers
```

Dependency direction: `server` → `runner` → `game-core`. `game-core` depends on nothing.

## Build and test

```
mvn -q verify                                  # compile all modules, run non-Docker tests
docker build -t javarpg-runner:1 runner/docker # build the sandbox image
mvn -q verify -Ddocker.tests=true              # also run the Docker-backed tests
mvn -q -pl server spring-boot:run              # start the API on http://127.0.0.1:8080
```

Check it is up with `curl http://127.0.0.1:8080/health`.

CI checks and branch protection are described in [docs/ci.md](docs/ci.md).

## Safety

Player code runs only inside the Docker sandbox. The `unsafe-local` mode exists for development without Docker and needs `javarpg.sandbox.allow-unsafe=true`; never enable it on a shared or public machine.
