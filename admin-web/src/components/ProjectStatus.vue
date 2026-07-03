<script setup lang="ts">
/**
 * 第一阶段只展示三端工程是否已建立。
 *
 * 后续接入真实接口后，state 将由后端健康检查和版本信息动态生成；
 * 当前保持静态数据，避免基础页面依赖尚未启动的数据库与业务服务。
 */
const applications = [
  { name: 'Spring Boot 后端', state: '基础工程' },
  { name: 'Vue 管理后台', state: '基础工程' },
  { name: '微信小程序', state: '基础工程' },
] as const
</script>

<template>
  <section aria-labelledby="project-status-title" class="status-panel">
    <h2 id="project-status-title">项目组成</h2>
    <ul>
      <li v-for="application in applications" :key="application.name">
        <strong>{{ application.name }}</strong>
        <span>{{ application.state }}</span>
      </li>
    </ul>
  </section>
</template>
