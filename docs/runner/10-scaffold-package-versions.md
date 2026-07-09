# Scaffold Package Versions

This file pins the dependency versions for the first `apps/runner` scaffold. Codex should use these exact versions and must not silently replace them with newer npm results during scaffold.

Last checked with `npm view <package> version`: 2026-07-09 Asia/Bangkok.

## Runtime Dependencies

```text
promptfoo  0.121.18
redis      6.1.0
zod        4.4.3
pino       10.3.1
undici     8.7.0
uuid       14.0.1
dotenv     17.4.2
```

Install command:

```bash
npm install \
  promptfoo@0.121.18 \
  redis@6.1.0 \
  zod@4.4.3 \
  pino@10.3.1 \
  undici@8.7.0 \
  uuid@14.0.1 \
  dotenv@17.4.2 \
  --save-exact
```

## Dev Dependencies

```text
typescript  7.0.2
tsx         4.23.0
vitest      4.1.10
oxlint      1.73.0
prettier    3.9.4
@types/node 24.13.3
```

Install command:

```bash
npm install -D \
  typescript@7.0.2 \
  tsx@4.23.0 \
  vitest@4.1.10 \
  oxlint@1.73.0 \
  prettier@3.9.4 \
  @types/node@24.13.3 \
  --save-exact
```

## Notes

- `promptfoo` is pinned exactly. Do not use `promptfoo@latest` in `package.json`.
- `@types/node` is pinned to the Node 24 major line because the runner targets Node 24 LTS. npm latest for `@types/node` may track a newer Node major.
- If these versions fail install/build/test when Codex scaffolds the project, Codex must stop and report the failure instead of silently choosing different versions.
- The `redis` package is intentional for runner v1. It supports XADD/XREADGROUP/XACK/XAUTOCLAIM through node-redis; keep it behind adapters so switching to ioredis later is localized.
- After install, commit `package-lock.json` and use `npm ci` in CI/Docker.
