<template>
  <div class="page-container">
    <el-tabs v-model="pageTab">
      <el-tab-pane label="我的执行" name="execution">
        <el-alert title="收藏仅保存文档入口；创建独立清单后，按本次项目安排日期、选择和进度。" type="info" :closable="false" />
        <el-alert v-if="mode === 'V4' && !enabled" title="新版写入暂未开放，可查看历史；不会自动恢复旧版写入或提醒。" type="warning" :closable="false" />
        <ExecutionBoard v-if="mode === 'V4'" :readonly="!enabled" />
        <div v-else>
          <el-alert title="原有待办继续保留在下方页签。启用新版后，原有进度转为只读历史，旧到期提醒停止；新版需明确创建独立清单。" :closable="false" />
          <el-button type="primary" :disabled="!enabled" :loading="switching" @click="enable">{{ enabled ? '启用新版执行清单' : '新版尚未开放' }}</el-button>
        </div>
      </el-tab-pane>
      <el-tab-pane label="规范文档库" name="docs">
        <KnowledgeDocList category="enterprise-compliance" category-name="企业合规要求" :allow-upload="true" />
      </el-tab-pane>
      <el-tab-pane label="模板维护" name="maintenance"><ComplianceTemplateEditor v-if="enabled" /></el-tab-pane>
      <el-tab-pane :label="mode === 'V4' ? '旧版只读历史' : '原有待办'" name="legacy"><LegacyCompliancePanel :key="mode" :readonly="mode === 'V4'" /></el-tab-pane>
    </el-tabs>
  </div>
</template>
<script setup>
import { onMounted, ref } from 'vue'
import { ElMessageBox } from 'element-plus'
import { getComplianceMode, enableComplianceV4 } from '../api/complianceV4'
import KnowledgeDocList from '../components/KnowledgeDocList.vue'
import ExecutionBoard from '../components/compliance/ExecutionBoard.vue'
import ComplianceTemplateEditor from '../components/compliance/ComplianceTemplateEditor.vue'
import LegacyCompliancePanel from '../components/compliance/LegacyCompliancePanel.vue'
const pageTab = ref('execution')
const mode = ref('V3'), enabled = ref(false), switching = ref(false)
async function loadMode() { const d = await getComplianceMode(); mode.value = d.mode; enabled.value = d.enabled }
async function enable() {
  try { await ElMessageBox.confirm('确认启用新版？原有个人进度保留为只读历史，旧到期提醒停止；不会自动搬移或推断项目。', '启用新版执行清单') } catch { return }
  switching.value = true
  try { await enableComplianceV4(); await loadMode() } catch { /* 拦截器提示 */ } finally { switching.value = false }
}
onMounted(loadMode)
</script>
<style scoped>
.page-container { padding: 16px; max-width: 1440px; margin: 0 auto; }
.el-alert { margin-bottom: 16px; }
</style>
