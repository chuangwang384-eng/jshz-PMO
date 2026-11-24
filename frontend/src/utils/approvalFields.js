// 审批/申请字段字典（前端唯一副本）——对应后端 ApplicationService.WHITELIST_FIELDS / PHOTO_FIELDS /
// MAX_TEXT_LENGTH / MAX_REASON_LENGTH 与 ApplicationType 枚举。
//
// 消费方：views/MyApproval.vue、components/ApprovalDiffDialog.vue、utils/format.js
// 约定（AGENTS.md 17.7 / 19.2）：后端字段白名单、状态字典或长度上限变更时，必须在同一次提交内同步本文件，
// 不得在页面/组件里再写第二份字面量副本。

/** 可编辑文本字段（顺序与后端白名单一致；含「身份证有效期」，后端 applyChanges 已支持写入） */
export const TEXT_FIELDS = [
  '姓名', '单位', '科室', '职称', '研究方向', '擅长领域',
  '联系方式', '简介', '身份证号', '银行卡号', '银行卡开户行', '身份证有效期'
]

/** 照片字段（值形如 TMP:临时文件名，审批通过后归档为正式文件名） */
export const PHOTO_FIELDS = ['照片', '身份证正面', '身份证反面']

/** 敏感字段（重置为密码框展示） */
export const SENSITIVE_FIELDS = ['身份证号', '银行卡号']

/** 合同申请仅允许修改的字段（与后端 submitContractApproval / resubmit 的 contract 分支一致） */
export const CONTRACT_FIELDS = ['身份证号', '银行卡号']

/** 对比弹窗字段展示顺序：先文本后照片（白名单外的键兜底排末尾） */
export const FIELD_ORDER = [...TEXT_FIELDS, ...PHOTO_FIELDS]

/** 单字段文本上限（后端 ApplicationService.MAX_TEXT_LENGTH） */
export const MAX_TEXT_LENGTH = 5000

/** 申请原因上限（后端 ApplicationService.MAX_REASON_LENGTH） */
export const MAX_REASON_LENGTH = 1000

/** 图片上传 accept（与后端 PhotoStorageService.ALLOWED_EXT 一致，选择前即拦截不支持的格式） */
export const PHOTO_ACCEPT = '.jpg,.jpeg,.png,.gif,.webp,.bmp'

/** 状态筛选选项（0-4，与后端 status 字典一致） */
export const STATUS_OPTIONS = [
  { value: 0, label: '待审批' },
  { value: 1, label: '已通过' },
  { value: 2, label: '已驳回' },
  { value: 3, label: '已撤回' },
  { value: 4, label: '退回修改' }
]

/** 合法状态值集合（URL 参数白名单校验用） */
export const VALID_STATUS = STATUS_OPTIONS.map((item) => item.value)

/** 申请类型筛选选项（值即后端 ApplicationType 枚举名） */
export const TYPE_OPTIONS = [
  { value: 'EXPERT_MODIFY', label: '修改申请' },
  { value: 'EXPERT_CREATE', label: '新增申请' },
  { value: 'CONTRACT', label: '合同审批' }
]

/** 申请类型英文名 -> 中文（移动端卡片与筛选项复用） */
export function applicationTypeText(applicationType, fallback) {
  const hit = TYPE_OPTIONS.find((item) => item.value === applicationType)
  return hit ? hit.label : (fallback || '修改申请')
}

/** 单页条数选项（上限与后端 ApplicationService.MAX_PAGE_SIZE 一致） */
export const PAGE_SIZES = [10, 20, 50, 100]
