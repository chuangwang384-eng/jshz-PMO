const legacyNames = {
  HOTEL_ROOM: '住宿费',
  HOTEL_TEA: '茶歇费',
  TRAFFIC_MAIN: '大交通费（按专家人数预估）',
  TRAFFIC_FLEET: '地面交通-车队专车',
  TRAFFIC_PLATFORM: '地面交通-平台专车'
}

export const quantityRules = {
  AGENDA_UP: { label: '数量增量', unit: '张' },
  MAT_DESKCARD_UP: { label: '数量增量', unit: '张' },
  MAT_STAND: { label: '每场数量', unit: '个/场' }
}

export const estimateDefaults = {
  attendees: null,
  travelers: null,
  sessions: null,
  city: '',
  staff: null,
  days: null,
  longDistance: 'MANUAL',
  localTransport: 'NONE'
}

function standard(fees, key) {
  const raw = fees[key]?.defaultValue
  if (raw == null || raw === '') return null
  const [low, high] = String(raw).split(',').map(Number)
  if (!Number.isFinite(low) || low < 0) return null
  return { low, text: high == null ? String(low) : `${low}–${high}` }
}

// 估算规则常量（对客报价口径；调整须经业务负责人确认，2026-11-17 审查提取）
// 是否并入管理员 feeStandards 配置待决策，故暂以命名常量集中维护
export const ESTIMATE_RULES = {
  HOTEL_DEFAULT_ROOMS: 2,            // 无外埠人员时住宿预留间数
  TEA_RATIO_NUM: 7,                  // 茶歇人数比例 70%：用 7/50 整数运算
  TEA_RATIO_DEN: 50,                 //   避免 attendees × 0.7 的浮点误差（×5 倍数取整）
  TEA_ROUND_TO: 5,
  PLATFORM_PRICE: { FIRST: 200, OTHER: 100 }, // 平台专车单价（元/人），按城市档位
  FLIGHT_FARE: 2000                  // 机票估算单价（元/人）
}

export function estimateChanges(items, input, fees, first, previousInput = {}) {
  const room = input.travelers || ESTIMATE_RULES.HOTEL_DEFAULT_ROOMS
  const tea = Math.ceil((input.attendees * ESTIMATE_RULES.TEA_RATIO_NUM) / ESTIMATE_RULES.TEA_RATIO_DEN) * ESTIMATE_RULES.TEA_ROUND_TO
  const flight = input.longDistance === 'FLIGHT'
  const rail = input.longDistance === 'RAIL'
  const fleet = input.localTransport === 'FLEET'
  const platform = input.localTransport === 'PLATFORM'
  const platformPrice = input.city === 'FIRST' ? ESTIMATE_RULES.PLATFORM_PRICE.FIRST
    : input.city === 'OTHER' ? ESTIMATE_RULES.PLATFORM_PRICE.OTHER : undefined
  const suggestions = [
    ['HOTEL_ROOM', room, undefined, '间', input.travelers ? `外埠人数 ${input.travelers} × 1 间` : '无外埠人员，预留 2 间'],
    ['HOTEL_TEA', tea, undefined, '人', `参会总人数 ${input.attendees} × 70%，向上取 5 的倍数`],
    ['TRAFFIC_FLEET', fleet ? input.attendees * 2 : 0, undefined, '次', fleet ? `参会总人数 ${input.attendees} × 2 次，单价待填` : '未选择车队专车'],
    ['TRAFFIC_PLATFORM', platform ? input.attendees : 0, platform ? platformPrice : undefined, '人',
      platform && platformPrice != null ? `参会总人数 ${input.attendees} × ${platformPrice} 元/人`
        : platform ? '按参会总人数，选择城市档位后可估算单价' : '未选择平台专车']
  ]
  if (flight || rail) {
    const fare = flight ? ESTIMATE_RULES.FLIGHT_FARE : previousInput.longDistance === 'FLIGHT' ? null : undefined
    suggestions.push(['TRAFFIC_MAIN', input.travelers, fare, '人',
      flight ? `外埠人数 ${input.travelers} × ${ESTIMATE_RULES.FLIGHT_FARE} 元/人` : `外埠人数 ${input.travelers}，高铁按一等座询价`])
  }
  for (const [key, rule] of Object.entries(quantityRules)) {
    const value = standard(fees, key)
    if (!value || !Number.isInteger(value.low)) continue
    const stands = key === 'MAT_STAND'
    suggestions.push([key, stands ? input.sessions * value.low : input.attendees + value.low, undefined,
      stands ? '个' : '张', stands ? `${input.sessions} 场 × ${value.low} 个/场`
        : `参会总人数 ${input.attendees} + ${value.low} 张（配置加量 ${value.text} 张）`])
  }
  const staffPrice = standard(fees, 'STAFF_DAY')
  suggestions.push(['STAFF_DAY', input.staff * input.days, staffPrice?.low, '人天',
    `${input.staff} 人 × ${input.days} 天${staffPrice ? `，参考单价 ${staffPrice.text} 元/人天` : '，单价待填'}`])

  const ready = {
    HOTEL_ROOM: input.travelers != null,
    HOTEL_TEA: input.attendees != null,
    TRAFFIC_MAIN: input.travelers != null,
    TRAFFIC_FLEET: input.attendees != null || (!fleet && previousInput.localTransport === 'FLEET'),
    TRAFFIC_PLATFORM: input.attendees != null || (!platform && previousInput.localTransport === 'PLATFORM'),
    AGENDA_UP: input.attendees != null,
    MAT_DESKCARD_UP: input.attendees != null,
    MAT_STAND: input.sessions != null,
    STAFF_DAY: input.staff != null && input.days != null
  }
  return suggestions.flatMap(([key, quantity, unitPrice, unit, basis]) => {
    if (!ready[key]) return []
    const index = items.findIndex(row => row.itemKey === key || (!row.itemKey && row.name === legacyNames[key]))
    if (index < 0) return []
    const row = items[index]
    const proposed = { quantity, unitPrice, unit }
    const changes = Object.fromEntries(Object.entries(proposed).filter(([field, value]) => {
      const previous = row[field]
      const empty = previous == null || previous === ''
      const equal = value === null ? empty : field === 'unit' ? previous === value : !empty && Number(previous) === value
      return value !== undefined && !equal && (!first || empty)
    }))
    return Object.keys(changes).length ? [{ key, index, name: row.name, changes, basis }] : []
  })
}
