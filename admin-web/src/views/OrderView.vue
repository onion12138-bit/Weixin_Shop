<script setup lang="ts">
// 管理订单列表与详情之间的切换，返回列表时重新加载最新状态。
import { ref } from 'vue'
import OrderDetailView from './OrderDetailView.vue'
import OrderListView from './OrderListView.vue'

const selectedOrderId = ref<number | null>(null)
</script>

<template>
  <OrderListView
    v-if="selectedOrderId === null"
    @open="selectedOrderId = $event"
  />
  <OrderDetailView
    v-else
    :order-id="selectedOrderId"
    @back="selectedOrderId = null"
  />
</template>
