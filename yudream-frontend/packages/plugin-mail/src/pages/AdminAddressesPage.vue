<script setup lang="ts">
import type { TableColumn } from '@yudream/components'
import type { YuDreamPluginSdk } from '@yudream/plugin-sdk'
import type { MailAddress, MailPurpose, MailSecurity } from '../types'
import {
  FaButton,
  FaCard,
  FaIcon,
  FaDrawer,
  FaInput,
  FaLabel,
  FaPageHeader,
  FaPageMain,
  FaPagination,
  FaResponsiveTable,
  FaSearchBar,
  FaSelect,
  FaSwitch,
  FaTag,
  FaTextarea,
  useFaModal,
} from '@yudream/components'
import { onMounted, ref } from 'vue'
import { useMailAddresses } from '../composables/useMailAddresses'
import { configStateText, ENABLED_OPTIONS, PURPOSE_OPTIONS, SECURITY_OPTIONS, SECURITY_PORT_HINT } from '../utils'

const props = defineProps<{ sdk: YuDreamPluginSdk }>()
const model = useMailAddresses(props.sdk)
const confirm = useFaModal()
const formOpen = ref(false)

const columns: TableColumn<MailAddress>[] = [
  { id: 'address', header: '邮箱地址', width: 260, fixed: 'left' },
  { id: 'purposeLabel', header: '用途', width: 90 },
  { id: 'configState', header: '收发配置', width: 150 },
  { id: 'enabled', header: '状态', width: 90, align: 'center' },
  { id: 'defaultSender', header: '默认', width: 90, align: 'center' },
  { id: 'updatedAt', header: '更新时间', width: 150 },
  { id: 'operation', header: '操作', width: 330, align: 'center', fixed: 'right' },
]

function openCreate() {
  model.openCreate()
  formOpen.value = true
}

function openEdit(row: MailAddress) {
  void model.openEdit(row).then(() => {
    formOpen.value = true
  })
}

async function save() {
  if (await model.save()) {
    formOpen.value = false
  }
}

function confirmDelete(row: MailAddress) {
  confirm.confirm({
    title: '删除邮箱地址',
    content: `确认删除“${row.address}”吗？该地址的发信记录与核验记录会保留，但无法再用于收发信。`,
    onConfirm: () => model.remove(row),
  })
}

function confirmSetDefault(row: MailAddress) {
  confirm.confirm({
    title: '设为默认发件地址',
    content: `将“${row.address}”设为默认发件地址后，发信页会优先选中它。`,
    onConfirm: () => model.setDefault(row),
  })
}

onMounted(model.load)
</script>

<template>
  <section class="mail-page">
    <FaPageHeader title="邮箱地址" :description="`共 ${model.pagination.total} 个地址，当前页启用 ${model.records.filter(item => item.enabled).length} 个`" class="mb-0">
      <FaButton @click="openCreate">
        <FaIcon name="i-ri:add-line" />
        新增地址
      </FaButton>
    </FaPageHeader>

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
          <FaSearchBar class="w-full">
            <form class="mail-filter-grid" @submit.prevent="model.search">
              <FaInput v-model="model.filters.keyword" clearable class="w-full" placeholder="地址 / 显示名 / 备注" @clear="model.search" />
              <FaSelect
                :model-value="model.filters.enabled"
                :options="ENABLED_OPTIONS"
                class="w-full"
                @update:model-value="(value: unknown) => { model.filters.enabled = (value ?? '') as typeof model.filters.enabled }"
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
            <strong class="mail-strong">{{ row.original.address }}</strong>
            <span class="mail-muted">{{ row.original.displayName || '未设置显示名' }}{{ row.original.remark ? ` · ${row.original.remark}` : '' }}</span>
          </div>
        </template>
        <template #cell-purposeLabel="{ row }">
          <FaTag variant="secondary">{{ row.original.purposeLabel }}</FaTag>
        </template>
        <template #cell-configState="{ row }">
          <div class="mail-tag-list">
            <FaTag :variant="row.original.smtpConfigured ? 'default' : 'outline'">SMTP{{ row.original.smtpConfigured ? ' ✓' : ' 未配' }}</FaTag>
            <FaTag :variant="row.original.imapConfigured ? 'default' : 'outline'">IMAP{{ row.original.imapConfigured ? ' ✓' : ' 未配' }}</FaTag>
          </div>
        </template>
        <template #cell-enabled="{ row }">
          <FaTag :variant="row.original.enabled ? 'default' : 'outline'">{{ row.original.enabled ? '已启用' : '已停用' }}</FaTag>
        </template>
        <template #cell-defaultSender="{ row }">
          <FaTag v-if="row.original.defaultSender" variant="default" icon="i-ri:star-fill">默认</FaTag>
          <span v-else class="mail-muted">-</span>
        </template>
        <template #cell-updatedAt="{ row }">{{ model.formatTime(row.original.updatedAt) }}</template>
        <template #cell-operation="{ row }">
          <div class="mail-row-actions">
            <FaButton
              v-if="!row.original.defaultSender"
              size="sm"
              variant="outline"
              :disabled="!row.original.enabled || model.switchingId === row.original.id"
              @click="confirmSetDefault(row.original)"
            >
              设为默认
            </FaButton>
            <FaButton
              size="sm"
              variant="outline"
              :loading="model.switchingId === row.original.id"
              :disabled="row.original.defaultSender"
              @click="model.toggleEnabled(row.original, !row.original.enabled)"
            >
              {{ row.original.enabled ? '停用' : '启用' }}
            </FaButton>
            <FaButton size="sm" variant="outline" @click="openEdit(row.original)">编辑</FaButton>
            <FaButton
              size="sm"
              variant="destructive"
              :loading="model.deletingId === row.original.id"
              :disabled="row.original.defaultSender"
              @click="confirmDelete(row.original)"
            >
              删除
            </FaButton>
          </div>
        </template>

        <template #card="{ row }">
          <FaCard class="w-full">
            <div class="flex flex-col gap-3">
              <div class="flex items-start justify-between gap-2">
                <div class="mail-cell-stack min-w-0">
                  <strong class="mail-strong break-all">{{ row.address }}</strong>
                  <span class="mail-muted">{{ row.displayName || '未设置显示名' }}</span>
                </div>
                <div class="flex shrink-0 flex-col items-end gap-1">
                  <FaTag :variant="row.enabled ? 'default' : 'outline'">{{ row.enabled ? '已启用' : '已停用' }}</FaTag>
                  <FaTag v-if="row.defaultSender" variant="secondary">默认发件</FaTag>
                </div>
              </div>
              <div class="mail-tag-list">
                <FaTag :variant="row.smtpConfigured ? 'default' : 'outline'">SMTP{{ row.smtpConfigured ? ' ✓' : ' 未配' }}</FaTag>
                <FaTag :variant="row.imapConfigured ? 'default' : 'outline'">IMAP{{ row.imapConfigured ? ' ✓' : ' 未配' }}</FaTag>
                <FaTag variant="secondary">{{ row.purposeLabel }}</FaTag>
              </div>
              <div class="mail-card-meta">
                <span>收发状态：{{ configStateText(row.smtpConfigured, row.imapConfigured) }}</span>
                <span>更新：{{ model.formatTime(row.updatedAt) }}</span>
                <span v-if="row.remark">备注：{{ row.remark }}</span>
              </div>
              <div class="mail-card-actions border-t pt-3">
                <FaButton
                  v-if="!row.defaultSender"
                  size="sm"
                  variant="outline"
                  :disabled="!row.enabled || model.switchingId === row.id"
                  @click="confirmSetDefault(row)"
                >
                  设为默认
                </FaButton>
                <FaButton
                  size="sm"
                  variant="outline"
                  :loading="model.switchingId === row.id"
                  :disabled="row.defaultSender"
                  @click="model.toggleEnabled(row, !row.enabled)"
                >
                  {{ row.enabled ? '停用' : '启用' }}
                </FaButton>
                <FaButton size="sm" variant="outline" @click="openEdit(row)">编辑</FaButton>
                <FaButton
                  size="sm"
                  variant="destructive"
                  :loading="model.deletingId === row.id"
                  :disabled="row.defaultSender"
                  @click="confirmDelete(row)"
                >
                  删除
                </FaButton>
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
      v-model="formOpen"
      :title="model.editingId ? '编辑邮箱地址' : '新增邮箱地址'"
      description="密码只上行不下行：保存后由宿主密钥库加密存储，任何接口都不会回传"
      side="right"
      content-class="sm:max-w-[720px]"
      :footer="false"
      :close-on-press-escape="!model.saving"
      :close-on-click-overlay="!model.saving"
      :loading="model.detailLoading"
      @closed="model.resetForm"
    >
      <form class="mail-form" @submit.prevent="save">
        <section class="mail-drawer-section">
          <h3 class="mail-drawer-title">基本信息</h3>
          <FaLabel label="邮箱地址" class="mail-field" required>
            <FaInput v-model="model.form.address" class="w-full" placeholder="ops@example.com" maxlength="160" />
          </FaLabel>
          <FaLabel label="显示名（发件人名称）" class="mail-field">
            <FaInput v-model="model.form.displayName" class="w-full" placeholder="例如：对外事务中心" maxlength="60" />
          </FaLabel>
          <FaLabel label="用途" class="mail-field">
            <FaSelect
              :model-value="model.form.purpose"
              :options="PURPOSE_OPTIONS"
              class="w-full"
              @update:model-value="(value: unknown) => { model.form.purpose = (value ?? 'OTHER') as MailPurpose }"
            />
          </FaLabel>
          <FaLabel label="备注" class="mail-field">
            <FaTextarea v-model="model.form.remark" class="w-full" input-class="min-h-[72px]" placeholder="用途说明、负责人等，便于后续识别" />
          </FaLabel>
          <div class="mail-switch-row">
            <div class="mail-cell-stack">
              <strong>启用该地址</strong>
              <span class="mail-muted">停用的地址不能用于发信，也不会出现在发件地址选择器中</span>
            </div>
            <FaSwitch v-model="model.form.enabled" />
          </div>
        </section>

        <section class="mail-drawer-section">
          <h3 class="mail-drawer-title">SMTP 发信配置</h3>
          <div class="mail-form-grid">
            <FaLabel label="服务器地址" class="mail-field">
              <FaInput v-model="model.form.smtpHost" class="w-full" placeholder="smtp.example.com" />
            </FaLabel>
            <FaLabel label="端口" class="mail-field">
              <FaInput v-model="model.form.smtpPort" class="w-full" placeholder="留空按加密方式推断" inputmode="numeric" />
            </FaLabel>
          </div>
          <FaLabel label="加密方式" class="mail-field">
            <FaSelect
              :model-value="model.form.smtpSecurity"
              :options="SECURITY_OPTIONS"
              class="w-full"
              @update:model-value="(value: unknown) => { model.form.smtpSecurity = (value ?? 'SSL') as MailSecurity }"
            />
            <span class="mail-muted">{{ SECURITY_PORT_HINT[model.form.smtpSecurity] }}</span>
          </FaLabel>
          <div class="mail-form-grid">
            <FaLabel label="用户名" class="mail-field">
              <FaInput v-model="model.form.smtpUsername" class="w-full" placeholder="通常与邮箱地址相同" />
            </FaLabel>
            <FaLabel label="密码 / 授权码" class="mail-field">
              <FaInput
                v-model="model.form.smtpPassword"
                type="password"
                class="w-full"
                :placeholder="model.editingId && model.smtpPasswordSet ? '已设置，留空保持不变' : 'SMTP 登录密码或授权码'"
              />
            </FaLabel>
          </div>
          <div class="mail-test-row">
            <FaButton
              type="button"
              size="sm"
              variant="outline"
              :loading="model.testing === 'smtp'"
              :disabled="!model.editingId"
              @click="model.testSmtp"
            >
              <FaIcon name="i-ri:plug-line" />
              测试 SMTP 连接
            </FaButton>
            <span v-if="model.smtpTest" class="mail-test-result" :class="model.smtpTestOk ? 'is-ok' : 'is-fail'">{{ model.smtpTest }}</span>
            <span v-else-if="!model.editingId" class="mail-muted">保存后才能测试连接</span>
          </div>
        </section>

        <section class="mail-drawer-section">
          <h3 class="mail-drawer-title">IMAP 收信配置</h3>
          <div class="mail-form-grid">
            <FaLabel label="服务器地址" class="mail-field">
              <FaInput v-model="model.form.imapHost" class="w-full" placeholder="imap.example.com" />
            </FaLabel>
            <FaLabel label="端口" class="mail-field">
              <FaInput v-model="model.form.imapPort" class="w-full" placeholder="留空按加密方式推断" inputmode="numeric" />
            </FaLabel>
          </div>
          <FaLabel label="加密方式" class="mail-field">
            <FaSelect
              :model-value="model.form.imapSecurity"
              :options="SECURITY_OPTIONS"
              class="w-full"
              @update:model-value="(value: unknown) => { model.form.imapSecurity = (value ?? 'SSL') as MailSecurity }"
            />
            <span class="mail-muted">{{ SECURITY_PORT_HINT[model.form.imapSecurity] }}</span>
          </FaLabel>
          <div class="mail-form-grid">
            <FaLabel label="用户名" class="mail-field">
              <FaInput v-model="model.form.imapUsername" class="w-full" placeholder="通常与邮箱地址相同" />
            </FaLabel>
            <FaLabel label="密码 / 授权码" class="mail-field">
              <FaInput
                v-model="model.form.imapPassword"
                type="password"
                class="w-full"
                :placeholder="model.editingId && model.imapPasswordSet ? '已设置，留空保持不变' : 'IMAP 登录密码或授权码'"
              />
            </FaLabel>
          </div>
          <div class="mail-form-grid">
            <FaLabel label="收件夹" class="mail-field">
              <div class="flex gap-2">
                <FaInput v-model="model.form.imapFolder" class="w-full" placeholder="INBOX" list="mail-folder-options" />
              </div>
              <datalist id="mail-folder-options">
                <option v-for="folder in model.folderOptions" :key="folder" :value="folder" />
              </datalist>
              <FaButton
                type="button"
                size="sm"
                variant="outline"
                class="mt-1 self-start"
                :loading="model.loadingFolders"
                :disabled="!model.editingId"
                @click="model.loadFolders"
              >
                读取收件夹列表
              </FaButton>
              <span v-if="!model.editingId" class="mail-muted">保存后才能读取真实收件夹</span>
            </FaLabel>
            <FaLabel label="单次拉取上限" class="mail-field">
              <FaInput v-model="model.form.imapFetchLimit" class="w-full" inputmode="numeric" placeholder="20" />
              <span class="mail-muted">收件箱与入站核验单次最多读取的邮件数（10-50）</span>
            </FaLabel>
          </div>
          <FaLabel label="允许的发件域" class="mail-field">
            <FaTextarea v-model="model.form.imapDomains" class="w-full" input-class="min-h-[64px]" placeholder="每行一个域名，例如：&#10;example.edu.cn&#10;gov.cn" />
            <span class="mail-muted">入站核验只匹配这些域名的来信；留空表示不限制</span>
          </FaLabel>
          <FaLabel label="必须包含的关键词" class="mail-field">
            <FaTextarea v-model="model.form.imapKeywords" class="w-full" input-class="min-h-[64px]" placeholder="每行一个关键词，例如：&#10;验证码&#10;回执" />
            <span class="mail-muted">入站核验只匹配主题或正文包含任一关键词的来信；留空表示不限制</span>
          </FaLabel>
          <div class="mail-test-row">
            <FaButton
              type="button"
              size="sm"
              variant="outline"
              :loading="model.testing === 'imap'"
              :disabled="!model.editingId"
              @click="model.testImap"
            >
              <FaIcon name="i-ri:plug-line" />
              测试 IMAP 连接
            </FaButton>
            <span v-if="model.imapTest" class="mail-test-result" :class="model.imapTestOk ? 'is-ok' : 'is-fail'">{{ model.imapTest }}</span>
          </div>
        </section>

        <div class="mail-form-actions">
          <FaButton type="button" variant="outline" :disabled="model.saving" @click="formOpen = false">取消</FaButton>
          <FaButton type="submit" :loading="model.saving">
            <FaIcon name="i-ri:save-3-line" />
            保存
          </FaButton>
        </div>
      </form>
    </FaDrawer>
  </section>
</template>
