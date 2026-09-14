import { defineYuDreamPlugin } from '@yudream/plugin-sdk'
import 'virtual:uno.css'
import './styles.css'
import AdminBindingsPage from './pages/AdminBindingsPage.vue'
import MyInviteCodesPage from './pages/MyInviteCodesPage.vue'

export const AdminBindings = AdminBindingsPage
export const MyInviteCodes = MyInviteCodesPage

export const routes = {
  AdminBindings,
  MyInviteCodes,
  'invite/AdminBindings': AdminBindings,
  'invite/MyInviteCodes': MyInviteCodes,
}

export {
  AdminBindings as 'invite/AdminBindings',
  MyInviteCodes as 'invite/MyInviteCodes',
}

export default defineYuDreamPlugin({
  routes,
  default: MyInviteCodes,
})
