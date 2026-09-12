import {
  createGuestSessionManager,
  ensureGuestToken,
  type SessionTransport,
  type StringStorage,
} from '../miniprogram/services/session'
import { shouldRefreshCustomerSession } from '../miniprogram/services/request'

describe('guest session manager', () => {
  it('rejects an empty session response without crashing its callback', async () => {
    ;(globalThis as any).getApp = () => ({ globalData: { apiBaseUrl: 'http://localhost:8081/api' } })
    ;(globalThis as any).wx = {
      getStorageSync: () => '', setStorageSync: () => undefined,
      request: (options: any) => options.success({ statusCode: 200, data: null }),
    }
    await expect(ensureGuestToken()).rejects.toThrow('建立访客会话失败')
  })

  it('reuses installation id and coalesces concurrent refreshes', async () => {
    const values = new Map<string, string>()
    const storage: StringStorage = {
      get: (key) => values.get(key) || null,
      set: (key, value) => values.set(key, value),
      remove: (key) => values.delete(key),
    }
    const transport: SessionTransport = {
      exchange: jest.fn().mockResolvedValue({
        accessToken: 'customer-token',
        expiresInSeconds: 2592000,
        customerIdentityId: 91,
      }),
    }
    const manager = createGuestSessionManager(
      storage,
      transport,
      () => '3f6f7816-0f1a-4aa0-870f-f34f9195a2ec',
    )

    await manager.ensure()
    await Promise.all([
      manager.refresh(),
      manager.refresh(),
    ])

    expect(transport.exchange).toHaveBeenCalledTimes(2)
    expect(transport.exchange).toHaveBeenLastCalledWith(
      '3f6f7816-0f1a-4aa0-870f-f34f9195a2ec',
    )
    expect(values.get('diy_installation_id'))
      .toBe('3f6f7816-0f1a-4aa0-870f-f34f9195a2ec')
    expect(values.get('diy_customer_access_token'))
      .toBe('customer-token')
  })

  it('invalidates a stale customer token before ensuring a new session', async () => {
    const values = new Map<string, string>([
      ['diy_customer_access_token', 'stale-token'],
    ])
    const storage: StringStorage = {
      get: (key) => values.get(key) || null,
      set: (key, value) => values.set(key, value),
      remove: (key) => values.delete(key),
    }
    const transport: SessionTransport = {
      exchange: jest.fn().mockResolvedValue({
        accessToken: 'fresh-token',
        expiresInSeconds: 2592000,
        customerIdentityId: 92,
      }),
    }
    const manager = createGuestSessionManager(
      storage,
      transport,
      () => '3f6f7816-0f1a-4aa0-870f-f34f9195a2ec',
    )

    await manager.refresh()
    values.set('diy_customer_access_token', 'stale-token')

    manager.invalidate()
    expect(values.has('diy_customer_access_token')).toBe(false)
    await manager.ensure()

    expect(transport.exchange).toHaveBeenCalledTimes(2)
    expect(values.get('diy_customer_access_token')).toBe('fresh-token')
  })
})

describe('customer session refresh policy', () => {
  it('refreshes only the first 401 response from a customer path', () => {
    expect(shouldRefreshCustomerSession('/app/orders', 401, false)).toBe(true)
    expect(shouldRefreshCustomerSession('/app/orders', 401, true)).toBe(false)
  })

  it('does not refresh public paths or non-401 responses', () => {
    expect(shouldRefreshCustomerSession('/public/products', 401, false))
      .toBe(false)
    expect(shouldRefreshCustomerSession('/app/orders', 403, false)).toBe(false)
    expect(shouldRefreshCustomerSession('/app/orders', 500, false)).toBe(false)
  })
})
