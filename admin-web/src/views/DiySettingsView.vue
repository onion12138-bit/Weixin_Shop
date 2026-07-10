<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ApiError } from '../api/http'
import {
  createCordOption,
  getAdminDiyConfiguration,
  saveCraftFeeRules,
  saveDiyRule,
  updateCordOption,
  type CordOption,
  type CraftFeeRule,
  type DiyRule,
  type OptionStatus,
  type SaveCordOptionPayload,
  type SaveCraftFeeRulePayload,
  type SaveDiyRulePayload,
} from '../api/diy'

type CraftFeeDraft = SaveCraftFeeRulePayload & {
  id?: number
  ruleVersion?: number
}

const loading = ref(false)
const savingRule = ref(false)
const savingCraft = ref(false)
const savingCord = ref(false)
const errorMessage = ref('')
const successMessage = ref('')
const cordOptions = ref<CordOption[]>([])
const craftRules = ref<CraftFeeDraft[]>([])
const editingCordId = ref<number | null>(null)

const rule = reactive<DiyRule>({
  minWristMm: 120,
  maxWristMm: 250,
  snugAllowanceMm: 5,
  standardAllowanceMm: 10,
  looseAllowanceMm: 15,
  toleranceMm: 4,
  maxBeadCount: 60,
  configVersion: 0,
})

const cordForm = reactive<SaveCordOptionPayload>({
  code: '',
  name: '',
  colorHex: '#E8E4DC',
  extraFee: 0,
  status: 'ENABLED',
  sortOrder: 10,
})

function readableError(error: unknown): string {
  if (error instanceof ApiError) {
    return error.traceId
      ? `${error.message}（追踪号：${error.traceId}）`
      : error.message
  }
  if (error instanceof Error) {
    return error.message
  }
  return 'DIY 配置操作失败，请确认后端服务已启动'
}

function nonNegativeNumber(value: string): number {
  const parsed = Number(value)
  if (!Number.isFinite(parsed) || parsed < 0) {
    throw new Error('请输入不小于 0 的数字')
  }
  return parsed
}

function positiveInteger(value: number, label: string): number {
  const parsed = Number(value)
  if (!Number.isInteger(parsed) || parsed < 1) {
    throw new Error(`${label}必须是不小于 1 的整数`)
  }
  return parsed
}

function applyConfiguration(config: {
  rule: DiyRule
  craftFeeRules: CraftFeeRule[]
  cordOptions: CordOption[]
}): void {
  Object.assign(rule, config.rule)
  craftRules.value = config.craftFeeRules.map((item) => ({
    id: item.id,
    minBeadCount: item.minBeadCount,
    maxBeadCount: item.maxBeadCount,
    fee: Number(item.fee),
    ruleVersion: item.ruleVersion,
  }))
  cordOptions.value = config.cordOptions
}

async function loadConfiguration(): Promise<void> {
  loading.value = true
  errorMessage.value = ''
  try {
    applyConfiguration(await getAdminDiyConfiguration())
  } catch (error) {
    errorMessage.value = readableError(error)
  } finally {
    loading.value = false
  }
}

function clearMessages(): void {
  errorMessage.value = ''
  successMessage.value = ''
}

function rulePayload(): SaveDiyRulePayload {
  return {
    minWristMm: nonNegativeNumber(String(rule.minWristMm)),
    maxWristMm: nonNegativeNumber(String(rule.maxWristMm)),
    snugAllowanceMm: nonNegativeNumber(String(rule.snugAllowanceMm)),
    standardAllowanceMm: nonNegativeNumber(String(rule.standardAllowanceMm)),
    looseAllowanceMm: nonNegativeNumber(String(rule.looseAllowanceMm)),
    toleranceMm: nonNegativeNumber(String(rule.toleranceMm)),
    maxBeadCount: positiveInteger(rule.maxBeadCount, '最大珠数'),
  }
}

async function saveRuleConfig(): Promise<void> {
  savingRule.value = true
  clearMessages()
  try {
    await saveDiyRule(rulePayload())
    successMessage.value = '尺寸规则已保存'
    await loadConfiguration()
  } catch (error) {
    errorMessage.value = readableError(error)
  } finally {
    savingRule.value = false
  }
}

function addCraftRule(): void {
  craftRules.value.push({
    minBeadCount: 1,
    maxBeadCount: rule.maxBeadCount,
    fee: 0,
  })
}

function removeCraftRule(index: number): void {
  if (craftRules.value.length <= 1) return
  craftRules.value.splice(index, 1)
}

function sortedCraftRules(): SaveCraftFeeRulePayload[] {
  return craftRules.value
    .map((item) => ({
      minBeadCount: positiveInteger(item.minBeadCount, '起始珠数'),
      maxBeadCount: positiveInteger(item.maxBeadCount, '结束珠数'),
      fee: nonNegativeNumber(String(item.fee)),
    }))
    .sort((left, right) => left.minBeadCount - right.minBeadCount)
}

function validateCraftRules(rules: SaveCraftFeeRulePayload[]): void {
  if (rules.length === 0) {
    throw new Error('工艺费区间不能重叠或留空档')
  }
  let expectedMin = 1
  for (const item of rules) {
    if (
      item.minBeadCount !== expectedMin ||
      item.maxBeadCount < item.minBeadCount
    ) {
      throw new Error('工艺费区间不能重叠或留空档')
    }
    expectedMin = item.maxBeadCount + 1
  }
  if (expectedMin - 1 !== Number(rule.maxBeadCount)) {
    throw new Error('工艺费区间不能重叠或留空档')
  }
}

async function saveCraft(): Promise<void> {
  savingCraft.value = true
  clearMessages()
  try {
    const payload = sortedCraftRules()
    validateCraftRules(payload)
    await saveCraftFeeRules(payload)
    successMessage.value = '工艺费规则已保存'
    await loadConfiguration()
  } catch (error) {
    errorMessage.value = readableError(error)
  } finally {
    savingCraft.value = false
  }
}

function resetCordForm(): void {
  editingCordId.value = null
  Object.assign(cordForm, {
    code: '',
    name: '',
    colorHex: '#E8E4DC',
    extraFee: 0,
    status: 'ENABLED' as OptionStatus,
    sortOrder: (cordOptions.value.length + 1) * 10,
  })
}

function editCord(item: CordOption): void {
  editingCordId.value = item.id
  Object.assign(cordForm, {
    code: item.code,
    name: item.name,
    colorHex: item.colorHex,
    extraFee: Number(item.extraFee),
    status: item.status,
    sortOrder: item.sortOrder,
  })
}

function cordPayload(): SaveCordOptionPayload {
  return {
    code: cordForm.code.trim().toUpperCase(),
    name: cordForm.name.trim(),
    colorHex: cordForm.colorHex,
    extraFee: nonNegativeNumber(String(cordForm.extraFee)),
    status: cordForm.status,
    sortOrder: Number(cordForm.sortOrder),
  }
}

async function saveCord(): Promise<void> {
  savingCord.value = true
  clearMessages()
  try {
    if (editingCordId.value) {
      await updateCordOption(editingCordId.value, cordPayload())
      successMessage.value = '绳材选项已更新'
    } else {
      await createCordOption(cordPayload())
      successMessage.value = '绳材选项已创建'
    }
    resetCordForm()
    await loadConfiguration()
  } catch (error) {
    errorMessage.value = readableError(error)
  } finally {
    savingCord.value = false
  }
}

onMounted(loadConfiguration)
</script>

<template>
  <section class="settings-workspace">
    <header class="content-header">
      <div>
        <p class="section-kicker">DIY / SETTINGS</p>
        <h1>DIY 定制规则</h1>
        <p class="muted">
          维护腕围范围、松紧余量、工艺费区间和绳材选项，小程序报价会实时使用这些配置。
        </p>
      </div>
    </header>

    <div v-if="errorMessage" class="notice error-notice" role="alert">
      {{ errorMessage }}
    </div>
    <div v-if="successMessage" class="notice success-notice">
      {{ successMessage }}
    </div>

    <div v-if="loading" class="empty-state">正在读取 DIY 配置…</div>

    <section class="settings-card">
      <header>
        <div>
          <p class="section-kicker">SIZING</p>
          <h2>尺寸与松紧规则</h2>
        </div>
        <span class="version-pill">v{{ rule.configVersion }}</span>
      </header>

      <div class="form-grid three-columns">
        <label>
          <span>最小腕围 mm</span>
          <input v-model.number="rule.minWristMm" type="number" min="0" />
        </label>
        <label>
          <span>最大腕围 mm</span>
          <input v-model.number="rule.maxWristMm" type="number" min="0" />
        </label>
        <label>
          <span>最大珠数</span>
          <input
            v-model.number="rule.maxBeadCount"
            data-test="max-bead-count"
            type="number"
            min="1"
            max="200"
          />
        </label>
        <label>
          <span>贴合余量 mm</span>
          <input v-model.number="rule.snugAllowanceMm" type="number" min="0" />
        </label>
        <label>
          <span>标准余量 mm</span>
          <input
            v-model.number="rule.standardAllowanceMm"
            type="number"
            min="0"
          />
        </label>
        <label>
          <span>宽松余量 mm</span>
          <input v-model.number="rule.looseAllowanceMm" type="number" min="0" />
        </label>
        <label>
          <span>允许误差 mm</span>
          <input v-model.number="rule.toleranceMm" type="number" min="0" />
        </label>
      </div>

      <footer>
        <button
          class="primary-button"
          data-test="save-diy-rule"
          :disabled="savingRule"
          @click="saveRuleConfig"
        >
          {{ savingRule ? '保存中…' : '保存尺寸规则' }}
        </button>
      </footer>
    </section>

    <section class="settings-card">
      <header>
        <div>
          <p class="section-kicker">CRAFT FEE</p>
          <h2>工艺费区间</h2>
        </div>
        <button
          class="secondary-button"
          data-test="add-craft-rule"
          @click="addCraftRule"
        >
          ＋ 新增区间
        </button>
      </header>

      <div class="editable-table">
        <div class="editable-row table-head">
          <span>起始珠数</span>
          <span>结束珠数</span>
          <span>工艺费</span>
          <span>操作</span>
        </div>
        <div
          v-for="(item, index) in craftRules"
          :key="item.id || index"
          class="editable-row"
        >
          <input v-model.number="item.minBeadCount" type="number" min="1" />
          <input v-model.number="item.maxBeadCount" type="number" min="1" />
          <input v-model.number="item.fee" type="number" min="0" step="0.01" />
          <button class="text-button" @click="removeCraftRule(index)">
            删除
          </button>
        </div>
      </div>

      <footer>
        <button
          class="primary-button"
          data-test="save-craft-rules"
          :disabled="savingCraft"
          @click="saveCraft"
        >
          {{ savingCraft ? '保存中…' : '保存工艺费' }}
        </button>
      </footer>
    </section>

    <section class="settings-card">
      <header>
        <div>
          <p class="section-kicker">CORD OPTIONS</p>
          <h2>绳材选项</h2>
        </div>
      </header>

      <div class="cord-layout">
        <form class="catalog-form" @submit.prevent="saveCord">
          <div class="form-grid">
            <label>
              <span>编码</span>
              <input
                v-model="cordForm.code"
                required
                maxlength="64"
                placeholder="ELASTIC-CLEAR"
              />
            </label>
            <label>
              <span>名称</span>
              <input v-model="cordForm.name" required maxlength="80" />
            </label>
            <label>
              <span>颜色</span>
              <input v-model="cordForm.colorHex" type="color" />
            </label>
            <label>
              <span>附加费</span>
              <input
                v-model.number="cordForm.extraFee"
                type="number"
                min="0"
                step="0.01"
              />
            </label>
            <label>
              <span>排序</span>
              <input v-model.number="cordForm.sortOrder" type="number" />
            </label>
            <label>
              <span>状态</span>
              <select v-model="cordForm.status">
                <option value="ENABLED">启用</option>
                <option value="DISABLED">停用</option>
              </select>
            </label>
          </div>
          <div class="form-actions">
            <button class="primary-button" :disabled="savingCord">
              {{ editingCordId ? '保存绳材' : '新增绳材' }}
            </button>
            <button type="button" class="secondary-button" @click="resetCordForm">
              清空
            </button>
          </div>
        </form>

        <div class="cord-list">
          <div
            v-for="item in cordOptions"
            :key="item.id"
            class="cord-option-card"
          >
            <span
              class="cord-color"
              :style="{ backgroundColor: item.colorHex }"
              aria-hidden="true"
            />
            <div>
              <strong>{{ item.name }}</strong>
              <small>{{ item.code }} · ¥{{ item.extraFee }}</small>
            </div>
            <span
              class="status-pill"
              :class="item.status === 'ENABLED' ? 'status-on_sale' : 'status-off_sale'"
            >
              {{ item.status === 'ENABLED' ? '启用' : '停用' }}
            </span>
            <button class="text-button" @click="editCord(item)">编辑</button>
          </div>
        </div>
      </div>
    </section>
  </section>
</template>
