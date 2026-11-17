<template>
  <div>
    <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <span>组织架构与通讯录</span>
          <span class="muted">展示根到本部门的组织链路与同部门成员（手机号已脱敏）</span>
        </div>
      </template>

      <el-skeleton v-if="loading" :rows="6" animated />

      <template v-else>
        <!-- 根到本部门链路 -->
        <div v-if="ancestors.length" class="chain">
          <template v-for="(d, i) in ancestors" :key="d.deptId">
            <el-tag size="large" :type="i === ancestors.length - 1 ? 'primary' : 'info'">{{ d.name }}</el-tag>
            <span v-if="i < ancestors.length - 1" class="arrow">→</span>
          </template>
        </div>
        <el-empty v-else description="尚未绑定部门，请联系管理员完成企微绑定" :image-size="80" />

        <!-- 本部门成员 -->
        <div v-if="members.length" class="members">
          <h3>本部门成员（{{ members.length }}）</h3>
          <el-table :data="members" border stripe size="small">
            <el-table-column prop="username" label="姓名" width="200" />
            <el-table-column prop="mobileMasked" label="手机号（脱敏）" min-width="160">
              <template #default="{ row }">{{ row.mobileMasked || '-' }}</template>
            </el-table-column>
            <el-table-column label="主部门" width="100" align="center">
              <template #default="{ row }">
                <el-tag v-if="row.isMain" type="success" size="small">主部门</el-tag>
                <span v-else class="muted">-</span>
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
        </div>
        <el-empty v-else-if="ancestors.length" description="本部门暂无成员" :image-size="80" />
      </template>
    </el-card>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { getMyDirectory } from '../../api/wecom'

const loading = ref(true)
const ancestors = ref([])
const members = ref([])

onMounted(async () => {
  try {
    const data = await getMyDirectory()
    ancestors.value = data?.ancestors || []
    members.value = data?.members || []
  } catch (e) {
    // 错误提示已由拦截器统一处理
  } finally {
    loading.value = false
  }
})
</script>

<style scoped>
.card-header {
  display: flex;
  align-items: baseline;
  gap: 12px;
}
.muted {
  font-size: 12px;
}
.chain {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 24px;
}
.arrow {
  color: var(--ui-text-muted);
}
.members h3 {
  margin-top: 0;
}
</style>
