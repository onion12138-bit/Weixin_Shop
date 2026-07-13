import {
  PENDING_AI_RECOMMENDATION_KEY,
  buildRecommendationRequest,
  createPendingAiRecommendation,
  deriveChineseZodiac,
  deriveConstellation,
  recommendationStatusMessage,
  toRecommendationViewModel,
  validateRecommendationForm,
  type RecommendationFitType,
  type RecommendationForm,
  type RecommendationResponse,
  type RecommendationViewModel,
} from '../../domain/recommendation-profile'
import {
  getRecommendationStatus,
  requestRecommendation,
} from '../../services/recommendation'

const FIT_TYPES: RecommendationFitType[] = ['SNUG', 'STANDARD', 'LOOSE']
const FIT_TYPE_LABELS = ['贴手', '标准', '宽松']
const GENERIC_UNAVAILABLE = 'AI 推荐服务暂时不可用，请稍后重试'

Page({
  data: {
    today: '',
    statusLoading: true,
    available: false,
    statusMessage: '',
    submitting: false,
    errorMessage: '',
    birthDate: '',
    derivedConstellation: '',
    derivedZodiac: '',
    manualConstellation: '',
    manualZodiac: '',
    elementPreference: '',
    wish: '',
    scene: '',
    colorPreference: '',
    budget: '500',
    wristCircumferenceMm: '160',
    fitType: 'STANDARD' as RecommendationFitType,
    fitTypeLabels: FIT_TYPE_LABELS,
    fitTypeIndex: 1,
    freeText: '',
    result: null as RecommendationViewModel | null,
  },

  recommendation: null as RecommendationResponse | null,
  submittedForm: null as RecommendationForm | null,

  onLoad() {
    this.setData({ today: localToday() })
    void this.loadStatus()
  },

  async loadStatus() {
    this.setData({
      statusLoading: true,
      available: false,
      statusMessage: '',
      errorMessage: '',
    })
    try {
      const status = await getRecommendationStatus()
      const statusMessage = recommendationStatusMessage(
        status.status,
        status.available,
      )
      this.setData({
        available: status.status === 'AVAILABLE' && status.available,
        statusMessage,
      })
    } catch {
      this.setData({
        available: false,
        statusMessage: GENERIC_UNAVAILABLE,
      })
    } finally {
      this.setData({ statusLoading: false })
    }
  },

  retryStatus() {
    void this.loadStatus()
  },

  changeBirthDate(event: WechatMiniprogram.PickerChange) {
    const birthDate = event.detail.value
    this.clearResult()
    this.setData({
      birthDate,
      derivedConstellation: deriveConstellation(birthDate),
      derivedZodiac: deriveChineseZodiac(birthDate),
      errorMessage: '',
    })
  },

  updateField(
    event: WechatMiniprogram.Input & WechatMiniprogram.TouchEvent,
  ) {
    const field = String(event.currentTarget.dataset.field || '')
    if (!field) return
    this.clearResult()
    this.setData({
      [field]: event.detail.value,
      errorMessage: '',
    } as never)
  },

  changeFitType(event: WechatMiniprogram.PickerChange) {
    const fitTypeIndex = Number(event.detail.value || 0)
    this.clearResult()
    this.setData({
      fitTypeIndex,
      fitType: FIT_TYPES[fitTypeIndex] || 'STANDARD',
      errorMessage: '',
    })
  },

  async submitRecommendation() {
    if (!this.data.available || this.data.submitting) return
    const form = this.currentForm()
    const errorMessage = validateRecommendationForm(form, this.data.today)
    if (errorMessage) {
      this.setData({ errorMessage })
      return
    }

    this.setData({ submitting: true, errorMessage: '', result: null })
    this.recommendation = null
    this.submittedForm = null
    try {
      const recommendation = await requestRecommendation(
        buildRecommendationRequest(form),
      )
      this.recommendation = recommendation
      this.submittedForm = form
      this.setData({ result: toRecommendationViewModel(recommendation) })
    } catch {
      this.setData({
        available: false,
        statusMessage: GENERIC_UNAVAILABLE,
      })
    } finally {
      this.setData({ submitting: false })
    }
  },

  enterDiy() {
    if (!this.recommendation || !this.submittedForm) return
    const handoff = createPendingAiRecommendation(
      this.recommendation,
      this.submittedForm,
    )
    wx.setStorageSync(
      PENDING_AI_RECOMMENDATION_KEY,
      JSON.stringify(handoff),
    )
    wx.navigateTo({ url: '/pages/diy/editor?source=ai' })
  },

  currentForm(): RecommendationForm {
    return {
      birthDate: this.data.birthDate,
      manualConstellation: this.data.manualConstellation,
      manualZodiac: this.data.manualZodiac,
      elementPreference: this.data.elementPreference,
      wish: this.data.wish,
      scene: this.data.scene,
      colorPreference: this.data.colorPreference,
      budget: this.data.budget,
      wristCircumferenceMm: this.data.wristCircumferenceMm,
      fitType: this.data.fitType,
      freeText: this.data.freeText,
    }
  },

  clearResult() {
    this.recommendation = null
    this.submittedForm = null
    this.setData({ result: null })
  },
})

function localToday(): string {
  const now = new Date()
  const year = now.getFullYear()
  const month = String(now.getMonth() + 1).padStart(2, '0')
  const day = String(now.getDate()).padStart(2, '0')
  return `${year}-${month}-${day}`
}
