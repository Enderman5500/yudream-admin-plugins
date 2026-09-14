<script setup lang="ts">
import type { YuDreamPluginSdk } from '@yudream/plugin-sdk'
import { FaButton, FaCard, FaIcon, FaInput, FaPagination, FaResponsiveTable, FaTag } from '@yudream/components'
import type { TableColumn } from '@yudream/components'
import type { MyBinding } from '../types'
import { onMounted, reactive } from 'vue'
import { useMyInviteCodes } from '../composables/useInviteBindings'

const props = defineProps<{
  sdk: YuDreamPluginSdk
}>()

const model = useMyInviteCodes(props.sdk)
const pagination = reactive({ page: 1, size: 20 })

const columns: TableColumn<MyBinding>[] = [
  { id: 'code', header: '邀请码', width: 280, fixed: 'left' },
  { id: 'remark', header: '备注', width: 280 },
  { id: 'boundAt', header: '绑定时间', width: 200 },
]

function formatTime(value: number) {
  return value ? new Date(value).toLocaleString() : '-'
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
        <span>邀请码</span>
        <h2>我的邀请码</h2>
      </div>
    </section>

    <FaCard title="绑定新邀请码">
      <p class="invite-muted">
        邀请码由合作皮肤站生成，用于外校成员在该站创建账户。一个邀请码只能被一位成员绑定；你可以绑定多个邀请码。
        绑定后不支持自行解绑，如需删除请联系管理员处理。
      </p>
      <div class="invite-bind-form">
        <FaInput v-model="model.form.code" placeholder="输入邀请码" :max-length="128" @keydown.enter="model.bind" />
        <FaInput v-model="model.form.remark" placeholder="备注（选填，例如被邀请人）" :max-length="200" />
        <FaButton :loading="model.binding" @click="model.bind">
          <FaIcon name="i-ri:key-2-line" />
          绑定
        </FaButton>
      </div>
    </FaCard>

    <section class="invite-panel">
      <FaResponsiveTable
        v-loading="model.loading"
        row-key="id"
        table-root-class="max-w-full overflow-x-auto rounded-lg"
        table-class="min-w-[720px]"
        border
        stripe
        :columns="columns"
        :data="model.records"
        empty-text="还没有绑定任何邀请码"
      >
        <template #cell-code="{ row }">
          <code class="invite-code">{{ row.original.code }}</code>
        </template>
        <template #cell-remark="{ row }">
          <FaTag v-if="row.original.remark" variant="secondary">{{ row.original.remark }}</FaTag>
          <span v-else class="invite-muted">-</span>
        </template>
        <template #cell-boundAt="{ row }">{{ formatTime(row.original.boundAt) }}</template>
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
