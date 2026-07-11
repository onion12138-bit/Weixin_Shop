/**
 * 小程序全局入口。
 *
 * V1 使用本地演示身份和本地后端地址。后续接入微信登录时只替换身份适配器，
 * 接入测试或生产环境时只调整环境配置，不改变页面业务状态。
 */
import { LOCAL_API_BASE_URL } from './config/runtime'
import { ensureGuestToken } from './services/session'

App({
  globalData: {
    apiBaseUrl: LOCAL_API_BASE_URL,
  },
  onLaunch() {
    void ensureGuestToken()
  },
})
