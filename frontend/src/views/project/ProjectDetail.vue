<template>
  <div v-loading="loading">
    <el-page-header :content="detail.name || '项目详情'" @back="$router.push('/project')" />

    <!-- 操作区 -->
    <div v-if="detail.id" class="action-bar">
      <el-button v-if="detail.canAdvance && hasNextPhase" type="primary" :disabled="!requiredSubmitted" @click="confirmAdvance">推进下一阶段</el-button>
      <span v-if="detail.canAdvance && hasNextPhase && !requiredSubmitted" class="advance-tip">{{ missingRequiredTip }}</span>
      <el-button v-if="detail.canAdvance && hasPrevPhase" @click="confirmRollback">回退阶段</el-button>
      <el-button v-if="detail.canManage" @click="openEdit">编辑项目</el-button>
      <el-button v-if="detail.canAdvance" type="warning" @click="openCancel">取消项目</el-button>
      <el-tag v-if="detail.status === 'CANCELED'" type="danger" style="margin-left: 8px">已取消：{{ detail.cancelReason }}</el-tag>
    </div>

    <!-- 基本信息卡 -->
    <el-card shadow="never" class="card-block">
      <template #header>基本信息</template>
      <el-descriptions :column="3" border>
        <el-descriptions-item label="项目名称">{{ detail.name }}</el-descriptions-item>
        <el-descriptions-item label="大类">{{ detail.category === 'ONLINE' ? '线上' : '线下' }}</el-descriptions-item>
        <el-descriptions-item label="类型">{{ detail.typeName }}</el-descriptions-item>
        <el-descriptions-item label="计划时间">{{ detail.startDate || '-' }} ~ {{ detail.endDate || '-' }}</el-descriptions-item>
        <el-descriptions-item label="负责人">{{ detail.ownerName }}</el-descriptions-item>
        <el-descriptions-item label="执行人">{{ detail.executorNames }}</el-descriptions-item>
        <el-descriptions-item label="当前阶段">{{ detail.phaseName }}</el-descriptions-item>
        <el-descriptions-item label="创建人">{{ detail.creatorName }}</el-descriptions-item>
        <el-descriptions-item label="创建时间">{{ detail.createTime }}</el-descriptions-item>
      </el-descriptions>
    </el-card>

    <!-- 预算 vs 实际 -->
    <el-card shadow="never" class="card-block">
      <template #header>
        预算 vs 实际
        <el-button v-if="detail.canAdvance" link type="primary" style="float: right" @click="openBudget">登记预算金额</el-button>
      </template>
      <el-row :gutter="16">
        <el-col :span="8">
          <div class="stat-box">
            <div class="stat-label">预算金额</div>
            <div class="stat-value">¥ {{ fmt(budgetAmount) }}</div>
          </div>
        </el-col>
        <el-col :span="8">
          <div class="stat-box">
            <div class="stat-label">专家劳务费合计</div>
            <div class="stat-value">¥ {{ fmt(expertFeeSum) }}</div>
          </div>
        </el-col>
        <el-col :span="8">
          <div class="stat-box">
            <div class="stat-label">供应商费用合计（实际）</div>
            <div class="stat-value">¥ {{ fmt(supplierFeeSum) }}</div>
          </div>
        </el-col>
      </el-row>
    </el-card>

    <!-- 阶段页签（分阶段资料 + 模板） -->
    <el-card shadow="never" class="card-block">
      <template #header>项目进度（阶段页签）</template>
      <el-tabs v-model="activePhaseId">
        <el-tab-pane
          v-for="p in detail.phases"
          :key="p.id"
          :name="String(p.id)"
          :label="(p.id === detail.phaseId ? '● ' : '') + p.name"
        >
          <template v-if="p.templates && p.templates.length">
            <div v-for="t in p.templates" :key="t.templateId" class="template-block">
              <div class="template-title">
                {{ t.templateName }}
                <el-tag v-if="t.required === 1" size="small" type="danger">必填</el-tag>
                <el-tag v-else size="small" type="info">选填</el-tag>
                <span v-if="t.required === 1 && isTemplateFulfilled(t) === false" class="unfilled-tip">未配置内容，阶段推进将受限</span>
              </div>

              <!-- 资料附件模板 -->
              <div v-if="t.templateType === 'FILE'">
                <div v-for="d in docsOfPhase(p.id, 'FILE')" :key="d.id" class="doc-row">
                  <a :href="d.filePath" target="_blank" class="doc-name">{{ d.fileName }}</a>
                  <span class="doc-meta">上传人：{{ d.uploadBy }} · {{ d.createTime }}</span>
                  <el-button v-if="detail.canAdvance" link type="danger" @click="removeDoc(d)">删除</el-button>
                </div>
                <div v-if="detail.canAdvance" class="upload-area">
                  <el-upload
                    :show-file-list="false"
                    :auto-upload="false"
                    :on-change="(f) => uploadDoc(p.id, f)"
                  >
                    <el-button size="small" type="primary" plain>上传资料到本阶段</el-button>
                  </el-upload>
                </div>
              </div>

              <!-- 自定义文本模板 -->
              <div v-else-if="t.templateType === 'RICH_TEXT'">
                <div v-if="!editingRich[p.id]" class="rich-view">{{ richTextOf(p.id) || '（尚未填写内容）' }}</div>
                <el-input v-if="detail.canAdvance && editingRich[p.id]" v-model="richDrafts[p.id]" type="textarea" :rows="4" />
                <el-button v-if="detail.canAdvance" size="small" type="primary" plain style="margin-top: 6px" @click="editRich(p.id)">{{ editingRich[p.id] ? '保存文本' : '编辑文本' }}</el-button>
              </div>

              <!-- 预算模板 -->
              <div v-else-if="t.templateType === 'BUDGET'">
                <div class="hint-text">预算金额：¥ {{ fmt(budgetAmount) }}（点击上方“登记预算金额”维护）</div>
              </div>

              <!-- 专家/供应商模板 -->
              <div v-else>
                <div class="hint-text">{{ t.templateType === 'EXPERT' ? '项目专家名单（全局一份）见下方“专家名单”卡' : '项目供应商名单（全局一份）见下方“供应商名单”卡' }}</div>
              </div>
            </div>
          </template>
          <el-empty v-else :description="'本阶段未绑定模板'" :image-size="60" />
        </el-tab-pane>
      </el-tabs>
    </el-card>

    <!-- 专家名单 -->
    <el-card shadow="never" class="card-block">
      <template #header>
        专家名单（全局一份）
        <el-button v-if="detail.canManage" link type="primary" style="float: right" @click="openAssignExpert">调派专家</el-button>
        <el-button v-if="detail.canManage" link type="success" style="float: right; margin-right: 12px" @click="openNewExpert">新建专家卡</el-button>
      </template>
      <el-table :data="detail.experts" stripe>
        <el-table-column prop="name" label="姓名" width="100" />
        <el-table-column prop="unit" label="单位" min-width="140" show-overflow-tooltip />
        <el-table-column prop="title" label="职称" width="100" />
        <el-table-column prop="role" label="角色/分工" width="110" />
        <el-table-column label="劳务费" width="110">
          <template #default="{ row }">
            <span v-if="!editingFeeExpert || editingFeeExpert.id !== row.id">{{ fmt(row.fee) }}</span>
            <el-input-number v-else v-model="feeDraft" :min="0" :precision="2" size="small" />
          </template>
        </el-table-column>
        <el-table-column label="身份证/银行卡" min-width="180">
          <template #default="{ row }">
            <span class="sensitive">{{ row.idCard || '-' }} / {{ row.bankCard || '-' }}</span>
            <span v-if="!detail.canAdvance" class="mask-tip">（负责人/执行人可见明文）</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="160" fixed="right">
          <template #default="{ row }">
            <template v-if="detail.canManage">
              <el-button
                v-if="editingFeeExpert && editingFeeExpert.id === row.id"
                link
                type="primary"
                @click="doSaveExpertFee(row)"
              >保存</el-button>
              <el-button v-else link type="primary" @click="startEditFee(row)">登记劳务费</el-button>
            </template>
            <el-popconfirm title="确认移出该专家？（留痕）" @confirm="removeExpert(row)">
              <template #reference>
                <el-button v-if="detail.canManage" link type="danger">移出</el-button>
              </template>
            </el-popconfirm>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 供应商名单 -->
    <el-card shadow="never" class="card-block">
      <template #header>
        供应商名单（全局一份）
        <el-button v-if="detail.canManage" link type="primary" style="float: right" @click="openAssignSupplier">关联供应商</el-button>
      </template>
      <el-table :data="detail.suppliers" stripe>
        <el-table-column prop="name" label="名称" min-width="140" show-overflow-tooltip />
        <el-table-column prop="typeName" label="类型" width="100" />
        <el-table-column prop="purpose" label="用途（自动带出）" min-width="130" />
        <el-table-column label="费用" width="120">
          <template #default="{ row }">
            <span v-if="!editingFeeSupplier || editingFeeSupplier.id !== row.id">{{ fmt(row.fee) }}</span>
            <el-input-number v-else v-model="supplierFeeDraft" :min="0" :precision="2" size="small" />
          </template>
        </el-table-column>
        <el-table-column label="操作" width="130" fixed="right">
          <template #default="{ row }">
            <template v-if="detail.canManage">
              <el-button
                v-if="editingFeeSupplier && editingFeeSupplier.id === row.id"
                link
                type="primary"
                @click="doSaveSupplierFee(row)"
              >保存</el-button>
              <el-button v-else link type="primary" @click="startEditSupplierFee(row)">填费用</el-button>
              <el-popconfirm title="确认移出该供应商？（留痕）" @confirm="removeSupplier(row)">
                <template #reference>
                  <el-button link type="danger">移出</el-button>
                </template>
              </el-popconfirm>
            </template>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 结算合同区 -->
    <el-card shadow="never" class="card-block">
      <template #header>
        结算合同
        <el-button v-if="detail.canAdvance" link type="primary" style="float: right" @click="openContract">登记合同</el-button>
      </template>
      <el-table :data="detail.contracts" stripe>
        <el-table-column prop="expertName" label="专家" width="90" />
        <el-table-column label="身份证/银行卡" min-width="200">
          <template #default="{ row }">
            <span class="sensitive">{{ row.idCard || '-' }} / {{ row.bankCard || '-' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="劳务费" width="100">
          <template #default="{ row }">{{ fmt(row.fee) }}</template>
        </el-table-column>
        <el-table-column label="合同文件" min-width="120">
          <template #default="{ row }">
            <a v-if="row.filePath" :href="row.filePath" target="_blank">下载</a>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag size="small" :type="statusType(row.status)">{{ statusText(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="210" fixed="right">
          <template #default="{ row }">
            <template v-if="detail.canAdvance">
              <el-upload
                v-if="!row.filePath"
                :show-file-list="false"
                :auto-upload="false"
                :on-change="(f) => uploadContract(row, f)"
                style="display: inline-block; margin-right: 8px"
              >
                <el-button link type="primary">上传文件</el-button>
              </el-upload>
              <el-button v-if="row.filePath && row.status === 'DRAFT'" link type="success" @click="doSubmitApproval(row)">发起审批</el-button>
              <el-button v-if="row.status === 'DRAFT'" link type="warning" @click="openSensitive(row)">改敏感字段</el-button>
            </template>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 时间线 -->
    <el-card shadow="never" class="card-block">
      <template #header>项目时间线（operation_log 同源，倒序）</template>
      <el-timeline v-if="detail.timeline && detail.timeline.length">
        <el-timeline-item
          v-for="(log, i) in detail.timeline"
          :key="i"
          :timestamp="log.createTime"
          placement="top"
        >
          <div class="tl-row">
            <span class="tl-user">{{ log.username }}</span>
            <span class="tl-desc">{{ log.actionDesc }}</span>
          </div>
        </el-timeline-item>
      </el-timeline>
      <el-empty v-else description="暂无操作记录" :image-size="60" />
    </el-card>

    <!-- ============ 弹窗 ============ -->
    <project-edit
      v-model="editVisible"
      :project="editRow"
      :owners="ownerOptions"
      :types="typeOptions"
      :phases="detail.phases"
      :users="userOptions"
      @saved="fetchDetail"
    />

    <!-- 调派专家 -->
    <el-dialog v-model="assignExpertVisible" title="调派专家（全局名单）" width="560px" destroy-on-close>
      <el-input v-model="expertKeyword" placeholder="搜索专家库（姓名/单位）" clearable style="margin-bottom: 10px" @keyup.enter="searchExperts" />
      <el-select v-model="assignExpertId" placeholder="选择专家" filterable style="width: 100%">
        <el-option v-for="e in expertCandidates" :key="e.id" :label="`${e.name}（${e.unit || '无单位'}）`" :value="e.id" />
      </el-select>
      <el-input v-model="assignExpertRole" placeholder="角色/分工（主持人、演讲嘉宾、评审专家等）" style="margin-top: 10px" />
      <div class="hint-text">未找到目标专家？可在下方新建专家卡。</div>
      <template #footer>
        <el-button @click="assignExpertVisible = false">取消</el-button>
        <el-button type="primary" :disabled="!assignExpertId" @click="doAssignExpert">调派</el-button>
      </template>
    </el-dialog>

    <!-- 新建专家卡 -->
    <el-dialog v-model="newExpertVisible" title="新建专家卡（保存后自动关联本项目）" width="520px" destroy-on-close>
      <el-form label-width="60px">
        <el-form-item label="姓名" required><el-input v-model="newExpert.name" placeholder="必填" /></el-form-item>
        <el-form-item label="单位" required><el-input v-model="newExpert.unit" placeholder="必填" /></el-form-item>
        <el-form-item label="职称" required><el-input v-model="newExpert.title" placeholder="必填" /></el-form-item>
        <el-form-item label="简介" required><el-input v-model="newExpert.profile" type="textarea" :rows="3" placeholder="必填" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="newExpertVisible = false">取消</el-button>
        <el-button type="primary" :disabled="!newExpert.name || !newExpert.unit || !newExpert.title || !newExpert.profile" @click="doNewExpert">保存并关联</el-button>
      </template>
    </el-dialog>

    <!-- 关联供应商 -->
    <el-dialog v-model="assignSupplierVisible" title="关联供应商（用途自动带出）" width="560px" destroy-on-close>
      <el-input v-model="supplierKeyword" placeholder="搜索供应商库（名称）" clearable style="margin-bottom: 10px" @keyup.enter="searchSuppliers" />
      <el-select v-model="assignSupplierId" placeholder="选择供应商" filterable style="width: 100%">
        <el-option v-for="s in supplierCandidates" :key="s.id" :label="`${s.name}（${s.typeName || '-'}）`" :value="s.id" />
      </el-select>
      <div class="hint-text">用途按供应商类型自动带出，无需手动填写。</div>
      <template #footer>
        <el-button @click="assignSupplierVisible = false">取消</el-button>
        <el-button type="primary" :disabled="!assignSupplierId" @click="doAssignSupplier">关联</el-button>
      </template>
    </el-dialog>

    <!-- 登记合同 -->
    <el-dialog v-model="contractVisible" title="登记合同" width="560px" destroy-on-close>
      <el-form label-width="100px">
        <el-form-item label="选择专家" required>
          <el-select v-model="contractForm.expertId" placeholder="从项目专家名单选择" style="width: 100%" @change="onPickContractExpert">
            <el-option v-for="e in detail.experts" :key="e.id" :label="e.name" :value="e.expertId" />
          </el-select>
        </el-form-item>
        <el-form-item label="身份证号">
          <el-input v-model="contractForm.idCard" placeholder="自动带出专家库档案，可修改（回写留痕）" />
        </el-form-item>
        <el-form-item label="银行卡号">
          <el-input v-model="contractForm.bankCard" placeholder="自动带出专家库档案，可修改（回写留痕）" />
        </el-form-item>
        <el-form-item label="劳务费">
          <el-input-number v-model="contractForm.fee" :min="0" :precision="2" style="width: 100%" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="contractVisible = false">取消</el-button>
        <el-button type="primary" :disabled="!contractForm.expertId" @click="doCreateContract">保存合同（A：信息）</el-button>
      </template>
    </el-dialog>

    <!-- 敏感字段修改 -->
    <el-dialog v-model="sensitiveVisible" title="修改合同敏感字段（同步回写专家库）" width="460px" destroy-on-close>
      <el-form label-width="90px">
        <el-form-item label="身份证号">
          <el-input v-model="sensitiveForm.idCard" placeholder="修改后将同步写回专家档案并留痕" />
        </el-form-item>
        <el-form-item label="银行卡号">
          <el-input v-model="sensitiveForm.bankCard" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="sensitiveVisible = false">取消</el-button>
        <el-button type="primary" @click="doUpdateSensitive">保存并回写</el-button>
      </template>
    </el-dialog>

    <!-- 取消项目 -->
    <el-dialog v-model="cancelVisible" title="取消项目" width="460px" destroy-on-close>
      <el-input v-model="cancelReason" type="textarea" :rows="3" maxlength="500" placeholder="取消原因（必填，写入时间线）" />
      <template #footer>
        <el-button @click="cancelVisible = false">再想想</el-button>
        <el-button type="danger" :disabled="!cancelReason.trim()" @click="doCancel">确认取消</el-button>
      </template>
    </el-dialog>

    <!-- 预算登记 -->
    <el-dialog v-model="budgetVisible" title="登记预算金额" width="420px" destroy-on-close>
      <el-input-number v-model="budgetDraft" :min="0" :precision="2" style="width: 100%" />
      <template #footer>
        <el-button @click="budgetVisible = false">取消</el-button>
        <el-button type="primary" @click="doBudget">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import ProjectEdit from './ProjectEdit.vue'
import request from '../../api/request'
import {
  getProjectDetail, changePhase, cancelProject,
  addProjectExpert, removeProjectExpert, createExpertCard,
  addProjectSupplier, removeProjectSupplier,
  uploadProjectDoc, deleteProjectDoc, saveRichText,
  registerBudget,
  createProjectContract, uploadContractFile, submitContractApproval, updateContractSensitive,
  saveExpertFee, saveSupplierFee,
  listProjectTypes, listProjectPhases, listProjectOwners, listProjectExecutors
} from '../../api/project'

const route = useRoute()
const projectId = route.params.id
const loading = ref(false)
const detail = ref({})
const activePhaseId = ref('')

const editVisible = ref(false)
const editRow = ref(null)
const ownerOptions = ref([])
const typeOptions = ref([])
const userOptions = ref([])

const cancelVisible = ref(false)
const cancelReason = ref('')
const budgetVisible = ref(false)
const budgetDraft = ref(0)

const editingRich = ref({})
const richDrafts = ref({})
const richOfPhase = ref({})

const editingFeeExpert = ref(null)
const feeDraft = ref(0)
const editingFeeSupplier = ref(null)
const supplierFeeDraft = ref(0)

const assignExpertVisible = ref(false)
const expertKeyword = ref('')
const expertCandidates = ref([])
const assignExpertId = ref(null)
const assignExpertRole = ref('')

const newExpertVisible = ref(false)
const newExpert = reactive({ name: '', unit: '', title: '', profile: '' })

const assignSupplierVisible = ref(false)
const supplierKeyword = ref('')
const supplierCandidates = ref([])
const assignSupplierId = ref(null)

const contractVisible = ref(false)
const contractForm = reactive({ expertId: null, idCard: '', bankCard: '', fee: 0 })
const sensitiveVisible = ref(false)
const sensitiveForm = reactive({ contractId: null, idCard: '', bankCard: '' })

const budgetAmount = computed(() => detail.value.budget?.amount ?? 0)
const expertFeeSum = computed(() => (detail.value.experts || []).reduce((s, e) => s + (Number(e.fee) || 0), 0))
const supplierFeeSum = computed(() => (detail.value.suppliers || []).reduce((s, e) => s + (Number(e.fee) || 0), 0))

async function fetchDetail() {
  loading.value = true
  try {
    detail.value = await getProjectDetail(projectId)
    if (!activePhaseId.value && detail.value.phases?.length) {
      activePhaseId.value = String(detail.value.phaseId || detail.value.phases[0].id)
    }
  } finally {
    loading.value = false
  }
}

function docsOfPhase(phaseId, type) {
  return (detail.value.docs || []).filter(
    (d) => Number(d.phaseId) === Number(phaseId) && (type === 'FILE' ? !d.isRichText : d.isRichText)
  )
}

function richTextOf(phaseId) {
  return richOfPhase.value[phaseId] ?? ''
}

function isTemplateFulfilled(t) {
  if (t.templateType === 'FILE') return docsOfPhase(t.phaseId, 'FILE').length > 0
  if (t.templateType === 'RICH_TEXT') return !!richOfPhase.value[t.phaseId]
  if (t.templateType === 'BUDGET') return Number(budgetAmount.value) > 0
  if (t.templateType === 'EXPERT') return (detail.value.experts || []).length > 0
  if (t.templateType === 'SUPPLIER') return (detail.value.suppliers || []).length > 0
  return true
}

function fmt(v) {
  if (v === null || v === undefined || v === '') return '0'
  return Number(v).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}

function statusText(s) {
  return { DRAFT: '草稿', PENDING: '审批中', APPROVED: '已通过', REJECTED: '已驳回' }[s] || s
}
function statusType(s) {
  return { DRAFT: 'info', PENDING: 'warning', APPROVED: 'success', REJECTED: 'danger' }[s] || 'info'
}

// ---- 阶段操作：推进下一阶段 / 回退阶段（严格逐级，二次确认） ----
const currentPhase = computed(() => (detail.value.phases || []).find((p) => Number(p.id) === Number(detail.value.phaseId)) || null)
const nextPhase = computed(() => {
  const c = currentPhase.value
  if (!c) return null
  return (detail.value.phases || []).find((p) => Number(p.sort ?? 0) === Number(c.sort ?? 0) + 1) || null
})
const prevPhase = computed(() => {
  const c = currentPhase.value
  if (!c) return null
  return (detail.value.phases || []).find((p) => Number(p.sort ?? 0) === Number(c.sort ?? 0) - 1) || null
})
const hasNextPhase = computed(() => !!nextPhase.value)
const hasPrevPhase = computed(() => !!prevPhase.value)
const missingRequiredTemplates = computed(() => detail.value.missingRequiredTemplates || [])
const requiredSubmitted = computed(() => missingRequiredTemplates.value.length === 0)
const missingRequiredTip = computed(() => `当前阶段必填材料未提交：${missingRequiredTemplates.value.join('、')}`)

async function confirmAdvance() {
  const next = nextPhase.value
  if (!next) return
  try {
    await ElMessageBox.confirm(`确认将项目推进到「${next.name}」阶段？`, '推进下一阶段', {
      type: 'warning',
      confirmButtonText: '确认推进',
      cancelButtonText: '再想想'
    })
  } catch (e) {
    return
  }
  await changePhase(projectId, next.id)
  ElMessage.success(`已推进到「${next.name}」阶段`)
  fetchDetail()
}

async function confirmRollback() {
  const prev = prevPhase.value
  if (!prev) return
  try {
    await ElMessageBox.confirm(`确认将项目回退到「${prev.name}」阶段？已提交的资料会保留，可再次推进。`, '回退阶段', {
      type: 'warning',
      confirmButtonText: '确认回退',
      cancelButtonText: '再想想'
    })
  } catch (e) {
    return
  }
  await changePhase(projectId, prev.id)
  ElMessage.success(`已回退到「${prev.name}」阶段`)
  fetchDetail()
}

function openCancel() {
  cancelReason.value = ''
  cancelVisible.value = true
}
async function doCancel() {
  await cancelProject(projectId, cancelReason.value.trim())
  ElMessage.success('项目已取消')
  cancelVisible.value = false
  fetchDetail()
}

// ---- 编辑 ----
function openEdit() {
  editRow.value = detail.value
  editVisible.value = true
}

// ---- 资料 ----
async function uploadDoc(phaseId, file) {
  if (!file || !file.raw) return
  await uploadProjectDoc(projectId, phaseId, file.raw)
  ElMessage.success('资料已上传')
  fetchDetail()
}
async function removeDoc(doc) {
  await deleteProjectDoc(projectId, doc.id)
  ElMessage.success('资料已删除（留痕）')
  fetchDetail()
}
function editRich(phaseId) {
  if (editingRich.value[phaseId]) {
    saveRichText(projectId, { phaseId, content: richDrafts.value[phaseId] || '' }).then(() => {
      ElMessage.success('文本已保存')
      editingRich.value[phaseId] = false
      fetchDetail()
    })
  } else {
    richDrafts.value[phaseId] = richOfPhase.value[phaseId] ?? ''
    editingRich.value[phaseId] = true
  }
}

// ---- 预算 ----
function openBudget() {
  budgetDraft.value = Number(budgetAmount.value) || 0
  budgetVisible.value = true
}
async function doBudget() {
  await registerBudget(projectId, budgetDraft.value)
  ElMessage.success('预算已登记')
  budgetVisible.value = false
  fetchDetail()
}

// ---- 专家 ----
function openAssignExpert() {
  assignExpertRole.value = ''
  assignExpertId.value = null
  searchExperts()
  assignExpertVisible.value = true
}
async function searchExperts() {
  const data = await request.get('/experts', { params: { keyword: expertKeyword.value || undefined, page: 1, size: 50 } })
  expertCandidates.value = data?.items || []
}
async function doAssignExpert() {
  await addProjectExpert(projectId, { expertId: assignExpertId.value, role: assignExpertRole.value })
  ElMessage.success('专家已调派（留痕）')
  assignExpertVisible.value = false
  fetchDetail()
}
function openNewExpert() {
  Object.assign(newExpert, { name: '', unit: '', title: '', profile: '' })
  newExpertVisible.value = true
}
async function doNewExpert() {
  await createExpertCard(projectId, { ...newExpert })
  ElMessage.success('专家卡已创建并自动关联（留痕）')
  newExpertVisible.value = false
  fetchDetail()
}
async function removeExpert(row) {
  await removeProjectExpert(projectId, row.expertId)
  ElMessage.success('专家已移出（留痕）')
  fetchDetail()
}
function startEditFee(row) {
  editingFeeExpert.value = row
  feeDraft.value = Number(row.fee) || 0
}
async function doSaveExpertFee(row) {
  await saveExpertFee(projectId, row.expertId, feeDraft.value)
  ElMessage.success('劳务费已登记')
  editingFeeExpert.value = null
  fetchDetail()
}

// ---- 供应商 ----
function openAssignSupplier() {
  assignSupplierId.value = null
  searchSuppliers()
  assignSupplierVisible.value = true
}
async function searchSuppliers() {
  const data = await request.get('/supplier/list', { params: { keyword: supplierKeyword.value || undefined, page: 1, size: 50 } })
  supplierCandidates.value = data?.items || []
}
async function doAssignSupplier() {
  await addProjectSupplier(projectId, { supplierId: assignSupplierId.value })
  ElMessage.success('供应商已关联（留痕）')
  assignSupplierVisible.value = false
  fetchDetail()
}
async function removeSupplier(row) {
  await removeProjectSupplier(projectId, row.supplierId)
  ElMessage.success('供应商已移出（留痕）')
  fetchDetail()
}
function startEditSupplierFee(row) {
  editingFeeSupplier.value = row
  supplierFeeDraft.value = Number(row.fee) || 0
}
async function doSaveSupplierFee(row) {
  await saveSupplierFee(projectId, row.supplierId, supplierFeeDraft.value)
  ElMessage.success('供应商费用已填写')
  editingFeeSupplier.value = null
  fetchDetail()
}

// ---- 合同 ----
function openContract() {
  Object.assign(contractForm, { expertId: null, idCard: '', bankCard: '', fee: 0 })
  contractVisible.value = true
}
function onPickContractExpert(expertId) {
  const e = (detail.value.experts || []).find((x) => x.expertId === expertId)
  if (e) {
    contractForm.idCard = e.idCard || ''
    contractForm.bankCard = e.bankCard || ''
    contractForm.fee = Number(e.fee) || 0
  }
}
async function doCreateContract() {
  await createProjectContract(projectId, { ...contractForm })
  ElMessage.success('合同已登记（A：信息）')
  contractVisible.value = false
  fetchDetail()
}
async function uploadContract(row, file) {
  if (!file || !file.raw) return
  await uploadContractFile(projectId, row.id, file.raw)
  ElMessage.success('合同文件已上传（B：文件）')
  fetchDetail()
}
async function doSubmitApproval(row) {
  try {
    await submitContractApproval(projectId, row.id)
    ElMessage.success('审批已发起，等待审批')
    fetchDetail()
  } catch (e) {
    // 校验失败提示
  }
}
function openSensitive(row) {
  sensitiveForm.contractId = row.id
  sensitiveForm.idCard = row.idCard || ''
  sensitiveForm.bankCard = row.bankCard || ''
  sensitiveVisible.value = true
}
async function doUpdateSensitive() {
  await updateContractSensitive(projectId, sensitiveForm.contractId, {
    idCard: sensitiveForm.idCard,
    bankCard: sensitiveForm.bankCard
  })
  ElMessage.success('敏感字段已更新并回写专家库（留痕）')
  sensitiveVisible.value = false
  fetchDetail()
}

onMounted(async () => {
  fetchDetail()
  typeOptions.value = await listProjectTypes()
  ownerOptions.value = await listProjectOwners()
  const userData = await listProjectExecutors()
  userOptions.value = userData || []
})
</script>

<style scoped>
.action-bar { margin: 12px 0; display: flex; align-items: center; gap: 8px; flex-wrap: wrap; }
.advance-tip { color: #f56c6c; font-size: 12px; }
.card-block { margin-bottom: 12px; }
.stat-box { text-align: center; padding: 8px 0; }
.stat-label { color: #909399; font-size: 13px; }
.stat-value { font-size: 20px; font-weight: 600; color: #303133; margin-top: 4px; }
.template-block { border: 1px solid #ebeef5; border-radius: 6px; padding: 10px; margin-bottom: 10px; }
.template-title { font-weight: 600; margin-bottom: 8px; }
.unfilled-tip { color: #f56c6c; font-size: 12px; margin-left: 8px; }
.doc-row { display: flex; align-items: center; gap: 10px; padding: 4px 0; }
.doc-name { color: #409eff; }
.doc-meta { color: #909399; font-size: 12px; }
.upload-area { margin-top: 6px; }
.rich-view { white-space: pre-wrap; color: #606266; }
.hint-text { color: #909399; font-size: 13px; }
.mask-tip { color: #909399; font-size: 12px; margin-left: 6px; }
.sensitive { font-family: monospace; font-size: 12px; }
.tl-row { display: flex; gap: 8px; }
.tl-user { font-weight: 600; }
</style>
