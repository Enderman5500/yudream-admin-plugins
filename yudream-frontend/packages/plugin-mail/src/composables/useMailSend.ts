import type { YuDreamPluginSdk } from '@yudream/plugin-sdk'
import type { MailAddressOption, MailRecord, MailSendForm } from '../types'
import { useFaToast } from '@yudream/components'
import { computed, reactive, ref } from 'vue'
import { createMailApi } from '../api/mail-api'
import { addressLabel, splitRecipients } from '../utils'

const EMPTY_FORM: MailSendForm = {
  addressId: '',
  to: '',
  cc: '',
  bcc: '',
  subject: '',
  bodyType: 'TEXT',
  body: '',
}

/**
 * 发信工作流（管理端与用户端共用），走插件自带的 SMTP 客户端。
 *
 * <p>只有“已启用且配置了 SMTP”的地址才能作为发件人，因此选择器里只出现这些地址；
 * 一个都没有时页面提示去「邮箱地址」配置，而不是让用户提交一次必然失败的发送。</p>
 */
export function useMailSend(sdk: YuDreamPluginSdk, mode: 'admin' | 'user') {
  const api = createMailApi(sdk)
  const toast = useFaToast()
  const senders = ref<MailAddressOption[]>([])
  const loadingSenders = ref(false)
  const sending = ref(false)
  const form = reactive<MailSendForm>({ ...EMPTY_FORM })
  const lastResult = ref<MailRecord | null>(null)

  const senderOptions = computed(() => senders.value.map(item => ({
    label: item.defaultSender ? `${addressLabel(item)}（默认）` : addressLabel(item),
    value: item.id,
  })))

  const selectedSender = computed(() => senders.value.find(item => item.id === form.addressId) || null)

  async function loadSenders() {
    loadingSenders.value = true
    try {
      const list = mode === 'admin' ? await api.addressOptions() : await api.mySenders()
      senders.value = list.filter(item => item.enabled && item.smtpConfigured)
      if (!senders.value.some(item => item.id === form.addressId)) {
        const preferred = senders.value.find(item => item.defaultSender) || senders.value[0]
        form.addressId = preferred ? preferred.id : ''
      }
    }
    catch {
      // 错误提示由宿主统一处理
    }
    finally {
      loadingSenders.value = false
    }
  }

  function resetForm() {
    const keepSender = form.addressId
    Object.assign(form, { ...EMPTY_FORM, addressId: keepSender })
  }

  /** 发送成功返回 true；校验失败或投递失败返回 false。 */
  async function send(): Promise<boolean> {
    if (!form.addressId) {
      toast.warning('请选择发件地址')
      return false
    }
    const to = splitRecipients(form.to)
    if (!to.length) {
      toast.warning('请填写收件人')
      return false
    }
    if (!form.subject.trim()) {
      toast.warning('请填写邮件主题')
      return false
    }
    if (!form.body.trim()) {
      toast.warning('请填写邮件正文')
      return false
    }
    sending.value = true
    try {
      const payload = {
        addressId: form.addressId,
        to,
        cc: splitRecipients(form.cc),
        bcc: splitRecipients(form.bcc),
        subject: form.subject.trim(),
        bodyType: form.bodyType,
        body: form.body,
      }
      const record = mode === 'admin' ? await api.sendMail(payload) : await api.sendMyMail(payload)
      lastResult.value = record
      toast.success('邮件已发送（SMTP 服务器已接收）')
      resetForm()
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

  return reactive({ senders, loadingSenders, sending, form, lastResult, senderOptions, selectedSender, loadSenders, resetForm, send })
}

export type MailSendModel = ReturnType<typeof useMailSend>
