<script setup lang="ts">
// 页码始终限制在有效范围，数据总数减少时同步修正当前页。
import { computed, ref, watch } from 'vue'

const props = withDefaults(defineProps<{
  page: number
  size: number
  total: number
  sizes?: number[]
}>(), {
  sizes: () => [10, 20, 50],
})

const emit = defineEmits<{
  'update:page': [page: number]
  'update:size': [size: number]
}>()

const jumpPage = ref('')
const totalPages = computed(() => Math.max(1, Math.ceil(props.total / props.size)))
const pageNumbers = computed(() => {
  const start = Math.max(1, Math.min(props.page - 2, totalPages.value - 4))
  const end = Math.min(totalPages.value, start + 4)
  return Array.from({ length: end - start + 1 }, (_, index) => start + index)
})

watch(totalPages, (value) => {
  if (props.page > value) emit('update:page', value)
})

function changePage(page: number): void {
  emit('update:page', Math.min(Math.max(page, 1), totalPages.value))
}

function changeSize(event: Event): void {
  emit('update:size', Number((event.target as HTMLSelectElement).value))
}

function submitJump(): void {
  const target = Number(jumpPage.value)
  if (Number.isInteger(target)) changePage(target)
  jumpPage.value = ''
}
</script>

<template>
  <footer class="list-pagination">
    <span>共 {{ total }} 条</span>
    <label>每页
      <select :value="size" @change="changeSize">
        <option v-for="option in sizes" :key="option" :value="option">{{ option }} 条</option>
      </select>
    </label>
    <button class="page-button" :disabled="page <= 1" @click="changePage(page - 1)">上一页</button>
    <button
      v-for="number in pageNumbers"
      :key="number"
      :class="['page-number', { active: number === page }]"
      @click="changePage(number)"
    >{{ number }}</button>
    <button class="page-button" :disabled="page >= totalPages" @click="changePage(page + 1)">下一页</button>
    <label class="jump-page">跳至
      <input v-model="jumpPage" aria-label="跳转页码" type="number" min="1" :max="totalPages" @keyup.enter="submitJump" />页
    </label>
  </footer>
</template>
