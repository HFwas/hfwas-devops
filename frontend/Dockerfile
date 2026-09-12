# syntax=docker/dockerfile:1
# =============================================================================
# Stage 1: Build Vue 3 frontend
# BuildKit cache：npm 缓存跨构建复用；仅 package*.json 变化时才重跑 npm ci
# =============================================================================
FROM node:20-alpine AS builder

WORKDIR /build

COPY package.json package-lock.json* ./
RUN --mount=type=cache,id=hfwas-npm,target=/root/.npm \
    npm ci --no-audit --no-fund

COPY . .
RUN npm run build

# =============================================================================
# Stage 2: Export dist to host (compose frontend-build)
# =============================================================================
FROM busybox:1.37 AS export
COPY --from=builder /build/dist /dist
CMD ["sh", "-c", "rm -rf /host/assets /host/index.html; cp -a /dist/. /host/"]

# =============================================================================
# Stage 3: Nginx runtime
# =============================================================================
FROM nginx:1.27-alpine

# Copy built assets
COPY --from=builder /build/dist /usr/share/nginx/html

# Copy Nginx config
COPY docker/nginx.conf /etc/nginx/conf.d/default.conf

EXPOSE 80

CMD ["nginx", "-g", "daemon off;"]
