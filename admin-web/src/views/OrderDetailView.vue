<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import defaultProductImage from '../../../miniprogram/miniprogram/assets/images/home/product-gentle-guardian.webp'
import checkIcon from '../assets/icons/check-line.svg'
import { ApiError, resolveApiResourceUrl } from '../api/http'
import {
  completeOrderProduction,
  getAdminOrder,
  shipAdminOrder,
  updateAdminOrderAddress,
  type AdminOrder,
  type OrderStatus,
} from '../api/orders'
import {
  formatDate,
  formatMoney,
  maskPhone,
  normalStatusSteps,
  paymentLabel,
  statusHint,
  statusLabels,
} from '../orders/presenter'

const props = defineProps<{ orderId: number }>()
const emit = defineEmits<{ back: [] }>()

const order = ref<AdminOrder | null>(null)
const loading = ref(true)
const working = ref(false)
const loadError = ref('')
const toastMessage = ref('')
const toastTone = ref<'success' | 'error'>('success')
let toastTimer: ReturnType<typeof setTimeout> | null = null
const shippingVisible = ref(false)
const addressVisible = ref(false)
const shipping = reactive({ logisticsCompany: '顺丰速运', trackingNo: '' })
const address = reactive({ contactName: '', contactPhone: '', addressDetail: '', orderNote: '' })

const isAbnormal = computed(() => order.value
  ? ['CANCELLED', 'AFTER_SALE', 'REFUNDED'].includes(order.value.status)
  : false)
const currentStep = computed(() => order.value
  ? normalStatusSteps.findIndex((step) => step.status === order.value?.status)
  : -1)
const originalAmount = computed(() => order.value
  ? order.value.beadAmount + order.value.craftFee + order.value.cordFee
  : 0)
const productImage = computed(() => {
  const url = order.value?.product.previewUrl
  return url ? resolveApiResourceUrl(url) : defaultProductImage
})

function readableError(error: unknown): string {
  if (error instanceof ApiError) {
    return error.traceId ? `${error.message}（追踪号：${error.traceId}）` : error.message
  }
  return error instanceof Error ? error.message : '订单操作失败'
}

function showToast(message: string, tone: 'success' | 'error' = 'success'): void {
  toastMessage.value = message
  toastTone.value = tone
  if (toastTimer) clearTimeout(toastTimer)
  toastTimer = setTimeout(() => {
    toastMessage.value = ''
    toastTimer = null
  }, 5000)
}

async function loadOrder(): Promise<void> {
  loading.value = true
  loadError.value = ''
  try {
    order.value = await getAdminOrder(props.orderId)
  } catch (error) {
    loadError.value = readableError(error)
    showToast(loadError.value, 'error')
  } finally {
    loading.value = false
  }
}

function stepTime(status: OrderStatus): string {
  const current = order.value
  if (!current) return '—'
  const times: Partial<Record<OrderStatus, string | null>> = {
    PENDING_PAYMENT: current.createdAt,
    IN_PRODUCTION: current.fulfillment?.productionStartedAt || current.paidAt,
    PENDING_SHIPMENT: current.fulfillment?.productionCompletedAt || null,
    SHIPPED: current.shippedAt,
    COMPLETED: current.completedAt,
  }
  const index = normalStatusSteps.findIndex((step) => step.status === status)
  return index <= currentStep.value ? formatDate(times[status] || null) : ''
}

function isStepComplete(index: number): boolean {
  if (!order.value) return false
  return index < currentStep.value
    || (order.value.status === 'COMPLETED' && index === currentStep.value)
}

function abnormalTime(current: AdminOrder): string {
  if (current.status === 'CANCELLED') return formatDate(current.cancelledAt || current.updatedAt)
  return formatDate(current.updatedAt)
}

async function copyOrderNo(): Promise<void> {
  if (!order.value) return
  try {
    await navigator.clipboard.writeText(order.value.orderNo)
    showToast('订单编号已复制')
  } catch {
    showToast('复制失败，请手动选择订单编号', 'error')
  }
}

function printBom(): void {
  window.print()
}

async function completeProduction(): Promise<void> {
  const current = order.value
  if (!current || !window.confirm('确认手串已经完成制作并进入待发货吗？')) return
  await runAction(
    () => completeOrderProduction(current.id),
    '已完成制作，订单进入待发货',
  )
}

async function submitShipping(): Promise<void> {
  const current = order.value
  if (!current) return
  if (!shipping.logisticsCompany.trim() || !shipping.trackingNo.trim()) {
    showToast('请填写物流公司和物流单号', 'error')
    return
  }
  const succeeded = await runAction(
    () => shipAdminOrder(
      current.id,
      shipping.logisticsCompany.trim(),
      shipping.trackingNo.trim(),
    ),
    '订单已发货',
  )
  if (succeeded) shippingVisible.value = false
}

function openAddressEditor(): void {
  const current = order.value
  if (!current) return
  Object.assign(address, {
    contactName: current.contactName,
    contactPhone: current.contactPhone,
    addressDetail: current.addressDetail,
    orderNote: current.orderNote || '',
  })
  addressVisible.value = true
}

async function saveAddress(): Promise<void> {
  const current = order.value
  if (!current) return
  if (!address.contactName.trim() || !address.contactPhone.trim() || !address.addressDetail.trim()) {
    showToast('请完整填写收件人、手机号和收货地址', 'error')
    return
  }
  const succeeded = await runAction(
    () => updateAdminOrderAddress(current.id, {
      contactName: address.contactName.trim(),
      contactPhone: address.contactPhone.trim(),
      addressDetail: address.addressDetail.trim(),
      orderNote: address.orderNote.trim(),
    }),
    '收货信息已更新',
  )
  if (succeeded) addressVisible.value = false
}

async function runAction(
  action: () => Promise<AdminOrder>,
  message: string,
): Promise<boolean> {
  working.value = true
  try {
    order.value = await action()
    showToast(message)
    return true
  } catch (error) {
    showToast(readableError(error), 'error')
    return false
  } finally {
    working.value = false
  }
}

function showScheme(): void {
  const url = order.value?.product.previewUrl
  if (!url) {
    showToast('该订单未保存独立预览图，当前已展示订单商品图')
    return
  }
  window.open(resolveApiResourceUrl(url), '_blank', 'noopener,noreferrer')
}

onMounted(loadOrder)
onBeforeUnmount(() => {
  if (toastTimer) clearTimeout(toastTimer)
})
</script>

<template>
  <section class="order-detail-page">
    <Transition name="toast">
      <div
        v-if="toastMessage"
        :class="['order-detail-toast', toastTone]"
        data-test="order-toast"
        role="status"
        aria-live="polite"
      >{{ toastMessage }}</div>
    </Transition>
    <div v-if="loading" class="empty-state">正在读取订单详情…</div>
    <div v-else-if="loadError && !order" class="empty-state"><p>{{ loadError }}</p><button class="secondary-button" @click="loadOrder">重新加载</button></div>

    <template v-else-if="order">
      <header class="detail-titlebar">
        <div><h1>订单详情</h1><p>查看订单信息、珠材明细、支付及履约进度。</p></div>
        <div class="detail-top-actions">
          <button class="secondary-button" @click="emit('back')">返回列表</button>
          <button v-if="order.status === 'IN_PRODUCTION'" class="secondary-button" @click="printBom">打印 BOM</button>
          <button v-if="order.status === 'IN_PRODUCTION'" class="primary-button" data-test="complete-production" :disabled="working" @click="completeProduction">完成制作</button>
          <button v-if="order.status === 'PENDING_SHIPMENT'" class="primary-button" :disabled="working" @click="shippingVisible = true">发货</button>
          <button v-if="order.status === 'SHIPPED'" class="secondary-button" @click="shippingVisible = true">查看物流</button>
        </div>
      </header>

      <section class="detail-summary-card">
        <div><span>订单编号</span><strong>{{ order.orderNo }}</strong><button class="text-button" @click="copyOrderNo">复制</button></div>
        <div><span>下单时间</span><strong>{{ formatDate(order.createdAt) }}</strong></div>
        <div><span>实付金额</span><strong class="summary-money">{{ formatMoney(order.totalAmount) }}</strong></div>
        <div><span>顾客</span><strong>{{ order.contactName }}</strong></div>
      </section>

      <section class="fulfillment-card">
        <div class="fulfillment-line">
          <div
            v-for="(step, index) in normalStatusSteps"
            :key="step.status"
            :class="['fulfillment-step', { completed: isStepComplete(index), current: index === currentStep && !isStepComplete(index), muted: isAbnormal }]"
          >
            <div class="step-marker">
              <img v-if="isStepComplete(index)" class="step-check" :src="checkIcon" alt="已完成" />
              <span v-else>{{ index + 1 }}</span>
            </div>
            <strong>{{ step.label }}</strong>
            <small>{{ stepTime(step.status) }}</small>
          </div>
        </div>
        <div v-if="!isAbnormal" class="status-guidance">当前状态：{{ statusLabels[order.status] }}；{{ statusHint(order.status) }}</div>
        <div v-else class="abnormal-status"><strong>{{ statusLabels[order.status] }}</strong><span>{{ abnormalTime(order) }}</span></div>
      </section>

      <div class="detail-grid upper-grid">
        <section class="detail-card customer-card">
          <header><h2>A. 顾客与收货信息</h2><button v-if="['PENDING_PAYMENT','IN_PRODUCTION','PENDING_SHIPMENT'].includes(order.status)" class="text-button" @click="openAddressEditor">编辑地址</button></header>
          <dl><div><dt>收件人</dt><dd>{{ order.contactName }}</dd></div><div><dt>手机号</dt><dd>{{ maskPhone(order.contactPhone) }}</dd></div><div><dt>收货地址</dt><dd>{{ order.addressDetail }}</dd></div><div><dt>备注信息</dt><dd>{{ order.orderNote || '无' }}</dd></div></dl>
        </section>

        <section class="detail-card product-card">
          <header><h2>B. 订单商品 / 方案</h2><button class="text-button" @click="showScheme">查看方案</button></header>
          <div class="product-detail"><img :src="productImage" :alt="order.product.name" /><div><h3>{{ order.product.name }}</h3><dl><div><dt>方案编号</dt><dd>{{ order.product.schemeNo }}</dd></div><div><dt>珠子数量</dt><dd>{{ order.product.beadCount }} 颗</dd></div><div><dt>腕围</dt><dd>{{ order.product.wristCircumferenceMm ? `${Number(order.product.wristCircumferenceMm / 10).toFixed(1)} cm` : '—' }}</dd></div><div><dt>预计工时</dt><dd>{{ order.product.estimatedMinutes }} 分钟</dd></div><div><dt>绳材</dt><dd>{{ order.product.cordName }}</dd></div></dl></div></div>
        </section>
      </div>

      <div class="detail-grid lower-grid">
        <section class="detail-card bom-detail-card">
          <header><h2>C. 珠材 BOM 明细</h2></header>
          <table><thead><tr><th>珠材</th><th>规格</th><th>数量</th><th>单价</th><th>小计</th></tr></thead><tbody><tr v-for="item in order.items" :key="item.skuId"><td>{{ item.name }}</td><td>{{ item.name.match(/\d+(?:\.\d+)?mm/i)?.[0] || item.skuCode }}</td><td>{{ item.quantity }} 颗</td><td>{{ formatMoney(item.unitPrice) }}</td><td>{{ formatMoney(item.totalPrice) }}</td></tr><tr v-if="order.craftFee"><td>工艺费</td><td>标准穿制</td><td>1 项</td><td>{{ formatMoney(order.craftFee) }}</td><td>{{ formatMoney(order.craftFee) }}</td></tr></tbody></table>
          <footer><button class="text-button" @click="printBom">打印 BOM</button><strong>BOM 估算：{{ formatMoney(order.beadAmount + order.craftFee + order.cordFee) }}</strong></footer>
        </section>

        <section class="detail-card payment-card">
          <header><h2>D. 支付信息</h2></header>
          <div class="payment-columns"><dl><div><dt>商品总价</dt><dd>{{ formatMoney(originalAmount) }}</dd></div><div><dt>优惠券</dt><dd>{{ order.couponName || '未使用' }}</dd></div><div><dt>优惠抵扣</dt><dd class="discount">-{{ formatMoney(order.discountAmount) }}</dd></div><div><dt>运费</dt><dd>{{ formatMoney(order.shippingFee) }}</dd></div><div class="paid-total"><dt>实付款</dt><dd>{{ formatMoney(order.totalAmount) }}</dd></div></dl><dl><div><dt>支付方式</dt><dd>{{ paymentLabel(order) }}</dd></div><div><dt>支付时间</dt><dd>{{ formatDate(order.payment?.paidAt || order.paidAt) }}</dd></div></dl></div>
        </section>

        <section class="detail-card operation-card">
          <header><h2>E. 操作记录</h2></header>
          <div v-if="!order.operations.length" class="operation-empty">暂无操作记录</div>
          <ol v-else><li v-for="operation in order.operations" :key="operation.id"><time>{{ formatDate(operation.occurredAt) }}</time><span>{{ operation.operatorName }}</span><strong>{{ operation.content }}</strong></li></ol>
        </section>
      </div>
    </template>

    <div v-if="shippingVisible && order" class="detail-modal-backdrop" @click.self="shippingVisible = false">
      <section class="detail-modal" role="dialog" aria-modal="true" aria-label="物流信息">
        <header><div><h2>{{ order.status === 'SHIPPED' ? '物流信息' : '订单发货' }}</h2><p>{{ order.orderNo }}</p></div><button class="text-button" @click="shippingVisible = false">关闭</button></header>
        <template v-if="order.status === 'SHIPPED'"><dl class="logistics-readonly"><div><dt>物流公司</dt><dd>{{ order.fulfillment?.logisticsCompany || '—' }}</dd></div><div><dt>物流单号</dt><dd>{{ order.fulfillment?.trackingNo || '—' }}</dd></div><div><dt>发货时间</dt><dd>{{ formatDate(order.shippedAt) }}</dd></div></dl></template>
        <template v-else><label><span>物流公司</span><input v-model="shipping.logisticsCompany" /></label><label><span>物流单号</span><input v-model="shipping.trackingNo" placeholder="请输入物流单号" /></label><footer><button class="secondary-button" @click="shippingVisible = false">取消</button><button class="primary-button" data-test="submit-shipping" :disabled="working" @click="submitShipping">确认发货</button></footer></template>
      </section>
    </div>

    <div v-if="addressVisible" class="detail-modal-backdrop" @click.self="addressVisible = false">
      <section class="detail-modal address-modal" role="dialog" aria-modal="true" aria-label="编辑收货信息">
        <header><div><h2>编辑收货信息</h2><p>发货前可调整本订单地址</p></div><button class="text-button" @click="addressVisible = false">关闭</button></header>
        <div class="address-fields"><label><span>收件人</span><input v-model="address.contactName" /></label><label><span>手机号</span><input v-model="address.contactPhone" /></label></div><label><span>收货地址</span><textarea v-model="address.addressDetail" rows="3" /></label><label><span>订单备注</span><textarea v-model="address.orderNote" rows="2" /></label>
        <footer><button class="secondary-button" @click="addressVisible = false">取消</button><button class="primary-button" :disabled="working" @click="saveAddress">保存地址</button></footer>
      </section>
    </div>
  </section>
</template>

<style scoped>
.order-detail-page { padding-bottom: 20px; }.detail-titlebar { display: flex; justify-content: space-between; align-items: flex-start; margin-bottom: 18px; }.detail-titlebar h1 { margin: 0 0 5px; font-family: "Songti SC", "STSong", serif; font-size: 34px; }.detail-titlebar p { margin: 0; color: var(--muted); }.detail-top-actions { display: flex; gap: 10px; }
.order-detail-toast { position: fixed; top: 24px; right: 28px; z-index: 90; width: min(420px, calc(100vw - 48px)); padding: 13px 17px; border: 1px solid #cce4d8; border-radius: 12px; color: #2d7255; line-height: 1.55; white-space: normal; overflow-wrap: anywhere; background: #effaf5; box-shadow: 0 16px 42px rgb(35 26 30 / 18%); }.order-detail-toast.error { border-color: #f0c7cb; color: #a53d49; background: #fff3f4; }.toast-enter-active,.toast-leave-active { transition: opacity .18s ease, transform .18s ease; }.toast-enter-from,.toast-leave-to { opacity: 0; transform: translateY(-8px); }
.detail-summary-card { display: grid; grid-template-columns: 1.1fr 1fr 1fr 1fr; margin-bottom: 16px; padding: 18px 22px; border: 1px solid var(--line); border-radius: 13px; background: var(--paper); box-shadow: 0 8px 26px rgb(58 43 49 / 5%); }.detail-summary-card > div { min-width: 0; padding: 0 22px; border-left: 1px solid #eee5e2; }.detail-summary-card > div:first-child { padding-left: 0; border-left: 0; }.detail-summary-card span,.detail-summary-card strong { display: block; }.detail-summary-card span { margin-bottom: 9px; color: var(--muted); font-size: 12px; }.detail-summary-card strong { font-size: 15px; }.detail-summary-card .text-button { margin-top: 5px; padding: 0; }.summary-money { color: var(--berry); font-size: 18px !important; }
.fulfillment-card { margin-bottom: 16px; padding: 22px 18px 12px; border: 1px solid var(--line); border-radius: 13px; background: var(--paper); box-shadow: 0 8px 26px rgb(58 43 49 / 5%); }.fulfillment-line { display: grid; grid-template-columns: repeat(5, 1fr); }.fulfillment-step { position: relative; display: grid; place-items: center; text-align: center; }.fulfillment-step::before,.fulfillment-step::after { position: absolute; top: 12px; z-index: 0; width: 50%; height: 2px; content: ""; background: #d9d4d2; }.fulfillment-step::before { left: 0; }.fulfillment-step::after { right: 0; }.fulfillment-step:first-child::before,.fulfillment-step:last-child::after { display: none; }.fulfillment-step.completed::before,.fulfillment-step.completed::after,.fulfillment-step.current::before { background: var(--berry); }.step-marker { position: relative; z-index: 1; display: grid; width: 25px; height: 25px; place-items: center; border: 3px solid #d9d4d2; border-radius: 50%; color: #9e9498; font-size: 10px; background: #fff; }.completed .step-marker,.current .step-marker { border-color: var(--berry); color: #fff; background: var(--berry); }.step-check { display: block; width: 14px; height: 14px; }.fulfillment-step strong { margin-top: 8px; font-size: 13px; }.fulfillment-step small { min-height: 16px; margin-top: 5px; color: var(--muted); font-size: 10px; }.fulfillment-step.muted { opacity: .45; }.status-guidance,.abnormal-status { margin-top: 15px; padding: 9px 12px; border-radius: 8px; color: #4d78a0; font-size: 12px; background: #f0f6fd; }.abnormal-status { display: flex; gap: 12px; color: #98515a; background: #fff0f1; }
.detail-grid { display: grid; gap: 14px; margin-bottom: 14px; }.upper-grid { grid-template-columns: .8fr 1.2fr; }.lower-grid { grid-template-columns: 1.05fr .8fr .8fr; }.detail-card { overflow: hidden; border: 1px solid var(--line); border-radius: 13px; background: var(--paper); box-shadow: 0 8px 26px rgb(58 43 49 / 5%); }.detail-card > header { display: flex; min-height: 44px; padding: 0 18px; border-bottom: 1px solid #eee5e2; align-items: center; justify-content: space-between; }.detail-card h2 { margin: 0; font-size: 14px; }.detail-card dl { margin: 0; }.customer-card > dl { display: grid; gap: 12px; padding: 18px 20px; }.detail-card dl div { display: grid; grid-template-columns: 90px 1fr; gap: 10px; }.detail-card dt,.detail-card dd { margin: 0; font-size: 12px; }.detail-card dt { color: var(--muted); }.product-detail { display: grid; grid-template-columns: 132px 1fr; gap: 20px; padding: 14px 20px; }.product-detail > img { width: 132px; height: 118px; border-radius: 10px; object-fit: cover; background: #f8f1f3; }.product-detail h3 { margin: 3px 0 16px; font-size: 15px; }.product-detail dl { display: grid; grid-template-columns: 1fr 1fr; gap: 12px 24px; }
.bom-detail-card table { width: 100%; }.bom-detail-card th,.bom-detail-card td { padding: 9px 14px; font-size: 11px; }.bom-detail-card footer { display: flex; padding: 12px 16px; border-top: 1px solid #eee5e2; align-items: center; justify-content: space-between; }.bom-detail-card footer strong { color: var(--berry); font-size: 14px; }.payment-columns { display: grid; grid-template-columns: 1fr .9fr; }.payment-columns > dl { padding: 14px 16px; }.payment-columns > dl + dl { border-left: 1px solid #eee5e2; }.payment-columns dl div { grid-template-columns: 1fr auto; margin-bottom: 11px; }.payment-columns dd { text-align: right; }.discount { color: #d14d4d; }.paid-total { margin: 10px -16px -14px !important; padding: 12px 16px; border-top: 1px solid #eee5e2; }.paid-total dd { color: var(--berry); font-size: 16px !important; font-weight: 800; }.operation-card ol { position: relative; display: grid; gap: 15px; margin: 0; padding: 17px 18px 17px 34px; list-style: none; }.operation-card li { position: relative; display: grid; grid-template-columns: 115px 78px 1fr; gap: 8px; font-size: 11px; }.operation-card li::before { position: absolute; top: 3px; left: -19px; width: 8px; height: 8px; border-radius: 50%; content: ""; background: var(--berry); }.operation-card time,.operation-card span { color: var(--muted); }.operation-empty { padding: 30px; color: var(--muted); text-align: center; }
.detail-modal-backdrop { position: fixed; inset: 0; z-index: 30; display: grid; padding: 24px; place-items: center; background: rgb(35 26 30 / 42%); }.detail-modal { display: grid; width: min(470px, 100%); gap: 18px; padding: 24px; border-radius: 18px; background: #fffdfb; box-shadow: 0 28px 80px rgb(35 26 30 / 25%); }.detail-modal header,.detail-modal footer { display: flex; align-items: center; justify-content: space-between; }.detail-modal h2,.detail-modal p { margin: 0; }.detail-modal p { margin-top: 4px; color: var(--muted); font-size: 12px; }.detail-modal footer { justify-content: flex-end; gap: 10px; }.logistics-readonly { display: grid; gap: 14px; }.logistics-readonly div { display: grid; grid-template-columns: 100px 1fr; }.address-modal { width: min(620px, 100%); }.address-fields { display: grid; grid-template-columns: 1fr 1fr; gap: 12px; }.address-modal textarea { padding: 10px 13px; resize: vertical; }
@media (max-width: 1280px) { .lower-grid { grid-template-columns: 1fr 1fr; }.operation-card { grid-column: 1 / -1; } }
@media print { .sidebar,.detail-titlebar,.notice,.fulfillment-card,.upper-grid,.payment-card,.operation-card,.detail-modal-backdrop { display: none !important; }.admin-content { padding: 0 !important; }.lower-grid { display: block; }.bom-detail-card { border: 0; box-shadow: none; }.bom-detail-card footer .text-button { display: none; } }
</style>
