import type { YggcAuthorizeContext, YggcDeviceContext } from '../types'
import type { AuthorizeParams } from '../api/yggc-api'
import type { YuDreamPluginSdk } from '@yudream/plugin-sdk'
import { useFaToast } from '@yudream/components'
import { computed, reactive, ref } from 'vue'
import { createYggcApi } from '../api/yggc-api'

/**
 * OAuth 授权确认页模型：解析当前路由上的 OAuth 参数，
 * 展示授权上下文，用户决定后跳转回客户端 redirect_uri。
 */
export function useYggcAuthorizeFlow(sdk: YuDreamPluginSdk, params: AuthorizeParams) {
  const api = createYggcApi(sdk)
  const toast = useFaToast()
  const loading = ref(false)
  const submitting = ref(false)
  const context = ref<YggcAuthorizeContext | null>(null)
  const error = ref('')
  const profileId = ref('')
  const selectedProfileName = computed(() => {
    const found = context.value?.profiles.find(profile => profile.id === profileId.value)
    return found ? found.name : ''
  })

  async function load() {
    loading.value = true
    error.value = ''
    try {
      context.value = await api.authorizeContext(params)
      profileId.value = context.value.profiles[0]?.id || ''
    }
    catch (e) {
      error.value = e instanceof Error ? e.message : '授权请求无效'
    }
    finally {
      loading.value = false
    }
  }

  async function decide(approve: boolean) {
    if (approve && context.value?.requireProfileSelection && !profileId.value) {
      toast.error('请选择要授权的游戏角色')
      return
    }
    submitting.value = true
    try {
      const result = await api.authorizeDecision(params, { approve, profileId: profileId.value })
      // 完成授权：整页跳转回客户端回调地址（携带 code / state 或 error）
      window.location.href = result.redirectUrl
    }
    catch (e) {
      error.value = e instanceof Error ? e.message : '授权失败'
    }
    finally {
      submitting.value = false
    }
  }

  return reactive({
    loading, submitting, context, error, profileId, selectedProfileName,
    load, decide,
  })
}

/**
 * 设备授权确认页模型：输入 / 链接携带 user_code，展示上下文并作出决定。
 */
export function useYggcDeviceFlow(sdk: YuDreamPluginSdk, initialUserCode: string) {
  const api = createYggcApi(sdk)
  const toast = useFaToast()
  const loading = ref(false)
  const submitting = ref(false)
  const userCode = ref(initialUserCode)
  const context = ref<YggcDeviceContext | null>(null)
  const error = ref('')
  const decision = ref('')
  const profileId = ref('')

  async function load() {
    if (!userCode.value.trim()) {
      return
    }
    loading.value = true
    error.value = ''
    decision.value = ''
    try {
      context.value = await api.deviceContext(userCode.value.trim())
      profileId.value = context.value.profiles[0]?.id || ''
      // 设备码已被处理过（approved / denied）时直接展示结果，不再重复征询。
      const status = String(context.value.status ?? '').trim().toLowerCase()
      if (status && status !== 'pending') {
        decision.value = status
      }
    }
    catch (e) {
      error.value = e instanceof Error ? e.message : '设备码无效'
    }
    finally {
      loading.value = false
    }
  }

  async function decide(approve: boolean) {
    if (!userCode.value.trim()) {
      toast.error('请输入设备码')
      return
    }
    if (approve && context.value?.requireProfileSelection && !profileId.value) {
      toast.error('请选择要授权的游戏角色')
      return
    }
    submitting.value = true
    error.value = ''
    try {
      const result = await api.deviceDecision({ approve, profileId: profileId.value, userCode: userCode.value.trim() })
      // 服务端返回小写状态（approved / denied），统一转小写后再比较，
      // 否则“确认授权”会被误判成拒绝。
      decision.value = String(result.status ?? '').trim().toLowerCase()
    }
    catch (e) {
      error.value = e instanceof Error ? e.message : '操作失败'
    }
    finally {
      submitting.value = false
    }
  }

  return reactive({
    loading, submitting, userCode, context, error, decision, profileId,
    load, decide,
  })
}

export type YggcAuthorizeModel = ReturnType<typeof useYggcAuthorizeFlow>
export type YggcDeviceModel = ReturnType<typeof useYggcDeviceFlow>
