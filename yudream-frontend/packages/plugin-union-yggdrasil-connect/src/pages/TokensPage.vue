<script setup lang="ts">
import type { TableColumn } from '@yudream/components'
import type { RouteLocationNormalizedLoaded } from 'vue-router'
import type { YuDreamPluginSdk } from '@yudream/plugin-sdk'
import type { YggcTokenView } from '../types'
import { FaButton, FaPagination, FaResponsiveTable, FaTag, useFaModal } from '@yudream/components'
import { onMounted, reactive } from 'vue'
import { useYggcTokens } from '../composables/useYggcClients'

const props = defineProps<{
  sdk: YuDreamPluginSdk
  route?: RouteLocationNormalizedLoaded
}>()

void props.route

const model = useYggcTokens(props.sdk)
const confirm = useFaModal()
const pagination = reactive({ page: 1, size: 10 })
const columns: TableColumn<YggcTokenView>[] = [
  { id: 'token', header: '访问令牌', width: 260, fixed: 'left' },
  { id: 'client', header: '应用', width: 160 },
  { id: 'user', header: '用户', width: 140 },
  { id: 'profile', header: '绑定角色', width: 160 },
  { id: 'scopes', header: '授权范围', width: 320 },
  { id: 'expiresAt', header: '过期时间', width: 170 },
  { id: 'operation', header: '操作', width: 120, align: 'center', fixed: 'right' },
]

function formatTime(value: number) {
  return value ? new Date(value).toLocaleString() : '-'
}

function askRevoke(token: YggcTokenView) {
  confirm.confirm({
    title: '吊销令牌',
    content: '确认吊销该访问令牌吗？对应刷新令牌将一并吊销。',
    onConfirm: () => model.revoke(token),
  })
}

async function changePage(page: number) {
  pagination.page = page
  await model.load(pagination.page, pagination.size)
}

async function changeSize(size: number) {
  pagination.size = size
  pagination.page = 1
  await model.load(pagination.page, pagination.size)
}

onMounted(() => model.load(pagination.page, pagination.size))
</script>

<template>
  <div class="yggc-plugin">
    <section class="yggc-toolbar">
      <div>
        <span>OAuth 令牌</span>
        <h2>令牌管理</h2>
      </div>
      <div class="yggc-actions">
        <FaButton variant="outline" :loading="model.loading" @click="model.load(pagination.page, pagination.size)">刷新</FaButton>
      </div>
    </section>

    <section class="yggc-panel">
      <FaResponsiveTable
        v-loading="model.loading"
        row-key="token"
        table-root-class="max-w-full overflow-x-auto rounded-lg"
        table-class="min-w-[1330px]"
        border
        stripe
        :columns="columns"
        :data="model.records"
        empty-text="暂无有效令牌"
      >
        <template #cell-token="{ row }">
          <code class="yggc-code">{{ row.original.token.slice(0, 16) }}…</code>
        </template>
        <template #cell-client="{ row }">
          <code class="yggc-code">{{ row.original.clientId }}</code>
        </template>
        <template #cell-user="{ row }">{{ row.original.nickname || row.original.userId }}</template>
        <template #cell-profile="{ row }">
          {{ row.original.profileName || '未绑定' }}
        </template>
        <template #cell-scopes="{ row }">
          <div class="yggc-chip-list">
            <FaTag v-for="scope in row.original.scopes" :key="scope" variant="secondary">{{ scope }}</FaTag>
          </div>
        </template>
        <template #cell-expiresAt="{ row }">{{ formatTime(row.original.expiresAt) }}</template>
        <template #cell-operation="{ row }">
          <div class="yggc-actions">
            <FaButton size="sm" variant="destructive" @click="askRevoke(row.original)">吊销</FaButton>
          </div>
        </template>
      </FaResponsiveTable>
      <FaPagination
        :page="pagination.page"
        :size="pagination.size"
        :total="model.total"
        class="mt-3"
        @update:page="changePage"
        @update:size="changeSize"
      />
    </section>
  </div>
</template>
