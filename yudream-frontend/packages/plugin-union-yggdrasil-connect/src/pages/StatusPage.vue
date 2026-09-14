<script setup lang="ts">
import type { RouteLocationNormalizedLoaded } from 'vue-router'
import type { TableColumn } from '@yudream/components'
import type { YuDreamPluginSdk } from '@yudream/plugin-sdk'
import type { YggcEndpoint } from '../types'
import { FaButton, FaCard, FaIcon, FaPageHeader, FaPageMain, FaResponsiveTable, FaTag } from '@yudream/components'
import { onMounted } from 'vue'
import { useYggcAdmin } from '../composables/useYggcAdmin'

const props = defineProps<{
  sdk: YuDreamPluginSdk
  route?: RouteLocationNormalizedLoaded
}>()

void props.route

const model = useYggcAdmin(props.sdk)

const columns: TableColumn<YggcEndpoint>[] = [
  { accessorKey: 'method', header: '方法', width: 100, fixed: 'left' },
  { accessorKey: 'path', header: '协议路径', width: 420 },
  { accessorKey: 'note', header: '用途', width: 360 },
]

onMounted(model.load)
</script>

<template>
  <section class="yggc-home">
    <FaPageHeader title="Yggdrasil Connect 运行状态" description="查看验证服务器地址、服务概览和固定协议端点。" class="mb-0">
      <FaButton variant="outline" :loading="model.loading" @click="model.load">
        <FaIcon name="i-ri:refresh-line" />
        刷新
      </FaButton>
      <FaButton @click="model.copy(model.apiRootUrl)">
        <FaIcon name="i-ri:file-copy-line" />
        复制 API 地址
      </FaButton>
    </FaPageHeader>

    <FaPageMain>
      <div class="yggc-summary-grid">
        <FaCard title="启动器配置" description="Yggdrasil 服务根地址" content-class="yggc-card-content">
          <div class="yggc-address-row">
            <code>{{ model.apiRootUrl }}</code>
            <FaButton size="sm" variant="outline" @click="model.copy(model.apiRootUrl)">
              <FaIcon name="i-ri:file-copy-line" />
              复制
            </FaButton>
          </div>
          <div class="yggc-address-row">
            <code :title="model.discoveryUrl">{{ model.discoveryUrl }}</code>
            <FaButton size="sm" variant="outline" @click="model.copy(model.discoveryUrl)">复制发现文档</FaButton>
          </div>
          <p class="yggc-muted">将此地址填写到支持 authlib-injector 的启动器或服务端配置中；支持 Yggdrasil Connect 的启动器会自动发现 OAuth 登录入口。</p>
        </FaCard>

        <FaCard title="服务概览" description="插件运行统计" content-class="yggc-card-content">
          <div class="yggc-status-line">
            <span>连接状态</span>
            <FaTag :variant="model.status ? 'default' : 'secondary'">{{ model.statusText }}</FaTag>
          </div>
          <div class="yggc-status-line"><span>OAuth 应用</span><strong>{{ model.status?.stats?.clients ?? '-' }} 个</strong></div>
          <div class="yggc-status-line"><span>有效 OAuth 令牌</span><strong>{{ model.status?.stats?.tokens ?? '-' }} 个</strong></div>
          <div class="yggc-status-line"><span>传统登录会话</span><strong>{{ model.status?.stats?.sessions ?? '-' }} 个</strong></div>
          <div class="yggc-status-line"><span>皮肤插件</span><strong>{{ model.status?.skinPluginEnabled ? '正常' : '未启用' }}</strong></div>
        </FaCard>
      </div>

      <FaResponsiveTable
        v-loading="model.loading"
        row-key="path"
        table-root-class="yggc-endpoint-table-root rounded-lg overflow-hidden"
        table-class="min-w-[880px]"
        border
        stripe
        column-visibility
        :columns="columns"
        :data="model.endpoints"
      >
        <template #toolbar>
          <div class="yggc-table-toolbar">
            <div>
              <strong>协议端点</strong>
              <span>传统 Yggdrasil + Yggdrasil Connect 固定协议清单，不作为管理 CRUD 数据。</span>
            </div>
            <FaTag variant="secondary">{{ model.endpoints.length }} 个端点</FaTag>
          </div>
        </template>
        <template #cell-method="{ row }">
          <FaTag :variant="row.original.method === 'GET' ? 'secondary' : 'default'">{{ row.original.method }}</FaTag>
        </template>
        <template #cell-path="{ row }"><code>{{ row.original.path }}</code></template>
        <template #card="{ row }">
          <FaCard class="w-full">
            <div class="flex flex-col gap-3">
              <div class="flex items-center justify-between gap-2">
                <span class="text-base font-semibold break-all">{{ row.path }}</span>
                <div class="flex gap-1">
                  <FaTag :variant="row.method === 'GET' ? 'secondary' : 'default'">{{ row.method }}</FaTag>
                </div>
              </div>
              <div class="flex flex-col gap-1 text-sm">
                <div v-if="row.note" class="flex gap-2">
                  <span class="shrink-0 text-secondary-foreground/60">用途</span>
                  <span class="break-all">{{ row.note }}</span>
                </div>
              </div>
            </div>
          </FaCard>
        </template>
      </FaResponsiveTable>
    </FaPageMain>
  </section>
</template>
