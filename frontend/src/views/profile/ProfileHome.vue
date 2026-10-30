<template>
  <div class="profile-home">
    <!-- 分类子页（/profile/xxx）：渲染对应子路由页面；父路由（/profile）渲染信息卡主页 -->
    <router-view />
    <ProfileCard v-if="isRootPage" />
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import ProfileCard from './ProfileCard.vue'

const route = useRoute()

// 父路由（/profile）展示主页内容；子路由（/profile/xxx）仅渲染子页
const isRootPage = computed(() => route.name === 'profile')
</script>
