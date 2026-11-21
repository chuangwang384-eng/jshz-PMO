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
        <span class="crop-status">{{ statusText }}</span>
        <el-slider v-model="zoomPct" :min="16" :max="100" style="flex: 1" />
        <el-button size="small" :loading="detecting" @click="detectFace(true)">重新识别</el-button>
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

// 圆形证件照裁剪：载入照片后自动人脸识别预定位（tiny 模型本地加载，内网可用），
// 用户可拖动圆框/缩放/重置微调；canvas 输出 400×400 圆形透明 PNG，零后端依赖。
// 消费方：ShowWizard.vue 专家头像。选区变化自动重导出。
const props = defineProps({
  src: { type: String, default: '' },
  modelValue: { type: String, default: '' }
})
const emit = defineEmits(['update:modelValue'])

const stageEl = ref(null)
const imgEl = ref(null)
const ready = ref(false)
const detecting = ref(false)
const statusText = ref('等待照片')
const zoomPct = ref(46)
// 选区用相对图片显示尺寸的百分比坐标，避免监听容器尺寸换算
const cx = ref(50)
const cy = ref(50)
let naturalW = 1
let naturalH = 1
let timer = null
let ringWidthPct = 46
let detectToken = 0
let userTouched = false

const ringStyle = computed(() => ({
  left: `${cx.value}%`,
  top: `${cy.value}%`,
  width: `${ringWidthPct}%`,
  transform: 'translate(-50%, -50%)'
}))

// face-api 模型只加载一次；tiny 模型 ~190KB，从 public/models 本地加载（内网无外网依赖）
let faceApiPromise = null
function loadFaceApi() {
  if (!faceApiPromise) {
    faceApiPromise = import('@vladmandic/face-api').then(async api => {
      await api.nets.tinyFaceDetector.loadFromUri('/models')
      return api
    }).catch(e => {
      faceApiPromise = null
      throw e
    })
  }
  return faceApiPromise
}

function clamp(v) {
  return Math.max(2, Math.min(98, Number(v.toFixed(2))))
}

function syncRingWidth() {
  ringWidthPct = (Math.min(naturalW, naturalH) / naturalW) * zoomPct.value
}

function onLoad() {
  naturalW = imgEl.value.naturalWidth || 1
  naturalH = imgEl.value.naturalHeight || 1
  ready.value = true
  reset()
  detectFace(false)
}

function reset() {
  userTouched = false
  cx.value = 50
  // 人脸通常在照片上半部，圆心默认放 42% 高度处（识别失败时的兜底）
  cy.value = 42
  zoomPct.value = 46
  syncRingWidth()
  scheduleRender()
}

function startDrag(event) {
  userTouched = true
  statusText.value = '手动调整中'
  const stage = stageEl.value
  if (!stage) return
  const move = e => {
    const rect = stage.getBoundingClientRect()
    if (!rect.width || !rect.height) return
    const x = ((e.clientX - rect.left) / rect.width) * 100
    const y = ((e.clientY - rect.top) / rect.height) * 100
    cx.value = clamp(x)
    cy.value = clamp(y)
  }
  const up = () => {
    window.removeEventListener('pointermove', move)
    window.removeEventListener('pointerup', up)
    statusText.value = '已按手动位置生成头像'
  }
  window.addEventListener('pointermove', move)
  window.addEventListener('pointerup', up)
  move(event)
}

async function detectFace(force) {
  if (!ready.value || (!force && userTouched)) return
  const token = ++detectToken
  detecting.value = true
  statusText.value = '正在自动识别人脸…'
  try {
    const api = await loadFaceApi()
    const det = await api.detectSingleFace(imgEl.value,
      new api.TinyFaceDetectorOptions({ inputSize: 320, scoreThreshold: 0.3 }))
    if (token !== detectToken) return
    if (det && det.box && det.box.width > 0) {
      applyFaceBox(det.box)
    } else {
      statusText.value = '未识别到人脸，请手动调整圆框'
    }
  } catch {
    if (token === detectToken) statusText.value = '自动识别不可用，请手动调整圆框'
  } finally {
    if (token === detectToken) detecting.value = false
  }
}

function applyFaceBox(box) {
  const min = Math.min(naturalW, naturalH)
  // 圆框直径取脸框长边约 1.24 倍（留出发型/肩部余量），圆心用脸框几何中心略上移
  const rPx = Math.min(Math.max(box.width, box.height) * 0.62, min / 2)
  cx.value = clamp(((box.x + box.width / 2) / naturalW) * 100)
  cy.value = clamp(((box.y + box.height / 2) / naturalH) * 100)
  zoomPct.value = Math.max(16, Math.min(100, Math.round((rPx / (min / 2)) * 100)))
  syncRingWidth()
  statusText.value = '已自动识别人脸，可微调后保存'
  scheduleRender()
}

function scheduleRender() {
  clearTimeout(timer)
  timer = setTimeout(render, 200)
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
  scheduleRender()
})
watch([cx, cy], () => scheduleRender(), { deep: true })
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
.crop-status { font-size: 12px; color: rgba(255, 243, 220, .65); flex: 1; min-width: 0; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.crop-side { flex: none; width: 132px; display: flex; flex-direction: column; gap: 6px; align-items: center; }
.crop-out { width: 108px; height: 108px; border-radius: 50%; overflow: hidden; background:
  radial-gradient(circle at 50% 40%, #2a2f3a, #14171e); border: 2px solid rgba(242, 198, 109, .5); display: flex; align-items: center; justify-content: center; }
.crop-out img { width: 100%; height: 100%; object-fit: cover; }
.crop-out-empty { font-size: 12px; color: rgba(255, 243, 220, .5); }
.crop-note { margin: 0; font-size: 11px; color: rgba(255, 243, 220, .45); text-align: center; }
</style>
