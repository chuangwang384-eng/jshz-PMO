<template>
  <div class="flow-overview">
    <div class="flow-overview__head">
      <div class="flow-overview__legend">
        <span class="legend-item"><span class="legend-dot legend-dot--own"></span>类型专属绑定</span>
        <span class="legend-item"><span class="legend-dot legend-dot--global"></span>全局兜底绑定</span>
        <span class="legend-item"><span class="legend-dot legend-dot--empty"></span>未配置流程（使用全部启用阶段）</span>
      </div>
      <div class="flow-overview__hint">拖动画布/滚轮缩放，点击阶段节点可折叠模板卡片，点击类型「配置」进入三步向导</div>
    </div>

    <div class="flow-overview__canvas">
      <VueFlow
        id="flow-overview"
        :nodes="nodes"
        :edges="edges"
        :min-zoom="0.15"
        :max-zoom="2"
        fit-view-on-init
        :fit-view-options="{ padding: 0.12 }"
        :nodes-draggable="false"
        :nodes-connectable="false"
        :nodes-deletable="false"
        :delete-key-code="null"
        :zoom-on-double-click="false"
        :apply-default="false"
      >
        <template #node-typeNode="nodeProps">
          <TypeNode :node="nodeProps" @edit="onEditType" />
        </template>
        <template #node-phaseNode="nodeProps">
          <PhaseNode :node="nodeProps" :phase="nodeProps.data.phase" :index="nodeProps.data.index" :templates="nodeProps.data.templates" :source="nodeProps.data.source" />
        </template>
        <Background :gap="24" pattern-color="#e6e9ef" />
        <Panel position="top-right">
          <div class="flow-overview__panel">
            <el-button size="small" @click="zoomIn">放大</el-button>
            <el-button size="small" @click="zoomOut">缩小</el-button>
            <el-button size="small" @click="fitView">适应屏幕</el-button>
          </div>
        </Panel>
      </VueFlow>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted } from 'vue'
import { VueFlow, useVueFlow, Panel } from '@vue-flow/core'
import { Background } from '@vue-flow/background'
import '@vue-flow/core/dist/style.css'
import '@vue-flow/core/dist/theme-default.css'
import TypeNode from './TypeNode.vue'
import PhaseNode from './PhaseNode.vue'
import { useProjectConfigStore } from '../../../stores/projectConfig'

const emit = defineEmits(['edit-type'])

const store = useProjectConfigStore()
const { zoomIn, zoomOut, fitView } = useVueFlow('flow-overview')

const ROW_GAP = 46
const PHASE_X0 = 190
const PHASE_STEP = 240
const PHASE_BODY_H = 40
const CARD_H = 50
const CARD_GAP = 5
const TMPL_PAD = 12
const EMPTY_H = 30

// 单行高度：按该类型各阶段「模板展开」高度取最大（PhaseNode 默认展开模板卡片）
function rowHeightOf(t) {
  const flow = store.typeFlowCache[t.id] || []
  if (!flow.length) return PHASE_BODY_H + EMPTY_H + 8
  let maxH = 0
  for (const f of flow) {
    const binds = store.bindsFor(t.id, f.phaseId)
    const cards = bindCards(t.id, f.phaseId, binds)
    maxH = Math.max(maxH, cards.length
      ? PHASE_BODY_H + TMPL_PAD + cards.length * CARD_H + (cards.length - 1) * CARD_GAP
      : PHASE_BODY_H + EMPTY_H)
  }
  return Math.max(maxH, 56)
}

// 类型节点数据：typeId -> { type, phaseCount }
const nodes = computed(() => {
  const list = []
  const sorted = store.sortedTypes
  let y = 0
  for (const t of sorted) {
    const rowH = rowHeightOf(t)
    const flow = store.typeFlowCache[t.id] || []
    const phaseCount = flow.length
    list.push({
      id: `type-${t.id}`,
      type: 'typeNode',
      position: { x: 10, y: y + Math.max(0, (rowH - 56) / 2) },
      data: { type: t, phaseCount },
      draggable: false
    })
    if (phaseCount) {
      flow.forEach((f, idx) => {
        const binds = store.bindsFor(t.id, f.phaseId)
        const phase = store.phases.find((p) => p.id === f.phaseId)
        list.push({
          id: `phase-${t.id}-${f.phaseId}`,
          type: 'phaseNode',
          position: { x: PHASE_X0 + idx * PHASE_STEP, y },
          data: {
            phase,
            index: idx,
            source: binds.fallback ? 'global' : 'own',
            templates: bindCards(t.id, f.phaseId, binds)
          },
          draggable: false
        })
      })
    } else {
      // 未配置流程 → 占位提示节点（使用全部启用阶段）
      list.push({
        id: `empty-${t.id}`,
        type: 'phaseNode',
        position: { x: PHASE_X0, y },
        data: {
          phase: null,
          index: 0,
          source: 'global',
          templates: [],
          label: '未配置流程，使用全部启用阶段'
        },
        draggable: false
      })
    }
    y += rowH + ROW_GAP
  }
  return list
})

const edges = computed(() => {
  const list = []
  const sorted = store.sortedTypes
  for (const t of sorted) {
    const flow = store.typeFlowCache[t.id] || []
    if (!flow.length) continue
    // 类型节点 → 首个阶段
    list.push({
      id: `e-type-${t.id}`,
      source: `type-${t.id}`,
      target: `phase-${t.id}-${flow[0].phaseId}`,
      type: 'smoothstep',
      animated: false
    })
    for (let i = 0; i < flow.length - 1; i++) {
      list.push({
        id: `e-${t.id}-${flow[i].phaseId}-${flow[i + 1].phaseId}`,
        source: `phase-${t.id}-${flow[i].phaseId}`,
        target: `phase-${t.id}-${flow[i + 1].phaseId}`,
        type: 'smoothstep',
        animated: false
      })
    }
  }
  return list
})

// 合并绑定与模板信息 → 模板卡片数据
function bindCards(typeId, phaseId, binds) {
  const phase = store.phases.find((p) => p.id === phaseId)
  const fallback = binds.fallback
  const cards = []
  if (phase) {
    // 该阶段专属绑定（保存后生效的）
    const own = store.phaseTemplateRels[`${typeId}_${phaseId}`] || []
    // 专属为空时回退全局
    const effective = own.length ? own : (store.phaseTemplateRels[`0_${phaseId}`] || [])
    for (const b of effective) {
      const tmpl = store.templates.find((x) => x.id === b.templateId)
      if (tmpl) cards.push({
        templateId: b.templateId,
        name: tmpl.name,
        templateType: tmpl.templateType,
        required: b.required,
        sort: b.sort,
        source: own.length ? 'own' : 'global'
      })
    }
  }
  return cards.sort((a, b) => (a.sort || 0) - (b.sort || 0))
}

function onEditType(type) {
  emit('edit-type', type)
}

onMounted(() => {
  // 数据已在父级加载；这里兜底确保绑定加载
  if (!store.types.length) store.loadAll()
})
</script>

<style scoped>
.flow-overview__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 10px;
}
.flow-overview__legend { display: flex; gap: 14px; flex-wrap: wrap; }
.legend-item { display: inline-flex; align-items: center; gap: 5px; font-size: 12px; color: #606266; }
.legend-dot { width: 10px; height: 10px; border-radius: 50%; display: inline-block; }
.legend-dot--own { background: #67c23a; }
.legend-dot--global { background: #409eff; }
.legend-dot--empty { background: #fff; border: 1.5px dashed #c0c4cc; }
.flow-overview__hint { color: #909399; font-size: 12px; }
.flow-overview__canvas {
  height: 520px;
  border: 1px solid #e4e7ed;
  border-radius: 8px;
  overflow: hidden;
  background: #fbfcfe;
}
.flow-overview__panel { display: flex; gap: 4px; }
</style>
