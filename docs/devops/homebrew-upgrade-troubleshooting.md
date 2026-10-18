# macOS Homebrew 4.4.12 → 7.0.1 升级排查报告

> 日期：2026-09-16  
> 版本：v0.1  
> 关联：[AGENTS.md](../../AGENTS.md)（外网走 `127.0.0.1:7890`）

### 变更记录

| 版本 | 日期 | 变更说明 |
|------|------|----------|
| v0.1 | 2026-09-16 | 初版：4.4.12 升 7.0.1 的完整过程、master→main 引导坑、Portable Ruby 断点续传 |

---

## 1. 环境与目标

| 项 | 升级前 | 升级后 |
|---|---|---|
| 机器 | macOS 26.6.2 / Apple Silicon | 同左 |
| 前缀 | `/opt/homebrew` | 同左 |
| Homebrew | **4.4.12**，Git 分支 `master` | **7.0.1**（`7.0.1-23-g3ab8044`），分支 `main` |
| Git 源 | `HOMEBREW_BREW_GIT_REMOTE=https://mirrors.ustc.edu.cn/brew.git` | 同左（未改 `~/.zprofile`） |
| 自动更新 | `HOMEBREW_NO_AUTO_UPDATE=true`（`~/.zshrc`） | 同左 |
| 代理 | Clash 等本机 HTTP 代理 `127.0.0.1:7890` | 同左 |
| Portable Ruby | 旧版自带（随后被 master 引导冲掉） | `4.0.6_2` → `/opt/homebrew/Library/Homebrew/vendor/portable-ruby/4.0.6_2` |

目标：只升级 **Homebrew 本身**（`brew update`），不跑 `brew upgrade`，已装 formula / cask 版本不动。

代理环境（后续所有外网步骤都带上）：

```bash
export HTTP_PROXY=http://127.0.0.1:7890
export HTTPS_PROXY=http://127.0.0.1:7890
export ALL_PROXY=http://127.0.0.1:7890
export http_proxy=http://127.0.0.1:7890
export https_proxy=http://127.0.0.1:7890
```

本机镜像配置（已有，未改）：

```bash
# ~/.zprofile
eval "$(/opt/homebrew/bin/brew shellenv)"
export HOMEBREW_BREW_GIT_REMOTE="https://mirrors.ustc.edu.cn/brew.git"
export HOMEBREW_CORE_GIT_REMOTE="https://mirrors.ustc.edu.cn/homebrew-core.git"
```

---

## 2. 完整升级过程（按时间）

```
确认版本 (4.4.12)
        │
        ▼
brew update + 代理
        │
        ├─ 成功：git fetch 中科大镜像，拉到 4.4.13 … 7.0.1 全部 tag
        │
        └─ 失败①：切到 legacy master 引导分支
              Library/Homebrew 被掏空
              curl shim 丢失 → Can't find a working Curl
        │
        ▼
排查 git：HEAD = origin/master（引导 stub）
origin/main 才是完整仓库
bin/brew 变成 bootstrap 脚本
        │
        ▼
清掉残留空 Library/Homebrew，再 brew update
        │
        ├─ 成功：checkout main，brew 恢复为 7.0.1
        │
        └─ 失败②：必须先装 Portable Ruby 4.0.6_2
              中科大 bottle 路径 404
              ghcr.io 下到 50%～75% 报 curl 18
        │
        ▼
排查：沙箱 HOMEBREW_CACHE、镜像 URL、ghcr 鉴权
        │
        ▼
curl 断点续传 + GHCR token → 12MB 包 SHA 对齐
brew vendor-install ruby（命中本地缓存）
brew update 完成：4.4.12 → 7.0.1
```

### 2.1 确认现状

```bash
brew --version    # Homebrew 4.4.12
brew --prefix     # /opt/homebrew
which brew        # /opt/homebrew/bin/brew
```

### 2.2 第一次 `brew update`

命令：代理环境变量 + `brew update`。

现象：

- Git remote 走中科大，`fetch` 成功，出现 `main` 分支和大量新 tag（一直到 `7.0.1`）。
- 随后：

```text
==> Updating Homebrew...
/opt/homebrew/Library/Homebrew/cmd/update.sh: line 21: /opt/homebrew/Library/Homebrew/shims/shared/curl: No such file or directory
Error: Can't find a working Curl!
```

本机 `/usr/bin/curl` 是好的（curl 8.7.1）。缺的是 Homebrew 自己的 shim，不是系统 curl。

### 2.3 切到 `main` 并二次 update

确认 `origin/main` 已在本地后，删掉引导分支留下的空 `Library/Homebrew`，再跑 `brew update`。Homebrew 官方引导脚本会：

1. `git fetch origin main`
2. `git checkout -B main origin/main`
3. `git remote set-head origin main`
4. 再 `exec` 新的 `bin/brew update`

这次 Git 侧成功，版本已经是 7.x，但卡在 Portable Ruby。

### 2.4 手工下 Portable Ruby 并 vendor-install

把包下到真实缓存目录（不要用 Cursor 沙箱临时 cache），校验 SHA 后：

```bash
export HOMEBREW_CACHE="$HOME/Library/Caches/Homebrew"
unset HOMEBREW_BOTTLE_DOMAIN
brew vendor-install ruby
brew update
brew --version
```

结束状态：

```text
Homebrew 7.0.1-23-g3ab8044
Updated Homebrew from ef5518545d to 7.0.1 (3ab8044837).
Homebrew Ruby: 4.0.6 => /opt/homebrew/Library/Homebrew/vendor/portable-ruby/4.0.6_2/bin/ruby
Branch: main
ORIGIN: https://mirrors.ustc.edu.cn/brew.git
```

---

## 3. 问题一：`master` 已变成引导分支，工作树被掏空

### 3.1 现象

`brew update` 报 `Can't find a working Curl`。再看仓库：

```text
cd /opt/homebrew
git branch          # * master
git ls-tree --name-only HEAD
# 只剩 LICENSE.txt / README.md / bin

ls /opt/homebrew/Library/Homebrew
# 几乎空，只剩 vendor/

head /opt/homebrew/bin/brew
# 已变成 bootstrap-homebrew() 脚本
```

`Library/`、`Cellar/`、`bin/` 在 `git status` 里全部变成 untracked：不是「文件还在 git 里」，而是 **当前 checkout 的树根本不包含它们**。旧 `Library` 是工作区残留。

### 3.2 排查思路

| 步骤 | 查什么 | 结论 |
|------|--------|------|
| 1 | 系统 `curl` 是否存在 | 存在，问题不在系统 |
| 2 | shim 路径是否还在 | `/opt/homebrew/Library/Homebrew/shims/shared/curl` 没有 |
| 3 | `git remote -v` / `git log -1` | remote 是中科大；HEAD 已是 `67984c752d … legacy-master-bootstrap` |
| 4 | `git ls-tree HEAD` vs `git ls-tree origin/main` | `master` 只有 3 个文件；`main` 才是完整 brew 仓库 |
| 5 | 读新的 `bin/brew` | 明确写着：master 不再支持，跑 `brew update` 迁移到 main |

关键对比：

```text
origin/master  HEAD = 67984c752d  Merge PR #23733 legacy-master-bootstrap
origin/main    HEAD = ef5518545d  （完整 Homebrew，随后又前进到 3ab8044837 / 7.0.1）
```

### 3.3 原因

Homebrew 上游把默认分支从 **`master` 迁到 `main`**。旧 `master` 被改成 **legacy bootstrap**：

- 树里只留引导用的 `bin/brew`。
- 这个脚本的唯一工作是 fetch/checkout `main`，然后重新 exec。
- 迁移窗口标注到 **2027-03-01**，之后这段引导也会删。

本机一直停在 4.4.12 + `master`。第一次 `brew update` 把 `master` fast-forward 到引导提交，**工作树按新树检出**，原来的 `Library/Homebrew`（含 `update.sh`、curl shim、Ruby 供应商目录）被换成空壳。`update.sh` 还在跑旧路径，shim 已经没了，于是报找不到 Curl。

这和中科大镜像无关。镜像只是把官方 `master`/`main` 同步过来；官方 `master` 本身已经是 stub。

### 3.4 处理

引导脚本就是官方恢复路径。注意两点：

1. 残留的空 `Library/Homebrew`（只有 `vendor/`）会挡住 `checkout main` 写出完整树，先删：

```bash
if [ -d /opt/homebrew/Library/Homebrew ] && [ ! -f /opt/homebrew/Library/Homebrew/brew.sh ]; then
  rm -rf /opt/homebrew/Library/Homebrew
fi
```

2. 再 `brew update`（带代理）。成功后应看到：

```text
* main   ... [origin/main]
  master ... legacy-master-bootstrap
```

`Cellar/`、已装 bottle、`bin/` 里的 formula 链接本来就不在 brew.git 里，checkout 不会删用户软件，只动 Homebrew 自己的仓库文件。

---

## 4. 问题二：Portable Ruby 下载失败（404 + curl 18）

### 4.1 现象

切到 `main` 之后：

```text
==> Downloading https://mirrors.ustc.edu.cn/homebrew-bottles/portable-ruby-4.0.6_2.arm64_big_sur.bottle.tar.gz
curl: (56) The requested URL returned error: 404

==> Downloading https://ghcr.io/v2/homebrew/core/portable-ruby/blobs/sha256:1643bb83d705f0c5e34445af151bb064bdf984081eff18a9646466b8fdd104db
# 进度到约 50%～75%
curl: (18) Transferred a partial file

Error: Failed to install Homebrew Portable Ruby (and your system version is too old)!
```

需要的产物（来自 `Library/Homebrew/vendor/portable-ruby-arm64-darwin`）：

```text
版本：portable-ruby 4.0.6_2
平台 tag：arm64_big_sur
SHA256：1643bb83d705f0c5e34445af151bb064bdf984081eff18a9646466b8fdd104db
文件名：portable-ruby-4.0.6_2.arm64_big_sur.bottle.tar.gz
大小：约 12MB
```

7.x 的 `brew --version` 可以只读 git 描述，**多数真正的 brew 命令要 Portable Ruby**。系统 `/usr/bin/ruby` 对 Homebrew 来说太旧，不能顶替。

### 4.2 排查思路

| 步骤 | 查什么 | 结论 |
|------|--------|------|
| 1 | `brew --version` / `git branch` | 已经在 `main`，7.0.1，问题收窄到 Ruby 供应商包 |
| 2 | `vendor/portable-ruby-version` 与 `portable-ruby-arm64-darwin` | 锁定版本、tag、SHA |
| 3 | `vendor-install.sh` 的 URL 列表 | `HOMEBREW_ARTIFACT_DOMAIN` → `HOMEBREW_BOTTLE_DOMAIN/文件名` → `ghcr.io` blob |
| 4 | 中科大 / 清华 bottles 路径 | 该文件名 **404**，镜像目录布局对不上 Homebrew 7 的 portable-ruby 路径 |
| 5 | GitHub Releases 同名 tar.gz | 也是 **404**（release 资源名对不上） |
| 6 | `curl -I` ghcr.io | 代理能连上；未带 token 时 **401** + `WWW-Authenticate` |
| 7 | `brew --cache` / 环境变量 | Cursor 沙箱把 `HOMEBREW_CACHE` 指到 `/var/folders/.../cursor-sandbox-cache/.../homebrew`，不完整下载被丢在临时目录 |
| 8 | 带 GHCR token + `-C -` 续传 | 12MB 一次拉完，SHA 与 vendor 文件一致 |

`vendor-install.sh` 里真正会试的地址（按顺序）：

```text
${HOMEBREW_ARTIFACT_DOMAIN}/v2/homebrew/core/portable-ruby/blobs/sha256:${SHA}
${HOMEBREW_BOTTLE_DOMAIN}/${ruby_FILENAME}
https://ghcr.io/v2/homebrew/core/portable-ruby/blobs/sha256:${SHA}
```

本次设置了 `HOMEBREW_BOTTLE_DOMAIN=https://mirrors.ustc.edu.cn/homebrew-bottles`，所以先打中科大文件名 URL（404），再回落到 ghcr（半路断开）。

另外试过、同样 404 的地址（避免以后再踩）：

```text
https://mirrors.tuna.tsinghua.edu.cn/homebrew-bottles/bottles-portable-ruby/portable-ruby-4.0.6_2.arm64_big_sur.bottle.tar.gz
https://mirrors.ustc.edu.cn/homebrew-bottles/bottles-portable-ruby/portable-ruby-4.0.6_2.arm64_big_sur.bottle.tar.gz
https://github.com/Homebrew/homebrew-portable-ruby/releases/download/4.0.6_2/portable-ruby-4.0.6_2.arm64_big_sur.bottle.tar.gz
```

### 4.3 原因（三件事叠在一起）

**A. Homebrew 7 强依赖 Portable Ruby 4.0.6_2**

升级跨了大版本（4 → 5 → 6 → 7），vendor 目录里的 Ruby 版本跟着仓库走。旧 Ruby 已被 master 引导冲掉，必须重新下载。

**B. 国内 bottles 镜像不是这个 blob 的可靠源**

`HOMEBREW_BOTTLE_DOMAIN` 只是「在域名后面拼文件名」。中科大/清华当前布局对不上 `portable-ruby-4.0.6_2.arm64_big_sur.bottle.tar.gz`，得到 404。这不是代理问题。

**C. ghcr.io 经代理会半路断（curl 18）**

`curl: (18) Transferred a partial file` = 对端在 Content-Length 走完前关连接。12MB 的 blob 走 `127.0.0.1:7890` 时多次停在 50%～75%。`brew vendor-install` 默认下载失败会删 `.incomplete`，**不会把断点留给下一次**，所以一直重头下、一直失败。

叠加：Agent/Cursor 沙箱注入了 `HOMEBREW_CACHE`，缓存不在 `~/Library/Caches/Homebrew`，半成品更难复用。

ghcr 还要求匿名 pull token。不带 `Authorization` 会 401；Homebrew 自己会带 `HOMEBREW_GITHUB_PACKAGES_AUTH`，手工 `curl` 必须自己去换 token。

### 4.4 处理

1. **用真实缓存目录**，不要沙箱 cache：

```bash
export HOMEBREW_CACHE="$HOME/Library/Caches/Homebrew"
unset HOMEBREW_BOTTLE_DOMAIN
mkdir -p "$HOMEBREW_CACHE"
```

2. **向 ghcr 要匿名 token，断点续传**（代理保留）：

```bash
DEST="$HOME/Library/Caches/Homebrew/portable-ruby-4.0.6_2.arm64_big_sur.bottle.tar.gz"
EXPECTED=1643bb83d705f0c5e34445af151bb064bdf984081eff18a9646466b8fdd104db

TOKEN=$(curl -fsSL -x http://127.0.0.1:7890 \
  "https://ghcr.io/token?service=ghcr.io&scope=repository:homebrew/core/portable-ruby:pull" \
  | python3 -c 'import sys,json; print(json.load(sys.stdin)["token"])')

curl -L --fail --retry 20 --retry-all-errors --retry-delay 2 -C - \
  -x http://127.0.0.1:7890 \
  -H "Authorization: Bearer $TOKEN" \
  -o "$DEST.incomplete" \
  "https://ghcr.io/v2/homebrew/core/portable-ruby/blobs/sha256:${EXPECTED}"

mv "$DEST.incomplete" "$DEST"
shasum -a 256 "$DEST"
# 必须等于 1643bb83d705f0c5e34445af151bb064bdf984081eff18a9646466b8fdd104db
```

3. **让 brew 走本地缓存**，不要再打镜像 404：

```bash
brew vendor-install ruby
# Already downloaded: ~/Library/Caches/Homebrew/portable-ruby-4.0.6_2.arm64_big_sur.bottle.tar.gz
# ==> Pouring portable-ruby-4.0.6_2.arm64_big_sur.bottle.tar.gz

brew update
brew --version
brew config | head -20
```

`vendor-install.sh` 若发现 `${CACHED_LOCATION}` 已存在且 SHA 匹配，会跳过下载，只 unpack 到 `Library/Homebrew/vendor/portable-ruby/`。

---

## 5. 根因对照

| # | 表面错误 | 直接原因 | 深层原因 |
|---|----------|----------|----------|
| 1 | `Can't find a working Curl` | curl shim 路径不存在 | 上游 `master` 改为引导 stub，`brew update` 把 4.4.12 的 `Library` 检出成空树 |
| 2 | `bin/brew` 变成 bootstrap、多数命令不可用 | 当前文件就是引导脚本 | 同上，这是官方迁移手段，不是损坏 |
| 3 | 中科大 portable-ruby URL 404 | `HOMEBREW_BOTTLE_DOMAIN` + 文件名拼出来的对象不存在 | 国内 bottles 镜像未同步该 portable-ruby 布局；7.x 默认权威源是 ghcr |
| 4 | `curl: (18) Transferred a partial file` | 12MB 传输未完成连接被关 | 代理链路不稳定；brew 失败即删 `.incomplete`，无法续传 |
| 5 | 沙箱里 `brew --cache` 指向 `/var/folders/.../cursor-sandbox-cache` | 环境变量 `HOMEBREW_CACHE` 被注入 | Agent 沙箱与用户真实 `~/Library/Caches/Homebrew` 不是同一处 |
| 6 | 手工 curl ghcr 401 | 缺 Docker Registry token | ghcr 匿名 pull 也要先打 `/token` |

一句话：**版本停太久（4.4.12 + `master`）碰上官方迁 `main`；迁过去后又必须从 ghcr 拉 Portable Ruby，代理大文件不稳定，镜像路径还 404。**

---

## 6. 结束后的检查

```bash
brew --version
# Homebrew 7.0.1-23-g3ab8044

git -C /opt/homebrew branch -vv
# * main   3ab8044837 [origin/main] ...

brew config
# HOMEBREW_VERSION: 7.0.1-23-g3ab8044
# Branch: main
# ORIGIN: https://mirrors.ustc.edu.cn/brew.git
# Homebrew Ruby: 4.0.6 => .../vendor/portable-ruby/4.0.6_2/bin/ruby
# http_proxy / https_proxy: http://127.0.0.1:7890
```

未改：

- `~/.zprofile` 里的中科大 `HOMEBREW_*_GIT_REMOTE`
- `~/.zshrc` 里的 `HOMEBREW_NO_AUTO_UPDATE=true`
- 已装 formula / cask（没有 `brew upgrade`）

`brew config` 里 `Core tap: N/A` 是 4.x 之后用 JSON API 装包的正常形态，不代表 core 坏了。

---

## 7. 以后同样升级怎么走

本机已经在 `main` + 7.0.1，日常只需：

```bash
export HTTP_PROXY=http://127.0.0.1:7890 HTTPS_PROXY=http://127.0.0.1:7890 ALL_PROXY=http://127.0.0.1:7890
brew update
```

若再遇到 **引导分支 / curl shim 丢失**：

```bash
cd /opt/homebrew
git fetch origin main
git checkout -B main origin/main
# 若 Library/Homebrew 是空壳且没有 brew.sh，先 rm -rf 再 checkout
brew update
```

若再遇到 **Portable Ruby 下不完**：

1. `unset HOMEBREW_BOTTLE_DOMAIN`（避免先打一个 404）。
2. `export HOMEBREW_CACHE="$HOME/Library/Caches/Homebrew"`。
3. 用第 4.4 节的 token + `curl -C -` 把 blob 放到缓存文件名 `portable-ruby-${VERSION}.${TAG}.bottle.tar.gz`。
4. `shasum -a 256` 对 `Library/Homebrew/vendor/portable-ruby-arm64-darwin` 里的 `ruby_SHA`。
5. `brew vendor-install ruby`。

不要用 `HOMEBREW_BOTTLE_DOMAIN` 去「猜」portable-ruby 文件路径，除非已经用浏览器/curl 确认该 URL 返回 200。

Git 仓库可以继续用中科大；**Portable Ruby / bottle 二进制仍可能必须走 ghcr + 代理**。

---

## 8. 参考

- Homebrew 7.0.0 说明：<https://brew.sh/blog/7.0.0>
- 7.0.1 tag：<https://github.com/Homebrew/brew/releases/tag/7.0.1>
- 本机 brew 仓库：`/opt/homebrew`（`ORIGIN=https://mirrors.ustc.edu.cn/brew.git`）
- Portable Ruby 安装逻辑：`/opt/homebrew/Library/Homebrew/cmd/vendor-install.sh`
- 平台 SHA：`/opt/homebrew/Library/Homebrew/vendor/portable-ruby-arm64-darwin`
- 本仓库代理约定：`AGENTS.md`「外网与本地代理」
