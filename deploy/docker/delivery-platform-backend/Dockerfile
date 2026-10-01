# =============================================================================
# delivery-platform 后端（Go + chi + SQLite）
# =============================================================================
# mattn/go-sqlite3 依赖 CGO。用构建镜像自带的 gcc 编出来是 glibc 产物，
# 运行阶段没有小体积的 glibc base 可用；装 musl-tools 用 musl-gcc 静态链接，
# 运行镜像就能落在 alpine 上。
# 本机拉不动 Docker Hub，base 镜像一律用本地已有的 tag。
# =============================================================================
FROM golang:1.22 AS builder

WORKDIR /build

# 国内直连 goproxy，避免走已失效的 Colima 代理
ENV GOPROXY=https://goproxy.cn,direct

RUN apt-get update \
    && apt-get install -y --no-install-recommends musl-tools \
    && rm -rf /var/lib/apt/lists/*

# 先只拷 go.mod/go.sum，最大化依赖层缓存命中
COPY go.mod go.sum ./
RUN go mod download

COPY . .
RUN CGO_ENABLED=1 CC=musl-gcc GOOS=linux \
    go build -ldflags '-linkmode external -extldflags "-static"' \
    -o /build/server ./cmd/server

# =============================================================================
# Runtime
# =============================================================================
FROM alpine:latest

RUN sed -i 's#dl-cdn.alpinelinux.org#mirrors.aliyun.com#g' /etc/apk/repositories \
    && apk --no-cache add ca-certificates tzdata

WORKDIR /app
COPY --from=builder /build/server /app/server

ENV PORT=8180 \
    DELIVERY_DATA_DIR=/data

RUN mkdir -p /data && chown -R 65532:65532 /data

USER 65532:65532

EXPOSE 8180

HEALTHCHECK --interval=30s --timeout=5s --retries=3 \
    CMD wget -qO- http://localhost:8180/health || exit 1

ENTRYPOINT ["/app/server"]