export type RecommendationFitType = 'SNUG' | 'STANDARD' | 'LOOSE'

export interface RecommendationForm {
  birthDate: string
  manualConstellation: string
  manualZodiac: string
  elementPreference: string
  wish: string
  scene: string
  colorPreference: string
  budget: string
  wristCircumferenceMm: string
  fitType: RecommendationFitType
  freeText: string
}

export interface RecommendationRequest {
  birthDate?: string
  constellation?: string
  zodiac?: string
  elementPreference?: string
  wish?: string
  scene?: string
  colorPreference?: string
  budget: number
  wristCircumferenceMm: number
  fitType: RecommendationFitType
  freeText?: string
}

export interface RecommendationStatusResponse {
  status: string
  available: boolean
}

export interface RecommendationItem {
  skuId: number
  name: string
  imageUrl: string
  quantity: number
  reason: string
}

export interface RecommendationResponse {
  schemeName: string
  sequence: number[]
  quantities: Record<number, number>
  items: RecommendationItem[]
  summary: string
  cordOption: {
    id: number
    code: string
    name: string
    colorHex: string
  }
  beadAmount: number
  craftFee: number
  cordFee: number
  totalAmount: number
  quoteFingerprint: string
  culturalDisclaimer: string
}

export interface RecommendationViewModel {
  schemeName: string
  summary: string
  items: Array<RecommendationItem & { quantityText: string }>
  orderedBeads: Array<{
    order: number
    skuId: number
    name: string
    imageUrl: string
  }>
  cordOptionName: string
  amountDetails: Array<{ label: string; amountText: string }>
  totalAmountText: string
  culturalDisclaimer: string
}

export interface PendingAiRecommendation {
  sequence: number[]
  wristCircumferenceMm: number
  fitType: RecommendationFitType
  cordOptionId: number
}

export interface AiRecommendationEditorDraft extends PendingAiRecommendation {
  schemeId: 0
  schemeVersion: 0
  name: 'AI 推荐手串'
  selectedIndex: -1
}

export interface PendingRecommendationStorage {
  get(key: string): unknown
  remove(key: string): void
}

export interface AiHandoffValidation {
  allowedSkuIds: number[]
  allowedCordOptionIds: number[]
  minWristCircumferenceMm: number
  maxWristCircumferenceMm: number
  maxSequenceLength: number
}

export const PENDING_AI_RECOMMENDATION_KEY = 'pending_ai_recommendation'

const CONSTELLATION_LIMITS: Array<[number, string]> = [
  [119, '摩羯座'],
  [218, '水瓶座'],
  [320, '双鱼座'],
  [419, '白羊座'],
  [520, '金牛座'],
  [621, '双子座'],
  [722, '巨蟹座'],
  [822, '狮子座'],
  [922, '处女座'],
  [1023, '天秤座'],
  [1122, '天蝎座'],
  [1221, '射手座'],
]

const CHINESE_ZODIAC = [
  '鼠', '牛', '虎', '兔', '龙', '蛇',
  '马', '羊', '猴', '鸡', '狗', '猪',
]

const FIT_TYPES: RecommendationFitType[] = ['SNUG', 'STANDARD', 'LOOSE']
const GENERIC_UNAVAILABLE = 'AI 推荐服务暂时不可用，请稍后重试'

/** Matches Task 8: ordinary Gregorian-year mapping, not lunar-new-year or 立春. */
export function deriveChineseZodiac(date: string): string {
  const parsed = parseDate(date)
  if (!parsed) return ''
  const index = positiveModulo(parsed.year - 4, CHINESE_ZODIAC.length)
  return CHINESE_ZODIAC[index]
}

export function deriveConstellation(date: string): string {
  const parsed = parseDate(date)
  if (!parsed) return ''
  const marker = parsed.month * 100 + parsed.day
  if (marker >= 1222) return '摩羯座'
  return CONSTELLATION_LIMITS.find(([limit]) => marker <= limit)?.[1]
    || '摩羯座'
}

export function validateRecommendationForm(
  form: RecommendationForm,
  today: string,
): string | null {
  if (form.birthDate) {
    const birthDate = parseDate(form.birthDate)
    if (!birthDate) return '请输入有效生日'
    const todayDate = parseDate(today)
    if (todayDate && dateMarker(birthDate) > dateMarker(todayDate)) {
      return '生日不能晚于今天'
    }
  }

  const textLimits: Array<[string, number, string]> = [
    [form.manualConstellation, 16, '星座最多 16 个字'],
    [form.manualZodiac, 16, '生肖最多 16 个字'],
    [form.elementPreference, 16, '五行偏好最多 16 个字'],
    [form.wish, 80, '期望最多 80 个字'],
    [form.scene, 80, '场景最多 80 个字'],
    [form.colorPreference, 80, '偏好颜色最多 80 个字'],
    [form.freeText, 500, '自由描述最多 500 个字'],
  ]
  for (const [value, max, message] of textLimits) {
    if (value.trim().length > max) return message
  }

  const budget = strictNumber(form.budget)
  if (budget === null || budget < 0.01 || budget > 999999.99) {
    return '请输入 0.01 至 999999.99 元预算'
  }
  const wrist = strictNumber(form.wristCircumferenceMm)
  if (wrist === null || wrist < 1 || wrist > 1000) {
    return '请输入 1 至 1000 毫米手围'
  }
  if (!FIT_TYPES.includes(form.fitType)) return '请选择松紧偏好'
  return null
}

export function buildRecommendationRequest(
  form: RecommendationForm,
): RecommendationRequest {
  const birthDate = optionalText(form.birthDate)
  const manualConstellation = optionalText(form.manualConstellation)
  const manualZodiac = optionalText(form.manualZodiac)
  return {
    birthDate,
    constellation: manualConstellation
      || (birthDate ? deriveConstellation(birthDate) : undefined),
    zodiac: manualZodiac
      || (birthDate ? deriveChineseZodiac(birthDate) : undefined),
    elementPreference: optionalText(form.elementPreference),
    wish: optionalText(form.wish),
    scene: optionalText(form.scene),
    colorPreference: optionalText(form.colorPreference),
    budget: Number(form.budget.trim()),
    wristCircumferenceMm: Number(form.wristCircumferenceMm.trim()),
    fitType: form.fitType,
    freeText: optionalText(form.freeText),
  }
}

export function recommendationStatusMessage(
  status: unknown,
  available: boolean,
): string {
  if (status === 'AVAILABLE' && available) return ''
  if (status === 'NOT_CONFIGURED') return 'AI 推荐暂未开放'
  if (status === 'DISABLED') return 'AI 推荐暂停使用'
  return GENERIC_UNAVAILABLE
}

export function recommendationFailureMessage(code: unknown): string {
  if (code === 'AI_NOT_CONFIGURED') return 'AI 推荐暂未开放'
  if (code === 'AI_DISABLED') return 'AI 推荐暂停使用'
  return GENERIC_UNAVAILABLE
}

export function toRecommendationViewModel(
  response: RecommendationResponse,
): RecommendationViewModel {
  const itemBySkuId = new Map(
    response.items.map((item) => [item.skuId, item]),
  )
  return {
    schemeName: response.schemeName,
    summary: response.summary,
    items: response.items.map((item) => ({
      ...item,
      quantityText: `${item.quantity} 颗`,
    })),
    orderedBeads: response.sequence.flatMap((skuId, index) => {
      const item = itemBySkuId.get(skuId)
      return item ? [{
        order: index + 1,
        skuId,
        name: item.name,
        imageUrl: item.imageUrl,
      }] : []
    }),
    cordOptionName: response.cordOption.name,
    amountDetails: [
      { label: '珠子金额', amountText: formatAmount(response.beadAmount) },
      { label: '制作费', amountText: formatAmount(response.craftFee) },
      { label: '绳材费', amountText: formatAmount(response.cordFee) },
    ],
    totalAmountText: formatAmount(response.totalAmount),
    culturalDisclaimer: response.culturalDisclaimer,
  }
}

export function createPendingAiRecommendation(
  response: RecommendationResponse,
  form: RecommendationForm,
): PendingAiRecommendation {
  return {
    sequence: response.sequence.slice(),
    wristCircumferenceMm: Number(form.wristCircumferenceMm.trim()),
    fitType: form.fitType,
    cordOptionId: response.cordOption.id,
  }
}

export function consumePendingAiRecommendation(
  source: string | undefined,
  storage: PendingRecommendationStorage,
  validation: AiHandoffValidation,
): AiRecommendationEditorDraft | null {
  if (source !== 'ai') return null

  let raw: unknown
  let removed = false
  try {
    raw = storage.get(PENDING_AI_RECOMMENDATION_KEY)
  } catch {
    return null
  } finally {
    try {
      storage.remove(PENDING_AI_RECOMMENDATION_KEY)
      removed = true
    } catch {
      removed = false
    }
  }

  if (!removed) return null
  const handoff = validatePendingAiRecommendation(raw, validation)
  if (!handoff) return null
  return {
    schemeId: 0,
    schemeVersion: 0,
    name: 'AI 推荐手串',
    ...handoff,
    selectedIndex: -1,
  }
}

function validatePendingAiRecommendation(
  raw: unknown,
  validation: AiHandoffValidation,
): PendingAiRecommendation | null {
  const value = parseStoredValue(raw)
  if (!isRecord(value)) return null
  const allowedKeys = [
    'sequence',
    'wristCircumferenceMm',
    'fitType',
    'cordOptionId',
  ]
  const keys = Object.keys(value)
  if (
    keys.length !== allowedKeys.length
    || keys.some((key) => !allowedKeys.includes(key))
  ) return null

  const sequence = value.sequence
  if (
    !Array.isArray(sequence)
    || sequence.length === 0
    || sequence.length > validation.maxSequenceLength
  ) return null
  const allowedSkuIds = new Set(validation.allowedSkuIds)
  if (sequence.some((id) => (
    !Number.isInteger(id) || Number(id) <= 0 || !allowedSkuIds.has(Number(id))
  ))) return null

  const wrist = value.wristCircumferenceMm
  if (
    typeof wrist !== 'number'
    || !Number.isFinite(wrist)
    || wrist < validation.minWristCircumferenceMm
    || wrist > validation.maxWristCircumferenceMm
  ) return null
  if (
    typeof value.fitType !== 'string'
    || !FIT_TYPES.includes(value.fitType as RecommendationFitType)
  ) return null
  const cordOptionId = value.cordOptionId
  if (
    !Number.isInteger(cordOptionId)
    || !validation.allowedCordOptionIds.includes(Number(cordOptionId))
  ) return null

  return {
    sequence: sequence.map(Number),
    wristCircumferenceMm: wrist,
    fitType: value.fitType as RecommendationFitType,
    cordOptionId: Number(cordOptionId),
  }
}

function parseStoredValue(raw: unknown): unknown {
  if (typeof raw !== 'string') return raw
  try {
    return JSON.parse(raw) as unknown
  } catch {
    return null
  }
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value)
}

function optionalText(value: string): string | undefined {
  const trimmed = value.trim()
  return trimmed || undefined
}

function strictNumber(value: string): number | null {
  const trimmed = value.trim()
  if (!trimmed) return null
  const parsed = Number(trimmed)
  return Number.isFinite(parsed) ? parsed : null
}

function formatAmount(value: number): string {
  return `¥${Number(value).toFixed(2)}`
}

function positiveModulo(value: number, divisor: number): number {
  return ((value % divisor) + divisor) % divisor
}

interface DateParts {
  year: number
  month: number
  day: number
}

function parseDate(value: string): DateParts | null {
  const match = /^(\d{4})-(\d{2})-(\d{2})$/.exec(value)
  if (!match) return null
  const year = Number(match[1])
  const month = Number(match[2])
  const day = Number(match[3])
  if (month < 1 || month > 12 || day < 1 || day > daysInMonth(year, month)) {
    return null
  }
  return { year, month, day }
}

function daysInMonth(year: number, month: number): number {
  if (month === 2) {
    const leap = year % 400 === 0 || (year % 4 === 0 && year % 100 !== 0)
    return leap ? 29 : 28
  }
  return [4, 6, 9, 11].includes(month) ? 30 : 31
}

function dateMarker(date: DateParts): number {
  return date.year * 10000 + date.month * 100 + date.day
}
