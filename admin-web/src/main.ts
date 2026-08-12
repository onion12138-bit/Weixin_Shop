import { createApp } from 'vue'
import App from './App.vue'
import { installInputClearButtons } from './utils/input-clear'
import './style.css'

/**
 * 管理后台唯一入口。
 *
 * 页面级路由、状态管理和权限守卫将在对应业务阶段按模块接入，
 * 此处只负责创建并挂载 Vue 根应用。
 */
createApp(App).mount('#app')
installInputClearButtons(document)
