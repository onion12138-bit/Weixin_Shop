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
      getStorageSync: (key: string) => key === 'diy_installation_id' ? 'existing-id' : '',
      setStorageSync: () => undefined,
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

  it('uses the official async crypto API and shares identity creation across requests', async () => {
    const values = new Map<string, string>()
    let supplyRandom!: (value: { randomValues: ArrayBuffer }) => void
    const random = jest.fn((options) => { supplyRandom = options.success })
    ;(globalThis as any).wx = { getUserCryptoManager: () => ({ getRandomValues: random }) }
    const exchange = jest.fn().mockResolvedValue({ accessToken: 'secure-token' })
    const manager = createGuestSessionManager({
      get: (key) => values.get(key) || null,
      set: (key, value) => { values.set(key, value) }, remove: (key) => { values.delete(key) },
    }, { exchange })
    const requests = [manager.ensure(), manager.ensure(), manager.refresh()]
    expect(random).toHaveBeenCalledTimes(1)
    expect(random.mock.calls[0][0].length).toBe(16)
    expect(exchange).not.toHaveBeenCalled()
    supplyRandom({ randomValues: Uint8Array.from({ length: 16 }, (_, i) => i + 1).buffer })
    await expect(Promise.all(requests)).resolves.toEqual(['secure-token', 'secure-token', 'secure-token'])
    expect(exchange).toHaveBeenCalledTimes(1)
    expect(exchange).toHaveBeenCalledWith('01020304-0506-4708-890a-0b0c0d0e0f10')
  })

  it('fails safely without crypto and retries after support becomes available', async () => {
    ;(globalThis as any).wx = {}
    const values = new Map<string, string>()
    const exchange = jest.fn().mockResolvedValue({ accessToken: 'secure-token' })
    const manager = createGuestSessionManager({
      get: (key) => values.get(key) || null,
      set: (key, value) => { values.set(key, value) }, remove: (key) => { values.delete(key) },
    }, { exchange })
    await expect(manager.ensure()).rejects.toThrow('请更新微信')
    expect(values.size).toBe(0)
    expect(exchange).not.toHaveBeenCalled()
    ;(globalThis as any).wx = { getUserCryptoManager: () => ({ getRandomValues: (options: any) => {
      options.success({ randomValues: new Uint8Array(16).buffer })
    } }) }
    await expect(manager.ensure()).resolves.toBe('secure-token')
  })

  it('preserves an existing installation identity even when crypto is unavailable', async () => {
    ;(globalThis as any).wx = {}
    const values = new Map([['diy_installation_id', 'existing-installation-id']])
    const exchange = jest.fn().mockResolvedValue({ accessToken: 'token' })
    const manager = createGuestSessionManager({
      get: (key) => values.get(key) || null,
      set: (key, value) => { values.set(key, value) }, remove: (key) => { values.delete(key) },
    }, { exchange })
    await expect(manager.ensure()).resolves.toBe('token')
    expect(exchange).toHaveBeenCalledWith('existing-installation-id')
  })

  it('does not invalidate a fresh token when an older request returns 401 later', async () => {
    const values = new Map([['diy_installation_id', 'id'], ['diy_customer_access_token', 'stale']])
    const exchange = jest.fn().mockResolvedValue({ accessToken: 'fresh' })
    const manager = createGuestSessionManager({
      get: (key) => values.get(key) || null,
      set: (key, value) => { values.set(key, value) }, remove: (key) => { values.delete(key) },
    }, { exchange })
    await expect(manager.refresh('stale')).resolves.toBe('fresh')
    await expect(manager.refresh('stale')).resolves.toBe('fresh')
    expect(exchange).toHaveBeenCalledTimes(1)
    expect(manager.currentToken()).toBe('fresh')
  })

  it('rejects a malformed successful session without persisting a bad token', async () => {
    const set = jest.fn()
    const manager = createGuestSessionManager({
      get: (key) => key === 'diy_installation_id' ? 'id' : null,
      set, remove: jest.fn(),
    }, { exchange: jest.fn().mockResolvedValue({ accessToken: null }) })
    await expect(manager.ensure()).rejects.toThrow('建立访客会话失败')
    expect(set).not.toHaveBeenCalled()
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
