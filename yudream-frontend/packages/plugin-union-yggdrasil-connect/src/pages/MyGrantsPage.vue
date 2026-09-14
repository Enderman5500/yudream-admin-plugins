<script setup lang="ts">
import type { RouteLocationNormalizedLoaded } from 'vue-router'
import type { YuDreamPluginSdk } from '@yudream/plugin-sdk'
import type { YggcGrantGroup } from '../types'
import { FaButton, FaCard, FaTag } from '@yudream/components'
import { onMounted } from 'vue'
import { useYggcMyGrants } from '../composables/useYggcMyGrants'

const props = defineProps<{
  sdk: YuDreamPluginSdk
  route?: RouteLocationNormalizedLoaded
}>()

void props.route

const model = useYggcMyGrants(props.sdk)

function formatTime(value: number) {
  return value ? new Date(value).toLocaleString() : '-'
}

function askRevokeToken(group: YggcGrantGroup, token: string) {
  model.revokeToken(group, token)
}

onMounted(model.load)
</script>

<template>
  <div class="yggc-plugin">
    <section class="yggc-toolbar">
      <div>
        <span>个人授权</span>
        <h2>我的授权应用</h2>
      </div>
      <div class="yggc-actions">
        <FaButton variant="outline" :loading="model.loading" @click="model.load">刷新</FaButton>
      </div>
    </section>

    <section v-loading="model.loading" class="yggc-panel yggc-grants">
      <p v-if="!model.loading && !model.groups.length" class="yggc-muted">
        暂无应用授权记录。当你在启动器或客户端使用 Yggdrasil Connect 登录时，授权会出现在这里。
      </p>
      <FaCard v-for="group in model.groups" :key="group.clientId" class="w-full">
        <div class="yggc-grant">
          <div class="yggc-grant-head">
            <div>
              <strong class="text-base">{{ group.clientName }}</strong>
              <code class="yggc-code">{{ group.clientId }}</code>
            </div>
            <div class="yggc-actions">
              <FaTag :variant="group.clientEnabled ? 'default' : 'secondary'">{{ group.clientEnabled ? '启用中' : '已禁用' }}</FaTag>
              <FaButton size="sm" variant="destructive" @click="model.revokeClient(group)">移除授权</FaButton>
            </div>
          </div>
          <div class="yggc-grant-tokens">
            <div v-for="token in group.tokens" :key="token.token" class="yggc-grant-token">
              <div class="yggc-grant-token-main">
                <code>{{ token.token.slice(0, 16) }}…</code>
                <span class="yggc-muted">{{ token.profileName || '未绑定角色' }} · 过期 {{ formatTime(token.expiresAt) }}</span>
              </div>
              <div class="yggc-chip-list">
                <FaTag v-for="scope in token.scopes" :key="scope" variant="secondary">{{ scope }}</FaTag>
              </div>
              <FaButton size="sm" variant="outline" @click="askRevokeToken(group, token.token)">吊销此令牌</FaButton>
            </div>
          </div>
        </div>
      </FaCard>
    </section>
  </div>
</template>
