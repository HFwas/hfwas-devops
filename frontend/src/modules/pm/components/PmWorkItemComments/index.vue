<script setup lang="ts">
import { useMessage } from 'naive-ui'
import { pmWorkItemApi } from '@/modules/pm/api'
import type { PmWorkItemComment } from '@/modules/pm/types'
import {
  avatarColor,
  avatarInitial,
  formatCommentHtml,
  formatDateTime,
  getCurrentAuthorName,
} from '@/modules/pm/utils/comment'

import type { EntityId } from '@/modules/pm/utils/id'

const props = withDefaults(defineProps<{
  workItemId: EntityId
  embedded?: boolean
}>(), {
  embedded: false,
})

const emit = defineEmits<{ 'update:count': [number] }>()

const message = useMessage()

const comments = ref<PmWorkItemComment[]>([])
const loading = ref(false)
const submitting = ref(false)
const content = ref('')
const replyTarget = ref<PmWorkItemComment | null>(null)
const sortDesc = ref(false)

const sortedComments = computed(() => {
  const list = [...comments.value]
  list.sort((a, b) => {
    const ta = a.createTime ?? ''
    const tb = b.createTime ?? ''
    return sortDesc.value ? tb.localeCompare(ta) : ta.localeCompare(tb)
  })
  return list
})

async function load() {
  loading.value = true
  try {
    comments.value = await pmWorkItemApi.listComments(props.workItemId)
    emit('update:count', comments.value.length)
  } finally {
    loading.value = false
  }
}

function startReply(comment: PmWorkItemComment) {
  replyTarget.value = comment
  content.value = `@${comment.authorName} `
}

function cancelReply() {
  replyTarget.value = null
  content.value = ''
}

async function submit() {
  const text = content.value.trim()
  if (!text) return
  submitting.value = true
  try {
    await pmWorkItemApi.saveComment({
      workItemId: props.workItemId,
      content: text,
      parentId: replyTarget.value?.id ?? null,
      authorName: getCurrentAuthorName(),
    })
    content.value = ''
    replyTarget.value = null
    await load()
  } catch (e) {
    message.error(e instanceof Error ? e.message : '评论失败')
  } finally {
    submitting.value = false
  }
}

async function remove(comment: PmWorkItemComment) {
  try {
    await pmWorkItemApi.deleteComment(comment.id)
    message.success('已删除')
    await load()
  } catch (e) {
    message.error(e instanceof Error ? e.message : '删除失败')
  }
}

watch(() => props.workItemId, load, { immediate: true })
</script>

<template>
  <div class="comments-panel" :class="{ embedded }">
    <div v-if="embedded" class="comments-toolbar">
      <n-button text size="small" @click="sortDesc = !sortDesc">
        {{ sortDesc ? '最新优先' : '最早优先' }}
      </n-button>
    </div>

    <n-spin :show="loading" class="comments-scroll">
      <n-empty v-if="!sortedComments.length" description="暂无评论，在下方输入第一条评论" />
      <div v-else class="comment-list">
        <div v-for="comment in sortedComments" :key="comment.id" class="comment-item">
          <n-avatar
            round
            :size="36"
            :style="{ backgroundColor: avatarColor(comment.authorName), color: '#fff' }"
          >
            {{ avatarInitial(comment.authorName) }}
          </n-avatar>
          <div class="comment-body">
            <div class="comment-header">
              <span class="comment-author">{{ comment.authorName }}</span>
            </div>
            <div class="comment-content" v-html="formatCommentHtml(comment.content)" />
            <div class="comment-footer">
              <span class="comment-time">{{ formatDateTime(comment.createTime) }}</span>
              <n-button text type="primary" size="tiny" @click="startReply(comment)">回复</n-button>
              <n-popconfirm v-if="comment.deletable" @positive-click="remove(comment)">
                <template #trigger>
                  <n-button text type="primary" size="tiny">删除</n-button>
                </template>
                确定删除这条评论吗？
              </n-popconfirm>
            </div>
          </div>
        </div>
      </div>
    </n-spin>

    <div class="comments-input">
      <n-input
        v-model:value="content"
        type="textarea"
        :placeholder="replyTarget ? `回复 ${replyTarget.authorName}` : '点击输入评论...'"
        :autosize="{ minRows: 2, maxRows: 6 }"
        @keydown.ctrl.enter="submit"
      />
      <n-space justify="end" style="margin-top: 8px">
        <n-button v-if="replyTarget" size="small" @click="cancelReply">取消回复</n-button>
        <n-button type="primary" size="small" :loading="submitting" :disabled="!content.trim()" @click="submit">
          发送
        </n-button>
      </n-space>
    </div>
  </div>
</template>

<style scoped>
.comments-panel {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.comments-panel.embedded {
  min-height: 480px;
  height: calc(100vh - 320px);
}

.comments-toolbar {
  display: flex;
  justify-content: flex-end;
}

.comments-scroll {
  flex: 1;
  overflow: auto;
  min-height: 200px;
}

.comment-list {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.comment-item {
  display: flex;
  gap: 12px;
  padding-bottom: 20px;
  border-bottom: 1px solid var(--n-border-color);
}

.comment-item:last-child {
  border-bottom: none;
  padding-bottom: 0;
}

.comment-body {
  flex: 1;
  min-width: 0;
}

.comment-header {
  margin-bottom: 6px;
}

.comment-author {
  font-weight: 500;
}

.comment-content {
  line-height: 1.6;
  word-break: break-word;
  white-space: pre-wrap;
}

.comment-content :deep(a) {
  color: var(--n-primary-color);
  text-decoration: none;
}

.comment-content :deep(a:hover) {
  text-decoration: underline;
}

.comment-footer {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-top: 8px;
}

.comment-time {
  color: var(--n-text-color-3);
  font-size: 12px;
}

.comments-input {
  border-top: 1px solid var(--n-border-color);
  padding-top: 12px;
  background: var(--n-color);
}
</style>
