import { defineYuDreamPlugin } from '@yudream/plugin-sdk'
import 'virtual:uno.css'
import './styles.css'
import AdminAddressesPage from './pages/AdminAddressesPage.vue'
import AdminInboundChecksPage from './pages/AdminInboundChecksPage.vue'
import AdminMessagesPage from './pages/AdminMessagesPage.vue'
import AdminSendPage from './pages/AdminSendPage.vue'
import InboxPage from './pages/InboxPage.vue'

export const AdminAddresses = AdminAddressesPage
export const AdminSend = AdminSendPage
export const AdminInbox = InboxPage
export const AdminMessages = AdminMessagesPage
export const AdminInboundChecks = AdminInboundChecksPage

export const routes = {
  AdminAddresses,
  AdminSend,
  AdminInbox,
  AdminMessages,
  AdminInboundChecks,
  'mail/AdminAddresses': AdminAddresses,
  'mail/AdminSend': AdminSend,
  'mail/AdminInbox': InboxPage,
  'mail/AdminMessages': AdminMessages,
  'mail/AdminInboundChecks': AdminInboundChecks,
}

export default defineYuDreamPlugin({
  routes,
  default: AdminAddresses,
})
