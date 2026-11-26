const slotKeywords = {
  U01: /酒店.*发票|住宿.*发票/,
  U02: /酒店.*(?:合规|支持材料)/,
  U03: /供应商.*(?:发票|明细单)/,
  U04: /垫付.*明细/,
  U05: /垫付.*发票/,
  U06: /高铁|火车票/,
  U07: /劳务协议/,
  U07B: /劳务.*会议资料/,
  U09: /预算单/,
  U10: /真实结算单/,
  U11: /客户确认.*结算单/,
  U12: /合规转化.*结算单/,
  U13: /合规资料整体包/,
  U14: /盖章.*结算单/,
  U15: /开具的发票|客户开票/,
  U16: /邮寄凭证|快递回执/,
  U17: /平台审核.*凭证/,
  U18: /平台.*补充资料/,
  U19: /内部报销单据/
}

export function suggestSettlementSlot(filename, slots) {
  const matches = slots.filter(({ definition }) => slotKeywords[definition.code]?.test(filename))
  return matches.length === 1 ? matches[0].definition.code : ''
}

export function invoiceFilenameTotal(documents) {
  let cents = 0
  let count = 0
  for (const { fileName } of documents) {
    const name = fileName.replace(/\.[^.]+$/, '').normalize('NFKC')
    const amounts = [...name.matchAll(/(?:¥\s*(\d{1,3}(?:,\d{3})+(?:\.\d{1,2})?|\d+(?:\.\d{1,2})?)(?![\d.,-])|(?<![\d.,-])(\d{1,3}(?:,\d{3})+(?:\.\d{1,2})?|\d+(?:\.\d{1,2})?)\s*元)/g)]
    if (amounts.length !== 1) continue
    const amount = Math.round(Number((amounts[0][1] ?? amounts[0][2]).replaceAll(',', '')) * 100)
    if (!Number.isSafeInteger(cents + amount)) continue
    cents += amount
    count++
  }
  return { cents, count }
}
