<template>
  <div class="mini-flow">
    <div class="mini-flow__wrap" :title="tip">
      <svg
        class="mini-flow__svg"
        viewBox="0 0 120 34"
        xmlns="http://www.w3.org/2000/svg"
      >
        <!-- 连线 -->
        <path
          v-for="(edge, i) in linePaths"
          :key="'l' + i"
          :d="edge"
          fill="none"
          stroke="#c0c4cc"
          stroke-width="1.5"
        />
        <!-- 阶段点 -->
        <circle
          v-for="(p, i) in points"
          :key="'p' + i"
          :cx="p.x"
          :cy="p.y"
          r="6"
          :fill="p.active ? '#409eff' : '#c0c4cc'"
          stroke="#fff"
          stroke-width="1"
        />
        <!-- 未配置提示点 -->
        <text
          v-if="!points.length"
          x="60"
          y="20"
          text-anchor="middle"
          font-size="9"
          fill="#c0c4cc"
        >未配置</text>
      </svg>
      <span v-if="points.length" class="mini-flow__count">{{ points.length }}步</span>
    </div>
    <el-tooltip placement="top" effect="dark" :content="tip" :disabled="!tip">
      <div class="mini-flow__label">{{ label }}</div>
    </el-tooltip>
  </div>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  phases: { type: Array, default: () => [] }, // [{phaseId, phaseName, active}]
  label: { type: String, default: '' }
})

const points = computed(() => {
  const n = props.phases.length
  if (!n) return []
  const step = n === 1 ? 0 : 100 / (n - 1)
  return props.phases.map((p, i) => ({
    x: 10 + i * step,
    y: 17,
    active: p.active !== false && p.active !== 0
  }))
})

const linePaths = computed(() => {
  const pts = points.value
  if (pts.length < 2) return []
  const paths = []
  for (let i = 0; i < pts.length - 1; i++) {
    paths.push(`M ${pts[i].x} ${pts[i].y} L ${pts[i + 1].x} ${pts[i + 1].y}`)
  }
  return paths
})

const tip = computed(() => {
  if (!props.phases.length) return `${props.label}：未配置流程（使用全部启用阶段）`
  return `${props.label}：${props.phases.map((p) => p.phaseName).join(' → ')}`
})
</script>

<style scoped>
.mini-flow {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 1px;
  width: 100%;
}
.mini-flow__wrap { position: relative; width: 100%; }
.mini-flow__svg {
  display: block; width: 100%; height: auto; border-radius: 4px; background: #fafbfc;
  transition: box-shadow 0.18s ease;
}
.mini-flow__wrap:hover .mini-flow__svg {
  box-shadow: 0 2px 10px rgba(0, 0, 0, 0.14);
}
.mini-flow__count {
  position: absolute;
  right: -2px;
  top: -2px;
  font-size: 10px;
  line-height: 14px;
  padding: 0 4px;
  border-radius: 7px;
  background: #409eff;
  color: #fff;
}
.mini-flow__label {
  font-size: 11px;
  color: #909399;
  max-width: 100%;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
</style>
