<template>
  <div class="page-container">
    <KnowledgeDocList category="association-compliance" category-name="协会合规要求" :allow-upload="true" />
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
