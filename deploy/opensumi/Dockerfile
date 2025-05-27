# 基于 node:18 镜像
FROM node:18-bullseye-slim

USER root
# 创建工作目录
WORKDIR /app
# 设置环境变量，避免交互提示
ENV DEBIAN_FRONTEND=noninteractive

RUN echo "当前基础镜像的操作系统信息：" && cat /etc/os-release

# 更新包列表并安装必要的依赖
RUN apt-get update && \
    apt-get install -y \
        git \
        jq \
        ca-certificates \
        curl \
        wget \
        unzip \
        tar \
        gnupg \
        sudo \
        lsb-release && \
    # 更新包列表
    apt-get update && \
    # 清理 apt 缓存
    apt-get clean && \
    rm -rf /var/lib/apt/lists/*

RUN node -v
RUN git clone https://github.com/opensumi/core.git .
RUN ls -lah
RUN yarn --version
# 安装依赖并构建OpenSumi
RUN yarn config set npmRegistryServer https://registry.npmmirror.com && \
    yarn install && \
    yarn run init && \
    yarn run download-extension

# 暴露OpenSumi默认端口
EXPOSE 8080
# 恢复默认环境变量
ENV DEBIAN_FRONTEND=dialog
# 设置启动命令
CMD ["yarn", "run", "start"]