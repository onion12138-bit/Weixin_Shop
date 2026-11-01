export {}

jest.mock('../miniprogram/services/session', () => ({ ensureGuestToken: jest.fn() }))
jest.mock('../miniprogram/services/scheme-preview', () => ({ retryPendingSchemePreviews: jest.fn() }))

it('handles failed session warmup and leaves later page requests able to retry', async () => {
  let app: any
  ;(globalThis as any).App = (options: any) => { app = options }
  let ensure: jest.Mock
  jest.isolateModules(() => {
    ensure = jest.requireMock('../miniprogram/services/session').ensureGuestToken
    ensure!.mockRejectedValue(new Error('offline'))
    jest.requireActual('../miniprogram/app')
  })
  expect(() => app.onLaunch()).not.toThrow()
  await Promise.resolve()
  expect(ensure!).toHaveBeenCalledTimes(1)
})
