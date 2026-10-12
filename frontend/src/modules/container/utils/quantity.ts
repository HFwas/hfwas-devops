const BINARY: Record<string, number> = {
  Ki: 1024,
  Mi: 1024 ** 2,
  Gi: 1024 ** 3,
  Ti: 1024 ** 4,
  Pi: 1024 ** 5,
  Ei: 1024 ** 6,
}

const DECIMAL: Record<string, number> = {
  n: 1e-9,
  u: 1e-6,
  m: 1e-3,
  k: 1e3,
  K: 1e3,
  M: 1e6,
  G: 1e9,
  T: 1e12,
  P: 1e15,
  E: 1e18,
}

/** 解析 Kubernetes 资源量（CPU `500m` / `1`，内存 `512Mi` / `1Gi`）。无法解析时返回 null。 */
export function parseQuantity(raw: string | null | undefined): number | null {
  const text = raw?.trim()
  if (!text) return null
  const match = /^(\d*\.?\d+)([A-Za-z]+)?$/.exec(text)
  if (!match) return null
  const value = Number(match[1])
  if (!Number.isFinite(value)) return null
  const suffix = match[2] ?? ''
  if (!suffix) return value
  if (Object.prototype.hasOwnProperty.call(BINARY, suffix)) return value * BINARY[suffix]
  if (Object.prototype.hasOwnProperty.call(DECIMAL, suffix)) return value * DECIMAL[suffix]
  return null
}

/** request / limit。分母缺失、无法解析或不是正数时返回 null，调用方只显示原文。 */
export function quantityRatio(numerator: string | null | undefined, denominator: string | null | undefined): number | null {
  const top = parseQuantity(numerator)
  const bottom = parseQuantity(denominator)
  if (top == null || bottom == null || bottom <= 0) return null
  return top / bottom
}
