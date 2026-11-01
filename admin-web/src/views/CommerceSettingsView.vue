<script setup lang="ts">
// 读取与编辑交易规则，保存成功后使用服务端返回的新版本。
import { onMounted, reactive, ref } from 'vue'
import { ApiError } from '../api/http'
import {
  getCommerceRule,
  saveCommerceRule,
  type CommerceRule,
} from '../api/commerce'

const loading = ref(false)
const saving = ref(false)
const errorMessage = ref('')
const successMessage = ref('')
const rule = reactive<CommerceRule>({
  minimumOrderAmount: 20,
  freeShippingThreshold: 60,
  baseShippingFee: 8,
  configVersion: 0,
})

function readableError(error: unknown): string {
  if (error instanceof ApiError) {
    return error.traceId ? `${error.message}（追踪号：${error.traceId}）` : error.message
  }
  return error instanceof Error ? error.message : '交易规则操作失败'
}

function amount(value: number, label: string): number {
  const parsed = Number(value)
  if (!Number.isFinite(parsed) || parsed < 0) {
    throw new Error(`${label}必须是不小于 0 的金额`)
  }
  return Math.round(parsed * 100) / 100
}

async function load(): Promise<void> {
  loading.value = true
  errorMessage.value = ''
  try {
    Object.assign(rule, await getCommerceRule())
  } catch (error) {
    errorMessage.value = readableError(error)
  } finally {
    loading.value = false
  }
}

async function save(): Promise<void> {
  if (saving.value) return
  saving.value = true
  errorMessage.value = ''
  successMessage.value = ''
  try {
    Object.assign(rule, await saveCommerceRule({
      minimumOrderAmount: amount(rule.minimumOrderAmount, '最低下单金额'),
      freeShippingThreshold: amount(rule.freeShippingThreshold, '包邮门槛'),
      baseShippingFee: amount(rule.baseShippingFee, '基础运费'),
    }))
    successMessage.value = '交易规则已保存，小程序下次读取时立即生效'
  } catch (error) {
    errorMessage.value = readableError(error)
  } finally {
    saving.value = false
  }
}

onMounted(load)
</script>

<template>
  <section class="commerce-workspace">
    <header class="content-header">
      <div>
        <p class="section-kicker">COMMERCE / SETTINGS</p>
        <h1>交易规则</h1>
        <p class="muted">统一设置最低下单金额、包邮门槛和未满包邮门槛时的基础运费。</p>
      </div>
      <span class="version-pill">v{{ rule.configVersion }}</span>
    </header>

    <div v-if="errorMessage" class="notice error-notice" role="alert">{{ errorMessage }}</div>
    <div v-if="successMessage" class="notice success-notice">{{ successMessage }}</div>
    <div v-if="loading" class="empty-state">正在读取交易规则…</div>

    <section v-else class="commerce-card">
      <header>
        <div>
          <p class="section-kicker">ORDER &amp; SHIPPING</p>
          <h2>下单与配送</h2>
        </div>
      </header>
      <div class="rule-grid">
        <label>
          <span>最低下单金额（元）</span>
          <input v-model.number="rule.minimumOrderAmount" data-test="minimum-order-amount" type="number" min="0" step="0.01" />
          <small>商品金额未达到此数值时，所有“去下单”入口都会提示并阻止继续。</small>
        </label>
        <label>
          <span>包邮门槛（元）</span>
          <input v-model.number="rule.freeShippingThreshold" data-test="free-shipping-threshold" type="number" min="0" step="0.01" />
          <small>优惠后商品金额达到门槛时，配送费自动减免为 ¥0.00。</small>
        </label>
        <label>
          <span>基础运费（元）</span>
          <input v-model.number="rule.baseShippingFee" data-test="base-shipping-fee" type="number" min="0" step="0.01" />
          <small>未达到包邮门槛时计入订单应付金额。</small>
        </label>
      </div>
      <div class="rule-example">
        当前规则：商品满 <strong>¥{{ Number(rule.minimumOrderAmount).toFixed(2) }}</strong> 可下单；优惠后商品满
        <strong>¥{{ Number(rule.freeShippingThreshold).toFixed(2) }}</strong> 包邮，否则运费
        <strong>¥{{ Number(rule.baseShippingFee).toFixed(2) }}</strong>。
      </div>
      <footer>
        <button class="primary-button" data-test="save-commerce-rule" :disabled="saving" @click="save">
          {{ saving ? '保存中…' : '保存交易规则' }}
        </button>
      </footer>
    </section>
  </section>
</template>

<style scoped>
.commerce-workspace { display: grid; gap: 18px; }.content-header { display: flex; justify-content: space-between; align-items: flex-start; }.content-header h1 { margin: 4px 0 8px; font-family: "Songti SC", "STSong", serif; font-size: 38px; }.version-pill { padding: 7px 12px; border-radius: 18px; color: #7d5263; font-size: 12px; background: #f3e6eb; }.commerce-card { overflow: hidden; border: 1px solid var(--line); border-radius: 16px; background: var(--paper); box-shadow: 0 12px 34px rgb(58 43 49 / 6%); }.commerce-card > header,.commerce-card > footer { display: flex; padding: 20px 24px; align-items: center; justify-content: space-between; }.commerce-card > header { border-bottom: 1px solid #eee5e2; }.commerce-card h2 { margin: 3px 0 0; font-size: 22px; }.rule-grid { display: grid; grid-template-columns: repeat(3, 1fr); gap: 18px; padding: 26px 24px; }.rule-grid label { display: grid; gap: 9px; }.rule-grid span { font-weight: 700; }.rule-grid small { color: var(--muted); line-height: 1.6; }.rule-example { margin: 0 24px; padding: 16px 18px; border-radius: 12px; color: #6b4a57; line-height: 1.7; background: #faf0f3; }.commerce-card > footer { justify-content: flex-end; }@media (max-width: 1000px) { .rule-grid { grid-template-columns: 1fr; } }
</style>
