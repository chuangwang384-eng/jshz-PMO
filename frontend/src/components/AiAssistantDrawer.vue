<template>
  <div>
    <!-- 可拖拽悬浮球：拖动记忆位置（localStorage），点击打开抽屉（2026-11-18） -->
    <button
      ref="fabRef"
      class="ai-fab"
      :class="{ dragging: dragging }"
      :style="fabStyle"
      title="AI 助手（可拖动）"
      aria-label="AI 助手"
      @pointerdown="onPointerDown"
    >
      <span class="ai-fab-icon">✦</span>
      <span class="ai-fab-text">AI 助手</span>
    </button>

    <el-drawer v-model="open" title="AI 助手 · 系统使用向导" size="420px" :append-to-body="true">
      <div class="ai-chat">
        <div ref="listRef" class="ai-msg-list">
          <div v-if="!messages.length" class="ai-welcome">
            <div class="ai-welcome-badge">✦</div>
            <div class="ai-welcome-title">你好，我是系统使用助手</div>
            <div class="ai-welcome-sub">系统操作问题我给指南和直达入口，也可以随便聊聊</div>
            <div class="ai-chips">
              <button v-for="s in suggestions" :key="s" class="ai-chip" @click="send(s)">{{ s }}</button>
            </div>
          </div>
          <div v-for="(m, i) in messages" :key="i" :class="['ai-msg', m.role]">
            <template v-if="m.role === 'user'">
              <div class="ai-bubble user">{{ m.text }}</div>
            </template>
            <template v-else>
              <div class="ai-avatar">✦</div>
              <div class="ai-bubble bot">
                <div v-if="m.title" class="ai-card-title">{{ m.title }}</div>
                <div class="ai-answer">{{ m.text }}</div>
                <ol v-if="m.steps && m.steps.length" class="ai-steps">
                  <li v-for="(s, j) in m.steps" :key="j">{{ s }}</li>
                </ol>
                <el-button v-if="m.linkUrl" type="primary" size="small" style="margin-top: 8px" @click="goto(m.linkUrl)">
                  {{ m.linkLabel || '前往操作' }} →
                </el-button>
              </div>
            </template>
          </div>
          <div v-if="loading" class="ai-msg bot"><div class="ai-avatar">✦</div><div class="ai-bubble bot ai-thinking">正在思考…</div></div>
        </div>
        <div class="ai-input-row">
          <el-input v-model="input" placeholder="例如：怎么新建项目？" :disabled="loading"
                    @keyup.enter="send(input)" maxlength="200"/>
          <el-button type="primary" :loading="loading" :disabled="!input.trim()" @click="send(input)">发送</el-button>
        </div>
      </div>
    </el-drawer>
  </div>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { askAssistant } from '../api/aiAssistant'

const router = useRouter()
const open = ref(false)
const input = ref('')
const loading = ref(false)
const messages = ref([])
const listRef = ref(null)
const suggestions = ['怎么新建项目？', '线下预算怎么填？', '日程怎么上传？', '收费标准在哪改？']

// ==================== 悬浮球拖拽（2026-11-18） ====================
const FAB_POS_KEY = 'ai-fab-pos'
const FAB_W = 108   // 与样式一致：图标+文字胶囊宽度估值，clamp 用
const FAB_H = 42
const fabRef = ref(null)
const dragging = ref(false)
const pos = reactive(loadPos())
let start = null
let moved = false

function loadPos() {
  try {
    const saved = JSON.parse(localStorage.getItem(FAB_POS_KEY) || 'null')
    if (saved && typeof saved.x === 'number' && typeof saved.y === 'number') return saved
  } catch { /* 忽略脏数据 */ }
  return { x: window.innerWidth - 130, y: window.innerHeight - 78 }
}
const fabStyle = computed(() => ({ left: pos.x + 'px', top: pos.y + 'px' }))

function clamp(x, y) {
  const w = fabRef.value?.offsetWidth || FAB_W
  const h = fabRef.value?.offsetHeight || FAB_H
  return {
    x: Math.min(Math.max(4, x), window.innerWidth - w - 4),
    y: Math.min(Math.max(4, y), window.innerHeight - h - 4)
  }
}
function onPointerDown(e) {
  if (e.button !== 0) return
  e.preventDefault()
  const rect = fabRef.value.getBoundingClientRect()
  start = { px: e.clientX, py: e.clientY, ox: rect.left, oy: rect.top }
  moved = false
  dragging.value = true
  fabRef.value.setPointerCapture(e.pointerId)
  fabRef.value.addEventListener('pointermove', onPointerMove)
  fabRef.value.addEventListener('pointerup', onPointerUp, { once: true })
}
function onPointerMove(e) {
  if (!start) return
  const dx = e.clientX - start.px
  const dy = e.clientY - start.py
  if (!moved && Math.hypot(dx, dy) < 4) return // 位移阈值内视为点击
  moved = true
  const next = clamp(start.ox + dx, start.oy + dy)
  pos.x = next.x
  pos.y = next.y
}
function onPointerUp() {
  fabRef.value?.removeEventListener('pointermove', onPointerMove)
  dragging.value = false
  start = null
  if (moved) {
    localStorage.setItem(FAB_POS_KEY, JSON.stringify({ x: pos.x, y: pos.y }))
  } else {
    open.value = true // 未拖动 = 点击
  }
}
function onViewportChange() {
  const next = clamp(pos.x, pos.y)
  pos.x = next.x
  pos.y = next.y
}
onMounted(() => window.addEventListener('resize', onViewportChange))
onBeforeUnmount(() => {
  window.removeEventListener('resize', onViewportChange)
  fabRef.value?.removeEventListener('pointermove', onPointerMove)
})

async function send(text) {
  const q = (text || '').trim()
  if (!q || loading.value) return
  messages.value.push({ role: 'user', text: q })
  input.value = ''
  loading.value = true
  scrollBottom()
  try {
    const data = await askAssistant(q)
    messages.value.push({
      role: 'bot',
      text: data?.answer || '暂时没有匹配的内容，请联系管理员。',
      title: data?.matched ? data.title : null,
      steps: data?.steps || [],
      linkUrl: data?.linkUrl || null,
      linkLabel: data?.linkLabel || null
    })
    // 行动意图（帮我新建项目…）：展示卡片后自动跳转（仅导航，不代执行）
    if (data?.autoNavigate && data?.linkUrl) {
      const target = data.linkUrl
      setTimeout(() => goto(target), 900)
    }
  } catch (e) {
    messages.value.push({ role: 'bot', text: e?.response?.data?.message || '助手暂时不可用，请稍后再试。' })
  } finally {
    loading.value = false
    scrollBottom()
  }
}

function goto(url) {
  open.value = false
  router.push(url).catch(() => ElMessage.warning('无法跳转，请检查权限'))
}

function scrollBottom() {
  nextTick(() => {
    if (listRef.value) listRef.value.scrollTop = listRef.value.scrollHeight
  })
}
</script>

<style scoped>
/* 悬浮球：可拖拽胶囊（位置由内联 left/top 控制） */
.ai-fab {
  position: fixed; z-index: 2000;
  display: flex; align-items: center; gap: 7px;
  padding: 9px 16px 9px 12px; border: none; border-radius: 24px; cursor: grab;
  background: linear-gradient(135deg, #409eff, #7b5cff); color: #fff;
  font-size: 14px; box-shadow: 0 4px 16px rgba(64, 158, 255, .38), 0 1px 3px rgba(0,0,0,.12);
  transition: box-shadow .15s, transform .15s;
  touch-action: none; user-select: none;
}
.ai-fab:hover { filter: brightness(1.08); transform: translateY(-1px); box-shadow: 0 6px 20px rgba(64, 158, 255, .45), 0 2px 4px rgba(0,0,0,.14); }
.ai-fab.dragging { cursor: grabbing; transition: none; transform: scale(1.03); opacity: .92; }
.ai-fab-icon {
  display: inline-flex; align-items: center; justify-content: center;
  width: 22px; height: 22px; border-radius: 50%;
  background: rgba(255, 255, 255, .22); font-size: 13px;
}
/* 抽屉内对话区 */
.ai-chat { display: flex; flex-direction: column; height: 100%; }
.ai-msg-list { flex: 1; overflow-y: auto; padding: 4px 2px 12px; }
.ai-welcome { text-align: center; padding: 26px 10px 20px; }
.ai-welcome-badge {
  display: inline-flex; align-items: center; justify-content: center;
  width: 44px; height: 44px; border-radius: 50%; margin-bottom: 10px;
  background: linear-gradient(135deg, #409eff, #7b5cff); color: #fff; font-size: 20px;
  box-shadow: 0 4px 12px rgba(123, 92, 255, .35);
}
.ai-welcome-title { font-size: 16px; font-weight: 700; }
.ai-welcome-sub { color: var(--el-text-color-secondary); font-size: 13px; margin: 8px 0 16px; }
.ai-chips { display: flex; flex-wrap: wrap; gap: 8px; justify-content: center; }
.ai-chip {
  border: 1px solid var(--el-border-color); background: #fff; border-radius: 16px;
  padding: 6px 13px; font-size: 12px; cursor: pointer; color: var(--el-text-color-regular);
  transition: color .12s, border-color .12s, transform .12s;
}
.ai-chip:hover { border-color: var(--el-color-primary); color: var(--el-color-primary); transform: translateY(-1px); }
.ai-msg { display: flex; margin-bottom: 12px; align-items: flex-start; }
.ai-msg.user { justify-content: flex-end; }
.ai-avatar {
  flex: none; width: 26px; height: 26px; border-radius: 50%; margin-right: 8px;
  display: inline-flex; align-items: center; justify-content: center;
  background: linear-gradient(135deg, #409eff, #7b5cff); color: #fff; font-size: 13px;
}
.ai-bubble { max-width: 82%; padding: 10px 13px; border-radius: 12px; font-size: 13px; line-height: 1.6; }
.ai-msg.user .ai-bubble.user {
  background: linear-gradient(135deg, #409eff, #6a5cff); color: #fff;
  border-top-right-radius: 4px;
}
.ai-bubble.bot { background: #f5f7fa; color: var(--el-text-color-primary); border-top-left-radius: 4px; }
.ai-card-title { font-weight: 700; margin-bottom: 6px; }
.ai-steps { margin: 8px 0 0; padding-left: 18px; }
.ai-steps li { margin-bottom: 3px; }
.ai-thinking { color: var(--el-text-color-secondary); }
.ai-input-row { display: flex; gap: 8px; padding-top: 10px; border-top: 1px solid var(--el-border-color-light); }
</style>
