/**
 * 本项目当前使用到的微信小程序运行时最小类型集合。
 *
 * 这里只声明实际调用的 API，避免为了少量接口引入整套运行时依赖。若后续接入
 * 登录、支付或订阅消息，应改用官方 `miniprogram-api-typings` 并删除本文件。
 */
declare namespace WechatMiniprogram {
  interface TouchEvent {
    currentTarget: {
      dataset: Record<string, string | number | boolean | undefined>
    }
    touches: Array<{
      x: number
      y: number
    }>
    changedTouches: Array<{
      x: number
      y: number
    }>
  }

  interface Input {
    detail: {
      value: string
    }
  }

  interface PickerChange {
    detail: {
      value: string
    }
  }

  interface ChooseAvatarEvent {
    detail: {
      avatarUrl: string
    }
  }

  interface SelectorQuery {
    select(selector: string): SelectorQueryNode
    exec(callback?: () => void): void
  }

  interface SelectorQueryNode {
    fields(
      fields: Record<string, boolean>,
      callback: (result: unknown) => void,
    ): SelectorQuery
  }
}

declare function App<T extends Record<string, unknown>>(options: T): void

declare function getApp<T>(): T

declare function setTimeout(callback: () => void, delay: number): number

declare function clearTimeout(handle: number): void

declare function Page<T extends { data: Record<string, unknown> }>(
  options: T &
    ThisType<
      T & {
        data: T['data']
        setData(data: Partial<T['data']>, callback?: () => void): void
      }
    >,
): void

declare const wx: {
  request<T>(options: {
    url: string
    method?: 'GET' | 'POST' | 'PUT' | 'PATCH' | 'DELETE'
    data?: unknown
    header?: Record<string, string>
    timeout?: number
    success(response: { data: T; statusCode: number }): void
    fail(error: { errMsg: string }): void
  }): void

  uploadFile(options: {
    url: string
    filePath: string
    name: string
    header?: Record<string, string>
    success(response: { data: string; statusCode: number }): void
    fail(error: { errMsg: string }): void
  }): void

  getStorageSync(key: string): string

  setStorageSync(key: string, value: string): void

  removeStorageSync(key: string): void

  getUserCryptoManager?(): {
    getRandomValues(options: {
      length: number
      success(result: { randomValues: ArrayBuffer }): void
      fail(error: { errMsg: string }): void
    }): void
  }

  navigateTo(options: {
    url: string
    success?(): void
    fail?(error: { errMsg: string }): void
  }): void

  redirectTo(options: {
    url: string
    success?(): void
    fail?(error: { errMsg: string }): void
  }): void

  navigateBack(options?: { delta?: number; fail?(error: { errMsg: string }): void }): void

  stopPullDownRefresh(): void

  setClipboardData(options: {
    data: string
    success?(): void
    fail?(error: { errMsg: string }): void
  }): void

  pageScrollTo(options: {
    scrollTop: number
    duration?: number
  }): void

  showToast(options: {
    title: string
    icon?: 'success' | 'error' | 'loading' | 'none'
  }): void

  showModal(options: {
    title: string
    content: string
    confirmText?: string
    cancelText?: string
    success(response: { confirm: boolean; cancel: boolean }): void
    fail?(error: { errMsg: string }): void
  }): void

  showLoading(options: {
    title: string
    mask?: boolean
  }): void

  hideLoading(): void

  getSystemInfoSync(): {
    pixelRatio: number
  }

  createSelectorQuery(): WechatMiniprogram.SelectorQuery

  canvasToTempFilePath(options: {
    canvas?: unknown
    fileType?: 'jpg' | 'png'
    quality?: number
    success(response: { tempFilePath: string }): void
    fail(error: { errMsg: string }): void
  }): void
}
