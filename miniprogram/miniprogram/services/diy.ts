import {
  absolutePublicUrl,
  request,
  upload,
} from './request'
import type { BraceletVisualConfig } from '../domain/diy-types'
import type { DiyVisual } from '../utils/catalog-view-model'

export type FitType = 'SNUG' | 'STANDARD' | 'LOOSE'
export type QuoteStatus = 'NOT_QUOTED' | 'VALID' | 'STALE' | 'INVALID'
export type SizeStatus = 'FIT' | 'TOO_SHORT' | 'TOO_LONG'

export interface DiyConfiguration {
  rule: {
    minWristMm: number
    maxWristMm: number
    snugAllowanceMm: number
    standardAllowanceMm: number
    looseAllowanceMm: number
    toleranceMm: number
    maxBeadCount: number
    configVersion: number
  }
  craftFeeRules: Array<{
    id: number
    minBeadCount: number
    maxBeadCount: number
    fee: number
    ruleVersion: number
  }>
  cordOptions: Array<{
    id: number
    code: string
    name: string
    colorHex: string
    extraFee: number
    sortOrder: number
    optionVersion: number
  }>
  visualConfig: BraceletVisualConfig & {
    configVersion: number
  }
}

export interface DiyDesignPayload {
  wristCircumferenceMm: number
  fitType: FitType
  cordOptionId: number
  sequence: number[]
}

export interface SaveDiySchemePayload extends DiyDesignPayload {
  name: string
  version: number
}

export interface DiyQuoteResponse {
  quotable: boolean
  issues: Array<{
    code: string
    message: string
    skuId: number | null
  }>
  skuQuantities: Record<number, number>
  currentLengthMm: number
  targetLengthMm: number
  deviationMm: number
  sizeStatus: SizeStatus
  beadAmount: number
  craftFee: number
  cordFee: number
  totalAmount: number
  quoteFingerprint: string | null
}

export interface DiySchemeResponse extends DiyQuoteResponse {
  id: number
  name: string
  wristCircumferenceMm: number
  fitType: FitType
  cordOptionId: number
  sequence: number[]
  beadCount: number
  catalogSnapshot: Array<{
    skuId: number
    skuCode: string
    name: string
    color: string | null
    diameterMm: number | null
    coverUrl: string | null
    status: string | null
    diyVisual: DiyVisual | null
  }>
  quoteStatus: QuoteStatus
  previewAssetId: number | null
  previewUrl: string | null
  version: number
  createdAt: string
  updatedAt: string
}

export function getDiyConfiguration(): Promise<DiyConfiguration> {
  return request<DiyConfiguration>('/app/diy/config')
}

export function quoteDiy(
  payload: DiyDesignPayload,
): Promise<DiyQuoteResponse> {
  return request<DiyQuoteResponse>('/app/diy/quotes', {
    method: 'POST',
    data: payload,
  })
}

export async function listSchemes(): Promise<DiySchemeResponse[]> {
  const schemes = await request<DiySchemeResponse[]>('/app/diy/schemes')
  return schemes.map(normalizeScheme)
}

export async function getScheme(id: number): Promise<DiySchemeResponse> {
  return normalizeScheme(
    await request<DiySchemeResponse>(`/app/diy/schemes/${id}`),
  )
}

export async function createScheme(
  payload: SaveDiySchemePayload,
): Promise<DiySchemeResponse> {
  return normalizeScheme(
    await request<DiySchemeResponse>('/app/diy/schemes', {
      method: 'POST',
      data: payload,
    }),
  )
}

export async function updateScheme(
  id: number,
  payload: SaveDiySchemePayload,
): Promise<DiySchemeResponse> {
  return normalizeScheme(
    await request<DiySchemeResponse>(`/app/diy/schemes/${id}`, {
      method: 'PUT',
      data: payload,
    }),
  )
}

export function deleteScheme(id: number): Promise<boolean> {
  return request<boolean>(`/app/diy/schemes/${id}`, {
    method: 'DELETE',
  })
}

export async function uploadSchemePreview(
  id: number,
  filePath: string,
): Promise<DiySchemeResponse> {
  return normalizeScheme(
    await upload<DiySchemeResponse>(`/app/diy/schemes/${id}/preview`, filePath),
  )
}

export function yuanToCents(value: number): number {
  return Math.round(value * 100)
}

function normalizeScheme(scheme: DiySchemeResponse): DiySchemeResponse {
  return {
    ...scheme,
    previewUrl: absolutePublicUrl(scheme.previewUrl),
    catalogSnapshot: scheme.catalogSnapshot.map((item) => ({
      ...item,
      coverUrl: absolutePublicUrl(item.coverUrl),
      diyVisual: item.diyVisual
        ? {
          ...item.diyVisual,
          trayUrl: absolutePublicUrl(item.diyVisual.trayUrl),
          canvasUrl: absolutePublicUrl(item.diyVisual.canvasUrl),
        }
        : null,
    })),
  }
}
