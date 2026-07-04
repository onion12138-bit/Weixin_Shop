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
    items: page.items.map((item) => ({
      ...item,
      coverUrl: absolutePublicUrl(item.coverUrl),
    })),
  }
}

export async function getBead(id: number): Promise<PublicBead> {
  const bead = await request<PublicBead>(`/public/catalog/beads/${id}`)
  return {
    ...bead,
    coverUrl: absolutePublicUrl(bead.coverUrl),
  }
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
    items: page.items.map((item) => ({
      ...item,
      coverUrl: absolutePublicUrl(item.coverUrl),
    })),
  }
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
      bead: {
        ...packageItem.bead,
        coverUrl: absolutePublicUrl(packageItem.bead.coverUrl),
      },
    })),
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
