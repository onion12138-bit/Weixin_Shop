export interface EditorHistory<T> {
  current: () => T
  push: (next: T) => void
  undo: () => T
  redo: () => T
  canUndo: () => boolean
  canRedo: () => boolean
}

export function createHistory<T>(
  initial: T,
  limit = 50,
): EditorHistory<T> {
  let currentValue = clone(initial)
  const past: T[] = []
  const future: T[] = []

  function push(next: T): void {
    past.push(clone(currentValue))
    while (past.length > limit) {
      past.shift()
    }
    currentValue = clone(next)
    future.length = 0
  }

  function undo(): T {
    const previous = past.pop()
    if (previous === undefined) return clone(currentValue)
    future.unshift(clone(currentValue))
    currentValue = clone(previous)
    return clone(currentValue)
  }

  function redo(): T {
    const next = future.shift()
    if (next === undefined) return clone(currentValue)
    past.push(clone(currentValue))
    while (past.length > limit) {
      past.shift()
    }
    currentValue = clone(next)
    return clone(currentValue)
  }

  return {
    current: () => clone(currentValue),
    push,
    undo,
    redo,
    canUndo: () => past.length > 0,
    canRedo: () => future.length > 0,
  }
}

function clone<T>(value: T): T {
  return JSON.parse(JSON.stringify(value)) as T
}
