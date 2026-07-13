const mockEnsureGuestToken = jest.fn<Promise<string>, []>()
const mockRefreshGuestToken = jest.fn<Promise<string>, []>()
const mockInvalidate = jest.fn<void, []>()

jest.mock('../miniprogram/services/session', () => ({
  ensureGuestToken: () => mockEnsureGuestToken(),
  refreshGuestToken: () => mockRefreshGuestToken(),
  guestSessionManager: { invalidate: () => mockInvalidate() },
}))

import { upload } from '../miniprogram/services/request'

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
    mockInvalidate.mockReset()
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

    expect(mockInvalidate).toHaveBeenCalledTimes(1)
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

    expect(mockInvalidate).toHaveBeenCalledTimes(1)
    expect(mockRefreshGuestToken).toHaveBeenCalledTimes(1)
    expect(uploadFile).toHaveBeenCalledTimes(2)
  })
})
