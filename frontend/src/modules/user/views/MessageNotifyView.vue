<script setup lang="ts">
import { onMounted } from 'vue'
import { useMessage } from 'naive-ui'
import { messageNotifyApi } from '@/modules/user/api'
import type { NotifyChannel, WebhookChannelConfig } from '@/modules/user/types'

const message = useMessage()
const loading = ref(false)
const saving = ref<string | null>(null)
const testing = ref<string | null>(null)
const channels = ref<NotifyChannel[]>([])

const forms = ref<Record<string, { enabled: number; webhookUrl: string; secret: string; remark: string }>>({
  site: { enabled: 1, webhookUrl: '', secret: '', remark: '' },
  dingtalk: { enabled: 0, webhookUrl: '', secret: '', remark: '' },
  feishu: { enabled: 0, webhookUrl: '', secret: '', remark: '' },
})

function parseConfig(json?: string): WebhookChannelConfig {
  if (!json) return { webhookUrl: '', secret: '' }
  try {
    return JSON.parse(json) as WebhookChannelConfig
  } catch {
    return { webhookUrl: '', secret: '' }
  }
}

function buildConfigJson(channel: string) {
  if (channel === 'site') return undefined
  const f = forms.value[channel]
  return JSON.stringify({ webhookUrl: f.webhookUrl.trim(), secret: f.secret.trim() || undefined })
}

async function load() {
  loading.value = true
  try {
    channels.value = await messageNotifyApi.channels()
    for (const ch of channels.value) {
      const cfg = parseConfig(ch.configJson)
      forms.value[ch.channel] = {
        enabled: ch.enabled ?? 0,
        webhookUrl: cfg.webhookUrl ?? '',
        secret: cfg.secret ?? '',
        remark: ch.remark ?? '',
      }
    }
  } finally {
    loading.value = false
  }
}

async function saveChannel(channel: string) {
  saving.value = channel
  try {
    const f = forms.value[channel]
    await messageNotifyApi.save({
      channel,
      enabled: f.enabled,
      configJson: buildConfigJson(channel),
      remark: f.remark.trim() || undefined,
    })
    message.success('已保存')
    await load()
  } catch (e) {
    message.error(e instanceof Error ? e.message : '保存失败')
  } finally {
    saving.value = null
  }
}

async function testChannel(channel: string) {
  if (channel === 'site') {
    message.info('站内消息无需 Webhook 测试')
    return
  }
  testing.value = channel
  try {
    const f = forms.value[channel]
    const result = await messageNotifyApi.test({
      channel,
      enabled: f.enabled,
      configJson: buildConfigJson(channel),
    })
    if (result.success) message.success(result.message)
    else message.warning(result.message)
  } catch (e) {
    message.error(e instanceof Error ? e.message : '测试失败')
  } finally {
    testing.value = null
  }
}

const channelMeta: Record<string, { title: string; desc: string }> = {
  site: {
    title: '站内消息',
    desc: '平台内收件箱与顶部铃铛通知；关闭后仅通过外部渠道推送，不再写入个人收件箱。',
  },
  dingtalk: {
    title: '钉钉',
    desc: '通过群机器人 Webhook 推送消息到钉钉群；支持加签 Secret。',
  },
  feishu: {
    title: '飞书',
    desc: '通过群机器人 Webhook 推送消息到飞书群；支持签名校验 Secret。',
  },
}

onMounted(load)
</script>

<template>
  <n-space vertical size="large">
    <n-page-header
      title="消息通知"
      subtitle="配置站内消息、钉钉、飞书等通知渠道；关键业务操作触发消息时会按已开启的渠道推送"
    />

    <n-alert type="info" :bordered="false">
      钉钉/飞书使用群机器人 Webhook，请在对应群聊中添加机器人后填入 Webhook 地址。开启渠道后，系统消息会同步推送到外部群。
    </n-alert>

    <n-spin :show="loading">
      <n-space vertical size="large">
        <n-card v-for="ch in channels" :key="ch.channel" :title="channelMeta[ch.channel]?.title ?? ch.channelLabel">
          <template #header-extra>
            <n-switch
              v-model:value="forms[ch.channel].enabled"
              :checked-value="1"
              :unchecked-value="0"
            />
          </template>
          <n-text depth="3" style="display: block; margin-bottom: 12px">
            {{ channelMeta[ch.channel]?.desc }}
          </n-text>
          <n-form v-if="ch.channel !== 'site'" label-placement="top">
            <n-form-item label="Webhook 地址" required>
              <n-input v-model:value="forms[ch.channel].webhookUrl" placeholder="https://..." />
            </n-form-item>
            <n-form-item label="签名 Secret（可选）">
              <n-input
                v-model:value="forms[ch.channel].secret"
                type="password"
                show-password-on="click"
                placeholder="SEC... 或飞书签名密钥；留空表示不签名"
              />
            </n-form-item>
          </n-form>
          <n-form-item label="备注">
            <n-input v-model:value="forms[ch.channel].remark" placeholder="可选说明" />
          </n-form-item>
          <n-space>
            <n-button :loading="saving === ch.channel" type="primary" @click="saveChannel(ch.channel)">
              保存
            </n-button>
            <n-button
              v-if="ch.channel !== 'site'"
              :loading="testing === ch.channel"
              @click="testChannel(ch.channel)"
            >
              发送测试
            </n-button>
          </n-space>
        </n-card>
      </n-space>
    </n-spin>
  </n-space>
</template>
