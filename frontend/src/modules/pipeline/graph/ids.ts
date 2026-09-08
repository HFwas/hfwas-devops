let seq = 0

export function nextClientKey(prefix = 'k'): string {
  seq += 1
  return `${prefix}-${seq}-${Math.random().toString(36).slice(2, 8)}`
}
