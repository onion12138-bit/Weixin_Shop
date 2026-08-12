import { request } from './request'

export interface CommerceRule {
  minimumOrderAmount: number
  freeShippingThreshold: number
  baseShippingFee: number
  configVersion: number
}

export function getCommerceRule(): Promise<CommerceRule> {
  return request<CommerceRule>('/app/commerce/config')
}
