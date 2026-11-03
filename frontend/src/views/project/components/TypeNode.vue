<template>
  <div class="type-node" :class="{ 'type-node--empty': !phaseCount }" @click="onEdit">
    <div class="type-node__name" :title="type.name">{{ type.name }}</div>
    <div class="type-node__sub">
      <span v-if="phaseCount" class="type-node__count">{{ phaseCount }} 阶段</span>
      <span v-else class="type-node__count">未配置流程</span>
      <el-button link type="primary" size="small" @click.stop="onEdit">配置</el-button>
    </div>
    <Handle v-if="phaseCount" type="source" :position="Position.Right" />
  </div>
</template>

<script setup>
import { Handle, Position } from '@vue-flow/core'

const props = defineProps({
  node: { type: Object, required: true }
})
const emit = defineEmits(['edit'])
const type = props.node.data.type
const phaseCount = props.node.data.phaseCount
function onEdit() {
  emit('edit', type)
}
</script>

<style scoped>
.type-node {
  width: 132px;
  min-height: 56px;
  background: linear-gradient(135deg, #f5f7fa, #eef1f6);
  border: 1.5px solid #cdd6e2;
  border-radius: 10px;
  padding: 8px 10px;
  box-sizing: border-box;
  display: flex;
  flex-direction: column;
  justify-content: center;
  gap: 4px;
  cursor: pointer;
}
.type-node:hover { border-color: #409eff; box-shadow: 0 2px 8px rgba(64, 158, 255, 0.18); }
.type-node--empty { border-style: dashed; }
.type-node__name { font-weight: 600; color: #303133; font-size: 13px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.type-node__sub { display: flex; align-items: center; gap: 4px; }
.type-node__count { color: #909399; font-size: 11px; }
</style>
