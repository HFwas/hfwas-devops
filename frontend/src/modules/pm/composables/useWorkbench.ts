import type { Component } from 'vue'
import {
  AlarmClock,
  BadgeCheck,
  Bug,
  CalendarClock,
  ChartColumn,
  CircleCheck,
  ClipboardList,
  FileStack,
  FolderKanban,
  Gauge,
  Layers,
  ListChecks,
  Rocket,
  ShieldAlert,
  Sparkles,
  Target,
  TimerReset,
  TrendingUp,
  Users,
} from '@lucide/vue'

/**
 * 工作台数据源
 * 目前全部为 mock：后端暂无对应的统计与最近访问接口。
 * 接入真实数据时替换本文件各 loader 实现即可，卡片组件无需改动。
 */

/** 最近访问的项目快捷入口 */
export interface RecentVisitItem {
  key: string
  name: string
  scope: string
  icon: Component
  path: string
  visitedAt: string
}

/** 我的资源快捷入口方块 */
export interface ResourceEntry {
  key: string
  name: string
  description: string
  icon: Component
  path: string
  count: number
  /** 图标底色，对应 scoped 样式里的 tone class 后缀 */
  tone: 'blue' | 'violet' | 'green' | 'amber' | 'cyan' | 'rose'
  tag?: string
}

/** 我的资源卡片头部小结 */
export interface ResourceSummary {
  key: string
  label: string
  value: string
  icon: Component
}

/** 业务监控指标 */
export interface MonitorMetric {
  key: string
  label: string
  value: number
  unit: string
  icon: Component
  /** 环比；正数上升、负数下降、缺省不展示 */
  delta?: number
  tone: 'neutral' | 'positive' | 'warning' | 'danger'
  path?: string
}

/** 顶栏文档下拉的帮助链接 */
export interface HelpLink {
  key: string
  label: string
  href: string
}

// TODO 接入真实接口：最近访问可由前端本地记录或后端埋点提供
const RECENT_VISITS: RecentVisitItem[] = [
  { key: 'p-1024', name: 'DevOps 平台重构', scope: '平台研发部', icon: FolderKanban, path: '/pm/projects/1024', visitedAt: '10 分钟前' },
  { key: 'p-1031', name: '统一权限中心', scope: '基础架构组', icon: Layers, path: '/pm/projects/1031', visitedAt: '1 小时前' },
  { key: 'p-1045', name: '工作项引擎 v2', scope: '平台研发部', icon: ListChecks, path: '/pm/projects/1045', visitedAt: '3 小时前' },
  { key: 'p-1052', name: '客户门户改版', scope: '前端体验组', icon: Sparkles, path: '/pm/projects/1052', visitedAt: '昨天' },
  { key: 'p-1060', name: '缺陷治理专项', scope: '质量保障组', icon: Bug, path: '/pm/projects/1060', visitedAt: '2 天前' },
  { key: 'p-1077', name: 'Q3 交付节奏优化', scope: 'PMO', icon: TimerReset, path: '/pm/projects/1077', visitedAt: '3 天前' },
]

// TODO 接入真实接口：各资源计数可由 pm 统计接口一次性返回
const RESOURCE_ENTRIES: ResourceEntry[] = [
  { key: 'projects', name: '项目', description: '项目空间与配置', icon: FolderKanban, path: '/pm/projects', count: 24, tone: 'blue' },
  { key: 'workitems', name: '工作项', description: '需求 / 任务 / 缺陷', icon: ClipboardList, path: '/pm/projects', count: 1286, tone: 'violet' },
  { key: 'iterations', name: '迭代', description: '进行中与规划中迭代', icon: CalendarClock, path: '/pm/projects', count: 37, tone: 'green' },
  { key: 'schemes', name: '方案模板', description: '字段 / 流程 / 视图方案', icon: FileStack, path: '/pm/projects', count: 15, tone: 'amber', tag: '新' },
  { key: 'members', name: '成员与角色', description: '权限与角色分配', icon: Users, path: '/user/accounts', count: 152, tone: 'cyan' },
  { key: 'releases', name: '发布计划', description: '版本与上线窗口', icon: Rocket, path: '/pm/projects', count: 9, tone: 'rose' },
]

const RESOURCE_SUMMARY: ResourceSummary[] = [
  { key: 'health', label: '健康度', value: '92 分', icon: Target },
  { key: 'overdue', label: '逾期工作项', value: '18 项', icon: AlarmClock },
]

// TODO 接入真实接口：项目维度统计指标
const MONITOR_METRICS: MonitorMetric[] = [
  { key: 'total', label: '项目总数', value: 24, unit: '个', icon: FolderKanban, tone: 'neutral', path: '/pm/projects' },
  { key: 'running', label: '进行中项目', value: 11, unit: '个', icon: Gauge, delta: 2, tone: 'positive' },
  { key: 'done', label: '已完成项目', value: 9, unit: '个', icon: CircleCheck, tone: 'neutral' },
  { key: 'pending', label: '待审批项目', value: 3, unit: '个', icon: BadgeCheck, tone: 'warning' },
  { key: 'weeklyNew', label: '本周新增项目', value: 4, unit: '个', icon: TrendingUp, delta: 1, tone: 'positive' },
  { key: 'alerts', label: '项目告警数', value: 6, unit: '条', icon: ShieldAlert, delta: -2, tone: 'danger' },
]

const HELP_LINKS: HelpLink[] = [
  { key: 'quickstart', label: '快速入门', href: '#' },
  { key: 'api', label: 'API 文档', href: '#' },
  { key: 'changelog', label: '更新日志', href: '#' },
]

export function useWorkbench() {
  return {
    recentVisits: RECENT_VISITS,
    resourceEntries: RESOURCE_ENTRIES,
    resourceSummary: RESOURCE_SUMMARY,
    monitorMetrics: MONITOR_METRICS,
    monitorIcon: ChartColumn,
  }
}

export function useHelpLinks() {
  return HELP_LINKS
}
