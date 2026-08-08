declare function require(name: string): unknown

jest.mock('../miniprogram/services/recommendation', () => ({
  getRecommendationProfile: jest.fn(),
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

const profile = {
  birthDate: '2024-02-29',
  constellation: '双鱼座',
  zodiac: '龙',
  constellationElement: '水',
}

const recommendation = {
  profile,
  plans: [
    {
      schemeName: '清透方案',
      summary: '一套清透的方案',
      items: [{
        skuId: 2031,
        name: '第一种珠子',
        imageUrl: 'https://cdn.test/2031.png',
        reason: '适合日常',
      }],
    },
    {
      schemeName: '柔和方案',
      summary: '另一套柔和的方案',
      items: [
        {
          skuId: 2032,
          name: '第二种珠子',
          imageUrl: 'https://cdn.test/2032.png',
          reason: '光感柔和',
        },
        {
          skuId: 2033,
          name: '第三种珠子',
          imageUrl: 'https://cdn.test/2033.png',
          reason: '增加层次',
        },
      ],
    },
  ],
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
  let profileMock!: jest.Mock
  let recommendMock!: jest.Mock
  jest.isolateModules(() => {
    const services = jest.requireMock(
      '../miniprogram/services/recommendation',
    ) as {
      getRecommendationProfile: jest.Mock
      getRecommendationStatus: jest.Mock
      requestRecommendation: jest.Mock
    }
    services.getRecommendationProfile.mockReset()
    services.getRecommendationStatus.mockReset()
    services.requestRecommendation.mockReset()
    services.getRecommendationStatus.mockResolvedValue({
      status: 'AVAILABLE',
      available: true,
    })
    services.getRecommendationProfile.mockResolvedValue(profile)
    statusMock = services.getRecommendationStatus
    profileMock = services.getRecommendationProfile
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
    profileMock,
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
  loaded.page.data.birthDate = '2024-02-29'
  return loaded
}

describe('recommendation Page behavior', () => {
  it('loads and displays the server-derived profile after birthday selection', async () => {
    const { page, profileMock } = await availablePage()

    await page.changeBirthDate({ detail: { value: '2024-02-29' } })

    expect(profileMock).toHaveBeenCalledWith('2024-02-29')
    expect(page.data.profile).toEqual(profile)
    expect(page.data.profileLoading).toBe(false)
    expect(page.data.profileError).toBe('')
  })

  it('prefills a valid wish inspiration from the home-page route', async () => {
    const { page } = loadPage()

    page.onLoad({ wishInspiration: encodeURIComponent('事业') })
    await flushMicrotasks()

    expect(page.data.wishInspiration).toBe('事业')
  })

  it('keeps direct entry blank and lets the optional inspiration be toggled', async () => {
    const { page } = await availablePage()

    expect(page.data.wishInspiration).toBe('')
    page.selectWishInspiration({
      currentTarget: { dataset: { wishInspiration: '家庭' } },
    })
    expect(page.data.wishInspiration).toBe('家庭')

    page.selectWishInspiration({
      currentTarget: { dataset: { wishInspiration: '家庭' } },
    })
    expect(page.data.wishInspiration).toBe('')
  })

  it('submits the selected wish inspiration with the recommendation request', async () => {
    const { page, recommendMock } = await availablePage()
    page.data.wishInspiration = '相守'
    recommendMock.mockResolvedValueOnce(recommendation)

    await page.submitRecommendation()

    expect(recommendMock).toHaveBeenCalledWith({
      birthDate: '2024-02-29',
      wishInspiration: '相守',
      wish: undefined,
    })
  })

  it('does not show a stale profile when birthday changes twice', async () => {
    const { page, profileMock } = await availablePage()
    const first = deferred<typeof profile>()
    const second = deferred<typeof profile>()
    profileMock
      .mockReturnValueOnce(first.promise)
      .mockReturnValueOnce(second.promise)

    const firstLoad = page.changeBirthDate({ detail: { value: '2024-02-29' } })
    const secondLoad = page.changeBirthDate({ detail: { value: '1992-01-20' } })
    second.resolve({
      ...profile,
      birthDate: '1992-01-20',
      constellation: '水瓶座',
    })
    await secondLoad
    first.resolve(profile)
    await firstLoad

    expect(page.data.profile.birthDate).toBe('1992-01-20')
    expect(page.data.profile.constellation).toBe('水瓶座')
  })

  it('keeps the birthday selected and shows a retryable profile error', async () => {
    const { page, profileMock, ApiError } = await availablePage()
    profileMock.mockRejectedValueOnce(
      new ApiError('technical detail', 'NETWORK_ERROR', 'trace-secret'),
    )

    await page.changeBirthDate({ detail: { value: '2024-02-29' } })

    expect(page.data.birthDate).toBe('2024-02-29')
    expect(page.data.profile).toBeNull()
    expect(page.data.profileError)
      .toBe('网络连接失败，请检查网络后重试')
    expect(JSON.stringify(page.data)).not.toContain('technical detail')
    expect(JSON.stringify(page.data)).not.toContain('trace-secret')
  })

  it('refreshes AI availability when the page becomes visible again', async () => {
    const { page, statusMock } = await availablePage()
    statusMock.mockResolvedValueOnce({ status: 'DISABLED', available: false })

    page.onShow()
    await flushMicrotasks()

    expect(statusMock).toHaveBeenCalledTimes(2)
    expect(page.data.available).toBe(false)
    expect(page.data.statusMessage).toBe('AI 推荐暂停使用')
  })

  it('keeps the form retryable when the public status probe fails', async () => {
    const { page, statusMock } = loadPage()
    statusMock.mockReset()
    statusMock.mockRejectedValueOnce(new Error('network unavailable'))

    page.onLoad()
    await flushMicrotasks()

    expect(page.data.statusLoading).toBe(false)
    expect(page.data.available).toBe(true)
    expect(page.data.statusMessage).toBe('')
    expect(page.data.errorMessage)
      .toBe('暂时无法确认 AI 服务状态，你可以填写后重试')
  })

  it.each([
    ['birthday', (page: Record<string, any>) => page.changeBirthDate({
      detail: { value: '1992-01-20' },
    })],
    ['wish', (page: Record<string, any>) => page.updateWish({
      detail: { value: '等待时改了期望' },
    })],
    ['wish inspiration', (page: Record<string, any>) => page.selectWishInspiration({
      currentTarget: { dataset: { wishInspiration: '活力' } },
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
    expect(page.data.submitting).toBe(false)
  })

  it('does not mutate page state after onUnload while POST is pending', async () => {
    const { page, recommendMock } = await availablePage()
    const pending = deferred<typeof recommendation>()
    recommendMock.mockReturnValueOnce(pending.promise)
    const submission = page.submitRecommendation()

    page.onUnload()
    const callsAfterUnload = page.setData.mock.calls.length
    pending.resolve(recommendation)
    await submission

    expect(page.setData).toHaveBeenCalledTimes(callsAfterUnload)
    expect(page.recommendation).toBeNull()
  })

  it.each([
    ['AI_NOT_CONFIGURED', 'AI 推荐暂未开放'],
    ['AI_DISABLED', 'AI 推荐暂停使用'],
  ])('moves blocking POST error %s to unavailable state', async (
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
    expect(page.data.errorMessage).toBe('')
  })

  it.each([
    ['AI_UNAUTHORIZED', 'AI 服务鉴权失败，请联系管理员检查 API Key'],
    ['AI_MODEL_UNAVAILABLE', '当前 AI 模型不可用，请联系管理员检查模型配置'],
    ['AI_RATE_LIMITED', 'AI 请求较多，请稍后重试'],
    ['AI_TEMPORARILY_UNAVAILABLE', 'AI 服务响应超时或暂时异常，请稍后重试'],
    ['AI_INVALID_RECOMMENDATION', '本次推荐未通过校验，请重试或调整期望'],
    ['NETWORK_ERROR', '网络连接失败，请检查网络后重试'],
  ])('keeps form retryable for POST error %s', async (code, expected) => {
    const { page, recommendMock, ApiError } = await availablePage()
    page.data.wish = '保留这段期望'
    recommendMock.mockRejectedValueOnce(
      new ApiError('upstream-secret-message', code, 'trace-secret'),
    )

    await page.submitRecommendation()

    expect(page.data.available).toBe(true)
    expect(page.data.statusMessage).toBe('')
    expect(page.data.errorMessage).toBe(expected)
    expect(page.data.wish).toBe('保留这段期望')
    expect(page.data.submitting).toBe(false)
    expect(JSON.stringify(page.data)).not.toContain('upstream-secret-message')
    expect(JSON.stringify(page.data)).not.toContain('trace-secret')
  })

  it('shows the safe backend message and trace for an unknown POST code', async () => {
    const { page, recommendMock, ApiError } = await availablePage()
    recommendMock.mockRejectedValueOnce(new ApiError(
      '新的安全业务提示',
      'NEW_AI_ERROR',
      '9c34a4d7-77a1-4d9c-a5c9-5073f0570c11',
    ))

    await page.submitRecommendation()

    expect(page.data.available).toBe(true)
    expect(page.data.errorMessage).toBe(
      '新的安全业务提示'
      + '（追踪号：9c34a4d7-77a1-4d9c-a5c9-5073f0570c11）',
    )
  })

  it('allows a second submission and renders all returned plans', async () => {
    const { page, recommendMock, ApiError } = await availablePage()
    recommendMock
      .mockRejectedValueOnce(new ApiError(
        'invalid provider output',
        'AI_INVALID_RECOMMENDATION',
        'trace-secret',
      ))
      .mockResolvedValueOnce(recommendation)

    await page.submitRecommendation()
    await page.submitRecommendation()

    expect(recommendMock).toHaveBeenCalledTimes(2)
    expect(recommendMock).toHaveBeenLastCalledWith({
      birthDate: '2024-02-29',
      wishInspiration: undefined,
      wish: undefined,
    })
    expect(page.data.available).toBe(true)
    expect(page.data.errorMessage).toBe('')
    expect(page.data.result.plans).toHaveLength(2)
  })

  it('hands only the selected plan bead types to DIY and guards double tap', async () => {
    const { page, wxMock } = await availablePage()
    page.recommendation = recommendation

    const event = { currentTarget: { dataset: { index: 1 } } }
    page.enterDiy(event)
    page.enterDiy(event)

    expect(wxMock.setStorageSync).toHaveBeenCalledTimes(1)
    expect(wxMock.setStorageSync.mock.calls[0]).toEqual([
      'pending_ai_recommendation',
      JSON.stringify({ sequence: [2032, 2033] }),
    ])
    expect(wxMock.navigateTo).toHaveBeenCalledTimes(1)
    expect(wxMock.navigateTo.mock.calls[0][0].url)
      .toBe('/pages/diy/editor?source=ai')
    wxMock.navigateTo.mock.calls[0][0].success()
    page.enterDiy(event)
    expect(wxMock.navigateTo).toHaveBeenCalledTimes(2)
  })
})
