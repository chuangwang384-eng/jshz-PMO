<template>
  <div v-loading="loading" class="closeout">
    <el-alert v-if="failed" title="结项凭证加载失败" type="error" :closable="false">
      <el-button link type="primary" @click="load">重试</el-button>
    </el-alert>
    <template v-else-if="overview">
      <div class="closeout-summary" role="status">
        <span>已确认 {{ confirmedCount }} / {{ overview.items.length }} 项</span>
        <el-tag v-if="overview.completed" type="success">已完成</el-tag>
        <template v-else-if="overview.canComplete">
          <span v-if="!ready" class="ui-desc">确认全部完结证明后可完结</span>
          <el-button type="primary" :disabled="!ready || Boolean(saving)" :loading="completing" @click="finish">
            项目完结
          </el-button>
        </template>
      </div>
      <section v-for="item in overview.items" :key="item.code" class="closeout-item">
        <div>
          <h3 :id="`closeout-item-${item.code}`">{{ item.label }}</h3>
          <p class="ui-desc">凭证要求 · {{ item.evidence }}</p>
          <el-tag :type="item.confirmed ? 'success' : 'info'" size="small">
            {{ item.confirmed ? '已确认' : '待上传' }}
          </el-tag>
        </div>
        <form v-if="overview.writable" method="post" class="closeout-form"
          :aria-labelledby="`closeout-item-${item.code}`" @submit.prevent="submit(item, $event)">
          <label :for="`closeout-files-${item.code}`">{{ item.files.length ? '选择替换凭证' : '选择凭证文件' }}</label>
          <input :id="`closeout-files-${item.code}`" name="files" type="file" multiple required :disabled="Boolean(saving)"
            accept=".pdf,.xls,.xlsx,.doc,.docx,.png,.jpg,.jpeg,.webp,.zip,.rar,.7z,.txt,.ofd" />
          <el-button type="primary" native-type="submit" :loading="saving === item.code" :disabled="Boolean(saving)">
            {{ item.files.length ? '替换凭证并确认' : '上传凭证并确认' }}
          </el-button>
        </form>
        <ul v-if="item.files.length" class="closeout-files">
          <li v-for="file in item.files" :key="file.id">
            <span>{{ file.fileName }}</span>
            <el-button link type="primary" @click="emit('preview', file)">预览</el-button>
            <el-button link type="primary" @click="emit('download', file)">下载</el-button>
          </li>
        </ul>
      </section>
    </template>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { completeProject, getProjectCloseout, saveProjectCloseout } from '../../../api/project'
import { ensureUploadLimits, toMb, uploadLimits } from '../../../utils/limits'

const props = defineProps({
  projectId: { type: [String, Number], required: true },
  phaseId: { type: [String, Number], required: true }
})
const emit = defineEmits(['saved', 'preview', 'download'])
const loading = ref(false)
const failed = ref(false)
const saving = ref('')
const completing = ref(false)
const overview = ref(null)
const confirmedCount = computed(() => overview.value?.items.filter(item => item.confirmed).length ?? 0)
const ready = computed(() => confirmedCount.value > 0 && confirmedCount.value === overview.value?.items.length)

async function finish() {
  if (completing.value || saving.value || !ready.value || !overview.value?.canComplete) return
  completing.value = true
  try {
    await ElMessageBox.confirm('完结后，项目将归入已完成档案并设为只读。确认完结？', '项目完结', {
      confirmButtonText: '确认完结', cancelButtonText: '取消', type: 'warning'
    })
    await completeProject(props.projectId)
    await load()
    emit('saved')
  } catch {
    // 取消确认或请求失败，保留当前状态。
  } finally {
    completing.value = false
  }
}

async function load() {
  loading.value = true
  failed.value = false
  try {
    overview.value = await getProjectCloseout(props.projectId, props.phaseId)
  } catch {
    failed.value = true
  } finally {
    loading.value = false
  }
}

async function submit(item, event) {
  if (saving.value || completing.value || !overview.value?.writable) return
  const form = event.currentTarget
  const body = new FormData(form)
  const files = body.getAll('files').filter(file => file.name)
  if (!files.length) return
  if (files.some(file => file.size > uploadLimits.docMaxBytes)) {
    ElMessage.warning(`单文件不能超过 ${toMb(uploadLimits.docMaxBytes)} MB`)
    return
  }
  if (files.reduce((total, file) => total + file.size, 0) > uploadLimits.settlementBatchMaxBytes) {
    ElMessage.warning(`本批凭证不能超过 ${toMb(uploadLimits.settlementBatchMaxBytes)} MB`)
    return
  }
  saving.value = item.code
  try {
    await saveProjectCloseout(props.projectId, props.phaseId, item.code, body)
    form.reset()
    await load()
    emit('saved')
  } catch {
    // 请求层已显示错误，保留选择的文件供重试。
  } finally {
    saving.value = ''
  }
}

onMounted(() => {
  ensureUploadLimits()
  load()
})
</script>

<style scoped>
.closeout { min-block-size: 48px; }
.closeout-summary, .closeout-form { display: flex; flex-wrap: wrap; align-items: center; gap: 8px; }
.closeout-summary { justify-content: space-between; padding-block: 6px 12px; }
.closeout-item {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 8px 16px;
  align-items: center;
  padding-block: 14px;
  border-block-start: 1px solid var(--el-border-color-lighter);
}
.closeout-item h3, .closeout-item p { margin-block: 0 5px; }
.closeout-form input { max-inline-size: 100%; }
.closeout-files { grid-column: 1 / -1; margin: 0; padding-inline-start: 20px; }
.closeout-files li { overflow-wrap: anywhere; }
.closeout-files span { margin-inline-end: 8px; }
@media (max-width: 1080px) {
  .closeout-item { grid-template-columns: minmax(0, 1fr); }
}
</style>
