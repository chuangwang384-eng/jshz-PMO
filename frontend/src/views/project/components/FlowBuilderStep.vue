<template>
  <div class="flow-builder">
    <div class="flow-builder__toolbar">
      <el-select v-model="addPhaseId" placeholder="添加阶段（仅启用阶段）" size="small" style="width: 200px" filterable>
        <el-option v-for="p in addablePhases" :key="p.id" :label="p.name" :value="p.id" />
      </el-select>
      <el-button size="small" type="primary" :disabled="!addPhaseId" @click="onAdd">添加阶段</el-button>
      <span class="flow-builder__hint">拖拽节点或使用右侧按钮调整顺序；未配置任何阶段时，该类型将使用全部启用阶段</span>
    </div>

    <div class="flow-builder__canvas">
      <VueFlow
        v-if="flow.length"
        id="flow-builder-step"
        :nodes="nodes"
        :edges="edges"
        :min-zoom="0.3"
        :max-zoom="2"
        fit-view-on-init
        :fit-view-options="{ padding: 0.15 }"
        :nodes-draggable="true"
        :nodes-connectable="false"
        :nodes-deletable="false"
        :delete-key-code="null"
        :zoom-on-double-click="false"
        :apply-default="true"
        @node-drag-stop="onDragStop"
      >
        <template #node-phaseNode="nodeProps">
          <PhaseNode v-bind="nodeProps" :show-target="false" />
        </template>
        <Background :gap="24" pattern-color="#e6e9ef" />
        <Panel position="top-right">
          <div class="flow-builder__panel">
            <el-button size="small" @click="zoomIn">放大</el-button>
            <el-button size="small" @click="zoomOut">缩小</el-button>
            <el-button size="small" @click="fitView">适应</el-button>
          </div>
        </Panel>
      </VueFlow>
      <div v-else class="flow-builder__empty">
        <el-icon class="flow-builder__empty-icon"><Connection /></el-icon>
        <div>尚未配置流程阶段</div>
        <div class="flow-builder__empty-sub">保存后该类型将使用「全部启用阶段」，可从上方选择阶段开始配置</div>
      </div>
    </div>

    <div class="flow-builder__list">
      <div class="flow-builder__list-title">阶段顺序（点击上移/下移微调）</div>
      <div v-if="!flow.length" class="flow-builder__list-empty">暂无阶段</div>
      <el-table :data="flow" size="small" max-height="220" stripe>
        <el-table-column type="index" label="#" width="50" />
        <el-table-column prop="phaseName" label="阶段" />
        <el-table-column label="操作" width="220">
          <template #default="{ $index }">
            <el-button link :disabled="$index === 0" @click="onMove($index, -1)">上移</el-button>
            <el-button link :disabled="$index === flow.length - 1" @click="onMove($index, 1)">下移</el-button>
            <el-button link type="danger" @click="onRemove($index)">移除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>
  </div>
</template>

<script setup>
import { computed, ref, watch } from 'vue'
import { VueFlow, useVueFlow, Panel } from '@vue-flow/core'
import { Background } from '@vue-flow/background'
import { Connection } from '@element-plus/icons-vue'
import '@vue-flow/core/dist/style.css'
import '@vue-flow/core/dist/theme-default.css'
import PhaseNode from './PhaseNode.vue'

const props = defineProps({
  modelValue: { type: Array, default: () => [] }, // [{phaseId, phaseName}]
  phases: { type: Array, default: () => [] } // 全部阶段（含停用）
})
const emit = defineEmits(['update:modelValue'])

const flow = computed({
  get: () => props.modelValue,
  set: (v) => emit('update:modelValue', v)
})
const addPhaseId = ref(null)

const { zoomIn, zoomOut, fitView } = useVueFlow('flow-builder-step')

const addablePhases = computed(() =>
  props.phases.filter((p) => p.isActive === 1 && !flow.value.some((f) => f.phaseId === p.id))
)

const nodes = computed(() =>
  flow.value.map((f, idx) => ({
    id: `fb-${f.phaseId}`,
    type: 'phaseNode',
    position: { x: idx * 200, y: 30 },
    data: { phase: props.phases.find((p) => p.id === f.phaseId) || { name: f.phaseName }, index: idx, source: 'own', templates: [] },
    draggable: true
  }))
)

const edges = computed(() => {
  const list = []
  for (let i = 0; i < flow.value.length - 1; i++) {
    list.push({
      id: `fbe-${flow.value[i].phaseId}-${flow.value[i + 1].phaseId}`,
      source: `fb-${flow.value[i].phaseId}`,
      target: `fb-${flow.value[i + 1].phaseId}`,
      type: 'smoothstep'
    })
  }
  return list
})

function onAdd() {
  if (!addPhaseId.value) return
  const p = props.phases.find((x) => x.id === addPhaseId.value)
  if (!p) return
  flow.value = [...flow.value, { phaseId: p.id, phaseName: p.name }]
  addPhaseId.value = null
}
function onMove(index, dir) {
  const target = index + dir
  if (target < 0 || target >= flow.value.length) return
  const arr = flow.value.slice()
  const [item] = arr.splice(index, 1)
  arr.splice(target, 0, item)
  flow.value = arr
}
function onRemove(index) {
  const arr = flow.value.slice()
  arr.splice(index, 1)
  flow.value = arr
}

// 拖拽结束：按节点 x 坐标重新排序（事件参数: (event, node, nodes)）
function onDragStop({ node }) {
  if (!node) return
  const positions = new Map(flow.value.map((f, index) => [`fb-${f.phaseId}`, index * 200]))
  positions.set(node.id, node.position.x)
  flow.value = [...flow.value].sort((a, b) => positions.get(`fb-${a.phaseId}`) - positions.get(`fb-${b.phaseId}`))
}

</script>

<style scoped>
.flow-builder__toolbar { display: flex; align-items: center; gap: 8px; margin-bottom: 10px; flex-wrap: wrap; }
.flow-builder__hint { color: #909399; font-size: 12px; }
.flow-builder__canvas {
  height: 260px;
  border: 1px solid #e4e7ed;
  border-radius: 8px;
  background: #fbfcfe;
  overflow: hidden;
  margin-bottom: 12px;
  position: relative;
}
.flow-builder__empty {
  height: 100%;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 6px;
  color: #c0c4cc;
  font-size: 13px;
}
.flow-builder__empty-icon { font-size: 30px; }
.flow-builder__empty-sub { font-size: 12px; }
.flow-builder__list-title { font-size: 13px; color: #606266; margin-bottom: 6px; font-weight: 600; }
.flow-builder__list-empty { color: #c0c4cc; font-size: 12px; padding: 8px 0; }
.flow-builder__panel { display: flex; gap: 4px; }
</style>
