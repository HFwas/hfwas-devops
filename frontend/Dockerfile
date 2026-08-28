# =============================================================================
# Stage 1: Build Vue 3 frontend
# =============================================================================
FROM node:20-alpine AS builder

WORKDIR /build

# Build context is project root, so frontend files are in frontend/
COPY frontend/package.json frontend/package-lock.json* ./
RUN npm ci --no-audit --no-fund

COPY frontend/ .
RUN npm run build

# =============================================================================
# Stage 2: Nginx runtime
# =============================================================================
FROM nginx:1.27-alpine

# Copy built assets
COPY --from=builder /build/dist /usr/share/nginx/html

# Copy Nginx config
COPY frontend/docker/nginx.conf /etc/nginx/conf.d/default.conf

EXPOSE 80

CMD ["nginx", "-g", "daemon off;"]