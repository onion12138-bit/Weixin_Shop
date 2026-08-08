<script setup lang="ts">
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
