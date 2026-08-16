import type { Component } from 'vue'
import { Beaker, Boxes, FileText, GitBranch, ServerCog, ShieldCheck, SquareKanban } from '@lucide/vue'

/**
 * 控制台产品目录（顶栏左侧下拉切换）
 * 扩展点：新增产品只需往 CONSOLE_PRODUCTS 追加一项，顶栏无需改动。
 * comingSoon 为 true 时下拉项置灰，用于占位未上线产品。
 */
export interface ConsoleProduct {
  key: string
  name: string
  description: string
  icon: Component
  path: string
  group: string
  comingSoon?: boolean
}

export const CONSOLE_PRODUCTS: ConsoleProduct[] = [
  {
    key: 'pm',
    name: '项目管理',
    description: '项目、工作项与迭代协同',
    icon: SquareKanban,
    path: '/pm/projects',
    group: '研发协同',
  },
  {
    key: 'api-test',
    name: '接口测试',
    description: 'API 定义、调试与集合管理',
    icon: Beaker,
    path: '/api-test',
    group: '质量保障',
  },
  {
    key: 'file-parser',
    name: '文件解析',
    description: '上传并解析文档、PDF、图片等文件',
    icon: FileText,
    path: '/file-parser',
    group: '效率工具',
  },
  {
    key: 'pipeline',
    name: '流水线',
    description: '持续集成与持续交付',
    icon: GitBranch,
    path: '/pipeline/overview',
    group: '研发协同',
    comingSoon: true,
  },
  {
    key: 'resource',
    name: '资源编排',
    description: '主机、集群与中间件编排',
    icon: ServerCog,
    path: '/resource/overview',
    group: '基础设施',
    comingSoon: true,
  },
  {
    key: 'artifact',
    name: '制品仓库',
    description: '镜像与二进制制品托管',
    icon: Boxes,
    path: '/artifact/overview',
    group: '基础设施',
    comingSoon: true,
  },
  {
    key: 'audit',
    name: '安全审计',
    description: '操作审计与权限合规',
    icon: ShieldCheck,
    path: '/audit/overview',
    group: '安全治理',
    comingSoon: true,
  },
]

export function findProduct(key: string): ConsoleProduct {
  return CONSOLE_PRODUCTS.find((item) => item.key === key) ?? CONSOLE_PRODUCTS[0]
}

/** 按路径解析当前产品；工作台 / 用户中心等非产品页返回 null */
export function resolveActiveProductKey(path: string): string | null {
  for (const product of CONSOLE_PRODUCTS) {
    if (product.comingSoon) continue
    const prefix = `/${product.key}`
    if (path === prefix || path.startsWith(`${prefix}/`)) return product.key
  }
  return null
}

export function resolveActiveProduct(path: string): ConsoleProduct | null {
  const key = resolveActiveProductKey(path)
  return key ? findProduct(key) : null
}

/** 按 group 聚合，供下拉菜单分组渲染 */
export function groupProducts(products: ConsoleProduct[] = CONSOLE_PRODUCTS) {
  const groups = new Map<string, ConsoleProduct[]>()
  products.forEach((product) => {
    const list = groups.get(product.group) ?? []
    list.push(product)
    groups.set(product.group, list)
  })
  return Array.from(groups, ([group, items]) => ({ group, items }))
}
