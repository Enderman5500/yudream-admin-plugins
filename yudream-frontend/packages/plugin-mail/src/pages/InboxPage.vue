<script setup lang="ts">
import type { TableColumn } from '@yudream/components'
import type { YuDreamPluginSdk } from '@yudream/plugin-sdk'
import type { InboxSummary, MailBodyType } from '../types'
import {
  FaButton,
  FaCard,
  FaCheckbox,
  FaDrawer,
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
  FaTextarea,
  useFaModal,
} from '@yudream/components'
import { onMounted } from 'vue'
import MailConfigHint from '../components/MailConfigHint.vue'
import MailHtmlPreview from '../components/MailHtmlPreview.vue'
import { useInbox } from '../composables/useInbox'
import { BODY_TYPE_OPTIONS, FETCH_LIMIT_OPTIONS } from '../utils'

const props = defineProps<{ sdk: YuDreamPluginSdk }>()
const model = useInbox(props.sdk)
const modal = useFaModal()

const columns: TableColumn<InboxSummary>[] = [
  { id: 'subject', header: '主题', minWidth: 240, fixed: 'left' },
  { id: 'from', header: '发件人', width: 220 },
  { id: 'to', header: '收件人', width: 200 },
  { id: 'sentAt', header: '时间', width: 150 },
  { id: 'seen', header: '已读', width: 80, align: 'center' },
  { id: 'operation', header: '操作', width: 220, align: 'center', fixed: 'right' },
]

function openDetail(row: InboxSummary) {
  void model.openDetail(row)
}

function confirmDelete(row: InboxSummary) {
  modal.confirm({
    title: '删除邮件',
    content: `确认删除“${row.subject || '（无主题）'}”吗？邮件会在服务器上被永久移除，不经过回收站，无法恢复。`,
    onConfirm: () => model.removeMail(row),
  })
}

function confirmRemoveCurrent() {
  if (!model.detail) {
    return
  }
  const row = model.records.find(item => item.uid === model.detail?.uid)
  const subject = model.detail.subject || '（无主题）'
  modal.confirm({
    title: '删除邮件',
    content: `确认删除“${subject}”吗？邮件会在服务器上被永久移除，不经过回收站，无法恢复。`,
    onConfirm: () => (row ? model.removeMail(row) : Promise.resolve()),
  })
}

const COMPOSE_TITLE: Record<string, string> = {
  reply: '回复邮件',
  'reply-all': '回复全部',
  forward: '转发邮件',
}

/** 当前详情对应的列表行（已读状态以列表信封为准）。 */
function currentRow(): InboxSummary | undefined {
  return model.records.find(item => item.uid === model.detail?.uid)
}

onMounted(model.loadSenders)
</script>

<template>
  <section class="mail-page">
    <FaPageHeader
      title="收件箱"
      description="自动同步所选地址 IMAP 收件夹的邮件到本地缓存并列出最近信封；打开详情才下载正文，IMAP 不可用时自动读取缓存"
      class="mb-0"
    />

    <FaPageMain>
      <MailConfigHint
        v-if="!model.senderOptions.length"
        title="暂无可读的邮箱地址"
        description="请先在「邮箱地址」页新增地址并配置 IMAP 服务器，只有已启用且配置了 IMAP 的地址才能浏览收件箱。"
      />

      <template v-else>
        <FaSearchBar class="w-full">
          <form class="mail-filter-grid" @submit.prevent="model.search">
            <FaSelect
              :model-value="model.query.addressId"
              :options="model.senderOptions"
              class="w-full"
              placeholder="选择邮箱地址"
              @update:model-value="(value: unknown) => { model.query.addressId = String(value ?? '') }"
            />
            <FaSelect
              :model-value="model.query.limit"
              :options="FETCH_LIMIT_OPTIONS"
              class="w-full"
              @update:model-value="(value: unknown) => { model.query.limit = String(value ?? '20') }"
            />
            <FaInput v-model="model.query.fromDomain" clearable class="w-full" placeholder="发件域过滤，如 example.edu.cn" />
            <FaInput v-model="model.query.keyword" clearable class="w-full" placeholder="主题 / 正文关键词" />
            <div class="mail-filter-actions">
              <FaCheckbox
                :model-value="model.autoRefresh"
                label="每 30 秒自动刷新"
                @update:model-value="(value: unknown) => model.toggleAutoRefresh(value === true)"
              />
              <FaButton type="button" variant="outline" @click="model.resetFilters">重置</FaButton>
              <FaButton type="submit" :loading="model.loading">
                <FaIcon name="i-ri:refresh-line" />
                刷新
              </FaButton>
            </div>
          </form>
        </FaSearchBar>

        <div v-if="model.result" class="mail-muted">
          收件夹 {{ model.result.folder }} 共约 {{ model.result.mailboxTotal }} 封；本次同步 {{ model.result.fetched }} 封，
          命中 {{ model.result.total }} 封{{ model.result.truncated ? '（本地缓存已达保留上限）' : '' }}{{ model.result.synced ? '' : '；IMAP 暂不可用，当前显示本地缓存' }}
        </div>

        <FaResponsiveTable
          v-loading="model.loading"
          row-key="uid"
          table-root-class="max-w-full overflow-x-auto rounded-lg"
          table-class="min-w-[1000px]"
          border
          stripe
          :columns="columns"
          :data="model.records"
        >
          <template #cell-subject="{ row }">
            <strong class="mail-strong">{{ row.original.subject || '（无主题）' }}</strong>
          </template>
          <template #cell-from="{ row }">
            <span class="mail-muted break-all">{{ row.original.from }}</span>
          </template>
          <template #cell-to="{ row }">
            <span class="mail-muted break-all">{{ row.original.to.join('、') }}</span>
          </template>
          <template #cell-sentAt="{ row }">{{ model.formatTime(row.original.sentAt) }}</template>
          <template #cell-size="{ row }">{{ model.formatBytes(row.original.size) }}</template>
          <template #cell-seen="{ row }">
            <FaTag :variant="row.original.seen ? 'secondary' : 'default'">{{ row.original.seen ? '已读' : '未读' }}</FaTag>
          </template>
          <template #cell-operation="{ row }">
            <div class="mail-row-actions">
              <FaButton size="sm" variant="outline" @click="openDetail(row.original)">查看</FaButton>
              <FaButton
                size="sm"
                variant="ghost"
                :loading="model.flagging === row.original.uid"
                @click="model.toggleSeen(row.original)"
              >
                {{ row.original.seen ? '标为未读' : '标为已读' }}
              </FaButton>
              <FaButton size="sm" variant="ghost" class="mail-danger" @click="confirmDelete(row.original)">删除</FaButton>
            </div>
          </template>

          <template #card="{ row }">
            <FaCard class="w-full">
              <div class="flex flex-col gap-2">
                <div class="flex items-start justify-between gap-2">
                  <strong class="mail-strong break-all">{{ row.subject || '（无主题）' }}</strong>
                  <FaTag :variant="row.seen ? 'secondary' : 'default'">{{ row.seen ? '已读' : '未读' }}</FaTag>
                </div>
                <div class="mail-card-meta">
                  <span>发件人：{{ row.from }}</span>
                  <span>时间：{{ model.formatTime(row.sentAt) }} · {{ model.formatBytes(row.size) }}</span>
                </div>
                <div class="mail-card-actions">
                  <FaButton size="sm" variant="outline" @click="openDetail(row)">查看详情</FaButton>
                  <FaButton size="sm" variant="ghost" @click="model.toggleSeen(row)">
                    {{ row.seen ? '标为未读' : '标为已读' }}
                  </FaButton>
                  <FaButton size="sm" variant="ghost" class="mail-danger" @click="confirmDelete(row)">删除</FaButton>
                </div>
              </div>
            </FaCard>
          </template>
        </FaResponsiveTable>

        <FaPagination
          v-model:page="model.query.page"
          v-model:size="model.query.size"
          :total="model.result?.total ?? 0"
          class="mt-3"
          @page-change="model.onPageChange"
          @size-change="model.onSizeChange"
        />
      </template>
    </FaPageMain>

    <FaDrawer
      :model-value="!!model.detail"
      title="邮件详情"
      side="right"
      content-class="sm:max-w-[760px]"
      :footer="false"
      :loading="model.detailLoading"
      @update:model-value="(value: unknown) => { if (!value) model.closeDetail() }"
    >
      <div v-if="model.detail" class="mail-detail">
        <section class="mail-detail-actions">
          <div class="mail-detail-actions-row">
            <FaButton size="sm" variant="outline" @click="model.openCompose('reply')">
              <FaIcon name="i-ri:reply-line" />
              回复
            </FaButton>
            <FaButton size="sm" variant="outline" @click="model.openCompose('reply-all')">
              <FaIcon name="i-ri:reply-all-line" />
              回复全部
            </FaButton>
            <FaButton size="sm" variant="outline" @click="model.openCompose('forward')">
              <FaIcon name="i-ri:forward-line" />
              转发
            </FaButton>
            <FaButton
              v-if="currentRow()"
              size="sm"
              variant="ghost"
              :loading="model.flagging === model.detail.uid"
              @click="model.toggleSeen(currentRow()!)"
            >
              {{ currentRow()!.seen ? '标为未读' : '标为已读' }}
            </FaButton>
            <FaButton size="sm" variant="ghost" class="mail-danger" @click="confirmRemoveCurrent">删除</FaButton>
          </div>
          <p v-if="!model.composeReady" class="mail-muted">
            当前地址未配置 SMTP，回复/转发不可用；可在「邮箱地址」页补全发信配置。
          </p>
        </section>

        <section class="mail-detail-head">
          <h3 class="mail-strong text-base">{{ model.detail.subject || '（无主题）' }}</h3>
          <dl class="mail-card-meta">
            <div><dt>发件人</dt><dd>{{ model.detail.from }}</dd></div>
            <div><dt>收件人</dt><dd>{{ model.detail.to.join('、') || '-' }}</dd></div>
            <div v-if="model.detail.cc.length"><dt>抄送</dt><dd>{{ model.detail.cc.join('、') }}</dd></div>
            <div><dt>时间</dt><dd>{{ model.formatTime(model.detail.sentAt) }}</dd></div>
            <div><dt>大小</dt><dd>{{ model.formatBytes(model.detail.size) }}</dd></div>
          </dl>
        </section>

        <section v-if="model.detail.attachments.length" class="mail-detail-attachments">
          <h4>附件（{{ model.detail.attachments.length }}）</h4>
          <ul class="mail-tag-list">
            <li v-for="item in model.detail.attachments" :key="item.name" class="mail-muted">
              <FaIcon name="i-ri:file-3-line" />
              {{ item.name }}（{{ model.formatBytes(item.size) }}）
            </li>
          </ul>
          <p class="mail-muted">插件只读取附件元数据，不下载附件内容。</p>
        </section>

        <template v-if="model.detail.html">
          <MailHtmlPreview :html="model.detail.html" />
          <p v-if="model.detail.truncated" class="mail-muted">正文过长，仅显示前一部分。</p>
        </template>
        <pre v-else class="mail-body-preview">{{ model.detail.text || '（无正文）' }}</pre>
      </div>
    </FaDrawer>

    <FaDrawer
      :model-value="!!model.compose"
      :title="model.compose ? COMPOSE_TITLE[model.compose.mode] : '写邮件'"
      side="right"
      content-class="sm:max-w-[720px]"
      :footer="false"
      @update:model-value="(value: unknown) => { if (!value) model.closeCompose() }"
    >
      <form v-if="model.compose" class="mail-compose" @submit.prevent="model.sendCompose">
        <FaLabel label="收件人" class="mail-field" required>
          <FaTextarea
            v-model="model.compose.to"
            class="w-full"
            input-class="min-h-[56px]"
            placeholder="多人用逗号或换行分隔"
          />
        </FaLabel>
        <FaLabel label="抄送" class="mail-field">
          <FaTextarea
            v-model="model.compose.cc"
            class="w-full"
            input-class="min-h-[56px]"
            placeholder="可选，多人用逗号或换行分隔"
          />
        </FaLabel>
        <FaLabel label="主题" class="mail-field" required>
          <FaInput v-model="model.compose.subject" class="w-full" maxlength="200" placeholder="邮件主题" />
        </FaLabel>
        <FaLabel label="正文格式" class="mail-field">
          <FaSelect
            :model-value="model.compose.bodyType"
            :options="BODY_TYPE_OPTIONS"
            class="w-full"
            @update:model-value="(value: unknown) => { if (model.compose) model.compose.bodyType = (value ?? 'TEXT') as MailBodyType }"
          />
        </FaLabel>
        <FaLabel label="正文" class="mail-field" required>
          <FaTextarea
            v-model="model.compose.body"
            class="w-full"
            input-class="min-h-[260px]"
            placeholder="邮件正文"
          />
        </FaLabel>
        <p class="mail-muted">
          以当前收件地址身份发送；回复会带上 In-Reply-To / References 线程头，便于对方邮件客户端归组。
        </p>
        <div class="mail-compose-actions">
          <FaButton type="button" variant="outline" @click="model.closeCompose">取消</FaButton>
          <FaButton type="submit" :loading="model.sending">
            <FaIcon name="i-ri:send-plane-line" />
            发送
          </FaButton>
        </div>
      </form>
    </FaDrawer>
  </section>
</template>

<style scoped>
.mail-detail-actions {
  display: grid;
  gap: 6px;
  padding-bottom: 12px;
  border-bottom: 1px solid var(--color-border-2);
}

.mail-detail-actions-row {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.mail-row-actions {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 4px;
}

.mail-danger {
  color: var(--color-danger-6);
}

.mail-compose {
  display: grid;
  gap: 14px;
}

.mail-compose-actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  padding-top: 4px;
}

.mail-detail-head dl {
  display: grid;
  gap: 4px;
  margin: 0;
}

.mail-detail-head dt,
.mail-detail-head dd {
  display: inline;
}

.mail-detail-head dt {
  margin-right: 6px;
  color: var(--color-text-3);
}

.mail-detail-attachments ul {
  margin: 0;
  padding: 0;
  list-style: none;
}
</style>
