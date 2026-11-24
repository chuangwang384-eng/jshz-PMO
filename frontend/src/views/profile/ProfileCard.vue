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
      <el-descriptions-item label="所属部门">{{ organization.departmentPath || organizationStatus }}</el-descriptions-item>
      <el-descriptions-item label="岗位">{{ organization.position || organizationStatus }}</el-descriptions-item>
    </el-descriptions>

    <div class="profile-actions">
      <el-button v-if="auth.hasRole('SUPER_ADMIN')" @click="showOrganization = true">组织架构管理</el-button>
      <el-button v-if="!auth.mustChangePassword" type="primary" @click="showChangePwd = true">修改密码</el-button>
    </div>

    <section class="profile-activity" aria-label="近期账号活动">
      <h2>近期账号活动</h2>
      <el-table v-loading="activityLoading" :data="activity.items" :empty-text="activityError ? '加载失败' : '暂无近期活动'">
        <el-table-column label="事件" min-width="130">
          <template #default="{ row }">{{ eventNames[row.event] }}</template>
        </el-table-column>
        <el-table-column label="时间" min-width="175">
          <template #default="{ row }">{{ formatTime(row.occurredAt) }}</template>
        </el-table-column>
      </el-table>
      <el-button v-if="activityError" link type="primary" @click="loadActivity">重试</el-button>
      <el-pagination v-if="activity.total > activity.pageSize" v-model:current-page="activityPage" :page-size="activity.pageSize"
        :total="activity.total" layout="prev, pager, next" @current-change="loadActivity" />
    </section>

    <ProfileOrganizationManager v-if="showOrganization && auth.hasRole('SUPER_ADMIN')"
      @close="showOrganization = false" @changed="loadOrganization" />

    <!-- 修改密码弹窗（复用 ChangePasswordDialog；普通模式：可关闭、无强制改密警示） -->
    <ChangePasswordDialog
      v-model="showChangePwd"
      title="修改密码"
      :force-mode="false"
    />
  </el-card>
</template>

<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import ProfileOrganizationManager from './ProfileOrganizationManager.vue'
import { getAccountActivity, getOrganization } from '../../api/profileOverview'
import { useAuthStore } from '../../store/auth'
import ChangePasswordDialog from '../../components/ChangePasswordDialog.vue'

const auth = useAuthStore()
const route = useRoute()
const router = useRouter()
const showOrganization = ref(false)
const organization = ref({})
const organizationStatus = ref('加载中…')
const activity = ref({ items: [], total: 0 })
const activityPage = ref(1)
const activityLoading = ref(false)
const activityError = ref(false)
const eventNames = { LOGIN: '登录', LOGOUT: '退出登录', CHANGE_PASSWORD: '修改密码', RESET_PASSWORD: '密码被重置' }
let activityRequest = 0

async function loadOrganization() {
  try {
    organization.value = await getOrganization()
    organizationStatus.value = '未分配'
  } catch {
    organizationStatus.value = '加载失败'
  }
}

async function loadActivity() {
  const request = ++activityRequest
  activityLoading.value = true
  activityError.value = false
  try {
    const result = await getAccountActivity(activityPage.value)
    if (request === activityRequest) activity.value = result
  } catch {
    if (request === activityRequest) {
      activity.value = { items: [], total: 0 }
      activityError.value = true
    }
  } finally {
    if (request === activityRequest) activityLoading.value = false
  }
}

onMounted(() => { loadOrganization(); loadActivity() })

// 角色友好名称：优先后端 roleNames（个人中心改造新增），空时回退角色编码
const displayRoleNames = computed(() => {
  const names = auth.roleNames || []
  if (names.length) return names
  return auth.roles || []
})

const showChangePwd = ref(false)
watch(() => route.query.changePassword, (value) => {
  if (value !== '1') return
  if (!auth.mustChangePassword) showChangePwd.value = true
  const query = { ...route.query }
  delete query.changePassword
  router.replace({ query })
}, { immediate: true })
watch(() => auth.mustChangePassword, (value, previous) => {
  if (value) showChangePwd.value = false
  if (previous && !value) loadActivity()
})
watch(showChangePwd, (open) => {
  if (!open) loadActivity()
})

function formatTime(value) {
  if (!value) return '-'
  const s = String(value)
  // 兼容 ISO-8601（T 分隔 / 毫秒 / 时区后缀），统一截取 yyyy-MM-dd HH:mm:ss
  const cleaned = s.replace('T', ' ').replace(/\.\d+/, '')
  return cleaned.length >= 19 ? cleaned.slice(0, 19) : cleaned
}
</script>

<style scoped>
.profile-activity {
  margin-top: 24px;
}

.profile-activity h2 {
  font-size: 16px;
}

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
