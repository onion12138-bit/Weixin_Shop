<script setup lang="ts">
import { computed, ref } from 'vue'
import BeadCatalogPanel from './BeadCatalogPanel.vue'
import CategoryCatalogPanel from './CategoryCatalogPanel.vue'
import PackageCatalogPanel from './PackageCatalogPanel.vue'

type CatalogSection = 'beads' | 'packages' | 'categories'

const activeSection = ref<CatalogSection>('beads')

/**
 * 用明确映射选择面板，不把组件名称拼接成字符串，避免模板重构时失去类型检查。
 */
const activePanel = computed(() => ({
  beads: BeadCatalogPanel,
  packages: PackageCatalogPanel,
  categories: CategoryCatalogPanel,
})[activeSection.value])
</script>

<template>
  <section class="catalog-workspace">
    <nav class="catalog-tabs" aria-label="商品目录模块">
      <button
        data-test="tab-beads"
        :class="{ active: activeSection === 'beads' }"
        @click="activeSection = 'beads'"
      >
        散珠商品
      </button>
      <button
        data-test="tab-packages"
        :class="{ active: activeSection === 'packages' }"
        @click="activeSection = 'packages'"
      >
        成品搭配
      </button>
      <button
        data-test="tab-categories"
        :class="{ active: activeSection === 'categories' }"
        @click="activeSection = 'categories'"
      >
        商品分类
      </button>
    </nav>

    <KeepAlive>
      <component :is="activePanel" />
    </KeepAlive>
  </section>
</template>
