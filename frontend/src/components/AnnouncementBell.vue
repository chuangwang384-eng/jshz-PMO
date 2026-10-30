<template>
  <el-popover
    v-model:visible="popoverVisible"
    placement="bottom-end"
    :width="380"
    trigger="click"
    popper-class="announcement-popper"
    @show="handleShow"
  >
    <template #reference>
      <div class="bell-wrap" title="公告">
        <el-badge :value="unreadCount" :hidden="!unreadCount" :max="99" class="bell-badge">
          <el-icon :size="20" class="bell-icon"><Bell /></el-icon>
        </el-badge>
      </div>
    </template>

    <div class="ann-panel">
      <div class="ann-head">
        <span class="ann-head-title">系统公告</span>
      </div>

      <div v-if="!announcements.length" class="ann-empty">暂无公告</div>
      <div v-else class="ann-list">
        <div
          v-for="a in announcements"
          :key="a.id"
          class="ann-item"
          @click="handleClick(a)"
        >
          <div class="ann-title">
            <el-tag v-if="a.isTop === 1" type="danger" size="small">置顶</el-tag>
            <span class="ann-title-text">{{ a.title }}</span>
            <span v-if="a.read === false" class="ann-dot" title="未读"></span>
          </div>
          <div v-if="annBrief(a)" class="ann-brief">{{ annBrief(a) }}</div>
          <div class="ann-time">{{ formatTime(a.createTime) }}</div>
        </div>
      </div>

      <div class="ann-foot">
        <el-button link type="primary" size="small" @click="handleViewAll">查看全部 »</el-button>
      </div>
    </div>
  </el-popover>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { Bell } from '@element-plus/icons-vue'
import { getEffectiveAnnouncements, markAnnouncementRead } from '../api/announcement'
import { useAuthStore } from '../store/auth'

const router = useRouter()
const auth = useAuthStore()
const popoverVisible = ref(false)
const announcements = ref([])

const unreadCount = computed(() => announcements.value.filter((a) => a.read === false).length)

onMounted(() => {
  if (!auth.isLoggedIn) return
  refresh()
})

function formatTime(t) {
  if (!t) return ''
  return String(t).replace('T', ' ').slice(0, 16)
}

function annBrief(a) {
  if (!a || !a.content) return ''
  const div = document.createElement('div')
  div.innerHTML = a.content
  const text = (div.textContent || '').replace(/\s+/g, ' ').trim()
  return text.length > 80 ? text.slice(0, 80) + '…' : text
}

// 打开面板时拉取生效公告（后端保证置顶优先 + 发布时间倒序）
async function refresh() {
  try {
    announcements.value = (await getEffectiveAnnouncements(10)) || []
  } catch (e) {
    announcements.value = []
  }
}

async function handleShow() {
  await refresh()
}

// 点击公告：收起面板，标记已读后跳详情
async function handleClick(a) {
  popoverVisible.value = false
  try {
    await markAnnouncementRead(a.id)
  } catch (e) {
    // 标记已读失败不阻断跳转
  }
  router.push(`/profile/announcements/${a.id}`)
}

// 查看全部：收起面板并进入公告列表页
function handleViewAll() {
  popoverVisible.value = false
  router.push('/profile/announcements')
}
</script>

<style scoped>
.bell-wrap {
  display: flex;
  align-items: center;
  cursor: pointer;
  padding: 4px;
  border-radius: 4px;
}
.bell-wrap:hover {
  background: rgba(0, 0, 0, 0.04);
}
.bell-icon {
  color: #303133;
}
.bell-badge {
  display: flex;
  align-items: center;
}

.ann-panel {
  padding: 4px 0;
}
.ann-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 4px 12px 10px;
  border-bottom: 1px solid #ebeef5;
}
.ann-head-title {
  font-size: 14px;
  font-weight: 600;
  color: #303133;
}
.ann-empty {
  padding: 32px 0;
  text-align: center;
  color: #909399;
  font-size: 13px;
}
.ann-list {
  max-height: 360px;
  overflow-y: auto;
}
.ann-item {
  padding: 10px 12px;
  border-bottom: 1px solid #f2f4f7;
  cursor: pointer;
}
.ann-item:hover {
  background: #f5f7fa;
}
.ann-title {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  font-weight: 600;
  color: #303133;
}
.ann-dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: #f56c6c;
  flex-shrink: 0;
}
.ann-title-text {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  flex: 1;
}
.ann-brief {
  margin-top: 4px;
  font-size: 12px;
  color: #606266;
  line-height: 1.5;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
.ann-time {
  margin-top: 6px;
  font-size: 11px;
  color: #a8abb2;
}
.ann-foot {
  display: flex;
  justify-content: center;
  padding: 6px 0 2px;
  border-top: 1px solid #ebeef5;
}
</style>
