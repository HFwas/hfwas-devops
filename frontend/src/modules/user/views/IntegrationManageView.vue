<script setup lang="ts">
import { h, onMounted } from 'vue'
import { NButton, NPopconfirm, NTag, useMessage } from 'naive-ui'
import { identityConnectorApi, tenantManageApi } from '@/modules/user/api'
import type { IdentityConnector, LdapConnectorConfig, Tenant } from '@/modules/user/types'
import { formatDateTime } from '@/modules/pm/utils/comment'
import { useDataTablePagination, usePagination } from '@/shared/composables/usePagination'

const message = useMessage()
const keyword = ref('')
const typeFilter = ref('all')
const loading = ref(false)
const connectors = ref<IdentityConnector[]>([])
const pagination = usePagination()
const connectorTypes = ref<{ label: string; value: string }[]>([])
const tenantOptions = ref<{ label: string; value: number | string }[]>([])

const showEditor = ref(false)
const editing = ref<IdentityConnector | null>(null)
const testing = ref(false)
const syncingId = ref<number | string | null>(null)

const form = ref({
  name: '',
  type: 'ldap',
  enabled: 1,
  defaultTenantId: null as number | string | null,
  autoCreateMember: 1,
  ldap: {
    url: '',
    baseDn: '',
    bindDn: '',
    bindPassword: '',
    userFilter: '(&(objectClass=person)(uid=*))',
    usernameAttribute: 'uid',
    displayNameAttribute: 'cn',
    emailAttribute: 'mail',
    phoneAttribute: 'telephoneNumber',
    externalIdAttribute: 'entryUUID',
  } as LdapConnectorConfig,
})

async function loadTypes() {
  const types = await identityConnectorApi.types()
  connectorTypes.value = types.map((t) => ({ label: t.label, value: t.type }))
}

async function loadTenants() {
  const tenants = await tenantManageApi.options()
  tenantOptions.value = tenants.map((t: Tenant) => ({
    label: `${t.name} (${t.code})`,
    value: t.id!,
  }))
}

async function load() {
  loading.value = true
  try {
    const page = await identityConnectorApi.page({
      ...pagination.query.value,
      keyword: keyword.value.trim() || undefined,
      type: typeFilter.value,
    })
    connectors.value = page.records
    pagination.setTotal(page.total)
  } finally {
    loading.value = false
  }
}

const { tablePagination, handlePageChange, handlePageSizeChange } = useDataTablePagination(pagination, load)

function onSearch() {
  pagination.resetPage()
  void load()
}

function defaultLdapConfig(): LdapConnectorConfig {
  return {
    url: '',
    baseDn: '',
    bindDn: '',
    bindPassword: '',
    userFilter: '(&(objectClass=person)(uid=*))',
    usernameAttribute: 'uid',
    displayNameAttribute: 'cn',
    emailAttribute: 'mail',
    phoneAttribute: 'telephoneNumber',
    externalIdAttribute: 'entryUUID',
  }
}

function parseLdapConfig(json?: string): LdapConnectorConfig {
  const defaults = defaultLdapConfig()
  if (!json) return defaults
  try {
    return { ...defaults, ...(JSON.parse(json) as LdapConnectorConfig) }
  } catch {
    return defaults
  }
}

function openCreate() {
  editing.value = null
  form.value = {
    name: '',
    type: 'ldap',
    enabled: 1,
    defaultTenantId: tenantOptions.value[0]?.value ?? null,
    autoCreateMember: 1,
    ldap: defaultLdapConfig(),
  }
  showEditor.value = true
}

async function openEdit(row: IdentityConnector) {
  editing.value = row
  const detail = row.id != null ? await identityConnectorApi.getById(row.id) : row
  form.value = {
    name: detail.name,
    type: detail.type,
    enabled: detail.enabled ?? 1,
    defaultTenantId: detail.defaultTenantId ?? null,
    autoCreateMember: detail.autoCreateMember ?? 1,
    ldap: parseLdapConfig(detail.configJson),
  }
  showEditor.value = true
}

function buildConfigJson() {
  if (form.value.type === 'ldap') {
    return JSON.stringify(form.value.ldap)
  }
  return '{}'
}

async function saveConnector() {
  try {
    await identityConnectorApi.save({
      id: editing.value?.id,
      name: form.value.name.trim(),
      type: form.value.type,
      configJson: buildConfigJson(),
      enabled: form.value.enabled,
      defaultTenantId: form.value.defaultTenantId ?? undefined,
      autoCreateMember: form.value.autoCreateMember,
    })
    message.success(editing.value ? '已更新' : '已创建')
    showEditor.value = false
    await load()
  } catch (e) {
    message.error(e instanceof Error ? e.message : '保存失败')
  }
}

async function testConnection() {
  testing.value = true
  try {
    const result = await identityConnectorApi.testConnection({
      id: editing.value?.id,
      type: form.value.type,
      configJson: buildConfigJson(),
    })
    if (result.success) {
      message.success(result.message)
    } else {
      message.warning(result.message)
    }
  } catch (e) {
    message.error(e instanceof Error ? e.message : '测试失败')
  } finally {
    testing.value = false
  }
}

async function syncConnector(row: IdentityConnector) {
  if (row.id == null) return
  syncingId.value = row.id
  try {
    const result = await identityConnectorApi.sync(row.id)
    message.success(result.message)
    await load()
  } catch (e) {
    message.error(e instanceof Error ? e.message : '同步失败')
  } finally {
    syncingId.value = null
  }
}

async function removeConnector(row: IdentityConnector) {
  if (row.id == null) return
  try {
    await identityConnectorApi.delete(row.id)
    message.success('已删除')
    await load()
  } catch (e) {
    message.error(e instanceof Error ? e.message : '删除失败')
  }
}

function syncStatusTag(row: IdentityConnector) {
  if (!row.lastSyncStatus) return h(NTag, { size: 'small', bordered: false }, () => '未同步')
  const type = row.lastSyncStatus === 'success' ? 'success' : row.lastSyncStatus === 'partial' ? 'warning' : 'error'
  return h(NTag, { size: 'small', type, bordered: false }, () => row.lastSyncStatus)
}

const columns = [
  { title: '名称', key: 'name' },
  {
    title: '类型',
    key: 'type',
    width: 160,
    render: (row: IdentityConnector) => row.typeLabel || row.type,
  },
  {
    title: '状态',
    key: 'enabled',
    width: 90,
    render: (row: IdentityConnector) =>
      h(NTag, { size: 'small', type: row.enabled === 1 ? 'success' : 'default' }, () =>
        row.enabled === 1 ? '启用' : '停用',
      ),
  },
  {
    title: '默认租户',
    key: 'defaultTenantName',
    width: 140,
    render: (row: IdentityConnector) => row.defaultTenantName || '-',
  },
  {
    title: '最近同步',
    key: 'lastSyncStatus',
    width: 100,
    render: (row: IdentityConnector) => syncStatusTag(row),
  },
  {
    title: '同步时间',
    key: 'lastSyncTime',
    width: 170,
    render: (row: IdentityConnector) => (row.lastSyncTime ? formatDateTime(row.lastSyncTime) : '-'),
  },
  {
    title: '同步说明',
    key: 'lastSyncMessage',
    ellipsis: { tooltip: true },
    render: (row: IdentityConnector) => row.lastSyncMessage || '-',
  },
  {
    title: '操作',
    key: 'actions',
    width: 220,
    render: (row: IdentityConnector) =>
      h('div', { style: 'display:flex;gap:8px;flex-wrap:wrap' }, [
        h(
          NButton,
          {
            text: true,
            type: 'primary',
            loading: syncingId.value === row.id,
            onClick: () => syncConnector(row),
          },
          () => '同步用户',
        ),
        h(NButton, { text: true, onClick: () => openEdit(row) }, () => '编辑'),
        h(
          NPopconfirm,
          { onPositiveClick: () => removeConnector(row) },
          {
            trigger: () => h(NButton, { text: true, type: 'error' }, () => '删除'),
            default: () => '确定删除该对接配置吗？',
          },
        ),
      ]),
  },
]

onMounted(async () => {
  await Promise.all([loadTypes(), loadTenants()])
  await load()
})
</script>

<template>
  <n-space vertical size="large">
    <n-page-header
      title="三方对接"
      subtitle="对接外部身份源（LDAP 等），将目录用户同步到平台账号；架构可扩展 OAuth2 / SAML 等类型"
    />

    <n-alert type="info" :bordered="false">
      同步会在平台创建/更新用户（auth_source=ldap），并按配置自动加入默认租户。本地 admin 账号不受影响。
      后续可在登录流程接入 LDAP 认证，当前版本以目录同步为主。
    </n-alert>

    <n-space>
      <n-input v-model:value="keyword" placeholder="搜索名称" clearable style="width: 220px" />
      <n-select
        v-model:value="typeFilter"
        :options="[
          { label: '全部类型', value: 'all' },
          ...connectorTypes,
        ]"
        style="width: 180px"
      />
      <n-button @click="onSearch">查询</n-button>
      <n-button type="primary" @click="openCreate">添加 LDAP 对接</n-button>
    </n-space>

    <n-data-table
      :columns="columns"
      :data="connectors"
      :loading="loading"
      :row-key="(r: IdentityConnector) => String(r.id)"
      :pagination="tablePagination"
      remote
      @update:page="handlePageChange"
      @update:page-size="handlePageSizeChange"
    />

    <n-modal
      v-model:show="showEditor"
      preset="card"
      :title="editing ? '编辑三方对接' : '添加 LDAP 对接'"
      style="width: 640px"
    >
      <n-form label-placement="top">
        <n-form-item label="对接名称" required>
          <n-input v-model:value="form.name" placeholder="如：公司 AD / OpenLDAP" />
        </n-form-item>
        <n-form-item label="对接类型">
          <n-select
            v-model:value="form.type"
            :options="connectorTypes"
            :disabled="!!editing"
          />
        </n-form-item>
        <template v-if="form.type === 'ldap'">
          <n-form-item label="LDAP 地址" required>
            <n-input v-model:value="form.ldap.url" placeholder="ldap://host:389 或 ldaps://host:636" />
          </n-form-item>
          <n-form-item label="Base DN" required>
            <n-input v-model:value="form.ldap.baseDn" placeholder="dc=example,dc=com" />
          </n-form-item>
          <n-form-item label="Bind DN" required>
            <n-input v-model:value="form.ldap.bindDn" placeholder="cn=admin,dc=example,dc=com" />
          </n-form-item>
          <n-form-item label="Bind 密码" required>
            <n-input
              v-model:value="form.ldap.bindPassword"
              type="password"
              show-password-on="click"
              placeholder="编辑时留空或 ****** 表示不修改"
            />
          </n-form-item>
          <n-form-item label="用户过滤条件">
            <n-input v-model:value="form.ldap.userFilter" placeholder="(&(objectClass=person)(uid=*))" />
          </n-form-item>
          <n-grid :cols="2" :x-gap="12">
            <n-gi>
              <n-form-item label="用户名字段">
                <n-input v-model:value="form.ldap.usernameAttribute" />
              </n-form-item>
            </n-gi>
            <n-gi>
              <n-form-item label="显示名字段">
                <n-input v-model:value="form.ldap.displayNameAttribute" />
              </n-form-item>
            </n-gi>
            <n-gi>
              <n-form-item label="邮箱字段">
                <n-input v-model:value="form.ldap.emailAttribute" />
              </n-form-item>
            </n-gi>
            <n-gi>
              <n-form-item label="电话字段">
                <n-input v-model:value="form.ldap.phoneAttribute" />
              </n-form-item>
            </n-gi>
          </n-grid>
        </template>
        <n-form-item label="默认租户">
          <n-select
            v-model:value="form.defaultTenantId"
            clearable
            placeholder="同步后自动加入的租户"
            :options="tenantOptions"
          />
        </n-form-item>
        <n-form-item label="自动加入租户">
          <n-switch v-model:value="form.autoCreateMember" :checked-value="1" :unchecked-value="0" />
        </n-form-item>
        <n-form-item label="启用">
          <n-switch v-model:value="form.enabled" :checked-value="1" :unchecked-value="0" />
        </n-form-item>
      </n-form>
      <template #footer>
        <n-space justify="space-between" style="width: 100%">
          <n-button :loading="testing" @click="testConnection">测试连接</n-button>
          <n-space>
            <n-button @click="showEditor = false">取消</n-button>
            <n-button type="primary" @click="saveConnector">保存</n-button>
          </n-space>
        </n-space>
      </template>
    </n-modal>
  </n-space>
</template>
