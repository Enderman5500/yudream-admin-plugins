import type { YuDreamPluginSdk } from '@yudream/plugin-sdk'
import type { MailAddressOption, MailRecord, MailRecordFilters } from '../types'
import { reactive, ref } from 'vue'
import { createMailApi } from '../api/mail-api'
import { formatTime } from '../utils'

/**
 * 发信记录工作流（只读审计）。
 *
 * <p>管理员可切换「全部记录」（/admin/messages，跨用户筛选）与「我的记录」（/me/messages）；
 * 普通用户固定只看本人记录（后端按 principal 过滤）。切换范围时重置分页。</p>
 */
export function useMailMessages(sdk: YuDreamPluginSdk, canManage: boolean) {
  const api = createMailApi(sdk)
  const loading = ref(false)
  const detailLoading = ref(false)
  const records = ref<MailRecord[]>([])
  const detail = ref<MailRecord | null>(null)
  const scope = ref<'mine' | 'all'>(canManage ? 'all' : 'mine')
  const pagination = reactive({ page: 1, size: 10, total: 0 })
  const filters = reactive<MailRecordFilters>({ keyword: '', addressId: '', status: '' })
  const addressOptions = ref<MailAddressOption[]>([])

  async function load() {
    loading.value = true
    try {
      const result = scope.value === 'all'
        ? await api.messages(filters, pagination.page, pagination.size)
        : await api.myMessages(pagination.page, pagination.size)
      records.value = result.records
      pagination.total = result.total
    }
    catch {
      // 错误提示由宿主统一处理
    }
    finally {
      loading.value = false
    }
  }

  async function loadAddressOptions() {
    if (!canManage) {
      return
    }
    try {
      addressOptions.value = await api.addressOptions()
    }
    catch {
      // 错误提示由宿主统一处理
    }
  }

  async function setScope(next: 'mine' | 'all') {
    if (scope.value === next) {
      return
    }
    scope.value = next
    pagination.page = 1
    Object.assign(filters, { keyword: '', addressId: '', status: '' })
    await load()
  }

  async function search() {
    pagination.page = 1
    await load()
  }

  async function resetFilters() {
    Object.assign(filters, { keyword: '', addressId: '', status: '' })
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

  async function openDetail(row: MailRecord) {
    detailLoading.value = true
    try {
      detail.value = scope.value === 'all' ? await api.message(row.id) : await api.myMessage(row.id)
    }
    catch {
      detail.value = row
    }
    finally {
      detailLoading.value = false
    }
  }

  function closeDetail() {
    detail.value = null
  }

  return reactive({
    canManage,
    loading,
    detailLoading,
    records,
    detail,
    scope,
    pagination,
    filters,
    addressOptions,
    load,
    loadAddressOptions,
    setScope,
    search,
    resetFilters,
    onPageChange,
    onSizeChange,
    openDetail,
    closeDetail,
    formatTime,
  })
}

export type MailMessagesModel = ReturnType<typeof useMailMessages>
