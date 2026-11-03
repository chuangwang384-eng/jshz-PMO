import assert from 'node:assert/strict'
const { chromium } = await import(process.env.PLAYWRIGHT_MODULE || 'playwright')
const browser = await chromium.launch({ channel: 'msedge', headless: true })
const page = await browser.newPage({ viewport: { width: 1440, height: 1100 } })
page.setDefaultTimeout(8000)
const errors = []
page.on('pageerror', e => errors.push(e.message))
const user = { id: 2, userId: 2, username: 'fixture', permissions: ['knowledge:maintain'], roles: [], mustChangePassword: false }
await page.addInitScript(u => localStorage.setItem('expert_user', JSON.stringify(u)), user)
let task = { id: 11, executionId: 1, executionName: '六月清单', progress: 'PROCESSING', priority: 'MEDIUM', rowVersion: 0, snapshot: { title: '协会选择', inputType: 'SINGLE', options: [{ optionCode: 'A', label: '协会 A' }, { optionCode: 'B', label: '协会 B' }] }, answer: null }
const writes = []
let failSave = true
const executionWrites = []
const executions = [{ id: 1, name: '六月清单', state: 'ACTIVE' }]
const templateWrites = []
let template = { id: 7, documentId: 5, title: '规范模板', state: 'DRAFT', rowVersion: 0, nodes: [] }
await page.route('**/api/**', async route => {
  const req = route.request(), path = new URL(req.url()).pathname
  if (!path.startsWith('/api/')) { await route.continue(); return }
  let data = []
  if (path === '/api/auth/me') data = user
  else if (path.endsWith('/tasks/11') && req.method() === 'PATCH') {
    if (failSave) { failSave = false; await route.fulfill({ json: { code: 409, message: '隔离保存冲突', data: null } }); return }
    const body = req.postDataJSON(); writes.push(body)
    task = { ...task, ...body, rowVersion: task.rowVersion + 1 }
    if ('waiting' in body) { task.waitingReason = body.waiting?.reason || null; task.nextAction = body.waiting?.nextAction || null; task.reviewDate = body.waiting?.reviewDate || null }
    data = task
  } else if (path.endsWith('/my-tasks')) {
    const scope = new URL(req.url()).searchParams.get('statusScope')
    const visible = (scope === 'COMPLETED') === (task.progress === 'COMPLETED')
    data = { items: visible ? [task] : [], total: visible ? 1 : 0, stats: { todoCount: task.progress === 'COMPLETED' ? 0 : 1 } }
  } else if (path.endsWith('/executions') && req.method() === 'POST') {
    const body = req.postDataJSON(); executionWrites.push({ path, body }); executions.push({ id: 2, name: body.name, state: 'DRAFT' }); data = { executionId: 2 }
  } else if (path.endsWith('/executions/2/start')) { executionWrites.push({ path }); executions[1].state = 'ACTIVE'; data = { started: true } }
  else if (path.endsWith('/executions/2/reuse')) { const body = req.postDataJSON(); executionWrites.push({ path, body }); executions.push({ id: 3, name: body.name, state: 'DRAFT' }); data = { executionId: 3 } }
  else if (path.endsWith('/executions')) data = { items: executions, total: executions.length }
  else if (path.endsWith('/executions/1')) data = { id: 1, name: '六月清单', state: 'ACTIVE', tasks: [task] }
  else if (path === '/api/knowledge/docs') data = { records: [{ id: 5, title: '隔离规范' }], total: 1 }
  else if (path.endsWith('/documents/5/templates')) data = [{ id: 7, revision: 1, title: '规范模板', nodeCount: 1 }]
  else if (path.endsWith('/documents/5/templates/drafts')) data = template
  else if (path.endsWith('/templates/7') && req.method() === 'PATCH') { const body = req.postDataJSON(); templateWrites.push(body); template = { ...template, ...body, rowVersion: 1 }; data = template }
  else if (path.endsWith('/templates/7')) data = template
  else if (path === '/api/project') data = { records: [{ id: 8, name: '系统项目' }], total: 1 }
  else if (path.includes('unread')) data = 0
  else if (path.includes('announcement')) data = { records: [], total: 0 }
  await route.fulfill({ json: { code: 0, data, message: 'success' } })
})
try {
  console.log('Step: loading page')
  await page.goto(process.env.TEST_BASE_URL || 'http://127.0.0.1:15173/knowledge-planet/enterprise-compliance', { waitUntil: 'domcontentloaded', timeout: 20000 })
  await page.getByRole('button', { name: '创建执行清单', exact: true }).waitFor({ timeout: 8000 })
  console.log('Step: editing task')
  await page.getByRole('button', { name: '详情', exact: true }).click()
  const dialog = page.getByRole('dialog', { name: '任务详情' })
  await dialog.waitFor({ timeout: 3000 })
  await dialog.locator('.el-select').first().click()
  await page.getByRole('option', { name: '协会 B', exact: true }).click()
  await dialog.getByLabel('个人备注').fill('十月可再次复用，六月独立保存')
  await dialog.getByRole('button', { name: '保存', exact: true }).click()
  await page.getByText('隔离保存冲突', { exact: true }).waitFor()
  assert.equal(await dialog.getByLabel('个人备注').inputValue(), '十月可再次复用，六月独立保存')
  await dialog.getByRole('button', { name: '保存', exact: true }).click()
  await dialog.waitFor({ state: 'hidden' })
  assert.equal(writes.at(-1).answer.optionCode, 'B')
  assert.equal(writes.at(-1).rowVersion, 0)
  await page.getByText('备注 · 十月可再次复用，六月独立保存', { exact: true }).waitFor()
  await page.getByRole('button', { name: '等待跟进', exact: true }).click()
  const waiting = page.getByRole('dialog', { name: '设置等待跟进（进度保持进行中）', exact: true })
  await waiting.getByPlaceholder('如：等待客户确认函件').fill('等待客户资料')
  await waiting.getByPlaceholder('如：发函催复 / 周一电话跟进').fill('周一电话确认')
  await waiting.getByRole('button', { name: '设置等待', exact: true }).click()
  await waiting.waitFor({ state: 'hidden' })
  assert.equal(writes.at(-1).rowVersion, 1)
  await page.locator('.task-table .el-tag').getByText('等待跟进', { exact: true }).waitFor()
  await page.getByRole('button', { name: '等待跟进', exact: true }).click()
  await waiting.getByRole('button', { name: '清除等待', exact: true }).click()
  await waiting.waitFor({ state: 'hidden' })
  assert.equal(writes.at(-1).waiting, null)
  await page.locator('.task-table .el-checkbox').first().click()
  await page.getByText('暂无进行中任务', { exact: false }).waitFor()
  await page.getByText('已完成记录', { exact: true }).click()
  await page.getByText('协会选择', { exact: true }).waitFor()
  await page.locator('.task-table .el-checkbox').first().click()
  await page.getByText('暂无完成记录', { exact: true }).waitFor()
  console.log('Step: create, start, reuse')
  await page.getByRole('button', { name: '创建执行清单', exact: true }).click()
  const create = page.getByRole('dialog', { name: '创建执行清单', exact: true })
  await create.getByText('个人用途', { exact: true }).click()
  await create.getByRole('button', { name: '下一步', exact: true }).click()
  await create.locator('.el-select').first().click()
  await page.getByRole('option', { name: '隔离规范', exact: true }).click()
  await create.locator('.el-select').nth(1).click()
  await page.getByRole('option', { name: 'v1 · 规范模板（1 项）', exact: true }).click()
  await create.getByRole('button', { name: '下一步', exact: true }).click()
  await create.getByPlaceholder('如：君实·6月项目·协会与材料').fill('十月独立清单')
  await create.getByRole('button', { name: '创建清单', exact: true }).click()
  await create.waitFor({ state: 'hidden' })
  assert.equal(executionWrites.length, 1, 'create must not silently start')
  await page.getByRole('button', { name: '开始执行', exact: true }).click()
  await page.getByText('十月独立清单 · ACTIVE', { exact: true }).waitFor()
  await page.getByRole('button', { name: '再次使用', exact: true }).click()
  const reuse = page.getByRole('dialog', { name: '再次使用', exact: true })
  await reuse.locator('input').first().fill('十一月独立清单')
  await reuse.getByRole('button', { name: '创建新清单', exact: true }).click()
  await reuse.waitFor({ state: 'hidden' })
  assert.equal(executionWrites.at(-1).body.copyAnswers, false)
  assert.ok(executionWrites.at(-1).body.idempotencyKey)
  await page.getByText('十一月独立清单 · DRAFT', { exact: true }).waitFor()
  console.log('Step: template editing')
  await page.getByRole('tab', { name: '模板维护', exact: true }).click()
  await page.locator('.el-tab-pane:visible .toolbar .el-select').first().click()
  await page.getByRole('option', { name: '隔离规范', exact: true }).click()
  await page.getByRole('button', { name: '新建草稿', exact: true }).click()
  await page.getByRole('button', { name: '添加细项', exact: true }).click()
  const node = page.locator('.node-card').first()
  await node.locator('input').first().fill('协会配置细项')
  await node.locator('textarea').first().fill('上传者维护具体要求')
  await node.locator('.el-select').click()
  await page.getByRole('option', { name: '单选', exact: true }).click()
  await node.getByRole('button', { name: '添加选项', exact: true }).click()
  await node.getByPlaceholder('如协会名称').fill('协会 A')
  await node.getByPlaceholder('该选项的具体要求').fill('准备协会 A 所需材料')
  await page.getByRole('button', { name: '保存草稿', exact: true }).click()
  await page.getByText('草稿已保存，请记录版本编号以继续编辑', { exact: true }).waitFor()
  assert.equal(templateWrites.at(-1).nodes[0].title, '协会配置细项')
  assert.equal(templateWrites.at(-1).rowVersion, 0)
  assert.equal(templateWrites.at(-1).nodes[0].options[0].label, '协会 A')
  assert.equal('dueDate' in templateWrites.at(-1).nodes[0], false)
  assert.deepEqual(errors, [])
  console.log('PASS: v4 page, structured answer, conflict preserves input, note, completion/reopen, draft/start/reuse and template editing; isolated API fixtures.')
} catch (e) { console.log('URL', page.url(), 'ERRORS', errors, 'BODY', (await page.locator('body').innerText()).slice(0, 2500)); throw e } finally { await browser.close() }
