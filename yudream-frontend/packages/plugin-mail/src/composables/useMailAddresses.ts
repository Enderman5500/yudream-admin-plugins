import type { YuDreamPluginSdk } from '@yudream/plugin-sdk'
import type { MailAddress, MailAddressDetail, MailAddressFilters, MailAddressForm } from '../types'
import { useFaToast } from '@yudream/components'
import { reactive, ref } from 'vue'
import { createMailApi } from '../api/mail-api'
import { formatTime, joinList, splitList } from '../utils'

const EMPTY_FORM: MailAddressForm = {
  address: '',
  displayName: '',
  purpose: 'NOTIFICATION',
  remark: '',
  enabled: true,
  smtpHost: '',
  smtpPort: '',
  smtpSecurity: 'SSL',
  smtpUsername: '',
  smtpPassword: '',
  imapHost: '',
  imapPort: '',
  imapSecurity: 'SSL',
  imapUsername: '',
  imapPassword: '',
  imapFolder: 'INBOX',
  imapFetchLimit: '20',
  imapDomains: '',
  imapKeywords: '',
}

/**
 * 管理端邮箱地址簿：列表、新增/编辑（含 SMTP/IMAP 配置）、启停、设为默认、删除、连通性测试与收件夹列表。
 *
 * <p>密码字段永远只上行、不下行：编辑时后端只回“是否已设置”，留空表示保持原密码。</p>
 */
export function useMailAddresses(sdk: YuDreamPluginSdk) {
  const api = createMailApi(sdk)
  const toast = useFaToast()
  const loading = ref(false)
  const saving = ref(false)
  const detailLoading = ref(false)
  const deletingId = ref('')
  const switchingId = ref('')
  const testing = ref('')
  const loadingFolders = ref(false)
  const records = ref<MailAddress[]>([])
  const pagination = reactive({ page: 1, size: 10, total: 0 })
  const filters = reactive<MailAddressFilters>({ keyword: '', enabled: '' })
  const form = reactive<MailAddressForm>({ ...EMPTY_FORM })
  const editingId = ref('')
  const smtpPasswordSet = ref(false)
  const imapPasswordSet = ref(false)
  const folderOptions = ref<string[]>([])
  const smtpTest = ref('')
  const imapTest = ref('')
  const smtpTestOk = ref(false)
  const imapTestOk = ref(false)

  async function load() {
    loading.value = true
    try {
      const result = await api.addresses(filters, pagination.page, pagination.size)
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

  async function search() {
    pagination.page = 1
    await load()
  }

  async function resetFilters() {
    Object.assign(filters, { keyword: '', enabled: '' })
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

  function resetTransientState() {
    smtpPasswordSet.value = false
    imapPasswordSet.value = false
    folderOptions.value = []
    smtpTest.value = ''
    imapTest.value = ''
    smtpTestOk.value = false
    imapTestOk.value = false
  }

  function resetForm() {
    editingId.value = ''
    Object.assign(form, { ...EMPTY_FORM })
    resetTransientState()
  }

  function openCreate() {
    resetForm()
  }

  /** 打开编辑：先取详情（含密码是否已设置），再回填表单。 */
  async function openEdit(row: MailAddress): Promise<void> {
    resetForm()
    editingId.value = row.id
    detailLoading.value = true
    let detail: MailAddressDetail | null = null
    try {
      detail = await api.address(row.id)
    }
    catch {
      // 详情取不到时退化为列表数据，密码状态按“未设置”处理
    }
    finally {
      detailLoading.value = false
    }
    const source = detail || row
    Object.assign(form, {
      address: source.address,
      displayName: source.displayName || '',
      purpose: source.purpose,
      remark: source.remark || '',
      enabled: source.enabled,
      smtpHost: source.smtp?.host || '',
      smtpPort: source.smtp?.port ? String(source.smtp.port) : '',
      smtpSecurity: source.smtp?.security || 'SSL',
      smtpUsername: source.smtp?.username || '',
      smtpPassword: '',
      imapHost: source.imap?.host || '',
      imapPort: source.imap?.port ? String(source.imap.port) : '',
      imapSecurity: source.imap?.security || 'SSL',
      imapUsername: source.imap?.username || '',
      imapPassword: '',
      imapFolder: source.imap?.folder || 'INBOX',
      imapFetchLimit: source.imap?.fetchLimit ? String(source.imap.fetchLimit) : '20',
      imapDomains: joinList(source.imap?.allowedFromDomains),
      imapKeywords: joinList(source.imap?.requiredKeywords),
    })
    smtpPasswordSet.value = detail ? detail.smtpPasswordSet : false
    imapPasswordSet.value = detail ? detail.imapPasswordSet : false
  }

  function payload() {
    return {
      address: form.address.trim(),
      displayName: form.displayName.trim(),
      purpose: form.purpose,
      remark: form.remark.trim(),
      enabled: form.enabled,
      smtp: {
        host: form.smtpHost.trim(),
        port: form.smtpPort ? Number(form.smtpPort) : null,
        security: form.smtpSecurity,
        username: form.smtpUsername.trim(),
        password: form.smtpPassword,
      },
      imap: {
        host: form.imapHost.trim(),
        port: form.imapPort ? Number(form.imapPort) : null,
        security: form.imapSecurity,
        username: form.imapUsername.trim(),
        password: form.imapPassword,
        folder: form.imapFolder.trim() || 'INBOX',
        fetchLimit: form.imapFetchLimit ? Number(form.imapFetchLimit) : null,
        allowedFromDomains: splitList(form.imapDomains),
        requiredKeywords: splitList(form.imapKeywords),
      },
    }
  }

  /** 保存成功返回 true，页面据此关闭抽屉；失败返回 false 并保持表单可修正。 */
  async function save(): Promise<boolean> {
    if (!form.address.trim()) {
      toast.warning('请填写邮箱地址')
      return false
    }
    saving.value = true
    try {
      if (editingId.value) {
        await api.updateAddress(editingId.value, payload())
        toast.success('邮箱地址已更新')
      }
      else {
        await api.createAddress(payload())
        toast.success('邮箱地址已新增')
      }
      resetForm()
      await load()
      return true
    }
    catch {
      return false
    }
    finally {
      saving.value = false
    }
  }

  async function toggleEnabled(row: MailAddress, enabled: boolean) {
    switchingId.value = row.id
    try {
      await api.setAddressEnabled(row.id, enabled)
      toast.success(enabled ? '邮箱地址已启用' : '邮箱地址已停用')
      await load()
    }
    catch {
      // 错误提示由宿主统一处理
    }
    finally {
      switchingId.value = ''
    }
  }

  async function setDefault(row: MailAddress) {
    switchingId.value = row.id
    try {
      await api.setDefaultAddress(row.id)
      toast.success('已设为默认发件地址')
      await load()
    }
    catch {
      // 错误提示由宿主统一处理
    }
    finally {
      switchingId.value = ''
    }
  }

  async function remove(row: MailAddress) {
    deletingId.value = row.id
    try {
      await api.deleteAddress(row.id)
      toast.success('邮箱地址已删除')
      const totalAfterDelete = Math.max(0, pagination.total - 1)
      const lastPage = Math.max(1, Math.ceil(totalAfterDelete / pagination.size))
      pagination.page = Math.min(pagination.page, lastPage)
      await load()
    }
    catch {
      // 错误提示由宿主统一处理
    }
    finally {
      deletingId.value = ''
    }
  }

  async function testSmtp() {
    if (!editingId.value) {
      toast.warning('请先保存地址，再测试 SMTP 连接')
      return
    }
    testing.value = 'smtp'
    smtpTest.value = ''
    try {
      const result = await api.testSmtp(editingId.value)
      smtpTestOk.value = result.ok
      smtpTest.value = result.message
    }
    catch {
      smtpTestOk.value = false
    }
    finally {
      testing.value = ''
    }
  }

  async function testImap() {
    if (!editingId.value) {
      toast.warning('请先保存地址，再测试 IMAP 连接')
      return
    }
    testing.value = 'imap'
    imapTest.value = ''
    try {
      const result = await api.testImap(editingId.value)
      imapTestOk.value = result.ok
      imapTest.value = result.message
    }
    catch {
      imapTestOk.value = false
    }
    finally {
      testing.value = ''
    }
  }

  /** 从服务端读取真实收件夹列表，供下拉选择（失败时保留手输）。 */
  async function loadFolders() {
    if (!editingId.value) {
      toast.warning('请先保存地址，再读取收件夹列表')
      return
    }
    loadingFolders.value = true
    try {
      const result = await api.imapFolders(editingId.value)
      folderOptions.value = result.folders || []
      if (!folderOptions.value.length) {
        toast.warning('未读取到收件夹，请确认 IMAP 配置正确')
      }
    }
    catch {
      // 错误提示由宿主统一处理
    }
    finally {
      loadingFolders.value = false
    }
  }

  return reactive({
    loading,
    saving,
    detailLoading,
    deletingId,
    switchingId,
    testing,
    loadingFolders,
    records,
    pagination,
    filters,
    form,
    editingId,
    smtpPasswordSet,
    imapPasswordSet,
    folderOptions,
    smtpTest,
    imapTest,
    smtpTestOk,
    imapTestOk,
    load,
    search,
    resetFilters,
    onPageChange,
    onSizeChange,
    resetForm,
    openCreate,
    openEdit,
    save,
    toggleEnabled,
    setDefault,
    remove,
    testSmtp,
    testImap,
    loadFolders,
    formatTime,
  })
}

export type MailAddressesModel = ReturnType<typeof useMailAddresses>
