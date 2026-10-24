<template>
  <div class="knowledge-doc-list">
    <!-- 工具栏：检索 + 上传 -->
    <div class="toolbar">
      <el-input
        v-model="keyword"
        placeholder="按标题 / 文件名检索"
        clearable
        class="search-input"
        @keyup.enter="handleSearch"
        @clear="handleSearch"
      />
      <el-button v-if="props.allowUpload" type="primary" @click="openUpload">上传文档</el-button>
    </div>

    <!-- 文档列表 -->
    <el-card shadow="never" class="doc-card">
      <el-table :data="docs" v-loading="loading" style="width: 100%">
        <el-table-column prop="title" label="标题" min-width="220" show-overflow-tooltip />
        <el-table-column label="分类" width="150">
          <template #default>{{ categoryName }}</template>
        </el-table-column>
        <el-table-column prop="version" label="版本" width="80" align="center" />
        <el-table-column prop="uploadBy" label="上传人" width="110" />
        <el-table-column label="更新时间" width="170">
          <template #default="{ row }">{{ row.updateTime || '—' }}</template>
        </el-table-column>
        <el-table-column label="状态" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'danger'" size="small">
              {{ row.status === 1 ? '已上架' : '已下架' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="350" align="center">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="handlePreview(row)">预览</el-button>
            <el-button link type="primary" size="small" @click="handleDownload(row)">下载</el-button>
            <el-button link type="primary" size="small" @click="openUpdate(row)">更新</el-button>
            <el-button link type="info" size="small" @click="openVersions(row)">历史</el-button>
            <el-button link type="danger" size="small" v-if="canManage" @click="handleOffShelf(row)">下架</el-button>
            <el-button link type="danger" size="small" v-if="canManage" @click="handlePermanentDelete(row)">永久删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div class="table-footer">
        <el-pagination
          class="pagination"
          background
          layout="total, prev, pager, next, sizes"
          :total="total"
          :page-size="pageSize"
          :current-page="page"
          @current-change="handlePageChange"
          @size-change="handleSizeChange"
        />
      </div>
    </el-card>

    <!-- 上传 / 更新弹窗 -->
    <el-dialog :title="uploadMode === 'create' ? '上传文档' : '更新文档'" v-model="dialogVisible" width="520px">
      <el-form label-width="80px">
        <el-form-item label="分类" v-if="uploadMode === 'create'">
          <el-select v-model="uploadForm.category" style="width: 100%">
            <el-option v-for="c in categoryOptions" :key="c.value" :label="c.label" :value="c.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="标题">
          <el-input v-model="uploadForm.title" placeholder="请输入文档标题" maxlength="200" />
        </el-form-item>
        <el-form-item label="文件">
          <el-upload
            :auto-upload="false"
            :show-file-list="true"
            :limit="1"
            accept=".pdf,.doc,.docx,.jpg,.jpeg,.png"
            :on-change="handleFileChange"
            :on-remove="handleFileRemove"
          >
            <el-button size="small" type="primary" plain>选择文件</el-button>
            <template #tip>
              <div class="el-upload__tip">支持 pdf/doc/docx/jpg/jpeg/png，单文件 ≤50MB</div>
            </template>
          </el-upload>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitUpload">确认</el-button>
      </template>
    </el-dialog>

    <!-- 版本历史弹窗 -->
    <el-dialog title="版本历史" v-model="versionVisible" width="640px">
      <el-table :data="versions" size="small" v-loading="versionLoading">
        <el-table-column prop="version" label="版本" width="70" align="center" />
        <el-table-column prop="fileName" label="文件名" min-width="220" show-overflow-tooltip />
        <el-table-column label="大小" width="100">
          <template #default="{ row }">{{ formatSize(row.fileSize) }}</template>
        </el-table-column>
        <el-table-column prop="uploadBy" label="上传人" width="110" />
        <el-table-column prop="uploadTime" label="上传时间" width="170" />
      </el-table>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useAuthStore } from '../store/auth'
import {
  listKnowledgeDocs,
  uploadKnowledgeDoc,
  updateKnowledgeDoc,
  offShelfKnowledgeDoc,
  permanentDeleteKnowledgeDoc,
  getKnowledgeVersions,
  fetchKnowledgeFile
} from '../api/knowledge'

const props = defineProps({
  category: { type: String, required: true },
  categoryName: { type: String, required: true },
  allowUpload: { type: Boolean, default: true }
})

const categoryOptions = [
  { label: '会议执行', value: 'meeting-execution' },
  { label: '协会合规要求', value: 'association-compliance' },
  { label: '企业合规要求', value: 'enterprise-compliance' },
  { label: '客户喜好', value: 'customer-preferences' }
]

const authStore = useAuthStore()
const canManage = computed(() => authStore.hasPermission('knowledge:manage'))

const keyword = ref('')
const docs = ref([])
const total = ref(0)
const page = ref(1)
const pageSize = ref(20)
const loading = ref(false)

const dialogVisible = ref(false)
const uploadMode = ref('create') // create | update
const updatingId = ref(null)
const uploadForm = ref({ category: props.category, title: '' })
const pickedFile = ref(null)
const submitting = ref(false)

const versionVisible = ref(false)
const versionLoading = ref(false)
const versions = ref([])

async function fetchList() {
  loading.value = true
  try {
    const data = await listKnowledgeDocs({
      category: props.category,
      keyword: keyword.value || undefined,
      page: page.value,
      pageSize: pageSize.value
    })
    docs.value = data?.items || []
    total.value = data?.total || 0
  } finally {
    loading.value = false
  }
}

function handleSearch() {
  page.value = 1
  fetchList()
}

function handlePageChange(p) {
  page.value = p
  fetchList()
}

function handleSizeChange(s) {
  pageSize.value = s
  page.value = 1
  fetchList()
}

function openUpload() {
  uploadMode.value = 'create'
  updatingId.value = null
  uploadForm.value = { category: props.category, title: '' }
  pickedFile.value = null
  dialogVisible.value = true
}

function openUpdate(row) {
  uploadMode.value = 'update'
  updatingId.value = row.id
  uploadForm.value = { category: props.category, title: row.title }
  pickedFile.value = null
  dialogVisible.value = true
}

function handleFileChange(file) {
  pickedFile.value = file.raw
}

function handleFileRemove() {
  pickedFile.value = null
}

async function submitUpload() {
  if (!uploadForm.value.title.trim()) {
    ElMessage.warning('请输入文档标题')
    return
  }
  if (!pickedFile.value) {
    ElMessage.warning('请选择文件')
    return
  }
  submitting.value = true
  try {
    const formData = new FormData()
    formData.append('title', uploadForm.value.title.trim())
    formData.append('file', pickedFile.value)
    if (uploadMode.value === 'create') {
      formData.append('category', props.category)
      await uploadKnowledgeDoc(formData)
      ElMessage.success('上传成功')
    } else {
      await updateKnowledgeDoc(updatingId.value, formData)
      ElMessage.success('更新成功')
    }
    dialogVisible.value = false
    fetchList()
  } finally {
    submitting.value = false
  }
}

async function handlePreview(row) {
  try {
    const response = await fetchKnowledgeFile(row.filePath, false)
    const blob = response.data
    const url = URL.createObjectURL(blob)
    window.open(url, '_blank')
  } catch (e) {
    ElMessage.error('预览失败')
  }
}

async function handleDownload(row) {
  try {
    const response = await fetchKnowledgeFile(row.filePath, true)
    const blob = response.data
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = row.fileName
    document.body.appendChild(a)
    a.click()
    a.remove()
    URL.revokeObjectURL(url)
  } catch (e) {
    ElMessage.error('下载失败')
  }
}

function handleOffShelf(row) {
  ElMessageBox.confirm(`确认下架文档「${row.title}」？下架后文档不可再访问。`, '下架确认', {
    confirmButtonText: '确认下架',
    cancelButtonText: '取消',
    type: 'warning'
  })
    .then(async () => {
      await offShelfKnowledgeDoc(row.id)
      ElMessage.success('已下架')
      fetchList()
    })
    .catch(() => {})
}

// 永久删除：主记录 + 全部版本历史 + 磁盘文件一并删除，不可恢复
function handlePermanentDelete(row) {
  ElMessageBox.confirm(
    `确认永久删除文档「${row.title}」？该操作将删除全部版本记录与磁盘文件，且不可恢复。`,
    '永久删除确认',
    {
      confirmButtonText: '永久删除',
      cancelButtonText: '取消',
      type: 'error'
    }
  )
    .then(async () => {
      await permanentDeleteKnowledgeDoc(row.id)
      ElMessage.success('已永久删除')
      fetchList()
    })
    .catch(() => {})
}

function openVersions(row) {
  versionLoading.value = true
  versionVisible.value = true
  getKnowledgeVersions(row.id)
    .then((data) => {
      versions.value = data || []
    })
    .finally(() => {
      versionLoading.value = false
    })
}

function formatSize(bytes) {
  if (!bytes) return '—'
  if (bytes < 1024) return bytes + ' B'
  if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + ' KB'
  return (bytes / 1024 / 1024).toFixed(2) + ' MB'
}

onMounted(fetchList)
</script>

<style scoped>
.knowledge-doc-list {
  padding: 4px;
}

.toolbar {
  display: flex;
  gap: 12px;
  margin-bottom: 16px;
}

.search-input {
  max-width: 360px;
}

.doc-card :deep(.el-table__row) {
  cursor: default;
}

.table-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 12px;
}
</style>
