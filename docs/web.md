---
title: Web client
---

# Web client

The browser client lives in the `web/` folder. This page is for people who work on it. Players only need the game server's address: the server delivers the client itself.

## What you need
- **Node 22** (the `web/.nvmrc` file says which; npm comes with it). Newer Node versions may print an engine warning for some tools, which is harmless, but CI and the build use 22.
- The game server running, for development: see the [quick start](quick-start.md).
- **Maven and JDK 21** only if you want to pack the client into the server.

The default Java build never needs Node. Everything below is opt-in.

## Commands
Run these in the `web/` folder.

| Command | What it does |
|---|---|
| `npm ci` | Installs exactly the locked dependency versions. Always use this, not `npm install`. |
| `npm run dev` | Starts the development page with live reload. It asks the game server at `http://127.0.0.1:8080` through a proxy, so the page and the API share one address. |
| `npm run build` | Checks the types and builds the production files into `dist/`. |
| `npm test` | Unit tests (the call layer, error handling, status text and the lint rules). |
| `npm run lint` | Style checks. Inserting text as markup (`innerHTML` and friends), `eval` and `console` are rejected on purpose. |
| `npm run typecheck` | Strict type check. |
| `npm run api:types` | Regenerates `src/api/schema.d.ts` from [the API description](api/openapi.yaml). CI fails if the committed file is out of date, so run it after any API change. |

To check a production build: `npm run build` and then `node scripts/check-dist.mjs`, which fails on any reference to another site or when the compressed size goes over 150 KB.

### Pointing the client at another server
The client talks to the **same address as the page** by default, so nothing needs configuring. In development the proxy target is `http://127.0.0.1:8080`; to use another one, set `VITE_PROXY_TARGET` before `npm run dev`. To make a build call a server at a different address, set `VITE_API_BASE` (this needs that server to allow the page's origin, see below).

## Delivered by the game server
The server serves the client at its root address, so a player opens one address and nothing else needs hosting. To pack the client into the server, ask for it explicitly:

```bash
mvn -q -Pweb -pl server -am package -DskipTests
```

This runs `npm ci` and `npm run build` in `web/` and copies `dist/` into the jar under `static/`. Without `-Pweb` the build is unchanged, and the server then shows a short page at its root address saying that the client is not built, with links to this documentation and to the interactive API page.

What the server adds for the client's files (and only for them, never for the API, health, documentation or the API description):
- `Content-Security-Policy` that allows nothing from other sites and no inline script, plus `X-Content-Type-Options: nosniff` and `Referrer-Policy: no-referrer`;
- a one-year cache for the fingerprinted files under `/assets/`, and no long cache for the page itself.

### Why there is no cross-origin setup
The page, the files and the API all come from the same address, in development (through the proxy) and in production (served by the server). The browser therefore never makes a cross-origin request and no CORS setting is involved. If a client is ever served from a different address, the server's `javarpg.cors.allowed-origins` setting decides which origins may call `/api/**`.

## Supported browsers
The current and the previous major version of Chrome, Edge, Firefox and Safari on desktop. The build targets ES2022, which covers them.

## How the client talks to the server
Every game operation goes through one layer, `src/api/client.ts`. Answers are typed from the API description. Every problem comes back as one of these named failures and never as an exception:

| Failure | When |
|---|---|
| `api` | The server answered with its error body. It carries the server's code and its friendly message. |
| `unreachable` | No answer could be obtained (server down, no network). |
| `timeout` | The server did not answer in time. |
| `unexpected` | The answer was not what the description promises, for example a proxy error page. |

The player id (the save code) only ever appears in the path of a game operation. The client writes no logs, so it cannot end up in one, and it is never placed in the page address.
