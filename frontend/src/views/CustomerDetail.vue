<template>
  <div class="customer-detail-page">
    <el-card shadow="never" class="head-card">
      <div class="head-bar">
        <el-button @click="goBack">返回列表</el-button>
        <div class="head-title">
          <span class="name">{{ customer.name || '客户详情' }}</span>
          <el-tag v-if="customer.workUnit" type="info" size="small">{{ customer.workUnit }}</el-tag>
          <el-tag v-if="customer.workPosition" size="small">{{ customer.workPosition }}</el-tag>
        </div>
        <div class="head-actions">
          <el-button v-permission="'customer:manage'" type="primary" @click="openEdit">编辑档案</el-button>
          <el-button type="warning" @click="goRelationTypeManage">关系类型管理</el-button>
        </div>
      </div>
      <el-descriptions :column="2" border class="detail-desc">
        <el-descriptions-item label="客户姓名">{{ customer.name || '—' }}</el-descriptions-item>
        <el-descriptions-item label="工作单位">{{ customer.workUnit || '—' }}</el-descriptions-item>
        <el-descriptions-item label="工作职位">{{ customer.workPosition || '—' }}</el-descriptions-item>
        <el-descriptions-item label="工作职级">{{ customer.workLevel || '—' }}</el-descriptions-item>
        <el-descriptions-item label="现任职务">{{ customer.currentDuty || '—' }}</el-descriptions-item>
        <el-descriptions-item label="创建人 / 时间">{{ customer.createBy || '—' }} / {{ customer.createTime || '—' }}</el-descriptions-item>
        <el-descriptions-item label="客户喜好" :span="2">{{ customer.preferences || '—' }}</el-descriptions-item>
        <el-descriptions-item label="备注" :span="2">{{ customer.remark || '—' }}</el-descriptions-item>
      </el-descriptions>
    </el-card>

    <el-card shadow="never" class="attach-card">
      <template #header>
        <div class="card-header">
          <span>相关资料附件（{{ attachments.length }}）</span>
          <el-upload
            v-permission="'customer:manage'"
            :show-file-list="false"
            :http-request="handleUpload"
            :disabled="uploading"
          >
            <el-button type="primary" plain :loading="uploading">上传附件</el-button>
          </el-upload>
        </div>
      </template>
      <el-table :data="attachments" border stripe size="small">
        <el-table-column prop="originalName" label="文件名" min-width="220" show-overflow-tooltip />
        <el-table-column prop="fileType" label="类型" width="90" />
        <el-table-column label="大小" width="110">
          <template #default="{ row }">{{ formatSize(row.fileSize) }}</template>
        </el-table-column>
        <el-table-column prop="uploadBy" label="上传人" width="110" />
        <el-table-column prop="uploadTime" label="上传时间" width="165" />
        <el-table-column label="操作" width="140" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="handleDownload(row)">下载</el-button>
            <el-popconfirm title="确认删除该附件？" confirm-button-text="删除" cancel-button-text="取消" @confirm="handleDeleteAttachment(row)">
              <template #reference>
                <el-button v-permission="'customer:manage'" link type="danger" size="small">删除</el-button>
              </template>
            </el-popconfirm>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-card shadow="never" class="graph-card">
      <template #header>
        <div class="card-header">
          <span>「{{ customer.name || '该客户' }}」的关系圈</span>
          <div class="graph-actions">
            <template v-if="canManage">
              <el-button v-if="!linkMode" type="warning" plain size="small" @click="enterLinkMode">连线模式</el-button>
              <template v-else>
                <el-button type="warning" size="small" @click="exitLinkMode">退出连线</el-button>
                <el-tag type="warning" effect="plain">{{ pendingFrom ? `已选起始：${pendingFrom.name}，请点击目标客户` : '请点击起始客户（连线方向：先点者 → 后点者）' }}</el-tag>
              </template>
            </template>
            <el-tooltip content="点击节点跳转客户详情；连线模式下两次点击节点建立关系；点击关系连线可删除">
              <el-icon><QuestionFilled /></el-icon>
            </el-tooltip>
          </div>
        </div>
      </template>
      <div ref="chartRef" class="graph-canvas" v-loading="graphLoading"></div>
      <el-empty v-if="!graphLoading && nodes.length === 0" description="暂无关系，可在连线模式下建立" />
    </el-card>

    <el-dialog v-model="formVisible" :title="form.id ? '编辑客户档案' : '新增客户档案'" width="600px" destroy-on-close>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
        <el-form-item label="客户姓名" prop="name"><el-input v-model="form.name" maxlength="50" /></el-form-item>
        <el-form-item label="工作单位" prop="workUnit"><el-input v-model="form.workUnit" maxlength="100" /></el-form-item>
        <el-form-item label="工作职位" prop="workPosition"><el-input v-model="form.workPosition" maxlength="50" /></el-form-item>
        <el-form-item label="工作职级" prop="workLevel"><el-input v-model="form.workLevel" maxlength="50" /></el-form-item>
        <el-form-item label="现任职务" prop="currentDuty"><el-input v-model="form.currentDuty" maxlength="50" /></el-form-item>
        <el-form-item label="客户喜好" prop="preferences"><el-input v-model="form.preferences" type="textarea" :rows="3" maxlength="500" show-word-limit /></el-form-item>
        <el-form-item label="备注" prop="remark"><el-input v-model="form.remark" type="textarea" :rows="3" maxlength="500" show-word-limit /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="formVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="handleSave">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="linkDialogVisible" title="建立客户关系" width="460px" destroy-on-close>
      <el-form label-width="100px">
        <el-form-item label="关系方向">
          <span>{{ pendingFrom?.name }} → {{ pendingTo?.name }}</span>
        </el-form-item>
        <el-form-item label="关系类型" required>
          <el-select v-model="linkForm.relationTypeId" placeholder="请选择关系类型" style="width: 100%">
            <el-option v-for="t in enabledTypes" :key="t.id" :label="t.name" :value="t.id" />
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
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, onBeforeUnmount, watch, getCurrentInstance } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { QuestionFilled } from '@element-plus/icons-vue'
import * as echarts from 'echarts'
import { useAuthStore } from '../store/auth'
import {
  getCustomer,
  updateCustomer,
  listCustomerAttachments,
  uploadCustomerAttachment,
  deleteCustomerAttachment,
  fetchCustomerAttachment,
  listRelationTypes,
  getCenterGraph,
  createRelation,
  deleteRelation
} from '../api/customer'

const route = useRoute()
const router = useRouter()
const { proxy } = getCurrentInstance()
const authStore = useAuthStore()
const canManage = computed(() => authStore.hasPermission('customer:manage'))

const customerId = computed(() => Number(route.params.id))
const customer = ref({})
const attachments = ref([])
const uploading = ref(false)

const chartRef = ref(null)
const graphLoading = ref(false)
const nodes = ref([])
let chart = null

// 连线模式
const linkMode = ref(false)
const pendingFrom = ref(null)
const pendingTo = ref(null)
const linkDialogVisible = ref(false)
const linkSaving = ref(false)
const linkForm = reactive({ relationTypeId: null, description: '' })
const relationTypes = ref([])
const enabledTypes = computed(() => relationTypes.value.filter((t) => t.status === 1))

const formVisible = ref(false)
const saving = ref(false)
const formRef = ref()
const emptyForm = { id: null, name: '', workUnit: '', workPosition: '', workLevel: '', currentDuty: '', preferences: '', remark: '' }
const form = reactive({ ...emptyForm })
const rules = {
  name: [{ required: true, message: '请输入客户姓名', trigger: 'blur' }]
}

async function loadDetail() {
  try {
    const res = await getCustomer(customerId.value)
    customer.value = res || {}
    Object.assign(form, emptyForm, customer.value)
  } catch (e) {
    proxy.$message.error(e?.message || '加载客户详情失败')
  }
}

async function loadAttachments() {
  try {
    const res = await listCustomerAttachments(customerId.value)
    attachments.value = res || []
  } catch (e) {
    proxy.$message.error(e?.message || '加载附件失败')
  }
}

async function loadGraph() {
  graphLoading.value = true
  try {
    const res = await getCenterGraph(customerId.value)
    const data = res || { nodes: [], links: [] }
    nodes.value = data.nodes || []
    renderGraph(data)
  } catch (e) {
    proxy.$message.error(e?.message || '加载关系圈失败')
  } finally {
    graphLoading.value = false
  }
}

function renderGraph(data) {
  if (!chart) return
  const nodeData = (data.nodes || []).map((n) => ({
    id: String(n.id),
    name: n.name || '未命名',
    workUnit: n.workUnit || '',
    isCenter: !!n.isCenter,
    itemStyle: n.isCenter ? { color: '#e6a23c', borderColor: '#b88230', borderWidth: 2 } : { color: '#409eff' },
    symbolSize: n.isCenter ? 62 : 46
  }))
  const linkData = (data.links || []).map((l) => ({
    id: String(l.id),
    source: String(l.source),
    target: String(l.target),
    relationTypeName: l.relationTypeName || '关系',
    description: l.description || '',
    label: { show: true, formatter: l.relationTypeName || '关系', fontSize: 10, color: '#7c4a03' },
    lineStyle: { color: '#c0c4cc', width: 2, curveness: 0.08 }
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
          force: { repulsion: 320, edgeLength: 140, gravity: 0.08 },
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
      ElMessage.info(`已选择起始客户：${node.name}，请点击目标客户`)
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
  if (nodeId !== customerId.value) {
    router.push(`/knowledge-planet/customer-detail/${nodeId}`)
  }
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
    proxy.$message.error(e?.message || '建立关系失败')
  } finally {
    linkSaving.value = false
  }
}

async function handleUpload({ file }) {
  uploading.value = true
  try {
    const fd = new FormData()
    fd.append('file', file)
    await uploadCustomerAttachment(customerId.value, fd)
    ElMessage.success('上传成功')
    loadAttachments()
  } catch (e) {
    proxy.$message.error(e?.message || '上传失败')
  } finally {
    uploading.value = false
  }
}

async function handleDownload(row) {
  try {
    const res = await fetchCustomerAttachment(row.storageName)
    const blob = res.data
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = row.originalName
    a.click()
    URL.revokeObjectURL(url)
  } catch (e) {
    proxy.$message.error(e?.message || '下载失败')
  }
}

async function handleDeleteAttachment(row) {
  try {
    await deleteCustomerAttachment(row.id)
    ElMessage.success('附件已删除')
    loadAttachments()
  } catch (e) {
    proxy.$message.error(e?.message || '删除附件失败')
  }
}

function formatSize(size) {
  if (!size && size !== 0) return '—'
  if (size < 1024) return size + ' B'
  if (size < 1024 * 1024) return (size / 1024).toFixed(1) + ' KB'
  return (size / 1024 / 1024).toFixed(2) + ' MB'
}

function openEdit() {
  Object.assign(form, emptyForm, customer.value)
  formVisible.value = true
}

async function handleSave() {
  try {
    await formRef.value.validate()
  } catch {
    return
  }
  saving.value = true
  try {
    await updateCustomer(customerId.value, {
      name: form.name.trim(),
      workUnit: form.workUnit?.trim() || '',
      workPosition: form.workPosition?.trim() || '',
      workLevel: form.workLevel?.trim() || '',
      currentDuty: form.currentDuty?.trim() || '',
      preferences: form.preferences?.trim() || '',
      remark: form.remark?.trim() || ''
    })
    ElMessage.success('保存成功')
    formVisible.value = false
    loadDetail()
  } catch (e) {
    proxy.$message.error(e?.message || '保存失败')
  } finally {
    saving.value = false
  }
}

function goBack() {
  router.push('/knowledge-planet/customer-info')
}

function goRelationTypeManage() {
  router.push('/knowledge-planet/customer-relation-type-manage')
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

watch(customerId, () => {
  exitLinkMode()
  loadDetail()
  loadAttachments()
  loadGraph()
})

onMounted(async () => {
  await Promise.all([loadDetail(), loadAttachments()])
  relationTypes.value = await listRelationTypes().catch(() => [])
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
.head-card {
  margin-bottom: 14px;
}
.head-bar {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 14px;
}
.head-title {
  display: flex;
  align-items: center;
  gap: 8px;
  flex: 1;
}
.head-title .name {
  font-size: 18px;
  font-weight: 600;
}
.head-actions {
  display: flex;
  gap: 10px;
}
.detail-desc {
  margin-top: 4px;
}
.attach-card {
  margin-bottom: 14px;
}
.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.graph-actions {
  display: flex;
  align-items: center;
  gap: 10px;
}
.graph-canvas {
  width: 100%;
  height: 560px;
}
</style>
