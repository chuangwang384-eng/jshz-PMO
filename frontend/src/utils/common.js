// 跨页面公共小工具（2026-11-17 审查收口：此前散落在 ProjectDetail / ProjectBudgetPanel /
// ProjectBudgetQuotes / BudgetFeeConfig 的重复实现）

// blob 下载：createObjectURL → a.download → click → 立即回收（统一此前两种不一致实现）
export function downloadBlob(blob, fileName) {
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = fileName || 'download'
  document.body.appendChild(link)
  link.click()
  link.remove()
  URL.revokeObjectURL(url)
}

// 金额展示：空值显示 '0'，其余千分位 + 固定两位小数
export function formatMoney(v) {
  if (v === null || v === undefined || v === '') return '0'
  return Number(v).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}

// 统一提取接口错误文案：axios 响应 message 优先，其次 Error.message，最后调用方兜底文案
export function apiErrorMessage(e, fallback = '操作失败') {
  return e?.response?.data?.message || e?.message || fallback
}
