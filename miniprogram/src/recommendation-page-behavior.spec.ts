declare function require(name: string): unknown

jest.mock('../miniprogram/services/recommendation', () => ({
  getRecommendationStatus: jest.fn(),
  requestRecommendation: jest.fn(),
}))

interface Deferred<T> {
  promise: Promise<T>
  resolve(value: T): void
  reject(reason: unknown): void
}

function deferred<T>(): Deferred<T> {
  let resolve!: (value: T) => void
  let reject!: (reason: unknown) => void
  const promise = new Promise<T>((onResolve, onReject) => {
    resolve = onResolve
    reject = onReject
  })
  return { promise, resolve, reject }
}

const recommendation = {
  schemeName: '第二页灵感',
  sequence: [2031],
  quantities: { 2031: 1 },
  items: [{
    skuId: 2031,
    name: '第二页珠子',
    imageUrl: 'https://cdn.test/2031.png',
    quantity: 1,
    reason: '适合日常',
  }],
  summary: '一套已校验方案',
  cordOption: {
    id: 6001,
    code: 'ELASTIC-CLEAR',
    name: '透明弹力绳',
    colorHex: '#ffffff',
  },
  beadAmount: 100,
  craftFee: 10,
  cordFee: 5,
  totalAmount: 115,
  quoteFingerprint: 'server-fingerprint',
  culturalDisclaimer: '仅供文化娱乐参考。',
}

function instantiate(definition: Record<string, any>): Record<string, any> {
  const instance: Record<string, any> = {
    ...definition,
    data: { ...definition.data },
  }
  instance.setData = jest.fn((patch: Record<string, unknown>) => {
    Object.assign(instance.data, patch)
  })
  return instance
}

function loadPage() {
  let definition: Record<string, any> | null = null
  let ApiError: new (message: string, code: string, traceId?: string) => Error
  const wxMock = {
    setStorageSync: jest.fn(),
    removeStorageSync: jest.fn(),
    navigateTo: jest.fn(),
  }
  ;(globalThis as any).wx = wxMock
  ;(globalThis as any).Page = (options: Record<string, any>) => {
    definition = options
  }

  let statusMock!: jest.Mock
  let recommendMock!: jest.Mock
  jest.isolateModules(() => {
    const services = jest.requireMock(
      '../miniprogram/services/recommendation',
    ) as {
      getRecommendationStatus: jest.Mock
      requestRecommendation: jest.Mock
    }
    services.getRecommendationStatus.mockReset()
    services.requestRecommendation.mockReset()
    services.getRecommendationStatus.mockResolvedValue({
      status: 'AVAILABLE',
      available: true,
    })
    statusMock = services.getRecommendationStatus
    recommendMock = services.requestRecommendation
    ApiError = (jest.requireActual('../miniprogram/services/request') as {
      ApiError: new (message: string, code: string, traceId?: string) => Error
    }).ApiError
    require('../miniprogram/pages/recommendation/index')
  })
  if (!definition) throw new Error('Page definition was not captured')
  return {
    page: instantiate(definition),
    wxMock,
    statusMock,
    recommendMock,
    ApiError: ApiError!,
  }
}

async function flushMicrotasks(): Promise<void> {
  for (let index = 0; index < 6; index += 1) await Promise.resolve()
}

async function availablePage() {
  const loaded = loadPage()
  loaded.page.onLoad()
  await flushMicrotasks()
  expect(loaded.page.data.available).toBe(true)
  return loaded
}

describe('recommendation Page behavior', () => {
  it.each([
    ['birthday', (page: Record<string, any>) => page.changeBirthDate({
      detail: { value: '2025-01-20' },
    })],
    ['field', (page: Record<string, any>) => page.updateField({
      currentTarget: { dataset: { field: 'freeText' } },
      detail: { value: 'changed while waiting' },
    })],
    ['fit', (page: Record<string, any>) => page.changeFitType({
      detail: { value: '2' },
    })],
  ])('does not revive an old response after %s changes', async (_, change) => {
    const { page, recommendMock } = await availablePage()
    const pending = deferred<typeof recommendation>()
    recommendMock.mockReturnValueOnce(pending.promise)

    const submission = page.submitRecommendation()
    change(page)
    pending.resolve(recommendation)
    await submission

    expect(page.data.result).toBeNull()
    expect(page.recommendation).toBeNull()
    expect(page.submittedForm).toBeNull()
    expect(page.data.submitting).toBe(false)
  })

  it('does not mutate page state after onUnload while POST is pending', async () => {
    const { page, recommendMock } = await availablePage()
    const pending = deferred<typeof recommendation>()
    recommendMock.mockReturnValueOnce(pending.promise)
    const submission = page.submitRecommendation()

    expect(typeof page.onUnload).toBe('function')
    if (typeof page.onUnload === 'function') page.onUnload()
    const callsAfterUnload = page.setData.mock.calls.length
    pending.resolve(recommendation)
    await submission

    expect(page.setData).toHaveBeenCalledTimes(callsAfterUnload)
    expect(page.recommendation).toBeNull()
    expect(page.submittedForm).toBeNull()
  })

  it('does not setData after onUnload while status request is pending', async () => {
    const { page, statusMock } = loadPage()
    const pending = deferred<{ status: string; available: boolean }>()
    statusMock.mockReset()
    statusMock.mockReturnValueOnce(pending.promise)

    page.onLoad()
    page.onUnload()
    const callsAfterUnload = page.setData.mock.calls.length
    pending.resolve({ status: 'AVAILABLE', available: true })
    await flushMicrotasks()

    expect(page.setData).toHaveBeenCalledTimes(callsAfterUnload)
    expect(page.recommendation).toBeNull()
  })

  it.each([
    ['AI_NOT_CONFIGURED', 'AI 推荐暂未开放'],
    ['AI_DISABLED', 'AI 推荐暂停使用'],
    ['AI_MODEL_UNAVAILABLE', 'AI 推荐服务暂时不可用，请稍后重试'],
  ])('maps POST error %s without rendering message or trace', async (
    code,
    expected,
  ) => {
    const { page, recommendMock, ApiError } = await availablePage()
    recommendMock.mockRejectedValueOnce(
      new ApiError('upstream-secret-message', code, 'trace-secret'),
    )

    await page.submitRecommendation()

    expect(page.data.available).toBe(false)
    expect(page.data.statusMessage).toBe(expected)
    expect(JSON.stringify(page.data)).not.toContain('upstream-secret-message')
    expect(JSON.stringify(page.data)).not.toContain('trace-secret')
  })

  it('guards double tap while navigation is in flight and resets on success', async () => {
    const { page, wxMock } = await availablePage()
    page.recommendation = recommendation
    page.submittedForm = page.currentForm()

    page.enterDiy()
    page.enterDiy()

    expect(wxMock.setStorageSync).toHaveBeenCalledTimes(1)
    expect(wxMock.navigateTo).toHaveBeenCalledTimes(1)
    const stored = JSON.parse(wxMock.setStorageSync.mock.calls[0][1]) as object
    expect(Object.keys(stored).sort()).toEqual([
      'cordOptionId',
      'fitType',
      'sequence',
      'wristCircumferenceMm',
    ])
    expect(JSON.stringify(stored)).not.toContain('birthDate')
    expect(JSON.stringify(stored)).not.toContain('freeText')
    const options = wxMock.navigateTo.mock.calls[0][0] as {
      success?: () => void
    }
    expect(typeof options.success).toBe('function')
    options.success?.()
    page.enterDiy()
    expect(wxMock.navigateTo).toHaveBeenCalledTimes(2)
  })

  it('cleans storage and restores navigation guard when navigateTo fails', async () => {
    const { page, wxMock } = await availablePage()
    page.recommendation = recommendation
    page.submittedForm = page.currentForm()

    page.enterDiy()
    const options = wxMock.navigateTo.mock.calls[0][0] as {
      fail?: () => void
    }
    expect(typeof options.fail).toBe('function')
    options.fail?.()

    expect(wxMock.removeStorageSync)
      .toHaveBeenCalledWith('pending_ai_recommendation')
    expect(page.data.enteringDiy).toBe(false)
    page.enterDiy()
    expect(wxMock.navigateTo).toHaveBeenCalledTimes(2)
  })
})
