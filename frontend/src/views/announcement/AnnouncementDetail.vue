<template>
  <div v-loading="loading">
    <div class="page-head">
      <el-button style="margin-left: 12px" @click="router.back()">返回</el-button>
    </div>

    <el-card v-if="detail" shadow="never" class="detail-card">
      <div class="detail-title-row">
        <el-tag v-if="detail.isTop === 1" type="danger" size="small">置顶</el-tag>
        <el-tag v-if="detail.expired" type="info" size="small" style="margin-left: 6px">已过期</el-tag>
        <el-tag
          v-if="detail.status === 'OFFLINE'"
          type="warning"
          size="small"
          style="margin-left: 6px"
        >未上架</el-tag>
        <h2 class="detail-title">{{ detail.title }}</h2>
      </div>
      <div class="detail-meta">
        <span>发布：{{ formatTime(detail.createTime) }}（{{ detail.creatorName || '系统' }}）</span>
        <span v-if="detail.effectiveTime" style="margin-left: 16px">生效：{{ formatTime(detail.effectiveTime) }}</span>
        <span v-if="detail.expireTime" style="margin-left: 16px">失效：{{ formatTime(detail.expireTime) }}</span>
      </div>
      <div class="detail-content">
        <!-- 富文本正文：后端已做 XSS 白名单清洗后入库 -->
        <div v-html="detail.content"></div>
      </div>
      <div class="detail-footer">
        <el-button
          v-if="!detail.read"
          type="primary"
          plain
          :loading="marking"
          @click="handleMarkRead"
        >标记已读</el-button>
        <el-tag v-else type="success" size="small">已读</el-tag>
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { dashboardFocus } from '../../api/dashboard'
import { ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { getAnnouncement, markAnnouncementRead } from '../../api/announcement'

const route = useRoute()
const router = useRouter()
const loading = ref(false)
const marking = ref(false)
const detail = ref(null)

function formatTime(t) {
  if (!t) return ''
  return String(t).replace('T', ' ').slice(0, 16)
}

async function loadDetail() {
  loading.value = true
  try {
    detail.value = route.query.from === 'dashboard' ? await dashboardFocus('announcement', route.params.id) : await getAnnouncement(route.params.id)
    if(route.query.from === 'dashboard' && !detail.value.read) {
      try { await markAnnouncementRead(detail.value.id); detail.value.read = true }
      catch { ElMessage.warning('公告已打开，但标记已读失败，请重试') }
    }
  } finally {
    loading.value = false
  }
}

async function handleMarkRead() {
  marking.value = true
  try {
    await markAnnouncementRead(detail.value.id)
    detail.value.read = true
    ElMessage.success('已标记为已读')
  } finally {
    marking.value = false
  }
}

onMounted(loadDetail)
</script>

<style scoped>
.detail-card {
  max-width: 960px;
}
.detail-title-row {
  display: flex;
  align-items: flex-start;
  gap: 4px;
}
.detail-title {
  margin: 0 0 0 4px;
  font-size: 20px;
  color: #303133;
}
.detail-meta {
  margin: 10px 0 16px;
  color: #909399;
  font-size: 13px;
}
.detail-content {
  line-height: 1.8;
  color: #303133;
  word-break: break-word;
}
.detail-content :deep(img) {
  max-width: 100%;
}
.detail-footer {
  margin-top: 24px;
}
</style>
