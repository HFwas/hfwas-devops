# 任务类型目录 Implementation Plan

> **For agentic workers:** Implement task-by-task. User asked to finish without asking permission; do not wait for review gates.

**Goal:** Expand `PipelineJobKind` to 13 executable types per `2026-09-07-pipeline-job-kind-design.md`.

**Architecture:** Enum metadata drives UI and compiler images. Clone is optional. IMAGE expands to multiple Tekton steps. LINT_SEMGREP / LINT_SONAR are single steps. APPROVAL splits the graph; control plane pauses at `WAITING_APPROVAL`.

**Tech Stack:** Java 21, pipeline-core, Vue 3 + Naive UI, Tekton via fabric8.

## Global Constraints

- `BizException.of(ResultCode.BAD_REQUEST, …)` for validation
- No new ResultCode values, no job-kinds API
- No docker.sock; Kaniko cache PVC RWO 10Gi `hfwas-kc-{pipelineId}`
- Greenfield: change schema/types directly
