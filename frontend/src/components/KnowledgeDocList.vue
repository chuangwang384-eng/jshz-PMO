<template>
  <div class="knowledge-doc-list">
    <!-- 格式筛选标签栏（分类视图；收藏视图为客户端跨分类筛选，不参与） -->
    <div v-if="!isFavoritesMode" class="format-filters">
      <el-radio-group v-model="fileTypeFilter" size="small" @change="handleFileTypeChange">
        <el-radio-button value="">全部</el-radio-button>
        <el-radio-button value="pdf">PDF</el-radio-button>
        <el-radio-button value="docx">DOCX</el-radio-button>
        <el-radio-button value="doc">DOC</el-radio-button>
        <el-radio-button value="xlsx">XLSX</el-radio-button>
        <el-radio-button value="pptx">PPTX</el-radio-button>
        <el-radio-button value="image">图片</el-radio-button>
      </el-radio-group>
    </div>

    <!-- 工具栏：检索 + 上传 -->
    <div class="toolbar">
      <el-input
        v-model="keyword"
        :placeholder="isFavoritesMode ? '在收藏中按标题 / 文件名筛选' : '按标题 / 内容关键字检索'"
        clearable
        class="search-input"
        @keyup.enter="handleSearch"
        @clear="handleSearch"
      />
      <el-button v-if="props.allowUpload && !isFavoritesMode" type="primary" @click="openUpload">上传文档</el-button>
    </div>

    <!-- 文档列表 -->
    <el-card shadow="never" class="doc-card">
      <el-table :data="displayDocs" v-loading="loading" style="width: 100%">
        <el-table-column label="标题" min-width="220">
          <template #default="{ row }">
            <span class="doc-title">{{ row.title }}</span>
            <span class="doc-ext">{{ extOf(row.fileType) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="格式" width="86" align="center">
          <template #default="{ row }">
            <span class="format-badge" :style="badgeStyle(row.fileType)">{{ badgeLabel(row.fileType) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="分类" width="150">
          <template #default="{ row }">{{ rowCategoryLabel(row) }}</template>
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
            <el-button v-if="canManage && !isFavoritesMode" link type="primary" size="small" @click="openUpdate(row)">更新</el-button>
            <el-button link type="info" size="small" v-if="canManage" @click="openVersions(row)">历史</el-button>
            <el-button link type="success" size="small" v-if="canManage && !isFavoritesMode && row.status === 0" @click="handleRelist(row)">上架</el-button>
            <el-button link type="danger" size="small" v-if="canManage && !isFavoritesMode" @click="handleOffShelf(row)">下架</el-button>
            <el-button link type="danger" size="small" v-if="canManage && !isFavoritesMode" @click="handlePermanentDelete(row)">永久删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div class="table-footer">
        <el-pagination
          v-if="!isFavoritesMode"
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
            accept=".pdf,.doc,.docx,.xls,.xlsx,.ppt,.pptx,.jpg,.jpeg,.png"
            :on-change="handleFileChange"
            :on-remove="handleFileRemove"
          >
            <el-button size="small" type="primary" plain>选择文件</el-button>
            <template #tip>
              <div class="el-upload__tip">支持 pdf/doc/docx/xls/xlsx/ppt/pptx/jpg/jpeg/png，单文件 ≤50MB</div>
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

    <!-- 内嵌预览抽屉：PDF/Office 用 pdf.js 渲染（Office 为后端转换的 PDF 流），图片直接 img 展示 -->
    <el-drawer v-model="previewVisible" size="72%" :title="previewTitle" destroy-on-close class="preview-drawer">
      <div class="preview-toolbar">
        <div v-if="previewMode === 'pdf'" class="preview-nav">
          <el-button-group>
            <el-button size="small" :disabled="previewPage <= 1" @click="previewPage -= 1">上一页</el-button>
            <el-button size="small" :disabled="previewPage >= previewNumPages" @click="previewPage += 1">下一页</el-button>
          </el-button-group>
          <span class="preview-info">{{ previewPage }} / {{ previewNumPages }}</span>
          <el-button size="small" @click="previewScale = Math.max(0.3, +(previewScale - 0.1).toFixed(1))">缩小</el-button>
          <span class="preview-info">{{ Math.round(previewScale * 100) }}%</span>
          <el-button size="small" @click="previewScale = Math.min(3, +(previewScale + 0.1).toFixed(1))">放大</el-button>
        </div>
        <el-button size="small" type="primary" @click="downloadPreviewRow">下载</el-button>
      </div>
      <div class="preview-body" v-loading="previewLoading">
        <img v-if="previewMode === 'image'" :src="previewImageUrl" class="preview-image" alt="文档预览" />
        <canvas v-else-if="previewMode === 'pdf'" ref="previewCanvasRef" class="preview-canvas"></canvas>
        <el-empty v-else description="该格式暂不支持在线预览，请下载查看" />
      </div>
    </el-drawer>
  </div>
</template>

<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import * as pdfjsLib from 'pdfjs-dist'
import pdfWorkerUrl from 'pdfjs-dist/build/pdf.worker.min.mjs?url'
import { useAuthStore } from '../store/auth'
import {
  listKnowledgeDocs,
  uploadKnowledgeDoc,
  updateKnowledgeDoc,
  offShelfKnowledgeDoc,
  permanentDeleteKnowledgeDoc,
  relistKnowledgeDoc,
  getKnowledgeVersions,
  fetchKnowledgeFile,
  listMyFavoriteDocs,
  favoriteKnowledgeDoc,
  unfavoriteKnowledgeDoc
} from '../api/knowledge'

// pdf.js worker 指向打包后的 worker 文件（Vite ?url 导入）
pdfjsLib.GlobalWorkerOptions.workerSrc = pdfWorkerUrl

const props = defineProps({
  category: { type: String, default: '' },
  categoryName: { type: String, default: '' },
  allowUpload: { type: Boolean, default: true },
  // C3：favorites = 我的收藏视图（跨分类，读接口 + 客户端筛选，仅保留 预览/下载/历史/取消收藏 操作）
  mode: { type: String, default: 'category' }
})

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

const authStore = useAuthStore()
const canManage = computed(() => authStore.hasPermission('knowledge:maintain'))
const isFavoritesMode = computed(() => props.mode === 'favorites')

const keyword = ref('')
const docs = ref([])
const total = ref(0)
const page = ref(1)
const pageSize = ref(20)
const loading = ref(false)

// 格式筛选：''=全部；pdf/docx/doc/xlsx/pptx=等值；image=图片集合
const fileTypeFilter = ref('')

// ==================== 格式展示辅助 ====================
const FORMAT_BADGES = {
  pdf: { label: 'PDF', color: '#f56c6c' },
  docx: { label: 'DOCX', color: '#409eff' },
  doc: { label: 'DOC', color: '#7a9cbb' },
  xlsx: { label: 'XLSX', color: '#e6a23c' },
  xls: { label: 'XLS', color: '#e6a23c' },
  pptx: { label: 'PPTX', color: '#b37feb' },
  ppt: { label: 'PPT', color: '#b37feb' },
  jpg: { label: '图片', color: '#67c23a' },
  jpeg: { label: '图片', color: '#67c23a' },
  png: { label: '图片', color: '#67c23a' }
}
function badgeLabel(type) {
  if (!type) return '未知'
  const t = String(type).toLowerCase()
  return FORMAT_BADGES[t]?.label || t.toUpperCase()
}
function badgeStyle(type) {
  const t = type ? String(type).toLowerCase() : ''
  const color = FORMAT_BADGES[t]?.color || '#909399'
  return { backgroundColor: color, borderColor: color, color: '#fff' }
}
function extOf(type) {
  return type ? '.' + type : ''
}
function isImageType(type) {
  return ['jpg', 'jpeg', 'png'].includes((type || '').toLowerCase())
}

// 收藏模式：客户端按标题/文件名筛选
const displayDocs = computed(() => {
  if (!isFavoritesMode.value) {
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

// 分类列：分类视图显示固定分类名，收藏视图显示各文档自身分类
function rowCategoryLabel(row) {
  if (isFavoritesMode.value) {
    return categoryLabels[row.category] || row.category || '—'
  }
  return props.categoryName
}

// C4：下载次数仅知识星球维护人（knowledge:maintain）可见，普通用户显示 —
function canViewCount(row) {
  return canManage.value
}

// C3：收藏 / 取消收藏（本地状态即时切换；收藏视图取消后从列表移除）
async function toggleFavorite(row) {
  try {
    if (row.isFavorite) {
      await unfavoriteKnowledgeDoc(row.id)
      row.isFavorite = false
      ElMessage.success('已取消收藏')
      if (isFavoritesMode.value) {
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
    if (isFavoritesMode.value) {
      // 我的收藏：一次性拉取（含 doc 与 favoriteTime），客户端筛选
      const data = await listMyFavoriteDocs()
      docs.value = (Array.isArray(data) ? data : []).map((item) => ({
        ...(item.doc || {}),
        favoriteTime: item.favoriteTime
      }))
      total.value = docs.value.length
    } else {
      const data = await listKnowledgeDocs({
        category: props.category || undefined,
        keyword: keyword.value || undefined,
        fileType: fileTypeFilter.value || undefined,
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

function handleFileTypeChange() {
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

// ==================== 内嵌预览抽屉（pdf.js / img / Office 后端 PDF 流） ====================
const previewVisible = ref(false)
const previewLoading = ref(false)
const previewMode = ref('') // image | pdf | unsupported
const previewTitle = ref('')
const previewRow = ref(null)
const previewImageUrl = ref('')
const previewBlobUrl = ref('')
const pdfDoc = ref(null)
const previewPage = ref(1)
const previewNumPages = ref(0)
const previewScale = ref(1)
const previewCanvasRef = ref(null)

async function handlePreview(row) {
  previewRow.value = row
  previewTitle.value = `${row.title}${extOf(row.fileType)}`
  previewMode.value = ''
  previewImageUrl.value = ''
  previewBlobUrl.value = ''
  pdfDoc.value = null
  previewPage.value = 1
  previewNumPages.value = 0
  previewScale.value = 1
  previewVisible.value = true
  previewLoading.value = true
  try {
    const response = await fetchKnowledgeFile(row.filePath, false)
    const blob = response.data
    if (isImageType(row.fileType)) {
      // 图片：直接 img 展示
      previewMode.value = 'image'
      previewImageUrl.value = URL.createObjectURL(blob)
    } else {
      // PDF 直接渲染；Office（doc/docx/xls/xlsx/ppt/pptx）后端已转换为 PDF 流，统一按 PDF 渲染
      previewMode.value = 'pdf'
      previewBlobUrl.value = URL.createObjectURL(blob)
      const doc = await pdfjsLib.getDocument({ url: previewBlobUrl.value }).promise
      pdfDoc.value = doc
      previewNumPages.value = doc.numPages
      await renderPdfPage()
    }
  } catch (e) {
    previewMode.value = 'unsupported'
    ElMessage.error('暂无法在线预览，请下载查看')
  } finally {
    previewLoading.value = false
  }
}

async function renderPdfPage() {
  const doc = pdfDoc.value
  const canvas = previewCanvasRef.value
  if (!doc || !canvas || !doc.numPages) return
  const page = await doc.getPage(previewPage.value)
  const viewport = page.getViewport({ scale: previewScale.value })
  const ctx = canvas.getContext('2d')
  const dpr = window.devicePixelRatio || 1
  canvas.width = Math.floor(viewport.width * dpr)
  canvas.height = Math.floor(viewport.height * dpr)
  ctx.setTransform(dpr, 0, 0, dpr, 0, 0)
  await page.render({ canvasContext: ctx, viewport }).promise
}

watch([previewPage, previewScale], () => {
  if (previewMode.value === 'pdf') {
    renderPdfPage()
  }
})

watch(previewVisible, (visible) => {
  if (!visible) {
    // 关闭抽屉：销毁 pdf.js 文档与 blob URL，避免泄漏
    if (pdfDoc.value) {
      try {
        pdfDoc.value.destroy()
      } catch (e) {
        // ignore
      }
      pdfDoc.value = null
    }
    if (previewImageUrl.value) {
      URL.revokeObjectURL(previewImageUrl.value)
      previewImageUrl.value = ''
    }
    if (previewBlobUrl.value) {
      URL.revokeObjectURL(previewBlobUrl.value)
      previewBlobUrl.value = ''
    }
    previewRow.value = null
    previewMode.value = ''
  }
})

function downloadPreviewRow() {
  if (previewRow.value) {
    handleDownload(previewRow.value)
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

// 上架：普通用户立即可见，收藏列表自动恢复，并通知全部收藏者
function handleRelist(row) {
  ElMessageBox.confirm(`确认上架文档「${row.title}」？上架后文档将对全员可见，收藏者将收到通知。`, '上架确认', {
    confirmButtonText: '确认上架',
    cancelButtonText: '取消',
    type: 'info'
  })
    .then(async () => {
      await relistKnowledgeDoc(row.id)
      ElMessage.success('已上架')
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

.format-filters {
  margin-bottom: 12px;
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

.doc-title {
  color: #303133;
}

.doc-ext {
  color: #a8abb2;
  font-size: 12px;
}

.format-badge {
  display: inline-block;
  min-width: 48px;
  padding: 2px 8px;
  border-radius: 4px;
  font-size: 12px;
  line-height: 18px;
  text-align: center;
  white-space: nowrap;
}

.table-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 12px;
}

/* 预览抽屉 */
.preview-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
}

.preview-nav {
  display: flex;
  align-items: center;
  gap: 12px;
}

.preview-info {
  font-size: 13px;
  color: #606266;
}

.preview-body {
  min-height: 400px;
  text-align: center;
  overflow: auto;
}

.preview-image {
  max-width: 100%;
}

.preview-canvas {
  display: inline-block;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.12);
}
</style>
