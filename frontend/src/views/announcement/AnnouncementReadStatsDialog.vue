<template>
  <el-dialog v-model="visible" title="已读进度与名单" width="720px">
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
</template>

<script setup>
import { ref, computed, watch } from 'vue'
import { getAnnouncementReadStats } from '../../api/announcement'

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  announcementId: { type: [Number, String], default: null }
})
const emit = defineEmits(['update:modelValue'])

const visible = computed(() => props.modelValue)
const stats = ref(null)

watch(
  () => props.modelValue,
  async (open) => {
    if (!open || props.announcementId == null) return
    stats.value = await getAnnouncementReadStats(props.announcementId)
  }
)

function formatTime(t) {
  if (!t) return ''
  return String(t).replace('T', ' ').slice(0, 16)
}
</script>

<style scoped>
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
