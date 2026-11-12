<template>
  <div v-loading="loading" class="maintenance-board">
    <section class="mb-section">
      <h2>文档解析</h2>
      <div class="mb-toolbar">
        <el-select v-model="parseDocumentId" placeholder="选择已上传的合规文档" filterable aria-label="选择解析文档" :disabled="busy">
          <el-option v-for="doc in docs" :key="doc.id" :label="doc.title" :value="doc.id" />
        </el-select>
        <el-button type="primary" :disabled="!parseDocumentId || busy" @click="parse(false)">解析文档</el-button>
        <el-button type="danger" plain :disabled="!parseDocumentId || busy" @click="parse(true)">清空并重新解析</el-button>
      </div>
      <p v-if="selectedDocument?.parseStatus" class="hint">上传解析：{{ parseStatusText[selectedDocument.parseStatus] || selectedDocument.parseStatus }}</p>
    </section>

    <section class="mb-section">
      <h2>候选事项</h2>
      <el-collapse v-if="draftGroups.length">
        <el-collapse-item v-for="group in draftGroups" :key="group.id" :name="group.id">
          <template #title>
            <span class="mb-group-title">{{ group.title }}</span>
            <el-tag type="warning" effect="plain" size="small">{{ group.items.length }} 条</el-tag>
          </template>
          <el-button class="mb-clear" type="danger" plain :disabled="busy" @click="clearDraftGroups(group)">清空候选事项</el-button>
          <el-table :data="group.items">
            <el-table-column prop="title" label="事项标题" min-width="180" />
            <el-table-column label="事项内容" min-width="220">
              <template #default="{ row }"><div class="item-content">{{ row.content }}</div></template>
            </el-table-column>
            <el-table-column label="操作" width="185">
              <template #default="{ row }">
                <el-button link :disabled="busy" @click="openEdit(row)">编辑</el-button>
                <el-button link type="danger" :disabled="busy" @click="remove(row)">删除</el-button>
                <el-button link type="primary" :disabled="busy" @click="saveDraft(row)">保存</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-collapse-item>
      </el-collapse>
      <el-empty v-else description="暂无解析候选" :image-size="64" />
    </section>

    <section class="mb-section">
      <div class="mb-section-heading">
        <h2>已发布事项</h2>
        <el-button type="primary" :disabled="busy" @click="openCreate">新增事项</el-button>
      </div>
      <el-collapse v-if="publishedGroups.length">
        <el-collapse-item v-for="group in publishedGroups" :key="group.id" :name="group.id">
          <template #title>
            <span class="mb-group-title">{{ group.title }}</span>
            <el-tag type="info" effect="plain" size="small">{{ group.items.length }} 条</el-tag>
          </template>
          <el-button class="mb-clear" type="danger" plain :disabled="busy" @click="clearPublished(group)">清空已发布事项</el-button>
          <el-table :data="group.items">
            <el-table-column prop="title" label="事项标题" min-width="180" />
            <el-table-column label="事项内容" min-width="220">
              <template #default="{ row }"><div class="item-content">{{ row.content }}</div></template>
            </el-table-column>
            <el-table-column label="操作" width="120">
              <template #default="{ row }">
                <el-button link :disabled="busy" @click="openEdit(row)">编辑</el-button>
                <el-button link type="danger" :disabled="busy" @click="remove(row)">撤下</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-collapse-item>
      </el-collapse>
      <el-empty v-else description="暂无已发布事项" :image-size="64" />
    </section>

    <el-dialog v-model="editVisible" :title="editForm.id ? '编辑事项' : '新增事项'" width="min(560px, 94vw)" destroy-on-close>
      <el-form label-position="top" @submit.prevent="saveEdit">
        <el-form-item v-if="!editForm.id" label="来源文档" required>
          <el-select v-model="editForm.documentId" placeholder="先选择已上传文档" filterable>
            <el-option v-for="doc in docs" :key="doc.id" :label="doc.title" :value="doc.id" />
          </el-select>
        </el-form-item>
        <template v-if="editForm.id || editForm.documentId">
          <el-form-item label="事项标题" required><el-input v-model="editForm.title" maxlength="200" /></el-form-item>
          <el-form-item label="事项内容"><el-input v-model="editForm.content" type="textarea" :rows="6" /></el-form-item>
        </template>
      </el-form>
      <template #footer>
        <el-button @click="editVisible=false">取消</el-button>
        <el-button type="primary" :loading="busy" :disabled="!editForm.title?.trim()" @click="saveEdit">{{ editForm.isDraft ? '保存编辑' : '保存' }}</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, ref, watch } from 'vue'
import { ElLoading, ElMessage, ElMessageBox } from 'element-plus'
import request from '../../api/request'
import { listAllComplianceDocuments } from '../../api/complianceV4'
import {
  listComplianceItems, createComplianceItem, updateComplianceItem, deleteComplianceItem,
  confirmComplianceDrafts, withdrawPreview, withdrawDocument, clearDraftsPreview, clearDrafts
} from '../../api/compliance'

const props = defineProps({ active: Boolean })
const emit = defineEmits(['changed'])
const loading = ref(false)
const busy = ref(false)
const docs = ref([])
const items = ref([])
const parseDocumentId = ref(null)
const editVisible = ref(false)
const editForm = ref({})
const parseStatusText = { PENDING: '等待解析', RUNNING: '解析中', SUCCESS: '已完成', FAILED: '失败' }
const documents = computed(() => new Map(docs.value.map(doc => [doc.id, doc])))
const selectedDocument = computed(() => documents.value.get(parseDocumentId.value))

function groupItems(isDraft) {
  const groups = new Map()
  for (const item of items.value) {
    if (item.isDraft !== isDraft || !documents.value.has(item.documentId)) continue
    if (!groups.has(item.documentId)) {
      groups.set(item.documentId, { id: item.documentId, title: documents.value.get(item.documentId).title, items: [] })
    }
    groups.get(item.documentId).items.push(item)
  }
  return [...groups.values()]
}
const draftGroups = computed(() => groupItems(1))
const publishedGroups = computed(() => groupItems(0))

let loadVersion = 0
async function load() {
  const version = ++loadVersion
  loading.value = true
  try {
    const nextDocs = await listAllComplianceDocuments()
    const rows = []
    const size = 200
    for (let page = 1; ; page++) {
      const data = await listComplianceItems({ includeDraft: true, page, size })
      const batch = data?.items || []
      rows.push(...batch)
      if (!batch.length || rows.length >= data.total) break
    }
    if (version !== loadVersion) return
    docs.value = nextDocs
    items.value = rows
    if (!nextDocs.some(doc => doc.id === parseDocumentId.value)) parseDocumentId.value = null
  } finally {
    if (version === loadVersion) loading.value = false
  }
}

async function perform(action, message) {
  if (busy.value) return
  busy.value = true
  try {
    const result = await action()
    ElMessage.success(typeof message === 'function' ? message(result) : message)
    emit('changed')
    await load()
  } finally {
    busy.value = false
  }
}

async function ask(message, title) {
  try {
    await ElMessageBox.confirm(message, title, { type: 'warning' })
    return true
  } catch {
    return false
  }
}

function openCreate() {
  editForm.value = { documentId: null, title: '', content: '' }
  editVisible.value = true
}
function openEdit(row) {
  editForm.value = { id: row.id, isDraft: row.isDraft, title: row.title, content: row.content || '' }
  editVisible.value = true
}
async function saveEdit() {
  const form = editForm.value
  if (!form.title.trim() || (!form.id && !form.documentId)) return
  const fields = { title: form.title.trim(), content: form.content }
  await perform(async () => {
    if (form.id) await updateComplianceItem(form.id, fields)
    else await createComplianceItem({ ...fields, documentId: form.documentId })
    editVisible.value = false
  }, '已保存')
}
function saveDraft(row) {
  return perform(() => confirmComplianceDrafts({ documentId: row.documentId, itemIds: [row.id] }), '已保存到展示列表')
}
async function remove(row) {
  const action = row.isDraft ? '删除' : '撤下'
  if (!await ask(`确认${action}「${row.title}」？`, `${action}事项`)) return
  await perform(() => row.isDraft ? deleteComplianceItem(row.id) : request.post(`/compliance/items/${row.id}/withdraw`), `已${action}`)
}
async function clearPublished(group) {
  const preview = await withdrawPreview(group.id)
  if (!preview.count || !await ask(`撤下「${preview.documentTitle}」全部 ${preview.count} 条已发布事项？`, '清空已发布事项')) return
  await perform(() => withdrawDocument(group.id, preview.count), '已清空已发布事项')
}
async function clearDraftGroups(group) {
  const preview = await clearDraftsPreview(group.id)
  if (!preview.count) { ElMessage.info('该文档暂无候选事项'); return }
  if (!await ask(`删除「${preview.documentTitle}」全部 ${preview.count} 条候选事项？删除后不可恢复，已发布事项不受影响。`, '清空候选事项')) return
  await perform(() => clearDrafts(group.id, preview.count), '已清空候选事项')
}
async function parse(reset) {
  const id = parseDocumentId.value
  if (!id || busy.value) return
  const preview = await request.get(`/compliance/documents/${id}/reset-preview`)
  const prompt = reset
    ? `清空「${preview.documentTitle}」的 ${preview.count} 条事项并重新解析？解析成功后才会撤下已发布事项并替换候选。`
    : `解析「${preview.documentTitle}」并替换原有候选？已发布事项保留。`
  if (!await ask(prompt, reset ? '清空并重新解析' : '解析文档')) return
  // 解析中全屏提示（Owner 2026-11-11）：OCR+大模型识别约需 1-2 分钟
  const loading = ElLoading.service({
    lock: true, text: '正在解析文档（OCR + 大模型识别，约需 1-2 分钟，请勿关闭或刷新页面）…',
    background: 'rgba(0, 0, 0, 0.7)'
  })
  try {
    await perform(
      () => request.post(`/compliance/documents/${id}/reparse`, { reset, expectedCount: preview.count }, { timeout: 300000 }),
      result => `已解析 ${result.parsed} 条候选`
    )
  } finally {
    loading.close()
  }
}

watch(() => props.active, active => { if (active) load() }, { immediate: true })
</script>

<style scoped>
.maintenance-board { display: grid; grid-template-columns: minmax(0, 1fr); gap: 14px; min-width: 0; }
.mb-toolbar { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; }
.mb-toolbar .el-input, .mb-toolbar .el-select { width: min(340px, 100%); }
.mb-toolbar :deep(.el-button + .el-button) { margin-left: 0; }
.mb-section {
  min-width: 0;
  padding: 14px 16px;
  background: var(--el-bg-color);
  border: 1px solid var(--el-border-color-light);
  border-radius: 10px;
}
.mb-section-heading { display: flex; align-items: center; justify-content: space-between; flex-wrap: wrap; gap: 8px; margin-bottom: 12px; }
.mb-section h2 { margin: 0 0 12px; font-size: 16px; line-height: 1.4; font-weight: 650; }
.mb-section :deep(.el-collapse-item__header) { gap: 8px; min-width: 0; text-align: left; }
.mb-section-heading h2 { margin: 0; }
.mb-group-title { min-width: 0; font-weight: 600; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.mb-section :deep(.el-collapse-item__header .el-tag) { flex: none; }
.mb-section :deep(.el-collapse-item__content) { padding: 8px 0 12px; }
.mb-clear { margin-bottom: 8px; }
.item-content { white-space: pre-wrap; overflow-wrap: anywhere; }
.hint { margin: 8px 0 0; font-size: 13px; color: var(--el-text-color-secondary); }
@media (max-width: 560px) {
  .mb-section { padding: 12px; }
  .mb-toolbar .el-input, .mb-toolbar .el-select { width: 100%; min-width: 0; }
  .mb-toolbar .el-button, .mb-clear { max-width: 100%; height: auto; min-height: 32px; white-space: normal; }
  .mb-section :deep(.el-collapse-item__content) { overflow-x: auto; }
}
</style>
