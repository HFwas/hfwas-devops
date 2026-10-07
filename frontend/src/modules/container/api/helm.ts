import { helmHttp } from '@/modules/container/api/helmHttp'
import { helmMock } from '@/modules/container/api/helmMock'

/**
 * TODO(helm-backend): container-core 还没有 Helm Controller。
 * 页面走 helmApi；HELM_USE_MOCK 为 true 时使用会话内示例数据，路径与类型已经按 helmHttp 对齐。
 * 后端提供 `/container/helm/*` 与 `/container/clusters/{id}/helm/releases/*` 后改为 false。
 */
export const HELM_USE_MOCK = true

export const helmApi = HELM_USE_MOCK ? helmMock : helmHttp

export function resetHelmSession() {
  helmMock.reset()
}
