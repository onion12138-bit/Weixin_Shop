import {
  createGuestSessionManager,
  type SessionTransport,
  type StringStorage,
} from '../miniprogram/services/session'

describe('guest session manager', () => {
  it('reuses installation id and coalesces concurrent refreshes', async () => {
    const values = new Map<string, string>()
    const storage: StringStorage = {
      get: (key) => values.get(key) || null,
      set: (key, value) => values.set(key, value),
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
})
