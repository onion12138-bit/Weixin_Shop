import { createApp } from 'vue'
import App from './App.vue'
import { installInputClearButtons } from './utils/input-clear'
import './style.css'

/**
 * 管理后台唯一入口。
 *
 * 挂载根组件后，统一增强动态表单的清空操作。
 */
createApp(App).mount('#app')
installInputClearButtons(document)
