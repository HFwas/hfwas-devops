/** 与后端 `CurrentUserAccessor.isAdmin()` 一致：role 去空白后忽略大小写等于 admin。 */
export function isPlatformAdmin(role?: string | null): boolean {
  return role?.trim().toLowerCase() === 'admin'
}
