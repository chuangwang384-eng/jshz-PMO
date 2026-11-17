import assert from 'node:assert/strict'
import test from 'node:test'
import { estimateChanges, estimateDefaults } from './budgetEstimate.js'

const fees = {
  AGENDA_UP: { defaultValue: '5,10' },
  MAT_DESKCARD_UP: { defaultValue: '10,20' },
  MAT_STAND: { defaultValue: '4' },
  STAFF_DAY: { defaultValue: '500' }
}
const input = { ...estimateDefaults, attendees: 51, travelers: 0, sessions: 2, city: 'FIRST', staff: 3, days: 2,
  longDistance: 'FLIGHT', localTransport: 'PLATFORM' }
const keys = ['HOTEL_ROOM', 'HOTEL_TEA', 'TRAFFIC_MAIN', 'TRAFFIC_FLEET', 'TRAFFIC_PLATFORM',
  'AGENDA_UP', 'MAT_DESKCARD_UP', 'MAT_STAND', 'STAFF_DAY']
const rows = () => keys.map(itemKey => ({ itemKey, name: itemKey, quantity: null, unitPrice: null }))
const byKey = changes => Object.fromEntries(changes.map(change => [change.key, change]))

test('material quantities and configurable staff rate are independent from materials pricing', () => {
  const result = byKey(estimateChanges(rows(), input, fees, true))
  assert.deepEqual(result.AGENDA_UP.changes, { quantity: 56, unit: '张' })
  assert.match(result.AGENDA_UP.basis, /5–10/)
  assert.deepEqual(result.MAT_DESKCARD_UP.changes, { quantity: 61, unit: '张' })
  assert.deepEqual(result.MAT_STAND.changes, { quantity: 8, unit: '个' })
  assert.deepEqual(result.STAFF_DAY.changes, { quantity: 6, unitPrice: 500, unit: '人天' })
})

test('first preview preserves entered quantities, explicit zero and historical material prices', () => {
  const items = rows()
  Object.assign(items.find(row => row.itemKey === 'AGENDA_UP'), { quantity: 45, unitPrice: 5, unit: '张' })
  Object.assign(items.find(row => row.itemKey === 'TRAFFIC_PLATFORM'), { unitPrice: 0 })
  const result = byKey(estimateChanges(items, input, fees, true))
  assert.equal(result.AGENDA_UP, undefined)
  assert.deepEqual(result.TRAFFIC_PLATFORM.changes, { quantity: 51, unit: '人' })
  assert.equal(items.find(row => row.itemKey === 'AGENDA_UP').unitPrice, 5)
})

test('hotel rounding and ground transport alternatives use the meeting inputs', () => {
  const initial = byKey(estimateChanges(rows(), input, fees, true))
  assert.equal(initial.HOTEL_ROOM.changes.quantity, 2)
  assert.equal(initial.HOTEL_TEA.changes.quantity, 40)
  assert.equal(initial.TRAFFIC_PLATFORM.changes.unitPrice, 200)
  assert.equal(initial.TRAFFIC_FLEET.changes.quantity, 0)
  const other = byKey(estimateChanges(rows(), { ...input, travelers: 6, city: 'OTHER', localTransport: 'FLEET' }, fees, true))
  assert.equal(other.HOTEL_ROOM.changes.quantity, 6)
  assert.equal(other.TRAFFIC_MAIN.changes.unitPrice, 2000)
  assert.equal(other.TRAFFIC_FLEET.changes.quantity, 102)
  assert.equal(other.TRAFFIC_PLATFORM.changes.quantity, 0)
})

test('re-estimating from a flight to rail previews clearing the old flight price', () => {
  const items = rows()
  const main = items.find(row => row.itemKey === 'TRAFFIC_MAIN')
  Object.assign(main, { quantity: 6, unitPrice: 2000, unit: '人' })
  const result = byKey(estimateChanges(items, { ...input, travelers: 6, longDistance: 'RAIL' }, fees, false, input))
  assert.deepEqual(result.TRAFFIC_MAIN.changes, { unitPrice: null })
  assert.equal(main.unitPrice, 2000)
})
