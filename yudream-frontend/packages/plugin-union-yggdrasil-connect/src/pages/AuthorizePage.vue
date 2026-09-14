<script setup lang="ts">
import type { RouteLocationNormalizedLoaded } from 'vue-router'
import type { YuDreamPluginSdk } from '@yudream/plugin-sdk'
import { FaAlert, FaButton, FaCard, FaIcon } from '@yudream/components'
import { computed, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { useYggcAuthorizeFlow } from '../composables/useYggcAuthorizeFlow'

const props = defineProps<{
  sdk: YuDreamPluginSdk
  route?: RouteLocationNormalizedLoaded
}>()

// OAuth 授权请求参数从当前页面 URL 透传（client_id / redirect_uri / scope / state / PKCE / nonce）
const route = useRoute()
const oauthParams = computed<Record<string, string>>(() => {
  const params: Record<string, string> = {}
  Object.entries(route.query).forEach(([key, value]) => {
    const first = Array.isArray(value) ? value[0] : value
    if (typeof first === 'string' && first !== '') {
      params[key] = first
    }
  })
  return params
})

const model = useYggcAuthorizeFlow(props.sdk, oauthParams.value)

onMounted(model.load)
</script>

<template>
  <div class="yggc-plugin yggc-consent-page">
    <section class="yggc-toolbar">
      <div>
        <span>登录服务</span>
        <h2>授权请求</h2>
      </div>
    </section>

    <FaCard v-loading="model.loading" content-class="yggc-card-content">
      <template v-if="model.error">
        <FaAlert
          variant="destructive"
          icon="i-ri:error-warning-line"
          title="无法完成授权"
          :description="model.error"
        />
        <p class="yggc-muted">请返回发起授权的应用检查参数，或联系站点管理员。</p>
      </template>

      <template v-else-if="model.context">
        <div class="yggc-consent-head">
          <p>
            <strong>{{ model.context.client.name }}</strong> 请求访问你的账号
            <strong>{{ model.context.user.nickname }}</strong>
          </p>
          <code class="yggc-code">{{ model.context.client.id }}</code>
        </div>

        <div class="yggc-consent-section">
          <h3 class="yggc-consent-section__title">该应用将获得以下权限</h3>
          <ul class="yggc-consent-scopes">
            <li v-for="scope in model.context.scopes" :key="scope.name">
              <code>{{ scope.name }}</code>
              <span>{{ scope.description }}</span>
            </li>
          </ul>
        </div>

        <div v-if="model.context.requireProfileSelection" class="yggc-consent-section">
          <h3 class="yggc-consent-section__title">选择用于登录的游戏角色</h3>
          <div class="yggc-consent-profiles">
            <label v-for="profile in model.context.profiles" :key="profile.id" class="yggc-consent-profile">
              <input v-model="model.profileId" type="radio" name="yggc-profile" :value="profile.id">
              <span>{{ profile.name }}</span>
              <code class="yggc-code">{{ profile.id }}</code>
            </label>
          </div>
          <p v-if="!model.context.profiles.length" class="yggc-muted">当前账号没有可用角色，无法完成需要角色的授权。</p>
        </div>

        <div class="yggc-consent-actions">
          <FaButton variant="outline" :loading="model.submitting" @click="model.decide(false)">
            <FaIcon name="i-ri:close-line" />
            <span>拒绝</span>
          </FaButton>
          <FaButton :loading="model.submitting" @click="model.decide(true)">
            <FaIcon name="i-ri:check-line" />
            <span>允许授权</span>
          </FaButton>
        </div>
      </template>

      <p v-else class="yggc-muted">正在加载授权请求…</p>
    </FaCard>
  </div>
</template>
