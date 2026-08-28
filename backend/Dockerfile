# =============================================================================
# Stage 1: Build Spring Boot fat JAR
# =============================================================================
FROM eclipse-temurin:21-jdk-alpine AS builder

WORKDIR /build

# Install Maven
RUN apk add --no-cache maven

# Cache dependencies first (layer 1)
# Build context is project root, so pom.xml is the parent POM
COPY pom.xml .
COPY backend/pom.xml backend/pom.xml
COPY backend/user-api/pom.xml backend/user-api/pom.xml
COPY backend/user-core/pom.xml backend/user-core/pom.xml
COPY backend/pm-core/pom.xml backend/pm-core/pom.xml
COPY backend/api-test-core/pom.xml backend/api-test-core/pom.xml
COPY backend/file-parser/pom.xml backend/file-parser/pom.xml
COPY backend/server/pom.xml backend/server/pom.xml
RUN mvn dependency:go-offline -pl backend/server -am -B -q || true

# Copy source and build
COPY backend/ backend/
RUN mvn clean package -pl backend/server -am -DskipTests -B -q

# =============================================================================
# Stage 2: Runtime image
# =============================================================================
FROM eclipse-temurin:21-jre-alpine

# 创建非 root 用户
# 使用固定 UID/GID 以兼容 Helm PodSecurityContext runAsUser
RUN addgroup -S devops -g 1000 && \
    adduser -S devops -u 1000 -G devops -h /app -s /sbin/nologin

# 安装运行时依赖
RUN apk add --no-cache \
    python3 \
    py3-pip \
    py3-pillow \
    py3-matplotlib \
    && rm -rf /var/cache/apk/*

# Install Python dependencies for docgen
COPY --from=builder /build/backend/scripts /app/scripts
RUN pip3 install --no-cache-dir \
    python-docx>=1.1.2 \
    openpyxl>=3.1.2 \
    python-pptx>=1.0.0 \
    matplotlib>=3.8.0 \
    fpdf2 \
    markdown \
    Pillow>=10.0.0 \
    -q && \
    rm -rf /root/.cache/pip

# Copy the fat JAR
COPY --from=builder /build/backend/server/target/server-*.jar /app/server.jar

WORKDIR /app

# glibc MALLOC_ARENA_MAX: 限制 malloc arena 数，防止多核容器 RSS 膨胀
# 默认 8*cores，每个 arena ~64MB，多核场景浪费严重
ENV MALLOC_ARENA_MAX=2

# 创建数据目录并设置权限
RUN mkdir -p /app/data /app/files /app/dumps /app/logs /app/config && \
    chown -R devops:devops /app

# 健康检查
HEALTHCHECK --interval=30s --timeout=5s --retries=3 \
    CMD wget -qO- http://localhost:8089/health/check || exit 1

EXPOSE 8089

# 切换到非 root 用户
USER devops:devops

ENTRYPOINT ["java", \
    "-Xms512m", "-Xmx1g", \
    "-XX:MaxDirectMemorySize=512m", \
    "-XX:+ExitOnOutOfMemoryError", \
    "-XX:+HeapDumpOnOutOfMemoryError", \
    "-XX:HeapDumpPath=/app/dumps", \
    "-jar", "/app/server.jar"]