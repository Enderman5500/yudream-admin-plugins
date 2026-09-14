<script setup lang="ts">
import type { YuDreamPluginSdk } from '@yudream/plugin-sdk'
import type { HostExternalAccount } from '../types'
import { FaButton, FaIcon, FaInput, FaLabel } from '@yudream/components'
import { onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { createCasApi } from '../api/cas-api'

/**
 * 学生档案预填挂件（组件 key = cas/Prefill，由 TaruSsoPlugin.registerGlobalWidget 注册）。
 *
 * 行为约定：
 * - 未登录不渲染；未绑定 CAS 不渲染；
 * - 已在 yudream-student-info 插件填写过档案（studentNo 非空）不渲染；
 * - 用户点「暂不填写」后按账号记忆（localStorage），不再打扰；
 * - 预填数据来自 cas 插件 /me/profile（按绑定记录里的学工号取最小字段集）；
 * - 保存调用 yudream-student-info 插件的 PUT /me（原生 fetch + localStorage token）；
 * - 任何查询失败静默放弃（fail-silent），不打断用户。
 */
const props = defineProps<{ sdk: YuDreamPluginSdk, widget?: unknown }>()
const api = createCasApi(props.sdk)

const CHECK_INTERVAL_MS = 15_000
const DISMISS_KEY_PREFIX = 'cas-prefill-dismissed:'

const bannerVisible = ref(false)
const formVisible = ref(false)
const saving = ref(false)
const failed = ref(false)
const checking = ref(false)

const form = reactive({
  studentName: '',
  studentNo: '',
  className: '',
  college: '',
})

let timer: ReturnType<typeof setInterval> | null = null

onMounted(() => {
  check()
  // 轮询覆盖「绑定完成后触发预填」「填写完成后自动消失」两类时序
  timer = setInterval(check, CHECK_INTERVAL_MS)
})

onBeforeUnmount(() => {
  if (timer) {
    clearInterval(timer)
    timer = null
  }
})

async function check() {
  if (checking.value || bannerVisible.value || formVisible.value) {
    return
  }
  checking.value = true
  try {
    const account = props.sdk.account
    if (!account || !account.userId) {
      return
    }
    const token = localStorage.getItem('token')
    if (!token || localStorage.getItem(DISMISS_KEY_PREFIX + account.userId)) {
      return
    }
    // 1. 宿主绑定记录里找 CAS 绑定，取学工号
    const accounts = await fetchExternalAccounts()
    if (accounts === null) {
      return
    }
    const binding = accounts.find(item => item.providerCode === 'cas' && item.socialUid)
    if (!binding || !binding.socialUid) {
      return
    }
    // 2. yudream-student-info 插件已填写过档案则不再提示
    const existing = await fetchStudentInfoMine()
    if (existing === null || existing) {
      return
    }
    // 3. 取 cas 档案预填字段
    const prefill = await api.myProfile(binding.socialUid).catch(() => null)
    if (!prefill || !prefill.studentNo) {
      return
    }
    form.studentName = prefill.studentName || ''
    form.studentNo = prefill.studentNo
    form.className = prefill.className || ''
    form.college = prefill.college || ''
    bannerVisible.value = true
  }
  catch {
    // fail-silent
  }
  finally {
    checking.value = false
  }
}

async function fetchExternalAccounts(): Promise<HostExternalAccount[] | null> {
  const token = localStorage.getItem('token')
  if (!token) {
    return null
  }
  const response = await fetch('/api/user/me/external-accounts', {
    headers: { Authorization: token, 'Accept-Language': 'zh-CN' },
  })
  if (!response.ok) {
    return null
  }
  const result = await response.json() as { code?: number, data?: HostExternalAccount[] }
  if (result.code !== 200 || !Array.isArray(result.data)) {
    return null
  }
  return result.data
}

/** 返回 null 表示学生档案插件不可用；true 表示已填写；false 表示未填写。 */
async function fetchStudentInfoMine(): Promise<boolean | null> {
  const token = localStorage.getItem('token')
  if (!token) {
    return null
  }
  const response = await fetch('/api/plugins/yudream-student-info/api/me', {
    headers: { Authorization: token, 'Accept-Language': 'zh-CN' },
  })
  if (!response.ok) {
    return null
  }
  const result = await response.json() as { code?: number, data?: { studentNo?: string | null } }
  if (result.code !== 200) {
    return null
  }
  return Boolean(result.data?.studentNo)
}

function openForm() {
  bannerVisible.value = false
  failed.value = false
  formVisible.value = true
}

function closeForm() {
  formVisible.value = false
}

function dismiss() {
  const account = props.sdk.account
  if (account && account.userId) {
    localStorage.setItem(DISMISS_KEY_PREFIX + account.userId, '1')
  }
  bannerVisible.value = false
}

const submittable = () =>
  form.studentName.trim() && form.studentNo.trim() && form.className.trim() && form.college.trim()

async function submit() {
  if (!submittable() || saving.value) {
    return
  }
  saving.value = true
  failed.value = false
  try {
    const token = localStorage.getItem('token')
    const response = await fetch('/api/plugins/yudream-student-info/api/me', {
      method: 'PUT',
      headers: {
        Authorization: token || '',
        'Content-Type': 'application/json',
        'Accept-Language': 'zh-CN',
      },
      body: JSON.stringify({
        studentName: form.studentName.trim(),
        studentNo: form.studentNo.trim(),
        className: form.className.trim(),
        college: form.college.trim(),
      }),
    })
    const result = await response.json() as { code?: number, message?: string }
    if (!response.ok || result.code !== 200) {
      throw new Error(result.message || `HTTP ${response.status}`)
    }
    formVisible.value = false
    bannerVisible.value = false
  }
  catch {
    failed.value = true
  }
  finally {
    saving.value = false
  }
}
</script>

<template>
  <Teleport to="body">
    <!-- 右下角提醒卡片 -->
    <div v-if="bannerVisible" class="cas-prefill" role="status">
      <div class="cas-prefill__icon">
        <FaIcon name="i-ri:id-card-line" />
      </div>
      <div class="cas-prefill__copy">
        <strong>完善学生档案</strong>
        <small>已检测到校园账号绑定，可一键带入姓名、学号与学院。</small>
      </div>
      <div class="cas-prefill__actions">
        <FaButton size="sm" variant="outline" @click="dismiss">
          暂不填写
        </FaButton>
        <FaButton size="sm" @click="openForm">
          <FaIcon name="i-ri:quill-pen-line" />
          去填写
        </FaButton>
      </div>
    </div>

    <!-- 预填表单 -->
    <div v-if="formVisible" class="cas-prefill-modal" role="dialog" aria-modal="true" aria-label="完善学生档案">
      <div class="cas-prefill-modal__panel">
        <h2 class="cas-prefill-modal__title">
          完善学生档案
        </h2>
        <p class="cas-prefill-modal__desc">
          带 <FaIcon name="i-ri:magic-line" /> 标记的字段由统一身份认证自动带入，请核对补全后保存。
        </p>
        <div class="cas-prefill-modal__grid">
          <FaLabel label="姓名" class="cas-prefill-modal__field">
            <FaInput v-model="form.studentName" class="w-full" maxlength="40" />
          </FaLabel>
          <FaLabel label="学号" class="cas-prefill-modal__field">
            <FaInput v-model="form.studentNo" class="w-full" maxlength="64" />
          </FaLabel>
          <FaLabel label="学院" class="cas-prefill-modal__field">
            <FaInput v-model="form.college" class="w-full" maxlength="80" />
          </FaLabel>
          <FaLabel label="班级" class="cas-prefill-modal__field">
            <FaInput v-model="form.className" class="w-full" maxlength="80" placeholder="认证中心未提供时请手动填写" />
          </FaLabel>
        </div>
        <p v-if="failed" class="cas-prefill-modal__error">
          保存失败，请稍后重试；若持续失败请到「学生信息」页手动填写。
        </p>
        <div class="cas-prefill-modal__actions">
          <FaButton variant="outline" :disabled="saving" @click="closeForm">
            取消
          </FaButton>
          <FaButton :loading="saving" :disabled="!submittable()" @click="submit">
            <FaIcon name="i-ri:save-3-line" />
            保存档案
          </FaButton>
        </div>
      </div>
    </div>
  </Teleport>
</template>

<style scoped>
.cas-prefill {
  position: fixed;
  right: 20px;
  bottom: 20px;
  z-index: 9000;
  display: flex;
  align-items: center;
  gap: 12px;
  max-width: 420px;
  padding: 14px 16px;
  border-radius: 12px;
  background: var(--color-bg, #fff);
  border: 1px solid var(--color-border, #e5e6eb);
  box-shadow: 0 8px 28px rgb(0 0 0 / 12%);
}

.cas-prefill__icon {
  display: flex;
  align-items: center;
  justify-content: center;
  flex: 0 0 auto;
  width: 40px;
  height: 40px;
  font-size: 20px;
  border-radius: 50%;
  color: var(--color-primary, #165dff);
  background: color-mix(in srgb, var(--color-primary, #165dff) 10%, transparent);
}

.cas-prefill__copy {
  display: grid;
  gap: 2px;
  flex: 1 1 auto;
  min-width: 0;
}

.cas-prefill__copy strong {
  font-size: 14px;
  font-weight: 600;
}

.cas-prefill__copy small {
  font-size: 12px;
  line-height: 1.5;
  color: var(--color-text-3, #86909c);
}

.cas-prefill__actions {
  display: flex;
  flex: 0 0 auto;
  gap: 8px;
}

.cas-prefill-modal {
  position: fixed;
  inset: 0;
  z-index: 9900;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 24px;
  background: color-mix(in srgb, var(--color-bg, #fff) 82%, transparent);
  backdrop-filter: blur(6px);
}

.cas-prefill-modal__panel {
  display: grid;
  gap: 14px;
  width: min(480px, 100%);
  padding: 26px 24px;
  border-radius: 14px;
  background: var(--color-bg, #fff);
  border: 1px solid var(--color-border, #e5e6eb);
  box-shadow: 0 12px 40px rgb(0 0 0 / 12%);
}

.cas-prefill-modal__title {
  margin: 0;
  font-size: 17px;
  font-weight: 600;
}

.cas-prefill-modal__desc {
  margin: 0;
  font-size: 13px;
  line-height: 1.6;
  color: var(--color-text-3, #86909c);
}

.cas-prefill-modal__grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}

.cas-prefill-modal__field {
  display: grid;
  gap: 4px;
}

.cas-prefill-modal__error {
  margin: 0;
  font-size: 12px;
  color: var(--color-danger, #f53f3f);
}

.cas-prefill-modal__actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
}

@media (max-width: 560px) {
  .cas-prefill-modal__grid {
    grid-template-columns: 1fr;
  }

  .cas-prefill {
    right: 12px;
    left: 12px;
    bottom: 12px;
    max-width: none;
    flex-wrap: wrap;
  }
}
</style>
