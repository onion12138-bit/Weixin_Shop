import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import {
  deleteAiRecommendationLog,
  getAiRecommendationLog,
  listAiRecommendationLogs,
  type AiRecommendationLogDetail,
  type AiRecommendationLogSummary,
} from '../api/ai'
import AiRecommendationLogsView from './AiRecommendationLogsView.vue'

vi.mock('../api/ai', () => ({
  listAiRecommendationLogs: vi.fn(),
  getAiRecommendationLog: vi.fn(),
  deleteAiRecommendationLog: vi.fn(),
}))

const summary: AiRecommendationLogSummary = {
  id: 7,
  traceId: 'trace-blue-7',
  status: 'SUCCEEDED',
  modelName: 'deepseek-v4-flash',
  wishSummary: '我还想要蓝宝石',
  preferenceSummary: '蓝色系',
  candidateCount: 24,
  matchedCandidateCount: 3,
  matchedPlanCount: 1,
  planCount: 3,
  attemptCount: 1,
  durationMs: 812,
  errorCode: null,
  createdAt: '2026-08-09T12:00:00',
}

const detail: AiRecommendationLogDetail = {
  summary,
  errorMessage: null,
  requestJson: '{"wish":"我还想要蓝宝石"}',
  configSnapshotJson: '{"preferencePrompt":"识别颜色和标签"}',
  candidatesJson: '[{"skuId":2030,"color":"深蓝金星","tags":["深蓝"]}]',
  attemptsJson: '[{"responseStatus":200}]',
  preferenceAnalysisJson: '{"summary":"蓝色系"}',
  finalResponseJson: '{"plans":[]}',
  updatedAt: '2026-08-09T12:00:01',
}

function deferred<T>() {
  let resolve!: (value: T) => void
  let reject!: (reason: unknown) => void
  const promise = new Promise<T>((yes, no) => { resolve = yes; reject = no })
  return { promise, resolve, reject }
}

describe('AiRecommendationLogsView', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    vi.mocked(listAiRecommendationLogs).mockResolvedValue({
      items: [summary], total: 1, page: 1, size: 20,
    })
    vi.mocked(getAiRecommendationLog).mockResolvedValue(detail)
    vi.mocked(deleteAiRecommendationLog).mockResolvedValue(undefined)
  })

  it('shows trace, user wish, preference summary and observational coverage', async () => {
    const wrapper = mount(AiRecommendationLogsView)
    await flushPromises()

    const row = wrapper.get('[data-test="log-row-7"]')
    expect(row.text()).toContain('trace-blue-7')
    expect(row.text()).toContain('我还想要蓝宝石')
    expect(row.text()).toContain('蓝色系')
    expect(row.text()).toContain('1/3 套采用')
  })

  it('opens the complete request, catalog, model and result detail', async () => {
    const wrapper = mount(AiRecommendationLogsView)
    await flushPromises()
    await wrapper.get('[data-test="log-row-7"] .secondary-button').trigger('click')
    await flushPromises()

    expect(getAiRecommendationLog).toHaveBeenCalledWith(7)
    expect(wrapper.get('[data-test="log-detail"]').attributes('role'))
      .toBe('dialog')
    expect(wrapper.get('[data-test="log-detail"]').classes())
      .toContain('ai-log-detail-modal')
    expect(wrapper.get('[data-test="preference-observation"]').text())
      .toContain('已识别偏好：蓝色系｜匹配候选 3 款｜最终 1/3 套使用匹配珠子')
    expect(wrapper.get('[data-test="log-detail"]').text()).toContain('候选珠子、颜色与标签')
    expect(wrapper.get('[data-test="log-detail"]').text()).toContain('模型请求与原始输出')
    expect(wrapper.get('[data-test="log-detail"]').text()).toContain('深蓝金星')

    await wrapper.get('[data-test="close-log-detail"]').trigger('click')
    expect(wrapper.find('[data-test="log-detail"]').exists()).toBe(false)
  })

  it('resets status and keyword before reloading the first page', async () => {
    const wrapper = mount(AiRecommendationLogsView)
    await flushPromises()

    await wrapper.get('[data-test="log-status-filter"]').setValue('FAILED')
    await wrapper.get('[data-test="log-keyword-filter"]').setValue('蓝色')
    await wrapper.get('[data-test="log-reset"]').trigger('click')
    await flushPromises()

    expect(wrapper.get<HTMLSelectElement>('[data-test="log-status-filter"]')
      .element.value).toBe('')
    expect(wrapper.get<HTMLInputElement>('[data-test="log-keyword-filter"]')
      .element.value).toBe('')
    expect(listAiRecommendationLogs).toHaveBeenLastCalledWith(1, 20, '', '')
  })

  it('deletes only after confirmation and refreshes the list', async () => {
    const confirm = vi.spyOn(window, 'confirm').mockReturnValue(true)
    const wrapper = mount(AiRecommendationLogsView)
    await flushPromises()
    await wrapper.get('[data-test="log-row-7"] .danger-button').trigger('click')
    await flushPromises()

    expect(confirm).toHaveBeenCalled()
    expect(deleteAiRecommendationLog).toHaveBeenCalledWith(7)
    expect(listAiRecommendationLogs).toHaveBeenCalledTimes(2)
  })

  it('closes a loading detail without reopening it when the request completes', async () => {
    const pending = deferred<AiRecommendationLogDetail>()
    vi.mocked(getAiRecommendationLog).mockReturnValueOnce(pending.promise)
    const wrapper = mount(AiRecommendationLogsView)
    await flushPromises()
    await wrapper.get('[data-test="log-row-7"] .secondary-button').trigger('click')

    await wrapper.get('.ai-log-modal-backdrop').trigger('click')
    expect(wrapper.find('.ai-log-modal-backdrop').exists()).toBe(false)
    pending.resolve(detail)
    await flushPromises()
    expect(wrapper.find('.ai-log-modal-backdrop').exists()).toBe(false)
    wrapper.unmount()
  })

  it.each(['success', 'failure'])('ignores an older detail %s while a newer detail is loading', async (outcome) => {
    const older = deferred<AiRecommendationLogDetail>()
    const latest = deferred<AiRecommendationLogDetail>()
    vi.mocked(getAiRecommendationLog).mockReturnValueOnce(older.promise).mockReturnValueOnce(latest.promise)
    vi.mocked(listAiRecommendationLogs).mockResolvedValueOnce({
      items: [summary, { ...summary, id: 8, traceId: 'trace-new-8' }], total: 2, page: 1, size: 20,
    })
    const wrapper = mount(AiRecommendationLogsView)
    await flushPromises()
    await wrapper.get('[data-test="log-row-7"] .secondary-button').trigger('click')
    await wrapper.get('.ai-log-modal-backdrop').trigger('click')
    await wrapper.get('[data-test="log-row-8"] .secondary-button').trigger('click')

    if (outcome === 'success') older.resolve(detail)
    else older.reject(new Error('older request failed'))
    await flushPromises()
    expect(wrapper.get('.ai-log-detail-loading').text()).toContain('正在读取详情')
    expect(wrapper.find('[role="alert"]').exists()).toBe(false)

    latest.resolve({ ...detail, summary: { ...summary, id: 8, traceId: 'trace-new-8' } })
    await flushPromises()
    expect(wrapper.get('[data-test="log-detail"]').text()).toContain('trace-new-8')
    wrapper.unmount()
  })

  it('does not publish a detail response after unmount', async () => {
    const pending = deferred<AiRecommendationLogDetail>()
    vi.mocked(getAiRecommendationLog).mockReturnValueOnce(pending.promise)
    const wrapper = mount(AiRecommendationLogsView)
    await flushPromises()
    await wrapper.get('[data-test="log-row-7"] .secondary-button').trigger('click')
    const state = wrapper.vm as unknown as { selected: AiRecommendationLogDetail | null }

    wrapper.unmount()
    pending.resolve(detail)
    await flushPromises()

    expect(state.selected).toBeNull()
  })
})
