// 跨页面共享业务字典（2026-11-13 复用治理收敛）
// 约定（AGENTS.md 19 节）：同一后端枚举的展示文案/颜色在多个页面重复出现时，
// 唯一定义收敛到本文件；页面私有字典（仅单页使用的枚举域）保留在页面内。
// 枚举域不同的字典（如日程 parseStatus vs 合规文档解析状态）不得合并。

// 客户关系方向（后端 relation_types.direction 枚举；4 个客户相关页面共用）
export const RELATION_DIRECTION_TEXT = { DIRECTED: '单向', BIDIRECTED: '双向', UNDIRECTED: '无向' }

export function relationDirectionLabel(direction) {
  return RELATION_DIRECTION_TEXT[direction] || '单向'
}
