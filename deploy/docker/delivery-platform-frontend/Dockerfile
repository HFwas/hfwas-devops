# =============================================================================
# delivery-platform 前端（Vue 3 + Vite）
# =============================================================================
# 本机拉不动 Docker Hub，base 镜像一律用本地已有的 tag。
# =============================================================================
FROM node:20-alpine AS builder

WORKDIR /build

COPY package.json package-lock.json* ./
RUN npm ci --no-audit --no-fund

COPY . .
RUN npm run build

# =============================================================================
# Runtime：nginx 托管 dist，并把 /api 反代到后端 Service
# =============================================================================
FROM nginx:1.27-alpine

COPY --from=builder /build/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf

EXPOSE 80

CMD ["nginx", "-g", "daemon off;"]