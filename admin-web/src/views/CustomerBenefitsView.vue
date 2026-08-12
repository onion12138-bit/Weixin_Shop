<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { ApiError, resolveApiResourceUrl } from '../api/http'
import OverflowText from '../components/OverflowText.vue'
import defaultAvatar from '../../../miniprogram/miniprogram/assets/images/profile/avatar.webp'
import {
  changeCustomerAddressStatus,
  createCouponTemplate,
  grantCouponsBatch,
  listCouponTemplates,
  listCustomerAddresses,
  listCustomers,
  searchCustomersByPhone,
  updateCouponTemplate,
  type AdminCustomerAddress,
  type CouponTemplate,
  type CustomerSummary,
  type SaveCouponTemplate,
} from '../api/customers'

type Tab = 'customers' | 'coupons'
type RecipientMode = 'SINGLE' | 'MULTIPLE' | 'ALL'

const activeTab = ref<Tab>('customers')
const customers = ref<CustomerSummary[]>([])
const addresses = ref<AdminCustomerAddress[]>([])
const coupons = ref<CouponTemplate[]>([])
const customerKeyword = ref('')
const addressStatus = ref<'' | 'ACTIVE' | 'DELETED'>('')
const addressCustomer = ref<CustomerSummary | null>(null)
const loading = ref(false)
const addressLoading = ref(false)
const workingId = ref<number | null>(null)
const errorMessage = ref('')
const successMessage = ref('')
const editingCouponId = ref<number | null>(null)
const grantTemplateId = ref<number | null>(null)
const grantNote = ref('')
const recipientMode = ref<RecipientMode>('SINGLE')
const phoneQuery = ref('')
const phoneResults = ref<CustomerSummary[]>([])
const visibleRecipientCount = ref(10)
const selectedCustomerIds = ref<number[]>([])
const customerSearching = ref(false)
let phoneSearchTimer: ReturnType<typeof setTimeout> | null = null

const filteredCustomers = computed(() => {
  const query = customerKeyword.value.trim().toLowerCase()
  if (!query) return customers.value
  return customers.value.filter((customer) => [
    customer.nickname, customer.phone, customer.address, String(customer.id),
  ].some((value) => value.toLowerCase().includes(query)))
})

const selectedCustomers = computed(() => {
  const lookup = new Map(
    [...customers.value, ...phoneResults.value].map((customer) => [customer.id, customer]),
  )
  return selectedCustomerIds.value
    .map((id) => lookup.get(id))
    .filter((customer): customer is CustomerSummary => Boolean(customer))
})
const recipientCandidates = computed(() => phoneQuery.value.trim()
  ? phoneResults.value
  : customers.value)
const visibleRecipientCandidates = computed(() =>
  recipientCandidates.value.slice(0, visibleRecipientCount.value))
const hasMoreRecipientCandidates = computed(() =>
  visibleRecipientCount.value < recipientCandidates.value.length)

const couponDraft = reactive<SaveCouponTemplate>({
  code: '', name: '', description: '', discountAmount: 10,
  minimumSpend: 0, validDays: 30, status: 'ACTIVE',
})

function readableError(error: unknown): string {
  if (error instanceof ApiError) {
    return error.traceId ? `${error.message}（追踪号：${error.traceId}）` : error.message
  }
  return error instanceof Error ? error.message : '操作失败，请稍后重试'
}

function formatDate(value: string): string {
  return value ? value.replace('T', ' ').slice(0, 16) : '—'
}

function avatarUrl(value: string | null): string {
  return value ? resolveApiResourceUrl(value) : ''
}

async function loadBenefits(): Promise<void> {
  loading.value = true
  errorMessage.value = ''
  try {
    const [customerItems, couponItems] = await Promise.all([
      listCustomers(), listCouponTemplates(),
    ])
    customers.value = customerItems
    coupons.value = couponItems
  } catch (error) {
    errorMessage.value = readableError(error)
  } finally {
    loading.value = false
  }
}

async function switchTab(tab: Tab): Promise<void> {
  activeTab.value = tab
  successMessage.value = ''
  await loadBenefits()
}

async function openAddresses(customer: CustomerSummary): Promise<void> {
  addressCustomer.value = customer
  addressStatus.value = ''
  await loadAddresses()
}

async function loadAddresses(): Promise<void> {
  if (!addressCustomer.value) return
  addressLoading.value = true
  errorMessage.value = ''
  try {
    addresses.value = await listCustomerAddresses({
      customerIdentityId: addressCustomer.value.id,
      status: addressStatus.value,
    })
  } catch (error) {
    errorMessage.value = readableError(error)
  } finally {
    addressLoading.value = false
  }
}

async function toggleAddress(address: AdminCustomerAddress): Promise<void> {
  workingId.value = address.id
  errorMessage.value = ''
  successMessage.value = ''
  try {
    const next = address.status === 'ACTIVE' ? 'DELETED' : 'ACTIVE'
    const updated = await changeCustomerAddressStatus(address.id, next)
    const index = addresses.value.findIndex((item) => item.id === updated.id)
    if (index >= 0) addresses.value[index] = updated
    successMessage.value = next === 'ACTIVE' ? '地址已恢复' : '地址已停用'
  } catch (error) {
    errorMessage.value = readableError(error)
  } finally {
    workingId.value = null
  }
}

function resetCouponDraft(): void {
  editingCouponId.value = null
  Object.assign(couponDraft, {
    code: '', name: '', description: '', discountAmount: 10,
    minimumSpend: 0, validDays: 30, status: 'ACTIVE',
  })
}

function editCoupon(coupon: CouponTemplate): void {
  editingCouponId.value = coupon.id
  Object.assign(couponDraft, {
    code: coupon.code, name: coupon.name, description: coupon.description,
    discountAmount: coupon.discountAmount,
    minimumSpend: coupon.minimumSpend,
    validDays: coupon.validDays, status: coupon.status,
  })
}

async function saveCoupon(): Promise<void> {
  errorMessage.value = ''
  successMessage.value = ''
  if (!couponDraft.code.trim() || !couponDraft.name.trim() || !couponDraft.description.trim()) {
    errorMessage.value = '请完整填写优惠券编码、名称和说明'
    return
  }
  loading.value = true
  try {
    const payload = { ...couponDraft, code: couponDraft.code.trim().toUpperCase() }
    if (editingCouponId.value) {
      await updateCouponTemplate(editingCouponId.value, payload)
      successMessage.value = '优惠券已更新'
    } else {
      await createCouponTemplate(payload)
      successMessage.value = '优惠券已创建'
    }
    resetCouponDraft()
    coupons.value = await listCouponTemplates()
  } catch (error) {
    errorMessage.value = readableError(error)
  } finally {
    loading.value = false
  }
}

function openGrant(coupon: CouponTemplate): void {
  grantTemplateId.value = coupon.id
  grantNote.value = ''
  recipientMode.value = 'SINGLE'
  phoneQuery.value = ''
  phoneResults.value = []
  visibleRecipientCount.value = 10
  selectedCustomerIds.value = []
}

function closeGrant(): void {
  grantTemplateId.value = null
  phoneResults.value = []
  selectedCustomerIds.value = []
}

function selectCustomer(id: number): void {
  if (recipientMode.value === 'SINGLE') {
    selectedCustomerIds.value = [id]
    return
  }
  selectedCustomerIds.value = selectedCustomerIds.value.includes(id)
    ? selectedCustomerIds.value.filter((item) => item !== id)
    : [...selectedCustomerIds.value, id]
}

function removeSelectedCustomer(id: number): void {
  selectedCustomerIds.value = selectedCustomerIds.value.filter((item) => item !== id)
}

async function runPhoneSearch(): Promise<void> {
  const phone = phoneQuery.value.trim()
  visibleRecipientCount.value = 10
  if (!phone) {
    phoneResults.value = []
    return
  }
  customerSearching.value = true
  try {
    phoneResults.value = await searchCustomersByPhone(phone, 50)
  } catch (error) {
    errorMessage.value = readableError(error)
  } finally {
    customerSearching.value = false
  }
}

function loadMoreRecipients(): void {
  if (!hasMoreRecipientCandidates.value) return
  visibleRecipientCount.value += 10
}

function onRecipientScroll(event: Event): void {
  const target = event.currentTarget as HTMLElement
  if (target.scrollTop + target.clientHeight >= target.scrollHeight - 24) {
    loadMoreRecipients()
  }
}

watch(phoneQuery, () => {
  if (phoneSearchTimer) clearTimeout(phoneSearchTimer)
  phoneSearchTimer = setTimeout(() => void runPhoneSearch(), 300)
})

watch(recipientMode, () => {
  selectedCustomerIds.value = []
  phoneQuery.value = ''
  phoneResults.value = []
  visibleRecipientCount.value = 10
})

async function sendCoupon(): Promise<void> {
  if (!grantTemplateId.value) return
  if (recipientMode.value !== 'ALL' && !selectedCustomerIds.value.length) {
    errorMessage.value = '请先按手机号搜索并选择顾客'
    return
  }
  workingId.value = grantTemplateId.value
  errorMessage.value = ''
  successMessage.value = ''
  try {
    const result = await grantCouponsBatch(grantTemplateId.value, {
      recipientMode: recipientMode.value === 'ALL' ? 'ALL' : 'SELECTED',
      customerIdentityIds: selectedCustomerIds.value,
      note: grantNote.value.trim(),
    })
    successMessage.value = `优惠券发送完成：成功 ${result.grantedCount} 位${result.skippedCount ? `，跳过 ${result.skippedCount} 位` : ''}`
    closeGrant()
    coupons.value = await listCouponTemplates()
  } catch (error) {
    errorMessage.value = readableError(error)
  } finally {
    workingId.value = null
  }
}

onMounted(() => loadBenefits())
onBeforeUnmount(() => {
  if (phoneSearchTimer) clearTimeout(phoneSearchTimer)
})
</script>

<template>
  <section class="customer-workspace">
    <header class="content-header customer-header">
      <div>
        <p class="section-kicker">CUSTOMER / BENEFITS</p>
        <h1>顾客与权益</h1>
        <p class="muted">统一查看顾客资料与地址，并按单人、多选或全量发送优惠券。</p>
      </div>
      <div class="customer-tabs">
        <button :class="{ active: activeTab === 'customers' }" @click="switchTab('customers')">客户信息</button>
        <button :class="{ active: activeTab === 'coupons' }" @click="switchTab('coupons')">优惠权益</button>
      </div>
    </header>

    <div v-if="errorMessage" class="notice error-notice" role="alert">{{ errorMessage }}</div>
    <div v-if="successMessage" class="notice success-notice">{{ successMessage }}</div>

    <template v-if="activeTab === 'customers'">
      <div class="customer-toolbar customer-search-toolbar">
        <input v-model="customerKeyword" placeholder="搜索昵称、手机号、地址或客户编号" />
        <span>共 {{ filteredCustomers.length }} 位客户</span>
      </div>
      <div v-if="loading" class="empty-state">正在读取客户信息…</div>
      <div v-else-if="!filteredCustomers.length" class="empty-state">暂无符合条件的客户</div>
      <div v-else class="address-table customer-table">
        <div class="customer-row customer-head"><span>客户</span><span>手机号</span><span>默认收货地址</span><span>地址簿</span><span>来源</span><span>加入时间</span></div>
        <div v-for="customer in filteredCustomers" :key="customer.id" class="customer-row">
          <span class="customer-identity">
            <img v-if="customer.avatarUrl" :src="avatarUrl(customer.avatarUrl)" :alt="customer.nickname" />
            <img v-else :src="defaultAvatar" alt="默认头像" />
            <span><strong>{{ customer.nickname }}</strong><small>客户 #{{ customer.id }}</small></span>
          </span>
          <span>{{ customer.phone || '未填写' }}</span>
          <OverflowText class="customer-address" :text="customer.address || '暂无收货地址'" />
          <span><button class="address-book-button" data-test="open-customer-addresses" @click="openAddresses(customer)">查看 {{ customer.addressCount || 0 }} 个地址</button></span>
          <span><em class="source-badge">{{ customer.source === 'GUEST' ? '小程序访客' : customer.source }}</em></span>
          <span>{{ formatDate(customer.createdAt) }}</span>
        </div>
      </div>
    </template>

    <template v-else>
      <div class="coupon-layout">
        <form class="coupon-form" @submit.prevent="saveCoupon">
          <div class="panel-title"><div><p class="section-kicker">COUPON TEMPLATE</p><h2>{{ editingCouponId ? '编辑优惠券' : '新建优惠券' }}</h2></div><button v-if="editingCouponId" type="button" class="link-button" @click="resetCouponDraft">取消编辑</button></div>
          <label>优惠券编码<input v-model="couponDraft.code" maxlength="64" placeholder="例如 NEW-20" /></label>
          <label>优惠券名称<input v-model="couponDraft.name" maxlength="80" placeholder="例如 新客立减券" /></label>
          <label>优惠说明<textarea v-model="couponDraft.description" maxlength="240" placeholder="展示给顾客的使用说明" /></label>
          <div class="inline-fields">
            <label>优惠金额<input v-model.number="couponDraft.discountAmount" type="number" min="0.01" step="0.01" /></label>
            <label>使用门槛<input v-model.number="couponDraft.minimumSpend" type="number" min="0" step="0.01" /></label>
          </div>
          <div class="inline-fields">
            <label>有效天数<input v-model.number="couponDraft.validDays" type="number" min="1" max="3650" /></label>
            <label>状态<select v-model="couponDraft.status"><option value="ACTIVE">启用</option><option value="INACTIVE">停用</option></select></label>
          </div>
          <button class="primary-button" type="submit" :disabled="loading">{{ editingCouponId ? '保存修改' : '创建优惠券' }}</button>
        </form>

        <div class="coupon-list-panel">
          <div class="panel-title"><div><p class="section-kicker">BENEFITS</p><h2>优惠券列表</h2></div><span>{{ coupons.length }} 个模板</span></div>
          <div v-if="loading" class="empty-state">正在读取优惠权益…</div>
          <div v-else-if="!coupons.length" class="empty-state">还没有优惠券模板</div>
          <article v-for="coupon in coupons" :key="coupon.id" class="coupon-row">
            <div class="coupon-value"><strong>¥{{ Number(coupon.discountAmount).toFixed(0) }}</strong><small>{{ Number(coupon.minimumSpend) > 0 ? `满 ¥${coupon.minimumSpend} 可用` : '无门槛' }}</small></div>
            <div class="coupon-copy"><h3>{{ coupon.name }}</h3><OverflowText :text="coupon.description" :lines="2" /><small>{{ coupon.code }} · {{ coupon.validDays }} 天有效 · 已发 {{ coupon.issuedCount }} 张</small></div>
            <span :class="['coupon-state', coupon.status.toLowerCase()]">{{ coupon.status === 'ACTIVE' ? '启用' : '停用' }}</span>
            <div class="coupon-actions"><button class="link-button" @click="editCoupon(coupon)">编辑</button><button class="primary-button" :disabled="coupon.status !== 'ACTIVE'" @click="openGrant(coupon)">发送</button></div>
          </article>
        </div>
      </div>
    </template>

    <div v-if="addressCustomer" class="modal-backdrop" @click.self="addressCustomer = null">
      <section class="address-modal" role="dialog" aria-modal="true" aria-label="客户地址簿">
        <div class="panel-title">
          <div><p class="section-kicker">ADDRESS BOOK</p><h2>{{ addressCustomer.nickname }}的地址</h2><span>客户 #{{ addressCustomer.id }}</span></div>
          <button class="link-button" @click="addressCustomer = null">关闭</button>
        </div>
        <div class="address-modal-toolbar"><span>共 {{ addresses.length }} 个地址</span><select v-model="addressStatus" @change="loadAddresses"><option value="">全部状态</option><option value="ACTIVE">使用中</option><option value="DELETED">已停用</option></select></div>
        <div v-if="addressLoading" class="empty-state">正在读取地址…</div>
        <div v-else-if="!addresses.length" class="empty-state">暂无符合条件的收货地址</div>
        <div v-else class="address-list">
          <article v-for="address in addresses" :key="address.id" class="address-card">
            <div><strong>{{ address.receiverName }} · {{ address.phone }}</strong><OverflowText :text="`${address.region} ${address.detail}`" :lines="2" /></div>
            <div class="address-state"><span><i :class="['state-dot', address.status.toLowerCase()]" />{{ address.status === 'ACTIVE' ? '使用中' : '已停用' }}<em v-if="address.isDefault">默认</em></span><button class="link-button" :disabled="workingId === address.id" @click="toggleAddress(address)">{{ address.status === 'ACTIVE' ? '停用' : '恢复' }}</button></div>
          </article>
        </div>
      </section>
    </div>

    <div v-if="grantTemplateId" class="modal-backdrop grant-backdrop" @click.self="closeGrant">
      <section class="grant-modal" role="dialog" aria-modal="true" aria-label="发送优惠券">
        <div class="panel-title"><div><p class="section-kicker">SEND BENEFIT</p><h2>发送优惠券</h2></div><button class="link-button" @click="closeGrant">关闭</button></div>
        <div class="recipient-tabs" aria-label="发送范围">
          <button :class="{ active: recipientMode === 'SINGLE' }" @click="recipientMode = 'SINGLE'">单个发送</button>
          <button :class="{ active: recipientMode === 'MULTIPLE' }" @click="recipientMode = 'MULTIPLE'">多选发送</button>
          <button :class="{ active: recipientMode === 'ALL' }" @click="recipientMode = 'ALL'">全量发送</button>
        </div>

        <template v-if="recipientMode !== 'ALL'">
          <label class="phone-search-label">按客户手机号模糊搜索<input v-model="phoneQuery" data-test="coupon-customer-search" inputmode="numeric" placeholder="输入手机号任意连续数字，例如 1380" /></label>
          <div class="search-results" data-test="coupon-customer-results" @scroll="onRecipientScroll">
            <p v-if="customerSearching">正在搜索…</p>
            <p v-else-if="phoneQuery.trim() && !phoneResults.length">没有匹配的客户</p>
            <p v-else-if="!recipientCandidates.length">暂无可选择的客户</p>
            <template v-else>
              <button v-for="customer in visibleRecipientCandidates" :key="customer.id" :class="['customer-option', { selected: selectedCustomerIds.includes(customer.id) }]" @click="selectCustomer(customer.id)">
                <span><strong>{{ customer.nickname }}</strong><small>{{ customer.phone || '未填写手机号' }} · #{{ customer.id }}</small></span><i>{{ selectedCustomerIds.includes(customer.id) ? '✓' : '+' }}</i>
              </button>
              <button v-if="hasMoreRecipientCandidates" class="load-more-customers" data-test="load-more-customers" @click="loadMoreRecipients">继续加载 10 位客户</button>
              <p v-else class="recipient-list-end">已展示全部 {{ recipientCandidates.length }} 位客户</p>
            </template>
          </div>
          <div v-if="selectedCustomers.length" class="selected-customers">
            <span>已选 {{ selectedCustomers.length }} 位</span>
            <button v-for="customer in selectedCustomers" :key="customer.id" @click="removeSelectedCustomer(customer.id)">{{ customer.nickname }} · {{ customer.phone }} ×</button>
          </div>
        </template>
        <div v-else class="all-customer-warning"><strong>将发送给全部有效客户</strong><span>系统会在服务端分批处理，不加载完整客户名单，适用于大量客户。</span></div>

        <label>发券备注<input v-model="grantNote" maxlength="120" placeholder="可选，例如：客服关怀" /></label>
        <button class="primary-button" data-test="send-coupon" :disabled="workingId === grantTemplateId || (recipientMode !== 'ALL' && !selectedCustomerIds.length)" @click="sendCoupon">{{ recipientMode === 'ALL' ? '确认全量发送' : `确认发送${selectedCustomerIds.length ? `（${selectedCustomerIds.length} 位）` : ''}` }}</button>
      </section>
    </div>
  </section>
</template>

<style scoped>
.customer-workspace { padding: 0 0 48px; }
.customer-header { align-items: flex-end; }
.customer-tabs { display: flex; padding: 5px; border: 1px solid #eadde2; border-radius: 14px; background: #fff; }
.customer-tabs button { padding: 10px 22px; border: 0; border-radius: 10px; color: #765c66; background: transparent; }
.customer-tabs button.active { color: #fff; background: #87445f; box-shadow: 0 8px 20px rgb(135 68 95 / 20%); }
.customer-toolbar { display: grid; gap: 12px; margin: 22px 0; }
.customer-search-toolbar { grid-template-columns: minmax(300px, 520px) auto; align-items: center; }
.customer-search-toolbar span { color: #8e7b83; font-size: 13px; }
input, select, textarea { box-sizing: border-box; width: 100%; border: 1px solid #e5d7dc; border-radius: 10px; color: #3d3035; background: #fff; outline: none; }
input, select { height: 44px; padding: 0 13px; }
textarea { min-height: 84px; padding: 11px 13px; resize: vertical; }
input:focus, select:focus, textarea:focus { border-color: #a6677f; box-shadow: 0 0 0 3px rgb(166 103 127 / 10%); }
.address-table, .coupon-form, .coupon-list-panel { overflow: hidden; border: 1px solid #eadde2; border-radius: 18px; background: rgb(255 255 255 / 84%); box-shadow: 0 18px 42px rgb(89 55 69 / 6%); }
.customer-row { display: grid; grid-template-columns: 1.2fr .75fr 1.8fr .8fr .7fr .8fr; gap: 16px; align-items: center; padding: 17px 20px; border-top: 1px solid #f0e7ea; color: #594950; font-size: 14px; }
.customer-row:first-child { border-top: 0; }
.customer-head { color: #8b7780; font-size: 12px; font-weight: 700; letter-spacing: .05em; background: #fbf7f8; }
.customer-identity { display: flex; min-width: 0; align-items: center; gap: 12px; }
.customer-identity > img { display: flex; width: 44px; height: 44px; flex: 0 0 auto; border-radius: 50%; object-fit: cover; }
.customer-identity strong, .customer-identity small { display: block; }
.customer-identity small { margin-top: 4px; color: #99878e; font-size: 11px; }
.customer-address { color: #75646b; }
.address-book-button { min-height: 34px; padding: 0 12px; border: 1px solid #dfcbd3; border-radius: 9px; color: #87445f; font-size: 12px; font-weight: 700; background: #fff8fa; white-space: nowrap; }
.address-book-button:hover { border-color: #b78196; background: #f9edf1; }
.source-badge { display: inline-flex; padding: 5px 9px; border-radius: 12px; color: #8b5368; font-size: 11px; font-style: normal; background: #f5e8ed; }
.state-dot { display: inline-block; width: 7px; height: 7px; margin-right: 7px; border-radius: 50%; background: #aaa; }
.state-dot.active { background: #49a078; }.state-dot.deleted { background: #c47a85; }
.link-button { padding: 3px 6px; border: 0; color: #914b67; background: transparent; }
.link-button:disabled { opacity: .45; cursor: not-allowed; }
.coupon-layout { display: grid; grid-template-columns: 330px minmax(0,1fr); gap: 22px; margin-top: 22px; align-items: start; }
.coupon-form, .coupon-list-panel { padding: 22px; }
.panel-title { display: flex; align-items: flex-start; justify-content: space-between; margin-bottom: 18px; }
.panel-title h2 { margin: 3px 0 0; font-size: 21px; }
.panel-title > span, .panel-title div > span { color: #907f86; font-size: 12px; }
.coupon-form label, .grant-modal label { display: block; margin: 0 0 14px; color: #5a464e; font-size: 13px; font-weight: 650; }
.coupon-form label input, .coupon-form label select, .coupon-form label textarea, .grant-modal label input { margin-top: 7px; font-weight: 400; }
.inline-fields { display: grid; grid-template-columns: 1fr 1fr; gap: 12px; }
.coupon-form > .primary-button { width: 100%; margin-top: 4px; }
.coupon-row { display: grid; grid-template-columns: 112px minmax(0,1fr) 70px 110px; gap: 15px; align-items: center; padding: 17px 0; border-top: 1px solid #f0e7ea; }
.coupon-value { display: flex; height: 76px; flex-direction: column; align-items: center; justify-content: center; border-radius: 14px; color: #a3426c; background: linear-gradient(135deg,#ffe8f0,#fff5f7); }
.coupon-value strong { font-size: 27px; }.coupon-value small { margin-top: 4px; font-size: 11px; }
.coupon-copy { min-width: 0; }.coupon-copy h3 { margin: 0 0 5px; font-size: 16px; }.coupon-copy p { margin: 0 0 6px; color: #74636a; font-size: 13px; }.coupon-copy small { color: #9b8a91; }
.coupon-state { justify-self: center; padding: 5px 9px; border-radius: 12px; font-size: 12px; }.coupon-state.active { color: #287b59; background: #e6f5ee; }.coupon-state.inactive { color: #9d6067; background: #f8e9eb; }
.coupon-actions { display: flex; flex-direction: column; gap: 7px; }.coupon-actions .primary-button { min-height: 34px; padding: 6px 12px; }
.modal-backdrop { position: fixed; inset: 0; z-index: 50; display: flex; justify-content: flex-end; background: rgb(35 26 30 / 42%); backdrop-filter: blur(2px); }
.address-modal, .grant-modal { width: min(620px, calc(100vw - 40px)); height: 100%; overflow: auto; padding: 28px; background: #fffafc; box-shadow: -22px 0 70px rgb(55 31 40 / 20%); }
.address-modal-toolbar { display: flex; align-items: center; justify-content: space-between; padding-bottom: 14px; color: #8e7b83; font-size: 13px; }.address-modal-toolbar select { width: 150px; }
.address-list { display: grid; gap: 12px; }.address-card { display: grid; grid-template-columns: minmax(0,1fr) 150px; gap: 20px; align-items: center; padding: 17px; border: 1px solid #eadde2; border-radius: 14px; background: #fff; }.address-card strong { display: block; margin-bottom: 8px; }.address-state { display: flex; align-items: center; justify-content: space-between; }.address-state em { margin-left: 6px; padding: 3px 7px; border-radius: 9px; color: #9b466a; font-size: 10px; font-style: normal; background: #f9e5ed; }
.grant-modal { width: min(540px, calc(100vw - 40px)); }
.recipient-tabs { display: grid; grid-template-columns: repeat(3, 1fr); gap: 6px; margin-bottom: 18px; padding: 5px; border-radius: 12px; background: #f3e9ed; }.recipient-tabs button { min-height: 38px; border: 0; border-radius: 9px; color: #775d67; background: transparent; }.recipient-tabs button.active { color: #fff; font-weight: 700; background: #87445f; box-shadow: 0 5px 14px rgb(135 68 95 / 18%); }
.phone-search-label { margin-bottom: 8px !important; }.search-results { max-height: 320px; min-height: 92px; overflow: auto; margin-bottom: 12px; border: 1px solid #eadde2; border-radius: 12px; background: #fff; }.search-results > p { margin: 0; padding: 28px 18px; color: #96848b; font-size: 12px; text-align: center; }.customer-option { display: flex; width: 100%; min-height: 58px; padding: 10px 14px; border: 0; border-top: 1px solid #f0e7ea; align-items: center; justify-content: space-between; color: #4d3e44; text-align: left; background: #fff; }.customer-option:first-of-type { border-top: 0; }.customer-option:hover, .customer-option.selected { background: #fff5f8; }.customer-option strong, .customer-option small { display: block; }.customer-option small { margin-top: 4px; color: #8e7b83; }.customer-option i { display: grid; width: 25px; height: 25px; border-radius: 50%; place-items: center; color: #fff; font-style: normal; background: #a5677f; }.load-more-customers { width: 100%; min-height: 42px; border: 0; border-top: 1px solid #f0e7ea; color: #87445f; font-size: 12px; background: #fff8fa; }.recipient-list-end { padding: 12px 18px !important; border-top: 1px solid #f0e7ea; }
.selected-customers { display: flex; flex-wrap: wrap; gap: 7px; margin: 0 0 14px; }.selected-customers > span { width: 100%; color: #7b6870; font-size: 12px; }.selected-customers button { padding: 6px 9px; border: 1px solid #e6d3da; border-radius: 16px; color: #7d4658; font-size: 11px; background: #fff; }
.all-customer-warning { display: flex; min-height: 105px; margin-bottom: 18px; padding: 18px; border: 1px solid #efcfda; border-radius: 13px; flex-direction: column; justify-content: center; color: #743e54; background: #fff1f5; }.all-customer-warning span { margin-top: 7px; color: #8f6978; font-size: 12px; line-height: 1.6; }
.grant-modal > .primary-button { width: 100%; margin-top: 7px; }
@media (max-width: 1100px) { .customer-row { grid-template-columns: 1fr 1fr 1.4fr 1fr; }.customer-row > :nth-child(5), .customer-row > :nth-child(6) { display: none; }.coupon-layout { grid-template-columns: 1fr; }.coupon-row { grid-template-columns: 95px minmax(0,1fr) 70px; }.coupon-actions { grid-column: 2 / -1; flex-direction: row; }.customer-head { display: none; } }
</style>
