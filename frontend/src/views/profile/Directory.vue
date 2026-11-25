<template>
  <div>
    <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <span>组织架构与通讯录</span>
          <span class="muted card-sub">展示根到本部门的组织链路与同部门成员（手机号已脱敏）</span>
        </div>
      </template>

      <el-skeleton v-if="loading" :rows="6" animated />

      <!-- 接口失败：与「未绑定部门」空态区分，避免误判 -->
      <el-empty v-else-if="loadError" description="通讯录加载失败，请稍后重试" :image-size="80">
        <el-button type="primary" size="small" @click="load">重新加载</el-button>
      </el-empty>

      <template v-else>
        <!-- 数据来源提示：企微未绑定/未同步部门时回退手动组织树（热修复 2026-11-25） -->
        <el-alert
          v-if="source === 'profile'"
          class="chain-truncated"
          type="info"
          :closable="false"
          show-icon
          title="当前展示手动组织树数据（企微通讯录未绑定或未同步部门）；绑定企微后将自动切换为企微通讯录"
        />

        <!-- 根到本部门链路 -->
        <div v-if="ancestors.length" class="chain">
          <template v-for="(d, i) in ancestors" :key="d.deptId">
            <el-tag size="large" :type="i === ancestors.length - 1 ? 'primary' : 'info'">{{ d.name }}</el-tag>
            <span v-if="i < ancestors.length - 1" class="arrow">→</span>
          </template>
        </div>
        <el-empty v-else description="尚未绑定部门，请联系管理员完成企微绑定" :image-size="80" />

        <!-- 上级部门链路被截断（后端沿 parent 链构建，防环截断时附 truncated 标记） -->
        <el-alert
          v-if="truncated"
          class="chain-truncated"
          type="warning"
          :closable="false"
          show-icon
          title="上级部门数据不完整（部门链路被截断），以下仅展示可用的部分链路"
        />

        <!-- 本部门成员 -->
        <div v-if="members.length" class="members">
          <div class="members-head">
            <h3>本部门成员（{{ filteredMembers.length }}/{{ members.length }}）</h3>
            <el-input
              v-model="keyword"
              class="member-search"
              placeholder="按姓名搜索本部门成员"
              clearable
              size="small"
            />
          </div>
          <el-table :data="filteredMembers" border stripe size="small">
            <el-table-column prop="username" label="姓名" width="200" />
            <el-table-column prop="mobileMasked" label="手机号（脱敏）" min-width="160">
              <template #default="{ row }">{{ row.mobileMasked || '-' }}</template>
            </el-table-column>
            <!-- 手动组织树来源时展示岗位（企微同步数据无岗位字段） -->
            <el-table-column v-if="source === 'profile'" prop="position" label="岗位" min-width="150">
              <template #default="{ row }">{{ row.position || '-' }}</template>
            </el-table-column>
            <el-table-column label="部门归属" width="100" align="center">
              <template #default="{ row }">
                <el-tag v-if="row.isMain" type="success" size="small">主部门</el-tag>
                <el-tag v-else type="info" size="small">兼职</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="状态" width="100" align="center">
              <template #default="{ row }">
                <el-tag :type="row.status === 1 ? 'success' : 'danger'" size="small">
                  {{ row.status === 1 ? '启用' : '停用' }}
                </el-tag>
              </template>
            </el-table-column>
          </el-table>
          <div v-if="!filteredMembers.length" class="muted card-sub">没有匹配「{{ keyword }}」的成员</div>
        </div>
        <el-empty v-else-if="ancestors.length" description="本部门暂无成员" :image-size="80" />
      </template>
    </el-card>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { getMyDirectory } from '../../api/wecom'

const loading = ref(true)
const loadError = ref(false)
const ancestors = ref([])
const members = ref([])
const truncated = ref(false)
const source = ref('wecom')
const keyword = ref('')

// R3：本部门姓名搜索（纯前端过滤，不新增接口）
const filteredMembers = computed(() => {
  const kw = keyword.value.trim().toLowerCase()
  if (!kw) return members.value
  return members.value.filter((m) => (m.username || '').toLowerCase().includes(kw))
})

async function load() {
  loading.value = true
  loadError.value = false
  try {
    const data = await getMyDirectory()
    ancestors.value = data?.ancestors || []
    members.value = data?.members || []
    truncated.value = Boolean(data?.truncated)
    source.value = data?.source || 'wecom'
  } catch (e) {
    // 错误提示已由拦截器统一处理；此处区分「加载失败」与「未绑定部门」空态
    loadError.value = true
    ancestors.value = []
    members.value = []
    truncated.value = false
    source.value = 'wecom'
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>

<style scoped>
.card-header {
  display: flex;
  align-items: baseline;
  gap: 12px;
}
/* 12px 变体：.muted 颜色已全局收口（ui-tokens.css），此处仅补字号 */
.card-sub {
  font-size: 12px;
}
.chain {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 24px;
}
.chain-truncated {
  margin-bottom: 16px;
}
.arrow {
  color: var(--ui-text-muted);
}
.members-head {
  display: flex;
  align-items: center;
  gap: 12px;
}
.members h3 {
  margin-top: 0;
}
.member-search {
  width: 220px;
}
</style>
