export type RecommendationFitType = 'SNUG' | 'STANDARD' | 'LOOSE'

export const WISH_INSPIRATION_OPTIONS = [
  '财运',
  '姻缘',
  '事业',
  '平安',
  '学业',
  '相守',
  '家庭',
  '活力',
] as const

export type WishInspiration = typeof WISH_INSPIRATION_OPTIONS[number]

export interface RecommendationForm {
  birthDate: string
  wishInspiration: string
  wish: string
}

export interface RecommendationRequest {
  birthDate: string
  wishInspiration?: WishInspiration
  wish?: string
}

export interface RecommendationStatusResponse {
  status: string
  available: boolean
}

export interface RecommendationProfileResponse {
  birthDate: string
  constellation: string
  zodiac: string
  constellationElement: string
}

export interface RecommendationItem {
  skuId: number
  name: string
  imageUrl: string
  reason: string
}

export interface RecommendationPlan {
  schemeName: string
  items: RecommendationItem[]
  summary: string
}

export interface RecommendationResponse {
  profile: RecommendationProfileResponse
  plans: RecommendationPlan[]
  culturalDisclaimer: string
}

export interface RecommendationViewModel {
  plans: Array<RecommendationPlan & {
    key: string
    numberLabel: string
  }>
  culturalDisclaimer: string
}

export interface PendingAiRecommendation {
  sequence: number[]
}

export interface AiRecommendationEditorDraft {
  schemeId: 0
  schemeVersion: 0
  name: 'AI 推荐手串'
  sequence: number[]
  wristCircumferenceMm: number
  fitType: RecommendationFitType
  cordOptionId: number
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

const GENERIC_UNAVAILABLE = 'AI 推荐服务暂时不可用，请稍后重试'

export function validateRecommendationForm(
  form: RecommendationForm,
  today: string,
): string | null {
  if (!form.birthDate.trim()) return '请选择生日'
  const birthDate = parseDate(form.birthDate)
  if (!birthDate) return '请选择有效生日'
  const todayDate = parseDate(today)
  if (todayDate && dateMarker(birthDate) > dateMarker(todayDate)) {
    return '生日不能晚于今天'
  }
  if (form.wishInspiration.trim() && !normalizeWishInspiration(form.wishInspiration)) {
    return '请选择有效的心愿灵感'
  }
  if (form.wish.trim().length > 80) return '期望最多 80 个字'
  return null
}

export function buildRecommendationRequest(
  form: RecommendationForm,
): RecommendationRequest {
  return {
    birthDate: form.birthDate.trim(),
    wishInspiration: normalizeWishInspiration(form.wishInspiration) || undefined,
    wish: optionalText(form.wish),
  }
}

export function normalizeWishInspiration(
  value: unknown,
): WishInspiration | '' {
  if (typeof value !== 'string') return ''
  const normalized = value.trim()
  return (WISH_INSPIRATION_OPTIONS as readonly string[]).includes(normalized)
    ? normalized as WishInspiration
    : ''
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

export function recommendationFailureMessage(
  code: unknown,
  fallbackMessage?: string,
  traceId?: string,
): string {
  if (code === 'AI_NOT_CONFIGURED') return 'AI 推荐暂未开放'
  if (code === 'AI_DISABLED') return 'AI 推荐暂停使用'
  if (code === 'AI_PROMPT_NOT_CONFIGURED') {
    return 'AI 推荐规则尚未配置，请联系管理员'
  }
  if (code === 'AI_UNAUTHORIZED') {
    return 'AI 服务鉴权失败，请联系管理员检查 API Key'
  }
  if (code === 'AI_MODEL_UNAVAILABLE') {
    return '当前 AI 模型不可用，请联系管理员检查模型配置'
  }
  if (code === 'AI_RATE_LIMITED') return 'AI 请求较多，请稍后重试'
  if (code === 'AI_TEMPORARILY_UNAVAILABLE') {
    return 'AI 服务响应超时或暂时异常，请稍后重试'
  }
  if (code === 'AI_TIMEOUT') return 'AI 服务响应超时，请稍后重试'
  if (code === 'AI_UPSTREAM_ERROR') {
    return 'AI 服务暂时异常，请稍后重试'
  }
  if (code === 'AI_INVALID_RECOMMENDATION') {
    return '本次推荐未通过校验，请重试或调整期望'
  }
  if (code === 'AI_RESPONSE_FORMAT_INVALID') {
    return 'AI 返回的方案格式不完整，请重试'
  }
  if (code === 'AI_RESULT_COUNT_INVALID') {
    return 'AI 返回的方案数量不正确，请重试'
  }
  if (code === 'AI_RESULT_ITEM_COUNT_INVALID') {
    return 'AI 返回的单套珠子种类数量不正确，请重试'
  }
  if (code === 'AI_RESULT_DUPLICATE_SKU') {
    return 'AI 在同一套方案中重复选择了珠子，请重试'
  }
  if (code === 'AI_RESULT_SKU_INVALID') {
    return 'AI 选择的珠子已不可用，请重试'
  }
  if (code === 'AI_RESPONSE_CONTENT_UNSAFE') {
    return 'AI 返回的文案未通过安全校验，请重试'
  }
  if (code === 'AI_SERVER_MISCONFIGURED') {
    return 'AI 服务配置异常，请联系管理员'
  }
  if (code === 'BIRTH_DATE_REQUIRED') return '请选择生日'
  if (code === 'BIRTH_DATE_INVALID') return '生日信息无效，请重新选择'
  if (code === 'CATALOG_EMPTY') return '暂无可推荐的在售珠子，请稍后重试'
  if (code === 'NETWORK_ERROR') return '网络连接失败，请检查网络后重试'
  const message = safeServerMessage(fallbackMessage)
    || '生成推荐失败，请稍后重试'
  const safeTraceId = safeTrace(traceId)
  return safeTraceId ? `${message}（追踪号：${safeTraceId}）` : message
}

export function isRecommendationUnavailableCode(code: unknown): boolean {
  return code === 'AI_NOT_CONFIGURED' || code === 'AI_DISABLED'
}

export function toRecommendationViewModel(
  response: RecommendationResponse,
): RecommendationViewModel {
  return {
    plans: response.plans.map((plan, index) => ({
      ...plan,
      key: `${index + 1}-${plan.schemeName}`,
      numberLabel: `方案 ${index + 1}`,
    })),
    culturalDisclaimer: response.culturalDisclaimer,
  }
}

export function createPendingAiRecommendation(
  plan: RecommendationPlan,
): PendingAiRecommendation {
  return {
    sequence: Array.from(new Set(plan.items.map((item) => item.skuId))),
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
    sequence: handoff.sequence,
    wristCircumferenceMm: Math.min(
      validation.maxWristCircumferenceMm,
      Math.max(validation.minWristCircumferenceMm, 160),
    ),
    fitType: 'STANDARD',
    cordOptionId: validation.allowedCordOptionIds[0],
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

  if (
    validation.allowedCordOptionIds.length === 0
    || validation.minWristCircumferenceMm > validation.maxWristCircumferenceMm
  ) return null

  return {
    sequence: sequence.map(Number),
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

function safeServerMessage(value: string | undefined): string {
  if (!value) return ''
  const normalized = value.replace(/[\r\n\t]+/g, ' ').trim().slice(0, 120)
  if (/authorization|bearer|api[ _-]?key|sk-[a-z0-9]/i.test(normalized)) {
    return ''
  }
  return normalized
}

function safeTrace(value: string | undefined): string {
  if (!value) return ''
  return /^[a-zA-Z0-9-]{6,80}$/.test(value) ? value : ''
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
