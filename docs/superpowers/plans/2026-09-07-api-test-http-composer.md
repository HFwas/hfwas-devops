# HTTP Composer Implementation Plan

> **For agentic workers:** Execute inline in this session. Spec: `docs/api-test-http-review.md`.

**Goal:** 把接口测试请求页改成 Hoppscotch 密度的 HTTP 编排，同时保留 Postman 集合壳层与结构化断言。

**Architecture:** 纯函数处理 KV / URL / Auth / Body / cURL；`RequestDraft` 改为数组 + bodyMode + auth + settings；`RequestWorkspace` 重排 Tab；`HttpDebugEngine` 按请求创建 RestClient 以应用超时与重定向。

**Tech Stack:** Vue 3, Naive UI, Pinia, Vitest, Spring RestClient.

## Global Constraints

- Greenfield: 直接改 `RequestDraft` 形状，不做 Record 双写。
- 发送契约仍是 `ApiDebugExecuteDTO` 的 Map headers/queryParams。
- 结构化断言保留；Visualize / OAuth2 / GraphQL 不做。
- 不提交 git，除非用户要求。

## Files

- Create: `frontend/src/modules/api-test/shared/types/keyValue.ts`
- Create: `frontend/src/modules/api-test/shared/utils/keyValue.ts` + test
- Create: `frontend/src/modules/api-test/debug/utils/{urlParams,auth,bodyMode,curlExport,cookies,draftExecute,interpolate}.ts` + tests
- Create: `frontend/src/modules/api-test/debug/components/{AuthEditor,BodyEditor,RequestSettings}.vue`
- Modify: `RequestDraft` / `KeyValueEditor` / `RequestWorkspace` / `loadDefinitionDraft` / `mapHistoryDetailToTab` / `ApiWorkspaceResponse` / `ResponseBodyRenderer`
- Modify: `HttpDebugEngine.java` + test
- Delete: `debug/views/DebugTab.vue`, `debug/components/RequestEditor.vue`

TDD: 先写 util / engine 测试，再写实现，再改 Vue 与现有组件测试。
