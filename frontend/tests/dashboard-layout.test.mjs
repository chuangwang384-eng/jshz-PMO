import test from 'node:test'
import assert from 'node:assert/strict'
import { normalizeDashboardLayout, placeCard } from '../src/utils/dashboardLayout.js'

const catalog = {
  'stat.one': { w: 3, h: 1, kind: 'stat' },
  'panel.two': { w: 4, h: 6, kind: 'panel' },
  'page.three': { w: 3, h: 1, kind: 'page' }
}

test('legacy widths and sequence map deterministically to two-dimensional coordinates', () => {
  assert.deepEqual(normalizeDashboardLayout([
    { i: 'stat.one', w: 7 }, { i: 'panel.two', w: 6 }, { i: 'page.three', w: 3 }
  ], catalog), [
    { i: 'stat.one', x: 0, y: 0, w: 7, h: 2 },
    { i: 'panel.two', x: 0, y: 2, w: 6, h: 6 },
    { i: 'page.three', x: 6, y: 2, w: 3, h: 2 }
  ])
})

test('valid saved geometry and explicit empty state survive normalization', () => {
  const saved = [{ i: 'panel.two', x: 7, y: 14, w: 5, h: 8 }]
  assert.deepEqual(normalizeDashboardLayout(saved, catalog), saved)
  assert.deepEqual(normalizeDashboardLayout([], catalog), [])
})

test('new placement rejects an occupied bounded canvas', () => {
  const filled = [{ x: 0, y: 0, w: 12, h: 200 }]
  assert.equal(placeCard(filled, { i: 'panel.two', w: 4, h: 6 }), null)
})
