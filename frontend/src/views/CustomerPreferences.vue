<template>
  <div class="page-container">
    <KnowledgeDocList category="customer-preferences" category-name="客户喜好" :allow-upload="false" />
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
