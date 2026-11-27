<template>
  <!-- 2026-11-26 P2-16：稳定锚点，供待办「去填写表单」精确定位（原先只能滚到卡片顶部，保存按钮在视口外） -->
  <div id="project-finance-form" class="fin-form">
    <!-- 顶部说明条 -->
    <div class="fin-banner">
      <div class="fin-banner-main">
        <span class="fin-banner-title">会前财务资料</span>
        <span v-if="projectType" class="fin-type-chip">{{ typeLabel }}</span>
        <span class="fin-banner-text">保存表单后，系统会在项目文件中更新合规立项申请表（内容为保存时的快照）。</span>
      </div>
      <span v-if="meta.updateTime" class="fin-banner-meta">上次保存：{{ meta.updateBy || '-' }} · {{ meta.updateTime }}</span>
      <span v-else class="fin-banner-meta">尚未填写</span>
    </div>

    <!-- 合规立项申请表独立入口（2026-11-26 新需求 1）：在线预览 / 下载当前生效版 -->
    <!-- 不再混在通用附件列表里（用户不必辨认哪一份是系统生成的申请表） -->
    <div class="fin-archive-entry">
      <div class="fin-archive-entry-main">
        <span class="fin-archive-entry-title">
          合规立项申请表
          <el-tag size="small" type="success">系统生成</el-tag>
        </span>
        <span v-if="archive.archived" class="fin-archive-entry-meta">
          当前生效版 · {{ projectFileDisplayName(archive, props.projectInfo.name) }} · 生成人：{{ archive.uploadBy || '-' }} · {{ archive.createTime || '-' }}
        </span>
        <span v-else class="fin-archive-entry-meta">
          {{ archive.previewable ? '尚未生成（保存表单后自动生成）' : '仅项目成员可查看' }}
        </span>
      </div>
      <div class="fin-archive-entry-ops">
        <el-button size="small" plain :disabled="!archive.archived" @click="emit('preview', archive)">在线预览</el-button>
        <el-button size="small" type="primary" plain :disabled="!archive.archived" @click="emit('download', archive)">下载当前生效版</el-button>
      </div>
    </div>

    <!-- 未选择项目类型（2026-11-26 P0-3）：给出直达入口，说明不选类型的后果 -->
    <div v-if="!projectType" class="fin-empty-type">
      <el-alert type="warning" :closable="false" show-icon
        title="该项目尚未选择项目类型"
        description="未选择项目类型时，本阶段无法保存表单、也不会生成合规立项申请表，项目将无法推进到下一阶段。请先补选项目类型（平台类 / 直付类 / 重点类）。" />
      <div class="fin-empty-type-actions">
        <el-button type="primary" size="small" @click="emit('edit-project')">去编辑项目选择项目类型</el-button>
        <span class="fin-empty-type-hint">补选后可在本阶段填写对应的财务资料表单。</span>
      </div>
    </div>

    <!-- 差异化表单（分节卡片，配色沿用合规立项申请表的分组口径） -->
    <!-- 2026-11-26 P1-10：改用 el-form rules + scroll-to-error，必填项逐项定位（原先只弹一行汇总） -->
    <!-- 2026-11-26 P2-13：结构由后端 finance-schema 下发（与 Excel 模板同源），前端不再维护 SCHEMAS 副本 -->
    <!-- 2026-11-26 P3-27：表单内容读取失败 → 显式错误态并禁止提交（避免误当「尚未填写」覆盖保存） -->
    <div v-if="loadError" class="fin-load-error">
      <el-alert type="error" :closable="false" show-icon
        title="表单内容加载失败"
        description="未能读取本阶段已保存的财务资料。为避免覆盖他人已填写的内容，暂不可保存。请检查网络后重新加载，确认内容无误再填写。" />
      <el-button size="small" @click="load">重新加载</el-button>
    </div>
    <div v-else-if="schemaError" class="fin-schema-error">
      <el-alert type="error" :closable="false" show-icon
        title="表单结构加载失败"
        description="未能获取本项目的财务资料表单定义（表单结构与合规立项申请表模板同源，由后端统一下发）。请检查网络后重试，或稍后再试。" />
      <el-button size="small" @click="load">重新加载</el-button>
    </div>
    <el-form v-else-if="schema" ref="formRef" class="fin-form-body" label-position="top" :disabled="readonly"
      :model="form" :rules="rules" :validate-on-rule-change="false" scroll-to-error @submit.prevent>
      <div v-for="(sec, si) in sections" :key="si" class="fin-sec" :class="'fin-sec-' + sec.tone">
        <div class="fin-sec-head">
          <span class="fin-sec-dot" :style="{ background: TONE_COLORS[sec.tone] }"></span>
          <span class="fin-sec-title">{{ sec.title }}</span>
          <span v-if="sec.note" class="fin-sec-note">{{ sec.note }}</span>
        </div>
        <el-row :gutter="16">
          <el-col v-for="f in sec.fields" :key="f.key" :span="f.full ? 24 : 12" :xs="24">
            <el-form-item :prop="f.key" :label-width="'auto'" class="fin-item" :class="{ 'fin-item-full': f.full }">
              <template #label>
                <span class="fin-label">
                  {{ f.label }}{{ f.unit || '' }}
                  <span v-if="isFieldRequired(f)" class="fin-required">*</span>
                  <span v-else class="fin-optional">选填</span>
                </span>
              </template>
              <div class="fin-field">
                <el-input v-if="f.type === 'project'" :model-value="projectValues[f.key]"
                  readonly placeholder="请在编辑项目中维护" />
                <!-- 金额 -->
                <el-input-number v-else-if="f.type === 'number'" v-model="form[f.key]"
                  :min="0" :controls="false" :precision="2" placeholder="0.00" style="width: 100%" />
                <!-- 下拉 -->
                <el-select v-else-if="f.type === 'select'" v-model="form[f.key]"
                  :placeholder="f.placeholder || '请选择'" clearable style="width: 100%">
                  <el-option v-for="o in f.options" :key="o" :label="o" :value="o" />
                </el-select>
                <!-- 长文本 -->
                <el-input v-else-if="f.type === 'textarea'" v-model="form[f.key]" type="textarea"
                  :rows="f.rows || 2" maxlength="500" show-word-limit :placeholder="f.placeholder || '请输入'" />
                <!-- 普通文本 -->
                <el-input v-else v-model="form[f.key]" maxlength="200" :placeholder="f.placeholder || '请输入'" />
                <!-- 2026-11-26 P1-7：主数据字段缺失时给出直达入口（本页只读，无法就地修正） -->
                <div v-if="isProjectFieldMissing(f)" class="fin-project-missing">
                  <span class="fin-project-missing-text">项目主数据未维护「{{ f.label }}」</span>
                  <el-button link type="primary" size="small" @click="emit('edit-project')">去编辑项目补充</el-button>
                </div>
              </div>
            </el-form-item>
          </el-col>
        </el-row>
      </div>

      <!-- 操作条 -->
      <div v-if="!readonly" class="fin-actions">
        <span class="fin-actions-hint">带 <span class="fin-required">*</span> 为必填项。再次保存会更新项目文件。</span>
        <el-button type="primary" :loading="saving" @click="save">保存表单</el-button>
      </div>
    </el-form>
  </div>
</template>

<script setup>
import { computed, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { getStageForm, saveStageForm, getFinanceSchema, getFinanceArchive } from '../../../api/project'
import { PROJECT_TYPE_LABELS } from '../../../utils/dict'
import { projectFileDisplayName } from '../../../utils/format'

/**
 * 会前财务资料上传阶段表单（2026-11-20）。
 * 字段结构参考《2026年合规立项申请表8.17.xlsx》三个项目类型 sheet：
 *  - 平台类：项目类型（细分类型）/ 会议信息 / 企业信息（赞助）/ 备注
 *  - 直付类：项目类型 / 会议信息（费用类型）/ 提供内容（公司名称、省区必填）/ 备注
 *  - 重点类：会议信息 / 提供内容（客户负责人、工作单位）/ 备注
 * 一项目一阶段一份，整包覆盖保存（project_stage_form）。
 */
const props = defineProps({
  projectId: { type: [String, Number], required: true },
  phaseId: { type: [String, Number], required: true },
  projectType: { type: String, default: '' },
  projectInfo: { type: Object, required: true },
  readonly: { type: Boolean, default: false }
})
const emit = defineEmits(['saved', 'edit-project', 'preview', 'download'])

// 2026-11-26 P2-13/P2-21：表单结构（分区/字段/中文名/必填/下拉）改由后端 finance-schema 下发，
// 与 Excel 模板同源；前端不再维护 SCHEMAS 副本（曾与后端 LAYOUTS、模板三处各写一份）。
// 此处只保留展示层配置：分区配色与字段占位提示。
const TONE_COLORS = { type: '#91ACE0', meeting: '#ACD78E', biz: '#F7D667', remark: '#BFBFBF' }
const FIELD_PLACEHOLDERS = {
  platform: '如：北京医学检验学会',
  location: '请输入会议地点',
  companyName: '实际提供内容的公司，如：恒瑞',
  sfaNo: '系统编号',
  remark: '可填写其他需要说明的内容'
}

const typeLabel = computed(() => PROJECT_TYPE_LABELS[props.projectType] || props.projectType || '')

// 后端下发的表单结构；未取到时为空（页面提示稍后重试，不渲染半截表单）
const schema = ref(null)
const schemaError = ref(false)

// 2026-11-26 新需求 1：合规立项申请表（当前生效版）独立入口数据
const archive = reactive({ archived: false, previewable: false, fileName: '', filePath: '', uploadBy: '', createTime: '' })

/** 拉取当前生效的合规立项申请表（独立入口；失败不影响表单填写）。 */
async function loadArchive() {
  try {
    const data = await getFinanceArchive(props.projectId, props.phaseId)
    Object.assign(archive, {
      archived: data?.archived === true,
      previewable: data?.previewable === true,
      fileName: data?.fileName || '',
      filePath: data?.filePath || '',
      uploadBy: data?.uploadBy || '',
      createTime: data?.createTime || ''
    })
  } catch (e) {
    Object.assign(archive, { archived: false, previewable: false })
  }
}

// 分区 → 字段（补齐展示层配置：配色、占位提示、整行字段）
const sections = computed(() => {
  const raw = schema.value?.sections || []
  return raw.map((sec) => ({
    ...sec,
    tone: sec.key,
    fields: (sec.fields || []).map((f) => ({
      ...f,
      type: valueTypeToUi(f.valueType),
      placeholder: FIELD_PLACEHOLDERS[f.key] || '',
      full: f.key === 'meetingName' || f.key === 'remark',
      rows: f.key === 'remark' ? 3 : 2
    }))
  }))
})

/** 后端值类型 → 前端控件类型。 */
function valueTypeToUi(valueType) {
  if (valueType === 'SELECT') return 'select'
  if (valueType === 'NUMBER') return 'number'
  if (valueType === 'TEXTAREA') return 'textarea'
  if (valueType === 'PROJECT_FIELD') return 'project'
  return 'text'
}

/** 拉取表单结构（后端单一真源）。 */
async function loadSchema() {
  schemaError.value = false
  if (!props.projectType) {
    schema.value = null
    return
  }
  try {
    const data = await getFinanceSchema(props.projectType)
    schema.value = data && Array.isArray(data.sections) ? data : null
    if (!schema.value) schemaError.value = true
  } catch (e) {
    schema.value = null
    schemaError.value = true
  }
}

// 2026-11-26 P1-8：申请表「提供内容」分组的分组说明为「恒瑞需填写所有信息，其他公司仅需公司名称及省区」，
// 按此实现动态必填：公司名称含「恒瑞」时，本分节其余字段一并转为必填；其余公司仅公司名称与省区必填。
const isHengRui = computed(() => String(form.companyName || '').includes('恒瑞'))

/** 字段是否必填：requiredWhenCompanyIs 的字段随「恒瑞」分支动态必填。 */
function isFieldRequired(f) {
  if (f.requiredWhenCompanyIs) return isHengRui.value
  return !!f.required
}

/** 主数据字段（type=project）在项目里缺失：只读无法就地修正，需引导到「编辑项目」。 */
function isProjectFieldMissing(f) {
  if (f.type !== 'project') return false
  if (!isFieldRequired(f)) return false
  return !projectValues.value[f.key]
}

// 2026-11-26 P1-10：必填规则交给 el-form，validate() 失败时逐项标红并滚动定位
const rules = computed(() => {
  const result = {}
  for (const sec of sections.value) {
    for (const f of sec.fields) {
      if (!isFieldRequired(f)) continue
      result[f.key] = [{
        required: true,
        message: f.type === 'project' ? `${f.label}未在项目中维护` : `${f.label}不能为空`,
        trigger: ['blur', 'change']
      }]
    }
  }
  return result
})

const projectValues = computed(() => {
  const { name, ownerName, meetingStartDate, meetingEndDate } = props.projectInfo
  return {
    meetingName: name || '',
    ourOwner: ownerName || '',
    meetingDate: [...new Set([meetingStartDate, meetingEndDate].filter(Boolean))].join(' 至 ')
  }
})

const form = reactive({})
const meta = reactive({ updateBy: '', updateTime: '' })
const saving = ref(false)
const formRef = ref(null)
// 2026-11-26 P3-27：读取失败显式呈现并禁止提交（原先静默吞掉异常，用户会误以为「尚未填写」而覆盖保存）
const loadError = ref(false)
// 2026-11-26 P3-24：项目信息变化时，未保存过的字段默认值要跟着变；已保存的以财务口径为准
const savedKeys = ref(new Set())
// 已套用的默认值（key → 值）：用于判断用户是否已手动改过，避免项目信息刷新覆盖用户输入
const appliedDefaults = {}
let loadVersion = 0

function blankForm() {
  const next = {}
  for (const sec of sections.value) {
    for (const f of sec.fields) next[f.key] = f.type === 'number' ? null : ''
  }
  return next
}

async function load() {
  const loadId = ++loadVersion
  Object.keys(form).forEach((k) => delete form[k])
  loadError.value = false
  await loadSchema()
  if (loadId !== loadVersion) return
  await loadArchive()
  if (loadId !== loadVersion) return
  if (!schema.value) return
  try {
    const data = await getStageForm(props.projectId, props.phaseId)
    if (loadId !== loadVersion) return
    const saved = data?.formData || {}
    // 2026-11-26 P3-24：记录「已保存过的字段」，只有未保存过的字段才跟随项目信息刷新默认值
    savedKeys.value = new Set(Object.keys(saved))
    const base = blankForm()
    const defaults = projectDefaults()
    for (const field of sections.value.flatMap(section => section.fields)) {
      const { key, options } = field
      let value = saved[key] ?? defaults[key] ?? base[key]
      // 2026-11-26 P2-19：数值字段只接受数字，历史空串/非数字一律归零为「未填写」（null），
      // 避免 el-input-number 收到非数字，以及后端把空串写成文本单元格
      if (field.type === 'number') {
        value = typeof value === 'number' && Number.isFinite(value) ? value : null
      }
      // 下拉值不在后端下发的选项集内时置空（用户需重新选择）；后端已把历史简称归一化，此处只兜底
      const isSelect = field.type === 'select'
      form[key] = isSelect && !options.includes(value) ? '' : value
      // 记录本次套用的默认值，供后续同步时判断用户是否已改过
      if (key in defaults && !savedKeys.value.has(key)) {
        appliedDefaults[key] = defaults[key]
      } else {
        delete appliedDefaults[key]
      }
    }
    syncProjectFields()
    meta.updateBy = data?.updateBy || ''
    meta.updateTime = data?.updateTime || ''
  } catch (e) {
    // 2026-11-26 P3-27：读取失败不再静默——显式报错并禁止提交，避免用户把「读不到」误当成「尚未填写」而覆盖保存
    loadError.value = true
  }
}

/** 项目主数据派生出的默认值（部门 ← 团队，客户对接人 ← 客户姓名）。 */
function projectDefaults() {
  return {
    dept: props.projectInfo.teamLabel || '',
    clientContact: props.projectInfo.clientName || ''
  }
}

/**
 * 2026-11-26 P3-24：项目信息变更后，未保存过该字段的项目默认值随之更新
 * （原先只在首次加载带出，项目调整归属后表单仍显示旧部门，与项目属性不一致）。
 * 用户已手动改过的字段不被覆盖。
 */
function syncProjectDefaults() {
  if (loadError.value) return
  const defaults = projectDefaults()
  for (const sec of sections.value) {
    for (const f of sec.fields) {
      if (!(f.key in defaults)) continue
      if (savedKeys.value.has(f.key)) continue
      if (form[f.key] !== appliedDefaults[f.key]) continue
      form[f.key] = defaults[f.key]
      appliedDefaults[f.key] = defaults[f.key]
    }
  }
}

/**
 * 2026-11-26 P1-10：把主数据字段（type=project）的项目实时值同步进表单模型。
 * 展示值来自 projectValues，校验读的是表单模型，两者不同步会让「显示为空却校验通过」或反之。
 */
function syncProjectFields() {
  for (const sec of sections.value) {
    for (const f of sec.fields) {
      if (f.type !== 'project') continue
      if (!(f.key in form)) form[f.key] = ''
      form[f.key] = projectValues.value[f.key] || ''
    }
  }
}

watch(projectValues, syncProjectFields)
watch(() => props.projectInfo, syncProjectDefaults)

watch(() => [props.projectId, props.phaseId, props.projectType], load, { immediate: true })

async function save() {
  if (!sections.value.length) return
  // 2026-11-26 P3-27：读取失败时禁止提交（否则会把「读不到」当成「尚未填写」而覆盖已有内容）
  if (loadError.value) {
    ElMessage.warning('表单读取失败，请先重新加载确认最新内容后再保存')
    return
  }
  // 2026-11-26 P1-10：必填校验交给 el-form（rules + scroll-to-error），逐项标红并滚动定位，
  // 不再只弹一行「请先填写：X、Y」让人自己找
  let invalid = null
  await formRef.value.validate().catch((e) => { invalid = e })
  if (invalid) {
    const labels = Object.values(invalid).flat().map(item => item.message).filter(Boolean)
    ElMessage.warning(labels.length ? `请先补齐：${labels.join('、')}` : '请先补齐标红的必填项')
    return
  }
  const payload = { ...form }
  for (const key of Object.keys(projectValues.value)) {
    if (key in payload) payload[key] = projectValues.value[key]
  }
  saving.value = true
  try {
    const data = await saveStageForm(props.projectId, {
      phaseId: Number(props.phaseId),
      formData: payload,
      // 2026-11-26 P3-23：带上读取时的最后保存时间作为版本令牌，避免并发保存静默覆盖
      version: meta.updateTime || null
    })
    meta.updateBy = data?.updateBy || ''
    meta.updateTime = data?.updateTime || ''
    loadError.value = false
    // 2026-11-26 P3-24：保存后这些字段已落库，归财务口径，不再跟随项目信息刷新默认值
    savedKeys.value = new Set(Object.keys(payload))
    for (const key of savedKeys.value) delete appliedDefaults[key]
    // 2026-11-26 新需求 1：保存后刷新「当前生效版」入口（归档件刚被替换）
    await loadArchive()
    ElMessage.success('会前财务资料表单已保存')
    emit('saved')
  } finally {
    saving.value = false
  }
}
</script>

<style scoped>
.fin-form { display: flex; flex-direction: column; gap: 12px; }
.fin-banner {
  display: flex; align-items: center; gap: 12px; flex-wrap: wrap;
  padding: 10px 14px; border-radius: 8px;
  background: linear-gradient(90deg, #eef4ff 0%, #f6f9ff 100%);
  border: 1px solid #dbe6ff;
}
.fin-banner-main { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; flex: 1; min-width: 0; }
.fin-banner-title { font-weight: 600; color: #24406b; }
.fin-type-chip {
  padding: 1px 8px; border-radius: 10px; font-size: 12px;
  color: #3a6cf6; background: #dfe9ff; border: 1px solid #c3d6ff;
}
.fin-banner-text { color: #67748e; font-size: 13px; }
.fin-banner-meta { color: #9096a6; font-size: 12px; white-space: nowrap; }
.fin-empty-type { padding: 4px 0; }
.fin-empty-type-actions { display: flex; align-items: center; gap: 10px; margin-top: 10px; }
.fin-empty-type-hint { color: var(--ui-text-muted, #909399); font-size: 12px; }
/* 2026-11-26 P2-13：表单结构加载失败态 */
.fin-schema-error { padding: 4px 0; }
.fin-schema-error .el-button { margin-top: 10px; }
/* 2026-11-26 P3-27：表单内容读取失败态 */
.fin-load-error { padding: 4px 0; }
.fin-load-error .el-button { margin-top: 10px; }
/* 2026-11-26 新需求 1：合规立项申请表独立入口 */
.fin-archive-entry {
  display: flex; align-items: center; gap: 12px; flex-wrap: wrap;
  padding: 10px 14px; border: 1px solid var(--ui-border-weak);
  border-left: 3px solid var(--ui-success); border-radius: var(--ui-radius-sm, 6px);
}
.fin-archive-entry-main { display: flex; flex-direction: column; gap: 2px; flex: 1; min-width: 0; }
.fin-archive-entry-title { display: inline-flex; align-items: center; gap: 6px; font-weight: 600; color: var(--ui-text-main, #303133); font-size: 13px; }
.fin-archive-entry-meta { color: var(--ui-text-muted, #909399); font-size: 12px; }
.fin-archive-entry-ops { display: flex; align-items: center; gap: 8px; margin-left: auto; }

.fin-sec { border: 1px solid #e6eaf2; border-radius: 8px; overflow: hidden; background: #fff; }
.fin-sec + .fin-sec { margin-top: 12px; }
.fin-sec-head {
  display: flex; align-items: center; gap: 8px;
  padding: 8px 14px; background: #f7f9fc; border-bottom: 1px solid #eef1f6;
}
.fin-sec-dot { width: 10px; height: 10px; border-radius: 3px; flex: none; }
.fin-sec-title { font-weight: 600; color: #33415c; font-size: 14px; }
.fin-sec-note { margin-left: auto; color: #b04a52; font-size: 12px; }

.fin-form-body { padding: 12px 14px 4px; }
.fin-form-body .fin-sec:first-child { margin-top: 0; }
.fin-item { margin-bottom: 14px; }
.fin-item-full :deep(.el-textarea) { width: 100%; }
/* 2026-11-26 P1-10：字段内容包裹层，保证 el-form-item 内多元素时输入框仍占满宽度 */
.fin-field { width: 100%; }
/* 2026-11-26 P1-7：主数据字段缺失提示 + 直达入口 */
.fin-project-missing { display: flex; align-items: center; gap: 6px; margin-top: 4px; }
.fin-project-missing-text { color: var(--ui-text-muted, #909399); font-size: 12px; }
.fin-label { display: inline-flex; align-items: center; gap: 4px; color: #4a5568; }
.fin-required { color: #e2686f; font-weight: 700; }
.fin-optional { color: #a8b0bf; font-size: 11px; }

.fin-actions {
  display: flex; align-items: center; justify-content: flex-end; gap: 12px;
  padding: 10px 0 8px;
}
.fin-actions-hint { color: #9096a6; font-size: 12px; margin-right: auto; }

/* el-form disabled 时保持可读性 */
.fin-form-body :deep(.el-form-item.is-disabled .el-input__inner),
.fin-form-body :deep(.el-form-item.is-disabled .el-textarea__inner) { color: #67748e; }
</style>
