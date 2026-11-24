<template>
  <div>
    <div class="page-head">
      <span class="page-sub">查看我提交的全部申请及审批进度（专家修改 / 新增等）</span>
    </div>

    <div class="filter-bar">
      <el-select v-model="status" placeholder="全部状态" clearable style="width: 140px" @change="handleStatusChange">
        <el-option label="待审批" :value="0" />
        <el-option label="已通过" :value="1" />
        <el-option label="已驳回" :value="2" />
        <el-option label="已撤回" :value="3" />
        <el-option label="退回修改" :value="4" />
      </el-select>
    </div>

    <el-table :data="requests" border stripe v-loading="loading" style="margin-top: 16px">
      <el-table-column prop="expertName" label="专家姓名" width="140" />
      <el-table-column label="申请类型" width="130" align="center">
        <template #default="{ row }">
          <el-tag :type="row.requestType === 1 ? 'success' : 'info'">{{ row.requestTypeText || '修改申请' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="createTime" label="申请时间" width="170" />
      <el-table-column label="变更摘要" min-width="220" show-overflow-tooltip>
        <template #default="{ row }">{{ changeSummary(row.changes, row.oldData) }}</template>
      </el-table-column>
      <el-table-column label="状态" width="100" align="center">
        <template #default="{ row }">
          <el-tag :type="approvalStatusTagType(row.status)">{{ approvalStatusText(row.status) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="审批意见" min-width="150" show-overflow-tooltip>
        <template #default="{ row }">
          <span v-if="row.status === 4 && row.returnComment">退回意见：{{ row.returnComment }}</span>
          <span v-else-if="row.approveComment">{{ row.approveComment }}</span>
          <span v-else class="muted">-</span>
        </template>
      </el-table-column>
      <el-table-column label="审批人" width="110" align="center">
        <template #default="{ row }">
          <span v-if="row.approveBy">{{ row.approveBy }}</span>
          <span v-else class="muted">-</span>
        </template>
      </el-table-column>
      <el-table-column label="审批时间" width="170">
        <template #default="{ row }">
          <span v-if="row.approveTime">{{ row.approveTime }}</span>
          <span v-else class="muted">-</span>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="300" align="center" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openProgress(row)">查看进度</el-button>
          <el-button link type="primary" @click="openDiff(row)">查看对比</el-button>
          <el-button v-if="row.status === 0" link type="danger" @click="handleWithdraw(row)">撤回</el-button>
          <el-button v-if="row.status === 4" link type="warning" @click="handleResubmit(row)">重新编辑提交</el-button>
        </template>
      </el-table-column>
    </el-table>

    <div class="pagination-wrap">
      <el-pagination
        v-model:current-page="page"
        v-model:page-size="size"
        :total="total"
        :page-sizes="[10, 20, 50, 100]"
        layout="total, sizes, prev, pager, next, jumper"
        @size-change="fetchList"
        @current-change="fetchList"
      />
    </div>

    <el-dialog v-model="resubmitVisible" title="重新编辑提交" width="600px" :close-on-click-modal="false">
      <el-alert v-if="resubmitRow?.returnComment" :title="resubmitRow.returnComment" type="warning" :closable="false" />
      <p class="page-sub">修改申请内容后重新提交；照片可重新上传（不上传则保留原申请照片）。身份和银行卡字段留空表示保留原提交值。</p>
      <el-form label-width="120px"><el-form-item v-for="field in resubmitFields" :key="field" :label="field">
        <el-input v-model="resubmitChanges[field]" :type="['身份证号','银行卡号'].includes(field)?'password':'textarea'" :show-password="['身份证号','银行卡号'].includes(field)" :maxlength="5000" />
      </el-form-item><el-form-item v-for="key in resubmitPhotoKeys" :key="key" :label="key"><span class="resubmit-photo-state">{{ resubmitPhotoState(key) }}</span><el-upload :show-file-list="false" accept="image/*" :before-upload="beforeResubmitPhoto" :http-request="(opt) => uploadResubmitPhoto(key, opt)"><el-button link type="primary" :loading="!!resubmitPhotoUploading[key]">重新上传</el-button></el-upload></el-form-item><el-form-item label="申请原因"><el-input v-model="resubmitReason" type="textarea" :maxlength="1000" /></el-form-item></el-form>
      <template #footer><el-button @click="resubmitVisible=false">取消</el-button><el-button type="primary" :loading="resubmitLoading" @click="confirmResubmit">确认提交</el-button></template>
    </el-dialog>
    <!-- 审批进度：时间线 -->
    <el-dialog v-model="progressVisible" title="审批进度" width="560px" destroy-on-close>
      <div class="progress-expert">专家：{{ progressRow?.expertName }}</div>
      <el-timeline v-if="progressRow?.records?.length">
        <el-timeline-item
          v-for="record in progressRow.records"
          :key="record.id"
          :timestamp="record.createTime"
          :type="timelineType(record.action)"
          placement="top"
        >
          <div class="timeline-node">{{ record.nodeName }}</div>
          <div class="timeline-meta">操作人：{{ record.operator || '-' }}　动作：{{ actionText(record.action) }}</div>
          <div v-if="record.comment" class="timeline-comment">意见：{{ record.comment }}</div>
        </el-timeline-item>
      </el-timeline>
      <el-empty v-else description="暂无进度记录" :image-size="80" />
    </el-dialog>

    <!-- 变更前后对比弹窗（只读，无审批操作区） -->
    <ApprovalDiffDialog v-model="diffVisible" :row="diffRow" mode="view" />
  </div>
</template>

<script setup>
import { watchDashboardFocus } from '../utils/dashboardFocus'
import { onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getMyApplications, withdrawRequest, resubmitRequest, uploadApprovalPhoto } from '../api/approval'
import { uploadLimits, ensureUploadLimits, toMb } from '../utils/limits'
import { useNotificationStore } from '../store/notification'
import { changeSummary, approvalStatusTagType, approvalStatusText } from '../utils/format'
import ApprovalDiffDialog from '../components/ApprovalDiffDialog.vue'

const route = useRoute()
const router = useRouter()
const notificationStore = useNotificationStore()
// 退回修改后可重传的照片字段（与后端 PHOTO_FIELDS 一致；合同申请不含照片）
const PHOTO_KEYS = ['照片', '身份证正面', '身份证反面']

const requests = ref([])
// 状态筛选：读取 URL query.status（0=待审批 1=已通过 2=已驳回），支持从通知中心跳转携带状态
const status = ref(
  route.query.status !== undefined && route.query.status !== '' ? Number(route.query.status) : undefined
)
const total = ref(0)
const page = ref(1)
const size = ref(20)
const loading = ref(false)

const progressVisible = ref(false)
const progressRow = ref(null)

const diffVisible = ref(false)
const diffRow = ref(null)

// 列表刷新驱动（2026-11-24 修正）：跟随全局消息通知 store 的 30s 轮询（store.lastPollAt），
// 不再在本页自建定时器——此前页面定时器与全局通知轮询并行，且新通知到达时页面不会立即刷新。
watch(
  () => notificationStore.lastPollAt,
  () => {
    if (!loading.value) {
      fetchList()
    }
  }
)

async function fetchList() {
  loading.value = true
  try {
    const data = await getMyApplications({
      page: page.value,
      size: size.value,
      status: status.value !== undefined && status.value !== '' ? status.value : undefined
    })
    requests.value = data?.items || []
    total.value = data?.total || 0
  } finally {
    loading.value = false
  }
}

function timelineType(action) {
  if (action === 'APPROVE') return 'success'
  if (action === 'REJECT') return 'danger'
  if (action === 'WITHDRAW') return 'danger'
  if (action === 'TRANSFER') return 'warning'
  if (action === 'ADD_APPROVER') return 'primary'
  return 'primary'
}

function actionText(action) {
  if (action === 'APPROVE') return '审批通过'
  if (action === 'REJECT') return '驳回'
  if (action === 'WITHDRAW') return '撤回申请'
  if (action === 'TRANSFER') return '审批转办'
  if (action === 'ADD_APPROVER') return '审批加签'
  return '提交申请'
}

function openProgress(row) {
  progressRow.value = row
  progressVisible.value = true
}

function openDiff(row) {
  diffRow.value = row
  diffVisible.value = true
}

// 撤回申请：仅待审批(status=0)行展示入口，二次确认后调用后端，成功刷新列表
async function handleWithdraw(row) {
  try {
    await ElMessageBox.confirm(
      `确认撤回「${row.expertName}」的申请吗？撤回后该申请将不可再审批，临时照片会被清理。`,
      '撤回确认',
      {
        confirmButtonText: '确认撤回',
        cancelButtonText: '取消',
        type: 'warning'
      }
    )
  } catch (e) {
    return
  }
  try {
    await withdrawRequest(row.id)
    ElMessage.success('已撤回')
    fetchList()
  } catch (e) {
    // 错误提示已由拦截器统一处理
  }
}

const resubmitVisible=ref(false), resubmitLoading=ref(false), resubmitRow=ref(null), resubmitChanges=ref({}), resubmitFields=ref([]), resubmitReason=ref('')
const editableFields=['姓名','单位','科室','职称','联系方式','简介','身份证号','银行卡号','银行卡开户行']
// 重新上传的照片：key -> 'TMP:文件名'（后端 resubmit 自 2026-11-24 起接受照片字段，仅接受 TMP: 临时文件名）
const resubmitPhotoKeys=ref([]), resubmitPhotos=ref({}), resubmitPhotoUploading=ref({})
function handleResubmit(row) {
  resubmitRow.value=row
  resubmitFields.value=row.applicationType==='CONTRACT'?['身份证号','银行卡号']:[...new Set([...editableFields,...['研究方向','擅长领域'].filter(key=>key in (row.changes || {}))])]
  resubmitChanges.value=Object.fromEntries(resubmitFields.value.map(key=>[key,['身份证号','银行卡号'].includes(key)?'':String(row.changes?.[key] ?? '')]))
  resubmitReason.value=row.reason || ''
  resubmitPhotoKeys.value=row.applicationType==='CONTRACT'?[]:PHOTO_KEYS
  resubmitPhotos.value={}
  resubmitPhotoUploading.value={}
  resubmitVisible.value=true
}
async function confirmResubmit() {
  const changes={}
  for(const field of resubmitFields.value) {
    const value=resubmitChanges.value[field]
    if(['身份证号','银行卡号'].includes(field) && !value)continue
    if(value !== String(resubmitRow.value.changes?.[field] ?? ''))changes[field]=value
  }
  // 照片：仅提交本次重新上传的字段（未重传的保留原申请照片）
  Object.assign(changes,resubmitPhotos.value)
  resubmitLoading.value=true
  try {
    await resubmitRequest(resubmitRow.value.id,{changes,reason:resubmitReason.value})
    resubmitVisible.value=false
    ElMessage.success('已重新提交，等待审批')
    await fetchList()
  } finally {resubmitLoading.value=false}
}

// 重新上传照片：大小限制沿用后端真源 expert.upload（GET /api/meta/limits），与专家表单一致
function beforeResubmitPhoto(raw){
  if(raw.size > uploadLimits.photoMaxBytes){
    ElMessage.warning(`图片大小不能超过 ${toMb(uploadLimits.photoMaxBytes)}MB`)
    return false
  }
  return true
}
async function uploadResubmitPhoto(key,{file}){
  resubmitPhotoUploading.value={...resubmitPhotoUploading.value,[key]:true}
  try{
    const res=await uploadApprovalPhoto(file)
    resubmitPhotos.value={...resubmitPhotos.value,[key]:'TMP:'+res.fileName}
  }catch(e){
    // 错误提示已由拦截器统一处理
  }finally{
    resubmitPhotoUploading.value={...resubmitPhotoUploading.value,[key]:false}
  }
}
function resubmitPhotoState(key){
  if(resubmitPhotos.value[key])return '已重新上传，提交后生效'
  return resubmitRow.value?.changes?.[key] ? '原申请已更换照片（不重传则保留）' : '未更换（沿用专家现有照片）'
}

function handleStatusChange() {
  const q = { ...route.query }
  if (status.value === undefined || status.value === '') {
    delete q.status
  } else {
    q.status = String(status.value)
  }
  router.replace({ query: q })
  page.value = 1
  fetchList()
}

// URL query.status 变化（浏览器前进/后退或通知跳转）时同步本地筛选
watch(
  () => route.query.status,
  (val) => {
    const next = val === undefined || val === '' ? undefined : Number(val)
    if (next !== status.value) {
      status.value = next
      page.value = 1
      fetchList()
    }
  }
)

onMounted(() => {
  fetchList()
  ensureUploadLimits()
})
watchDashboardFocus(route, 'applicationId', 'application', row => row.status === 4 ? handleResubmit(row) : openProgress(row), message => ElMessage.warning(message))
</script>

<style scoped>
.page-head {
  display: flex;
  align-items: baseline;
  gap: 12px;
}
.filter-bar {
  margin-top: 12px;
}
.progress-expert {
  font-size: 15px;
  font-weight: 600;
  color: var(--ui-text-main);
  margin-bottom: 16px;
}
.timeline-node {
  font-size: 14px;
  font-weight: 600;
  color: var(--ui-text-main);
}
.timeline-meta {
  font-size: 12px;
  color: var(--ui-text-muted);
  margin-top: 4px;
}
.timeline-comment {
  font-size: 13px;
  color: var(--ui-text-sub);
  margin-top: 4px;
}
.resubmit-photo-state {
  font-size: 13px;
  color: var(--ui-text-muted);
  margin-right: 12px;
}
</style>
