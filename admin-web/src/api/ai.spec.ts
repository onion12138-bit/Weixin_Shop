import { beforeEach, describe, expect, it, vi } from 'vitest'
import {
  clearAiApiKey,
  deleteAiRecommendationLog,
  getAiRecommendationLog,
  getAiConfiguration,
  listAiRecommendationLogs,
  revealAiApiKey,
  saveAiConfiguration,
  testAiConfiguration,
  type SaveAiConfigurationPayload,
} from './ai'
import { request } from './http'

vi.mock('./http', () => ({ request: vi.fn() }))

const payload: SaveAiConfigurationPayload = {
  baseUrl: 'https://api.deepseek.com',
  modelName: 'deepseek-v4-flash',
  apiKey: '',
  enabled: true,
  timeoutSeconds: 30,
  maxOutputTokens: 2048,
  recommendationPrompt: '只返回满足尺寸和预算的完整手串推荐。',
  preferencePrompt: '识别名称、材质、颜色和全部标签中的用户偏好。',
  softPreferenceEnabled: true,
  planCount: 3,
  version: 7,
}

describe('AI configuration API contract', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    vi.mocked(request).mockResolvedValue(undefined)
  })

  it('loads the singleton configuration with GET defaults', async () => {
    await getAiConfiguration()

    expect(request).toHaveBeenCalledWith('/admin/ai/config')
  })

  it('saves with PUT and the complete JSON body', async () => {
    await saveAiConfiguration(payload)

    expect(request).toHaveBeenCalledWith('/admin/ai/config', {
      method: 'PUT',
      body: JSON.stringify(payload),
    })
  })

  it('tests the saved configuration with POST', async () => {
    await testAiConfiguration()

    expect(request).toHaveBeenCalledWith('/admin/ai/config/test', {
      method: 'POST',
    })
  })

  it('reveals the key only through the explicit GET endpoint', async () => {
    await revealAiApiKey()

    expect(request).toHaveBeenCalledWith('/admin/ai/config/api-key')
  })

  it('clears the key with DELETE and the encoded version query', async () => {
    await clearAiApiKey(7)

    expect(request).toHaveBeenCalledWith(
      '/admin/ai/config/api-key?version=7',
      { method: 'DELETE' },
    )
  })

  it('lists, reads and deletes administrator recommendation logs', async () => {
    await listAiRecommendationLogs(2, 20, 'SUCCEEDED', '蓝色')
    expect(request).toHaveBeenCalledWith(
      '/admin/ai/logs?page=2&size=20&status=SUCCEEDED&keyword=%E8%93%9D%E8%89%B2',
    )

    await getAiRecommendationLog(7)
    expect(request).toHaveBeenCalledWith('/admin/ai/logs/7')

    await deleteAiRecommendationLog(7)
    expect(request).toHaveBeenCalledWith('/admin/ai/logs/7', {
      method: 'DELETE',
    })
  })
})
