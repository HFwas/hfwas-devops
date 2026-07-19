<script setup lang="ts">
import { pmWorkItemApi } from '@/modules/pm/api'
import type { ActivityFilter, PmWorkItemActivity, PmWorkItemComment, PmWorkItemTimelineItem } from '@/modules/pm/types'
import {
  avatarColor,
  avatarInitial,
  formatCommentHtml,
  formatDateTime,
} from '@/modules/pm/utils/comment'
import type { EntityId } from '@/modules/pm/utils/id'

const props = defineProps<{
  workItemId: EntityId
}>()

const loading = ref(false)
const filter = ref<ActivityFilter>('all')
const sortDesc = ref(true)
const activities = ref<PmWorkItemActivity[]>([])
const comments = ref<PmWorkItemComment[]>([])

const filterTabs: { label: string; value: ActivityFilter }[] = [
  { label: '全部', value: 'all' },
  { label: '变更', value: 'change' },
  { label: '评论', value: 'comment' },
  { label: '关联', value: 'link' },
]

function parseLinkExtra(activity: PmWorkItemActivity) {
  if (!activity.extraJson) return null
  try {
    return JSON.parse(activity.extraJson) as {
      linkType?: string
      targetId?: string
      targetKey?: string
      targetTitle?: string
    }
  } catch {
    return null
  }
}

function linkTypeLabel(linkType?: string) {
  const map: Record<string, string> = {
    relates_to: '关联',
    blocks: '阻塞',
    duplicates: '重复',
  }
  return map[linkType ?? ''] ?? linkType ?? '关联'
}

function buildTimeline(): PmWorkItemTimelineItem[] {
  const items: PmWorkItemTimelineItem[] = []

  const grouped = new Map<string, PmWorkItemActivity[]>()
  const singles: PmWorkItemActivity[] = []
  const seenBatches = new Set<string>()

  for (const act of activities.value) {
    if (act.eventType === 'FIELD_CHANGE' && act.batchId) {
      if (!seenBatches.has(act.batchId)) {
        seenBatches.add(act.batchId)
        const batchItems = activities.value.filter((a) => a.batchId === act.batchId)
        grouped.set(act.batchId, batchItems)
      }
    } else {
      singles.push(act)
    }
  }

  for (const [batchId, changes] of grouped) {
    const sorted = [...changes].sort((a, b) => (a.fieldName ?? '').localeCompare(b.fieldName ?? ''))
    items.push({
      kind: 'activity',
      time: sorted[0]?.createTime ?? '',
      activity: sorted[0],
      groupedChanges: sorted,
    })
    void batchId
  }

  for (const act of singles) {
    items.push({ kind: 'activity', time: act.createTime ?? '', activity: act })
  }

  for (const comment of comments.value) {
    items.push({ kind: 'comment', time: comment.createTime ?? '', comment })
  }

  items.sort((a, b) => {
    const ta = a.time || ''
    const tb = b.time || ''
    return sortDesc.value ? tb.localeCompare(ta) : ta.localeCompare(tb)
  })
  return items
}

const timeline = computed(() => {
  const all = buildTimeline()
  if (filter.value === 'all') return all
  if (filter.value === 'comment') return all.filter((i) => i.kind === 'comment')
  if (filter.value === 'link') {
    return all.filter((i) => i.kind === 'activity' && i.activity?.eventType === 'LINK_ADD')
  }
  return all.filter((i) => {
    if (i.kind !== 'activity') return false
    const type = i.activity?.eventType
    return type === 'FIELD_CHANGE' || type === 'CREATE'
  })
})

function emptyLabel() {
  const map: Record<ActivityFilter, string> = {
    all: '暂无动态',
    change: '暂无字段变更记录',
    comment: '暂无评论',
    link: '暂无关联记录',
  }
  return map[filter.value]
}

function displayValue(label?: string | null, fallback?: string | null) {
  if (label != null && label !== '') return label
  if (fallback != null && fallback !== '') return fallback
  return '空'
}

async function load(silent = false) {
  if (!silent) loading.value = true
  try {
    const [actList, commentList] = await Promise.all([
      pmWorkItemApi.listActivities(props.workItemId),
      pmWorkItemApi.listComments(props.workItemId),
    ])
    activities.value = actList ?? []
    comments.value = commentList ?? []
  } finally {
    if (!silent) loading.value = false
  }
}

defineExpose({ reload: () => load(true) })

watch(() => props.workItemId, load, { immediate: true })
</script>

<template>
  <div class="activity-panel">
    <div class="activity-toolbar">
      <n-tabs v-model:value="filter" type="segment" size="small" class="filter-tabs">
        <n-tab-pane v-for="tab in filterTabs" :key="tab.value" :name="tab.value" :tab="tab.label" />
      </n-tabs>
      <n-button text size="small" @click="sortDesc = !sortDesc">
        {{ sortDesc ? '最新优先' : '最早优先' }}
      </n-button>
    </div>

    <n-spin :show="loading" class="activity-scroll">
      <n-empty v-if="!timeline.length" :description="emptyLabel()" />
      <div v-else class="timeline">
        <div v-for="(entry, index) in timeline" :key="`${entry.kind}-${entry.activity?.id ?? entry.comment?.id}-${index}`" class="timeline-item">
          <div class="timeline-axis">
            <span class="timeline-dot" />
            <span v-if="index < timeline.length - 1" class="timeline-line" />
          </div>

          <div class="timeline-content">
            <template v-if="entry.kind === 'comment' && entry.comment">
              <div class="entry-head">
                <n-avatar
                  round
                  :size="28"
                  :style="{ backgroundColor: avatarColor(entry.comment.authorName), color: '#fff' }"
                >
                  {{ avatarInitial(entry.comment.authorName) }}
                </n-avatar>
                <span class="actor">{{ entry.comment.authorName }}</span>
                <span class="action">发表了评论</span>
                <span class="time">{{ formatDateTime(entry.comment.createTime) }}</span>
              </div>
              <div class="comment-box" v-html="formatCommentHtml(entry.comment.content)" />
            </template>

            <template v-else-if="entry.activity?.eventType === 'CREATE'">
              <div class="entry-head">
                <span class="actor">{{ entry.activity.actorName }}</span>
                <span class="action">创建了工作项</span>
                <span class="time">{{ formatDateTime(entry.activity.createTime) }}</span>
              </div>
            </template>

            <template v-else-if="entry.activity?.eventType === 'LINK_ADD'">
              <div class="entry-head">
                <span class="actor">{{ entry.activity.actorName }}</span>
                <span class="action">新增了{{ linkTypeLabel(parseLinkExtra(entry.activity)?.linkType) }}</span>
                <span class="time">{{ formatDateTime(entry.activity.createTime) }}</span>
              </div>
              <div class="link-box">
                <span class="link-key">{{ entry.activity.newLabel }}</span>
                <span v-if="parseLinkExtra(entry.activity)?.targetTitle" class="link-title">
                  {{ parseLinkExtra(entry.activity)?.targetTitle }}
                </span>
              </div>
            </template>

            <template v-else-if="entry.groupedChanges?.length">
              <div class="entry-head">
                <span class="actor">{{ entry.groupedChanges[0].actorName }}</span>
                <span class="action">编辑了 {{ entry.groupedChanges.length }} 个字段</span>
                <span class="time">{{ formatDateTime(entry.groupedChanges[0].createTime) }}</span>
              </div>
              <div class="change-list">
                <div v-for="change in entry.groupedChanges" :key="change.id" class="change-row">
                  <span class="field-name">{{ change.fieldName }}</span>
                  <div class="diff">
                    <span
                      class="diff-old"
                      :class="{ 'diff-status': change.fieldType === 'STATUS' }"
                    >{{ displayValue(change.oldLabel, change.oldValue) }}</span>
                    <span class="diff-arrow">→</span>
                    <span
                      class="diff-new"
                      :class="{ 'diff-status-new': change.fieldType === 'STATUS' }"
                    >{{ displayValue(change.newLabel, change.newValue) }}</span>
                  </div>
                </div>
              </div>
            </template>

            <template v-else-if="entry.activity?.eventType === 'FIELD_CHANGE'">
              <div class="entry-head">
                <span class="actor">{{ entry.activity.actorName }}</span>
                <span class="action">编辑了</span>
                <span class="field-inline">{{ entry.activity.fieldName }}</span>
                <span class="time">{{ formatDateTime(entry.activity.createTime) }}</span>
              </div>
              <div class="diff single-diff">
                <span
                  class="diff-old"
                  :class="{ 'diff-status': entry.activity.fieldType === 'STATUS' }"
                >{{ displayValue(entry.activity.oldLabel, entry.activity.oldValue) }}</span>
                <span class="diff-arrow">→</span>
                <span
                  class="diff-new"
                  :class="{ 'diff-status-new': entry.activity.fieldType === 'STATUS' }"
                >{{ displayValue(entry.activity.newLabel, entry.activity.newValue) }}</span>
              </div>
            </template>
          </div>
        </div>
      </div>
    </n-spin>
  </div>
</template>

<style scoped>
.activity-panel {
  display: flex;
  flex-direction: column;
  gap: 12px;
  min-height: 480px;
}

.activity-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.filter-tabs {
  flex: 1;
  max-width: 420px;
}

.activity-scroll {
  flex: 1;
  overflow: auto;
  min-height: 240px;
}

.timeline {
  display: flex;
  flex-direction: column;
  gap: 0;
}

.timeline-item {
  display: flex;
  gap: 12px;
  padding-bottom: 20px;
}

.timeline-axis {
  width: 16px;
  display: flex;
  flex-direction: column;
  align-items: center;
  flex-shrink: 0;
}

.timeline-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: #d0d0d0;
  margin-top: 6px;
  flex-shrink: 0;
}

.timeline-line {
  flex: 1;
  width: 0;
  border-left: 1px dashed #e0e0e6;
  margin-top: 4px;
  min-height: 24px;
}

.timeline-content {
  flex: 1;
  min-width: 0;
}

.entry-head {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 6px;
  margin-bottom: 8px;
  font-size: 13px;
  color: var(--n-text-color-2);
}

.actor {
  font-weight: 500;
  color: var(--n-text-color-1);
}

.action {
  color: var(--n-text-color-3);
}

.field-inline {
  font-weight: 500;
  color: var(--n-text-color-1);
}

.time {
  margin-left: auto;
  font-size: 12px;
  color: var(--n-text-color-3);
}

.change-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.change-row {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.field-name {
  font-size: 13px;
  color: var(--n-text-color-2);
}

.diff {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
}

.single-diff {
  margin-top: 2px;
}

.diff-old {
  padding: 2px 8px;
  border-radius: 4px;
  background: #fff1f0;
  color: #cf1322;
  text-decoration: line-through;
  font-size: 13px;
}

.diff-new {
  padding: 2px 8px;
  border-radius: 4px;
  background: #f6ffed;
  color: #389e0d;
  font-size: 13px;
}

.diff-status {
  text-decoration: none;
  background: #fff1f0;
  color: #cf1322;
}

.diff-status-new {
  background: #f6ffed;
  color: #389e0d;
  font-weight: 500;
}

.diff-arrow {
  color: var(--n-text-color-3);
  font-size: 12px;
}

.comment-box {
  padding: 10px 12px;
  background: #fafafa;
  border-radius: 6px;
  line-height: 1.6;
  font-size: 13px;
}

.comment-box :deep(a) {
  color: var(--n-primary-color);
}

.link-box {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
}

.link-key {
  color: var(--n-primary-color);
  font-weight: 500;
}

.link-title {
  color: var(--n-text-color-3);
}
</style>
