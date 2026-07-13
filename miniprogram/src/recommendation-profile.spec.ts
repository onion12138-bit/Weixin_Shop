import {
  buildRecommendationRequest,
  consumePendingAiRecommendation,
  createPendingAiRecommendation,
  deriveChineseZodiac,
  deriveConstellation,
  recommendationStatusMessage,
  recommendationFailureMessage,
  toRecommendationViewModel,
  validateRecommendationForm,
  type RecommendationForm,
  type RecommendationResponse,
} from '../miniprogram/domain/recommendation-profile'
import {
  getRecommendationStatus,
  requestRecommendation,
} from '../miniprogram/services/recommendation'
import { listAllPublicBeads } from '../miniprogram/services/catalog'
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
  manualConstellation: '',
  manualZodiac: '',
  elementPreference: '水',
  wish: '专注',
  scene: '通勤',
  colorPreference: '灰蓝',
  budget: '500',
  wristCircumferenceMm: '160',
  fitType: 'STANDARD',
  freeText: '希望日常耐看',
  ...overrides,
})

describe('recommendation birth profile', () => {
  it.each([
    ['2024-12-21', '射手座'],
    ['2024-12-22', '摩羯座'],
    ['2025-01-19', '摩羯座'],
    ['2025-01-20', '水瓶座'],
    ['2024-02-18', '水瓶座'],
    ['2024-02-19', '双鱼座'],
    ['2024-02-29', '双鱼座'],
    ['2024-03-20', '双鱼座'],
    ['2024-03-21', '白羊座'],
    ['2024-04-20', '金牛座'],
    ['2024-05-21', '双子座'],
    ['2024-06-22', '巨蟹座'],
    ['2024-07-23', '狮子座'],
    ['2024-08-23', '处女座'],
    ['2024-09-23', '天秤座'],
    ['2024-10-24', '天蝎座'],
    ['2024-11-23', '射手座'],
  ])('derives constellation boundary %s as %s', (date, expected) => {
    expect(deriveConstellation(date)).toBe(expected)
  })

  it('uses the backend Gregorian-year zodiac mapping across lunar and leap boundaries', () => {
    expect(deriveChineseZodiac('2023-12-31')).toBe('兔')
    expect(deriveChineseZodiac('2024-01-01')).toBe('龙')
    expect(deriveChineseZodiac('2024-02-04')).toBe('龙')
    expect(deriveChineseZodiac('2024-02-09')).toBe('龙')
    expect(deriveChineseZodiac('2024-02-10')).toBe('龙')
    expect(deriveChineseZodiac('2024-02-29')).toBe('龙')
  })

  it('prefers trimmed manual constellation and zodiac values over birthday derivation', () => {
    const payload = buildRecommendationRequest(validForm({
      manualConstellation: '  自定义星座  ',
      manualZodiac: '  自定义生肖  ',
    }))

    expect(payload.constellation).toBe('自定义星座')
    expect(payload.zodiac).toBe('自定义生肖')
  })

  it('derives missing manual values and sends budget, wrist and free text', () => {
    const payload = buildRecommendationRequest(validForm({
      budget: '888.88',
      wristCircumferenceMm: '172.5',
      fitType: 'LOOSE',
      freeText: '  保留在本次页面内存  ',
    }))

    expect(payload).toMatchObject({
      birthDate: '2024-02-29',
      constellation: '双鱼座',
      zodiac: '龙',
      budget: 888.88,
      wristCircumferenceMm: 172.5,
      fitType: 'LOOSE',
      freeText: '保留在本次页面内存',
    })
  })
})

describe('recommendation form validation', () => {
  it.each([
    [{ budget: '0' }, '请输入 0.01 至 999999.99 元预算'],
    [{ budget: '1000000' }, '请输入 0.01 至 999999.99 元预算'],
    [{ wristCircumferenceMm: '0' }, '请输入 1 至 1000 毫米手围'],
    [{ wristCircumferenceMm: '1000.01' }, '请输入 1 至 1000 毫米手围'],
    [{ freeText: '私'.repeat(501) }, '自由描述最多 500 个字'],
    [{ manualConstellation: '星'.repeat(17) }, '星座最多 16 个字'],
    [{ wish: '愿'.repeat(81) }, '期望最多 80 个字'],
    [{ birthDate: '2999-01-01' }, '生日不能晚于今天'],
  ])('rejects DTO boundary %o', (overrides, expected) => {
    expect(validateRecommendationForm(
      validForm(overrides),
      '2026-07-13',
    )).toBe(expected)
  })

  it('accepts all Task 8 DTO upper boundaries', () => {
    expect(validateRecommendationForm(validForm({
      manualConstellation: '星'.repeat(16),
      manualZodiac: '肖'.repeat(16),
      elementPreference: '行'.repeat(16),
      wish: '愿'.repeat(80),
      scene: '景'.repeat(80),
      colorPreference: '色'.repeat(80),
      budget: '999999.99',
      wristCircumferenceMm: '1000',
      freeText: '私'.repeat(500),
    }), '2026-07-13')).toBeNull()
  })
})

describe('recommendation availability copy', () => {
  it.each([
    ['NOT_CONFIGURED', false, 'AI 推荐暂未开放'],
    ['DISABLED', false, 'AI 推荐暂停使用'],
    ['UNKNOWN', false, 'AI 推荐服务暂时不可用，请稍后重试'],
  ])('maps %s to confirmed customer copy', (status, available, expected) => {
    expect(recommendationStatusMessage(status, available)).toBe(expected)
  })

  it('has no unavailable copy only for an explicitly available service', () => {
    expect(recommendationStatusMessage('AVAILABLE', true)).toBe('')
    expect(recommendationStatusMessage('AVAILABLE', false))
      .toBe('AI 推荐服务暂时不可用，请稍后重试')
  })

  it.each([
    ['AI_NOT_CONFIGURED', 'AI 推荐暂未开放'],
    ['AI_DISABLED', 'AI 推荐暂停使用'],
    ['AI_MODEL_UNAVAILABLE', 'AI 推荐服务暂时不可用，请稍后重试'],
    ['trace-secret', 'AI 推荐服务暂时不可用，请稍后重试'],
  ])('maps POST business code %s without exposing technical details', (
    code,
    expected,
  ) => {
    expect(recommendationFailureMessage(code)).toBe(expected)
  })
})

describe('single recommendation view model', () => {
  const response: RecommendationResponse = {
    schemeName: '灰蓝通勤',
    sequence: [2001, 2002, 2001],
    quantities: { 2001: 2, 2002: 1 },
    items: [
      {
        skuId: 2001,
        name: '白水晶 8mm',
        imageUrl: 'https://example.test/a.png',
        quantity: 2,
        reason: '清透层次',
      },
      {
        skuId: 2002,
        name: '紫水晶 8mm',
        imageUrl: 'https://example.test/b.png',
        quantity: 1,
        reason: '紫色点缀',
      },
    ],
    summary: '适合通勤。',
    cordOption: {
      id: 6001,
      code: 'ELASTIC-CLEAR',
      name: '透明弹力绳',
      colorHex: '#E8E4DC',
    },
    beadAmount: 300,
    craftFee: 10,
    cordFee: 4.8,
    totalAmount: 999.99,
    quoteFingerprint: 'server-fingerprint',
    culturalDisclaimer: '星座、生肖、五行及寓意内容仅供文化娱乐参考。',
  }

  it('renders one ordered design with item images, quantities and reasons', () => {
    const result = toRecommendationViewModel(response)

    expect(result.schemeName).toBe('灰蓝通勤')
    expect(result.items[0]).toMatchObject({
      imageUrl: 'https://example.test/a.png',
      quantityText: '2 颗',
      reason: '清透层次',
    })
    expect(result.orderedBeads.map((item) => item.name))
      .toEqual(['白水晶 8mm', '紫水晶 8mm', '白水晶 8mm'])
    expect(result.culturalDisclaimer).toContain('文化娱乐参考')
  })

  it('formats the server total directly without calculating it from components', () => {
    const result = toRecommendationViewModel(response)

    expect(result.totalAmountText).toBe('¥999.99')
    expect(result.amountDetails.map((item) => item.amountText))
      .toEqual(['¥300.00', '¥10.00', '¥4.80'])
  })

  it('creates a storage handoff containing only the four allowed fields', () => {
    const handoff = createPendingAiRecommendation(response, validForm({
      birthDate: '1992-01-20',
      freeText: '不得进入 storage',
      wristCircumferenceMm: '168',
      fitType: 'SNUG',
    }))

    expect(handoff).toEqual({
      sequence: [2001, 2002, 2001],
      wristCircumferenceMm: 168,
      fitType: 'SNUG',
      cordOptionId: 6001,
    })
    expect(JSON.stringify(handoff)).not.toContain('birthDate')
    expect(JSON.stringify(handoff)).not.toContain('freeText')
    expect(Object.keys(handoff).sort()).toEqual([
      'cordOptionId',
      'fitType',
      'sequence',
      'wristCircumferenceMm',
    ])
  })
})

describe('recommendation API adapter', () => {
  beforeEach(() => {
    requestMock.mockReset()
    absolutePublicUrlMock.mockClear()
  })

  it('loads status from the Task 8 plural customer endpoint', async () => {
    requestMock.mockResolvedValue({ status: 'AVAILABLE', available: true })

    await expect(getRecommendationStatus()).resolves.toEqual({
      status: 'AVAILABLE',
      available: true,
    })
    expect(requestMock).toHaveBeenCalledWith('/app/recommendations/status')
  })

  it('loads a second public-catalog page so its SKU remains a valid AI handoff', async () => {
    const firstPageBead = {
      id: 2001,
      skuCode: 'BEAD-2001',
      name: '第一页珠子',
      material: '水晶',
      color: '透明',
      diameterMm: 8,
      salePrice: 10,
      coverUrl: '/api/public/files/first.png',
      tags: [],
    }
    const secondPageBead = {
      ...firstPageBead,
      id: 2031,
      skuCode: 'BEAD-2031',
      name: '第二页合法珠子',
      coverUrl: '/api/public/files/second.png',
    }
    requestMock
      .mockResolvedValueOnce({
        items: [firstPageBead],
        total: 101,
        page: 1,
        size: 100,
      })
      .mockResolvedValueOnce({
        items: [secondPageBead],
        total: 101,
        page: 2,
        size: 100,
      })

    const beads = await listAllPublicBeads()
    const stored = {
      sequence: [2031],
      wristCircumferenceMm: 165,
      fitType: 'STANDARD',
      cordOptionId: 6001,
    }
    const draft = consumePendingAiRecommendation(
      'ai',
      { get: () => stored, remove: jest.fn() },
      {
        allowedSkuIds: beads.map((item) => item.id),
        allowedCordOptionIds: [6001],
        minWristCircumferenceMm: 120,
        maxWristCircumferenceMm: 220,
        maxSequenceLength: 30,
      },
    )

    expect(requestMock).toHaveBeenNthCalledWith(
      1,
      '/public/catalog/beads?page=1&size=100',
    )
    expect(requestMock).toHaveBeenNthCalledWith(
      2,
      '/public/catalog/beads?page=2&size=100',
    )
    expect(beads.map((item) => item.id)).toEqual([2001, 2031])
    expect(draft?.sequence).toEqual([2031])
  })

  it('posts the exact DTO and normalizes every returned item image', async () => {
    const payload = buildRecommendationRequest(validForm())
    const response: RecommendationResponse = {
      schemeName: '灰蓝通勤',
      sequence: [2001],
      quantities: { 2001: 1 },
      items: [{
        skuId: 2001,
        name: '白水晶 8mm',
        imageUrl: '/api/public/files/a.png',
        quantity: 1,
        reason: '清透层次',
      }],
      summary: '适合通勤。',
      cordOption: {
        id: 6001,
        code: 'ELASTIC-CLEAR',
        name: '透明弹力绳',
        colorHex: '#E8E4DC',
      },
      beadAmount: 100,
      craftFee: 10,
      cordFee: 4.8,
      totalAmount: 114.8,
      quoteFingerprint: 'server-fingerprint',
      culturalDisclaimer: '仅供文化娱乐参考。',
    }
    requestMock.mockResolvedValue(response)

    await expect(requestRecommendation(payload)).resolves.toMatchObject({
      items: [{ imageUrl: 'https://cdn.test/api/public/files/a.png' }],
      totalAmount: 114.8,
    })
    expect(requestMock).toHaveBeenCalledWith('/app/recommendations', {
      method: 'POST',
      data: payload,
    })
    expect(absolutePublicUrlMock)
      .toHaveBeenCalledWith('/api/public/files/a.png')
  })
})
