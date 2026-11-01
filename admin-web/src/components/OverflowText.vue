<script setup lang="ts">
// 长文本按行数截断，完整内容由可键盘访问的提示层展示。
import DataTooltip from './DataTooltip.vue'
defineProps<{ text: string; lines?: number }>()
</script>

<template>
  <DataTooltip :text="text" class="overflow-text" :style="{ '--overflow-lines': String(lines || 1) }" />
</template>

<style scoped>
.overflow-text {
  display: -webkit-box;
  min-width: 0;
  overflow: hidden;
  line-height: 1.45;
  text-overflow: ellipsis;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: var(--overflow-lines);
  overflow-wrap: anywhere;
}
</style>
