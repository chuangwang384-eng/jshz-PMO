import request from './request'

// ==================== AI 助手（2026-11-16） ====================

// 问答：返回操作指引卡片（答案+跳转链接+步骤）
export function askAssistant(question) {
  return request.post('/ai-assistant/ask', { question })
}

// 知识条目管理（ai:manage）
export function listAssistantEntries() {
  return request.get('/ai-assistant/entries')
}

export function saveAssistantEntries(rows) {
  return request.put('/ai-assistant/entries', { rows })
}
