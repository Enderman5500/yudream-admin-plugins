<script setup lang="ts">
import type { TableColumn } from '@yudream/components'
import type { YuDreamPluginSdk } from '@yudream/plugin-sdk'
import type { MailRecord, MailRecordStatus } from '../types'
import {
  FaButton,
  FaCard,
  FaDrawer,
  FaIcon,
  FaInput,
  FaPageHeader,
  FaPageMain,
  FaPagination,
  FaResponsiveTable,
  FaSearchBar,
  FaSelect,
  FaTag,
} from '@yudream/components'
import { computed, onMounted } from 'vue'
import { useMailMessages } from '../composables/useMailMessages'
import { RECORD_STATUS_OPTIONS, addressLabel } from '../utils'

const props = defineProps<{ sdk: YuDreamPluginSdk }>()
const canManage = props.sdk.account.permissions.includes('plugin:mail:manage')
const model = useMailMessages(props.sdk, canManage)

const SCOPE_OPTIONS = [
  { label: '全部用户的发信记录', value: 'all' },
  { label: '我的发信记录', value: 'mine' },
]

const addressFilterOptions = computed(() => model.addressOptions.map(item => ({
  label: addressLabel(item),
  value: item.id,
})))

const columns: TableColumn<MailRecord>[] = [
  { id: 'subject', header: '主题', minWidth: 200, fixed: 'left' },
  { id: 'fromAddress', header: '发件地址', width: 220 },
  { id: 'to', header: '收件人', width: 200 },
  { id: 'statusLabel', header: '结果', width: 100, align: 'center' },
  { id: 'sourceLabel', header: '来源', width: 90, align: 'center' },
  { id: 'operatorName', header: '操作人', width: 120 },
  { id: 'createdAt', header: '时间', width: 150 },
  { id: 'operation', header: '操作', width: 90, align: 'center', fixed: 'right' },
]

function statusVariant(status: MailRecordStatus) {
  return status === 'SENT' ? 'default' : 'destructive'
}

function openDetail(row: MailRecord) {
  void model.openDetail(row)
}

onMounted(() => {
  void model.loadAddressOptions()
  void model.load()
})
</script>

<template>
  <section class="mail-page">
    <FaPageHeader
      title="发信记录"
      :description="model.scope === 'all'
        ? '所有通过插件 SMTP 客户端发出的邮件的审计日志；点开单封可查看正文预览与失败原因'
        : '你本人通过插件发出的邮件记录；点开单封可查看正文预览与失败原因'"
      class="mb-0"
    />

    <FaPageMain>
      <FaResponsiveTable
        v-loading="model.loading"
        row-key="id"
        table-root-class="max-w-full overflow-x-auto rounded-lg"
        table-class="min-w-[1160px]"
        border
        stripe
        column-visibility
        :columns="columns"
        :data="model.records"
      >
        <template #toolbar>
          <div class="flex flex-col gap-2 w-full">
            <FaSelect
              v-if="canManage"
              :model-value="model.scope"
              :options="SCOPE_OPTIONS"
              class="w-full sm:max-w-[240px]"
              @update:model-value="(value: unknown) => { void model.setScope((value ?? 'all') as 'mine' | 'all') }"
            />
            <FaSearchBar v-if="model.scope === 'all'" class="w-full">
              <form class="mail-filter-grid" @submit.prevent="model.search">
              <FaInput v-model="model.filters.keyword" clearable class="w-full" placeholder="主题 / 收件人 / 发件地址" @clear="model.search" />
              <FaSelect
                :model-value="model.filters.addressId"
                :options="addressFilterOptions"
                class="w-full"
                placeholder="全部发件地址"
                @update:model-value="(value: unknown) => { model.filters.addressId = String(value ?? '') }"
                @change="model.search"
              />
              <FaSelect
                :model-value="model.filters.status"
                :options="RECORD_STATUS_OPTIONS"
                class="w-full"
                @update:model-value="(value: unknown) => { model.filters.status = (value ?? '') as typeof model.filters.status }"
                @change="model.search"
              />
              <div class="mail-filter-actions">
                <FaButton type="button" variant="outline" @click="model.resetFilters">重置</FaButton>
                <FaButton type="submit">
                  <FaIcon name="i-ri:search-line" />
                  查询
                </FaButton>
              </div>
              </form>
            </FaSearchBar>
          </div>
        </template>

        <template #cell-subject="{ row }">
          <div class="mail-cell-stack">
            <strong class="mail-strong">{{ row.original.subject }}</strong>
            <span class="mail-muted">{{ row.original.bodyPreview }}</span>
          </div>
        </template>
        <template #cell-fromAddress="{ row }">
          <span class="mail-muted break-all">{{ row.original.fromAddress }}</span>
        </template>
        <template #cell-to="{ row }">
          <span class="mail-muted break-all">{{ row.original.to.slice(0, 3).join('、') }}{{ row.original.to.length > 3 ? ` 等 ${row.original.to.length} 人` : '' }}</span>
        </template>
        <template #cell-statusLabel="{ row }">
          <FaTag :variant="statusVariant(row.original.status)">{{ row.original.statusLabel }}</FaTag>
        </template>
        <template #cell-sourceLabel="{ row }">
          <FaTag variant="secondary">{{ row.original.sourceLabel }}</FaTag>
        </template>
        <template #cell-operatorName="{ row }">{{ row.original.operatorName || '-' }}</template>
        <template #cell-createdAt="{ row }">{{ model.formatTime(row.original.createdAt) }}</template>
        <template #cell-operation="{ row }">
          <FaButton size="sm" variant="outline" @click="openDetail(row.original)">详情</FaButton>
        </template>

        <template #card="{ row }">
          <FaCard class="w-full">
            <div class="flex flex-col gap-2">
              <div class="flex items-start justify-between gap-2">
                <strong class="mail-strong break-all">{{ row.subject }}</strong>
                <FaTag :variant="statusVariant(row.status)">{{ row.statusLabel }}</FaTag>
              </div>
              <div class="mail-card-meta">
                <span>发件地址：{{ row.fromAddress }}</span>
                <span>收件人：{{ row.to.join('、') }}</span>
                <span>操作人：{{ row.operatorName || '-' }} · {{ model.formatTime(row.createdAt) }}</span>
                <span v-if="row.errorMessage" class="text-destructive">失败原因：{{ row.errorMessage }}</span>
              </div>
              <div class="mail-card-actions">
                <FaButton size="sm" variant="outline" @click="openDetail(row)">查看详情</FaButton>
              </div>
            </div>
          </FaCard>
        </template>
      </FaResponsiveTable>

      <FaPagination
        v-model:page="model.pagination.page"
        v-model:size="model.pagination.size"
        :total="model.pagination.total"
        class="mt-3"
        @page-change="model.onPageChange"
        @size-change="model.onSizeChange"
      />
    </FaPageMain>

    <FaDrawer
      :model-value="!!model.detail"
      title="发信详情"
      side="right"
      content-class="sm:max-w-[720px]"
      :footer="false"
      :loading="model.detailLoading"
      @update:model-value="(value: unknown) => { if (!value) model.closeDetail() }"
    >
      <div v-if="model.detail" class="mail-detail">
        <section class="mail-detail-head">
          <h3 class="mail-strong text-base">{{ model.detail.subject }}</h3>
          <dl class="mail-card-meta">
            <div><dt>发件地址</dt><dd>{{ model.detail.fromAddress }}（{{ model.detail.smtpHost }}）</dd></div>
            <div><dt>收件人</dt><dd>{{ model.detail.to.join('、') || '-' }}</dd></div>
            <div v-if="model.detail.cc.length"><dt>抄送</dt><dd>{{ model.detail.cc.join('、') }}</dd></div>
            <div v-if="model.detail.bcc.length"><dt>密送</dt><dd>{{ model.detail.bcc.join('、') }}</dd></div>
            <div><dt>结果</dt><dd>{{ model.detail.statusLabel }}{{ model.detail.errorMessage ? `：${model.detail.errorMessage}` : '' }}</dd></div>
            <div><dt>来源</dt><dd>{{ model.detail.sourceLabel }} · 操作人 {{ model.detail.operatorName || '-' }}</dd></div>
            <div><dt>时间</dt><dd>{{ model.formatTime(model.detail.createdAt) }}</dd></div>
          </dl>
        </section>
        <pre class="mail-body-preview">{{ model.detail.body || model.detail.bodyPreview }}</pre>
      </div>
    </FaDrawer>
  </section>
</template>

<style scoped>
.mail-detail-head dl {
  display: grid;
  gap: 4px;
  margin: 0;
}

.mail-detail-head dt {
  margin-right: 6px;
  color: var(--color-text-3);
}

.mail-detail-head dt,
.mail-detail-head dd {
  display: inline;
}
</style>
