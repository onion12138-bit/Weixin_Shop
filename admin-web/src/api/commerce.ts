// 统一交易规则接口，版本号从服务端读取并供页面展示。
import { request } from './http'

export interface CommerceRule {
  minimumOrderAmount: number
  freeShippingThreshold: number
  baseShippingFee: number
  configVersion: number
}

export type SaveCommerceRulePayload = Omit<CommerceRule, 'configVersion'>

export const getCommerceRule = (): Promise<CommerceRule> =>
  request<CommerceRule>('/admin/commerce/config')

export const saveCommerceRule = (
  payload: SaveCommerceRulePayload,
): Promise<CommerceRule> =>
  request<CommerceRule>('/admin/commerce/config', {
    method: 'PUT',
    body: JSON.stringify(payload),
  })
