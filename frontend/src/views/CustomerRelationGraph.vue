<template>
  <div class="relation-graph-page">
    <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <div class="header-left">
            <el-button @click="goBack">返回列表</el-button>
            <span class="title">全局客户关系网络</span>
            <el-tag v-if="truncated" type="warning" effect="plain">客户数较多，仅展示前 {{ nodes.length }} 位客户的关系</el-tag>
          </div>
          <div class="header-right">
            <el-tooltip content="节点=客户；箭头=有向关系；点击节点跳转客户详情；点击关系连线查看说明">
              <el-icon><QuestionFilled /></el-icon>
            </el-tooltip>
          </div>
        </div>
      </template>
      <div ref="chartRef" class="graph-canvas" v-loading="loading"></div>
      <el-empty v-if="!loading && nodes.length === 0" description="暂无客户关系数据" />
      <div v-if="!loading && nodes.length > 0" class="legend">
        <span class="legend-item"><i class="dot center"></i>客户</span>
        <span class="legend-item"><i class="arrow"></i>有向关系（类型标注在连线上）</span>
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { ref, onMounted, onBeforeUnmount, getCurrentInstance } from 'vue'
import { useRouter } from 'vue-router'
import { QuestionFilled } from '@element-plus/icons-vue'
import * as echarts from 'echarts'
import { getNetworkGraph } from '../api/customer'

const router = useRouter()
const { proxy } = getCurrentInstance()

const chartRef = ref(null)
const loading = ref(false)
const nodes = ref([])
const truncated = ref(false)
let chart = null

function renderGraph(data) {
  if (!chart) return
  const nodeData = (data.nodes || []).map((n) => ({
    id: String(n.id),
    name: n.name || '未命名',
    workUnit: n.workUnit || '',
    symbolSize: 46,
    itemStyle: { color: '#409eff' }
  }))
  const linkData = (data.links || []).map((l) => ({
    id: String(l.id),
    source: String(l.source),
    target: String(l.target),
    relationTypeName: l.relationTypeName || '关系',
    description: l.description || '',
    label: { show: true, formatter: l.relationTypeName || '关系', fontSize: 10, color: '#7c4a03' },
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
          edgeSymbol: ['none', 'arrow'],
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
      const nodeId = Number(params.data.id)
      router.push(`/knowledge-planet/customer-detail/${nodeId}`)
    } else if (params.dataType === 'edge') {
      proxy.$message.info(`关系：${params.data.relationTypeName}${params.data.description ? '（' + params.data.description + '）' : ''}`)
    }
  })
}

async function loadGraph() {
  loading.value = true
  try {
    const res = await getNetworkGraph()
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
  chart = echarts.init(chartRef.value)
  bindChartEvents()
  window.addEventListener('resize', handleResize)
}

function handleResize() {
  chart?.resize()
}

function goBack() {
  router.push('/knowledge-planet/customer-info')
}

onMounted(() => {
  initChart()
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
</style>
