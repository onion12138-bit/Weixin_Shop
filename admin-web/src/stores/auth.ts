import { computed, reactive } from 'vue'
import { loginAdmin } from '../api/auth'
import {
  clearAccessToken,
  getAccessToken,
  setAccessToken,
} from '../api/http'

const state = reactive({
  token: getAccessToken(),
  loading: false,
})

/**
 * 小型单页后台的共享认证状态。
 *
 * 当前只有登录页和商品页，无需引入额外状态库；该组合式 store 保持 API 清晰，
 * 未来接入 Pinia 时调用方可以维持相同的 login/logout 语义。
 */
export function useAuthStore() {
  const isAuthenticated = computed(() => Boolean(state.token))

  async function login(username: string, password: string): Promise<void> {
    state.loading = true
    try {
      const result = await loginAdmin(username, password)
      setAccessToken(result.accessToken)
      state.token = result.accessToken
    } finally {
      state.loading = false
    }
  }

  function logout(): void {
    clearAccessToken()
    state.token = null
  }

  return {
    state,
    isAuthenticated,
    login,
    logout,
  }
}
