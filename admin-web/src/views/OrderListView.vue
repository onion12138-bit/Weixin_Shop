<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ApiError } from '../api/http'
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
const errorMessage = ref('')
const successMessage = ref('')
const expandedFilters = ref(false)
const showCustomerPhones = ref(false)
const page = ref(1)
const size = ref(10)
const total = ref(0)
const jumpPage = ref('')
const shippingOrder = ref<AdminOrder | null>(null)
const shippingDraft = reactive({ logisticsCompany: '顺丰速运', trackingNo: '' })

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

const totalPages = computed(() => Math.max(1, Math.ceil(total.value / size.value)))
const pageNumbers = computed(() => {
  const start = Math.max(1, Math.min(page.value - 2, totalPages.value - 4))
  const end = Math.min(totalPages.value, start + 4)
  return Array.from({ length: end - start + 1 }, (_, index) => start + index)
})

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

async function loadOrders(resetPage = false): Promise<void> {
  if (resetPage) page.value = 1
  loading.value = true
  errorMessage.value = ''
  try {
    const [result, counts] = await Promise.all([
      listAdminOrders(query()),
      getOrderStatistics(query()),
    ])
    orders.value = result.items
    total.value = result.total
    statistics.value = counts
    if (page.value > totalPages.value) page.value = totalPages.value
  } catch (error) {
    errorMessage.value = readableError(error)
  } finally {
    loading.value = false
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
): Promise<void> {
  workingOrderId.value = order.id
  errorMessage.value = ''
  successMessage.value = ''
  try {
    await action()
    successMessage.value = message
    await loadOrders()
  } catch (error) {
    errorMessage.value = readableError(error)
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
    errorMessage.value = '请填写物流公司和物流单号'
    return
  }
  await runAction(
    order,
    () => shipAdminOrder(
      order.id,
      shippingDraft.logisticsCompany.trim(),
      shippingDraft.trackingNo.trim(),
    ),
    `订单 ${order.orderNo} 已发货`,
  )
  shippingOrder.value = null
}

async function goToPage(target: number): Promise<void> {
  const safe = Math.min(Math.max(target, 1), totalPages.value)
  if (safe === page.value) return
  page.value = safe
  await loadOrders()
}

function submitJump(): void {
  const target = Number(jumpPage.value)
  if (Number.isInteger(target)) void goToPage(target)
  jumpPage.value = ''
}

async function exportOrders(): Promise<void> {
  errorMessage.value = ''
  try {
    const first = await listAdminOrders({ ...query(), page: 1, size: 100 })
    const pages = Math.ceil(first.total / 100)
    const rest = await Promise.all(
      Array.from({ length: Math.max(0, pages - 1) }, (_, index) =>
        listAdminOrders({ ...query(), page: index + 2, size: 100 }),
      ),
    )
    const all = [first, ...rest].flatMap((result) => result.items)
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
    successMessage.value = `已导出 ${all.length} 条订单`
  } catch (error) {
    errorMessage.value = readableError(error)
  }
}

function csvCell(value: unknown): string {
  return `"${String(value ?? '').replaceAll('"', '""')}"`
}

onMounted(() => loadOrders())
</script>

<template>
  <section class="orders-list-page">
    <header class="content-header orders-titlebar">
      <div>
        <h1>订单列表</h1>
        <p class="muted">查看与管理所有订单，跟踪制作、发货及售后状态。</p>
      </div>
      <div class="content-header-actions">
        <button class="secondary-button" :disabled="loading" @click="exportOrders">导出订单</button>
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

    <div v-if="errorMessage" class="notice error-notice" role="alert">{{ errorMessage }}</div>
    <div v-if="successMessage" class="notice success-notice">{{ successMessage }}</div>

    <section class="order-statistics" aria-label="订单状态统计">
      <button
        v-for="card in statisticCards"
        :key="card.label"
        :class="['statistic-card', { active: filters.status === card.status }]"
        @click="chooseStatus(card.status)"
      >
        <span class="statistic-copy"><span>{{ card.label }}</span><strong>{{ card.value }}</strong></span>
        <span :class="['statistic-icon', `tone-${card.tone}`]" aria-hidden="true">
          <i :style="{ maskImage: `url(${card.icon})`, WebkitMaskImage: `url(${card.icon})` }" />
        </span>
      </button>
    </section>

    <section class="order-table-card">
      <div class="table-scroll">
        <table class="order-table">
          <colgroup>
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
          <thead><tr><th>订单编号</th><th>下单时间</th><th><span class="customer-heading">顾客信息<button class="phone-visibility" data-test="toggle-order-phones" :aria-label="showCustomerPhones ? '隐藏全部手机号' : '显示全部手机号'" :title="showCustomerPhones ? '隐藏全部手机号' : '显示全部手机号'" @click="showCustomerPhones = !showCustomerPhones"><i :style="{ maskImage: `url(${showCustomerPhones ? eyeOffIcon : eyeIcon})`, WebkitMaskImage: `url(${showCustomerPhones ? eyeOffIcon : eyeIcon})` }" aria-hidden="true" /></button></span></th><th>商品 / 方案</th><th>珠子明细摘要</th><th>实付金额</th><th>状态</th><th>支付方式</th><th>操作</th></tr></thead>
          <tbody>
            <tr v-if="loading"><td colspan="9" class="table-state">正在读取订单…</td></tr>
            <tr v-else-if="!orders.length"><td colspan="9" class="table-state">当前筛选条件下暂无订单</td></tr>
            <tr v-for="order in orders" v-else :key="order.id">
              <td><button class="order-link" data-test="open-order" @click="emit('open', order.id)">{{ order.orderNo }}</button></td>
              <td class="nowrap">{{ formatDate(order.createdAt) }}</td>
              <td><OverflowText class="cell-primary" :text="order.contactName" /><span class="cell-secondary">{{ showCustomerPhones ? order.contactPhone : maskPhone(order.contactPhone) }}</span></td>
              <td><OverflowText class="cell-primary" :text="order.product.name" :lines="2" /><OverflowText class="cell-secondary" :text="`方案 ${order.product.schemeNo}`" /></td>
              <td><OverflowText class="bead-summary" :text="beadSummary(order)" :lines="2" /></td>
              <td class="order-amount">{{ formatMoney(order.totalAmount) }}</td>
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
                  <button v-if="order.status === 'SHIPPED'" class="text-button" @click="emit('open', order.id)">查看物流</button>
                  <button v-if="order.status === 'AFTER_SALE'" class="text-button" @click="emit('open', order.id)">售后处理</button>
                </div>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
      <footer class="order-pagination">
        <span>共 {{ total }} 条</span>
        <label>每页<select v-model.number="size" @change="loadOrders(true)"><option :value="10">10 条</option><option :value="20">20 条</option><option :value="50">50 条</option></select></label>
        <button class="page-button" :disabled="page <= 1" @click="goToPage(page - 1)">上一页</button>
        <button v-for="number in pageNumbers" :key="number" :class="['page-number', { active: number === page }]" @click="goToPage(number)">{{ number }}</button>
        <button class="page-button" :disabled="page >= totalPages" @click="goToPage(page + 1)">下一页</button>
        <label class="jump-page">跳至<input v-model="jumpPage" type="number" min="1" :max="totalPages" @keyup.enter="submitJump" />页</label>
      </footer>
    </section>

    <div v-if="shippingOrder" class="order-modal-backdrop" @click.self="shippingOrder = null">
      <section class="order-modal" role="dialog" aria-modal="true" aria-label="填写物流信息">
        <header><div><h2>订单发货</h2><p>{{ shippingOrder.orderNo }}</p></div><button class="text-button" @click="shippingOrder = null">关闭</button></header>
        <label><span>物流公司</span><input v-model="shippingDraft.logisticsCompany" /></label>
        <label><span>物流单号</span><input v-model="shippingDraft.trackingNo" placeholder="请输入物流单号" /></label>
        <footer><button class="secondary-button" @click="shippingOrder = null">取消</button><button class="primary-button" :disabled="workingOrderId === shippingOrder.id" @click="submitShipping">确认发货</button></footer>
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
.order-table { width: 100%; min-width: 1080px; table-layout: fixed; }.order-table .col-order-no { width: 16%; }.order-table .col-created-at { width: 10%; }.order-table .col-customer { width: 9%; }.order-table .col-product { width: 17%; }.order-table .col-beads { width: 16%; }.order-table .col-amount { width: 7%; }.order-table .col-status { width: 7%; }.order-table .col-payment { width: 7%; }.order-table .col-actions { width: 11%; }.order-table th,.order-table td { padding: 13px 10px; }.order-table th { white-space: nowrap; }.order-table td { color: #51494d; font-size: 12px; vertical-align: top; }
.order-link { max-width: 100%; padding: 0; border: 0; color: var(--berry); font-weight: 800; line-height: 1.45; overflow-wrap: anywhere; text-align: left; background: transparent; }.nowrap { white-space: nowrap; }.cell-primary,.cell-secondary { display: block; }.cell-primary { font-weight: 700; }.cell-secondary { margin-top: 4px; color: var(--muted); font-size: 11px; }
.bead-summary { display: block; max-width: 270px; }.order-amount { color: var(--berry) !important; font-size: 14px !important; font-weight: 800; white-space: nowrap; }
.customer-heading { display: inline-flex; gap: 5px; align-items: center; }.phone-visibility { display: grid; width: 24px; height: 24px; padding: 0; border: 0; border-radius: 6px; place-items: center; color: #8c6674; background: transparent; }.phone-visibility:hover { color: var(--berry); background: #f4e9ed; }.phone-visibility i { width: 17px; height: 17px; background: currentColor; mask-repeat: no-repeat; mask-position: center; mask-size: contain; }
.order-status { display: inline-flex; padding: 5px 9px; border-radius: 999px; color: #765d68; font-size: 11px; font-weight: 700; background: #f2e9ec; white-space: nowrap; }.is-in_production,.is-pending_shipment { color: #956126; background: #fbf1df; }.is-shipped { color: #37658a; background: #eaf2f8; }.is-completed { color: var(--green); background: var(--green-soft); }.is-cancelled,.is-refunded { color: #776e72; background: #eeeae8; }.is-after_sale { color: #9b4d57; background: #fae8ea; }
.row-actions { display: flex; flex-wrap: wrap; gap: 4px 8px; }.row-actions .text-button { padding: 2px 0; white-space: nowrap; }.danger-text { color: #a2464e; }
.table-state { height: 180px; color: var(--muted); text-align: center; }.order-pagination { display: flex; gap: 8px; align-items: center; justify-content: flex-end; padding: 13px 16px; color: var(--muted); font-size: 12px; }.order-pagination > span { margin-right: auto; }.order-pagination label { display: flex; gap: 6px; align-items: center; font-size: 12px; font-weight: 500; }.order-pagination select { width: 82px; min-height: 34px; }.page-number { min-width: 34px; min-height: 34px; border: 1px solid var(--line); border-radius: 8px; color: var(--muted); background: #fff; }.page-number.active { border-color: var(--berry); color: #fff; background: var(--berry); }.jump-page input { width: 56px; min-height: 34px; }
.order-modal-backdrop { position: fixed; inset: 0; z-index: 20; display: grid; padding: 24px; place-items: center; background: rgb(35 26 30 / 42%); }.order-modal { display: grid; width: min(460px, 100%); gap: 18px; padding: 24px; border-radius: 18px; background: #fffdfb; box-shadow: 0 28px 80px rgb(35 26 30 / 25%); }.order-modal header,.order-modal footer { display: flex; align-items: center; justify-content: space-between; }.order-modal h2,.order-modal p { margin: 0; }.order-modal p { margin-top: 5px; color: var(--muted); font-size: 12px; }.order-modal footer { justify-content: flex-end; gap: 10px; }
@media (max-width: 1320px) { .order-statistics { grid-template-columns: repeat(4, 1fr); }.advanced-filters { grid-template-columns: repeat(3, 1fr); } }
</style>
