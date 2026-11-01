import { uploadSchemePreview } from './diy'
import { ApiError } from './request'

interface PreviewJob {
  schemeId: number
  version: number
  filePath: string
}

const STORAGE_KEY = 'diy_pending_previews_v2'
const jobs = new Map<number, PreviewJob>()
const running = new Map<number, PreviewJob>()
const ready = new Map<number, { version: number; url: string }>()
const listeners = new Set<(schemeId: number, version: number, url: string) => void>()
let restored = false

function restore() {
  if (restored) return
  restored = true
  try {
    const raw = wx.getStorageSync(STORAGE_KEY)
    const entries: unknown = raw ? JSON.parse(raw) : []
    if (!Array.isArray(entries)) return
    for (const entry of entries) {
      if (entry && Number.isSafeInteger(entry.schemeId) && entry.schemeId > 0
        && Number.isSafeInteger(entry.version) && entry.version >= 0
        && typeof entry.filePath === 'string') jobs.set(entry.schemeId, entry)
    }
  } catch { /* 损坏的本地缓存不影响设计保存。 */ }
}

function persist() {
  try { wx.setStorageSync(STORAGE_KEY, JSON.stringify(Array.from(jobs.values()))) }
  catch { /* 当前会话仍保留补传任务。 */ }
}

export function hasPendingSchemePreview(schemeId: number): boolean {
  restore()
  return jobs.has(schemeId)
}

export function onSchemePreviewReady(listener: (schemeId: number, version: number, url: string) => void): () => void {
  listeners.add(listener)
  return () => { listeners.delete(listener) }
}

export function readySchemePreviewUrl(schemeId: number, version: number): string {
  const preview = ready.get(schemeId)
  return preview?.version === version ? preview.url : ''
}

// 不持有页面或修改页面版本；生成器必须只使用保存时的不可变设计快照。
export function queueSchemePreview(
  schemeId: number,
  version: number,
  generate: () => Promise<string>,
): void {
  restore()
  const existing = jobs.get(schemeId)
  if (existing && existing.version > version) return
  if (existing?.version === version && running.get(schemeId) === existing) return
  const job = { schemeId, version, filePath: '' }
  ready.delete(schemeId)
  jobs.set(schemeId, job)
  persist()
  // 让保存反馈和页面跳转先完成，图片生成不占用当前点击处理。
  setTimeout(() => { void run(job, generate) }, 0)
}

async function run(job: PreviewJob, generate?: () => Promise<string>): Promise<void> {
  if (jobs.get(job.schemeId) !== job || running.get(job.schemeId) === job) return
  running.set(job.schemeId, job)
  try {
    if (!job.filePath && generate) job.filePath = await generate()
    if (jobs.get(job.schemeId) !== job || !job.filePath) return
    persist()
    const saved = await uploadSchemePreview(job.schemeId, job.filePath, job.version)
    if (jobs.get(job.schemeId) === job) {
      jobs.delete(job.schemeId)
      if (saved?.previewUrl) {
        ready.set(job.schemeId, { version: job.version, url: saved.previewUrl })
        for (const listener of listeners) {
          try { listener(job.schemeId, job.version, saved.previewUrl) } catch { /* 不阻塞其他页面。 */ }
        }
      }
    }
  } catch (error) {
    // 已被新设计替代、删除或下单的旧任务无需重试。
    if (jobs.get(job.schemeId) === job && error instanceof ApiError
      && ['DIY_SCHEME_VERSION_CONFLICT', 'COMMON_NOT_FOUND'].includes(error.code)) {
      jobs.delete(job.schemeId)
    }
  } finally {
    if (running.get(job.schemeId) === job) running.delete(job.schemeId)
    persist()
  }
}

export async function retryPendingSchemePreviews(): Promise<void> {
  restore()
  await Promise.all(Array.from(jobs.values())
    .filter((job) => job.filePath && running.get(job.schemeId) !== job)
    .map((job) => run(job)))
}
