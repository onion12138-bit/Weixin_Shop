import { flushPromises, mount } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
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

function deferred<T>() {
  let resolve!: (value: T) => void
  const promise = new Promise<T>((resolvePromise) => {
    resolve = resolvePromise
  })
  return { promise, resolve }
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

  afterEach(() => {
    vi.restoreAllMocks()
  })

  it('uses fixed trace-aware copy when loading fails', async () => {
    vi.mocked(getAiConfiguration).mockRejectedValue(
      new ApiError(
        'sensitive-server-load-detail',
        'COMMON_INTERNAL_ERROR',
        500,
        'trace-load-7',
      ),
    )
    const wrapper = mount(AiConfigurationView)
    await flushPromises()

    expect(wrapper.get('[role="alert"]').text()).toContain('AI 配置读取失败')
    expect(wrapper.get('[role="alert"]').text()).toContain('trace-load-7')
    expect(wrapper.text()).not.toContain('sensitive-server-load-detail')
  })

  it('hides default configuration after load failure and retries explicitly', async () => {
    vi.mocked(getAiConfiguration)
      .mockRejectedValueOnce(
        new ApiError(
          'hidden-load-detail',
          'COMMON_INTERNAL_ERROR',
          500,
          'trace-retry-7',
        ),
      )
      .mockResolvedValueOnce(configuration())
    const wrapper = mount(AiConfigurationView)
    await flushPromises()

    expect(wrapper.find('[data-test="configuration-form"]').exists())
      .toBe(false)
    expect(wrapper.find('[data-test="configuration-version"]').exists())
      .toBe(false)
    expect(wrapper.get('[data-test="reload-configuration"]').text())
      .toContain('重新加载')

    await wrapper.get('[data-test="reload-configuration"]').trigger('click')
    await flushPromises()

    expect(getAiConfiguration).toHaveBeenCalledTimes(2)
    expect(wrapper.get('[data-test="configuration-version"]').text())
      .toBe('v5')
  })

  it('loads the DeepSeek configuration without ever echoing its API key', async () => {
    const wrapper = mount(AiConfigurationView)
    await flushPromises()

    expect(wrapper.get('[data-test="provider"]').attributes('readonly'))
      .toBeDefined()
    expect(wrapper.get('[data-test="model-name"]').attributes('readonly'))
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

  it('clears a replacement API key only after a successful save', async () => {
    const wrapper = mount(AiConfigurationView)
    await flushPromises()
    await wrapper.get('[data-test="api-key"]').setValue('sk-replacement')

    await wrapper.get('[data-test="configuration-form"]').trigger('submit')
    await flushPromises()

    expect(saveAiConfiguration).toHaveBeenCalledWith(
      expect.objectContaining({ apiKey: 'sk-replacement' }),
    )
    expect(wrapper.get('[data-test="api-key"]').element)
      .toMatchObject({ value: '' })
  })

  it('retains the API key when client-side validation fails', async () => {
    const wrapper = mount(AiConfigurationView)
    await flushPromises()
    await wrapper.get('[data-test="api-key"]').setValue('sk-retry-validation')
    await wrapper.get('input[type="url"]').setValue('')

    await wrapper.get('[data-test="configuration-form"]').trigger('submit')
    await flushPromises()

    expect(saveAiConfiguration).not.toHaveBeenCalled()
    expect(wrapper.get('[data-test="api-key"]').element)
      .toMatchObject({ value: 'sk-retry-validation' })
  })

  it('retains the API key and hides server detail when save fails', async () => {
    vi.mocked(saveAiConfiguration).mockRejectedValue(
      new ApiError(
        'sensitive-server-save-detail',
        'COMMON_CONFLICT',
        409,
        'trace-save-7',
      ),
    )
    const wrapper = mount(AiConfigurationView)
    await flushPromises()
    await wrapper.get('[data-test="api-key"]').setValue('sk-retry-save')

    await wrapper.get('[data-test="configuration-form"]').trigger('submit')
    await flushPromises()

    expect(wrapper.get('[role="alert"]').text()).toContain('AI 配置保存失败')
    expect(wrapper.get('[role="alert"]').text()).toContain('trace-save-7')
    expect(wrapper.text()).not.toContain('sensitive-server-save-detail')
    expect(wrapper.get('[data-test="api-key"]').element)
      .toMatchObject({ value: 'sk-retry-save' })
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

  it('retains the draft key and hides server detail when clearing fails', async () => {
    vi.spyOn(window, 'confirm').mockReturnValue(true)
    vi.mocked(clearAiApiKey).mockRejectedValue(
      new ApiError(
        'sensitive-server-clear-detail',
        'COMMON_CONFLICT',
        409,
        'trace-clear-7',
      ),
    )
    const wrapper = mount(AiConfigurationView)
    await flushPromises()
    await wrapper.get('[data-test="api-key"]').setValue('sk-retry-clear')

    await wrapper.get('[data-test="clear-api-key"]').trigger('click')
    await flushPromises()

    expect(wrapper.get('[role="alert"]').text()).toContain('API Key 清除失败')
    expect(wrapper.get('[role="alert"]').text()).toContain('trace-clear-7')
    expect(wrapper.text()).not.toContain('sensitive-server-clear-detail')
    expect(wrapper.get('[data-test="api-key"]').element)
      .toMatchObject({ value: 'sk-retry-clear' })
  })

  it('disables every conflicting action while an operation is pending', async () => {
    const pendingSave = deferred<AiConfiguration>()
    vi.mocked(saveAiConfiguration).mockReturnValue(pendingSave.promise)
    const saveWrapper = mount(AiConfigurationView)
    await flushPromises()
    await saveWrapper.get('[data-test="configuration-form"]').trigger('submit')

    expect(saveWrapper.get('[data-test="test-connection"]').attributes('disabled'))
      .toBeDefined()
    expect(saveWrapper.get('[data-test="clear-api-key"]').attributes('disabled'))
      .toBeDefined()
    pendingSave.resolve(configuration({ version: 6 }))
    await flushPromises()

    const pendingTest = deferred<void>()
    vi.mocked(testAiConfiguration).mockReturnValue(pendingTest.promise)
    const testWrapper = mount(AiConfigurationView)
    await flushPromises()
    await testWrapper.get('[data-test="test-connection"]').trigger('click')

    expect(testWrapper.get('button[type="submit"]').attributes('disabled'))
      .toBeDefined()
    expect(testWrapper.get('[data-test="clear-api-key"]').attributes('disabled'))
      .toBeDefined()
    pendingTest.resolve()
    await flushPromises()

    const pendingClear = deferred<AiConfiguration>()
    vi.spyOn(window, 'confirm').mockReturnValue(true)
    vi.mocked(clearAiApiKey).mockReturnValue(pendingClear.promise)
    const clearWrapper = mount(AiConfigurationView)
    await flushPromises()
    await clearWrapper.get('[data-test="clear-api-key"]').trigger('click')

    expect(clearWrapper.get('button[type="submit"]').attributes('disabled'))
      .toBeDefined()
    expect(clearWrapper.get('[data-test="test-connection"]').attributes('disabled'))
      .toBeDefined()
    pendingClear.resolve(configuration({ apiKeyConfigured: false, version: 6 }))
    await flushPromises()
  })

  it('disables editable inputs while save or clear is pending', async () => {
    const pendingSave = deferred<AiConfiguration>()
    vi.mocked(saveAiConfiguration).mockReturnValue(pendingSave.promise)
    const saveWrapper = mount(AiConfigurationView)
    await flushPromises()
    await saveWrapper.get('[data-test="configuration-form"]').trigger('submit')

    const saveFields = saveWrapper.get('[data-test="configuration-fields"]')
    expect(saveFields.attributes('disabled')).toBeDefined()
    expect(saveWrapper.get('[data-test="model-name"]').element.matches(':disabled'))
      .toBe(true)
    expect(saveWrapper.get('[data-test="api-key"]').element.matches(':disabled'))
      .toBe(true)
    pendingSave.resolve(configuration({ version: 6 }))
    await flushPromises()

    const pendingClear = deferred<AiConfiguration>()
    vi.spyOn(window, 'confirm').mockReturnValue(true)
    vi.mocked(clearAiApiKey).mockReturnValue(pendingClear.promise)
    const clearWrapper = mount(AiConfigurationView)
    await flushPromises()
    await clearWrapper.get('[data-test="clear-api-key"]').trigger('click')

    const clearFields = clearWrapper.get('[data-test="configuration-fields"]')
    expect(clearFields.attributes('disabled')).toBeDefined()
    expect(clearWrapper.get('[data-test="model-name"]').element.matches(':disabled'))
      .toBe(true)
    expect(clearWrapper.get('[data-test="api-key"]').element.matches(':disabled'))
      .toBe(true)
    pendingClear.resolve(configuration({ apiKeyConfigured: false, version: 6 }))
    await flushPromises()
  })

  it.each([
    ['input[type="url"]', 'https://api.deepseek.com/'],
    ['input[type="number"]', '31'],
    ['input[type="number"]:nth-of-type(2)', '4096'],
  ])('requires saving after editing %s before testing', async (selector, value) => {
    const wrapper = mount(AiConfigurationView)
    await flushPromises()

    const numericInputs = wrapper.findAll('input[type="number"]')
    const input = selector === 'input[type="number"]:nth-of-type(2)'
      ? numericInputs[1]
      : selector === 'input[type="number"]'
        ? numericInputs[0]
        : wrapper.get(selector)
    await input.setValue(value)

    expect(wrapper.get('[data-test="test-connection"]').attributes('disabled'))
      .toBeDefined()
    expect(wrapper.get('[data-test="save-before-test"]').text())
      .toContain('请先保存配置')
  })

  it('requires saving after changing the enabled switch before testing', async () => {
    const wrapper = mount(AiConfigurationView)
    await flushPromises()

    await wrapper.get('input[type="checkbox"]').setValue(false)

    expect(wrapper.get('[data-test="test-connection"]').attributes('disabled'))
      .toBeDefined()
    expect(wrapper.get('[data-test="save-before-test"]').text())
      .toContain('请先保存配置')
  })

  it('associates the API key input with its visible label', async () => {
    const wrapper = mount(AiConfigurationView)
    await flushPromises()

    expect(wrapper.get('label[for="ai-api-key"]').text())
      .toContain('DeepSeek API Key')
    expect(wrapper.get('[data-test="api-key"]').attributes('id'))
      .toBe('ai-api-key')
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

  it('uses the saved model in success copy when the form changes in flight', async () => {
    const pending = deferred<void>()
    vi.mocked(testAiConfiguration).mockReturnValue(pending.promise)
    const wrapper = mount(AiConfigurationView)
    await flushPromises()

    await wrapper.get('[data-test="test-connection"]').trigger('click')
    await wrapper.get('[data-test="model-name"]').setValue('deepseek-v4-pro')
    pending.resolve()
    await flushPromises()

    expect(wrapper.get('[data-test="connection-result"]').text())
      .toContain('连接成功，模型 deepseek-v4-flash 可用')
    expect(wrapper.get('[data-test="connection-result"]').text())
      .not.toContain('deepseek-v4-pro')
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
