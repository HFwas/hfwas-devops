# syntax=docker/dockerfile:1
# =============================================================================
# Stage 1: Build Spring Boot fat JAR
# BuildKit cache：/root/.m2 跨构建复用，改业务代码不再全量拉依赖
# =============================================================================
FROM maven:3.9.9-eclipse-temurin-21 AS builder

WORKDIR /build

COPY backend/docker/maven-settings.xml /root/.m2/settings.xml

# 先只拷 POM，最大化依赖层缓存命中
COPY pom.xml .
COPY backend/pom.xml backend/
COPY backend/user-api/pom.xml backend/user-api/
COPY backend/user-core/pom.xml backend/user-core/
COPY backend/pm-core/pom.xml backend/pm-core/
COPY backend/api-test-core/pom.xml backend/api-test-core/
COPY backend/file-parser/pom.xml backend/file-parser/
COPY backend/image-core/pom.xml backend/image-core/
COPY backend/pipeline-core/pom.xml backend/pipeline-core/
COPY backend/container-core/pom.xml backend/container-core/
COPY backend/server/pom.xml backend/server/

# 预拉依赖（失败不阻断：个别 optional 解析失败时仍可用缓存继续 package）
RUN --mount=type=cache,id=hfwas-m2,target=/root/.m2/repository \
    mvn -B -pl backend/server -am -DskipTests dependency:go-offline || true

# 再拷源码；仅此处变化时跳过上面的依赖下载
COPY backend/ backend/

RUN --mount=type=cache,id=hfwas-m2,target=/root/.m2/repository \
    mvn -B -pl backend/server -am -DskipTests package \
    && cp "$(ls backend/server/target/server-*.jar | grep -v original | head -1)" /build/server.jar

# =============================================================================
# Stage 2: Export fat JAR to host (compose backend-build)
# =============================================================================
FROM busybox:1.37 AS export
COPY --from=builder /build/server.jar /out/server.jar
CMD ["cp", "/out/server.jar", "/host/server.jar"]

# =============================================================================
# Stage 3: Runtime image
# =============================================================================
FROM eclipse-temurin:21-jre-alpine

RUN addgroup -S devops -g 1000 && \
    adduser -S devops -u 1000 -G devops -h /app -s /sbin/nologin

# Alpine 官方源在国内很慢；matplotlib/Pillow 用 apk 预编译包，避免 pip 源码编译
RUN sed -i 's#dl-cdn.alpinelinux.org#mirrors.aliyun.com#g' /etc/apk/repositories \
    && apk add --no-cache python3 py3-pip py3-pillow py3-matplotlib \
    && rm -rf /var/cache/apk/*

COPY --from=builder /build/backend/scripts /app/scripts
RUN --mount=type=cache,id=hfwas-pip,target=/root/.cache/pip \
    pip3 install --break-system-packages \
    -i https://mirrors.aliyun.com/pypi/simple/ \
    --trusted-host mirrors.aliyun.com \
    python-docx>=1.1.2 \
    openpyxl>=3.1.2 \
    python-pptx>=1.0.0 \
    fpdf2 \
    markdown \
    reportlab>=4.1.0

# 图片处理：Magick 改像素，ExifTool 读标签（Perl 是 ExifTool 运行时）
# 不装 imagemagick-pdf，降低 Magick 攻击面
# 图片处理 + shell 语法验证依赖 bash
RUN apk add --no-cache \
    bash \
    imagemagick \
    imagemagick-jpeg \
    imagemagick-tiff \
    imagemagick-webp \
    imagemagick-heic \
    libheif \
    perl \
    exiftool \
    && rm -rf /var/cache/apk/*

RUN mkdir -p /etc/ImageMagick-7
COPY backend/image-core/src/main/resources/imagemagick/policy.xml \
     /etc/ImageMagick-7/policy.xml

RUN magick -version \
    && magick -list format | grep -Ei 'HEIC|HEIF' \
    && exiftool -ver \
    && perl -v >/dev/null

# Copy the fat JAR
COPY --from=builder /build/server.jar /app/server.jar

WORKDIR /app

ENV MALLOC_ARENA_MAX=2 \
    MAGICK_CONFIGURE_PATH=/etc/ImageMagick-7 \
    MAGICK_TEMPORARY_PATH=/tmp \
    MAGICK_MEMORY_LIMIT=256MiB \
    MAGICK_MAP_LIMIT=512MiB \
    MAGICK_DISK_LIMIT=1GiB \
    MAGICK_AREA_LIMIT=40MP \
    MAGICK_THREAD_LIMIT=2

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
