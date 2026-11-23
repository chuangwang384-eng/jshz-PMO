<template>
  <div class="crop-avatar">
    <div class="crop-main">
      <div class="crop-view">
        <div ref="stageEl" class="crop-stage" role="group" tabindex="0" aria-label="头像裁剪区，滚轮缩放，方向键移动图片"
          @wheel="wheelZoom"
          @pointerdown="startDrag" @pointermove="moveImage" @lostpointercapture="finishDrag" @keydown="nudgeImage">
          <img ref="imgEl" :src="src" :style="imageStyle" alt="证件照" draggable="false" @load="onLoad">
          <div v-if="ready" class="crop-ring" aria-hidden="true"></div>
        </div>
      </div>
      <div class="crop-tools">
        <span class="crop-status" role="status">{{ statusText }}</span>
        <el-button size="small" :disabled="!ready" aria-label="缩小图片" @click="scaleImage(1.12)">−</el-button>
        <el-button size="small" :disabled="!ready" aria-label="放大图片" @click="scaleImage(1 / 1.12)">＋</el-button>
        <el-button size="small" :loading="detecting" @click="detectFace(true)">重新识别</el-button>
        <el-button size="small" @click="reset">重置</el-button>
      </div>
    </div>
    <div class="crop-side">
      <div class="crop-out" :class="{ 'has-frame': frame }" :style="frame ? {
        '--frame-size': `${frameFit.scale}%`, '--frame-x': `${50 + frameFit.x}%`, '--frame-y': `${50 + frameFit.y}%`
      } : {}">
        <img v-if="modelValue" :src="modelValue" alt="圆形头像预览">
        <span v-else class="crop-out-empty">{{ ready ? '生成中' : '等待照片' }}</span>
        <img v-if="frame" class="crop-frame" :src="frame" alt="">
      </div>
      <p class="crop-note">输出 400×400 圆形透明图（WebP）</p>
    </div>
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, ref, watch } from 'vue'

// 圆形证件照裁剪：载入照片后自动人脸识别预定位（tiny 模型本地加载，内网可用），
// 固定圆框，拖动或缩放原图微调。显示和 canvas 裁剪共用原图坐标。
// 消费方：ShowWizard.vue 专家头像。选区变化自动重导出。
const props = defineProps({
  src: { type: String, default: '' },
  modelValue: { type: String, default: '' },
  frame: { type: String, default: '' },
  frameFit: { type: Object, default: () => ({ scale: 82, x: 0, y: 0 }) }
})
const emit = defineEmits(['update:modelValue', 'detecting'])

const stageEl = ref(null)
const imgEl = ref(null)
const ready = ref(false)
const detecting = ref(false)
const statusText = ref('等待照片')
const zoomPct = ref(62)
const cx = ref(50)
const cy = ref(50)
const naturalW = ref(1)
const naturalH = ref(1)
const frameRatio = 0.78
let timer = null
let detectToken = 0
let userTouched = false
let drag = null

const diameter = computed(() => Math.min(naturalW.value, naturalH.value) * zoomPct.value / 100)
const imageStyle = computed(() => ready.value ? {
  width: `${100 * frameRatio * naturalW.value / diameter.value}%`,
  translate: `${-cx.value}% ${-cy.value}%`
} : { visibility: 'hidden' })
function scaleImage(factor) {
  const next = zoomPct.value * factor
  if (!ready.value || !Number.isFinite(next) || next <= 0) return
  markManual()
  zoomPct.value = next
}

function wheelZoom(event) {
  if (!ready.value || !event.deltaY || event.ctrlKey || event.metaKey) return
  event.preventDefault()
  const delta = event.deltaY * (event.deltaMode === 1 ? 16 : event.deltaMode === 2 ? 248 : 1)
  scaleImage(1.12 ** Math.max(-1, Math.min(1, delta / 100)))
}

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

function onLoad() {
  naturalW.value = imgEl.value.naturalWidth || 1
  naturalH.value = imgEl.value.naturalHeight || 1
  ready.value = true
  reset()
  detectFace(false)
}

function reset() {
  cancelDetection()
  userTouched = false
  statusText.value = '拖动图片，滚轮缩放'
  cx.value = 50
  // 人脸通常在照片上半部，圆心默认放 42% 高度处（识别失败时的兜底）；默认框放大到含肩
  cy.value = 42
  zoomPct.value = 62
  scheduleRender()
}

function cancelDetection() {
  detectToken += 1
  detecting.value = false
}

function markManual() {
  cancelDetection()
  userTouched = true
  statusText.value = '手动调整中'
}

function startDrag(event) {
  if (!ready.value || drag || event.button !== 0) return
  markManual()
  drag = { id: event.pointerId, x: event.clientX, y: event.clientY, cx: cx.value, cy: cy.value }
  event.currentTarget.setPointerCapture(event.pointerId)
}

function moveImage(event) {
  if (drag?.id !== event.pointerId) return
  const width = stageEl.value.getBoundingClientRect().width
  const ratio = diameter.value / (width * frameRatio)
  cx.value = clamp(drag.cx - (event.clientX - drag.x) * ratio * 100 / naturalW.value)
  cy.value = clamp(drag.cy - (event.clientY - drag.y) * ratio * 100 / naturalH.value)
}

function finishDrag(event) {
  if (drag?.id !== event.pointerId) return
  drag = null
  render()
  statusText.value = '已按手动位置生成头像'
}

function nudgeImage(event) {
  const offsets = { ArrowLeft: [-1, 0], ArrowRight: [1, 0], ArrowUp: [0, -1], ArrowDown: [0, 1] }
  const offset = offsets[event.key]
  if (!ready.value || !offset) return
  event.preventDefault()
  markManual()
  cx.value = clamp(cx.value - offset[0])
  cy.value = clamp(cy.value - offset[1])
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
      statusText.value = '未识别到人脸，请拖动或缩放图片'
    }
  } catch {
    if (token === detectToken) statusText.value = '自动识别不可用，请拖动或缩放图片'
  } finally {
    if (token === detectToken) detecting.value = false
  }
}

function applyFaceBox(box) {
  const min = Math.min(naturalW.value, naturalH.value)
  // 圆框直径取脸框长边约 2.2 倍（覆盖发型与肩部），圆心用脸框几何中心略下移露出肩颈
  const rPx = Math.min(Math.max(box.width, box.height) * 1.1, min / 2)
  cx.value = clamp(((box.x + box.width / 2) / naturalW.value) * 100)
  cy.value = clamp(((box.y + box.height * 0.66) / naturalH.value) * 100)
  zoomPct.value = Math.max(16, Math.min(100, Math.round((rPx / (min / 2)) * 100)))
  statusText.value = '已自动识别人脸，可微调后保存'
  scheduleRender()
}

function scheduleRender() {
  clearTimeout(timer)
  timer = setTimeout(render, 200)
}

function render() {
  clearTimeout(timer)
  timer = null
  const img = imgEl.value
  if (!img || !ready.value) return
  const size = 400
  const r = diameter.value / 2
  const sx = (naturalW.value * cx.value) / 100
  const sy = (naturalH.value * cy.value) / 100
  const canvas = document.createElement('canvas')
  canvas.width = size
  canvas.height = size
  const ctx = canvas.getContext('2d')
  ctx.beginPath()
  ctx.arc(size / 2, size / 2, size / 2, 0, Math.PI * 2)
  ctx.clip()
  try {
    ctx.drawImage(img, sx - r, sy - r, r * 2, r * 2, 0, 0, size, size)
    // 优先 WebP（同质量体积约为 PNG 的 1/5，透明通道保留），不支持时回退 PNG
    const url = canvas.toDataURL('image/webp', 0.9)
    emit('update:modelValue', url.startsWith('data:image/webp') ? url : canvas.toDataURL('image/png'))
  } catch {
    emit('update:modelValue', '')
  }
}

watch(detecting, value => emit('detecting', value))
onBeforeUnmount(() => {
  cancelDetection()
  if (timer !== null) render()
  emit('detecting', false)
})

watch([zoomPct, cx, cy], scheduleRender)
watch(() => props.src, () => {
  cancelDetection()
  clearTimeout(timer)
  timer = null
  drag = null
  ready.value = false
  emit('update:modelValue', '')
})

defineExpose({ render })
</script>

<style scoped>
.crop-avatar { display: flex; gap: 14px; align-items: stretch; }
.crop-main { flex: 1; min-width: 0; display: flex; flex-direction: column; gap: 8px; }
.crop-view { display: flex; justify-content: center; }
.crop-stage { position: relative; width: min(248px, 100%); aspect-ratio: 1; overflow: hidden; background: #0b0e14;
  border-radius: 10px; cursor: grab; touch-action: none; }
.crop-stage:active { cursor: grabbing; }
.crop-stage:focus-visible { outline: 2px solid #f2c66d; outline-offset: 3px; }
.crop-stage img { position: absolute; top: 50%; left: 50%; max-width: none; display: block; user-select: none; pointer-events: none; }
.crop-ring { position: absolute; left: 50%; top: 50%; translate: -50% -50%; width: 78%; aspect-ratio: 1;
  box-sizing: border-box; border: 2px solid #f2c66d; border-radius: 50%; pointer-events: none;
  box-shadow: 0 0 0 9999px rgba(6, 8, 12, .55), inset 0 0 14px rgba(242, 198, 109, .3); }
.crop-tools { display: flex; align-items: center; gap: 10px; }
.crop-status { font-size: 12px; color: rgba(255, 243, 220, .65); flex: 1; min-width: 0; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.crop-side { flex: none; width: 132px; display: flex; flex-direction: column; gap: 6px; align-items: center; }
.crop-out { width: 108px; height: 108px; border-radius: 50%; overflow: hidden; background:
  radial-gradient(circle at 50% 40%, #2a2f3a, #14171e); border: 2px solid rgba(242, 198, 109, .5); display: flex; align-items: center; justify-content: center; }
.crop-out img { width: 100%; height: 100%; object-fit: cover; }
.crop-out-empty { font-size: 12px; color: rgba(255, 243, 220, .5); }
.crop-note { margin: 0; font-size: 11px; color: rgba(255, 243, 220, .45); text-align: center; }
.crop-out { position: relative; }
.crop-out.has-frame { border-color: transparent; overflow: visible; }
.crop-out.has-frame img:not(.crop-frame) { position: absolute; width: var(--frame-size); height: var(--frame-size);
  left: var(--frame-x); top: var(--frame-y); transform: translate(-50%, -50%); border-radius: 50%; }
.crop-out .crop-frame { position: absolute; inset: 0; width: 100%; height: 100%; object-fit: contain; pointer-events: none; }
</style>
