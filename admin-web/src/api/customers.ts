// 集中封装客户资料和发券请求，手机号等查询参数统一进行 URL 编码。
import { request } from './http'

export interface CustomerSummary {
  id: number
  nickname: string
  avatarUrl: string | null
  phone: string
  address: string
  addressCount: number
  source: string
  createdAt: string
}

export interface AdminCustomerAddress {
  id: number
  customerIdentityId: number
  customerNickname: string
  receiverName: string
  phone: string
  region: string
  detail: string
  isDefault: boolean
  status: 'ACTIVE' | 'DELETED'
  updatedAt: string
}

export interface CouponTemplate {
  id: number
  code: string
  name: string
  description: string
  discountAmount: number
  minimumSpend: number
  validDays: number
  status: 'ACTIVE' | 'INACTIVE'
  issuedCount: number
  createdAt: string
  updatedAt: string
}

export interface SaveCouponTemplate {
  code: string
  name: string
  description: string
  discountAmount: number
  minimumSpend: number
  validDays: number
  status: 'ACTIVE' | 'INACTIVE'
}

export interface GrantedCoupon {
  id: number
  templateId: number
  code: string
  name: string
  expiresAt: string
}

export function listCustomers(): Promise<CustomerSummary[]> {
  return request<CustomerSummary[]>('/admin/customers')
}

export function searchCustomersByPhone(
  phone: string,
  limit = 50,
): Promise<CustomerSummary[]> {
  const params = new URLSearchParams({ phone, limit: String(limit) })
  return request<CustomerSummary[]>(`/admin/customers/search?${params}`)
}

export function listCustomerAddresses(query: {
  keyword?: string
  status?: '' | 'ACTIVE' | 'DELETED'
  customerIdentityId?: number
} = {}): Promise<AdminCustomerAddress[]> {
  const params = new URLSearchParams()
  if (query.keyword) params.set('keyword', query.keyword)
  if (query.status) params.set('status', query.status)
  if (query.customerIdentityId) {
    params.set('customerIdentityId', String(query.customerIdentityId))
  }
  const suffix = params.toString()
  return request<AdminCustomerAddress[]>(
    `/admin/customer-addresses${suffix ? `?${suffix}` : ''}`,
  )
}

export function changeCustomerAddressStatus(
  id: number,
  status: 'ACTIVE' | 'DELETED',
): Promise<AdminCustomerAddress> {
  return request<AdminCustomerAddress>(
    `/admin/customer-addresses/${id}/status`,
    { method: 'PATCH', body: JSON.stringify({ status }) },
  )
}

export function listCouponTemplates(): Promise<CouponTemplate[]> {
  return request<CouponTemplate[]>('/admin/coupons')
}

export function createCouponTemplate(
  payload: SaveCouponTemplate,
): Promise<CouponTemplate> {
  return request<CouponTemplate>('/admin/coupons', {
    method: 'POST',
    body: JSON.stringify(payload),
  })
}

export function updateCouponTemplate(
  id: number,
  payload: SaveCouponTemplate,
): Promise<CouponTemplate> {
  return request<CouponTemplate>(`/admin/coupons/${id}`, {
    method: 'PUT',
    body: JSON.stringify(payload),
  })
}

export function grantCoupon(
  templateId: number,
  customerIdentityId: number,
  note: string,
  batchId: string,
): Promise<GrantedCoupon> {
  return request<GrantedCoupon>(`/admin/coupons/${templateId}/grants`, {
    method: 'POST',
    body: JSON.stringify({ customerIdentityId, note, batchId }),
  })
}

export interface BatchGrantCouponResult {
  requestedCount: number
  grantedCount: number
  skippedCount: number
}

export function grantCouponsBatch(
  templateId: number,
  payload: {
    batchId: string
    recipientMode: 'SELECTED' | 'ALL'
    customerIdentityIds: number[]
    note: string
  },
): Promise<BatchGrantCouponResult> {
  return request<BatchGrantCouponResult>(`/admin/coupons/${templateId}/grants/batch`, {
    method: 'POST',
    body: JSON.stringify(payload),
  })
}
