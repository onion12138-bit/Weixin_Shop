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
  }

  interface Input {
    detail: {
      value: string
    }
  }
}

declare function App<T extends Record<string, unknown>>(options: T): void

declare function getApp<T>(): T

declare function Page<T extends { data: Record<string, unknown> }>(
  options: T &
    ThisType<
      T & {
        data: T['data']
        setData(data: Partial<T['data']>): void
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

  getRandomValues?(array: Uint8Array): Uint8Array

  navigateTo(options: { url: string }): void

  stopPullDownRefresh(): void

  showToast(options: {
    title: string
    icon?: 'success' | 'error' | 'loading' | 'none'
  }): void

  createSelectorQuery(): {
    select(selector: string): {
      fields(
        fields: Record<string, boolean>,
        callback: (result: unknown) => void,
      ): unknown
    }
    exec(callback?: () => void): void
  }

  canvasToTempFilePath(options: {
    canvas?: unknown
    fileType?: 'jpg' | 'png'
    quality?: number
    success(response: { tempFilePath: string }): void
    fail(error: { errMsg: string }): void
  }): void
}
