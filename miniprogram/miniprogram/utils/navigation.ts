type NavigationOptions = {
  url: string
  success?: () => void
  fail?: (error: { errMsg: string }) => void
}

const pendingNavigation = new WeakMap<object, object>()

export function isNavigationPending(page: object): boolean {
  return pendingNavigation.has(page)
}

// 在 onShow 中释放：跳转成功后仍需拦住原页面队列里尚未处理的点击。
export function resetNavigation(page: object): void {
  pendingNavigation.delete(page)
}

export function navigateOnce(
  page: object,
  options: NavigationOptions,
  method: 'navigateTo' | 'redirectTo' = 'navigateTo',
): boolean {
  if (isNavigationPending(page)) return false
  const token = {}
  pendingNavigation.set(page, token)
  const fail = (error = { errMsg: '页面暂时无法打开' }) => {
    if (pendingNavigation.get(page) !== token) return
    pendingNavigation.delete(page)
    if (options.fail) options.fail(error)
    else wx.showToast({ title: '页面暂时无法打开，请重试', icon: 'none' })
  }
  try {
    wx[method]({
      url: options.url,
      success: () => {
        if (pendingNavigation.get(page) === token) options.success?.()
      },
      fail,
    })
    return true
  } catch (error) {
    fail({ errMsg: error instanceof Error ? error.message : '页面暂时无法打开' })
    return false
  }
}
