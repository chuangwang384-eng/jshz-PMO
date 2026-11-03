import { readFileSync } from 'node:fs'
import assert from 'node:assert/strict'
import { parse, compileScript } from '@vue/compiler-sfc'
import * as vue from 'vue'

function setup(file, input) {
  const { descriptor } = parse(readFileSync(new URL('../src/views/project/components/' + file, import.meta.url), 'utf8'))
  let source = compileScript(descriptor, { id: file, genDefaultAs: '__component' }).content
  source = source.replace(/^import\s+(.+?)\s+from\s+['"](.+?)['"]\s*;?$/gm, (_, names, module) => {
    if (module === 'vue') return `const ${names} = vue`
    if (module === '@vue-flow/core') return 'const { VueFlow, Panel, useVueFlow } = stubs'
    if (module === '@vue-flow/background') return 'const Background = {}'
    if (names.startsWith('{')) return `const ${names} = stubs`
    return `const ${names} = {}`
  }).replace(/^import\s+['"].+?['"]\s*;?$/gm, '')
  const events = []; let exposed
  const component = new Function('vue', 'stubs', source + '\nreturn __component')(vue, {
    useVueFlow: () => ({ zoomIn() {}, zoomOut() {}, fitView() {} }), Connection: {}
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
assert.deepEqual(storeOptions.actions.globalFallbackTypeCount.call(context,7), [1])
context.phaseTemplateRels['1_7'] = [{templateId:2,fallback:true}]
context.phaseTemplateRels['0_7'] = [{templateId:2}]
assert.equal(storeOptions.actions.bindsFor.call(context,1,7).fallback,true)
console.log('PASS: empty type flow uses active-phase fallback and inherited bindings remain global')
