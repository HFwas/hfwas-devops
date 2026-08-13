<template>
  <n-modal
    :show="show"
    :title="isEdit ? '编辑接口' : '新建接口'"
    preset="card"
    style="width: 900px; max-height: 85vh;"
    :mask-closable="false"
    @update:show="$emit('update:show', $event)"
  >
    <n-spin :show="loading">
      <n-form
        ref="formRef"
        :model="formData"
        :rules="rules"
        label-placement="top"
        label-width="auto"
      >
        <!-- 基本信息 -->
        <n-collapse :default-expanded-names="['basic']">
          <n-collapse-item title="基本信息" name="basic">
            <n-grid :cols="2" :x-gap="16">
              <n-grid-item>
                <n-form-item label="接口名称" path="name">
                  <n-input v-model:value="formData.name" placeholder="请输入接口名称" />
                </n-form-item>
              </n-grid-item>
              <n-grid-item>
                <n-form-item label="请求方式" path="method">
                  <n-select v-model:value="formData.method" :options="methodOptions" />
                </n-form-item>
              </n-grid-item>
            </n-grid>
            <n-grid :cols="2" :x-gap="16">
              <n-grid-item>
                <n-form-item label="请求路径" path="path">
                  <n-input v-model:value="formData.path" placeholder="/api/v1/example" />
                </n-form-item>
              </n-grid-item>
              <n-grid-item>
                <n-form-item label="所属分组">
                  <n-tree-select
                    v-model:value="formData.groupId"
                    :options="groupOptions"
                    :default-expand-all="true"
                    placeholder="选择分组（可选）"
                    clearable
                  />
                </n-form-item>
              </n-grid-item>
            </n-grid>
            <n-form-item label="接口描述">
              <n-input
                v-model:value="formData.description"
                type="textarea"
                placeholder="接口描述（可选）"
                :rows="2"
              />
            </n-form-item>
          </n-collapse-item>

          <!-- 请求参数 -->
          <n-collapse-item title="请求参数" name="params">
            <param-editor v-model:params="formData.params!" />
          </n-collapse-item>

          <!-- 响应定义 -->
          <n-collapse-item title="响应定义" name="responses">
            <response-editor v-model:responses="formData.responses!" />
          </n-collapse-item>
        </n-collapse>
      </n-form>
    </n-spin>

    <template #footer>
      <n-space justify="end">
        <n-button @click="$emit('update:show', false)">取消</n-button>
        <n-button type="primary" :loading="saving" :disabled="loading" @click="handleSave">保存</n-button>
      </n-space>
    </template>
  </n-modal>
</template>

<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import { useMessage } from 'naive-ui'
import { useApiDefinitionStore } from '@/modules/api-test/define/stores/definition'
import { useApiGroupStore } from '@/modules/api-test/define/stores/group'
import { useAuthStore } from '@/modules/user/stores/auth'
import { HTTP_METHOD_OPTIONS } from '@/modules/api-test/define/types/definition'
import type { HttpMethod, ApiDefinitionCreateDTO, ApiDefinitionUpdateDTO, ApiDefinitionParamDTO, ApiDefinitionResponseDTO } from '@/modules/api-test/define/types/definition'
import ParamEditor from '@/modules/api-test/define/components/ParamEditor.vue'
import ResponseEditor from '@/modules/api-test/define/components/ResponseEditor.vue'

const props = defineProps<{
  show: boolean
  definitionId: number | null
  projectId: number
}>()

const emit = defineEmits<{
  'update:show': [value: boolean]
  saved: []
}>()

const store = useApiDefinitionStore()
const groupStore = useApiGroupStore()
const authStore = useAuthStore()
const message = useMessage()

const isEdit = computed(() => props.definitionId != null)
const userId = computed(() => Number(authStore.user?.id) || 0)

const formRef = ref<any>(null)
const loading = ref(false)
const saving = ref(false)

const methodOptions = HTTP_METHOD_OPTIONS.map((o) => ({ label: o.label, value: o.value }))

const groupOptions = computed(() => buildGroupTreeOptions(groupStore.groupTree))

const formData = ref<ApiDefinitionCreateDTO | ApiDefinitionUpdateDTO>({
  projectId: props.projectId,
  name: '',
  path: '',
  method: 'GET',
  description: '',
  params: [],
  responses: [],
})

const rules = {
  name: { required: true, message: '请输入接口名称', trigger: 'blur' },
  path: { required: true, message: '请输入请求路径', trigger: 'blur' },
  method: { required: true, message: '请选择请求方式', trigger: 'change' },
}

watch(() => props.show, async (val) => {
  if (val) {
    if (props.definitionId) {
      // 编辑模式：加载详情
      loading.value = true
      try {
        const detail = await store.loadDetail(props.definitionId)
        formData.value = {
          projectId: props.projectId,
          groupId: detail.groupId,
          name: detail.name,
          path: detail.path,
          method: detail.method,
          description: detail.description,
          params: detail.params?.map(mapParamToDTO) || [],
          responses: detail.responses?.map(mapResponseToDTO) || [],
        }
      } catch (e: any) {
        message.error(e.message || '加载失败')
      } finally {
        loading.value = false
      }
    } else {
      // 新建模式：重置表单
      formData.value = {
        projectId: props.projectId,
        name: '',
        path: '',
        method: 'GET',
        description: '',
        params: [],
        responses: [],
      }
    }
  }
})

function buildGroupTreeOptions(groups: any[]): any[] {
  return groups.map((g) => ({
    key: g.id,
    label: g.name,
    children: g.children?.length ? buildGroupTreeOptions(g.children) : undefined,
  }))
}

function mapParamToDTO(p: any): ApiDefinitionParamDTO {
  return {
    id: p.id,
    paramType: p.paramType,
    name: p.name,
    dataType: p.dataType || 'string',
    required: p.required,
    defaultValue: p.defaultValue,
    description: p.description,
    parentId: p.parentId,
    sortOrder: p.sortOrder,
    example: p.example,
  }
}

function mapResponseToDTO(r: any): ApiDefinitionResponseDTO {
  return {
    id: r.id,
    statusCode: r.statusCode || 200,
    contentType: r.contentType || 'application/json',
    description: r.description,
    bodySchema: r.bodySchema,
    bodyExample: r.bodyExample,
  }
}

async function handleSave() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return

  saving.value = true
  try {
    if (isEdit.value) {
      await store.update(props.definitionId!, formData.value as ApiDefinitionUpdateDTO, userId.value)
      message.success('更新成功')
    } else {
      await store.create(formData.value as ApiDefinitionCreateDTO, userId.value)
      message.success('创建成功')
    }
    emit('saved')
  } catch (e: any) {
    message.error(e.message || '保存失败')
  } finally {
    saving.value = false
  }
}
</script>