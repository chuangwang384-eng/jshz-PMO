<template>
  <el-card shadow="never" class="employment-card">
    <template #header>
      <div class="ui-toolbar employment-header">
        <span>任职履历</span>
        <el-button v-if="canManage" type="primary" plain @click="openEditor()">新增任职</el-button>
      </div>
    </template>
    <el-alert v-if="failed" type="error" title="任职履历加载失败" :closable="false">
      <el-button link type="primary" @click="loadHistory">重试</el-button>
    </el-alert>
    <el-table v-else v-loading="loading" :data="entries" empty-text="暂无任职履历">
      <el-table-column prop="workUnit" label="单位" min-width="150" show-overflow-tooltip />
      <el-table-column prop="position" label="职务" min-width="120" show-overflow-tooltip />
      <el-table-column label="开始月份" min-width="108">
        <template #default="{ row }">{{ row.startMonth || '未知' }}</template>
      </el-table-column>
      <el-table-column label="结束月份" min-width="108">
        <template #default="{ row }">{{ row.endMonth || '至今' }}</template>
      </el-table-column>
      <el-table-column v-if="canManage" label="操作" width="115">
        <template #default="{ row }">
          <el-button link type="primary" :disabled="saving" @click="openEditor(row)">编辑</el-button>
          <el-popconfirm title="确认删除这条任职记录？" confirm-button-text="删除" cancel-button-text="取消" @confirm="removeEntry(row)">
            <template #reference>
              <el-button link type="danger" :disabled="saving">删除</el-button>
            </template>
          </el-popconfirm>
        </template>
      </el-table-column>
    </el-table>
  </el-card>

  <el-dialog v-model="dialogVisible" :title="entryId == null ? '新增任职' : '编辑任职'" width="min(480px, 92vw)" :before-close="closeEditor">
    <el-form :model="form" :disabled="saving" label-width="85px">
      <el-form-item label="单位" required>
        <el-input v-model.trim="form.workUnit" />
      </el-form-item>
      <el-form-item label="职务" required>
        <el-input v-model.trim="form.position" />
      </el-form-item>
      <el-form-item label="开始月份">
        <el-date-picker v-model="form.startMonth" type="month" value-format="YYYY-MM" placeholder="未知可留空" />
      </el-form-item>
      <el-form-item label="结束月份">
        <el-date-picker v-model="form.endMonth" type="month" value-format="YYYY-MM" placeholder="留空表示至今" />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button :disabled="saving" @click="dialogVisible = false">取消</el-button>
      <el-button type="primary" :loading="saving" @click="saveEntry">保存</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { deleteCustomerEmployment, listCustomerEmployment, saveCustomerEmployment } from '../api/customer'

const props = defineProps({
  customerId: { type: Number, required: true },
  canManage: { type: Boolean, default: false }
})

const entries = ref([])
const loading = ref(false)
const failed = ref(false)
const saving = ref(false)
const dialogVisible = ref(false)
const entryId = ref(null)
const form = reactive({ workUnit: '', position: '', startMonth: null, endMonth: null })
let loadVersion = 0

async function loadHistory() {
  const version = ++loadVersion
  loading.value = true
  failed.value = false
  try {
    const result = await listCustomerEmployment(props.customerId)
    if (version === loadVersion) entries.value = result || []
  } catch {
    if (version === loadVersion) failed.value = true
  } finally {
    if (version === loadVersion) loading.value = false
  }
}

function openEditor(row) {
  entryId.value = row?.id ?? null
  Object.assign(form, {
    workUnit: row?.workUnit || '',
    position: row?.position || '',
    startMonth: row?.startMonth ?? null,
    endMonth: row?.endMonth ?? null
  })
  dialogVisible.value = true
}

function closeEditor(done) {
  if (!saving.value) done()
}

async function saveEntry() {
  if (saving.value) return
  if (!form.workUnit || !form.position) {
    ElMessage.warning('请填写单位和职务')
    return
  }
  if (form.startMonth && form.endMonth && form.endMonth < form.startMonth) {
    ElMessage.warning('结束月份不能早于开始月份')
    return
  }
  saving.value = true
  try {
    await saveCustomerEmployment(props.customerId, entryId.value, { ...form })
    dialogVisible.value = false
    ElMessage.success('任职记录已保存')
    await loadHistory()
  } catch {
    // 请求层已显示服务端错误，保留输入便于修正。
  } finally {
    saving.value = false
  }
}

async function removeEntry(row) {
  if (saving.value) return
  saving.value = true
  try {
    await deleteCustomerEmployment(props.customerId, row.id)
    ElMessage.success('任职记录已删除')
    await loadHistory()
  } catch {
    // 请求层已显示服务端错误。
  } finally {
    saving.value = false
  }
}

onMounted(loadHistory)
</script>

<style scoped>
.employment-card {
  margin-bottom: 14px;
}

.employment-header {
  justify-content: space-between;
}
</style>
