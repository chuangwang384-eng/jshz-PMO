<template>
  <div v-loading="loading" class="settlement-materials">
    <el-alert v-if="failed" type="error" title="结算资料加载失败" :closable="false">
      <el-button link type="primary" @click="load">重试</el-button>
    </el-alert>
    <template v-else-if="slots.length">
      <div class="settlement-heading">
        <p>必填资料已齐 {{ requiredComplete }} / {{ requiredCount }}</p>
        <el-button v-if="canArchive" type="primary" :loading="archiving" :disabled="busy"
          @click="archive">归档结算资料包</el-button>
      </div>
      <p v-if="canArchive" class="ui-desc">归档会将当前各细项资料打包到项目文件。补件后可再次归档。</p>
      <p class="ui-desc" role="status">{{ status }}</p>
      <section v-for="[group, items] in groups" :key="group" class="settlement-group">
        <h3>{{ group }}</h3>
        <article v-for="item in items" :key="item.definition.code" class="ui-card settlement-slot">
          <div class="settlement-heading">
            <h4>{{ item.definition.name }}</h4>
            <el-tag size="small" :type="item.complete ? 'success' : item.definition.required ? 'warning' : 'info'">
              {{ item.notApplicableReason ? '不适用' : item.complete ? '已提交' : item.definition.required ? '必填 · 待提交' : '选填' }}
            </el-tag>
          </div>
          <p class="ui-desc">{{ item.definition.description }}</p>
          <p v-if="item.definition.deadline" class="ui-desc">{{ item.definition.deadline }}</p>
          <p v-if="item.definition.suggestedUploader || item.definition.suggestedReviewer" class="ui-desc">
            <span v-if="item.definition.suggestedUploader">建议上传角色 {{ item.definition.suggestedUploader }}　</span>
            <span v-if="item.definition.suggestedReviewer">建议核对角色 {{ item.definition.suggestedReviewer }}</span>
          </p>
          <p v-if="item.notApplicableReason">不适用原因 {{ item.notApplicableReason }}</p>
          <div v-for="doc in item.batches[0] || []" :key="doc.id" class="settlement-file">
            <span>{{ doc.fileName }}</span>
            <el-button link type="primary" @click="emit('preview', doc)">预览</el-button>
            <el-button link type="primary" @click="emit('download', doc)">下载</el-button>
            <el-button v-if="item.writable" link type="danger" :disabled="busy || archiving"
              :aria-label="`删除 ${doc.fileName}`" @click="removeFile(item, doc)">删除</el-button>
            <small>{{ doc.uploadBy }} {{ doc.createTime }}</small>
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
          <div v-if="item.writable && editor?.slot !== item.definition.code" class="settlement-actions">
            <el-button v-if="item.batches[0]?.length && !item.notApplicableReason" size="small"
              :disabled="busy || archiving" @click="open(item, 'files', true)">追加文件</el-button>
            <el-button size="small" :disabled="busy || archiving" @click="open(item, 'files')">
              {{ item.complete ? (item.definition.versioned ? '上传新版本' : '替换当前批次') : '上传文件' }}
            </el-button>
            <el-button v-if="item.definition.code === 'U09' && budgetSources.length" size="small" :disabled="busy"
              @click="open(item, 'source')">引用归档资料</el-button>
            <el-button v-if="item.definition.allowsNotApplicable" size="small" :disabled="busy"
              @click="open(item, 'reason')">填写不适用原因</el-button>
          </div>
          <form v-if="editor?.slot === item.definition.code && item.writable"
            class="settlement-editor" :aria-label="`${item.definition.name}资料提交`" @submit.prevent="submit">
            <template v-if="editor.mode === 'files'">
              <label for="settlement-files">本批次文件</label>
              <input id="settlement-files" type="file" multiple :disabled="busy || archiving"
                accept=".pdf,.xls,.xlsx,.doc,.docx,.png,.jpg,.jpeg,.webp,.zip,.rar,.7z,.txt,.ofd"
                aria-describedby="settlement-file-help" @change="addFiles" />
              <small id="settlement-file-help">可多选，也可反复打开选择框添加。每批最多 20 个文件，合计不超过 50 MB。</small>
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
        </article>
      </section>
    </template>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { ElMessageBox } from 'element-plus'
import { archiveSettlementMaterials, deleteSettlementFile, getSettlementMaterials, saveSettlementMaterials } from '../../../api/project'

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

const groups = computed(() => {
  const result = new Map()
  for (const slot of slots.value) {
    const group = slot.definition.group
    if (!result.has(group)) result.set(group, [])
    result.get(group).push(slot)
  }
  return [...result]
})
const required = computed(() => slots.value.filter(slot => slot.definition.required))
const requiredCount = computed(() => required.value.length)
const requiredComplete = computed(() => required.value.filter(slot => slot.complete).length)

async function load() {
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
  const body = new FormData()
  if (mode === 'files') {
    if (!files.length || files.length > 20 || files.reduce((size, file) => size + file.size, 0) > 50 * 1024 * 1024) {
      status.value = '请选择 1–20 个文件，合计不超过 50 MB。'
      return
    }
    files.forEach(file => body.append('files', file))
    if (append) body.append('append', 'true')
  } else if (mode === 'source') {
    body.append('sourceDocId', source)
  } else {
    if (!reason) {
      status.value = '请填写不适用原因。'
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

onMounted(load)
</script>

<style scoped>
.settlement-materials { min-height: 48px; }
.settlement-group { margin-block: 20px; }
.settlement-slot { padding: 12px; margin-block: 8px; }
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
</style>
