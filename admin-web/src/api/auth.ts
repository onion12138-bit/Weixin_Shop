import { request } from './http'

export interface LoginResponse {
  accessToken: string
  tokenType: 'Bearer'
  expiresInSeconds: number
}

/**
 * 使用配置化本地管理员账号登录。
 */
export function loginAdmin(
  username: string,
  password: string,
): Promise<LoginResponse> {
  return request<LoginResponse>('/admin/auth/login', {
    method: 'POST',
    body: JSON.stringify({ username, password }),
  })
}
