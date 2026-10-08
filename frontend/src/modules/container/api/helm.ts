import { helmHttp } from '@/modules/container/api/helmHttp'
import { helmMock } from '@/modules/container/api/helmMock'

/**
 * 页面统一走 helmApi。HELM_USE_MOCK 保持 true。
 *
 * 后端已有仓库列表、Chart 列表、版本查询和 `POST /container/helm/charts/upload`，
 * 但摘要字段、详情路径和 values 接口与 helmHttp 不一致，Release 安装/升级也还没有。
 * 不能只把这个开关改成 false，否则目录页和安装页会一起打到尚未对齐的接口。
 * 差异见 docs/frontend/helm-ui.md。
 */
export const HELM_USE_MOCK = true

export const helmApi = HELM_USE_MOCK ? helmMock : helmHttp

export function resetHelmSession() {
  helmMock.reset()
}
