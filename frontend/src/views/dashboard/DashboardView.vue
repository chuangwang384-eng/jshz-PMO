<template>
  <main class="dashboard">
    <header class="dashboard-head"><div><p class="eyebrow">PERSONAL WORKSPACE</p><h1>{{ greeting }}，{{ auth.username }}</h1><p class="muted">先处理重要事项，再开始今天的工作。</p></div><div class="head-actions"><span>{{ businessDate }}</span><template v-if="!editing"><el-button @click="startEdit">编辑布局</el-button></template><template v-else><el-button type="primary" :loading="savingLayout" @click="saveLayout">保存布局</el-button><el-button @click="resetEdit">恢复默认</el-button><el-button @click="cancelEdit">取消</el-button></template><el-button :loading="loading" @click="refresh()">刷新工作台</el-button></div></header>
    <GlobalSearchBar />
    <nav class="quick-links" aria-label="快捷入口"><router-link v-for="entry in visibleEntries" :key="entry[1]" :to="entry[1]">{{ entry[0] }} <span>↗</span></router-link></nav>
    <el-alert v-if="pageError" title="工作台加载失败，请重试；其他入口仍可使用。" type="error" :closable="false" />
    <template v-if="!editing">
      <section class="layout-grid" aria-label="工作台卡片">
        <div v-for="item in layoutItems" :key="item.i" class="cell" :style="{ gridColumn: 'span ' + Math.min(item.w, 12) }">
              <!-- 统计卡 -->
              <button v-if="item.i==='stat.myApprovals'" class="summary" @click="go({ path:'/profile/approval/manage', query:{ actionable:'1' } })"><span>待我审批</span><strong>{{ count('myApprovals','total') }}</strong><small>当前可由我处理</small></button>
              <button v-else-if="item.i==='stat.myApplications'" class="summary" @click="go({ path:'/profile/my-approvals', query:{ status:'4' } })"><span>退回待修改</span><strong>{{ count('myApplications','returnedCount') }}</strong><small>需要我补充或重新提交</small></button>
              <button v-else-if="item.i==='stat.riskOverdue'" class="summary danger" @click="scrollProjects"><span>已逾期项目</span><strong>{{ count('projectRisk','overdueCount') }}</strong><small>按项目结束日期计算</small></button>
              <button v-else-if="item.i==='stat.riskDue'" class="summary caution" @click="scrollProjects"><span>今天到期 / 七天内到期</span><strong>{{ count('projectRisk','dueTodayCount') }} <i>/</i> {{ count('projectRisk','dueSoonCount') }}</strong><small>今天到期不计入逾期</small></button>
              <!-- 页面卡片 -->
              <button v-else-if="item.i.startsWith('page.')" class="summary page-tile" @click="go(cardTarget(item.i))"><span class="page-tile-title">{{ CARD_CATALOG[item.i].title }}</span><span class="page-tile-arrow">↗</span></button>
              <!-- 面板：待我审批 -->
              <article v-else-if="item.i==='panel.myApprovals'" class="panel"><div class="panel-head"><h2>待我审批</h2><el-button link @click="refresh(['myApprovals'])">刷新</el-button></div><SectionState :value="section.myApprovals" :loading="loading && !section.myApprovals" @retry="refresh(['myApprovals'])"/><template v-if="section.myApprovals?.status !== 'error'"><button v-for="item2 in pendingItems('myApprovals')" :key="item2.id" class="item-row" @click="go(item2.target)"><span><b>{{ item2.title }}</b><small>{{ item2.timeType || '申请时间' }} · {{ item2.time }}</small></span><el-tag v-if="item2.status !== undefined" size="small">{{ statuses[item2.status] }}</el-tag><span v-else>处理 →</span></button></template></article>
              <!-- 面板：退回待修改 -->
              <article v-else-if="item.i==='panel.returned'" class="panel"><div class="panel-head"><h2>退回待修改</h2><el-button link @click="refresh(['myApplications'])">刷新</el-button></div><SectionState :value="section.myApplications" :loading="loading && !section.myApplications" @retry="refresh(['myApplications'])"/><p v-if="section.myApplications?.status!=='error' && !pendingItems('returned').length" class="muted">暂无退回待修改</p><template v-if="section.myApplications?.status !== 'error'"><button v-for="item2 in pendingItems('returned')" :key="item2.id" class="item-row" @click="go(item2.target)"><span><b>{{ applicationTitle(item2.title) }}</b><small>{{ item2.timeType || '申请时间' }} · {{ item2.time }}</small></span><el-tag v-if="item2.status !== undefined" size="small">{{ statuses[item2.status] }}</el-tag><span v-else>处理 →</span></button></template></article>
              <!-- 面板：我的项目 -->
              <article v-else-if="item.i==='panel.myProjects'" id="my-projects" ref="projectsElement" class="panel projects"><div class="panel-head"><div><h2>我的项目</h2><p class="muted">逾期优先，其次今天和未来七天到期</p></div><div><el-button v-if="auth.hasPermission('project:view-all')" link @click="router.push('/project')">全部可见项目</el-button><el-button link @click="router.push('/project?mine=1')">查看我的全部</el-button><el-button link @click="refresh(['myProjects','projectRisk'])">刷新</el-button></div></div><SectionState :value="section.myProjects" :loading="loading && !section.myProjects" @retry="refresh(['myProjects','projectRisk'])"/><div v-if="section.myProjects?.status === 'success'" class="project-grid"><button v-for="item2 in data('myProjects').items" :key="item2.id" class="project-item" @click="go(item2.target)"><div class="project-top"><el-tag v-if="warningNames[item2.warning]" :type="item2.warning==='OVERDUE'?'danger':'warning'" size="small">{{ warningNames[item2.warning] }}</el-tag><span>{{ item2.myRelations?.join(' / ') }}</span></div><h3>{{ item2.name }}</h3><p>{{ item2.phaseName }} · 负责人 {{ item2.ownerName }}</p><p>结束日期：{{ item2.endDate || '未设置结束日期' }}</p><small v-if="item2.lastPhaseChange">最近{{ item2.lastPhaseChange.actionType==='ROLLBACK'?'回退':'推进' }}：{{ item2.lastPhaseChange.time }}</small><small v-else>暂无阶段变更记录</small></button></div></article>
              <!-- 面板：收藏/公告/通知 -->
              <article v-else-if="item.i==='panel.favorites' || item.i==='panel.announcements' || item.i==='panel.notifications'" class="panel"><div class="panel-head"><h2>{{ CARD_CATALOG[item.i].title }} <small v-if="item.i==='panel.notifications'">{{ data(sectionKeyOfPanel(item.i)).unreadCount ?? '' }}</small></h2><el-button link @click="go(allTargets[sectionKeyOfPanel(item.i)])">查看全部</el-button></div><SectionState :value="section[sectionKeyOfPanel(item.i)]" :loading="loading && !section[sectionKeyOfPanel(item.i)]" @retry="refresh([sectionKeyOfPanel(item.i)])"/><template v-if="section[sectionKeyOfPanel(item.i)]?.status !== 'error'"><button v-for="item2 in data(sectionKeyOfPanel(item.i)).items || []" :key="item2.id || item2.docId" class="item-row" @click="go(item2.target)"><span><b>{{ item2.title }}</b><small>{{ item2.favoriteTime || item2.publishTime || item2.time }}</small></span><el-tag v-if="sectionKeyOfPanel(item.i)==='announcements' && !item2.read" size="small">未读</el-tag></button></template></article>
            </div>
      </section>
      <p v-if="data('projectRisk').unknownCompletionCount" class="unknown-note">{{ data('projectRisk').unknownCompletionCount }} 个项目的完成状态待核实，未归入正常或到期风险。仅历史明确的完成阶段可判定完成。</p>
    </template>
    <template v-else>
      <div class="edit-tip">拖动卡片调整顺序，用 ◀ ▶ 调整宽度（1~12 列）；下方备选卡片点击添加，卡片右上角 × 移除。完成后点「保存布局」。</div>
      <div class="layout-grid edit-grid">
        <div v-for="(item, idx) in editLayout" :key="item.i" class="cell" :style="{ gridColumn: 'span ' + Math.min(item.w, 12) }"
             draggable="true" @dragstart="onDragStart(idx)" @dragover.prevent="onDragOver(idx)" @drop.prevent="onDrop(idx)"
             :class="{ 'edit-cell-dragging': dragIndex === idx }">
          <div class="edit-card">
            <span class="edit-card-title">{{ CARD_CATALOG[item.i]?.title || item.i }}</span>
            <div class="edit-card-actions">
              <button type="button" title="变窄" @click.stop="narrowCard(item)">◀</button>
              <span class="edit-card-w">{{ Math.min(item.w, 12) }}</span>
              <button type="button" title="变宽" @click.stop="widenCard(item)">▶</button>
              <button type="button" class="edit-card-remove" title="移除卡片" @click.stop="removeCard(item.i)">×</button>
            </div>
          </div>
        </div>
      </div>
      <div class="palette">
        <div class="palette-head">备选卡片（点击添加到工作台）</div>
        <div class="palette-list">
          <button v-for="id in paletteIds" :key="id" class="palette-item" @click="addCard(id)"><span class="chip-dot" :data-color="cardDot(id)"></span>{{ CARD_CATALOG[id].title }}<small>{{ CARD_CATALOG[id].kind==='page'?'页面':(CARD_CATALOG[id].kind==='stat'?'统计':'面板') }}</small></button>
          <p v-if="!paletteIds.length" class="muted" style="margin:0">所有可用卡片均已添加</p>
        </div>
      </div>
    </template>
    <footer class="muted">{{ updatedAt ? `最近刷新 ${updatedAt}` : '正在获取工作台数据' }} · 页面可见时每60秒刷新</footer>
  </main>
</template>

<script setup>
import { computed, defineComponent, h, nextTick, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useAuthStore } from '../../store/auth'
import { overview, getDashboardLayout, saveDashboardLayout } from '../../api/dashboard'
import { sectionRequests, safeTarget, warningNames } from '../../utils/dashboard'
import GlobalSearchBar from '../../components/GlobalSearchBar.vue'
const router=useRouter(),auth=useAuthStore(),section=ref({}),loading=ref(false),pageError=ref(false),businessDate=ref(''),updatedAt=ref(''),projectsElement=ref(null),seq=sectionRequests()
// capabilities 会话级缓存：首帧立即渲染快捷入口，避免 overview 返回前整条消失再弹入
const capabilities=ref((()=>{try{return JSON.parse(sessionStorage.getItem(`dashboard-capabilities:${auth.id}`)||'[]')}catch(e){return []}})())
const greeting=new Date().getHours()<12?'早上好':new Date().getHours()<18?'下午好':'晚上好'
const titles={myApprovals:'待我审批',myApplications:'我发起的申请',returned:'退回待修改',favorites:'我的收藏',announcements:'最新公告',notifications:'未读消息'}
const statuses={0:'等待审批',1:'已通过',2:'已驳回',3:'已撤回',4:'退回修改'}
const types={EXPERT_MODIFY:'专家修改申请',EXPERT_CREATE:'专家新增申请',CONTRACT:'专家合同申请'}
const applicationTitle=t=>types[t] || t

// ==================== 卡片目录与可调布局 ====================
// 布局模型（借鉴 vue-grid-layout 的"布局即数据"思路，零第三方依赖）：[{i,w}]，顺序即排列，w 为 12 列栅格宽度
const CARD_CATALOG={
 'stat.myApprovals':{title:'待我审批',w:3,h:1,kind:'stat',perm:'approval:audit'},
 'stat.myApplications':{title:'退回待修改',w:3,h:1,kind:'stat'},
 'stat.riskOverdue':{title:'已逾期项目',w:3,h:1,kind:'stat'},
 'stat.riskDue':{title:'今天到期 / 七天内到期',w:3,h:1,kind:'stat'},
 'panel.myApprovals':{title:'待我审批',w:4,h:6,kind:'panel',perm:'approval:audit'},
 'panel.returned':{title:'退回待修改',w:4,h:6,kind:'panel'},
 'panel.myProjects':{title:'我的项目',w:4,h:6,kind:'panel'},
 'panel.favorites':{title:'我的收藏',w:4,h:6,kind:'panel'},
 'panel.announcements':{title:'最新公告',w:4,h:6,kind:'panel'},
 'panel.notifications':{title:'未读消息',w:4,h:6,kind:'panel'},
 'page.knowledge':{title:'知识星球',w:3,h:1,kind:'page',path:'/knowledge-planet/meeting-execution',cap:'knowledge'},
 'page.project':{title:'项目管理',w:3,h:1,kind:'page',path:'/project',cap:'project'},
 'page.experts':{title:'专家库',w:3,h:1,kind:'page',path:'/experts',cap:'expert'},
 'page.customer':{title:'客户信息',w:3,h:1,kind:'page',path:'/knowledge-planet/customer-info',cap:'customer'},
 'page.supplier':{title:'供应商管理',w:3,h:1,kind:'page',path:'/supplier',cap:'supplier'},
 'page.myApplications':{title:'我的申请',w:3,h:1,kind:'page',path:'/profile/my-approvals',cap:'myApplications'},
 'page.announcements':{title:'公告',w:3,h:1,kind:'page',path:'/profile/announcements',cap:'announcement'},
 'page.directory':{title:'通讯录',w:3,h:1,kind:'page',path:'/profile/directory',cap:'directory'},
 'page.notifications':{title:'消息中心',w:3,h:1,kind:'page',path:'/profile/notifications'},
 'page.approvalManage':{title:'审批管理',w:3,h:1,kind:'page',path:'/profile/approval/manage',perm:'approval:audit'}
}
const DEFAULT_LAYOUT=[
 {i:'stat.myApprovals',w:3},{i:'stat.myApplications',w:3},{i:'stat.riskOverdue',w:3},{i:'stat.riskDue',w:3},
 {i:'panel.myApprovals',w:4},{i:'panel.returned',w:4},{i:'panel.myProjects',w:4},
 {i:'panel.favorites',w:4},{i:'panel.announcements',w:4},{i:'panel.notifications',w:4}
]
function canShow(id){
  const card=CARD_CATALOG[id];if(!card)return false
  if(card.perm && !auth.hasPermission(card.perm))return false
  if(card.cap && !capabilities.value.includes(card.cap))return false
  return true
}
const savedLayout=ref([])
const editing=ref(false),savingLayout=ref(false)
const editLayout=ref([])
const layoutItems=computed(()=>savedLayout.value.filter(item=>canShow(item.i)))
const paletteIds=computed(()=>Object.keys(CARD_CATALOG).filter(id=>canShow(id)&&!editLayout.value.some(item=>item.i===id)))
function normalizeLayout(raw){
  if(!Array.isArray(raw))return null
  const seen=new Set()
  const clean=raw.filter(item=>item && CARD_CATALOG[item.i] && !seen.has(item.i) && seen.add(item.i) && canShow(item.i) && Number.isFinite(item.w) && item.w>=1 && item.w<=12)
  return clean.length?clean.map(item=>({i:item.i,w:Math.min(Math.max(1,Math.round(item.w)),12)})):null
}
function cardTarget(id){const card=CARD_CATALOG[id];return card?.path?{path:card.path}:null}
function sectionKeyOfPanel(id){return {'panel.favorites':'favorites','panel.announcements':'announcements','panel.notifications':'notifications'}[id]}
function cardDot(id){const card=CARD_CATALOG[id];return card.kind==='page'?'primary':(card.kind==='stat'?'warning':'success')}
function addCard(id){
  const card=CARD_CATALOG[id];if(!card||editLayout.value.some(item=>item.i===id))return
  editLayout.value.push({i:id,w:card.w})
}
function removeCard(id){editLayout.value=editLayout.value.filter(item=>item.i!==id)}
const dragIndex=ref(null)
function onDragStart(idx){dragIndex.value=idx}
function onDragOver(idx){if(dragIndex.value===null||dragIndex.value===idx)return;const arr=editLayout.value.slice();const [moved]=arr.splice(dragIndex.value,1);arr.splice(idx,0,moved);editLayout.value=arr;dragIndex.value=idx}
function onDrop(){dragIndex.value=null}
function widenCard(item){item.w=Math.min((item.w||1)+1,12)}
function narrowCard(item){item.w=Math.max((item.w||1)-1,1)}
function startEdit(){editLayout.value=layoutItems.value.map(item=>({...item}));dragIndex.value=null;editing.value=true}
function resetEdit(){editLayout.value=DEFAULT_LAYOUT.filter(item=>canShow(item.i)).map(item=>({...item}));ElMessage.info('已恢复默认布局，点击「保存布局」生效')}
function cancelEdit(){editing.value=false}
async function saveLayout(){
  savingLayout.value=true
  try{await saveDashboardLayout(JSON.stringify(editLayout.value));savedLayout.value=editLayout.value.map(item=>({...item}));editing.value=false;ElMessage.success('布局已保存，下次进入工作台即为此布局')}
  catch(e){/* 拦截器已提示 */}finally{savingLayout.value=false}
}

const entries=[['知识星球','/knowledge-planet/meeting-execution'],['项目管理','/project'],['专家库','/experts'],['客户信息','/knowledge-planet/customer-info'],['供应商','/supplier'],['我的申请','/profile/my-approvals'],['公告','/profile/announcements'],['通讯录','/profile/directory']]
const entryModules=['knowledge','project','expert','customer','supplier','myApplications','announcement','directory']
const visibleEntries=computed(()=>entries.filter((_,i)=>capabilities.value.includes(entryModules[i])))
const allTargets={favorites:{path:'/knowledge-planet/meeting-execution',query:{favorites:'1'}},announcements:{path:'/profile/announcements'},notifications:{path:'/profile/notifications'}}
const data=key=>section.value[key]?.data || {}
const count=(key,field)=>section.value[key]?.status==='error'?'暂不可用':data(key)[field] ?? '—'
const pendingItems=key=>key==='returned'?data('myApplications').returnedItems || []:key==='myApplications'?data(key).latestItems || []:data(key).items || []
const SectionState=defineComponent({props:['value','loading'],emits:['retry'],setup(p,{emit}){return()=>p.loading?h('p',{class:'muted'},'正在加载…'):p.value?.status==='error'?h('p',{class:'section-error'},['加载失败，数据暂不可用。',h('button',{onClick:()=>emit('retry')},'重试')]):p.value?.status==='empty'?h('p',{class:'muted'},'暂无相关事项'):null}})
const allSections=['myApplications','myApprovals','myProjects','projectRisk','favorites','announcements','notifications']
const requests=new Set()
let timer,lastFocus=0,alive=true,pending=0,capabilityVersion=0
async function refresh(keys) {
 const selected=keys || allSections
 if(!keys)for(const request of requests)request.abort()
 const controller=new AbortController();requests.add(controller)
 const id=seq.begin(selected);pending++;loading.value=true;pageError.value=false
 try {
  const response=await overview(keys,controller.signal)
  if(!alive)return
  const next={...section.value}
  for(const key of selected)if(seq.current(key,id)) {
   if(response.sections[key])next[key]=response.sections[key]
   else delete next[key]
  }
  section.value=next
  if(id>=capabilityVersion){capabilities.value=response.capabilities || [];capabilityVersion=id;businessDate.value=response.businessDate;updatedAt.value=new Date(response.generatedAt).toLocaleTimeString('zh-CN',{hour12:false,timeZone:'Asia/Shanghai'});try{sessionStorage.setItem(`dashboard-capabilities:${auth.id}`,JSON.stringify(capabilities.value))}catch(e){}}
 } catch(e) {
  if(alive && e.code!=='ERR_CANCELED') {
   pageError.value=true
   for(const key of selected)if(seq.current(key,id) && (key!=='myApprovals' || auth.hasPermission('approval:audit')))section.value[key]={status:'error',data:null}
  }
 } finally {requests.delete(controller);pending--;if(alive)loading.value=pending>0}
}
function go(target){const value=safeTarget(router,target);if(value)router.push(value)}
function scrollProjects(){projectsElement.value?.scrollIntoView({behavior:'smooth',block:'start'})}
function focused(){if(!document.hidden&&Date.now()-lastFocus>1000){lastFocus=Date.now();refresh()}}
onMounted(async()=>{
 const saved=Number(sessionStorage.getItem(`dashboard-scroll:${auth.id}`) || 0)
 timer=setInterval(()=>{if(!document.hidden)refresh()},60000)
 window.addEventListener('focus',focused);document.addEventListener('visibilitychange',focused)
 // 并行：加载数据 + 加载已保存布局
 try{const d=await getDashboardLayout();const normalized=normalizeLayout(JSON.parse(d.layout||'null'));if(normalized)savedLayout.value=normalized}catch(e){/* 无保存布局或解析失败用默认 */}
 if(!savedLayout.value.length)savedLayout.value=DEFAULT_LAYOUT.filter(item=>canShow(item.i)).map(item=>({...item}))
 await refresh();await nextTick()
 if(alive && Number.isFinite(saved)){const main=document.querySelector('.el-main');if(main)main.scrollTop=Math.max(0,saved)}
})
onBeforeUnmount(()=>{
 alive=false;sessionStorage.setItem(`dashboard-scroll:${auth.id}`,String(document.querySelector('.el-main')?.scrollTop || 0))
 for(const request of requests)request.abort()
 clearInterval(timer);window.removeEventListener('focus',focused);document.removeEventListener('visibilitychange',focused)
})
</script>

<style scoped>
.dashboard{max-width:1440px;margin:auto;color:#24334d}.dashboard-head{display:flex;align-items:center;justify-content:space-between;gap:20px;margin-bottom:18px}.eyebrow{font-size:11px;letter-spacing:2px;color:#557194;margin:0 0 8px}h1{font-size:26px;margin:0}h2{font-size:16px;margin:0}h3{font-size:17px;margin:13px 0 10px}.muted{color:#758298;font-size:12px;margin:8px 0}.head-actions{display:flex;align-items:center;gap:12px;font-size:13px;color:#64748b}.quick-links{display:flex;gap:8px;flex-wrap:wrap;margin:16px 0 22px}.quick-links a{padding:7px 12px;color:#4a607c;text-decoration:none;background:white;border:1px solid #e3eaf3;border-radius:6px;font-size:12px}.quick-links span{margin-left:8px;color:#8fa2b9}
.layout-grid{margin:16px 0}
.cell{height:100%;overflow:hidden}
.summary{display:flex;flex-direction:column;align-items:flex-start;justify-content:center;gap:2px;padding:8px 12px;border:1px solid #dce5f2;background:#fff;border-radius:10px;cursor:pointer;color:#334e72;text-align:left;width:100%;height:100%;box-sizing:border-box}.summary span{font-size:11px}.summary strong{font-size:22px;color:#254f91;line-height:1.2}.summary strong i{font-size:16px;font-style:normal;color:#c7d1df}.summary small{color:#8391a4;font-size:10px;line-height:1.2}.summary.danger{background:#fff8f6;border-color:#f4dcd6}.danger strong{color:#bb5446}.summary.caution{background:#fffaf1;border-color:#f0e3c8}.caution strong{color:#a67526}
.page-tile{flex-direction:row;align-items:center;justify-content:space-between}.page-tile-title{font-size:13px;font-weight:600;color:#254f91}.page-tile-arrow{color:#8fa2b9;font-size:14px}.page-tile:hover{border-color:#a0cfff;background:#f4f9ff}
.panel{background:white;border:1px solid #e3eaf3;border-radius:10px;padding:12px 18px;margin:0;width:100%;height:100%;box-sizing:border-box;overflow-y:auto}.panel-head{display:flex;justify-content:space-between;align-items:center;gap:10px;margin-bottom:8px}.item-row{display:flex;justify-content:space-between;align-items:center;gap:10px;width:100%;padding:12px 0;border:0;border-top:1px solid #f0f3f8;background:white;text-align:left;cursor:pointer;color:#405475}.item-row b{font-size:13px;font-weight:500}.item-row small{display:block;font-size:11px;color:#8593a5;margin-top:5px}.project-grid{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:12px}.project-item{border:1px solid #e5ebf4;background:#fbfcfe;border-radius:8px;padding:16px;text-align:left;cursor:pointer;color:#455c79}.project-top{display:flex;justify-content:space-between;min-height:20px;font-size:11px;color:#8a99ac}.project-item p{font-size:12px;margin:8px 0}.project-item small{font-size:11px;color:#7c8da3}.unknown-note{font-size:12px;color:#8f692f;background:#fff9ed;padding:10px 14px;border-radius:6px}.section-error{color:#b15d4a;font-size:13px}footer{padding:4px 0 18px}button:focus-visible,a:focus-visible{outline:2px solid #2864d8;outline-offset:3px}
.edit-tip{font-size:12px;color:#758298;background:#f4f8ff;border:1px solid #dce5f2;border-radius:6px;padding:8px 12px;margin:12px 0}
.edit-card{position:relative;display:flex;align-items:center;justify-content:center;height:100%;background:#fff;border:1px solid #b9d1f2;border-radius:8px;overflow:hidden;cursor:move}.edit-card-title{font-size:12px;color:#334e72;padding:0 24px;text-align:center}.edit-card-remove{position:absolute;top:2px;right:4px;border:0;background:none;color:#c0c4cc;font-size:14px;cursor:pointer}.edit-card-remove:hover{color:#f56c6c}
.palette{margin-top:16px;background:white;border:1px solid #e3eaf3;border-radius:10px;padding:14px}.palette-head{font-size:12px;color:#758298;margin-bottom:10px}.palette-list{display:flex;flex-wrap:wrap;gap:8px}.palette-item{display:inline-flex;align-items:center;gap:6px;padding:6px 12px;font-size:12px;color:#334e72;background:#f5f8fd;border:1px solid #dce5f2;border-radius:14px;cursor:pointer}.palette-item:hover{border-color:#a0cfff;background:#ecf5ff}.palette-item small{color:#8fa2b9}
.chip-dot{width:8px;height:8px;border-radius:50%;flex-shrink:0}.chip-dot[data-color='primary']{background:#409eff}.chip-dot[data-color='warning']{background:#e6a23c}.chip-dot[data-color='success']{background:#67c23a}
</style>
