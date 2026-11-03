<template>
  <div class="page-container">
    <el-tabs v-model="pageTab">
      <el-tab-pane label="我的执行" name="execution">
        <el-alert title="收藏仅保存文档入口；创建独立清单后，按本次项目安排日期、选择和进度。" type="info" :closable="false" />
        <ExecutionBoard />
      </el-tab-pane>
      <el-tab-pane label="规范文档库" name="docs">
        <KnowledgeDocList category="enterprise-compliance" category-name="企业合规要求" :allow-upload="true" />
      </el-tab-pane>
      <el-tab-pane label="模板维护" name="maintenance"><ComplianceTemplateEditor /></el-tab-pane>
    </el-tabs>
  </div>
</template>
<script setup>
import { ref } from 'vue'
import KnowledgeDocList from '../components/KnowledgeDocList.vue'
import ExecutionBoard from '../components/compliance/ExecutionBoard.vue'
import ComplianceTemplateEditor from '../components/compliance/ComplianceTemplateEditor.vue'
const pageTab = ref('execution')
</script>
<style scoped>
.page-container { padding: 16px; max-width: 1440px; margin: 0 auto; }
.el-alert { margin-bottom: 16px; }
</style>
