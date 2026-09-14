import type { YggcEndpoint, YggcStatus } from '../types'
import type { YuDreamPluginSdk } from '@yudream/plugin-sdk'
import { useFaToast } from '@yudream/components'
import { computed, reactive, ref } from 'vue'
import { createYggcApi } from '../api/yggc-api'

export function useYggcAdmin(sdk: YuDreamPluginSdk) {
  const api = createYggcApi(sdk)
  const toast = useFaToast()
  const loading = ref(false)
  const status = ref<YggcStatus | null>(null)
  const apiRootUrl = computed(() => {
    const url = api.apiUrl('/api/yggdrasil').replace(/\/$/, '')
    return /^https?:\/\//i.test(url) ? url : `${window.location.origin}${url}`
  })
  const discoveryUrl = computed(() => `${apiRootUrl.value}/.well-known/openid-configuration`)
  const statusText = computed(() => (status.value ? '已连接' : '待刷新'))
  const endpoints = computed<YggcEndpoint[]>(() => [
    { method: 'GET', path: '/', note: 'ALI 元数据（含 openid_configuration_url）' },
    { method: 'POST', path: '/authserver/authenticate', note: '传统登录，获取 accessToken' },
    { method: 'POST', path: '/authserver/refresh', note: '刷新访问令牌' },
    { method: 'POST', path: '/authserver/validate', note: '校验访问令牌' },
    { method: 'POST', path: '/authserver/invalidate', note: '吊销访问令牌' },
    { method: 'POST', path: '/authserver/signout', note: '登出全部会话' },
    { method: 'POST', path: '/sessionserver/session/minecraft/join', note: '加入服务器（会话 / OAuth 令牌）' },
    { method: 'GET', path: '/sessionserver/session/minecraft/hasJoined', note: '服务端验证玩家' },
    { method: 'GET', path: '/sessionserver/session/minecraft/profile/{uuid}', note: '查询角色材质属性' },
    { method: 'POST', path: '/api/profiles/minecraft', note: '批量查询角色' },
    { method: 'PUT', path: '/api/user/profile/{uuid}/{textureType}', note: '绑定角色材质' },
    { method: 'DELETE', path: '/api/user/profile/{uuid}/{textureType}', note: '清除角色材质' },
    { method: 'GET', path: '/.well-known/openid-configuration', note: 'Yggdrasil Connect 发现文档' },
    { method: 'GET', path: '/.well-known/jwks.json', note: 'ID Token 签名公钥' },
    { method: 'GET', path: '/oauth/authorize', note: 'OAuth 授权端点（授权码 + PKCE）' },
    { method: 'POST', path: '/oauth/token', note: '令牌端点（授权码 / 刷新 / 设备码）' },
    { method: 'POST', path: '/oauth/device', note: '设备授权（RFC 8628）' },
    { method: 'GET', path: '/userinfo', note: 'OAuth UserInfo' },
  ])

  async function load() {
    loading.value = true
    try {
      status.value = await api.status()
    }
    finally {
      loading.value = false
    }
  }

  async function copy(value: string) {
    await navigator.clipboard.writeText(value)
    toast.success('已复制')
  }

  return reactive({ loading, status, apiRootUrl, discoveryUrl, statusText, endpoints, load, copy })
}

export type YggcAdminModel = ReturnType<typeof useYggcAdmin>
