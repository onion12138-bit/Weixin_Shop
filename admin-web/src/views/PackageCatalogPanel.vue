<script setup lang="ts">
import OverflowText from '../components/OverflowText.vue'
import { computed, onMounted, onUnmounted, reactive, ref } from 'vue'
import { ApiError, resolveApiResourceUrl } from '../api/http'
import ListPagination from '../components/ListPagination.vue'
import {
  changePackageRecommendation,
  changePackageStatus,
  createAdminPackage,
  getAdminPackage,
  listAdminBeads,
  listAdminPackages,
  updateAdminPackage,
  uploadCatalogFile,
  type BeadSku,
  type CatalogStatus,
  type ProductPackage,
  type SavePackageRequest,
} from '../api/catalog'

const packages = ref<ProductPackage[]>([])
const availableBeads = ref<BeadSku[]>([])
const total = ref(0)
const loading = ref(false)
const saving = ref(false)
const recommendingId = ref<number | null>(null)
const editorLoading = ref(false)
const coverUploading = ref(false)
const coverDropActive = ref(false)
const showForm = ref(false)
const editingId = ref<number | null>(null)
const errorMessage = ref('')
const toastMessage = ref('')
const coverPreviewUrl = ref('')
const MAX_COVER_SIZE_BYTES = 5_000_000
const ACCEPTED_COVER_TYPES = new Set([
  'image/png',
  'image/jpeg',
  'image/webp',
])
let toastTimer: ReturnType<typeof setTimeout> | null = null
let latestPackageRequest = 0
let latestEditorRequest = 0
let disposed = false
let latestCoverRequest = 0

const filters = reactive({
  keyword: '',
  status: '' as CatalogStatus | '',
  page: 1,
  size: 20,
})

const form = reactive<SavePackageRequest>({
  packageCode: '',
  name: '',
  description: '',
  salePrice: 0,
  coverAssetId: null,
  status: 'DRAFT',
  items: [],
})

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

function readableError(error: unknown): string {
  if (error instanceof ApiError) {
    return error.traceId
      ? `${error.message}（追踪号：${error.traceId}）`
      : error.message
  }
  return '套餐操作失败，请确认后端服务已启动'
}

function showToast(message: string): void {
  toastMessage.value = message
  if (toastTimer !== null) clearTimeout(toastTimer)
  toastTimer = setTimeout(() => {
    toastMessage.value = ''
    toastTimer = null
  }, 2600)
}

async function loadPackages(): Promise<void> {
  if (disposed) return
  const requestId = ++latestPackageRequest
  loading.value = true
  errorMessage.value = ''
  try {
    const result = await listAdminPackages(filters)
    if (requestId !== latestPackageRequest) return
    packages.value = result.items
    total.value = result.total
  } catch (error) {
    if (requestId !== latestPackageRequest) return
    errorMessage.value = readableError(error)
  } finally {
    if (requestId === latestPackageRequest) loading.value = false
  }
}

async function loadEditorBeads(): Promise<BeadSku[]> {
  const result = await listAdminBeads({ page: 1, size: 100 })
  return result.items
}

function emptyItem(): { beadSkuId: number; quantity: number } {
  return {
    beadSkuId: availableBeads.value[0]?.id || 0,
    quantity: 1,
  }
}

async function openCreate(): Promise<void> {
  if (disposed || saving.value) return
  invalidateCoverUpload()
  showForm.value = false
  const requestId = ++latestEditorRequest
  editingId.value = null
  editorLoading.value = true
  errorMessage.value = ''
  try {
    const beads = await loadEditorBeads()
    if (requestId !== latestEditorRequest) return
    availableBeads.value = beads
    Object.assign(form, {
      packageCode: '',
      name: '',
      description: '',
      salePrice: 0,
      coverAssetId: null,
      status: 'DRAFT',
      items: [emptyItem()],
    })
    coverPreviewUrl.value = ''
    showForm.value = true
  } catch (error) {
    if (requestId !== latestEditorRequest) return
    errorMessage.value = readableError(error)
  } finally {
    if (requestId === latestEditorRequest) editorLoading.value = false
  }
}

/**
 * 套餐详情与可选散珠并行加载；明细按照后端 sortOrder 返回顺序写入表单。
 * 提交时后端会在一个事务中整体替换明细，避免出现半旧半新的搭配。
 */
async function openEdit(item: ProductPackage): Promise<void> {
  if (disposed || saving.value) return
  invalidateCoverUpload()
  showForm.value = false
  const requestId = ++latestEditorRequest
  editingId.value = item.id
  editorLoading.value = true
  errorMessage.value = ''
  try {
    const [detail, beads] = await Promise.all([
      getAdminPackage(item.id),
      loadEditorBeads(),
    ])
    if (requestId !== latestEditorRequest) return
    availableBeads.value = beads
    Object.assign(form, {
      packageCode: detail.packageCode,
      name: detail.name,
      description: detail.description,
      salePrice: Number(detail.salePrice),
      coverAssetId: detail.coverAssetId,
      status: detail.status,
      items: detail.items.map((packageItem) => ({
        beadSkuId: packageItem.beadSkuId,
        quantity: packageItem.quantity,
      })),
    })
    coverPreviewUrl.value = detail.coverUrl
      ? resolveApiResourceUrl(detail.coverUrl)
      : ''
    showForm.value = true
  } catch (error) {
    if (requestId !== latestEditorRequest) return
    errorMessage.value = readableError(error)
  } finally {
    if (requestId === latestEditorRequest) editorLoading.value = false
  }
}

function addItem(): void {
  form.items.push(emptyItem())
}

function removeItem(index: number): void {
  if (form.items.length <= 1) return
  form.items.splice(index, 1)
}

function moveItem(index: number, offset: -1 | 1): void {
  const target = index + offset
  if (target < 0 || target >= form.items.length) return
  const [current] = form.items.splice(index, 1)
  form.items.splice(target, 0, current)
}

// 每次关闭或切换商品都废弃旧上传，避免旧封面写入新商品表单。
function invalidateCoverUpload(): void {
  latestCoverRequest++
  coverUploading.value = false
}

function closeEditor(): void {
  if (saving.value) return
  invalidateCoverUpload()
  latestEditorRequest++
  editorLoading.value = false
  showForm.value = false
}

async function uploadCoverFile(file: File): Promise<void> {
  if (disposed || saving.value || coverUploading.value || !showForm.value) return
  if (!ACCEPTED_COVER_TYPES.has(file.type)) {
    errorMessage.value = '封面图片仅支持 PNG、JPEG 或 WebP'
    return
  }
  if (file.size > MAX_COVER_SIZE_BYTES) {
    errorMessage.value = '封面图片不能超过 5 MB'
    return
  }
  const requestId = ++latestCoverRequest
  coverUploading.value = true
  errorMessage.value = ''
  try {
    const uploaded = await uploadCatalogFile(file)
    if (disposed || requestId !== latestCoverRequest) return
    form.coverAssetId = uploaded.id
    coverPreviewUrl.value = resolveApiResourceUrl(uploaded.publicUrl)
  } catch (error) {
    if (requestId === latestCoverRequest && !disposed) errorMessage.value = readableError(error)
  } finally {
    if (requestId === latestCoverRequest) coverUploading.value = false
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

async function submit(): Promise<void> {
  if (disposed || saving.value || coverUploading.value || editorLoading.value) return
  saving.value = true
  errorMessage.value = ''
  try {
    const payload: SavePackageRequest = {
      ...form,
      items: form.items.map((item) => ({
        beadSkuId: Number(item.beadSkuId),
        quantity: Number(item.quantity),
      })),
    }
    if (editingId.value) {
      await updateAdminPackage(editingId.value, payload)
      showToast('成品搭配已更新')
    } else {
      await createAdminPackage(payload)
      showToast('成品搭配已创建')
    }
    showForm.value = false
    await loadPackages()
  } catch (error) {
    errorMessage.value = readableError(error)
  } finally {
    saving.value = false
  }
}

async function toggleStatus(item: ProductPackage): Promise<void> {
  const nextStatus: CatalogStatus =
    item.status === 'ON_SALE' ? 'OFF_SALE' : 'ON_SALE'
  errorMessage.value = ''
  try {
    await changePackageStatus(item.id, nextStatus)
    await loadPackages()
  } catch (error) {
    errorMessage.value = readableError(error)
  }
}

async function toggleRecommendation(item: ProductPackage): Promise<void> {
  recommendingId.value = item.id
  errorMessage.value = ''
  try {
    const updated = await changePackageRecommendation(
      item.id,
      !item.recommended,
    )
    packages.value = packages.value.map((candidate) => (
      candidate.id === updated.id ? updated : candidate
    ))
    showToast(updated.recommended
      ? '已加入小程序首页成品精选'
      : '已取消首页推荐')
  } catch (error) {
    errorMessage.value = readableError(error)
  } finally {
    recommendingId.value = null
  }
}

function search(): void {
  filters.page = 1
  void loadPackages()
}

function clearSearch(): void {
  filters.keyword = ''
  search()
}

function resetFilters(): void {
  Object.assign(filters, { keyword: '', status: '', page: 1 })
  void loadPackages()
}

function changePage(page: number): void {
  filters.page = Math.min(Math.max(page, 1), totalPages.value)
  void loadPackages()
}

function changePageSize(size: number): void {
  filters.size = size
  filters.page = 1
  void loadPackages()
}

onMounted(loadPackages)
onUnmounted(() => {
  disposed = true
  invalidateCoverUpload()
  latestPackageRequest++
  latestEditorRequest++
  if (toastTimer !== null) clearTimeout(toastTimer)
})
</script>

<template>
  <section class="catalog-view">
    <header class="content-header">
      <div>
        <p class="section-kicker">CATALOG / PACKAGES</p>
        <h1>成品搭配</h1>
        <p class="muted">
          按顺序组合散珠；已上架且标记为推荐的成品会显示在小程序首页。
        </p>
      </div>
      <button
        class="primary-button"
        data-test="create-package"
        :disabled="editorLoading"
        @click="openCreate"
      >
        {{ editorLoading ? '正在准备…' : '＋ 新增搭配' }}
      </button>
    </header>

    <div v-if="errorMessage" class="notice error-notice" role="alert">
      {{ errorMessage }}
    </div>
    <div
      v-if="toastMessage"
      class="package-toast"
      data-test="package-toast"
      role="status"
    >
      {{ toastMessage }}
    </div>

    <section class="toolbar" aria-label="套餐筛选">
      <div class="search-field search-field-with-clear">
        <span class="sr-only">搜索套餐</span>
        <input
          v-model="filters.keyword"
          aria-label="搜索套餐"
          placeholder="搜索套餐编码或名称"
          @keyup.enter="search"
        />
        <button
          v-if="filters.keyword"
          class="search-clear-button"
          data-test="clear-package-search"
          type="button"
          aria-label="清空搜索"
          @click="clearSearch"
        >×</button>
      </div>
      <label>
        <span class="sr-only">套餐状态</span>
        <select v-model="filters.status" @change="search">
          <option value="">全部状态</option>
          <option value="DRAFT">草稿</option>
          <option value="ON_SALE">已上架</option>
          <option value="OFF_SALE">已下架</option>
        </select>
      </label>
      <button class="secondary-button" @click="search">查询</button>
      <button class="secondary-button" data-test="reset-package-filters" @click="resetFilters">重置</button>
      <span class="result-count">共 {{ total }} 个搭配</span>
    </section>

    <section class="table-card">
      <div v-if="loading" class="empty-state">正在读取成品搭配…</div>
      <div v-else-if="packages.length === 0" class="empty-state">
        <strong>还没有符合条件的成品搭配</strong>
        <span>创建后，小程序套餐页会展示已上架的数据。</span>
      </div>
      <div v-else class="table-scroll">
        <table class="package-table">
          <thead>
            <tr>
              <th>搭配</th>
              <th>说明</th>
              <th>销售价</th>
              <th>状态</th>
              <th>首页推荐</th>
              <th class="align-right">操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="item in packages" :key="item.id">
              <td>
                <OverflowText class="table-primary" :text="item.name" :lines="2" />
                <OverflowText class="table-code" :text="item.packageCode" />
              </td>
              <td><OverflowText class="cell-detail package-description" :text="item.description" :lines="2" /></td>
              <td class="money">¥ {{ Number(item.salePrice).toFixed(2) }}</td>
              <td>
                <span class="status-pill" :class="`status-${item.status.toLowerCase()}`">
                  {{ statusLabel(item.status) }}
                </span>
              </td>
              <td>
                <button
                  class="recommendation-button"
                  :class="{ 'is-recommended': item.recommended }"
                  :aria-pressed="item.recommended"
                  :aria-label="item.recommended ? `取消推荐${item.name}` : `推荐${item.name}`"
                  :data-test="`toggle-package-recommendation-${item.id}`"
                  :disabled="recommendingId === item.id"
                  @click="toggleRecommendation(item)"
                >
                  {{ recommendingId === item.id ? '处理中…' : item.recommended ? '已推荐' : '推荐' }}
                </button>
              </td>
              <td class="align-right">
                <button
                  class="text-button"
                  :data-test="`edit-package-${item.id}`"
                  @click="openEdit(item)"
                >编辑</button>
                <button class="text-button" @click="toggleStatus(item)">
                  {{ item.status === 'ON_SALE' ? '下架' : '上架' }}
                </button>
              </td>
            </tr>
          </tbody>
        </table>
      </div>

      <ListPagination :page="filters.page" :size="filters.size" :total="total" @update:page="changePage" @update:size="changePageSize" />
    </section>

    <div v-if="showForm" class="modal-backdrop" @click.self="closeEditor">
      <section class="drawer" aria-labelledby="package-form-title">
        <header>
          <div>
            <p class="section-kicker">
              {{ editingId ? 'EDIT PACKAGE' : 'NEW PACKAGE' }}
            </p>
            <h2 id="package-form-title">
              {{ editingId ? '编辑成品搭配' : '新增成品搭配' }}
            </h2>
          </div>
          <button class="icon-button" aria-label="关闭" @click="closeEditor">×</button>
        </header>

        <form class="catalog-form" @submit.prevent="submit">
          <div class="form-grid">
            <label>
              <span>搭配名称</span>
              <input v-model="form.name" required maxlength="120" />
            </label>
            <label>
              <span>套餐编码</span>
              <input
                v-model="form.packageCode"
                required
                maxlength="64"
                pattern="[A-Z0-9]+(?:-[A-Z0-9]+)*"
                placeholder="PACKAGE-CALM-001"
              />
            </label>
            <label>
              <span>销售价（元）</span>
              <input v-model.number="form.salePrice" type="number" min="0" step="0.01" required />
            </label>
            <label>
              <span>状态</span>
              <select v-model="form.status">
                <option value="DRAFT">草稿</option>
                <option value="ON_SALE">直接上架</option>
                <option value="OFF_SALE">已下架</option>
              </select>
            </label>
          </div>
          <label>
            <span>搭配说明</span>
            <textarea v-model="form.description" required maxlength="1000" rows="3" />
          </label>

          <section class="package-items-editor">
            <header>
              <div>
                <strong>散珠明细</strong>
                <small>列表顺序就是小程序展示和生产明细顺序。</small>
              </div>
              <button type="button" class="secondary-button" @click="addItem">＋ 添加散珠</button>
            </header>
            <div
              v-for="(packageItem, index) in form.items"
              :key="index"
              class="package-item-row"
            >
              <span class="item-order">{{ index + 1 }}</span>
              <select v-model.number="packageItem.beadSkuId" required>
                <option
                  v-for="bead in availableBeads"
                  :key="bead.id"
                  :value="bead.id"
                >
                  {{ bead.name }}（{{ bead.skuCode }}）
                </option>
              </select>
              <label>
                <span class="sr-only">数量</span>
                <input
                  v-model.number="packageItem.quantity"
                  type="number"
                  min="1"
                  max="999"
                  required
                  title="数量"
                />
              </label>
              <div class="item-actions">
                <button type="button" class="icon-button" title="上移" @click="moveItem(index, -1)">↑</button>
                <button type="button" class="icon-button" title="下移" @click="moveItem(index, 1)">↓</button>
                <button
                  type="button"
                  class="icon-button danger-icon"
                  title="移除"
                  :disabled="form.items.length <= 1"
                  @click="removeItem(index)"
                >
                  ×
                </button>
              </div>
            </div>
          </section>

          <label
            class="cover-upload file-dropzone cover-dropzone"
            :class="{ 'is-dragging': coverDropActive }"
            data-test="package-cover-dropzone"
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
              素材 #{{ form.coverAssetId }} 已选择
            </small>
            <img v-if="coverPreviewUrl" :src="coverPreviewUrl" alt="成品搭配封面预览" />
          </label>

          <footer>
            <button type="button" class="secondary-button" @click="closeEditor">取消</button>
            <button
              class="primary-button"
              :disabled="saving || coverUploading || availableBeads.length === 0"
            >
              {{ saving ? '正在保存…' : editingId ? '保存修改' : '创建搭配' }}
            </button>
          </footer>
        </form>
      </section>
    </div>
  </section>
</template>

<style scoped>
.package-toast {
  position: fixed;
  z-index: 30;
  top: 28px;
  left: 50%;
  max-width: min(480px, calc(100vw - 40px));
  padding: 12px 18px;
  border-radius: 10px;
  color: #fff;
  font-size: 14px;
  font-weight: 600;
  line-height: 1.4;
  text-align: center;
  background: #548b72;
  box-shadow: 0 12px 28px rgba(54, 84, 68, .22);
  transform: translateX(-50%);
}
</style>
