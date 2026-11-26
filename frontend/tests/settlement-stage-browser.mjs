import assert from 'node:assert/strict'
import path from 'node:path'
import { invoiceFilenameTotal, suggestSettlementSlot } from '../src/views/project/components/settlementPresentation.js'

const { chromium } = await import(process.env.PLAYWRIGHT_MODULE || 'playwright')
const dist = path.resolve(process.argv[2])
const browser = await chromium.launch({ channel: 'msedge', headless: true })
const origin = 'http://isolated-pmo.test'
const phases = ['会议预算制作', '会前财务资料上传', '日程上传', '串场制作', '结算资料上传', '会议费用结算及项目结项']
  .map((name, index) => ({ id: index + 1, sort: index + 1, name, templates: [] }))
const slots = [
  { code: 'U01', name: '酒店发票', group: '供应商费用核对', required: true },
  { code: 'U10', name: '真实结算单', group: '结算单制作', required: true, deadline: '会议结束后2个工作日内' },
  { code: 'U17', name: '平台审核提交凭证', group: '财务提交内审', required: true },
  { code: 'U18', name: '补充资料', group: '财务提交内审', required: false }
].map(definition => ({ definition, writable: true, batches: [], complete: false }))
const doc = (id, fileName) => ({ id, fileName, uploadBy: '测试用户', createTime: '2026-11-26 08:00:00' })
slots[0].batches = [[doc(1, '酒店发票8656元餐费.pdf'), doc(2, '20261126餐.pdf')]]
slots[0].complete = true
assert.deepEqual(invoiceFilenameTotal([doc(1, '¥1,200.05住宿.pdf'), doc(2, '8656餐.pdf'), doc(3, '金额123元与456元.pdf')]), { cents: 120005, count: 1 })
assert.equal(suggestSettlementSlot('发票.pdf', slots), '')
assert.equal(suggestSettlementSlot('酒店发票真实结算单.pdf', slots), '')
assert.equal(suggestSettlementSlot('盖章结算单.pdf', slots), '')
const writes = []
let failOnce = true
let deletions = 0
let archived = false
let projectCompleted = false
const missing = () => {
  const pending = slots.filter(item => item.definition.required && !item.complete).map(item => item.definition.name)
  return pending.length || archived ? pending : ['归档结算资料包']
}
const closeoutItems = [
  { code: 'labor', label: '劳务费用全部结转完毕', evidence: '结转记录或回执', files: [], confirmed: false },
  { code: 'execution', label: '会务执行费用结算至我司账户', evidence: '银行回单', files: [], confirmed: false }
]
try {
  const page = await browser.newPage({ viewport: { width: 1280, height: 800 }, reducedMotion: 'reduce' })
  const errors = []
  page.on('pageerror', error => errors.push(error.message))
  const user = { id: 1, username: 'fixture', roles: [], permissions: ['project:manage'] }
  await page.addInitScript(auth => {
    localStorage.setItem('expert_user', JSON.stringify(auth))
    localStorage.setItem('expert_token', 'fixture')
  }, user)
  await page.route('**/*', async route => {
    const req = route.request()
    const url = new URL(req.url())
    if (url.origin !== origin) return route.abort()
    if (!url.pathname.startsWith('/api/')) return route.fulfill({ path: path.join(dist, url.pathname.startsWith('/assets/') ? url.pathname : '/index.html') })
    let data = []
    if (url.pathname === '/api/auth/me') data = user
    if (url.pathname === '/api/project/1') data = { id: 1, name: '测试结算', status: 'ACTIVE', phaseId: 5, phaseName: '结算资料上传', projectType: 'PLATFORM', phases, docs: [], canOperate: true, canAdvance: true, missingRequiredTemplates: missing() }
    if (url.pathname === '/api/project/2') data = { id: 2, name: '测试结项', status: projectCompleted ? 'COMPLETED' : 'ACTIVE', phaseId: 6, phaseName: '会议费用结算及项目结项', projectType: 'PLATFORM', phases, docs: closeoutItems.flatMap(item => item.files), canOperate: !projectCompleted, canAdvance: !projectCompleted, canReopen: projectCompleted, missingRequiredTemplates: [] }
    if (url.pathname === '/api/project/1/settlement-materials/5') data = { slots, canArchive: true, budgetSources: [] }
    if (url.pathname === '/api/project/1/settlement-materials/5/archive' && req.method() === 'POST') archived = true
    if (url.pathname === '/api/project/2/closeout/6') data = { items: closeoutItems, writable: !projectCompleted, completed: projectCompleted, canComplete: !projectCompleted }
    if (url.pathname === '/api/project/2/complete' && req.method() === 'PUT') {
      assert.ok(closeoutItems.every(item => item.confirmed))
      projectCompleted = true
    }
    if (url.pathname === '/api/project/2/reopen' && req.method() === 'PUT') {
      assert.equal(req.postDataJSON().reason, '补正资料')
      projectCompleted = false
    }
    const match = url.pathname.match(/\/settlement-materials\/5\/(U\d+)(?:\/(\d+))?$/)
    if (match && req.method() === 'POST') {
      const target = slots.find(item => item.definition.code === match[1])
      const form = await new Response(req.postDataBuffer(), { headers: { 'content-type': req.headers()['content-type'] } }).formData()
      const files = form.getAll('files')
      writes.push({ code: match[1], append: form.get('append'), names: files.map(file => file.name) })
      if (match[1] === 'U10' && failOnce) {
        failOnce = false
        return route.fulfill({ status: 400, json: { code: 400, message: '模拟提交失败' } })
      }
      target.batches = [[...(target.batches[0] || []), ...files.map((file, index) => doc(10 * writes.length + index, file.name))]]
      target.complete = true
    }
    if (match && req.method() === 'DELETE') {
      const target = slots.find(item => item.definition.code === match[1])
      target.batches[0] = target.batches[0].filter(file => file.id !== Number(match[2]))
      deletions++
      archived = false
    }
    const closeout = url.pathname.match(/\/closeout\/6\/(labor|execution)$/)
    if (closeout && req.method() === 'POST') {
      const form = await new Response(req.postDataBuffer(), { headers: { 'content-type': req.headers()['content-type'] } }).formData()
      const item = closeoutItems.find(candidate => candidate.code === closeout[1])
      assert.deepEqual([...form.keys()], ['files'])
      item.files = [{ ...doc(200 + closeoutItems.indexOf(item), form.get('files').name),
        filePath: `project_closeout_${item.code}_test.pdf` }]
      item.confirmed = true
    }
    return route.fulfill({ json: { code: 0, data } })
  })
  await page.goto(origin + '/project/1')
  const advance = page.getByRole('button', { name: '推进下一阶段' })
  await advance.waitFor()
  assert.equal(await advance.isDisabled(), true)
  const initialViewport = await advance.boundingBox()
  assert.ok(initialViewport.y + initialViewport.height <= 800)
  assert.equal(await page.locator('#settlement-slot-U01').count(), 0)
  assert.equal(await page.getByText('当前待办', { exact: true }).count(), 0)
  const steps = page.locator('.steps-row')
  assert.equal(await steps.locator('.step-state:visible').count(), phases.length)
  await page.getByText('已完成', { exact: true }).last().click()
  assert.equal(await page.locator('#settlement-slot-U01 h4 button').getAttribute('aria-expanded'), 'false')
  await page.getByRole('button', { name: '查看缺项' }).click()
  await page.locator('#settlement-slot-U10.settlement-slot-focus').waitFor()
  await page.locator('#settlement-slot-U10').getByText('会议结束后2个工作日内').first().waitFor()
  const sample = name => ({ name, mimeType: 'application/pdf', buffer: Buffer.from('fixture') })
  await page.locator('.settlement-drop input').setInputFiles([sample('酒店发票¥3000会场.pdf'), sample('真实结算单.pdf'), sample('凭证.pdf')])
  assert.equal(await page.getByLabel('酒店发票¥3000会场.pdf').inputValue(), 'U01')
  assert.equal(await page.getByLabel('凭证.pdf', { exact: true }).inputValue(), '')
  await page.getByLabel('凭证.pdf', { exact: true }).selectOption('U17')
  await page.getByRole('button', { name: '确认归类并提交' }).click()
  await page.locator('.settlement-editor').getByText('模拟提交失败').waitFor()
  assert.equal(await page.getByLabel('酒店发票¥3000会场.pdf').count(), 0)
  assert.equal(writes.find(write => write.code === 'U01').append, 'true')
  await page.getByRole('button', { name: '确认归类并提交' }).click()
  await page.getByText('已提交 1 个文件，剩余 0 个待处理。').waitFor()
  assert.equal(writes.filter(write => write.code === 'U01').length, 1)
  await page.getByText('请先归档当前结算资料包', { exact: true }).waitFor()
  assert.equal(await advance.isDisabled(), true)
  await page.getByRole('button', { name: '查看归档' }).click()
  await page.getByRole('button', { name: '归档结算资料包' }).click()
  await page.waitForFunction(() => !document.querySelector('.pd-settlement-footer button:last-child').disabled)
  await page.getByText('全部', { exact: true }).last().click()
  await page.getByText('文件名标记金额合计 ¥11656.00（2 / 3 个文件）').waitFor()
  await page.locator('#settlement-slot-U01 h4 button').click()
  await page.getByRole('button', { name: '酒店发票8656元餐费.pdf的更多操作' }).click()
  await page.getByRole('menuitem', { name: '删除', exact: true }).click()
  await page.getByRole('dialog', { name: '删除结算资料' }).getByRole('button', { name: '取消' }).click()
  assert.equal(deletions, 0)
  await page.getByRole('button', { name: '酒店发票8656元餐费.pdf的更多操作' }).click()
  await page.getByRole('menuitem', { name: '删除', exact: true }).click()
  await page.getByRole('dialog', { name: '删除结算资料' }).getByRole('button', { name: '删除', exact: true }).click()
  await page.getByText('文件名标记金额合计 ¥3000.00（1 / 2 个文件）').waitFor()
  assert.equal(deletions, 1)
  await page.getByText('请先归档当前结算资料包', { exact: true }).waitFor()
  assert.equal(await advance.isDisabled(), true)
  assert.equal(await page.getByText('盖章结算单', { exact: true }).count(), 0)
  await page.setViewportSize({ width: 600, height: 800 })
  await steps.getByRole('button', { name: /结算资料上传/ }).waitFor()
  await page.setViewportSize({ width: 1280, height: 800 })
  await page.goto(origin + '/project/2')
  const complete = page.getByRole('button', { name: '项目完结', exact: true })
  await complete.waitFor()
  assert.ok(await complete.isDisabled())
  for (const [index, item] of closeoutItems.entries()) {
    const form = page.getByRole('form', { name: item.label })
    await form.getByLabel('选择凭证文件').setInputFiles(sample(`${item.code}.pdf`))
    await form.getByRole('button', { name: '上传凭证并确认' }).click()
    await page.getByText(`已确认 ${index + 1} / 2 项`, { exact: true }).waitFor()
  }
  assert.ok(await complete.isEnabled())
  await page.locator('.side-doc').getByText('测试结项-labor.pdf').waitFor()
  await complete.click()
  await page.getByRole('button', { name: '确认完结', exact: true }).click()
  const reopen = page.getByRole('button', { name: '重新打开', exact: true })
  await reopen.waitFor()
  await page.locator('.pd-title-row').getByText('已完成', { exact: true }).waitFor()
  assert.equal(await page.getByRole('button', { name: '替换凭证并确认' }).count(), 0)
  await reopen.click()
  await page.getByRole('dialog', { name: '重新打开项目' }).getByRole('textbox').fill('补正资料')
  await page.getByRole('dialog', { name: '重新打开项目' }).getByRole('button', { name: '重新打开', exact: true }).click()
  await complete.waitFor()
  assert.ok(await complete.isEnabled())
  assert.deepEqual(errors, [])
  console.log('Settlement stage browser regression passed.')
} finally {
  await browser.close()
}
