import type { LucideIcon } from 'lucide-react'
import { LayoutDashboard, Settings, Users } from 'lucide-react'
import { CONSOLE_PRODUCTS, type ConsoleProduct } from '@/shared/console/products'

export interface NavItem {
  key: string
  label: string
  path: string
  icon: LucideIcon
  /** 产品 key。激活态按 `/${productKey}` 前缀判断，覆盖模块内子路由。 */
  productKey?: string
  comingSoon?: boolean
  end?: boolean
}

export interface NavGroup {
  key: string
  label: string
  items: NavItem[]
}

export function buildNavGroups(options: { isAdmin: boolean }): NavGroup[] {
  const userItems: NavItem[] = []
  if (options.isAdmin) {
    userItems.push({
      key: 'user-accounts',
      label: '用户管理',
      path: '/user/accounts',
      icon: Users,
    })
  }
  userItems.push({
    key: 'user-settings',
    label: '账号设置',
    path: '/user/settings',
    icon: Settings,
    end: true,
  })

  const productGroups = new Map<string, NavItem[]>()
  CONSOLE_PRODUCTS.forEach((product) => {
    const items = productGroups.get(product.group) ?? []
    items.push(productToNav(product))
    productGroups.set(product.group, items)
  })

  return [
    {
      key: 'platform',
      label: '平台',
      items: [
        {
          key: 'workbench',
          label: '工作台',
          path: '/workbench',
          icon: LayoutDashboard,
          end: true,
        },
      ],
    },
    { key: 'user', label: '用户中心', items: userItems },
    ...Array.from(productGroups, ([label, items]) => ({ key: label, label, items })),
  ]
}

function productToNav(product: ConsoleProduct): NavItem {
  return {
    key: product.key,
    label: product.name,
    path: product.path,
    icon: product.icon,
    productKey: product.key,
    comingSoon: product.comingSoon,
  }
}

export function isNavItemActive(pathname: string, item: Pick<NavItem, 'path' | 'productKey' | 'comingSoon' | 'end'>): boolean {
  if (item.comingSoon) return false
  if (item.end) return pathname === item.path
  if (item.productKey) {
    const prefix = `/${item.productKey}`
    return pathname === prefix || pathname.startsWith(`${prefix}/`)
  }
  return pathname === item.path || pathname.startsWith(`${item.path}/`)
}
