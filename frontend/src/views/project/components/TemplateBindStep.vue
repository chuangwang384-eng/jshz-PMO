<template>
  <div class="template-bind">
    <div class="template-bind__layout">
      <!-- 左：阶段列表 -->
      <div class="template-bind__phases">
        <div class="template-bind__phases-title">选择阶段</div>
        <div
          v-for="(f, idx) in flow"
          :key="f.phaseId"
          class="template-bind__phase"
          :class="{ 'is-active': idx === activeIndex }"
          @click="activeIndex = idx"
        >
          <span class="template-bind__phase-idx">{{ idx + 1 }}</span>
          <span class="template-bind__phase-name">{{ f.phaseName }}</span>
          <span v-if="phaseState(f.phaseId).fallback" class="template-bind__phase-tag template-bind__phase-tag--global">兜底</span>
          <span v-else class="template-bind__phase-tag template-bind__phase-tag--own">专属</span>
        </div>
        <div v-if="!flow.length" class="template-bind__phases-empty">尚未配置流程阶段，无法绑定模板</div>
      </div>

      <!-- 右：当前阶段模板卡片 -->
      <div class="template-bind__detail">
        <template v-if="flow.length">
          <div class="template-bind__detail-head">
            <div>
              <div class="template-bind__detail-title">{{ currentPhase?.phaseName || '' }} · 模板绑定</div>
              <div v-if="phaseState(currentPhaseId).fallback" class="template-bind__fallback-hint">
                当前展示的是全局兜底绑定；勾选并保存后将成为「{{ typeName }}」类型专属绑定
              </div>
            </div>
          </div>
          <div class="template-bind__cards" @dragover.prevent @drop.prevent.stop="onDrop">
            <div
              v-for="(item, i) in phaseState(currentPhaseId).items"
              :key="item.templateId"
              class="template-bind__card"
              :class="{ 'is-bound': item._bound, 'is-dragging': dragIndex === i }"
              :draggable="item._bound"
              @dragstart="onDragStart($event, i)"
              @dragend="onDrop"
              @dragover.prevent="onDragOver(i)"
              @drop.prevent.stop="onDrop"
            >
              <div class="template-bind__card-main">
                <template-card
                  :template="item"
                  :required="item._bound ? item._required : null"
                  :source="phaseState(currentPhaseId).fallback ? 'global' : 'own'"
                />
              </div>
              <div class="template-bind__card-actions">
                <el-checkbox v-model="item._bound" size="small">绑定</el-checkbox>
                <el-select
                  v-model="item._required"
                  size="small"
                  style="width: 84px"
                  :disabled="!item._bound"
                >
                  <el-option label="必填" :value="1" />
                  <el-option label="选填" :value="0" />
                </el-select>
                <el-button
                  link
                  size="small"
                  :disabled="!item._bound || i === 0"
                  @click="moveTemplate(currentPhaseId, i, -1)"
                >上移</el-button>
                <el-button
                  link
                  size="small"
                  :disabled="!item._bound || i === phaseState(currentPhaseId).items.length - 1"
                  @click="moveTemplate(currentPhaseId, i, 1)"
                >下移</el-button>
              </div>
            </div>
          </div>
        </template>
        <div v-else class="template-bind__empty">请先在「步骤2」配置流程阶段</div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, ref, watch } from 'vue'
import TemplateCard from './TemplateCard.vue'

const props = defineProps({
  flow: { type: Array, default: () => [] }, // [{phaseId, phaseName}]
  templates: { type: Array, default: () => [] }, // 全部模板
  typeName: { type: String, default: '' },
  // 初始状态：phaseId -> { fallback, items:[{templateId, required, sort, _bound}] }
  initialStates: { type: Object, default: () => ({}) }
})
const emit = defineEmits(['change'])

const dragIndex = ref(null)
const activeIndex = ref(0)
const states = ref({})

// 初始化 states（合并 initialStates 与全部模板）
function buildStates() {
  const s = {}
  for (const f of props.flow) {
    const init = props.initialStates[f.phaseId] || { fallback: true, items: [] }
    const map = {}
    init.items.forEach((b) => { map[String(b.templateId)] = b })
    s[f.phaseId] = {
      fallback: init.fallback,
      items: props.templates.map((t) => ({
        templateId: t.id,
        name: t.name,
        templateType: t.templateType,
        _bound: !!map[String(t.id)],
        _required: map[String(t.id)] ? map[String(t.id)].required : 0,
        _sort: map[String(t.id)] ? map[String(t.id)].sort : 1
      })).sort((a, b) => Number(b._bound) - Number(a._bound) || a._sort - b._sort)
    }
  }
  states.value = s
}
watch(
  () => [props.initialStates, props.templates],
  buildStates,
  { immediate: true, deep: true }
)

watch(() => props.flow, () => {
  const previous = states.value
  buildStates()
  for (const f of props.flow) {
    if (previous[f.phaseId]) states.value[f.phaseId] = previous[f.phaseId]
  }
  activeIndex.value = Math.min(activeIndex.value, Math.max(0, props.flow.length - 1))
}, { deep: true })

const currentPhaseId = computed(() => props.flow[activeIndex.value]?.phaseId ?? null)
const currentPhase = computed(() => props.flow[activeIndex.value] || null)
function phaseState(phaseId) {
  return states.value[phaseId] || { fallback: true, items: [] }
}

function moveTemplate(phaseId, index, dir) {
  const items = states.value[phaseId].items
  const target = index + dir
  if (target < 0 || target >= items.length) return
  const arr = items.slice()
  const [item] = arr.splice(index, 1)
  arr.splice(target, 0, item)
  states.value[phaseId].items = arr
  emit('change', states.value)
}

// HTML5 拖拽排序：拖动中按目标下标实时重排，保存沿用现有 _sort 序列（绑定项按序写回）
function onDragStart(e, i) {
  dragIndex.value = i
  if (e.dataTransfer) e.dataTransfer.effectAllowed = 'move'
}
function onDragOver(i) {
  if (dragIndex.value === null || dragIndex.value === i) return
  const items = states.value[currentPhaseId.value].items
  const [moved] = items.splice(dragIndex.value, 1)
  items.splice(i, 0, moved)
  dragIndex.value = i
  emit('change', states.value)
}
function onDrop() {
  dragIndex.value = null
}

// 暴露给父级：获取保存用状态
function getStates() {
  return states.value
}
defineExpose({ getStates })
</script>

<style scoped>
.template-bind__layout { display: flex; gap: 14px; min-height: 320px; }
.template-bind__phases { width: 220px; flex-shrink: 0; border-right: 1px solid #ebeef5; padding-right: 12px; }
.template-bind__phases-title { font-size: 13px; color: #606266; margin-bottom: 8px; font-weight: 600; }
.template-bind__phase {
  display: flex; align-items: center; gap: 8px;
  padding: 8px 10px; border-radius: 6px; cursor: pointer;
  border: 1px solid transparent; margin-bottom: 6px;
}
.template-bind__phase:hover { background: #f5f7fa; }
.template-bind__phase.is-active { background: #ecf5ff; border-color: #b3d8ff; }
.template-bind__phase-idx {
  width: 20px; height: 20px; border-radius: 50%;
  background: #409eff; color: #fff; font-size: 12px;
  display: flex; align-items: center; justify-content: center; flex-shrink: 0;
}
.template-bind__phase-name { flex: 1; font-size: 13px; color: #303133; }
.template-bind__phase-tag { font-size: 11px; padding: 0 4px; border-radius: 3px; line-height: 15px; }
.template-bind__phase-tag--global { color: #409eff; background: #ecf5ff; }
.template-bind__phase-tag--own { color: #67c23a; background: #f0f9eb; }
.template-bind__phases-empty { color: #c0c4cc; font-size: 12px; }
.template-bind__detail { flex: 1; min-width: 0; }
.template-bind__detail-title { font-size: 14px; font-weight: 600; color: #303133; }
.template-bind__fallback-hint { color: #409eff; font-size: 12px; margin-top: 3px; }
.template-bind__cards { margin-top: 10px; display: flex; flex-direction: column; gap: 8px; max-height: 300px; overflow: auto; }
.template-bind__card {
  display: flex; align-items: center; gap: 10px;
  border: 1px solid #ebeef5; border-radius: 8px; padding: 8px 10px;
  transition: all 0.15s;
}
.template-bind__card.is-bound { border-color: #d3e9d0; background: #f8fbf8; }
.template-bind__card-main { flex: 1; min-width: 0; }
.template-bind__card-actions { display: flex; align-items: center; gap: 6px; flex-shrink: 0; }
.template-bind__empty { color: #c0c4cc; font-size: 13px; padding: 40px 0; text-align: center; }
</style>
