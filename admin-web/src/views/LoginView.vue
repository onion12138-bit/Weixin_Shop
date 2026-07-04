<script setup lang="ts">
import { reactive, ref } from 'vue'
import { ApiError } from '../api/http'
import { useAuthStore } from '../stores/auth'

const emit = defineEmits<{
  success: []
}>()

const auth = useAuthStore()
const form = reactive({
  username: 'admin',
  password: 'Admin@123',
})
const errorMessage = ref('')

async function submit(): Promise<void> {
  errorMessage.value = ''
  try {
    await auth.login(form.username.trim(), form.password)
    emit('success')
  } catch (error) {
    errorMessage.value =
      error instanceof ApiError ? error.message : '登录失败，请检查后端服务'
  }
}
</script>

<template>
  <main class="login-page">
    <section class="login-brand">
      <p class="eyebrow">DIY BRACELET STUDIO</p>
      <h1>把每颗珠子的故事，<br />做成可管理的商品。</h1>
      <p>
        一个面向手串定制业务的商品工作台。维护分类、散珠、成品搭配与后续订单履约。
      </p>
      <div class="brand-orbit" aria-hidden="true">
        <span v-for="index in 9" :key="index" />
      </div>
    </section>

    <section class="login-card" aria-labelledby="login-title">
      <div>
        <p class="section-kicker">ADMIN CONSOLE</p>
        <h2 id="login-title">登录管理后台</h2>
        <p class="muted">使用本地演示管理员账号进入商品目录。</p>
      </div>

      <form @submit.prevent="submit">
        <label>
          <span>用户名</span>
          <input
            v-model="form.username"
            name="username"
            autocomplete="username"
            required
          />
        </label>
        <label>
          <span>密码</span>
          <input
            v-model="form.password"
            name="password"
            type="password"
            autocomplete="current-password"
            required
          />
        </label>
        <p v-if="errorMessage" class="form-error" role="alert">
          {{ errorMessage }}
        </p>
        <button class="primary-button full-width" :disabled="auth.state.loading">
          {{ auth.state.loading ? '正在登录…' : '进入工作台' }}
        </button>
      </form>

      <p class="login-hint">
        当前为本地产品阶段，账号可通过后端环境变量修改。
      </p>
    </section>
  </main>
</template>
