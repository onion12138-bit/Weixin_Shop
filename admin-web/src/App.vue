<script setup lang="ts">
import { useAuthStore } from './stores/auth'
import CatalogView from './views/CatalogView.vue'
import LoginView from './views/LoginView.vue'

const auth = useAuthStore()
</script>

<template>
  <LoginView v-if="!auth.isAuthenticated.value" />
  <div v-else class="admin-layout">
    <aside class="sidebar">
      <a class="brand-mark" href="#" aria-label="DIY 手串管理后台">
        <span class="brand-gem">串</span>
        <span>
          <strong>拾珠作坊</strong>
          <small>DIY BRACELET</small>
        </span>
      </a>

      <nav aria-label="主导航">
        <p>商品中心</p>
        <a class="active" href="#">
          <span>◉</span>
          商品目录
          <small>分类 / 散珠 / 搭配</small>
        </a>
        <p>交易履约</p>
        <a class="disabled" href="#" aria-disabled="true">
          <span>▤</span>
          订单管理
          <small>下一阶段</small>
        </a>
      </nav>

      <div class="sidebar-footer">
        <div class="admin-avatar">管</div>
        <div>
          <strong>本地管理员</strong>
          <small>产品演示环境</small>
        </div>
        <button class="logout-button" title="退出登录" @click="auth.logout">
          退出
        </button>
      </div>
    </aside>

    <main class="admin-content">
      <CatalogView />
    </main>
  </div>
</template>
