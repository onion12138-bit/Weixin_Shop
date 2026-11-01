/** 统一新增、编辑及默认地址操作，提交期间锁定表单并阻止重复请求。 */
import {
  createAddress,
  deleteAddress,
  listAddresses,
  setDefaultAddress,
  updateAddress,
  type CustomerAddress,
  type SaveAddress,
} from '../../services/account'
import { selectCheckoutAddress } from '../../utils/checkout-state'
import { ApiError } from '../../services/request'

const blankDraft = (): SaveAddress => ({
  receiverName: '', phone: '', region: '', detail: '', isDefault: false,
})

Page({
  data: {
    selectForScheme: 0,
    loading: true,
    saving: false,
    operating: '' as '' | 'default' | 'delete',
    operatingId: 0,
    addresses: [] as CustomerAddress[],
    showEditor: false,
    editingId: 0,
    draft: blankDraft(),
    errorMessage: '',
  },

  onLoad(options: Record<string, string> = {}) {
    this.setData({ selectForScheme: Number(options.selectForScheme || 0) })
    void this.loadAddresses()
  },

  chooseForCheckout(event: WechatMiniprogram.TouchEvent) {
    if (!this.data.selectForScheme || this.data.saving || this.data.operating) return
    const id = Number(event.currentTarget.dataset.id)
    if (!this.data.addresses.some(item => item.id === id)) return
    try {
      selectCheckoutAddress(this.data.selectForScheme, id)
      wx.navigateBack({ fail: () => this.setData({ errorMessage: '本单地址已选择，请返回订单确认页' }) })
    } catch (error) { this.setData({ errorMessage: readableError(error) }) }
  },

  async loadAddresses() {
    this.setData({ loading: true, errorMessage: '' })
    try { this.setData({ addresses: await listAddresses() }) }
    catch (error) { this.setData({ errorMessage: readableError(error) }) }
    finally { this.setData({ loading: false }) }
  },

  addAddress() {
    if (this.data.saving || this.data.operating) return
    this.setData({ showEditor: true, editingId: 0, draft: blankDraft() })
  },

  editAddress(event: WechatMiniprogram.TouchEvent) {
    if (this.data.saving || this.data.operating) return
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

  closeEditor() {
    if (this.data.saving || this.data.operating) return
    this.setData({ showEditor: false, editingId: 0 })
  },

  noop() {},

  updateDraft(event: WechatMiniprogram.Input & WechatMiniprogram.TouchEvent) {
    if (this.data.saving || this.data.operating) return
    const field = String(event.currentTarget.dataset.field || '')
    if (!field) return
    this.setData({ [`draft.${field}`]: event.detail.value } as never)
  },

  toggleDraftDefault() {
    if (this.data.saving || this.data.operating) return
    this.setData({ 'draft.isDefault': !this.data.draft.isDefault } as never)
  },

  async saveAddress() {
    if (!this.data.showEditor || this.data.saving || this.data.operating) return
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

  async makeDefault(event: WechatMiniprogram.TouchEvent) {
    if (this.data.saving || this.data.operating) return
    const id = Number(event.currentTarget.dataset.id)
    const current = this.data.addresses.find((item) => item.id === id)
    if (!current || current.isDefault) return
    this.setData({ operating: 'default', operatingId: id, errorMessage: '' })
    try {
      await setDefaultAddress(id)
      await this.loadAddresses()
    } catch (error) { this.setData({ errorMessage: readableError(error) }) }
    finally { this.setData({ operating: '', operatingId: 0 }) }
  },

  async removeAddress(event: WechatMiniprogram.TouchEvent) {
    if (this.data.saving || this.data.operating) return
    const id = Number(event.currentTarget.dataset.id)
    if (!this.data.addresses.some((item) => item.id === id)) return
    this.setData({ operating: 'delete', operatingId: id, errorMessage: '' })
    try {
      const confirmed = await new Promise<boolean>((resolve) => {
        wx.showModal({
          title: '删除收货地址', content: '确定删除这条收货地址吗？',
          success: (response) => resolve(response.confirm),
          fail: () => resolve(false),
        })
      })
      if (!confirmed) return
      await deleteAddress(id)
      await this.loadAddresses()
    } catch (error) { this.setData({ errorMessage: readableError(error) }) }
    finally { this.setData({ operating: '', operatingId: 0 }) }
  },
})

function readableError(error: unknown): string {
  return error instanceof ApiError ? error.message
    : error instanceof Error ? error.message : '地址操作失败'
}
