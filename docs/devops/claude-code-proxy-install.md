# Claude Code 本地代理安装文档

> 适用环境：国内网络无法直连 `claude.ai` / `downloads.claude.ai`  
> 安装方式：官方 Native Installer（不依赖 Node）  
> 代理：`127.0.0.1:7890`（Clash / 同类本地 HTTP 代理）  
> 已验证版本：2.1.269（2026-09-12，macOS arm64）

---

## 目录

1. [背景与目标](#1-背景与目标)
2. [环境信息](#2-环境信息)
3. [为什么必须走代理](#3-为什么必须走代理)
4. [安装步骤](#4-安装步骤)
5. [配置 PATH](#5-配置-path)
6. [验证](#6-验证)
7. [从损坏的 npm 安装迁移](#7-从损坏的-npm-安装迁移)
8. [日常使用与更新](#8-日常使用与更新)
9. [常见问题](#9-常见问题)

---

## 1. 背景与目标

官方推荐命令：

```bash
curl -fsSL https://claude.ai/install.sh | bash
```

在国内直连时，该 URL 会被重定向到地区拦截页 `https://claude.com/app-unavailable-in-region`。`curl` 拿到的是 HTML，`bash` 会报：

```text
bash:行1: 未预期的符号“<”附近有语法错误
```

随后 `curl: (56) Failure writing output to destination`，因为 bash 已退出、管道写不进去。

目标：

- 通过本机 `127.0.0.1:7890` 代理拉取官方安装脚本和原生二进制
- 安装到 `~/.local/bin/claude`，不依赖 nvm / Node
- 避免残留的 npm 全局包抢占 `claude` 命令

---

## 2. 环境信息

| 项目 | 值 |
|------|-----|
| 操作系统 | macOS（Apple Silicon / arm64） |
| Shell | zsh |
| 代理 | `http://127.0.0.1:7890` |
| 安装位置 | `~/.local/bin/claude` → `~/.local/share/claude/versions/<version>` |
| 已验证版本 | 2.1.269 |
| Node | 不需要（官方安装为原生二进制） |

前置条件：

- 本机代理已监听 `127.0.0.1:7890`（Clash 默认混合端口常见为此值）
- 已安装 `curl`、`bash`

检查代理是否在听：

```bash
nc -z -w 2 127.0.0.1 7890 && echo "7890 open"
```

---

## 3. 为什么必须走代理

| URL | 直连结果 | 经 `127.0.0.1:7890` |
|-----|----------|---------------------|
| `https://claude.ai/install.sh` | 302 → 地区不可用 HTML | 返回真正的 `#!/bin/bash` 脚本 |
| `https://downloads.claude.ai/claude-code-releases/latest` | 超时 / 连不上 | HTTP 200，可下载二进制 |

官方文档把「脚本变成 HTML / `syntax error near unexpected token '<'`」归为同一类：当前出口 IP 被判定为不可用地区，或网络被拦截。

**不要**把直连下到的 HTML 交给 bash 执行。先确认前几行是脚本：

```bash
export HTTP_PROXY=http://127.0.0.1:7890
export HTTPS_PROXY=http://127.0.0.1:7890
export ALL_PROXY=http://127.0.0.1:7890
export http_proxy=http://127.0.0.1:7890
export https_proxy=http://127.0.0.1:7890

curl -fsSL -x http://127.0.0.1:7890 https://claude.ai/install.sh | head -n 5
```

第一行必须是 `#!/bin/bash`，不能是 `<!DOCTYPE html>`。

---

## 4. 安装步骤

### 4.1 导出代理并安装

```bash
export HTTP_PROXY=http://127.0.0.1:7890
export HTTPS_PROXY=http://127.0.0.1:7890
export ALL_PROXY=http://127.0.0.1:7890
export http_proxy=http://127.0.0.1:7890
export https_proxy=http://127.0.0.1:7890
export no_proxy=localhost,127.0.0.1

curl -fsSL -x http://127.0.0.1:7890 https://claude.ai/install.sh | bash
```

安装程序会再从 `downloads.claude.ai` 拉原生包，同样走上述代理环境变量。成功时类似：

```text
✔ Claude Code successfully installed!
  Version: 2.1.269
  Location: ~/.local/bin/claude
```

可能同时提示 `~/.local/bin` 不在 PATH 中，按下一节处理。

### 4.2 指定版本（可选）

```bash
# 稳定通道
curl -fsSL -x http://127.0.0.1:7890 https://claude.ai/install.sh | bash -s stable

# 指定版本
curl -fsSL -x http://127.0.0.1:7890 https://claude.ai/install.sh | bash -s 2.1.269
```

---

## 5. 配置 PATH

官方二进制在 `~/.local/bin/claude`。若本机使用 nvm，**必须把 `~/.local/bin` 加在 nvm 之后**，否则 nvm 的 `bin` 会排在前面；一旦残留 npm 全局包，终端仍会命中 Node 那份。

在 `~/.zshrc` 中，nvm 加载之后增加：

```bash
export NVM_DIR="$HOME/.nvm"
[ -s "$NVM_DIR/nvm.sh" ] && \. "$NVM_DIR/nvm.sh"
[ -s "$NVM_DIR/bash_completion" ] && \. "$NVM_DIR/bash_completion"

# Native Claude Code（放在 nvm 之后，避免被 npm 全局 bin 抢走）
export PATH="$HOME/.local/bin:$PATH"
```

立即生效：

```bash
source ~/.zshrc
```

或新开一个终端。

---

## 6. 验证

```bash
which claude
# 必须是：/Users/<you>/.local/bin/claude
# 不能是：~/.nvm/versions/node/.../bin/claude

ls -la "$(which claude)"
file -L "$(which claude)"
# 应为：Mach-O 64-bit executable arm64

claude --version
# 例如：2.1.269 (Claude Code)
```

`which` 找不到、但直接敲 `claude` 报 `permission denied`，说明 PATH 里有一个**不可执行**的同名文件（常见于损坏的 npm 占位脚本）。见第 7 节。

---

## 7. 从损坏的 npm 安装迁移

早期用 `npm install -g @anthropic-ai/claude-code` 时，命令落在 nvm 的全局 bin。后台自动更新失败后，常见状态：

| 现象 | 原因 |
|------|------|
| `zsh: permission denied: claude` | `bin/claude` 指向不可执行的 500 字节占位脚本 `claude.exe` |
| `which claude` → `not found` | `which` 只列出可执行文件 |
| `npm uninstall -g` 报 `ENOTEMPTY` | 升级残留目录 `.claude-code-*` 挡住 rename |

`claude.exe` 是官方包在 Windows 上的入口名，Unix 会忽略后缀；**不是装成了 Windows 包**。占位脚本内容大意是：postinstall 没跑完，或平台原生 optional 依赖没下完。

### 7.1 清掉 npm 残留

在**当前正在用的 Node 版本**下执行（nvm 按版本隔离全局包）：

```bash
nvm use 20.15.1   # 按本机实际版本调整

NPM_ROOT="$(npm root -g)"
NPM_PREFIX="$(npm prefix -g)"

rm -rf "$NPM_ROOT/@anthropic-ai/claude-code"
rm -rf "$NPM_ROOT/@anthropic-ai/.claude-code-"*
rm -f "$NPM_PREFIX/bin/claude"

npm uninstall -g @anthropic-ai/claude-code
hash -r
```

确认：

```bash
npm ls -g --depth=0 @anthropic-ai/claude-code
# 应为 empty

ls "$(npm prefix -g)/bin/claude"
# No such file or directory
```

然后再走第 4 节官方安装。不要只 `chmod +x`：加上执行位后跑的仍是占位脚本。

---

## 8. 日常使用与更新

- 日常执行 `claude` 即可，运行时不经过 Node。
- Native 安装会在后台自动更新，更新仍访问 `downloads.claude.ai`。**代理未开时更新可能失败**，可能再次留下半安装状态。
- 更新前先开代理，或临时导出与第 4.1 节相同的 `HTTP_PROXY` / `HTTPS_PROXY`。
- 若希望手动更新：

```bash
export HTTP_PROXY=http://127.0.0.1:7890
export HTTPS_PROXY=http://127.0.0.1:7890
claude update
```

或重新跑一遍第 4.1 节安装命令。

- 关闭自动更新（可选，写入 `~/.claude/settings.json`）：

```json
{
  "autoUpdates": false
}
```

---

## 9. 常见问题

### 9.1 直连安装报 `未预期的符号“<”`

没有走代理，或代理未生效。先 `head -n 5` 确认脚本内容，再 `| bash`。

### 9.2 `which claude` 仍指向 nvm

`~/.local/bin` 没有进 PATH，或写在 nvm **之前**。按第 5 节把 `export PATH="$HOME/.local/bin:$PATH"` 放在 nvm 加载之后，然后 `hash -r` / 重开终端。

### 9.3 `ENOTEMPTY` 无法 npm uninstall

先删 `claude-code` 和 `.claude-code-*` 残留目录，再 uninstall。见第 7.1 节。不要对整个 `node_modules` 执行无路径限制的 `rm -rf`。

### 9.4 Homebrew 备选不可用

官方文档在脚本被拦截时建议 `brew install --cask claude-code`。若本机 Homebrew 过旧、不识别当前 macOS 版本（例如 macOS 26 + 旧 brew），不要走这条，继续用代理 + 官方脚本。

### 9.5 npm 重装（仅代理也连不上官方脚本时）

npm 装的是同一份原生二进制，运行时不调用 Node，但命令会跟着 nvm 版本走，且自动更新容易再次半失败。仅作兜底：

```bash
npm install -g --include=optional @anthropic-ai/claude-code
```

包声明 `engines.node >= 22`，Node 20 上 npm 通常只警告仍能装。不要把 default Node 仅仅为了 Claude 改成 22，否则切回 20 后命令会消失。

### 9.6 卸载官方安装

```bash
rm -f "$HOME/.local/bin/claude"
rm -rf "$HOME/.local/share/claude"
# 如不再需要，从 ~/.zshrc 去掉 ~/.local/bin 那一行
```

---

## 参考

- [Claude Code 安装文档](https://code.claude.com/docs/en/setup)
- [安装与登录排障](https://code.claude.com/docs/en/troubleshoot-install)
- 上游已知：npm 自动更新在 macOS 上留下 `claude.exe` 占位脚本 — [anthropics/claude-code#57178](https://github.com/anthropics/claude-code/issues/57178)
