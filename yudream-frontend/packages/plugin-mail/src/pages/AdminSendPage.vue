<script setup lang="ts">
import type { YuDreamPluginSdk } from '@yudream/plugin-sdk'
import type { MailBodyType } from '../types'
import { FaButton, FaIcon, FaLabel, FaPageHeader, FaPageMain, FaSelect, FaTag, FaTextarea, FaInput } from '@yudream/components'
import { onMounted } from 'vue'
import MailConfigHint from '../components/MailConfigHint.vue'
import { useMailSend } from '../composables/useMailSend'
import { BODY_TYPE_OPTIONS } from '../utils'

const props = defineProps<{ sdk: YuDreamPluginSdk }>()
// 管理员：可选全部地址、走 /admin/messages 发送；普通用户：只选已启用地址、走 /me/messages（principal 归属）
const canManage = props.sdk.account.permissions.includes('plugin:mail:manage')
const model = useMailSend(props.sdk, canManage ? 'admin' : 'user')

onMounted(model.loadSenders)
</script>

<template>
  <section class="mail-page">
    <FaPageHeader
      title="发信"
      description="选择邮箱地址，通过插件自带的 SMTP 客户端同步投递；发送结果立即反馈并写入发信记录"
      class="mb-0"
    />

    <FaPageMain>
      <MailConfigHint
        v-if="!model.loadingSenders && !model.senderOptions.length"
        title="暂无可用的发件地址"
        description="请先在「邮箱地址」页新增地址并配置 SMTP 服务器，只有已启用且配置了 SMTP 的地址才能发信。"
      />

      <form v-else class="mail-form" @submit.prevent="model.send">
        <FaLabel label="发件地址" class="mail-field">
          <FaSelect
            :model-value="model.form.addressId"
            :options="model.senderOptions"
            :loading="model.loadingSenders"
            class="w-full"
            placeholder="选择发件地址"
            @update:model-value="(value: unknown) => { model.form.addressId = String(value ?? '') }"
          />
          <div v-if="model.selectedSender" class="mail-muted">
            SMTP：{{ model.selectedSender.smtpConfigured ? '已配置' : '未配置' }}
            <template v-if="model.selectedSender.displayName">，显示名：{{ model.selectedSender.displayName }}</template>
          </div>
        </FaLabel>

        <FaLabel label="收件人" class="mail-field" required>
          <FaTextarea
            v-model="model.form.to"
            class="w-full"
            input-class="min-h-[64px]"
            placeholder="支持批量粘贴，用逗号、分号或换行分隔，例如：&#10;a@example.com, b@example.com"
          />
        </FaLabel>

        <div class="mail-form-grid">
          <FaLabel label="抄送" class="mail-field">
            <FaTextarea v-model="model.form.cc" class="w-full" input-class="min-h-[56px]" placeholder="可选，多人用逗号或换行分隔" />
          </FaLabel>
          <FaLabel label="密送" class="mail-field">
            <FaTextarea v-model="model.form.bcc" class="w-full" input-class="min-h-[56px]" placeholder="可选，多人用逗号或换行分隔" />
          </FaLabel>
        </div>

        <FaLabel label="主题" class="mail-field" required>
          <FaInput v-model="model.form.subject" class="w-full" maxlength="200" placeholder="邮件主题" />
        </FaLabel>

        <FaLabel label="正文格式" class="mail-field">
          <FaSelect
            :model-value="model.form.bodyType"
            :options="BODY_TYPE_OPTIONS"
            class="w-full"
            @update:model-value="(value: unknown) => { model.form.bodyType = (value ?? 'TEXT') as MailBodyType }"
          />
        </FaLabel>

        <FaLabel label="正文" class="mail-field" required>
          <FaTextarea
            v-model="model.form.body"
            class="w-full"
            :input-class="model.form.bodyType === 'HTML' ? 'min-h-[220px] font-mono' : 'min-h-[220px]'"
            :placeholder="model.form.bodyType === 'HTML' ? '<p>支持完整 HTML 邮件正文</p>' : '邮件正文'"
          />
        </FaLabel>

        <div class="mail-form-actions">
          <FaButton type="button" variant="outline" :disabled="model.sending" @click="model.resetForm">清空</FaButton>
          <FaButton type="submit" :loading="model.sending" :disabled="!model.senderOptions.length">
            <FaIcon name="i-ri:send-plane-line" />
            发送
          </FaButton>
        </div>

        <div v-if="model.lastResult" class="mail-check-result">
          <div class="flex flex-wrap items-center gap-2">
            <FaTag variant="default">最近一次发送</FaTag>
            <strong class="mail-strong">{{ model.lastResult.subject }}</strong>
          </div>
          <div class="mail-card-meta">
            <span>发件人：{{ model.lastResult.fromAddress }}</span>
            <span>收件人：{{ model.lastResult.to.join('、') }}</span>
            <span>结果：{{ model.lastResult.statusLabel }}</span>
          </div>
        </div>
      </form>
    </FaPageMain>
  </section>
</template>
