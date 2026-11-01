declare function require(name: string): any
export {}
jest.mock('../miniprogram/services/diy', () => ({ uploadSchemePreview: jest.fn() }))

async function flush() { for (let i = 0; i < 12; i += 1) await Promise.resolve() }
function load(storage = new Map<string, string>()) {
  ;(globalThis as any).wx = {
    getStorageSync: (key: string) => storage.get(key) || '',
    setStorageSync: (key: string, value: string) => storage.set(key, value),
  }
  let queue: any; let upload: jest.Mock; let ApiError: any
  jest.isolateModules(() => {
    upload = jest.requireMock('../miniprogram/services/diy').uploadSchemePreview
    ApiError = require('../miniprogram/services/request').ApiError
    queue = require('../miniprogram/services/scheme-preview')
  })
  return { queue, upload: upload!, ApiError, storage }
}

describe('background versioned scheme previews', () => {
  beforeEach(() => { jest.resetAllMocks(); jest.useFakeTimers() })
  afterEach(() => { jest.clearAllTimers(); jest.useRealTimers() })

  it('retains a failed upload and resumes it after a new app session without re-exporting', async () => {
    const { queue, upload, storage } = load()
    upload.mockRejectedValueOnce(new Error('offline'))
    const generate = jest.fn().mockResolvedValue('/tmp/version0.png')
    queue.queueSchemePreview(7, 0, generate)
    expect(generate).not.toHaveBeenCalled()
    jest.advanceTimersByTime(0)
    await flush()
    expect(queue.hasPendingSchemePreview(7)).toBe(true)
    const restored = load(storage)
    restored.upload.mockResolvedValueOnce({ previewUrl: '/public/preview.png' })
    await restored.queue.retryPendingSchemePreviews()
    expect(restored.upload).toHaveBeenLastCalledWith(7, '/tmp/version0.png', 0)
    expect(restored.queue.hasPendingSchemePreview(7)).toBe(false)
    expect(generate).toHaveBeenCalledTimes(1)
  })

  it('does not upload an obsolete export that finishes after a newer save', async () => {
    const { queue, upload } = load()
    let resolve!: (path: string) => void
    queue.queueSchemePreview(7, 0, () => new Promise((done) => { resolve = done }))
    jest.advanceTimersByTime(0)
    upload.mockResolvedValue({})
    queue.queueSchemePreview(7, 1, () => Promise.resolve('/tmp/new.png'))
    jest.advanceTimersByTime(0)
    await flush()
    resolve('/tmp/old.png')
    await flush()
    expect(upload.mock.calls).toEqual([[7, '/tmp/new.png', 1]])
  })

  it('does not erase a newer pending task when an older upload completes', async () => {
    const { queue, upload } = load()
    let resolve!: (value: any) => void
    upload.mockReturnValueOnce(new Promise((done) => { resolve = done }))
    queue.queueSchemePreview(7, 0, () => Promise.resolve('/tmp/old.png'))
    jest.advanceTimersByTime(0)
    await flush()
    queue.queueSchemePreview(7, 1, () => Promise.reject(new Error('export failed')))
    jest.advanceTimersByTime(0)
    await flush()
    resolve({ previewUrl: '/old.png' })
    await flush()
    expect(queue.hasPendingSchemePreview(7)).toBe(true)
  })

  it.each(['DIY_SCHEME_VERSION_CONFLICT', 'COMMON_NOT_FOUND'])('drops terminal %s jobs', async (code) => {
    const { queue, upload, ApiError } = load()
    upload.mockRejectedValueOnce(new ApiError('obsolete', code))
    queue.queueSchemePreview(7, 0, () => Promise.resolve('/tmp/old.png'))
    jest.advanceTimersByTime(0)
    await flush()
    await queue.retryPendingSchemePreviews()
    expect(upload).toHaveBeenCalledTimes(1)
    expect(queue.hasPendingSchemePreview(7)).toBe(false)
  })

  it('does not duplicate active uploads on repeated page shows and detaches disposed listeners', async () => {
    const { queue, upload } = load()
    let resolve!: (value: any) => void
    upload.mockReturnValueOnce(new Promise((done) => { resolve = done }))
    const disposed = jest.fn(); const active = jest.fn()
    const unsubscribe = queue.onSchemePreviewReady(disposed)
    queue.onSchemePreviewReady(active)
    queue.queueSchemePreview(7, 0, () => Promise.resolve('/tmp/preview.png'))
    jest.advanceTimersByTime(0)
    await flush()
    await queue.retryPendingSchemePreviews()
    await queue.retryPendingSchemePreviews()
    unsubscribe()
    resolve({ previewUrl: '/ready.png' })
    await flush()
    expect(upload).toHaveBeenCalledTimes(1)
    expect(disposed).not.toHaveBeenCalled()
    expect(active).toHaveBeenCalledWith(7, 0, '/ready.png')
    expect(queue.readySchemePreviewUrl(7, 0)).toBe('/ready.png')
    expect(queue.readySchemePreviewUrl(7, 1)).toBe('')
    queue.queueSchemePreview(7, 1, () => Promise.resolve('/tmp/new.png'))
    expect(queue.readySchemePreviewUrl(7, 0)).toBe('')
  })

  it('keeps independently saved schemes in the retry queue', async () => {
    const { queue, upload, storage } = load()
    upload.mockRejectedValue(new Error('offline'))
    queue.queueSchemePreview(7, 0, () => Promise.resolve('/tmp/first.png'))
    queue.queueSchemePreview(8, 3, () => Promise.resolve('/tmp/second.png'))
    jest.advanceTimersByTime(0)
    await flush()
    expect(JSON.parse(storage.get('diy_pending_previews_v2')!)).toEqual([
      { schemeId: 7, version: 0, filePath: '/tmp/first.png' },
      { schemeId: 8, version: 3, filePath: '/tmp/second.png' },
    ])
  })

  it('regenerates an interrupted export when its saved version is reopened without duplicating an active upload', async () => {
    const storage = new Map([['diy_pending_previews_v2', JSON.stringify([
      { schemeId: 7, version: 0, filePath: '' },
    ])]])
    const { queue, upload } = load(storage)
    let resolve!: (value: any) => void
    upload.mockReturnValueOnce(new Promise((done) => { resolve = done }))
    const generate = jest.fn().mockResolvedValue('/tmp/rebuilt.png')
    queue.queueSchemePreview(7, 0, generate)
    jest.advanceTimersByTime(0)
    await flush()
    queue.queueSchemePreview(7, 0, generate)
    jest.advanceTimersByTime(0)
    await flush()
    expect(generate).toHaveBeenCalledTimes(1)
    expect(upload).toHaveBeenCalledTimes(1)
    resolve({ previewUrl: '/rebuilt.png' })
    await flush()
    expect(queue.hasPendingSchemePreview(7)).toBe(false)
  })
})
