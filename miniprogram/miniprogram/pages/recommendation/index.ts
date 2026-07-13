import {
  PENDING_AI_RECOMMENDATION_KEY,
  buildRecommendationRequest,
  createPendingAiRecommendation,
  deriveChineseZodiac,
  deriveConstellation,
  recommendationFailureMessage,
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
import { ApiError } from '../../services/request'

const FIT_TYPES: RecommendationFitType[] = ['SNUG', 'STANDARD', 'LOOSE']
const FIT_TYPE_LABELS = ['贴手', '标准', '宽松']
Page({
  data: {
    today: '',
    statusLoading: true,
    available: false,
    statusMessage: '',
    submitting: false,
    enteringDiy: false,
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
  pageAlive: false,
  formRevision: 0,
  recommendationToken: 0,
  statusToken: 0,
  diyNavigationInFlight: false,

  onLoad() {
    this.pageAlive = true
    this.setData({ today: localToday() })
    void this.loadStatus()
  },

  onUnload() {
    this.pageAlive = false
    this.formRevision += 1
    this.recommendationToken += 1
    this.statusToken += 1
    this.diyNavigationInFlight = false
    this.recommendation = null
    this.submittedForm = null
  },

  async loadStatus() {
    if (!this.pageAlive) return
    const token = ++this.statusToken
    this.setData({
      statusLoading: true,
      available: false,
      statusMessage: '',
      errorMessage: '',
    })
    try {
      const status = await getRecommendationStatus()
      if (!this.isStatusCurrent(token)) return
      const statusMessage = recommendationStatusMessage(
        status.status,
        status.available,
      )
      this.setData({
        available: status.status === 'AVAILABLE' && status.available,
        statusMessage,
      })
    } catch {
      if (!this.isStatusCurrent(token)) return
      this.setData({
        available: false,
        statusMessage: recommendationFailureMessage(undefined),
      })
    } finally {
      if (this.isStatusCurrent(token)) {
        this.setData({ statusLoading: false })
      }
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

    const revision = this.formRevision
    const token = ++this.recommendationToken
    this.setData({ submitting: true, errorMessage: '', result: null })
    this.recommendation = null
    this.submittedForm = null
    try {
      const recommendation = await requestRecommendation(
        buildRecommendationRequest(form),
      )
      if (!this.isRecommendationCurrent(token, revision)) return
      this.recommendation = recommendation
      this.submittedForm = form
      this.setData({ result: toRecommendationViewModel(recommendation) })
    } catch (error) {
      if (!this.isRecommendationCurrent(token, revision)) return
      const code = error instanceof ApiError ? error.code : undefined
      this.setData({
        available: false,
        statusMessage: recommendationFailureMessage(code),
      })
    } finally {
      if (this.isRecommendationCurrent(token, revision)) {
        this.setData({ submitting: false })
      }
    }
  },

  enterDiy() {
    if (
      !this.pageAlive
      || this.diyNavigationInFlight
      || !this.recommendation
      || !this.submittedForm
    ) return
    this.diyNavigationInFlight = true
    this.setData({ enteringDiy: true, errorMessage: '' })
    const handoff = createPendingAiRecommendation(
      this.recommendation,
      this.submittedForm,
    )
    try {
      wx.setStorageSync(
        PENDING_AI_RECOMMENDATION_KEY,
        JSON.stringify(handoff),
      )
      wx.navigateTo({
        url: '/pages/diy/editor?source=ai',
        success: () => this.finishDiyNavigation(),
        fail: () => {
          this.clearPendingAiHandoff()
          this.finishDiyNavigation('进入 DIY 失败，请重试')
        },
      })
    } catch {
      this.clearPendingAiHandoff()
      this.finishDiyNavigation('进入 DIY 失败，请重试')
    }
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
    this.formRevision += 1
    this.recommendationToken += 1
    this.recommendation = null
    this.submittedForm = null
    if (this.pageAlive) {
      this.setData({ result: null, submitting: false })
    }
  },

  isStatusCurrent(token: number): boolean {
    return this.pageAlive && token === this.statusToken
  },

  isRecommendationCurrent(token: number, revision: number): boolean {
    return this.pageAlive
      && token === this.recommendationToken
      && revision === this.formRevision
  },

  clearPendingAiHandoff() {
    try {
      wx.removeStorageSync(PENDING_AI_RECOMMENDATION_KEY)
      return
    } catch {
      // Overwrite first so a second source=ai open cannot replay valid data.
    }
    try {
      wx.setStorageSync(PENDING_AI_RECOMMENDATION_KEY, '')
    } catch {
      // Best effort only; the navigation itself has already failed.
    }
    try {
      wx.removeStorageSync(PENDING_AI_RECOMMENDATION_KEY)
    } catch {
      // Best effort only.
    }
  },

  finishDiyNavigation(errorMessage = '') {
    this.diyNavigationInFlight = false
    if (!this.pageAlive) return
    this.setData({ enteringDiy: false, errorMessage })
  },
})

function localToday(): string {
  const now = new Date()
  const year = now.getFullYear()
  const month = String(now.getMonth() + 1).padStart(2, '0')
  const day = String(now.getDate()).padStart(2, '0')
  return `${year}-${month}-${day}`
}
