import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import path from 'node:path'

const { chromium } = await import(process.env.PLAYWRIGHT_MODULE || 'playwright')
const dist = path.resolve(process.argv[2])
const browser = await chromium.launch({ channel: 'msedge', headless: true })
const origin = 'http://127.0.0.1:18690'
const user = { id: 1, username: '布局验收', roles: [], permissions: ['approval:audit'] }
let items = [
  { i: 'panel.myApprovals', x: 0, y: 0, w: 4, h: 6 },
  { i: 'stat.myApplications', x: 4, y: 0, w: 3, h: 2 }
]
const mime = { '.html': 'text/html', '.js': 'text/javascript', '.css': 'text/css' }
const section = data => ({ status: 'success', data })
const approval = { id: 1, title: 'EXPERT_CREATE', status: 0, time: '2026-11-07T16:06:16', target: { path: '/profile/my-approvals' } }

try {
  const page = await browser.newPage({ viewport: { width: 1500, height: 1050 } })
  await page.addInitScript(u => {
    localStorage.setItem('expert_user', JSON.stringify(u))
    localStorage.setItem('expert_token', 'fixture')
  }, user)
  await page.route(origin + '/**', async route => {
    const url = new URL(route.request().url())
    if (url.pathname.startsWith('/api/')) {
      let data = []
      if (url.pathname === '/api/dashboard/layout/v2') {
        if (route.request().method() === 'PUT') items = route.request().postDataJSON().items
        data = { revision: 'fixture', items }
      } else if (url.pathname === '/api/dashboard/quick-access') {
        data = { items: ['page.knowledge', 'page.experts', 'page.myApplications'] }
      } else if (url.pathname === '/api/dashboard/overview') {
        data = {
          capabilities: ['knowledge', 'expert', 'myApplications'],
          sections: {
            myApprovals: section({ items: [approval], total: 1 }),
            myApplications: section({ latestItems: [approval], pendingOthersCount: 1, returnedCount: 0 })
          },
          businessDate: '2026-11-12', generatedAt: '2026-11-12T14:00:00+08:00'
        }
      } else if (url.pathname.endsWith('/me')) data = user
      return route.fulfill({ json: { code: 0, data } })
    }
    const file = path.join(dist, url.pathname.startsWith('/assets/') ? url.pathname.slice(1) : 'index.html')
    return route.fulfill({ body: await readFile(file), contentType: mime[path.extname(file)] || 'application/octet-stream' })
  })
  await page.goto(origin + '/dashboard')
  const approvals = page.locator('[data-card="panel.myApprovals"]')
  await approvals.getByText('专家新增申请').waitFor()
  await approvals.getByText(/2026-11-07 16:06:16/).waitFor()
  assert.equal(await approvals.getByText('EXPERT_CREATE').count(), 0)
  await page.getByRole('button', { name: '自定义布局', exact: true }).click()
  const palette = page.locator('.palette')
  await palette.waitFor()
  for (const duplicate of [/待我审批/, /退回待修改.*统计/, /专家库/, /知识星球/, /审批管理/]) {
    assert.equal(await palette.getByRole('button', { name: duplicate }).count(), 0, String(duplicate))
  }
  await palette.getByRole('button', { name: /我的申请进度/ }).click()
  await palette.getByRole('button', { name: /规范文档库/ }).click()
  await page.getByRole('button', { name: '保存布局', exact: true }).click()
  await page.getByRole('button', { name: '自定义布局', exact: true }).waitFor()
  await page.reload()
  await page.locator('[data-card="panel.applicationProgress"]').getByText('专家新增申请').waitFor()
  await page.locator('[data-card="stat.myApplications"]').waitFor()
  await page.locator('[data-card="page.complianceDocs"]').getByRole('button', { name: /规范文档库/ }).click()
  await page.waitForURL('**/knowledge-planet/enterprise-compliance?tab=docs')
  await page.getByRole('tab', { name: '规范文档库', selected: true }).waitFor()
  console.log('PASS: Edge approval localization, duplicate palette filtering, existing layout retention, optional cards save/reload and document-tab navigation.')
} finally {
  await browser.close()
}
