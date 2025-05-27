# 使用官方Node.js LTS版本作为基础镜像
FROM node:lts-alpine

# 设置工作目录
WORKDIR /app

# 安装必要的系统依赖
RUN apk add --no-cache \
    git \
    openssh-client \
    python3 \
    make \
    g++

# 克隆OpenSumi仓库（可替换为特定版本或自己的fork）
RUN git clone https://github.com/opensumi/core.git .

# 安装依赖并构建OpenSumi
RUN yarn install && \
    yarn run init && \
    yarn run download-extension

# 暴露OpenSumi默认端口
EXPOSE 8080

# 设置启动命令
CMD ["yarn", "run", "start"]