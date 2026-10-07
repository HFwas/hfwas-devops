import { Link, useLocation } from 'react-router'
import {
  Sidebar,
  SidebarContent,
  SidebarGroup,
  SidebarGroupContent,
  SidebarGroupLabel,
  SidebarHeader,
  SidebarMenu,
  SidebarMenuButton,
  SidebarMenuItem,
  SidebarMenuSub,
  SidebarMenuSubButton,
  SidebarMenuSubItem,
  SidebarRail,
} from '@/components/ui/sidebar'
import {
  CONTAINER_NAV,
  clusterIdFromPath,
  containerNavHref,
  isContainerNavActive,
} from '@/modules/container/nav'
import { useContainerCluster } from '@/modules/container/clusterStore'
import { buildNavGroups, isNavItemActive, type NavItem } from '@/shared/console/navigation'

const groupLabelClass = 'font-semibold uppercase tracking-wide text-muted-foreground'
const activeIconClass = 'data-[active=true]:[&_svg]:text-sidebar-primary'

export function AppSidebar({ isAdmin }: { isAdmin: boolean }) {
  const { pathname } = useLocation()
  const storedClusterId = useContainerCluster((state) => state.currentId)
  const clusterId = clusterIdFromPath(pathname) ?? storedClusterId
  const groups = buildNavGroups({ isAdmin })
  const containerOpen = pathname === '/container' || pathname.startsWith('/container/')

  return (
    <Sidebar variant="inset" collapsible="icon">
      <SidebarHeader>
        <SidebarMenu>
          <SidebarMenuItem>
            <SidebarMenuButton size="lg" asChild tooltip="HFWAS DevOps">
              <Link to="/workbench">
                <span className="flex size-8 items-center justify-center rounded-md bg-sidebar-primary text-sm font-semibold text-sidebar-primary-foreground">
                  H
                </span>
                <span className="truncate font-semibold">HFWAS DevOps</span>
              </Link>
            </SidebarMenuButton>
          </SidebarMenuItem>
        </SidebarMenu>
      </SidebarHeader>
      <SidebarContent>
        {groups.map((group) => (
          <SidebarGroup key={group.key}>
            <SidebarGroupLabel className={groupLabelClass}>{group.label}</SidebarGroupLabel>
            <SidebarGroupContent>
              <SidebarMenu>
                {group.items.map((item) => (
                  <NavEntry
                    key={item.key}
                    item={item}
                    pathname={pathname}
                    clusterId={clusterId}
                    containerOpen={containerOpen}
                  />
                ))}
              </SidebarMenu>
            </SidebarGroupContent>
          </SidebarGroup>
        ))}
      </SidebarContent>
      <SidebarRail />
    </Sidebar>
  )
}

function NavEntry({
  item,
  pathname,
  clusterId,
  containerOpen,
}: {
  item: NavItem
  pathname: string
  clusterId: string | null
  containerOpen: boolean
}) {
  const active = isNavItemActive(pathname, item)
  const Icon = item.icon

  return (
    <SidebarMenuItem>
      {item.comingSoon ? (
        <SidebarMenuButton disabled aria-disabled tooltip={`${item.label}（即将推出）`} className="opacity-50">
          <Icon />
          <span>{item.label}</span>
        </SidebarMenuButton>
      ) : (
        <SidebarMenuButton asChild isActive={active} tooltip={item.label} className={activeIconClass}>
          <Link to={item.path}>
            <Icon />
            <span>{item.label}</span>
          </Link>
        </SidebarMenuButton>
      )}
      {item.key === 'container' && containerOpen && (
        <SidebarMenuSub>
          {CONTAINER_NAV.map((entry) => {
            const href = containerNavHref(entry, clusterId)
            const EntryIcon = entry.icon
            const entryActive = isContainerNavActive(pathname, entry)
            return (
              <SidebarMenuSubItem key={entry.key}>
                {href ? (
                  <SidebarMenuSubButton asChild isActive={entryActive} className={activeIconClass}>
                    <Link to={href}>
                      <EntryIcon />
                      <span>{entry.label}</span>
                    </Link>
                  </SidebarMenuSubButton>
                ) : (
                  <SidebarMenuSubButton aria-disabled className="pointer-events-none opacity-40">
                    <EntryIcon />
                    <span>{entry.label}</span>
                  </SidebarMenuSubButton>
                )}
              </SidebarMenuSubItem>
            )
          })}
        </SidebarMenuSub>
      )}
    </SidebarMenuItem>
  )
}
