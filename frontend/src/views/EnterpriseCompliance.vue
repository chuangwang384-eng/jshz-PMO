<template>
  <div class="page-container">
    <KnowledgeDocList category="enterprise-compliance" category-name="企业合规要求" :allow-upload="false" />
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
