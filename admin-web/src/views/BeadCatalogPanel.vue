<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ApiError, resolveApiResourceUrl } from '../api/http'
import { generateBeadSku } from '../catalog/generate-sku'
import {
  changeBeadStatus,
  createAdminBead,
  downloadBeadImportTemplate,
  importAdminBeads,
  listAdminBeads,
  listAdminCategories,
  updateAdminBead,
  uploadCatalogFile,
  type BeadSku,
  type CatalogImportResult,
  type CatalogStatus,
  type Category,
  type CreateBeadSkuRequest,
  type SaveBeadSkuRequest,
} from '../api/catalog'

const categories = ref<Category[]>([])
const beads = ref<BeadSku[]>([])
const total = ref(0)
const loading = ref(false)
const saving = ref(false)
const coverUploading = ref(false)
const coverDropActive = ref(false)
const errorMessage = ref('')
const successMessage = ref('')
const showCreateForm = ref(false)
const showImportDialog = ref(false)
const importFile = ref<File | null>(null)
const importing = ref(false)
const importDropActive = ref(false)
const importErrorMessage = ref('')
const importResult = ref<CatalogImportResult | null>(null)
const editingBeadId = ref<number | null>(null)
const enlargedCoverUrl = ref('')
const MAX_COVER_SIZE_BYTES = 5_000_000
const MAX_IMPORT_SIZE_BYTES = 10_000_000
const ACCEPTED_COVER_TYPES = new Set([
  'image/png',
  'image/jpeg',
  'image/webp',
])
let latestCatalogRequest = 0

const filters = reactive({
  keyword: '',
  status: '' as CatalogStatus | '',
  categoryId: '' as number | '',
  attribute: '',
  page: 1,
  size: 20,
})

const form = reactive<CreateBeadSkuRequest>({
  categoryId: 0,
  skuCode: '',
  name: '',
  material: '',
  color: '',
  diameterMm: 8,
  salePrice: 0,
  costPrice: 0,
  stockAlertThreshold: 10,
  initialStock: 200,
  coverAssetId: null,
  tags: [],
  status: 'DRAFT',
})
const tagInput = ref('')
const coverPreviewUrl = ref('')

const categoryNames = computed(() =>
  new Map(categories.value.map((category) => [category.id, category.name])),
)
const totalPages = computed(() =>
  Math.max(1, Math.ceil(total.value / filters.size)),
)

function statusLabel(status: CatalogStatus): string {
  return {
    DRAFT: '草稿',
    ON_SALE: '已上架',
    OFF_SALE: '已下架',
  }[status]
}

function parseTags(tagsJson: string): string[] {
  try {
    const tags = JSON.parse(tagsJson) as unknown
    return Array.isArray(tags) ? tags.map(String) : []
  } catch {
    return []
  }
}

function resolvedCoverUrl(bead: BeadSku): string {
  return bead.coverUrl ? resolveApiResourceUrl(bead.coverUrl) : ''
}

function readableError(error: unknown): string {
  if (error instanceof ApiError) {
    return error.traceId
      ? `${error.message}（追踪号：${error.traceId}）`
      : error.message
  }
  return '请求失败，请确认后端服务已启动'
}

async function loadCatalog(): Promise<void> {
  const requestId = ++latestCatalogRequest
  loading.value = true
  errorMessage.value = ''
  try {
    const [categoryResult, beadResult] = await Promise.all([
      listAdminCategories(),
      listAdminBeads(filters),
    ])
    if (requestId !== latestCatalogRequest) return
    categories.value = categoryResult
    beads.value = beadResult.items
    total.value = beadResult.total
    if (!form.categoryId && categoryResult.length > 0) {
      form.categoryId = categoryResult[0].id
    }
  } catch (error) {
    if (requestId !== latestCatalogRequest) return
    errorMessage.value = readableError(error)
  } finally {
    if (requestId === latestCatalogRequest) {
      loading.value = false
    }
  }
}

function search(): void {
  filters.page = 1
  void loadCatalog()
}

function clearSearch(): void {
  filters.keyword = ''
  search()
}

function generateSku(): void {
  const category = categories.value.find(
    (item) => item.id === Number(form.categoryId),
  )
  form.skuCode = generateBeadSku(category?.slug || 'general', form.diameterMm)
}

function resetForm(): void {
  Object.assign(form, {
    categoryId: categories.value[0]?.id || 0,
    skuCode: '',
    name: '',
    material: '',
    color: '',
    diameterMm: 8,
    salePrice: 0,
    costPrice: 0,
    stockAlertThreshold: 10,
    initialStock: 200,
    coverAssetId: null,
    tags: [],
    status: 'DRAFT',
  })
  tagInput.value = ''
  coverPreviewUrl.value = ''
}

/**
 * 新建和编辑共用同一份表单，打开前始终完整覆盖字段，避免上一次操作残留数据。
 */
function openCreateForm(): void {
  editingBeadId.value = null
  resetForm()
  generateSku()
  showCreateForm.value = true
}

function openEditForm(bead: BeadSku): void {
  editingBeadId.value = bead.id
  Object.assign(form, {
    categoryId: bead.categoryId,
    skuCode: bead.skuCode,
    name: bead.name,
    material: bead.material,
    color: bead.color,
    diameterMm: Number(bead.diameterMm),
    salePrice: Number(bead.salePrice),
    costPrice: Number(bead.costPrice),
    stockAlertThreshold: bead.stockAlertThreshold,
    coverAssetId: bead.coverAssetId,
    tags: parseTags(bead.tagsJson),
    status: bead.status,
  })
  tagInput.value = parseTags(bead.tagsJson).join('，')
  coverPreviewUrl.value = bead.coverUrl
    ? resolveApiResourceUrl(bead.coverUrl)
    : ''
  showCreateForm.value = true
}

async function uploadCoverFile(file: File): Promise<void> {
  if (!ACCEPTED_COVER_TYPES.has(file.type)) {
    errorMessage.value = '封面图片仅支持 PNG、JPEG 或 WebP'
    return
  }
  if (file.size > MAX_COVER_SIZE_BYTES) {
    errorMessage.value = '封面图片不能超过 5 MB'
    return
  }
  coverUploading.value = true
  errorMessage.value = ''
  try {
    const uploaded = await uploadCatalogFile(file)
    form.coverAssetId = uploaded.id
    coverPreviewUrl.value = resolveApiResourceUrl(uploaded.publicUrl)
  } catch (error) {
    errorMessage.value = readableError(error)
  } finally {
    coverUploading.value = false
  }
}

async function uploadCover(event: Event): Promise<void> {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  if (file) {
    await uploadCoverFile(file)
  }
  input.value = ''
}

function handleCoverDrop(event: DragEvent): void {
  coverDropActive.value = false
  const file = event.dataTransfer?.files?.[0]
  if (file) {
    void uploadCoverFile(file)
  }
}

function openImportDialog(): void {
  importFile.value = null
  importResult.value = null
  importErrorMessage.value = ''
  importDropActive.value = false
  showImportDialog.value = true
}

function selectImportFile(file: File | undefined): void {
  importResult.value = null
  importErrorMessage.value = ''
  if (!file) return
  if (!file.name.toLowerCase().endsWith('.xlsx')) {
    importFile.value = null
    importErrorMessage.value = '仅支持 .xlsx 格式的 Excel 文件'
    return
  }
  if (file.size > MAX_IMPORT_SIZE_BYTES) {
    importFile.value = null
    importErrorMessage.value = 'Excel 文件不能超过 10 MB'
    return
  }
  importFile.value = file
}

function handleImportFileChange(event: Event): void {
  const input = event.target as HTMLInputElement
  selectImportFile(input.files?.[0])
  input.value = ''
}

function handleImportDrop(event: DragEvent): void {
  importDropActive.value = false
  selectImportFile(event.dataTransfer?.files?.[0])
}

async function downloadImportTemplate(): Promise<void> {
  importErrorMessage.value = ''
  try {
    const blob = await downloadBeadImportTemplate()
    const url = URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = url
    link.download = '商品批量导入模板.xlsx'
    link.click()
    URL.revokeObjectURL(url)
  } catch (error) {
    importErrorMessage.value = readableError(error)
  }
}

async function submitImport(): Promise<void> {
  if (!importFile.value) {
    importErrorMessage.value = '请先选择需要导入的 Excel 文件'
    return
  }
  importing.value = true
  importErrorMessage.value = ''
  importResult.value = null
  try {
    importResult.value = await importAdminBeads(importFile.value)
    if (importResult.value.successCount > 0) {
      successMessage.value = `已成功导入 ${importResult.value.successCount} 个商品`
      await loadCatalog()
    }
  } catch (error) {
    importErrorMessage.value = readableError(error)
  } finally {
    importing.value = false
  }
}

async function submitBead(): Promise<void> {
  saving.value = true
  errorMessage.value = ''
  successMessage.value = ''
  try {
    const { initialStock, ...bead } = form
    const payload: SaveBeadSkuRequest = {
      ...bead,
      tags: tagInput.value
        .split(/[,，]/)
        .map((tag) => tag.trim())
        .filter(Boolean),
    }
    if (editingBeadId.value) {
      await updateAdminBead(editingBeadId.value, payload)
      successMessage.value = '散珠商品已更新'
    } else {
      await createAdminBead({ ...payload, initialStock })
      successMessage.value = '散珠商品已创建'
    }
    showCreateForm.value = false
    resetForm()
    await loadCatalog()
  } catch (error) {
    errorMessage.value = readableError(error)
  } finally {
    saving.value = false
  }
}

async function toggleStatus(bead: BeadSku): Promise<void> {
  const nextStatus: CatalogStatus =
    bead.status === 'ON_SALE' ? 'OFF_SALE' : 'ON_SALE'
  errorMessage.value = ''
  try {
    await changeBeadStatus(bead.id, nextStatus)
    await loadCatalog()
  } catch (error) {
    errorMessage.value = readableError(error)
  }
}

function changePage(page: number): void {
  filters.page = Math.min(Math.max(page, 1), totalPages.value)
  void loadCatalog()
}

onMounted(loadCatalog)
</script>

<template>
  <section class="catalog-view">
    <header class="content-header">
      <div>
        <p class="section-kicker">CATALOG / BEADS</p>
        <h1>散珠商品</h1>
        <p class="muted">维护 DIY 设计器和成品手串共用的基础珠子。</p>
      </div>
      <div class="content-header-actions">
        <button
          class="secondary-button"
          data-test="import-beads"
          @click="openImportDialog"
        >
          Excel 批量导入
        </button>
        <button
          class="primary-button"
          data-test="create-bead"
          @click="openCreateForm"
        >
          ＋ 新增散珠
        </button>
      </div>
    </header>

    <div v-if="errorMessage" class="notice error-notice" role="alert">
      {{ errorMessage }}
    </div>
    <div v-if="successMessage" class="notice success-notice">
      {{ successMessage }}
    </div>

    <section class="toolbar" aria-label="商品筛选">
      <div class="search-field search-field-with-clear">
        <span class="sr-only">搜索商品</span>
        <input
          v-model="filters.keyword"
          aria-label="搜索商品"
          placeholder="搜索 SKU、名称或材质"
          @keyup.enter="search"
        />
        <button
          v-if="filters.keyword"
          class="search-clear-button"
          data-test="clear-bead-search"
          type="button"
          aria-label="清空搜索"
          @click="clearSearch"
        >×</button>
      </div>
      <label>
        <span class="sr-only">商品分类</span>
        <select
          v-model.number="filters.categoryId"
          aria-label="商品分类"
          @change="search"
        >
          <option value="">全部分类</option>
          <option
            v-for="category in categories"
            :key="category.id"
            :value="category.id"
          >
            {{ category.name }}
          </option>
        </select>
      </label>
      <label class="attribute-filter">
        <span class="sr-only">商品属性</span>
        <input
          v-model="filters.attribute"
          aria-label="商品属性"
          placeholder="材质 / 颜色 / 珠径 / 标签"
          @keyup.enter="search"
        />
      </label>
      <label>
        <span class="sr-only">商品状态</span>
        <select
          v-model="filters.status"
          aria-label="商品状态"
          @change="search"
        >
          <option value="">全部状态</option>
          <option value="DRAFT">草稿</option>
          <option value="ON_SALE">已上架</option>
          <option value="OFF_SALE">已下架</option>
        </select>
      </label>
      <button class="secondary-button" @click="search">查询</button>
      <span class="result-count">共 {{ total }} 个商品</span>
    </section>

    <section class="table-card">
      <div v-if="loading" class="empty-state">正在读取商品目录…</div>
      <div v-else-if="beads.length === 0" class="empty-state">
        <strong>还没有符合条件的散珠</strong>
        <span>可以调整筛选条件，或创建第一个商品。</span>
      </div>
      <div v-else class="table-scroll">
        <table>
          <thead>
            <tr>
              <th>商品</th>
              <th>分类 / 属性</th>
              <th>销售价</th>
              <th>成本价</th>
              <th>状态</th>
              <th class="align-right">操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="bead in beads" :key="bead.id">
              <td>
                <div class="product-cell">
                  <button
                    v-if="bead.coverUrl"
                    class="bead-cover-button"
                    type="button"
                    :aria-label="`放大预览 ${bead.name} 封面`"
                    :data-test="`preview-bead-cover-${bead.id}`"
                    @click="enlargedCoverUrl = resolvedCoverUrl(bead)"
                  >
                    <img
                      :src="resolvedCoverUrl(bead)"
                      :alt="`${bead.name} 封面`"
                    />
                  </button>
                  <div v-else class="bead-preview" :style="{ '--bead-color': bead.color }">
                    <span />
                  </div>
                  <div>
                    <strong>{{ bead.name }}</strong>
                    <code>{{ bead.skuCode }}</code>
                    <div class="tag-list">
                      <span
                        v-for="tag in parseTags(bead.tagsJson)"
                        :key="tag"
                      >
                        {{ tag }}
                      </span>
                    </div>
                  </div>
                </div>
              </td>
              <td>
                <strong class="subtle-strong">
                  {{ categoryNames.get(bead.categoryId) || '未分类' }}
                </strong>
                <span class="cell-detail">
                  {{ bead.material }} · {{ bead.color }} ·
                  {{ bead.diameterMm }}mm
                </span>
              </td>
              <td class="money">¥ {{ Number(bead.salePrice).toFixed(2) }}</td>
              <td class="money muted">¥ {{ Number(bead.costPrice).toFixed(2) }}</td>
              <td>
                <span class="status-pill" :class="`status-${bead.status.toLowerCase()}`">
                  {{ statusLabel(bead.status) }}
                </span>
              </td>
              <td class="align-right">
                <button
                  class="text-button"
                  :data-test="`edit-bead-${bead.id}`"
                  @click="openEditForm(bead)"
                >
                  编辑
                </button>
                <button class="text-button" @click="toggleStatus(bead)">
                  {{ bead.status === 'ON_SALE' ? '下架' : '上架' }}
                </button>
              </td>
            </tr>
          </tbody>
        </table>
      </div>

      <footer class="pagination">
        <button
          class="page-button"
          :disabled="filters.page <= 1"
          @click="changePage(filters.page - 1)"
        >
          上一页
        </button>
        <span>第 {{ filters.page }} / {{ totalPages }} 页</span>
        <button
          class="page-button"
          :disabled="filters.page >= totalPages"
          @click="changePage(filters.page + 1)"
        >
          下一页
        </button>
      </footer>
    </section>

    <div
      v-if="showImportDialog"
      class="modal-backdrop centered-modal-backdrop"
      @click.self="!importing && (showImportDialog = false)"
    >
      <section
        class="drawer import-drawer"
        role="dialog"
        aria-modal="true"
        aria-labelledby="import-products-title"
      >
        <header>
          <div>
            <p class="section-kicker">CATALOG / EXCEL IMPORT</p>
            <h2 id="import-products-title">Excel 批量导入商品</h2>
          </div>
          <button
            class="icon-button"
            type="button"
            aria-label="关闭批量导入"
            :disabled="importing"
            @click="showImportDialog = false"
          >×</button>
        </header>

        <div class="import-guide">
          <div>
            <strong>先下载模板，再填写商品数据</strong>
            <span>图片无需写入 Excel，导入成功后可在商品编辑页补充。</span>
          </div>
          <button
            class="secondary-button"
            data-test="download-import-template"
            type="button"
            @click="downloadImportTemplate"
          >
            下载 Excel 模板
          </button>
        </div>

        <label
          class="file-dropzone import-dropzone"
          :class="{ 'is-dragging': importDropActive }"
          data-test="import-dropzone"
          @dragenter.prevent="importDropActive = true"
          @dragover.prevent="importDropActive = true"
          @dragleave.prevent="importDropActive = false"
          @drop.prevent="handleImportDrop"
        >
          <input
            type="file"
            accept=".xlsx,application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
            @change="handleImportFileChange"
          />
          <strong>{{ importFile ? importFile.name : '点击选择或拖拽 Excel 到这里' }}</strong>
          <span>仅支持 .xlsx，最大 10 MB，单次最多 1000 行</span>
        </label>

        <div v-if="importErrorMessage" class="notice error-notice" role="alert">
          {{ importErrorMessage }}
        </div>

        <section
          v-if="importResult"
          class="import-result"
          aria-label="批量导入结果"
        >
          <strong>
            共读取 {{ importResult.totalRows }} 行，成功 {{ importResult.successCount }} 行，
            失败 {{ importResult.failureCount }} 行
          </strong>
          <ul v-if="importResult.errors.length" class="import-error-list">
            <li v-for="item in importResult.errors" :key="`${item.rowNumber}-${item.skuCode}`">
              第 {{ item.rowNumber }} 行
              <template v-if="item.skuCode">（{{ item.skuCode }}）</template>：
              {{ item.message }}
            </li>
          </ul>
        </section>

        <footer>
          <button
            type="button"
            class="secondary-button"
            :disabled="importing"
            @click="showImportDialog = false"
          >
            关闭
          </button>
          <button
            type="button"
            class="primary-button"
            :disabled="importing || !importFile"
            data-test="submit-import"
            @click="submitImport"
          >
            {{ importing ? '正在导入…' : '开始导入' }}
          </button>
        </footer>
      </section>
    </div>

    <div v-if="showCreateForm" class="modal-backdrop" @click.self="showCreateForm = false">
      <section class="drawer" aria-labelledby="create-bead-title">
        <header>
          <div>
            <p class="section-kicker">
              {{ editingBeadId ? 'EDIT BEAD SKU' : 'NEW BEAD SKU' }}
            </p>
            <h2 id="create-bead-title">
              {{ editingBeadId ? '编辑散珠商品' : '新增散珠商品' }}
            </h2>
          </div>
          <button class="icon-button" aria-label="关闭" @click="showCreateForm = false">
            ×
          </button>
        </header>

        <form class="catalog-form" @submit.prevent="submitBead">
          <div class="form-grid">
            <label>
              <span>商品名称</span>
              <input v-model="form.name" required maxlength="120" />
            </label>
            <label>
              <span>SKU 编码</span>
              <span class="input-with-action">
                <input
                  v-model="form.skuCode"
                  data-test="bead-sku-code"
                  required
                  maxlength="64"
                  placeholder="BEAD-CRYSTAL-001"
                />
                <button
                  class="field-action-button"
                  data-test="generate-bead-sku"
                  type="button"
                  @click="generateSku"
                >重新生成</button>
              </span>
            </label>
            <label>
              <span>所属分类</span>
              <select v-model.number="form.categoryId" required>
                <option
                  v-for="category in categories"
                  :key="category.id"
                  :value="category.id"
                >
                  {{ category.name }}
                </option>
              </select>
            </label>
            <label>
              <span>初始状态</span>
              <select v-model="form.status">
                <option value="DRAFT">草稿</option>
                <option value="ON_SALE">直接上架</option>
                <option value="OFF_SALE">已下架</option>
              </select>
            </label>
            <label>
              <span>材质</span>
              <input v-model="form.material" required maxlength="80" />
            </label>
            <label>
              <span>颜色</span>
              <input v-model="form.color" required maxlength="80" />
            </label>
            <label>
              <span>珠径（mm）</span>
              <input v-model.number="form.diameterMm" type="number" min="0.01" step="0.01" required />
            </label>
            <label>
              <span>库存预警</span>
              <input v-model.number="form.stockAlertThreshold" type="number" min="0" required />
            </label>
            <label v-if="!editingBeadId">
              <span>初始库存</span>
              <input
                v-model.number="form.initialStock"
                data-test="initial-stock"
                type="number"
                min="0"
                step="1"
                required
              />
            </label>
            <label>
              <span>销售价（元）</span>
              <input v-model.number="form.salePrice" type="number" min="0" step="0.01" required />
            </label>
            <label>
              <span>成本价（元）</span>
              <input v-model.number="form.costPrice" type="number" min="0" step="0.01" required />
            </label>
          </div>
          <label>
            <span>标签</span>
            <input
              v-model="tagInput"
              placeholder="净化，日常（使用逗号分隔）"
            />
          </label>
          <label
            class="cover-upload file-dropzone cover-dropzone"
            :class="{ 'is-dragging': coverDropActive }"
            data-test="cover-dropzone"
            @dragenter.prevent="coverDropActive = true"
            @dragover.prevent="coverDropActive = true"
            @dragleave.prevent="coverDropActive = false"
            @drop.prevent="handleCoverDrop"
          >
            <span>封面图片（PNG / JPEG / WebP，最大 5 MB）</span>
            <input
              type="file"
              accept="image/png,image/jpeg,image/webp"
              @change="uploadCover"
            />
            <small v-if="!coverUploading && !form.coverAssetId">
              点击选择或将图片拖拽到这里上传
            </small>
            <small v-if="coverUploading">正在上传封面…</small>
            <small v-else-if="form.coverAssetId">
              素材 #{{ form.coverAssetId }} 已上传
            </small>
            <img
              v-if="coverPreviewUrl"
              :src="coverPreviewUrl"
              alt="新商品封面预览"
            />
          </label>
          <footer>
            <button type="button" class="secondary-button" @click="showCreateForm = false">
              取消
            </button>
            <button
              class="primary-button"
              :disabled="saving || coverUploading"
            >
              {{
                saving
                  ? '正在保存…'
                  : editingBeadId
                    ? '保存修改'
                    : '创建商品'
              }}
            </button>
          </footer>
        </form>
      </section>
    </div>

    <div
      v-if="enlargedCoverUrl"
      class="image-preview-backdrop"
      role="presentation"
      @click.self="enlargedCoverUrl = ''"
    >
      <section
        class="image-preview-dialog"
        role="dialog"
        aria-modal="true"
        aria-label="商品封面大图预览"
      >
        <button
          class="image-preview-close"
          type="button"
          aria-label="关闭大图预览"
          @click="enlargedCoverUrl = ''"
        >×</button>
        <img :src="enlargedCoverUrl" alt="商品封面大图" />
      </section>
    </div>
  </section>
</template>
