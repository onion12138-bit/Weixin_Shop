<script setup lang="ts">
import { ref } from 'vue'
import { useAuthStore } from './stores/auth'
import AiConfigurationView from './views/AiConfigurationView.vue'
import AiRecommendationLogsView from './views/AiRecommendationLogsView.vue'
import CatalogView from './views/CatalogView.vue'
import CustomerBenefitsView from './views/CustomerBenefitsView.vue'
import DiySettingsView from './views/DiySettingsView.vue'
import LoginView from './views/LoginView.vue'
import OrderView from './views/OrderView.vue'

const auth = useAuthStore()
type AdminView = 'catalog' | 'diy' | 'orders' | 'customers' | 'ai' | 'ai-logs'
const activeView = ref<AdminView>('catalog')
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
        <button
          :class="{ active: activeView === 'catalog' }"
          @click="activeView = 'catalog'"
        >
          <span>◉</span>
          商品目录
          <small>分类 / 散珠 / 搭配</small>
        </button>
        <button
          :class="{ active: activeView === 'diy' }"
          @click="activeView = 'diy'"
        >
          <span>◎</span>
          DIY 规则
          <small>尺寸 / 工艺费 / 绳材</small>
        </button>
        <p>交易履约</p>
        <button
          :class="{ active: activeView === 'orders' }"
          @click="activeView = 'orders'"
        >
          <span>▤</span>
          订单管理
          <small>列表 / 详情 / 发货</small>
        </button>
        <p>客户运营</p>
        <button
          :class="{ active: activeView === 'customers' }"
          @click="activeView = 'customers'"
        >
          <span>♡</span>
          顾客与权益
          <small>地址 / 优惠券 / 发券</small>
        </button>
        <p>平台能力</p>
        <button
          :class="{ active: activeView === 'ai' }"
          @click="activeView = 'ai'"
        >
          <span>✦</span>
          AI 模型
          <small>DeepSeek / 连接配置</small>
        </button>
        <button
          :class="{ active: activeView === 'ai-logs' }"
          @click="activeView = 'ai-logs'"
        >
          <span>◫</span>
          AI 调用日志
          <small>输入 / 处理 / 输出</small>
        </button>
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
      <CatalogView v-if="activeView === 'catalog'" />
      <DiySettingsView v-else-if="activeView === 'diy'" />
      <OrderView v-else-if="activeView === 'orders'" />
      <CustomerBenefitsView v-else-if="activeView === 'customers'" />
      <AiConfigurationView v-else-if="activeView === 'ai'" />
      <AiRecommendationLogsView v-else />
    </main>
  </div>
</template>
