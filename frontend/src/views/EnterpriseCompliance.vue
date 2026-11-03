<template>
  <div class="page-container">
    <el-tabs v-model="pageTab" class="page-tabs">
    <!-- 页签 1：我的执行（v4 可复用清单；替换 v3 旧待办区） -->
    <el-tab-pane label="我的执行" name="execution">
    <el-card shadow="never" class="ui-card todo-card">
      <template #header>
        <div class="sec-head">
          <span>我的执行清单（企业合规）</span>
          <span class="sec-sub">口径：我创建的执行清单（项目用途 / 个人用途）；收藏文档仅保存入口，"用于项目"才创建待办</span>
        </div>
      </template>
      <ExecutionBoard v-if="v4Ready" />
      <el-alert v-else type="info" :closable="false" title="正在加载执行清单…" />
    </el-card>
    </el-tab-pane>

    <!-- 页签 2：规范文档库 -->
    <el-tab-pane label="规范文档库" name="docs">
    <el-card shadow="never" class="ui-card doc-card">
      <template #header>
        <div class="sec-head">
          <span>合规文档库<span class="sec-sub" style="margin-left: 8px">收藏（关注）保存入口；需要执行时点「用于本次」创建清单</span></span>
        </div>
      </template>
      <KnowledgeDocList category="enterprise-compliance" category-name="企业合规要求" :allow-upload="true" @favorite-changed="onFavoriteChanged" />
    </el-card>
    </el-tab-pane>

    <!-- 页签 3：模板维护（仅维护人可见） -->
    <el-tab-pane v-if="isMaintainer" label="模板维护" name="maintenance">
    <el-card shadow="never" class="ui-card board-card">
      <template #header>
        <div class="sec-head">
          <span>模板维护<span class="sec-badge">仅维护人可见</span></span>
        </div>
      </template>
      <MaintenanceBoard />
    </el-card>
    </el-tab-pane>
    </el-tabs>

    <!-- 维护人全局看板入口+看板（仅 knowledge:maintain 可见） -->
    <el-card v-if="isMaintainer" shadow="never" class="ui-card board-card">
      <template #header>
        <div class="sec-head">
          <span>维护人全局看板<span class="sec-badge">仅维护人可见</span></span>
        </div>
      </template>
      <MaintenanceBoard />
    </el-card>

    <!-- 文档列表（保留 + 收藏=关注） -->
    <el-card shadow="never" class="ui-card doc-card">
      <template #header>
        <div class="sec-head">
          <span>合规文档库<span class="sec-sub" style="margin-left: 8px">收藏（关注）文档后，其已发布事项自动进入我的待办</span></span>
        </div>
      </template>
      <KnowledgeDocList category="enterprise-compliance" category-name="企业合规要求" :allow-upload="true" />
    </el-card>

    <!-- 个人备注弹窗 -->
    <el-dialog v-model="remarkVisible" title="个人备注（仅自己可见）" width="460px" destroy-on-close>
      <el-input v-model="remarkDraft" type="textarea" :rows="4" maxlength="500" show-word-limit placeholder="记录个人处理进展、联系信息等" />
      <template #footer>
        <el-button @click="remarkVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="saveRemark">保存</el-button>
      </template>
    </el-dialog>

    <!-- 阻塞原因弹窗 -->
    <el-dialog v-model="blockVisible" title="标记阻塞原因（进度按处理中统计）" width="460px" destroy-on-close>
      <el-select v-model="blockPreset" placeholder="预设原因（可空）" clearable style="width: 100%">
        <el-option label="等待外部资料" value="等待外部资料" />
        <el-option label="等待其他部门" value="等待其他部门" />
        <el-option label="等待客户确认" value="等待客户确认" />
        <el-option label="其他原因" value="其他原因" />
      </el-select>
      <el-input v-model="blockCustom" placeholder="自定义原因（可与预设同时填写）" style="margin-top: 10px" />
      <template #footer>
        <el-button @click="blockVisible = false">取消</el-button>
        <el-button type="warning" :loading="saving" @click="saveBlock">标记阻塞</el-button>
      </template>
    </el-dialog>

    <!-- 提醒设置弹窗 -->
    <el-dialog v-model="reminderVisible" title="个人提醒设置" width="420px" destroy-on-close>
      <div class="rem-row">
        <span>启用提醒</span>
        <el-switch v-model="remindEnabled" />
      </div>
      <div class="rem-row">
        <span>提前天数（1-30，另加到期当天各提醒一次）</span>
        <el-input-number v-model="remindDays" :min="1" :max="30" size="small" />
      </div>
      <template #footer>
        <el-button @click="reminderVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="saveReminder">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import KnowledgeDocList from '../components/KnowledgeDocList.vue'
import MyTodoSection from '../components/compliance/MyTodoSection.vue'
import MaintenanceBoard from '../components/compliance/MaintenanceBoard.vue'
import { myComplianceTodo, updateMyComplianceProgress, updateMyComplianceReminder } from '../api/compliance'

const isMaintainer = ref(false)
const todoStat = ref({})
const todoRows = ref([])
const todoLoading = ref(false)
const saving = ref(false)

const remarkVisible = ref(false)
const remarkRow = ref(null)
const remarkDraft = ref('')
const blockVisible = ref(false)
const blockRow = ref(null)
const blockPreset = ref(null)
const blockCustom = ref('')
const reminderVisible = ref(false)
const reminderRow = ref(null)
const remindEnabled = ref(true)
const remindDays = ref(3)

async function loadTodo() {
  todoLoading.value = true
  try {
    const d = await myComplianceTodo()
    todoStat.value = { todoCount: d?.todoCount, nearDueCount: d?.nearDueCount, overdueCount: d?.overdueCount }
    todoRows.value = d?.items || []
  } finally {
    todoLoading.value = false
  }
}

async function onChangeProgress(row, v) {
  await updateMyComplianceProgress(row.id, { progressStatus: v })
  ElMessage.success('进度已更新（仅自己可见）')
  loadTodo()
}
function openRemark(row) {
  remarkRow.value = row
  remarkDraft.value = row.myRemark || ''
  remarkVisible.value = true
}
async function saveRemark() {
  await updateMyComplianceProgress(remarkRow.value.id, { remark: remarkDraft.value })
  ElMessage.success('备注已保存（仅自己可见）')
  remarkVisible.value = false
  loadTodo()
}
function openBlock(row) {
  blockRow.value = row
  blockPreset.value = null
  blockCustom.value = row.blockReason || ''
  blockVisible.value = true
}
async function saveBlock() {
  const reason = (blockPreset.value ? blockPreset.value : '') + (blockCustom.value ? (blockPreset.value ? '：' : '') + blockCustom.value : '')
  if (!reason) return ElMessage.warning('请填写阻塞原因')
  await updateMyComplianceProgress(blockRow.value.id, { blockReason: reason, progressStatus: 'PROCESSING' })
  ElMessage.success('已标记阻塞（进度按处理中统计）')
  blockVisible.value = false
  loadTodo()
}
function openReminder(row) {
  reminderRow.value = row
  remindEnabled.value = row.remindEnabled === 1
  remindDays.value = row.remindDays || 3
  reminderVisible.value = true
}
async function saveReminder() {
  await updateMyComplianceReminder(reminderRow.value.id, { remindEnabled: remindEnabled.value ? 1 : 0, remindDays: remindDays.value })
  ElMessage.success('提醒设置已保存')
  reminderVisible.value = false
  loadTodo()
}

// 维护人判定：登录后由 /api/auth/me 的 permissions 判定（全局看板入口仅维护人可见）
async function loadMaintainerFlag() {
  try {
    const me = await fetch('/api/auth/me', { credentials: 'include' }).then((x) => x.json())
    isMaintainer.value = !!(me.data && (me.data.isAdmin || (me.data.permissions || []).includes('knowledge:maintain')))
  } catch (e) {
    isMaintainer.value = false
  }
}

onMounted(() => {
  loadTodo()
  loadMaintainerFlag()
})
</script>

<style scoped>
.page-container { padding: 16px; max-width: 1440px; margin: 0 auto; }
.ui-card { border: 1px solid rgba(0,0,0,0.06); box-shadow: 0 1px 2px rgba(0,0,0,0.04), 0 4px 12px rgba(0,0,0,0.05); border-radius: 10px; }
.todo-card, .board-card, .doc-card { margin-bottom: 16px; }
.sec-head { display: flex; align-items: center; font-weight: 600; color: #303133; }
.sec-sub { font-size: 12px; color: #909399; font-weight: 400; margin-left: 8px; }
.sec-badge { font-size: 11px; color: #337ECC; background: #ECF5FF; border-radius: 999px; padding: 1px 8px; margin-left: 8px; font-weight: 400; }
.rem-row { display: flex; justify-content: space-between; align-items: center; padding: 8px 0; font-size: 13px; color: #606266; }
</style>
