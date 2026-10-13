import { clsx, type ClassValue } from 'clsx'
import { twMerge } from 'tailwind-merge'

export function cn(...inputs: ClassValue[]) {
  return twMerge(clsx(inputs))
}

/** 把后端返回的数字/字符串主键统一成字符串，供路由与 React key 使用。 */
export function asId(value: string | number): string {
  return String(value)
}
