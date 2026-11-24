import { defineStore } from 'pinia'
import { ElNotification } from 'element-plus'
import {
  fetchNotifications,
  fetchUnreadCount,
  markNotificationRead,
  markAllNotificationsRead,
  readNotificationsBatch,
  deleteNotification,
  deleteNotificationsBatch
} from '../api/notification'
import { getAnnouncementUnreadCount } from '../api/announcement'

// 消息通知中心状态管理：
// - unread：系统消息未读数（顶栏铃铛红点）
// - announcementUnread：生效中公告未读数（与 unread 合成总角标）
// - items：消息列表（铃铛面板展示最近 20 条）
// - 30 秒轮询：startPolling 启动后周期拉取未读数与列表，检测新增消息并弹轻提示
// - 轻提示仅对"轮询期间新增且用户尚未查看"的消息触发一次
export const useNotificationStore = defineStore('notification', {
  state: () => ({
    unread: 0,
    announcementUnread: 0,
    items: [],
    total: 0,
    pollTimer: null,
    pollInterval: 30000,
    // 已见过的消息 id 基线：初始加载 / 手动打开面板 / 弹提示后都会推进基线，避免重复提示
    baselineIds: [],
    // 最近一次轮询完成时间戳：供页面（如「我的申请」）跟随全局轮询刷新，避免各页面自建定时器（2026-11-24）
    lastPollAt: 0
  }),
  actions: {
    async loadUnread() {
      try {
        this.unread = await fetchUnreadCount()
      } catch (e) {
        // 网络异常静默，轮询下轮重试
      }
    },
    async loadAnnouncementUnread() {
      try {
        this.announcementUnread = await getAnnouncementUnreadCount()
      } catch (e) {
        // 网络异常静默，轮询下轮重试
      }
    },
    async loadList({ page = 1, pageSize = 20 } = {}) {
      try {
        const data = await fetchNotifications({ page, pageSize })
        this.items = data?.items || []
        this.total = data?.total || 0
      } catch (e) {
        // 网络异常静默
      }
    },
    // 初始化：拉系统消息/公告未读数 + 列表，并把当前首屏消息设为基线
    async init() {
      await Promise.all([this.loadUnread(), this.loadAnnouncementUnread()])
      await this.loadList({ page: 1, pageSize: 20 })
      this.baselineIds = this.items.map((i) => i.id)
    },
    // 轮询一次：刷新未读数与列表，检测新增消息弹轻提示
    async poll() {
      const before = new Set(this.baselineIds)
      await Promise.all([this.loadUnread(), this.loadAnnouncementUnread()])
      await this.loadList({ page: 1, pageSize: 20 })
      const fresh = this.items.filter((i) => !before.has(i.id))
      if (fresh.length) {
        const latest = fresh[0]
        ElNotification({
          title: latest.title,
          message: latest.content || '',
          type: 'info',
          duration: 4500,
          position: 'top-right'
        })
        this.baselineIds = this.items.map((i) => i.id)
      }
      this.lastPollAt = Date.now()
    },
    startPolling() {
      if (this.pollTimer) return
      this.pollTimer = setInterval(() => {
        this.poll()
      }, this.pollInterval)
    },
    stopPolling() {
      if (this.pollTimer) {
        clearInterval(this.pollTimer)
        this.pollTimer = null
      }
    },
    // 打开面板时刷新列表并推进基线（用户已查看，后续不再对当前列表弹提示）
    async refresh() {
      await this.loadList({ page: 1, pageSize: 20 })
      this.baselineIds = this.items.map((i) => i.id)
      await Promise.all([this.loadUnread(), this.loadAnnouncementUnread()])
    },
    async markRead(id) {
      await markNotificationRead(id)
      const item = this.items.find((i) => i.id === id)
      if (item) item.isRead = 1
      this.unread = Math.max(0, this.unread - 1)
    },
    async markAllRead() {
      await markAllNotificationsRead()
      this.items.forEach((i) => {
        i.isRead = 1
      })
      this.unread = 0
    },
    // 批量已读：返回实际置为已读的条数，未读数以服务端为准刷新
    async readBatch(ids) {
      const count = (await readNotificationsBatch(ids)) || 0
      ids.forEach((id) => {
        const item = this.items.find((i) => i.id === id)
        if (item) item.isRead = 1
      })
      await this.loadUnread()
      return count
    },
    // 单条删除：物理删除，未读数以服务端为准刷新
    async removeOne(id) {
      await deleteNotification(id)
      this.items = this.items.filter((i) => i.id !== id)
      await this.loadUnread()
    },
    // 批量删除：物理删除，返回实际删除条数
    async removeBatch(ids) {
      const count = (await deleteNotificationsBatch(ids)) || 0
      this.items = this.items.filter((i) => !ids.includes(i.id))
      await this.loadUnread()
      return count
    }
  }
})
