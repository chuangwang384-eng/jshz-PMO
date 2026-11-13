<template>
  <el-card shadow="never" class="profile-card">
    <el-descriptions :column="1" border class="profile-descriptions">
      <el-descriptions-item label="用户名">
        <span class="username-text">{{ auth.username || '-' }}</span>
      </el-descriptions-item>
      <el-descriptions-item label="角色">
        <template v-if="displayRoleNames.length">
          <el-tag
            v-for="(role, i) in displayRoleNames"
            :key="i"
            size="small"
            class="role-tag"
            type="primary"
            effect="light"
          >{{ role }}</el-tag>
        </template>
        <span v-else>-</span>
      </el-descriptions-item>
      <el-descriptions-item label="账号ID">
        {{ auth.id ?? '-' }}
      </el-descriptions-item>
      <el-descriptions-item label="注册时间">
        {{ formatTime(auth.createTime) }}
      </el-descriptions-item>
    </el-descriptions>

    <div class="profile-actions">
      <el-button type="primary" @click="showChangePwd = true">修改密码</el-button>
    </div>

    <!-- 修改密码弹窗（复用 ChangePasswordDialog；普通模式：可关闭、无强制改密警示） -->
    <ChangePasswordDialog
      v-model="showChangePwd"
      title="修改密码"
      :force-mode="false"
    />
  </el-card>
</template>

<script setup>
import { computed, ref } from 'vue'
import { useAuthStore } from '../../store/auth'
import ChangePasswordDialog from '../../components/ChangePasswordDialog.vue'

const auth = useAuthStore()

// 角色友好名称：优先后端 roleNames（个人中心改造新增），空时回退角色编码
const displayRoleNames = computed(() => {
  const names = auth.roleNames || []
  if (names.length) return names
  return auth.roles || []
})

const showChangePwd = ref(false)

function formatTime(value) {
  if (!value) return '-'
  const s = String(value)
  // 兼容 ISO-8601（T 分隔 / 毫秒 / 时区后缀），统一截取 yyyy-MM-dd HH:mm:ss
  const cleaned = s.replace('T', ' ').replace(/\.\d+/, '')
  return cleaned.length >= 19 ? cleaned.slice(0, 19) : cleaned
}
</script>

<style scoped>
.profile-card {
  max-width: 640px;
  margin: 0 auto;
}

.username-text {
  font-weight: 600;
}

.role-tag {
  margin-right: 8px;
}

.profile-actions {
  margin-top: 20px;
  text-align: right;
}
</style>
