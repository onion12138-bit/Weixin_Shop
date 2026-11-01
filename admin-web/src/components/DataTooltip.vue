<script setup lang="ts">
// 提示层支持鼠标和键盘访问，关闭时释放全局监听及尺寸观察器。
import { computed, nextTick, onBeforeUnmount, ref, useAttrs, useId, watch } from 'vue'

defineOptions({ inheritAttrs: false })
const props = withDefaults(defineProps<{ text: string; as?: 'span' | 'button' }>(), { as: 'span' })
const attrs = useAttrs()
const id = `data-tooltip-${useId()}`
const trigger = ref<HTMLElement | null>(null)
const popup = ref<HTMLElement | null>(null)
const visible = ref(false)
const position = ref<Record<string, string>>({})
const describedBy = computed(() => [attrs['aria-describedby'], visible.value ? id : ''].filter(Boolean).join(' ') || undefined)
let closeTimer: ReturnType<typeof setTimeout> | undefined
let observer: ResizeObserver | undefined
let focused = false
let hovering = false
let disposed = false

function cancelClose(): void {
  clearTimeout(closeTimer)
  closeTimer = undefined
}
function reposition(): void {
  if (!trigger.value || !popup.value) return
  const anchor = trigger.value.getBoundingClientRect()
  const bounds = popup.value.getBoundingClientRect()
  const margin = 12
  const maxLeft = Math.max(margin, window.innerWidth - bounds.width - margin)
  const below = anchor.bottom + 8
  const top = below + bounds.height <= window.innerHeight - margin
    ? below : anchor.top - bounds.height - 8
  position.value = {
    left: `${Math.max(margin, Math.min(anchor.left, maxLeft))}px`,
    top: `${Math.max(margin, Math.min(top, window.innerHeight - bounds.height - margin))}px`,
  }
}
function hide(): void {
  cancelClose()
  visible.value = false
  hovering = false
  focused = false
  window.removeEventListener('scroll', reposition, true)
  window.removeEventListener('resize', reposition)
  window.removeEventListener('keydown', onKeydown)
  observer?.disconnect()
  observer = undefined
}
function onKeydown(event: KeyboardEvent): void {
  if (event.key !== 'Escape') return
  if (document.activeElement === popup.value) trigger.value?.focus({ preventScroll: true })
  hide()
}
async function show(): Promise<void> {
  cancelClose()
  if (!props.text.trim() || disposed || visible.value) return
  visible.value = true
  await nextTick()
  if (!visible.value || disposed) return
  reposition()
  window.addEventListener('scroll', reposition, true)
  window.addEventListener('resize', reposition)
  window.addEventListener('keydown', onKeydown)
  if (typeof ResizeObserver !== 'undefined') {
    observer = new ResizeObserver(reposition)
    if (trigger.value) observer.observe(trigger.value)
    if (popup.value) observer.observe(popup.value)
  }
}
function enter(): void { hovering = true; void show() }
function leave(): void {
  hovering = false
  cancelClose()
  if (!focused) closeTimer = setTimeout(hide, 120)
}
function focus(): void { focused = true; void show() }
function blur(): void {
  focused = false
  if (!hovering) { cancelClose(); closeTimer = setTimeout(hide, 120) }
}
async function focusPopup(): Promise<void> {
  await show()
  await nextTick()
  popup.value?.focus({ preventScroll: true })
}
watch(() => props.text, async () => {
  if (!props.text.trim()) hide()
  else if (visible.value) { await nextTick(); reposition() }
})
onBeforeUnmount(() => { disposed = true; hide() })
</script>

<template>
  <component
    :is="as" ref="trigger" v-bind="attrs" class="data-tooltip-trigger"
    :type="as === 'button' ? 'button' : undefined"
    :tabindex="attrs.tabindex ?? 0" :aria-describedby="describedBy"
    aria-keyshortcuts="ArrowDown"
    @mouseenter="enter" @mouseleave="leave" @focus="focus" @blur="blur"
    @keydown.down.prevent="focusPopup"
  >
    <slot>{{ text }}</slot>
    <Teleport to="body">
      <div
        v-if="visible" :id="id" ref="popup" class="overflow-tooltip" role="tooltip" tabindex="0"
        :style="position" @mouseenter="enter" @mouseleave="leave"
        @focus="focus" @blur="blur"
      >{{ text }}</div>
    </Teleport>
  </component>
</template>

<style scoped>
.data-tooltip-trigger { min-width: 0; }
.data-tooltip-trigger:focus-visible { outline: 2px solid #a36b81; outline-offset: 3px; border-radius: 4px; }
.overflow-tooltip {
  position: fixed;
  z-index: 2000;
  box-sizing: border-box;
  width: max-content;
  max-width: min(380px, calc(100vw - 24px));
  max-height: min(360px, calc(100vh - 24px));
  overflow: auto;
  overscroll-behavior: contain;
  padding: 10px 13px;
  border: 1px solid #6d5862;
  border-radius: 9px;
  color: #fff;
  font-size: 12px;
  font-weight: 400;
  line-height: 1.65;
  text-align: left;
  white-space: pre-line;
  overflow-wrap: anywhere;
  background: #46373d;
  box-shadow: 0 10px 28px rgb(39 25 31 / 22%);
}
</style>
