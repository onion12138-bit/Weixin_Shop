import { flushPromises, mount } from '@vue/test-utils'
import { afterEach, describe, expect, it, vi } from 'vitest'
import OverflowText from './OverflowText.vue'

const wrappers: ReturnType<typeof mount>[] = []
function render(text = '商品详情') {
  const wrapper = mount(OverflowText, { props: { text }, attachTo: document.body })
  wrappers.push(wrapper)
  return wrapper
}

afterEach(() => {
  wrappers.splice(0).forEach((wrapper) => wrapper.unmount())
  vi.restoreAllMocks()
  vi.useRealTimers()
})

describe('OverflowText details', () => {
  it('associates the complete plain text with a focused trigger and dismisses on Escape', async () => {
    const wrapper = render('<img src=x onerror=alert(1)>完整数据')
    await wrapper.get('[tabindex]').trigger('focus')
    await flushPromises()
    const tooltip = document.querySelector('[role="tooltip"]')!
    expect(tooltip.textContent).toBe('<img src=x onerror=alert(1)>完整数据')
    expect(tooltip.querySelector('img')).toBeNull()
    expect(wrapper.get('[tabindex]').attributes('aria-describedby')).toBe(tooltip.id)
    window.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape' }))
    await flushPromises()
    expect(document.querySelector('[role="tooltip"]')).toBeNull()
  })

  it('keeps long details above the bottom edge instead of clipping the content', async () => {
    vi.spyOn(HTMLElement.prototype, 'getBoundingClientRect').mockImplementation(function (this: HTMLElement) {
      return this.getAttribute('role') === 'tooltip'
        ? { x: 0, y: 0, top: 0, left: 0, bottom: 260, right: 320, width: 320, height: 260, toJSON() {} }
        : { x: 900, y: 720, top: 720, left: 900, bottom: 744, right: 1000, width: 100, height: 24, toJSON() {} }
    })
    const wrapper = render('长内容'.repeat(80))
    await wrapper.get('[tabindex]').trigger('mouseenter')
    await flushPromises()
    const tooltip = document.querySelector<HTMLElement>('[role="tooltip"]')!
    expect(parseFloat(tooltip.style.top) + 260).toBeLessThanOrEqual(window.innerHeight - 12)
    expect(parseFloat(tooltip.style.left) + 320).toBeLessThanOrEqual(window.innerWidth - 12)
  })

  it('keeps the popup open while moving the mouse into it to read or scroll', async () => {
    vi.useFakeTimers()
    const wrapper = render()
    await wrapper.get('[tabindex]').trigger('mouseenter')
    await flushPromises()
    const tooltip = document.querySelector('[role="tooltip"]')!
    await wrapper.get('[tabindex]').trigger('mouseleave')
    tooltip.dispatchEvent(new MouseEvent('mouseenter'))
    await vi.runAllTimersAsync()
    expect(document.querySelector('[role="tooltip"]')).not.toBeNull()
    tooltip.dispatchEvent(new MouseEvent('mouseleave'))
    await vi.runAllTimersAsync()
    expect(document.querySelector('[role="tooltip"]')).toBeNull()
  })

  it('does not open an empty popup and removes an open popup on unmount', async () => {
    const empty = render('  ')
    await empty.get('[tabindex]').trigger('mouseenter')
    await flushPromises()
    expect(document.querySelector('[role="tooltip"]')).toBeNull()
    const wrapper = render()
    await wrapper.get('[tabindex]').trigger('focus')
    await flushPromises()
    wrapper.unmount()
    expect(document.querySelector('[role="tooltip"]')).toBeNull()
  })

  it('clears stale hover state after Escape so the next keyboard visit closes on blur', async () => {
    vi.useFakeTimers()
    const wrapper = render()
    const trigger = wrapper.get('[tabindex]')
    await trigger.trigger('mouseenter')
    await flushPromises()
    document.querySelector('[role="tooltip"]')!.dispatchEvent(new MouseEvent('mouseenter'))
    window.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape' }))
    await flushPromises()
    await trigger.trigger('focus')
    await flushPromises()
    await trigger.trigger('blur')
    await vi.runAllTimersAsync()
    expect(document.querySelector('[role="tooltip"]')).toBeNull()
  })

  it('lets keyboard users enter long details to scroll, then returns focus on Escape', async () => {
    const wrapper = render('完整详情\n'.repeat(100))
    const trigger = wrapper.get<HTMLElement>('[tabindex]')
    trigger.element.focus()
    await trigger.trigger('keydown', { key: 'ArrowDown' })
    await flushPromises()
    const popup = document.querySelector<HTMLElement>('[role="tooltip"]')!
    expect(popup).not.toBeNull()
    expect(document.activeElement).toBe(popup)
    popup.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true }))
    await flushPromises()
    expect(document.querySelector('[role="tooltip"]')).toBeNull()
    expect(document.activeElement).toBe(trigger.element)
  })
})
