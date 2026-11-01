/** 读取运费与起订金额规则，供详情页及结算页统一展示。 */
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
