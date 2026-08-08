import {
  PENDING_AI_RECOMMENDATION_KEY,
  buildRecommendationRequest,
  createPendingAiRecommendation,
  isRecommendationUnavailableCode,
  recommendationFailureMessage,
  recommendationStatusMessage,
  normalizeWishInspiration,
  toRecommendationViewModel,
  validateRecommendationForm,
  WISH_INSPIRATION_OPTIONS,
  type RecommendationForm,
  type RecommendationProfileResponse,
  type RecommendationResponse,
  type RecommendationViewModel,
  type WishInspiration,
} from '../../domain/recommendation-profile'
import {
  getRecommendationProfile,
  getRecommendationStatus,
  requestRecommendation,
} from '../../services/recommendation'
import { ApiError } from '../../services/request'

const wishInspirationImages: Record<WishInspiration, string> = {
  财运: '/assets/images/wishes/wealth.png',
  姻缘: '/assets/images/wishes/romance.png',
  事业: '/assets/images/wishes/career.png',
  平安: '/assets/images/wishes/peace.png',
  学业: '/assets/images/wishes/study.png',
  相守: '/assets/images/wishes/companionship.png',
  家庭: '/assets/images/wishes/family.png',
  活力: '/assets/images/wishes/vitality.png',
}

const wishInspirationOptions = WISH_INSPIRATION_OPTIONS.map((name) => ({
  name,
  imageUrl: wishInspirationImages[name],
}))

Page({
  data: {
    today: '',
    statusLoading: true,
    available: false,
    statusMessage: '',
    profileLoading: false,
    profileError: '',
    profile: null as RecommendationProfileResponse | null,
    submitting: false,
    enteringDiy: false,
    errorMessage: '',
    birthDate: '',
    wishInspiration: '',
    wishInspirationOptions,
    wish: '',
    result: null as RecommendationViewModel | null,
  },

  recommendation: null as RecommendationResponse | null,
  pageAlive: false,
  formRevision: 0,
  recommendationToken: 0,
  profileToken: 0,
  statusToken: 0,
  diyNavigationInFlight: false,

  onLoad(options: Record<string, string | undefined> = {}) {
    this.pageAlive = true
    this.setData({
      today: localToday(),
      wishInspiration: routeWishInspiration(options.wishInspiration),
    })
    void this.loadStatus()
  },

  onShow() {
    if (this.pageAlive && !this.data.statusLoading) {
      void this.loadStatus()
    }
  },

  onUnload() {
    this.pageAlive = false
    this.formRevision += 1
    this.recommendationToken += 1
    this.profileToken += 1
    this.statusToken += 1
    this.diyNavigationInFlight = false
    this.recommendation = null
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
      const blocked = status.status === 'NOT_CONFIGURED'
        || status.status === 'DISABLED'
      this.setData({
        available: !blocked,
        statusMessage: blocked
          ? recommendationStatusMessage(status.status, status.available)
          : '',
        errorMessage: !blocked && !(status.status === 'AVAILABLE' && status.available)
          ? '暂时无法确认 AI 服务状态，你可以填写后重试'
          : '',
      })
    } catch {
      if (!this.isStatusCurrent(token)) return
      this.setData({
        available: true,
        statusMessage: '',
        errorMessage: '暂时无法确认 AI 服务状态，你可以填写后重试',
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

  async changeBirthDate(event: WechatMiniprogram.PickerChange) {
    const birthDate = event.detail.value
    this.clearResult()
    const token = ++this.profileToken
    this.setData({
      birthDate,
      profile: null,
      profileLoading: Boolean(birthDate),
      profileError: '',
      errorMessage: '',
    })
    if (!birthDate) return

    try {
      const profile = await getRecommendationProfile(birthDate)
      if (!this.isProfileCurrent(token, birthDate)) return
      this.setData({ profile })
    } catch (error) {
      if (!this.isProfileCurrent(token, birthDate)) return
      const code = error instanceof ApiError ? error.code : undefined
      this.setData({ profileError: profileFailureMessage(code) })
    } finally {
      if (this.isProfileCurrent(token, birthDate)) {
        this.setData({ profileLoading: false })
      }
    }
  },

  updateWish(event: WechatMiniprogram.Input) {
    this.clearResult()
    this.setData({
      wish: event.detail.value,
      errorMessage: '',
    })
  },

  selectWishInspiration(event: WechatMiniprogram.TouchEvent) {
    const wishInspiration = normalizeWishInspiration(
      event.currentTarget.dataset.wishInspiration,
    )
    if (!wishInspiration) return
    this.clearResult()
    this.setData({
      wishInspiration: this.data.wishInspiration === wishInspiration
        ? ''
        : wishInspiration,
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
    try {
      const recommendation = await requestRecommendation(
        buildRecommendationRequest(form),
      )
      if (!this.isRecommendationCurrent(token, revision)) return
      this.recommendation = recommendation
      this.setData({
        profile: recommendation.profile,
        profileError: '',
        result: toRecommendationViewModel(recommendation),
      })
    } catch (error) {
      if (!this.isRecommendationCurrent(token, revision)) return
      const code = error instanceof ApiError ? error.code : undefined
      if (isRecommendationUnavailableCode(code)) {
        this.setData({
          available: false,
          statusMessage: recommendationFailureMessage(code),
        })
      } else {
        this.setData({
          errorMessage: recommendationFailureMessage(
            code,
            error instanceof ApiError ? error.message : undefined,
            error instanceof ApiError ? error.traceId : undefined,
          ),
        })
      }
    } finally {
      if (this.isRecommendationCurrent(token, revision)) {
        this.setData({ submitting: false })
      }
    }
  },

  enterDiy(event: WechatMiniprogram.TouchEvent) {
    if (
      !this.pageAlive
      || this.diyNavigationInFlight
      || !this.recommendation
    ) return
    const planIndex = Number(event.currentTarget.dataset.index)
    const plan = this.recommendation.plans[planIndex]
    if (!plan) return

    this.diyNavigationInFlight = true
    this.setData({ enteringDiy: true, errorMessage: '' })
    const handoff = createPendingAiRecommendation(plan)
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
      wishInspiration: this.data.wishInspiration,
      wish: this.data.wish,
    }
  },

  clearResult() {
    this.formRevision += 1
    this.recommendationToken += 1
    this.recommendation = null
    if (this.pageAlive) {
      this.setData({ result: null, submitting: false })
    }
  },

  isStatusCurrent(token: number): boolean {
    return this.pageAlive && token === this.statusToken
  },

  isProfileCurrent(token: number, birthDate: string): boolean {
    return this.pageAlive
      && token === this.profileToken
      && birthDate === this.data.birthDate
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

function profileFailureMessage(code: unknown): string {
  if (code === 'BIRTH_DATE_REQUIRED') return '请选择生日'
  if (code === 'BIRTH_DATE_INVALID' || code === 'COMMON_BAD_REQUEST') {
    return '生日信息无效，请重新选择'
  }
  if (code === 'NETWORK_ERROR') return '网络连接失败，请检查网络后重试'
  return '生日信息加载失败，请重新选择'
}

function localToday(): string {
  const now = new Date()
  const year = now.getFullYear()
  const month = String(now.getMonth() + 1).padStart(2, '0')
  const day = String(now.getDate()).padStart(2, '0')
  return `${year}-${month}-${day}`
}

function routeWishInspiration(value: string | undefined): string {
  if (typeof value !== 'string') return ''
  try {
    return normalizeWishInspiration(decodeURIComponent(value))
  } catch {
    return normalizeWishInspiration(value)
  }
}
