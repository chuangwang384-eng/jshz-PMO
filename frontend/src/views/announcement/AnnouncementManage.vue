<template>
  <div>
    <div class="page-head">
      <span class="page-title">公告管理</span>
      <span class="page-sub">发布 / 编辑 / 删除 / 上下架 / 置顶公告，查看已读进度与名单</span>
    </div>

    <div class="toolbar">
      <el-input
        v-model="filters.keyword"
        placeholder="搜索标题 / 内容"
        clearable
        style="width: 220px"
        @keyup.enter="handleSearch"
        @clear="handleSearch"
      />
      <el-select v-model="filters.status" placeholder="全部状态" clearable style="width: 140px; margin-left: 8px" @change="handleSearch">
        <el-option label="上架" value="ONLINE" />
        <el-option label="下架" value="OFFLINE" />
      </el-select>
      <el-button type="primary" style="margin-left: 8px" @click="handleSearch">搜索</el-button>
      <el-button type="success" style="margin-left: 8px" @click="openCreate">新建公告</el-button>
    </div>

    <el-table v-loading="loading" :data="records" border stripe style="width: 100%; margin-top: 4px">
      <el-table-column label="标题" min-width="260">
        <template #default="{ row }">
          <span>{{ row.title }}</span>
          <el-tag v-if="row.isTop === 1" type="danger" size="small" style="margin-left: 6px">置顶</el-tag>
          <el-tag v-if="row.expired" type="info" size="small" style="margin-left: 6px">已过期</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="状态" width="80" align="center">
        <template #default="{ row }">
          <el-tag :type="row.status === 'ONLINE' ? 'success' : 'info'">{{ row.status === 'ONLINE' ? '上架' : '下架' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="生效时间" width="150">
        <template #default="{ row }">{{ formatTime(row.effectiveTime) }}</template>
      </el-table-column>
      <el-table-column label="失效时间" width="150">
        <template #default="{ row }">{{ row.expireTime ? formatTime(row.expireTime) : '永久' }}</template>
      </el-table-column>
      <el-table-column label="已读进度" width="120" align="center">
        <template #default="{ row }">
          <el-button v-if="row.totalCount > 0" link type="primary" @click="openStats(row)">{{ row.readCount }}/{{ row.totalCount }}</el-button>
          <span v-else>-</span>
        </template>
      </el-table-column>
      <el-table-column label="创建人" width="100">
        <template #default="{ row }">{{ row.creatorName || '-' }}</template>
      </el-table-column>
      <el-table-column label="操作" width="320" align="center" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="goDetail(row.id)">查看</el-button>
          <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
          <el-button link type="warning" @click="toggleTop(row)">{{ row.isTop === 1 ? '取消置顶' : '置顶' }}</el-button>
          <el-button link :type="row.status === 'ONLINE' ? 'danger' : 'success'" @click="toggleStatus(row)">
            {{ row.status === 'ONLINE' ? '下架' : '上架' }}
          </el-button>
          <el-popconfirm title="确认删除该公告？（已读记录同步清理）" @confirm="handleDelete(row)">
            <template #reference>
              <el-button link type="danger">删除</el-button>
            </template>
          </el-popconfirm>
        </template>
      </el-table-column>
    </el-table>

    <div class="pagination-wrap">
      <el-pagination
        v-model:current-page="filters.page"
        v-model:page-size="filters.size"
        :total="total"
        :page-sizes="[10, 20, 50]"
        layout="total, sizes, prev, pager, next, jumper"
        @size-change="loadList"
        @current-change="loadList"
      />
    </div>

    <!-- 新建 / 编辑 -->
    <el-dialog
      v-model="formVisible"
      :title="editingId ? '编辑公告' : '新建公告'"
      width="820px"
      destroy-on-close
      @closed="resetForm"
    >
      <el-form label-width="90px">
        <el-form-item label="标题" required>
          <el-input v-model="form.title" maxlength="120" placeholder="请输入公告标题" />
        </el-form-item>
        <el-form-item label="正文" required>
          <div class="editor-wrap">
            <Toolbar :editor="editorRef" :default-config="toolbarConfig" mode="default" class="editor-toolbar" />
            <Editor
              v-model="form.content"
              :default-config="editorConfig"
              mode="default"
              class="editor-body"
              @on-created="handleCreated"
            />
          </div>
        </el-form-item>
        <el-row>
          <el-col :span="12">
            <el-form-item label="生效时间">
              <el-date-picker
                v-model="form.effectiveTime"
                type="datetime"
                value-format="YYYY-MM-DDTHH:mm:ss"
                placeholder="默认立即生效"
                style="width: 100%"
              />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="失效时间">
              <el-date-picker
                v-model="form.expireTime"
                type="datetime"
                value-format="YYYY-MM-DDTHH:mm:ss"
                placeholder="留空 = 永久有效"
                style="width: 100%"
              />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row>
          <el-col :span="12">
            <el-form-item label="置顶">
              <el-switch v-model="form.isTop" :active-value="1" :inactive-value="0" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="状态">
              <el-radio-group v-model="form.status">
                <el-radio value="ONLINE">上架</el-radio>
                <el-radio value="OFFLINE">下架</el-radio>
              </el-radio-group>
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>
      <template #footer>
        <el-button @click="formVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="confirmSave">保存</el-button>
      </template>
    </el-dialog>

    <!-- 已读名单 -->
    <el-dialog v-model="statsVisible" title="已读进度与名单" width="720px">
      <template v-if="stats">
        <el-progress
          :percentage="stats.totalCount ? Math.round((stats.readCount / stats.totalCount) * 100) : 0"
          :format="() => `${stats.readCount}/${stats.totalCount} 人已读`"
          style="margin-bottom: 12px"
        />
        <el-row :gutter="12">
          <el-col :span="12">
            <div class="stats-title">已读（{{ stats.readUsers.length }}）</div>
            <div class="stats-list">
              <el-tag v-for="u in stats.readUsers" :key="u.userId" size="small" style="margin: 3px">
                {{ u.username }}<span class="stats-time">{{ formatTime(u.readTime) }}</span>
              </el-tag>
              <el-empty v-if="!stats.readUsers.length" :image-size="60" description="暂无" />
            </div>
          </el-col>
          <el-col :span="12">
            <div class="stats-title">未读（{{ stats.unreadUsers.length }}）</div>
            <div class="stats-list">
              <el-tag v-for="u in stats.unreadUsers" :key="u.userId" size="small" type="info" style="margin: 3px">
                {{ u.username }}
              </el-tag>
              <el-empty v-if="!stats.unreadUsers.length" :image-size="60" description="暂无" />
            </div>
          </el-col>
        </el-row>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, shallowRef, onBeforeUnmount, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import '@wangeditor/editor/dist/css/style.css'
import { Editor, Toolbar } from '@wangeditor/editor-for-vue'
import {
  listAnnouncements,
  createAnnouncement,
  updateAnnouncement,
  deleteAnnouncement,
  changeAnnouncementStatus,
  toggleAnnouncementTop,
  getAnnouncementReadStats,
  uploadRichtextImage
} from '../../api/announcement'

const router = useRouter()
const loading = ref(false)
const saving = ref(false)
const records = ref([])
const total = ref(0)
const filters = reactive({ keyword: '', status: '', page: 1, size: 10 })

const formVisible = ref(false)
const editingId = ref(null)
const form = reactive({ title: '', content: '', effectiveTime: '', expireTime: '', isTop: 0, status: 'ONLINE' })

const statsVisible = ref(false)
const stats = ref(null)

// wangEditor 实例（shallowRef，避免深层响应开销）
const editorRef = shallowRef()
const toolbarConfig = {}
const editorConfig = {
  placeholder: '请输入公告正文…',
  MENU_CONF: {
    uploadImage: {
      async customUpload(resultFiles, insertFn) {
        const file = resultFiles[0]
        const res = await uploadRichtextImage(file)
        insertFn(res.url, file.name || '', '')
      }
    }
  }
}

function handleCreated(editor) {
  editorRef.value = editor
}

function formatTime(t) {
  if (!t) return ''
  return String(t).replace('T', ' ').slice(0, 16)
}

async function loadList() {
  loading.value = true
  try {
    const data = await listAnnouncements({
      keyword: filters.keyword || undefined,
      status: filters.status || undefined,
      page: filters.page,
      size: filters.size
    })
    records.value = data.records || []
    total.value = data.total || 0
  } finally {
    loading.value = false
  }
}

function handleSearch() {
  filters.page = 1
  loadList()
}

function resetForm() {
  editingId.value = null
  Object.assign(form, { title: '', content: '', effectiveTime: '', expireTime: '', isTop: 0, status: 'ONLINE' })
}

function openCreate() {
  resetForm()
  formVisible.value = true
}

function openEdit(row) {
  editingId.value = row.id
  Object.assign(form, {
    title: row.title,
    content: row.content,
    effectiveTime: row.effectiveTime || '',
    expireTime: row.expireTime || '',
    isTop: row.isTop,
    status: row.status
  })
  formVisible.value = true
}

async function confirmSave() {
  if (!form.title || !form.title.trim()) {
    ElMessage.warning('请输入标题')
    return
  }
  if (!form.content || !form.content.trim()) {
    ElMessage.warning('请输入正文内容')
    return
  }
  saving.value = true
  try {
    const payload = {
      title: form.title.trim(),
      content: form.content,
      effectiveTime: form.effectiveTime || null,
      expireTime: form.expireTime || null,
      isTop: form.isTop,
      status: form.status
    }
    if (editingId.value) {
      await updateAnnouncement(editingId.value, payload)
      ElMessage.success('公告已更新')
    } else {
      await createAnnouncement(payload)
      ElMessage.success('公告已发布')
    }
    formVisible.value = false
    loadList()
  } finally {
    saving.value = false
  }
}

async function toggleTop(row) {
  try {
    await toggleAnnouncementTop(row.id, row.isTop === 1)
    ElMessage.success(row.isTop === 1 ? '已取消置顶' : '已置顶')
    loadList()
  } catch (e) {
    // 错误提示由 request 拦截器统一处理
  }
}

async function toggleStatus(row) {
  const next = row.status === 'ONLINE' ? 'OFFLINE' : 'ONLINE'
  try {
    await changeAnnouncementStatus(row.id, next)
    ElMessage.success(next === 'ONLINE' ? '已上架' : '已下架')
    loadList()
  } catch (e) {
    // 错误提示由 request 拦截器统一处理
  }
}

async function handleDelete(row) {
  try {
    await deleteAnnouncement(row.id)
    ElMessage.success('公告已删除')
    loadList()
  } catch (e) {
    // 错误提示由 request 拦截器统一处理
  }
}

async function openStats(row) {
  stats.value = await getAnnouncementReadStats(row.id)
  statsVisible.value = true
}

function goDetail(id) {
  router.push(`/announcements/${id}`)
}

onMounted(loadList)

onBeforeUnmount(() => {
  if (editorRef.value) {
    editorRef.value.destroy()
  }
})
</script>

<style scoped>
.editor-wrap {
  width: 100%;
  border: 1px solid #dcdfe6;
  border-radius: 4px;
}
.editor-toolbar {
  border-bottom: 1px solid #dcdfe6;
}
.editor-body {
  height: 320px;
  overflow-y: hidden;
}
.pagination-wrap {
  margin-top: 12px;
  display: flex;
  justify-content: flex-end;
}
.stats-title {
  font-weight: 600;
  margin-bottom: 8px;
  color: #303133;
}
.stats-list {
  min-height: 80px;
  max-height: 280px;
  overflow-y: auto;
  border: 1px solid #ebeef5;
  border-radius: 4px;
  padding: 6px;
}
.stats-time {
  margin-left: 4px;
  color: #909399;
}
</style>
