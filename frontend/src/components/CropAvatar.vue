<template>
  <div class="crop-avatar">
    <div class="crop-main">
      <div class="crop-view">
        <div ref="stageEl" class="crop-stage">
          <img ref="imgEl" :src="src" alt="证件照" draggable="false" @load="onLoad">
          <div v-if="ready" class="crop-ring" :style="ringStyle" @pointerdown.prevent="startDrag"></div>
        </div>
      </div>
      <div class="crop-tools">
        <span class="crop-hint">拖动圆框 · 调整大小</span>
        <el-slider v-model="zoomPct" :min="16" :max="100" style="flex: 1" />
        <el-button size="small" @click="reset">重置</el-button>
      </div>
    </div>
    <div class="crop-side">
      <div class="crop-out">
        <img v-if="modelValue" :src="modelValue" alt="圆形头像预览">
        <span v-else class="crop-out-empty">{{ ready ? '生成中' : '等待照片' }}</span>
      </div>
      <p class="crop-note">输出 400×400 圆形透明 PNG</p>
    </div>
  </div>
</template>

<script setup>
import { computed, ref, watch } from 'vue'

// 圆形证件照裁剪：拖动圆框定位置，滑杆调半径，canvas 输出圆形透明 PNG（零后端依赖）
// 消费方：ShowWizard.vue 第二步专家头像；选区变化自动重导出
const props = defineProps({
  src: { type: String, default: '' },
  modelValue: { type: String, default: '' }
})
const emit = defineEmits(['update:modelValue'])

const stageEl = ref(null)
const imgEl = ref(null)
const ready = ref(false)
const zoomPct = ref(46)
// 选区用相对图片显示尺寸的百分比坐标，避免监听容器尺寸换算
const cx = ref(50)
const cy = ref(50)
let naturalW = 1
let naturalH = 1
let timer = null
const ringWidthPct = ref(46)

const ringStyle = computed(() => ({
  left: `${cx.value}%`,
  top: `${cy.value}%`,
  width: `${ringWidthPct.value}%`,
  transform: 'translate(-50%, -50%)'
}))

function onLoad() {
  naturalW = imgEl.value.naturalWidth || 1
  naturalH = imgEl.value.naturalHeight || 1
  ready.value = true
  reset()
}

function reset() {
  cx.value = 50
  // 人脸通常在照片上半部，圆心默认放 42% 高度处
  cy.value = 42
  zoomPct.value = 46
  syncRingWidth()
}

// 圆环显示宽度按「短边占比」换算为相对图片宽度的百分比，保证显示环与导出圆一致
function syncRingWidth() {
  ringWidthPct.value = (Math.min(naturalW, naturalH) / naturalW) * zoomPct.value
}

function startDrag(event) {
  const stage = stageEl.value
  if (!stage) return
  const move = e => {
    const rect = stage.getBoundingClientRect()
    if (!rect.width || !rect.height) return
    const x = ((e.clientX - rect.left) / rect.width) * 100
    const y = ((e.clientY - rect.top) / rect.height) * 100
    cx.value = Math.max(0, Math.min(100, Number(x.toFixed(2))))
    cy.value = Math.max(0, Math.min(100, Number(y.toFixed(2))))
  }
  const up = () => {
    window.removeEventListener('pointermove', move)
    window.removeEventListener('pointerup', up)
  }
  window.addEventListener('pointermove', move)
  window.addEventListener('pointerup', up)
  move(event)
}

function render() {
  const img = imgEl.value
  if (!img || !ready.value) return
  const size = 400
  const r = (Math.min(naturalW, naturalH) / 2) * (zoomPct.value / 100)
  const sx = (naturalW * cx.value) / 100
  const sy = (naturalH * cy.value) / 100
  const canvas = document.createElement('canvas')
  canvas.width = size
  canvas.height = size
  const ctx = canvas.getContext('2d')
  ctx.beginPath()
  ctx.arc(size / 2, size / 2, size / 2, 0, Math.PI * 2)
  ctx.clip()
  try {
    ctx.drawImage(img, sx - r, sy - r, r * 2, r * 2, 0, 0, size, size)
    emit('update:modelValue', canvas.toDataURL('image/png'))
  } catch {
    emit('update:modelValue', '')
  }
}

watch(zoomPct, () => {
  syncRingWidth()
  clearTimeout(timer)
  timer = setTimeout(render, 200)
})
watch([cx, cy], () => {
  clearTimeout(timer)
  timer = setTimeout(render, 200)
}, { deep: true })

watch(() => props.src, () => {
  ready.value = false
  emit('update:modelValue', '')
})

defineExpose({ render })
</script>

<style scoped>
.crop-avatar { display: flex; gap: 14px; align-items: stretch; }
.crop-main { flex: 1; min-width: 0; display: flex; flex-direction: column; gap: 8px; }
.crop-view { display: flex; justify-content: center; }
.crop-stage { position: relative; display: inline-flex; max-width: 100%; overflow: hidden; background: #0b0e14; border-radius: 10px; }
.crop-stage img { max-width: 100%; max-height: 300px; object-fit: contain; display: block; user-select: none; }
.crop-ring { position: absolute; aspect-ratio: 1 / 1; border: 2px solid #f2c66d; border-radius: 50%;
  box-shadow: 0 0 0 9999px rgba(6, 8, 12, .55), inset 0 0 14px rgba(242, 198, 109, .3); cursor: grab; }
.crop-ring:active { cursor: grabbing; }
.crop-tools { display: flex; align-items: center; gap: 10px; }
.crop-hint { font-size: 12px; color: rgba(255, 243, 220, .6); flex: none; }
.crop-side { flex: none; width: 132px; display: flex; flex-direction: column; gap: 6px; align-items: center; }
.crop-out { width: 108px; height: 108px; border-radius: 50%; overflow: hidden; background:
  radial-gradient(circle at 50% 40%, #2a2f3a, #14171e); border: 2px solid rgba(242, 198, 109, .5); display: flex; align-items: center; justify-content: center; }
.crop-out img { width: 100%; height: 100%; object-fit: cover; }
.crop-out-empty { font-size: 12px; color: rgba(255, 243, 220, .5); }
.crop-note { margin: 0; font-size: 11px; color: rgba(255, 243, 220, .45); text-align: center; }
</style>
