import {
  absolutePublicUrl,
  request,
} from './request'
import type {
  PublicBead,
  PublicPackage,
} from '../utils/catalog-view-model'

export interface Category {
  id: number
  name: string
  slug: string
}

export interface PageResponse<T> {
  items: T[]
  total: number
  page: number
  size: number
}

export interface PublicPackageDetail extends PublicPackage {
  items: Array<{
    id: number
    beadSkuId: number
    quantity: number
    sortOrder: number
    bead: PublicBead
  }>
}

interface CatalogQuery {
  categoryId?: number
  keyword?: string
  page?: number
  size?: number
}

export function listCategories(): Promise<Category[]> {
  return request<Category[]>('/public/catalog/categories')
}

export async function listBeads(
  query: CatalogQuery,
): Promise<PageResponse<PublicBead>> {
  const params = queryString(query)
  const page = await request<PageResponse<PublicBead>>(
    `/public/catalog/beads?${params}`,
  )
  return {
    ...page,
    items: page.items.map(normalizePublicBead),
  }
}

/** 拉取完整珠库供编辑与 AI 接续使用，并限制分页并发以给交互请求留出带宽。 */
export async function listAllPublicBeads(
  query: Omit<CatalogQuery, 'page' | 'size'> = {},
): Promise<PublicBead[]> {
  const pageSize = 100
  const first = await listBeads({ ...query, page: 1, size: pageSize })
  const pageCount = Math.max(1, Math.ceil(first.total / pageSize))
  const remaining: Array<PageResponse<PublicBead>> = []
  for (let page = 2; page <= pageCount; page += 3) {
    const batch = await Promise.all(Array.from(
      { length: Math.min(3, pageCount - page + 1) },
      (_, index) => listBeads({ ...query, page: page + index, size: pageSize }),
    ))
    remaining.push(...batch)
  }
  const byId = new Map<number, PublicBead>()
  for (const bead of [first, ...remaining].flatMap((page) => page.items)) {
    byId.set(bead.id, bead)
  }
  return Array.from(byId.values())
}

export async function getBead(id: number): Promise<PublicBead> {
  const bead = await request<PublicBead>(`/public/catalog/beads/${id}`)
  return normalizePublicBead(bead)
}

export async function listPackages(
  query: Pick<CatalogQuery, 'page' | 'size'>,
): Promise<PageResponse<PublicPackage>> {
  const params = queryString(query)
  const page = await request<PageResponse<PublicPackage>>(
    `/public/catalog/packages?${params}`,
  )
  return {
    ...page,
    items: page.items.map(normalizePublicPackage),
  }
}

export async function listRecommendedPackages(
  limit = 4,
): Promise<PublicPackage[]> {
  const items = await request<PublicPackage[]>(
    `/public/catalog/packages/recommended?limit=${encodeURIComponent(limit)}`,
  )
  return items.map(normalizePublicPackage)
}

export async function getPackage(
  id: number,
): Promise<PublicPackageDetail> {
  const item = await request<PublicPackageDetail>(
    `/public/catalog/packages/${id}`,
  )
  return {
    ...item,
    coverUrl: absolutePublicUrl(item.coverUrl),
    items: item.items.map((packageItem) => ({
      ...packageItem,
      bead: normalizePublicBead(packageItem.bead),
    })),
  }
}

function normalizePublicBead(bead: PublicBead): PublicBead {
  return {
    ...bead,
    coverUrl: absolutePublicUrl(bead.coverUrl),
    diyVisual: bead.diyVisual
      ? {
        ...bead.diyVisual,
        trayUrl: absolutePublicUrl(bead.diyVisual.trayUrl),
        canvasUrl: absolutePublicUrl(bead.diyVisual.canvasUrl),
      }
      : null,
  }
}

function normalizePublicPackage(item: PublicPackage): PublicPackage {
  return {
    ...item,
    coverUrl: absolutePublicUrl(item.coverUrl),
  }
}

function queryString(query: CatalogQuery): string {
  const pairs: string[] = []
  if (query.categoryId) {
    pairs.push(`categoryId=${encodeURIComponent(query.categoryId)}`)
  }
  if (query.keyword) {
    pairs.push(`keyword=${encodeURIComponent(query.keyword)}`)
  }
  pairs.push(`page=${query.page || 1}`)
  pairs.push(`size=${query.size || 20}`)
  return pairs.join('&')
}
