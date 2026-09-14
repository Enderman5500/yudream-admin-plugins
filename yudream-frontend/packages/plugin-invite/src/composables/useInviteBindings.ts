import type { AdminBinding, MyBinding } from '../types'
import type { YuDreamPluginSdk } from '@yudream/plugin-sdk'
import { useFaModal, useFaToast } from '@yudream/components'
import { reactive, ref } from 'vue'
import { createInviteApi } from '../api/invite-api'

/** 用户端：我的邀请码（绑定 / 分页列表）。绑定后不可自行解绑，删除仅限管理员。 */
export function useMyInviteCodes(sdk: YuDreamPluginSdk) {
  const api = createInviteApi(sdk)
  const toast = useFaToast()
  const loading = ref(false)
  const binding = ref(false)
  const records = ref<MyBinding[]>([])
  const total = ref(0)
  const form = reactive({ code: '', remark: '' })

  async function load(page = 1, size = 20) {
    loading.value = true
    try {
      const body = await api.myBindings(page, size)
      records.value = body.records
      total.value = body.total
    }
    finally {
      loading.value = false
    }
  }

  async function bind() {
    if (!form.code.trim()) {
      toast.error('请输入邀请码')
      return
    }
    binding.value = true
    try {
      await api.bindInvite(form.code, form.remark)
      toast.success('邀请码绑定成功')
      form.code = ''
      form.remark = ''
      await load()
    }
    finally {
      binding.value = false
    }
  }

  return reactive({ loading, binding, records, total, form, load, bind })
}

/** 管理端：邀请码绑定总览（跨用户搜索 + 分页 + 删除纠错）。 */
export function useAdminBindings(sdk: YuDreamPluginSdk) {
  const api = createInviteApi(sdk)
  const toast = useFaToast()
  const confirm = useFaModal()
  const loading = ref(false)
  const records = ref<AdminBinding[]>([])
  const total = ref(0)
  const keyword = ref('')

  async function load(page = 1, size = 10) {
    loading.value = true
    try {
      const body = await api.adminBindings(keyword.value, page, size)
      records.value = body.records
      total.value = body.total
    }
    finally {
      loading.value = false
    }
  }

  async function search() {
    await load(1, 10)
  }

  function remove(record: AdminBinding) {
    confirm.confirm({
      title: '删除绑定记录',
      content: `确认删除「${record.username}」对邀请码「${record.code}」的绑定吗？删除后该邀请码可被重新绑定。`,
      onConfirm: async () => {
        await api.deleteAdminBinding(record.id)
        toast.success('绑定记录已删除')
        await load()
      },
    })
  }

  return reactive({ loading, records, total, keyword, load, search, remove })
}
