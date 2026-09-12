import { enableAutoUnmount, flushPromises, mount } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { nextTick } from 'vue'
import { ApiError } from '../api/http'
import { getOrderStatistics, listAdminOrders, type AdminOrder } from '../api/orders'
import { listAdminBeads, listAdminCategories, listAdminPackages, type BeadSku, type PageResponse, type ProductPackage } from '../api/catalog'
import { listAiRecommendationLogs, type AiRecommendationLogSummary } from '../api/ai'
import OrderListView from './OrderListView.vue'
import AiRecommendationLogsView from './AiRecommendationLogsView.vue'
import BeadCatalogPanel from './BeadCatalogPanel.vue'
import PackageCatalogPanel from './PackageCatalogPanel.vue'

vi.mock('../api/orders', async (importOriginal) => ({
  ...await importOriginal<typeof import('../api/orders')>(),
  listAdminOrders: vi.fn(),
  getOrderStatistics: vi.fn(),
}))
vi.mock('../api/catalog', async (importOriginal) => ({
  ...await importOriginal<typeof import('../api/catalog')>(),
  listAdminBeads: vi.fn(),
  listAdminCategories: vi.fn(),
  listAdminPackages: vi.fn(),
}))
vi.mock('../api/ai', async (importOriginal) => ({
  ...await importOriginal<typeof import('../api/ai')>(),
  listAiRecommendationLogs: vi.fn(),
}))

enableAutoUnmount(afterEach)

function deferred<T>() {
  let resolve!: (value: T) => void
  let reject!: (reason: unknown) => void
  const promise = new Promise<T>((yes, no) => { resolve = yes; reject = no })
  return { promise, resolve, reject }
}

const order: AdminOrder = {
  id: 1, orderNo: 'ORDER-1', status: 'PENDING_PAYMENT', schemeId: 1,
  beadAmount: 10, craftFee: 0, cordFee: 0, discountAmount: 0, shippingFee: 0,
  totalAmount: 10, couponName: null, contactName: '张三', contactPhone: '13800000000',
  addressDetail: '上海市', orderNote: null,
  product: { name: '手串', schemeNo: '1', wristCircumferenceMm: 160, fitType: 'STANDARD',
    cordName: '弹力绳', beadCount: 1, estimatedMinutes: 1, previewUrl: null },
  items: [], payment: null, fulfillment: null, operations: [],
  paidAt: null, cancelledAt: null, shippedAt: null, completedAt: null,
  createdAt: '2026-09-12T00:00:00', updatedAt: '2026-09-12T00:00:00',
}
const bead: BeadSku = {
  id: 1, categoryId: 1, productType: 'BEAD', skuCode: 'BEAD-1', name: '水晶',
  material: '水晶', color: '透明', diameterMm: 8, salePrice: 10, costPrice: 5,
  stockAlertThreshold: 10, availableQty: 20, reservedQty: 0, soldQty: 0,
  stockWarningStatus: 'NORMAL', coverAssetId: null, coverUrl: null, tagsJson: '[]', status: 'ON_SALE',
}
const productPackage: ProductPackage = {
  id: 1, packageCode: 'PACKAGE-1', name: '水晶手串', description: '日常手串',
  salePrice: 10, coverAssetId: null, status: 'ON_SALE', recommended: false,
}
const log: AiRecommendationLogSummary = {
  id: 1, traceId: 'trace-1', status: 'SUCCEEDED', modelName: 'model', wishSummary: '水晶',
  preferenceSummary: null, candidateCount: 1, matchedCandidateCount: 1, matchedPlanCount: 1,
  planCount: 1, attemptCount: 1, durationMs: 100, errorCode: null, createdAt: '2026-09-12T00:00:00',
}
const counts = {
  total: 41, pendingPayment: 41, inProduction: 0, pendingShipment: 0,
  shipped: 0, completed: 0, cancelled: 0, afterSale: 0, refunded: 0,
}
const fetchPage = vi.fn<() => Promise<PageResponse<unknown>>>()

describe.each([
  { name: 'orders', component: OrderListView, input: 'input[placeholder="精确或模糊查询"]', search: '[data-test="query-orders"]', rows: 'orders', item: (name: string) => ({ ...order, orderNo: name }) },
  { name: 'AI logs', component: AiRecommendationLogsView, input: '[data-test="log-keyword-filter"]', search: '.ai-log-filters', rows: 'logs', item: (name: string) => ({ ...log, wishSummary: name }) },
  { name: 'beads', component: BeadCatalogPanel, input: '[aria-label="搜索商品"]', search: '.toolbar .secondary-button', rows: 'beads', item: (name: string) => ({ ...bead, name }) },
  { name: 'packages', component: PackageCatalogPanel, input: '[aria-label="搜索套餐"]', search: '.toolbar .secondary-button', rows: 'packages', item: (name: string) => ({ ...productPackage, name }) },
])('$name list requests', (scenario) => {
  const response = (name: string, total = 41) => ({
    items: [scenario.item(name)], total, page: 1, size: 20,
  })
  const search = async (wrapper: ReturnType<typeof mount>, keyword: string) => {
    await wrapper.get(scenario.input).setValue(keyword)
    await wrapper.get(scenario.search).trigger(scenario.name === 'AI logs' ? 'submit' : 'click')
  }

  beforeEach(() => {
    vi.resetAllMocks()
    fetchPage.mockReset().mockResolvedValue(response('初始列表'))
    vi.mocked(listAdminOrders).mockImplementation(() => fetchPage() as Promise<PageResponse<AdminOrder>>)
    vi.mocked(getOrderStatistics).mockResolvedValue(counts)
    vi.mocked(listAdminBeads).mockImplementation(() => fetchPage() as Promise<PageResponse<BeadSku>>)
    vi.mocked(listAdminCategories).mockResolvedValue([])
    vi.mocked(listAdminPackages).mockImplementation(() => fetchPage() as Promise<PageResponse<ProductPackage>>)
    vi.mocked(listAiRecommendationLogs).mockImplementation(() => fetchPage() as Promise<PageResponse<AiRecommendationLogSummary>>)
  })

  it('keeps the latest search results when an older search completes later', async () => {
    const wrapper = mount(scenario.component)
    await flushPromises()
    const older = deferred<PageResponse<unknown>>()
    fetchPage.mockReturnValueOnce(older.promise).mockResolvedValueOnce(response('最新结果'))

    await search(wrapper, '旧查询')
    await search(wrapper, '新查询')
    await flushPromises()
    expect(wrapper.text()).toContain('最新结果')

    older.resolve(response('过期结果'))
    await flushPromises()
    expect(wrapper.text()).toContain('最新结果')
    expect(wrapper.text()).not.toContain('过期结果')
  })

  it('keeps the current loading state and suppresses errors from an older search', async () => {
    const wrapper = mount(scenario.component)
    await flushPromises()
    const older = deferred<PageResponse<unknown>>()
    const latest = deferred<PageResponse<unknown>>()
    fetchPage.mockReturnValueOnce(older.promise).mockReturnValueOnce(latest.promise)

    await search(wrapper, '旧查询')
    await search(wrapper, '新查询')
    older.reject(new ApiError('过期错误', 'COMMON_INTERNAL_ERROR', 500))
    await flushPromises()
    expect(wrapper.text()).toContain('正在读取')
    expect(wrapper.find('[role="alert"]').exists()).toBe(false)
    expect(wrapper.find('[data-test="order-toast"]').exists()).toBe(false)

    latest.resolve(response('最新结果'))
    await flushPromises()
    expect(wrapper.text()).toContain('最新结果')
  })

  it('fetches the last valid page when the requested page becomes empty', async () => {
    const wrapper = mount(scenario.component)
    await flushPromises()
    fetchPage.mockResolvedValueOnce({ items: [], total: 1, page: 2, size: 20 })
      .mockResolvedValueOnce(response('有效页结果', 1))

    await wrapper.findAll('.page-number').find((button) => button.text() === '2')!.trigger('click')
    await flushPromises()

    expect(wrapper.get('.page-number.active').text()).toBe('1')
    expect(wrapper.text()).toContain('有效页结果')
    expect(fetchPage).toHaveBeenCalledTimes(3)
  })

  it('does not publish data from a request that finishes after unmount', async () => {
    const wrapper = mount(scenario.component)
    await flushPromises()
    const pending = deferred<PageResponse<unknown>>()
    fetchPage.mockReturnValueOnce(pending.promise)
    await search(wrapper, '离开前查询')
    const state = wrapper.vm as unknown as Record<string, unknown>
    const previousRows = state[scenario.rows]

    wrapper.unmount()
    pending.resolve(response('卸载后结果'))
    await flushPromises()
    await nextTick()

    expect(state[scenario.rows]).toBe(previousRows)
  })
})
