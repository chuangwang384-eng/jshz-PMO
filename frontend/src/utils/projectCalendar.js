// 项目日历展示层单一来源（2026-11-26 项目日历需求）。
// 消费方：ProjectCalendar.vue（个人月历）、ProjectDeptSchedule.vue（部门排期）、
// DashboardView.vue（panel.myMeetings / panel.deptMeetings 卡片）。
// 角色文案/优先级、日期展开、月历网格、撞期判定只在这里实现；后端聚合口径见
// backend ProjectCalendarService（撞期计数的服务端唯一实现），两侧不互抄对方逻辑。

// 后端角色码 -> 展示（文字标记 + 颜色双重区分，不单靠颜色；色值经 ui-tokens 令牌）
export const ROLE_CODES = ['OWNER', 'ASSISTANT', 'EXECUTOR']
export const ROLE_TEXT = { OWNER: '负', ASSISTANT: '协', EXECUTOR: '执' }
export const ROLE_LABEL = { OWNER: '负责人', ASSISTANT: '协助人', EXECUTOR: '执行人' }
/** 角色字（负/协/执）。 */
export function roleText(code) {
  return ROLE_TEXT[code] || '—'
}
/** 角色全名（负责人/协助人/执行人）。 */
export function roleLabel(code) {
  return ROLE_LABEL[code] || '—'
}

/** 角色优先级：负责人 > 协助人 > 执行人（需求 FR-01；后端 DashboardReadService.myRelations 同序）。 */
export function primaryRole(roles) {
  const list = Array.isArray(roles) ? roles : []
  for (const code of ROLE_CODES) {
    if (list.includes(code)) return code
  }
  return list[0] || null
}

/** 会议是否覆盖某天（多日会议逐日显示）。 */
export function coversDay(meeting, day) {
  return meeting.meetingStartDate <= day && day <= meeting.meetingEndDate
}

/** 当天撞期：同一人同一天覆盖的项目数 >=2（调用方按人聚合后逐日调用）。 */
export function hasCollision(meetings) {
  return new Set((meetings || []).map((m) => m.projectId)).size >= 2
}

const pad = (n) => String(n).padStart(2, '0')

/** YYYY-MM-DD 格式化（本地时间，与业务日期口径 Asia/Shanghai 一致的使用方式）。 */
export function fmtDate(year, month, day) {
  return `${year}-${pad(month)}-${pad(day)}`
}

/** 月份字符串 'YYYY-MM' 与 {year, month} 互转。 */
export function parseMonth(monthStr) {
  const [y, m] = monthStr.split('-').map(Number)
  return { year: y, month: m }
}

export function fmtMonth({ year, month }) {
  return `${year}-${pad(month)}`
}

/** 当月天数。 */
export function daysInMonth(year, month) {
  return new Date(year, month, 0).getDate()
}

/** 当月 1 日是周一偏移（0=周一，与月历网格行序一致）。 */
export function firstDayOffset(year, month) {
  return (new Date(year, month - 1, 1).getDay() + 6) % 7
}

/**
 * 个人月历网格：每格 {day, date, meetings, collision}；前导空格由组件按 offset 处理。
 * meetings: 该天覆盖的会议列表（调用方已按区间重叠取回）。
 */
export function buildMonthGrid(meetings, { year, month }, todayStr) {
  const n = daysInMonth(year, month)
  const cells = []
  for (let d = 1; d <= n; d++) {
    const date = fmtDate(year, month, d)
    const dayMeetings = (meetings || []).filter((m) => coversDay(m, date))
    cells.push({
      day: d,
      date,
      meetings: dayMeetings,
      collision: hasCollision(dayMeetings),
      today: date === todayStr
    })
  }
  return { offset: firstDayOffset(year, month), cells }
}

/** 会议单行展示文案：单日 M/D，多日 M/D – M/D。 */
export function meetingRangeText(meeting) {
  const md = (s) => String(s || '').slice(5).replace('-', '/')
  return meeting.meetingStartDate === meeting.meetingEndDate
    ? md(meeting.meetingStartDate)
    : `${md(meeting.meetingStartDate)} – ${md(meeting.meetingEndDate)}`
}

/** 距今天数文案：进行中 / 明天 / N 天后（工作台卡片用）。 */
export function relativeDay(meeting, todayStr) {
  if (meeting.meetingStartDate <= todayStr) return '进行中'
  const diff = Math.round((Date.parse(meeting.meetingStartDate) - Date.parse(todayStr)) / 86400000)
  return diff === 1 ? '明天' : `${diff} 天后`
}

/** 本月及前后月导航：返回 {year, month}。 */
export function shiftMonth({ year, month }, delta) {
  const next = month + delta
  if (next < 1) return { year: year - 1, month: 12 }
  if (next > 12) return { year: year + 1, month: 1 }
  return { year, month: next }
}

/** 当天业务日期串（本地时区；后端按 Asia/Shanghai 计数，前端展示当日即可）。 */
export function todayStr() {
  const now = new Date()
  return fmtDate(now.getFullYear(), now.getMonth() + 1, now.getDate())
}
