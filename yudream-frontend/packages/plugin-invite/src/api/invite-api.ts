import type { AdminBinding, BindingPage, MyBinding } from '../types'
import type { YuDreamPluginSdk } from '@yudream/plugin-sdk'

function buildQuery(params: Record<string, string | number | boolean | undefined | null>) {
  const search = new URLSearchParams()
  Object.entries(params).forEach(([key, value]) => {
    if (value !== undefined && value !== null && value !== '') {
      search.set(key, String(value))
    }
  })
  const value = search.toString()
  return value ? `?${value}` : ''
}

export function createInviteApi(sdk: YuDreamPluginSdk) {
  return {
    // ---- 管理端：邀请码绑定总览 ----
    adminBindings: (keyword: string, page: number, size: number) =>
      sdk.http.get<BindingPage<AdminBinding>>(`/admin/invite-bindings${buildQuery({ keyword, page, size })}`),
    deleteAdminBinding: (id: string) =>
      sdk.http.request(`/admin/invite-bindings/${encodeURIComponent(id)}`, { method: 'DELETE' }),
    // ---- 用户端：我的邀请码 ----
    myBindings: (page: number, size: number) =>
      sdk.http.get<BindingPage<MyBinding>>(`/me/invite-bindings${buildQuery({ page, size })}`),
    bindInvite: (code: string, remark: string) =>
      sdk.http.post<MyBinding>('/me/invite-bindings', { code, remark }),
    // 用户端不提供解绑：删除绑定记录仅限管理端 deleteAdminBinding
  }
}

export type InviteApi = ReturnType<typeof createInviteApi>
