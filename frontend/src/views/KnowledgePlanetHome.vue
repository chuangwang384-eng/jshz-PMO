<template>
  <div class="knowledge-planet-home">
    <!-- 上传中心 -->
    <el-card shadow="never" class="upload-card">
      <template #header>
        <span class="card-title">上传中心</span>
      </template>
      <el-form label-width="80px" class="upload-form">
        <el-form-item label="分类" required>
          <el-select v-model="uploadForm.category" placeholder="请选择分类" style="width: 320px">
            <el-option v-for="c in categoryOptions" :key="c.value" :label="c.label" :value="c.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="标题" required>
          <el-input v-model="uploadForm.title" placeholder="请输入文档标题" maxlength="200" style="width: 320px" />
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
        <el-form-item>
          <el-button type="primary" :loading="uploading" @click="submitUpload">上传文档</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 全分类文档总览 -->
    <el-card shadow="never" class="doc-card">
      <template #header>
        <span class="card-title">全部文档</span>
      </template>
      <div class="toolbar">
        <el-input
          v-model="keyword"
          placeholder="按标题 / 文件名检索"
          clearable
          class="search-input"
          @keyup.enter="handleSearch"
          @clear="handleSearch"
        />
      </div>
      <el-table :data="docs" v-loading="loading" style="width: 100%">
        <el-table-column prop="title" label="标题" min-width="220" show-overflow-tooltip />
        <el-table-column label="分类" width="150">
          <template #default="{ row }">{{ categoryName(row.category) }}</template>
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

    <!-- 更新弹窗 -->
    <el-dialog title="更新文档" v-model="updateVisible" width="520px">
      <el-form label-width="80px">
        <el-form-item label="标题">
          <el-input v-model="updateForm.title" placeholder="请输入文档标题" maxlength="200" />
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
        <el-button @click="updateVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitUpdate">确认</el-button>
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

const categoryOptions = [
  { label: '会议执行', value: 'meeting-execution' },
  { label: '协会合规要求', value: 'association-compliance' },
  { label: '企业合规要求', value: 'enterprise-compliance' },
  { label: '客户喜好', value: 'customer-preferences' }
]

const categoryLabels = {
  'meeting-execution': '会议执行',
  'association-compliance': '协会合规要求',
  'enterprise-compliance': '企业合规要求',
  'customer-preferences': '客户喜好'
}
function categoryName(value) {
  return categoryLabels[value] || value || '—'
}

const authStore = useAuthStore()
const canManage = computed(() => authStore.hasPermission('knowledge:manage'))

// 上传中心
const uploadForm = ref({ category: '', title: '' })
const pickedFile = ref(null)
const uploading = ref(false)

// 总览列表
const keyword = ref('')
const docs = ref([])
const total = ref(0)
const page = ref(1)
const pageSize = ref(20)
const loading = ref(false)

// 更新
const updateVisible = ref(false)
const updatingId = ref(null)
const updateForm = ref({ title: '' })
const submitting = ref(false)

// 版本历史
const versionVisible = ref(false)
const versionLoading = ref(false)
const versions = ref([])

function handleFileChange(file) {
  pickedFile.value = file.raw
}

function handleFileRemove() {
  pickedFile.value = null
}

async function submitUpload() {
  if (!uploadForm.value.category) {
    ElMessage.warning('请选择分类')
    return
  }
  if (!uploadForm.value.title.trim()) {
    ElMessage.warning('请输入文档标题')
    return
  }
  if (!pickedFile.value) {
    ElMessage.warning('请选择文件')
    return
  }
  uploading.value = true
  try {
    const formData = new FormData()
    formData.append('category', uploadForm.value.category)
    formData.append('title', uploadForm.value.title.trim())
    formData.append('file', pickedFile.value)
    await uploadKnowledgeDoc(formData)
    ElMessage.success('上传成功')
    uploadForm.value = { category: '', title: '' }
    pickedFile.value = null
    page.value = 1
    fetchList()
  } finally {
    uploading.value = false
  }
}

async function fetchList() {
  loading.value = true
  try {
    const data = await listKnowledgeDocs({
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

function openUpdate(row) {
  updatingId.value = row.id
  updateForm.value = { title: row.title }
  pickedFile.value = null
  updateVisible.value = true
}

async function submitUpdate() {
  if (!pickedFile.value) {
    ElMessage.warning('请选择文件')
    return
  }
  submitting.value = true
  try {
    const formData = new FormData()
    formData.append('title', updateForm.value.title.trim())
    formData.append('file', pickedFile.value)
    await updateKnowledgeDoc(updatingId.value, formData)
    ElMessage.success('更新成功')
    updateVisible.value = false
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
.knowledge-planet-home {
  padding: 4px;
}

.upload-card {
  margin-bottom: 16px;
}

.card-title {
  font-weight: 600;
}

.upload-form {
  max-width: 520px;
}

.toolbar {
  display: flex;
  gap: 12px;
  margin-bottom: 16px;
}

.search-input {
  max-width: 360px;
}

.table-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 12px;
}
</style>
