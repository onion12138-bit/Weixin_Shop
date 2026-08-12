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
