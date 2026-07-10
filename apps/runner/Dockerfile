# syntax=docker/dockerfile:1.7

ARG NODE_VERSION=24.18.0


FROM node:${NODE_VERSION}-bookworm-slim AS build
WORKDIR /app

COPY package.json package-lock.json ./

RUN --mount=type=cache,target=/root/.npm,sharing=locked \
    npm ci

COPY tsconfig.json ./
COPY src ./src

RUN npm run build


FROM node:${NODE_VERSION}-bookworm-slim AS production-dependencies
WORKDIR /app

COPY package.json package-lock.json ./

RUN --mount=type=cache,target=/root/.npm,sharing=locked \
    npm ci --omit=dev


FROM node:${NODE_VERSION}-bookworm-slim AS runtime
WORKDIR /app

ENV NODE_ENV=production

COPY --from=production-dependencies --chown=node:node \
    /app/node_modules ./node_modules

COPY --from=build --chown=node:node \
    /app/dist ./dist

COPY --chown=node:node package.json ./

RUN mkdir -p /app/.runner-work /app/storage/artifacts && \
    chown -R node:node /app/.runner-work /app/storage

USER node

CMD ["node", "dist/src/main.js"]