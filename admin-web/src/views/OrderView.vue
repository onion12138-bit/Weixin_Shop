<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ApiError } from '../api/http'
import {
  completeAdminOrder,
  listAdminOrders,
  shipAdminOrder,
  startOrderProduction,
  type AdminOrder,
  type OrderStatus,
} from '../api/orders'

const orders = ref<AdminOrder[]>([])
const total = ref(0)
const page = ref(1)
const size = 20
const statusFilter = ref<OrderStatus | ''>('')
const loading = ref(false)
const workingOrderId = ref<number | null>(null)
const errorMessage = ref('')
const successMessage = ref('')
const shippingDrafts = reactive<
  Record<number, { logisticsCompany: string; trackingNo: string }>
>({})

const statusLabels: Record<OrderStatus, string> = {
  PENDING_PAYMENT: '待支付',
  PAID: '已支付',
  IN_PRODUCTION: '制作中',
  SHIPPED: '已发货',
  COMPLETED: '已完成',
  CANCELLED: '已取消',
}

function readableError(error: unknown): string {
  if (error instanceof ApiError) {
    return error.traceId
      ? `${error.message}（追踪号：${error.traceId}）`
      : error.message
  }
  if (error instanceof Error) return error.message
  return '订单操作失败，请确认后端服务已启动'
}

function formatMoney(value: number): string {
  return `¥${Number(value).toFixed(2)}`
}

function formatDate(value: string | null): string {
  if (!value) return '—'
  return value.replace('T', ' ').slice(0, 16)
}

function ensureShippingDraft(orderId: number): {
  logisticsCompany: string
  trackingNo: string
} {
  if (!shippingDrafts[orderId]) {
    shippingDrafts[orderId] = {
      logisticsCompany: '顺丰速运',
      trackingNo: '',
    }
  }
  return shippingDrafts[orderId]
}

async function loadOrders(resetPage = false): Promise<void> {
  if (resetPage) page.value = 1
  loading.value = true
  errorMessage.value = ''
  try {
    const response = await listAdminOrders({
      status: statusFilter.value,
      page: page.value,
      size,
    })
    orders.value = response.items
    total.value = response.total
    response.items.forEach((order) => ensureShippingDraft(order.id))
  } catch (error) {
    errorMessage.value = readableError(error)
  } finally {
    loading.value = false
  }
}

function replaceOrder(updated: AdminOrder): void {
  const index = orders.value.findIndex((order) => order.id === updated.id)
  if (index >= 0) orders.value[index] = updated
}

async function runOrderAction(
  orderId: number,
  action: () => Promise<AdminOrder>,
  message: string,
): Promise<void> {
  workingOrderId.value = orderId
  errorMessage.value = ''
  successMessage.value = ''
  try {
    replaceOrder(await action())
    successMessage.value = message
  } catch (error) {
    errorMessage.value = readableError(error)
  } finally {
    workingOrderId.value = null
  }
}

async function startProduction(order: AdminOrder): Promise<void> {
  await runOrderAction(
    order.id,
    () => startOrderProduction(order.id),
    `订单 ${order.orderNo} 已进入制作`,
  )
}

async function ship(order: AdminOrder): Promise<void> {
  const draft = ensureShippingDraft(order.id)
  const company = draft.logisticsCompany.trim()
  const trackingNo = draft.trackingNo.trim()
  if (!company || !trackingNo) {
    errorMessage.value = '请填写物流公司和运单号'
    return
  }
  await runOrderAction(
    order.id,
    () => shipAdminOrder(order.id, company, trackingNo),
    `订单 ${order.orderNo} 已发货`,
  )
}

async function complete(order: AdminOrder): Promise<void> {
  await runOrderAction(
    order.id,
    () => completeAdminOrder(order.id),
    `订单 ${order.orderNo} 已完成`,
  )
}

async function previousPage(): Promise<void> {
  if (page.value <= 1) return
  page.value -= 1
  await loadOrders()
}

async function nextPage(): Promise<void> {
  if (page.value * size >= total.value) return
  page.value += 1
  await loadOrders()
}

onMounted(() => loadOrders())
</script>

<template>
  <section class="order-workspace">
    <header class="content-header order-header">
      <div>
        <p class="section-kicker">COMMERCE / FULFILLMENT</p>
        <h1>订单与履约</h1>
        <p class="muted">
          查看顾客 DIY 订单和制作 BOM，并按顺序完成制作、发货与交付。
        </p>
      </div>
      <button class="secondary-button" :disabled="loading" @click="loadOrders()">
        刷新订单
      </button>
    </header>

    <div class="order-toolbar">
      <label>
        <span>订单状态</span>
        <select v-model="statusFilter" @change="loadOrders(true)">
          <option value="">全部状态</option>
          <option
            v-for="(label, value) in statusLabels"
            :key="value"
            :value="value"
          >
            {{ label }}
          </option>
        </select>
      </label>
      <div class="order-total">
        <strong>{{ total }}</strong>
        <span>条订单</span>
      </div>
    </div>

    <div v-if="errorMessage" class="notice error-notice" role="alert">
      {{ errorMessage }}
    </div>
    <div v-if="successMessage" class="notice success-notice">
      {{ successMessage }}
    </div>
    <div v-if="loading" class="empty-state">正在读取订单…</div>
    <div v-else-if="orders.length === 0" class="empty-state">
      当前筛选条件下暂无订单
    </div>

    <div v-else class="order-list">
      <article v-for="order in orders" :key="order.id" class="order-card">
        <header class="order-card-header">
          <div>
            <p class="order-number">{{ order.orderNo }}</p>
            <span>{{ formatDate(order.createdAt) }} 创建</span>
          </div>
          <div class="order-card-status">
            <strong>{{ formatMoney(order.totalAmount) }}</strong>
            <span :class="['status-pill', `status-${order.status.toLowerCase()}`]">
              {{ statusLabels[order.status] }}
            </span>
          </div>
        </header>

        <div class="order-card-body">
          <section class="receiver-panel">
            <p class="section-kicker">RECEIVER</p>
            <h3>{{ order.contactName }}</h3>
            <p>{{ order.contactPhone }}</p>
            <p>{{ order.addressDetail }}</p>
            <dl>
              <div>
                <dt>方案编号</dt>
                <dd>#{{ order.schemeId }}</dd>
              </div>
              <div>
                <dt>支付时间</dt>
                <dd>{{ formatDate(order.paidAt) }}</dd>
              </div>
            </dl>
          </section>

          <section class="bom-panel">
            <div class="panel-title">
              <div>
                <p class="section-kicker">BOM</p>
                <h3>制作清单</h3>
              </div>
              <span>{{ order.items.reduce((sum, item) => sum + item.quantity, 0) }} 颗</span>
            </div>
            <div class="bom-table" role="table" aria-label="订单制作清单">
              <div v-for="item in order.items" :key="item.skuId" class="bom-row">
                <div>
                  <strong>{{ item.name }}</strong>
                  <small>{{ item.skuCode }}</small>
                </div>
                <span>{{ item.quantity }} 颗</span>
                <span>{{ formatMoney(item.totalPrice) }}</span>
              </div>
            </div>
          </section>

          <section class="action-panel">
            <p class="section-kicker">NEXT ACTION</p>
            <template v-if="order.status === 'PAID'">
              <h3>等待开始制作</h3>
              <p>确认 BOM 后，将订单进入手串制作环节。</p>
              <button
                class="primary-button"
                data-test="start-production"
                :disabled="workingOrderId === order.id"
                @click="startProduction(order)"
              >
                {{ workingOrderId === order.id ? '处理中…' : '开始制作' }}
              </button>
            </template>

            <template v-else-if="order.status === 'IN_PRODUCTION'">
              <h3>制作完成后发货</h3>
              <label>
                <span>物流公司</span>
                <input v-model="ensureShippingDraft(order.id).logisticsCompany" />
              </label>
              <label>
                <span>运单号</span>
                <input
                  v-model="ensureShippingDraft(order.id).trackingNo"
                  placeholder="请输入模拟运单号"
                />
              </label>
              <button
                class="primary-button"
                :disabled="workingOrderId === order.id"
                @click="ship(order)"
              >
                确认发货
              </button>
            </template>

            <template v-else-if="order.status === 'SHIPPED'">
              <h3>等待确认完成</h3>
              <p>
                {{ order.fulfillment?.logisticsCompany }} ·
                {{ order.fulfillment?.trackingNo }}
              </p>
              <button
                class="primary-button"
                :disabled="workingOrderId === order.id"
                @click="complete(order)"
              >
                完成订单
              </button>
            </template>

            <template v-else>
              <h3>{{ statusLabels[order.status] }}</h3>
              <p>当前订单无需后台操作。</p>
            </template>
          </section>
        </div>
      </article>
    </div>

    <footer v-if="total > size" class="order-pagination">
      <button class="page-button" :disabled="page <= 1" @click="previousPage">
        上一页
      </button>
      <span>第 {{ page }} 页</span>
      <button
        class="page-button"
        :disabled="page * size >= total"
        @click="nextPage"
      >
        下一页
      </button>
    </footer>
  </section>
</template>

<style scoped>
.order-workspace {
  padding: 38px 42px 56px;
}

.order-header,
.order-card-header,
.panel-title,
.order-pagination {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.order-header h1 {
  margin: 0 0 8px;
  font-family: "Songti SC", "STSong", serif;
  font-size: 34px;
}

.order-header p:last-child {
  margin: 0;
}

.order-toolbar {
  display: flex;
  gap: 18px;
  align-items: end;
  margin: 30px 0 20px;
  padding: 18px 20px;
  border: 1px solid var(--line);
  border-radius: 16px;
  background: rgb(255 253 251 / 85%);
}

.order-toolbar label {
  width: 220px;
}

.order-total {
  display: flex;
  gap: 8px;
  align-items: baseline;
  margin-left: auto;
  color: var(--muted);
}

.order-total strong {
  color: var(--berry);
  font-size: 30px;
}

.order-list {
  display: grid;
  gap: 18px;
}

.order-card {
  overflow: hidden;
  border: 1px solid var(--line);
  border-radius: 18px;
  background: var(--paper);
  box-shadow: 0 14px 38px rgb(74 52 60 / 7%);
}

.order-card-header {
  padding: 20px 24px;
  border-bottom: 1px solid var(--line);
  background: linear-gradient(110deg, #fffdfb, #faf4f5);
}

.order-number {
  margin: 0 0 5px;
  font-size: 17px;
  font-weight: 800;
  letter-spacing: 0.03em;
}

.order-card-header span {
  color: var(--muted);
  font-size: 12px;
}

.order-card-status {
  display: flex;
  gap: 14px;
  align-items: center;
}

.order-card-status strong {
  font-family: Georgia, serif;
  font-size: 24px;
}

.status-pill {
  padding: 7px 11px;
  border-radius: 999px;
  color: var(--berry-dark) !important;
  font-weight: 800;
  background: var(--berry-soft);
}

.status-in_production,
.status-shipped {
  color: var(--amber) !important;
  background: var(--amber-soft);
}

.status-completed {
  color: var(--green) !important;
  background: var(--green-soft);
}

.order-card-body {
  display: grid;
  grid-template-columns: minmax(210px, 0.8fr) minmax(360px, 1.45fr) minmax(250px, 0.9fr);
}

.order-card-body > section {
  padding: 24px;
}

.order-card-body > section + section {
  border-left: 1px solid var(--line);
}

.receiver-panel h3,
.bom-panel h3,
.action-panel h3 {
  margin: 0 0 10px;
}

.receiver-panel > p:not(.section-kicker),
.action-panel > p:not(.section-kicker) {
  margin: 6px 0;
  color: var(--muted);
  font-size: 13px;
  line-height: 1.6;
}

.receiver-panel dl {
  display: grid;
  gap: 10px;
  margin: 20px 0 0;
}

.receiver-panel dl div {
  display: flex;
  justify-content: space-between;
}

.receiver-panel dt,
.receiver-panel dd {
  margin: 0;
  font-size: 12px;
}

.receiver-panel dt {
  color: var(--muted);
}

.panel-title > span {
  color: var(--berry);
  font-size: 13px;
  font-weight: 800;
}

.bom-row {
  display: grid;
  grid-template-columns: 1fr 70px 90px;
  gap: 10px;
  align-items: center;
  padding: 12px 0;
  border-top: 1px solid #efe9e5;
  font-size: 13px;
}

.bom-row:first-child {
  border-top: 0;
}

.bom-row strong,
.bom-row small {
  display: block;
}

.bom-row small {
  margin-top: 4px;
  color: var(--muted);
  font-size: 10px;
}

.bom-row > span {
  text-align: right;
}

.action-panel {
  background: #fcfaf8;
}

.action-panel label {
  margin-top: 12px;
}

.action-panel .primary-button {
  width: 100%;
  margin-top: 16px;
}

.order-pagination {
  max-width: 340px;
  margin: 24px auto 0;
  color: var(--muted);
}

@media (max-width: 1280px) {
  .order-card-body {
    grid-template-columns: 0.85fr 1.15fr;
  }

  .action-panel {
    grid-column: 1 / -1;
    border-top: 1px solid var(--line);
    border-left: 0 !important;
  }
}
</style>
