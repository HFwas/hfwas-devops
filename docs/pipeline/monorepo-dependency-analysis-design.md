# Monorepo 多语言依赖分析方案

> 日期：2026-09-13
> 版本：v0.1
> 定位：解决 Maven + Node.js 等混合语言 Monorepo 项目的依赖分析问题，扩展 `DEPENDENCY_ANALYSIS` 任务类型使其按模块自动选择扫描工具
> 状态：已定稿

---

### 变更记录

| 版本 | 日期 | 变更说明 |
|------|------|----------|
| v0.1 | 2026-09-13 | 初版：模块扫描方案 + 运行时自动检测 + 三种架构方案对比 |

---

## 1. 背景与问题

### 1.1 问题描述

当前 `DEPENDENCY_ANALYSIS` 任务类型只支持单语言栈扫描，用户创建依赖扫描流水线时传入 `stack` 参数（如 `JAVA_MAVEN`），**整个仓库按一种语言处理**。

但实际项目往往是 **Monorepo** 结构，一个仓库内包含多种语言的子项目：

```
my-monorepo/
├── pom.xml                    ← Maven 根（后端 Java）
├── backend/
│   ├── server/
│   ├── pipeline-core/
│   └── ...
└── frontend/
    └── package.json           ← npm 前端
```

hfwas-devops 项目本身就是典型：Maven 多模块后端（10+ 子模块）+ Vue 3 前端（~24 个 NPM 包）。

### 1.2 现有方案的局限

| 方案 | 效果 | 局限 |
|------|------|------|
| 只跑 Maven Plugin | ✅ Java 后端依赖完整 | ❌ 前端 NPM 包全部漏掉 |
| 只跑 cdxgen | ✅ 前后端都能扫 | ❌ Java 精度不如 Maven Plugin |
| 一个 shell 脚本二选一（设计文档 7.2 方式三） | ✅ 按需选择 | ❌ 仍然是二选一，不是合并 |

### 1.3 设计目标

- **Monorepo 友好**：一个仓库多个语言栈，每个模块按需扫描
- **精度优先**：Java 用 Maven Plugin（Maven Resolver API），其他用 cdxgen
- **运行时自动检测**：无需用户在 API 层指定语言，容器内检测 manifest 文件自动路由
- **零配置介入**：用户只需选择模块路径（如 `frontend/`），语言检测和工具选择自动完成
- **兼容存量**：不破坏已有单语言栈的扫描流程

---

## 2. 方案设计

### 2.1 核心思路：模块扫描 + 运行时自动检测

```
用户选择模块路径（可选）
    │
    ▼
运行时（容器内）cd 到模块目录
    │
    ├── pom.xml 存在 → CycloneDX Maven Plugin（Java，精度最高）
    ├── package.json 存在 → cdxgen（Node.js）
    ├── go.mod 存在 → cdxgen（Go）
    ├── requirements.txt / Pipfile / pyproject.toml → cdxgen（Python）
    └── 无匹配 → 跳过（exit 0）
```

### 2.2 API 层改动

在 `DependencyScanSubmitDTO` 中新增 `modulePath` 字段：

```json
{
  "repoUrl": "https://github.com/org/my-monorepo.git",
  "modulePath": "frontend",
  "stack": "NODE"
}
```

| 字段 | 必填 | 说明 |
|------|------|------|
| `repoUrl` | ✅ | 仓库地址 |
| `modulePath` | ❌ | 模块路径，如 `backend/`、`frontend/`；为空则扫仓库根 |
| `stack` | ❌ | 语言栈，如 `JAVA_MAVEN`/`NODE`；为空则运行时自动检测 |
| `gitRef` | ❌ | 分支/标签，默认 `main` |

**行为矩阵**：

| modulePath | stack | 行为 |
|------------|-------|------|
| 空 | 空 | 仓库根目录自动检测（向后兼容） |
| `backend/` | `JAVA_MAVEN` | `cd backend/` + Maven Plugin（最确定最快） |
| `frontend/` | 空 | `cd frontend/` + 运行时检测 manifest → cdxgen |
| `frontend/` | `NODE` | `cd frontend/` + cdxgen（跳过检测） |
| 空 | `JAVA_MAVEN` | 仓库根 Maven Plugin（等于原来的行为） |

### 2.3 命令生成

**`buildCommand(stack, modulePath)`**：

- 指定 stack：`cd <modulePath> && mvn dependency:resolve ...`
- 未指定 stack + 有 modulePath：`cd <modulePath> && if [ -f pom.xml ] ... elif [ -f package.json ] ...`
- 其余情况：`echo 'no build needed'`

**`dependencyAnalysisCommand(stack, modulePath)`**：

- 指定 stack：`cd <modulePath> && <stack-specific-tool>`
- 未指定 stack：`cd <modulePath> && if [ -f pom.xml ] ... elif [ -f package.json ] ... elif [ -f go.mod ] ...`

### 2.4 运行时自动检测脚本

```bash
cd <modulePath>

if [ -f "pom.xml" ]; then
  echo "detected Maven project, using CycloneDX Maven Plugin"
  mvn org.cyclonedx:cyclonedx-maven-plugin:2.9.3:makeAggregateBom \
    -Dcyclonedx.outputFormat=json -DoutputName=sbom --no-transfer-progress

elif [ -f "package.json" ]; then
  echo "detected Node.js project, using cdxgen"
  cdxgen -o target/sbom.json -t cyclonedx:json

elif [ -f "go.mod" ]; then
  echo "detected Go project, using cdxgen"
  cdxgen -o target/sbom.json -t cyclonedx:json

elif [ -f "requirements.txt" ] || [ -f "Pipfile" ] || [ -f "pyproject.toml" ]; then
  echo "detected Python project, using cdxgen"
  cdxgen -o target/sbom.json -t cyclonedx:json

else
  echo "no supported language manifest detected in module, skip dependency analysis"
  exit 0
fi
```

### 2.5 模块路径规范化

`DependencyScanService.normalizeModulePath()`:

| 输入 | 输出 | 说明 |
|------|------|------|
| `null` | `null` | 无模块 |
| `""` | `null` | 空字符串 |
| `"  "` | `null` | 空白 |
| `"backend"` | `"backend"` | 正常 |
| `"backend/"` | `"backend"` | 去尾部斜杠 |
| `"frontend///"` | `"frontend"` | 多斜杠 |
| `"frontend/src/"` | `"frontend/src"` | 保留内部路径 |

---

## 3. 三种架构方案对比

### 方案 A：单任务 + 自动检测脚本（✅ 已实现，推荐）

**思路**：一个 DEPENDENCY_ANALYSIS Task，`cd` 到模块路径后用 shell 脚本自动检测 manifest 并路由工具。

```
Tekton Task: DEPENDENCY_ANALYSIS
  Step:
    1. cd <modulePath>
    2. if pom.xml → mvn cyclonedx:makeAggregateBom
       elif package.json → cdxgen
       elif go.mod → cdxgen
       ...
    3. 上传 target/sbom.json
```

**优点**：
- 改动最小，只改 `DependencyScanService` 的 command 拼接
- 不涉及 TektonCompiler、PipelineGraph 改动
- 一次上传一个 SBOM，后端解析逻辑不变
- 兼容原有单语言栈流程

**缺点**：
- 同一 Task 内工具切换依赖于 shell 条件分支
- 容器镜像需同时包含 mvn 和 cdxgen（当前 `tool_image` = `maven:3.9.9-eclipse-temurin-21` 不包含 cdxgen）
- 整体镜像体积较大

**代码改动量**：~60 行（DTO + Service）

### 方案 B：纯 cdxgen 方案（最简）

**思路**：完全放弃 Maven Plugin，只用 cdxgen 扫描模块目录。

```
cd <modulePath> && cdxgen -o target/sbom.json -t cyclonedx:json --recurse
```

`--recurse` 默认开启，cdxgen 会自动递归检测子目录中所有语言的 manifest 文件。

**优点**：
- 最简：单镜像、单命令、单输出
- 再加其他语言（Go/Python/Rust）0 成本
- 无需修改 Task 市场默认配置

**缺点**：
- Java 精度不如 Maven Plugin：cdxgen 通过解析 pom.xml + 执行 `mvn dependency:tree` 间接解析，在传递依赖完整度和多模块 Reactor 聚合上不如 Maven Plugin（详见设计文档 2.2 节）
- 需要改 `tool_image` 为 `ghcr.io/cyclonedx/cdxgen:v11.0.0`
- 当前项目已锁定 Maven Plugin 2.9.3，切换成本高

### 方案 C：多任务编排（长期）

**思路**：流水线编排多个 DEPENDENCY_ANALYSIS Task，各自用不同的 tool_image 和 command。

```
Stage "后端依赖分析":
  Job: DEPENDENCY_ANALYSIS (tool_image=maven:3.9.9,       command=mvn cyclonedx:...)

Stage "前端依赖分析":
  Job: DEPENDENCY_ANALYSIS (tool_image=cdxgen:v11.0.0,    command=cdxgen frontend/)
```

**优点**：
- 每个语言的工具镜像独立，各自最优
- 解耦，后续加语言只需加 Stage
- 不改现有命令和镜像策略

**缺点**：
- 上传两份 SBOM → 后端需合并或分两次解析
- 前端 / 后端的 `target/sbom.json` 会覆盖 → 需要不同文件名
- 需要改动 PipelineGraph 模型、TektonCompiler、前端 JobCatalog
- 改动面大，周期长

### 3.1 方案对比总表

| 维度 | 方案 A（自动检测） | 方案 B（纯 cdxgen） | 方案 C（多任务） |
|------|:-:|:-:|:-:|
| 实现成本 | **低** | **最低** | **高** |
| Java 精度 | **最高**（Maven Plugin） | 中（cdxgen） | **最高**（Maven Plugin） |
| Node 精度 | 高（cdxgen） | 高（cdxgen） | 高（cdxgen） |
| 容器镜像 | 大（mvn + cdxgen） | 小（仅 cdxgen） | 灵活（各自独立） |
| 后端改动 | 1 个 Service 方法 | 1 个 Service 方法 | TektonCompiler + API |
| SBOM 上传 | 1 份 | 1 份 | 2 份（需合并） |
| 扩展新语言 | 加 elif 分支 | 无需改动 | 加新 Stage |

---

## 4. 实现细节

### 4.1 `DependencyScanSubmitDTO`

```java
@Data
public class DependencyScanSubmitDTO {
    private String repoUrl;
    private String gitRef;
    private Long credentialId;
    private String stack;          // JAVA_MAVEN / NODE / GO / PYTHON，为空则自动检测
    private String runtimeVersion;
    private String modulePath;     // 模块路径，如 backend/ frontend/，为空则扫仓库根目录
}
```

### 4.2 `DependencyScanService` 关键方法

```java
// 构建命令
static String buildCommand(String stack, String modulePath) {
    String prefix = modulePath != null ? "cd " + modulePath + " && " : "";
    if (stack != null && !stack.isBlank()) {
        return prefix + switch (stack) { ... };
    }
    // stack 为空 → 运行时自动检测
    if (modulePath != null) {
        return prefix + autoDetectBuild();
    }
    return "echo 'no build needed'";
}

// 依赖分析命令
static String dependencyAnalysisCommand(String stack, String modulePath) {
    String prefix = modulePath != null ? "cd " + modulePath + " && " : "";
    if (stack != null && !stack.isBlank()) {
        return prefix + switch (stack) {
            case "JAVA_MAVEN" -> "mvn org.cyclonedx:...:makeAggregateBom ...";
            default -> "cdxgen -o target/sbom.json -t cyclonedx:json";
        };
    }
    // stack 为空 → 运行时自动检测
    return prefix + autoDetectAnalysis();
}
```

### 4.3 模块路径规范化

```java
static String normalizeModulePath(String modulePath) {
    if (modulePath == null || modulePath.isBlank()) return null;
    String normalized = modulePath.trim();
    while (normalized.endsWith("/") || normalized.endsWith("\\")) {
        normalized = normalized.substring(0, normalized.length() - 1);
    }
    return normalized.isBlank() ? null : normalized;
}
```

### 4.4 SBOM 上传路径

当前上传脚本（`TektonCompiler.dependencyAnalysisScript()`）通过 `find . \( -path '*/target/sbom.json' ... \)` 查找 SBOM 文件。当命令在模块目录内执行时：

```
workspace:
  /workspace/source/src/
    ├── backend/
    │   └── target/sbom.json   ← Maven Plugin 产出
    └── frontend/
        └── target/sbom.json   ← cdxgen 产出（如有）
```

`find` 命令会递归搜索，两种都能找到。上传到 `/pipeline/runs/{runId}/artifacts`，后端按 `(run_id, artifact_type)` 区分。

---

## 5. 使用场景

### 5.1 扫描前端模块（自动检测）

```bash
curl -X POST /dependency-scan/submit \
  -H "Content-Type: application/json" \
  -d '{
    "repoUrl": "https://github.com/org/hfwas-devops.git",
    "modulePath": "frontend"
  }'
```

- 容器内执行 `cd frontend && if [ -f package.json ] ...` → 检测到 `package.json`
- 走 `cdxgen -o target/sbom.json -t cyclonedx:json`
- SBOM 中只包含前端 NPM 依赖

### 5.2 扫描后端模块（明确指定语言）

```bash
curl -X POST /dependency-scan/submit \
  -H "Content-Type: application/json" \
  -d '{
    "repoUrl": "https://github.com/org/hfwas-devops.git",
    "modulePath": "backend",
    "stack": "JAVA_MAVEN"
  }'
```

- `cd backend && mvn org.cyclonedx:...:makeAggregateBom`
- SBOM 包含 Java 后端完整传递依赖树
- 跳过自动检测，直接执行 Maven Plugin

### 5.3 扫描 Go 子模块

```bash
curl -X POST /dependency-scan/submit \
  -H "Content-Type: application/json" \
  -d '{
    "repoUrl": "https://github.com/org/monorepo.git",
    "modulePath": "services/gateway"
  }'
```

- `cd services/gateway && if [ -f go.mod ] ...` → 检测到 `go.mod`
- 走 `cdxgen -o target/sbom.json -t cyclonedx:json`

### 5.4 全量扫描（单语言项目，兼容旧行为）

```bash
curl -X POST /dependency-scan/submit \
  -H "Content-Type: application/json" \
  -d '{
    "repoUrl": "https://github.com/org/java-app.git",
    "stack": "JAVA_MAVEN"
  }'
```

- 和原来完全一样，仓库根目录跑 Maven Plugin

### 5.5 新增语言栈的扩展

当需要支持新的语言栈时，只需在 `autoDetectAnalysis()` 和 `autoDetectBuild()` 方法中新增 `elif` 分支：

```bash
elif [ -f "Cargo.toml" ]; then
  echo "detected Rust project, using cdxgen"
  cdxgen -o target/sbom.json -t cyclonedx:json
elif [ -f "Gemfile" ] || [ -f "Gemfile.lock" ]; then
  echo "detected Ruby project, using cdxgen"
  cdxgen -o target/sbom.json -t cyclonedx:json
```

cdxgen 覆盖 50+ 语言，大部分语言不需要调整镜像即可支持。

---

## 6. 涉及修改的文件清单

| 位置 | 文件 | 改动 |
|------|------|------|
| DTO | `DependencyScanSubmitDTO.java` | 新增 `modulePath` 字段 |
| 服务 | `DependencyScanService.java` | 新增 `buildCommand()` / `dependencyAnalysisCommand()` / `autoDetectBuild()` / `autoDetectAnalysis()` / `normalizeModulePath()` |
| 测试 | `DependencyScanServiceTest.java` | 新增 modulePath 相关测例、命令生成单元测试、normalize 单元测试 |
| 设计文档 | `docs/pipeline/monorepo-dependency-analysis-design.md` | 本文 |

---

## 7. 未解决问题

| 问题 | 说明 |
|------|------|
| 容器镜像 | 自动检测脚本需要同时包含 `mvn` 和 `cdxgen`。当前 `tool_image` = `maven:3.9.9-eclipse-temurin-21` 不包含 cdxgen。短期可改为 `ghcr.io/cyclonedx/cdxgen:v11.0.0`（自带 JDK + Maven），或用定制镜像。 |
| 多个模块一次扫描 | 当前一次 API 调用只能指定一个模块路径。如果需要一次扫描整个 Monorepo 的所有模块，可以用 batch API 传多个 `(stack, modulePath)` 对，或后续实现全量递归检测。 |
| 上传文件路径 | `cd` 到模块目录后，`target/sbom.json` 在 `<module>/target/sbom.json`。find 模式能搜到，但文件名不含模块信息。后续可在 artifact 元数据中记录模块路径。 |
| Tekton mirror 策略 | 如果 `tool_image` 使用 cdxgen 镜像，参考 `docs/container-platform/helm-install-and-config.md` 对 `ghcr.io/cyclonedx/cdxgen` 配置 harbor mirror，避免拉取失败。 |

---

## 8. 附录

### 8.1 相关文档

| 文档 | 路径 | 说明 |
|------|------|------|
| 依赖分析任务设计 | `docs/pipeline/dependency-analysis-design.md` | DEPENDENCY_ANALYSIS 任务原始设计 |
| 任务市场设计 | `docs/pipeline/pipeline-task-marketplace-design.md` | 14 种任务类型的数据库设计与 API |
| 镜像选择策略 | `docs/pipeline/pipeline-toolchain-image-strategy.md` | 固定镜像与动态镜像的选取规则 |
| Tekton 详解 | `docs/pipeline/tekton-intro.md` | Tekton 对象模型、数据传递、运行时序 |

### 8.2 决策记录

| 决策项 | 结论 | 状态 |
|--------|------|------|
| 核心方案 | 单 Task + 运行时自动检测脚本（方案 A） | ✅ 已实现 |
| Java 扫描工具 | CycloneDX Maven Plugin 2.9.3 | ✅ 保持原决策 |
| 非 Java 扫描工具 | cdxgen v11.0.0 | ✅ 保持原决策 |
| 模块路径 | DTO 新增 `modulePath` 字段 | ✅ 已实现 |
| 语言检测时机 | 容器运行时检测 manifest 文件 | ✅ 已实现 |
| stack 为空行为 | 走自动检测脚本；modulePath 为空时扫根目录 | ✅ 已实现 |