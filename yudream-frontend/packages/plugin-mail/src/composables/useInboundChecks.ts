import type { InboundCheckRecord, InboundCheckStatus, MailAddressOption } from '../types'
import type { YuDreamPluginSdk } from '@yudream/plugin-sdk'
import { useFaToast } from '@yudream/components'
import { computed, reactive, ref } from 'vue'
import { createMailApi } from '../api/mail-api'
import { addressLabel, formatTime } from '../utils'

/**
 * 入站核验：选地址 + 验证码 + 时间窗口 → 插件直连该地址的 IMAP 收件箱拉取回信并按
 * 发件域/关键词/验证码匹配 → 展示结论与命中邮件摘要，同时落审计记录。
 */
export function useInboundChecks(sdk: YuDreamPluginSdk) {
  const api = createMailApi(sdk)
  const toast = useFaToast()
  const senders = ref<MailAddressOption[]>([])
  const checking = ref(false)
  const lastResult = ref<InboundCheckRecord | null>(null)
  const records = ref<InboundCheckRecord[]>([])
  const loading = ref(false)
  const pagination = reactive({ page: 1, size: 10, total: 0 })
  const filters = reactive<{ addressId: string, status: '' | InboundCheckStatus }>({ addressId: '', status: '' })
  const form = reactive({ addressId: '', verificationCode: '', windowMinutes: '30' })

  const senderOptions = computed(() => senders.value.map(item => ({
    label: item.enabled
      ? `${addressLabel(item)}${item.imapConfigured ? '' : '（未配置 IMAP）'}`
      : `${addressLabel(item)}（已停用）`,
    value: item.id,
  })))

  async function loadSenders() {
    try {
      senders.value = await api.addressOptions()
      if (!form.addressId && senders.value.length) {
        const preferred = senders.value.find(item => item.imapConfigured) || senders.value[0]
        form.addressId = preferred ? preferred.id : ''
      }
    }
    catch {
      // 错误提示由宿主统一处理
    }
  }

  async function load() {
    loading.value = true
    try {
      const page = await api.inboundChecks(filters.addressId, filters.status, pagination.page, pagination.size)
      records.value = page.records
      pagination.total = page.total
    }
    catch {
      // 错误提示由宿主统一处理
    }
    finally {
      loading.value = false
    }
  }

  async function search() {
    pagination.page = 1
    await load()
  }

  async function resetFilters() {
    Object.assign(filters, { addressId: '', status: '' })
    pagination.page = 1
    await load()
  }

  async function onPageChange(page: number) {
    pagination.page = page
    await load()
  }

  async function onSizeChange(size: number) {
    pagination.size = size
    pagination.page = 1
    await load()
  }

  /** 发起核验：地址必选，验证码可留空（只按关键词匹配）。 */
  async function check(): Promise<boolean> {
    if (!form.addressId) {
      toast.warning('请选择邮箱地址')
      return false
    }
    checking.value = true
    try {
      const record = await api.checkInbound(form.addressId, form.verificationCode.trim(),
        Number(form.windowMinutes) || 30)
      lastResult.value = record
      if (record.status === 'MATCHED') {
        toast.success('核验通过：已匹配到回信')
      }
      else if (record.status === 'UNAVAILABLE') {
        toast.warning(record.message || '收件箱不可用')
      }
      else {
        toast.info(`${record.statusLabel}：${record.message || '暂无匹配回信'}`)
      }
      pagination.page = 1
      await load()
      return true
    }
    catch {
      return false
    }
    finally {
      checking.value = false
    }
  }

  return reactive({
    senders,
    checking,
    lastResult,
    records,
    loading,
    pagination,
    filters,
    form,
    senderOptions,
    loadSenders,
    load,
    search,
    resetFilters,
    onPageChange,
    onSizeChange,
    check,
    formatTime,
  })
}

export type InboundChecksModel = ReturnType<typeof useInboundChecks>
