// 跨页面共享业务字典（2026-11-13 复用治理收敛）
// 约定（AGENTS.md 19 节）：同一后端枚举的展示文案/颜色在多个页面重复出现时，
// 唯一定义收敛到本文件；页面私有字典（仅单页使用的枚举域）保留在页面内。
// 枚举域不同的字典（如日程 parseStatus vs 合规文档解析状态）不得合并。

// 客户关系方向（后端 relation_types.direction 枚举；仅供本文件 relationDirectionLabel 使用）
const RELATION_DIRECTION_TEXT = { DIRECTED: '单向', BIDIRECTED: '双向', UNDIRECTED: '无向' }

export function relationDirectionLabel(direction) {
  return RELATION_DIRECTION_TEXT[direction] || '单向'
}

// 项目类型展示名（2026-11-26 审查收口）：后端权威=ProjectService.PROJECT_TYPES 码集
// （PLATFORM/DIRECT_PAY/KEY，仅校验码不存文案）；本映射为唯一前端展示副本，
// ProjectList/ProjectDetail 引用此处；新增/改文案须与后端码集成对修改（AGENTS 17.7）。
export const PROJECT_TYPE_LABELS = { PLATFORM: '平台类项目', DIRECT_PAY: '直付类项目', KEY: '重点类项目' }
