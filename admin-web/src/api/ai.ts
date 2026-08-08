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
