<script setup lang="ts">
import type { RouteLocationNormalizedLoaded } from 'vue-router'
import type { YuDreamPluginSdk } from '@yudream/plugin-sdk'
import { FaAlert, FaButton, FaCard, FaIcon } from '@yudream/components'
import { computed, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { useYggcDeviceFlow } from '../composables/useYggcAuthorizeFlow'

const props = defineProps<{
  sdk: YuDreamPluginSdk
  route?: RouteLocationNormalizedLoaded
}>()

const route = useRoute()
const initialCode = typeof route.query.user_code === 'string' ? route.query.user_code : ''

const model = useYggcDeviceFlow(props.sdk, initialCode)

const approved = computed(() => model.decision === 'approved')

onMounted(() => model.load())
</script>

<template>
  <div class="yggc-plugin yggc-consent-page">
    <section class="yggc-toolbar">
      <div>
        <span>登录服务</span>
        <h2>设备授权</h2>
      </div>
    </section>

    <FaCard v-loading="model.loading" content-class="yggc-card-content">
      <p class="yggc-muted">
        输入应用显示的设备码（如 <code>ABCD-1234</code>）确认登录。
      </p>

      <div class="yggc-device-input">
        <input
          v-model="model.userCode"
          class="yggc-input"
          placeholder="XXXX-XXXX"
          @keydown.enter="model.load"
        >
        <FaButton variant="outline" :loading="model.loading" @click="model.load">
          <FaIcon name="i-ri:search-line" />
          <span>查询</span>
        </FaButton>
      </div>

      <FaAlert
        v-if="model.error"
        variant="destructive"
        icon="i-ri:error-warning-line"
        :description="model.error"
      />

      <div
        v-if="model.decision"
        class="yggc-consent-result"
        :class="approved ? 'yggc-consent-result--approved' : 'yggc-consent-result--denied'"
      >
        <FaIcon
          class="yggc-consent-result__icon"
          :name="approved ? 'i-ri:checkbox-circle-line' : 'i-ri:close-circle-line'"
        />
        <div class="yggc-consent-result__body">
          <h3>{{ approved ? '授权成功' : '已拒绝授权' }}</h3>
          <p class="yggc-muted">
            {{ approved ? '请回到发起登录的应用，它会自动完成后续流程。' : '发起登录的应用将收到拒绝结果。' }}
          </p>
        </div>
      </div>

      <template v-else-if="model.context">
        <div class="yggc-consent-section">
          <h3 class="yggc-consent-section__title"><strong>{{ model.context.client.name }}</strong> 请求以下权限</h3>
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
              <input v-model="model.profileId" type="radio" name="yggc-device-profile" :value="profile.id">
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
            <span>确认授权</span>
          </FaButton>
        </div>
      </template>
    </FaCard>
  </div>
</template>
