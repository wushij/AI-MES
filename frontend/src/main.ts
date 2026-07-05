import { createApp } from 'vue'
import { createPinia } from 'pinia'
import ElementPlus from 'element-plus'
import { ElMessage } from 'element-plus'
import zhCn from 'element-plus/es/locale/lang/zh-cn'
import 'element-plus/dist/index.css'
import App from './App.vue'
import router from './router'
import { initThemeFromStorage } from '@/utils/theme'
import { applyFavicon, warmBrandIconCache } from '@/utils/brandIconCache'
import '@/assets/styles/theme.css'
import '@/assets/styles/layout-sidebar.css'
import { installSelectToggle } from '@/plugins/selectToggle'

initThemeFromStorage()
applyFavicon()
void warmBrandIconCache()
installSelectToggle()

const app = createApp(App)

app.use(createPinia())
app.use(router)
app.use(ElementPlus, { locale: zhCn })

app.config.errorHandler = (error, _instance, info) => {
  console.error('[Vue]', info, error)
}

window.addEventListener('unhandledrejection', (event) => {
  console.error('[Unhandled]', event.reason)
  if (import.meta.env.DEV && event.reason instanceof Error && !event.reason.message.includes('401')) {
    ElMessage.error(event.reason.message || '页面发生未处理错误')
  }
})

app.mount('#app')
