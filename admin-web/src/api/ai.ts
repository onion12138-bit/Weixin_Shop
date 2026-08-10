import { request } from './http'

export interface AiConfiguration {
  provider: 'DEEPSEEK'
  baseUrl: string
  modelName: string
  apiKeyConfigured: boolean
  enabled: boolean
  timeoutSeconds: number
  maxOutputTokens: number
  recommendationPrompt: string
  preferencePrompt: string
  softPreferenceEnabled: boolean
  planCount: number
  version: number
}

export interface SaveAiConfigurationPayload {
  baseUrl: string
  modelName: string
  apiKey: string
  enabled: boolean
  timeoutSeconds: number
  maxOutputTokens: number
  recommendationPrompt: string
  preferencePrompt: string
  softPreferenceEnabled: boolean
  planCount: number
  version: number
}

export interface AiApiKeyView {
  apiKey: string
}

export const getAiConfiguration = (): Promise<AiConfiguration> =>
  request<AiConfiguration>('/admin/ai/config')

export const saveAiConfiguration = (
  payload: SaveAiConfigurationPayload,
): Promise<AiConfiguration> =>
  request<AiConfiguration>('/admin/ai/config', {
    method: 'PUT',
    body: JSON.stringify(payload),
  })

export const testAiConfiguration = (): Promise<void> =>
  request<void>('/admin/ai/config/test', { method: 'POST' })

export const revealAiApiKey = (): Promise<AiApiKeyView> =>
  request<AiApiKeyView>('/admin/ai/config/api-key')

export const clearAiApiKey = (version: number): Promise<AiConfiguration> =>
  request<AiConfiguration>(
    `/admin/ai/config/api-key?version=${encodeURIComponent(String(version))}`,
    { method: 'DELETE' },
  )

export type AiLogStatus = 'PROCESSING' | 'SUCCEEDED' | 'FAILED'

export interface AiRecommendationLogSummary {
  id: number
  traceId: string
  status: AiLogStatus
  modelName: string | null
  wishSummary: string | null
  preferenceSummary: string | null
  candidateCount: number
  matchedCandidateCount: number
  matchedPlanCount: number
  planCount: number
  attemptCount: number
  durationMs: number | null
  errorCode: string | null
  createdAt: string
}

export interface AiRecommendationLogDetail {
  summary: AiRecommendationLogSummary
  errorMessage: string | null
  requestJson: string | null
  configSnapshotJson: string | null
  candidatesJson: string | null
  attemptsJson: string | null
  preferenceAnalysisJson: string | null
  finalResponseJson: string | null
  updatedAt: string
}

export interface AiRecommendationLogPage {
  items: AiRecommendationLogSummary[]
  total: number
  page: number
  size: number
}

export const listAiRecommendationLogs = (
  page = 1,
  size = 20,
  status = '',
  keyword = '',
): Promise<AiRecommendationLogPage> => {
  const params = new URLSearchParams({
    page: String(page),
    size: String(size),
  })
  if (status) params.set('status', status)
  if (keyword.trim()) params.set('keyword', keyword.trim())
  return request<AiRecommendationLogPage>(`/admin/ai/logs?${params.toString()}`)
}

export const getAiRecommendationLog = (
  id: number,
): Promise<AiRecommendationLogDetail> =>
  request<AiRecommendationLogDetail>(`/admin/ai/logs/${id}`)

export const deleteAiRecommendationLog = (id: number): Promise<void> =>
  request<void>(`/admin/ai/logs/${id}`, { method: 'DELETE' })
