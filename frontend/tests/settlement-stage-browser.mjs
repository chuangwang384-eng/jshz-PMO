import assert from 'node:assert/strict'
import path from 'node:path'
import { invoiceFilenameTotal, suggestSettlementSlot } from '../src/views/project/components/settlementPresentation.js'

const { chromium } = await import(process.env.PLAYWRIGHT_MODULE || 'playwright')
const dist = path.resolve(process.argv[2])
const browser = await chromium.launch({ channel: 'msedge', headless: true })
const origin = 'http://isolated-pmo.test'
const phases = ['会议预算制作', '会前财务资料上传', '日程上传', '串场制作', '结算资料上传', '会后复盘', '归档', '完成']
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
const missing = () => slots.filter(item => item.definition.required && !item.complete).map(item => item.definition.name)
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
    if (url.pathname === '/api/project/1/settlement-materials/5') data = { slots, canArchive: true, budgetSources: [] }
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
  const steps = await page.locator('.steps-row').evaluate(el => ({ width: el.clientWidth, content: el.scrollWidth }))
  assert.ok(steps.content <= steps.width + 1)
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
  await advance.waitFor()
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
  assert.equal(await page.getByText('盖章结算单', { exact: true }).count(), 0)
  await page.setViewportSize({ width: 600, height: 800 })
  await page.getByLabel('查看项目阶段').waitFor()
  assert.deepEqual(errors, [])
  console.log('Settlement stage browser regression passed.')
} finally {
  await browser.close()
}
