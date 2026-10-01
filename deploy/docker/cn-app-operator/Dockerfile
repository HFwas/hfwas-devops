# cn-app-operator Dockerfile
# Build stage
# 用 golang:1.22（Debian）而非 golang:1.22-alpine：本机拉不动 Docker Hub 的 alpine variant，
# 构建产物是 CGO_ENABLED=0 的静态二进制，builder 用什么 libc 不影响运行镜像。
FROM golang:1.22 AS builder

WORKDIR /workspace

# 国内直连 goproxy，避免走已失效的 Colima 代理
ENV GOPROXY=https://goproxy.cn,direct

# Cache dependencies
COPY go.mod go.sum ./
RUN go mod download

# Build
# 不写死 GOARCH：builder 镜像跑在哪个架构，就编译成哪个架构。
# BuildKit 下 builder 会按 --platform 拉取，legacy builder 下就是本机架构，
# 两种方式都得到与运行节点一致的二进制（本机与 k3s 容器均为 aarch64）。
COPY . .
RUN CGO_ENABLED=0 GOOS=linux go build -a -o operator cmd/operator/main.go

# Runtime stage
FROM alpine:latest

RUN sed -i 's#dl-cdn.alpinelinux.org#mirrors.aliyun.com#g' /etc/apk/repositories \
    && apk --no-cache add ca-certificates tzdata

WORKDIR /
COPY --from=builder /workspace/operator .

# Helm SDK 运行时需要
ENV HELM_DRIVER=secret

USER 65532:65532

ENTRYPOINT ["/operator"]