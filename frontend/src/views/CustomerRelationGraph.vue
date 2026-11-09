<template>
  <div class="relation-graph-page">
    <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <div class="header-left">
            <el-button @click="goBack">返回列表</el-button>
            <span class="title">{{ centerMode ? (centerName ? `「${centerName}」的客户关系网络` : "客户关系网络") : "全局客户关系网络" }}</span>
            <el-tag v-if="truncated" type="warning" effect="plain">客户数较多，仅展示前 {{ nodes.length }} 位客户的关系</el-tag>
          </div>
          <div class="header-right">
            <template v-if="canManage">
              <el-button v-if="!linkMode" type="warning" plain size="small" @click="enterLinkMode">连线模式</el-button>
              <template v-else>
                <el-button type="warning" size="small" @click="exitLinkMode">退出连线</el-button>
                <el-button type="primary" size="small" @click="openSearchTarget">搜索目标客户</el-button>
                <el-tag type="warning" effect="plain">{{ pendingFrom ? `已选起始：${pendingFrom.name}` : '请点击起始客户，再点击目标客户建立关系' }}</el-tag>
              </template>
            </template>
            <el-tooltip content="节点=客户；箭头=有向关系；连线模式下两次点击节点建立关系，点击关系连线可删除；点击节点跳转客户详情">
              <el-icon><QuestionFilled /></el-icon>
            </el-tooltip>
          </div>
        </div>
      </template>
      <div ref="chartRef" class="graph-canvas" v-loading="loading"></div>
      <el-empty v-if="!loading && nodes.length === 0" description="暂无客户关系数据" />
      <div v-if="!loading && nodes.length > 0" class="legend">
        <span class="legend-item"><i class="dot center"></i>客户</span>
        <span class="legend-item"><i class="arrow"></i>单向关系（类型标注在连线上）</span>
        <span class="legend-item"><i class="double-arrow"></i>双向关系</span>
        <span class="legend-item"><i class="plain-line"></i>无向关系</span>
      </div>
    </el-card>

    <el-dialog v-model="linkDialogVisible" title="建立客户关系" width="460px" destroy-on-close>
      <el-form label-width="100px">
        <el-form-item label="关系方向">
          <span>{{ pendingFrom?.name }} → {{ pendingTo?.name }}</span>
        </el-form-item>
        <el-form-item label="关系类型" required>
          <el-select v-model="linkForm.relationTypeId" placeholder="请选择关系类型" style="width: 100%">
            <el-option v-for="t in enabledTypes" :key="t.id" :label="typeLabel(t)" :value="t.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="关系说明">
          <el-input v-model="linkForm.description" type="textarea" :rows="2" maxlength="200" show-word-limit placeholder="选填" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="linkDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="linkSaving" @click="confirmCreateRelation">建立</el-button>
      </template>
    </el-dialog>

    <CustomerSelectDialog
      v-model="targetSelectVisible"
      :customer-id="pendingFrom?.id ?? null"
      @select="onTargetSelect"
    />
  </div>
</template>

<script setup>
import { ref, reactive, computed, watch, onMounted, onBeforeUnmount, getCurrentInstance } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { QuestionFilled } from '@element-plus/icons-vue'
import * as echarts from 'echarts'
import { useAuthStore } from '../store/auth'
import { getCenterGraph, getNetworkGraph, listRelationTypes, createRelation, deleteRelation } from '../api/customer'
import CustomerSelectDialog from '../components/CustomerSelectDialog.vue'

const router = useRouter()
const route = useRoute()
// 中心模式：从客户列表/客户详情跳入，仅展示该客户的关系圈（Owner 2026-11-09）
const centerId = computed(() => {
  const v = Number(route.query.customerId)
  return Number.isInteger(v) && v > 0 ? v : null
})
const centerMode = computed(() => centerId.value !== null)
const centerName = computed(() => String(route.query.customerName || '').trim())
const { proxy } = getCurrentInstance()
const authStore = useAuthStore()
const canManage = computed(() => authStore.hasPermission('customer:manage'))

const chartRef = ref(null)
const loading = ref(false)
const nodes = ref([])
const truncated = ref(false)
let chart = null

// 连线模式
const linkMode = ref(false)
const pendingFrom = ref(null)
const pendingTo = ref(null)
const linkDialogVisible = ref(false)
const linkSaving = ref(false)
const targetSelectVisible = ref(false)
const linkForm = reactive({ relationTypeId: null, description: '' })
const relationTypes = ref([])
const enabledTypes = computed(() => relationTypes.value.filter((t) => t.status === 1))

function typeLabel(t) {
  const map = { DIRECTED: '单向', BIDIRECTED: '双向', UNDIRECTED: '无向' }
  return `${t.name}（${map[t.direction] || '单向'}）`
}

function directionOf(typeId) {
  return relationTypes.value.find((x) => x.id === typeId)?.direction || 'DIRECTED'
}

function edgeSymbolOf(typeId) {
  const d = directionOf(typeId)
  if (d === 'BIDIRECTED') return ['arrow', 'arrow']
  if (d === 'UNDIRECTED') return ['none', 'none']
  return ['none', 'arrow']
}

function edgeSymbolOfLink(l) {
  const d = l.relationDirection || directionOf(l.relationTypeId)
  if (d === 'BIDIRECTED') return ['arrow', 'arrow']
  if (d === 'UNDIRECTED') return ['none', 'none']
  return ['none', 'arrow']
}

function renderGraph(data) {
  if (!chart) return
  const nodeData = (data.nodes || []).map((n) => ({
    id: String(n.id),
    name: n.name || '未命名',
    workUnit: n.workUnit || '',
    isCenter: !!n.isCenter,
    symbolSize: n.isCenter ? 64 : 46,
    itemStyle: { color: n.isCenter ? '#e6a23c' : '#409eff' }
  }))
  const linkData = (data.links || []).map((l) => ({
    id: String(l.id),
    source: String(l.source),
    target: String(l.target),
    relationTypeId: l.relationTypeId,
    relationTypeName: l.relationTypeName || '关系',
    description: l.description || '',
    symbol: edgeSymbolOfLink(l),
    label: { show: true, formatter: l.relationTypeName || '关系', fontSize: 13, color: '#5a3a00', backgroundColor: 'rgba(255,255,255,0.7)', padding: [2, 4], borderRadius: 2 },
    lineStyle: { color: '#c0c4cc', width: 2, curveness: 0.1 }
  }))
  chart.setOption(
    {
      tooltip: {
        formatter: (params) => {
          if (params.dataType === 'node') {
            return `${params.data.name}<br/>${params.data.workUnit || '无单位信息'}`
          }
          if (params.dataType === 'edge') {
            return `${params.data.relationTypeName}${params.data.description ? '：' + params.data.description : ''}`
          }
          return ''
        }
      },
      series: [
        {
          type: 'graph',
          layout: 'force',
          roam: true,
          draggable: true,
          force: { repulsion: 320, edgeLength: 150, gravity: 0.06 },
          edgeSymbol: ['none', 'none'], // 箭头由每条 link 的 symbol 字段按方向控制
          edgeSymbolSize: 8,
          label: { show: true, position: 'bottom', fontSize: 12 },
          data: nodeData,
          links: linkData,
          emphasis: { focus: 'adjacency', lineStyle: { width: 3 } }
        }
      ]
    },
    true
  )
}

function bindChartEvents() {
  if (!chart) return
  chart.off('click')
  chart.on('click', (params) => {
    if (params.dataType === 'node') {
      handleNodeClick(params.data)
    } else if (params.dataType === 'edge') {
      handleEdgeClick(params.data)
    }
  })
}

function handleNodeClick(node) {
  const nodeId = Number(node.id)
  if (linkMode.value && canManage.value) {
    if (!pendingFrom.value) {
      pendingFrom.value = { id: nodeId, name: node.name }
      ElMessage.info(`已选择起始客户：${node.name}，请点击目标客户或点击「搜索目标客户」`)
      return
    }
    if (pendingFrom.value.id === nodeId) {
      ElMessage.warning('不能与自己建立关系，请选择其他客户')
      return
    }
    pendingTo.value = { id: nodeId, name: node.name }
    linkForm.relationTypeId = null
    linkForm.description = ''
    linkDialogVisible.value = true
    return
  }
  router.push(`/knowledge-planet/customer-detail/${nodeId}`)
}

function handleEdgeClick(link) {
  if (!canManage.value) {
    ElMessage.info(`关系：${link.relationTypeName}${link.description ? '（' + link.description + '）' : ''}`)
    return
  }
  ElMessageBox.confirm(
    `确认删除该关系？\n类型：${link.relationTypeName}${link.description ? '；说明：' + link.description : ''}`,
    '删除关系',
    { confirmButtonText: '删除', cancelButtonText: '取消', type: 'warning' }
  )
    .then(async () => {
      try {
        await deleteRelation(Number(link.id))
        ElMessage.success('关系已删除')
        loadGraph()
      } catch (e) {
        proxy.$message.error(e?.message || '删除关系失败')
      }
    })
    .catch(() => {})
}

function enterLinkMode() {
  linkMode.value = true
  pendingFrom.value = null
  ElMessage.info('连线模式已开启：请点击起始客户，再点击目标客户（方向：先点者 → 后点者）')
}

function exitLinkMode() {
  linkMode.value = false
  pendingFrom.value = null
  pendingTo.value = null
}

function openSearchTarget() {
  if (!linkMode.value) return
  if (!pendingFrom.value) {
    ElMessage.warning('请先在图上点击起始客户')
    return
  }
  targetSelectVisible.value = true
}

function onTargetSelect(customer) {
  if (!pendingFrom.value) {
    ElMessage.warning('请先选择起始客户')
    return
  }
  if (pendingFrom.value.id === customer.id) {
    ElMessage.warning('不能与自己建立关系，请选择其他客户')
    return
  }
  pendingTo.value = { id: customer.id, name: customer.name }
  linkForm.relationTypeId = null
  linkForm.description = ''
  linkDialogVisible.value = true
}

async function confirmCreateRelation() {
  if (!linkForm.relationTypeId) {
    ElMessage.warning('请选择关系类型')
    return
  }
  linkSaving.value = true
  try {
    await createRelation({
      fromCustomerId: pendingFrom.value.id,
      toCustomerId: pendingTo.value.id,
      relationTypeId: linkForm.relationTypeId,
      description: linkForm.description?.trim() || ''
    })
    ElMessage.success('关系已建立')
    linkDialogVisible.value = false
    exitLinkMode()
    loadGraph()
  } catch (e) {
    const msg = e?.message || '建立关系失败'
    if (msg.includes('已存在')) {
      ElMessage.warning(msg)
    } else {
      proxy.$message.error(msg)
    }
  } finally {
    linkSaving.value = false
  }
}

async function loadGraph() {
  loading.value = true
  try {
    const res = centerMode.value
      ? await getCenterGraph(centerId.value)
      : await getNetworkGraph()
    const data = res || { nodes: [], links: [] }
    nodes.value = data.nodes || []
    truncated.value = !!data.truncated
    renderGraph(data)
  } catch (e) {
    proxy.$message.error(e?.message || '加载关系网络失败')
  } finally {
    loading.value = false
  }
}

function initChart() {
  if (!chartRef.value) return
  chart = echarts.init(chartRef.value, null, { renderer: 'canvas', devicePixelRatio: window.devicePixelRatio || 2 })
  bindChartEvents()
  window.addEventListener('resize', handleResize)
}

function handleResize() {
  chart?.resize()
}

function goBack() {
  // 优先回退到上一页（保留列表的 URL 查询状态：搜索词/页码/已删除视图）
  const hasBack = window.history.state && window.history.state.back
  if (hasBack) {
    router.back()
    return
  }
  router.push('/knowledge-planet/customer-info')
}

onMounted(async () => {
  initChart()
  relationTypes.value = (await listRelationTypes().catch(() => [])) || []
  await loadGraph()
})

// 兜底：若类型列表异步晚到且图已渲染，重绘一次确保箭头按方向展示
watch(relationTypes, () => {
  if (nodes.value.length) loadGraph()
})

// 同一页面内切换中心客户（列表/详情再次跳入）时重新加载，避免残留上一位客户的关系图
watch(centerId, () => {
  exitLinkMode()
  linkDialogVisible.value = false
  loadGraph()
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', handleResize)
  chart?.dispose()
  chart = null
})
</script>

<style scoped>
.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.header-left {
  display: flex;
  align-items: center;
  gap: 12px;
}
.title {
  font-size: 16px;
  font-weight: 600;
}
.header-right {
  display: flex;
  align-items: center;
}
.graph-canvas {
  width: 100%;
  height: 640px;
}
.legend {
  margin-top: 10px;
  display: flex;
  gap: 24px;
  align-items: center;
  color: #606266;
  font-size: 13px;
}
.legend-item {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}
.dot {
  display: inline-block;
  width: 10px;
  height: 10px;
  border-radius: 50%;
  background: #409eff;
}
.arrow {
  display: inline-block;
  width: 24px;
  height: 2px;
  background: #c0c4cc;
  position: relative;
}
.arrow::after {
  content: '';
  position: absolute;
  right: -1px;
  top: -3px;
  border-left: 6px solid #c0c4cc;
  border-top: 4px solid transparent;
  border-bottom: 4px solid transparent;
}
.double-arrow {
  display: inline-block;
  width: 24px;
  height: 2px;
  background: #c0c4cc;
  position: relative;
}
.double-arrow::before {
  content: '';
  position: absolute;
  left: -1px;
  top: -3px;
  border-right: 6px solid #c0c4cc;
  border-top: 4px solid transparent;
  border-bottom: 4px solid transparent;
}
.double-arrow::after {
  content: '';
  position: absolute;
  right: -1px;
  top: -3px;
  border-left: 6px solid #c0c4cc;
  border-top: 4px solid transparent;
  border-bottom: 4px solid transparent;
}
.plain-line {
  display: inline-block;
  width: 24px;
  height: 2px;
  background: #c0c4cc;
}
</style>
