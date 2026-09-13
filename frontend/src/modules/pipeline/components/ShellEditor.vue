<script setup lang="ts">
import { EditorView, basicSetup } from 'codemirror'
import { EditorState } from '@codemirror/state'
import { StreamLanguage } from '@codemirror/language'
import { shell } from '@codemirror/legacy-modes/mode/shell'
import { lintGutter } from '@codemirror/lint'
import { CheckCircle, RotateCcw } from '@lucide/vue'
import { useMessage } from 'naive-ui'
import { pipelineTaskKindApi } from '@/modules/pipeline/api/pipeline'
import { isApiError } from '@/shared/errors/apiError'

const props = defineProps<{
  modelValue: string
  kindValue: string
  defaultTemplate?: string
  height?: string
}>()

const emit = defineEmits<{
  'update:modelValue': [value: string]
}>()

const message = useMessage()
const editorRef = ref<HTMLDivElement>()
const validating = ref(false)
let view: EditorView | null = null

onMounted(() => {
  if (!editorRef.value) return
  const startState = EditorState.create({
    doc: props.modelValue || '',
    extensions: [
      basicSetup,
      StreamLanguage.define(shell),
      lintGutter(),
      EditorView.updateListener.of((update) => {
        if (update.docChanged) {
          emit('update:modelValue', update.state.doc.toString())
        }
      }),
      EditorView.theme({
        '&': { height: props.height || '200px' },
        '.cm-scroller': { overflow: 'auto' },
        '.cm-editor': {
          outline: 'none',
        },
      }),
    ],
  })
  view = new EditorView({ state: startState, parent: editorRef.value })
})

onUnmounted(() => view?.destroy())

watch(
  () => props.modelValue,
  (val) => {
    if (view && val !== view.state.doc.toString()) {
      view.dispatch({
        changes: { from: 0, to: view.state.doc.length, insert: val ?? '' },
      })
    }
  },
)

async function validateSyntax() {
  validating.value = true
  try {
    const result = await pipelineTaskKindApi.validateTemplate(props.kindValue, {
      script: view?.state.doc.toString() || '',
    })
    if (result.valid) {
      message.success('语法检查通过 ✓')
    } else {
      message.error('语法错误：\n' + (result.errors || []).join('\n'))
    }
  } catch (e: unknown) {
    message.error(isApiError(e) ? e.message : '验证请求失败')
  } finally {
    validating.value = false
  }
}

function resetToDefault() {
  if (props.defaultTemplate) {
    emit('update:modelValue', props.defaultTemplate)
  }
}

defineExpose({ validateSyntax })
</script>

<template>
  <div class="shell-editor">
    <div ref="editorRef" class="shell-editor-cm"></div>
    <div class="shell-editor-toolbar">
      <n-button size="tiny" quaternary :loading="validating" @click="validateSyntax">
        <template #icon><CheckCircle :size="14" /></template>
        验证语法
      </n-button>
      <n-button size="tiny" quaternary @click="resetToDefault">
        <template #icon><RotateCcw :size="14" /></template>
        重置默认
      </n-button>
    </div>
  </div>
</template>

<style scoped>
.shell-editor {
  border: 1px solid var(--wb-border, #e5e7eb);
  border-radius: 6px;
  overflow: hidden;
}

.shell-editor-cm {
  font-size: 13px;
  line-height: 1.5;
}

.shell-editor-toolbar {
  display: flex;
  gap: 4px;
  padding: 4px 8px;
  border-top: 1px solid var(--wb-border, #e5e7eb);
  background: var(--wb-th, #fafbfc);
}
</style>