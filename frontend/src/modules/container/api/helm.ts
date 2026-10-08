import { helmHttp } from '@/modules/container/api/helmHttp'
import { helmMock } from '@/modules/container/api/helmMock'

/**
 * 页面统一走 helmApi。默认打真实 `/container/helm` 与集群 Release 接口。
 * `helmMock` 仍留给单测和本地无后端时手动改开关。契约见 docs/frontend/helm-ui.md。
 */
export const HELM_USE_MOCK = false

export const helmApi = HELM_USE_MOCK ? helmMock : helmHttp

export function resetHelmSession() {
  helmMock.reset()
}
