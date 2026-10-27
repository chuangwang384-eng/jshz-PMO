<template>
  <el-dialog
    :model-value="modelValue"
    :title="dialogTitle"
    width="760px"
    destroy-on-close
    @update:model-value="(v) => emit('update:modelValue', v)"
    @open="comment = ''"
  >
    <!-- 头部：专家/申请人/类型/时间，已处理单附审批结果与时间 -->
    <div class="diff-head">
      <div class="diff-meta">
        <span>专家：<b>{{ row?.expertName || '-' }}</b></span>
        <span>申请人：{{ row?.applicant || '-' }}</span>
        <span>申请类型：{{ row?.requestTypeText || '-' }}</span>
        <span>申请时间：{{ row?.createTime || '-' }}</span>
      </div>
      <div v-if="row && row.status !== 0" class="diff-meta">
        <span>
          审批结果：<el-tag :type="approvalStatusTagType(row.status)">{{ row.statusText }}</el-tag>
        </span>
        <span>审批人：{{ row.approveBy || '-' }}</span>
        <span>审批时间：{{ row.approveTime || '-' }}</span>
      </div>
    </div>

    <!-- 申请原因 -->
    <div class="reason-box">
      <span class="reason-label">申请原因：</span>
      <span>{{ row?.reason || '-' }}</span>
    </div>

    <!-- 历史单提示：修改申请但未留存旧值快照（2026-10-27 之前已通过的存量单） -->
    <el-alert
      v-if="showLegacyTip"
      type="warning"
      :closable="false"
      show-icon
      title="该申请单为历史数据（旧值快照未留存），无法显示变更前内容，仅展示申请提交的变更后值"
      style="margin-bottom: 12px"
    />

    <!-- 字段对比表：字段 | 变更前 | → | 变更后 -->
    <div class="diff-table">
      <div class="diff-tr diff-th">
        <div class="diff-td field">字段</div>
        <div class="diff-td">变更前</div>
        <div class="diff-td arrow"></div>
        <div class="diff-td">变更后</div>
      </div>
      <div v-for="item in diffRows" :key="item.key" class="diff-tr">
        <div class="diff-td field">
          {{ item.key }}
          <el-tag v-if="item.isNew" size="small" type="success">新增</el-tag>
        </div>
        <div class="diff-td old">
          <!-- 照片字段：变更前缩略图 -->
          <PhotoImage
            v-if="item.photo && !item.isNew && item.old"
            :filename="String(item.old)"
            :width="72"
            :height="72"
            empty-text="已清理"
          />
          <span v-else-if="item.photo && !item.isNew" class="muted">未上传</span>
          <span v-else-if="item.isNew || item.old === undefined || item.old === null || String(item.old).trim() === ''" class="muted">-</span>
          <span v-else>{{ item.old }}</span>
        </div>
        <div class="diff-td arrow">→</div>
        <div class="diff-td new">
          <!-- 照片字段：变更后缩略图（待审批=临时照片，已生效=归档照片，驳回/撤回=临时文件已清理） -->
          <template v-if="item.photo">
            <PhotoImage
              v-if="newPhotoOf(item.key)"
              :filename="newPhotoOf(item.key).name"
              :temp="newPhotoOf(item.key).temp"
              :width="72"
              :height="72"
              empty-text="未上传"
            />
            <span v-else-if="newPhotoCleaned(item.key)" class="muted">临时照片已清理</span>
            <span v-else class="muted">-</span>
          </template>
          <span v-else>{{ item.value ?? '' }}</span>
        </div>
      </div>
      <el-empty v-if="!diffRows.length" description="无变更内容" :image-size="60" />
    </div>

    <!-- 底部操作区：按 mode 区分 -->
    <template #footer>
      <div v-if="mode === 'view'" class="footer-plain">
        <el-button @click="emit('update:modelValue', false)">关 闭</el-button>
      </div>
      <div v-else class="footer-action">
        <el-input
          v-model="comment"
          type="textarea"
          :rows="3"
          maxlength="200"
          :show-word-limit="mode === 'reject'"
          :placeholder="mode === 'approve' ? '审批意见（可空）' : '请填写驳回意见（必填）'"
        />
        <div class="footer-buttons">
          <el-button @click="emit('update:modelValue', false)">取 消</el-button>
          <el-button
            v-permission="'approval:audit'"
            :type="mode === 'approve' ? 'success' : 'danger'"
            :loading="submitting"
            @click="confirm"
          >{{ mode === 'approve' ? '确认通过' : '确认驳回' }}</el-button>
        </div>
      </div>
    </template>
  </el-dialog>
</template>

<script setup>
import { computed, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { approveRequest, rejectRequest } from '../api/approval'
import { approvalStatusTagType } from '../utils/format'
import PhotoImage from './PhotoImage.vue'

// 审批变更前后对比弹窗（审批管理 + 我的申请共用）：
// mode=view 只读查看 / approve 通过（意见可空）/ reject 驳回（意见必填），
// 审批动作在弹窗内完成，成功后 emit('success') 由父组件刷新列表。
const props = defineProps({
  modelValue: { type: Boolean, default: false },
  row: { type: Object, default: null },
  mode: { type: String, default: 'view' }
})

const emit = defineEmits(['update:modelValue', 'success'])

const PHOTO_KEYS = ['照片', '身份证正面', '身份证反面']
// 字段展示顺序与后端白名单一致（白名单外键兜底排在末尾）
const FIELD_ORDER = [
  '姓名', '单位', '科室', '职称', '研究方向', '擅长领域',
  '联系方式', '简介', '身份证号', '银行卡号', '银行卡开户行',
  '照片', '身份证正面', '身份证反面'
]

const comment = ref('')
const submitting = ref(false)

const dialogTitle = computed(() => {
  if (props.mode === 'approve') return '审批通过（变更前后对比）'
  if (props.mode === 'reject') return '审批驳回（变更前后对比）'
  return '申请详情对比'
})

// 历史单提示：修改申请且无旧值快照（存量已通过单未回填）
const showLegacyTip = computed(() => {
  return props.row && props.row.requestType === 0 && !Object.keys(props.row.oldData || {}).length
})

const diffRows = computed(() => {
  const changes = props.row?.changes || {}
  const keys = Object.keys(changes)
  const ordered = FIELD_ORDER.filter((k) => keys.includes(k)).concat(keys.filter((k) => !FIELD_ORDER.includes(k)))
  const isNew = props.row?.requestType === 1
  return ordered.map((k) => ({
    key: k,
    photo: PHOTO_KEYS.includes(k),
    isNew,
    old: props.row?.oldData?.[k],
    value: changes[k]
  }))
})

// 变更后照片源：photoChanges 原始值（TMP: 前缀=待审批临时照片走 /api/files-tmp，否则为生效后归档文件名）
function newPhotoOf(key) {
  const raw = props.row?.photoChanges?.[key]
  if (!raw) return null
  const value = String(raw)
  if (value.startsWith('TMP:')) {
    if (props.row.status !== 0) return null
    return { temp: true, name: value.slice('TMP:'.length) }
  }
  return { temp: false, name: value }
}

// 驳回/撤回后临时照片已被清理（值仍为 TMP: 但单据非待审批），无法预览
function newPhotoCleaned(key) {
  const raw = props.row?.photoChanges?.[key]
  return !!raw && String(raw).startsWith('TMP:') && props.row.status !== 0
}

async function confirm() {
  if (props.mode === 'reject' && !comment.value.trim()) {
    ElMessage.warning('请填写驳回意见')
    return
  }
  submitting.value = true
  try {
    if (props.mode === 'approve') {
      await approveRequest(props.row.id, { comment: comment.value.trim() || undefined })
      ElMessage.success('已通过')
    } else {
      await rejectRequest(props.row.id, { comment: comment.value.trim() })
      ElMessage.success('已驳回')
    }
    emit('update:modelValue', false)
    emit('success')
  } catch (e) {
    // 错误提示已由拦截器统一处理
  } finally {
    submitting.value = false
  }
}
</script>

<style scoped>
.diff-head {
  margin-bottom: 10px;
}
.diff-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 18px;
  font-size: 13px;
  color: #606266;
  margin-bottom: 6px;
}
.diff-meta b {
  color: #303133;
  font-size: 14px;
}
.reason-box {
  font-size: 13px;
  color: #606266;
  background: #f5f7fa;
  border-radius: 4px;
  padding: 8px 12px;
  margin-bottom: 12px;
  word-break: break-all;
}
.reason-label {
  font-weight: 600;
  color: #303133;
}
.diff-table {
  border: 1px solid #ebeef5;
  border-radius: 4px;
  overflow: hidden;
}
.diff-tr {
  display: grid;
  grid-template-columns: 130px 1fr 28px 1fr;
  border-bottom: 1px solid #ebeef5;
}
.diff-tr:last-child {
  border-bottom: none;
}
.diff-th {
  background: #f5f7fa;
  font-weight: 600;
  color: #606266;
  font-size: 13px;
}
.diff-td {
  padding: 8px 10px;
  font-size: 13px;
  display: flex;
  align-items: center;
  word-break: break-all;
  min-width: 0;
}
.diff-td.field {
  color: #606266;
  font-weight: 600;
  gap: 6px;
}
.diff-td.old {
  color: #909399;
}
.diff-td.new {
  color: #303133;
  font-weight: 600;
}
.diff-td.arrow {
  justify-content: center;
  color: #909399;
}
.muted {
  color: #a8abb2;
}
.footer-action {
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.footer-buttons {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
}
</style>
