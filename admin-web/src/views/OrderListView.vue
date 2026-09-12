<script setup lang="ts">
import DataTooltip from '../components/DataTooltip.vue'
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { ApiError } from '../api/http'
import ListPagination from '../components/ListPagination.vue'
import OverflowText from '../components/OverflowText.vue'
import allOrdersIcon from '../assets/icons/file-list-3-line.svg'
import pendingPaymentIcon from '../assets/icons/bank-card-line.svg'
import productionIcon from '../assets/icons/hammer-line.svg'
import shippingIcon from '../assets/icons/truck-line.svg'
import completedIcon from '../assets/icons/checkbox-circle-line.svg'
import cancelledIcon from '../assets/icons/close-circle-line.svg'
import afterSaleIcon from '../assets/icons/customer-service-2-line.svg'
import eyeIcon from '../assets/icons/eye-line.svg'
import eyeOffIcon from '../assets/icons/eye-off-line.svg'
import {
  cancelAdminOrder,
  completeOrderProduction,
  getOrderStatistics,
  listAdminOrders,
  remindAdminOrderPayment,
  shipAdminOrder,
  type AdminOrder,
  type AdminOrderQuery,
  type OrderStatistics,
  type OrderStatus,
} from '../api/orders'
import {
  beadSummary,
  formatDate,
  formatMoney,
  maskPhone,
  paymentLabel,
  statusLabels,
} from '../orders/presenter'

const emit = defineEmits<{
  open: [id: number]
}>()

const orders = ref<AdminOrder[]>([])
const statistics = ref<OrderStatistics>(emptyStatistics())
const loading = ref(false)
const workingOrderId = ref<number | null>(null)
const batchWorking = ref(false)
const toastMessage = ref('')
const toastTone = ref<'success' | 'error'>('success')
const expandedFilters = ref(false)
const showCustomerPhones = ref(false)
const page = ref(1)
const size = ref(10)
const total = ref(0)
const shippingOrder = ref<AdminOrder | null>(null)
const logisticsOrder = ref<AdminOrder | null>(null)
const shippingDraft = reactive({ logisticsCompany: '顺丰速运', trackingNo: '' })
const selectedOrders = ref<AdminOrder[]>([])
const batchShippingOrders = ref<AdminOrder[]>([])
const batchShippingDrafts = ref<Record<number, { logisticsCompany: string; trackingNo: string }>>({})
let toastTimer: ReturnType<typeof setTimeout> | null = null
let latestOrderRequest = 0
let disposed = false

const filters = reactive({
  orderNo: '',
  contactName: '',
  contactPhone: '',
  status: '' as OrderStatus | '',
  startDate: '',
  endDate: '',
  minAmount: '',
  maxAmount: '',
  paymentMethod: '',
  sortDirection: 'desc' as 'asc' | 'desc',
})

const selectedOrderIds = computed(() => new Set(selectedOrders.value.map((order) => order.id)))
const allPageSelected = computed(() => orders.value.length > 0
  && orders.value.every((order) => selectedOrderIds.value.has(order.id)))
const selectedProductionOrders = computed(() => selectedOrders.value
  .filter((order) => order.status === 'IN_PRODUCTION'))
const selectedShipmentOrders = computed(() => selectedOrders.value
  .filter((order) => order.status === 'PENDING_SHIPMENT'))

const statisticCards = computed(() => [
  { label: '全部订单', value: statistics.value.total, status: '' as OrderStatus | '', tone: 'berry', icon: allOrdersIcon },
  { label: '待付款', value: statistics.value.pendingPayment, status: 'PENDING_PAYMENT' as OrderStatus, tone: 'amber', icon: pendingPaymentIcon },
  { label: '制作中', value: statistics.value.inProduction, status: 'IN_PRODUCTION' as OrderStatus, tone: 'blue', icon: productionIcon },
  { label: '待发货', value: statistics.value.pendingShipment, status: 'PENDING_SHIPMENT' as OrderStatus, tone: 'orange', icon: shippingIcon },
  { label: '已发货', value: statistics.value.shipped, status: 'SHIPPED' as OrderStatus, tone: 'green', icon: shippingIcon },
  { label: '已完成', value: statistics.value.completed, status: 'COMPLETED' as OrderStatus, tone: 'success', icon: completedIcon },
  { label: '已取消', value: statistics.value.cancelled, status: 'CANCELLED' as OrderStatus, tone: 'gray', icon: cancelledIcon },
  { label: '售后中', value: statistics.value.afterSale, status: 'AFTER_SALE' as OrderStatus, tone: 'purple', icon: afterSaleIcon },
])

function emptyStatistics(): OrderStatistics {
  return {
    total: 0, pendingPayment: 0, inProduction: 0, pendingShipment: 0,
    shipped: 0, completed: 0, cancelled: 0, afterSale: 0, refunded: 0,
  }
}

function query(): AdminOrderQuery {
  return { ...filters, page: page.value, size: size.value }
}

function readableError(error: unknown): string {
  if (error instanceof ApiError) {
    return error.traceId ? `${error.message}（追踪号：${error.traceId}）` : error.message
  }
  return error instanceof Error ? error.message : '订单操作失败'
}

function showToast(message: string, tone: 'success' | 'error' = 'success'): void {
  toastMessage.value = message
  toastTone.value = tone
  if (toastTimer !== null) clearTimeout(toastTimer)
  toastTimer = setTimeout(() => {
    toastMessage.value = ''
    toastTimer = null
  }, 5000)
}

async function loadOrders(resetPage = false): Promise<void> {
  if (disposed) return
  const requestId = ++latestOrderRequest
  if (resetPage) page.value = 1
  loading.value = true
  try {
    const [result, counts] = await Promise.all([
      listAdminOrders(query()),
      getOrderStatistics(query()),
    ])
    if (requestId !== latestOrderRequest) return
    const lastPage = Math.max(1, Math.ceil(result.total / size.value))
    if (page.value > lastPage) {
      page.value = lastPage
      await loadOrders()
      return
    }
    orders.value = result.items
    total.value = result.total
    statistics.value = counts
    selectedOrders.value = selectedOrders.value.map((selected) =>
      result.items.find((order) => order.id === selected.id) || selected)
  } catch (error) {
    if (requestId !== latestOrderRequest) return
    showToast(readableError(error), 'error')
  } finally {
    if (requestId === latestOrderRequest) loading.value = false
  }
}

function resetFilters(): void {
  Object.assign(filters, {
    orderNo: '', contactName: '', contactPhone: '', status: '',
    startDate: '', endDate: '', minAmount: '', maxAmount: '',
    paymentMethod: '', sortDirection: 'desc',
  })
  void loadOrders(true)
}

function chooseStatus(status: OrderStatus | ''): void {
  filters.status = status
  void loadOrders(true)
}

async function runAction(
  order: AdminOrder,
  action: () => Promise<AdminOrder>,
  message: string,
): Promise<boolean> {
  workingOrderId.value = order.id
  try {
    await action()
    showToast(message)
    await loadOrders()
    return true
  } catch (error) {
    showToast(readableError(error), 'error')
    return false
  } finally {
    workingOrderId.value = null
  }
}

function remindPayment(order: AdminOrder): void {
  void runAction(
    order, () => remindAdminOrderPayment(order.id),
    `订单 ${order.orderNo} 的付款提醒已记录`,
  )
}

function cancelOrder(order: AdminOrder): void {
  if (!window.confirm(`确认取消订单 ${order.orderNo}？`)) return
  void runAction(
    order, () => cancelAdminOrder(order.id),
    `订单 ${order.orderNo} 已取消`,
  )
}

function completeProduction(order: AdminOrder): void {
  if (!window.confirm(`确认订单 ${order.orderNo} 已完成制作？`)) return
  void runAction(
    order, () => completeOrderProduction(order.id),
    `订单 ${order.orderNo} 已进入待发货`,
  )
}

function openShipping(order: AdminOrder): void {
  shippingOrder.value = order
  shippingDraft.logisticsCompany = '顺丰速运'
  shippingDraft.trackingNo = ''
}

async function submitShipping(): Promise<void> {
  const order = shippingOrder.value
  if (!order) return
  if (!shippingDraft.logisticsCompany.trim() || !shippingDraft.trackingNo.trim()) {
    showToast('请填写物流公司和物流单号', 'error')
    return
  }
  const shipped = await runAction(
    order,
    () => shipAdminOrder(
      order.id,
      shippingDraft.logisticsCompany.trim(),
      shippingDraft.trackingNo.trim(),
    ),
    `订单 ${order.orderNo} 已发货`,
  )
  if (shipped) shippingOrder.value = null
}

function toggleOrderSelection(order: AdminOrder): void {
  selectedOrders.value = selectedOrderIds.value.has(order.id)
    ? selectedOrders.value.filter((item) => item.id !== order.id)
    : [...selectedOrders.value, order]
}

function toggleCurrentPage(): void {
  if (allPageSelected.value) {
    const pageIds = new Set(orders.value.map((order) => order.id))
    selectedOrders.value = selectedOrders.value.filter((order) => !pageIds.has(order.id))
    return
  }
  const existing = new Map(selectedOrders.value.map((order) => [order.id, order]))
  orders.value.forEach((order) => existing.set(order.id, order))
  selectedOrders.value = [...existing.values()]
}

function clearSelection(): void {
  selectedOrders.value = []
}

async function completeSelectedProduction(): Promise<void> {
  const targets = selectedProductionOrders.value
  if (!targets.length) {
    showToast('所选订单中没有“制作中”的订单', 'error')
    return
  }
  if (!window.confirm(`确认将所选 ${targets.length} 个订单标记为完成制作？`)) return
  batchWorking.value = true
  const results = await Promise.allSettled(
    targets.map((order) => completeOrderProduction(order.id)),
  )
  batchWorking.value = false
  const succeededIds = new Set(targets
    .filter((_, index) => results[index].status === 'fulfilled')
    .map((order) => order.id))
  selectedOrders.value = selectedOrders.value.filter((order) => !succeededIds.has(order.id))
  const failed = results.length - succeededIds.size
  showToast(
    failed
      ? `批量完成制作：成功 ${succeededIds.size} 单，失败 ${failed} 单。失败订单仍保留勾选，请检查订单状态后重试。`
      : `已将 ${succeededIds.size} 个订单标记为完成制作`,
    failed ? 'error' : 'success',
  )
  await loadOrders()
}

function openBatchShipping(): void {
  const targets = selectedShipmentOrders.value
  if (!targets.length) {
    showToast('所选订单中没有“待发货”的订单', 'error')
    return
  }
  batchShippingOrders.value = [...targets]
  batchShippingDrafts.value = Object.fromEntries(targets.map((order) => [
    order.id,
    { logisticsCompany: '顺丰速运', trackingNo: '' },
  ]))
}

function closeBatchShipping(): void {
  if (batchWorking.value) return
  batchShippingOrders.value = []
  batchShippingDrafts.value = {}
}

async function submitBatchShipping(): Promise<void> {
  const incomplete = batchShippingOrders.value.filter((order) => {
    const draft = batchShippingDrafts.value[order.id]
    return !draft?.logisticsCompany.trim() || !draft?.trackingNo.trim()
  })
  if (incomplete.length) {
    showToast(
      `请补充以下订单的物流公司和快递单号：${incomplete.map((order) => order.orderNo).join('、')}`,
      'error',
    )
    return
  }
  batchWorking.value = true
  const targets = [...batchShippingOrders.value]
  const results = await Promise.allSettled(targets.map((order) => {
    const draft = batchShippingDrafts.value[order.id]
    return shipAdminOrder(order.id, draft.logisticsCompany.trim(), draft.trackingNo.trim())
  }))
  batchWorking.value = false
  const succeededIds = new Set(targets
    .filter((_, index) => results[index].status === 'fulfilled')
    .map((order) => order.id))
  selectedOrders.value = selectedOrders.value.filter((order) => !succeededIds.has(order.id))
  const failedOrders = targets.filter((_, index) => results[index].status === 'rejected')
  if (!failedOrders.length) closeBatchShipping()
  else batchShippingOrders.value = failedOrders
  showToast(
    failedOrders.length
      ? `批量发货：成功 ${succeededIds.size} 单，失败 ${failedOrders.length} 单。失败订单已保留在弹窗中。`
      : `已完成 ${succeededIds.size} 个订单的发货`,
    failedOrders.length ? 'error' : 'success',
  )
  await loadOrders()
}

function updatePage(target: number): void {
  if (target === page.value) return
  page.value = target
  void loadOrders()
}

function updatePageSize(nextSize: number): void {
  size.value = nextSize
  void loadOrders(true)
}

async function exportOrders(): Promise<void> {
  if (!selectedOrders.value.length) {
    showToast('请先勾选需要导出的订单', 'error')
    return
  }
  try {
    const all = selectedOrders.value
    const cells = [
      ['订单编号', '下单时间', '收件人', '手机号', '商品/方案', '珠子摘要', '实付金额', '状态', '支付方式'],
      ...all.map((order) => [
        order.orderNo, formatDate(order.createdAt), order.contactName,
        order.contactPhone, `${order.product.name} ${order.product.schemeNo}`,
        beadSummary(order), formatMoney(order.totalAmount),
        statusLabels[order.status], paymentLabel(order),
      ]),
    ]
    const csv = `\uFEFF${cells.map((row) => row.map(csvCell).join(',')).join('\n')}`
    const url = URL.createObjectURL(new Blob([csv], { type: 'text/csv;charset=utf-8' }))
    const anchor = document.createElement('a')
    anchor.href = url
    anchor.download = `拾珠作坊订单-${new Date().toISOString().slice(0, 10)}.csv`
    anchor.click()
    URL.revokeObjectURL(url)
    showToast(`已导出 ${all.length} 条订单`)
  } catch (error) {
    showToast(readableError(error), 'error')
  }
}

function csvCell(value: unknown): string {
  return `"${String(value ?? '').replaceAll('"', '""')}"`
}

onMounted(() => loadOrders())
onBeforeUnmount(() => {
  disposed = true
  latestOrderRequest++
  if (toastTimer !== null) clearTimeout(toastTimer)
})
</script>

<template>
  <section class="orders-list-page">
    <header class="content-header orders-titlebar">
      <div>
        <h1>订单列表</h1>
        <p class="muted">查看与管理所有订单，跟踪制作、发货及售后状态。</p>
      </div>
      <div class="content-header-actions">
        <button class="secondary-button" data-test="export-selected-orders" :disabled="loading || !selectedOrders.length" @click="exportOrders">导出所选{{ selectedOrders.length ? `（${selectedOrders.length}）` : '' }}</button>
        <button class="secondary-button" :disabled="loading" @click="loadOrders()">刷新</button>
      </div>
    </header>

    <section class="order-filter-card" aria-label="订单查询条件">
      <div class="filter-grid primary-filters">
        <label><span>订单编号</span><input v-model="filters.orderNo" placeholder="精确或模糊查询" @keyup.enter="loadOrders(true)" /></label>
        <label><span>收件人</span><input v-model="filters.contactName" placeholder="客户姓名" @keyup.enter="loadOrders(true)" /></label>
        <label><span>手机号</span><input v-model="filters.contactPhone" placeholder="收货手机号" @keyup.enter="loadOrders(true)" /></label>
        <label><span>订单状态</span><select v-model="filters.status"><option value="">全部状态</option><option v-for="(label, status) in statusLabels" :key="status" :value="status">{{ label }}</option></select></label>
      </div>
      <div v-if="expandedFilters" class="filter-grid advanced-filters">
        <label><span>开始日期</span><input v-model="filters.startDate" type="date" /></label>
        <label><span>结束日期</span><input v-model="filters.endDate" type="date" /></label>
        <label><span>最低金额</span><input v-model="filters.minAmount" type="number" min="0" placeholder="0.00" /></label>
        <label><span>最高金额</span><input v-model="filters.maxAmount" type="number" min="0" placeholder="不限" /></label>
        <label><span>支付方式</span><select v-model="filters.paymentMethod"><option value="">全部方式</option><option value="WECHAT">微信支付</option></select></label>
        <label><span>下单时间排序</span><select v-model="filters.sortDirection"><option value="desc">降序（最新优先）</option><option value="asc">升序（最早优先）</option></select></label>
      </div>
      <div class="filter-actions">
        <button class="text-button filter-toggle" @click="expandedFilters = !expandedFilters">{{ expandedFilters ? '收起筛选' : '展开筛选' }}</button>
        <button class="secondary-button" @click="resetFilters">重置</button>
        <button class="primary-button" data-test="query-orders" @click="loadOrders(true)">查询</button>
      </div>
    </section>

    <div v-if="toastMessage" :class="['order-toast', `is-${toastTone}`]" data-test="order-toast" role="status">{{ toastMessage }}</div>

    <section class="order-statistics" aria-label="订单状态统计">
      <DataTooltip
        v-for="card in statisticCards"
        :key="card.label"
        as="button"
        :text="`${card.label}：${card.value} 单\n当前查询条件下共 ${statistics.total} 单\n${card.status ? '点击按此状态筛选订单' : `其中退款成功 ${statistics.refunded} 单`}`"
        :class="['statistic-card', { active: filters.status === card.status }]"
        @click="chooseStatus(card.status)"
      >
        <span class="statistic-copy"><span>{{ card.label }}</span><strong>{{ card.value }}</strong></span>
        <span :class="['statistic-icon', `tone-${card.tone}`]" aria-hidden="true">
          <i :style="{ maskImage: `url(${card.icon})`, WebkitMaskImage: `url(${card.icon})` }" />
        </span>
      </DataTooltip>
    </section>

    <section class="order-table-card">
      <div v-if="selectedOrders.length" class="batch-toolbar" data-test="batch-toolbar">
        <strong>已选 {{ selectedOrders.length }} 个订单</strong>
        <span>其中制作中 {{ selectedProductionOrders.length }} 个，待发货 {{ selectedShipmentOrders.length }} 个</span>
        <button class="secondary-button" :disabled="batchWorking || !selectedProductionOrders.length" data-test="batch-complete-production" @click="completeSelectedProduction">完成制作</button>
        <button class="primary-button" :disabled="batchWorking || !selectedShipmentOrders.length" data-test="batch-ship" @click="openBatchShipping">发货</button>
        <button class="text-button" :disabled="batchWorking" @click="clearSelection">取消选择</button>
      </div>
      <div class="table-scroll">
        <table class="order-table">
          <colgroup>
            <col class="col-select" />
            <col class="col-order-no" />
            <col class="col-created-at" />
            <col class="col-customer" />
            <col class="col-product" />
            <col class="col-beads" />
            <col class="col-amount" />
            <col class="col-status" />
            <col class="col-payment" />
            <col class="col-actions" />
          </colgroup>
          <thead><tr><th class="select-cell"><input type="checkbox" data-test="select-page-orders" :checked="allPageSelected" aria-label="选择当前页全部订单" @change="toggleCurrentPage" /></th><th>订单编号</th><th>下单时间</th><th><span class="customer-heading">顾客信息<button class="phone-visibility" data-test="toggle-order-phones" :aria-label="showCustomerPhones ? '隐藏全部手机号' : '显示全部手机号'" :title="showCustomerPhones ? '隐藏全部手机号' : '显示全部手机号'" @click="showCustomerPhones = !showCustomerPhones"><i :style="{ maskImage: `url(${showCustomerPhones ? eyeOffIcon : eyeIcon})`, WebkitMaskImage: `url(${showCustomerPhones ? eyeOffIcon : eyeIcon})` }" aria-hidden="true" /></button></span></th><th>商品 / 方案</th><th>珠子明细摘要</th><th>订单金额</th><th>状态</th><th>支付方式</th><th>操作</th></tr></thead>
          <tbody>
            <tr v-if="loading"><td colspan="10" class="table-state">正在读取订单…</td></tr>
            <tr v-else-if="!orders.length"><td colspan="10" class="table-state">当前筛选条件下暂无订单</td></tr>
            <tr v-for="order in orders" v-else :key="order.id">
              <td class="select-cell"><input type="checkbox" data-test="select-order" :checked="selectedOrderIds.has(order.id)" :aria-label="`选择订单 ${order.orderNo}`" @change="toggleOrderSelection(order)" /></td>
              <td><button class="order-link" data-test="open-order" @click="emit('open', order.id)">{{ order.orderNo }}</button></td>
              <td><OverflowText :text="formatDate(order.createdAt)" :lines="2" /></td>
              <td><OverflowText class="cell-primary" :text="order.contactName" /><span class="cell-secondary">{{ showCustomerPhones ? order.contactPhone : maskPhone(order.contactPhone) }}</span></td>
              <td><OverflowText class="cell-primary" :text="order.product.name" :lines="2" /><OverflowText class="cell-secondary" :text="`方案 ${order.product.schemeNo}`" /></td>
              <td><OverflowText class="bead-summary" :text="beadSummary(order)" :lines="2" /></td>
              <td class="order-amount"><DataTooltip :text="`商品金额：${formatMoney(order.beadAmount)}\n工艺费：${formatMoney(order.craftFee)}\n绳材费：${formatMoney(order.cordFee)}\n运费：${formatMoney(order.shippingFee)}\n优惠抵扣：${formatMoney(order.discountAmount)}\n订单应付：${formatMoney(order.totalAmount)}\n实付金额：${formatMoney(order.payment?.status === 'SUCCESS' ? order.payment.amount : 0)}`">{{ formatMoney(order.totalAmount) }}</DataTooltip></td>
              <td><span :class="['order-status', `is-${order.status.toLowerCase()}`]">{{ statusLabels[order.status] }}</span></td>
              <td>{{ paymentLabel(order) }}</td>
              <td>
                <div class="row-actions">
                  <button class="text-button" @click="emit('open', order.id)">查看详情</button>
                  <button v-if="order.status === 'PENDING_PAYMENT'" class="text-button" :disabled="workingOrderId === order.id" @click="remindPayment(order)">提醒付款</button>
                  <button v-if="order.status === 'PENDING_PAYMENT'" class="text-button danger-text" :disabled="workingOrderId === order.id" @click="cancelOrder(order)">取消</button>
                  <button v-if="order.status === 'IN_PRODUCTION'" class="text-button" @click="emit('open', order.id)">打印 BOM</button>
                  <button v-if="order.status === 'IN_PRODUCTION'" class="text-button" data-test="complete-production" :disabled="workingOrderId === order.id" @click="completeProduction(order)">完成制作</button>
                  <button v-if="order.status === 'PENDING_SHIPMENT'" class="text-button" @click="openShipping(order)">发货</button>
                  <button v-if="order.status === 'SHIPPED'" class="text-button" data-test="view-logistics" @click="logisticsOrder = order">查看物流</button>
                  <button v-if="order.status === 'AFTER_SALE'" class="text-button" @click="emit('open', order.id)">售后处理</button>
                </div>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
      <ListPagination :page="page" :size="size" :total="total" @update:page="updatePage" @update:size="updatePageSize" />
    </section>

    <div v-if="shippingOrder" class="order-modal-backdrop" @click.self="shippingOrder = null">
      <section class="order-modal" role="dialog" aria-modal="true" aria-label="填写物流信息">
        <header><div><h2>订单发货</h2><p>{{ shippingOrder.orderNo }}</p></div><button class="text-button" @click="shippingOrder = null">关闭</button></header>
        <label><span>物流公司</span><input v-model="shippingDraft.logisticsCompany" /></label>
        <label><span>物流单号</span><input v-model="shippingDraft.trackingNo" placeholder="请输入物流单号" /></label>
        <footer><button class="secondary-button" @click="shippingOrder = null">取消</button><button class="primary-button" :disabled="workingOrderId === shippingOrder.id" @click="submitShipping">确认发货</button></footer>
      </section>
    </div>

    <div v-if="logisticsOrder" class="order-modal-backdrop" @click.self="logisticsOrder = null">
      <section class="order-modal" role="dialog" aria-modal="true" aria-label="查看物流信息">
        <header><div><h2>物流信息</h2><p>{{ logisticsOrder.orderNo }}</p></div><button class="text-button" @click="logisticsOrder = null">关闭</button></header>
        <dl class="logistics-summary">
          <div><dt>物流公司</dt><dd>{{ logisticsOrder.fulfillment?.logisticsCompany || '—' }}</dd></div>
          <div><dt>物流单号</dt><dd data-test="logistics-tracking-no">{{ logisticsOrder.fulfillment?.trackingNo || '—' }}</dd></div>
          <div><dt>发货时间</dt><dd>{{ formatDate(logisticsOrder.shippedAt) }}</dd></div>
        </dl>
        <footer><button class="primary-button" @click="logisticsOrder = null">知道了</button></footer>
      </section>
    </div>

    <div v-if="batchShippingOrders.length" class="order-modal-backdrop" @click.self="closeBatchShipping">
      <section class="order-modal batch-shipping-modal" role="dialog" aria-modal="true" aria-label="批量填写物流信息">
        <header><div><h2>批量发货</h2><p>请为每个订单分别填写快递单号</p></div><button class="text-button" :disabled="batchWorking" @click="closeBatchShipping">关闭</button></header>
        <div class="batch-shipping-list">
          <article v-for="order in batchShippingOrders" :key="order.id" class="batch-shipping-row">
            <div><strong>{{ order.orderNo }}</strong><small>{{ order.contactName }} · {{ order.contactPhone }}</small></div>
            <label><span>物流公司</span><input v-model="batchShippingDrafts[order.id].logisticsCompany" /></label>
            <label><span>快递单号</span><input v-model="batchShippingDrafts[order.id].trackingNo" :data-test="`batch-tracking-${order.id}`" placeholder="请输入快递单号" /></label>
          </article>
        </div>
        <footer><button class="secondary-button" :disabled="batchWorking" @click="closeBatchShipping">取消</button><button class="primary-button" data-test="submit-batch-shipping" :disabled="batchWorking" @click="submitBatchShipping">确认发货（{{ batchShippingOrders.length }} 单）</button></footer>
      </section>
    </div>
  </section>
</template>

<style scoped>
.orders-list-page { padding-bottom: 30px; }
.orders-titlebar h1 { font-family: "Songti SC", "STSong", serif; }
.order-filter-card { margin-bottom: 16px; padding: 16px 18px; border: 1px solid var(--line); border-radius: 14px; background: rgb(255 253 251 / 90%); box-shadow: 0 10px 28px rgb(58 43 49 / 4%); }
.filter-grid { display: grid; grid-template-columns: repeat(4, minmax(150px, 1fr)); gap: 12px; }
.advanced-filters { grid-template-columns: repeat(6, minmax(130px, 1fr)); margin-top: 12px; padding-top: 12px; border-top: 1px solid #efe7e4; }
.filter-grid label { gap: 6px; font-size: 12px; }.filter-grid input,.filter-grid select { min-height: 38px; }
.filter-actions { display: flex; gap: 9px; justify-content: flex-end; align-items: center; margin-top: 14px; }.filter-toggle { margin-right: auto; }
.order-statistics { display: grid; grid-template-columns: repeat(8, minmax(92px, 1fr)); gap: 9px; margin-bottom: 16px; }
.statistic-card { display: flex; min-height: 76px; padding: 12px 13px; border: 1px solid var(--line); border-radius: 12px; align-items: center; justify-content: space-between; color: var(--muted); text-align: left; background: var(--paper); box-shadow: 0 8px 20px rgb(58 43 49 / 4%); transition: 160ms ease; }
.statistic-card:hover { border-color: #d7c7cd; transform: translateY(-1px); }.statistic-copy { display: flex; gap: 6px; flex-direction: column; }.statistic-card strong { color: var(--ink); font-size: 22px; font-variant-numeric: tabular-nums; }.statistic-card.active { border-color: #a76f83; color: var(--berry); background: #faf0f3; }.statistic-card.active strong { color: var(--berry); }
.statistic-icon { display: grid; width: 34px; height: 34px; flex: 0 0 auto; border-radius: 50%; place-items: center; }.statistic-icon i { width: 20px; height: 20px; background: currentColor; mask-repeat: no-repeat; mask-position: center; mask-size: contain; }.tone-berry { color: #9c4269; background: #f5e2ea; }.tone-amber { color: #c27a18; background: #fff1d7; }.tone-blue { color: #357fd1; background: #e5f0ff; }.tone-orange { color: #dc7134; background: #ffeadf; }.tone-green { color: #2f9b50; background: #e6f6e9; }.tone-success { color: #23a24a; background: #e2f6e7; }.tone-gray { color: #777277; background: #efedef; }.tone-purple { color: #795ac1; background: #eee8fb; }
.order-table-card { overflow: hidden; border: 1px solid var(--line); border-radius: 14px; background: var(--paper); box-shadow: 0 12px 34px rgb(58 43 49 / 5%); }
.batch-toolbar { display: flex; gap: 12px; min-height: 58px; padding: 9px 14px; align-items: center; border-bottom: 1px solid #eadde2; color: #5e4450; background: #fff5f8; }.batch-toolbar strong { color: var(--berry); }.batch-toolbar span { margin-right: auto; color: var(--muted); font-size: 12px; }.batch-toolbar button { min-height: 36px; padding: 0 13px; }
.order-table { width: 100%; min-width: 1130px; table-layout: fixed; }.order-table .col-select { width: 4%; }.order-table .col-order-no { width: 15%; }.order-table .col-created-at { width: 10%; }.order-table .col-customer { width: 9%; }.order-table .col-product { width: 16%; }.order-table .col-beads { width: 15%; }.order-table .col-amount { width: 7%; }.order-table .col-status { width: 7%; }.order-table .col-payment { width: 7%; }.order-table .col-actions { width: 10%; }.order-table th,.order-table td { padding: 13px 10px; }.order-table th { white-space: nowrap; }.order-table td { color: #51494d; font-size: 12px; vertical-align: top; }.select-cell { padding: 0 !important; text-align: center; vertical-align: middle !important; }.select-cell input { display: block; width: 16px; min-height: 16px; height: 16px; margin: auto; padding: 0; border-radius: 3px; accent-color: var(--berry); box-shadow: none !important; }
.order-link { max-width: 100%; padding: 0; border: 0; color: var(--berry); font-weight: 800; line-height: 1.45; overflow-wrap: anywhere; text-align: left; background: transparent; }.nowrap { white-space: nowrap; }.cell-secondary:not(.overflow-text) { display: block; }.cell-primary { font-weight: 700; }.cell-secondary { margin-top: 4px; color: var(--muted); font-size: 11px; }
.bead-summary { max-width: 270px; }.order-amount { color: var(--berry) !important; font-size: 14px !important; font-weight: 800; white-space: nowrap; }
.customer-heading { display: inline-flex; gap: 5px; align-items: center; }.phone-visibility { display: grid; width: 24px; height: 24px; padding: 0; border: 0; border-radius: 6px; place-items: center; color: #8c6674; background: transparent; }.phone-visibility:hover { color: var(--berry); background: #f4e9ed; }.phone-visibility i { width: 17px; height: 17px; background: currentColor; mask-repeat: no-repeat; mask-position: center; mask-size: contain; }
.order-status { display: inline-flex; padding: 5px 9px; border-radius: 999px; color: #765d68; font-size: 11px; font-weight: 700; background: #f2e9ec; white-space: nowrap; }.is-in_production,.is-pending_shipment { color: #956126; background: #fbf1df; }.is-shipped { color: #37658a; background: #eaf2f8; }.is-completed { color: var(--green); background: var(--green-soft); }.is-cancelled,.is-refunded { color: #776e72; background: #eeeae8; }.is-after_sale { color: #9b4d57; background: #fae8ea; }
.row-actions { display: flex; flex-wrap: wrap; gap: 4px 8px; }.row-actions .text-button { padding: 2px 0; white-space: nowrap; }.danger-text { color: #a2464e; }
.table-state { height: 180px; color: var(--muted); text-align: center; }.order-pagination { display: flex; gap: 8px; align-items: center; justify-content: flex-end; padding: 13px 16px; color: var(--muted); font-size: 12px; }.order-pagination > span { margin-right: auto; }.order-pagination label { display: flex; gap: 6px; align-items: center; font-size: 12px; font-weight: 500; }.order-pagination select { width: 82px; min-height: 34px; }.page-number { min-width: 34px; min-height: 34px; border: 1px solid var(--line); border-radius: 8px; color: var(--muted); background: #fff; }.page-number.active { border-color: var(--berry); color: #fff; background: var(--berry); }.jump-page input { width: 56px; min-height: 34px; }
.order-modal-backdrop { position: fixed; inset: 0; z-index: 20; display: grid; padding: 24px; place-items: center; background: rgb(35 26 30 / 42%); }.order-modal { display: grid; width: min(460px, 100%); gap: 18px; padding: 24px; border-radius: 18px; background: #fffdfb; box-shadow: 0 28px 80px rgb(35 26 30 / 25%); }.order-modal header,.order-modal footer { display: flex; align-items: center; justify-content: space-between; }.order-modal h2,.order-modal p { margin: 0; }.order-modal p { margin-top: 5px; color: var(--muted); font-size: 12px; }.order-modal footer { justify-content: flex-end; gap: 10px; }
.logistics-summary { display: grid; gap: 13px; margin: 0; padding: 16px; border: 1px solid #eadde2; border-radius: 12px; background: #fff; }.logistics-summary div { display: grid; grid-template-columns: 90px 1fr; gap: 12px; }.logistics-summary dt,.logistics-summary dd { margin: 0; }.logistics-summary dt { color: var(--muted); }.logistics-summary dd { font-weight: 700; overflow-wrap: anywhere; }
.order-toast { position: fixed; z-index: 80; top: 24px; right: 28px; width: min(420px, calc(100vw - 56px)); padding: 14px 18px; border: 1px solid #d7c2ca; border-radius: 12px; color: #553b45; line-height: 1.55; white-space: normal; overflow-wrap: anywhere; background: #fffdfb; box-shadow: 0 16px 44px rgb(43 27 34 / 18%); }.order-toast.is-success { border-color: #bcd7c8; color: #315f4c; background: #f3fbf6; }.order-toast.is-error { border-color: #e1b7bc; color: #923e48; background: #fff5f6; }
.batch-shipping-modal { width: min(880px, 100%); max-height: calc(100vh - 48px); }.batch-shipping-list { display: grid; gap: 10px; overflow: auto; max-height: 58vh; padding-right: 3px; }.batch-shipping-row { display: grid; grid-template-columns: minmax(180px, 1fr) minmax(170px, .8fr) minmax(220px, 1.2fr); gap: 12px; padding: 13px; align-items: end; border: 1px solid #eadde2; border-radius: 12px; background: #fff; }.batch-shipping-row strong,.batch-shipping-row small { display: block; }.batch-shipping-row small { margin-top: 5px; color: var(--muted); }.batch-shipping-row label { display: grid; gap: 5px; }.batch-shipping-row label span { color: var(--muted); font-size: 11px; }
@media (max-width: 1320px) { .order-statistics { grid-template-columns: repeat(4, 1fr); }.advanced-filters { grid-template-columns: repeat(3, 1fr); } }
</style>
