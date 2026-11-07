export const orderLayout = (items) => [...items].sort((a, b) => a.y - b.y || a.x - b.x || a.i.localeCompare(b.i))

function overlaps(a, b) {
  return a.x < b.x + b.w && b.x < a.x + a.w && a.y < b.y + b.h && b.y < a.y + a.h
}

export function placeCard(items, card, after = null) {
  for (let y = after?.y ?? 0; y + card.h <= 200; y++) {
    for (let x = y === after?.y ? after.x + 1 : 0; x + card.w <= 12; x++) {
      const positioned = { ...card, x, y }
      if (!items.some(item => overlaps(item, positioned))) return positioned
    }
  }
  return null
}

export function normalizeDashboardLayout(raw, catalog) {
  const items = []
  const pending = []
  const seen = new Set()
  for (const item of raw) {
    if (!item || !Object.hasOwn(catalog, item.i) || seen.has(item.i)) continue
    seen.add(item.i)
    const definition = catalog[item.i]
    const minH = definition.kind === 'panel' ? 4 : 2
    const w = Number.isFinite(item.w) ? Math.max(3, Math.min(12, Math.round(item.w))) : definition.w
    const h = Number.isInteger(item.h) ? Math.max(minH, Math.min(24, item.h)) : Math.max(minH, definition.h)
    const card = { i: item.i, x: item.x, y: item.y, w, h }
    if (Number.isInteger(card.x) && Number.isInteger(card.y) && card.x >= 0 && card.y >= 0 &&
        card.x + w <= 12 && card.y + h <= 200 && !items.some(other => overlaps(other, card))) {
      items.push(card)
    } else {
      pending.push(card)
    }
  }
  let previous = null
  for (const card of pending) {
    const placed = placeCard(items, card, previous)
    if (placed) {
      items.push(placed)
      previous = placed
    }
  }
  return orderLayout(items)
}
