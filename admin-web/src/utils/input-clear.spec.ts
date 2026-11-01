import { afterEach, describe, expect, it, vi } from 'vitest'
import { installInputClearButtons } from './input-clear'

describe('installInputClearButtons', () => {
  afterEach(() => {
    document.body.innerHTML = ''
  })

  it('adds a working clear button to editable inputs but not checkboxes', () => {
    document.body.innerHTML = `
      <input aria-label="订单编号" value="ORD123" />
      <textarea aria-label="订单备注">请尽快发货</textarea>
      <input type="checkbox" aria-label="选择订单" />
    `
    const editable = document.querySelector<HTMLInputElement>('[aria-label="订单编号"]')!
    const onInput = vi.fn()
    editable.addEventListener('input', onInput)

    const observer = installInputClearButtons(document)
    const clearButtons = document.querySelectorAll<HTMLButtonElement>('.global-input-clear')

    expect(clearButtons).toHaveLength(2)
    expect(clearButtons[0].getAttribute('aria-label')).toBe('清空订单编号')
    clearButtons[0].click()
    expect(editable.value).toBe('')
    expect(onInput).toHaveBeenCalledTimes(1)
    expect(document.querySelector<HTMLInputElement>('[type="checkbox"]')!
      .dataset.clearButtonReady).toBeUndefined()
    observer.disconnect()
  })

  it('does not duplicate an existing search clear control', () => {
    document.body.innerHTML = `
      <div class="search-field-with-clear">
        <input value="关键词" />
        <button class="search-clear-button">×</button>
      </div>
    `
    const observer = installInputClearButtons(document)

    expect(document.querySelectorAll('.global-input-clear')).toHaveLength(0)
    expect(document.querySelectorAll('.search-clear-button')).toHaveLength(1)
    observer.disconnect()
  })

  it('enhances inputs added after installation', async () => {
    const observer = installInputClearButtons(document)
    const input = document.createElement('input')
    input.value = '动态内容'
    document.body.append(input)
    await new Promise<void>((resolve) => queueMicrotask(resolve))

    expect(document.querySelectorAll('.global-input-clear')).toHaveLength(1)
    observer.disconnect()
  })

  it('coalesces repeated scroll events into one layout measurement per frame', async () => {
    document.body.innerHTML = '<input value="测试" />'
    const frames: FrameRequestCallback[] = []
    const schedule = vi.spyOn(window, 'requestAnimationFrame').mockImplementation((cb) => {
      frames.push(cb)
      return frames.length
    })
    const observer = installInputClearButtons(document)
    await new Promise<void>((resolve) => queueMicrotask(resolve))
    const measure = vi.spyOn(document.querySelector('input')!, 'getBoundingClientRect')
    for (let i = 0; i < 20; i++) window.dispatchEvent(new Event('scroll'))
    expect(schedule).toHaveBeenCalledTimes(1)
    expect(measure).not.toHaveBeenCalled()
    frames[0](0)
    expect(measure).toHaveBeenCalledTimes(1)
    observer.disconnect()
    vi.restoreAllMocks()
  })

  it('removes old listeners so a detached field can be enhanced again safely', async () => {
    const input = document.createElement('input')
    input.value = '保存内容'
    document.body.append(input)
    const observer = installInputClearButtons(document)
    input.remove()
    await new Promise<void>((resolve) => queueMicrotask(resolve))
    expect(input.dataset.clearButtonReady).toBeUndefined()
    document.body.append(input)
    await new Promise<void>((resolve) => queueMicrotask(resolve))
    const changed = vi.fn()
    input.addEventListener('input', changed)
    document.querySelector<HTMLButtonElement>('.global-input-clear')!.click()
    expect(changed).toHaveBeenCalledTimes(1)
    observer.disconnect()
    expect(input.dataset.clearButtonReady).toBeUndefined()
  })

  it('does not clear a field that became read-only after installation', () => {
    document.body.innerHTML = '<input value="不可更改" />'
    const observer = installInputClearButtons(document)
    const input = document.querySelector('input')!
    input.readOnly = true
    document.querySelector<HTMLButtonElement>('.global-input-clear')!.click()
    expect(input.value).toBe('不可更改')
    observer.disconnect()
  })
})
