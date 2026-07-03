import vue from '@vitejs/plugin-vue'
import { defineConfig } from 'vitest/config'

/**
 * 管理后台的构建与单元测试共用配置。
 *
 * jsdom 提供浏览器 DOM，使 Vue 组件测试无需启动真实浏览器。
 */
export default defineConfig({
  plugins: [vue()],
  test: {
    environment: 'jsdom',
  },
})
