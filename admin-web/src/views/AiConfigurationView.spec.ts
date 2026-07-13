import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { ApiError } from '../api/http'
import {
  clearAiApiKey,
  getAiConfiguration,
  saveAiConfiguration,
  testAiConfiguration,
  type AiConfiguration,
} from '../api/ai'
import AiConfigurationView from './AiConfigurationView.vue'

vi.mock('../api/ai', () => ({
  getAiConfiguration: vi.fn(),
  saveAiConfiguration: vi.fn(),
  testAiConfiguration: vi.fn(),
  clearAiApiKey: vi.fn(),
}))

function configuration(
  overrides: Partial<AiConfiguration> = {},
): AiConfiguration {
  return {
    provider: 'DEEPSEEK',
    baseUrl: 'https://api.deepseek.com',
    modelName: 'deepseek-v4-flash',
    apiKeyConfigured: true,
    enabled: true,
    timeoutSeconds: 30,
    maxOutputTokens: 2048,
    version: 5,
    ...overrides,
  }
}

describe('AiConfigurationView', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    vi.mocked(getAiConfiguration).mockResolvedValue(configuration())
    vi.mocked(saveAiConfiguration).mockResolvedValue(
      configuration({ version: 6 }),
    )
    vi.mocked(testAiConfiguration).mockResolvedValue(undefined)
    vi.mocked(clearAiApiKey).mockResolvedValue(
      configuration({ apiKeyConfigured: false, version: 6 }),
    )
  })

  it('loads the DeepSeek configuration without ever echoing its API key', async () => {
    const wrapper = mount(AiConfigurationView)
    await flushPromises()

    expect(wrapper.get('[data-test="provider"]').attributes('readonly'))
      .toBeDefined()
    expect(wrapper.get('[data-test="model-name"]').element)
      .toMatchObject({ value: 'deepseek-v4-flash' })
    expect(wrapper.get('[data-test="api-key"]').attributes('type'))
      .toBe('password')
    expect(wrapper.get('[data-test="api-key"]').element)
      .toMatchObject({ value: '' })
    expect(wrapper.get('[data-test="api-key-status"]').text()).toBe('已配置')
  })

  it('sends a blank API key to preserve it and adopts the saved version', async () => {
    const wrapper = mount(AiConfigurationView)
    await flushPromises()

    await wrapper.get('[data-test="configuration-form"]').trigger('submit')
    await flushPromises()

    expect(saveAiConfiguration).toHaveBeenCalledWith({
      baseUrl: 'https://api.deepseek.com',
      modelName: 'deepseek-v4-flash',
      apiKey: '',
      enabled: true,
      timeoutSeconds: 30,
      maxOutputTokens: 2048,
      version: 5,
    })
    expect(wrapper.get('[data-test="configuration-version"]').text())
      .toBe('v6')
    expect(wrapper.get('[data-test="api-key"]').element)
      .toMatchObject({ value: '' })
  })

  it('clears the API key only after confirmation and uses the current version', async () => {
    const confirm = vi.spyOn(window, 'confirm').mockReturnValue(false)
    const wrapper = mount(AiConfigurationView)
    await flushPromises()

    await wrapper.get('[data-test="clear-api-key"]').trigger('click')
    expect(clearAiApiKey).not.toHaveBeenCalled()

    confirm.mockReturnValue(true)
    await wrapper.get('[data-test="clear-api-key"]').trigger('click')
    await flushPromises()

    expect(clearAiApiKey).toHaveBeenCalledWith(5)
    expect(wrapper.get('[data-test="configuration-version"]').text())
      .toBe('v6')
    expect(wrapper.get('[data-test="api-key-status"]').text()).toBe('未配置')
    confirm.mockRestore()
  })

  it('shows a successful test-connection result', async () => {
    const wrapper = mount(AiConfigurationView)
    await flushPromises()

    await wrapper.get('[data-test="test-connection"]').trigger('click')
    await flushPromises()

    expect(testAiConfiguration).toHaveBeenCalledOnce()
    expect(wrapper.get('[data-test="connection-result"]').text())
      .toContain('连接成功，模型 deepseek-v4-flash 可用')
  })

  it.each([
    ['AI_NOT_CONFIGURED', '尚未配置 API Key，无法测试连接'],
    ['AI_DISABLED', 'AI 服务已停用，无法测试连接'],
    ['AI_UNAUTHORIZED', 'API Key 鉴权失败，请检查后重新保存'],
    ['AI_RATE_LIMITED', 'DeepSeek 请求过于频繁，请稍后重试'],
    ['AI_MODEL_UNAVAILABLE', '配置的模型不存在或暂不可用'],
    ['AI_TEMPORARILY_UNAVAILABLE', 'DeepSeek 连接超时或服务暂不可用'],
  ])('maps %s to safe, trace-aware connection feedback', async (code, copy) => {
    vi.mocked(testAiConfiguration).mockRejectedValue(
      new ApiError('upstream-secret-response-body', code, 503, 'trace-ai-7'),
    )
    const wrapper = mount(AiConfigurationView)
    await flushPromises()

    await wrapper.get('[data-test="test-connection"]').trigger('click')
    await flushPromises()

    const result = wrapper.get('[data-test="connection-result"]').text()
    expect(result).toContain(copy)
    expect(result).toContain('trace-ai-7')
    expect(result).not.toContain('upstream-secret-response-body')
  })
})
