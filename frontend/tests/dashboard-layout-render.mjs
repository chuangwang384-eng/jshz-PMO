import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import path from 'node:path'

const { chromium } = await import(process.env.PLAYWRIGHT_MODULE || 'playwright')
const dist = path.resolve(process.argv[2])
const browser = await chromium.launch({ channel: 'msedge', headless: true })
const origin = 'http://127.0.0.1:18690'
const user = { id: 1, username: '布局验收', roles: [], permissions: [] }
const items = [
  { i: 'page.knowledge', x: 0, y: 0, w: 5, h: 2 },
  { i: 'stat.myApplications', x: 5, y: 0, w: 3, h: 2 },
  { i: 'stat.riskOverdue', x: 8, y: 0, w: 3, h: 2 },
  { i: 'stat.riskDue', x: 0, y: 2, w: 9, h: 2 }
]
const mime = { '.html': 'text/html', '.js': 'text/javascript', '.css': 'text/css' }

try {
  const page = await browser.newPage({ viewport: { width: 1500, height: 1050 } })
  await page.addInitScript(user => localStorage.setItem('expert_user', JSON.stringify(user)), user)
  await page.route(origin + '/**', async route => {
    const url = new URL(route.request().url())
    if (url.pathname.startsWith('/api/')) {
      const data = url.pathname === '/api/dashboard/layout/v2' ? { revision: 'fixture', items } :
        url.pathname === '/api/dashboard/overview' ? { capabilities: ['knowledge'], sections: {}, businessDate: '2026-11-06', generatedAt: '2026-11-06T14:00:00+08:00' } : user
      return route.fulfill({ json: { code: 0, data } })
    }
    const file = path.join(dist, url.pathname.startsWith('/assets/') ? url.pathname.slice(1) : 'index.html')
    return route.fulfill({ body: await readFile(file), contentType: mime[path.extname(file)] || 'application/octet-stream' })
  })
  await page.goto(origin + '/dashboard')
  const geometry = () => page.evaluate(() => {
    const grid = document.querySelector('[aria-label="工作台卡片"]')
    const box = grid.getBoundingClientRect()
    const cards = [...grid.querySelectorAll('[data-card]')].map(el => {
      const card = el.querySelector('.summary').getBoundingClientRect()
      return { id: el.dataset.card, geometry: [card.left - box.left, card.top - box.top, card.width, card.height].map(value => Math.round(value)), bottom: card.bottom }
    })
    return { cards, footer: document.querySelector('.dashboard footer').getBoundingClientRect().top }
  })
  await page.locator('[data-card="page.knowledge"]').waitFor()
  const browse = await geometry()
  await page.getByRole('button', { name: '编辑布局', exact: true }).click()
  await page.locator('[data-card="page.knowledge"][gs-w="5"]').waitFor()
  const edit = await geometry()
  await page.getByRole('button', { name: '保存布局', exact: true }).click()
  await page.getByRole('button', { name: '编辑布局', exact: true }).waitFor()
  const saved = await geometry()
  await page.reload()
  await page.locator('[data-card="page.knowledge"]').waitFor()
  const reloaded = await geometry()
  const summary = page.locator('[data-card="stat.myApplications"] .summary')
  assert.equal(await summary.evaluate(el => getComputedStyle(el).textAlign), 'center')
  assert.equal(await summary.locator('small').innerText(), '待补充')
  const relative = state => state.cards.map(({ id, geometry }) => ({ id, geometry }))
  assert.ok([browse, edit, saved, reloaded].every(state => state.cards.every(card => card.bottom <= state.footer)), 'footer intersects a card')
  for (const state of [edit, saved, reloaded]) {
    relative(state).forEach((card, i) => {
      assert.equal(card.id, browse.cards[i].id)
      assert.ok(card.geometry.every((n, j) => Math.abs(n - browse.cards[i].geometry[j]) <= 1), card.id + ' geometry differs')
    })
  }
  console.log('PASS: Edge card faces match before edit, during edit, after save and reload; footer stays below and summaries are centered.')
} finally {
  await browser.close()
}
