import { useLocation } from 'react-router'
import { resolveActiveProduct } from '@/shared/console/products'

export function RouteScreen() {
  const location = useLocation()
  const product = resolveActiveProduct(location.pathname)

  return (
    <div className="mx-auto flex w-full max-w-4xl flex-col gap-2 p-6">
      <h1 className="text-xl font-semibold">{product?.name ?? '页面'}</h1>
      <p className="text-sm text-muted-foreground">{product?.description ?? location.pathname}</p>
      <p className="font-mono text-xs text-muted-foreground">{location.pathname}</p>
    </div>
  )
}
