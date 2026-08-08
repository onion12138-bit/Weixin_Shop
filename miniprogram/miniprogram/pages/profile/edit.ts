import {
  getProfile,
  saveProfile,
  uploadAvatar,
} from '../../services/account'
import { ApiError } from '../../services/request'

const FALLBACK_AVATAR = '/assets/images/profile/avatar.webp'

Page({
  data: {
    loading: true,
    saving: false,
    nickname: '',
    avatarUrl: FALLBACK_AVATAR,
    avatarAssetId: null as number | null,
    pendingAvatarPath: '',
    errorMessage: '',
  },

  onLoad() { void this.loadProfile() },

  async loadProfile() {
    this.setData({ loading: true, errorMessage: '' })
    try {
      const profile = await getProfile()
      this.setData({
        nickname: profile.nickname,
        avatarUrl: profile.avatarUrl || FALLBACK_AVATAR,
        avatarAssetId: profile.avatarAssetId,
      })
    } catch (error) {
      this.setData({ errorMessage: readableError(error, '个人资料加载失败') })
    } finally {
      this.setData({ loading: false })
    }
  },

  onNicknameInput(event: WechatMiniprogram.Input) {
    this.setData({ nickname: event.detail.value })
  },

  chooseAvatar(event: WechatMiniprogram.ChooseAvatarEvent) {
    const avatarUrl = event.detail.avatarUrl
    if (avatarUrl) this.setData({ avatarUrl, pendingAvatarPath: avatarUrl })
  },

  async submit() {
    const nickname = this.data.nickname.trim()
    if (!nickname) {
      this.setData({ errorMessage: '请输入昵称' })
      return
    }
    this.setData({ saving: true, errorMessage: '' })
    try {
      let avatarAssetId = this.data.avatarAssetId
      if (this.data.pendingAvatarPath) {
        const uploaded = await uploadAvatar(this.data.pendingAvatarPath)
        avatarAssetId = uploaded.id
      }
      const profile = await saveProfile({ nickname, avatarAssetId })
      this.setData({
        nickname: profile.nickname,
        avatarUrl: profile.avatarUrl || this.data.avatarUrl,
        avatarAssetId: profile.avatarAssetId,
        pendingAvatarPath: '',
      })
      wx.showToast({ title: '资料已保存', icon: 'success' })
    } catch (error) {
      this.setData({ errorMessage: readableError(error, '保存失败') })
    } finally {
      this.setData({ saving: false })
    }
  },
})

function readableError(error: unknown, fallback: string): string {
  return error instanceof ApiError ? error.message
    : error instanceof Error ? error.message : fallback
}
