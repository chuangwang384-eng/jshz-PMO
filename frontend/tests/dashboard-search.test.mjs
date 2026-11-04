import assert from 'node:assert/strict'
import test from 'node:test'
import { readFileSync } from 'node:fs'
import { parse, compileScript } from '@vue/compiler-sfc'
import * as Vue from 'vue'
import { latestRequest, moduleNames, safeTarget } from '../src/utils/dashboard.js'

// Execute the real SFC setup with mocked transport, keeping Vue reactivity and production handlers.
// This tests IME and out-of-order responses without replacing the component's implementation.
const sfc=parse(readFileSync(new URL('../src/components/GlobalSearchBar.vue',import.meta.url),'utf8')).descriptor
const compiled=compileScript(sfc,{id:'dashboard-search-test'}).content.replace(/^import .*$/gm,'').replace('export default','return')
const factory=new Function('computed','onBeforeUnmount','onMounted','ref','useRouter','globalSearch','latestRequest','moduleNames','safeTarget','document',compiled)
function setup(transport){const cleanup=[],navigations=[];const router={resolve:()=>({matched:[{}]}),push:value=>navigations.push(value)};const component=factory(Vue.computed,cb=>cleanup.push(cb),()=>{},Vue.ref,()=>router,transport,latestRequest,moduleNames,safeTarget,{addEventListener(){},removeEventListener(){}});return {state:component.setup({}, {expose(){}}),cleanup:()=>cleanup.forEach(cb=>cb()),navigations}}
const deferred=()=>{let resolve;const promise=new Promise(r=>{resolve=r});return {promise,resolve}}
const key=name=>({key:name,preventDefault(){},isComposing:false})
test('IME defers searches until composition end and ignores composing Enter',async()=>{
 const calls=[];const fixture=setup(async keyword=>{calls.push(keyword);return {groups:[]}})
 try{const s=fixture.state;s.composing=true;s.keyword.value='中文';s.schedule();s.keyDown({...key('Enter'),isComposing:true});await new Promise(r=>setTimeout(r,330));assert.deepEqual(calls,[]);s.compositionEnd();await new Promise(r=>setTimeout(r,330));assert.deepEqual(calls,['中文'])}finally{fixture.cleanup()}
})
test('older success cannot replace current results after request order reverses',async()=>{
 const first=deferred(),second=deferred();let calls=0;const fixture=setup(()=>++calls===1?first.promise:second.promise)
 try{const s=fixture.state;s.keyword.value='A';const a=s.search();s.keyword.value='AB';const ab=s.search();second.resolve({groups:[{module:'expert',items:[{title:'AB'}]}]});await ab;first.resolve({groups:[{module:'expert',items:[{title:'A'}]}]});await a;assert.equal(s.groups.value[0].items[0].title,'AB')}finally{fixture.cleanup()}
})
test('clear invalidates a pending response even if transport ignores AbortSignal',async()=>{
 const pending=deferred(),fixture=setup(()=>pending.promise)
 try{const s=fixture.state;s.keyword.value='旧词';const result=s.search();s.clear();pending.resolve({groups:[{module:'expert',items:[{title:'旧结果'}]}]});await result;assert.equal(s.open.value,false);assert.deepEqual(s.groups.value,[]);assert.equal(s.keyword.value,'')}finally{fixture.cleanup()}
})
test('keyboard arrows and Enter navigate the selected authorized result',()=>{
 const fixture=setup(async()=>({groups:[]}))
 try{const s=fixture.state;s.groups.value=[{module:'expert',items:[{title:'一',target:{path:'/experts',query:{expertId:'1'}}},{title:'二',target:{path:'/experts',query:{expertId:'2'}}}]}];s.keyDown(key('ArrowUp'));assert.equal(s.active.value,1);s.keyDown(key('Enter'));assert.deepEqual(fixture.navigations,[{path:'/experts',query:{expertId:'2'}}]);assert.equal(s.open.value,false)}finally{fixture.cleanup()}
})
