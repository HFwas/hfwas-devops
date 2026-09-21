# cn-app-operator Dockerfile
# Build stage
FROM golang:1.22-alpine AS builder

WORKDIR /workspace

# Cache dependencies
COPY go.mod go.sum ./
RUN go mod download

# Build
COPY . .
RUN CGO_ENABLED=0 GOOS=linux GOARCH=amd64 go build -a -o operator cmd/operator/main.go

# Runtime stage
FROM alpine:3.20

RUN apk --no-cache add ca-certificates tzdata

WORKDIR /
COPY --from=builder /workspace/operator .

# Helm SDK 运行时需要
ENV HELM_DRIVER=secret

USER 65532:65532

ENTRYPOINT ["/operator"]