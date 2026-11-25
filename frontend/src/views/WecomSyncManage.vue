<template>
  <div v-permission="'wecom:sync'">
    <!-- 操作区：DryRun 试算 / 实跑同步 -->
    <el-card shadow="never" class="section-card">
      <div class="ui-toolbar">
        <div>
          <el-button type="primary" :loading="running" @click="handleDryRun">立即同步（DryRun 试算）</el-button>
          <el-button type="danger" :loading="running" @click="handleRealRun">确认实跑同步</el-button>
          <span class="muted small hint">DryRun 只试算变更清单，不写业务数据；实跑前请先试算确认</span>
        </div>
        <div class="spacer" />
        <el-tag v-if="syncStatus" :type="statusTagType(syncStatus.status)" size="large">
          {{ statusText(syncStatus.status) }}
        </el-tag>
      </div>

      <!-- 同步结果卡片 -->
      <el-descriptions v-if="syncStatus" :column="4" border class="status-desc">
        <el-descriptions-item label="触发方式">{{ syncStatus.triggerType }}</el-descriptions-item>
        <el-descriptions-item label="操作人">{{ syncStatus.operator || '-' }}</el-descriptions-item>
        <el-descriptions-item label="拉取成员">{{ syncStatus.fetchedUsers ?? '-' }}</el-descriptions-item>
        <el-descriptions-item label="新建用户">{{ syncStatus.createdUsers ?? '-' }}</el-descriptions-item>
        <el-descriptions-item label="更新用户">{{ syncStatus.updatedUsers ?? '-' }}</el-descriptions-item>
        <el-descriptions-item label="停用用户">{{ syncStatus.disabledUsers ?? '-' }}</el-descriptions-item>
        <el-descriptions-item label="绑定用户">{{ syncStatus.boundUsers ?? '-' }}</el-descriptions-item>
        <el-descriptions-item label="未绑定剩余">{{ syncStatus.unboundLeft ?? '-' }}</el-descriptions-item>
        <el-descriptions-item label="开始时间">{{ syncStatus.startTime || '-' }}</el-descriptions-item>
        <el-descriptions-item label="结束时间">{{ syncStatus.endTime || '-' }}</el-descriptions-item>
        <el-descriptions-item label="错误信息" :span="2">
          <span v-if="syncStatus.errorMsg" class="error-text">{{ syncStatus.errorMsg }}</span>
          <span v-else class="muted small">-</span>
        </el-descriptions-item>
      </el-descriptions>
    </el-card>

    <!-- 最近同步记录 -->
    <el-card shadow="never" class="section-card">
      <template #header>最近同步记录</template>
      <el-table :data="recentLogs" border stripe v-loading="loading" size="small">
        <el-table-column prop="id" label="ID" width="70" align="center" />
        <el-table-column prop="triggerType" label="触发方式" width="110" align="center" />
        <el-table-column prop="operator" label="操作人" width="110" />
        <el-table-column label="状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="statusTagType(row.status)">{{ statusText(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="fetchedUsers" label="拉取" width="70" align="center" />
        <el-table-column prop="createdUsers" label="新建" width="70" align="center" />
        <el-table-column prop="updatedUsers" label="更新" width="70" align="center" />
        <el-table-column prop="disabledUsers" label="停用" width="70" align="center" />
        <el-table-column prop="boundUsers" label="绑定" width="70" align="center" />
        <el-table-column prop="unboundLeft" label="未绑定" width="80" align="center" />
        <el-table-column prop="startTime" label="开始时间" width="160" />
        <el-table-column prop="endTime" label="结束时间" width="160" />
        <el-table-column prop="errorMsg" label="错误信息" min-width="160">
          <template #default="{ row }">
            <span v-if="row.errorMsg" class="error-text">{{ row.errorMsg }}</span>
            <span v-else class="muted small">-</span>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 组织架构树 -->
    <el-card shadow="never" class="section-card">
      <template #header>组织架构树（部门节点带人数，展开查看成员）</template>
      <el-tree
        :data="departmentTree"
        :props="treeProps"
        node-key="deptId"
        default-expand-all
        :expand-on-click-node="false"
      >
        <template #default="{ data }">
          <span class="tree-node">
            <span>{{ data.name }}</span>
            <el-tag v-if="data.status === 0" size="small" type="warning" class="tag-gap">已删除</el-tag>
            <span class="muted small tag-gap">（{{ data.memberCount || 0 }} 人）</span>
          </span>
          <span v-if="data.members && data.members.length" class="tree-members">
            <el-tag
              v-for="m in data.members"
              :key="m.userId"
              size="small"
              :type="m.status === 1 ? 'info' : 'danger'"
              class="member-tag"
            >
              {{ m.username }} · {{ m.mobileMasked || '未录手机号' }}<template v-if="m.isMain"> · 主部门</template>
            </el-tag>
          </span>
        </template>
      </el-tree>
    </el-card>

    <!-- 未绑定清单 + 人工绑定 -->
    <el-card shadow="never" class="section-card">
      <template #header>未绑定清单（人工指认：为系统账号选择企微成员）</template>
      <el-alert
        type="info"
        :closable="false"
        show-icon
        title="企微侧重名/系统侧已有同名绑定等歧义成员会进入「企微未匹配成员」列表；管理员可在此为存量账号人工指认绑定"
        class="unbound-alert"
      />
      <el-table :data="unbound.systemUsers" border stripe v-loading="loading" size="small">
        <el-table-column prop="id" label="ID" width="70" align="center" />
        <el-table-column prop="username" label="系统账号姓名" width="150" />
        <el-table-column prop="mobile" label="手机号（脱敏）" width="150">
          <template #default="{ row }">{{ maskMobile(row.mobile) || '-' }}</template>
        </el-table-column>
        <el-table-column prop="departmentName" label="部门" min-width="120">
          <template #default="{ row }">{{ row.departmentName || '-' }}</template>
        </el-table-column>
        <el-table-column label="企微绑定" width="100" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.wecomUserId" type="success" size="small">{{ row.wecomUserId }}</el-tag>
            <span v-else class="muted small">未绑定</span>
          </template>
        </el-table-column>
        <el-table-column label="人工绑定" min-width="220">
          <template #default="{ row }">
            <el-select
              v-model="bindSelections[row.id]"
              placeholder="选择企微成员"
              size="small"
              clearable
              filterable
              class="bind-select"
            >
              <el-option
                v-for="m in unbound.wecomMembers"
                :key="m.userId"
                :label="`${m.name}（${m.mobileMasked || '无手机号'}）`"
                :value="m.userId"
              />
            </el-select>
            <el-button
              type="primary"
              size="small"
              :disabled="!bindSelections[row.id]"
              :loading="bindingIds.includes(row.id)"
              class="tag-gap"
              @click="handleBind(row)"
            >
              绑定
            </el-button>
          </template>
        </el-table-column>
      </el-table>
      <div v-if="!unbound.systemUsers || !unbound.systemUsers.length" class="muted small unbound-empty">
        暂无未绑定系统账号
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  triggerSync,
  getSyncStatus,
  getRecentSyncLogs,
  getDepartments,
  getUnbound,
  bindWecom
} from '../api/wecom'
import { maskMobile } from '../utils/format'

// R4：同步轮询参数具名常量（间隔 3s，总时长放宽到 10 分钟）
const SYNC_POLL_INTERVAL_MS = 3000
const SYNC_POLL_TIMEOUT_MS = 10 * 60 * 1000

const loading = ref(false)
const running = ref(false)
const syncStatus = ref(null)
const recentLogs = ref([])
const departmentTree = ref([])
const unbound = reactive({ systemUsers: [], wecomMembers: [] })
const bindSelections = reactive({})
const bindingIds = ref([])

const treeProps = {
  children: 'children',
  label: 'name'
}

function statusText(s) {
  return { RUNNING: '同步中', SUCCESS: '成功', FAILED: '失败' }[s] || s || '-'
}
function statusTagType(s) {
  return { RUNNING: 'warning', SUCCESS: 'success', FAILED: 'danger' }[s] || 'info'
}

// 轮询同步状态直到结束（超时提示保留、完成弹确认逻辑不变）
async function waitSyncDone(logId) {
  const deadline = Date.now() + SYNC_POLL_TIMEOUT_MS
  while (Date.now() < deadline) {
    await new Promise((r) => setTimeout(r, SYNC_POLL_INTERVAL_MS))
    const st = await getSyncStatus(logId)
    syncStatus.value = st
    if (st && st.status && st.status !== 'RUNNING') {
      return st
    }
  }
  ElMessage.warning('同步状态轮询超时，请稍后在最近记录中查看')
  return null
}

async function refresh() {
  loading.value = true
  try {
    const [logs, depts, ub] = await Promise.all([
      getRecentSyncLogs({ limit: 10 }),
      getDepartments(),
      getUnbound()
    ])
    recentLogs.value = Array.isArray(logs) ? logs : []
    departmentTree.value = Array.isArray(depts) ? depts : []
    unbound.systemUsers = ub?.systemUsers || []
    unbound.wecomMembers = ub?.wecomMembers || []
    const latest = await getSyncStatus(null)
    syncStatus.value = latest
  } catch (e) {
    // 拦截器已提示
  } finally {
    loading.value = false
  }
}

async function handleDryRun() {
  running.value = true
  try {
    const logId = await triggerSync({ dryRun: true })
    ElMessage.success('DryRun 试算已开始，等待结果...')
    const st = await waitSyncDone(logId)
    if (st) {
      refresh()
      if (st.detail && st.detail !== '[]' && st.detail !== 'null') {
        let changes = []
        try {
          changes = JSON.parse(st.detail)
        } catch (e) {
          changes = []
        }
        if (changes.length) {
          const lines = changes
            .map((c) => `[${c.action}] ${c.name || ''}（${c.mobileMasked || '-'}）— ${c.reason || ''}`)
            .join('\n')
          try {
            await ElMessageBox.confirm(
              `DryRun 试算发现 ${changes.length} 项变更：\n${lines}\n\n是否确认实跑同步？`,
              '确认实跑',
              { type: 'warning', confirmButtonText: '确认实跑', cancelButtonText: '取消' }
            )
            await handleRealRun()
          } catch (e) {
            // 用户取消实跑
          }
        }
      }
    }
  } catch (e) {
    // 拦截器已提示
  } finally {
    running.value = false
  }
}

async function handleRealRun() {
  try {
    await ElMessageBox.confirm('将执行企微通讯录全量实跑同步：会新建/绑定/更新/停用账号，确定执行？', '确认实跑同步', {
      type: 'warning',
      confirmButtonText: '执行同步',
      cancelButtonText: '取消'
    })
  } catch (e) {
    return
  }
  running.value = true
  try {
    const logId = await triggerSync({ dryRun: false })
    ElMessage.success('同步已开始，等待结果...')
    await waitSyncDone(logId)
    refresh()
  } catch (e) {
    // 拦截器已提示
  } finally {
    running.value = false
  }
}

async function handleBind(row) {
  const wecomUserId = bindSelections[row.id]
  if (!wecomUserId) {
    ElMessage.warning('请先选择企微成员')
    return
  }
  bindingIds.value = [...bindingIds.value, row.id]
  try {
    await bindWecom(row.id, wecomUserId)
    ElMessage.success('绑定成功')
    delete bindSelections[row.id]
    refresh()
  } catch (e) {
    // 拦截器已提示
  } finally {
    bindingIds.value = bindingIds.value.filter((x) => x !== row.id)
  }
}

onMounted(refresh)
</script>

<style scoped>
.section-card {
  margin-bottom: 16px;
}
/* 12px 变体：.muted 颜色已全局收口（ui-tokens.css），此处仅补字号 */
.small {
  font-size: 12px;
}
.hint {
  margin-left: 8px;
}
.status-desc {
  margin-top: 16px;
}
.tag-gap {
  margin-left: 6px;
}
.member-tag {
  margin-right: 4px;
  margin-top: 4px;
}
.unbound-alert {
  margin-bottom: 12px;
}
.bind-select {
  width: 160px;
}
.unbound-empty {
  margin-top: 12px;
}
.error-text {
  color: var(--ui-danger);
  font-size: 12px;
}
.tree-node {
  display: inline-flex;
  align-items: center;
  font-weight: 500;
}
.tree-members {
  margin-left: 12px;
}
</style>
