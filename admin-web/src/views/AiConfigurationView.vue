<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import {
  clearAiApiKey,
  getAiConfiguration,
  revealAiApiKey,
  saveAiConfiguration,
  testAiConfiguration,
  type AiConfiguration,
  type SaveAiConfigurationPayload,
} from '../api/ai'
import { ApiError } from '../api/http'

const OFFICIAL_BASE_URL = 'https://api.deepseek.com'
const MASKED_API_KEY = '••••••••••••••••'

const loading = ref(true)
const loaded = ref(false)
const saving = ref(false)
const testing = ref(false)
const clearing = ref(false)
const revealing = ref(false)
const apiKey = ref('')
const apiKeyVisible = ref(false)
const revealedStoredKey = ref(false)
const maskedStoredKey = ref(false)
const errorMessage = ref('')
const successMessage = ref('')
const connectionMessage = ref('')
const connectionSucceeded = ref(false)
const lastSavedConfiguration = ref<AiConfiguration | null>(null)

const configuration = reactive<AiConfiguration>({
  provider: 'DEEPSEEK',
  baseUrl: OFFICIAL_BASE_URL,
  modelName: 'deepseek-v4-flash',
  apiKeyConfigured: false,
  enabled: false,
  timeoutSeconds: 30,
  maxOutputTokens: 2048,
  recommendationPrompt: '',
  planCount: 3,
  version: 0,
})

const hasUnsavedChanges = computed(() => {
  const saved = lastSavedConfiguration.value
  if (!saved) return true
  return (apiKey.value !== ''
      && !revealedStoredKey.value
      && !maskedStoredKey.value)
    || configuration.baseUrl !== saved.baseUrl
    || configuration.modelName !== saved.modelName
    || configuration.enabled !== saved.enabled
    || Number(configuration.timeoutSeconds) !== saved.timeoutSeconds
    || Number(configuration.maxOutputTokens) !== saved.maxOutputTokens
    || configuration.recommendationPrompt !== saved.recommendationPrompt
    || Number(configuration.planCount) !== saved.planCount
})

function applyConfiguration(next: AiConfiguration): void {
  Object.assign(configuration, next)
  lastSavedConfiguration.value = { ...next }
  apiKey.value = next.apiKeyConfigured ? MASKED_API_KEY : ''
  apiKeyVisible.value = false
  revealedStoredKey.value = false
  maskedStoredKey.value = next.apiKeyConfigured
}

function withTrace(message: string, error: ApiError): string {
  return error.traceId
    ? `${message}（追踪号：${error.traceId}）`
    : message
}

function readableOperationError(error: unknown, fallback: string): string {
  if (error instanceof ApiError) {
    return withTrace(fallback, error)
  }
  return fallback
}

const connectionErrorCopies: Record<string, string> = {
  AI_NOT_CONFIGURED: '尚未配置 API Key，无法测试连接',
  AI_DISABLED: 'AI 服务已停用，无法测试连接',
  AI_UNAUTHORIZED: 'API Key 鉴权失败，请检查后重新保存',
  AI_RATE_LIMITED: 'DeepSeek 请求过于频繁，请稍后重试',
  AI_MODEL_UNAVAILABLE: '配置的模型不存在或暂不可用',
  AI_TEMPORARILY_UNAVAILABLE: 'DeepSeek 连接超时或服务暂不可用',
}

function safeConnectionError(error: unknown): string {
  if (error instanceof ApiError) {
    const message = connectionErrorCopies[error.code] || '测试连接失败，请稍后重试'
    return withTrace(message, error)
  }
  return '测试连接失败，请确认网络和后端服务状态'
}

function clearMessages(): void {
  errorMessage.value = ''
  successMessage.value = ''
  connectionMessage.value = ''
  connectionSucceeded.value = false
}

function integerInRange(
  value: number,
  min: number,
  max: number,
  label: string,
): number {
  const parsed = Number(value)
  if (!Number.isInteger(parsed) || parsed < min || parsed > max) {
    throw new Error(`${label}必须是 ${min}–${max} 的整数`)
  }
  return parsed
}

function savePayload(): SaveAiConfigurationPayload {
  const baseUrl = configuration.baseUrl.trim()
  const modelName = configuration.modelName.trim()
  const recommendationPrompt = configuration.recommendationPrompt.trim()
  if (!baseUrl) throw new Error('Base URL 不能为空')
  if (!modelName) throw new Error('模型名称不能为空')
  if (!recommendationPrompt) throw new Error('推荐提示词不能为空')

  return {
    baseUrl,
    modelName,
    apiKey: revealedStoredKey.value || maskedStoredKey.value
      ? ''
      : apiKey.value,
    enabled: configuration.enabled,
    timeoutSeconds: integerInRange(
      configuration.timeoutSeconds,
      1,
      120,
      '请求超时',
    ),
    maxOutputTokens: integerInRange(
      configuration.maxOutputTokens,
      256,
      16384,
      '最大输出 Token',
    ),
    recommendationPrompt,
    planCount: integerInRange(
      configuration.planCount,
      1,
      5,
      '推荐套数',
    ),
    version: configuration.version,
  }
}

async function loadConfiguration(): Promise<void> {
  loading.value = true
  loaded.value = false
  errorMessage.value = ''
  try {
    applyConfiguration(await getAiConfiguration())
    loaded.value = true
  } catch (error) {
    errorMessage.value = readableOperationError(error, 'AI 配置读取失败')
  } finally {
    loading.value = false
  }
}

async function saveConfiguration(): Promise<void> {
  if (saving.value || testing.value || clearing.value || revealing.value) return
  saving.value = true
  clearMessages()

  let payload: SaveAiConfigurationPayload
  try {
    payload = savePayload()
  } catch (error) {
    errorMessage.value = error instanceof Error
      ? error.message
      : 'AI 配置校验失败'
    saving.value = false
    return
  }

  try {
    applyConfiguration(await saveAiConfiguration(payload))
    successMessage.value = 'AI 配置已保存'
  } catch (error) {
    errorMessage.value = readableOperationError(error, 'AI 配置保存失败')
  } finally {
    saving.value = false
  }
}

async function testConnection(): Promise<void> {
  if (testing.value || saving.value || clearing.value || revealing.value) return
  if (hasUnsavedChanges.value) {
    connectionSucceeded.value = false
    connectionMessage.value = '请先保存配置'
    return
  }
  testing.value = true
  clearMessages()
  try {
    await testAiConfiguration()
    connectionSucceeded.value = true
    connectionMessage.value = `连接成功，模型 ${lastSavedConfiguration.value!.modelName} 可用`
  } catch (error) {
    connectionMessage.value = safeConnectionError(error)
  } finally {
    testing.value = false
  }
}

async function clearApiKey(): Promise<void> {
  if (
    saving.value
    || testing.value
    || clearing.value
    || revealing.value
    || !configuration.apiKeyConfigured
  ) return
  const confirmed = window.confirm(
    '确定清除当前 DeepSeek API Key？清除后 AI 服务将无法调用。',
  )
  if (!confirmed) return

  clearing.value = true
  clearMessages()
  try {
    applyConfiguration(await clearAiApiKey(configuration.version))
    successMessage.value = 'API Key 已清除'
  } catch (error) {
    errorMessage.value = readableOperationError(error, 'API Key 清除失败')
  } finally {
    clearing.value = false
  }
}

function markApiKeyEdited(): void {
  revealedStoredKey.value = false
  maskedStoredKey.value = false
}

function beginApiKeyReplacement(): void {
  if (maskedStoredKey.value) {
    apiKey.value = ''
  }
}

function restoreMaskedApiKey(): void {
  if (maskedStoredKey.value && apiKey.value === '') {
    apiKey.value = MASKED_API_KEY
  }
}

async function toggleApiKeyVisibility(): Promise<void> {
  if (saving.value || testing.value || clearing.value || revealing.value) return
  if (apiKeyVisible.value) {
    apiKeyVisible.value = false
    return
  }
  if (!maskedStoredKey.value
      && (apiKey.value || !configuration.apiKeyConfigured)) {
    apiKeyVisible.value = true
    return
  }

  revealing.value = true
  clearMessages()
  try {
    const revealed = await revealAiApiKey()
    apiKey.value = revealed.apiKey
    revealedStoredKey.value = true
    maskedStoredKey.value = false
    apiKeyVisible.value = true
  } catch (error) {
    errorMessage.value = readableOperationError(error, 'API Key 读取失败')
  } finally {
    revealing.value = false
  }
}

onMounted(loadConfiguration)
</script>

<template>
  <section class="settings-workspace ai-configuration-workspace">
    <header class="content-header">
      <div>
        <p class="section-kicker">PLATFORM / AI MODEL</p>
        <h1>AI 模型配置</h1>
        <p class="muted">
          维护唯一一套 DeepSeek 连接参数。已保存的 API Key 仅在管理员点击眼睛按钮后临时显示。
        </p>
      </div>
    </header>

    <div v-if="errorMessage" class="notice error-notice" role="alert">
      {{ errorMessage }}
    </div>
    <div v-if="successMessage" class="notice success-notice" role="status">
      {{ successMessage }}
    </div>

    <div v-if="loading" class="empty-state">正在读取 AI 配置…</div>

    <div v-else-if="!loaded" class="empty-state ai-load-failure">
      <p>当前无法显示 AI 配置。</p>
      <button
        class="secondary-button"
        data-test="reload-configuration"
        type="button"
        @click="loadConfiguration"
      >
        重新加载
      </button>
    </div>

    <form
      v-else
      class="settings-card ai-configuration-card"
      data-test="configuration-form"
      @submit.prevent="saveConfiguration"
    >
      <header>
        <div>
          <p class="section-kicker">DEEPSEEK</p>
          <h2>当前连接配置</h2>
        </div>
        <span
          class="version-pill"
          data-test="configuration-version"
        >v{{ configuration.version }}</span>
      </header>

      <fieldset
        class="ai-configuration-fields"
        data-test="configuration-fields"
        :disabled="saving || clearing || revealing"
      >
        <div class="form-grid ai-configuration-grid">
          <label>
            <span>Provider</span>
            <input
              data-test="provider"
              :value="configuration.provider"
              readonly
            />
          </label>
          <label>
            <span>官方 Base URL</span>
            <input
              v-model="configuration.baseUrl"
              type="url"
              required
              maxlength="255"
              :placeholder="OFFICIAL_BASE_URL"
            />
            <small>仅允许 DeepSeek 官方 HTTPS 地址</small>
          </label>
          <label class="ai-model-field">
            <span>模型名称</span>
            <input
              v-model="configuration.modelName"
              data-test="model-name"
              required
              readonly
              maxlength="120"
              placeholder="deepseek-v4-flash"
            />
          </label>
          <label>
            <span>请求超时（秒）</span>
            <input
              v-model.number="configuration.timeoutSeconds"
              type="number"
              min="1"
              max="120"
              step="1"
              required
            />
          </label>
          <label>
            <span>最大输出 Token</span>
            <input
              v-model.number="configuration.maxOutputTokens"
              type="number"
              min="256"
              max="16384"
              step="1"
              required
            />
          </label>
          <label class="switch-field">
            <span>服务状态</span>
            <span class="switch-control">
              <input v-model="configuration.enabled" type="checkbox" />
              <span>{{ configuration.enabled ? '已启用' : '已停用' }}</span>
            </span>
          </label>
        </div>

        <section class="ai-recommendation-panel">
          <header>
            <div>
              <strong>推荐生成设置</strong>
              <small>配置小程序每次请求使用的提示词，以及返回的推荐方案数量。</small>
            </div>
          </header>
          <div class="ai-recommendation-fields">
            <label class="ai-plan-count-field">
              <span>推荐套数</span>
              <input
                v-model.number="configuration.planCount"
                data-test="plan-count"
                type="number"
                min="1"
                max="5"
                step="1"
                required
              />
              <small>每次生成 1–5 套推荐，实际默认值由后端配置返回。</small>
            </label>
            <label class="ai-prompt-field">
              <span>推荐提示词</span>
              <textarea
                v-model="configuration.recommendationPrompt"
                data-test="recommendation-prompt"
                rows="8"
                maxlength="4000"
                required
                placeholder="请输入用于生成手串推荐方案的提示词"
              ></textarea>
              <small>保存后会与顾客生日画像、当前库存珠子名称及材质一起提交给模型；输出格式和安全边界由后端统一控制。</small>
            </label>
          </div>
        </section>

        <section class="ai-key-panel">
          <header>
            <label for="ai-api-key">
              <strong>DeepSeek API Key</strong>
              <small>留空保存会保留当前 Key；点击眼睛按钮可临时查看。</small>
            </label>
            <span
              class="status-pill"
              :class="configuration.apiKeyConfigured ? 'status-on_sale' : 'status-off_sale'"
              data-test="api-key-status"
            >{{ configuration.apiKeyConfigured ? '已配置' : '未配置' }}</span>
          </header>
          <div class="ai-key-input-wrap">
            <input
              v-model="apiKey"
              id="ai-api-key"
              data-test="api-key"
              :type="apiKeyVisible ? 'text' : 'password'"
              autocomplete="new-password"
              spellcheck="false"
              placeholder="输入新的 API Key；留空则保留现有配置"
              @focus="beginApiKeyReplacement"
              @input="markApiKeyEdited"
              @blur="restoreMaskedApiKey"
            />
            <button
              class="ai-key-visibility-button"
              data-test="toggle-api-key-visibility"
              type="button"
              :aria-label="apiKeyVisible ? '隐藏 API Key' : '显示 API Key'"
              :title="apiKeyVisible ? '隐藏 API Key' : '显示 API Key'"
              :disabled="saving || testing || clearing || revealing"
              @click="toggleApiKeyVisibility"
            >
              <span v-if="revealing" aria-hidden="true">…</span>
              <svg v-else viewBox="0 0 24 24" aria-hidden="true">
                <path d="M2.5 12s3.5-6 9.5-6 9.5 6 9.5 6-3.5 6-9.5 6-9.5-6-9.5-6Z" />
                <circle cx="12" cy="12" r="2.7" />
                <path v-if="apiKeyVisible" d="m4 4 16 16" />
              </svg>
            </button>
          </div>
        </section>
      </fieldset>

      <div
        v-if="connectionMessage"
        class="notice connection-result"
        :class="connectionSucceeded ? 'success-notice' : 'error-notice'"
        :role="connectionSucceeded ? 'status' : 'alert'"
        data-test="connection-result"
      >
        {{ connectionMessage }}
      </div>

      <footer class="ai-configuration-actions">
        <button
          class="danger-button"
          data-test="clear-api-key"
          type="button"
          :disabled="saving || testing || clearing || revealing || !configuration.apiKeyConfigured"
          @click="clearApiKey"
        >
          {{ clearing ? '清除中…' : '清除 API Key' }}
        </button>
        <div class="ai-action-stack">
          <small
            v-if="hasUnsavedChanges"
            class="save-before-test"
            data-test="save-before-test"
          >请先保存配置</small>
          <span class="ai-action-buttons">
            <button
              class="secondary-button"
              data-test="test-connection"
              type="button"
              :disabled="testing || saving || clearing || revealing || hasUnsavedChanges"
              @click="testConnection"
            >
              {{ testing ? '测试中…' : '测试连接' }}
            </button>
            <button
              class="primary-button"
              type="submit"
              :disabled="saving || testing || clearing || revealing"
            >
              {{ saving ? '保存中…' : '保存配置' }}
            </button>
          </span>
        </div>
      </footer>
    </form>
  </section>
</template>
