import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import path from 'node:path'

const { chromium } = await import(process.env.PLAYWRIGHT_MODULE || 'playwright')
const dist = path.resolve(process.argv[2])
const origin = 'http://127.0.0.1:18692'
const user = { id: 1, username: '审批员', permissions: ['approval:audit'] }
const types = { '.html': 'text/html', '.js': 'text/javascript', '.css': 'text/css' }
const browser = await chromium.launch({ channel: 'msedge', headless: true })
const rows = [1, 2, 3, 4, 5].map(id => ({
  id, status: 0, expertName: `测试专家${id}`, currentAssignee: id >= 4 ? '其他人' : '',
  extraApprover: id === 4 ? user.username : '', changes: { 姓名: `测试专家${id}` }
}))
const writes = []
try {
  const page = await browser.newPage()
  await page.addInitScript(value => {
    localStorage.setItem('expert_user', JSON.stringify(value))
    localStorage.setItem('expert_token', 'fixture')
  }, user)
  await page.route(origin + '/**', async route => {
    const url = new URL(route.request().url())
    if (!url.pathname.startsWith('/api/')) {
      const file = path.join(dist, url.pathname.startsWith('/assets/') ? url.pathname.slice(1) : 'index.html')
      return route.fulfill({ body: await readFile(file), contentType: types[path.extname(file)] || 'application/octet-stream' })
    }
    let data = []
    if (url.pathname === '/api/approval/requests') {
      const items = url.searchParams.has('status') ? rows.filter(row => row.status === 0) : []
      data = { items, total: items.length }
    } else if (url.pathname.endsWith('/me')) {
      data = user
    } else {
      const action = url.pathname.match(/\/requests\/(\d+)\/(approve|reject)$/)
      if (action) {
        const [, id, type] = action
        writes.push({ id: Number(id), type, ...route.request().postDataJSON() })
        rows.find(row => row.id === Number(id)).status = type === 'approve' ? 1 : 2
      }
    }
    return route.fulfill({ json: { code: 0, data } })
  })
  await page.goto(origin + '/profile/approval/manage')
  const entry = page.getByRole('button', { name: '快速审批', exact: true })
  await entry.click()
  const dialog = page.getByRole('dialog', { name: '快速审批（变更前后对比）' })
  const next = dialog.getByRole('button', { name: '下一条' })
  const previous = dialog.getByRole('button', { name: '上一条' })
  const note = dialog.getByRole('textbox')
  await dialog.getByText('剩余 4 条 · 第 1 条').waitFor()
  assert.equal(await previous.isDisabled(), true)
  await note.fill('不会带到下一条')
  await next.click()
  await dialog.getByText('剩余 4 条 · 第 2 条').waitFor()
  assert.equal(await note.inputValue(), '')
  await previous.click()
  await dialog.getByText('剩余 4 条 · 第 1 条').waitFor()
  await dialog.getByRole('button', { name: '确认驳回' }).click()
  await page.getByText('请填写驳回意见', { exact: true }).waitFor()
  assert.equal(writes.length, 0)
  await note.fill('资料待补')
  await dialog.getByRole('button', { name: '确认驳回' }).click()
  await dialog.locator('.diff-meta b').getByText('测试专家2').waitFor()
  await entry.waitFor({ state: 'visible' })
  for (const id of [2, 3, 4]) {
    await dialog.locator('.diff-meta b').getByText(`测试专家${id}`).waitFor()
    if (id === 3) await entry.waitFor({ state: 'hidden' })
    assert.equal(await note.inputValue(), '')
    await dialog.getByRole('button', { name: '确认通过' }).click()
  }
  await dialog.waitFor({ state: 'hidden' })
  await entry.waitFor({ state: 'hidden' })
  assert.deepEqual(writes.map(({ id, type }) => [id, type]), [[1, 'reject'], [2, 'approve'], [3, 'approve'], [4, 'approve']])
  assert.equal(writes[0].comment, '资料待补')
  console.log('PASS: quick approval threshold, navigation, comment reset, reject validation, automatic advance and assignee handling.')
} finally {
  await browser.close()
}
