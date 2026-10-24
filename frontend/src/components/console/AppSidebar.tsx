import { useQuery } from '@tanstack/react-query'
import { ChevronDown, FolderKanban, Loader2, SquareKanban, Workflow } from 'lucide-react'
import { Link, useLocation } from 'react-router'
import { Collapsible, CollapsibleContent, CollapsibleTrigger } from '@/components/ui/collapsible'
import {
  Sidebar,
  SidebarContent,
  SidebarGroup,
  SidebarGroupContent,
  SidebarGroupLabel,
  SidebarMenu,
  SidebarMenuButton,
  SidebarMenuItem,
  SidebarRail,
} from '@/components/ui/sidebar'
import {
  CONTAINER_NAV_GROUPS,
  clusterIdFromPath,
  containerNavHref,
  isContainerNavActive,
} from '@/modules/container/nav'
import { useContainerCluster } from '@/modules/container/clusterStore'
import { pmProjectApi, pmProjectIssueTypeApi } from '@/modules/pm/api'
import {
  ALL_ITEMS_CODE,
  isProjectItemActive,
  projectBoardPath,
  projectContextFromPath,
  projectItemsPath,
  projectTypeIcon,
  projectWorkflowPath,
} from '@/modules/pm/nav'
import { isMenuItemActive, menuForScope, resolveShellScope } from '@/shared/console/navigation'

const groupLabelClass = 'font-semibold uppercase tracking-wide text-muted-foreground'
const activeIconClass = 'data-[active=true]:[&_svg]:text-sidebar-primary'

export function AppSidebar({ isAdmin }: { isAdmin: boolean }) {
  const { pathname } = useLocation()
  const scope = resolveShellScope(pathname)
  const project = scope === 'pm' ? projectContextFromPath(pathname) : null

  return (
    <Sidebar
      variant="inset"
      collapsible="icon"
      className="top-(--header-height) h-[calc(100svh-var(--header-height))]!"
    >
      <SidebarContent className="pt-2">
        {scope === 'container' ? (
          <ContainerMenu pathname={pathname} />
        ) : project ? (
          <ProjectMenu projectId={project.projectId} pathname={pathname} />
        ) : (
          menuForScope(scope, { isAdmin }).map((group) => (
            <SidebarGroup key={group.key}>
              {group.label && <SidebarGroupLabel className={groupLabelClass}>{group.label}</SidebarGroupLabel>}
              <SidebarGroupContent>
                <SidebarMenu>
                  {group.items.map((item) => {
                    const Icon = item.icon
                    return (
                      <SidebarMenuItem key={item.key}>
                        <SidebarMenuButton
                          asChild
                          isActive={isMenuItemActive(pathname, item)}
                          tooltip={item.label}
                          className={activeIconClass}
                        >
                          <Link to={item.to}>
                            <Icon />
                            <span>{item.label}</span>
                          </Link>
                        </SidebarMenuButton>
                      </SidebarMenuItem>
                    )
                  })}
                </SidebarMenu>
              </SidebarGroupContent>
            </SidebarGroup>
          ))
        )}
      </SidebarContent>
      <SidebarRail />
    </Sidebar>
  )
}

/** 进入项目后，侧栏切换为项目内菜单：项目切换、启用的事项类型、看板与工作流。 */
function ProjectMenu({ projectId, pathname }: { projectId: string; pathname: string }) {
  const projectQuery = useQuery({
    queryKey: ['pm-project', projectId],
    queryFn: () => pmProjectApi.getById(projectId),
    staleTime: 60_000,
  })
  const typesQuery = useQuery({
    queryKey: ['pm-issue-types', projectId],
    queryFn: () => pmProjectIssueTypeApi.list(projectId),
    staleTime: 60_000,
  })

  const types = (typesQuery.data ?? []).filter((type) => type.enabled !== 0)
  // 接口可能把计数序列化为字符串，先数值化再求和，避免 "0" + "1" 拼接成 010000。
  const allItemsCount = types.reduce((sum, type) => sum + Number(type.workItemCount ?? 0), 0)
  const routeType = projectContextFromPath(pathname)?.typeCode
  const typeCode = routeType && routeType !== ALL_ITEMS_CODE ? routeType : types[0]?.code ?? 'task'
  const projectName = projectQuery.data?.name?.trim() || '工作项'

  return (
    <>
      <SidebarGroup>
        <SidebarGroupLabel className={groupLabelClass}>项目</SidebarGroupLabel>
        <SidebarGroupContent>
          <SidebarMenu>
            <SidebarMenuItem>
              <SidebarMenuButton asChild tooltip="全部项目">
                <Link to="/pm/projects">
                  <FolderKanban />
                  <span>全部项目</span>
                </Link>
              </SidebarMenuButton>
            </SidebarMenuItem>
          </SidebarMenu>
        </SidebarGroupContent>
      </SidebarGroup>

      <SidebarGroup>
        <SidebarGroupLabel className={`${groupLabelClass} truncate`} title={projectName}>
          {projectName}
        </SidebarGroupLabel>
        <SidebarGroupContent>
          <SidebarMenu>
            <SidebarMenuItem>
              <SidebarMenuButton
                asChild
                isActive={pathname === projectItemsPath(projectId, ALL_ITEMS_CODE)
                  || pathname.startsWith(`${projectItemsPath(projectId, ALL_ITEMS_CODE)}/`)}
                tooltip="全部工作项"
                className={activeIconClass}
              >
                <Link to={projectItemsPath(projectId, ALL_ITEMS_CODE)}>
                  {(() => { const Icon = projectTypeIcon('all'); return <Icon /> })()}
                  <span>全部工作项</span>
                  <span className="ml-auto text-xs tabular-nums text-muted-foreground">{allItemsCount}</span>
                </Link>
              </SidebarMenuButton>
            </SidebarMenuItem>
            {types.map((type) => {
              const Icon = projectTypeIcon(type.code)
              const label = type.name || type.code
              return (
                <SidebarMenuItem key={type.code}>
                  <SidebarMenuButton
                    asChild
                    isActive={isProjectItemActive(pathname, projectId, type.code)}
                    tooltip={label}
                    className={activeIconClass}
                  >
                    <Link to={projectItemsPath(projectId, type.code)}>
                      <Icon />
                      <span>{label}</span>
                      <span className="ml-auto text-xs tabular-nums text-muted-foreground">{type.workItemCount ?? 0}</span>
                      {type.builtIn === 0 ? <span className="text-[10px] text-muted-foreground">自</span> : null}
                    </Link>
                  </SidebarMenuButton>
                </SidebarMenuItem>
              )
            })}
            {typesQuery.isLoading && (
              <SidebarMenuItem>
                <SidebarMenuButton disabled tooltip="正在加载事项类型">
                  <Loader2 className="animate-spin" />
                  <span>加载类型…</span>
                </SidebarMenuButton>
              </SidebarMenuItem>
            )}
          </SidebarMenu>
        </SidebarGroupContent>
      </SidebarGroup>

      <SidebarGroup>
        <SidebarGroupLabel className={groupLabelClass}>视图</SidebarGroupLabel>
        <SidebarGroupContent>
          <SidebarMenu>
            <SidebarMenuItem>
              <SidebarMenuButton
                asChild
                isActive={pathname.startsWith(`/pm/projects/${projectId}/board/`)}
                tooltip="看板"
                className={activeIconClass}
              >
                <Link to={projectBoardPath(projectId, typeCode)}>
                  <SquareKanban />
                  <span>看板</span>
                </Link>
              </SidebarMenuButton>
            </SidebarMenuItem>
            <SidebarMenuItem>
              <SidebarMenuButton
                asChild
                isActive={pathname.startsWith(`/pm/projects/${projectId}/settings/workflow/`)}
                tooltip="工作流"
                className={activeIconClass}
              >
                <Link to={projectWorkflowPath(projectId, typeCode)}>
                  <Workflow />
                  <span>工作流</span>
                </Link>
              </SidebarMenuButton>
            </SidebarMenuItem>
          </SidebarMenu>
        </SidebarGroupContent>
      </SidebarGroup>
    </>
  )
}

function ContainerMenu({ pathname }: { pathname: string }) {
  const storedClusterId = useContainerCluster((state) => state.currentId)
  const clusterId = clusterIdFromPath(pathname) ?? storedClusterId

  return (
    <>
      {CONTAINER_NAV_GROUPS.map((group) => (
        <Collapsible key={group.key} defaultOpen className="group/collapsible">
          <SidebarGroup>
            <SidebarGroupLabel asChild className={groupLabelClass}>
              <CollapsibleTrigger className="w-full cursor-pointer">
                <span>{group.label}</span>
                <ChevronDown className="ml-auto transition-transform group-data-[collapsible=icon]:hidden group-data-[state=open]/collapsible:rotate-180" />
              </CollapsibleTrigger>
            </SidebarGroupLabel>
            <CollapsibleContent>
              <SidebarGroupContent>
                <SidebarMenu>
                  {group.items.map((item) => {
                    const href = containerNavHref(item, clusterId)
                    const Icon = item.icon
                    const active = isContainerNavActive(pathname, item)
                    return (
                      <SidebarMenuItem key={item.key}>
                        {href ? (
                          <SidebarMenuButton asChild isActive={active} tooltip={item.label} className={activeIconClass}>
                            <Link to={href}>
                              <Icon />
                              <span>{item.label}</span>
                            </Link>
                          </SidebarMenuButton>
                        ) : (
                          <SidebarMenuButton disabled aria-disabled tooltip={item.label} className="opacity-40">
                            <Icon />
                            <span>{item.label}</span>
                          </SidebarMenuButton>
                        )}
                      </SidebarMenuItem>
                    )
                  })}
                </SidebarMenu>
              </SidebarGroupContent>
            </CollapsibleContent>
          </SidebarGroup>
        </Collapsible>
      ))}
    </>
  )
}
