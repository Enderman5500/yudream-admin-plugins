import type { YggcGrantGroup } from '../types'
import type { YuDreamPluginSdk } from '@yudream/plugin-sdk'
import { useFaModal, useFaToast } from '@yudream/components'
import { reactive, ref } from 'vue'
import { createYggcApi } from '../api/yggc-api'

export function useYggcMyGrants(sdk: YuDreamPluginSdk) {
  const api = createYggcApi(sdk)
  const toast = useFaToast()
  const confirm = useFaModal()
  const loading = ref(false)
  const groups = ref<YggcGrantGroup[]>([])

  async function load() {
    loading.value = true
    try {
      groups.value = await api.myGrants()
    }
    finally {
      loading.value = false
    }
  }

  function revokeToken(_group: YggcGrantGroup, token: string) {
    confirm.confirm({
      title: '取消授权',
      content: '确认吊销该应用的这个访问令牌吗？使用它的启动器将需要重新授权。',
      onConfirm: async () => {
        await api.revokeMyToken(token)
        toast.success('令牌已吊销')
        await load()
      },
    })
  }

  function revokeClient(group: YggcGrantGroup) {
    confirm.confirm({
      title: '取消应用授权',
      content: `确认移除“${group.clientName}”的全部授权吗？该应用的所有令牌将被吊销。`,
      onConfirm: async () => {
        await api.revokeMyClient(group.clientId)
        toast.success('授权已移除')
        await load()
      },
    })
  }

  return reactive({ loading, groups, load, revokeToken, revokeClient })
}

export type YggcMyGrantsModel = ReturnType<typeof useYggcMyGrants>
