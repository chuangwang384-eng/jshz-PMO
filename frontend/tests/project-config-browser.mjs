const { chromium } = await import(process.env.PLAYWRIGHT_MODULE || 'playwright')
import assert from 'node:assert/strict'
const browser=await chromium.launch({channel:'msedge',headless:true})
const page=await browser.newPage({viewport:{width:1440,height:1100}})
const errors=[]; page.on('pageerror',e=>errors.push(e.message))
const user={id:2,userId:2,username:'acceptance',roles:['ADMIN'],permissions:['project:manage','project:view-all'],mustChangePassword:false}
await page.addInitScript(u=>localStorage.setItem('expert_user',JSON.stringify(u)),user)
const types=[{id:1,name:'隔离验收类型',sort:1}]
const phases=[{id:1,name:'立项',sort:1,isActive:1},{id:2,name:'执行',sort:2,isActive:1}]
const templates=[{id:1,name:'文本验收',templateType:'CUSTOM_TEXT'}]
const writes=[]
await page.route('**/api/**',async route=>{
 const request=route.request(),path=new URL(request.url()).pathname; let data=[]; if(!path.startsWith('/api/')) { await route.continue(); return }
 if(request.method()!=='GET') {writes.push({path,body:request.postDataJSON()});data={id:2,...request.postDataJSON()}}
 else if(path==='/api/auth/me')data=user
 else if(path.endsWith('/config/types'))data=types
 else if(path.endsWith('/config/phases'))data=phases
 else if(path.endsWith('/config/templates'))data=templates
 else if(/types\/\d+\/phases$/.test(path))data=phases.map(p=>({phaseId:p.id,phaseName:p.name}))
 else if(path.endsWith('/templates'))data=[{templateId:1,required:1,sort:1,fallback:false}]
 else if(path.includes('announcement'))data={total:0,records:[],items:[]}
 else if(path.includes('unread'))data=0
 await route.fulfill({json:{code:0,data,message:'success'}})
})
try {
 await page.goto('http://127.0.0.1:15173/admin/project/config')
 await page.getByText('项目类型流程总览',{exact:true}).waitFor()
 await page.getByRole('button',{name:'编辑',exact:true}).last().click()
 const dialog=page.getByRole('dialog');await dialog.waitFor()
 await dialog.getByRole('button',{name:'下一步',exact:true}).click()
 await dialog.getByRole('button',{name:'下一步',exact:true}).click()
 await dialog.getByText('文本验收',{exact:true}).waitFor()
 await dialog.getByRole('button',{name:'保存',exact:true}).click()
 await dialog.waitFor({state:'hidden'})
 assert.ok(writes.some(x=>x.path.endsWith('/phases/1/templates')&&x.body[0]?.templateId===1&&x.body[0]?.required===1))
 await page.getByText('表格视图',{exact:true}).click()
 await page.getByRole('tab',{name:'项目类型'}).waitFor()
 await page.getByText('图形视图',{exact:true}).click()
 await page.getByText('项目类型流程总览',{exact:true}).waitFor()
 assert.deepEqual(errors,[])
 console.log('PASS: Edge browser loads graph, edits and preserves async bindings, saves, switches table/graph without page errors. API fixtures isolated; not live backend E2E.')
 await page.screenshot({path:process.env.TEMP+'/jshz-acceptance-0e8f3a4/project-config.png',fullPage:true})
} catch (error) { console.log('URL',page.url(),'ERRORS',errors,'BODY',(await page.locator('body').innerText()).slice(0,2500));throw error } finally {await browser.close()}
