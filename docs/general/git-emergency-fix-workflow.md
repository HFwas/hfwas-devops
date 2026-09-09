# Git 紧急修复工作流：本地未提交改动时处理突发缺陷

## 场景

本地正在开发**需求A**，改动尚未 commit，此时突然插入一个**紧急缺陷A**需要优先修复。目标是在不影响需求A的前提下，切换到缺陷A的修复工作。

---

## 方案一：git stash（推荐，简单快捷）

适合缺陷A改动较小、修复后立即返回的场景。

### 流程

```bash
# 1. 查看当前改动状态
git status

# 2. 暂存当前所有改动（包括未跟踪的新文件）
git stash push -u -m "需求A: 进行中，待暂存"

# 参数说明：
#   -u / --include-untracked  : 包含新增的未跟踪文件
#   -m "描述"                 : 给 stash 加标签，便于区分

# 3. 确认工作区已干净
git status

# 4. 切到修复分支，修复缺陷A
git checkout dev
# ... 修复代码 ...
git add .
git commit -m "fix: 修复缺陷A"

# 5. 切回原分支，恢复需求A的改动
git checkout -  # 切回上一个分支

# 恢复方式一：恢复并删除 stash 记录（推荐）
git stash pop

# 恢复方式二：仅恢复，保留 stash 记录（同名冲突时更安全）
git stash apply
```

### 常用 stash 命令

```bash
git stash list              # 查看所有 stash 记录
git stash show -p stash@{0} # 预览某个 stash 的具体改动
git stash drop stash@{0}    # 删除指定 stash
git stash clear             # 清空所有 stash
```

### 冲突处理

如果在 `git stash pop` 时出现冲突：

```bash
# 1. 手动解决冲突
# 2. 暂存已解决的文件
git add <冲突文件>
# 3. 放弃 stash 记录（pop 失败后 stash 仍保留，需手动删除）
git stash drop
```

---

## 方案二：git worktree（修复复杂或长时间并行时推荐）

适合缺陷A修复工作量大、需要长时间测试，或两个需求需要并行开发、同时运行。

### 核心原理

`git worktree` 允许你在**同一仓库中同时检出多个分支**到不同的工作目录，共享同一份 `.git` 对象存储，不占用双份磁盘空间。

```
.git/                          # 共享的核心仓库对象
├── objects/                   # 对象存储（所有分支共享）
├── refs/                      # 引用（所有分支共享）
├── HEAD                       # 主工作目录的 HEAD
└── worktrees/                 # 附加工作树的元数据
    ├── hotfix-A/
    │   ├── HEAD               # 独立 HEAD
    │   ├── index              # 独立暂存区
    │   └── commondir -> ../..
    └── ...

主仓库目录/                     # 主工作目录（默认）
├── src/ ...
├── ...

worktree-hotfix/               # 附加工作树
├── src/ ...
└── ...
```

- **对象存储共享**：`.git/objects` 只有一份，不占双份磁盘
- **HEAD 和 index 独立**：每个工作树有自己的 HEAD 和暂存区，互不干扰
- **分支不能重复检出**：同一个分支不能同时在两个工作树中被检出

### 常用命令

#### 创建附加工作树

```bash
# 基于 dev 分支创建，工作目录在 ../hotfix-A
git worktree add ../hotfix-A dev

# 创建并切换到新分支
git worktree add -b feature/new-feature ../new-feature main

# 基于当前 HEAD 创建（detached HEAD）
git worktree add ../temp HEAD

# 指定 commit 创建
git worktree add ../bug-hunt abc1234
```

#### 查看所有工作树

```bash
git worktree list
```

输出示例：

```
/Users/hfwas/project          dev          (主工作树)
/Users/hfwas/hotfix-a         fix/a        (附加工作树)
/Users/hfwas/feature-x        feat/x       (附加工作树)
```

#### 删除工作树

```bash
git worktree remove ../hotfix-a
```

#### 清理修剪

如果工作树目录被 Finder 或其它工具手动删除，Git 会留下脏记录：

```bash
git worktree prune
```

### 实战流程

```bash
# 1. 主工作区：正在开发需求A，有未提交改动
git status  # 一堆改动

# 2. 创建附加工作树，基于 dev 分支修复缺陷A
git worktree add ../hotfix-A dev

# 3. 在另一个终端窗口进入 ../hotfix-A
cd ../hotfix-A
# 修复缺陷A、提交
git add .
git commit -m "fix: 修复缺陷A"
git push

# 4. 切回主工作区继续开发，完全不受影响
cd /Users/hfwas/IdeaProjects/hfwas-devops

# 5. 修复完成后删除附加工作树
git worktree remove ../hotfix-A
```

### 更多实战场景

#### 场景 1：并行 review 他人 PR

```bash
# 主工作区：自己的开发
git worktree add ../review-pr-123 feature/PR-123
cd ../review-pr-123
# 运行、测试、review，完全不干扰主工作区
```

#### 场景 2：同时测试多个版本

```bash
git worktree add ../v1.0 v1.0
git worktree add ../v2.0 v2.0
git worktree add ../dev  dev
# 三个目录同时打开，分别运行不同版本
```

#### 场景 3：构建/发布脚本

```bash
# 在 CI 或发布脚本中，用 worktree 分离构建产物
git worktree add ../build-output deploy
cd ../build-output
./build.sh
git add -A
git commit -m "deploy: build $(date)"
git push origin deploy
```

### 注意事项

1. **分支不能重复检出**
   ```bash
   git worktree add ../hotfix dev
   git checkout dev  # ❌ fatal: 'dev' is already checked out at '../hotfix'
   ```

2. **删除前确保无未提交改动**
   ```bash
   git worktree remove ../hotfix
   # 如果工作树有未提交改动，会报错
   # 用 --force 强制删除
   git worktree remove --force ../hotfix
   ```

3. **主工作树不能被删除**
   ```bash
   git worktree remove .  # ❌ 不能删除主工作树
   ```

4. **相对路径**：`git worktree add` 的相对路径是相对于**当前工作目录**，而不是仓库根目录

5. **不同分支修改同一文件仍可能冲突**：虽然工作目录隔离了，但 `git pull` 时如果两个分支都改了同一个文件，pull 仍可能产生冲突

### 小技巧

```bash
# 用 alias 简化命令
git config --global alias.work 'worktree'
git work add ../hotfix dev  # 等价于 git worktree add ../hotfix dev

# 结合 IDE 使用：VS Code 中 File > Add Folder to Workspace
# 把多个 worktree 目录加到同一窗口，用侧边栏切换

# 在 CI/CD 中避免完整 git clone
git worktree add /var/www/project-release release
# /var/www/project-release 中就是 release 分支的代码
# 比完整克隆快得多
```

---

## 方案三：本地分支提交（适合需求A较完整时）

适合需求A已经开发到一定阶段，代码相对完整。

### 流程

```bash
# 1. 从当前分支创建新分支专门保存需求A
git checkout -b feature/feature-a

# 2. 提交需求A到本地分支（不推送）
git add .
git commit -m "feat: 需求A（开发中，未完成）"

# 3. 切回原分支修复缺陷A
git checkout dev
# ... 修复缺陷A ...
git add .
git commit -m "fix: 修复缺陷A"

# 4. 切回需求A分支继续开发
git checkout feature/feature-a

# 5. 后续可以将 dev 的最新代码合并到 feature-a
git merge dev
```

### 变体：临时 commit 后 rebase 清理

如果你不想保留这个半成品 commit，可以在后续合并到 dev 前 rebase 掉：

```bash
# 在 feature-a 分支开发完成后，回到 dev 分支
git checkout dev
git merge feature-a

# 如果只想把改动带过去而不保留 merge commit：
git checkout dev
git cherry-pick <feature-a的commit-hash>
# 然后删除 feature-a 分支
```

---

## 方案对比

| 方案 | 适用场景 | 优点 | 缺点 |
|------|---------|------|------|
| **stash** | 改动不大，快修快走 | 简单，一行命令，无需分支 | 冲突时需手动处理，长时间容易遗忘 |
| **worktree** | 修复复杂，需要长时间测试或并行 | 工作区完全隔离；可同时运行多个版本；共享对象存储，轻量 | 多一个目录；分支不能重复检出；需熟悉 worktree 命令 |
| **本地分支 commit** | 需求A已较完整 | 版本管理清晰，可随时切换 | 多了一个临时 commit，后续可能需要清理 |

---

## 特别篇：AI 辅助开发场景下为什么 worktree 更合适

如果你使用 AI 编程助手（如 Claude Code、Cursor、GitHub Copilot 等）进行开发，**worktree 的优势会更加突出**，原因如下：

### 1. AI 需要读取完整的工作区上下文

AI 助手在理解代码时，会扫描当前工作区所有文件。如果需求A和缺陷A的改动混在同一个工作区：

```
# ❌ 混在一起 — AI 分不清哪些是需求A，哪些是缺陷A的改动
src/user/login.ts          # 需求A：加了新参数
src/user/register.ts       # 缺陷A：修复空指针
src/user/profile.ts        # 需求A：改样式
```

AI 看到的是一堆改动，**难以区分哪些改动用在哪次任务上**，生成的代码可能互相干扰。

### 2. stash 对 AI 不可见

```bash
git stash push -m "需求A进行中"
# AI 助手现在完全看不到需求A的代码
```

AI 没法读取 stash 中的内容，它会基于"不完整"的代码状态生成建议，修复缺陷A时**可能破坏需求A的设计意图**。

### 3. worktree 天然适合 AI 的工作模式

```bash
# 创建需求A的工作树
git worktree add ../feature-a feature-a

# 在原目录用 AI 修复缺陷A
# AI 看到的只有缺陷A的改动，不会被需求A干扰
```

| AI 开发特性 | stash | worktree |
|------------|-------|----------|
| AI 读取完整上下文 | ✅ 能看到，但混在一起 | ✅ 干净，只有当前任务 |
| AI 理解任务边界 | ❌ 改动混在一起 | ✅ 每个工作树一个任务 |
| 多任务并行 | ❌ 只能串行 | ✅ 每个工作树独立 |
| 修改后立即验证 | ❌ 需 pop 才能验证 | ✅ 直接运行验证 |
| 绕过 AI 的 token 限制 | ❌ 无关文件消耗 token | ✅ 每个工作树只加载必要文件 |

### 4. 实际案例：Claude Code 的 worktree 集成

像 Claude Code 这类 AI 工具，已经内置了 worktree 的支持：

```
# Claude Code 中直接用 /worktree 命令创建
# 它会自动创建一个隔离的工作树，新任务在新工作树中完成
# 原工作树的代码不受影响，AI 也只看到当前任务的文件
```

### 5. AI 开发的推荐工作流

```bash
# 1. 主仓库：保留干净的 dev 分支
git checkout dev

# 2. 任务A：用 worktree 创建独立工作区
git worktree add -b feature/logout ../feature-logout dev
# 在 ../feature-logout 中用 AI 开发功能A

# 3. 紧急缺陷A：用另一个 worktree 创建修复工作区
git worktree add -b hotfix/login ../hotfix-login dev
# 在 ../hotfix-login 中用 AI 修复缺陷A，完全不受影响

# 4. 两个工作区可以同时给 AI 操作，互不干扰
```

### 小结

| 场景 | 推荐方案 | 原因 |
|------|---------|------|
| 手动开发，快速修复 | stash | 简单，够用 |
| 手动开发，复杂修复 | worktree | 隔离性好 |
| **AI 辅助开发** | **worktree** ✅ | **AI 需要干净上下文，stash 对 AI 不可见** |

**一句话总结：AI 开发中，worktree 提供的"任务即工作区"的隔离模式，完美匹配 AI 的工作方式。stash 在 AI 面前等于"藏起来"，而 worktree 是"打开一个干净的窗口给 AI 看"。**

---

## 最佳实践建议

1. **stash 解决"几分钟切一下"，worktree 解决"几小时甚至几天并行"**：快速切换用 stash，长时间并行开发用 worktree。

2. **stash 打标签**：始终用 `-m "描述"`，避免 `stash list` 多了以后分不清。

3. **长时间不恢复时**：如果 stash 超过一天还没恢复，建议用 `git stash show -p` 先确认内容，再决定是否 pop。

4. **多层级 stash**：可以同时有多个 stash，用 `stash@{0}`、`stash@{1}` 索引区分。

5. **避免 push 前 stash**：`git stash pop` 后记得确认代码完整性，再 push。

6. **worktree 用完后及时删除**：`git worktree remove` 删除目录，或 `git worktree prune` 清理残留记录，避免工作树越积越多。

---

## 常见问题

### Q: stash 后忘了恢复，直接 push 了怎么办？

A: 不要慌，stash 记录不会因 push 而丢失。用 `git stash list` 查看，`git stash pop` 恢复即可。

### Q: stash pop 冲突太多，想放弃恢复怎么办？

A: 用 `git reset --hard HEAD` 放弃所有冲突，然后 `git stash drop` 删除该 stash。

### Q: 需求A已经 add 了一部分，不想全部 stash？

A: 可以用交互式 stash：

```bash
git stash push -p -m "需求A部分改动"
```

这会逐个 hunk 询问是否暂存，只暂存你选择的改动。