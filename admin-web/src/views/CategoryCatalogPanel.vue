<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ApiError } from '../api/http'
import {
  changeCategoryStatus,
  createAdminCategory,
  listAdminCategories,
  updateAdminCategory,
  type CatalogStatus,
  type Category,
  type ProductType,
  type SaveCategoryRequest,
} from '../api/catalog'

const categories = ref<Category[]>([])
const loading = ref(false)
const saving = ref(false)
const showForm = ref(false)
const editingId = ref<number | null>(null)
const errorMessage = ref('')
const successMessage = ref('')

const form = reactive<SaveCategoryRequest>({
  name: '',
  slug: '',
  productType: 'BEAD',
  sortOrder: 0,
  status: 'DRAFT',
})

function statusLabel(status: CatalogStatus): string {
  return {
    DRAFT: '草稿',
    ON_SALE: '已上架',
    OFF_SALE: '已下架',
  }[status]
}

function productTypeLabel(productType: ProductType): string {
  return {
    BEAD: '珠子',
    CORD: '绳类',
    ACCESSORY: '其他饰品',
  }[productType]
}

function readableError(error: unknown): string {
  if (error instanceof ApiError) {
    return error.traceId
      ? `${error.message}（追踪号：${error.traceId}）`
      : error.message
  }
  return '分类操作失败，请确认后端服务已启动'
}

async function loadCategories(): Promise<void> {
  loading.value = true
  errorMessage.value = ''
  try {
    categories.value = await listAdminCategories()
  } catch (error) {
    errorMessage.value = readableError(error)
  } finally {
    loading.value = false
  }
}

function openCreate(): void {
  editingId.value = null
  Object.assign(form, {
    name: '',
    slug: '',
    productType: 'BEAD',
    sortOrder: (categories.value.length + 1) * 10,
    status: 'DRAFT',
  })
  showForm.value = true
}

function openEdit(category: Category): void {
  editingId.value = category.id
  Object.assign(form, {
    name: category.name,
    slug: category.slug,
    productType: category.productType,
    sortOrder: category.sortOrder,
    status: category.status,
  })
  showForm.value = true
}

/**
 * 分类 slug 是公共接口和后续运营链接的稳定标识，编辑时不由名称自动覆盖。
 */
async function submit(): Promise<void> {
  saving.value = true
  errorMessage.value = ''
  successMessage.value = ''
  try {
    if (editingId.value) {
      await updateAdminCategory(editingId.value, { ...form })
      successMessage.value = '商品分类已更新'
    } else {
      await createAdminCategory({ ...form })
      successMessage.value = '商品分类已创建'
    }
    showForm.value = false
    await loadCategories()
  } catch (error) {
    errorMessage.value = readableError(error)
  } finally {
    saving.value = false
  }
}

async function toggleStatus(category: Category): Promise<void> {
  const nextStatus: CatalogStatus =
    category.status === 'ON_SALE' ? 'OFF_SALE' : 'ON_SALE'
  errorMessage.value = ''
  try {
    await changeCategoryStatus(category.id, nextStatus)
    await loadCategories()
  } catch (error) {
    errorMessage.value = readableError(error)
  }
}

onMounted(loadCategories)
</script>

<template>
  <section class="catalog-view">
    <header class="content-header">
      <div>
        <p class="section-kicker">CATALOG / CATEGORIES</p>
        <h1>商品分类</h1>
        <p class="muted">控制小程序分类入口和散珠的归属、顺序及可见性。</p>
      </div>
      <button class="primary-button" data-test="create-category" @click="openCreate">
        ＋ 新增分类
      </button>
    </header>

    <div v-if="errorMessage" class="notice error-notice" role="alert">
      {{ errorMessage }}
    </div>
    <div v-if="successMessage" class="notice success-notice">
      {{ successMessage }}
    </div>

    <section class="table-card">
      <div v-if="loading" class="empty-state">正在读取商品分类…</div>
      <div v-else-if="categories.length === 0" class="empty-state">
        <strong>还没有商品分类</strong>
        <span>先创建分类，再维护散珠商品。</span>
      </div>
      <div v-else class="table-scroll">
        <table>
          <thead>
            <tr>
              <th>分类名称</th>
              <th>Slug</th>
              <th>商品类型</th>
              <th>排序</th>
              <th>状态</th>
              <th class="align-right">操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="category in categories" :key="category.id">
              <td><strong>{{ category.name }}</strong></td>
              <td><code>{{ category.slug }}</code></td>
              <td>{{ productTypeLabel(category.productType) }}</td>
              <td>{{ category.sortOrder }}</td>
              <td>
                <span class="status-pill" :class="`status-${category.status.toLowerCase()}`">
                  {{ statusLabel(category.status) }}
                </span>
              </td>
              <td class="align-right">
                <button class="text-button" @click="openEdit(category)">编辑</button>
                <button class="text-button" @click="toggleStatus(category)">
                  {{ category.status === 'ON_SALE' ? '下架' : '上架' }}
                </button>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </section>

    <div v-if="showForm" class="modal-backdrop" @click.self="showForm = false">
      <section class="drawer compact-drawer" aria-labelledby="category-form-title">
        <header>
          <div>
            <p class="section-kicker">
              {{ editingId ? 'EDIT CATEGORY' : 'NEW CATEGORY' }}
            </p>
            <h2 id="category-form-title">
              {{ editingId ? '编辑商品分类' : '新增商品分类' }}
            </h2>
          </div>
          <button class="icon-button" aria-label="关闭" @click="showForm = false">×</button>
        </header>

        <form class="catalog-form" @submit.prevent="submit">
          <label>
            <span>分类名称</span>
            <input v-model="form.name" required maxlength="80" />
          </label>
          <label>
            <span>Slug（小写英文、数字、连字符）</span>
            <input
              v-model="form.slug"
              required
              maxlength="80"
              pattern="[a-z0-9]+(?:-[a-z0-9]+)*"
              placeholder="natural-crystal"
            />
          </label>
          <label>
            <span>商品类型</span>
            <select v-model="form.productType" data-test="category-product-type">
              <option value="BEAD">珠子</option>
              <option value="CORD">绳类</option>
              <option value="ACCESSORY">其他饰品</option>
            </select>
            <small>珠子参与 DIY 和 AI 推荐；绳类及其他饰品独立管理库存。</small>
          </label>
          <div class="form-grid">
            <label>
              <span>排序值</span>
              <input v-model.number="form.sortOrder" type="number" required />
            </label>
            <label>
              <span>状态</span>
              <select v-model="form.status">
                <option value="DRAFT">草稿</option>
                <option value="ON_SALE">已上架</option>
                <option value="OFF_SALE">已下架</option>
              </select>
            </label>
          </div>
          <footer>
            <button type="button" class="secondary-button" @click="showForm = false">取消</button>
            <button class="primary-button" :disabled="saving">
              {{ saving ? '正在保存…' : '保存分类' }}
            </button>
          </footer>
        </form>
      </section>
    </div>
  </section>
</template>
