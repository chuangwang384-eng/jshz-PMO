<template>
  <el-dialog v-model="visible" :title="presetName ? '专家建档' : projectMode ? '库外专家批量建档' : '批量上传简历'"
    :width="presetName ? 'min(680px, 96vw)' : 'min(1120px, 96vw)'"
    :close-on-click-modal="false" :close-on-press-escape="!busy" :show-close="!busy" @closed="rows = []">
    <div class="toolbar">
      <label class="el-button" :class="{ 'is-disabled': busy }">
        {{ presetName ? '上传简历' : '选择多份简历' }}
        <input type="file" :multiple="!presetName" :accept="extensions" :disabled="busy" @change="addFiles" />
      </label>
      <el-button type="primary" :loading="parsing" :disabled="submitting || !rows.some(canParse)"
        @click="parseWaiting">开始解析</el-button>
      <span>PDF / Word / PPT / 图片，单份最多 {{ toMb(maxBytes) }} MB</span>
      <slot v-if="!busy" name="extra" />
    </div>
    <p v-if="presetName" class="message">参会专家：{{ presetName }}。解析出的姓名一致后才能提交建档。</p>
    <p v-else-if="autoMatchTargets" class="message">解析后按姓名匹配待建档的参会专家。核对资料和证件照后提交。</p>
    <p v-else-if="projectMode" class="message">为每份简历选择对应的参会专家。解析出的姓名一致后才能提交建档。</p>
    <el-empty v-if="!rows.length" :description="presetName ? '上传一份简历后开始解析' : '选择简历后，每份文件会生成一张待核对的专家卡'" />
    <div v-else class="cards" :class="{ 'single-card': presetName }">
      <BatchResumeCard v-for="row in rows" :key="row.id" :fields="row.fields" :photo-url="row.photo?.url" :label="row.file.name">
        <template #header-actions>
          <div v-if="row.photos.length && !finished(row)" class="row-actions portrait-picker" role="group" aria-label="选择证件照">
            <span>选择证件照</span>
            <div v-for="(photo, index) in row.photos" :key="photo.url" class="photo-choice">
              <PhotoImage :src="photo.url" :width="44" :height="44" fit="contain"
                :alt="`候选证件照 ${index + 1}`" />
              <el-button link type="primary" size="small" :aria-label="`使用候选证件照 ${index + 1}`"
                :aria-pressed="row.photo === photo" :disabled="busy"
                @click="row.photo = row.photo === photo ? null : photo">
                {{ row.photo === photo ? '已选用' : '使用' }}
              </el-button>
            </div>
          </div>
        </template>
        <el-select v-if="projectMode && !presetName" :model-value="row.targetName" filterable placeholder="搜索并选择参会专家" aria-label="对应参会专家"
          :disabled="busy || finished(row)" @change="name => setTarget(row, name)">
          <el-option v-for="name in targetNames" :key="name" :label="name" :value="name"
            :disabled="rows.some(other => other.id !== row.id && other.targetName === name)" />
        </el-select>
        <div class="row-actions">
          <el-checkbox v-if="!presetName" v-model="row.selected" :disabled="busy || row.status !== 'ready' || !valid(row)">提交此卡</el-checkbox>
          <el-tag :type="row.status === 'failed' ? 'danger' : row.status === 'saved' ? 'success' : 'info'">{{ statusLabels[row.status] }}</el-tag>
          <el-button link :disabled="busy || finished(row)" @click="row.editing = !row.editing">
            {{ row.editing ? '收起' : '编辑' }}
          </el-button>
          <el-button v-if="row.status === 'failed' || (row.status === 'ready' && !valid(row))"
            link type="primary" :disabled="busy" @click="retry(row)">重试解析</el-button>
          <label v-if="projectMode && row.status === 'failed'" class="el-button replacement">
            更换简历
            <input type="file" :accept="extensions" :disabled="busy" @change="event => replaceFile(row, event)" />
          </label>
          <el-button v-if="!finished(row)" link :disabled="busy" @click="remove(row)">移除</el-button>
        </div>
        <p v-if="row.message" class="message" role="status">{{ row.message }}</p>
        <p v-if="row.status === 'ready' && !valid(row)" class="message">请补全姓名和单位</p>
        <el-form v-if="row.editing" label-width="92px" :disabled="busy || finished(row)">
          <el-form-item v-for="field in editableFields" :key="field" :label="field" :required="requiredFields.includes(field)">
            <el-input v-model="row.fields[field]" :readonly="projectMode && field === '姓名'"
              :type="field === '简介' ? 'textarea' : 'text'" @input="edit(row)" />
          </el-form-item>
        </el-form>
      </BatchResumeCard>
    </div>
    <template #footer>
      <el-button :disabled="busy" @click="visible = false">关闭</el-button>
      <el-button type="primary" :loading="submitting" :disabled="busy || !selectedRows.length" @click="submit">
        {{ presetName ? '确认建档' : `提交已选（${selectedRows.length}）` }}
      </el-button>
    </template>
  </el-dialog>

  <el-dialog v-if="conflict" :model-value="true" append-to-body title="发现同名同单位专家"
    width="min(920px, 96vw)" :show-close="false" :close-on-click-modal="false" :close-on-press-escape="false">
    <el-select v-if="conflict.matches.length > 1" v-model="conflict.index" aria-label="选择对比档案" class="match-select">
      <el-option v-for="(match, index) in conflict.matches" :key="index" :value="index" :label="match.label" />
    </el-select>
    <div class="compare">
      <BatchResumeCard :fields="currentMatch.fields" :photo-url="currentMatch.photoUrl" :label="currentMatch.label" details
        badge="专家库已有档案" badge-type="lib" />
      <BatchResumeCard :fields="conflict.row.fields" :photo-url="conflict.row.photo?.url" :label="conflict.row.file.name" details
        badge="本次简历解析" badge-type="warn" />
    </div>
    <p class="message">左侧为<b>专家库已有档案</b>（蓝色标识），右侧为<b>本次简历解析结果</b>（橙色标识）。{{ conflict.linkOnly ? '关联后沿用现有档案的资料和证件照。' : '覆盖会使用本次确认的内容，未填写的字段和未选择的照片保留原值。' }}</p>
    <template #footer>
      <el-button @click="resolveConflict('cancel')">{{ conflict.linkOnly ? '暂不关联' : '取消当前简历解析' }}</el-button>
      <el-button v-if="!conflict.linkOnly" @click="resolveConflict('keep')">都保留</el-button>
      <el-button type="primary" @click="resolveConflict(conflict.linkOnly ? 'link' : 'replace')">{{ conflict.linkOnly ? '确认关联' : '覆盖' }}</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { computed, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import BatchResumeCard from './BatchResumeCard.vue'
import PhotoImage from './PhotoImage.vue'
import { createExpert, getExpert, listExperts, parseResume, updateExpert } from '../api/expert'
import { createExpertApprovalRequest, submitApprovalRequest, uploadApprovalPhoto } from '../api/approval'
import { uploadLimits, ensureUploadLimits, toMb } from '../utils/limits'

ensureUploadLimits()

const props = defineProps({
  canAdd: Boolean, canEdit: Boolean, targetNames: Array, presetName: String, saveProjectExpert: Function,
  autoMatchTargets: Boolean, maxPendingFiles: { type: Number, default: 10 }
})
const emit = defineEmits(['saved'])
const visible = defineModel({ type: Boolean })
const requiredFields = ['姓名', '单位']
const fieldNames = [...requiredFields, '科室', '职称', '联系方式', '简介', '身份证号', '银行卡号', '银行卡开户行']
const projectFields = ['姓名', '单位', '科室', '职称', '联系方式', '简介']
const projectMode = computed(() => Boolean(props.saveProjectExpert))
const editableFields = computed(() => projectMode.value ? projectFields : fieldNames)
const extensions = '.pdf,.doc,.docx,.ppt,.pptx,.jpg,.jpeg,.png'
const maxBytes = computed(() => uploadLimits.resumeMaxBytes)
const statusLabels = { waiting: '待解析', parsing: '解析中', ready: '待提交', failed: '解析失败', saved: '已提交', cancelled: '已取消' }
const rows = ref([])
const parsing = ref(false)
const submitting = ref(false)
const conflict = ref(null)
const currentMatch = computed(() => conflict.value?.matches[conflict.value.index])
const busy = computed(() => parsing.value || submitting.value)
const selectedRows = computed(() => rows.value.filter(row => (props.presetName || row.selected) && row.status === 'ready' && valid(row)))
let nextId = 0

function valid(row) {
  return (!projectMode.value || hasTarget(row))
    && requiredFields.every(field => String(confirmedFields(row)[field] ?? '').trim())
}

function hasTarget(row) {
  return props.presetName ? row.targetName === props.presetName : props.targetNames.includes(row.targetName)
}

function finished(row) {
  return row.status === 'saved' || row.status === 'cancelled'
}

function addFiles(event) {
  const rejected = []
  for (const file of event.target.files) {
    if (!acceptedFile(file)) {
      rejected.push(file.name)
      continue
    }
    if (props.presetName && rows.value.length) {
      ElMessage.warning('单人建档只可添加一份简历')
      break
    }
    if (projectMode.value && rows.value.filter(row => !finished(row)).length >= props.maxPendingFiles) {
      ElMessage.warning(`单次最多添加 ${props.maxPendingFiles} 份待处理简历`)
      break
    }
    const targetName = props.presetName || ''
    rows.value.push({ id: ++nextId, file, targetName, fields: { 姓名: targetName }, status: 'waiting', selected: false, editing: false, message: '', photos: [], photo: null })
  }
  event.target.value = ''
  if (rejected.length) ElMessage.warning(`文件为空、超限或格式不支持：${rejected.join('、')}`)
}

function acceptedFile(file) {
  return file && extensions.split(',').some(extension => file.name.toLowerCase().endsWith(extension))
    && file.size > 0 && file.size <= maxBytes.value
}

function setTarget(row, name) {
  row.targetName = name
  if (props.autoMatchTargets && row.status === 'ready') {
    row.selected = Boolean(valid(row))
    return
  }
  row.fields = { 姓名: name }
  row.status = 'waiting'
  row.selected = false
  row.editing = false
  row.message = ''
  row.photos = []
  row.photo = null
}

function replaceFile(row, event) {
  const [file] = event.target.files
  event.target.value = ''
  if (!file) return
  if (!acceptedFile(file)) {
    ElMessage.warning('文件为空、超限或格式不支持')
    return
  }
  row.file = file
  setTarget(row, row.targetName)
  if (canParse(row)) retry(row)
}

function canParse(row) {
  return row.status === 'waiting' && (!projectMode.value || props.autoMatchTargets || hasTarget(row))
}

function remove(row) {
  rows.value = rows.value.filter(item => item.id !== row.id)
}

function edit(row) {
  if (projectMode.value) return
  if (row.status === 'failed' && valid(row)) {
    row.status = 'ready'
    row.selected = true
    row.message = '已手动补填'
  }
}

async function parseRow(row) {
  row.status = 'parsing'
  try {
    const result = await parseResume(row.file)
    if (projectMode.value) {
      const parsedName = String(result?.fields?.姓名 ?? '').trim()
      if (props.autoMatchTargets && !row.targetName && props.targetNames.includes(parsedName)
        && !rows.value.some(other => other !== row && other.targetName === parsedName && !finished(other))) {
        row.targetName = parsedName
      }
      if (!props.autoMatchTargets && (!parsedName || parsedName !== row.targetName)) {
        row.status = 'failed'
        row.selected = false
        row.editing = false
        row.message = !parsedName ? '未从简历识别到姓名' : !row.targetName
          ? `「${parsedName}」无法唯一匹配待建档名单，或本批已添加该专家`
          : `解析姓名「${parsedName}」与所选专家「${row.targetName}」不一致`
        return
      }
      if (props.autoMatchTargets) row.fields.姓名 = parsedName
    }
    for (const field of editableFields.value) {
      if (!String(row.fields[field] ?? '').trim() && result?.fields?.[field] != null) {
        row.fields[field] = projectMode.value && field === '联系方式'
          ? fieldValue(field, result.fields[field]) : String(result.fields[field])
      }
    }
    row.photos = (result?.photoCandidates || []).filter(Boolean).map(candidate => {
      const base64 = typeof candidate === 'string' ? candidate : candidate.base64
      const mime = candidate.mime === 'image/png' ? 'image/png' : 'image/jpeg'
      return { mime, base64, url: `data:${mime};base64,${base64}` }
    }).filter(photo => photo.base64)
    row.status = projectMode.value || result?.hitCount || row.photos.length || valid(row) ? 'ready' : 'failed'
    row.message = props.autoMatchTargets && !row.targetName ? '请搜索并选择对应的参会专家'
      : result?.message || (row.status === 'failed' ? '未识别到可用内容，可重试或手动补填' : '')
    row.selected = row.status === 'ready' && Boolean(valid(row))
    row.editing = true
  } catch (error) {
    row.status = 'failed'
    row.selected = false
    row.message = error?.response?.data?.message || '解析失败，可重试或手动补填'
    row.editing = true
  }
}

async function retry(row) {
  if (projectMode.value && !props.autoMatchTargets && !hasTarget(row)) return
  parsing.value = true
  try {
    await parseRow(row)
  } finally {
    parsing.value = false
  }
}

async function parseWaiting() {
  parsing.value = true
  try {
    for (const row of rows.value.filter(canParse)) await parseRow(row)
  } finally {
    parsing.value = false
  }
}

function sameExpert(left, right) {
  return requiredFields.every(field => String(left[field] ?? '').trim() === String(right[field] ?? '').trim())
}

function askConflict(row, matches, linkOnly = false) {
  return new Promise(resolve => {
    conflict.value = { row, matches, index: 0, resolve, linkOnly }
  })
}

function resolveConflict(action) {
  const { resolve } = conflict.value
  const match = currentMatch.value
  conflict.value = null
  resolve({ action, match })
}

function cancelRow(row) {
  row.selected = false
  row.status = 'cancelled'
  row.message = '已取消此条，未提交'
}

async function findMatches(row) {
  const input = confirmedFields(row)
  const matches = []
  const size = 50
  let page = 1
  let total
  do {
    const result = await listExperts({ keyword: input.姓名, unit: input.单位, page, size })
    matches.push(...(result.items || []).filter(expert => sameExpert(expert, input)))
    total = result.total || 0
    page++
  } while ((page - 1) * size < total)
  return Promise.all(matches.map(async match => {
    const fields = await getExpert(match.id)
    return { id: match.id, label: `库内专家卡 #${match.id}`, fields: { ...fields, 简介: fields.原始简介 ?? fields.简介 } }
  }))
}

function confirmedFields(row) {
  return Object.fromEntries(editableFields.value.map(field => [
    field, fieldValue(field, props.autoMatchTargets && field === '姓名' ? row.targetName : row.fields[field])
  ]).filter(([, value]) => value))
}

function fieldValue(field, raw) {
  const value = String(raw ?? '').trim()
  return projectMode.value && field === '联系方式' && value === '无' ? '' : value
}

async function saveRow(row, target) {
  const changes = confirmedFields(row)
  if (props.autoMatchTargets && target) return props.saveProjectExpert(changes, null, target.id)
  const photo = row.photo && new File(
    [Uint8Array.from(atob(row.photo.base64), character => character.charCodeAt(0))],
    row.photo.mime === 'image/png' ? 'resume-photo.png' : 'resume-photo.jpg',
    { type: row.photo.mime }
  )
  if (projectMode.value) return props.saveProjectExpert(changes, photo)
  const direct = target ? props.canEdit : props.canAdd
  if (direct) {
    let payload = changes
    if (photo) {
      payload = new FormData()
      for (const [field, value] of Object.entries(changes)) payload.append(field, value)
      payload.append('photo', photo)
    }
    if (target) await updateExpert(target.id, payload)
    else await createExpert(payload)
    return target ? '已更新专家卡' : '已新建专家卡'
  }
  if (photo) {
    const uploaded = await uploadApprovalPhoto(photo)
    changes.照片 = 'TMP:' + uploaded.fileName
  }
  const reason = '批量上传简历'
  if (target) await submitApprovalRequest({ expertId: target.id, changes, reason })
  else await createExpertApprovalRequest({ changes, reason })
  return '已提交审批'
}

function invalidField(row) {
  const fields = confirmedFields(row)
  if (!projectMode.value && !/^[\u4e00-\u9fa5·]{2,20}$/.test(fields.姓名)) return '姓名格式不正确（2–20 个汉字，可含间隔号）'
  if (fields.身份证号 && !/^\d{17}[\dXx]$/.test(fields.身份证号)) return '身份证号格式不正确'
  if (fields.银行卡号 && !/^\d{13,19}$/.test(fields.银行卡号)) return '银行卡号格式不正确'
  if (fields.联系方式 && !/^1\d{10}$|^[\w.-]+@[\w.-]+\.[a-zA-Z]{2,}$/.test(fields.联系方式)) return '联系方式须为手机号或邮箱'
  return ''
}

async function submit() {
  submitting.value = true
  let saved = 0
  try {
    const candidates = []
    for (const row of selectedRows.value) {
      const validationMessage = invalidField(row)
      if (validationMessage) {
        row.message = validationMessage
        row.editing = true
        continue
      }
      if (projectMode.value) {
        candidates.push(row)
        continue
      }
      const duplicates = candidates.filter(item => sameExpert(item.fields, row.fields))
      if (duplicates.length) {
        const { action, match } = await askConflict(row, duplicates.map(item => ({
          row: item, fields: item.fields, label: item.file.name, photoUrl: item.photo?.url
        })))
        if (action === 'cancel') {
          cancelRow(row)
          continue
        }
        if (action === 'replace') {
          row.fields = { ...match.row.fields, ...confirmedFields(row) }
          if (!row.photo) row.photo = match.row.photo
          cancelRow(match.row)
          candidates.splice(candidates.indexOf(match.row), 1)
        }
      }
      candidates.push(row)
    }

    // Resolve existing records before writing any row, so same-batch duplicates are compared only once.
    const pending = []
    for (const row of candidates) {
      if (projectMode.value && !props.autoMatchTargets) {
        pending.push({ row })
        continue
      }
      try {
        const parsedName = String(row.fields.姓名 ?? '').trim()
        if (props.autoMatchTargets && parsedName !== row.targetName) {
          try {
            await ElMessageBox.confirm(
              `简历姓名：${parsedName || '未识别'}。确认以参会专家「${row.targetName}」保存并关联？`,
              '确认参会专家姓名', { type: 'warning', confirmButtonText: '确认使用参会姓名' }
            )
          } catch {
            continue
          }
        }
        const matches = await findMatches(row)
        let target
        if (matches.length) {
          const { action, match } = await askConflict(row, matches, props.autoMatchTargets)
          if (action === 'cancel') {
            if (!props.autoMatchTargets) cancelRow(row)
            continue
          }
          if (action === 'link') target = match
          if (action === 'replace') {
            if (pending.some(item => item.target?.id === match.id)) {
              row.message = '该专家卡已被本批另一条选为覆盖目标，请单独核对后再提交'
              continue
            }
            target = match
          }
        }
        pending.push({ row, target })
      } catch {
        row.message = '查重或读取专家卡失败，请重试提交'
      }
    }
    for (const { row, target } of pending) {
      try {
        row.message = await saveRow(row, target)
        row.status = 'saved'
        row.selected = false
        row.editing = false
        saved++
      } catch (error) {
        row.selected = false
        row.message = error?.response?.data?.message || (typeof error?.code === 'number' && error.message)
          || '未能确认提交结果，请核对专家库或我的申请后再重试'
      }
    }
  } finally {
    submitting.value = false
    if (saved) {
      ElMessage.success(`已处理 ${saved} 张专家卡`)
      emit('saved')
    }
  }
}
</script>

<style scoped>
.toolbar, .row-actions { display: flex; flex-wrap: wrap; align-items: center; gap: 10px; }
.toolbar { margin-bottom: 14px; }
.toolbar label, .replacement { position: relative; }
.toolbar input, .replacement input { position: absolute; inset: 0; width: 100%; opacity: 0; cursor: pointer; }
.toolbar label:focus-within, .replacement:focus-within { outline: 2px solid var(--el-color-primary); }
.cards, .compare { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 12px; max-height: 60vh; overflow: auto; }
.cards.single-card { grid-template-columns: 1fr; }
.message { color: var(--el-text-color-secondary); overflow-wrap: anywhere; }
.match-select { width: 100%; margin-bottom: 12px; }
.photo-choice { display: flex; flex-direction: column; align-items: center; gap: 4px; }
.portrait-picker { margin-inline-start: auto; }
@media (max-width: 640px) {
  .cards, .compare { grid-template-columns: 1fr; }
}
</style>
