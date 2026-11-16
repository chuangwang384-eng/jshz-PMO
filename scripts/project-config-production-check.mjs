import assert from 'node:assert/strict'
import { readFile, writeFile } from 'node:fs/promises'
import { createHash } from 'node:crypto'
const { request }=await import(process.env.PLAYWRIGHT_MODULE||'playwright')
const [base,directory,stage,oldRoot]=process.argv.slice(2)
if(!base||!directory)throw new Error('Base URL and private evidence directory required')
const client=await request.newContext({baseURL:base})
const report={base,result:'PENDING',checks:[]}
async function ok(path){const r=await client.get(path);assert.equal(r.status(),200,path);const data=await r.json();assert.equal(data.code,0,path);return data.data}
try {
  const login=await client.post('/api/auth/login',{data:{username:process.env.PC_SMOKE_USER,password:process.env.PC_SMOKE_PASSWORD}})
  assert.equal(login.status(),200);assert.equal((await login.json()).code,0);report.checks.push('existing authorized test account login')
  const user=await ok('/api/auth/me');report.checks.push('authenticated session')
  const projects=await ok('/api/project?page=1&size=20');const records=projects.records||[];report.visibleProjects=records.length
  for(const item of records){const detail=await ok('/api/project/'+item.id);if(process.env.PC_EXPECT_V3==='true'){
    const snapshot=await ok('/api/project/'+item.id+'/configuration');assert.deepEqual(detail.phases.map(p=>Number(p.id)),snapshot.phases.map(p=>Number(p.phaseId)))
  }}
  report.checks.push('visible existing projects and fixed phase sequence')
  if(process.env.PC_EXPECT_V3==='true'){
    const config=await client.get('/api/project/config/definition-context');const data=await config.json()
    if(user.permissions?.includes('project:manage')||user.isAdmin){assert.equal(data.code,0);report.checks.push('configuration manager read')}
    else {assert.equal(data.code,403);report.checks.push('ordinary account denied configuration manager read')}
  }
  if(stage){
    const response=await client.get('/');assert.equal(response.status(),200);const actual=await response.body(),expected=await readFile(stage+'/index.html');assert.equal(createHash('sha256').update(actual).digest('hex'),createHash('sha256').update(expected).digest('hex'))
    let assets=0
    for(const root of [stage,oldRoot].filter(Boolean)){
      const index=await readFile(root+'/index.html','utf8')
      for(const match of index.matchAll(/(?:src|href)="(\/assets\/[^"?#]+)"/g)){
        const r=await client.get(match[1]);assert.equal(r.status(),200);const bytes=await r.body(),file=await readFile(root+match[1]);assert.equal(createHash('sha256').update(bytes).digest('hex'),createHash('sha256').update(file).digest('hex'));assets++
      }
    }
    report.assetsVerified=assets;report.checks.push('exact new index and old/new hashed assets')
    await client.get('/admin/project/config');report.checks.push('configuration SPA route')
  }
  report.result='PASS';await writeFile(directory+'/authenticated-read-only.json',JSON.stringify(report,null,2));console.log(JSON.stringify(report))
}finally{await client.dispose()}
