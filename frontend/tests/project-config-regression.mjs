import { readFileSync } from 'node:fs'
import assert from 'node:assert/strict'
import { parse, compileScript } from '@vue/compiler-sfc'
import * as vue from 'vue'
import { createProjectConfigRequestId } from '../src/utils/projectConfigRequestId.js'

function setup(file, input, overrides = {}) {
  const { descriptor } = parse(readFileSync(new URL('../src/views/project/components/' + file, import.meta.url), 'utf8'))
  let source = compileScript(descriptor, { id: file, genDefaultAs: '__component' }).content
  source = source.replace(/^import\s+(\{[^}]*\}|[^\r\n]+?)\s+from\s+['"](.+?)['"]\s*;?$/gm, (_, names, module) => {
    if (module === 'vue') return `const ${names} = vue`
    if (module === '@vue-flow/core') return 'const { VueFlow, Panel, useVueFlow } = stubs'
    if (module === '@vue-flow/background') return 'const Background = {}'
    if (names.startsWith('{')) return `const ${names} = stubs`
    return `const ${names} = {}`
  }).replace(/^import\s+['"].+?['"]\s*;?$/gm, '')
  const events = []; let exposed
  const component = new Function('vue', 'stubs', source + '\nreturn __component')(vue, {
    useVueFlow: () => ({ zoomIn() {}, zoomOut() {}, fitView() {} }), Connection: {}, ...overrides
  })
  const props = vue.reactive(input)
  const scope = vue.effectScope()
  const state = scope.run(() => component.setup(props, { expose: x => exposed = x, emit: (...args) => events.push(args) }))
  return { state, props, events, exposed, stop: () => scope.stop() }
}

const flow = setup('FlowBuilderStep.vue', { modelValue: [{phaseId:1},{phaseId:2},{phaseId:3}], phases:[] })
flow.state.onDragStop({ node:{id:'fb-3',position:{x:-10,y:0}}, nodes:[{id:'fb-3',position:{x:-10,y:0}}] })
assert.deepEqual(flow.events.at(-1)[1].map(x=>x.phaseId), [3,1,2])
flow.stop()

const bind = setup('TemplateBindStep.vue', { flow:[{phaseId:1},{phaseId:2}], templates:[{id:10},{id:20}], initialStates:{}, typeName:'fixture' })
bind.props.initialStates = {1:{fallback:false,items:[{templateId:20,required:1,sort:1},{templateId:10,required:0,sort:2}]}}
await vue.nextTick()
let state = bind.exposed.getStates()
assert.deepEqual(state[1].items.map(x=>x.templateId),[20,10])
assert.equal(state[1].items[0]._required,1)
state[1].items[0]._required = 0
bind.props.flow = [{phaseId:2},{phaseId:1},{phaseId:3}]
await vue.nextTick()
assert.equal(bind.exposed.getStates()[1].items[0]._required,0)
bind.state.onDragStart({dataTransfer:{}},0)
bind.state.onDragOver(1)
bind.state.onDrop()
assert.equal(bind.state.dragIndex.value,null)
bind.stop()
console.log('PASS: phase drag keeps every node; async bindings load in saved order; flow edits preserve bindings; template drag ends cleanly')
const storeSource = readFileSync(new URL('../src/stores/projectConfig.js', import.meta.url), 'utf8')
  .replace(/^import[\s\S]*?from\s+['"][^'"]+['"]\s*$/gm, '')
  .replace('export const useProjectConfigStore', 'const useProjectConfigStore')
const storeOptions = new Function('defineStore', storeSource + '\nreturn useProjectConfigStore')((name, options) => options)
const context = { types:[{id:1}], typePhaseRels:{1:[]}, activePhases:[{id:7}], phaseTemplateRels:{} }
assert.deepEqual(storeOptions.actions.globalFallbackTypeCount.call(context,7), [])
context.typePhaseRels[1]=[7]
assert.deepEqual(storeOptions.actions.globalFallbackTypeCount.call(context,7), [1])
context.phaseTemplateRels['1_7'] = [{templateId:2,fallback:true}]
context.phaseTemplateRels['0_7'] = [{templateId:2}]
assert.equal(storeOptions.actions.bindsFor.call(context,1,7).fallback,true)
context.phaseModes={'1_7':'NONE'}
assert.deepEqual(storeOptions.actions.bindsFor.call(context,1,7).list,[])
assert.equal(storeOptions.actions.bindsFor.call(context,1,7).fallback,false)
assert.deepEqual(storeOptions.actions.globalFallbackTypeCount.call(context,7),[])
context.phaseModes['1_7']='CUSTOM'
context.phaseTemplateRels['1_7']=[{templateId:9,required:0,fallback:false}]
assert.equal(storeOptions.actions.bindsFor.call(context,1,7).list[0].templateId,9)
console.log('PASS: empty flow never follows newly enabled stages; explicit inheritance, NONE and all-optional CUSTOM remain distinct')


// The aggregate wizard must preserve draft-only stages and reject stale async previews.
const wizardStore={types:[],phases:[],templates:[],sortedTypes:[],async loadTypes(){},async loadPhases(){},async loadTemplates(){}}
let releaseContext
const contextReady=new Promise(resolve=>{releaseContext=resolve})
let releasePreview, delayedPreview=true, attempts=[]
const wizard=setup('StepWizard.vue',{visible:true,type:null},{
  createProjectConfigRequestId,
  useProjectConfigStore:()=>wizardStore,
  getDefinitionContext:()=>contextReady,
  previewDefinition:async body=>delayedPreview?new Promise(resolve=>{releasePreview=resolve}):({...body}),
  saveTypeDefinition:async(id,body)=>{attempts.push(JSON.parse(JSON.stringify(body)));if(attempts.length===1)throw new Error('Simulated response loss');return {id:99}},
  ElMessage:{warning(){},error(){},success(){}},ElMessageBox:{async confirm(){}}
})
const opening=wizard.state.open()
await vue.nextTick()
assert.equal(wizard.state.loading.value,true)
releaseContext({common:{},expectedEpoch:5})
await opening
assert.equal(wizard.state.loading.value,false)
wizard.state.draft.name='Async draft'
wizard.state.newName.value='New shared stage'
wizard.state.addNew()
assert.equal(wizard.state.draft.phases.length,1)
assert.equal(wizardStore.phases.length,0,'Adding draft stages cannot write the shared dictionary')
wizard.state.draft.phases[0].mode='NONE'
wizard.state.draft.phases[0].confirmed=true
await vue.nextTick()
const stale=wizard.state.preview()
await vue.nextTick()
wizard.state.draft.name='Changed while preview was pending'
await vue.nextTick()
releasePreview({name:'obsolete',phases:[]})
await stale
assert.equal(wizard.state.previewResult.value,null,'A stale preview must never become saveable')
delayedPreview=false
await wizard.state.preview()
assert.equal(wizard.state.previewResult.value.name,wizard.state.draft.name)
wizard.state.accepted.value=true
await wizard.state.save()
assert.equal(attempts.length,1)
await wizard.state.save()
assert.equal(attempts.length,2)
assert.equal(attempts[0].requestId,attempts[1].requestId,'Retry after response loss must reuse its persistent idempotency key')
assert.ok(wizard.events.some(event=>event[0]==='saved'))
wizard.stop()
console.log('PASS: wizard waits for initialization, keeps new stages draft-only, rejects stale preview responses and reuses its save key after response loss')

assert.equal(createProjectConfigRequestId({getRandomValues(bytes){bytes.fill(1);return bytes}}),'01'.repeat(16))
assert.match(createProjectConfigRequestId(),/^[a-f0-9]{32}$/)
console.log('PASS: request IDs work without secure-context-only randomUUID on intranet HTTP')
