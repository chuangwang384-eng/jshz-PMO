<template>
  <div class="fin-form">
    <!-- 顶部说明条 -->
    <div class="fin-banner">
      <div class="fin-banner-main">
        <span class="fin-banner-title">会前财务资料</span>
        <span v-if="projectType" class="fin-type-chip">{{ typeLabel }}</span>
        <span class="fin-banner-text">保存表单后，系统会在项目文件中更新合规立项申请表。</span>
      </div>
      <span v-if="meta.updateTime" class="fin-banner-meta">上次保存：{{ meta.updateBy || '-' }} · {{ meta.updateTime }}</span>
      <span v-else class="fin-banner-meta">尚未填写</span>
    </div>

    <!-- 未选择项目类型 -->
    <div v-if="!projectType" class="fin-empty-type">
      <el-alert type="warning" :closable="false" show-icon
        title="该项目尚未选择项目类型"
        description="请先通过「编辑项目」选择项目类型（平台类 / 直付类 / 重点类），选择后本阶段将展示对应类型的财务资料表单。" />
    </div>

    <!-- 差异化表单（分节卡片，配色沿用合规立项申请表的分组口径） -->
    <el-form v-else class="fin-form-body" label-position="top" :disabled="readonly" @submit.prevent>
      <div v-for="(sec, si) in sections" :key="si" class="fin-sec" :class="'fin-sec-' + sec.tone">
        <div class="fin-sec-head">
          <span class="fin-sec-dot" :style="{ background: TONE_COLORS[sec.tone] }"></span>
          <span class="fin-sec-title">{{ sec.title }}</span>
          <span v-if="sec.note" class="fin-sec-note">{{ sec.note }}</span>
        </div>
        <el-row :gutter="16">
          <el-col v-for="f in sec.fields" :key="f.key" :span="f.full ? 24 : 12" :xs="24">
            <el-form-item :label-width="'auto'" class="fin-item" :class="{ 'fin-item-full': f.full }">
              <template #label>
                <span class="fin-label">
                  {{ f.label }}
                  <span v-if="f.required" class="fin-required">*</span>
                  <span v-else class="fin-optional">选填</span>
                </span>
              </template>
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
import { getStageForm, saveStageForm } from '../../../api/project'

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
const emit = defineEmits(['saved'])

const TYPE_LABELS = { PLATFORM: '平台类项目', DIRECT_PAY: '直付类项目', KEY: '重点类项目' }
const TONE_COLORS = { type: '#91ACE0', meeting: '#ACD78E', biz: '#F7D667', remark: '#BFBFBF' }

const OUR_COMPANY_OPTIONS = ['景帅', '华秀', '偌君', '会信盈']
const PLATFORM_COMPANIES = ['河南景帅', '郑州华秀', '河南偌君', '河南云展汇', '上海独家记忆', '北京际格']
const SUB_TYPE_OPTIONS = ['单会立项', '系列会立项', '系列单场立项']
const EXPENSE_TYPE_OPTIONS = ['会议费', '设计费', '物料费', '其他']

// 字段 key 与后端 form_data JSON 约定，改动需同步交接文档
const SCHEMAS = {
  PLATFORM: [
    { tone: 'type', title: '项目类型', fields: [
      { key: 'subType', label: '细分类型', type: 'select', options: SUB_TYPE_OPTIONS, required: true }
    ] },
    { tone: 'meeting', title: '会议信息', fields: [
      { key: 'platform', label: '依托平台', type: 'text', placeholder: '如：北京医学检验学会' },
      { key: 'ourCompany', label: '我方公司', type: 'select', options: PLATFORM_COMPANIES, required: true },
      { key: 'ourOwner', label: '我方项目负责人', type: 'project' },
      { key: 'dept', label: '会议归属部门', type: 'text' },
      { key: 'meetingDate', label: '会议日期', type: 'project', required: true },
      { key: 'clientContact', label: '客户对接人', type: 'text' },
      { key: 'location', label: '地点', type: 'text', placeholder: '请输入会议地点' },
      { key: 'meetingName', label: '会议/项目名称', type: 'project', required: true, full: true }
    ] },
    { tone: 'biz', title: '企业信息', fields: [
      { key: 'sponsorAmount', label: '赞助金额（元）', type: 'number' },
      { key: 'sponsorCompany', label: '赞助企业', type: 'text' },
      { key: 'productLine', label: '产品线', type: 'text' },
      { key: 'province', label: '省区', type: 'text' }
    ] },
    { tone: 'remark', title: '备注', fields: [
      { key: 'remark', label: '备注', type: 'textarea', rows: 3, full: true, placeholder: '可填写其他需要说明的内容' }
    ] }
  ],
  DIRECT_PAY: [
    { tone: 'type', title: '项目类型', fields: [
      { key: 'subType', label: '细分类型', type: 'select', options: ['有合同', '无合同'], required: true }
    ] },
    { tone: 'meeting', title: '会议信息', fields: [
      { key: 'ourCompany', label: '我方公司', type: 'select', options: OUR_COMPANY_OPTIONS, required: true },
      { key: 'expenseType', label: '费用类型', type: 'select', options: EXPENSE_TYPE_OPTIONS },
      { key: 'ourOwner', label: '我方项目负责人', type: 'project' },
      { key: 'dept', label: '会议归属部门', type: 'text' },
      { key: 'meetingDate', label: '会议日期', type: 'project', required: true },
      { key: 'clientContact', label: '客户对接人', type: 'text' },
      { key: 'location', label: '地点', type: 'text', placeholder: '请输入会议地点' },
      { key: 'meetingName', label: '会议/项目名称', type: 'project', required: true, full: true }
    ] },
    { tone: 'biz', title: '提供内容',
      note: '恒瑞需填写所有信息，其他公司仅需公司名称及省区',
      fields: [
      { key: 'companyName', label: '公司名称', type: 'select', options: OUR_COMPANY_OPTIONS, required: true },
      { key: 'businessUnit', label: '事业部', type: 'text' },
      { key: 'productLine', label: '产品线', type: 'text' },
      { key: 'province', label: '省区', type: 'text', required: true },
      { key: 'bookDept', label: '记账部门', type: 'text' },
      { key: 'sfaNo', label: 'SFA/HOPES/CRM/钉钉', type: 'text', placeholder: '系统编号' }
    ] },
    { tone: 'remark', title: '备注', fields: [
      { key: 'remark', label: '备注', type: 'textarea', rows: 3, full: true, placeholder: '可填写其他需要说明的内容' }
    ] }
  ],
  KEY: [
    { tone: 'meeting', title: '会议信息', fields: [
      { key: 'meetingDate', label: '会议日期', type: 'project', required: true },
      { key: 'clientContact', label: '客户对接人', type: 'text' },
      { key: 'location', label: '地点', type: 'text', placeholder: '请输入会议地点' },
      { key: 'meetingName', label: '会议/项目名称', type: 'project', required: true, full: true }
    ] },
    { tone: 'biz', title: '提供内容', fields: [
      { key: 'clientOwner', label: '客户负责人', type: 'text' },
      { key: 'clientWorkUnit', label: '客户工作单位', type: 'text' }
    ] },
    { tone: 'remark', title: '备注', fields: [
      { key: 'remark', label: '备注', type: 'textarea', rows: 3, full: true, placeholder: '可填写其他需要说明的内容' }
    ] }
  ]
}

const typeLabel = computed(() => TYPE_LABELS[props.projectType] || props.projectType || '')
const sections = computed(() => SCHEMAS[props.projectType] || [])

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
let loadVersion = 0

function blankForm() {
  const next = {}
  for (const sec of SCHEMAS[props.projectType] || []) {
    for (const f of sec.fields) next[f.key] = ''
  }
  return next
}

async function load() {
  const loadId = ++loadVersion
  Object.keys(form).forEach((k) => delete form[k])
  try {
    const data = await getStageForm(props.projectId, props.phaseId)
    if (loadId !== loadVersion) return
    const saved = data?.formData || {}
    const base = blankForm()
    const defaults = { dept: props.projectInfo.teamLabel, clientContact: props.projectInfo.clientName }
    for (const field of sections.value.flatMap(section => section.fields)) {
      const { key, options } = field
      let value = saved[key] ?? defaults[key] ?? base[key]
      const platformCompany = key === 'ourCompany' && props.projectType === 'PLATFORM'
      if (platformCompany) {
        value = { 景帅: '河南景帅', 华秀: '郑州华秀', 偌君: '河南偌君' }[value] ?? value
      }
      const changedOptions = platformCompany || (key === 'subType' && props.projectType === 'DIRECT_PAY')
      form[key] = changedOptions && !options.includes(value) ? '' : value
    }
    meta.updateBy = data?.updateBy || ''
    meta.updateTime = data?.updateTime || ''
  } catch (e) {
    // 读取失败不阻断表单填写（保持空白可编辑），错误提示由 request 拦截器统一弹出
  }
}

watch(() => [props.projectId, props.phaseId, props.projectType], load, { immediate: true })

async function save() {
  if (!sections.value.length) return
  // 建议必填校验（前端拦截；后端为宽松存储，不阻断历史数据）
  const payload = { ...form }
  for (const key of Object.keys(projectValues.value)) {
    if (key in payload) payload[key] = projectValues.value[key]
  }
  const missing = []
  for (const sec of sections.value) {
    for (const f of sec.fields) {
      if (f.required && (payload[f.key] === '' || payload[f.key] === null || payload[f.key] === undefined)) {
        missing.push(f.label)
      }
    }
  }
  if (missing.length) {
    ElMessage.warning(`请先填写：${missing.join('、')}`)
    return
  }
  saving.value = true
  try {
    const data = await saveStageForm(props.projectId, {
      phaseId: Number(props.phaseId),
      formData: payload
    })
    meta.updateBy = data?.updateBy || ''
    meta.updateTime = data?.updateTime || ''
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
