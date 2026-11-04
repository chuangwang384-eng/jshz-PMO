<template>
  <div>
    <div class="toolbar">
      <el-input
        v-model="keyword"
        placeholder="按姓名 / 单位 / 科室 / 职称 / 联系方式 / 简介检索"
        clearable
        style="width: 360px"
        @keyup.enter="handleSearch"
      >
        <template #append>
          <el-button @click="handleSearch">搜索</el-button>
        </template>
      </el-input>
      <!-- 外部链接：卫健委机构查询平台（新窗口打开，仅前端跳转，不依赖后端） -->
      <el-button
        tag="a"
        href="https://zgcx.nhc.gov.cn/unit"
        target="_blank"
        rel="noopener"
        type="primary"
        plain
        style="margin-left: 12px"
      >
        专家单位查询
      </el-button>
      <div class="spacer" />
      <template v-if="canAddExpert">
        <el-button type="primary" @click="openAdminCreate">新增专家</el-button>
      </template>
      <template v-else>
        <el-button type="primary" @click="openCreateDialog">新增专家</el-button>
      </template>
      <template v-if="canExport">
        <el-button type="success" @click="handleExportExcel">下载全量 Excel</el-button>
        <el-button type="warning" @click="handleExportZip">下载含照片 ZIP</el-button>
      </template>
      <template v-else>
        <el-button type="primary" :disabled="!selectedIds.size" @click="handleExportSelectedExcel">
          下载选中 Excel{{ selectedIds.size ? `（${selectedIds.size}）` : '' }}
        </el-button>
        <el-button type="info" :disabled="!selectedIds.size" @click="handleExportSelectedZip">
          下载选中含照片 ZIP{{ selectedIds.size ? `（${selectedIds.size}）` : '' }}
        </el-button>
      </template>
    </div>

    <!-- 已选专家姓名展示：跨搜索/翻页保留，可逐项取消或一键清空 -->
    <div v-if="!canExport && selectedExperts.length" class="selected-bar">
      <span class="selected-label">已选 {{ selectedExperts.length }} 位：</span>
      <el-tag
        v-for="expert in selectedExperts"
        :key="expert.id"
        closable
        type="primary"
        @close="removeSelectedExpert(expert)"
      >
        {{ expert.name }}
      </el-tag>
      <el-button type="danger" link size="small" @click="clearSelection">清空</el-button>
    </div>

    <div v-loading="loading" class="expert-cards" style="margin-top: 16px; min-height: 120px">
      <div
        v-for="expert in experts"
        :key="expert.id"
        class="expert-card"
        :class="{ selected: selectedIds.has(expert.id) }"
        @click="openDetail(expert)"
      >
        <el-checkbox
          v-if="!canExport"
          :model-value="selectedIds.has(expert.id)"
          class="card-check"
          @click.stop
          @change="() => toggleSelect(expert)"
        />
        <div class="card-photo">
          <PhotoImage :filename="expert.照片" :width="128" :height="128" />
        </div>
        <div class="card-name">{{ expert.姓名 }}</div>
        <div class="card-text">{{ expert.单位 }}</div>
        <div class="card-text">{{ expert.科室 }}</div>
        <div class="card-meta">
          <span class="card-meta-item" :title="expert['修改时间'] || '—'">
            <svg class="meta-icon" viewBox="0 0 16 16" fill="none" stroke="currentColor" stroke-width="1.35" stroke-linecap="round" stroke-linejoin="round" width="15" height="15" aria-hidden="true">
              <circle cx="8" cy="8" r="6.5" />
              <polyline points="8 4.5 8 8 10.5 10" />
            </svg>
            <span>{{ expert['修改时间'] ? formatMonthDay(expert['修改时间']) : '—' }}</span>
          </span>
          <span class="card-meta-item" :title="expert['修改人id'] || '—'">
            <svg class="meta-icon" viewBox="0 0 16 16" fill="none" stroke="currentColor" stroke-width="1.35" stroke-linecap="round" stroke-linejoin="round" width="15" height="15" aria-hidden="true">
              <circle cx="8" cy="5.5" r="3" />
              <path d="M3.5 14a4.5 4.5 0 0 1 9 0" />
            </svg>
            <span>{{ expert['修改人id'] || '—' }}</span>
          </span>
          <span class="card-meta-item">
            <svg class="meta-icon" viewBox="0 0 16 16" fill="none" stroke="currentColor" stroke-width="1.35" stroke-linecap="round" stroke-linejoin="round" width="15" height="15" aria-hidden="true">
              <path d="M8 2v8" />
              <polyline points="4.5 7 8 10.5 11.5 7" />
              <path d="M3 14h10" />
            </svg>
            <span>{{ expert['下载次数'] ?? 0 }}</span>
          </span>
        </div>
      </div>
      <el-empty
        v-if="!loading && !experts.length"
        description="暂无专家数据"
        :image-size="100"
        style="grid-column: 1 / -1"
      />
    </div>

    <div class="pagination-wrap">
      <el-pagination
        v-model:current-page="page"
        v-model:page-size="size"
        :total="total"
        :page-sizes="[10, 20, 50, 100]"
        layout="total, sizes, prev, pager, next, jumper"
        @size-change="handlePageChange"
        @current-change="handlePageChange"
      />
    </div>

    <!-- 专家详情（统一视图：管理员/超管可保存直写与删除，员工走审批流） -->
    <el-dialog
      v-model="detailVisible"
      title="专家详情"
      width="760px"
      destroy-on-close
      :before-close="handleDetailClose"
    >
      <el-form v-if="detail" :model="editForm" label-width="150px" class="edit-form">
        <div class="edit-photos">
          <div class="edit-photo-item">
            <div class="edit-photo-label">证件照</div>
            <div class="edit-photo-box round">
              <img v-if="photoPreview.photo" :src="photoPreview.photo" class="edit-photo-img" alt="证件照预览" />
              <PhotoImage v-else :filename="detail.照片" :width="80" :height="80" />
            </div>
            <el-upload
              :auto-upload="false"
              :show-file-list="false"
              accept="image/*"
              :on-change="(f) => onPickPhoto('photo', f)"
            >
              <el-button size="small" type="primary" plain>更换照片</el-button>
            </el-upload>
          </div>
          <div class="edit-photo-item">
            <div class="edit-photo-label">身份证正面</div>
            <div class="edit-photo-box">
              <img v-if="activePhotoPreview.front" :src="activePhotoPreview.front" class="edit-photo-img" alt="身份证正面预览" />
              <div v-else-if="idFrontUrl" class="masked-photo">
                <img :src="idFrontUrl" class="masked-img" alt="身份证正面（已脱敏）" />
                <span class="masked-tip">已脱敏</span>
              </div>
              <span v-else class="photo-empty">未上传</span>
            </div>
            <el-upload
              :auto-upload="false"
              :show-file-list="false"
              accept="image/*"
              :on-change="(f) => onPickPhoto('front', f)"
            >
              <el-button size="small" type="primary" plain>更换照片</el-button>
            </el-upload>
          </div>
          <div class="edit-photo-item">
            <div class="edit-photo-label">身份证反面</div>
            <div class="edit-photo-box">
              <img v-if="activePhotoPreview.back" :src="activePhotoPreview.back" class="edit-photo-img" alt="身份证反面预览" />
              <div v-else-if="idBackUrl" class="masked-photo">
                <img :src="idBackUrl" class="masked-img" alt="身份证反面（已脱敏）" />
                <span class="masked-tip">已脱敏</span>
              </div>
              <span v-else class="photo-empty">未上传</span>
            </div>
            <el-upload
              :auto-upload="false"
              :show-file-list="false"
              accept="image/*"
              :on-change="(f) => onPickPhoto('back', f)"
            >
              <el-button size="small" type="primary" plain>更换照片</el-button>
            </el-upload>
          </div>
        </div>
        <el-form-item label="姓名">
          <el-input v-model="editForm.姓名" placeholder="请输入姓名">
            <template #suffix>
              <el-tooltip content="复制" placement="top">
                <el-icon class="copy-icon" @click="copyFieldValue(editForm['姓名'])"><CopyDocument /></el-icon>
              </el-tooltip>
            </template>
          </el-input>
        </el-form-item>
        <el-form-item label="单位">
          <el-input v-model="editForm.单位" placeholder="请输入单位">
            <template #suffix>
              <el-tooltip content="复制" placement="top">
                <el-icon class="copy-icon" @click="copyFieldValue(editForm['单位'])"><CopyDocument /></el-icon>
              </el-tooltip>
            </template>
          </el-input>
        </el-form-item>
        <el-form-item label="科室">
          <el-input v-model="editForm.科室" placeholder="请输入科室">
            <template #suffix>
              <el-tooltip content="复制" placement="top">
                <el-icon class="copy-icon" @click="copyFieldValue(editForm['科室'])"><CopyDocument /></el-icon>
              </el-tooltip>
            </template>
          </el-input>
        </el-form-item>
        <el-form-item label="职称">
          <el-input v-model="editForm.职称" placeholder="请输入职称">
            <template #suffix>
              <el-tooltip content="复制" placement="top">
                <el-icon class="copy-icon" @click="copyFieldValue(editForm['职称'])"><CopyDocument /></el-icon>
              </el-tooltip>
            </template>
          </el-input>
        </el-form-item>
        <el-form-item label="联系方式">
          <el-input v-model="editForm.联系方式" placeholder="请输入联系方式">
            <template #suffix>
              <el-tooltip content="复制" placement="top">
                <el-icon class="copy-icon" @click="copyFieldValue(editForm['联系方式'])"><CopyDocument /></el-icon>
              </el-tooltip>
            </template>
          </el-input>
        </el-form-item>
        <el-form-item label="简介">
          <div class="textarea-copy-wrap">
            <el-input v-model="editForm.简介" type="textarea" :rows="2" placeholder="请输入简介" />
            <el-tooltip content="复制" placement="top">
              <el-icon class="copy-icon textarea-copy" @click="copyFieldValue(editForm['简介'])"><CopyDocument /></el-icon>
            </el-tooltip>
          </div>
        </el-form-item>
        <el-form-item label="身份证号">
          <el-input v-model="editForm.身份证号" placeholder="请输入身份证号（18 位）">
            <template #suffix>
              <el-tooltip content="复制" placement="top">
                <el-icon class="copy-icon" @click="copyFieldValue(editForm['身份证号'])"><CopyDocument /></el-icon>
              </el-tooltip>
            </template>
          </el-input>
        </el-form-item>
        <el-form-item label="银行卡号">
          <el-input v-model="editForm.银行卡号" placeholder="请输入银行卡号">
            <template #suffix>
              <el-tooltip content="复制" placement="top">
                <el-icon class="copy-icon" @click="copyFieldValue(editForm['银行卡号'])"><CopyDocument /></el-icon>
              </el-tooltip>
            </template>
          </el-input>
        </el-form-item>
        <el-form-item label="银行卡开户行">
          <el-input v-model="editForm.银行卡开户行" placeholder="请输入开户行">
            <template #suffix>
              <el-tooltip content="复制" placement="top">
                <el-icon class="copy-icon" @click="copyFieldValue(editForm['银行卡开户行'])"><CopyDocument /></el-icon>
              </el-tooltip>
            </template>
          </el-input>
        </el-form-item>
        <el-alert
          type="info"
          :closable="false"
          show-icon
          :title="canEditExpert ? '管理员编辑免审批，保存后直接生效' : '修改将在审批通过后生效，关闭弹窗时如检测到修改会引导您提交申请'"
        />
      </el-form>
      <template #footer>
        <el-button v-permission="'expert:delete'" type="danger" :loading="adminDeleting" @click="handleAdminDelete">删除专家</el-button>
        <el-button @click="handleDetailCancel">取消</el-button>
        <el-button v-if="canEditExpert" type="primary" :loading="adminSubmitting" @click="submitAdminEdit">保存</el-button>
        <el-button v-else type="primary" @click="handleDetailSubmit">提交申请</el-button>
      </template>
    </el-dialog>

    <!-- 新增专家申请弹窗（员工：复用可编辑表单字段结构与照片选择，提交审批流） -->
    <el-dialog v-model="createVisible" title="新增专家（需审批）" width="760px" destroy-on-close>
      <el-form :model="createForm" label-width="110px" class="edit-form">
        <div class="edit-photos">
          <div class="edit-photo-item">
            <div class="edit-photo-label">证件照</div>
            <div class="edit-photo-box round">
              <img v-if="createPhotoPreview.photo" :src="createPhotoPreview.photo" class="edit-photo-img" alt="证件照预览" />
              <span v-else class="photo-empty">未上传</span>
            </div>
            <el-upload
              :auto-upload="false"
              :show-file-list="false"
              accept="image/*"
              :on-change="(f) => onCreatePickPhoto('photo', f)"
            >
              <el-button size="small" type="primary" plain>上传照片</el-button>
            </el-upload>
          </div>
          <div class="edit-photo-item">
            <div class="edit-photo-label">身份证正面</div>
            <div class="edit-photo-box">
              <img v-if="createPhotoPreview.front" :src="createPhotoPreview.front" class="edit-photo-img" alt="身份证正面预览" />
              <span v-else class="photo-empty">未上传</span>
            </div>
            <el-upload
              :auto-upload="false"
              :show-file-list="false"
              accept="image/*"
              :on-change="(f) => onCreatePickPhoto('front', f)"
            >
              <el-button size="small" type="primary" plain>上传照片</el-button>
            </el-upload>
          </div>
          <div class="edit-photo-item">
            <div class="edit-photo-label">身份证反面</div>
            <div class="edit-photo-box">
              <img v-if="createPhotoPreview.back" :src="createPhotoPreview.back" class="edit-photo-img" alt="身份证反面预览" />
              <span v-else class="photo-empty">未上传</span>
            </div>
            <el-upload
              :auto-upload="false"
              :show-file-list="false"
              accept="image/*"
              :on-change="(f) => onCreatePickPhoto('back', f)"
            >
              <el-button size="small" type="primary" plain>上传照片</el-button>
            </el-upload>
          </div>
        </div>
        <el-form-item label="姓名">
          <el-input v-model="createForm.姓名" placeholder="请输入姓名（2-20 个汉字）" />
        </el-form-item>
        <el-form-item label="单位">
          <el-input v-model="createForm.单位" placeholder="请输入单位" />
        </el-form-item>
        <el-form-item label="科室">
          <el-input v-model="createForm.科室" placeholder="请输入科室" />
        </el-form-item>
        <el-form-item label="职称">
          <el-input v-model="createForm.职称" placeholder="请输入职称" />
        </el-form-item>
        <el-form-item label="联系方式">
          <el-input v-model="createForm.联系方式" placeholder="请输入联系方式" />
        </el-form-item>
        <el-form-item label="简介">
          <el-input v-model="createForm.简介" type="textarea" :rows="2" placeholder="请输入简介" />
        </el-form-item>
        <el-form-item label="身份证号">
          <el-input v-model="createForm.身份证号" placeholder="请输入身份证号（18 位）" />
        </el-form-item>
        <el-form-item label="银行卡号">
          <el-input v-model="createForm.银行卡号" placeholder="请输入银行卡号" />
        </el-form-item>
        <el-form-item label="银行卡开户行">
          <el-input v-model="createForm.银行卡开户行" placeholder="请输入开户行" />
        </el-form-item>
        <el-alert
          type="info"
          :closable="false"
          show-icon
          title="提交后将生成审批流，管理员审批通过后创建专家记录并归档照片"
        />
      </el-form>
      <template #footer>
        <el-button @click="createVisible = false">取消</el-button>
        <el-button type="primary" :loading="createSubmitting" @click="submitCreateExpert">提交申请</el-button>
      </template>
    </el-dialog>

    <!-- 新增专家弹窗（管理员：multipart 直传免审批） -->
    <el-dialog v-model="adminCreateVisible" title="新增专家（免审批）" width="760px" destroy-on-close>
      <el-form :model="adminCreateForm" label-width="110px" class="edit-form">
        <div class="edit-photos">
          <div class="edit-photo-item">
            <div class="edit-photo-label">证件照</div>
            <div class="edit-photo-box round">
              <img v-if="adminCreatePhotoPreview.photo" :src="adminCreatePhotoPreview.photo" class="edit-photo-img" alt="证件照预览" />
              <span v-else class="photo-empty">未上传</span>
            </div>
            <el-upload
              :auto-upload="false"
              :show-file-list="false"
              accept="image/*"
              :on-change="(f) => onAdminCreatePickPhoto('photo', f)"
            >
              <el-button size="small" type="primary" plain>上传照片</el-button>
            </el-upload>
          </div>
          <div class="edit-photo-item">
            <div class="edit-photo-label">身份证正面</div>
            <div class="edit-photo-box">
              <img v-if="adminCreatePhotoPreview.front" :src="adminCreatePhotoPreview.front" class="edit-photo-img" alt="身份证正面预览" />
              <span v-else class="photo-empty">未上传</span>
            </div>
            <el-upload
              :auto-upload="false"
              :show-file-list="false"
              accept="image/*"
              :on-change="(f) => onAdminCreatePickPhoto('front', f)"
            >
              <el-button size="small" type="primary" plain>上传照片</el-button>
            </el-upload>
          </div>
          <div class="edit-photo-item">
            <div class="edit-photo-label">身份证反面</div>
            <div class="edit-photo-box">
              <img v-if="adminCreatePhotoPreview.back" :src="adminCreatePhotoPreview.back" class="edit-photo-img" alt="身份证反面预览" />
              <span v-else class="photo-empty">未上传</span>
            </div>
            <el-upload
              :auto-upload="false"
              :show-file-list="false"
              accept="image/*"
              :on-change="(f) => onAdminCreatePickPhoto('back', f)"
            >
              <el-button size="small" type="primary" plain>上传照片</el-button>
            </el-upload>
          </div>
        </div>
        <el-form-item label="姓名">
          <el-input v-model="adminCreateForm.姓名" placeholder="请输入姓名（2-20 个汉字）" />
        </el-form-item>
        <el-form-item label="单位">
          <el-input v-model="adminCreateForm.单位" placeholder="请输入单位" />
        </el-form-item>
        <el-form-item label="科室">
          <el-input v-model="adminCreateForm.科室" placeholder="请输入科室" />
        </el-form-item>
        <el-form-item label="职称">
          <el-input v-model="adminCreateForm.职称" placeholder="请输入职称" />
        </el-form-item>
        <el-form-item label="联系方式">
          <el-input v-model="adminCreateForm.联系方式" placeholder="请输入联系方式" />
        </el-form-item>
        <el-form-item label="简介">
          <el-input v-model="adminCreateForm.简介" type="textarea" :rows="2" placeholder="请输入简介" />
        </el-form-item>
        <el-form-item label="身份证号">
          <el-input v-model="adminCreateForm.身份证号" placeholder="请输入身份证号（18 位）" />
        </el-form-item>
        <el-form-item label="银行卡号">
          <el-input v-model="adminCreateForm.银行卡号" placeholder="请输入银行卡号" />
        </el-form-item>
        <el-form-item label="银行卡开户行">
          <el-input v-model="adminCreateForm.银行卡开户行" placeholder="请输入开户行" />
        </el-form-item>
        <el-alert
          type="info"
          :closable="false"
          show-icon
          title="管理员新增免审批，保存后直接创建专家记录并归档照片"
        />
      </el-form>
      <template #footer>
        <el-button @click="adminCreateVisible = false">取消</el-button>
        <el-button type="primary" :loading="adminCreateSubmitting" @click="submitAdminCreate">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { listExperts, getExpert, createExpert, updateExpert, deleteExpert, exportAllExcel, exportEmployeeZip, exportSelectedExcel, exportSelectedZip, fetchPhotoBlob } from '../api/expert'
import { submitApprovalRequest, uploadApprovalPhoto, createExpertApprovalRequest } from '../api/approval'
import { useAuthStore } from '../store/auth'
import PhotoImage from '../components/PhotoImage.vue'
import { CopyDocument } from '@element-plus/icons-vue'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()
const canAddExpert = computed(() => authStore.hasPermission('expert:add'))
const canEditExpert = computed(() => authStore.hasPermission('expert:edit'))
const canExport = computed(() => authStore.hasPermission('expert:export'))
// 统一详情弹窗照片预览：管理员/超管显示 admin 预览，员工显示 edit 预览
const activePhotoPreview = computed(() => (canEditExpert.value ? adminPhotoPreview : photoPreview))
const selectedIds = ref(new Set())
// 已选专家姓名（{id, 姓名}），跨搜索/翻页保留，用于标签展示与逐项取消
const selectedExperts = ref([])

const keyword = ref('')
const experts = ref([])
const total = ref(0)
const page = ref(1)
const size = ref(20)
const loading = ref(false)

const detailVisible = ref(false)
const detail = ref(null)
// 身份证正反面照片 blob URL（仅脱敏模糊展示，不提供原图预览）
const idFrontUrl = ref('')
const idBackUrl = ref('')

// 卡片内直接编辑（需求3：员工视角详情弹窗可编辑，关闭时检测变更生成审批流）
const TEXT_FIELDS = ['姓名', '单位', '科室', '职称', '联系方式', '简介', '身份证号', '银行卡号', '银行卡开户行']
const editForm = reactive({
  姓名: '',
  单位: '',
  科室: '',
  职称: '',
  联系方式: '',
  简介: '',
  身份证号: '',
  银行卡号: '',
  银行卡开户行: ''
})
// 打开弹窗时的初始值快照（用于 dirty 检测，员工视角脱敏值不改则不提交）
const editSnapshot = ref(null)
// 照片变更标记 + 本地预览 + 待上传文件
const photoChanged = reactive({ photo: false, front: false, back: false })
const photoPreview = reactive({ photo: '', front: '', back: '' })
const pickedFiles = reactive({ photo: null, front: null, back: null })

// 是否有未提交的修改：任一文本字段变化或任一照片被更换
const dirty = computed(() => {
  if (!editSnapshot.value) return false
  for (const k of TEXT_FIELDS) {
    if (String(editForm[k] ?? '').trim() !== String(editSnapshot.value[k] ?? '').trim()) {
      return true
    }
  }
  return photoChanged.photo || photoChanged.front || photoChanged.back
})

async function fetchList() {
  loading.value = true
  try {
    const data = await listExperts({
      keyword: keyword.value || undefined,
      page: page.value,
      size: size.value
    })
    experts.value = data?.items || []
    total.value = data?.total || 0
  } finally {
    loading.value = false
  }
}

// 更新时间仅显示 MM-DD（如 10-22），完整时间由调用处放 title
function formatMonthDay(value) {
  if (!value) return '—'
  const s = String(value).trim()
  const m = s.match(/(\d{4})-(\d{2})-(\d{2})/)
  return m ? `${m[2]}-${m[3]}` : s
}

function handleSearch() {
  page.value = 1
  // 搜索不清理已勾选数据，勾选集合跨搜索保留
  fetchList()
}

// 翻页 / 改每页条数：重新加载数据但保留勾选（selectedIds 为全局集合，跨页有效）
function handlePageChange() {
  fetchList()
}

function clearSelection() {
  if (selectedIds.value.size) {
    selectedIds.value = new Set()
    selectedExperts.value = []
    ElMessage.info('已清空勾选')
  }
}

// 勾选切换：Set 快照更新，避免引用问题；同步维护已选姓名数组
function toggleSelect(expert) {
  const next = new Set(selectedIds.value)
  if (next.has(expert.id)) {
    next.delete(expert.id)
    selectedExperts.value = selectedExperts.value.filter((e) => e.id !== expert.id)
  } else {
    next.add(expert.id)
    selectedExperts.value.push({ id: expert.id, name: expert.姓名 })
  }
  selectedIds.value = next
}

// 逐项取消：点击姓名标签的 × 时，同步移除 selectedIds 与 selectedExperts
function removeSelectedExpert(expert) {
  const next = new Set(selectedIds.value)
  next.delete(expert.id)
  selectedIds.value = next
  selectedExperts.value = selectedExperts.value.filter((e) => e.id !== expert.id)
}

let detailSequence = 0
onBeforeUnmount(() => { detailSequence++ })
async function openDetail(row) {
  const sequence = ++detailSequence
  const data = await getExpert(row.id, canEditExpert.value ? { sensitive: 1 } : undefined)
  if(sequence !== detailSequence)return
  detail.value = data
  // 统一视图初始化：详情弹窗文本字段统一回填 editForm（管理员免审批直写 / 员工审批流共用）
  if (canEditExpert.value) {
    adminPhotoPreview.photo = ''
    adminPhotoPreview.front = ''
    adminPhotoPreview.back = ''
    adminPickedFiles.photo = null
    adminPickedFiles.front = null
    adminPickedFiles.back = null
  }
  for (const k of TEXT_FIELDS) {
    editForm[k] = data?.[k] ?? ''
  }
  // 简介回填原始简介（data['简介'] 为后端拼接展示值，避免把拼接内容提交审批写回存储）
  editForm['简介'] = data?.['原始简介'] ?? data?.['简介'] ?? ''
  editSnapshot.value = {}
  for (const k of TEXT_FIELDS) {
    editSnapshot.value[k] = data?.[k] ?? ''
  }
  editSnapshot.value['简介'] = data?.['原始简介'] ?? data?.['简介'] ?? ''
  photoChanged.photo = false
  photoChanged.front = false
  photoChanged.back = false
  photoPreview.photo = ''
  photoPreview.front = ''
  photoPreview.back = ''
  pickedFiles.photo = null
  pickedFiles.front = null
  pickedFiles.back = null
  // 加载身份证正反面照片（仅用于脱敏模糊展示，不提供原图预览）；先 revoke 旧 blob URL 防内存泄漏
  if (idFrontUrl.value) {
    URL.revokeObjectURL(idFrontUrl.value)
  }
  if (idBackUrl.value) {
    URL.revokeObjectURL(idBackUrl.value)
  }
  idFrontUrl.value = ''
  idBackUrl.value = ''
  if (data['身份证正面']) {
    try {
      const resp = await fetchPhotoBlob(data['身份证正面'])
      if(sequence !== detailSequence)return
      idFrontUrl.value = URL.createObjectURL(resp.data)
    } catch (e) {
      // 照片不存在或无权限：保持占位
    }
  }
  if (data['身份证反面']) {
    try {
      const resp = await fetchPhotoBlob(data['身份证反面'])
      if(sequence !== detailSequence)return
      idBackUrl.value = URL.createObjectURL(resp.data)
    } catch (e) {
      // 照片不存在或无权限：保持占位
    }
  }
  if(sequence === detailSequence) detailVisible.value = true
}

// 复制字段值到剪贴板：优先 navigator.clipboard，不可用时兜底 document.execCommand('copy')
async function copyFieldValue(value) {
  const text = String(value ?? '').trim()
  if (!text) {
    ElMessage.warning('暂无可复制的内容')
    return
  }
  try {
    if (navigator.clipboard && window.isSecureContext) {
      await navigator.clipboard.writeText(text)
      ElMessage.success('已复制')
      return
    }
    throw new Error('clipboard unavailable')
  } catch (e) {
    const ta = document.createElement('textarea')
    ta.value = text
    ta.style.position = 'fixed'
    ta.style.opacity = '0'
    document.body.appendChild(ta)
    ta.select()
    try {
      document.execCommand('copy')
      ElMessage.success('已复制')
    } catch (err) {
      ElMessage.error('复制失败，请手动复制')
    } finally {
      document.body.removeChild(ta)
    }
  }
}

function handleExportExcel() {
  exportAllExcel().catch(() => {})
}

function handleExportZip() {
  exportEmployeeZip().catch(() => {})
}

function handleExportSelectedExcel() {
  if (!selectedIds.value.size) {
    ElMessage.warning('请先勾选专家卡片')
    return
  }
  exportSelectedExcel([...selectedIds.value]).catch(() => {})
}

function handleExportSelectedZip() {
  if (!selectedIds.value.size) {
    ElMessage.warning('请先勾选专家卡片')
    return
  }
  exportSelectedZip([...selectedIds.value]).catch(() => {})
}

// 选择新照片：本地预览 + 标记变更 + 暂存文件（提交审批时再上传临时接口）；
// 统一弹窗按角色分派：管理员/超管存入 admin 变量（保存时随 FormData 直传），员工存入 edit 变量（提交审批时上传）
function onPickPhoto(kind, file) {
  const raw = file?.raw || file
  if (!raw) {
    return
  }
  if (raw.size > 5 * 1024 * 1024) {
    ElMessage.warning('图片大小不能超过 5MB')
    return
  }
  if (canEditExpert.value) {
    adminPickedFiles[kind] = raw
    if (adminPhotoPreview[kind]) {
      URL.revokeObjectURL(adminPhotoPreview[kind])
    }
    adminPhotoPreview[kind] = URL.createObjectURL(raw)
    return
  }
  pickedFiles[kind] = raw
  photoChanged[kind] = true
  if (photoPreview[kind]) {
    URL.revokeObjectURL(photoPreview[kind])
  }
  photoPreview[kind] = URL.createObjectURL(raw)
}

// ==================== 新增专家申请（需求4：员工提交审批流） ====================
const createVisible = ref(false)
const createSubmitting = ref(false)
const createForm = reactive({
  姓名: '',
  单位: '',
  科室: '',
  职称: '',
  联系方式: '',
  简介: '',
  身份证号: '',
  银行卡号: '',
  银行卡开户行: ''
})
// 新增弹窗照片：本地预览 + 待上传文件
const createPhotoChanged = reactive({ photo: false, front: false, back: false })
const createPhotoPreview = reactive({ photo: '', front: '', back: '' })
const createPickedFiles = reactive({ photo: null, front: null, back: null })

// 打开新增专家弹窗：表单与照片均初始为空
function openCreateDialog() {
  for (const k of TEXT_FIELDS) {
    createForm[k] = ''
  }
  createPhotoChanged.photo = false
  createPhotoChanged.front = false
  createPhotoChanged.back = false
  createPhotoPreview.photo = ''
  createPhotoPreview.front = ''
  createPhotoPreview.back = ''
  createPickedFiles.photo = null
  createPickedFiles.front = null
  createPickedFiles.back = null
  createVisible.value = true
}

// 新增弹窗选照片：本地预览 + 暂存文件（提交时再上传临时接口）
function onCreatePickPhoto(kind, file) {
  const raw = file?.raw || file
  if (!raw) {
    return
  }
  if (raw.size > 5 * 1024 * 1024) {
    ElMessage.warning('图片大小不能超过 5MB')
    return
  }
  createPickedFiles[kind] = raw
  createPhotoChanged[kind] = true
  if (createPhotoPreview[kind]) {
    URL.revokeObjectURL(createPhotoPreview[kind])
  }
  createPhotoPreview[kind] = URL.createObjectURL(raw)
}

// 提交新增专家申请：先逐个上传照片取 TMP: 文件名并入 changes，再调用 create-request
async function submitCreateExpert() {
  const requiredFields = ['姓名', '单位', '科室', '职称', '联系方式']
  for (const k of requiredFields) {
    if (!String(createForm[k] ?? '').trim()) {
      ElMessage.warning(`请填写${k}`)
      return
    }
  }
  const name = String(createForm.姓名).trim()
  if (!/^[\u4e00-\u9fa5·]{2,20}$/.test(name)) {
    ElMessage.warning('姓名格式不正确（2-20 个汉字，可含间隔号）')
    return
  }
  const idCard = String(createForm.身份证号 ?? '').trim()
  if (idCard && !/^\d{17}[\dXx]$/.test(idCard)) {
    ElMessage.warning('身份证号格式不正确（18 位，末位可为 X）')
    return
  }
  const bankCard = String(createForm.银行卡号 ?? '').trim()
  if (bankCard && !/^\d{13,19}$/.test(bankCard)) {
    ElMessage.warning('银行卡号格式不正确（13-19 位数字）')
    return
  }
  const contact = String(createForm.联系方式 ?? '').trim()
  if (contact && !/^1\d{10}$|^[\w.-]+@[\w.-]+\.[a-zA-Z]{2,}$/.test(contact)) {
    ElMessage.warning('联系方式格式不正确（11 位手机号或邮箱）')
    return
  }
  const changes = {}
  for (const k of TEXT_FIELDS) {
    const v = String(createForm[k] ?? '').trim()
    if (v) {
      changes[k] = v
    }
  }
  const photoItems = [
    { changed: createPhotoChanged.photo, key: '照片', file: createPickedFiles.photo },
    { changed: createPhotoChanged.front, key: '身份证正面', file: createPickedFiles.front },
    { changed: createPhotoChanged.back, key: '身份证反面', file: createPickedFiles.back }
  ]
  for (const item of photoItems) {
    if (item.changed && item.file) {
      try {
        const res = await uploadApprovalPhoto(item.file)
        changes[item.key] = 'TMP:' + res.fileName
      } catch (e) {
        // 错误提示已由拦截器统一处理
        return
      }
    }
  }
  createSubmitting.value = true
  try {
    await createExpertApprovalRequest({
      changes,
      reason: '员工新增专家'
    })
    ElMessage.success('已提交新增专家申请，可在「我的申请」查看进度')
    createVisible.value = false
  } catch (e) {
    // 错误提示已由拦截器统一处理
  } finally {
    createSubmitting.value = false
  }
}

// 提交卡片内编辑的修改申请：仅提交【当前模板可见且有变化】的字段；照片先上传临时接口再以 TMP: 前缀并入
async function submitEditChange() {
  const changes = {}
  for (const k of TEXT_FIELDS) {
    const newVal = String(editForm[k] ?? '').trim()
    const oldVal = String(editSnapshot.value?.[k] ?? '').trim()
    if (newVal !== oldVal) {
      changes[k] = newVal
    }
  }
  const photoItems = [
    { changed: photoChanged.photo, key: '照片', file: pickedFiles.photo },
    { changed: photoChanged.front, key: '身份证正面', file: pickedFiles.front },
    { changed: photoChanged.back, key: '身份证反面', file: pickedFiles.back }
  ]
  for (const item of photoItems) {
    if (item.changed && item.file) {
      try {
        const res = await uploadApprovalPhoto(item.file)
        changes[item.key] = 'TMP:' + res.fileName
      } catch (e) {
        // 错误提示已由拦截器统一处理
        return false
      }
    }
  }
  if (!Object.keys(changes).length) {
    return false
  }
  try {
    await submitApprovalRequest({
      expertId: detail.value.id,
      changes,
      reason: '卡片内直接编辑提交'
    })
    ElMessage.success('已提交修改申请，可在「我的申请」查看进度')
    return true
  } catch (e) {
    // 错误提示已由拦截器统一处理
    return false
  }
}

// 关闭详情弹窗：管理员/超管免审批直写直接关闭（未保存修改不提示，与原行为一致）；
// 员工 dirty 时确认提交 / 放弃修改；未修改直接关闭
async function handleDetailClose(done) {
  if (canEditExpert.value) {
    detailVisible.value = false
    done()
    return
  }
  if (!dirty.value) {
    detailVisible.value = false
    done()
    return
  }
  let confirmed = false
  try {
    await ElMessageBox.confirm(
      '您已修改了专家信息，是否确认提交修改申请？确认后将生成审批流，审批通过后才生效。',
      '提交修改申请',
      { confirmButtonText: '确认提交', cancelButtonText: '放弃修改', type: 'warning' }
    )
    confirmed = true
  } catch (e) {
    // 用户选择放弃修改
  }
  if (confirmed) {
    const ok = await submitEditChange()
    if (ok) {
      detailVisible.value = false
      done()
      return
    }
    // 提交失败：保持弹窗打开，用户可调整后再次关闭提交
    return
  }
  // 放弃修改并关闭
  detailVisible.value = false
  done()
}

// 统一弹窗"提交申请"按钮（员工）：直接提交当前编辑为审批申请
async function handleDetailSubmit() {
  const ok = await submitEditChange()
  if (ok) {
    detailVisible.value = false
    fetchList()
  }
}

// 统一弹窗"取消"按钮：管理员直接关闭；员工走 before-close 相同的确认流程
function handleDetailCancel() {
  handleDetailClose(() => {})
}

// ==================== 管理员：新增 / 编辑 / 删除（免审批直写） ====================
const adminSubmitting = ref(false)
const adminDeleting = ref(false)
const adminPhotoPreview = reactive({ photo: '', front: '', back: '' })
const adminPickedFiles = reactive({ photo: null, front: null, back: null })

// 管理员新增弹窗
const adminCreateVisible = ref(false)
const adminCreateSubmitting = ref(false)
const adminCreateForm = reactive({
  姓名: '', 单位: '', 科室: '', 职称: '',
  联系方式: '', 简介: '', 身份证号: '', 银行卡号: '', 银行卡开户行: ''
})
const adminCreatePhotoPreview = reactive({ photo: '', front: '', back: '' })
const adminCreatePickedFiles = reactive({ photo: null, front: null, back: null })

// 管理员新增弹窗选照片：本地预览 + 暂存文件
function onAdminCreatePickPhoto(kind, file) {
  const raw = file?.raw || file
  if (!raw) {
    return
  }
  if (raw.size > 5 * 1024 * 1024) {
    ElMessage.warning('图片大小不能超过 5MB')
    return
  }
  adminCreatePickedFiles[kind] = raw
  if (adminCreatePhotoPreview[kind]) {
    URL.revokeObjectURL(adminCreatePhotoPreview[kind])
  }
  adminCreatePhotoPreview[kind] = URL.createObjectURL(raw)
}

// 管理员编辑提交：FormData 中文字段 + photo/idCardFront/idCardBack，PUT /api/experts/{id} 直写
function buildAdminFormData() {
  const fd = new FormData()
  // 统一表单 editForm：管理员/超管在详情弹窗中的文本修改即写 editForm（打开时已回填全量）
  for (const k of TEXT_FIELDS) {
    fd.append(k, String(editForm[k] ?? '').trim())
  }
  if (adminPickedFiles.photo) fd.append('photo', adminPickedFiles.photo)
  if (adminPickedFiles.front) fd.append('idCardFront', adminPickedFiles.front)
  if (adminPickedFiles.back) fd.append('idCardBack', adminPickedFiles.back)
  return fd
}

async function submitAdminEdit() {
  if (!String(editForm.姓名 ?? '').trim()) {
    ElMessage.warning('请填写姓名')
    return
  }
  adminSubmitting.value = true
  try {
    await updateExpert(detail.value.id, buildAdminFormData())
    ElMessage.success('保存成功（免审批直改）')
    detailVisible.value = false
    fetchList()
  } catch (e) {
    // 错误提示已由拦截器统一处理
  } finally {
    adminSubmitting.value = false
  }
}

// 管理员删除：二次确认（提示照片一并物理删除）后 DELETE /api/experts/{id}
async function handleAdminDelete() {
  try {
    await ElMessageBox.confirm(
      `确定删除专家「${detail.value?.姓名 ?? ''}」吗？其照片文件将一并物理删除，此操作不可恢复。`,
      '删除确认',
      { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' }
    )
  } catch (e) {
    return
  }
  adminDeleting.value = true
  try {
    await deleteExpert(detail.value.id)
    ElMessage.success('删除成功')
    detailVisible.value = false
    fetchList()
  } catch (e) {
    // 错误提示已由拦截器统一处理
  } finally {
    adminDeleting.value = false
  }
}

// 打开管理员新增弹窗
function openAdminCreate() {
  for (const k of TEXT_FIELDS) {
    adminCreateForm[k] = ''
  }
  adminCreatePhotoPreview.photo = ''
  adminCreatePhotoPreview.front = ''
  adminCreatePhotoPreview.back = ''
  adminCreatePickedFiles.photo = null
  adminCreatePickedFiles.front = null
  adminCreatePickedFiles.back = null
  adminCreateVisible.value = true
}

// 管理员新增提交：FormData 中文字段 + 三类照片文件，POST /api/experts 直写
function buildAdminCreateFormData() {
  const fd = new FormData()
  for (const k of TEXT_FIELDS) {
    fd.append(k, String(adminCreateForm[k] ?? '').trim())
  }
  if (adminCreatePickedFiles.photo) fd.append('photo', adminCreatePickedFiles.photo)
  if (adminCreatePickedFiles.front) fd.append('idCardFront', adminCreatePickedFiles.front)
  if (adminCreatePickedFiles.back) fd.append('idCardBack', adminCreatePickedFiles.back)
  return fd
}

async function submitAdminCreate() {
  const requiredFields = ['姓名', '单位', '科室', '职称', '联系方式']
  for (const k of requiredFields) {
    if (!String(adminCreateForm[k] ?? '').trim()) {
      ElMessage.warning(`请填写${k}`)
      return
    }
  }
  const name = String(adminCreateForm.姓名).trim()
  if (!/^[\u4e00-\u9fa5·]{2,20}$/.test(name)) {
    ElMessage.warning('姓名格式不正确（2-20 个汉字，可含间隔号）')
    return
  }
  const idCard = String(adminCreateForm.身份证号 ?? '').trim()
  if (idCard && !/^\d{17}[\dXx]$/.test(idCard)) {
    ElMessage.warning('身份证号格式不正确（18 位，末位可为 X）')
    return
  }
  const bankCard = String(adminCreateForm.银行卡号 ?? '').trim()
  if (bankCard && !/^\d{13,19}$/.test(bankCard)) {
    ElMessage.warning('银行卡号格式不正确（13-19 位数字）')
    return
  }
  const contact = String(adminCreateForm.联系方式 ?? '').trim()
  if (contact && !/^1\d{10}$|^[\w.-]+@[\w.-]+\.[a-zA-Z]{2,}$/.test(contact)) {
    ElMessage.warning('联系方式格式不正确（11 位手机号或邮箱）')
    return
  }
  adminCreateSubmitting.value = true
  try {
    await createExpert(buildAdminCreateFormData())
    ElMessage.success('新增成功（免审批直写）')
    adminCreateVisible.value = false
    fetchList()
  } catch (e) {
    // 错误提示已由拦截器统一处理
  } finally {
    adminCreateSubmitting.value = false
  }
}

onMounted(() => {
  fetchList()
})
watch(() => route.query.expertId, async id => {
  if(id===undefined)return
  if(!/^[1-9]\d{0,17}$/.test(String(id)))return ElMessage.warning('记录编号无效')
  try{await openDetail({id:String(id)})}catch{ElMessage.warning('记录不可用或无权访问')}
},{immediate:true})
</script>

<style scoped>
.form-tip {
  font-size: 12px;
  color: #909399;
  line-height: 1.4;
  margin-top: 4px;
}
.toolbar {
  display: flex;
  align-items: center;
}
.spacer {
  flex: 1;
}
.pagination-wrap {
  margin-top: 45px;
  display: flex;
  justify-content: flex-end;
  align-items: center;
  padding: 10px 16px;
  background: rgba(255, 255, 255, 0.95);
  backdrop-filter: blur(4px);
  border-top: 1px solid #e4e7ed;
  box-shadow: 0 -2px 8px rgba(0, 21, 41, 0.06);
}
.expert-cards {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  grid-auto-rows: auto;
  column-gap: 20px;
  row-gap: 45px;
}
.expert-card {
  min-width: 0;
  height: 100%;
  display: flex;
  flex-direction: column;
  align-items: center;
  background: #fff;
  border: 1px solid #ebeef5;
  border-radius: 10px;
  padding: 26px 16px 20px;
  text-align: center;
  cursor: pointer;
  transition: box-shadow 0.2s ease, transform 0.2s ease;
  position: relative;
  overflow: hidden;
}
/* 详情弹窗字段复制图标 */
.copy-icon {
  font-size: 16px;
  color: #909399;
  cursor: pointer;
  transition: color 0.2s;
}
.copy-icon:hover {
  color: #409eff;
}
.textarea-copy-wrap {
  position: relative;
  flex: 1;
}
.textarea-copy {
  position: absolute;
  right: 8px;
  top: 8px;
  z-index: 1;
}
.selected-bar {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px 10px;
  margin-top: 16px;
  padding: 12px 16px;
  background: #f5f7fa;
  border: 1px solid #e4e7ed;
  border-radius: 8px;
}
.selected-label {
  font-size: 14px;
  color: #606266;
  white-space: nowrap;
}
.expert-card.selected {
  border-color: #409eff;
  background: #ecf5ff;
  box-shadow: 0 0 0 2px rgba(64, 158, 255, 0.35);
}
.card-check {
  position: absolute;
  top: 10px;
  left: 10px;
  z-index: 1;
}
.card-meta {
  width: 100%;
  margin-top: auto;
  padding-top: 10px;
  border-top: 1px solid #ebeef5;
  display: flex;
  justify-content: center;
  align-items: center;
  flex-wrap: nowrap;
  gap: 10px;
  font-size: 14px;
  color: #909399;
  line-height: 1.4;
  white-space: nowrap;
}
.card-meta-item {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  min-width: 0;
}
.meta-icon {
  flex-shrink: 0;
}
.card-apply-btn {
  position: absolute;
  right: 10px;
  bottom: 6px;
  z-index: 1;
  font-size: 13px;
}
.expert-card:hover {
  box-shadow: 0 6px 18px rgba(0, 21, 41, 0.16);
  transform: translateY(-3px);
}
.card-photo {
  width: 128px;
  height: 128px;
  margin: 0 auto 14px;
  border-radius: 50%;
  overflow: hidden;
  border: 2px solid #e4e7ed;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #f5f7fa;
}
.card-name {
  min-width: 0;
  max-width: 100%;
  font-size: 20px;
  font-weight: 600;
  color: #303133;
  margin-bottom: 8px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.card-text {
  min-width: 0;
  max-width: 100%;
  font-size: 14px;
  color: #606266;
  line-height: 1.8;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.masked-photo {
  position: relative;
  display: inline-block;
}
.masked-img {
  display: block;
  width: 128px;
  height: 80px;
  object-fit: cover;
  border-radius: 4px;
  filter: blur(10px);
  cursor: default;
}
.masked-tip {
  position: absolute;
  left: 0;
  right: 0;
  top: 50%;
  transform: translateY(-50%);
  text-align: center;
  color: #909399;
  font-size: 12px;
  background: rgba(255, 255, 255, 0.7);
  padding: 2px 0;
}
.photo-empty {
  color: #a8abb2;
  font-size: 12px;
}
.edit-form {
  max-height: 62vh;
  overflow-y: auto;
  padding-right: 4px;
}
.edit-photos {
  display: flex;
  gap: 16px;
  margin-bottom: 18px;
}
.edit-photo-item {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  padding: 12px 8px;
  border: 1px dashed #dcdfe6;
  border-radius: 8px;
}
.edit-photo-label {
  font-size: 13px;
  color: #606266;
  margin-bottom: 2px;
}
.edit-photo-box {
  width: 120px;
  height: 84px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 4px;
  overflow: hidden;
  background: #f5f7fa;
}
.edit-photo-box.round {
  width: 80px;
  height: 80px;
  border-radius: 50%;
}
.edit-photo-img {
  max-width: 100%;
  max-height: 100%;
  object-fit: cover;
  border-radius: 4px;
}
</style>
