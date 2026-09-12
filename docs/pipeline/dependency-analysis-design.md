# 流水线依赖分析任务设计方案

> 日期：2026-09-12
> 版本：v0.3
> 定位：在流水线中增加一个**通用依赖分析任务**，支持多种语言项目的依赖扫描，生成标准 SBOM 作为后续漏洞检测的前置输入
> 状态：已定稿

---

### 变更记录

| 版本 | 日期 | 变更说明 |
|------|------|----------|
| v0.3 | 2026-09-12 | 更新版本为 2.9.3（2.10.0 在 Maven Central 尚无），修正 CLI 参数 `-Dcyclonedx.outputName` → `-DoutputName`，修正 SBOM specVersion 为 1.6 |
| v0.2 | 2026-09-12 | 新增 SBOM 持久化与下载设计（5.3），补充 artifact 存储、API、前端展示 |
| v0.1 | 2026-09-12 | 初版：多语言依赖分析任务设计，Java 用 CycloneDX Maven Plugin，其他语言用 cdxgen |

---

## 1. 背景与目标

### 1.1 现状

当前流水线系统支持 14 种任务类型（JobKind），支持 **4 种编程语言栈**：

| 语言栈 | 构建工具 | 依赖管理文件 |
|--------|---------|-------------|
| JAVA_MAVEN | Maven | `pom.xml` |
| NODE | npm / pnpm / yarn | `package-lock.json` / `pnpm-lock.yaml` / `yarn.lock` |
| GO | go | `go.mod` / `go.sum` |
| PYTHON | pip | `requirements.txt` / `Pipfile.lock` / `pyproject.toml` / `poetry.lock` |

流水线中已有 `SCAN` 安全扫描任务，但缺少一个**通用的、结构化的依赖清单生成环节**，导致：

- 无法在不同语言项目间统一获得标准化的依赖清单
- 漏洞扫描缺少精确的输入（只能依赖文件系统的全量扫描）
- 难以与外部 SBOM 管理平台（如 Dependency-Track）对接

### 1.2 目标

- 在流水线中新增**依赖分析**任务类型（`DEPENDENCY_ANALYSIS`）
- **多语言覆盖**：Java/Maven 使用 CycloneDX Maven Plugin（Maven 原生解析最精确），Node/Go/Python 等使用 cdxgen（通用 SBOM 工具）
- **专注依赖获取**：只负责依赖发现 + SBOM 生成，**不执行漏洞扫描**
- **不动客户代码**：全程通过 CLI 调用工具，不修改任何项目文件
- **标准化输出**：统一输出 CycloneDX JSON 格式 SBOM
- **漏洞检测后置**：SBOM 作为后续漏洞扫描任务的输入，漏洞检测另行设计

### 1.3 核心设计原则

```
DEPENDENCY_ANALYSIS（依赖分析）                   漏洞扫描（后续设计）
       ↓                                                  ↑
  只做依赖发现 + SBOM 生成                   消费 SBOM 做漏洞匹配
  按语言栈选择最优工具                        支持 Trivy / Grype /
  Java → Maven Plugin                         Dependency-Track 等
  其他 → cdxgen                                      ↑
       ↓                                                  |
  输出: target/sbom.json  ──── 标准接口 ───────────→ 任意 CycloneDX 工具
```

---

## 2. 工具选型

### 2.1 候选方案总览

| 工具 | 适用语言 | 定位 | 依赖解析方式 |
|------|---------|------|-------------|
| **CycloneDX Maven Plugin** | Java (Maven) | Maven 原生插件 | 通过 Maven Resolver API 获取完整依赖树 |
| **cdxgen** | Java / Node / Go / Python / Ruby / Rust / PHP 等 50+ | CycloneDX 官方通用工具 | 各自语言的包管理器 CLI 或文件解析 |
| Syft | Java / Node / Go / Python / 等 | Anchore 系通用 SBOM 工具 | 文件系统扫描 + 语言解析器 |
| Trivy fs | 偏 OS 包 + 锁文件 | 漏洞扫描（顺带 SBOM） | 文件系统扫描 |
| Language native | 单个语言 | 各语言自带 CLI | 如 `mvn dependency:tree` / `go list -m all` |

### 2.2 分语言推荐

#### Java (Maven)：CycloneDX Maven Plugin

```
工具：  mvn org.cyclonedx:cyclonedx-maven-plugin:2.9.3:makeAggregateBom
调用：  通过 Maven CLI 临时调用，不修改 pom.xml
精度：  最高——使用 Maven Resolver API，完整呈现直接 + 传递依赖、scope、依赖仲裁
```

**优点**：
- Maven 自身解析依赖树，与 `mvn dependency:tree` 同级别精度
- `makeAggregateBom` 完美支持多模块 Reactor 项目
- CycloneDX 官方出品，格式兼容性最佳
- 无需下载额外的漏洞库或索引

**为什么不直接用 cdxgen 做 Java**：
cdxgen 也支持 Maven，但它通过解析 `pom.xml` + `mvn dependency:tree` 输出来构建 SBOM，属于间接解析，在传递依赖完整度和多模块聚合上不如 Maven Plugin 直接。

#### Node / Go / Python 等：cdxgen

```
工具：  cdxgen -o target/sbom.json -t cyclonedx:json
调用：  单条命令，自动检测语言栈
精度：  高——直接读取 lockfile + 包管理器输出
```

**cdxgen 支持的语言栈矩阵**：

| 语言 | 检测文件 | 依赖来源 |
|------|---------|---------|
| **Node.js** | package-lock.json / yarn.lock / pnpm-lock.yaml | lockfile + node_modules |
| **Go** | go.mod / go.sum | go.mod + module cache |
| **Python** | Pipfile.lock / poetry.lock / requirements.txt / pyproject.toml | lockfile / pip metadata |
| **Java (备选)** | pom.xml / build.gradle | Maven/Gradle CLI（在不方便用 Maven Plugin 时） |
| **Ruby** | Gemfile.lock | lockfile |
| **Rust** | Cargo.lock | lockfile |
| **.NET** | *.csproj / packages.lock.json | NuGet |
| **PHP** | composer.lock | lockfile |

### 2.3 对比总结

| 维度 | CycloneDX Maven Plugin（Java） | cdxgen（其他语言） |
|------|-------------------------------|-------------------|
| 定位 | Maven 依赖的精确解析 | 通用多语言 SBOM 生成 |
| 输出格式 | CycloneDX XML/JSON | CycloneDX JSON/XML |
| 是否需要安装依赖 | 需要 `~/.m2` 完整缓存 | 需要 lockfile 或包管理器缓存 |
| 是否需要修改项目文件 | **不需要**（CLI 临时调用） | **不需要** |
| 是否需要漏洞库 | **不需要**（纯依赖分析） | **不需要**（纯依赖分析） |
| 官方维护 | OWASP CycloneDX 项目 | OWASP CycloneDX 项目 |
| 容器镜像 | 复用 Maven 工具链镜像 | 专用 cdxgen 镜像 |

### 2.4 工具链关系

```
源代码（Java / Go / Python / Node）

  → BUILD 阶段（编译 / 安装依赖）

      → DEPENDENCY_ANALYSIS

          ├── pom.xml 存在 → CycloneDX Maven Plugin
          │    mvn org.cyclonedx:cyclonedx-maven-plugin:2.9.3:makeAggregateBom \
          │      -Dcyclonedx.outputFormat=json \
          │      -Dcyclonedx.outputName=sbom
          │    → target/sbom.json（Java 多模块聚合，精确依赖树）
          │
          └── 其他项目 → cdxgen
               cdxgen -o target/sbom.json -t cyclonedx:json
               → target/sbom.json（自动检测语言栈，通用依赖提取）

      → target/sbom.json（标准接口，语言无关）

          → [下游] 漏洞扫描（后续设计）
          → [下游] 依赖清单查看
          → [下游] Dependency-Track 上报
```

---

## 3. SBOM 生成：两种工具

### 3.1 Java/Maven：CycloneDX Maven Plugin

#### 命令

```bash
# 无需修改 pom.xml，通过 CLI 临时调用
cd /workspace/source
mvn org.cyclonedx:cyclonedx-maven-plugin:2.9.3:makeAggregateBom \
  -Dcyclonedx.outputFormat=json \
  -DoutputName=sbom \
  --no-transfer-progress \
  -q
```

#### 参数说明

| 参数 | 作用 |
|------|------|
| `org.cyclonedx:cyclonedx-maven-plugin:2.9.3:makeAggregateBom` | 插件坐标 + 版本 + goal，完全限定避免搜索 |
| `-Dcyclonedx.outputFormat=json` | 输出 JSON 格式 |
| `-DoutputName=sbom` | 输出文件名（不含后缀）。注意：2.9.x 使用 `-DoutputName`（不带 `cyclonedx.` 前缀）; 后续版本可能恢复为 `-Dcyclonedx.outputName` |
| `--no-transfer-progress` | 减少 Maven 下载进度日志 |
| `-q` | 静默模式 |

> 插件自动从 `~/.m2` 和 `target/` 中读取已解析的依赖。

#### 工作流程

```
mvn cyclonedx:makeAggregateBom 执行时：
  1. Maven 加载 Reactor 所有模块
  2. 为每个模块收集 compile + runtime + provided scope 的依赖
  3. 应用 Maven 的依赖仲裁（nearest-wins、exclusion、optional）
  4. 聚合所有模块的依赖树去重
  5. 输出 CycloneDX JSON 到 ${project.build.directory}/sbom.json
  6. 多模块情况下，输出文件在项目根目录的 target/ 下
```

#### 版本锁定

锁定 `2.9.3`。后续升级需手动验证。不建议使用 `LATEST` 或 `RELEASE`。

> **注意**：设计文档原定锁定 `2.10.0`，但该版尚未发布到 Maven Central（截至 2026-09-12 最新 release 为 `2.9.3`），实际验证后回退锁定至此版。

### 3.2 其他语言：cdxgen

#### 命令

```bash
# 自动检测语言栈，通用调用
cd /workspace/source
cdxgen -o target/sbom.json -t cyclonedx:json
```

#### 参数说明

| 参数 | 作用 |
|------|------|
| `-o target/sbom.json` | 输出文件路径 |
| `-t cyclonedx:json` | 输出格式为 CycloneDX JSON |

#### cdxgen 自动检测逻辑

```
cdxgen 扫描工作区：

  检测到 package-lock.json / yarn.lock / pnpm-lock.yaml
    → Node 模式：解析 lockfile，遍历 node_modules 提取实际版本

  检测到 go.mod
    → Go 模式：go mod graph + go mod why，提取完整 module 树

  检测到 Pipfile.lock / poetry.lock / requirements.txt / pyproject.toml
    → Python 模式：按优先级读取 lockfile 或 metadata

  检测到 Gemfile.lock
    → Ruby 模式

  检测到 Cargo.lock
    → Rust 模式

  检测到 composer.lock
    → PHP 模式
```

#### 版本锁定

锁定 `cdxgen:v11.0.0`（`ghcr.io/cyclonedx/cdxgen:v11.0.0`）。后续升级需验证对各语言栈的解析准确性。

### 3.3 输出规格

| 属性 | Java（Maven Plugin） | 其他（cdxgen） |
|------|--------------------|---------------|
| 输出格式 | CycloneDX JSON | CycloneDX JSON |
| 输出路径 | `target/sbom.json` | `target/sbom.json` |
| SBOM 版本 | 1.6 | 1.6+ |
| 组件标识 | purl + groupId:artifactId:version | purl + name + version |
| 依赖树 | 完整传递依赖 + scope | 视 lockfile 而定 |
| 多模块聚合 | 支持（makeAggregateBom） | 不支持（单目录扫描） |

> 两套工具最终产出**相同格式的 CycloneDX JSON 文件**，下游无需区分来源。

### 3.4 各语言前置条件

| 语言栈 | 工具 | 前置条件 | 依赖完整性 |
|--------|------|---------|-----------|
| **JAVA_MAVEN** | Maven Plugin | 已执行 `mvn dependency:resolve` 或 `mvn package` | 最高（Maven Resolver） |
| **NODE** | cdxgen | 有 `package-lock.json` 或 `node_modules` | 高（lockfile 精准） |
| **GO** | cdxgen | 已完成 `go mod download` | 高（go.mod 完整） |
| **PYTHON** | cdxgen | 有 lockfile 或已 `pip install` | 中~高（视 lockfile 而定） |

---

## 4. 镜像策略

DEPENDENCY_ANALYSIS 需要根据项目类型选择 **两种容器镜像之一**：

| 项目类型 | 镜像 | 来源 | 说明 |
|---------|------|------|------|
| **Java (Maven)** | 工具链 Maven 镜像 | `maven:3.9.9-eclipse-temurin-21` | 复用 BUILD 阶段的 Maven 镜像（已有） |
| **其他 (Node/Go/Python)** | cdxgen 镜像 | `ghcr.io/cyclonedx/cdxgen:v11.0.0` | **新增** |

### 4.1 Java 项目：复用工具链镜像

Java 项目的 DEPENDENCY_ANALYSIS 使用与 BUILD 相同的 Maven 工具链镜像（如 `maven:3.9.9-eclipse-temurin-21`），因为 CycloneDX Maven Plugin 依赖 Maven 运行时和缓存。

```
PipelineRun 中：
  BUILD task（maven:3.9.9-eclipse-temurin-21）
    → mvn package → 填充 ~/.m2 + target/
  DEPENDENCY_ANALYSIS task（同一 Maven 镜像）
    → mvn cyclonedx:makeAggregateBom → 复用 ~/.m2 缓存
```

**⚡ 优化提示**：如果 BUILD 和 DEPENDENCY_ANALYSIS 使用同一镜像，可考虑将 Maven 本地仓库 `~/.m2` 映射为一个独立的 workspace（如 `maven-cache`），实现跨 Task 的依赖缓存共享，避免重复下载。

> 注：当前 TektonCompiler 中 BUILD 等任务使用动态镜像（toolchain），DEPENDENCY_ANALYSIS 在 Java 项目场景下应使用同一工具链镜像。平台未来可支持在流水线定义中引用前一任务的镜像，或由用户通过任务市场的 `tool_image` 覆盖。

### 4.2 非 Java 项目：cdxgen 镜像

Node/Go/Python 等项目的 DEPENDENCY_ANALYSIS 使用专用 cdxgen 容器镜像：

| 常量 | 默认值 | 说明 |
|------|--------|------|
| `CDXGEN_IMAGE` | `ghcr.io/cyclonedx/cdxgen:v11.0.0` | CycloneDX 官方通用 SBOM 生成工具 |

cdxgen 镜像包含 Node.js、Go、Python、Ruby、Rust 等运行时的依赖解析器，可自动检测并处理各语言栈。

### 4.3 tool_image 选择方式

DEPENDENCY_ANALYSIS 的 `tool_image` 通过以下方式确定（按优先级）：

1. **任务市场覆盖**：管理员可在任务市场页面修改 `tool_image` 字段
2. **流水线配置**：用户在创建流水线时可选择镜像（未来规划）
3. **默认值**：根据平台推荐策略设置

**推荐默认策略**：

| 场景 | 推荐 tool_image | 推荐 default_command |
|------|----------------|-------------------|
| 混合项目或不确定 | `maven:3.9.9-eclipse-temurin-21` | Maven plugin 命令（Java 为主） |
| 纯 Java Maven | `maven:3.9.9-eclipse-temurin-21` | `mvn org.cyclonedx:...` |
| 纯 Node | `ghcr.io/cyclonedx/cdxgen:v11.0.0` | `cdxgen -o target/sbom.json -t cyclonedx:json` |
| 纯 Go | `ghcr.io/cyclonedx/cdxgen:v11.0.0` | `cdxgen -o target/sbom.json -t cyclonedx:json` |
| 纯 Python | `ghcr.io/cyclonedx/cdxgen:v11.0.0` | `cdxgen -o target/sbom.json -t cyclonedx:json` |

> 接入任务市场后，管理员可为不同租户/项目设置不同的默认 `tool_image`，实现按项目类型的镜像选择。

---

## 5. SBOM 消费方说明

### 5.1 SBOM 作为契约接口

`DEPENDENCY_ANALYSIS` 的唯一产物是 `target/sbom.json`（CycloneDX JSON 格式）。下游任务通过该文件获取依赖信息。

**下游消费者示例**（后续设计实现）：

| 消费者 | 方式 | 生态 |
|--------|------|------|
| Trivy | `trivy sbom target/sbom.json` | Aqua Security |
| Grype | `grype sbom:target/sbom.json` | Anchore（与 Syft 同体系） |
| Dependency-Track | `POST /api/v1/bom` | OWASP 企业级平台 |
| 自定义脚本 | 直接读取 JSON | 内部工具 / 报表 |

### 5.2 约束说明

- DEPENDENCY_ANALYSIS **不依赖**任何特定漏洞扫描工具
- SBOM 文件路径固定为 `target/sbom.json`
- Java 和 非 Java 项目产出格式一致，下游无需区分来源

### 5.3 SBOM 持久化与下载

DEPENDENCY_ANALYSIS 产出的 SBOM 文件需要持久化存储，供用户在流水线运行详情页查看和下载。

#### 设计目标

| 目标 | 说明 |
|------|------|
| 持久化 | SBOM 文件随 PipelineRun 记录保留，不随 Pod 删除而丢失 |
| 可下载 | 用户在运行详情页可下载 SBOM 文件（JSON 原文） |
| 可管理 | 支持查看历史运行的 SBOM 记录 |
| 可观测 | 运行详情页展示组件总数、生成工具等信息 |

#### 上传方式

**方案 A：DEPENDENCY_ANALYSIS Task 完成后主动上传（推荐）**

```
DEPENDENCY_ANALYSIS Task:
  Step 1: syft / mvn cyclonedx → target/sbom.json
  Step 2: curl -X POST 平台API -F "file=@target/sbom.json" -H "Run-ID: $(context.pipelineRun.uid)"
```

通过 Tekton Step 内嵌 curl 命令将 SBOM 文件上传至平台后端 API：

```bash
# 上传到平台 artifact 存储接口
curl -fsS -X POST "${API_ENDPOINT}/pipeline/runs/${RUN_ID}/artifacts" \
  -H "Authorization: Bearer $(cat /var/run/secrets/kubernetes.io/serviceaccount/token)" \
  -F "type=sbom" \
  -F "file=@target/sbom.json"
```

**方案 B：平台后端在 PipelineRun 完成后主动拉取**（备选）

```
PipelineRun 完成后:
  平台后端检测该运行是否有 DEPENDENCY_ANALYSIS Task
    → 通过 Kubernetes API 读取 TaskRun 的 Workspace 中 target/sbom.json
    → 若不存在则跳过（非 SBOM 场景）
```

#### 推荐方案（方案 A）

方案 A 优势：
- 解耦：上传逻辑在 Task 内，平台后端无需关心 Task 类型
- 实时：运行中即可上传，不依赖运行结束
- 自包含：Task 本身完成「生成 → 上传」闭环
- 扩展性：该模式也可用于上传其他构建产物（测试报告、覆盖率报告等）

#### 平台后端改动

**数据库设计**（可选，与现有 `pipeline_run` / `pipeline_job` 表协作）：

```sql
-- 流水线运行产物表
CREATE TABLE IF NOT EXISTS pipeline_run_artifact (
  id            INTEGER      NOT NULL PRIMARY KEY,
  run_id        INTEGER      NOT NULL REFERENCES pipeline_run(id),
  job_id        INTEGER      REFERENCES pipeline_job(id),      -- 来源 Task，可空
  artifact_type TEXT         NOT NULL DEFAULT 'sbom',          -- 产物类型：sbom / test-report / coverage / ...
  file_name     TEXT         NOT NULL,                         -- 文件名：sbom.json
  file_size     INTEGER      NOT NULL DEFAULT 0,               -- 文件大小（字节）
  storage_path  TEXT         NOT NULL,                         -- 存储路径（OSS 或本地路径）
  content_type  TEXT         NOT NULL DEFAULT 'application/json',
  create_time   TEXT         NOT NULL DEFAULT (datetime('now'))
);

CREATE INDEX IF NOT EXISTS idx_artifact_run ON pipeline_run_artifact (run_id, artifact_type);
```

**存储位置**：

| 环境 | 存储后端 | 路径规则 |
|------|---------|---------|
| 开发/本地 | 本地文件系统 | `data/artifacts/{runId}/{artifactId}/sbom.json` |
| 生产 | 对象存储（MinIO/S3） | `sbom/{runId}/{artifactId}.json` |

**REST API**：

| Method | Path | 说明 |
|--------|------|------|
| `POST` | `/pipeline/runs/{runId}/artifacts` | 上传产物（SBOM 文件），multipart/form-data |
| `GET` | `/pipeline/runs/{runId}/artifacts` | 查询某次运行的产物列表 |
| `GET` | `/pipeline/runs/{runId}/artifacts/{artifactId}/download` | 下载指定产物（返回文件流） |
| `GET` | `/pipeline/runs/{artifactId}/sbom` | 快捷获取最近一次 SBOM 文件（用于对接漏洞扫描） |

#### 前端展示

**运行详情页新增"产物"（Artifacts）面板**：

```
┌─ 运行 #1024 ──────────────────────────────┐
│                                             │
│  状态: ✅ 成功                              │
│  耗时: 3m12s                                │
│                                             │
│  ┌─ 产物 ──────────────────────────────┐   │
│  │ 📄 sbom.json         128 KB  下载   │   │
│  │   生成工具: CycloneDX Maven Plugin  │   │
│  │   组件数量: 128                      │   │
│  │   生成时间: 2026-09-12 10:32:15     │   │
│  └─────────────────────────────────────┘   │
│                                             │
└─────────────────────────────────────────────┘
```

---

## 6. Tekton 数据传递

### 6.1 Workspaces vs Results

| 方式 | 适合 | 不适合 |
|------|------|--------|
| **Workspaces**（PVC / volumeClaimTemplate） | SBOM 文件（50 KiB+ 的 JSON） | 无 |
| **Pipeline Results** | 轻量元数据（SBOM 路径、组件总数） | SBOM 文件本体（K8s annotation 256 KiB 上限） |

### 6.2 推荐传递方式

```
CLONE Task (Workspace: source)
  └── git clone → source 目录
                              ↓
BUILD/DEP-INSTALL Task (同一 Workspace, PVC-backed)
  └── 按语言栈安装依赖：mvn package / npm ci / go mod download / pip install
                              ↓
DEPENDENCY_ANALYSIS Task (同一 Workspace)
  ├── [Java] mvn org.cyclonedx:...:makeAggregateBom → target/sbom.json
  └── [其他] cdxgen -o target/sbom.json → target/sbom.json
                              ↓
  [Result] sbom_path: /workspace/source/target/sbom.json
  [Result] tool: cyclonedx-maven-plugin | cdxgen
                              ↓
[未来] 漏洞扫描 Task (同一 Workspace)
  └── 读取 target/sbom.json 执行漏洞匹配
```

### 6.3 TektonCompiler 集成

新增镜像常量：

| 常量 | 默认值 | 说明 |
|------|--------|------|
| `CDXGEN_IMAGE` | `ghcr.io/cyclonedx/cdxgen:v11.0.0` | 非 Java 项目使用 |

在 `TektonCompiler.toSteps()` 中增加 `DEPENDENCY_ANALYSIS` 分支：

```
job.kind == DEPENDENCY_ANALYSIS
  → Step Image: 按 job 配置读取 tool_image（可能为 Maven 镜像或 cdxgen 镜像）
  → Step Command: 用户配置的命令

// 编译器不关心用户用哪个工具，只负责：
// 1. 将用户命令包装为标准 Step 脚本
// 2. 挂载 source workspace
// 3. 输出 Result（sbom_path, tool）
```

---

## 7. 任务类型设计

### 7.1 新增 `DEPENDENCY_ANALYSIS`

**pipeline_task_kind 初始数据**：

| 字段 | 值 |
|------|-----|
| `kind_value` | `DEPENDENCY_ANALYSIS` |
| `label` | 依赖分析 |
| `task_group` | 质量控制 |
| `description` | 生成 CycloneDX 格式的依赖清单（SBOM），为漏洞扫描提供精确的依赖树 |
| `hint` | Java 项目使用 CycloneDX Maven Plugin，其他语言使用 cdxgen。产出 target/sbom.json。 |
| `default_command` | `mvn org.cyclonedx:cyclonedx-maven-plugin:2.9.3:makeAggregateBom -Dcyclonedx.outputFormat=json -DoutputName=sbom --no-transfer-progress -q` |
| `requires_command` | 1 |
| `enabled` | 1 |
| `tool_image` | `maven:3.9.9-eclipse-temurin-21`（Java 默认；非 Java 项目需改为 cdxgen 镜像） |
| `sort_order` | 45（介于 LINT_SONAR=40 和 SCAN=50 之间） |

### 7.2 使用方式

**方式一：Java Maven 项目（默认）**

直接在流水线中添加 DEPENDENCY_ANALYSIS 任务，使用默认命令和镜像即可：

```
tool_image: maven:3.9.9-eclipse-temurin-21（或对应 Java 版本的 Maven 镜像）
default_command: mvn org.cyclonedx:cyclonedx-maven-plugin:2.9.3:makeAggregateBom ...
```

**方式二：非 Java 项目**

修改任务的 tool_image 和命令：

```
tool_image: ghcr.io/cyclonedx/cdxgen:v11.0.0（通过任务市场或流水线配置）
command:    cdxgen -o target/sbom.json -t cyclonedx:json
```

**方式三：自动检测脚本（进阶）**

如果希望在同一个 pipeline 中支持不确定的项目类型，可以使用 shell 脚本组合：

```bash
# 自动检测：pom.xml 存在则用 Maven Plugin，否则用 cdxgen
if [ -f "pom.xml" ]; then
  mvn org.cyclonedx:cyclonedx-maven-plugin:2.9.3:makeAggregateBom \
    -Dcyclonedx.outputFormat=json \
    -DoutputName=sbom \
    --no-transfer-progress -q
else
  cdxgen -o target/sbom.json -t cyclonedx:json
fi
```

> 该方式需要容器镜像同时包含 `mvn` 和 `cdxgen`（不推荐生产使用，建议用明确的镜像策略）。

### 7.3 设计要点

**通用性**：
- `requires_command=1` 允许用户自定义参数
- `tool_image` 可覆盖，适应不同语言栈

**上下游关系**：
- 建议在 BUILD 或依赖安装 Task 之后执行
- SBOM 文件通过 workspace 传递，路径约定为 `target/sbom.json`

**与现有任务的关系**：
- 不修改任何现有任务逻辑
- 不依赖任何漏洞扫描工具的存在
- 属于质量控制组的新增任务

### 7.4 涉及修改的文件清单

| 位置 | 文件 | 改动 |
|------|------|------|
| 后端枚举 | `PipelineJobKind.java` | 新增 `DEPENDENCY_ANALYSIS` |
| 编译器 | `TektonCompiler.java` | 新增 `CDXGEN_IMAGE` 常量；`toSteps()` 新增 `DEPENDENCY_ANALYSIS` 分支 |
| 编译器 | `PipelineGraphValidator.java` | 新增校验规则（无特殊限制，与普通命令任务相同） |
| SQL 初始数据 | 任务市场初始化 SQL | 新增 `DEPENDENCY_ANALYSIS` 记录 |
| 前端枚举 | `jobCatalog.ts` | 新增对应条目 |
| 前端图标 | `jobIcons.ts` | 新增图标映射（可用 `mdi:file-tree` 或 `mdi:code-braces`） |

---

## 8. 流水线编排建议

### 8.1 按语言栈的推荐流程

**Java/Maven 项目**：

```
[CLONE] → [BUILD (mvn package)] → [DEPENDENCY_ANALYSIS] → [未来: 漏洞扫描] → [IMAGE]

使用的镜像: Maven 工具链镜像 (maven:3.9.9-eclipse-temurin-21)
执行命令: mvn org.cyclonedx:...:makeAggregateBom
```

**Node.js 项目**：

```
[CLONE] → [BUILD (npm ci)] → [DEPENDENCY_ANALYSIS] → [未来: 漏洞扫描] → [IMAGE]

使用的镜像: cdxgen (ghcr.io/cyclonedx/cdxgen:v11.0.0)
执行命令: cdxgen -o target/sbom.json -t cyclonedx:json
```

**Go 项目**：

```
[CLONE] → [BUILD (go build)] → [DEPENDENCY_ANALYSIS] → [未来: 漏洞扫描] → [IMAGE]

使用的镜像: cdxgen
执行命令: cdxgen -o target/sbom.json -t cyclonedx:json
```

**Python 项目**：

```
[CLONE] → [BUILD (pip install)] → [DEPENDENCY_ANALYSIS] → [未来: 漏洞扫描] → [IMAGE]

使用的镜像: cdxgen
执行命令: cdxgen -o target/sbom.json -t cyclonedx:json
```

### 8.2 可选变体

```
# 只有依赖分析，不跟漏洞扫描
[CLONE] → [BUILD] → [DEPENDENCY_ANALYSIS]

# 依赖分析后接多个消费方
[CLONE] → [BUILD] → [DEPENDENCY_ANALYSIS] → [漏洞扫描 A] / [Dependency-Track 上报] / [报告生成]
```

---

## 9. 附录

### 9.1 参考文档

| 文档 | 路径 | 说明 |
|------|------|------|
| 任务市场设计 | `docs/pipeline/pipeline-task-marketplace-design.md` | 14 种任务类型的数据库设计与 API |
| 镜像选择策略 | `docs/pipeline/pipeline-toolchain-image-strategy.md` | 固定镜像与动态镜像的选取规则 |
| 任务类型目录 | `docs/superpowers/specs/2026-09-07-pipeline-job-kind-design.md` | 任务枚举的完整目录与校验逻辑 |
| Tekton 详解 | `docs/pipeline/tekton-intro.md` | Tekton 对象模型、数据传递、运行时序 |
| Pipeline API | `docs/pipeline/pipeline-core-api.md` | 流水线创建、运行、任务管理等 REST API |

### 9.2 外部链接

- [CycloneDX Maven Plugin GitHub](https://github.com/CycloneDX/cyclonedx-maven-plugin)
- [cdxgen GitHub](https://github.com/cyclonedx/cdxgen)
- [CycloneDX 规范](https://cyclonedx.org/specification/)

### 9.3 决策跟踪

| 决策项 | 结论 | 状态 |
|--------|------|------|
| 架构方式 | **独立 Task**，不依赖现有任务类型 | ✅ 已定 |
| SBOM 输出格式 | **CycloneDX JSON**，统一为 `target/sbom.json` | ✅ 已定 |
| 不动客户代码 | 全程 CLI 调用，不修改项目文件 | ✅ 已定 |
| Java/Maven 工具 | **CycloneDX Maven Plugin**（`org.cyclonedx:cyclonedx-maven-plugin`），CLI 调用，版本锁定 2.9.3（原定 2.10.0 尚未发布） | ✅ 已定（2.9.3 已验证） |
| 非 Java 工具 | **cdxgen**（`ghcr.io/cyclonedx/cdxgen:v11.0.0`），自动检测语言栈 | ✅ 已定 |
| Java 镜像 | **复用 Maven 工具链镜像**（如 `maven:3.9.9-eclipse-temurin-21`） | ✅ 已定 |
| 非 Java 镜像 | **新增 cdxgen 镜像**（`ghcr.io/cyclonedx/cdxgen:v11.0.0`） | ✅ 已定 |
| SBOM 持久化 | **上传至平台后端**，DEPENDENCY_ANALYSIS Task 完成后主动上传，支持在运行详情页下载 | ✅ 已定 |
| 漏洞扫描 | **后续独立设计**，DEPENDENCY_ANALYSIS 不负责 | ✅ 已定 |

### 9.4 待讨论项

1. **tool_image 选择策略**：是否在流水线创建时根据项目语言栈自动推荐/设置 tool_image？还是统一由用户手动在任务市场配置？
2. **Maven 缓存共享**：是否将 `~/.m2` 映射为独立 workspace，实现 BUILD 和 DEPENDENCY_ANALYSIS 之间的 Maven 缓存共享？
3. **组件计数元数据**：是否将组件总数通过 Pipeline Results 输出（如 `component_count: 128`），供下游任务或前端展示？
4. **上传认证**：DEPENDENCY_ANALYSIS 容器的上传请求如何认证？使用 ServiceAccount token 还是平台 API token？