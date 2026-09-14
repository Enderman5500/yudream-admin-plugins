import type { YuDreamPluginSdk } from '@yudream/plugin-sdk'
import type { YggcUnionBlacklistResult, YggcUnionServer, YggcUnionStatus, YggcUnionSyncResult } from '../types'
import { useFaModal, useFaToast } from '@yudream/components'
import { reactive, ref } from 'vue'
import { createYggcApi } from '../api/yggc-api'

/** 皮肤站展示名称（原插件从 {bs_root}/api/yggdrasil 的 meta.serverName 拉取）。 */
const serverNames = reactive<Record<string, string>>({})

async function resolveServerName(server: YggcUnionServer) {
  if (!server.bs_root || serverNames[server.bs_root]) {
    return
  }
  try {
    const response = await fetch(`${server.bs_root.replace(/\/$/, '')}/api/yggdrasil`)
    if (!response.ok) {
      return
    }
    const data = await response.json()
    serverNames[server.bs_root] = String(data?.meta?.serverName ?? '')
  }
  catch {
    // 站点不可达时保持为空，仅展示 bs_root
  }
}

export function useYggcUnion(sdk: YuDreamPluginSdk) {
  const api = createYggcApi(sdk)
  const toast = useFaToast()
  const confirm = useFaModal()
  const loading = ref(false)
  const syncingKey = ref(false)
  const syncingList = ref(false)
  const syncingProfiles = ref(false)
  const status = ref<YggcUnionStatus | null>(null)
  const lastSyncResult = ref<YggcUnionSyncResult | null>(null)

  async function load() {
    loading.value = true
    try {
      status.value = await api.unionStatus()
      status.value.serverList?.servers?.forEach(resolveServerName)
    }
    finally {
      loading.value = false
    }
  }

  async function syncPrivateKey() {
    syncingKey.value = true
    try {
      const result = await api.syncUnionPrivateKey()
      toast.success(result.message || '已从上游同步签名私钥')
      await load()
    }
    finally {
      syncingKey.value = false
    }
  }

  async function syncServerList() {
    syncingList.value = true
    try {
      await api.syncUnionServerList()
      toast.success('已同步皮肤站列表')
      await load()
    }
    finally {
      syncingList.value = false
    }
  }

  function syncProfiles() {
    confirm.confirm({
      title: '全量同步角色',
      content: '将把本站全部角色（uuid → 角色名）推送到 MUA 主服务器，覆盖主服务器上的本站角色索引。确认继续吗？',
      onConfirm: async () => {
        syncingProfiles.value = true
        try {
          lastSyncResult.value = await api.syncUnionProfiles()
          toast.success(lastSyncResult.value.message || '全量同步已推送')
        }
        finally {
          syncingProfiles.value = false
        }
      },
    })
  }

  return reactive({
    loading, syncingKey, syncingList, syncingProfiles, status, lastSyncResult, serverNames,
    load, syncPrivateKey, syncServerList, syncProfiles,
  })
}

export function useYggcBlacklist(sdk: YuDreamPluginSdk) {
  const api = createYggcApi(sdk)
  const toast = useFaToast()
  const confirm = useFaModal()
  const querying = ref(false)
  const creating = ref(false)
  const result = ref<YggcUnionBlacklistResult | null>(null)
  const error = ref('')
  /** 上游契约（对齐 PHP 成员插件）：GET /blacklist/query?q=关键词&page=页码，page 必传。 */
  const form = reactive({ q: '', page: '1' })
  /** 上游契约：POST /blacklist/restful 字段为 email + reason。 */
  const create = reactive({ email: '', reason: '' })

  async function query() {
    querying.value = true
    error.value = ''
    try {
      const params: Record<string, string> = { page: form.page || '1' }
      if (form.q) {
        params.q = form.q
      }
      result.value = await api.blacklistQuery(params)
    }
    catch (e: unknown) {
      error.value = e instanceof Error ? e.message : String(e)
      result.value = null
    }
    finally {
      querying.value = false
    }
  }

  async function submitCreate() {
    creating.value = true
    error.value = ''
    try {
      if (!create.email.trim()) {
        throw new Error('请填写要拉黑的邮箱地址')
      }
      const payload: Record<string, unknown> = { email: create.email.trim() }
      if (create.reason) {
        payload.reason = create.reason
      }
      result.value = await api.blacklistCreate(payload)
      toast.success('已提交黑名单记录')
      await query()
    }
    catch (e: unknown) {
      error.value = e instanceof Error ? e.message : String(e)
    }
    finally {
      creating.value = false
    }
  }

  function invalidate(id: string) {
    confirm.confirm({
      title: '使黑名单记录失效',
      content: `确认使黑名单记录 ${id} 失效吗？该记录将不再拦截角色登录。`,
      onConfirm: async () => {
        await api.blacklistInvalidate(id)
        toast.success('记录已失效')
        await query()
      },
    })
  }

  function remove(id: string) {
    confirm.confirm({
      title: '删除黑名单记录',
      content: `确认删除黑名单记录 ${id} 吗？该操作不可恢复。`,
      onConfirm: async () => {
        await api.blacklistDelete(id)
        toast.success('记录已删除')
        await query()
      },
    })
  }

  return reactive({
    querying, creating, result, error, form, create,
    query, submitCreate, invalidate, remove,
  })
}
