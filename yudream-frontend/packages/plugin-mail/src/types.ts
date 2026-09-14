export type MailPurpose = 'NOTIFICATION' | 'VERIFICATION' | 'SUPPORT' | 'MARKETING' | 'OTHER'

/** 传输加密方式。 */
export type MailSecurity = 'SSL' | 'STARTTLS' | 'NONE'

export interface SmtpConfig {
  host?: string | null
  port: number
  security: MailSecurity
  securityLabel: string
  username?: string | null
}

export interface ImapConfig {
  host?: string | null
  port: number
  security: MailSecurity
  securityLabel: string
  username?: string | null
  folder: string
  fetchLimit: number
  allowedFromDomains: string[]
  requiredKeywords: string[]
}

/** 邮箱地址（收发身份 + 收发配置，不含密码）。 */
export interface MailAddress {
  id: string
  address: string
  displayName?: string | null
  purpose: MailPurpose
  purposeLabel: string
  enabled: boolean
  defaultSender: boolean
  remark: string
  smtp: SmtpConfig
  imap: ImapConfig
  smtpConfigured: boolean
  imapConfigured: boolean
  createdAt: number
  updatedAt: number
}

/** 地址详情：额外带“密码是否已设置”（永不返回密码本身）。 */
export interface MailAddressDetail extends MailAddress {
  smtpPasswordSet: boolean
  imapPasswordSet: boolean
}

export interface MailAddressOption {
  id: string
  address: string
  displayName?: string | null
  enabled: boolean
  defaultSender: boolean
  smtpConfigured: boolean
  imapConfigured: boolean
  folder: string
}

/** 地址编辑表单（扁平字段便于绑定，提交时组装成后端嵌套结构）。 */
export interface MailAddressForm {
  address: string
  displayName: string
  purpose: MailPurpose
  remark: string
  enabled: boolean
  smtpHost: string
  smtpPort: string
  smtpSecurity: MailSecurity
  smtpUsername: string
  smtpPassword: string
  imapHost: string
  imapPort: string
  imapSecurity: MailSecurity
  imapUsername: string
  imapPassword: string
  imapFolder: string
  imapFetchLimit: string
  imapDomains: string
  imapKeywords: string
}

export interface MailAddressFilters {
  keyword: string
  enabled: '' | 'true' | 'false'
}

export interface TransportTestResult {
  ok: boolean
  message: string
  messageCount?: number | null
}

/** 收件箱列表行（持久化副本的信封，不含正文）。 */
export interface InboxSummary {
  id: string
  addressId: string
  folder: string
  uid: string
  subject: string
  from: string
  to: string[]
  sentAt: number
  size: number
  seen: boolean
  bodyFetched: boolean
}

export interface InboxAttachment {
  name: string
  contentType: string
  size: number
}

/** 邮件详情 = 列表信封 + 缓存正文与附件元数据。 */
export interface InboxDetail extends InboxSummary {
  cc: string[]
  text: string
  html: string
  truncated: boolean
  attachments: InboxAttachment[]
  messageId: string
}

/** 回复/转发组合模式。 */
export type ComposeMode = 'reply' | 'reply-all' | 'forward'

/** 收件箱回复/转发草稿（发送走管理端发信端点，带线程头）。 */
export interface InboxCompose {
  mode: ComposeMode
  to: string
  cc: string
  subject: string
  bodyType: MailBodyType
  body: string
  inReplyTo: string
  references: string
}

/** 收件箱一次「同步 + 分页查询」的结果。 */
export interface InboxResult {
  records: InboxSummary[]
  total: number
  mailboxTotal: number
  fetched: number
  folder: string
  /** false 表示 IMAP 不可用，本次结果来自本地缓存副本。 */
  synced: boolean
  truncated: boolean
}

export interface InboxQuery {
  addressId: string
  folder: string
  limit: string
  page: number
  size: number
  fromDomain: string
  keyword: string
}

export type MailBodyType = 'TEXT' | 'HTML'

export type MailRecordStatus = 'SENT' | 'FAILED'

export type MailRecordSource = 'ADMIN' | 'USER'

/** 发信记录（审计日志，只读）。 */
export interface MailRecord {
  id: string
  addressId: string
  fromAddress: string
  smtpHost: string
  to: string[]
  cc: string[]
  bcc: string[]
  subject: string
  bodyType: MailBodyType
  bodyPreview: string
  status: MailRecordStatus
  statusLabel: string
  errorMessage: string
  source: MailRecordSource
  sourceLabel: string
  operatorUserId: string
  operatorName: string
  createdAt: number
  body?: string
}

export interface MailRecordFilters {
  keyword: string
  addressId: string
  status: '' | MailRecordStatus
}

export type InboundCheckStatus = 'MATCHED' | 'NOT_FOUND' | 'UNAVAILABLE' | 'PENDING'

/** 入站核验记录（审计日志，只读；验证码脱敏存储）。 */
export interface InboundCheckRecord {
  id: string
  addressId: string
  address?: string | null
  displayName?: string | null
  folder: string
  codeMask: string
  status: InboundCheckStatus
  statusLabel: string
  message: string
  matchedUid: string
  matchedSubject: string
  matchedFrom: string
  matchedAt: number
  operatorUserId: string
  operatorName: string
  createdAt: number
}

/** 发信表单（收件人/抄送/密送以文本维护，提交前拆分）。 */
export interface MailSendForm {
  addressId: string
  to: string
  cc: string
  bcc: string
  subject: string
  bodyType: MailBodyType
  body: string
}

export interface PageResult<T> {
  records: T[]
  total: number
}
