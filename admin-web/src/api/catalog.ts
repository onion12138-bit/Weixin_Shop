import { request, requestBlob } from './http'

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
  coverUrl: string | null
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
  categoryId?: number | ''
  attribute?: string
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

export type BeadVisualStatus =
  | 'DRAFT'
  | 'GENERATING'
  | 'READY'
  | 'FAILED'
  | 'PUBLISHED'
  | 'ARCHIVED'

export interface BeadVisualVersion {
  id: number
  version: number
  sourceType: 'AUTO' | 'MANUAL'
  status: BeadVisualStatus
  trayAssetId: number | null
  trayUrl: string | null
  canvasAssetId: number | null
  canvasUrl: string | null
  failureCode: string | null
  failureMessage: string | null
  createdBy: string
  publishedBy: string | null
  publishedAt: string | null
  createdAt: string
}

export interface BeadVisualHistory {
  publishedVisualVersionId: number | null
  versions: BeadVisualVersion[]
}

export interface CreateManualBeadVisualRequest {
  trayAssetId: number
  canvasAssetId: number
}

export interface CatalogImportError {
  rowNumber: number
  skuCode: string
  message: string
}

export interface CatalogImportResult {
  totalRows: number
  successCount: number
  failureCount: number
  errors: CatalogImportError[]
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
  if (query.categoryId) params.set('categoryId', String(query.categoryId))
  if (query.attribute) params.set('attribute', query.attribute)
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

/** 读取指定散珠的 DIY 固定视觉版本历史。 */
export function listBeadVisuals(beadId: number): Promise<BeadVisualHistory> {
  return request<BeadVisualHistory>(`/admin/catalog/beads/${beadId}/visuals`)
}

/** 将两份人工上传的透明素材保存为可发布的视觉版本。 */
export function createManualBeadVisual(
  beadId: number,
  payload: CreateManualBeadVisualRequest,
): Promise<BeadVisualVersion> {
  return request<BeadVisualVersion>(
    `/admin/catalog/beads/${beadId}/visuals/manual`,
    {
      method: 'POST',
      body: JSON.stringify(payload),
    },
  )
}

/** 发布状态为 READY 的视觉版本。 */
export function publishBeadVisual(
  beadId: number,
  versionId: number,
): Promise<BeadVisualVersion> {
  return request<BeadVisualVersion>(
    `/admin/catalog/beads/${beadId}/visuals/${versionId}/publish`,
    { method: 'POST' },
  )
}

/** 基于历史版本立即生成并发布新的回滚版本。 */
export function rollbackBeadVisual(
  beadId: number,
  versionId: number,
): Promise<BeadVisualVersion> {
  return request<BeadVisualVersion>(
    `/admin/catalog/beads/${beadId}/visuals/${versionId}/rollback`,
    { method: 'POST' },
  )
}

/** 下载包含当前分类下拉项和填写说明的 Excel 模板。 */
export function downloadBeadImportTemplate(): Promise<Blob> {
  return requestBlob('/admin/catalog/beads/import-template')
}

/** 批量导入商品；图片在导入后通过商品编辑页补充。 */
export function importAdminBeads(file: File): Promise<CatalogImportResult> {
  const formData = new FormData()
  formData.append('file', file)
  return request<CatalogImportResult>('/admin/catalog/beads/import', {
    method: 'POST',
    body: formData,
  })
}
