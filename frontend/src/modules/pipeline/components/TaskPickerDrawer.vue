<script setup lang="ts">
import { JOB_KIND_CATALOG, JOB_KIND_GROUPS } from '@/modules/pipeline/graph/jobCatalog'
import { jobKindIcon } from '@/modules/pipeline/graph/jobIcons'
import { canAddKindToStage, type StageInsertTarget } from '@/modules/pipeline/graph/pipelineGraph'
import { jobKindTone } from '@/modules/pipeline/status'
import type { EditorStage, JobKind } from '@/modules/pipeline/types/pipeline'

const show = defineModel<boolean>('show', { default: false })

const props = defineProps<{
  stages: EditorStage[]
  target: StageInsertTarget | null
}>()

const emit = defineEmits<{
  pick: [kind: JobKind]
}>()

const activeGroup = ref<(typeof JOB_KIND_GROUPS)[number]>(JOB_KIND_GROUPS[0])
const listEl = ref<HTMLElement | null>(null)

const stageKey = computed(() => (props.target?.type === 'parallel' ? props.target.stageKey : null))

function grouped(group: string) {
  return JOB_KIND_CATALOG.filter((item) => item.group === group)
}

function disabledReason(kind: JobKind): string | null {
  return canAddKindToStage(props.stages, stageKey.value, kind)
}

function scrollTo(group: (typeof JOB_KIND_GROUPS)[number]) {
  activeGroup.value = group
  const section = listEl.value?.querySelector(`[data-group="${group}"]`)
  section?.scrollIntoView({ block: 'start', behavior: 'smooth' })
}

function onPick(kind: JobKind) {
  if (disabledReason(kind)) return
  emit('pick', kind)
}

watch(show, (open) => {
  if (open) activeGroup.value = JOB_KIND_GROUPS[0]
})
</script>

<template>
  <n-drawer v-model:show="show" :width="560" placement="right">
    <n-drawer-content title="选择任务" closable :native-scrollbar="false" body-content-style="padding: 0; height: 100%">
      <div class="picker">
        <nav class="picker-rail">
          <button
            v-for="group in JOB_KIND_GROUPS"
            :key="group"
            type="button"
            class="picker-rail-item"
            :class="{ 'is-active': activeGroup === group }"
            @click="scrollTo(group)"
          >
            {{ group }}
          </button>
        </nav>
        <div ref="listEl" class="picker-list">
          <section v-for="group in JOB_KIND_GROUPS" :key="group" class="picker-section" :data-group="group">
            <h3 class="picker-section-title">{{ group }}</h3>
            <div class="picker-grid">
              <button
                v-for="item in grouped(group)"
                :key="item.value"
                type="button"
                class="picker-card"
                :class="{ 'is-disabled': !!disabledReason(item.value) }"
                :title="disabledReason(item.value) ?? item.description"
                :disabled="!!disabledReason(item.value)"
                @click="onPick(item.value)"
              >
                <span class="picker-icon" :class="`tone-${jobKindTone(item.value)}`">
                  <component :is="jobKindIcon(item.value)" :size="18" />
                </span>
                <span class="picker-text">
                  <span class="picker-name">{{ item.label }}</span>
                  <span class="picker-desc">{{ item.description }}</span>
                </span>
              </button>
            </div>
          </section>
        </div>
      </div>
    </n-drawer-content>
  </n-drawer>
</template>

<style scoped>
.picker {
  display: flex;
  height: 100%;
  min-height: 0;
  background: var(--wb-card-bg, #fff);
}

.picker-rail {
  display: flex;
  flex-direction: column;
  width: 96px;
  flex-shrink: 0;
  padding: 8px 0;
  border-right: 1px solid var(--wb-border-soft, #eef0f3);
  background: var(--wb-th, #fafbfc);
}

.picker-rail-item {
  padding: 10px 8px;
  border: none;
  background: transparent;
  font: inherit;
  font-size: 13px;
  color: var(--wb-muted, #646a73);
  text-align: center;
  cursor: pointer;
}

.picker-rail-item:hover {
  color: var(--wb-primary, #3370ff);
}

.picker-rail-item.is-active {
  color: var(--wb-primary, #3370ff);
  font-weight: 600;
  background: var(--wb-card-bg, #fff);
  box-shadow: inset 3px 0 0 var(--wb-primary, #3370ff);
}

.picker-list {
  flex: 1;
  min-width: 0;
  padding: 12px 16px 24px;
  overflow: auto;
}

.picker-section + .picker-section {
  margin-top: 18px;
}

.picker-section-title {
  margin: 0 0 10px;
  padding-left: 8px;
  border-left: 3px solid var(--wb-primary, #3370ff);
  font-size: 13px;
  font-weight: 600;
  line-height: 1.2;
  color: var(--wb-primary, #3370ff);
}

.picker-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 10px;
}

.picker-card {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  min-height: 72px;
  padding: 12px;
  border: 1px solid var(--wb-border, #e5e7eb);
  border-radius: 6px;
  background: var(--wb-card-bg, #fff);
  font: inherit;
  color: inherit;
  text-align: left;
  cursor: pointer;
}

.picker-card:hover:not(:disabled) {
  border-color: var(--wb-primary, #3370ff);
  box-shadow: 0 2px 8px rgba(51, 112, 255, 0.12);
}

.picker-card.is-disabled {
  cursor: not-allowed;
  opacity: 0.45;
}

.picker-icon {
  display: inline-flex;
  flex-shrink: 0;
  align-items: center;
  justify-content: center;
  width: 36px;
  height: 36px;
  border-radius: 50%;
}

.tone-blue {
  background: #eff6ff;
  color: #2563eb;
}
.tone-violet {
  background: #f5f3ff;
  color: #7c3aed;
}
.tone-green {
  background: #ecfdf5;
  color: #059669;
}
.tone-amber {
  background: #fffbeb;
  color: #d97706;
}
.tone-cyan {
  background: #ecfeff;
  color: #0891b2;
}
.tone-rose {
  background: #fff1f2;
  color: #e11d48;
}

.picker-text {
  min-width: 0;
}

.picker-name {
  display: block;
  font-size: 13px;
  font-weight: 600;
  color: var(--wb-ink, #1f2329);
}

.picker-desc {
  display: -webkit-box;
  margin-top: 4px;
  overflow: hidden;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
  font-size: 12px;
  line-height: 1.4;
  color: var(--wb-muted, #646a73);
}
</style>
