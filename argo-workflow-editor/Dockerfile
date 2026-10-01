# Stage 1: Build React frontend
FROM node:20-alpine AS builder
WORKDIR /build
COPY package.json package-lock.json* ./
RUN --mount=type=cache,id=hfwas-npm,target=/root/.npm \
    npm ci --no-audit --no-fund
COPY . .
RUN npm run build

# Stage 2: Nginx runtime
FROM nginx:1.27-alpine
RUN rm -rf /usr/share/nginx/html/*
COPY --from=builder /build/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf
EXPOSE 80
CMD ["nginx", "-g", "daemon off;"]