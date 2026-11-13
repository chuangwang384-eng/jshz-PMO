import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import path from 'node:path'

const { chromium } = await import(process.env.PLAYWRIGHT_MODULE || 'playwright')
const dist = path.resolve(process.argv[2])
const browser = await chromium.launch({ channel: 'msedge', headless: true })
const origin = 'http://127.0.0.1:18693'
const types = { '.html': 'text/html', '.js': 'text/javascript', '.css': 'text/css' }
const user = { id: 1, username: '测试', permissions: ['project:view', 'project:manage'] }
const speaker = { id: 11, expertName: '张医生', role: 'SPEAKER', expertId: null }
const other = { id: 12, expertName: '王医生', role: 'SPEAKER', expertId: null }
const candidate = { id: 13, expertName: '李医生', role: 'SPEAKER', expertId: null }
const writes = []
const sample = name => ({ name, mimeType: 'application/pdf', buffer: Buffer.from('%PDF fixture') })
let parses = 0
try {
  const page = await browser.newPage({ viewport: { width: 1440, height: 950 } })
  await page.addInitScript(value => {
    localStorage.setItem('expert_user', JSON.stringify(value))
    localStorage.setItem('expert_token', 'fixture')
  }, user)
  await page.route(origin + '/**', async route => {
    const request = route.request()
    const url = new URL(request.url())
    if (!url.pathname.startsWith('/api/')) {
      const file = path.join(dist, url.pathname.startsWith('/assets/') ? url.pathname.slice(1) : 'index.html')
      return route.fulfill({ body: await readFile(file), contentType: types[path.extname(file)] || 'application/octet-stream' })
    }
    let data = []
    if (url.pathname.endsWith('/me')) data = user
    if (url.pathname === '/api/project/55') {
      data = { id: 55, name: '示例项目', canManage: true, phaseId: 1, phaseName: '日程上传', phases: [{ id: 1, name: '日程上传' }] }
    }
    if (url.pathname === '/api/project/55/schedule') data = { speakers: [speaker, other], candidates: [candidate] }
    if (url.pathname === '/api/experts') {
      assert.equal(url.searchParams.get('keyword'), '李医生')
      data = { total: 3, items: [
        { id: 51, 姓名: '李医生', 单位: '甲医院' },
        { id: 52, 姓名: '李医生', 单位: '乙医院' },
        { id: 53, 姓名: '王医生', 单位: '李医生诊所' }
      ] }
    }
    if (url.pathname === '/api/experts/resume/parse') {
      assert.equal(url.searchParams.get('contentNameOnly'), 'true')
      parses++
      data = { fields: parses === 1 ? { 姓名: '其他人' } : parses === 2 ? {} : { 姓名: parses === 4 ? '李医生' : '张医生', 单位: '测试医院' } }
    }
    if (url.pathname === '/api/project/55/speakers/archive-manual') {
      writes.push(request.postDataJSON())
      const target = writes.at(-1).name === '李医生' ? candidate : speaker
      target.expertId = 100 + writes.length
      data = { expertId: target.expertId, boundSpeakers: 1 }
    }
    return route.fulfill({ json: { code: 0, data } })
  })
  await page.goto(origin + '/project/55')
  await page.getByText('选择同名专家', { exact: true }).click()
  await page.getByRole('option', { name: '李医生·甲医院' }).waitFor()
  await page.getByRole('option', { name: '李医生·乙医院' }).waitFor()
  assert.equal(await page.getByRole('option', { name: /王医生/ }).count(), 0)
  await page.getByText('选择同名专家', { exact: true }).click()
  await page.getByRole('button', { name: /批量建档/ }).click()
  const dialog = page.getByRole('dialog', { name: '库外专家批量建档' })
  await dialog.locator('input[type=file][multiple]').setInputFiles([sample('张医生.pdf'), sample('王医生.pdf')])
  const cards = dialog.locator('.resume-card')
  for (const [index, name] of ['张医生', '王医生'].entries()) {
    await cards.nth(index).getByText('选择参会专家', { exact: true }).click()
    const listId = await cards.nth(index).getByRole('combobox').getAttribute('aria-controls')
    await page.locator(`[id="${listId}"]`).getByRole('option', { name, exact: true }).click()
  }
  await dialog.getByRole('button', { name: '开始解析' }).click()
  await cards.nth(0).getByText(/正文姓名.*不一致/).waitFor()
  await cards.nth(1).getByText('未从简历正文识别到姓名').waitFor()
  assert.equal(await dialog.getByRole('button', { name: '提交已选（0）' }).isDisabled(), true)
  await cards.nth(0).locator('input[type=file]').setInputFiles(sample('正确简历.pdf'))
  await dialog.getByRole('button', { name: '提交已选（1）' }).click()
  await page.getByText('库外专家待入档 1 人').waitFor()
  assert.deepEqual(writes, [{ name: '张医生', unit: '测试医院', requirePendingSpeaker: true }])
  await dialog.getByRole('button', { name: '关闭', exact: true }).click()
  await page.getByRole('row').filter({ hasText: '王医生' }).getByText('库外专家·去建档', { exact: true }).first().click()
  const single = page.getByRole('dialog', { name: '专家建档', exact: true })
  await single.getByText('上传简历', { exact: true }).waitFor()
  await single.locator('input[type=file]:not([multiple])').setInputFiles(sample('单人.pdf'))
  assert.equal(await single.getByRole('combobox').count(), 0)
  await single.locator('.resume-card strong').getByText('王医生').waitFor()
  await single.getByRole('button', { name: '关闭', exact: true }).click()
  await page.getByRole('button', { name: '去建档', exact: true }).click()
  await single.locator('input[type=file]:not([multiple])').setInputFiles(sample('候选.pdf'))
  await single.locator('.resume-card strong').getByText('李医生').waitFor()
  await single.getByRole('button', { name: '开始解析' }).click()
  await single.getByRole('button', { name: '确认建档' }).click()
  await single.getByText('已建档，回绑参会记录 1 条').waitFor()
  assert.deepEqual(writes.at(-1), { name: '李医生', unit: '测试医院', requirePendingSpeaker: false })
  console.log('PASS: project batch resume reuse, strict name matching, replacement, submission, refresh and single-speaker preset.')
} finally {
  await browser.close()
}
