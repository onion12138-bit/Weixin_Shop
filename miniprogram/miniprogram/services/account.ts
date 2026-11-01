/** 封装当前访客的资料、地址和优惠券请求，并统一头像的公开访问地址。 */
import { absolutePublicUrl, request, upload } from './request'

export interface CustomerProfile {
  customerIdentityId: number
  nickname: string
  avatarAssetId: number | null
  avatarUrl: string | null
}

export interface CustomerAddress {
  id: number
  receiverName: string
  phone: string
  region: string
  detail: string
  isDefault: boolean
  status: 'ACTIVE' | 'DELETED'
  createdAt: string
  updatedAt: string
}

export interface SaveAddress {
  receiverName: string
  phone: string
  region: string
  detail: string
  isDefault: boolean
}

export interface CustomerCoupon {
  id: number
  templateId: number
  code: string
  name: string
  description: string
  discountAmount: number
  minimumSpend: number
  status: 'UNUSED' | 'USED' | 'EXPIRED' | 'REVOKED'
  issuedAt: string
  expiresAt: string
  note: string | null
}

interface UploadedFile {
  id: number
  publicUrl: string
}

export async function getProfile(): Promise<CustomerProfile> {
  const profile = await request<CustomerProfile>('/app/profile')
  return { ...profile, avatarUrl: absolutePublicUrl(profile.avatarUrl) }
}

export async function saveProfile(payload: {
  nickname: string
  avatarAssetId: number | null
}): Promise<CustomerProfile> {
  const profile = await request<CustomerProfile>('/app/profile', {
    method: 'PUT', data: payload,
  })
  return { ...profile, avatarUrl: absolutePublicUrl(profile.avatarUrl) }
}

export function uploadAvatar(filePath: string): Promise<UploadedFile> {
  return upload<UploadedFile>('/app/files', filePath)
}

export function listAddresses(): Promise<CustomerAddress[]> {
  return request<CustomerAddress[]>('/app/addresses')
}

export function createAddress(payload: SaveAddress): Promise<CustomerAddress> {
  return request<CustomerAddress>('/app/addresses', { method: 'POST', data: payload })
}

export function updateAddress(id: number, payload: SaveAddress): Promise<CustomerAddress> {
  return request<CustomerAddress>(`/app/addresses/${id}`, { method: 'PUT', data: payload })
}

export function setDefaultAddress(id: number): Promise<CustomerAddress> {
  return request<CustomerAddress>(`/app/addresses/${id}/default`, { method: 'PUT' })
}

export function deleteAddress(id: number): Promise<boolean> {
  return request<boolean>(`/app/addresses/${id}`, { method: 'DELETE' })
}

export function listCoupons(): Promise<CustomerCoupon[]> {
  return request<CustomerCoupon[]>('/app/coupons')
}
