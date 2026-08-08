import {
  buildRecommendationRequest,
  consumePendingAiRecommendation,
  createPendingAiRecommendation,
  isRecommendationUnavailableCode,
  recommendationFailureMessage,
  recommendationStatusMessage,
  normalizeWishInspiration,
  toRecommendationViewModel,
  validateRecommendationForm,
  type RecommendationForm,
  type RecommendationResponse,
} from '../miniprogram/domain/recommendation-profile'
import {
  getRecommendationProfile,
  getRecommendationStatus,
  requestRecommendation,
} from '../miniprogram/services/recommendation'
import {
  absolutePublicUrl,
  request,
} from '../miniprogram/services/request'

jest.mock('../miniprogram/services/request', () => ({
  absolutePublicUrl: jest.fn((url: string | null) => (
    url ? `https://cdn.test${url}` : url
  )),
  request: jest.fn(),
}))

const requestMock = request as jest.MockedFunction<typeof request>
const absolutePublicUrlMock = absolutePublicUrl as jest.MockedFunction<
  typeof absolutePublicUrl
>

const validForm = (overrides: Partial<RecommendationForm> = {}): RecommendationForm => ({
  birthDate: '2024-02-29',
  wishInspiration: '',
  wish: '希望日常佩戴时更从容',
  ...overrides,
})

const response: RecommendationResponse = {
  profile: {
    birthDate: '2024-02-29',
    constellation: '双鱼座',
    zodiac: '龙',
    constellationElement: '水',
  },
  plans: [
    {
      schemeName: '清透水光',
      summary: '以清透层次为主。',
      items: [
        {
          skuId: 2001,
          name: '白水晶 8mm',
          imageUrl: '/api/public/files/a.png',
          reason: '清透通透',
        },
        {
          skuId: 2002,
          name: '海蓝宝 8mm',
          imageUrl: '/api/public/files/b.png',
          reason: '增加蓝色层次',
        },
      ],
    },
    {
      schemeName: '柔和月光',
      summary: '偏柔和的日常组合。',
      items: [{
        skuId: 2003,
        name: '月光石 8mm',
        imageUrl: '/api/public/files/c.png',
        reason: '光感柔和',
      }],
    },
  ],
  culturalDisclaimer: '星座、生肖及寓意内容仅供文化娱乐参考。',
}

describe('recommendation form contract', () => {
  it.each([
    [{ birthDate: '' }, '请选择生日'],
    [{ birthDate: '2023-02-29' }, '请选择有效生日'],
    [{ birthDate: '2999-01-01' }, '生日不能晚于今天'],
    [{ wishInspiration: '未知心愿' }, '请选择有效的心愿灵感'],
    [{ wish: '愿'.repeat(81) }, '期望最多 80 个字'],
  ])('rejects invalid input %o', (overrides, expected) => {
    expect(validateRecommendationForm(
      validForm(overrides),
      '2026-07-19',
    )).toBe(expected)
  })

  it('accepts a required birthday with empty optional wish fields', () => {
    expect(validateRecommendationForm(
      validForm({ wishInspiration: '', wish: '' }),
      '2026-07-19',
    )).toBeNull()
  })

  it('builds a request containing a selected inspiration and trimmed optional wish', () => {
    expect(buildRecommendationRequest(validForm({
      wishInspiration: '事业',
      wish: '  从容  ',
    }))).toEqual({
      birthDate: '2024-02-29',
      wishInspiration: '事业',
      wish: '从容',
    })
    expect(buildRecommendationRequest(validForm({
      wishInspiration: '   ',
      wish: '   ',
    }))).toEqual({
      birthDate: '2024-02-29',
      wishInspiration: undefined,
      wish: undefined,
    })
  })

  it('normalizes only the eight supported inspiration values', () => {
    expect(normalizeWishInspiration('  相守  ')).toBe('相守')
    expect(normalizeWishInspiration('好运')).toBe('')
    expect(normalizeWishInspiration(undefined)).toBe('')
  })
})

describe('recommendation availability copy', () => {
  it.each([
    ['NOT_CONFIGURED', false, 'AI 推荐暂未开放'],
    ['DISABLED', false, 'AI 推荐暂停使用'],
    ['UNKNOWN', false, 'AI 推荐服务暂时不可用，请稍后重试'],
  ])('maps %s to customer copy', (status, available, expected) => {
    expect(recommendationStatusMessage(status, available)).toBe(expected)
  })

  it.each([
    ['AI_NOT_CONFIGURED', 'AI 推荐暂未开放'],
    ['AI_DISABLED', 'AI 推荐暂停使用'],
    ['AI_PROMPT_NOT_CONFIGURED', 'AI 推荐规则尚未配置，请联系管理员'],
    ['AI_UNAUTHORIZED', 'AI 服务鉴权失败，请联系管理员检查 API Key'],
    ['AI_MODEL_UNAVAILABLE', '当前 AI 模型不可用，请联系管理员检查模型配置'],
    ['AI_RATE_LIMITED', 'AI 请求较多，请稍后重试'],
    ['AI_TEMPORARILY_UNAVAILABLE', 'AI 服务响应超时或暂时异常，请稍后重试'],
    ['AI_TIMEOUT', 'AI 服务响应超时，请稍后重试'],
    ['AI_UPSTREAM_ERROR', 'AI 服务暂时异常，请稍后重试'],
    ['AI_INVALID_RECOMMENDATION', '本次推荐未通过校验，请重试或调整期望'],
    ['AI_RESPONSE_FORMAT_INVALID', 'AI 返回的方案格式不完整，请重试'],
    ['AI_RESULT_COUNT_INVALID', 'AI 返回的方案数量不正确，请重试'],
    ['AI_RESULT_ITEM_COUNT_INVALID', 'AI 返回的单套珠子种类数量不正确，请重试'],
    ['AI_RESULT_DUPLICATE_SKU', 'AI 在同一套方案中重复选择了珠子，请重试'],
    ['AI_RESULT_SKU_INVALID', 'AI 选择的珠子已不可用，请重试'],
    ['AI_RESPONSE_CONTENT_UNSAFE', 'AI 返回的文案未通过安全校验，请重试'],
    ['BIRTH_DATE_REQUIRED', '请选择生日'],
    ['BIRTH_DATE_INVALID', '生日信息无效，请重新选择'],
    ['CATALOG_EMPTY', '暂无可推荐的在售珠子，请稍后重试'],
    ['NETWORK_ERROR', '网络连接失败，请检查网络后重试'],
    ['trace-secret', '生成推荐失败，请稍后重试'],
  ])('maps POST code %s without exposing technical details', (code, expected) => {
    expect(recommendationFailureMessage(code)).toBe(expected)
  })

  it('shows a safe backend message and trace id for an unknown code', () => {
    expect(recommendationFailureMessage(
      'NEW_AI_ERROR',
      '暂时无法生成这类方案',
      '9c34a4d7-77a1-4d9c-a5c9-5073f0570c11',
    )).toBe(
      '暂时无法生成这类方案'
      + '（追踪号：9c34a4d7-77a1-4d9c-a5c9-5073f0570c11）',
    )
  })

  it('does not display a suspicious unknown server message', () => {
    expect(recommendationFailureMessage(
      'NEW_AI_ERROR',
      'Authorization: Bearer secret-token',
      'invalid trace!',
    )).toBe('生成推荐失败，请稍后重试')
  })

  it.each([
    ['AI_NOT_CONFIGURED', true],
    ['AI_DISABLED', true],
    ['AI_INVALID_RECOMMENDATION', false],
    ['AI_TEMPORARILY_UNAVAILABLE', false],
    ['NETWORK_ERROR', false],
  ])('marks only configuration state %s as unavailable', (code, expected) => {
    expect(isRecommendationUnavailableCode(code)).toBe(expected)
  })
})

describe('multi-plan recommendation view model and DIY seed', () => {
  it('keeps every dynamic plan and omits price, wrist and fit fields', () => {
    const result = toRecommendationViewModel(response)

    expect(result.plans).toHaveLength(2)
    expect(result.plans.map((plan) => plan.numberLabel))
      .toEqual(['方案 1', '方案 2'])
    expect(result.plans[0].items.map((item) => item.name))
      .toEqual(['白水晶 8mm', '海蓝宝 8mm'])
    expect(JSON.stringify(result)).not.toMatch(/totalAmount|budget|wrist|fitType/)
  })

  it('creates a DIY seed with every bead type once and no sizing or price data', () => {
    const seed = createPendingAiRecommendation({
      ...response.plans[0],
      items: [
        response.plans[0].items[0],
        response.plans[0].items[0],
        response.plans[0].items[1],
      ],
    })

    expect(seed).toEqual({ sequence: [2001, 2002] })
    expect(Object.keys(seed)).toEqual(['sequence'])
  })

  it('applies existing DIY defaults only when the seed is consumed', () => {
    expect(consumePendingAiRecommendation(
      'ai',
      { get: () => ({ sequence: [2001, 2002] }), remove: jest.fn() },
      {
        allowedSkuIds: [2001, 2002],
        allowedCordOptionIds: [7001],
        minWristCircumferenceMm: 120,
        maxWristCircumferenceMm: 220,
        maxSequenceLength: 30,
      },
    )).toEqual({
      schemeId: 0,
      schemeVersion: 0,
      name: 'AI 推荐手串',
      sequence: [2001, 2002],
      wristCircumferenceMm: 160,
      fitType: 'STANDARD',
      cordOptionId: 7001,
      selectedIndex: -1,
    })
  })
})

describe('recommendation API adapter', () => {
  beforeEach(() => {
    requestMock.mockReset()
    absolutePublicUrlMock.mockClear()
  })

  it('loads status and the server-derived birthday profile publicly', async () => {
    requestMock
      .mockResolvedValueOnce({ status: 'AVAILABLE', available: true })
      .mockResolvedValueOnce(response.profile)

    await expect(getRecommendationStatus()).resolves.toEqual({
      status: 'AVAILABLE',
      available: true,
    })
    await expect(getRecommendationProfile('2024-02-29'))
      .resolves.toEqual(response.profile)

    expect(requestMock).toHaveBeenNthCalledWith(
      1,
      '/public/recommendations/status',
    )
    expect(requestMock).toHaveBeenNthCalledWith(
      2,
      '/public/recommendations/profile?birthDate=2024-02-29',
    )
  })

  it('posts only the consultation DTO and normalizes every nested item image', async () => {
    const payload = buildRecommendationRequest(validForm())
    requestMock.mockResolvedValue(response)

    const actual = await requestRecommendation(payload)

    expect(actual.plans[0].items[0].imageUrl)
      .toBe('https://cdn.test/api/public/files/a.png')
    expect(actual.plans[1].items[0].imageUrl)
      .toBe('https://cdn.test/api/public/files/c.png')
    expect(requestMock).toHaveBeenCalledWith('/app/recommendations', {
      method: 'POST',
      data: payload,
      timeout: 70000,
    })
    expect(absolutePublicUrlMock).toHaveBeenCalledTimes(3)
  })
})
