import { Link } from 'react-router'
import { Card, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { CONSOLE_PRODUCTS, groupProducts } from '@/shared/console/products'
import { useAuthStore } from '@/stores/auth'

export function WorkbenchPage() {
  const user = useAuthStore((s) => s.user)
  const tenantName = useAuthStore((s) => s.activeTenantName)
  const groups = groupProducts(CONSOLE_PRODUCTS.filter((item) => !item.comingSoon))

  return (
    <div className="mx-auto flex w-full max-w-6xl flex-col gap-4 md:gap-6">
      <div>
        <h1 className="text-2xl font-semibold">控制台</h1>
        <p className="mt-1 text-sm text-muted-foreground">
          {user?.displayName || user?.username || '已登录'}
          {tenantName ? ` · ${tenantName}` : ''}
        </p>
      </div>
      {groups.map((group) => (
        <section key={group.group} className="flex flex-col gap-3">
          <h2 className="text-sm font-medium text-muted-foreground">{group.group}</h2>
          <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
            {group.items.map((product) => {
              const Icon = product.icon
              return (
                <Link key={product.key} to={product.path} className="block">
                  <Card className="h-full bg-card transition-shadow hover:shadow-md">
                    <CardHeader>
                      <CardTitle className="flex items-center gap-2">
                        <Icon className="size-4" />
                        {product.name}
                      </CardTitle>
                      <CardDescription>{product.description}</CardDescription>
                    </CardHeader>
                  </Card>
                </Link>
              )
            })}
          </div>
        </section>
      ))}
    </div>
  )
}
