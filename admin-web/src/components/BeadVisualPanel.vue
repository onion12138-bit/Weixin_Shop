<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ApiError, resolveApiResourceUrl } from '../api/http'
import {
  createManualBeadVisual,
  listBeadVisuals,
  publishBeadVisual,
  rollbackBeadVisual,
  uploadCatalogFile,
  type BeadVisualHistory,
  type BeadVisualStatus,
  type BeadVisualVersion,
} from '../api/catalog'

const props = defineProps<{
  beadId: number
  beadName: string
}>()

const emit = defineEmits<{
  close: []
  published: []
}>()

const MAX_VISUAL_FILE_SIZE = 500_000
const ACCEPTED_VISUAL_TYPES = new Set(['image/png', 'image/webp'])

const history = ref<BeadVisualHistory>({
  publishedVisualVersionId: null,
  versions: [],
})
const trayFile = ref<File | null>(null)
const canvasFile = ref<File | null>(null)
const loading = ref(false)
const creating = ref(false)
const actionVersionId = ref<number | null>(null)
const mutationBusy = ref(false)
const errorMessage = ref('')
const successMessage = ref('')
let latestVisualRequest = 0

const publishedVisual = computed(() => history.value.versions.find(
  (version) => version.id === history.value.publishedVisualVersionId,
) || history.value.versions.find((version) => version.status === 'PUBLISHED'))

function statusLabel(status: BeadVisualStatus): string {
  return {
    DRAFT: '草稿',
    GENERATING: '生成中',
    READY: '待发布',
    FAILED: '失败',
    PUBLISHED: '已发布',
    ARCHIVED: '已归档',
  }[status]
}

function readableError(error: unknown): string {
  if (error instanceof ApiError) {
    return error.traceId
      ? `${error.message}（追踪号：${error.traceId}）`
      : error.message
  }
  return '请求失败，请确认后端服务已启动'
}

function imageUrl(url: string | null): string {
  return url ? resolveApiResourceUrl(url) : ''
}

function isReviewCandidate(status: BeadVisualStatus): boolean {
  return status === 'READY' || status === 'ARCHIVED'
}

function setSelectedFile(kind: 'tray' | 'canvas', file: File | undefined): void {
  errorMessage.value = ''
  successMessage.value = ''
  if (!file) return
  const allowedExtension = /\.(png|webp)$/i.test(file.name)
  if (!ACCEPTED_VISUAL_TYPES.has(file.type) && !allowedExtension) {
    errorMessage.value = '素材仅支持 PNG 或 WebP 格式'
    return
  }
  if (file.size > MAX_VISUAL_FILE_SIZE) {
    errorMessage.value = '单个视觉素材不能超过 500 KB'
    return
  }
  if (kind === 'tray') {
    trayFile.value = file
  } else {
    canvasFile.value = file
  }
}

function handleFileChange(kind: 'tray' | 'canvas', event: Event): void {
  const input = event.target as HTMLInputElement
  setSelectedFile(kind, input.files?.[0])
}

async function loadVisuals(): Promise<void> {
  const requestId = ++latestVisualRequest
  loading.value = true
  errorMessage.value = ''
  try {
    const nextHistory = await listBeadVisuals(props.beadId)
    if (requestId !== latestVisualRequest) return
    history.value = nextHistory
  } catch (error) {
    if (requestId !== latestVisualRequest) return
    errorMessage.value = readableError(error)
  } finally {
    if (requestId === latestVisualRequest) {
      loading.value = false
    }
  }
}

async function createManual(): Promise<void> {
  if (mutationBusy.value) return
  if (!trayFile.value || !canvasFile.value) {
    errorMessage.value = '请先选择托盘图和画布图'
    return
  }
  mutationBusy.value = true
  creating.value = true
  errorMessage.value = ''
  successMessage.value = ''
  try {
    const [tray, canvas] = await Promise.all([
      uploadCatalogFile(trayFile.value),
      uploadCatalogFile(canvasFile.value),
    ])
    await createManualBeadVisual(props.beadId, {
      trayAssetId: tray.id,
      canvasAssetId: canvas.id,
    })
    successMessage.value = '已创建手工视觉版本，确认预览后即可发布'
    await loadVisuals()
  } catch (error) {
    errorMessage.value = readableError(error)
  } finally {
    creating.value = false
    mutationBusy.value = false
  }
}

async function publish(version: BeadVisualVersion): Promise<void> {
  if (mutationBusy.value || version.status !== 'READY') return
  mutationBusy.value = true
  actionVersionId.value = version.id
  errorMessage.value = ''
  successMessage.value = ''
  try {
    await publishBeadVisual(props.beadId, version.id)
    successMessage.value = `已发布视觉版本 V${version.version}`
    await loadVisuals()
    emit('published')
  } catch (error) {
    errorMessage.value = readableError(error)
  } finally {
    actionVersionId.value = null
    mutationBusy.value = false
  }
}

async function rollback(version: BeadVisualVersion): Promise<void> {
  if (mutationBusy.value || version.status !== 'ARCHIVED') return
  if (!window.confirm(`确定回滚到视觉版本 V${version.version} 吗？`)) return
  mutationBusy.value = true
  actionVersionId.value = version.id
  errorMessage.value = ''
  successMessage.value = ''
  try {
    await rollbackBeadVisual(props.beadId, version.id)
    successMessage.value = `已回滚并发布基于 V${version.version} 的新版本`
    await loadVisuals()
    emit('published')
  } catch (error) {
    errorMessage.value = readableError(error)
  } finally {
    actionVersionId.value = null
    mutationBusy.value = false
  }
}

onMounted(loadVisuals)
</script>

<template>
  <div class="modal-backdrop centered-modal-backdrop" @click.self="emit('close')">
    <section class="drawer visual-drawer" aria-label="DIY 效果管理">
      <header>
        <div>
          <p class="section-kicker">DIY VISUAL / MANUAL</p>
          <h2>{{ beadName }} · DIY 效果</h2>
          <p class="muted">上传透明托盘图与画布图，创建后再手动发布。</p>
        </div>
        <button class="icon-button" aria-label="关闭 DIY 效果" @click="emit('close')">×</button>
      </header>

      <p v-if="errorMessage" class="form-message error-message" role="alert">{{ errorMessage }}</p>
      <p v-if="successMessage" class="form-message success-message" role="status">{{ successMessage }}</p>

      <section class="visual-current-panel">
        <header><strong>当前已发布效果</strong></header>
        <div v-if="publishedVisual" class="visual-preview-grid">
          <figure>
            <img v-if="publishedVisual.trayUrl" :src="imageUrl(publishedVisual.trayUrl)" alt="当前托盘图" />
            <span v-else>暂无托盘图</span>
            <figcaption>托盘图 · V{{ publishedVisual.version }}</figcaption>
          </figure>
          <figure>
            <img v-if="publishedVisual.canvasUrl" :src="imageUrl(publishedVisual.canvasUrl)" alt="当前画布图" />
            <span v-else>暂无画布图</span>
            <figcaption>画布图 · V{{ publishedVisual.version }}</figcaption>
          </figure>
        </div>
        <p v-else class="muted">尚未发布 DIY 效果。</p>
      </section>

      <section class="visual-create-panel">
        <header>
          <strong>创建手工版本</strong>
          <small>仅 PNG / WebP，单个文件最大 500 KB。</small>
        </header>
        <div class="visual-file-grid">
          <label>
            <span>托盘图</span>
            <input data-test="tray-file" type="file" accept=".png,.webp" @change="handleFileChange('tray', $event)" />
            <small>{{ trayFile?.name || '请选择透明托盘图' }}</small>
          </label>
          <label>
            <span>画布图</span>
            <input data-test="canvas-file" type="file" accept=".png,.webp" @change="handleFileChange('canvas', $event)" />
            <small>{{ canvasFile?.name || '请选择透明画布图' }}</small>
          </label>
        </div>
        <button
          class="primary-button"
          data-test="create-manual-visual"
          :disabled="mutationBusy || !trayFile || !canvasFile"
          @click="createManual"
        >{{ creating ? '正在创建…' : '创建手工效果' }}</button>
      </section>

      <section class="visual-history-panel">
        <header><strong>版本历史</strong></header>
        <p v-if="loading" class="muted">正在读取视觉版本…</p>
        <p v-else-if="history.versions.length === 0" class="muted">暂无视觉版本。</p>
        <div v-else class="visual-history-list">
          <article v-for="version in history.versions" :key="version.id" class="visual-history-row">
            <div class="visual-history-summary">
              <div>
                <strong>V{{ version.version }}</strong>
                <span class="status-pill" :class="`visual-status-${version.status.toLowerCase()}`">{{ statusLabel(version.status) }}</span>
                <span class="visual-source">{{ version.sourceType === 'MANUAL' ? '手工上传' : '自动生成' }}</span>
                <small>{{ version.createdBy }} · {{ version.createdAt }}</small>
                <small v-if="version.failureMessage" class="visual-failure">{{ version.failureMessage }}</small>
              </div>
              <div class="visual-row-actions">
                <button
                  class="text-button"
                  :data-test="`publish-visual-${version.id}`"
                  :disabled="version.status !== 'READY' || mutationBusy"
                  @click="publish(version)"
                >发布</button>
                <button
                  class="text-button"
                  :data-test="`rollback-visual-${version.id}`"
                  :disabled="version.status !== 'ARCHIVED' || mutationBusy"
                  @click="rollback(version)"
                >回滚</button>
              </div>
            </div>
            <div
              v-if="isReviewCandidate(version.status)"
              class="visual-preview-grid visual-history-preview"
            >
              <figure>
                <img
                  v-if="version.trayUrl"
                  :src="imageUrl(version.trayUrl)"
                  :alt="`V${version.version} 托盘图`"
                  :data-test="`history-tray-preview-${version.id}`"
                />
                <span v-else>暂无托盘图</span>
                <figcaption>候选托盘图 · V{{ version.version }}</figcaption>
              </figure>
              <figure>
                <img
                  v-if="version.canvasUrl"
                  :src="imageUrl(version.canvasUrl)"
                  :alt="`V${version.version} 画布图`"
                  :data-test="`history-canvas-preview-${version.id}`"
                />
                <span v-else>暂无画布图</span>
                <figcaption>候选画布图 · V{{ version.version }}</figcaption>
              </figure>
            </div>
          </article>
        </div>
      </section>
    </section>
  </div>
</template>
