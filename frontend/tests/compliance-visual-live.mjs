import assert from 'node:assert/strict'
import {mkdir,readFile} from 'node:fs/promises'
const {chromium}=await import(process.env.PLAYWRIGHT_MODULE || 'playwright')
if(process.env.LIVE_V4_ACCEPTANCE!=='acceptance_v4' || !process.env.V4_TEST_PASSWORD) throw Error('Isolated fixture required')
const browser=await chromium.launch({channel:'msedge',headless:true})
try {
 const page=await browser.newPage({viewport:{width:1500,height:1000}})
 const errors=[];page.on('pageerror',e=>errors.push(e.message))
 await page.goto('http://localhost:5173/login?redirect=/knowledge-planet/enterprise-compliance')
 await page.locator('input[autocomplete="username"]').fill('验收维护人')
 await page.getByPlaceholder('密码',{exact:true}).fill(process.env.V4_TEST_PASSWORD)
 await page.getByRole('button',{name:/登\s*录/}).click()
 await page.getByRole('tab',{name:'模板维护',exact:true}).click()
 await page.locator('.editor .toolbar .el-select').first().click()
 await page.getByRole('option',{name:'隔离规范',exact:true}).click()
 const legacy=await page.request.get('http://localhost:5173/api/compliance/v4/documents/1/template-workspace/legacy-candidates'); assert.equal((await legacy.json()).code,0)
 await page.getByRole('textbox',{name:'模板名称',exact:true}).fill('可视化真实验收')
 await page.getByRole('button',{name:'添加事项',exact:true}).click()
 await page.locator('.properties input').first().fill('真实页面新增事项')
 await page.locator('.properties textarea').first().fill('原文核实后提交材料')
 await page.getByRole('button',{name:'保存并生效',exact:true}).click()
 await page.getByText('已生效，仅用于之后新建的清单',{exact:true}).waitFor()
 const upload=await page.request.post('http://localhost:5173/api/knowledge/docs?category=enterprise-compliance&title='+encodeURIComponent('候选合并真实验收'),{multipart:{file:{name:'北京融和医学发展基金会合规要求.pdf',mimeType:'application/pdf',buffer:await readFile(process.env.V4_SAMPLE_PDF)}}})
 const uploaded=await upload.json();assert.equal(uploaded.code,0);const sourceDocId=uploaded.data.id
 await page.reload();await page.getByRole('tab',{name:'模板维护',exact:true}).click()
 await page.locator('.editor .toolbar .el-select').first().click()
 await page.getByRole('option',{name:'候选合并真实验收',exact:true}).click()
 await page.getByRole('textbox',{name:'模板名称',exact:true}).fill('真实候选模板')
 const imported=page.locator('.editor-columns > section').first()
 await page.setViewportSize({width:390,height:844})
 assert.equal(await imported.isVisible(),true)
 assert.equal(await page.locator('.editor-columns > section').last().isVisible(),false)
 await page.locator('.mobile-tabs').getByText('模板事项',{exact:true}).click()
 assert.equal(await imported.isVisible(),false)
 assert.equal(await page.locator('.editor-columns > section').last().isVisible(),true)
 await page.setViewportSize({width:1500,height:1000})
 console.log('PASS: narrow-screen candidate/template tabs')

 await imported.locator('.el-card').first().waitFor({timeout:180000})
 const tableRow=imported.locator('.el-card').filter({hasText:'专家讲课费'}).filter({hasText:'PDF第2页'}).first()
 await tableRow.waitFor()
 assert.match(await tableRow.innerText(),/5000.*3000.*2000.*1000/s)
 const originalResponse=page.context().waitForEvent('response',{predicate:r=>new URL(r.url()).pathname.startsWith('/api/knowledge/docs/files/') && r.url().includes('preview=true')})
 const originalTab=page.context().waitForEvent('page')
 await tableRow.getByRole('button',{name:'查看原件',exact:true}).click()
 const original=await originalTab
 const originalFile=await originalResponse
 assert.equal(originalFile.status(),200)
 assert.match(originalFile.headers()['content-type'],/application\/pdf/)
 await original.waitForURL(url=>url.hash==='#page=2')
 await original.close()
 await tableRow.getByRole('button',{name:'编辑后新增',exact:true}).click()
 await tableRow.locator('form input').fill('专家讲课费（验收修订）')
 await tableRow.locator('form').getByRole('button',{name:'加入模板',exact:true}).click()
 if(await page.getByRole('button',{name:'已人工核对并补齐漏提取内容',exact:true}).count()){
  await page.getByRole('button',{name:'已人工核对并补齐漏提取内容',exact:true}).click()
  await page.getByRole('dialog',{name:'人工核对',exact:true}).getByRole('button',{name:'确定',exact:true}).click()
  await page.getByText('已记录人工核对',{exact:true}).waitFor()
 }
 await page.getByRole('button',{name:'保存并生效',exact:true}).click()
 const review=page.getByRole('dialog',{name:'核对并生效'})
 await review.getByRole('button',{name:'确认已核对全部事项',exact:true}).click()
 await review.getByRole('button',{name:'保存并生效',exact:true}).click()
 await page.getByText('已生效，仅用于之后新建的清单',{exact:true}).waitFor()


 const endpoint=`http://localhost:5173/api/compliance/v4/documents/${sourceDocId}/template-workspace`
 const state=(await (await page.request.get(endpoint)).json()).data
 const editedNode=state.content.nodes.find(node=>node.title==='专家讲课费（验收修订）')
 assert.ok(editedNode,'Edited OCR candidate must be persisted in the active workspace')
 const storedCitation=JSON.parse(editedNode.sourceAnchor)
 assert.match(storedCitation.anchor,/PDF第2页表格第\d+项/)
 assert.match(editedNode.instruction,/5000.*3000.*2000.*1000/s)
 console.log('PASS: OCR table title/body, PDF original page and persisted citation')

 await page.getByRole('tab',{name:'规范文档库',exact:true}).click()
 const sourceRow=page.getByRole('row').filter({hasText:'候选合并真实验收'})
 const fixtureRow=page.getByRole('row').filter({hasText:'隔离规范'})
 for (const row of [sourceRow,fixtureRow]) {
  await row.getByRole('button',{name:'收藏',exact:true}).click()
  await row.getByRole('button',{name:'取消收藏',exact:true}).waitFor()
 }
 const onlyFavorites=page.getByRole('checkbox',{name:'仅看收藏'})
 await page.getByText('仅看收藏',{exact:true}).click()
 assert.equal(await onlyFavorites.isChecked(),true)
 const list=async params=>{
  const response=await page.request.get('http://localhost:5173/api/knowledge/docs',{params:{category:'enterprise-compliance',favoritesOnly:true,pageSize:1,...params}})
  const payload=await response.json()
  assert.equal(payload.code,0)
  return payload.data
 }
 const favoritesFirst=await list({page:1})
 const favoritesSecond=await list({page:2})
 assert.equal(favoritesFirst.total,2);assert.equal(favoritesSecond.total,2)
 assert.equal(favoritesFirst.items.length,1);assert.equal(favoritesSecond.items.length,1)
 assert.notEqual(favoritesFirst.items[0].id,favoritesSecond.items[0].id)
 const search=page.getByPlaceholder('按标题 / 内容关键字检索')
 await page.locator('.format-filters').getByText('PDF',{exact:true}).click()
 await search.fill('候选合并真实验收');await search.press('Enter')
 await fixtureRow.waitFor({state:'hidden'})
 await sourceRow.getByRole('button',{name:'取消收藏',exact:true}).click()
 await sourceRow.waitFor({state:'hidden'})
 assert.equal((await list({page:1,fileType:'pdf',keyword:'候选合并真实验收'})).total,0)
 console.log('PASS: favorites across two database pages and Edge filter/unfavorite')

 const forged=JSON.parse(JSON.stringify(state.content));const forgedNode=forged.nodes.find(node=>node.nodeCode===editedNode.nodeCode);const forgedSource=JSON.parse(forgedNode.sourceAnchor);forgedSource.excerpt='forged excerpt';forgedNode.sourceAnchor=JSON.stringify(forgedSource)
 const denied=await page.request.put(endpoint,{data:{content:forged,expectedRowVersion:state.rowVersion}});assert.notEqual((await denied.json()).code,0)
 const duplicated=JSON.parse(JSON.stringify(state.content));duplicated.nodes.push({...duplicated.nodes[0]})
 const invalid=await page.request.put(endpoint,{data:{content:duplicated,expectedRowVersion:state.rowVersion}});assert.notEqual((await invalid.json()).code,0)
 const noChange=await page.request.post(endpoint+'/activate',{data:{expectedRowVersion:state.rowVersion,requestId:crypto.randomUUID()}})
 const unchanged=await noChange.json();assert.equal(unchanged.code,0);assert.equal(unchanged.data.activeTemplateVersionId,state.activeTemplateVersionId)


 const employeeContext=await browser.newContext()
 const employee=await employeeContext.newPage()
 await employee.goto('http://localhost:5173/login?redirect=/knowledge-planet/enterprise-compliance')
 await employee.locator('input[autocomplete="username"]').fill('验收员工甲')
 await employee.getByPlaceholder('密码',{exact:true}).fill(process.env.V4_TEST_PASSWORD)
 await employee.getByRole('button',{name:/登\s*录/}).click()
 await employee.getByRole('button',{name:'启用新版执行清单',exact:true}).click()
 await employee.getByRole('dialog',{name:'启用新版执行清单',exact:true}).getByRole('button',{name:'确定',exact:true}).click()
 await employee.getByRole('button',{name:'创建执行清单',exact:true}).waitFor()
 assert.equal(await employee.getByRole('tab',{name:'旧版只读历史',exact:true}).count(),0)
 console.log('PASS: upgraded employee has no legacy history tab')
 await employee.getByRole('button',{name:'创建执行清单',exact:true}).click()
 const create=employee.getByRole('dialog',{name:'创建执行清单',exact:true})
 await create.locator('.el-select').click();await employee.getByRole('option',{name:'隔离项目',exact:true}).click()
 await create.getByRole('button',{name:'下一步',exact:true}).click()
 await create.locator('.el-select').first().click();await employee.getByRole('option',{name:'候选合并真实验收',exact:true}).click()
 await create.getByRole('button',{name:'下一步',exact:true}).click()
 await create.getByPlaceholder('如：君实·6月项目·协会与材料').fill('可视化员工真实清单')
 await create.getByRole('button',{name:'创建清单',exact:true}).click();await create.waitFor({state:'hidden'})
 const executionList=await (await employeeContext.request.get('http://localhost:5173/api/compliance/v4/executions')).json()
 const execution=executionList.data.items.find(e=>e.name==='可视化员工真实清单');assert.ok(execution)
 const executionUrl=`http://localhost:5173/api/compliance/v4/executions/${execution.id}`
 const initial=(await (await employeeContext.request.get(executionUrl)).json()).data
 assert.ok(initial.tasks.length)
 const task=initial.tasks[0]
 const filled=await employeeContext.request.patch(`http://localhost:5173/api/compliance/v4/tasks/${task.id}`,{
  data:{rowVersion:task.rowVersion,note:'隔离验收填写记录'}
 })
 assert.equal((await filled.json()).code,0)
 const oldDetail=(await (await employeeContext.request.get(executionUrl)).json()).data
 assert.equal(oldDetail.tasks[0].note,'隔离验收填写记录')
 const firstEndpoint=endpoint
 const first=(await (await page.request.get(firstEndpoint)).json()).data
 first.content.nodes[0].title='更新后的模板事项'
 for(let index=1;index<=20;index++){
  first.content.nodes.push({nodeCode:crypto.randomUUID(),title:`长列表验收事项 ${index}`,stage:'未分组',inputType:'CHECK',required:0})
 }
 const applied=await page.request.post(firstEndpoint+'/save-and-activate',{data:{
  content:first.content,expectedRowVersion:first.rowVersion,requestId:crypto.randomUUID(),
  reviewedNodeCodes:first.content.nodes.map(node=>node.nodeCode)
 }})
 assert.equal((await applied.json()).code,0)
 const preserved=(await (await employeeContext.request.get(`http://localhost:5173/api/compliance/v4/executions/${execution.id}`)).json()).data
 assert.deepEqual(preserved.tasks,oldDetail.tasks,'Template update must preserve existing employee tasks')
 await page.reload()
 await page.getByRole('tab',{name:'模板维护',exact:true}).click()
 await page.locator('.editor .toolbar .el-select').first().click()
 await page.getByRole('option',{name:'候选合并真实验收',exact:true}).click()
 const editor=page.locator('.editor')
 const board=page.locator('.maintenance-board')
 await editor.locator('main article strong').filter({hasText:'长列表验收事项 20'}).waitFor()
 const lastArticle=await editor.locator('main article').last().boundingBox()
 const boardBox=await board.boundingBox()
 assert.ok(lastArticle && boardBox && lastArticle.y+lastArticle.height<=boardBox.y,'Long template must stay above the situations board')
 await editor.getByRole('button',{name:'清空模板事项',exact:true}).click()
 await page.getByRole('dialog',{name:'清空模板事项'}).getByRole('button',{name:'清空事项',exact:true}).click()
 const beforeClear=(await (await page.request.get(endpoint)).json()).data
 assert.ok(beforeClear.content.nodes.length,'Clear must wait for explicit save')
 const candidateUrl=endpoint+'/extractions/latest'
 const beforeCandidates=(await (await page.request.get(candidateUrl)).json()).data
 await editor.getByRole('button',{name:'保存并生效',exact:true}).click()
 await page.getByText('模板事项已清空',{exact:true}).waitFor()
 const cleared=(await (await page.request.get(endpoint)).json()).data
 assert.deepEqual(cleared.content.nodes,[])
 assert.equal(cleared.activeTemplateVersionId,null)
 assert.deepEqual((await (await page.request.get(candidateUrl)).json()).data,beforeCandidates)
 const staleCreate=await employeeContext.request.post('http://localhost:5173/api/compliance/v4/executions',{data:{
  templateVersionId:beforeClear.activeTemplateVersionId,projectId:1,
  name:'清空后旧模板不得创建',idempotencyKey:crypto.randomUUID()
 }})
 assert.equal((await staleCreate.json()).code,409)
 const afterClear=(await (await employeeContext.request.get(executionUrl)).json()).data
 assert.deepEqual(afterClear.tasks,oldDetail.tasks)
 console.log('PASS: long template layout, explicit clear save and preserved execution records')
 await employee.reload();await employee.getByRole('button',{name:'创建执行清单',exact:true}).waitFor()
 await employeeContext.close()

 await page.getByRole('tab',{name:'模板维护',exact:true}).click()
 assert.deepEqual(errors,[])
 const evidence='../builds/.build-compliance-v4-acceptance/evidence'
 await mkdir(evidence,{recursive:true})
 await page.screenshot({path:evidence+'/visual-editor-live.png',fullPage:true})
 console.log('PASS: real PDF upload, atomic activation, employee creation and preserved execution records')
} finally {await browser.close()}
