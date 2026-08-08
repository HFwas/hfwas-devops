# Console Workbench Nav Implementation Plan

> **For agentic workers:** Implement task-by-task. Steps use checkbox (`- [x]`) syntax for tracking.

**Goal:** Platform workbench as default home; product switcher enters PM; User Center as header entry only.

**Architecture:** Move workbench off PM tabs to `/workbench`; PM tabs become projects + monitor; resolve active product from route path.

**Tech Stack:** Vue 3, Vue Router, Naive UI, existing `shared/console/*`

## Global Constraints

- Greenfield: no legacy dual-write; no `/pm/overview` route
- No「系统设置」entry

---

### Task 1: Routes & product/tab config

- [x] Add `/workbench` route; `/` → `/workbench`; remove PM overview tab route (redirect ok)
- [x] PM product `path` → `/pm/projects`; `resolveActiveProductKey(path)`
- [x] `CONSOLE_TABS`: only 项目、项目监控
- [x] Update default redirects (login, admin guard, tenant switch, UserLayout back)

### Task 2: AppShell & ProductSwitcher

- [x] Header「用户中心」button for admins; strip from user dropdown
- [x] ProductSwitcher uses route-based current product; idle label「选择产品」
- [x] Brand link → `/workbench`

### Task 3: Workbench copy tweak

- [x] Platform-level hero copy (not「当前产品：项目管理」as product home)
