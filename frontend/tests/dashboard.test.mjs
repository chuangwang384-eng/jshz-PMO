import assert from 'node:assert/strict'
import test from 'node:test'
import { safeLocalPath, safeTarget, latestRequest, sectionRequests } from '../src/utils/dashboard.js'
test('login redirect preserves local detail and rejects external or control characters',()=>{
 for(const path of ['//outside.example','https://outside.example','javascript:alert(1)','/\\outside','/x\ny','/login']) assert.equal(safeLocalPath(path),'/dashboard')
 assert.equal(safeLocalPath('/project/12?from=dashboard'),'/project/12?from=dashboard')
 assert.equal(safeLocalPath('/experts?expertId=2'),'/experts?expertId=2')
})
test('targets accept only registered module paths and approved scalar parameters',()=>{
 const router={resolve:()=>({matched:[{}]})}
 assert.equal(safeTarget(router,{path:'/admin/roles/manage'}),null)
 assert.equal(safeTarget(router,{path:'//outside.example'}),null)
 assert.deepEqual(safeTarget(router,{path:'/experts',query:{expertId:'3',secret:'never',userId:5}}),{path:'/experts',query:{expertId:'3'}})
 assert.equal(safeTarget({resolve:()=>({matched:[]})},{path:'/project/7'}),null)
})
test('dashboard targets follow the live menu and drop retired query flags',()=>{
 const router={resolve:()=>({matched:[{}]})}
 for(const path of ['/profile/overview','/knowledge-planet/association-compliance','/knowledge-planet/enterprise-compliance'])
   assert.deepEqual(safeTarget(router,{path}),{path,query:{}})
 // 项目列表已不再读取 mine；工作台不得再产生该深链参数
 assert.deepEqual(safeTarget(router,{path:'/project',query:{mine:'1',keyword:'abc'}}),{path:'/project',query:{keyword:'abc'}})
})
test('standards library deep link accepts only the docs tab on enterprise compliance', () => {
  const router = { resolve: () => ({ matched: [{}] }) }
  const path = '/knowledge-planet/enterprise-compliance'
  assert.deepEqual(safeTarget(router, { path, query: { tab: 'docs' } }), { path, query: { tab: 'docs' } })
  assert.deepEqual(safeTarget(router, { path, query: { tab: 'maintenance' } }), { path, query: {} })
  assert.deepEqual(safeTarget(router, { path: '/project', query: { tab: 'docs' } }), { path: '/project', query: {} })
})
test('stale responses are rejected after another request or clear',()=>{
 const sequence=latestRequest(),first=sequence.next(),second=sequence.next()
 assert.equal(sequence.current(first),false);assert.equal(sequence.current(second),true)
 sequence.next();assert.equal(sequence.current(second),false)
})

test('independent card retries do not cancel each other while overlaps reject stale data',()=>{
 const tracker=sectionRequests()
 const all=tracker.begin(['favorites','notifications']),favorite=tracker.begin(['favorites']),notification=tracker.begin(['notifications'])
 assert.equal(tracker.current('favorites',all),false)
 assert.equal(tracker.current('notifications',all),false)
 assert.equal(tracker.current('favorites',favorite),true)
 assert.equal(tracker.current('notifications',notification),true)
 tracker.begin(['favorites','notifications'])
 assert.equal(tracker.current('favorites',favorite),false)
 assert.equal(tracker.current('notifications',notification),false)
})
