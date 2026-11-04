<template>
  <main class="dashboard">
    <header class="dashboard-head"><div><p class="eyebrow">PERSONAL WORKSPACE</p><h1>{{ greeting }}，{{ auth.username }}</h1><p class="muted">先处理重要事项，再开始今天的工作。</p></div><div class="head-actions"><span>{{ businessDate }}</span><el-button :loading="loading" @click="refresh()">刷新工作台</el-button></div></header>
    <GlobalSearchBar />
    <nav class="quick-links" aria-label="快捷入口"><router-link v-for="entry in visibleEntries" :key="entry[1]" :to="entry[1]">{{ entry[0] }} <span>↗</span></router-link></nav>
    <el-alert v-if="pageError" title="工作台加载失败，请重试；其他入口仍可使用。" type="error" :closable="false" />
    <section class="summary-grid" aria-label="待处理与到期风险">
      <button v-if="section.myApprovals" class="summary" @click="go({ path:'/profile/approval/manage', query:{ actionable:'1' } })"><span>待我审批</span><strong>{{ count('myApprovals','total') }}</strong><small>当前可由我处理</small></button>
      <button v-if="section.myApplications" class="summary" @click="go({ path:'/profile/my-approvals', query:{ status:'4' } })"><span>退回待修改</span><strong>{{ count('myApplications','returnedCount') }}</strong><small>需要我补充或重新提交</small></button>
      <button v-if="loading || section.projectRisk" class="summary danger" @click="scrollProjects"><span>已逾期项目</span><strong>{{ count('projectRisk','overdueCount') }}</strong><small>按项目结束日期计算</small></button>
      <button v-if="loading || section.projectRisk" class="summary caution" @click="scrollProjects"><span>今天到期 / 七天内到期</span><strong>{{ count('projectRisk','dueTodayCount') }} <i>/</i> {{ count('projectRisk','dueSoonCount') }}</strong><small>今天到期不计入逾期</small></button>
    </section>
    <p v-if="data('projectRisk').unknownCompletionCount" class="unknown-note">{{ data('projectRisk').unknownCompletionCount }} 个项目的完成状态待核实，未归入正常或到期风险。仅历史明确的完成阶段可判定完成。</p>
    <section class="pending-grid">
      <article v-for="key in pendingKeys" :key="key" class="panel"><div class="panel-head"><h2>{{ titles[key] }}</h2><el-button link @click="refresh([key==='returned'?'myApplications':key])">刷新</el-button></div><SectionState :value="section[key==='returned'?'myApplications':key]" :loading="loading && !section[key==='returned'?'myApplications':key]" @retry="refresh([key==='returned'?'myApplications':key])"/><p v-if="key==='returned' && section.myApplications?.status!=='error' && !pendingItems(key).length" class="muted">暂无退回待修改</p><template v-if="section[key==='returned'?'myApplications':key]?.status !== 'error'"><button v-for="item in pendingItems(key)" :key="`${key}-${item.id}`" class="item-row" @click="go(item.target)"><span><b>{{ applicationTitle(item.title) }}</b><small>{{ item.timeType || '申请时间' }} · {{ item.time }}</small></span><el-tag v-if="item.status !== undefined" size="small">{{ statuses[item.status] }}</el-tag><span v-else>处理 →</span></button></template></article>
    </section>
    <section v-if="(loading || section.myProjects) && section.myProjects?.status !== 'empty'" id="my-projects" ref="projectsElement" class="panel projects"><div class="panel-head"><div><h2>我的项目</h2><p class="muted">逾期优先，其次今天和未来七天到期</p></div><div><el-button v-if="auth.hasPermission('project:view-all')" link @click="router.push('/project')">全部可见项目</el-button><el-button link @click="router.push('/project?mine=1')">查看我的全部</el-button><el-button link @click="refresh(['myProjects','projectRisk'])">刷新</el-button></div></div><SectionState :value="section.myProjects" :loading="loading && !section.myProjects" @retry="refresh(['myProjects','projectRisk'])"/><div v-if="section.myProjects?.status === 'success'" class="project-grid"><button v-for="item in data('myProjects').items" :key="item.id" class="project-item" @click="go(item.target)"><div class="project-top"><el-tag v-if="warningNames[item.warning]" :type="item.warning==='OVERDUE'?'danger':'warning'" size="small">{{ warningNames[item.warning] }}</el-tag><span>{{ item.myRelations?.join(' / ') }}</span></div><h3>{{ item.name }}</h3><p>{{ item.phaseName }} · 负责人 {{ item.ownerName }}</p><p>结束日期：{{ item.endDate || '未设置结束日期' }}</p><small v-if="item.lastPhaseChange">最近{{ item.lastPhaseChange.actionType==='ROLLBACK'?'回退':'推进' }}：{{ item.lastPhaseChange.time }}</small><small v-else>暂无阶段变更记录</small></button></div></section>
    <section class="resources-grid"><article v-for="key in ['favorites','announcements','notifications'].filter(key => loading || section[key])" :key="key" class="panel"><div class="panel-head"><h2>{{ titles[key] }} <small v-if="key==='notifications'">{{ data(key).unreadCount ?? '' }}</small></h2><el-button link @click="go(allTargets[key])">查看全部</el-button></div><SectionState :value="section[key]" :loading="loading && !section[key]" @retry="refresh([key])"/><template v-if="section[key]?.status !== 'error'"><button v-for="item in data(key).items || []" :key="item.id || item.docId" class="item-row" @click="go(item.target)"><span><b>{{ item.title }}</b><small>{{ item.favoriteTime || item.publishTime || item.time }}</small></span><el-tag v-if="key==='announcements' && !item.read" size="small">未读</el-tag></button></template></article></section>
    <footer class="muted">{{ updatedAt ? `最近刷新 ${updatedAt}` : '正在获取工作台数据' }} · 页面可见时每60秒刷新</footer>
  </main>
</template>
<script setup>
import { computed, defineComponent, h, nextTick, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '../../store/auth'
import { overview } from '../../api/dashboard'
import { sectionRequests, safeTarget, warningNames } from '../../utils/dashboard'
import GlobalSearchBar from '../../components/GlobalSearchBar.vue'
const router=useRouter(),auth=useAuthStore(),section=ref({}),loading=ref(false),pageError=ref(false),capabilities=ref([]),businessDate=ref(''),updatedAt=ref(''),projectsElement=ref(null),seq=sectionRequests()
const greeting=new Date().getHours()<12?'早上好':new Date().getHours()<18?'下午好':'晚上好'
const titles={myApprovals:'待我审批',myApplications:'我发起的申请',returned:'退回待修改',favorites:'我的收藏',announcements:'最新公告',notifications:'未读消息'}
const statuses={0:'等待审批',1:'已通过',2:'已驳回',3:'已撤回',4:'退回修改'}
const types={EXPERT_MODIFY:'专家修改申请',EXPERT_CREATE:'专家新增申请',CONTRACT:'专家合同申请'}
const applicationTitle=t=>types[t] || t
const entries=[['知识星球','/knowledge-planet/meeting-execution'],['项目管理','/project'],['专家库','/experts'],['客户信息','/knowledge-planet/customer-info'],['供应商','/supplier'],['我的申请','/profile/my-approvals'],['公告','/profile/announcements'],['通讯录','/profile/directory']]
const entryModules=['knowledge','project','expert','customer','supplier','myApplications','announcement','directory']
const visibleEntries=computed(()=>entries.filter((_,i)=>capabilities.value.includes(entryModules[i])))
const allTargets={favorites:{path:'/knowledge-planet/meeting-execution',query:{favorites:'1'}},announcements:{path:'/profile/announcements'},notifications:{path:'/profile/notifications'}}
const pendingKeys=computed(()=>[...(section.value.myApprovals?['myApprovals']:[]),...(section.value.myApplications?['returned','myApplications']:[])])
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
  if(id>=capabilityVersion){capabilities.value=response.capabilities || [];capabilityVersion=id;businessDate.value=response.businessDate;updatedAt.value=new Date(response.generatedAt).toLocaleTimeString('zh-CN',{hour12:false,timeZone:'Asia/Shanghai'})}
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
.dashboard{max-width:1440px;margin:auto;color:#24334d}.dashboard-head{display:flex;align-items:center;justify-content:space-between;gap:20px;margin-bottom:18px}.eyebrow{font-size:11px;letter-spacing:2px;color:#557194;margin:0 0 8px}h1{font-size:26px;margin:0}h2{font-size:16px;margin:0}h3{font-size:17px;margin:13px 0 10px}.muted{color:#758298;font-size:12px;margin:8px 0}.head-actions{display:flex;align-items:center;gap:16px;font-size:13px;color:#64748b}.quick-links{display:flex;gap:8px;flex-wrap:wrap;margin:16px 0 22px}.quick-links a{padding:7px 12px;color:#4a607c;text-decoration:none;background:white;border:1px solid #e3eaf3;border-radius:6px;font-size:12px}.quick-links span{margin-left:8px;color:#8fa2b9}.summary-grid{display:grid;grid-template-columns:repeat(4,1fr);gap:14px;margin:16px 0}.summary{display:flex;flex-direction:column;align-items:flex-start;gap:8px;padding:18px;border:1px solid #dce5f2;background:#fff;border-radius:10px;cursor:pointer;color:#334e72;text-align:left}.summary strong{font-size:29px;color:#254f91}.summary strong i{font-size:20px;font-style:normal;color:#c7d1df}.summary small{color:#8391a4}.summary.danger{background:#fff8f6;border-color:#f4dcd6}.danger strong{color:#bb5446}.summary.caution{background:#fffaf1;border-color:#f0e3c8}.caution strong{color:#a67526}.unknown-note{font-size:12px;color:#8f692f;background:#fff9ed;padding:10px 14px;border-radius:6px}.panel{background:white;border:1px solid #e3eaf3;border-radius:10px;padding:18px;margin-bottom:16px}.panel-head{display:flex;justify-content:space-between;align-items:center;gap:10px;margin-bottom:10px}.pending-grid{display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:14px}.pending-grid:has(>.panel:nth-child(2):last-child){grid-template-columns:repeat(2,minmax(0,1fr))}.item-row{display:flex;justify-content:space-between;align-items:center;gap:10px;width:100%;padding:12px 0;border:0;border-top:1px solid #f0f3f8;background:white;text-align:left;cursor:pointer;color:#405475}.item-row b{font-size:13px;font-weight:500}.item-row small{display:block;font-size:11px;color:#8593a5;margin-top:5px}.project-grid{display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:12px}.project-item{border:1px solid #e5ebf4;background:#fbfcfe;border-radius:8px;padding:16px;text-align:left;cursor:pointer;color:#455c79}.project-top{display:flex;justify-content:space-between;min-height:20px;font-size:11px;color:#8a99ac}.project-item p{font-size:12px;margin:8px 0}.project-item small{font-size:11px;color:#7c8da3}.resources-grid{display:grid;grid-template-columns:1.2fr 1fr 1fr;gap:14px}.section-error{color:#b15d4a;font-size:13px}footer{padding:4px 0 18px}button:focus-visible,a:focus-visible{outline:2px solid #2864d8;outline-offset:3px}@media(max-width:1100px){.summary-grid,.project-grid{grid-template-columns:repeat(2,1fr)}.resources-grid,.pending-grid{grid-template-columns:1fr 1fr}}
</style>
