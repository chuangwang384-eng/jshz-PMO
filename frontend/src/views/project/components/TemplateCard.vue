<template>
  <div class="tmpl-card" :class="{ 'tmpl-card--dim': dim }" :draggable="draggable" @dragstart="onDragStart">
    <div class="tmpl-card__head">
      <span class="tmpl-card__name" :title="template.name">{{ template.name }}</span>
      <span class="tmpl-card__type">{{ typeText(template.templateType) }}</span>
    </div>
    <div class="tmpl-card__meta">
      <span v-if="required !== null" class="tmpl-card__flag" :class="required === 1 ? 'is-req' : 'is-opt'">
        {{ required === 1 ? '必填' : '选填' }}
      </span>
      <span class="tmpl-card__src" :class="source === 'global' ? 'is-global' : 'is-own'">
        {{ source === 'global' ? '沿用通用' : '单独配置' }}
      </span>
    </div>
    <slot />
  </div>
</template>

<script setup>
const props = defineProps({
  template: { type: Object, required: true },
  required: { type: Number, default: null }, // 1 必填 / 0 选填 / null 不显示
  source: { type: String, default: 'own' }, // own 类型专属 / global 全局兜底
  dim: { type: Boolean, default: false }, // 弱化（非必填或未绑定提示）
  draggable: { type: Boolean, default: false }
})
const emit = defineEmits(['dragstart'])
function typeText(t) {
  return { FILE: '附件', EXPERT: '专家', SUPPLIER: '供应商', BUDGET: '预算', RICH_TEXT: '文本' }[t] || t
}
function onDragStart(e) {
  emit('dragstart', e)
}
</script>

<style scoped>
.tmpl-card {
  border: 1px solid #e5e7eb;
  border-radius: 6px;
  padding: 6px 8px;
  background: #fff;
  font-size: 12px;
  line-height: 1.4;
  transition: box-shadow 0.15s;
}
.tmpl-card--dim { opacity: 0.72; }
.tmpl-card__head { display: flex; align-items: center; gap: 6px; }
.tmpl-card__name { font-weight: 600; color: #303133; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; flex: 1; }
.tmpl-card__type { flex-shrink: 0; color: #909399; font-size: 11px; }
.tmpl-card__meta { display: flex; align-items: center; gap: 6px; margin-top: 3px; }
.tmpl-card__flag { font-size: 11px; padding: 0 5px; border-radius: 3px; line-height: 16px; }
.tmpl-card__flag.is-req { color: #f56c6c; background: #fef0f0; }
.tmpl-card__flag.is-opt { color: #e6a23c; background: #fdf6ec; }
.tmpl-card__src { font-size: 11px; padding: 0 5px; border-radius: 3px; line-height: 16px; }
.tmpl-card__src.is-own { color: #67c23a; background: #f0f9eb; }
.tmpl-card__src.is-global { color: #409eff; background: #ecf5ff; }
</style>
