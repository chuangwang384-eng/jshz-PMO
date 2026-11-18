import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import path from 'node:path'

const { chromium } = await import(process.env.PLAYWRIGHT_MODULE || 'playwright')
const dist = path.resolve(process.argv[2])
const browser = await chromium.launch({ channel: 'msedge', headless: true })
const origin = 'http://127.0.0.1:18691'
const types = { '.html': 'text/html', '.js': 'text/javascript', '.css': 'text/css' }
const existing = { id: 42, 姓名: '张医生', 单位: '示例医院', 职称: '旧职称', 科室: '原科室' }
const document = name => ({ name, mimeType: 'application/pdf', buffer: Buffer.from('%PDF fixture') })

async function exercise(permissions) {
  const page = await browser.newPage({ viewport: { width: 1440, height: 980 } })
  const user = { id: 1, username: '验收用户', roles: [], permissions }
  const writes = []
  let parsed = 0
  await page.addInitScript(value => {
    localStorage.setItem('expert_user', JSON.stringify(value))
    localStorage.setItem('expert_token', 'fixture')
  }, user)
  await page.route(origin + '/**', async route => {
    const request = route.request()
    const url = new URL(request.url())
    const method = request.method()
    if (!url.pathname.startsWith('/api/')) {
      const file = path.join(dist, url.pathname.startsWith('/assets/') ? url.pathname.slice(1) : 'index.html')
      return route.fulfill({ body: await readFile(file), contentType: types[path.extname(file)] || 'application/octet-stream' })
    }
    let data = []
    if (url.pathname === '/api/experts/resume/parse') {
      parsed++
      const name = parsed <= 3 ? '张医生' : '王医生'
      data = parsed === 5 ? { fields: {}, hitCount: 0, message: '解析失败' } : {
        fields: { 姓名: name, 单位: '示例医院', 职称: '新职称' }, hitCount: 3,
        photoCandidates: parsed === 1 ? [{ mime: 'image/png', base64: 'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+/l9sAAAAASUVORK5CYII=' }] : []
      }
    } else if (url.pathname === '/api/experts' && method === 'GET') {
      const items = url.searchParams.get('keyword') === '王医生' ? [] : [existing]
      data = { items, total: items.length }
    } else if (url.pathname === '/api/experts/42' && method === 'GET') {
      data = existing
    } else if (['POST', 'PUT'].includes(method) && /\/api\/(experts|approval)/.test(url.pathname)) {
      writes.push({ method, path: url.pathname, payload: request.postDataJSON() })
      data = { id: 43 }
    } else if (url.pathname.endsWith('/me')) {
      data = user
    }
    return route.fulfill({ json: { code: 0, data } })
  })
  try {
    await page.goto(origin + '/experts')
    await page.getByRole('button', { name: '批量上传简历' }).click()
    const dialog = page.getByRole('dialog', { name: '批量上传简历' })
    await dialog.getByText(/单份最多 30 MB/).waitFor()
    await dialog.locator('input[type=file][multiple]').evaluate(input => {
      const documents = new DataTransfer()
      const limit = 30 * 1024 * 1024
      documents.items.add(new File([new Uint8Array(limit)], '上限.pdf'))
      documents.items.add(new File([new Uint8Array(limit + 1)], '超过上限.pdf'))
      input.files = documents.files
      input.dispatchEvent(new Event('change', { bubbles: true }))
    })
    await dialog.getByText('上限.pdf', { exact: true }).waitFor()
    assert.equal(await dialog.locator('.resume-card').count(), 1)
    await dialog.getByRole('button', { name: '移除', exact: true }).click()
    await page.locator('input[type=file][multiple]').setInputFiles(['覆盖.pdf', '保留.pdf', '取消.pdf', '新增.pdf', '失败.pdf'].map(document))
    await page.getByRole('button', { name: '开始解析' }).click()
    await page.getByRole('button', { name: '提交已选（4）' }).waitFor()
    const cards = page.locator('.resume-card')
    assert.equal(await cards.locator('.el-form').count(), 5)
    const portrait = cards.first().locator('header').getByRole('button', { name: '候选证件照 1' })
    await portrait.click()
    assert.equal(await portrait.getAttribute('aria-pressed'), 'true')
    await portrait.click()
    await cards.first().getByRole('button', { name: '收起', exact: true }).click()
    assert.equal(await cards.first().locator('.el-form').count(), 0)
    await page.getByRole('button', { name: '提交已选（4）' }).click()
    const compare = page.getByRole('dialog', { name: '发现同名同单位专家' })
    // Three candidates share one identity. Keep the first two and cancel the third.
    await compare.getByText('覆盖.pdf', { exact: true }).waitFor()
    await compare.getByRole('button', { name: '都保留' }).click()
    await compare.getByText('取消.pdf', { exact: true }).waitFor()
    await compare.getByRole('button', { name: '取消当前简历解析' }).click()
    await compare.getByText('原科室', { exact: true }).waitFor()
    await compare.getByText('新职称', { exact: true }).waitFor()
    await compare.getByRole('button', { name: '覆盖', exact: true }).click()
    await compare.getByText('保留.pdf', { exact: true }).waitFor()
    await compare.getByRole('button', { name: '都保留' }).click()
    await page.getByText('已处理 3 张专家卡', { exact: true }).waitFor()
    assert.equal(writes.length, 3)
    if (permissions.includes('expert:edit')) {
      assert.deepEqual(writes.map(write => [write.method, write.path]), [
        ['PUT', '/api/experts/42'], ['POST', '/api/experts'], ['POST', '/api/experts']
      ])
      assert.deepEqual(writes[0].payload, { 姓名: '张医生', 单位: '示例医院', 职称: '新职称' })
    } else {
      assert.deepEqual(writes.map(write => write.path), [
        '/api/approval/requests', '/api/approval/create-request', '/api/approval/create-request'
      ])
      assert.equal(writes[0].payload.expertId, 42)
      assert.deepEqual(writes[0].payload.changes, { 姓名: '张医生', 单位: '示例医院', 职称: '新职称' })
    }
    await page.getByRole('button', { name: '重试解析' }).click()
    await page.getByRole('button', { name: '提交已选（1）' }).waitFor()
  } finally {
    await page.close()
  }
}

try {
  await exercise(['expert:add', 'expert:edit'])
  await exercise([])
  console.log('PASS: Edge batch upload, duplicate comparison and decisions, partial failure retry, patch fields and approval routing.')
} finally {
  await browser.close()
}
