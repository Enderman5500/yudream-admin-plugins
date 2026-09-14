<script setup lang="ts">
import type { TableColumn } from '@yudream/components'
import type { YuDreamPluginSdk } from '@yudream/plugin-sdk'
import type { AdminBinding } from '../types'
import { FaButton, FaIcon, FaInput, FaPagination, FaResponsiveTable, FaSearchBar, FaTag } from '@yudream/components'
import { onMounted, reactive } from 'vue'
import { useAdminBindings } from '../composables/useInviteBindings'

const props = defineProps<{
  sdk: YuDreamPluginSdk
}>()

const model = useAdminBindings(props.sdk)
const pagination = reactive({ page: 1, size: 10 })

const columns: TableColumn<AdminBinding>[] = [
  { id: 'username', header: '绑定用户', width: 180, fixed: 'left' },
  { id: 'code', header: '邀请码', width: 260 },
  { id: 'remark', header: '备注', width: 220 },
  { id: 'boundAt', header: '绑定时间', width: 170 },
  { id: 'operation', header: '操作', width: 110, align: 'center', fixed: 'right' },
]

function formatTime(value: number) {
  return value ? new Date(value).toLocaleString() : '-'
}

async function search() {
  pagination.page = 1
  await model.load(pagination.page, pagination.size)
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
  <div class="invite-plugin">
    <section class="invite-toolbar">
      <div>
        <span>邀请码插件</span>
        <h2>邀请码绑定总览</h2>
      </div>
      <div class="invite-actions">
        <FaButton variant="outline" :loading="model.loading" @click="model.load(pagination.page, pagination.size)">
          <FaIcon name="i-ri:refresh-line" />
          刷新
        </FaButton>
      </div>
    </section>

    <section class="invite-panel">
      <FaSearchBar class="mb-3 w-full">
        <div class="invite-filter-bar">
          <FaInput v-model="model.keyword" placeholder="搜索邀请码、用户 ID 或备注" clearable @keydown.enter="search" @clear="search" />
          <FaButton variant="outline" @click="search">
            <FaIcon name="i-ri:search-line" />
            查询
          </FaButton>
        </div>
      </FaSearchBar>
      <FaResponsiveTable
        v-loading="model.loading"
        row-key="id"
        table-root-class="max-w-full overflow-x-auto rounded-lg"
        table-class="min-w-[940px]"
        border
        stripe
        :columns="columns"
        :data="model.records"
        empty-text="暂无绑定记录"
      >
        <template #cell-username="{ row }">
          <div class="invite-title-cell">
            <strong>{{ row.original.username }}</strong>
            <span class="invite-muted">ID {{ row.original.userId }}</span>
          </div>
        </template>
        <template #cell-code="{ row }">
          <code class="invite-code">{{ row.original.code }}</code>
        </template>
        <template #cell-remark="{ row }">
          <FaTag v-if="row.original.remark" variant="secondary">{{ row.original.remark }}</FaTag>
          <span v-else class="invite-muted">-</span>
        </template>
        <template #cell-boundAt="{ row }">{{ formatTime(row.original.boundAt) }}</template>
        <template #cell-operation="{ row }">
          <div class="invite-actions">
            <FaButton size="sm" variant="destructive" @click="model.remove(row.original)">删除</FaButton>
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
