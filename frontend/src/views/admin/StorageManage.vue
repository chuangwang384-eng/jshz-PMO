<template>
  <div class="sm-page">
    <el-alert type="warning" :closable="false" class="sm-banner"
      title="本页为存储管理工具：硬删除操作永久生效、不可恢复，请谨慎操作" />

    <!-- 存储占用统计与预警 -->
    <el-card class="sm-card">
      <template #header>
        <div class="sm-card-head">
          <span>存储占用统计与预警</span>
          <el-button size="small" :loading="statsLoading" @click="loadStats">刷新</el-button>
        </div>
      </template>
      <div v-if="stats" v-loading="statsLoading">
        <div class="sm-summary">
          <el-tag :type="levelTagType(stats.level)" effect="dark">
            {{ levelText(stats.level) }}
          </el-tag>
          <span class="sm-summary-item">总计：<b>{{ fmtBytes(stats.totalBytes) }}</b> / {{ stats.totalFiles }} 个文件</span>
          <span class="sm-summary-item">磁盘剩余：<b>{{ fmtBytes(stats.diskUsableBytes) }}</b></span>
          <span class="sm-summary-item">预警阈值：单目录 ≥ {{ fmtBytes(stats.warnThresholdBytes) }} 提醒 / ≥ {{ fmtBytes(stats.criticalThresholdBytes) }} 严重</span>
          <span class="sm-summary-item sm-time">检查于 {{ stats.checkedAt }}</span>
        </div>
        <el-table :data="stats.dirs" size="small" border>
          <el-table-column prop="name" label="目录" width="130" />
          <el-table-column prop="path" label="路径" min-width="240" show-overflow-tooltip />
          <el-table-column label="占用" width="110">
            <template #default="{ row }">{{ row.exists ? fmtBytes(row.bytes) : '-' }}</template>
          </el-table-column>
          <el-table-column label="文件数" width="90">
            <template #default="{ row }">{{ row.exists ? row.files : '-' }}</template>
          </el-table-column>
          <el-table-column label="预警" width="90">
            <template #default="{ row }">
              <el-tag size="small" :type="levelTagType(row.level)">{{ levelText(row.level) }}</el-tag>
            </template>
          </el-table-column>
        </el-table>
      </div>
    </el-card>

    <!-- 孤儿文件清理（全目录） -->
    <el-card class="sm-card">
      <template #header>
        <div class="sm-card-head">
          <span>孤儿文件清理（全目录）</span>
          <el-button size="small" type="primary" :loading="orphanLoading" @click="scanOrphans">扫描全部目录</el-button>
        </div>
      </template>
      <el-alert type="info" :closable="false" class="sm-tip"
        title="孤儿文件 = 磁盘上存在但数据库中已无任何引用的文件，清理不影响在线功能。知识文档库排除 _preview 缓存；照片库排除 richtext_ 富文本内嵌图（引用在正文中）；照片临时目录保留审批单 TMP: 引用的文件。" />
      <el-alert type="warning" :closable="false" class="sm-tip"
        title="「项目资料库（已删除资料）」= 资料记录已被软删、且全库无任何未删引用的物理文件。这类文件系统内已无法访问（无资料恢复入口），回收只删磁盘文件、保留数据库留痕；如仍有未删记录共享同一文件则不会被清理。" />
      <template v-if="orphanAll">
        <div class="sm-summary">
          <span class="sm-summary-item">全部目录合计 <b>{{ orphanAll.totalCount }}</b> 个孤儿文件，<b>{{ fmtBytes(orphanAll.totalBytes) }}</b></span>
        </div>
        <el-table :data="orphanAll.scopes" size="small" border row-key="scope">
          <el-table-column type="expand">
            <template #default="{ row }">
              <div class="sm-expand">
                <div v-if="!row.count" class="sm-more">该目录无孤儿文件</div>
                <el-table v-else :data="row.items.slice(0, 100)" size="small" border max-height="260">
                  <el-table-column prop="path" label="文件（相对路径）" min-width="260" show-overflow-tooltip />
                  <el-table-column label="大小" width="100">
                    <template #default="s">{{ fmtBytes(s.row.bytes) }}</template>
                  </el-table-column>
                  <el-table-column prop="lastModified" label="最后修改" width="160" />
                </el-table>
                <div v-if="row.count > 100" class="sm-more">仅展示前 100 个，共 {{ row.count }} 个</div>
              </div>
            </template>
          </el-table-column>
          <el-table-column prop="name" label="目录" width="130" />
          <el-table-column prop="path" label="路径" min-width="240" show-overflow-tooltip />
          <el-table-column label="孤儿文件" width="100">
            <template #default="{ row }"><b>{{ row.count }}</b> 个</template>
          </el-table-column>
          <el-table-column label="可释放" width="110">
            <template #default="{ row }">{{ fmtBytes(row.bytes) }}</template>
          </el-table-column>
          <el-table-column label="操作" width="120">
            <template #default="{ row }">
              <el-button size="small" type="danger" plain :disabled="!row.count || busy"
                @click="doPurgeScope(row)">清理</el-button>
            </template>
          </el-table-column>
        </el-table>
      </template>
    </el-card>

    <!-- 预览缓存清理 -->
    <el-card class="sm-card">
      <template #header>
        <div class="sm-card-head">
          <span>文档预览缓存清理</span>
          <el-button v-if="previewDir" size="small" type="danger" plain :disabled="busy"
            @click="doClearPreview">清空预览缓存（{{ fmtBytes(previewDir.bytes) }} / {{ previewDir.files }} 个）</el-button>
        </div>
      </template>
      <el-alert type="info" :closable="false" class="sm-tip"
        title="预览缓存为 Office 转 PDF 的临时产物，清空后按需自动再生成，零业务风险" />
    </el-card>

    <!-- 项目数据硬删除 -->
    <el-card class="sm-card sm-danger-card">
      <template #header><span>项目关联数据硬删除（不可恢复）</span></template>
      <el-alert type="error" :closable="false" class="sm-tip"
        title="仅限「已取消 / 已删除」状态的项目。将永久删除该项目的全部资料文件、日程、参会专家、合同、劳务费关联、合规待办与执行清单、阶段历史等数据，删除后无法通过系统恢复。" />
      <div class="sm-purge-row">
        <el-input v-model="purgeProjectId" placeholder="输入项目 ID" style="width: 200px" clearable />
        <el-button type="primary" :loading="previewLoading" :disabled="!purgeProjectId" @click="loadPurgePreview">查询关联数据</el-button>
      </div>
      <template v-if="purgePreview">
        <el-descriptions :title="`项目「${purgePreview.projectName}」（状态：${purgePreview.status}）`" :column="4" border size="small" class="sm-purge-detail">
          <el-descriptions-item label="分阶段资料">{{ purgePreview.docs }}</el-descriptions-item>
          <el-descriptions-item label="日程文件">{{ purgePreview.schedules }}</el-descriptions-item>
          <el-descriptions-item label="参会专家">{{ purgePreview.speakers }}</el-descriptions-item>
          <el-descriptions-item label="合同">{{ purgePreview.contracts }}</el-descriptions-item>
          <el-descriptions-item label="专家关联（劳务费）">{{ purgePreview.expertRels }}</el-descriptions-item>
          <el-descriptions-item label="供应商关联">{{ purgePreview.supplierRels }}</el-descriptions-item>
          <el-descriptions-item label="阶段历史">{{ purgePreview.phaseHistories }}</el-descriptions-item>
          <el-descriptions-item label="合规执行清单">{{ purgePreview.complianceExecutions }}</el-descriptions-item>
          <el-descriptions-item label="合规事项">{{ purgePreview.complianceItems }}</el-descriptions-item>
          <el-descriptions-item label="合规待办推送">{{ purgePreview.complianceTodos }}</el-descriptions-item>
          <el-descriptions-item label="物理文件">{{ purgePreview.files }} 个（{{ fmtBytes(purgePreview.fileBytes) }}）</el-descriptions-item>
          <el-descriptions-item label="可否硬删">
            <el-tag :type="purgePreview.purgeable ? 'warning' : 'info'" size="small">
              {{ purgePreview.purgeable ? '可硬删' : '仅已取消/已删除项目可硬删' }}
            </el-tag>
          </el-descriptions-item>
        </el-descriptions>
        <el-button type="danger" :disabled="!purgePreview.purgeable || busy" @click="doPurgeProject">
          永久删除该项目全部关联数据（不可恢复）
        </el-button>
      </template>
    </el-card>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useAuthStore } from '../../store/auth'
import {
  storageStats, fetchOrphans, purgeOrphans, clearPreviewCache, purgeProjectPreview, purgeProject
} from '../../api/storageAdmin'

const auth = useAuthStore()
if (!auth.hasPermission('storage:manage')) {
  ElMessage.error('无权限访问存储管理')
}

const stats = ref(null)
const statsLoading = ref(false)
const orphanAll = ref(null)
const orphanLoading = ref(false)
const busy = ref(false)
const purgeProjectId = ref('')
const purgePreview = ref(null)
const previewLoading = ref(false)

const previewDir = computed(() => {
  if (!stats.value) return null
  return (stats.value.dirs || []).find((d) => d.name === '预览缓存')
})

function fmtBytes(n) {
  if (n == null || n < 0) return '-'
  if (n < 1024) return `${n} B`
  const units = ['KB', 'MB', 'GB', 'TB']
  let v = n
  let i = -1
  while (v >= 1024 && i < units.length - 1) { v /= 1024; i++ }
  return `${v.toFixed(v >= 100 ? 0 : 1)} ${units[i]}`
}
function levelTagType(level) {
  return level === 'CRITICAL' ? 'danger' : level === 'WARNING' ? 'warning' : 'success'
}
function levelText(level) {
  return level === 'CRITICAL' ? '严重' : level === 'WARNING' ? '提醒' : '正常'
}

async function loadStats() {
  statsLoading.value = true
  try { stats.value = await storageStats() } catch (e) { /* request 统一提示 */ } finally { statsLoading.value = false }
}
async function scanOrphans() {
  orphanLoading.value = true
  try { orphanAll.value = await fetchOrphans() } catch (e) { /* 统一提示 */ } finally { orphanLoading.value = false }
}

async function doPurgeScope(row) {
  try {
    await ElMessageBox.prompt(
      `将永久删除「${row.name}」中 ${row.count} 个孤儿文件（${fmtBytes(row.bytes)}），删除后不可恢复。请输入「清理」确认。`,
      '清理孤儿文件', { confirmButtonText: '执行删除', cancelButtonText: '取消', inputPattern: /^清理$/, inputErrorMessage: '请输入「清理」' }
    )
  } catch { return }
  busy.value = true
  try {
    const r = await purgeOrphans({ scope: row.scope, expectedCount: row.count, expectedBytes: row.bytes })
    ElMessage.success(`已永久删除「${row.name}」${r.deleted} 个孤儿文件（${fmtBytes(r.deletedBytes)}）`)
    await scanOrphans()
    loadStats()
  } finally { busy.value = false }
}

async function doClearPreview() {
  try {
    await ElMessageBox.prompt(
      `将清空 ${previewDir.value.files} 个预览缓存文件（${fmtBytes(previewDir.value.bytes)}），清空后按需自动再生成。请输入「清空」确认。`,
      '清空预览缓存', { confirmButtonText: '执行清空', cancelButtonText: '取消', inputPattern: /^清空$/, inputErrorMessage: '请输入「清空」' }
    )
  } catch { return }
  busy.value = true
  try {
    const r = await clearPreviewCache({ expectedCount: previewDir.value.files })
    ElMessage.success(`已清空 ${r.deleted} 个缓存文件（${fmtBytes(r.deletedBytes)}）`)
    loadStats()
  } finally { busy.value = false }
}

async function loadPurgePreview() {
  previewLoading.value = true
  purgePreview.value = null
  try {
    purgePreview.value = await purgeProjectPreview(purgeProjectId.value.trim())
  } catch (e) { /* 统一提示 */ } finally { previewLoading.value = false }
}

async function doPurgeProject() {
  const p = purgePreview.value
  try {
    await ElMessageBox.prompt(
      `将永久删除项目「${p.projectName}」的全部关联数据：${p.docs} 份资料（含物理文件 ${fmtBytes(p.fileBytes)}）、${p.schedules} 份日程、${p.contracts} 份合同、${p.complianceItems} 条合规事项等，删除后系统内不可恢复。请输入完整项目名确认。`,
      '硬删除项目数据', {
        confirmButtonText: '永久删除',
        cancelButtonText: '取消',
        type: 'warning',
        inputValidator: (v) => v === p.projectName || '请输入完整项目名'
      }
    )
  } catch { return }
  busy.value = true
  try {
    await purgeProject(p.projectId, p.projectName)
    ElMessage.success(`项目「${p.projectName}」关联数据已永久删除`)
    purgePreview.value = null
    purgeProjectId.value = ''
    loadStats()
  } finally { busy.value = false }
}

onMounted(loadStats)
</script>

<style scoped>
.sm-page { max-width: 1200px; margin: 0 auto; padding: 16px; }
.sm-banner { margin-bottom: 16px; }
.sm-card { margin-bottom: 16px; }
.sm-card-head { display: flex; align-items: center; justify-content: space-between; }
.sm-summary { display: flex; flex-wrap: wrap; gap: 8px 20px; align-items: center; margin-bottom: 10px; }
.sm-summary-item { color: #475569; font-size: 13px; }
.sm-time { color: #94a3b8; }
.sm-tip { margin-bottom: 10px; }
.sm-more { color: #94a3b8; font-size: 12px; margin-top: 6px; }
.sm-expand { padding: 8px 12px; }
.sm-danger-card { border-color: #fecaca; }
.sm-purge-row { display: flex; gap: 10px; margin-bottom: 12px; }
.sm-purge-detail { margin-bottom: 12px; }
</style>
