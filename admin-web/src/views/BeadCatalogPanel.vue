<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ApiError, resolveApiResourceUrl } from '../api/http'
import {
  changeBeadStatus,
  createAdminBead,
  listAdminBeads,
  listAdminCategories,
  updateAdminBead,
  uploadCatalogFile,
  type BeadSku,
  type CatalogStatus,
  type Category,
  type SaveBeadSkuRequest,
} from '../api/catalog'

const categories = ref<Category[]>([])
const beads = ref<BeadSku[]>([])
const total = ref(0)
const loading = ref(false)
const saving = ref(false)
const coverUploading = ref(false)
const errorMessage = ref('')
const successMessage = ref('')
const showCreateForm = ref(false)
const editingBeadId = ref<number | null>(null)

const filters = reactive({
  keyword: '',
  status: '' as CatalogStatus | '',
  page: 1,
  size: 20,
})

const form = reactive<SaveBeadSkuRequest>({
  categoryId: 0,
  skuCode: '',
  name: '',
  material: '',
  color: '',
  diameterMm: 8,
  salePrice: 0,
  costPrice: 0,
  stockAlertThreshold: 10,
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

function readableError(error: unknown): string {
  if (error instanceof ApiError) {
    return error.traceId
      ? `${error.message}（追踪号：${error.traceId}）`
      : error.message
  }
  return '请求失败，请确认后端服务已启动'
}

async function loadCatalog(): Promise<void> {
  loading.value = true
  errorMessage.value = ''
  try {
    const [categoryResult, beadResult] = await Promise.all([
      listAdminCategories(),
      listAdminBeads(filters),
    ])
    categories.value = categoryResult
    beads.value = beadResult.items
    total.value = beadResult.total
    if (!form.categoryId && categoryResult.length > 0) {
      form.categoryId = categoryResult[0].id
    }
  } catch (error) {
    errorMessage.value = readableError(error)
  } finally {
    loading.value = false
  }
}

function search(): void {
  filters.page = 1
  void loadCatalog()
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
  coverPreviewUrl.value = ''
  showCreateForm.value = true
}

async function uploadCover(event: Event): Promise<void> {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  if (!file) return
  coverUploading.value = true
  errorMessage.value = ''
  try {
    const uploaded = await uploadCatalogFile(file)
    form.coverAssetId = uploaded.id
    coverPreviewUrl.value = resolveApiResourceUrl(uploaded.publicUrl)
  } catch (error) {
    errorMessage.value = readableError(error)
    input.value = ''
  } finally {
    coverUploading.value = false
  }
}

async function submitBead(): Promise<void> {
  saving.value = true
  errorMessage.value = ''
  successMessage.value = ''
  try {
    const payload = {
      ...form,
      tags: tagInput.value
        .split(/[,，]/)
        .map((tag) => tag.trim())
        .filter(Boolean),
    }
    if (editingBeadId.value) {
      await updateAdminBead(editingBeadId.value, payload)
      successMessage.value = '散珠商品已更新'
    } else {
      await createAdminBead(payload)
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
      <button
        class="primary-button"
        data-test="create-bead"
        @click="openCreateForm"
      >
        ＋ 新增散珠
      </button>
    </header>

    <div v-if="errorMessage" class="notice error-notice" role="alert">
      {{ errorMessage }}
    </div>
    <div v-if="successMessage" class="notice success-notice">
      {{ successMessage }}
    </div>

    <section class="toolbar" aria-label="商品筛选">
      <label class="search-field">
        <span class="sr-only">搜索商品</span>
        <input
          v-model="filters.keyword"
          placeholder="搜索 SKU、名称或材质"
          @keyup.enter="search"
        />
      </label>
      <label>
        <span class="sr-only">商品状态</span>
        <select v-model="filters.status" @change="search">
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
                  <div class="bead-preview" :style="{ '--bead-color': bead.color }">
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
              <input
                v-model="form.skuCode"
                required
                maxlength="64"
                placeholder="BEAD-CRYSTAL-001"
              />
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
          <label class="cover-upload">
            <span>封面图片（PNG / JPEG / WebP，最大 5 MiB）</span>
            <input
              type="file"
              accept="image/png,image/jpeg,image/webp"
              @change="uploadCover"
            />
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
  </section>
</template>
