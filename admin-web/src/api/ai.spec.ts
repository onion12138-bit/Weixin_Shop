import { beforeEach, describe, expect, it, vi } from 'vitest'
import {
  clearAiApiKey,
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
  version: 7,
}

describe('AI configuration API contract', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    vi.mocked(request).mockResolvedValue(undefined)
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

  it('clears the key with DELETE and the encoded version query', async () => {
    await clearAiApiKey(7)

    expect(request).toHaveBeenCalledWith(
      '/admin/ai/config/api-key?version=7',
      { method: 'DELETE' },
    )
  })
})
