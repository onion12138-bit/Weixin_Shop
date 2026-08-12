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
}

/** 为后台全部可编辑输入框统一补充右侧清除按钮，包括动态弹窗中的输入框。 */
export function installInputClearButtons(root: ParentNode = document): MutationObserver {
  const ownerDocument = root instanceof Document ? root : root.ownerDocument || document
  const pairs = new Set<ClearButtonPair>()

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

      const sync = (): void => {
        const rect = input.getBoundingClientRect()
        button.hidden = input.value.length === 0 || input.disabled || !input.isConnected
          || rect.width === 0 || rect.height === 0
        button.style.top = `${rect.top + rect.height / 2}px`
        button.style.left = `${rect.right - 31}px`
      }
      const pair = { input, button, sync }
      pairs.add(pair)

      button.addEventListener('pointerdown', (event) => event.preventDefault())
      button.addEventListener('click', () => {
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

  const syncAll = (): void => pairs.forEach((pair) => pair.sync())
  const removeDetached = (): void => {
    pairs.forEach((pair) => {
      if (pair.input.isConnected) return
      pair.button.remove()
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
  observer.observe(root, { childList: true, subtree: true })
  window.addEventListener('resize', syncAll)
  window.addEventListener('scroll', syncAll, true)
  const disconnect = observer.disconnect.bind(observer)
  observer.disconnect = () => {
    disconnect()
    window.removeEventListener('resize', syncAll)
    window.removeEventListener('scroll', syncAll, true)
    pairs.forEach((pair) => pair.button.remove())
    pairs.clear()
  }
  return observer
}
