<script setup lang="ts">
import type { TableColumn } from '@yudream/components'
import type { YuDreamPluginSdk } from '@yudream/plugin-sdk'
import type { InboundCheckRecord, InboundCheckStatus } from '../types'
import {
  FaButton,
  FaCard,
  FaIcon,
  FaInput,
  FaLabel,
  FaPageHeader,
  FaPageMain,
  FaPagination,
  FaResponsiveTable,
  FaSearchBar,
  FaSelect,
  FaTag,
} from '@yudream/components'
import { onMounted } from 'vue'
import { useInboundChecks } from '../composables/useInboundChecks'
import { CHECK_STATUS_OPTIONS, WINDOW_OPTIONS } from '../utils'

const props = defineProps<{ sdk: YuDreamPluginSdk }>()
const model = useInboundChecks(props.sdk)

const columns: TableColumn<InboundCheckRecord>[] = [
  { id: 'address', header: '邮箱地址', width: 220, fixed: 'left' },
  { id: 'codeMask', header: '验证码', width: 110, align: 'center' },
  { id: 'statusLabel', header: '结论', width: 100, align: 'center' },
  { id: 'matchedFrom', header: '命中来信', minWidth: 200 },
  { id: 'message', header: '说明', minWidth: 180 },
  { id: 'operatorName', header: '操作人', width: 110 },
  { id: 'createdAt', header: '核验时间', width: 150 },
]

function statusVariant(status: InboundCheckStatus) {
  if (status === 'MATCHED') {
    return 'default'
  }
  if (status === 'UNAVAILABLE') {
    return 'destructive'
  }
  return 'outline'
}

onMounted(() => {
  void model.loadSenders()
  void model.load()
})
</script>

<template>
  <section class="mail-page">
    <FaPageHeader
      title="入站核验"
      description="输入对方应回信的验证码，插件直连所选地址的 IMAP 收件箱，按时间窗口与发件域/关键词匹配回信；每次核验都写入审计记录，验证码脱敏存储"
      class="mb-0"
    />

    <FaPageMain>
      <section class="mail-panel">
        <h2 class="mail-section-title">发起核验</h2>
        <form class="mail-form" @submit.prevent="model.check">
          <div class="mail-form-grid">
            <FaLabel label="邮箱地址" class="mail-field" required>
              <FaSelect
                :model-value="model.form.addressId"
                :options="model.senderOptions"
                class="w-full"
                placeholder="选择要核验的地址"
                @update:model-value="(value: unknown) => { model.form.addressId = String(value ?? '') }"
              />
            </FaLabel>
            <FaLabel label="验证码" class="mail-field">
              <FaInput v-model="model.form.verificationCode" class="w-full" maxlength="120" placeholder="可留空，只按关键词匹配" />
            </FaLabel>
            <FaLabel label="时间窗口" class="mail-field">
              <FaSelect
                :model-value="model.form.windowMinutes"
                :options="WINDOW_OPTIONS"
                class="w-full"
                @update:model-value="(value: unknown) => { model.form.windowMinutes = String(value ?? '30') }"
              />
              <span class="mail-muted">只在该时间段内收到的来信中查找</span>
            </FaLabel>
          </div>
          <div class="mail-form-actions">
            <FaButton type="submit" :loading="model.checking" :disabled="!model.senderOptions.length">
              <FaIcon name="i-ri:mail-check-line" />
              开始核验
            </FaButton>
          </div>

          <div v-if="model.lastResult" class="mail-check-result">
            <div class="flex flex-wrap items-center gap-2">
              <FaTag :variant="statusVariant(model.lastResult.status)">{{ model.lastResult.statusLabel }}</FaTag>
              <strong class="mail-strong">验证码 {{ model.lastResult.codeMask }}</strong>
            </div>
            <div class="mail-card-meta">
              <span>{{ model.lastResult.message || '无补充说明' }}</span>
              <span v-if="model.lastResult.matchedSubject">
                命中邮件：{{ model.lastResult.matchedSubject }}（来自 {{ model.lastResult.matchedFrom || '未知发件人' }}，
                {{ model.formatTime(model.lastResult.matchedAt) }}）
              </span>
            </div>
          </div>
        </form>
      </section>

      <section class="mail-panel">
        <h2 class="mail-section-title">核验记录</h2>
        <FaResponsiveTable
          v-loading="model.loading"
          row-key="id"
          table-root-class="max-w-full overflow-x-auto rounded-lg"
          table-class="min-w-[1100px]"
          border
          stripe
          :columns="columns"
          :data="model.records"
        >
          <template #toolbar>
            <FaSearchBar class="w-full">
              <form class="mail-filter-grid" @submit.prevent="model.search">
                <FaSelect
                  :model-value="model.filters.addressId"
                  :options="model.senderOptions"
                  class="w-full"
                  placeholder="全部地址"
                  @update:model-value="(value: unknown) => { model.filters.addressId = String(value ?? '') }"
                  @change="model.search"
                />
                <FaSelect
                  :model-value="model.filters.status"
                  :options="CHECK_STATUS_OPTIONS"
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
          </template>

          <template #cell-address="{ row }">
            <div class="mail-cell-stack">
              <strong class="mail-strong break-all">{{ row.original.address || '-' }}</strong>
              <span class="mail-muted">{{ row.original.displayName || '' }}{{ row.original.folder && row.original.folder !== 'INBOX' ? ` · ${row.original.folder}` : '' }}</span>
            </div>
          </template>
          <template #cell-codeMask="{ row }">
            <span class="mail-strong">{{ row.original.codeMask || '-' }}</span>
          </template>
          <template #cell-statusLabel="{ row }">
            <FaTag :variant="statusVariant(row.original.status)">{{ row.original.statusLabel }}</FaTag>
          </template>
          <template #cell-matchedFrom="{ row }">
            <div v-if="row.original.matchedSubject" class="mail-cell-stack">
              <span class="mail-strong">{{ row.original.matchedSubject }}</span>
              <span class="mail-muted break-all">{{ row.original.matchedFrom }} · {{ model.formatTime(row.original.matchedAt) }}</span>
            </div>
            <span v-else class="mail-muted">-</span>
          </template>
          <template #cell-message="{ row }">
            <span class="mail-muted">{{ row.original.message || '-' }}</span>
          </template>
          <template #cell-operatorName="{ row }">{{ row.original.operatorName || '-' }}</template>
          <template #cell-createdAt="{ row }">{{ model.formatTime(row.original.createdAt) }}</template>

          <template #card="{ row }">
            <FaCard class="w-full">
              <div class="flex flex-col gap-2">
                <div class="flex items-start justify-between gap-2">
                  <strong class="mail-strong break-all">{{ row.address || '-' }}</strong>
                  <FaTag :variant="statusVariant(row.status)">{{ row.statusLabel }}</FaTag>
                </div>
                <div class="mail-card-meta">
                  <span>验证码：{{ row.codeMask || '-' }}</span>
                  <span v-if="row.matchedSubject">命中：{{ row.matchedSubject }}（{{ row.matchedFrom }}）</span>
                  <span>{{ row.message || '-' }}</span>
                  <span>操作人：{{ row.operatorName || '-' }} · {{ model.formatTime(row.createdAt) }}</span>
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
      </section>
    </FaPageMain>
  </section>
</template>
