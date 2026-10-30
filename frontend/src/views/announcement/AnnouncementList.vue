<template>
  <div>
    <div class="page-head">
      <span class="page-title">公告</span>
      <span class="page-sub">系统通知与重要事项（点击查看详情，可标记已读）</span>
    </div>

    <div class="toolbar">
      <el-input
        v-model="filters.keyword"
        placeholder="搜索标题 / 内容"
        clearable
        style="width: 240px"
        @keyup.enter="handleSearch"
        @clear="handleSearch"
      />
      <el-button type="primary" style="margin-left: 8px" @click="handleSearch">搜索</el-button>
    </div>

    <div v-loading="loading" class="announcement-list">
      <el-card
        v-for="a in records"
        :key="a.id"
        shadow="hover"
        class="announcement-item"
        @click="goDetail(a.id)"
      >
        <div class="ann-item-main">
          <el-tag v-if="a.isTop === 1" type="danger" size="small" class="ann-top-tag">置顶</el-tag>
          <el-tag v-if="a.expired" type="info" size="small" class="ann-top-tag">已过期</el-tag>
          <span class="ann-title" :class="{ 'ann-title-read': a.read }">{{ a.title }}</span>
          <span v-if="a.read === false" class="ann-dot" title="未读"></span>
        </div>
        <div class="ann-item-meta">
          <span>{{ a.creatorName || '系统' }}</span>
          <span class="ann-time">{{ formatTime(a.createTime) }}</span>
          <span v-if="a.effectiveTime" class="ann-time">生效：{{ formatTime(a.effectiveTime) }}</span>
          <span v-if="a.expireTime" class="ann-time">失效：{{ formatTime(a.expireTime) }}</span>
        </div>
      </el-card>
      <el-empty v-if="!loading && !records.length" description="暂无公告" />
    </div>

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
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { listAnnouncements } from '../../api/announcement'

const router = useRouter()
const loading = ref(false)
const records = ref([])
const total = ref(0)
const filters = reactive({ keyword: '', page: 1, size: 10 })

function formatTime(t) {
  if (!t) return ''
  return String(t).replace('T', ' ').slice(0, 16)
}

async function loadList() {
  loading.value = true
  try {
    const data = await listAnnouncements({
      keyword: filters.keyword || undefined,
      page: filters.page,
      size: filters.size
    })
    records.value = data.items || []
    total.value = data.total || 0
  } finally {
    loading.value = false
  }
}

function handleSearch() {
  filters.page = 1
  loadList()
}

function goDetail(id) {
  router.push(`/profile/announcements/${id}`)
}

onMounted(loadList)
</script>

<style scoped>
.toolbar {
  margin-top: 12px;
  display: flex;
  flex-wrap: wrap;
  align-items: center;
}
.announcement-list {
  min-height: 120px;
}
.announcement-item {
  cursor: pointer;
  margin-bottom: 10px;
}
.ann-item-main {
  display: flex;
  align-items: center;
}
.ann-top-tag {
  margin-right: 8px;
}
.ann-title {
  font-size: 15px;
  font-weight: 600;
  color: #303133;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.ann-title-read {
  font-weight: 400;
  color: #909399;
}
.ann-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: #f56c6c;
  margin-left: 8px;
  flex-shrink: 0;
}
.ann-item-meta {
  margin-top: 6px;
  color: #909399;
  font-size: 12px;
  display: flex;
  flex-wrap: wrap;
  gap: 16px;
}
.pagination-wrap {
  margin-top: 12px;
  display: flex;
  justify-content: flex-end;
}
</style>
