const ELIGIBLE_INPUT = [
  'input:not([type="checkbox"])',
  ':not([type="radio"])',
  ':not([type="file"])',
  ':not([type="color"])',
  ':not([type="hidden"])',
  ':not([readonly])',
].join('')
const ELIGIBLE_FIELD = `${ELIGIBLE_INPUT}, textarea:not([readonly])`
type ClearableField = HTMLInputElement | HTMLTextAreaElement

interface ClearButtonPair {
  input: ClearableField
  button: HTMLButtonElement
  sync: () => void
  apply: (rect: DOMRect) => void
  dispose: () => void
}

/** 为后台全部可编辑输入框统一补充右侧清除按钮，包括动态弹窗中的输入框。 */
export function installInputClearButtons(root: ParentNode = document): MutationObserver {
  const ownerDocument = root instanceof Document ? root : root.ownerDocument || document
  const pairs = new Set<ClearButtonPair>()
  let frame: number | null = null

  const enhance = (scope: ParentNode): void => {
    const inputs: ClearableField[] = [
      ...((scope instanceof HTMLInputElement || scope instanceof HTMLTextAreaElement)
        && scope.matches(ELIGIBLE_FIELD) ? [scope] : []),
      ...Array.from(scope.querySelectorAll<ClearableField>(ELIGIBLE_FIELD)),
    ]
    inputs.forEach((input) => {
      if (input.dataset.clearButtonReady === 'true') return
      if (input.closest('.search-field-with-clear')) return
      input.dataset.clearButtonReady = 'true'
      input.classList.add('has-clear-button')

      const button = ownerDocument.createElement('button')
      button.type = 'button'
      button.className = 'global-input-clear'
      button.textContent = '×'
      button.setAttribute(
        'aria-label',
        `清空${input.getAttribute('aria-label') || input.placeholder || '输入内容'}`,
      )
      ownerDocument.body.append(button)

      const apply = (rect: DOMRect): void => {
        button.hidden = input.value.length === 0 || input.disabled || input.readOnly || !input.isConnected
          || rect.width === 0 || rect.height === 0
        button.style.top = `${rect.top + rect.height / 2}px`
        button.style.left = `${rect.right - 31}px`
      }
      const sync = (): void => apply(input.getBoundingClientRect())
      const dispose = (): void => {
        input.removeEventListener('input', sync)
        input.removeEventListener('change', sync)
        input.removeEventListener('focus', sync)
        delete input.dataset.clearButtonReady
        input.classList.remove('has-clear-button')
        button.remove()
      }
      const pair = { input, button, sync, apply, dispose }
      pairs.add(pair)

      button.addEventListener('pointerdown', (event) => event.preventDefault())
      button.addEventListener('click', () => {
        if (input.disabled || input.readOnly) return
        input.value = ''
        input.dispatchEvent(new Event('input', { bubbles: true }))
        input.dispatchEvent(new Event('change', { bubbles: true }))
        input.focus()
        sync()
      })
      input.addEventListener('input', sync)
      input.addEventListener('change', sync)
      input.addEventListener('focus', sync)
      sync()
    })
  }

  // 滚动和 DOM 更新同一帧只测量一次；先读尺寸再写样式，避免逐字段强制重排。
  const syncAll = (): void => {
    if (frame !== null) return
    frame = window.requestAnimationFrame(() => {
      frame = null
      const measurements = Array.from(pairs, (pair) => ({ pair, rect: pair.input.getBoundingClientRect() }))
      measurements.forEach(({ pair, rect }) => pair.apply(rect))
    })
  }
  const removeDetached = (): void => {
    pairs.forEach((pair) => {
      if (pair.input.isConnected) return
      pair.dispose()
      pairs.delete(pair)
    })
  }

  enhance(root)
  const observer = new MutationObserver((records) => {
    records.forEach((record) => record.addedNodes.forEach((node) => {
      if (node instanceof Element) enhance(node)
    }))
    removeDetached()
    syncAll()
  })
  observer.observe(root, { childList: true, subtree: true, attributes: true, attributeFilter: ['disabled', 'readonly'] })
  window.addEventListener('resize', syncAll)
  window.addEventListener('scroll', syncAll, true)
  const disconnect = observer.disconnect.bind(observer)
  observer.disconnect = () => {
    disconnect()
    window.removeEventListener('resize', syncAll)
    window.removeEventListener('scroll', syncAll, true)
    if (frame !== null) window.cancelAnimationFrame(frame)
    frame = null
    pairs.forEach((pair) => pair.dispose())
    pairs.clear()
  }
  return observer
}
