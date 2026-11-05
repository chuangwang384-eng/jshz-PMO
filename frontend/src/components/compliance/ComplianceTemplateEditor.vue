<template>
  <section v-loading="loading" class="editor">
    <div class="toolbar">
      <el-select v-model="documentId" placeholder="选择规范文档" @change="open" :disabled="saving || dirty || extracting || activating || merging || reviewing || selecting || loading" aria-label="模板来源文档"><el-option v-for="d in docs" :key="d.id" :label="d.title" :value="d.id" /></el-select>
      <span role="status">{{ status }}</span>
      <el-button v-if="failed" @click="save">重试保存</el-button>
      <el-button :disabled="!content || !resolved" :loading="extracting" @click="extract">从原文提取</el-button>
      <el-button v-if="extraction" @click="importVisible=true">继续处理候选（{{ pendingCandidates.length }}）</el-button>
      <el-button :disabled="!undoStack.length" @click="undo">撤销结构操作</el-button>
      <el-button :disabled="!content || !resolved" @click="preview=true">预览员工清单</el-button>
      <el-button type="primary" :disabled="!resolved || failed || !canActivate || (!dirty && !workspace?.hasChanges)" :loading="activating" @click="activate">保存并生效</el-button>
    </div>
    <el-alert v-if="conflict" title="内容已被其他维护人修改，本地输入仍保留。请复制所需内容后重新加载对比。" type="error" :closable="false" />
    <el-button v-if="conflict || failed" @click="reload">重新加载</el-button>
    <template v-if="workspace && !resolved">
      <el-alert title="请选择要保留的模板内容，其余仅归入历史，已有员工清单不变。" :closable="false" />
      <el-radio-group v-model="selectedId"><div v-for="c in candidates" :key="c.id" class="legacy"><el-radio :value="c.id">{{ c.title }} · {{ c.state==='PUBLISHED'?'已发布内容':'尚未生效内容' }} · {{ c.node_count }} 项 · {{ c.updated_at || '时间未知' }}</el-radio><el-button link @click="previewLegacy(c)">查看内容</el-button></div></el-radio-group>
      <el-button type="primary" :disabled="!selectedId || !canActivate" :loading="selecting" @click="selectExisting">保留所选内容</el-button>
    </template>
    <template v-else-if="content">
      <el-input :disabled="activating || merging || reviewing" v-model="content.title" placeholder="模板名称" maxlength="200" aria-label="模板名称" />
      <p class="hint">编辑自动保留；点击保存并生效后，仅用于之后新建的清单。</p>
      <div class="layout" :inert="activating || merging || reviewing">
        <aside><h3>阶段目录</h3><button v-for="g in stages" :key="g" @click="currentStage=g" :class="{active:currentStage===g}">{{ g }}（{{ content.nodes.filter(n=>stage(n)===g).length }}）</button><el-button @click="addStage">添加阶段</el-button></aside>
        <main>
          <header><h3>{{ currentStage }}</h3><div v-if="currentStage!=='未分组'"><el-button link @click="renameStage">改名</el-button><el-button link @click="shiftStage(-1)">阶段上移</el-button><el-button link @click="shiftStage(1)">阶段下移</el-button><el-button link type="danger" @click="deleteStage">删除阶段</el-button></div><el-button @click="addNode">添加事项</el-button></header>
          <article v-for="n in shown" :key="n.nodeCode" draggable="true" @dragstart="dragCode=n.nodeCode" @dragover.prevent @drop.prevent="drop(n)" :class="{chosen:chosen===n}" @click="chosen=n">
            <strong>⠿ {{ n.title || '未命名事项' }}</strong><p>{{ n.instruction || n.acceptance || '点击填写操作要求' }}</p>
            <small>{{ typeName(n.inputType) }}{{ citation(n) && (!workspace?.reviewedNodeCodes?.includes(n.nodeCode) || dirty)?' · 原文待核对':'' }}</small>
            <div @click.stop><el-button v-if="citation(n)" link @click="openSource(n)">查看原件</el-button><el-button v-if="citation(n) && !workspace?.reviewedNodeCodes?.includes(n.nodeCode)" link type="primary" @click="review(n)">确认核对</el-button><el-button link @click="move(n,-1)">上移</el-button><el-button link @click="move(n,1)">下移</el-button><el-button link @click="copy(n)">复制</el-button><el-button link type="danger" @click="remove(n)">删除</el-button></div>
          </article>
          <el-empty v-if="!shown.length" description="本阶段没有事项，点击添加事项" />
        </main>
        <div v-if="chosen" class="properties">
          <h3>事项内容</h3><el-form label-position="top">
            <el-form-item label="事项名称"><el-input ref="itemTitle" v-model="chosen.title" maxlength="200" /></el-form-item>
            <el-form-item label="操作要求"><el-input v-model="chosen.instruction" type="textarea" :rows="4" /></el-form-item>
            <el-form-item label="所在阶段"><el-select :model-value="stage(chosen)" @change="chosen.stage=$event"><el-option v-for="g in stages" :key="g" :label="g" :value="g" /></el-select></el-form-item>
            <el-collapse><el-collapse-item title="更多设置" name="settings">
              <el-form-item label="填写方式"><el-select v-model="chosen.inputType"><el-option v-for="t in types" :key="t.value" :label="t.label" :value="t.value" /></el-select></el-form-item>
              <el-checkbox v-model="chosen.required" :true-value="1" :false-value="0">完成前必填</el-checkbox>
              <el-form-item v-for="f in fields" :key="f.key" :label="f.label"><el-input v-model="chosen[f.key]" type="textarea" /></el-form-item>
              <template v-if="['SINGLE','MULTI'].includes(chosen.inputType)"><div v-for="(o,i) in chosen.options" :key="o.optionCode"><el-input v-model="o.label" placeholder="选项名称" /><el-input v-model="o.guidance" placeholder="该选项的具体要求"/><el-button link @click="chosen.options.splice(i,1)">移除</el-button></div><el-button @click="(chosen.options ||= []).push({optionCode:key(),label:'',guidance:''})">添加选项</el-button></template>
            </el-collapse-item></el-collapse>
            <el-card v-if="citation(chosen)" class="source-citation"><strong>{{ citation(chosen).anchor }}</strong><p>{{ citation(chosen).excerpt }}</p><el-button @click="openSource(chosen)">查看原件</el-button><el-button @click="review(chosen)">确认已核对当前事项</el-button><small>修改后须重新确认</small></el-card>
          </el-form>
        </div>
      </div>
    </template>
    <el-empty v-else-if="!loading" description="选择规范文档开始维护" />
    <el-dialog v-model="publishVisible" title="核对并生效" width="min(840px, 95vw)" :close-on-click-modal="false">
      <p>生效后，员工新建清单使用本次模板。</p>
      <el-button v-if="publishPending.length" @click="publishChecked=publishPending.map(n=>n.nodeCode)">确认已核对全部事项</el-button>
      <el-checkbox-group v-model="publishChecked" style="display:block;max-height:50vh;overflow:auto">
        <div v-for="n in publishPending" :key="n.nodeCode">
          <el-checkbox :value="n.nodeCode">已核对</el-checkbox>
          <strong>{{ n.title }}</strong>
          <p>{{ citation(n)?.anchor }} · {{ citation(n)?.excerpt }}</p>
          <el-button link @click="openSource(n)">查看原件</el-button>
        </div>
      </el-checkbox-group>
      <el-checkbox v-if="content?.extractionId && extractionWarning" v-model="publishConfirmed">已核对并补齐漏提取内容</el-checkbox>
      <template #footer><el-button @click="publishVisible=false">返回编辑</el-button><el-button type="primary" :loading="activating" :disabled="publishChecked.length!==publishPending.length || (!!content?.extractionId && !!extractionWarning && !publishConfirmed)" @click="publish">确认并生效</el-button></template>
    </el-dialog>
    <el-dialog v-model="legacyPreviewVisible" title="存量内容预览" width="70%"><template v-if="legacyPreview"><h3>{{ legacyPreview.title }}</h3><el-card v-for="n in legacyPreview.nodes" :key="n.nodeCode"><strong>{{ n.title }}</strong><p>{{ n.instruction || n.acceptance }}</p><small>{{ citation(n)?.anchor }}</small></el-card></template></el-dialog>
    <el-dialog v-model="importVisible" title="原文提取候选与差异合并" width="85%">
      <template v-if="extraction">
        <el-alert v-if="extraction.incomplete" :title="extraction.warning || '存在漏提取或截断，请人工核对补齐'" type="warning" :closable="false" />
        <p v-if="extraction.missingAnchors?.length">需人工核对：{{ extraction.missingAnchors.join('、') }}</p>
        <p>已有事项不会自动覆盖，相同来源重复加入会跳过。</p>
        <el-card v-for="c in pendingCandidates" :key="c.candidateId" style="margin:12px 0">
          <strong>{{ c.title }}</strong><p>{{ citation(c)?.anchor }} · {{ citation(c)?.excerpt }}</p>
          <el-tag v-if="looksGarbled(c)" type="warning">疑似识别异常</el-tag>
          <el-button link @click="openSource(c)">查看原件</el-button>
          <el-button :disabled="merging" @click="ignoreCandidate(c)">忽略</el-button>
          <el-button type="primary" :disabled="merging" @click="mergeCandidate(c)">确认新增</el-button>
          <el-button :disabled="merging" @click="editCandidate(c)">编辑后新增</el-button>
          <el-form v-if="candidateEdit?.id===c.candidateId" label-position="top" :disabled="merging" @submit.prevent>
            <el-form-item label="事项名称" required>
              <el-input v-model="candidateEdit.title" maxlength="200" />
            </el-form-item>
            <el-form-item label="操作要求">
              <el-input v-model="candidateEdit.instruction" type="textarea" :rows="4" maxlength="10000" />
            </el-form-item>
            <el-button @click="candidateEdit=null">取消编辑</el-button>
            <el-button type="primary" :loading="merging" :disabled="!candidateEdit.title.trim()" @click="saveEditedCandidate(c)">保存为新事项</el-button>
          </el-form>
          <el-select v-if="content.nodes.length" v-model="mergeChoices[c.candidateId]" placeholder="选择要替换的事项">
            <el-option v-for="n in content.nodes" :key="n.nodeCode" :label="n.title || '未命名事项'" :value="n.nodeCode"/>
          </el-select>
          <div v-if="mergeChoices[c.candidateId]">
            <p>原内容：{{ content.nodes.find(n=>n.nodeCode===mergeChoices[c.candidateId])?.instruction }}</p>
            <p>替换为：{{ c.acceptance }}</p>
            <el-button :disabled="merging" @click="mergeCandidate(c, mergeChoices[c.candidateId])">确认替换</el-button>
          </div>
        </el-card>
        <el-empty v-if="!pendingCandidates.length" description="本次候选已处理完毕"/>
        <el-button v-if="extraction.incomplete && !extraction.confirmed" :disabled="merging" @click="confirmMissing(extraction.extractionId)">已人工核对并补齐漏提取内容</el-button>
      </template>
    </el-dialog>
    <el-alert v-if="content?.extractionId && extractionWarning" :title="extractionWarning" type="warning" :closable="false"><el-button @click="confirmMissing(content.extractionId)">已人工核对并补齐漏提取内容</el-button></el-alert>
    <el-dialog v-model="preview" title="员工清单预览" width="70%"><template v-if="content"><h2>{{ content.title }}</h2><section v-for="g in stages" :key="g"><h3>{{ g }}</h3><div v-for="n in content.nodes.filter(n=>stage(n)===g)" :key="n.nodeCode"><p>☐ {{ n.title }}</p><p>{{ n.instruction }}</p></div></section></template></el-dialog>
  </section>
</template>
<script setup>
import {ref,computed,watch,nextTick,onMounted,onBeforeUnmount} from 'vue'
import {onBeforeRouteLeave} from 'vue-router'
import {ElMessage,ElMessageBox} from 'element-plus'
import request from '../../api/request'
import {listAllComplianceDocuments} from '../../api/complianceV4'
const docs=ref([]),documentId=ref(null),workspace=ref(null),content=ref(null),loading=ref(false),saving=ref(false),activating=ref(false),dirty=ref(false),failed=ref(false),conflict=ref(false),candidates=ref([]),selectedId=ref(null),chosen=ref(null),currentStage=ref('未分组'),preview=ref(false),canActivate=ref(false)
const legacyPreviewVisible=ref(false),legacyPreview=ref(null)
const publishRevision=ref(null)
const publishVisible=ref(false), publishChecked=ref([]), publishPending=ref([]), publishConfirmed=ref(false)
const reviewing=ref(false), selecting=ref(false), itemTitle=ref(null), account=ref(null)
const handled=ref([]), candidateEdit=ref(null)
const extracting=ref(false),merging=ref(false),importVisible=ref(false),extraction=ref(null),mergeChoices=ref({}),extractionWarning=ref(''),undoStack=ref([])
let timer,hydrating=false,dragCode='',inflight=null
function key(){
  const bytes=globalThis.crypto.getRandomValues(new Uint8Array(16))
  return Array.from(bytes, byte=>byte.toString(16).padStart(2,'0')).join('')
}
const path=()=>`/compliance/v4/documents/${documentId.value}/template-workspace`
const stage=n=>n.stage || '未分组',types=[{value:'CHECK',label:'确认事项'},{value:'TEXT',label:'文本填写'},{value:'SINGLE',label:'单选'},{value:'MULTI',label:'多选'}],fields=[{key:'materials',label:'所需材料'},{key:'acceptance',label:'完成标准'},{key:'timeRule',label:'规范时限'}]
const typeName=v=>types.find(t=>t.value===v)?.label || v
const stages=computed(()=>[...new Set(['未分组',...(content.value?.groups || []),...(content.value?.nodes || []).map(stage)])])
const shown=computed(()=>(content.value?.nodes || []).filter(n=>stage(n)===currentStage.value))
const resolved=computed(()=>workspace.value?.selectionResolved)
const status=computed(()=>conflict.value?'编辑冲突':failed.value?'保存失败，内容仍在当前页面':saving.value?'保存中':dirty.value?'等待保存':workspace.value?(workspace.value.activeTemplateVersionId?(workspace.value.hasChanges?'编辑已保存，尚未生效':'当前内容已生效'):'编辑已保存，尚未生效'):'')
function hydrate(w){hydrating=true;workspace.value=w;content.value=JSON.parse(JSON.stringify(w.content));content.value.nodes ||= [];content.value.groups ||= [];chosen.value=null;undoStack.value=[];dirty.value=false;failed.value=false;conflict.value=false;queueMicrotask(()=>{hydrating=false})}
const pendingCandidates=computed(()=> (extraction.value?.candidates || []).filter(c=>
  !handled.value.includes(c.candidateId) && !content.value?.nodes.some(n=>n.sourceKey===c.candidateId)))
const progressKey=()=>`compliance-candidates:${account.value}:${documentId.value}`
function keepProgress(){
  try{localStorage.setItem(progressKey(),JSON.stringify({id:extraction.value.extractionId,handled:handled.value}))}catch{}
}
async function open(){
  loading.value=true
  extraction.value=null
  handled.value=[]
  mergeChoices.value={}
  candidateEdit.value=null
  selectedId.value=null
  currentStage.value='未分组'
  try{
    const w=await request.get(path())
    hydrate(w)
    extractionWarning.value=''
    if(!w.selectionResolved){
      candidates.value=canActivate.value?await request.get(path()+'/legacy-candidates'):[]
      return
    }
    let progress
    try{progress=JSON.parse(localStorage.getItem(progressKey()) || 'null')}catch{}
    const id=progress?.id || content.value.extractionId
    if(id){
      try{
        extraction.value=await request.get(path()+'/extractions/'+id)
        handled.value=progress?.id===id?progress.handled || []:[]
        if(extraction.value.incomplete && !extraction.value.confirmed)
          extractionWarning.value=extraction.value.warning || '请人工核对漏提取内容'
      }catch{extractionWarning.value='来源已更新或不可用，请重新提取并核对'}
    }
  }catch{workspace.value=null;content.value=null}
  finally{loading.value=false}
}
watch(content,()=>{if(hydrating || !resolved.value)return;dirty.value=true;clearTimeout(timer);if(!conflict.value)timer=setTimeout(()=>save(),700)},{deep:true,flush:'sync'})
async function save(){clearTimeout(timer);if(inflight){await inflight;if(dirty.value && !failed.value)return save();return}if(!dirty.value || conflict.value)return;const snapshot=JSON.stringify(content.value),doc=documentId.value;saving.value=true;inflight=(async()=>{try{const w=await request.put(path(),{content:JSON.parse(snapshot),expectedRowVersion:workspace.value.rowVersion});if(documentId.value===doc){workspace.value=w;dirty.value=JSON.stringify(content.value)!==snapshot;failed.value=false}}catch(e){failed.value=true;conflict.value=e.code===409 || /409|已被修改|尚未选择/.test(e.message || '')}finally{saving.value=false}})();await inflight;inflight=null;if(dirty.value && !failed.value)return save()}
async function activate(){
  await save()
  if(dirty.value || failed.value)return
  if(!content.value.nodes.length){ElMessage.warning('请先添加至少一个事项');return}
  const invalid=content.value.nodes.find(n=>!n.title?.trim())
  if(invalid){currentStage.value=stage(invalid);chosen.value=invalid;ElMessage.warning('请填写事项名称');return}
  publishPending.value=content.value.nodes.filter(n=>citation(n) && !workspace.value.reviewedNodeCodes?.includes(n.nodeCode))
  publishRevision.value=workspace.value.rowVersion
  publishChecked.value=[]
  publishConfirmed.value=false
  publishVisible.value=true
}
async function publish(){
  if(activating.value)return
  activating.value=true
  try{
    const result=await request.post(path()+'/activate',{
      expectedRowVersion:publishRevision.value, requestId:key(),
      reviewedNodeCodes:publishChecked.value, confirmIncomplete:publishConfirmed.value
    })
    hydrate(result)
    if(publishConfirmed.value)extractionWarning.value=''
    publishVisible.value=false
    ElMessage.success('已生效，仅用于之后新建的清单')
  }catch{
    // The request interceptor displays the server validation error.
  }finally{activating.value=false}
}
async function selectExisting(){
  if(selecting.value)return
  selecting.value=true
  try{
    hydrate(await request.post(path()+'/select-existing',{selectedId:selectedId.value,expectedRowVersion:workspace.value.rowVersion,requestId:key()}))
    currentStage.value=stage(content.value.nodes[0] || {})
    ElMessage.success('已保留所选内容，可以继续编辑')
  }catch(error){
    ElMessage.error(error.response?.data?.message || error.message || '无法保留所选内容，请重试')
  }finally{selecting.value=false}
}
async function reload(){try{await ElMessageBox.confirm('重新加载会替换当前页面输入，请先复制需要保留的内容。','重新加载');await open()}catch{}}
async function addStage(){try{const {value}=await ElMessageBox.prompt('阶段名称（最多32字）','添加阶段',{inputValidator:v=>!!v?.trim() && v.trim().length<=32 || '请输入1～32字'});checkpoint();content.value.groups.push(value.trim());currentStage.value=value.trim()}catch{}}
async function addNode(){checkpoint();const n={nodeCode:key(),title:'',instruction:'',stage:currentStage.value,inputType:'CHECK',required:0,options:[]};content.value.nodes.push(n);chosen.value=content.value.nodes.at(-1);await nextTick();itemTitle.value?.focus()}
function move(n,delta){checkpoint();const list=content.value.nodes,i=list.indexOf(n),j=i+delta;if(j>=0 && j<list.length){list.splice(i,1);list.splice(j,0,n)}}
function drop(n){checkpoint();const list=content.value.nodes,i=list.findIndex(v=>v.nodeCode===dragCode),j=list.indexOf(n);if(i<0 || i===j)return;const [m]=list.splice(i,1);m.stage=currentStage.value;list.splice(j,0,m)}
function copy(n){checkpoint();const c=JSON.parse(JSON.stringify(n));c.nodeCode=key();delete c.id;delete c.parentId;content.value.nodes.push(c);chosen.value=c}
async function remove(n){try{await ElMessageBox.confirm('删除此事项？已有员工清单不受影响。','删除事项');checkpoint();content.value.nodes.splice(content.value.nodes.indexOf(n),1);if(chosen.value===n)chosen.value=null}catch{}}
function citation(n){try{return JSON.parse(n.sourceAnchor || 'null')}catch{return null}}

async function previewLegacy(c){try{legacyPreview.value=await request.get('/compliance/v4/templates/'+c.id);legacyPreviewVisible.value=true}catch{}}
function checkpoint(){undoStack.value.push(JSON.stringify(content.value));if(undoStack.value.length>20)undoStack.value.shift()}
function undo(){if(undoStack.value.length){content.value=JSON.parse(undoStack.value.pop());chosen.value=null}}
async function renameStage(){try{const old=currentStage.value;const {value}=await ElMessageBox.prompt('新名称（最多32字）','阶段改名',{inputValue:old,inputValidator:v=>!!v?.trim() && v.trim().length<=32 && (v.trim()===old || !stages.value.includes(v.trim())) || '名称无效或重复'});checkpoint();content.value.groups=stages.value.filter(g=>g!=='未分组').map(g=>g===old?value.trim():g);for(const n of content.value.nodes)if(stage(n)===old)n.stage=value.trim();currentStage.value=value.trim()}catch{}}
function shiftStage(delta){const list=stages.value.filter(g=>g!=='未分组'),i=list.indexOf(currentStage.value),j=i+delta;if(j>=0 && j<list.length){checkpoint();[list[i],list[j]]=[list[j],list[i]];content.value.groups=list;const rank=g=>g==='未分组'?-1:list.indexOf(g);content.value.nodes.sort((a,b)=>rank(stage(a))-rank(stage(b)))}}
async function deleteStage(){try{const old=currentStage.value;await ElMessageBox.confirm('删除阶段后，其事项移动到“未分组”，内容保留。','删除阶段');checkpoint();content.value.groups=stages.value.filter(g=>g!==old && g!=='未分组');for(const n of content.value.nodes)if(stage(n)===old)n.stage='未分组';currentStage.value='未分组'}catch{}}
async function extract(){await save();if(dirty.value || failed.value)return;extracting.value=true;try{extraction.value=await request.post(path()+'/extract',{}, {timeout:180000});mergeChoices.value={};candidateEdit.value=null;handled.value=[];keepProgress();importVisible.value=true}catch{}finally{extracting.value=false}}
function ignoreCandidate(candidate){
  if(candidateEdit.value?.id===candidate.candidateId)candidateEdit.value=null
  handled.value.push(candidate.candidateId)
  keepProgress()
}
function looksGarbled(candidate){
  const text=candidate.title || ''
  return /�/.test(text) || (/[\u4e00-\u9fff]/.test(text) && (text.match(/[a-z]/gi)||[]).length>text.length/2)
}
function editCandidate(candidate){
  if(candidateEdit.value?.id===candidate.candidateId)return
  candidateEdit.value={
    id:candidate.candidateId,
    title:candidate.title || '',
    instruction:candidate.acceptance || ''
  }
}
function saveEditedCandidate(candidate){
  const edit=candidateEdit.value
  if(edit?.id!==candidate.candidateId || !edit.title.trim())return
  return mergeCandidate(candidate,'',{title:edit.title.trim(),instruction:edit.instruction.trim()})
}
async function mergeCandidate(candidate,targetCode='',edits){
  if(merging.value)return
  merging.value=true
  try{
    await save()
    if(dirty.value || failed.value)return
    const existing=content.value.nodes.find(n=>n.sourceKey===candidate.candidateId)
    if(existing && !targetCode){
      ignoreCandidate(candidate)
      ElMessage.info('该来源已存在')
      return
    }
    const result=await request.post(path()+'/merge-candidates',{
      extractionId:extraction.value.extractionId,
      actions:[{candidateId:candidate.candidateId,targetCode,...(edits && {edits})}],
      expectedRowVersion:workspace.value.rowVersion,
      requestId:key()
    })
    hydrate(result)
    chosen.value=content.value.nodes.find(n=>targetCode?n.nodeCode===targetCode:n.sourceKey===candidate.candidateId)
    if(chosen.value)currentStage.value=stage(chosen.value)
    ignoreCandidate(candidate)
    extractionWarning.value=extraction.value.incomplete && !extraction.value.confirmed
      ? extraction.value.warning || '需人工核对漏提取内容' : ''
    ElMessage.success(targetCode?'已替换并保存':'已新增并保存，请核对原文')
  }catch(error){
    ElMessage.error(error.response?.data?.message || error.message || '候选保存失败，请重试')
  }finally{merging.value=false}
}
async function confirmMissing(extractionId){
  try{
    await ElMessageBox.confirm('确认已对照完整原件检查并补齐漏提取和截断内容？','人工核对')
    await request.post(path()+'/confirm-extraction',{extractionId,confirmed:true})
    if(extraction.value?.extractionId===extractionId)extraction.value.confirmed=true
    extractionWarning.value=''
    ElMessage.success('已记录人工核对')
  }catch{}
}
async function review(node){await save();if(dirty.value || failed.value || !node)return;const code=node.nodeCode;reviewing.value=true;try{hydrate(await request.post(path()+'/review-node',{nodeCode:code,expectedRowVersion:workspace.value.rowVersion}));chosen.value=content.value.nodes.find(n=>n.nodeCode===code);ElMessage.success('已记录本次内容核对，后续修改须重新核对')}catch{}finally{reviewing.value=false}}
function openSource(n){const doc=docs.value.find(d=>d.id===documentId.value);if(!doc?.filePath){ElMessage.warning('原件不可定位，请从规范文档库查看');return}const source=citation(n);if(source?.sourceVersion && Number(source.sourceVersion)!==Number(doc.version)){ElMessage.warning('来源版本已变化，请重新提取；未跳转到其他版本');return}const page=source?.sourceVersion ? source?.anchor?.match(/第(\d+)页/)?.[1] : null;window.open(`/api/knowledge/docs/files/${encodeURIComponent(doc.filePath)}?preview=true${page?'#page='+page:''}`,'_blank','noopener')}

function leave(e){if(dirty.value){e.preventDefault();e.returnValue=''}}
onBeforeRouteLeave(()=>!dirty.value || window.confirm('有尚未保存的内容，确认离开？'))
onMounted(async()=>{docs.value=await listAllComplianceDocuments();const me=await request.get('/auth/me');account.value=me.id || me.username;canActivate.value=me.permissions?.includes('knowledge:maintain');window.addEventListener('beforeunload',leave)})
onBeforeUnmount(()=>{clearTimeout(timer);window.removeEventListener('beforeunload',leave)})
</script>
<style scoped>
.toolbar{display:flex;gap:12px;align-items:center;flex-wrap:wrap;margin-bottom:16px}.toolbar .el-select{width:320px}.hint{color:#687587}.layout{display:grid;grid-template-columns:180px minmax(300px,1fr) 340px;gap:20px;margin-top:20px}aside button{display:block;width:100%;padding:12px;text-align:left;border:0;background:#f5f7fa;margin-bottom:6px;cursor:pointer}aside button.active{background:#e7f1ff;color:#2672d7}header{display:flex;justify-content:space-between;align-items:center}article{border:1px solid #dce3ed;border-radius:8px;padding:16px;margin:12px 0;background:white;cursor:pointer}article.chosen{border-color:#409eff}article p{white-space:pre-wrap;color:#64748b}small{color:#64748b}.properties{padding:16px;background:#f7f9fc;border-radius:8px}.legacy{padding:10px}.source-citation{margin-top:16px}@media(max-width:1050px){.layout{grid-template-columns:150px 1fr}.properties{grid-column:1/-1}}@media(max-width:650px){.layout{display:block}}
</style>
