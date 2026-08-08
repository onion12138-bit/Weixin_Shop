<script setup lang="ts">
import { nextTick, onBeforeUnmount, ref, useAttrs } from 'vue'

defineOptions({ inheritAttrs: false })

defineProps<{
  text: string
  lines?: number
}>()

const trigger = ref<HTMLElement | null>(null)
const attrs = useAttrs()
const visible = ref(false)
const tooltipStyle = ref<Record<string, string>>({})

async function show(): Promise<void> {
  visible.value = true
  await nextTick()
  positionTooltip()
  window.addEventListener('scroll', positionTooltip, true)
  window.addEventListener('resize', positionTooltip)
}

function hide(): void {
  visible.value = false
  window.removeEventListener('scroll', positionTooltip, true)
  window.removeEventListener('resize', positionTooltip)
}

function positionTooltip(): void {
  const element = trigger.value
  if (!element) return
  const rect = element.getBoundingClientRect()
  const maxWidth = Math.min(360, window.innerWidth - 32)
  const left = Math.min(Math.max(16, rect.left), window.innerWidth - maxWidth - 16)
  tooltipStyle.value = {
    left: `${left}px`,
    top: `${Math.min(rect.bottom + 8, window.innerHeight - 80)}px`,
    maxWidth: `${maxWidth}px`,
  }
}

onBeforeUnmount(hide)
</script>

<template>
  <span
    ref="trigger"
    v-bind="attrs"
    class="overflow-text"
    :style="{ '--overflow-lines': String(lines || 1) }"
    tabindex="0"
    @mouseenter="show"
    @mouseleave="hide"
    @focus="show"
    @blur="hide"
  >{{ text }}</span>
  <Teleport to="body">
    <div v-if="visible" class="overflow-tooltip" :style="tooltipStyle" role="tooltip">{{ text }}</div>
  </Teleport>
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

.overflow-text:focus-visible {
  border-radius: 4px;
  outline: 2px solid rgb(135 68 95 / 35%);
  outline-offset: 2px;
}

.overflow-tooltip {
  position: fixed;
  z-index: 1000;
  box-sizing: border-box;
  padding: 9px 12px;
  border: 1px solid #eadde2;
  border-radius: 9px;
  color: #fff;
  font-size: 12px;
  line-height: 1.55;
  white-space: normal;
  overflow-wrap: anywhere;
  background: #46373d;
  box-shadow: 0 10px 28px rgb(39 25 31 / 22%);
  pointer-events: none;
}
</style>
