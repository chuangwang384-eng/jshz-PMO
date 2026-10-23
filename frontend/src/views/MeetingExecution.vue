<template>
  <div class="page-container">
    <KnowledgeDocList category="meeting-execution" category-name="会议执行" :allow-upload="false" />
  </div>
</template>

<script setup>
import KnowledgeDocList from '../components/KnowledgeDocList.vue'
</script>

<style scoped>
.page-container {
  padding: 16px;
}
</style>
