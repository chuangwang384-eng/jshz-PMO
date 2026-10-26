<template>
  <div class="knowledge-planet-home">
    <!-- 分类子页（/knowledge-planet/xxx）：渲染对应分类文档列表；父路由时为空 -->
    <router-view />

    <!-- 父页内容：上传中心 + 全部文档总览（仅 /knowledge-planet 展示） -->
    <template v-if="isRootPage">
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

    <!-- 全分类文档总览 / 我的收藏 -->
    <el-card shadow="never" class="doc-card">
      <template #header>
        <div class="card-header-row">
          <span class="card-title">{{ viewMode === 'all' ? '全部文档' : '我的收藏' }}</span>
          <el-radio-group v-model="viewMode" size="small" @change="handleViewChange">
            <el-radio-button value="all">全部文档</el-radio-button>
            <el-radio-button value="favorites">我的收藏</el-radio-button>
          </el-radio-group>
        </div>
      </template>
      <div class="toolbar">
        <el-input
          v-model="keyword"
          :placeholder="viewMode === 'all' ? '按标题 / 内容关键字检索' : '在收藏中按标题 / 文件名筛选'"
          clearable
          class="search-input"
          @keyup.enter="handleSearch"
          @clear="handleSearch"
        />
      </div>
      <el-table :data="displayDocs" v-loading="loading" style="width: 100%">
        <el-table-column prop="title" label="标题" min-width="220" show-overflow-tooltip />
        <el-table-column label="分类" width="150">
          <template #default="{ row }">{{ categoryName(row.category) }}</template>
        </el-table-column>
        <el-table-column prop="version" label="版本" width="80" align="center" />
        <el-table-column prop="uploadBy" label="上传人" width="110" />
        <el-table-column label="下载次数" width="90" align="center">
          <template #default="{ row }">
            {{ canViewCount(row) ? row.downloadCount ?? 0 : '—' }}
          </template>
        </el-table-column>
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
        <el-table-column label="操作" width="380" align="center">
          <template #default="{ row }">
            <el-button
              link
              :type="row.isFavorite ? 'warning' : 'primary'"
              size="small"
              @click="toggleFavorite(row)"
            >
              {{ row.isFavorite ? '取消收藏' : '收藏' }}
            </el-button>
            <el-button link type="primary" size="small" @click="handlePreview(row)">预览</el-button>
            <el-button link type="primary" size="small" @click="handleDownload(row)">下载</el-button>
            <el-button v-if="viewMode === 'all'" link type="primary" size="small" @click="openUpdate(row)">更新</el-button>
            <el-button link type="info" size="small" @click="openVersions(row)">历史</el-button>
            <el-button link type="danger" size="small" v-if="canManage && viewMode === 'all'" @click="handleOffShelf(row)">下架</el-button>
            <el-button link type="danger" size="small" v-if="canManage && viewMode === 'all'" @click="handlePermanentDelete(row)">永久删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div class="table-footer">
        <el-pagination
          v-if="viewMode === 'all'"
          class="pagination"
          background
          layout="total, prev, pager, next, sizes"
          :total="total"
          :page-size="pageSize"
          :current-page="page"
          @current-change="handlePageChange"
          @size-change="handleSizeChange"
        />
        <span v-else class="favorites-total">共 {{ displayDocs.length }} 篇收藏</span>
      </div>
    </el-card>
    </template>

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
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useAuthStore } from '../store/auth'
import {
  listKnowledgeDocs,
  uploadKnowledgeDoc,
  updateKnowledgeDoc,
  offShelfKnowledgeDoc,
  permanentDeleteKnowledgeDoc,
  getKnowledgeVersions,
  fetchKnowledgeFile,
  listMyFavoriteDocs,
  favoriteKnowledgeDoc,
  unfavoriteKnowledgeDoc
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

const route = useRoute()
const authStore = useAuthStore()
const canManage = computed(() => authStore.hasPermission('knowledge:manage'))
// 仅父路由 /knowledge-planet 展示上传中心 + 全部文档总览；分类子页只渲染 router-view 内的分类列表
const isRootPage = computed(() => route.name === 'knowledge-planet')

// 上传中心
const uploadForm = ref({ category: '', title: '' })
const pickedFile = ref(null)
const uploading = ref(false)

// 总览列表 / 我的收藏
const viewMode = ref('all') // all | favorites
const keyword = ref('')
const docs = ref([])
const total = ref(0)
const page = ref(1)
const pageSize = ref(20)
const loading = ref(false)

// 收藏视图：客户端按标题/文件名筛选
const displayDocs = computed(() => {
  if (viewMode.value !== 'favorites') {
    return docs.value
  }
  const kw = keyword.value.trim().toLowerCase()
  if (!kw) return docs.value
  return docs.value.filter(
    (d) =>
      (d.title || '').toLowerCase().includes(kw) ||
      (d.fileName || '').toLowerCase().includes(kw)
  )
})

function handleViewChange() {
  keyword.value = ''
  page.value = 1
  fetchList()
}

// C4：下载次数对 上传人本人 / 管理员（knowledge:manage）可见
function canViewCount(row) {
  return canManage.value || row.uploadBy === authStore.username
}

// C3：收藏 / 取消收藏（本地状态即时切换；收藏视图取消后从列表移除）
async function toggleFavorite(row) {
  try {
    if (row.isFavorite) {
      await unfavoriteKnowledgeDoc(row.id)
      row.isFavorite = false
      ElMessage.success('已取消收藏')
      if (viewMode.value === 'favorites') {
        docs.value = docs.value.filter((d) => d.id !== row.id)
      }
    } else {
      await favoriteKnowledgeDoc(row.id)
      row.isFavorite = true
      ElMessage.success('已收藏，文档更新时将通知您')
    }
  } catch (e) {
    // 错误提示已由拦截器统一处理
  }
}

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
    if (viewMode.value === 'favorites') {
      // 我的收藏：一次性拉取（含 doc 与 favoriteTime），客户端筛选
      const data = await listMyFavoriteDocs()
      docs.value = (Array.isArray(data) ? data : []).map((item) => ({
        ...(item.doc || {}),
        favoriteTime: item.favoriteTime
      }))
      total.value = docs.value.length
    } else {
      const data = await listKnowledgeDocs({
        keyword: keyword.value || undefined,
        page: page.value,
        pageSize: pageSize.value
      })
      docs.value = data?.items || []
      total.value = data?.total || 0
    }
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

.card-header-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.favorites-total {
  font-size: 13px;
  color: #909399;
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
