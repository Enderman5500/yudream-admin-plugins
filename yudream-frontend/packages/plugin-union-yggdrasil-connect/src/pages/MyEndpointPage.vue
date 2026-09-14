<script setup lang="ts">
import type { YuDreamPluginSdk } from '@yudream/plugin-sdk'
import { FaButton, FaCard, FaIcon, useFaToast } from '@yudream/components'
import { computed, ref } from 'vue'

const props = defineProps<{ sdk: YuDreamPluginSdk }>()
const toast = useFaToast()

const endpointUrl = computed(() => absoluteUrl(props.sdk.http.url('/api/yggdrasil')).replace(/\/$/, ''))
/** 拖拽数据与 authlib-injector 约定一致：authlib-injector:yggdrasil-server:<URL 编码后的地址>。 */
const dragPayload = computed(() => `authlib-injector:yggdrasil-server:${encodeURIComponent(endpointUrl.value)}`)
const copied = ref(false)
let copiedTimer: ReturnType<typeof setTimeout> | undefined

async function copyEndpoint() {
  try {
    await navigator.clipboard.writeText(endpointUrl.value)
  }
  catch {
    // 旧浏览器 / 非安全上下文回退
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
  toast.success('认证服务器地址已复制')
}

/** 地址框既可直接拖到启动器，也可点击复制地址。 */
function handleDragStart(event: DragEvent) {
  if (!event.dataTransfer) {
    return
  }
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
</script>

<template>
  <div class="yggc-plugin">
    <section class="yggc-toolbar">
      <div>
        <span>登录服务</span>
        <h2>认证服务器地址</h2>
      </div>
    </section>

    <section class="yggc-panel">
      <FaCard class="w-full">
        <div class="yggc-endpoint-hero">
          <p class="yggc-muted">在启动器或游戏服务器的 authlib-injector 中填写以下地址：</p>
          <div
            id="ygg-dnd-button"
            class="yggc-endpoint-box yggc-grab"
            title="拖到 HMCL / PCL2 等启动器即可快速添加，点击复制地址"
            draggable="true"
            tabindex="0"
            role="button"
            :data-clipboard-text="endpointUrl"
            @click="copyEndpoint"
            @keydown.enter.prevent="copyEndpoint"
            @keydown.space.prevent="copyEndpoint"
            @dragstart="handleDragStart"
          >
            <code>{{ endpointUrl }}</code>
            <span class="yggc-endpoint-box__action">
              <FaIcon :name="copied ? 'i-ri:check-line' : 'i-ri:drag-move-line'" />
              <span>{{ copied ? '已复制' : '拖动 / 复制' }}</span>
            </span>
          </div>
          <p class="yggc-endpoint-hint">
            <FaIcon name="i-ri:information-line" />
            <span>支持传统 Yggdrasil 与 OAuth 登录；把地址框拖到启动器即可添加，点击复制地址。</span>
          </p>
          <div class="yggc-dnd-row">
            <FaButton
              variant="secondary"
              title="唤起 SJMCL 启动器并添加本站认证服务器"
              @click="addToSjmcl"
            >
              <FaIcon name="i-ri:add-circle-line" />
              <span>添加到 SJMCL</span>
            </FaButton>
          </div>
        </div>
      </FaCard>

      <FaCard class="w-full">
        <h3>使用说明</h3>
        <ol class="yggc-endpoint-steps">
          <li>复制上方地址</li>
          <li>打开启动器（如 HMCL / PCL2）的「认证服务器 / 外置登录」设置，粘贴地址并添加</li>
          <li>使用本站账号登录，选择你的角色进入游戏</li>
        </ol>
      </FaCard>
    </section>
  </div>
</template>
