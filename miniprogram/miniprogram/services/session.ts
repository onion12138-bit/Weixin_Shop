interface ApiEnvelope<T> {
  code: string
  message: string
  data: T
  traceId: string
}

export interface GuestSession {
  customerIdentityId: number
  accessToken: string
  expiresInSeconds: number
}

export interface StringStorage {
  get(key: string): string | null
  set(key: string, value: string): void
  remove(key: string): void
}

export interface SessionTransport {
  exchange(installationId: string): Promise<GuestSession>
}

export interface GuestSessionManager {
  ensure(): Promise<string>
  refresh(): Promise<string>
  invalidate(): void
  currentToken(): string | null
}

export const INSTALLATION_ID_KEY = 'diy_installation_id'
export const CUSTOMER_TOKEN_KEY = 'diy_customer_access_token'

export function createGuestSessionManager(
  storage: StringStorage,
  transport: SessionTransport,
  uuidFactory: () => string = createUuidV4,
): GuestSessionManager {
  let inFlightRefresh: Promise<string> | null = null

  function installationId(): string {
    const existing = storage.get(INSTALLATION_ID_KEY)
    if (existing) return existing
    const generated = uuidFactory()
    storage.set(INSTALLATION_ID_KEY, generated)
    return generated
  }

  async function refresh(): Promise<string> {
    if (inFlightRefresh) return inFlightRefresh
    inFlightRefresh = transport.exchange(installationId())
      .then((session) => {
        storage.set(CUSTOMER_TOKEN_KEY, session.accessToken)
        return session.accessToken
      })
      .finally(() => {
        inFlightRefresh = null
      })
    return inFlightRefresh
  }

  async function ensure(): Promise<string> {
    const token = storage.get(CUSTOMER_TOKEN_KEY)
    if (token) return token
    return refresh()
  }

  return {
    ensure,
    refresh,
    invalidate: () => storage.remove(CUSTOMER_TOKEN_KEY),
    currentToken: () => storage.get(CUSTOMER_TOKEN_KEY),
  }
}

export const guestSessionManager = createGuestSessionManager(
  {
    get: (key) => wx.getStorageSync(key) || null,
    set: (key, value) => wx.setStorageSync(key, value),
    remove: (key) => wx.removeStorageSync(key),
  },
  {
    exchange: exchangeGuestSession,
  },
)

export function ensureGuestToken(): Promise<string> {
  return guestSessionManager.ensure()
}

export function refreshGuestToken(): Promise<string> {
  return guestSessionManager.refresh()
}

function exchangeGuestSession(installationId: string): Promise<GuestSession> {
  return new Promise((resolve, reject) => {
    wx.request<ApiEnvelope<GuestSession>>({
      url: `${apiBaseUrl()}/app/session/guest`,
      method: 'POST',
      data: { installationId },
      timeout: 10000,
      success(response) {
        const envelope = response.data
        if (
          response.statusCode >= 200 &&
          response.statusCode < 300 &&
          envelope.code === 'OK'
        ) {
          resolve(envelope.data)
          return
        }
        reject(new Error(envelope?.message || '建立访客会话失败'))
      },
      fail(error) {
        reject(new Error(error.errMsg || '网络连接失败'))
      },
    })
  })
}

function apiBaseUrl(): string {
  const app = getApp<{
    globalData: { apiBaseUrl: string }
  }>()
  return app.globalData.apiBaseUrl.replace(/\/$/, '')
}

function createUuidV4(): string {
  const bytes = randomBytes()
  bytes[6] = (bytes[6] & 0x0f) | 0x40
  bytes[8] = (bytes[8] & 0x3f) | 0x80
  const hex = Array.from(bytes, (byte) =>
    byte.toString(16).padStart(2, '0'),
  )
  return [
    hex.slice(0, 4).join(''),
    hex.slice(4, 6).join(''),
    hex.slice(6, 8).join(''),
    hex.slice(8, 10).join(''),
    hex.slice(10, 16).join(''),
  ].join('-')
}

function randomBytes(): Uint8Array {
  const bytes = new Uint8Array(16)
  if (typeof wx !== 'undefined' && wx.getRandomValues) {
    wx.getRandomValues(bytes)
    return bytes
  }
  for (let index = 0; index < bytes.length; index += 1) {
    bytes[index] = Math.floor(Math.random() * 256)
  }
  return bytes
}
