# 基于 node:18 镜像
FROM node:18 as builder
# 用户工作空间，用于打包到前端工作空间地址
ENV WORKSPACE_DIR workspace
ENV EXTENSION_DIR extensions
# 创建工作目录
WORKDIR /app

RUN echo "当前基础镜像的操作系统信息：" && cat /etc/os-release

RUN node -v
RUN git clone https://github.com/opensumi/core.git .
ENV ELECTRON_MIRROR https://npmmirror.com/mirrors/electron/

RUN mkdir -p ${WORKSPACE_DIR}  &&\
    mkdir -p ${EXTENSION_DIR} \
RUN yarn --ignore-scripts --network-timeout 1000000 && \
    yarn run build && \
    yarn run download:extensions && \
    rm -rf ./node_modules


FROM node:18 as app

ENV WORKSPACE_DIR /workspace
ENV EXTENSION_DIR /root/.sumi/extensions
ENV EXT_MODE js
ENV NODE_ENV production

RUN mkdir -p ${WORKSPACE_DIR}  &&\
    mkdir -p ${EXTENSION_DIR}

WORKDIR /release

COPY /app/configs/docker/productionDependencies.json package.json

RUN yarn config set -H npmRegistryServer "https://registry.npmmirror.com" && \
    yarn --network-timeout 1000000

COPY --from=builder /app/dist dist
COPY --from=builder /app/dist-node dist-node
COPY --from=builder /app/hosted hosted
COPY --from=builder /app/extensions /root/.sumi/extensions

EXPOSE 8000

CMD [ "node", "./dist-node/server/index.js" ]