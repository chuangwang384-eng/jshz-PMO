<template>
  <div v-loading="loading" class="settlement-materials">
    <el-alert v-if="failed" type="error" title="结算资料加载失败" :closable="false">
      <el-button link type="primary" @click="load">重试</el-button>
    </el-alert>
    <template v-else-if="slots.length">
      <div class="settlement-heading">
        <h3>必填资料 {{ requiredComplete }} / {{ requiredCount }}</h3>
        <el-button v-if="canArchive" type="primary" :loading="archiving" :disabled="busy"
          @click="archive">归档结算资料包</el-button>
      </div>
      <progress class="settlement-progress" aria-label="结算资料必填进度" :max="requiredCount || 1" :value="requiredComplete" />
      <div v-if="missing.length" class="settlement-actions">
        <strong>还差 {{ missing.length }} 项必填</strong>
        <el-button v-for="item in missing" :key="item.definition.code" size="small"
          @click="focusSlot(item.definition.code)">{{ item.definition.name }}</el-button>
      </div>
      <p v-else class="ui-desc">必填资料已齐</p>
      <p v-if="canArchive" class="ui-desc">归档会将当前各细项资料打包到项目文件。补件后可再次归档。</p>
      <p class="ui-desc" role="status">{{ status }}</p>
      <div v-if="uploadSlots.length" class="settlement-drop" @dragover.prevent @drop.prevent="queueFiles($event.dataTransfer.files)">
        <button type="button" :disabled="busy || archiving" @click="batchInput?.click()">选择批量上传文件</button>
        <input ref="batchInput" type="file" hidden multiple :accept="acceptedFiles" @change="selectBatchFiles" />
        <span>也可将文件拖到此处，确认归类后提交。</span>
      </div>
      <form v-if="queued.length" class="settlement-editor" @submit.prevent="submitBatch">
        <div v-for="row in queued" :key="row.id" class="settlement-actions">
          <label :for="`settlement-batch-${row.id}`">{{ row.file.name }}</label>
          <select :id="`settlement-batch-${row.id}`" v-model="row.slot" required :disabled="busy || archiving">
            <option value="" disabled>请选择上传口</option>
            <option v-for="item in uploadSlots" :key="item.definition.code" :value="item.definition.code">{{ item.definition.name }}</option>
          </select>
          <el-button link :disabled="busy || archiving" @click="queued = queued.filter(file => file.id !== row.id)">移除</el-button>
          <small v-if="row.error">{{ row.error }}</small>
        </div>
        <el-button native-type="submit" type="primary" :loading="busy" :disabled="archiving">确认归类并提交</el-button>
      </form>
      <el-radio-group v-model="filter" class="settlement-filters" aria-label="筛选结算资料">
        <el-radio-button label="todo">待办</el-radio-button>
        <el-radio-button label="done">已完成</el-radio-button>
        <el-radio-button label="all">全部</el-radio-button>
      </el-radio-group>
      <p v-if="!groups.length" class="ui-desc">当前筛选下没有资料细项。</p>
      <section v-for="group in groups" :key="group.name" class="settlement-group">
        <h3><button type="button" :aria-expanded="!closedGroups[group.name]" @click="closedGroups[group.name] = !closedGroups[group.name]">
          {{ group.name }} <small>{{ group.complete }} / {{ group.total }} 已完成</small>
        </button></h3>
        <div v-show="!closedGroups[group.name]">
        <article v-for="item in group.visible" :id="`settlement-slot-${item.definition.code}`"
          :key="item.definition.code" class="ui-card settlement-slot"
          :class="{ 'settlement-slot-focus': focusCode === item.definition.code }">
          <div class="settlement-heading">
            <h4><button type="button" :aria-expanded="isOpen(item)" @click="toggleSlot(item)">
              <span class="settlement-dot" :class="{ 'settlement-dot-complete': item.complete }" aria-hidden="true"></span>
              {{ item.definition.name }}</button></h4>
            <el-tag size="small" effect="plain" :type="item.definition.required ? 'warning' : 'info'">{{ item.definition.required ? '必填' : '选填' }}</el-tag>
            <el-tag size="small" :type="item.complete ? 'success' : item.definition.required ? 'warning' : 'info'">
              {{ item.notApplicableReason ? '不适用' : item.complete ? '已提交' : '未上传' }}
            </el-tag>
            <small v-if="item.definition.deadline && !item.complete">{{ item.definition.deadline }}</small>
            <small v-if="!isOpen(item) && item.batches[0]?.length">{{ batchSummary(item.batches[0]) }}</small>
          </div>
          <p v-if="item.definition.code === 'U01' && invoiceTotal.count" class="ui-desc">文件名标记金额合计 ¥{{ (invoiceTotal.cents / 100).toFixed(2) }}（{{ invoiceTotal.count }} / {{ item.batches[0].length }} 个文件）</p>
          <div v-if="isOpen(item)">
          <p class="ui-desc">{{ item.definition.description }}</p>
          <p v-if="item.definition.deadline" class="ui-desc">{{ item.definition.deadline }}</p>
          <p v-if="item.definition.suggestedUploader || item.definition.suggestedReviewer" class="ui-desc">
            <span v-if="item.definition.suggestedUploader">建议上传角色 {{ item.definition.suggestedUploader }}　</span>
            <span v-if="item.definition.suggestedReviewer">建议核对角色 {{ item.definition.suggestedReviewer }}</span>
          </p>
          <p v-if="item.notApplicableReason">不适用原因 {{ item.notApplicableReason }}</p>
          <div v-if="item.batches[0]?.length" class="settlement-table-wrap" tabindex="0" role="region" :aria-label="`${item.definition.name}当前批次文件`">
          <table class="settlement-table">
            <thead><tr><th scope="col">文件名</th><th scope="col">上传人</th><th scope="col">上传时间</th><th scope="col">操作</th></tr></thead>
            <tbody><tr v-for="doc in item.batches[0]" :key="doc.id">
            <th scope="row">{{ doc.fileName }}</th><td>{{ doc.uploadBy || '—' }}</td><td>{{ doc.createTime || '—' }}</td>
            <td class="settlement-table-actions">
            <el-button link type="primary" @click="emit('preview', doc)">预览</el-button>
            <el-button link type="primary" @click="emit('download', doc)">下载</el-button>
            <el-dropdown v-if="item.writable" trigger="click" @command="removeFile(item, doc)">
              <el-button link :disabled="busy || archiving" :aria-label="`${doc.fileName}的更多操作`">更多</el-button>
              <template #dropdown><el-dropdown-menu><el-dropdown-item command="delete">删除</el-dropdown-item></el-dropdown-menu></template>
            </el-dropdown>
            </td></tr></tbody>
          </table>
          </div>
          <details v-if="item.batches.length > 1">
            <summary>历史批次（{{ item.batches.length - 1 }}）</summary>
            <div v-for="(batch, index) in item.batches.slice(1)" :key="batch[0].id" class="settlement-history">
              <strong>第 {{ item.batches.length - index - 1 }} 批</strong>
              <div v-for="doc in batch" :key="doc.id" class="settlement-file">
                <span>{{ doc.fileName }}</span>
                <el-button link type="primary" @click="emit('preview', doc)">预览</el-button>
                <el-button link type="primary" @click="emit('download', doc)">下载</el-button>
              </div>
            </div>
          </details>
          <div v-if="item.writable && item.definition.code !== 'U08' && editor?.slot !== item.definition.code" class="settlement-actions">
            <el-button v-if="item.batches[0]?.length && !item.notApplicableReason" size="small"
              :disabled="busy || archiving" @click="open(item, 'files', true)">追加文件</el-button>
            <el-button v-if="item.complete" size="small" :disabled="busy || archiving" @click="open(item, 'files')">
              {{ item.complete ? (item.definition.versioned ? '上传新版本' : '替换当前批次') : '上传文件' }}
            </el-button>
            <div v-else class="settlement-drop" @dragover.prevent @drop.prevent="dropOnSlot(item, $event)">
              <button type="button" :disabled="busy || archiving" @click="open(item, 'files')">选择文件或拖到此处</button>
            </div>
            <el-button v-if="item.definition.code === 'U09' && budgetSources.length" size="small" :disabled="busy"
              @click="open(item, 'source')">引用归档资料</el-button>
            <el-button v-if="item.definition.allowsNotApplicable" size="small" :disabled="busy"
              @click="open(item, 'reason')">填写不适用原因</el-button>
          </div>
          <!-- 成本单（U08）改为填表生成：不再要求上传 PDF，提交后由服务端按模板生成 Excel -->
          <ProjectCostSheetForm v-if="item.definition.code === 'U08' && item.writable"
            :project-id="projectId" :phase-id="phaseId" :title="item.definition.name"
            :submitted="item.complete" :readonly="busy || archiving" @saved="onCostSheetSaved" />
          <form v-if="editor?.slot === item.definition.code && item.writable"
            class="settlement-editor" :aria-label="`${item.definition.name}资料提交`" @submit.prevent="submit">
            <template v-if="editor.mode === 'files'">
              <label for="settlement-files">本批次文件</label>
              <input id="settlement-files" type="file" multiple :disabled="busy || archiving"
                :accept="acceptedFiles"
                aria-describedby="settlement-file-help" @change="addFiles" />
              <small id="settlement-file-help">可多选，也可反复打开选择框添加，文件数量不限。单文件不超过 {{ fileMaxMb }} MB，单批次合计不超过 {{ batchMaxMb }} MB。</small>
              <p v-if="editor.files.length" class="ui-desc" role="status">
                已选 {{ editor.files.length }} 个文件，合计 {{ selectedSizeMb }} MB
              </p>
              <ul v-if="editor.files.length" aria-label="待提交文件">
                <li v-for="(file, index) in editor.files" :key="index">
                  {{ file.name }}
                  <el-button link type="danger" :disabled="busy" :aria-label="`移除 ${file.name}`"
                    @click="editor.files.splice(index, 1)">移除</el-button>
                </li>
              </ul>
            </template>
            <template v-else-if="editor.mode === 'source'">
              <label for="settlement-source">本项目已归档的预算资料</label>
              <select id="settlement-source" v-model="editor.source" :disabled="busy" required>
                <option value="" disabled>请选择资料</option>
                <option v-for="doc in budgetSources" :key="doc.id" :value="doc.id">{{ doc.fileName }} · {{ doc.createTime }}</option>
              </select>
            </template>
            <template v-else>
              <label for="settlement-reason">不适用原因</label>
              <textarea id="settlement-reason" v-model.trim="editor.reason" maxlength="500" :disabled="busy" required rows="2" />
            </template>
            <div class="settlement-actions">
              <el-button type="primary" size="small" native-type="submit" :loading="busy" :disabled="archiving">提交</el-button>
              <el-button size="small" :disabled="busy" @click="editor = null">取消</el-button>
            </div>
          </form>
          </div>
        </article>
        </div>
      </section>
    </template>
  </div>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { archiveSettlementMaterials, deleteSettlementFile, getSettlementMaterials, saveSettlementMaterials } from '../../../api/project'
import { uploadLimits, ensureUploadLimits, toMb } from '../../../utils/limits'
import ProjectCostSheetForm from './ProjectCostSheetForm.vue'
import { invoiceFilenameTotal, suggestSettlementSlot } from './settlementPresentation'

const props = defineProps({
  projectId: { type: [String, Number], required: true },
  phaseId: { type: [String, Number], required: true }
})
const emit = defineEmits(['saved', 'preview', 'download'])
const loading = ref(false)
const failed = ref(false)
const canArchive = ref(false)
const archiving = ref(false)
const busy = ref(false)
const status = ref('')
const slots = ref([])
const budgetSources = ref([])
const editor = ref(null)
const focusCode = ref('')
const filter = ref('todo')
const closedGroups = ref({})
const expandedSlots = ref({})
const queued = ref([])
const batchInput = ref(null)
const acceptedFiles = '.pdf,.xls,.xlsx,.doc,.docx,.png,.jpg,.jpeg,.webp,.zip,.rar,.7z,.txt,.ofd'
let queuedId = 0
let focusTimer = 0
let inFlight = null

const groups = computed(() => {
  const result = new Map()
  for (const slot of slots.value) {
    const group = slot.definition.group
    if (!result.has(group)) result.set(group, [])
    result.get(group).push(slot)
  }
  return [...result].map(([name, items]) => ({
    name,
    total: items.length,
    complete: items.filter(item => item.complete).length,
    visible: items.filter(item => editor.value?.slot === item.definition.code || filter.value === 'all'
      || (filter.value === 'done' ? item.complete : !item.complete))
      .sort((a, b) => Number(a.complete) - Number(b.complete))
  })).filter(group => group.visible.length)
    .sort((a, b) => Number(a.visible[0].complete) - Number(b.visible[0].complete))
})
const required = computed(() => slots.value.filter(slot => slot.definition.required))
const requiredCount = computed(() => required.value.length)
const requiredComplete = computed(() => required.value.filter(slot => slot.complete).length)
const missing = computed(() => required.value.filter(slot => !slot.complete))
const uploadSlots = computed(() => slots.value.filter(item => item.writable && item.definition.code !== 'U08'))
const invoiceTotal = computed(() => invoiceFilenameTotal(slots.value.find(item => item.definition.code === 'U01')?.batches[0] ?? []))
// 上限取值统一走后端单一真源（application.yml expert.upload → GET /api/meta/limits），本地仅兜底
const fileMaxMb = computed(() => toMb(uploadLimits.docMaxBytes))
const batchMaxMb = computed(() => toMb(uploadLimits.settlementBatchMaxBytes))
const selectedSizeMb = computed(() => {
  const bytes = (editor.value?.files || []).reduce((size, file) => size + file.size, 0)
  return (bytes / 1024 / 1024).toFixed(1)
})

function load() {
  // 复用进行中的请求：待办「去处理」跳转时可能与首屏加载并发，避免重复拉取把刚打开的编辑器状态冲掉
  if (inFlight) return inFlight
  inFlight = (async () => {
    loading.value = true
    failed.value = false
    try {
      const result = await getSettlementMaterials(props.projectId, props.phaseId)
      canArchive.value = result.canArchive
      slots.value = result.slots
      budgetSources.value = result.budgetSources
    } catch {
      failed.value = true
    } finally {
      loading.value = false
      inFlight = null
    }
  })()
  return inFlight
}

/**
 * 右侧待办「去处理」入口：按细项名称定位到对应资料卡片，滚动到可视区并高亮，
 * 可写的上传类细项直接展开文件选择区（成本单为在线填表，不展开上传区）。
 * 返回是否命中，未命中时由父组件回退到「阶段资料」顶部。
 */
async function focusSlot(name) {
  const target = String(name || '').trim()
  if (!target) return false
  if (!slots.value.length) await load().catch(() => {})
  const item = slots.value.find(slot => slot.definition.name === target || slot.definition.code === target)
  if (!item) return false
  if (busy.value || archiving.value) return false
  if (filter.value === 'done' && !item.complete) filter.value = 'todo'
  if (filter.value === 'todo' && item.complete) filter.value = 'all'
  closedGroups.value[item.definition.group] = false
  expandedSlots.value[item.definition.code] = true
  await nextTick()
  if (!editor.value && item.writable && item.definition.code !== 'U08' && !item.complete) {
    open(item, 'files', Boolean(item.batches[0]?.length))
    await nextTick()
  }
  const card = document.getElementById(`settlement-slot-${item.definition.code}`)
  if (!card) return false
  card.scrollIntoView({ block: 'center', behavior: window.matchMedia('(prefers-reduced-motion: reduce)').matches ? 'instant' : 'smooth' })
  focusCode.value = item.definition.code
  window.clearTimeout(focusTimer)
  focusTimer = window.setTimeout(() => { focusCode.value = '' }, 2000)
  const focusTarget = card.querySelector('input[type="file"]') || card.querySelector('h4 button')
  focusTarget?.focus({ preventScroll: true })
  return true
}
defineExpose({ focusSlot })

function isOpen(item) {
  return expandedSlots.value[item.definition.code] ?? !item.complete
}

function toggleSlot(item) {
  expandedSlots.value[item.definition.code] = !isOpen(item)
}

function batchSummary(batch) {
  const latest = batch.reduce((current, doc) => (doc.createTime || '') > (current.createTime || '') ? doc : current)
  return [`${batch.length} 个文件`, latest.uploadBy, latest.createTime].filter(Boolean).join(' · ')
}

function dropOnSlot(item, event) {
  if (busy.value || archiving.value || !event.dataTransfer.files.length) return
  open(item, 'files', Boolean(item.batches[0]?.length))
  editor.value.files.push(...event.dataTransfer.files)
}

function selectBatchFiles(event) {
  queueFiles(event.target.files)
  event.target.value = ''
}

function queueFiles(files) {
  if (busy.value || archiving.value) return
  for (const file of files) {
    queued.value.push({ id: ++queuedId, file, slot: suggestSettlementSlot(file.name, uploadSlots.value), error: '' })
  }
}

function fileError(files) {
  if (!files.length) return '请至少选择 1 个文件'
  const oversized = files.find(file => file.size > uploadLimits.docMaxBytes)
  if (oversized) return `「${oversized.name}」超过单文件 ${fileMaxMb.value} MB 限制`
  const total = files.reduce((size, file) => size + file.size, 0)
  return total > uploadLimits.settlementBatchMaxBytes ? `超过单批次 ${batchMaxMb.value} MB 上限，请分批提交` : ''
}

function uploadBody(files, append) {
  const body = new FormData()
  files.forEach(file => body.append('files', file))
  if (append) body.append('append', 'true')
  return body
}

async function submitBatch() {
  if (busy.value || archiving.value || !queued.value.length) return
  const bySlot = new Map()
  for (const row of queued.value) {
    row.error = ''
    if (!bySlot.has(row.slot)) bySlot.set(row.slot, [])
    bySlot.get(row.slot).push(row)
  }
  busy.value = true
  let uploaded = 0
  try {
    for (const [code, rows] of bySlot) {
      const item = uploadSlots.value.find(slot => slot.definition.code === code)
      const files = rows.map(row => row.file)
      const error = item ? fileError(files) : '请选择可上传的细项'
      if (error) {
        rows.forEach(row => { row.error = error })
        continue
      }
      try {
        const append = Boolean(item.batches[0]?.length && !item.notApplicableReason)
        await saveSettlementMaterials(props.projectId, props.phaseId, code, uploadBody(files, append))
        const ids = new Set(rows.map(row => row.id))
        queued.value = queued.value.filter(row => !ids.has(row.id))
        uploaded += rows.length
      } catch (error) {
        rows.forEach(row => { row.error = error.response?.data?.message || '提交失败，请检查文件或权限后重试。' })
      }
    }
    status.value = `已提交 ${uploaded} 个文件，剩余 ${queued.value.length} 个待处理。`
    if (uploaded) {
      await load()
      emit('saved')
    }
  } finally {
    busy.value = false
  }
}

function open(item, mode, append = false) {
  status.value = ''
  editor.value = { slot: item.definition.code, mode, append, files: [], source: '', reason: item.notApplicableReason || '' }
}

function addFiles(event) {
  const input = event.target
  editor.value.files.push(...Array.from(input.files))
  input.value = ''
}
/** U08 成本单生成成功后刷新批次，让新 Excel 立即出现在当前批次里。 */
async function onCostSheetSaved() {
  status.value = '成本单已生成。'
  await load()
  emit('saved')
}

async function removeFile(item, doc) {
  if (busy.value || archiving.value) return
  try {
    await ElMessageBox.confirm(`删除「${doc.fileName}」？已归档的资料包会保留当时的文件。`, '删除结算资料',
      { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消', autofocus: false })
  } catch {
    return
  }
  busy.value = true
  try {
    await deleteSettlementFile(props.projectId, props.phaseId, item.definition.code, doc.id)
    status.value = '文件已删除。'
    await load()
    emit('saved')
  } catch {
    status.value = '删除失败，请重试。'
  } finally {
    busy.value = false
  }
}

async function archive() {
  if (archiving.value || busy.value) return
  archiving.value = true
  status.value = '正在归档结算资料包…'
  try {
    await archiveSettlementMaterials(props.projectId, props.phaseId)
    status.value = '已归档。可在项目文件栏下载。'
    emit('saved')
  } catch {
    status.value = '归档失败，请检查资料后重试。'
  } finally {
    archiving.value = false
  }
}

async function submit() {
  if (busy.value || archiving.value || !editor.value) return
  const { slot, mode, append, files, source, reason } = editor.value
  let body = new FormData()
  if (mode === 'files') {
    // 2026-11-25：取消「每批最多 20 个文件」限制，文件数量不限；仅校验单文件与单批次合计
    const error = fileError(files)
    if (error) {
      ElMessage.warning(error)
      return
    }
    body = uploadBody(files, append)
  } else if (mode === 'source') {
    body.append('sourceDocId', source)
  } else {
    if (!reason) {
      ElMessage.warning('请填写不适用原因')
      return
    }
    body.append('reason', reason)
  }
  busy.value = true
  status.value = '正在提交资料…'
  try {
    await saveSettlementMaterials(props.projectId, props.phaseId, slot, body)
    editor.value = null
    status.value = '资料已提交。'
    await load()
    emit('saved')
  } catch {
    status.value = '提交失败，请检查文件或权限后重试。'
  } finally {
    busy.value = false
  }
}

onMounted(() => {
  ensureUploadLimits()
  load()
})
onBeforeUnmount(() => window.clearTimeout(focusTimer))
</script>

<style scoped>
.settlement-materials { min-height: 48px; }
.settlement-group { margin-block: 20px; }
.settlement-slot { padding: 12px; margin-block: 8px; }
/* 待办跳转定位高亮：3 秒后自动消失，避免残留视觉噪声 */
.settlement-slot-focus { outline: 2px solid var(--ui-primary); outline-offset: 2px; }
.settlement-heading,
.settlement-file,
.settlement-actions { display: flex; gap: 8px; align-items: center; flex-wrap: wrap; }
.settlement-heading h4 { margin: 0; }
.settlement-slot p { margin-block: 6px; }
.settlement-file span { overflow-wrap: anywhere; }
.settlement-history { margin-block: 8px; }
.settlement-actions { margin-top: 8px; }
.settlement-editor { display: grid; gap: 6px; margin-top: 8px; justify-items: start; }
.settlement-editor :is(textarea, select) { width: min(100%, 34rem); font: inherit; }
.settlement-progress { display: block; width: 100%; accent-color: var(--ui-primary); }
.settlement-filters { margin-top: 12px; }
.settlement-group h3 { margin-block: 0 8px; }
.settlement-group h3 button, .settlement-heading h4 button { min-block-size: 24px; font: inherit; color: inherit; background: none; border: 0; padding: 4px 0; cursor: pointer; }
.settlement-group h3 small { margin-inline-start: 8px; color: var(--ui-text-sub); font-weight: normal; }
.settlement-dot { display: inline-block; width: 8px; height: 8px; margin-inline-end: 5px; border-radius: 50%; background: var(--ui-text-muted); }
.settlement-dot-complete { background: var(--ui-success); }
.settlement-drop { display: flex; flex-wrap: wrap; gap: 8px; align-items: center; padding: 10px; border: 1px dashed var(--ui-primary); border-radius: 8px; }
.settlement-drop button { border: 0; background: transparent; color: var(--ui-primary); cursor: pointer; font: inherit; }
.settlement-table-wrap { overflow-x: auto; }
.settlement-table { border-collapse: collapse; width: 100%; font-size: 13px; text-align: start; }
.settlement-table :is(th, td) { text-align: start; padding: 6px; border-block-end: 1px solid var(--el-border-color-lighter); }
.settlement-table tbody th { font-weight: normal; overflow-wrap: anywhere; min-width: 7rem; }
.settlement-table-actions { white-space: nowrap; }
.settlement-table-actions .el-dropdown { margin-inline-start: 8px; }
@media (pointer: coarse) {
  .settlement-group h3 button, .settlement-heading h4 button, .settlement-drop button { min-block-size: 44px; }
}
</style>
