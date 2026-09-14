import type { InboundCheckStatus, MailAddressOption, MailPurpose, MailSecurity } from './types'

/** 时间格式化：0 / 空值统一显示 -，避免出现 undefined。 */
export function formatTime(value?: number | null): string {
  if (!value) {
    return '-'
  }
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) {
    return '-'
  }
  const pad = (input: number) => String(input).padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${pad(date.getHours())}:${pad(date.getMinutes())}`
}

/** 邮件大小格式化。 */
export function formatBytes(value?: number | null): string {
  if (!value || value <= 0) {
    return '-'
  }
  if (value < 1024) {
    return `${value} B`
  }
  if (value < 1024 * 1024) {
    return `${(value / 1024).toFixed(1)} KB`
  }
  return `${(value / 1024 / 1024).toFixed(1)} MB`
}

/** 收件人文本拆分：支持逗号、分号、空格与换行批量粘贴。 */
export function splitRecipients(raw: string): string[] {
  return (raw || '')
    .split(/[\s,;，；]+/)
    .map(item => item.trim())
    .filter(Boolean)
}

/** 列表文本拆分为去重数组（用于发件域与关键词）。 */
export function splitList(raw: string): string[] {
  const seen = new Set<string>()
  const result: string[] = []
  for (const item of (raw || '').split(/[\s,;，；\n]+/)) {
    const value = item.trim()
    if (value && !seen.has(value)) {
      seen.add(value)
      result.push(value)
    }
  }
  return result
}

/** 数组回填为多行文本，便于在表单里逐行维护。 */
export function joinList(values?: string[] | null): string {
  return (values || []).join('\n')
}

export const PURPOSE_OPTIONS: Array<{ label: string, value: MailPurpose }> = [
  { label: '通知', value: 'NOTIFICATION' },
  { label: '验证码', value: 'VERIFICATION' },
  { label: '客服', value: 'SUPPORT' },
  { label: '推广', value: 'MARKETING' },
  { label: '其他', value: 'OTHER' },
]

export const SECURITY_OPTIONS: Array<{ label: string, value: MailSecurity }> = [
  { label: 'SSL/TLS（推荐）', value: 'SSL' },
  { label: 'STARTTLS', value: 'STARTTLS' },
  { label: '不加密', value: 'NONE' },
]

/** 端口留空时后端按加密方式推断默认端口，这里给出提示文案。 */
export const SECURITY_PORT_HINT: Record<MailSecurity, string> = {
  SSL: 'SSL/TLS 默认端口：SMTP 465 / IMAP 993',
  STARTTLS: 'STARTTLS 默认端口：SMTP 587 / IMAP 143',
  NONE: '不加密默认端口：SMTP 25 / IMAP 143（不建议）',
}

export const BODY_TYPE_OPTIONS = [
  { label: '纯文本', value: 'TEXT' },
  { label: 'HTML', value: 'HTML' },
]

export const ENABLED_OPTIONS = [
  { label: '全部状态', value: '' },
  { label: '已启用', value: 'true' },
  { label: '已停用', value: 'false' },
]

export const RECORD_STATUS_OPTIONS = [
  { label: '全部结果', value: '' },
  { label: '已发送', value: 'SENT' },
  { label: '发送失败', value: 'FAILED' },
]

export const CHECK_STATUS_OPTIONS: Array<{ label: string, value: '' | InboundCheckStatus }> = [
  { label: '全部结果', value: '' },
  { label: '核验通过', value: 'MATCHED' },
  { label: '未匹配', value: 'NOT_FOUND' },
  { label: '不可用', value: 'UNAVAILABLE' },
]

export const FETCH_LIMIT_OPTIONS = [10, 20, 30, 50].map(value => ({
  label: `${value} 封`,
  value: String(value),
}))

export const WINDOW_OPTIONS = [5, 10, 15, 30, 60, 120, 240, 1440].map(minutes => ({
  label: minutes >= 60 ? `${minutes / 60} 小时` : `${minutes} 分钟`,
  value: String(minutes),
}))

/** 地址展示文案：显示名 + 地址，缺显示名时只显示地址。 */
export function addressLabel(option: Pick<MailAddressOption, 'address' | 'displayName'>): string {
  return option.displayName ? `${option.displayName} <${option.address}>` : option.address
}

/** 从「Name <a@b.c>」格式里提取纯邮箱地址；无尖括号时原样返回。 */
export function extractAddress(raw: string): string {
  const match = /<([^>]+)>/.exec(raw || '')
  return (match ? match[1] : (raw || '').trim()).trim()
}

/** 主题加前缀（Re:/Fwd:），已有前缀时不重复。 */
export function withSubjectPrefix(subject: string, prefix: string): string {
  const base = (subject || '').trim()
  if (!base) {
    return `${prefix} （无主题）`
  }
  return base.startsWith(prefix) ? base : `${prefix} ${base}`
}

/** 构造引用正文头（纯文本）。 */
export function quoteHeader(kind: '原始邮件' | '转发邮件', from: string, to: string[], cc: string[], sentAt: number): string {
  const lines = [
    `---- ${kind} ----`,
    `发件人：${from || '-'}`,
    `收件人：${(to || []).join('、') || '-'}`,
  ]
  if ((cc || []).length) {
    lines.push(`抄送：${cc.join('、')}`)
  }
  lines.push(`时间：${formatTime(sentAt)}`, '')
  return lines.join('\n')
}

/** 纯文本引用：每行加 "> " 前缀。 */
export function quoteText(text: string): string {
  return (text || '')
    .split('\n')
    .map(line => `> ${line}`)
    .join('\n')
}

/** 收发配置完整度文案。 */
export function configStateText(smtpConfigured: boolean, imapConfigured: boolean): string {
  if (smtpConfigured && imapConfigured) {
    return '收发已配置'
  }
  if (smtpConfigured) {
    return '仅发信可用'
  }
  if (imapConfigured) {
    return '仅收信可用'
  }
  return '未配置收发'
}
