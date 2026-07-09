# Step 02：流转 Validator（关单必填字段）

> 状态：已完成  
> 日期：2026-07-09  
> 路线图：[pm-evolution-roadmap.md](../pm-evolution-roadmap.md) Step 2  
> 对应对比文档：[pm-jira-comparison.md](../pm-jira-comparison.md) §5.1 Phase B

---

## 1. 动机与范围

### 1.1 动机

团队常见需求：「流转到已关闭前必须填写某字段」（类似 Jira Resolution）。Step 1 已有后置动作，本步补齐流转**提交前**校验。

### 1.2 范围内

| 层 | 内容 |
|----|------|
| 模型 | `TransitionRule.validators[]`，首期仅 `REQUIRED_FIELDS` |
| 后端 | 保存时校验配置；`transition` / 状态变更 `save` 执行前校验；`transition` 可附带字段值 |
| 前端 | 规则 Drawer 配置必填字段；看板/详情流转确认弹窗 |
| 文档 | 本文件 + 对比文档 + pm-api |

### 1.3 非目标

- Transition 名称 / ID（Step 3）
- Condition 可见性（Step 8）
- 自定义脚本校验、附件必填等其它 Validator 类型
- 看板拖拽（Step 6）

---

## 2. 数据模型

```text
TransitionRule
  ├── toStatus: string
  ├── validators: TransitionValidator[]     // NEW
  └── postFunctions: TransitionPostFunction[]

TransitionValidator
  ├── type: REQUIRED_FIELDS
  └── fieldKeys: string[]                   // 如 ["priority", "custom_resolution"]
```

仍存于 `pm_status_definition.transition_rules` JSON，**无需新列**。旧数据反序列化后 `validators` 为空。

禁止将 `status` 配为必填字段。

---

## 3. API

| 方法 | 路径 | 变更 |
|------|------|------|
| POST | `/pm/status/workflow/save` | 校验 validators 配置 |
| POST | `/pm/status/workflow/transition-meta` | **新增**：给定 from/to，返回必填字段元数据 |
| POST | `/pm/work-items/{id}/transition` | Body 增加可选 `fields`；先校验再改状态 |
| POST | `/pm/work-items`（save） | 若 status 变更，对当前事项字段执行同一套校验 |

执行顺序：

```text
validateTransition(path)
→ apply submitted fields（仅 transition API）
→ TransitionValidatorExecutor.validate()
→ UPDATE status
→ TransitionPostFunctionExecutor
→ UPDATE + ActivityLog
```

`__any__` 行与显式源状态行的 validators **合并**（同 post-functions）。

---

## 4. 前端 UI

- **配置**：`PmTransitionRuleDrawer` 增加「流转前校验」多选字段
- **运行时**：`PmTransitionDialog` — 有必填字段时弹出，提交 `{ toStatus, fields }`
- **看板**：移动前拉 `transition-meta`，有校验则弹窗
- **详情**：侧栏改状态时同样走弹窗 + `transition` API（有校验时），避免仅 `save` 无法补填

---

## 5. 验收标准

1. 配置「→ closed：必填 priority」后，未填 priority 的事项无法流转到 closed。
2. 弹窗中补填后可成功流转，且字段已写入。
3. 看板与详情均生效；方案导入导出保留 validators。
4. 非法 validator type / 空 fieldKeys / 未知字段 key 保存时报错。

---

## 6. 风险

| 风险 | 缓解 |
|------|------|
| 详情侧栏仍走 save | 有 validators 时改走 transition + 弹窗 |
| 系统字段与自定义字段取值路径不同 | Executor 统一读系统列 / customFields |
| 旧客户端只传 toStatus | 仍可用；缺字段则 4xx |

---

## 7. 修订记录

| 日期 | 说明 |
|------|------|
| 2026-07-09 | 初版 |
