import { defineYuDreamPlugin } from '@yudream/plugin-sdk'
import 'virtual:uno.css'
import './styles.css'
import SettingsPage from './pages/SettingsPage.vue'
import StudentsPage from './pages/StudentsPage.vue'
import GateWidget from './widgets/GateWidget.vue'
import PrefillWidget from './widgets/PrefillWidget.vue'

export const Settings = SettingsPage
export const Students = StudentsPage
export const Gate = GateWidget
export const Prefill = PrefillWidget

export const routes = {
  Settings,
  Students,
  Gate,
  Prefill,
  'cas/Settings': Settings,
  'cas/Students': Students,
  'cas/Gate': Gate,
  'cas/Prefill': Prefill,
}

export default defineYuDreamPlugin({
  routes,
  default: Settings,
})
