import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { useAuthStore } from '../stores/auth'
import {
  ApiError,
  clearAccessToken,
  getAccessToken,
  request,
  requestBlob,
} from './http'

const auth = useAuthStore()
const fetchStub = vi.fn<typeof fetch>()

function envelope(data: unknown, code = 'OK', message = '成功'): Response {
  return Response.json({
    code,
    message,
    data,
    traceId: 'trace-auth-test',
    timestamp: '2026-09-12T00:00:00Z',
  }, { status: code === 'OK' ? 200 : 401 })
}

async function login(token: string): Promise<void> {
  fetchStub.mockResolvedValueOnce(envelope({
    accessToken: token,
    tokenType: 'Bearer',
    expiresInSeconds: 3600,
  }))
  await auth.login('admin', 'test-password')
}

describe.each([
  ['request', () => request('/admin/catalog/beads')],
  ['requestBlob', () => requestBlob('/admin/catalog/beads/import-template')],
] as const)('%s authentication failures', (_name, sendRequest) => {
  beforeEach(() => {
    vi.stubGlobal('fetch', fetchStub)
    fetchStub.mockReset()
    auth.logout()
  })

  afterEach(() => {
    auth.logout()
    vi.unstubAllGlobals()
  })

  it('updates the shared authenticated state when the current token expires', async () => {
    await login('expired-token')
    expect(auth.isAuthenticated.value).toBe(true)
    fetchStub.mockResolvedValueOnce(envelope(null, 'AUTH_UNAUTHORIZED', '登录已失效'))

    await expect(sendRequest()).rejects.toMatchObject({
      name: 'ApiError',
      status: 401,
      code: 'AUTH_UNAUTHORIZED',
      message: '登录已失效',
      traceId: 'trace-auth-test',
    })

    expect(getAccessToken()).toBeNull()
    expect(auth.state.token).toBeNull()
    expect(auth.isAuthenticated.value).toBe(false)
  })

  it('invalidates the session even when a 401 response is not JSON', async () => {
    await login('expired-token')
    fetchStub.mockResolvedValueOnce(new Response('<html>upstream details</html>', { status: 401 }))

    await expect(sendRequest()).rejects.toBeInstanceOf(ApiError)

    expect(getAccessToken()).toBeNull()
    expect(auth.isAuthenticated.value).toBe(false)
  })

  it.each(['expired-token', null])(
    'preserves a new login when an older request using %s returns 401',
    async (oldToken) => {
      if (oldToken) await login(oldToken)
      let respond!: (response: Response) => void
      fetchStub.mockReturnValueOnce(new Promise<Response>((resolve) => { respond = resolve }))
      const pending = sendRequest()
      const rejected = expect(pending).rejects.toBeInstanceOf(ApiError)

      await login('new-token')
      respond(envelope(null, 'AUTH_UNAUTHORIZED', '登录已失效'))
      await rejected

      expect(getAccessToken()).toBe('new-token')
      expect(auth.state.token).toBe('new-token')
      expect(auth.isAuthenticated.value).toBe(true)
    },
  )

  it.each([
    ['HTML', '<html>upstream details</html>'],
    ['empty body', ''],
    ['JSON null', 'null'],
    ['invalid envelope fields', '{"message":{"private":"upstream details"},"code":17,"traceId":true}'],
  ])('returns a stable ApiError for %s errors without ending the session', async (_format, body) => {
    await login('valid-token')
    fetchStub.mockResolvedValueOnce(new Response(body, { status: 502 }))

    let error: unknown
    try {
      await sendRequest()
    } catch (caught) {
      error = caught
    }

    expect(error).toBeInstanceOf(ApiError)
    expect(error).toMatchObject({ status: 502, code: 'COMMON_INTERNAL_ERROR' })
    expect((error as ApiError).message).not.toMatch(/upstream|object Object/)
    expect((error as ApiError).traceId).toBeUndefined()
    expect(auth.isAuthenticated.value).toBe(true)
    expect(getAccessToken()).toBe('valid-token')
  })
})

describe('shared authentication state', () => {
  beforeEach(() => {
    vi.stubGlobal('fetch', fetchStub)
    fetchStub.mockReset()
    auth.logout()
  })

  afterEach(() => {
    auth.logout()
    vi.unstubAllGlobals()
  })

  it('updates every store consumer when the HTTP token is cleared', async () => {
    const secondConsumer = useAuthStore()
    await login('active-token')
    expect(secondConsumer.isAuthenticated.value).toBe(true)

    clearAccessToken()

    expect(auth.state.token).toBeNull()
    expect(secondConsumer.isAuthenticated.value).toBe(false)
  })
})
