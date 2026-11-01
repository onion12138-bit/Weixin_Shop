import type { CreateOrderPayload } from '../services/orders'
import { INSTALLATION_ID_KEY } from '../services/session'

// 按安装身份和方案隔离；保存完整请求，任何弱网重试均复用相同内容及令牌。
export function checkoutStorageKey(schemeId: number): string {
  return `diy_checkout_v1:${wx.getStorageSync(INSTALLATION_ID_KEY) || 'guest'}:${schemeId}`
}

export function readCheckoutRequest(schemeId: number): CreateOrderPayload | null {
  const value = wx.getStorageSync(checkoutStorageKey(schemeId))
  if (!value) return null
  const request = JSON.parse(value) as CreateOrderPayload
  if (!request.checkoutToken || !request.quoteFingerprint || request.schemeId !== schemeId
    || !request.contactName || !request.contactPhone || !request.addressDetail) {
    throw new Error('本机结算记录无法读取，请先在订单列表确认提交结果')
  }
  return request
}

export function saveCheckoutRequest(request: CreateOrderPayload): void {
  wx.setStorageSync(checkoutStorageKey(request.schemeId), JSON.stringify(request))
}

export function clearCheckoutRequest(schemeId: number): void {
  wx.removeStorageSync(checkoutStorageKey(schemeId))
}

export function selectCheckoutAddress(schemeId: number, addressId: number): void {
  wx.setStorageSync(`${checkoutStorageKey(schemeId)}:address`, String(addressId))
}

export function selectedCheckoutAddress(schemeId: number): number {
  return Number(wx.getStorageSync(`${checkoutStorageKey(schemeId)}:address`) || 0)
}

export function clearCheckoutAddress(schemeId: number): void {
  wx.removeStorageSync(`${checkoutStorageKey(schemeId)}:address`)
}
