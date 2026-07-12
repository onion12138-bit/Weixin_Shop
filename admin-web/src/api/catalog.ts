import { request } from './http'

export type CatalogStatus = 'DRAFT' | 'ON_SALE' | 'OFF_SALE'

export interface Category {
  id: number
  name: string
  slug: string
  sortOrder: number
  status: CatalogStatus
}

export interface BeadSku {
  id: number
  categoryId: number
  skuCode: string
  name: string
  material: string
  color: string
  diameterMm: number
  salePrice: number
  costPrice: number
  stockAlertThreshold: number
  coverAssetId: number | null
  tagsJson: string
  status: CatalogStatus
}

export interface PageResponse<T> {
  items: T[]
  total: number
  page: number
  size: number
}

export interface BeadQuery {
  keyword?: string
  status?: CatalogStatus | ''
  page?: number
  size?: number
}

export interface SaveCategoryRequest {
  name: string
  slug: string
  sortOrder: number
  status: CatalogStatus
}

export interface SaveBeadSkuRequest {
  categoryId: number
  skuCode: string
  name: string
  material: string
  color: string
  diameterMm: number
  salePrice: number
  costPrice: number
  stockAlertThreshold: number
  coverAssetId?: number | null
  tags: string[]
  status: CatalogStatus
}

export type CreateBeadSkuRequest = SaveBeadSkuRequest & {
  initialStock: number
}

export interface ProductPackage {
  id: number
  packageCode: string
  name: string
  description: string
  salePrice: number
  coverAssetId: number | null
  status: CatalogStatus
}

export interface AdminPackageItem {
  beadSkuId: number
  quantity: number
  sortOrder: number
}

export interface AdminPackageDetail extends ProductPackage {
  items: AdminPackageItem[]
}

export interface SavePackageRequest {
  packageCode: string
  name: string
  description: string
  salePrice: number
  coverAssetId?: number | null
  status: CatalogStatus
  items: Array<{
    beadSkuId: number
    quantity: number
  }>
}

export type PackageQuery = BeadQuery

export interface UploadedFile {
  id: number
  storageKey: string
  originalName: string
  contentType: string
  sizeBytes: number
  publicUrl: string
}

export function listAdminCategories(): Promise<Category[]> {
  return request<Category[]>('/admin/catalog/categories')
}

export function createAdminCategory(
  payload: SaveCategoryRequest,
): Promise<Category> {
  return request<Category>('/admin/catalog/categories', {
    method: 'POST',
    body: JSON.stringify(payload),
  })
}

export function updateAdminCategory(
  id: number,
  payload: SaveCategoryRequest,
): Promise<Category> {
  return request<Category>(`/admin/catalog/categories/${id}`, {
    method: 'PUT',
    body: JSON.stringify(payload),
  })
}

export function changeCategoryStatus(
  id: number,
  status: CatalogStatus,
): Promise<Category> {
  return request<Category>(`/admin/catalog/categories/${id}/status`, {
    method: 'PATCH',
    body: JSON.stringify({ status }),
  })
}

export function listAdminBeads(
  query: BeadQuery = {},
): Promise<PageResponse<BeadSku>> {
  const params = new URLSearchParams()
  if (query.keyword) params.set('keyword', query.keyword)
  if (query.status) params.set('status', query.status)
  params.set('page', String(query.page || 1))
  params.set('size', String(query.size || 20))
  return request<PageResponse<BeadSku>>(
    `/admin/catalog/beads?${params.toString()}`,
  )
}

export function createAdminBead(
  payload: CreateBeadSkuRequest,
): Promise<BeadSku> {
  return request<BeadSku>('/admin/catalog/beads', {
    method: 'POST',
    body: JSON.stringify(payload),
  })
}

export function updateAdminBead(
  id: number,
  payload: SaveBeadSkuRequest,
): Promise<BeadSku> {
  return request<BeadSku>(`/admin/catalog/beads/${id}`, {
    method: 'PUT',
    body: JSON.stringify(payload),
  })
}

export function changeBeadStatus(
  id: number,
  status: CatalogStatus,
): Promise<BeadSku> {
  return request<BeadSku>(`/admin/catalog/beads/${id}/status`, {
    method: 'PATCH',
    body: JSON.stringify({ status }),
  })
}

export function listAdminPackages(
  query: PackageQuery = {},
): Promise<PageResponse<ProductPackage>> {
  const params = new URLSearchParams()
  if (query.keyword) params.set('keyword', query.keyword)
  if (query.status) params.set('status', query.status)
  params.set('page', String(query.page || 1))
  params.set('size', String(query.size || 20))
  return request<PageResponse<ProductPackage>>(
    `/admin/catalog/packages?${params.toString()}`,
  )
}

export function getAdminPackage(id: number): Promise<AdminPackageDetail> {
  return request<AdminPackageDetail>(`/admin/catalog/packages/${id}`)
}

export function createAdminPackage(
  payload: SavePackageRequest,
): Promise<ProductPackage> {
  return request<ProductPackage>('/admin/catalog/packages', {
    method: 'POST',
    body: JSON.stringify(payload),
  })
}

export function updateAdminPackage(
  id: number,
  payload: SavePackageRequest,
): Promise<ProductPackage> {
  return request<ProductPackage>(`/admin/catalog/packages/${id}`, {
    method: 'PUT',
    body: JSON.stringify(payload),
  })
}

export function changePackageStatus(
  id: number,
  status: CatalogStatus,
): Promise<ProductPackage> {
  return request<ProductPackage>(`/admin/catalog/packages/${id}/status`, {
    method: 'PATCH',
    body: JSON.stringify({ status }),
  })
}

/**
 * 上传商品封面并返回可写入 coverAssetId 的素材主键。
 */
export function uploadCatalogFile(file: File): Promise<UploadedFile> {
  const formData = new FormData()
  formData.append('file', file)
  return request<UploadedFile>('/admin/files', {
    method: 'POST',
    body: formData,
  })
}
