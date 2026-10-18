import { describe, expect, it } from 'vitest'
import { CONSOLE_PRODUCTS, groupProducts } from '@/shared/console/products'

function visibleKeys(isAdmin: boolean) {
  return groupProducts(CONSOLE_PRODUCTS, isAdmin).flatMap((group) => group.items.map((item) => item.key))
}

describe('groupProducts admin filtering', () => {
  it('hides adminOnly products from non-admins', () => {
    expect(visibleKeys(false)).not.toContain('argo')
  })

  it('shows adminOnly products to admins', () => {
    expect(visibleKeys(true)).toContain('argo')
  })
})
