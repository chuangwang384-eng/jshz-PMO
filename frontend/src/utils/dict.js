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
// ProjectList/ProjectDetail/ProjectEdit/ProjectFinanceForm 统一引用此处；
// 新增/改文案须与后端码集成对修改（AGENTS 17.7）。
export const PROJECT_TYPE_LABELS = { PLATFORM: '平台类项目', DIRECT_PAY: '直付类项目', KEY: '重点类项目' }

// 项目阶段名（2026-11-26 P2-15）：后端权威=com.jshz.expert.common.ProjectPhaseNames，
// 本映射为唯一前端展示副本；阶段名同时是「阶段资料区」判定依据，改名须成对修改两侧。
export const PROJECT_PHASE_NAMES = {
  BUDGET_MAKING: '会议预算制作',
  FINANCE_UPLOAD: '会前财务资料上传',
  SCHEDULE_UPLOAD: '日程上传',
  SHOW_MAKING: '串场制作',
  SETTLEMENT_UPLOAD: '结算资料上传',
  CLOSEOUT: '会议费用结算及项目结项'
}


// 收费标准库类目（2026-11-26 审查收口 P3-16）：后端 project_budget_fee_standard.catalog 自由码，
// 本映射为内置 6 类展示副本；BudgetFeeConfig（管理页）与 ProjectBudgetPanel（点选弹窗）共用，
// 不得再在页面内复制字面量（AGENTS 19.1 第 3 处复制即收口）。
export const BUDGET_FEE_CATALOGS = {
  ELEC: '电子物料', PRINT: '小型制作物料', ONLINE: '线上会议及直播',
  BUILD: '大型搭建物料', STAFF: '人员服务', SPECIAL: '非常规物料'
}

// 项目会议预算单状态（2026-11-26 审查收口 P3-21，AGENTS 17.7 成对登记）：
// 后端权威=com.jshz.expert.entity.ProjectBudgetStatus 枚举（DRAFT/CONFIRMED），
// 本映射为唯一前端展示副本；新增状态/改文案须同一次提交成对修改两侧。
export const BUDGET_STATUS_TEXT = { DRAFT: '草稿', CONFIRMED: '已确认' }
