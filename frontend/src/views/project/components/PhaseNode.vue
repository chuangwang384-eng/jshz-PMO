<template>
  <div class="phase-node" :class="{ 'phase-node--expanded': expanded, 'phase-node--inactive': phase?.isActive === 0 }">
    <Handle v-if="showSource" type="target" :position="Position.Left" />
    <div class="phase-node__body" @click="onToggle">
      <div class="phase-node__badge">{{ index + 1 }}</div>
      <div class="phase-node__info">
        <div class="phase-node__name">{{ phase?.name || node.data.label }}</div>
        <div class="phase-node__sub">
          <span v-if="source === 'global'" class="src-tag src-tag--global">沿用通用</span>
          <span v-else-if="source === 'none'" class="src-tag">无需材料</span>
          <span v-else class="src-tag src-tag--own">单独配置</span>
          <span class="tmpl-count">{{ templates.length }} 项资料要求</span>
        </div>
      </div>
      <el-icon class="phase-node__arrow"><ArrowDown /></el-icon>
    </div>
    <div v-if="expanded && templates.length" class="phase-node__tmpls">
      <template-card
        v-for="(t, i) in templates"
        :key="t.templateId || t.id"
        :template="t"
        :required="t.required"
        :source="source"
      />
    </div>
    <div v-if="expanded && !templates.length" class="phase-node__empty">{{ source === 'none' ? '无需提交材料' : '当前没有资料要求' }}</div>
    <Handle v-if="showTarget" type="source" :position="Position.Right" />
  </div>
</template>

<script setup>
import { ref, computed } from 'vue'
import { Handle, Position } from '@vue-flow/core'
import { ArrowDown } from '@element-plus/icons-vue'
import TemplateCard from './TemplateCard.vue'

const props = defineProps({
  node: { type: Object, required: true },
  phase: { type: Object, default: null },
  index: { type: Number, default: 0 },
  templates: { type: Array, default: () => [] },
  source: { type: String, default: 'own' },
  showSource: { type: Boolean, default: true },
  showTarget: { type: Boolean, default: true }
})

const expanded = ref(true)
function onToggle() {
  expanded.value = !expanded.value
}
</script>

<style scoped>
.phase-node {
  width: 180px;
  background: #fff;
  border: 1.5px solid #d8dee8;
  border-radius: 8px;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.06);
  font-size: 12px;
}
.phase-node--expanded { border-color: #c6d4ea; }
.phase-node--inactive { opacity: 0.6; }
.phase-node__body {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 10px;
  cursor: pointer;
}
.phase-node__badge {
  width: 22px; height: 22px;
  border-radius: 50%;
  background: var(--ui-primary);
  color: #fff;
  font-size: 12px;
  display: flex; align-items: center; justify-content: center;
  flex-shrink: 0;
}
.phase-node__info { flex: 1; min-width: 0; }
.phase-node__name { font-weight: 600; color: var(--ui-text-main); white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.phase-node__sub { display: flex; align-items: center; gap: 4px; margin-top: 2px; flex-wrap: wrap; }
.src-tag { font-size: 10px; line-height: 14px; padding: 0 4px; border-radius: 3px; }
.src-tag--global { color: var(--ui-primary); background: #ecf5ff; }
.src-tag--own { color: var(--ui-success); background: #f0f9eb; }
.tmpl-count { color: var(--ui-text-muted); font-size: 11px; }
.phase-node__arrow { color: var(--ui-text-muted); flex-shrink: 0; }
.phase-node__tmpls { border-top: 1px dashed #e5e7eb; padding: 6px 8px; display: flex; flex-direction: column; gap: 5px; }
.phase-node__empty { border-top: 1px dashed #e5e7eb; padding: 8px; color: var(--ui-info); text-align: center; font-size: 11px; }
</style>
