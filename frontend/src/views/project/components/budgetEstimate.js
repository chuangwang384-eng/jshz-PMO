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

// 估算规则兑底常量（2026-11-17 A1 决策：真源已迁入管理员「收费标准配置」feeStandards 的 EST_* 键，
// 管理员可在线调价；本常量仅作接口缺失/未启用时的兑底，值须与 init.sql / migration 种子保持一致）
export const ESTIMATE_RULES = {
  HOTEL_DEFAULT_ROOMS: 2,            // EST_HOTEL_ROOMS：无外埠人员时住宿预留间数
  TEA_RATIO: 70,                     // EST_TEA_RATIO：茶歇人数比例（%），向上取 5 的倍数
  PLATFORM_PRICE: { FIRST: 200, OTHER: 100 }, // EST_PLATFORM_FIRST / EST_PLATFORM_OTHER：平台专车单价（元/人）
  FLIGHT_FARE: 2000                  // EST_FLIGHT_FARE：机票估算单价（元/人）
}

// 供收费标准管理页展示估算参数类键的标签（不参与估算逻辑，与 quantityRules 分离）
export const estimateRuleMeta = {
  EST_TEA_RATIO: { label: '估算参数', unit: '%' },
  EST_FLIGHT_FARE: { label: '估算参数', unit: '元/人' },
  EST_PLATFORM_FIRST: { label: '估算参数', unit: '元/人' },
  EST_PLATFORM_OTHER: { label: '估算参数', unit: '元/人' },
  EST_HOTEL_ROOMS: { label: '估算参数', unit: '间' }
}

/** 从收费标准取估算参数；缺失/非法时回退 ESTIMATE_RULES 兑底值。 */
function estimateParam(fees, key, fallback) {
  const value = standard(fees, key)
  return value && Number.isFinite(value.low) ? value.low : fallback
}

export function estimateChanges(items, input, fees, first, previousInput = {}) {
  const defaultRooms = estimateParam(fees, 'EST_HOTEL_ROOMS', ESTIMATE_RULES.HOTEL_DEFAULT_ROOMS)
  const teaRatio = estimateParam(fees, 'EST_TEA_RATIO', ESTIMATE_RULES.TEA_RATIO)
  const flightFare = estimateParam(fees, 'EST_FLIGHT_FARE', ESTIMATE_RULES.FLIGHT_FARE)
  const platformFirst = estimateParam(fees, 'EST_PLATFORM_FIRST', ESTIMATE_RULES.PLATFORM_PRICE.FIRST)
  const platformOther = estimateParam(fees, 'EST_PLATFORM_OTHER', ESTIMATE_RULES.PLATFORM_PRICE.OTHER)
  const room = input.travelers || defaultRooms
  const tea = Math.ceil((input.attendees * teaRatio) / 500) * 5
  const flight = input.longDistance === 'FLIGHT'
  const rail = input.longDistance === 'RAIL'
  const fleet = input.localTransport === 'FLEET'
  const platform = input.localTransport === 'PLATFORM'
  const platformPrice = input.city === 'FIRST' ? platformFirst : input.city === 'OTHER' ? platformOther : undefined
  const suggestions = [
    ['HOTEL_ROOM', room, undefined, '间', input.travelers ? `外埠人数 ${input.travelers} × 1 间` : `无外埠人员，预留 ${defaultRooms} 间`],
    ['HOTEL_TEA', tea, undefined, '人', `参会总人数 ${input.attendees} × ${teaRatio}%，向上取 5 的倍数`],
    ['TRAFFIC_FLEET', fleet ? input.attendees * 2 : 0, undefined, '次', fleet ? `参会总人数 ${input.attendees} × 2 次，单价待填` : '未选择车队专车'],
    ['TRAFFIC_PLATFORM', platform ? input.attendees : 0, platform ? platformPrice : undefined, '人',
      platform && platformPrice != null ? `参会总人数 ${input.attendees} × ${platformPrice} 元/人`
        : platform ? '按参会总人数，选择城市档位后可估算单价' : '未选择平台专车']
  ]
  if (flight || rail) {
    const fare = flight ? flightFare : previousInput.longDistance === 'FLIGHT' ? null : undefined
    suggestions.push(['TRAFFIC_MAIN', input.travelers, fare, '人',
      flight ? `外埠人数 ${input.travelers} × ${flightFare} 元/人` : `外埠人数 ${input.travelers}，高铁按一等座询价`])
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
