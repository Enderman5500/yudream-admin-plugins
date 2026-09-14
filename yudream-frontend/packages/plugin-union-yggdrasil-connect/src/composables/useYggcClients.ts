import type { YggcClientView, YggcTokenView } from '../types'
import type { YuDreamPluginSdk } from '@yudream/plugin-sdk'
import { useFaModal, useFaToast } from '@yudream/components'
import { reactive, ref } from 'vue'
import { createYggcApi } from '../api/yggc-api'

export function useYggcClients(sdk: YuDreamPluginSdk) {
  const api = createYggcApi(sdk)
  const toast = useFaToast()
  const confirm = useFaModal()
  const loading = ref(false)
  const saving = ref(false)
  const records = ref<YggcClientView[]>([])
  const total = ref(0)
  const keyword = ref('')
  const modalVisible = ref(false)
  const editing = ref<YggcClientView | null>(null)
  const form = reactive({
    name: '',
    redirectUrisText: '',
    publicClient: false,
    enabled: true,
  })

  async function load(page = 1, size = 10) {
    loading.value = true
    try {
      const body = await api.clients(keyword.value, page, size)
      records.value = body.records
      total.value = body.total
    }
    finally {
      loading.value = false
    }
  }

  function openCreate() {
    editing.value = null
    form.name = ''
    form.redirectUrisText = ''
    form.publicClient = false
    form.enabled = true
    modalVisible.value = true
  }

  function openEdit(client: YggcClientView) {
    editing.value = client
    form.name = client.name
    form.redirectUrisText = client.redirectUris.join('\n')
    form.publicClient = client.publicClient
    form.enabled = client.enabled
    modalVisible.value = true
  }

  async function save() {
    saving.value = true
    try {
      const redirectUris = form.redirectUrisText.split('\n').map(item => item.trim()).filter(Boolean)
      const data = { name: form.name, redirectUris, publicClient: form.publicClient, enabled: form.enabled }
      if (editing.value) {
        await api.updateClient(editing.value.id, data)
        toast.success('应用已更新')
      }
      else {
        const created = await api.createClient(data)
        toast.success('应用已创建')
        if (created.secret) {
          await navigator.clipboard.writeText(created.secret).catch(() => undefined)
          toast.success('client_secret 已生成并复制，仅显示一次，请妥善保存')
        }
      }
      modalVisible.value = false
      await load()
    }
    finally {
      saving.value = false
    }
  }

  async function resetSecret(client: YggcClientView) {
    const result = await api.resetClientSecret(client.id)
    if (result.secret) {
      await navigator.clipboard.writeText(result.secret).catch(() => undefined)
      toast.success('新 client_secret 已复制到剪贴板，仅显示一次')
    }
    else {
      toast.success('client_secret 已重置')
    }
  }

  function remove(client: YggcClientView) {
    confirm.confirm({
      title: '删除应用',
      content: `确认删除“${client.name}”吗？其名下全部访问与刷新令牌将被吊销。`,
      onConfirm: async () => {
        await api.deleteClient(client.id)
        toast.success('应用已删除')
        await load()
      },
    })
  }

  return reactive({
    loading, saving, records, total, keyword, modalVisible, editing, form,
    load, openCreate, openEdit, save, resetSecret, remove,
  })
}

export function useYggcTokens(sdk: YuDreamPluginSdk) {
  const api = createYggcApi(sdk)
  const toast = useFaToast()
  const confirm = useFaModal()
  const loading = ref(false)
  const records = ref<YggcTokenView[]>([])
  const total = ref(0)

  async function load(page = 1, size = 10) {
    loading.value = true
    try {
      const body = await api.tokens(page, size)
      records.value = body.records
      total.value = body.total
    }
    finally {
      loading.value = false
    }
  }

  function revoke(token: YggcTokenView) {
    confirm.confirm({
      title: '吊销令牌',
      content: '确认吊销该访问令牌吗？对应的刷新令牌将一并吊销。',
      onConfirm: async () => {
        await api.revokeToken(token.token)
        toast.success('令牌已吊销')
        await load()
      },
    })
  }

  return reactive({ loading, records, total, load, revoke })
}

export type YggcClientsModel = ReturnType<typeof useYggcClients>
export type YggcTokensModel = ReturnType<typeof useYggcTokens>
