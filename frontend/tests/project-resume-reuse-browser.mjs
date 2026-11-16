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
const complianceWrites = []
const complianceTodos = [
  { id: 71, title: '签到表', status: 'DONE', rowVersion: 2 },
  { id: 72, title: '现场照片', status: 'PENDING', rowVersion: 1 }
]
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
      data = { id: 55, name: '示例项目', canManage: true, canOperate: true, phaseId: 1, phaseName: '日程上传',
        phases: [{ id: 1, name: '日程上传' }], complianceTodos }
    }
    if (url.pathname === '/api/compliance/notebook/todos/72' && request.method() === 'PUT') {
      const todo = complianceTodos[1]
      const body = request.postDataJSON()
      assert.equal(body.rowVersion, todo.rowVersion)
      complianceWrites.push(body)
      todo.status = body.status
      todo.rowVersion++
      data = null
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
      parses++
      const filenameFallbackDisabled = url.searchParams.get('contentNameOnly') === 'true'
      data = { fields: parses === 1 ? { 姓名: '其他人' }
        : parses === 2 || (parses === 4 && filenameFallbackDisabled) ? {}
        : parses === 4 ? { 姓名: '李医生', 联系方式: '无' }
        : { 姓名: '张医生', 单位: '测试医院' },
        photoCandidates: [{ mime: 'image/png', base64: 'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+jH1sAAAAASUVORK5CYII=' }] }
    }
    if (url.pathname === '/api/project/55/speakers/archive-manual') {
      const contentType = request.headers()['content-type']
      if (contentType.startsWith('multipart/form-data')) {
        const form = await new Response(request.postDataBuffer(), { headers: { 'content-type': contentType } }).formData()
        const photo = form.get('photo')
        assert.equal(photo.name, 'resume-photo.png')
        assert.equal(photo.type, 'image/png')
        assert.deepEqual([...new Uint8Array(await photo.arrayBuffer()).slice(0, 8)], [137, 80, 78, 71, 13, 10, 26, 10])
        writes.push({ ...JSON.parse(await form.get('data').text()), hasPhoto: true })
      } else {
        writes.push(request.postDataJSON())
      }
      const target = writes.at(-1).name === '李医生' ? candidate : speaker
      target.expertId = 100 + writes.length
      data = { expertId: target.expertId, boundSpeakers: 1 }
    }
    return route.fulfill({ json: { code: 0, data } })
  })
  await page.setViewportSize({ width: 1191, height: 675 })
  await page.goto(origin + '/project/55')
  const compliance = page.locator('.pd-compliance')
  const item = compliance.getByRole('listitem').filter({ hasText: '现场照片' })
  await item.waitFor()
  const sidebar = page.locator('.pd-side')
  const sidebarScroll = await sidebar.evaluate(side => {
    const main = side.closest('main')
    const mainTop = main.getBoundingClientRect().top
    const before = side.getBoundingClientRect().top - mainTop
    main.scrollTop += before + 8
    return { gap: side.getBoundingClientRect().top - mainTop, before, scroll: main.scrollTop,
      max: main.scrollHeight - main.clientHeight, stickyTop: getComputedStyle(side).top }
  })
  assert.ok(Math.abs(sidebarScroll.gap) <= 1, `Expected sidebar flush with scroll container, ${JSON.stringify(sidebarScroll)}`)
  await page.setViewportSize({ width: 1024, height: 675 })
  assert.equal(await sidebar.evaluate(side => getComputedStyle(side).position), 'static')
  await page.setViewportSize({ width: 1191, height: 675 })
  assert.equal(await compliance.getByRole('listitem').filter({ hasText: '签到表' }).count(), 0)
  await item.getByRole('button', { name: '完成', exact: true }).click()
  await compliance.getByText('暂无待完成事项', { exact: true }).waitFor()
  const views = compliance.getByRole('radiogroup', { name: '合规事项视图' })
  await views.getByText('已完成', { exact: true }).click()
  await item.getByRole('button', { name: '重开', exact: true }).click()
  await item.waitFor({ state: 'hidden' })
  await views.getByText('待完成', { exact: true }).click()
  await item.getByRole('button', { name: '完成', exact: true }).waitFor()
  assert.deepEqual(complianceWrites, [{ status: 'DONE', rowVersion: 1 }, { status: 'PENDING', rowVersion: 2 }])
  await page.setViewportSize({ width: 1440, height: 950 })
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
  await cards.nth(0).getByText(/姓名.*不一致/).waitFor()
  await cards.nth(1).getByText(/未从简历.*识别到姓名/).waitFor()
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
  await single.locator('input[type=file]:not([multiple])').setInputFiles(sample('副本李医生简介-测试医院.pdf'))
  await single.locator('.resume-card strong').getByText('李医生').waitFor()
  await single.getByRole('button', { name: '开始解析' }).click()
  await single.getByText('请补全姓名和单位').waitFor()
  assert.equal(await single.getByRole('button', { name: '确认建档' }).isDisabled(), true)
  const contact = single.locator('.el-form-item').filter({ hasText: '联系方式' }).getByRole('textbox')
  assert.equal(await contact.inputValue(), '')
  await single.locator('.el-form-item').filter({ hasText: '单位' }).getByRole('textbox').fill('测试医院')
  await contact.fill('错误号码')
  await single.getByRole('button', { name: '确认建档' }).click()
  await single.getByText('联系方式须为手机号或邮箱').waitFor()
  assert.equal(writes.length, 1)
  await contact.fill('无')
  await single.getByRole('button', { name: '候选证件照 1' }).click({ timeout: 5000 })
  assert.equal(await single.getByRole('button', { name: '候选证件照 1' }).getAttribute('aria-pressed'), 'true')
  await single.getByRole('button', { name: '确认建档' }).click()
  await single.getByText('已建档，回绑参会记录 1 条').waitFor()
  assert.deepEqual(writes.at(-1), { name: '李医生', unit: '测试医院', requirePendingSpeaker: false, hasPhoto: true })
  console.log('PASS: project batch resume reuse, single-speaker archiving, and project compliance views, completion and reopen. Sidebar aligns with scroll top.')
} finally {
  await browser.close()
}
