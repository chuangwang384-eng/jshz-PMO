import assert from 'node:assert/strict'
import { mkdir } from 'node:fs/promises'

if (process.env.LIVE_V4_ACCEPTANCE !== 'acceptance_v4' || !process.env.V4_TEST_PASSWORD) {
  throw new Error('Isolated fixture required')
}
const { chromium } = await import(process.env.PLAYWRIGHT_MODULE || 'playwright')
const base = 'http://localhost:5173'
const title = '隔离验收：主动解析真实 PDF'
const editedTitle = '隔离验收修订后的候选事项'
const browser = await chromium.launch({ channel: 'msedge', headless: true })

async function login(name) {
  const page = await browser.newPage({ viewport: { width: 1440, height: 980 } })
  page.setDefaultTimeout(20000)
  await page.goto(base + '/login?redirect=/knowledge-planet/enterprise-compliance')
  await page.locator('input[autocomplete="username"]').fill(name)
  await page.getByPlaceholder('密码', { exact: true }).fill(process.env.V4_TEST_PASSWORD)
  await page.getByRole('button', { name: /登\s*录/ }).click()
  await page.getByRole('tab', { name: '合规记事本', exact: true }).waitFor()
  return page
}
async function data(response) {
  const result = await response.json()
  assert.equal(result.code, 0, result.message)
  return result.data
}

try {
  const page = await login('验收维护人')
  await page.getByRole('tab', { name: '规范文档库' }).click()
  await page.getByRole('button', { name: '上传文档', exact: true }).click()
  const upload = page.getByRole('dialog', { name: '上传文档' })
  await upload.getByPlaceholder('请输入文档标题').fill(title)
  await upload.locator('input[type=file]').setInputFiles(process.env.V4_SAMPLE_PDF)
  const uploaded = page.waitForResponse(r => r.request().method() === 'POST' && new URL(r.url()).pathname === '/api/knowledge/docs')
  await upload.getByRole('button', { name: '确认', exact: true }).click()
  const doc = await data(await uploaded)
  assert.ok(doc.id)
  const query = base + '/api/knowledge/docs?category=enterprise-compliance&page=1&pageSize=100'
  const until = Date.now() + 240000
  while (true) {
    const result = await data(await page.request.get(query))
    const status = (result.items || result.records).find(row => row.id === doc.id)?.parseStatus
    if (status === 'SUCCESS' || status === 'FAILED') break
    if (Date.now() > until) throw new Error('Upload parser did not finish')
    await new Promise(resolve => setTimeout(resolve, 2000))
  }

  await page.getByRole('tab', { name: '模板维护' }).click()
  const board = page.locator('.maintenance-board')
  const section = name => board.locator('.mb-section').filter({ has: page.getByRole('heading', { name, exact: true }) })
  const parse = section('文档解析')
  const drafts = section('候选事项')
  const publishedItems = section('已发布事项')
  await page.setViewportSize({ width: 430, height: 850 })
  const dimensions = await board.evaluate(el => ({
    width: el.clientWidth, scroll: el.scrollWidth,
    children: [...el.children].map(child => ({ class: child.className, width: child.clientWidth, scroll: child.scrollWidth }))
  }))
  assert.ok(dimensions.scroll <= dimensions.width + 1, JSON.stringify(dimensions))
  await page.setViewportSize({ width: 1500, height: 1000 })
  await parse.locator('.el-select').click()
  await page.getByRole('option', { name: title, exact: true }).click()
  const reparsePath = '/api/compliance/documents/' + doc.id + '/reparse'
  async function parseAction(label) {
    await parse.getByRole('button', { name: label, exact: true }).click()
    const dialog = page.getByRole('dialog', { name: label, exact: true })
    assert.match(await dialog.innerText(), /隔离验收：主动解析真实 PDF/)
    if (label === '清空并重新解析') assert.match(await dialog.innerText(), /\d+ 条事项/)
    const pending = page.waitForResponse(r => r.request().method() === 'POST' && new URL(r.url()).pathname === reparsePath, { timeout: 310000 })
    await dialog.getByRole('button', { name: '确定', exact: true }).click()
    const result = await data(await pending)
    assert.ok(result.parsed > 0)
    await page.getByText('已解析 ' + result.parsed + ' 条候选', { exact: true }).waitFor()
    await drafts.locator('.el-collapse-item__header').filter({ hasText: title })
      .getByText(result.parsed + ' 条', { exact: true }).waitFor()
  }
  await parseAction('解析文档')
  const heading = drafts.locator('.el-collapse-item__header').filter({ hasText: title })
  await heading.click()
  const candidate = drafts.locator('tbody tr').first()
  await candidate.getByRole('button', { name: '编辑', exact: true }).click()
  const edit = page.getByRole('dialog', { name: '编辑事项' })
  await edit.locator('input').fill(editedTitle)
  await edit.locator('textarea').fill('隔离验收：保留项目快照。')
  await edit.getByRole('button', { name: '保存编辑' }).click()
  await edit.waitFor({ state: 'hidden' })
  await drafts.locator('tbody tr').filter({ hasText: editedTitle }).getByRole('button', { name: '保存', exact: true }).click()
  await page.getByText('已保存到展示列表', { exact: true }).waitFor()
  const published = publishedItems.locator('.el-collapse-item__header').filter({ hasText: title })
  await published.getByText('1 条', { exact: true }).waitFor()
  await published.click()
  await board.locator('tbody tr').filter({ hasText: editedTitle }).waitFor()

  const employee = await login('验收员工甲')
  await employee.getByRole('tab', { name: '规范文档库' }).click()
  await employee.locator('tbody tr').filter({ hasText: title }).getByRole('button', { name: '收藏', exact: true }).click()
  await employee.getByRole('tab', { name: '合规记事本' }).click()
  const note = employee.locator('.nb-item').filter({ hasText: editedTitle })
  await note.locator('.el-checkbox').click()
  await employee.getByRole('button', { name: '推送到项目…', exact: true }).click()
  const push = employee.getByRole('dialog', { name: '推送到项目' })
  await push.locator('.el-select').click()
  await employee.getByRole('option', { name: '隔离项目', exact: true }).click()
  await push.getByRole('button', { name: '推送（1 条）', exact: true }).click()
  await push.waitFor({ state: 'hidden' })
  const todosUrl = base + '/api/compliance/notebook/projects/1/todos'
  const before = (await data(await employee.request.get(todosUrl))).find(todo => todo.title === editedTitle)
  assert.ok(before)

  await parseAction('清空并重新解析')
  const itemsUrl = base + '/api/compliance/items?includeDraft=true&documentId=' + doc.id + '&size=500'
  const reset = await data(await page.request.get(itemsUrl))
  assert.ok(reset.items.length && reset.items.every(item => item.isDraft === 1))
  assert.ok(!reset.items.some(item => item.title === editedTitle))

  // Save a replacement so permanent delete covers both published items and remaining candidates.
  const remaining = await heading.getAttribute('aria-expanded')
  if (remaining !== 'true') await heading.click()
  await drafts.locator('tbody tr').first().getByRole('button', { name: '保存', exact: true }).click()
  await page.getByText('已保存到展示列表', { exact: true }).waitFor()
  await employee.reload()
  await employee.locator('.nb-group').filter({ hasText: title }).waitFor()

  await page.getByRole('tab', { name: '规范文档库' }).click()
  await page.locator('tbody tr').filter({ hasText: title }).getByRole('button', { name: '永久删除', exact: true }).click()
  const deletion = page.getByRole('dialog', { name: '永久删除确认' })
  await deletion.getByRole('button', { name: '永久删除', exact: true }).click()
  await page.getByText('已永久删除', { exact: true }).waitFor()
  assert.equal((await data(await page.request.get(itemsUrl))).total, 0)
  await page.getByRole('tab', { name: '模板维护' }).click()
  await publishedItems.locator('.el-collapse-item__header').filter({ hasText: title }).waitFor({ state: 'hidden' })
  await employee.reload()
  await employee.locator('.nb-group').filter({ hasText: title }).waitFor({ state: 'hidden' })
  const after = (await data(await employee.request.get(todosUrl))).find(todo => todo.id === before.id)
  assert.deepEqual(after, { ...before, sourceDeleted: true })
  console.log('PASS: real PDF upload, active parse, edit/publish, reset/reparse, permanent deletion, notebook cleanup and project snapshot')
  const evidence = '../builds/.build-compliance-v4-acceptance/evidence'
  await mkdir(evidence, { recursive: true })
  await page.screenshot({ path: evidence + '/compliance-maintenance-live.png', fullPage: true })
} finally {
  await browser.close()
}
