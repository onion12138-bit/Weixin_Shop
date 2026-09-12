import { ref } from 'vue'

const TOKEN_KEY = 'diy_bracelet_admin_access_token'

// 请求层和认证 store 共用该 ref，避免存储已清理但界面仍保持登录态。
export const accessToken = ref(window.localStorage.getItem(TOKEN_KEY))

export function getAccessToken(): string | null {
  accessToken.value = window.localStorage.getItem(TOKEN_KEY)
  return accessToken.value
}

export function setAccessToken(token: string): void {
  window.localStorage.setItem(TOKEN_KEY, token)
  accessToken.value = token
}

/** 只有请求携带的令牌仍是当前令牌时，401 才能使当前会话失效。 */
export function clearAccessToken(expectedToken?: string | null): void {
  if (expectedToken !== undefined && getAccessToken() !== expectedToken) {
    return
  }
  window.localStorage.removeItem(TOKEN_KEY)
  accessToken.value = null
}
