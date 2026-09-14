<script setup lang="ts">
import { FaAlert } from '@yudream/components'

/**
 * 收发配置提示：插件自带 SMTP/IMAP，不再依赖宿主能力，因此这里只提示
 * “有没有可用于该操作的地址”，并指向邮箱地址页去补配置。
 */
const props = withDefaults(defineProps<{
  title: string
  description: string
  tone?: 'info' | 'warning'
}>(), {
  tone: 'warning',
})
</script>

<template>
  <div class="mail-config-hint">
    <FaAlert
      :variant="props.tone === 'warning' ? 'destructive' : 'default'"
      :icon="props.tone === 'warning' ? 'i-ri:error-warning-line' : 'i-ri:information-line'"
      :title="props.title"
      :description="props.description"
    />
  </div>
</template>

<style scoped>
.mail-config-hint {
  margin-bottom: 16px;
}
</style>
