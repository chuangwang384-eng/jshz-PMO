<template>
  <div class="msg-bell-wrap" title="消息中心" @click="openDrawer">
    <el-badge :value="totalUnread" :hidden="!totalUnread" :max="99" class="msg-bell-badge">
      <el-icon :size="20" class="msg-bell-icon"><Bell /></el-icon>
    </el-badge>

    <el-drawer
      v-model="drawerVisible"
      direction="rtl"
      :size="drawerSize"
      destroy-on-close
      class="message-drawer"
    >
      <template #header>
        <div class="msg-drawer-header">
          <div class="msg-drawer-title-wrap">
            <span class="msg-drawer-title">消息中心</span>
            <el-tag v-if="totalUnread" type="danger" size="small" effect="light" class="msg-drawer-tag">
              {{ totalUnread }} 条未读
            </el-tag>
          </div>
          <el-button link type="primary" :disabled="!currentUnread" @click="handleMarkAllRead">
            全部已读
          </el-button>
        </div>
      </template>

      <div class="msg-drawer-body">
        <el-tabs v-model="activeTab" class="msg-tabs" @tab-change="handleTabChange">
          <!-- 全部：两路合并 -->
          <el-tab-pane label="全部" name="all">
            <div
              class="msg-scroll"
              v-infinite-scroll="loadMoreAll"
              :infinite-scroll-disabled="scrollDisabledAll"
              :infinite-scroll-distance="80"
            >
              <div v-if="!mergedItems.length && !allLoading" class="msg-empty">暂无消息</div>
              <template v-else>
                <div
                  v-for="item in mergedItems"
                  :key="item.key"
                  class="msg-item"
                  :class="{ 'is-unread': item.read === false || item.isRead === 0 }"
                  @click="handleItemClick(item)"
                >
                  <div class="msg-title">
                    <span v-if="item.source === 'ann' && item.read === false" class="msg-dot"></span>
                    <span v-else-if="item.source === 'notif' && item.isRead === 0" class="msg-dot"></span>
                    <el-tag v-if="item.source === 'notif' && item.type" size="small" effect="plain" class="msg-tag">
                      {{ typeLabel[item.type] || item.type }}
                    </el-tag>
                    <span class="msg-title-text">{{ item.title }}</span>
                  </div>
                  <div class="msg-time">{{ formatTime(item.createTime) }}</div>
                </div>
                <div v-if="allLoading" class="msg-scroll-tip">加载中...</div>
                <div v-if="allDone" class="msg-scroll-tip">没有更多了</div>
              </template>
              <div class="msg-foot">
                <el-button link type="primary" size="small" @click="handleViewAll('all')">查看全部 »</el-button>
              </div>
            </div>
          </el-tab-pane>

          <!-- 系统消息 -->
          <el-tab-pane label="系统消息" name="notif">
            <div
              class="msg-scroll"
              v-infinite-scroll="loadMoreNotif"
              :infinite-scroll-disabled="scrollDisabledNotif"
              :infinite-scroll-distance="80"
            >
              <div v-if="!notifItems.length && !notifLoading" class="msg-empty">暂无消息</div>
              <template v-else>
                <div
                  v-for="item in notifItems"
                  :key="item.key"
                  class="msg-item"
                  :class="{ 'is-unread': item.isRead === 0 }"
                  @click="handleItemClick(item)"
                >
                  <div class="msg-title">
                    <span v-if="item.isRead === 0" class="msg-dot"></span>
                    <el-tag v-if="item.type" size="small" effect="plain" class="msg-tag">
                      {{ typeLabel[item.type] || item.type }}
                    </el-tag>
                    <span class="msg-title-text">{{ item.title }}</span>
                  </div>
                  <div class="msg-time">{{ formatTime(item.createTime) }}</div>
                </div>
                <div v-if="notifLoading" class="msg-scroll-tip">加载中...</div>
                <div v-if="notifDone" class="msg-scroll-tip">没有更多了</div>
              </template>
              <div class="msg-foot">
                <el-button link type="primary" size="small" @click="handleViewAll('notif')">查看全部 »</el-button>
              </div>
            </div>
          </el-tab-pane>

          <!-- 公告 -->
          <el-tab-pane label="公告" name="ann">
            <div
              class="msg-scroll"
              v-infinite-scroll="loadMoreAnn"
              :infinite-scroll-disabled="scrollDisabledAnn"
              :infinite-scroll-distance="80"
            >
              <div v-if="!annItems.length && !annLoading" class="msg-empty">暂无公告</div>
              <template v-else>
                <div
                  v-for="item in annItems"
                  :key="item.key"
                  class="msg-item"
                  :class="{ 'is-unread': item.read === false }"
                  @click="handleItemClick(item)"
                >
                  <div class="msg-title">
                    <span v-if="item.read === false" class="msg-dot"></span>
                    <el-tag v-if="item.isTop === 1" type="danger" size="small" class="msg-tag">置顶</el-tag>
                    <span class="msg-title-text">{{ item.title }}</span>
                  </div>
                  <div class="msg-time">{{ formatTime(item.createTime) }}</div>
                </div>
                <div v-if="annLoading" class="msg-scroll-tip">加载中...</div>
                <div v-if="annDone" class="msg-scroll-tip">没有更多了</div>
              </template>
              <div class="msg-foot">
                <el-button link type="primary" size="small" @click="handleViewAll('ann')">查看全部 »</el-button>
              </div>
            </div>
          </el-tab-pane>
        </el-tabs>
      </div>
    </el-drawer>
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { Bell } from '@element-plus/icons-vue'
import { useNotificationStore } from '../store/notification'
import { useAuthStore } from '../store/auth'
import { listAnnouncements, markAnnouncementRead, markAllAnnouncementsRead } from '../api/announcement'
import { fetchNotifications } from '../api/notification'

const PAGE_SIZE = 20

const router = useRouter()
const notification = useNotificationStore()
const auth = useAuthStore()

// 抽屉与 Tab
const drawerVisible = ref(false)
const activeTab = ref('all')
const drawerSize = ref('420px')

// 系统消息分页状态
const notifItems = ref([])
const notifPage = ref(0)
const notifLoading = ref(false)
const notifDone = ref(false)

// 公告分页状态
const annItems = ref([])
const annPage = ref(0)
const annLoading = ref(false)
const annDone = ref(false)

// 全部 Tab：两路合并
const mergedItems = ref([])

// 类型标签映射（与消息中心页一致）
const typeLabel = {
  approval_result: '审批结果',
  approval_todo: '待审批',
  approval_withdrawn: '申请已撤回',
  approval_transferred: '审批转办',
  approval_assigned: '审批加签',
  role_changed: '角色调整',
  password_reset: '密码重置',
  doc_off_shelf: '文档下架',
  doc_deleted: '文档删除',
  doc_updated: '文档更新',
  doc_relisted: '文档上架'
}

// 总未读角标 = 系统消息未读 + 公告未读（均来自 store 轮询合成）
const totalUnread = computed(() => notification.unread + notification.announcementUnread)

// 当前 Tab 未读数（决定"全部已读"按钮可用性）
const currentUnread = computed(() => {
  if (activeTab.value === 'all') return totalUnread.value
  if (activeTab.value === 'notif') return notification.unread
  return notification.announcementUnread
})

// 全部 Tab 合并加载状态
const allLoading = computed(() => notifLoading.value || annLoading.value)
const allDone = computed(() => notifDone.value && annDone.value)
const scrollDisabledAll = computed(() => notifLoading.value || annLoading.value || allDone.value)
const scrollDisabledNotif = computed(() => notifLoading.value || notifDone.value)
const scrollDisabledAnn = computed(() => annLoading.value || annDone.value)

function formatTime(t) {
  if (!t) return ''
  return String(t).replace('T', ' ').slice(0, 16)
}

// 通用分页加载器：fetchPage(page) 拉一页数据，mapItem 生成带 key 的展示条目；
// 第 1 页整体替换，追加页按 key 去重；返回不足一页时置 done
async function loadPage(page, state, fetchPage, mapItem) {
  if (state.loading.value || state.done.value) return
  if (page < 1) page = 1
  state.loading.value = true
  try {
    const data = await fetchPage(page)
    const items = ((data && data.items) || []).map(mapItem)
    if (page === 1) {
      state.items.value = items
    } else {
      const map = new Map(state.items.value.map((n) => [n.key, n]))
      items.forEach((n) => map.set(n.key, n))
      state.items.value = [...map.values()]
    }
    state.page.value = page
    if (items.length < PAGE_SIZE) state.done.value = true
  } catch (e) {
    // 网络异常静默，允许滚动重试
  } finally {
    state.loading.value = false
    if (activeTab.value === 'all') mergeAll()
  }
}

// 加载系统消息一页（page>=1），追加时按 key 去重
function loadNotif(page) {
  return loadPage(
    page,
    { items: notifItems, page: notifPage, loading: notifLoading, done: notifDone },
    (p) => fetchNotifications({ page: p, pageSize: PAGE_SIZE }),
    (n) => ({ ...n, source: 'notif', key: `notif-${n.id}` })
  )
}

// 加载公告一页（scope=effective，生效中优先 + 时间倒序）
function loadAnn(page) {
  return loadPage(
    page,
    { items: annItems, page: annPage, loading: annLoading, done: annDone },
    (p) => listAnnouncements({ scope: 'effective', page: p, size: PAGE_SIZE }),
    (a) => ({ ...a, source: 'ann', key: `ann-${a.id}` })
  )
}

// 「全部」Tab 合并去重：key=type-id，createTime 倒序
// 直接复用源列表引用（不拷贝），保证「全部」Tab 内单条点击已读后，再次 mergeAll 不会回退已读状态
function mergeAll() {
  const map = new Map()
  for (const n of notifItems.value) map.set(n.key, n)
  for (const a of annItems.value) map.set(a.key, a)
  mergedItems.value = [...map.values()].sort((x, y) => {
    const tx = (x.createTime || '').replace('T', ' ')
    const ty = (y.createTime || '').replace('T', ' ')
    return ty.localeCompare(tx)
  })
}

// 滚动加载分派
function loadMoreAll() {
  if (!notifLoading.value && !notifDone.value) loadNotif(notifPage.value + 1)
  if (!annLoading.value && !annDone.value) loadAnn(annPage.value + 1)
}
function loadMoreNotif() {
  if (!notifLoading.value && !notifDone.value) loadNotif(notifPage.value + 1)
}
function loadMoreAnn() {
  if (!annLoading.value && !annDone.value) loadAnn(annPage.value + 1)
}

// 打开抽屉：按当前 Tab 强制刷新第 1 页，并同步角标
function openDrawer() {
  drawerVisible.value = true
  refreshCurrentTab()
  notification.loadUnread()
  notification.loadAnnouncementUnread()
}

function refreshCurrentTab() {
  if (activeTab.value === 'all') {
    notifPage.value = 0
    notifDone.value = false
    annPage.value = 0
    annDone.value = false
    mergedItems.value = []
    loadNotif(1)
    loadAnn(1)
  } else if (activeTab.value === 'notif') {
    notifPage.value = 0
    notifDone.value = false
    loadNotif(1)
  } else {
    annPage.value = 0
    annDone.value = false
    loadAnn(1)
  }
}

// 切换 Tab：列表为空时加载第 1 页，已有数据保持滚动位置不重复加载
function handleTabChange() {
  if (activeTab.value === 'all') {
    if (!mergedItems.value.length) refreshCurrentTab()
  } else if (activeTab.value === 'notif') {
    if (!notifItems.value.length) loadNotif(1)
  } else {
    if (!annItems.value.length) loadAnn(1)
  }
}

// 点击条目：公告标记已读并跳详情；系统消息标记已读并按 jumpPath 跳转
async function handleItemClick(item) {
  if (item.source === 'ann') {
    if (item.read === false) {
      try {
        await markAnnouncementRead(item.id)
        item.read = true
        notification.announcementUnread = Math.max(0, notification.announcementUnread - 1)
      } catch (e) {
        // 标记已读失败不阻断跳转
      }
    }
    router.push(`/profile/announcements/${item.id}`)
  } else {
    if (item.isRead === 0) {
      try {
        await notification.markRead(item.id)
        item.isRead = 1
      } catch (e) {
        // 标记已读失败不阻断跳转
      }
    }
    if (item.jumpPath) router.push(item.jumpPath)
  }
}

// 全部已读：按当前 Tab 分派（全部=两个 read-all 都调），并同步本地状态与角标
async function handleMarkAllRead() {
  try {
    if (activeTab.value === 'all') {
      await Promise.all([markAllAnnouncementsRead(), notification.markAllRead()])
      annItems.value.forEach((a) => { a.read = true })
      notifItems.value.forEach((n) => { n.isRead = 1 })
      notification.announcementUnread = 0
      notification.unread = 0
      mergeAll()
    } else if (activeTab.value === 'notif') {
      await notification.markAllRead()
      notifItems.value.forEach((n) => { n.isRead = 1 })
    } else {
      await markAllAnnouncementsRead()
      annItems.value.forEach((a) => { a.read = true })
      notification.announcementUnread = 0
    }
  } catch (e) {
    // 接口异常静默，角标由下轮轮询校正
  }
}

// 查看全部：跳独立列表页（「全部」Tab 固定跳系统消息列表页，与需求 S8 一致）
function handleViewAll(tab) {
  router.push(tab === 'ann' ? '/profile/announcements' : '/profile/notifications')
}

onMounted(() => {
  if (window.innerWidth < 480) {
    drawerSize.value = '90%'
  }
  if (auth.isLoggedIn) {
    notification.init()
    notification.startPolling()
  }
})

onBeforeUnmount(() => {
  notification.stopPolling()
})
</script>

<style scoped>
.msg-bell-wrap {
  display: flex;
  align-items: center;
  cursor: pointer;
  padding: 4px;
  border-radius: 4px;
}
.msg-bell-wrap:hover {
  background: rgba(0, 0, 0, 0.04);
}
.msg-bell-icon {
  color: #303133;
}
.msg-bell-badge {
  display: flex;
  align-items: center;
}

.msg-drawer-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  width: 100%;
}
.msg-drawer-title-wrap {
  display: flex;
  align-items: center;
  gap: 8px;
}
.msg-drawer-title {
  font-size: 16px;
  font-weight: 600;
  color: #303133;
}
.msg-drawer-tag {
  flex-shrink: 0;
}

.msg-drawer-body {
  display: flex;
  flex-direction: column;
  height: 100%;
}
.msg-tabs {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-height: 0;
}
.msg-tabs :deep(.el-tabs__header) {
  margin-bottom: 0;
}
.msg-tabs :deep(.el-tabs__content) {
  flex: 1;
  min-height: 0;
  overflow: hidden;
}
.msg-scroll {
  height: calc(100vh - 200px);
  overflow-y: auto;
}
.msg-empty {
  padding: 40px 0;
  text-align: center;
  color: #909399;
  font-size: 13px;
}
.msg-item {
  padding: 10px 12px;
  border-bottom: 1px solid #f2f4f7;
  cursor: pointer;
}
.msg-item:hover {
  background: #f5f7fa;
}
.msg-title {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  font-weight: 600;
  color: #303133;
}
.msg-dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: #f56c6c;
  flex-shrink: 0;
}
.msg-tag {
  flex-shrink: 0;
}
.msg-title-text {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  flex: 1;
}
.msg-time {
  margin-top: 6px;
  font-size: 11px;
  color: #a8abb2;
}
.msg-scroll-tip {
  padding: 10px 0;
  text-align: center;
  color: #909399;
  font-size: 12px;
}
.msg-foot {
  display: flex;
  justify-content: center;
  padding: 8px 0 4px;
  border-top: 1px solid #ebeef5;
  background: #fff;
}
</style>
