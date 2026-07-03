<script setup lang="ts">
import PmFieldConfigurator from '@/modules/pm/components/PmFieldConfigurator/index.vue'

const route = useRoute()
const projectId = computed(() => Number(route.params.projectId))
const typeCode = ref('task')
</script>

<template>
  <n-space vertical>
    <n-select
      v-model:value="typeCode"
      :options="[
        { label: '需求', value: 'requirement' },
        { label: '任务', value: 'task' },
        { label: '缺陷', value: 'bug' },
        { label: '测试用例', value: 'test_case' },
      ]"
      style="width: 160px"
    />
    <PmFieldConfigurator :project-id="projectId" :type-code="typeCode" />
  </n-space>
</template>
