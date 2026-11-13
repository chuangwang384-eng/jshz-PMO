<template>
  <div class="page-container">
    <el-tabs v-model="pageTab">
      <!-- 合规记事本（Owner 2026-11-10 需求：默认页签；收藏文档的已发布条目集中阅读/隐藏/推送项目） -->
      <el-tab-pane label="合规记事本" name="notebook">
        <ComplianceNotebook ref="notebookRef" @goto-docs="pageTab = 'docs'" />
      </el-tab-pane>
      <el-tab-pane label="规范文档库" name="docs">
        <KnowledgeDocList category="enterprise-compliance" category-name="企业合规要求" :allow-upload="true" @favorite-changed="onFavoriteChanged" />
      </el-tab-pane>
      <el-tab-pane v-if="auth.hasPermission('knowledge:maintain')" label="模板维护" name="maintenance">
        <ComplianceTemplateEditor v-if="enabled" />
        <MaintenanceBoard :active="pageTab === 'maintenance'" @changed="onFavoriteChanged" />
      </el-tab-pane>
      <el-tab-pane v-if="mode === 'V3'" label="原有待办" name="legacy"><LegacyCompliancePanel /></el-tab-pane>
    </el-tabs>
  </div>
</template>
<script setup>
import { onMounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { useAuthStore } from '../store/auth'
import { getComplianceMode } from '../api/complianceV4'
import KnowledgeDocList from '../components/KnowledgeDocList.vue'
import ComplianceNotebook from '../components/compliance/ComplianceNotebook.vue'
import ComplianceTemplateEditor from '../components/compliance/ComplianceTemplateEditor.vue'
import LegacyCompliancePanel from '../components/compliance/LegacyCompliancePanel.vue'
import MaintenanceBoard from '../components/compliance/MaintenanceBoard.vue'
const auth = useAuthStore()
const route = useRoute()
const pageTab = ref('notebook')
watch(() => route.query.tab, tab => {
  pageTab.value = tab === 'docs' ? 'docs' : 'notebook'
}, { immediate: true })
const notebookRef = ref(null)
// 收藏变更 → 记事本与文档筛选项立即刷新（需求规则3：取消收藏即移除该文档分组）
function onFavoriteChanged() {
  notebookRef.value?.reload()
}
// 旧「我的执行」已按需求规则6移除（1.0.37.34）：后端写接口关闭、提醒调度保持停用
const mode = ref(sessionStorage.getItem(`compliance-legacy-mode`) || 'V3')
async function loadMode() {
  try {
    const d = await getComplianceMode()
    mode.value = d.mode
    sessionStorage.setItem('compliance-legacy-mode', d.mode)
  } catch (e) { /* 拦截器提示 */ }
}
onMounted(loadMode)
</script>
<style scoped>
.page-container { padding: 16px; max-width: 1440px; margin: 0 auto; }
</style>
