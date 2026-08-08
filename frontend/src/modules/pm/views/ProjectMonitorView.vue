<script setup lang="ts">
import MonitorStatsCard from '@/modules/pm/components/workbench/MonitorStatsCard.vue'
import { useWorkbench } from '@/modules/pm/composables/useWorkbench'

// 占位页：后端暂无监控统计接口，先复用工作台的 mock 指标做骨架
const { monitorMetrics } = useWorkbench()
</script>

<template>
  <n-space vertical size="large" style="padding: 20px 24px 28px">
    <n-page-header title="项目监控" subtitle="项目运行状态与告警趋势" />
    <n-alert type="info" :bordered="false">
      监控数据接口尚未提供，当前为界面骨架与 mock 指标。
    </n-alert>
    <MonitorStatsCard :metrics="monitorMetrics" />
    <n-card :bordered="true" size="small">
      <n-empty description="趋势图表待接入监控数据后开放" />
    </n-card>
  </n-space>
</template>
