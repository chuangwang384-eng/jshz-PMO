<template>
  <el-sub-menu v-if="item.children?.length" :index="item.path">
    <template #title>{{ item.title }}</template>
    <SideMenuItem v-for="(c, i) in item.children" :key="i" :item="c" />
  </el-sub-menu>
  <el-menu-item v-else :index="item.path">{{ item.title }}</el-menu-item>
</template>

<script setup>
defineOptions({ name: 'SideMenuItem' })
defineProps({
  item: {
    type: Object,
    required: true
  }
})
</script>
