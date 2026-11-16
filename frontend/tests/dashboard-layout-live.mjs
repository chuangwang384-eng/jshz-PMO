import assert from 'node:assert/strict'

if (process.env.LIVE_V4_ACCEPTANCE !== 'acceptance_v4' || !process.env.V4_TEST_PASSWORD) {
  throw new Error('Isolated fixture required')
}
const { chromium } = await import(process.env.PLAYWRIGHT_MODULE || 'playwright')
const browser = await chromium.launch({ channel: 'msedge', headless: true })
const origin = 'http://localhost:5173'
const endpoint = origin + '/api/dashboard/layout/v2'
const errors = []

async function login(context, name) {
  const page = await context.newPage()
  page.on('pageerror', error => errors.push(error.message))
  page.on('crash', () => errors.push('Edge page crashed'))
  await page.goto(origin + '/login?redirect=/dashboard')
  await page.locator('input[autocomplete="username"]').fill(name)
  await page.getByPlaceholder('密码', { exact: true }).fill(process.env.V4_TEST_PASSWORD)
  await page.getByRole('button', { name: /登\s*录/ }).click()
  await page.waitForURL('**/dashboard')
  await page.getByRole('button', { name: '编辑布局', exact: true }).waitFor()
  assert.ok((await context.cookies()).some(cookie => cookie.name === 'expert_token'), 'browser login cookie missing')
  return page
}

async function read(context) {
  const response = await context.request.get(endpoint)
  const body = await response.text()
  assert.ok(body, 'layout GET returned empty HTTP ' + response.status())
  const result = JSON.parse(body)
  assert.equal(result.code, 0, JSON.stringify(result))
  return result.data
}

async function put(context, revision, items) {
  return (await context.request.put(endpoint, { data: { revision, items } })).json()
}

async function drag(page, locator, dx, dy) {
  await locator.scrollIntoViewIfNeeded()
  const bounds = await locator.boundingBox()
  const x = bounds.x + bounds.width / 2
  const y = bounds.y + bounds.height / 2
  await page.mouse.move(x, y)
  await page.mouse.down()
  await page.mouse.move(x + dx, y + dy, { steps: 16 })
  await page.mouse.up()
}

try {
  const context = await browser.newContext({ viewport: { width: 1600, height: 1100 } })
  const page = await login(context, '验收维护人')
  const fresh = await read(context)
  assert.equal(fresh.items, null)
  const seeded = await put(context, fresh.revision, [{ i: 'stat.riskOverdue', x: 3, y: 0, w: 3, h: 2 }])
  assert.equal(seeded.code, 0, JSON.stringify(seeded))
  const initial = seeded.data
  await page.reload()
  const edit = page.getByRole('button', { name: '编辑布局', exact: true })
  await edit.click()
  const card = page.locator('[data-card="stat.riskOverdue"]')
  const handle = card.locator('.layout-handle')
  await handle.waitFor()
  const position = async () => Promise.all(['gs-x', 'gs-y', 'gs-w', 'gs-h'].map(async name => Number(await card.getAttribute(name) || (name.endsWith('w') || name.endsWith('h') ? 1 : 0))))
  const before = await position()
  await drag(page, handle, 135, 118)
  const moved = await position()
  assert.notEqual(moved[0], before[0], 'horizontal drag')
  assert.notEqual(moved[1], before[1], 'vertical drag')
  await card.hover()
  await drag(page, card.locator('.ui-resizable-se'), 130, 59)
  const resized = await position()
  assert.notEqual(resized[2], moved[2], 'width resize')
  assert.notEqual(resized[3], moved[3], 'height resize')
  const relativeBounds = async () => {
    const cardBox = await card.boundingBox()
    const gridBox = await page.getByRole('region', { name: '工作台卡片' }).boundingBox()
    return [cardBox.x - gridBox.x, cardBox.y - gridBox.y, cardBox.width, cardBox.height]
  }
  const editedBounds = await relativeBounds()
  await page.getByRole('button', { name: '保存布局', exact: true }).click()
  await edit.waitFor()
  const committed = await read(context)
  const item = committed.items.find(item => item.i === 'stat.riskOverdue')
  assert.deepEqual([item.x, item.y, item.w, item.h], resized)
  assert.equal((await put(context, initial.revision, committed.items)).code, 409)
  assert.equal((await put(context, committed.revision, [{ ...item, w: 13 }])).code, 400)
  assert.equal((await put(context, committed.revision, [item, { ...item, i: 'stat.riskDue' }])).code, 400)
  await page.reload()
  await edit.waitFor()
  await card.waitFor()
  const readBounds = await relativeBounds()
  assert.ok(readBounds.every((value, index) => Math.abs(value - editedBounds[index]) < 1),
    'same geometry after reload: ' + JSON.stringify({ editedBounds, readBounds }))
  await page.setViewportSize({ width: 800, height: 900 })
  await page.getByText('请使用宽屏编辑布局。').waitFor()
  assert.deepEqual(await read(context), committed)
  await page.setViewportSize({ width: 1600, height: 1100 })
  await edit.click()
  await handle.press('Shift+ArrowDown')
  assert.equal((await position())[3], resized[3] + 1, 'keyboard resize')
  await page.locator('.palette-item').filter({ hasText: '我的项目' }).click()
  await page.locator('[data-card="panel.myProjects"]').waitFor()
  await page.getByRole('button', { name: '移除我的项目', exact: true }).click()
  await page.locator('[data-card="panel.myProjects"]').waitFor({ state: 'detached' })
  await page.getByRole('button', { name: '恢复默认', exact: true }).click()
  await page.locator('[data-card="panel.myProjects"]').waitFor()
  await page.getByRole('button', { name: '取消', exact: true }).click()
  assert.deepEqual(await read(context), committed)
  const employee = await browser.newContext({ viewport: { width: 1600, height: 1100 } })
  await login(employee, '验收员工甲')
  const other = await read(employee)
  assert.equal(other.items, null)
  assert.equal((await put(employee, other.revision, [{ i: 'panel.myApprovals', x: 0, y: 0, w: 4, h: 6 }])).code, 403)
  assert.equal((await put(context, committed.revision, [])).code, 0)
  assert.deepEqual((await read(context)).items, [])
  assert.equal((await read(employee)).items, null)
  const empty = await read(context)
  const legacy = await context.request.put(origin + '/api/dashboard/layout', {
    data: { layout: JSON.stringify([{ i: item.i, w: 4 }]) }
  })
  assert.equal((await legacy.json()).code, 0)
  assert.equal((await put(context, empty.revision, [])).code, 409)
  assert.deepEqual((await read(context)).items, [{ i: item.i, w: 4 }])
  assert.deepEqual(errors, [])
  console.log('PASS: real Edge drag, resize, reload, narrow mode, cancel; isolated MySQL persistence, conflict, validation, permissions and empty layout.')
} finally {
  await browser.close()
}
