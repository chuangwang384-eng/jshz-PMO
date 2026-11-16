<template>
  <div>
    <button class="ai-fab" title="AI 助手" aria-label="AI 助手" @click="open = true">
      <span class="ai-fab-icon">✦</span>
      <span class="ai-fab-text">AI 助手</span>
    </button>

    <el-drawer v-model="open" title="AI 助手 · 系统使用向导" size="420px" :append-to-body="true">
      <div class="ai-chat">
        <div ref="listRef" class="ai-msg-list">
          <div v-if="!messages.length" class="ai-welcome">
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
          <div v-if="loading" class="ai-msg bot"><div class="ai-bubble bot ai-thinking">正在思考…</div></div>
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
import { nextTick, ref } from 'vue'
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
.ai-fab {
  position: fixed; right: 22px; bottom: 26px; z-index: 2000;
  display: flex; align-items: center; gap: 6px;
  padding: 10px 16px; border: none; border-radius: 22px; cursor: pointer;
  background: linear-gradient(135deg, #409eff, #7b5cff); color: #fff;
  font-size: 14px; box-shadow: 0 4px 14px rgba(64, 158, 255, .4);
}
.ai-fab:hover { filter: brightness(1.08); }
.ai-fab-icon { font-size: 16px; }
.ai-chat { display: flex; flex-direction: column; height: 100%; }
.ai-msg-list { flex: 1; overflow-y: auto; padding: 4px 2px 12px; }
.ai-welcome { text-align: center; padding: 30px 10px; }
.ai-welcome-title { font-size: 16px; font-weight: 700; }
.ai-welcome-sub { color: var(--el-text-color-secondary); font-size: 13px; margin: 8px 0 16px; }
.ai-chips { display: flex; flex-wrap: wrap; gap: 8px; justify-content: center; }
.ai-chip {
  border: 1px solid var(--el-border-color); background: #fff; border-radius: 14px;
  padding: 6px 12px; font-size: 12px; cursor: pointer; color: var(--el-text-color-regular);
}
.ai-chip:hover { border-color: var(--el-color-primary); color: var(--el-color-primary); }
.ai-msg { display: flex; margin-bottom: 12px; }
.ai-msg.user { justify-content: flex-end; }
.ai-bubble { max-width: 88%; padding: 10px 12px; border-radius: 10px; font-size: 13px; line-height: 1.6; }
.ai-bubble.user { background: var(--el-color-primary); color: #fff; }
.ai-bubble.bot { background: #f5f7fa; color: var(--el-text-color-primary); }
.ai-card-title { font-weight: 700; margin-bottom: 6px; }
.ai-steps { margin: 8px 0 0; padding-left: 18px; }
.ai-steps li { margin-bottom: 3px; }
.ai-thinking { color: var(--el-text-color-secondary); }
.ai-input-row { display: flex; gap: 8px; padding-top: 10px; border-top: 1px solid var(--el-border-color-light); }
</style>
