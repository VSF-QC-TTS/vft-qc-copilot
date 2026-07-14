# syntax=docker/dockerfile:1.7

ARG NODE_VERSION=24.18.0


FROM node:${NODE_VERSION}-bookworm-slim AS build
WORKDIR /app

COPY package.json package-lock.json ./

RUN --mount=type=cache,target=/root/.npm,sharing=locked \
    npm ci

COPY index.html         ./
COPY tsconfig.json      ./
COPY tsconfig.app.json  ./
COPY tsconfig.node.json ./
COPY vite.config.ts     ./
COPY public  ./public
COPY src     ./src

ARG VITE_API_BASE_URL
ENV VITE_API_BASE_URL=${VITE_API_BASE_URL}

RUN npm run build


FROM nginxinc/nginx-unprivileged:stable-alpine AS runtime

COPY --from=build /app/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf

EXPOSE 8080

# nginxinc/nginx-unprivileged already runs as uid 101 (nginx)
# No need to set USER or chown
