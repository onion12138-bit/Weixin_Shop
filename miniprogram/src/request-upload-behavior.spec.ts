const mockEnsureGuestToken = jest.fn<Promise<string>, []>()
const mockRefreshGuestToken = jest.fn<Promise<string>, [string?]>()

jest.mock('../miniprogram/services/session', () => ({
  ensureGuestToken: () => mockEnsureGuestToken(),
  refreshGuestToken: (token?: string) => mockRefreshGuestToken(token),
}))

import { request, upload } from '../miniprogram/services/request'

interface UploadCall {
  header: { Authorization: string }
  success: (response: { data: string; statusCode: number }) => void
}

function response(statusCode: number, code: string, data: unknown = null): string {
  return JSON.stringify({
    code,
    message: code === 'OK' ? '成功' : '访客会话已失效',
    data,
    traceId: `trace-${statusCode}`,
  })
}

describe('customer upload session recovery', () => {
  const uploadFile = jest.fn()

  beforeEach(() => {
    mockEnsureGuestToken.mockReset()
    mockRefreshGuestToken.mockReset()
    uploadFile.mockReset()
    ;(globalThis as unknown as { getApp: () => unknown }).getApp = () => ({
      globalData: { apiBaseUrl: 'http://localhost:8081/api' },
    })
    ;(globalThis as unknown as { wx: unknown }).wx = { uploadFile }
  })

  it('invalidates and refreshes once after the first upload 401', async () => {
    mockEnsureGuestToken
      .mockResolvedValueOnce('stale-token')
      .mockResolvedValueOnce('fresh-token')
    mockRefreshGuestToken.mockResolvedValue('fresh-token')
    uploadFile
      .mockImplementationOnce((options: UploadCall) => options.success({
        statusCode: 401,
        data: response(401, 'CUSTOMER_UNAUTHORIZED'),
      }))
      .mockImplementationOnce((options: UploadCall) => options.success({
        statusCode: 200,
        data: response(200, 'OK', { fileId: 42 }),
      }))

    await expect(upload<{ fileId: number }>(
      '/app/diy-schemes/preview',
      '/tmp/preview.png',
    )).resolves.toEqual({ fileId: 42 })

    expect(mockRefreshGuestToken).toHaveBeenCalledWith('stale-token')
    expect(mockRefreshGuestToken).toHaveBeenCalledTimes(1)
    expect(uploadFile).toHaveBeenCalledTimes(2)
    expect(uploadFile.mock.calls[0][0].header.Authorization)
      .toBe('Bearer stale-token')
    expect(uploadFile.mock.calls[1][0].header.Authorization)
      .toBe('Bearer fresh-token')
  })

  it('stops after the retried upload also returns 401', async () => {
    mockEnsureGuestToken
      .mockResolvedValueOnce('stale-token')
      .mockResolvedValueOnce('fresh-token')
    mockRefreshGuestToken.mockResolvedValue('fresh-token')
    uploadFile.mockImplementation((options: UploadCall) => options.success({
      statusCode: 401,
      data: response(401, 'CUSTOMER_UNAUTHORIZED'),
    }))

    await expect(upload(
      '/app/diy-schemes/preview',
      '/tmp/preview.png',
    )).rejects.toMatchObject({
      code: 'CUSTOMER_UNAUTHORIZED',
      traceId: 'trace-401',
    })

    expect(mockRefreshGuestToken).toHaveBeenCalledWith('stale-token')
    expect(mockRefreshGuestToken).toHaveBeenCalledTimes(1)
    expect(uploadFile).toHaveBeenCalledTimes(2)
  })

  it('rejects a null upload envelope with a readable API error', async () => {
    mockEnsureGuestToken.mockResolvedValue('token')
    uploadFile.mockImplementation((options: UploadCall) => options.success({
      statusCode: 200,
      data: 'null',
    }))

    await expect(upload('/app/files', '/tmp/avatar.png')).rejects.toMatchObject({
      name: 'ApiError', code: 'COMMON_INTERNAL_ERROR',
    })
  })

  it('passes the failed request token to guarded session refresh', async () => {
    mockEnsureGuestToken.mockResolvedValueOnce('stale-token').mockResolvedValueOnce('fresh-token')
    mockRefreshGuestToken.mockResolvedValue('fresh-token')
    ;(globalThis as any).wx.request = jest.fn()
      .mockImplementationOnce((options: any) => options.success({ statusCode: 401, data: {} }))
      .mockImplementationOnce((options: any) => options.success({ statusCode: 200, data: { code: 'OK', data: 7 } }))
    await expect(request('/app/orders')).resolves.toBe(7)
    expect(mockRefreshGuestToken).toHaveBeenCalledWith('stale-token')
  })

  it('rejects a null request envelope with a readable API error', async () => {
    ;(globalThis as any).wx.request = (options: any) => options.success({
      statusCode: 200, data: null,
    })

    await expect(request('/public/catalog/categories')).rejects.toMatchObject({
      name: 'ApiError', code: 'COMMON_INTERNAL_ERROR',
    })
  })
})
