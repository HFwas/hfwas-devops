# =============================================================================
# Stage 1: Build Vue 3 frontend
# =============================================================================
FROM node:20-alpine AS builder

WORKDIR /build

COPY package.json package-lock.json* ./
RUN npm ci --no-audit --no-fund

COPY . .
RUN npm run build

# =============================================================================
# Stage 2: Nginx runtime
# =============================================================================
FROM nginx:1.27-alpine

# Copy built assets
COPY --from=builder /build/dist /usr/share/nginx/html

# Copy Nginx config
COPY docker/nginx.conf /etc/nginx/conf.d/default.conf

EXPOSE 80

CMD ["nginx", "-g", "daemon off;"]