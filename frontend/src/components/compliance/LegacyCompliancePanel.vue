<template>
  <div v-loading="loading">
    <el-alert :title="readonly ? '只读历史：保留旧进度和个人备注，不再发送旧到期提醒；新项目请创建新版清单。' : '原有待办仍按旧规则运行，可显式启用新版。'" :closable="false" />
    <!-- 需求21a：来源文档下架/删除的事项仍展示，但标注来源状态并禁止继续操作 -->
    <el-alert
      v-if="unavailableCount > 0"
      type="warning"
      :closable="false"
      :title="`${unavailableCount} 条待办的来源文档已下架或删除，仅供历史参考，不能更新进度`"
    />
    <el-checkbox v-model="includeCompleted">显示已完成</el-checkbox>
    <el-button @click="load">刷新</el-button>
    <el-table :data="visibleRows">
      <el-table-column prop="title" label="事项" min-width="180" />
      <el-table-column label="来源状态" min-width="160">
        <template #default="{ row }">
          <el-tag v-if="row.sourceNotice" type="warning" size="small">{{ row.sourceNotice }}</el-tag>
          <span v-else>正常</span>
        </template>
      </el-table-column>
      <el-table-column label="我的进度"><template #default="{ row }">{{ row.progress_status || row.myProgress }}</template></el-table-column>
      <el-table-column label="个人备注"><template #default="{ row }">{{ row.remark || row.myRemark || '—' }}</template></el-table-column>
      <el-table-column v-if="!readonly" label="操作">
        <template #default="{ row }">
          <el-button :disabled="row.actionable === false" @click="edit(row)">编辑原有事项</el-button>
        </template>
      </el-table-column>
    </el-table>
    <el-dialog v-model="editing" title="编辑原有事项" width="500px">
      <el-form label-width="100px">
        <el-form-item label="个人进度"><el-select v-model="form.progress"><el-option label="未处理" value="UNPROCESSED" /><el-option label="处理中" value="PROCESSING" /><el-option label="已完成" value="COMPLETED" /></el-select></el-form-item>
        <el-form-item label="个人备注"><el-input v-model="form.remark" type="textarea" maxlength="500" /></el-form-item>
        <el-form-item label="提醒"><el-switch v-model="form.enabled" /></el-form-item>
        <el-form-item label="提前天数"><el-input-number v-model="form.days" :min="1" :max="30" /></el-form-item>
      </el-form>
      <template #footer><el-button :loading="saving" type="primary" @click="save">保存</el-button></template>
    </el-dialog>
  </div>
</template>
<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { listLegacyHistory } from '../../api/complianceV4'
import { myComplianceTodo, updateMyComplianceProgress, updateMyComplianceReminder } from '../../api/compliance'
const props = defineProps({ readonly: Boolean })
const rows = ref([]), loading = ref(false), saving = ref(false), editing = ref(false), includeCompleted = ref(false)
const form = reactive({ id: null, progress: 'UNPROCESSED', remark: '', enabled: true, days: 3 })
const visibleRows = computed(() => rows.value.filter(r => includeCompleted.value || (r.progress_status || r.myProgress) !== 'COMPLETED'))
// 只读历史接口不返回来源状态，按可用处理；待办接口返回 actionable 字段
const unavailableCount = computed(() => rows.value.filter(r => r.actionable === false).length)
async function load() { loading.value = true; try { rows.value = props.readonly ? await listLegacyHistory() : (await myComplianceTodo()).items || [] } catch { /* 拦截器提示 */ } finally { loading.value = false } }
function edit(row) { Object.assign(form, { id: row.id, progress: row.myProgress, remark: row.myRemark || '', enabled: row.remindEnabled !== 0, days: row.remindDays || 3 }); editing.value = true }
async function save() { saving.value = true; try { await updateMyComplianceProgress(form.id, { progressStatus: form.progress, remark: form.remark }); await updateMyComplianceReminder(form.id, { remindEnabled: form.enabled ? 1 : 0, remindDays: form.days }); editing.value = false; await load() } catch { /* 保留输入，重试为幂等更新 */ } finally { saving.value = false } }
onMounted(load)
</script>
