import { ChevronDown } from 'lucide-react'
import { useLocation, useNavigate } from 'react-router'
import { Button } from '@/components/ui/button'
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuGroup,
  DropdownMenuItem,
  DropdownMenuLabel,
  DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu'
import { CONSOLE_PRODUCTS, groupProducts, resolveActiveProduct } from '@/shared/console/products'

export function ProductSwitcher() {
  const navigate = useNavigate()
  const location = useLocation()
  const active = resolveActiveProduct(location.pathname)
  const groups = groupProducts()

  return (
    <DropdownMenu>
      <DropdownMenuTrigger asChild>
        <Button variant="outline" size="sm">
          {active?.name ?? '产品'}
          <ChevronDown />
        </Button>
      </DropdownMenuTrigger>
      <DropdownMenuContent align="start" className="w-72">
        {groups.map((group) => (
          <DropdownMenuGroup key={group.group}>
            <DropdownMenuLabel>{group.group}</DropdownMenuLabel>
            {group.items.map((product) => {
              const Icon = product.icon
              return (
                <DropdownMenuItem
                  key={product.key}
                  disabled={product.comingSoon}
                  onSelect={() => {
                    if (!product.comingSoon) void navigate(product.path)
                  }}
                >
                  <Icon />
                  <span className="flex flex-col">
                    <span>{product.name}</span>
                    <span className="text-xs text-muted-foreground">{product.description}</span>
                  </span>
                </DropdownMenuItem>
              )
            })}
          </DropdownMenuGroup>
        ))}
        {CONSOLE_PRODUCTS.length === 0 && <DropdownMenuItem disabled>暂无产品</DropdownMenuItem>}
      </DropdownMenuContent>
    </DropdownMenu>
  )
}
