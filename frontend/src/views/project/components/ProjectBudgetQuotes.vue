<template>
  <section class="quote-versions">
    <div class="ui-toolbar quote-header">
      <div>
        <h3>对客报价</h3>
        <p>生成的 Excel 自动归入本阶段资料。</p>
      </div>
      <el-button v-if="!readonly && confirmed" size="small" type="primary" plain
                 @click="dialog = 'generate'">生成对客报价</el-button>
    </div>
    <el-alert v-if="error" :title="error" type="error" :closable="false" show-icon/>
    <el-empty v-if="!loading && !error && !versions.length" description="暂无对客报价" :image-size="48"/>
    <el-table v-else v-loading="loading" :data="versions" size="small">
      <el-table-column label="报价文件" min-width="150">
        <template #default="{ row }">
          <el-button link type="primary" @click="download(row)">会议报价-V{{ row.versionNo }}.xlsx</el-button>
        </template>
      </el-table-column>
      <el-table-column label="客户确认" min-width="170">
        <template #default="{ row }">
          <template v-if="row.recordedAt">
            <div>{{ row.customerName }} · {{ formatTime(row.customerConfirmedAt) }}</div>
            <div v-if="row.customerRemark">{{ row.customerRemark }}</div>
            <div v-if="row.evidenceDocId">凭证 {{ evidenceName(row.evidenceDocId) }}</div>
          </template>
          <span v-else class="ui-badge ui-badge-info">待确认</span>
        </template>
      </el-table-column>
      <el-table-column v-if="canRecord || (!readonly && confirmed)" label="操作" min-width="190">
        <template #default="{ row }">
          <el-button v-if="canRecord && !row.recordedAt" link type="primary" @click="openConfirmation(row)">登记客户确认</el-button>
          <el-button v-if="!readonly && confirmed" link type="primary" @click="restore(row)">另存为草稿</el-button>
        </template>
      </el-table-column>
    </el-table>
    <el-dialog :model-value="!!dialog" :title="dialog === 'generate' ? '生成对客报价' : '登记客户确认'"
               :close-on-click-modal="!working" :close-on-press-escape="!working" :show-close="!working"
               width="min(520px, 95vw)" @update:model-value="value => { if (!value) dialog = '' }">
      <el-form label-position="top">
        <el-form-item v-if="dialog === 'generate'" label="对客说明">
          <el-input v-model="clientNote" type="textarea" maxlength="1000" :rows="3"/>
        </el-form-item>
        <template v-else>
          <el-form-item label="客户确认人" required>
            <el-input v-model="form.customerName" maxlength="100"/>
          </el-form-item>
          <el-form-item label="确认时间" required>
            <el-date-picker v-model="form.confirmedAt" type="datetime" value-format="YYYY-MM-DDTHH:mm:ss"/>
          </el-form-item>
          <el-form-item label="备注">
            <el-input v-model="form.remark" type="textarea" maxlength="1000"/>
          </el-form-item>
          <el-form-item label="确认凭证">
            <el-select v-model="form.evidenceDocId" placeholder="选择已上传的项目文件" clearable filterable>
              <el-option v-for="doc in evidenceOptions" :key="doc.id" :label="doc.fileName" :value="doc.id"/>
            </el-select>
          </el-form-item>
        </template>
      </el-form>
      <template #footer>
        <el-button :disabled="working" @click="dialog = ''">取消</el-button>
        <el-button type="primary" :loading="working" @click="submit">{{ dialog === 'generate' ? '生成' : '登记' }}</el-button>
      </template>
    </el-dialog>
  </section>
</template>

<script setup>
import { computed, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { listBudgetQuotes, generateBudgetQuote, downloadBudgetQuote, recordBudgetQuoteConfirmation,
  restoreBudgetQuote } from '../../../api/project'

const props = defineProps({
  projectId: { type: [String, Number], required: true },
  readonly: Boolean,
  canRecord: Boolean,
  confirmed: Boolean,
  documents: { type: Array, default: () => [] }
})
const emit = defineEmits(['generated', 'restored'])
const versions = ref([])
const error = ref('')
const loading = ref(false)
const working = ref(false)
const dialog = ref('')
const clientNote = ref('')
const form = ref({})
const active = ref(null)
let pendingGeneration
const evidenceOptions = computed(() => props.documents.filter(doc => !doc.isRichText
  && !versions.value.some(version => version.docId === doc.id)))
const evidenceName = id => props.documents.find(doc => doc.id === id)?.fileName || `#${id}`
const formatTime = value => String(value || '').replace('T', ' ')
const message = e => e?.response?.data?.message || e?.message || '操作失败'

async function load() {
  loading.value = true
  error.value = ''
  try {
    versions.value = await listBudgetQuotes(props.projectId)
  } catch (e) {
    error.value = message(e)
  } finally {
    loading.value = false
  }
}
watch(() => props.projectId, load, { immediate: true })

function openConfirmation(row) {
  active.value = row
  form.value = {}
  dialog.value = 'confirm'
}

async function submit() {
  if (working.value) return
  if (dialog.value !== 'generate' && (!form.value.customerName?.trim() || !form.value.confirmedAt)) {
    ElMessage.warning('请填写客户确认人和确认时间')
    return
  }
  working.value = true
  try {
    if (dialog.value === 'generate') {
      const note = clientNote.value.trim()
      if (!pendingGeneration || pendingGeneration.clientNote !== note || pendingGeneration.projectId !== props.projectId) {
        const random = globalThis.crypto.getRandomValues(new Uint8Array(16))
        random[6] = (random[6] & 15) | 64
        random[8] = (random[8] & 63) | 128
        const hex = Array.from(random, byte => byte.toString(16).padStart(2, '0')).join('')
        const requestKey = `${hex.slice(0, 8)}-${hex.slice(8, 12)}-${hex.slice(12, 16)}-${hex.slice(16, 20)}-${hex.slice(20)}`
        pendingGeneration = { projectId: props.projectId, requestKey, clientNote: note }
      }
      await generateBudgetQuote(props.projectId, pendingGeneration)
      pendingGeneration = null
      emit('generated')
      ElMessage.success('报价已生成并挂载到阶段资料')
    } else {
      await recordBudgetQuoteConfirmation(props.projectId, active.value.id, form.value)
      ElMessage.success('客户确认已登记')
    }
    dialog.value = ''
    await load()
  } catch (e) {
    ElMessage.error(message(e))
  } finally {
    working.value = false
  }
}

async function download(row) {
  try {
    const response = await downloadBudgetQuote(props.projectId, row.id)
    const url = URL.createObjectURL(response.data)
    const link = document.createElement('a')
    link.href = url
    link.download = `会议报价-V${row.versionNo}.xlsx`
    link.click()
    setTimeout(() => URL.revokeObjectURL(url), 1000)
  } catch (e) {
    ElMessage.error(message(e))
  }
}

async function restore(row) {
  try {
    await ElMessageBox.confirm(`从 V${row.versionNo} 另存为编辑草稿？历史报价和客户确认记录会保留。`, '另存为草稿')
  } catch {
    return
  }
  try {
    emit('restored', await restoreBudgetQuote(props.projectId, row.id))
    ElMessage.success('已另存为草稿')
  } catch (e) {
    ElMessage.error(message(e))
  }
}
</script>

<style scoped>
.quote-versions { min-width: 0; margin-top: 16px; padding: 12px; border: 1px solid var(--ui-border-weak); border-radius: var(--ui-radius-lg); }
.quote-header { flex-wrap: wrap; justify-content: space-between; gap: 8px; margin-bottom: 12px; }
.quote-header h3 { margin: 0 0 4px; color: var(--ui-text-main); font-size: 16px; }
.quote-header p { margin: 0; color: var(--ui-text-sub); font-size: 13px; }
.quote-versions :deep(.el-table) { --el-table-header-bg-color: var(--ui-fill); }
</style>
