<script setup lang="ts">
import DataTooltip from '../components/DataTooltip.vue'
import OverflowText from '../components/OverflowText.vue'
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import {
  deleteAiRecommendationLog,
  getAiRecommendationLog,
  listAiRecommendationLogs,
  type AiLogStatus,
  type AiRecommendationLogDetail,
  type AiRecommendationLogSummary,
} from '../api/ai'
import { ApiError } from '../api/http'
import ListPagination from '../components/ListPagination.vue'

const logs = ref<AiRecommendationLogSummary[]>([])
const selected = ref<AiRecommendationLogDetail | null>(null)
const loading = ref(false)
const detailLoading = ref(false)
const deletingId = ref<number | null>(null)
const errorMessage = ref('')
const page = ref(1)
const size = ref(20)
const total = ref(0)
const filters = reactive({ status: '' as AiLogStatus | '', keyword: '' })
let latestListRequest = 0
let latestDetailRequest = 0
let disposed = false

const totalPages = computed(() => Math.max(1, Math.ceil(total.value / size.value)))

const statusLabels: Record<AiLogStatus, string> = {
  PROCESSING: '处理中',
  SUCCEEDED: '成功',
  FAILED: '失败',
}

function readableError(error: unknown, fallback: string): string {
  if (error instanceof ApiError) {
    return error.traceId ? `${fallback}（追踪号：${error.traceId}）` : fallback
  }
  return fallback
}

async function load(resetPage = false): Promise<void> {
  if (disposed) return
  const requestId = ++latestListRequest
  if (resetPage) page.value = 1
  loading.value = true
  errorMessage.value = ''
  try {
    const result = await listAiRecommendationLogs(
      page.value, size.value, filters.status, filters.keyword,
    )
    if (requestId !== latestListRequest) return
    const lastPage = Math.max(1, Math.ceil(result.total / size.value))
    if (page.value > lastPage) {
      page.value = lastPage
      await load()
      return
    }
    logs.value = result.items
    total.value = result.total
  } catch (error) {
    if (requestId !== latestListRequest) return
    errorMessage.value = readableError(error, 'AI 调用日志读取失败')
  } finally {
    if (requestId === latestListRequest) loading.value = false
  }
}

async function openDetail(id: number): Promise<void> {
  if (disposed) return
  const requestId = ++latestDetailRequest
  selected.value = null
  detailLoading.value = true
  errorMessage.value = ''
  try {
    const result = await getAiRecommendationLog(id)
    if (requestId !== latestDetailRequest) return
    selected.value = result
  } catch (error) {
    if (requestId !== latestDetailRequest) return
    errorMessage.value = readableError(error, 'AI 日志详情读取失败')
  } finally {
    if (requestId === latestDetailRequest) detailLoading.value = false
  }
}

function closeDetail(): void {
  latestDetailRequest++
  detailLoading.value = false
  selected.value = null
}

async function resetFilters(): Promise<void> {
  filters.status = ''
  filters.keyword = ''
  await load(true)
}

async function remove(log: AiRecommendationLogSummary): Promise<void> {
  if (!window.confirm(`确定删除追踪号 ${log.traceId} 的日志？删除后无法恢复。`)) return
  deletingId.value = log.id
  errorMessage.value = ''
  try {
    await deleteAiRecommendationLog(log.id)
    if (selected.value?.summary.id === log.id) selected.value = null
    await load()
  } catch (error) {
    errorMessage.value = readableError(error, 'AI 日志删除失败')
  } finally {
    deletingId.value = null
  }
}

async function goToPage(next: number): Promise<void> {
  const target = Math.max(1, Math.min(next, totalPages.value))
  if (target === page.value) return
  page.value = target
  await load()
}

function changePageSize(nextSize: number): void {
  size.value = nextSize
  void load(true)
}

function formatTime(value: string): string {
  return new Intl.DateTimeFormat('zh-CN', {
    year: 'numeric', month: '2-digit', day: '2-digit',
    hour: '2-digit', minute: '2-digit', second: '2-digit',
  }).format(new Date(value))
}

function pretty(value: string | null): string {
  if (!value) return '暂无记录'
  try {
    return JSON.stringify(JSON.parse(value), null, 2)
  } catch {
    return value
  }
}

function preferenceObservation(log: AiRecommendationLogSummary): string {
  if (!log.preferenceSummary) return '模型未返回可用的偏好摘要'
  return `已识别偏好：${log.preferenceSummary}｜匹配候选 ${log.matchedCandidateCount} 款｜最终 ${log.matchedPlanCount}/${log.planCount} 套使用匹配珠子`
}

onMounted(() => load())
onBeforeUnmount(() => {
  disposed = true
  latestListRequest++
  latestDetailRequest++
})
</script>

<template>
  <section class="ai-log-workspace">
    <header class="content-header ai-log-titlebar">
      <div>
        <p class="section-kicker">PLATFORM / AI OBSERVABILITY</p>
        <h1>AI 调用日志</h1>
        <p class="muted">查看推荐输入、候选珠子、提示词、模型输出和最终处理结果。API Key 永不记录。</p>
      </div>
    </header>

    <div v-if="errorMessage" class="notice error-notice" role="alert">
      {{ errorMessage }}
    </div>

    <form class="ai-log-filters" @submit.prevent="load(true)">
      <label>
        <span>状态</span>
        <select v-model="filters.status" data-test="log-status-filter">
          <option value="">全部状态</option>
          <option value="SUCCEEDED">成功</option>
          <option value="FAILED">失败</option>
          <option value="PROCESSING">处理中</option>
        </select>
      </label>
      <label class="ai-log-keyword-filter">
        <span>追踪号、期望或偏好</span>
        <input v-model="filters.keyword" data-test="log-keyword-filter" placeholder="输入关键词" />
      </label>
      <div class="ai-log-filter-actions">
        <button class="primary-button" data-test="log-search" type="submit">查询</button>
        <button
          class="secondary-button"
          data-test="log-reset"
          type="button"
          @click="resetFilters"
        >重置</button>
      </div>
    </form>

    <div class="ai-log-layout">
      <section class="table-card ai-log-list-card">
        <div v-if="loading" class="empty-state">正在读取 AI 日志…</div>
        <div v-else-if="logs.length === 0" class="empty-state">暂无符合条件的 AI 调用日志</div>
        <div v-else class="table-scroll">
          <table class="ai-log-table">
            <thead>
              <tr>
                <th>时间 / 状态</th>
                <th>用户期望</th>
                <th>偏好观察</th>
                <th>调用</th>
                <th>操作</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="log in logs" :key="log.id" :data-test="`log-row-${log.id}`">
                <td>
                  <strong>{{ formatTime(log.createdAt) }}</strong>
                  <OverflowText class="trace-code" :text="log.traceId" />
                  <span class="status-pill" :class="`ai-log-status-${log.status.toLowerCase()}`">
                    {{ statusLabels[log.status] }}
                  </span>
                </td>
                <td><OverflowText :text="log.wishSummary || '未填写期望'" :lines="2" /></td>
                <td>
                  <DataTooltip :text="preferenceObservation(log)">
                    <span class="log-preference-copy">{{ log.preferenceSummary || '未识别' }}</span>
                    <small>{{ log.matchedPlanCount }}/{{ log.planCount }} 套采用 · {{ log.candidateCount }} 款候选</small>
                  </DataTooltip>
                </td>
                <td>
                  <span>{{ log.attemptCount }} 次</span>
                  <small>{{ log.durationMs === null ? '—' : `${log.durationMs} ms` }}</small>
                  <small v-if="log.errorCode" class="error-code">{{ log.errorCode }}</small>
                </td>
                <td><div class="ai-log-actions">
                  <button class="secondary-button" type="button" @click="openDetail(log.id)">详情</button>
                  <button
                    class="danger-button"
                    type="button"
                    :disabled="deletingId === log.id"
                    @click="remove(log)"
                  >{{ deletingId === log.id ? '删除中…' : '删除' }}</button>
                </div></td>
              </tr>
            </tbody>
          </table>
        </div>
        <ListPagination :page="page" :size="size" :total="total" @update:page="goToPage" @update:size="changePageSize" />
      </section>
    </div>

    <div
      v-if="detailLoading || selected"
      class="modal-backdrop centered-modal-backdrop ai-log-modal-backdrop"
      @click.self="closeDetail"
    >
      <section
        v-if="detailLoading"
        class="ai-log-detail ai-log-detail-modal ai-log-detail-loading"
        role="status"
      >正在读取详情…</section>
      <section
        v-else-if="selected"
        class="ai-log-detail ai-log-detail-modal"
        data-test="log-detail"
        role="dialog"
        aria-modal="true"
        aria-labelledby="ai-log-detail-title"
      >
        <header>
          <div>
            <p class="section-kicker">TRACE DETAIL</p>
            <h2 id="ai-log-detail-title">调用详情</h2>
          </div>
          <button
            class="secondary-button"
            data-test="close-log-detail"
            type="button"
            @click="closeDetail"
          >关闭</button>
        </header>

        <div class="ai-preference-observation" data-test="preference-observation">
          {{ preferenceObservation(selected.summary) }}
        </div>

        <dl class="ai-log-metadata">
          <div><dt>追踪号</dt><dd>{{ selected.summary.traceId }}</dd></div>
          <div><dt>模型</dt><dd>{{ selected.summary.modelName || '—' }}</dd></div>
          <div><dt>状态</dt><dd>{{ statusLabels[selected.summary.status] }}</dd></div>
          <div><dt>耗时</dt><dd>{{ selected.summary.durationMs ?? '—' }} ms</dd></div>
          <div v-if="selected.summary.errorCode"><dt>错误码</dt><dd>{{ selected.summary.errorCode }}</dd></div>
          <div v-if="selected.errorMessage"><dt>错误说明</dt><dd>{{ selected.errorMessage }}</dd></div>
        </dl>

        <details open>
          <summary>小程序原始输入</summary>
          <pre>{{ pretty(selected.requestJson) }}</pre>
        </details>
        <details>
          <summary>生效配置与提示词</summary>
          <pre>{{ pretty(selected.configSnapshotJson) }}</pre>
        </details>
        <details>
          <summary>候选珠子、颜色与标签</summary>
          <pre>{{ pretty(selected.candidatesJson) }}</pre>
        </details>
        <details>
          <summary>模型请求与原始输出</summary>
          <pre>{{ pretty(selected.attemptsJson) }}</pre>
        </details>
        <details>
          <summary>偏好识别摘要</summary>
          <pre>{{ pretty(selected.preferenceAnalysisJson) }}</pre>
        </details>
        <details>
          <summary>最终返回结果</summary>
          <pre>{{ pretty(selected.finalResponseJson) }}</pre>
        </details>
      </section>
    </div>
  </section>
</template>
