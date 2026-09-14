<script setup lang="ts">
import type { YuDreamPluginSdk } from '@yudream/plugin-sdk'
import { FaButton, FaIcon, useFaToast } from '@yudream/components'
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'

interface DashboardCardLike {
  title?: string
  description?: string
  actionPath?: string
  dragPayloadTemplate?: string
}

const props = defineProps<{ sdk: YuDreamPluginSdk, card: DashboardCardLike, onOpen?: (card?: DashboardCardLike) => void }>()
const toast = useFaToast()
const endpointUrl = computed(() => absoluteUrl(props.sdk.http.url('/api/yggdrasil')).replace(/\/$/, ''))
/** 拖拽数据默认按 authlib-injector 约定生成（authlib-injector:yggdrasil-server:<URL 编码后的地址>）。 */
const dragPayload = computed(() => (props.card.dragPayloadTemplate || 'authlib-injector:yggdrasil-server:{encodedUrl}')
  .replaceAll('{encodedUrl}', encodeURIComponent(endpointUrl.value))
  .replaceAll('{url}', endpointUrl.value))
const copied = ref(false)
let copiedTimer: ReturnType<typeof setTimeout> | undefined

async function copyEndpoint() {
  try {
    await navigator.clipboard.writeText(endpointUrl.value)
  }
  catch {
    const input = document.createElement('textarea')
    input.value = endpointUrl.value
    input.style.position = 'fixed'
    input.style.opacity = '0'
    document.body.appendChild(input)
    input.select()
    document.execCommand('copy')
    input.remove()
  }
  copied.value = true
  if (copiedTimer) {
    clearTimeout(copiedTimer)
  }
  copiedTimer = setTimeout(() => {
    copied.value = false
  }, 2000)
  toast.success('API 地址已复制')
}

/** 地址框既可直接拖到启动器，也可点击复制地址。 */
function handleDragStart(event: DragEvent) {
  if (!event.dataTransfer) {
    return
  }
  event.stopPropagation()
  event.dataTransfer.effectAllowed = 'copy'
  event.dataTransfer.dropEffect = 'copy'
  // 与 authlib-injector 插件一致：text/plain 放拖拽协议串，text/uri-list 放原始地址。
  event.dataTransfer.setData('text/plain', dragPayload.value)
  event.dataTransfer.setData('text/uri-list', endpointUrl.value)
}

/** 通过 SJMCL 自定义协议唤起添加认证服务器。 */
function addToSjmcl() {
  window.location.href = `sjmcl://add-auth-server?url=${encodeURIComponent(endpointUrl.value)}`
}

function absoluteUrl(url: string) {
  if (/^https?:\/\//i.test(url)) {
    return url
  }
  return `${window.location.origin}${url.startsWith('/') ? url : `/${url}`}`
}

/**
 * 仪表盘卡片高度由宿主决定，且往往偏紧。
 * 高度不足时收起描述与提示文案，确保地址框和下方操作按钮始终完整可见。
 */
const rootRef = ref<HTMLElement | null>(null)
const compact = ref(false)
let resizeObserver: ResizeObserver | undefined

onMounted(() => {
  const el = rootRef.value
  if (!el || typeof ResizeObserver === 'undefined') {
    return
  }
  resizeObserver = new ResizeObserver(() => {
    const height = el.clientHeight
    if (height <= 0) {
      return
    }
    // 双阈值迟滞，避免在临界高度反复切换。
    if (height < 150) {
      compact.value = true
    }
    else if (height >= 175) {
      compact.value = false
    }
  })
  resizeObserver.observe(el)
})

onBeforeUnmount(() => {
  resizeObserver?.disconnect()
  resizeObserver = undefined
})
</script>

<template>
  <div
    ref="rootRef"
    class="dashboard-card__content yggc-dashboard-card"
    :class="{ 'is-compact': compact }"
  >
    <div class="yggc-dashboard-card__body">
      <p v-if="card.description" class="yggc-dashboard-card__desc">{{ card.description }}</p>
      <button
        type="button"
        class="yggc-dashboard-card__endpoint yggc-grab"
        title="拖到 HMCL / PCL2 等启动器即可快速添加，点击复制地址"
        draggable="true"
        @click="copyEndpoint"
        @dragstart="handleDragStart"
      >
        <code>{{ endpointUrl }}</code>
        <span class="yggc-dashboard-card__copy">
          <FaIcon :name="copied ? 'i-ri:check-line' : 'i-ri:drag-move-line'" />
        </span>
      </button>
      <p class="yggc-dashboard-card__hint">
        <FaIcon name="i-ri:information-line" />
        <span>支持传统 Yggdrasil 与 OAuth 登录，拖到启动器或点击复制</span>
      </p>
    </div>
    <div class="yggc-dashboard-card__actions">
      <FaButton
        size="sm"
        variant="secondary"
        title="唤起 SJMCL 启动器并添加本站认证服务器"
        @click="addToSjmcl"
      >
        <FaIcon name="i-ri:add-circle-line" />
        <span>添加到 SJMCL</span>
      </FaButton>
      <FaButton v-if="card.actionPath" size="sm" variant="outline" @click="onOpen?.(card)">打开管理</FaButton>
    </div>
  </div>
</template>
