<template>
  <div class="page">
    <div class="page-header">
      <div class="page-title">AI 助手知识库</div>
      <div class="page-sub">维护系统使用问答条目：用户提问后，助手匹配条目并返回答案、操作步骤与跳转入口。链接与步骤由人工维护，模型只做意图匹配。</div>
    </div>

    <el-alert v-if="error" :title="error" type="error" :closable="false" style="margin-bottom: 10px"/>

    <div class="fee-card">
      <div class="toolbar">
        <el-input v-model="filter" placeholder="搜索标题/键" clearable style="width: 240px" size="small"/>
        <el-button type="primary" plain size="small" @click="addEntry">+ 新增条目</el-button>
        <el-button type="primary" size="small" :loading="saving" :disabled="!dirty" @click="save">保存修改</el-button>
        <span v-if="dirty" class="hint-text" style="align-self:center">有未保存修改</span>
      </div>
      <el-table v-loading="loading" :data="filtered" size="small" :header-cell-style="{ background: '#f5f7fa' }">
        <el-table-column prop="title" label="标题" min-width="150"/>
        <el-table-column label="问法样例" min-width="180">
          <template #default="{ row }">
            <span class="hint-text">{{ firstLines(row.questions, 2) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="跳转" min-width="140">
          <template #default="{ row }">
            <span v-if="row.linkUrl" class="hint-text">{{ row.linkLabel || row.linkUrl }}</span>
            <span v-else class="hint-text">无</span>
          </template>
        </el-table-column>
        <el-table-column label="权限" width="120">
          <template #default="{ row }">
            <el-tag v-if="row.permCode" size="small" type="warning">{{ row.permCode }}</el-tag>
            <span v-else class="hint-text">全员</span>
          </template>
        </el-table-column>
        <el-table-column label="启用" width="70" align="center">
          <template #default="{ row }">
            <el-switch v-model="row.isActive" :active-value="1" :inactive-value="0" size="small"/>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="110" align="center">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="editEntry(row)">编辑</el-button>
            <el-button v-if="row._new" link type="danger" size="small" @click="removeEntry(row)">移除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <el-dialog v-model="editVisible" :title="editing._new ? '新增条目' : '编辑条目'" width="640px" destroy-on-close>
      <el-form label-width="90px" size="small">
        <el-form-item label="标题" required>
          <el-input v-model="editing.title" placeholder="如：新建项目"/>
        </el-form-item>
        <el-form-item label="问法样例">
          <el-input v-model="editing.questions" type="textarea" :rows="3" placeholder="每行一条用户可能的问法，用于意图匹配"/>
        </el-form-item>
        <el-form-item label="答案" required>
          <el-input v-model="editing.answer" type="textarea" :rows="4" placeholder="回答正文"/>
        </el-form-item>
        <el-form-item label="跳转链接">
          <el-input v-model="editing.linkUrl" placeholder="站内路由，如 /project（留空=无跳转）"/>
        </el-form-item>
        <el-form-item label="按钮文案">
          <el-input v-model="editing.linkLabel" placeholder="如：去项目管理"/>
        </el-form-item>
        <el-form-item label="操作步骤">
          <el-input v-model="editing.steps" type="textarea" :rows="4" placeholder="每行一步"/>
        </el-form-item>
        <el-form-item label="所需权限">
          <el-input v-model="editing.permCode" placeholder="权限码，如 project:manage；留空=全员可见"/>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editVisible = false">取消</el-button>
        <el-button type="primary" @click="applyEdit">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { listAssistantEntries, saveAssistantEntries } from '../../api/aiAssistant'

const loading = ref(false)
const saving = ref(false)
const error = ref('')
const rows = ref([])
const original = ref('')
const filter = ref('')
const editVisible = ref(false)
const editing = ref({})
let editIndex = -1

const dirty = computed(() => JSON.stringify(stripped(rows.value)) !== original.value)
const filtered = computed(() => {
  const f = filter.value.trim().toLowerCase()
  if (!f) return rows.value
  return rows.value.filter((r) => (r.title || '').toLowerCase().includes(f) || (r.entryKey || '').toLowerCase().includes(f))
})

function stripped(list) {
  return list.map((r) => ({
    entryKey: r.entryKey,
    title: (r.title || '').trim(),
    questions: r.questions || null,
    answer: (r.answer || '').trim(),
    linkUrl: (r.linkUrl || '').trim() || null,
    linkLabel: (r.linkLabel || '').trim() || null,
    steps: r.steps || null,
    permCode: (r.permCode || '').trim() || null,
    sort: r.sort ?? 0,
    isActive: r.isActive
  }))
}

function firstLines(text, n) {
  if (!text) return '-'
  return text.split('\n').filter(Boolean).slice(0, n).join('；')
}

function addEntry() {
  const key = 'CUSTOM_' + Date.now().toString(36).toUpperCase()
  editing.value = { entryKey: key, title: '', questions: '', answer: '', linkUrl: '', linkLabel: '', steps: '', permCode: '', sort: rows.value.length + 1, isActive: 1, _new: true }
  editIndex = -1
  editVisible.value = true
}

function editEntry(row) {
  editIndex = rows.value.indexOf(row)
  editing.value = { ...row }
  editVisible.value = true
}

function applyEdit() {
  if (!editing.value.title?.trim() || !editing.value.answer?.trim()) {
    ElMessage.warning('标题与答案必填')
    return
  }
  if (editIndex >= 0) {
    rows.value[editIndex] = { ...editing.value }
  } else {
    rows.value.push({ ...editing.value })
  }
  editVisible.value = false
}

function removeEntry(row) {
  const i = rows.value.indexOf(row)
  if (i >= 0) rows.value.splice(i, 1)
}

async function load() {
  loading.value = true
  error.value = ''
  try {
    const data = await listAssistantEntries()
    rows.value = (Array.isArray(data) ? data : []).map((r) => ({ ...r }))
    original.value = JSON.stringify(stripped(rows.value))
  } catch (e) {
    error.value = e?.response?.data?.message || e?.message || '知识条目加载失败'
  } finally {
    loading.value = false
  }
}

async function save() {
  const list = stripped(rows.value)
  if (list.some((r) => !r.title || !r.answer)) {
    ElMessage.warning('存在标题或答案为空的条目')
    return
  }
  saving.value = true
  try {
    const data = await saveAssistantEntries(list)
    rows.value = (Array.isArray(data) ? data : []).map((r) => ({ ...r }))
    original.value = JSON.stringify(stripped(rows.value))
    ElMessage.success('知识库已保存，即时生效')
  } catch (e) {
    ElMessage.error(e?.response?.data?.message || e?.message || '保存失败')
  } finally {
    saving.value = false
  }
}

onMounted(load)
</script>

<style scoped>
.page { max-width: 1100px; }
.page-header { margin: 4px 0 16px; }
.page-title { font-size: 20px; font-weight: 700; }
.page-sub { color: var(--el-text-color-secondary); font-size: 13px; margin-top: 6px; }
.fee-card { background: #fff; border: 1px solid var(--el-border-color-light); border-radius: 10px; padding: 16px; }
.toolbar { display: flex; gap: 8px; margin-bottom: 12px; }
.hint-text { color: var(--el-text-color-secondary); font-size: 12px; }
</style>
