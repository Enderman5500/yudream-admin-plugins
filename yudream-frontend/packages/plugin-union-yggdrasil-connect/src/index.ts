import { defineYuDreamPlugin } from '@yudream/plugin-sdk'
import 'virtual:uno.css'
import './styles.css'
import DashboardEndpointCard from './components/DashboardEndpointCard.vue'
import AuthorizePage from './pages/AuthorizePage.vue'
import BlacklistPage from './pages/BlacklistPage.vue'
import ClientsPage from './pages/ClientsPage.vue'
import DevicePage from './pages/DevicePage.vue'
import MyEndpointPage from './pages/MyEndpointPage.vue'
import MyGrantsPage from './pages/MyGrantsPage.vue'
import SettingsPage from './pages/SettingsPage.vue'
import StatusPage from './pages/StatusPage.vue'
import TokensPage from './pages/TokensPage.vue'
import UnionAdminPage from './pages/UnionAdminPage.vue'
import UnionPage from './pages/UnionPage.vue'

export const EndpointCard = DashboardEndpointCard
export const AdminStatus = StatusPage
export const AdminClients = ClientsPage
export const AdminSettings = SettingsPage
export const AdminTokens = TokensPage
export const AdminUnion = UnionAdminPage
export const AdminBlacklist = BlacklistPage
export const MyGrants = MyGrantsPage
export const MyEndpoint = MyEndpointPage
export const MyUnion = UnionPage
export const Authorize = AuthorizePage
export const Device = DevicePage

export const routes = {
  EndpointCard,
  AdminStatus,
  AdminClients,
  AdminSettings,
  AdminTokens,
  AdminUnion,
  AdminBlacklist,
  MyGrants,
  MyEndpoint,
  MyUnion,
  Authorize,
  Device,
  'yggc/EndpointCard': EndpointCard,
  'yggc/AdminStatus': AdminStatus,
  'yggc/AdminClients': AdminClients,
  'yggc/AdminSettings': AdminSettings,
  'yggc/AdminTokens': AdminTokens,
  'yggc/AdminUnion': AdminUnion,
  'yggc/AdminBlacklist': AdminBlacklist,
  'yggc/MyGrants': MyGrants,
  'yggc/MyEndpoint': MyEndpoint,
  'yggc/MyUnion': MyUnion,
  'yggc/Authorize': Authorize,
  'yggc/Device': Device,
}

export {
  EndpointCard as 'yggc/EndpointCard',
  AdminStatus as 'yggc/AdminStatus',
  AdminClients as 'yggc/AdminClients',
  AdminSettings as 'yggc/AdminSettings',
  AdminTokens as 'yggc/AdminTokens',
  AdminUnion as 'yggc/AdminUnion',
  AdminBlacklist as 'yggc/AdminBlacklist',
  MyGrants as 'yggc/MyGrants',
  MyEndpoint as 'yggc/MyEndpoint',
  MyUnion as 'yggc/MyUnion',
  Authorize as 'yggc/Authorize',
  Device as 'yggc/Device',
}

export default defineYuDreamPlugin({
  routes,
  default: AdminStatus,
})
