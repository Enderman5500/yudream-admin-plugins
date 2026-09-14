import type { YuDreamPluginSdk } from '@yudream/plugin-sdk'
import type {
  InboundCheckRecord,
  InboxDetail,
  InboxResult,
  MailAddress,
  MailAddressDetail,
  MailAddressFilters,
  MailAddressOption,
  MailRecord,
  MailRecordFilters,
  PageResult,
  TransportTestResult,
} from '../types'

function query(params: Record<string, string | number | boolean | undefined | null>): string {
  const search = new URLSearchParams()
  Object.entries(params).forEach(([key, value]) => {
    if (value !== undefined && value !== null && value !== '') {
      search.set(key, String(value))
    }
  })
  const value = search.toString()
  return value ? `?${value}` : ''
}

/** 邮箱中心 API：管理端 /admin/**，用户端 /me/**，收件箱中立路径 /inbox/**，路径与后端注解一一对应。 */
export function createMailApi(sdk: YuDreamPluginSdk) {
  return {
    // ---- 管理端：邮箱地址与收发配置 ----
    addresses: (filters: MailAddressFilters, page: number, size: number) =>
      sdk.http.get<PageResult<MailAddress>>(`/admin/addresses${query({
        keyword: filters.keyword,
        enabled: filters.enabled,
        page,
        size,
      })}`),
    address: (id: string) => sdk.http.get<MailAddressDetail>(`/admin/addresses/${encodeURIComponent(id)}`),
    addressOptions: () => sdk.http.get<MailAddressOption[]>('/admin/address-options'),
    createAddress: (data: Record<string, unknown>) => sdk.http.post<MailAddress>('/admin/addresses', data),
    updateAddress: (id: string, data: Record<string, unknown>) =>
      sdk.http.request<MailAddress>(`/admin/addresses/${encodeURIComponent(id)}`, { method: 'PUT', data }),
    setAddressEnabled: (id: string, enabled: boolean) =>
      sdk.http.post<MailAddress>(`/admin/addresses/${encodeURIComponent(id)}/enabled${query({ enabled })}`),
    setDefaultAddress: (id: string) =>
      sdk.http.post<MailAddress>(`/admin/addresses/${encodeURIComponent(id)}/default`),
    deleteAddress: (id: string) =>
      sdk.http.request(`/admin/addresses/${encodeURIComponent(id)}`, { method: 'DELETE' }),
    imapFolders: (id: string) =>
      sdk.http.get<{ folders: string[] }>(`/admin/addresses/${encodeURIComponent(id)}/imap-folders`),
    testSmtp: (id: string) =>
      sdk.http.post<TransportTestResult>(`/admin/addresses/${encodeURIComponent(id)}/test/smtp`),
    testImap: (id: string) =>
      sdk.http.post<TransportTestResult>(`/admin/addresses/${encodeURIComponent(id)}/test/imap`),

    // ---- 管理端：发信与发信记录 ----
    sendMail: (data: Record<string, unknown>) => sdk.http.post<MailRecord>('/admin/messages', data),
    messages: (filters: MailRecordFilters, page: number, size: number) =>
      sdk.http.get<PageResult<MailRecord>>(`/admin/messages${query({
        keyword: filters.keyword,
        addressId: filters.addressId,
        status: filters.status,
        page,
        size,
      })}`),
    message: (id: string) => sdk.http.get<MailRecord>(`/admin/messages/${encodeURIComponent(id)}`),

    // ---- 收件箱（/inbox/**，plugin:mail:use；访问即同步 + 持久化分页） ----
    inboxAddressOptions: () => sdk.http.get<MailAddressOption[]>('/inbox/address-options'),
    inbox: (params: Record<string, string | number | undefined>) =>
      sdk.http.get<InboxResult>(`/inbox${query(params)}`),
    inboxMessage: (uid: string, addressId: string, folder: string) =>
      sdk.http.get<InboxDetail>(`/inbox/messages/${encodeURIComponent(uid)}${query({ addressId, folder })}`),
    markInboxSeen: (uid: string, addressId: string, folder: string, seen: boolean) =>
      sdk.http.post<Record<string, boolean>>(`/inbox/seen${query({ uid, addressId, folder, seen })}`),
    deleteInboxMessage: (uid: string, addressId: string, folder: string) =>
      sdk.http.request<Record<string, boolean>>(
        `/inbox/messages/${encodeURIComponent(uid)}${query({ addressId, folder })}`,
        { method: 'DELETE' }),

    // ---- 管理端：入站核验 ----
    checkInbound: (addressId: string, verificationCode: string, windowMinutes: number) =>
      sdk.http.post<InboundCheckRecord>('/admin/inbound-checks', { addressId, verificationCode, windowMinutes }),
    inboundChecks: (addressId: string, status: string, page: number, size: number) =>
      sdk.http.get<PageResult<InboundCheckRecord>>(`/admin/inbound-checks${query({ addressId, status, page, size })}`),

    // ---- 用户端：我的发信 ----
    mySenders: () => sdk.http.get<MailAddressOption[]>('/me/senders'),
    sendMyMail: (data: Record<string, unknown>) => sdk.http.post<MailRecord>('/me/messages', data),
    myMessages: (page: number, size: number) =>
      sdk.http.get<PageResult<MailRecord>>(`/me/messages${query({ page, size })}`),
    myMessage: (id: string) => sdk.http.get<MailRecord>(`/me/messages/${encodeURIComponent(id)}`),
  }
}

export type MailApi = ReturnType<typeof createMailApi>
