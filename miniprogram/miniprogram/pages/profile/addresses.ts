import {
  createAddress,
  deleteAddress,
  listAddresses,
  setDefaultAddress,
  updateAddress,
  type CustomerAddress,
  type SaveAddress,
} from '../../services/account'
import { ApiError } from '../../services/request'

const blankDraft = (): SaveAddress => ({
  receiverName: '', phone: '', region: '', detail: '', isDefault: false,
})

Page({
  data: {
    loading: true,
    saving: false,
    addresses: [] as CustomerAddress[],
    showEditor: false,
    editingId: 0,
    draft: blankDraft(),
    errorMessage: '',
  },

  onLoad() { void this.loadAddresses() },

  async loadAddresses() {
    this.setData({ loading: true, errorMessage: '' })
    try { this.setData({ addresses: await listAddresses() }) }
    catch (error) { this.setData({ errorMessage: readableError(error) }) }
    finally { this.setData({ loading: false }) }
  },

  addAddress() { this.setData({ showEditor: true, editingId: 0, draft: blankDraft() }) },

  editAddress(event: WechatMiniprogram.TouchEvent) {
    const id = Number(event.currentTarget.dataset.id)
    const current = this.data.addresses.find((item) => item.id === id)
    if (!current) return
    this.setData({
      showEditor: true, editingId: id,
      draft: {
        receiverName: current.receiverName, phone: current.phone,
        region: current.region, detail: current.detail,
        isDefault: current.isDefault,
      },
    })
  },

  closeEditor() { this.setData({ showEditor: false, editingId: 0 }) },

  noop() {},

  updateDraft(event: WechatMiniprogram.Input & WechatMiniprogram.TouchEvent) {
    const field = String(event.currentTarget.dataset.field || '')
    if (!field) return
    this.setData({ [`draft.${field}`]: event.detail.value } as never)
  },

  toggleDraftDefault() {
    this.setData({ 'draft.isDefault': !this.data.draft.isDefault } as never)
  },

  async saveAddress() {
    if (this.data.saving) return
    const draft = {
      ...this.data.draft,
      receiverName: this.data.draft.receiverName.trim(),
      phone: this.data.draft.phone.trim(),
      region: this.data.draft.region.trim(),
      detail: this.data.draft.detail.trim(),
    }
    if (!draft.receiverName || !draft.phone || !draft.region || !draft.detail) {
      this.setData({ errorMessage: '请完整填写收货人、电话、地区和详细地址' })
      return
    }
    this.setData({ saving: true, errorMessage: '' })
    try {
      if (this.data.editingId) await updateAddress(this.data.editingId, draft)
      else await createAddress(draft)
      this.setData({ showEditor: false, editingId: 0 })
      await this.loadAddresses()
      wx.showToast({ title: '地址已保存', icon: 'success' })
    } catch (error) { this.setData({ errorMessage: readableError(error) }) }
    finally { this.setData({ saving: false }) }
  },

  makeDefault(event: WechatMiniprogram.TouchEvent) {
    const id = Number(event.currentTarget.dataset.id)
    void setDefaultAddress(id).then(() => this.loadAddresses()).catch((error) => {
      this.setData({ errorMessage: readableError(error) })
    })
  },

  removeAddress(event: WechatMiniprogram.TouchEvent) {
    const id = Number(event.currentTarget.dataset.id)
    wx.showModal({
      title: '删除收货地址', content: '确定删除这条收货地址吗？',
      success: (response) => {
        if (!response.confirm) return
        void deleteAddress(id).then(() => this.loadAddresses()).catch((error) => {
          this.setData({ errorMessage: readableError(error) })
        })
      },
    })
  },
})

function readableError(error: unknown): string {
  return error instanceof ApiError ? error.message
    : error instanceof Error ? error.message : '地址操作失败'
}
