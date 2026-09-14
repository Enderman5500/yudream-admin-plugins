import type { YuDreamPluginSdk } from '@yudream/plugin-sdk'
import type { ComposeMode, InboxCompose, InboxDetail, InboxQuery, InboxResult, InboxSummary, MailAddressOption } from '../types'
import { useFaToast } from '@yudream/components'
import { computed, onScopeDispose, reactive, ref } from 'vue'
import { createMailApi } from '../api/mail-api'
import { addressLabel, extractAddress, formatBytes, formatTime, quoteHeader, quoteText, splitRecipients, withSubjectPrefix } from '../utils'

/** 自动刷新间隔（毫秒）。 */
const AUTO_REFRESH_MS = 30_000

/**
 * 收件箱：选地址 → 同步该地址 IMAP 收件夹信封到本地持久化副本 → 分页查询副本。
 *
 * <p>访问列表即触发同步（IMAP 不可用时自动降级读缓存，synced=false 提示）；
 * 打开详情才下载正文并缓存（bodyFetched）。回复/转发以收件地址身份走 SMTP 发信，
 * 并带上 In-Reply-To/References 线程头；已读标记与删除是对邮箱的显式写操作。
 * 支持定时自动刷新（30s 轮询「同步 + 查询」端点）。</p>
 */
export function useInbox(sdk: YuDreamPluginSdk) {
  const api = createMailApi(sdk)
  const toast = useFaToast()
  // 管理员走 /admin/messages 发送（管理端点），普通用户走 /me/messages（principal 归属）
  const canManage = sdk.account.permissions.includes('plugin:mail:manage')
  const senders = ref<MailAddressOption[]>([])
  const loading = ref(false)
  const detailLoading = ref(false)
  const flagging = ref<string>('')
  const deleting = ref(false)
  const sending = ref(false)
  const autoRefresh = ref(false)
  let refreshTimer: ReturnType<typeof setInterval> | null = null
  const records = ref<InboxSummary[]>([])
  const detail = ref<InboxDetail | null>(null)
  const result = ref<InboxResult | null>(null)
  const compose = ref<InboxCompose | null>(null)
  const query = reactive<InboxQuery>({
    addressId: '',
    folder: '',
    limit: '20',
    page: 1,
    size: 20,
    fromDomain: '',
    keyword: '',
  })

  const senderOptions = computed(() => senders.value
    .filter(item => item.enabled && item.imapConfigured)
    .map(item => ({
      label: item.defaultSender ? `${addressLabel(item)}（默认）` : addressLabel(item),
      value: item.id,
    })))

  const selectedSender = computed(() => senders.value.find(item => item.id === query.addressId) || null)

  const folderHint = computed(() => selectedSender.value?.folder || 'INBOX')

  /** 组合发信的地址必须已配置 SMTP；未配置时在详情里提示而不是让发送必然失败。 */
  const composeReady = computed(() => !!selectedSender.value?.smtpConfigured)

  async function loadSenders() {
    try {
      // 收件箱地址走 use 权限的专用端点，普通用户也能列出共享邮箱地址
      senders.value = await api.inboxAddressOptions()
      if (!senderOptions.value.some(item => item.value === query.addressId)) {
        const first = senderOptions.value[0]
        query.addressId = first ? String(first.value) : ''
      }
    }
    catch {
      // 错误提示由宿主统一处理
    }
  }

  async function load() {
    if (!query.addressId) {
      toast.warning('请先选择配置了 IMAP 的邮箱地址')
      return
    }
    loading.value = true
    detail.value = null
    try {
      result.value = await api.inbox({
        addressId: query.addressId,
        folder: query.folder.trim(),
        limit: query.limit ? Number(query.limit) : undefined,
        page: query.page,
        size: query.size,
        fromDomain: query.fromDomain.trim(),
        keyword: query.keyword.trim(),
      })
      records.value = result.value.records
    }
    catch {
      records.value = []
      result.value = null
    }
    finally {
      loading.value = false
    }
  }

  async function search() {
    query.page = 1
    await load()
  }

  function resetFilters() {
    query.fromDomain = ''
    query.keyword = ''
    query.page = 1
  }

  async function onPageChange(page: number) {
    query.page = page
    await load()
  }

  async function onSizeChange(size: number) {
    query.size = size
    query.page = 1
    await load()
  }

  function stopAutoRefresh() {
    if (refreshTimer) {
      clearInterval(refreshTimer)
      refreshTimer = null
    }
  }

  /** 定时自动拉取：轮询「同步 + 查询」端点，组件卸载时自动清理。 */
  function toggleAutoRefresh(enabled: boolean) {
    autoRefresh.value = enabled
    stopAutoRefresh()
    if (enabled) {
      refreshTimer = setInterval(() => {
        if (!loading.value) {
          void load()
        }
      }, AUTO_REFRESH_MS)
    }
  }

  onScopeDispose(stopAutoRefresh)

  async function openDetail(row: InboxSummary) {
    detailLoading.value = true
    try {
      detail.value = await api.inboxMessage(row.uid, query.addressId, query.folder.trim())
    }
    catch {
      // 错误提示由宿主统一处理
    }
    finally {
      detailLoading.value = false
    }
  }

  function closeDetail() {
    detail.value = null
  }

  /** 打开详情后刷新列表里对应行的已读状态（服务端真实值可能与本地缓存不一致）。 */
  function patchSummarySeen(uid: string, seen: boolean) {
    const row = records.value.find(item => item.uid === uid)
    if (row) {
      row.seen = seen
    }
  }

  async function toggleSeen(row: InboxSummary) {
    if (!query.addressId) {
      return
    }
    flagging.value = row.uid
    try {
      await api.markInboxSeen(row.uid, query.addressId, query.folder.trim(), !row.seen)
      row.seen = !row.seen
      patchSummarySeen(detail.value?.uid ?? '', row.seen)
      toast.success(row.seen ? '已标记为已读' : '已标记为未读')
    }
    catch {
      // 错误提示由宿主统一处理
    }
    finally {
      flagging.value = ''
    }
  }

  async function removeMail(row: InboxSummary) {
    if (!query.addressId) {
      return
    }
    deleting.value = true
    try {
      await api.deleteInboxMessage(row.uid, query.addressId, query.folder.trim())
      if (detail.value?.uid === row.uid) {
        detail.value = null
      }
      toast.success('邮件已删除（服务端永久移除）')
      // 重新拉取，让分页与缓存副本保持一致
      await load()
    }
    catch {
      // 错误提示由宿主统一处理
    }
    finally {
      deleting.value = false
    }
  }

  /** 由当前详情构造回复/回复全部/转发草稿。 */
  function openCompose(mode: ComposeMode) {
    const source = detail.value
    if (!source) {
      return
    }
    if (!composeReady.value) {
      toast.warning('当前地址未配置 SMTP，无法回复或转发；请先在「邮箱地址」页补全发信配置')
      return
    }
    const messageId = source.messageId || ''
    if (mode === 'forward') {
      compose.value = {
        mode,
        to: '',
        cc: '',
        subject: withSubjectPrefix(source.subject, 'Fwd:'),
        bodyType: 'TEXT',
        body: `${quoteHeader('转发邮件', source.from, source.to, source.cc, source.sentAt)}\n${quoteText(source.text)}`,
        inReplyTo: '',
        references: messageId,
      }
      return
    }
    // 回复：收件人是原发件人；回复全部再加上原收件人/抄送（排除自己这个收件地址）
    const self = (selectedSender.value?.address || '').toLowerCase()
    const dedupe = (items: string[]) => {
      const seen = new Set<string>()
      return items
        .map(extractAddress)
        .filter((item) => {
          const key = item.toLowerCase()
          if (!item || key === self || seen.has(key)) {
            return false
          }
          seen.add(key)
          return true
        })
    }
    const to = dedupe([source.from, ...(mode === 'reply-all' ? source.to : [])])
    const cc = mode === 'reply-all' ? dedupe(source.cc) : []
    compose.value = {
      mode,
      to: to.join(', '),
      cc: cc.join(', '),
      subject: withSubjectPrefix(source.subject, 'Re:'),
      bodyType: 'TEXT',
      body: `${quoteHeader('原始邮件', source.from, source.to, source.cc, source.sentAt)}\n${quoteText(source.text)}`,
      inReplyTo: messageId,
      references: messageId,
    }
  }

  function closeCompose() {
    compose.value = null
  }

  /** 发送组合草稿：走当前收件地址的 SMTP，成功后关闭草稿并刷新列表。 */
  async function sendCompose(): Promise<boolean> {
    const draft = compose.value
    if (!draft) {
      return false
    }
    const to = splitRecipients(draft.to)
    if (!to.length) {
      toast.warning('请填写收件人')
      return false
    }
    if (!draft.subject.trim()) {
      toast.warning('请填写邮件主题')
      return false
    }
    if (!draft.body.trim()) {
      toast.warning('请填写邮件正文')
      return false
    }
    sending.value = true
    try {
      const payload = {
        addressId: query.addressId,
        to,
        cc: splitRecipients(draft.cc),
        bcc: [],
        subject: draft.subject.trim(),
        bodyType: draft.bodyType,
        body: draft.body,
        inReplyTo: draft.inReplyTo || undefined,
        references: draft.references || undefined,
      }
      // 管理员走管理端点；普通用户走 /me/messages（后端按 principal 归属，无 manage 权限时管理端点会 403）
      if (canManage) {
        await api.sendMail(payload)
      }
      else {
        await api.sendMyMail(payload)
      }
      toast.success('邮件已发送（SMTP 服务器已接收）')
      compose.value = null
      await load()
      return true
    }
    catch {
      // 投递失败的原因由宿主错误反馈与「发信记录」共同呈现
      return false
    }
    finally {
      sending.value = false
    }
  }

  return reactive({
    senders,
    loading,
    detailLoading,
    flagging,
    deleting,
    sending,
    autoRefresh,
    records,
    detail,
    result,
    compose,
    query,
    senderOptions,
    selectedSender,
    folderHint,
    composeReady,
    loadSenders,
    load,
    search,
    resetFilters,
    onPageChange,
    onSizeChange,
    toggleAutoRefresh,
    openDetail,
    closeDetail,
    toggleSeen,
    removeMail,
    openCompose,
    closeCompose,
    sendCompose,
    formatTime,
    formatBytes,
  })
}

export type InboxModel = ReturnType<typeof useInbox>
