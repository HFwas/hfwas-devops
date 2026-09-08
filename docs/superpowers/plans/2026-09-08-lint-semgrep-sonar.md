# Split LINT into LINT_SEMGREP / LINT_SONAR Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace `PipelineJobKind.LINT` with two independent kinds `LINT_SEMGREP` and `LINT_SONAR`, each compiling to a single Tekton step.

**Architecture:** Enum metadata still drives UI and compiler images. Semgrep uses the same command-script path as `SCAN`. Sonar evals user exports then runs `sonar-scanner`; empty Host/Token fails. No skip flags, no `LINT` alias.

**Tech Stack:** Java 21, pipeline-core, Vue 3 + TypeScript, Tekton via fabric8.

## Global Constraints

- Greenfield: delete `LINT`; no alias, no `LINT_SKIP_SEMGREP`
- No new ResultCode; unknown kind stays `未知任务类型`
- No new job-kinds API; Java enum and frontend `JobKind` stay aligned
- Semgrep image `semgrep/semgrep:1.97.0`; Sonar image `sonarsource/sonar-scanner-cli:11.2`
- Directory is 14 kinds
- `BizException.of(ResultCode.BAD_REQUEST, …)` for validation
- Do not commit unless the user asks

---

### Task 1: Compiler — one step each

**Files:**
- Modify: `backend/pipeline-core/src/test/java/com/hfwas/devops/pipeline/tekton/TektonCompilerTest.java`
- Modify: `backend/pipeline-core/src/main/java/com/hfwas/devops/pipeline/graph/PipelineJobKind.java`
- Modify: `backend/pipeline-core/src/main/java/com/hfwas/devops/pipeline/tekton/TektonCompiler.java`
- Modify: `backend/pipeline-core/src/main/java/com/hfwas/devops/pipeline/tekton/DnsNames.java` (comment only)

**Interfaces:**
- Consumes: `PipelineJobKind`, `TektonCompiler.compile(CompileRequest)`
- Produces: `LINT_SEMGREP` / `LINT_SONAR` enum constants; Semgrep via `commandScript`; Sonar via `evalPrefix` + required Host/Token

- [x] **Step 1: Replace the combined lint test with two failing tests**

Replace `lintExpandsToSemgrepAndSonar` in `TektonCompilerTest.java` with:

```java
    @Test
    void lintSemgrepUsesSemgrepImageAndUserCli() {
        PipelineGraphSpec graph = new PipelineGraphSpec(List.of(
                stage("lint", 0, List.of(job("sg", PipelineJobKind.LINT_SEMGREP,
                        PipelineJobKind.LINT_SEMGREP.defaultCommand(), 0)))
        ));
        List<CompiledStep> steps = TektonCompiler.compile(request(3L, graph, false)).tasks().getFirst().steps();
        assertEquals(1, steps.size());
        assertEquals(TektonCompiler.SEMGREP_IMAGE, steps.getFirst().image());
        assertTrue(steps.getFirst().script().contains("semgrep scan --error --config=auto ."));
        assertFalse(steps.getFirst().script().contains("LINT_SKIP_SEMGREP"));
        assertFalse(steps.getFirst().script().contains("sonar-scanner"));
    }

    @Test
    void lintSonarUsesSonarImageAndFailsWhenTokenEmpty() {
        PipelineGraphSpec graph = new PipelineGraphSpec(List.of(
                stage("lint", 0, List.of(job("sn", PipelineJobKind.LINT_SONAR,
                        PipelineJobKind.LINT_SONAR.defaultCommand(), 0)))
        ));
        List<CompiledStep> steps = TektonCompiler.compile(request(3L, graph, false)).tasks().getFirst().steps();
        assertEquals(1, steps.size());
        assertEquals(TektonCompiler.SONAR_IMAGE, steps.getFirst().image());
        assertTrue(steps.getFirst().script().contains("SONAR_HOST_URL:?SONAR_HOST_URL is required"));
        assertTrue(steps.getFirst().script().contains("SONAR_TOKEN:?SONAR_TOKEN is required"));
        assertTrue(steps.getFirst().script().contains("sonar-scanner"));
        assertFalse(steps.getFirst().script().contains("skip sonar"));
    }
```

- [ ] **Step 2: Run tests and confirm they fail (missing enum or wrong compile)**

```bash
export JAVA_HOME=/Users/hfwas/Library/Java/JavaVirtualMachines/azul-21.0.3/Contents/Home
export PATH="$JAVA_HOME/bin:/usr/local/apache-maven-3.8.1/bin:$PATH"
mvn -pl backend/pipeline-core -am test -Dtest=TektonCompilerTest#lintSemgrepUsesSemgrepImageAndUserCli,TektonCompilerTest#lintSonarUsesSonarImageAndFailsWhenTokenEmpty
```

Expected: compile error (`LINT_SEMGREP` cannot be resolved) or assertion failure.

- [ ] **Step 3: Replace the enum constant**

In `PipelineJobKind.java`, replace `LINT` with:

```java
    LINT_SEMGREP("Semgrep 检查", true, "semgrep scan --error --config=auto ."),
    LINT_SONAR("Sonar 检查", true, """
            export SONAR_HOST_URL=https://sonar.example.com
            export SONAR_TOKEN=
            export SONAR_PROJECT_KEY=app
            """),
```

- [ ] **Step 4: Compile Semgrep as SCAN-style; Sonar as eval + required vars**

In `TektonCompiler.toSteps`, replace the `LINT` two-step branch with:

```java
        if (job.kind() == PipelineJobKind.LINT_SONAR) {
            return List.of(new CompiledStep(base, SONAR_IMAGE, lintSonarScript(command), env, false, false));
        }
```

Add `LINT_SEMGREP` to the image switch next to `SCAN`:

```java
            case LINT_SEMGREP -> SEMGREP_IMAGE;
            case SCAN -> SCAN_IMAGE;
```

Delete `lintSemgrepScript`. Replace `lintSonarScript` with:

```java
    private static String lintSonarScript(String command) {
        return evalPrefix(command) + """

                : "${SONAR_HOST_URL:?SONAR_HOST_URL is required}"
                : "${SONAR_TOKEN:?SONAR_TOKEN is required}"
                : "${SONAR_PROJECT_KEY:=app}"
                sonar-scanner -Dsonar.host.url="$SONAR_HOST_URL" -Dsonar.token="$SONAR_TOKEN" -Dsonar.projectKey="$SONAR_PROJECT_KEY" -Dsonar.sources=.
                """.stripIndent();
    }
```

Update `DnsNames` javadoc: IMAGE still generates `base-crane` suffixes; LINT no longer does.

- [ ] **Step 5: Re-run compiler tests**

```bash
mvn -pl backend/pipeline-core -am test -Dtest=TektonCompilerTest,PipelineGraphValidatorTest
```

Expected: PASS.

---

### Task 2: Frontend catalog and docs

**Files:**
- Modify: `frontend/src/modules/pipeline/graph/pipelineGraph.test.ts`
- Modify: `frontend/src/modules/pipeline/types/pipeline.ts`
- Modify: `frontend/src/modules/pipeline/graph/jobCatalog.ts`
- Modify: `frontend/src/modules/pipeline/graph/jobIcons.ts`
- Modify: `frontend/src/modules/pipeline/status.ts`
- Modify: `deploy/tekton/README.md`
- Modify: `docs/superpowers/plans/2026-09-08-pipeline-job-kind.md` (architecture sentence)

**Interfaces:**
- Consumes: Task 1 enum names `LINT_SEMGREP` / `LINT_SONAR`
- Produces: `JobKind` union of 14 values; catalog entries with default commands matching the Java enum

- [ ] **Step 1: Fail the catalog length test**

In `pipelineGraph.test.ts`:

```ts
  it('exposes 14 job kinds in 云效-style groups and a clone/build/test template', () => {
    expect(JOB_KIND_OPTIONS).toHaveLength(14)
    expect(JOB_KIND_OPTIONS.map((item) => item.value)).toEqual(
      expect.arrayContaining(['LINT_SEMGREP', 'LINT_SONAR']),
    )
    expect(JOB_KIND_OPTIONS.map((item) => item.value)).not.toContain('LINT')
```

- [ ] **Step 2: Run frontend test and confirm failure**

```bash
cd frontend && npx vitest run src/modules/pipeline/graph/pipelineGraph.test.ts
```

Expected: FAIL length 13 !== 14.

- [ ] **Step 3: Update types, catalog, icons, tone**

`JobKind`: replace `'LINT'` with `'LINT_SEMGREP' | 'LINT_SONAR'`.

Catalog: replace the `LINT` entry with two 质量控制 entries:

```ts
  {
    value: 'LINT_SEMGREP',
    label: 'Semgrep 检查',
    group: '质量控制',
    description: 'Semgrep 静态检查',
    hint: '填写 Semgrep CLI。',
    requiresCommand: true,
    defaultCommand: 'semgrep scan --error --config=auto .',
  },
  {
    value: 'LINT_SONAR',
    label: 'Sonar 检查',
    group: '质量控制',
    description: 'SonarScanner 静态检查',
    hint: '填写 SONAR_HOST_URL / SONAR_TOKEN / SONAR_PROJECT_KEY。',
    requiresCommand: true,
    defaultCommand: `export SONAR_HOST_URL=https://sonar.example.com
export SONAR_TOKEN=
export SONAR_PROJECT_KEY=app`,
  },
```

Icons: `LINT_SEMGREP: Search`, `LINT_SONAR: Search`.

Tone: `kind === 'LINT_SEMGREP' || kind === 'LINT_SONAR' || kind === 'SCAN'`.

`deploy/tekton/README.md`: `CLONE/LINT_SEMGREP/LINT_SONAR/IMAGE`.

Old plan architecture line: IMAGE expands to multiple steps; lint kinds are single-step.

- [ ] **Step 4: Re-run frontend test**

```bash
cd frontend && npx vitest run src/modules/pipeline/graph/pipelineGraph.test.ts
```

Expected: PASS.

---

## Spec coverage

- §2 catalog 14 kinds, no `LINT` → Task 1 enum + Task 2 types/catalog
- §4.7 Semgrep command path + Sonar eval/fail → Task 1 compiler
- §5 frontend 14 dropdown, hints → Task 2
- §7.3 / unknown `LINT` → `parseJobKind` already maps unknown to `未知任务类型`
- Token redaction `SONAR_TOKEN` → already in `TektonPipelineExecutor.extraSecrets`; keep
