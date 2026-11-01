import { computed, reactive } from 'vue'
import { loginAdmin } from '../api/auth'
import {
  accessToken,
  clearAccessToken,
  setAccessToken,
} from '../utils/access-token'

const state = reactive({
  token: accessToken,
  loading: false,
})

/**
 * 小型单页后台的共享认证状态。
 *
 * 所有业务页面共用请求层的令牌状态，401 和主动退出都立即回到登录页。
 */
export function useAuthStore() {
  const isAuthenticated = computed(() => Boolean(state.token))

  async function login(username: string, password: string): Promise<void> {
    if (state.loading) return
    state.loading = true
    try {
      const result = await loginAdmin(username, password)
      setAccessToken(result.accessToken)
    } finally {
      state.loading = false
    }
  }

  function logout(): void {
    clearAccessToken()
  }

  return {
    state,
    isAuthenticated,
    login,
    logout,
  }
}
