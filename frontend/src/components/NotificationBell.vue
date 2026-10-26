<template>
  <el-popover
    v-model:visible="popoverVisible"
    placement="bottom-end"
    :width="380"
    trigger="click"
    popper-class="notification-popper"
    @show="handleShow"
  >
    <template #reference>
      <div class="bell-wrap" title="消息通知">
        <el-badge :value="notification.unread" :hidden="!notification.unread" :max="99" class="bell-badge">
          <el-icon :size="20" class="bell-icon"><Bell /></el-icon>
        </el-badge>
      </div>
    </template>

    <div class="notify-panel">
      <div class="notify-head">
        <span class="notify-head-title">消息通知</span>
        <el-button link type="primary" size="small" :disabled="!notification.unread" @click="handleMarkAllRead">
          全部已读
        </el-button>
      </div>

      <div v-if="!notification.items.length" class="notify-empty">暂无消息</div>
      <div v-else class="notify-list">
        <div
          v-for="n in notification.items"
          :key="n.id"
          class="notify-item"
          :class="{ 'is-unread': n.isRead === 0 }"
          @click="handleClick(n)"
        >
          <div class="notify-title">
            <span v-if="n.isRead === 0" class="notify-dot"></span>
            <span class="notify-title-text">{{ n.title }}</span>
          </div>
          <div class="notify-content">{{ n.content || '' }}</div>
          <div class="notify-time">{{ n.createTime || '' }}</div>
        </div>
      </div>

      <div class="notify-foot">
        <el-button link type="primary" size="small" @click="handleViewAll">查看全部 »</el-button>
      </div>
    </div>
  </el-popover>
</template>

<script setup>
import { onMounted, onBeforeUnmount, ref } from 'vue'
import { useRouter } from 'vue-router'
import { Bell } from '@element-plus/icons-vue'
import { useNotificationStore } from '../store/notification'
import { useAuthStore } from '../store/auth'

const router = useRouter()
const notification = useNotificationStore()
const auth = useAuthStore()
// 弹层开关受控：跳转消息中心前主动收起，避免浮层盖在新页面上
const popoverVisible = ref(false)

onMounted(() => {
  if (!auth.isLoggedIn) return
  notification.init()
  notification.startPolling()
})

onBeforeUnmount(() => {
  notification.stopPolling()
})

async function handleShow() {
  // 打开面板时刷新列表并推进基线，避免对已查看消息重复弹提示
  await notification.refresh()
}

async function handleClick(n) {
  if (n.isRead === 0) {
    await notification.markRead(n.id)
  }
  if (n.jumpPath) {
    router.push(n.jumpPath)
  }
}

async function handleMarkAllRead() {
  await notification.markAllRead()
}

// 查看全部：收起弹层并进入消息中心页
function handleViewAll() {
  popoverVisible.value = false
  router.push('/profile/notifications')
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

.notify-panel {
  padding: 4px 0;
}
.notify-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 4px 12px 10px;
  border-bottom: 1px solid #ebeef5;
}
.notify-head-title {
  font-size: 14px;
  font-weight: 600;
  color: #303133;
}
.notify-empty {
  padding: 32px 0;
  text-align: center;
  color: #909399;
  font-size: 13px;
}
.notify-list {
  max-height: 360px;
  overflow-y: auto;
}
.notify-item {
  padding: 10px 12px;
  border-bottom: 1px solid #f2f4f7;
  cursor: pointer;
}
.notify-item:hover {
  background: #f5f7fa;
}
.notify-title {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  font-weight: 600;
  color: #303133;
}
.notify-dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: #f56c6c;
  flex-shrink: 0;
}
.notify-title-text {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.notify-content {
  margin-top: 4px;
  font-size: 12px;
  color: #606266;
  line-height: 1.5;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
.notify-time {
  margin-top: 6px;
  font-size: 11px;
  color: #a8abb2;
}
.notify-foot {
  display: flex;
  justify-content: center;
  padding: 6px 0 2px;
  border-top: 1px solid #ebeef5;
}
</style>
