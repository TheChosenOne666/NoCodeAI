<template>
  <a-layout class="basic-layout">
    <!-- 顶部导航栏 -->
    <GlobalHeader v-if="showHeader" />
    <!-- 主要内容区域 -->
    <a-layout-content class="main-content" :class="{ 'no-header': !showHeader }">
      <router-view />
    </a-layout-content>
    <!-- 底部版权信息 -->
    <GlobalFooter v-if="showHeader" />
  </a-layout>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import GlobalHeader from '@/components/GlobalHeader.vue'
import GlobalFooter from '@/components/GlobalFooter.vue'

const route = useRoute()
const hideHeaderRoutes = ['/user/login', '/user/register']
const showHeader = computed(() => !hideHeaderRoutes.includes(route.path))
</script>

<style scoped>
.basic-layout {
  background: #f8fafc;
}

.main-content {
  width: 100%;
  padding: 0;
  background: none;
  margin: 0;
  min-height: 100vh;
}

.main-content.no-header {
  padding: 0;
  margin: 0;
}
</style>
