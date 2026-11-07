<template>
  <main class="dashboard">
    <header class="dashboard-head"><div><p class="eyebrow">PERSONAL WORKSPACE</p><h1>{{ greeting }}，{{ auth.username }}</h1><p class="muted">先处理重要事项，再开始今天的工作。</p></div><div class="head-actions"><span>{{ businessDate }}</span><template v-if="!editing"><el-button v-if="layoutEnabled" :disabled="!layoutReady || !capabilitiesReady || !wide" @click="startEdit">编辑布局</el-button></template><template v-else><el-button type="primary" :loading="savingLayout" :disabled="!wide || conflict" @click="saveLayout">保存布局</el-button><el-button :disabled="savingLayout || !wide" @click="resetEdit">恢复默认</el-button><el-button :disabled="savingLayout" @click="cancelEdit">取消</el-button></template><el-button :loading="loading" @click="refreshWorkbench">刷新工作台</el-button></div></header>
    <GlobalSearchBar />
    <nav class="quick-links" aria-label="快捷入口"><router-link v-for="entry in visibleEntries" :key="entry[1]" :to="entry[1]">{{ entry[0] }} <span>↗</span></router-link></nav>
    <el-alert v-if="pageError" title="工作台加载失败，请重试；其他入口仍可使用。" type="error" :closable="false" />
    <p v-if="layoutEnabled && !wide" class="muted">请使用宽屏编辑布局。</p>
    <p v-if="editing" class="edit-tip">拖动标题移动，拖动右下角改变宽高。聚焦手柄可用方向键移动，按住 Shift 改变宽高。<span role="status">{{ layoutStatus }}</span></p>
    <el-button v-if="editing && conflict" :disabled="savingLayout" @click="reloadLayout">重新加载最新布局</el-button>
    <p v-if="!displayLayout.length" class="muted">暂无卡片，可通过编辑布局添加模块或恢复默认。</p>
    <section :key="activeGrid ? 'editor' : 'browse'" ref="gridElement" :class="activeGrid ? 'grid-stack' : ['layout-grid', { narrow: !wide }]" aria-label="工作台卡片">
      <div v-for="item in displayLayout" :key="item.i" :data-card="item.i" :class="activeGrid ? 'grid-stack-item' : 'cell'"
           :style="activeGrid || !wide ? undefined : { gridColumn: (item.x + 1) + ' / span ' + item.w, gridRow: (item.y + 1) + ' / span ' + item.h }">
        <div :class="activeGrid ? 'grid-stack-item-content layout-content' : 'layout-content'">
          <div v-if="editing" class="layout-toolbar">
            <button class="layout-handle" type="button" :disabled="savingLayout" :aria-label="'移动' + CARD_CATALOG[item.i].title" title="拖动移动，方向键调整" @keydown="adjustCard($event, item.i)">⠿</button>
            <button type="button" :disabled="savingLayout" :aria-label="'移除' + CARD_CATALOG[item.i].title" @click="removeCard(item.i)">×</button>
          </div>
          <div class="layout-body">
              <!-- 统计卡 -->
              <button v-if="item.i==='stat.myApprovals'" class="summary" @click="go({ path:'/profile/approval/manage', query:{ actionable:'1' } })"><span>待我审批</span><strong>{{ count('myApprovals','total') }}</strong><small>待处理</small></button>
              <button v-else-if="item.i==='stat.myApplications'" class="summary" @click="go({ path:'/profile/my-approvals', query:{ status:'4' } })"><span>退回待修改</span><strong>{{ count('myApplications','returnedCount') }}</strong><small>待补充</small></button>
              <button v-else-if="item.i==='stat.riskOverdue'" class="summary danger" @click="scrollProjects"><span>已逾期项目</span><strong>{{ count('projectRisk','overdueCount') }}</strong><small>已超期</small></button>
              <button v-else-if="item.i==='stat.riskDue'" class="summary caution" @click="scrollProjects"><span>到期提醒</span><strong>{{ count('projectRisk','dueTodayCount') }} <i>/</i> {{ count('projectRisk','dueSoonCount') }}</strong><small>今天 / 七天内</small></button>
              <!-- 页面卡片 -->
              <button v-else-if="item.i.startsWith('page.')" class="summary page-tile" @click="go(cardTarget(item.i))"><span class="page-tile-title">{{ CARD_CATALOG[item.i].title }}</span><span class="page-tile-arrow">↗</span></button>
              <!-- 面板：待我审批 -->
              <article v-else-if="item.i==='panel.myApprovals'" class="panel"><div class="panel-head"><h2>待我审批</h2><el-button link @click="refresh(['myApprovals'])">刷新</el-button></div><SectionState :value="section.myApprovals" :loading="loading && !section.myApprovals" @retry="refresh(['myApprovals'])"/><template v-if="section.myApprovals?.status !== 'error'"><button v-for="item2 in pendingItems('myApprovals')" :key="item2.id" class="item-row" @click="go(item2.target)"><span><b>{{ item2.title }}</b><small>{{ item2.timeType || '申请时间' }} · {{ item2.time }}</small></span><el-tag v-if="item2.status !== undefined" size="small">{{ statuses[item2.status] }}</el-tag><span v-else>处理 →</span></button></template></article>
              <!-- 面板：退回待修改 -->
              <article v-else-if="item.i==='panel.returned'" class="panel"><div class="panel-head"><h2>退回待修改</h2><el-button link @click="refresh(['myApplications'])">刷新</el-button></div><SectionState :value="section.myApplications" :loading="loading && !section.myApplications" @retry="refresh(['myApplications'])"/><p v-if="section.myApplications?.status!=='error' && !pendingItems('returned').length" class="muted">暂无退回待修改</p><template v-if="section.myApplications?.status !== 'error'"><button v-for="item2 in pendingItems('returned')" :key="item2.id" class="item-row" @click="go(item2.target)"><span><b>{{ applicationTitle(item2.title) }}</b><small>{{ item2.timeType || '申请时间' }} · {{ item2.time }}</small></span><el-tag v-if="item2.status !== undefined" size="small">{{ statuses[item2.status] }}</el-tag><span v-else>处理 →</span></button></template></article>
              <!-- 面板：我的项目 -->
              <article v-else-if="item.i==='panel.myProjects'" id="my-projects" ref="projectsElement" class="panel projects"><div class="panel-head"><div><h2>我的项目</h2><p class="muted">按到期风险排序</p></div><div><el-button v-if="auth.hasPermission('project:view-all')" link @click="router.push('/project')">全部可见项目</el-button><el-button link @click="router.push('/project?mine=1')">查看我的全部</el-button><el-button link @click="refresh(['myProjects','projectRisk'])">刷新</el-button></div></div><SectionState :value="section.myProjects" :loading="loading && !section.myProjects" @retry="refresh(['myProjects','projectRisk'])"/><template v-if="section.myProjects?.status === 'success'"><button v-for="item2 in data('myProjects').items" :key="item2.id" class="item-row project-row" @click="go(item2.target)"><span><b>{{ item2.name }} <el-tag v-if="warningNames[item2.warning]" :type="item2.warning==='OVERDUE'?'danger':'warning'" size="small">{{ warningNames[item2.warning] }}</el-tag></b><small>{{ item2.phaseName || '筹备' }} · 负责人 {{ item2.ownerName }} · 结束日期：{{ item2.endDate || '未设置' }}<template v-if="item2.lastPhaseChange"> · 最近{{ item2.lastPhaseChange.actionType==='ROLLBACK'?'回退':'推进' }} {{ item2.lastPhaseChange.time }}</template></small><small v-if="item2.myRelations?.length" class="relation-line">我的角色：{{ item2.myRelations.join(' / ') }}</small></span><span class="detail-arrow">详情 →</span></button></template></article>
              <!-- 面板：收藏/公告/通知 -->
              <article v-else-if="item.i==='panel.favorites' || item.i==='panel.announcements' || item.i==='panel.notifications'" class="panel"><div class="panel-head"><h2>{{ CARD_CATALOG[item.i].title }} <small v-if="item.i==='panel.notifications'">{{ data(sectionKeyOfPanel(item.i)).unreadCount ?? '' }}</small></h2><el-button link @click="go(allTargets[sectionKeyOfPanel(item.i)])">查看全部</el-button></div><SectionState :value="section[sectionKeyOfPanel(item.i)]" :loading="loading && !section[sectionKeyOfPanel(item.i)]" @retry="refresh([sectionKeyOfPanel(item.i)])"/><template v-if="section[sectionKeyOfPanel(item.i)]?.status !== 'error'"><button v-for="item2 in data(sectionKeyOfPanel(item.i)).items || []" :key="item2.id || item2.docId" class="item-row" @click="go(item2.target)"><span><b>{{ item2.title }}</b><small>{{ item2.favoriteTime || item2.publishTime || item2.time }}</small></span><el-tag v-if="sectionKeyOfPanel(item.i)==='announcements' && !item2.read" size="small">未读</el-tag></button></template></article>
          </div>
        </div>
      </div>
    </section>
    <p v-if="data('projectRisk').unknownCompletionCount" class="unknown-note">{{ data('projectRisk').unknownCompletionCount }} 个项目的完成状态待核实，未归入正常或到期风险。仅历史明确的完成阶段可判定完成。</p>
    <template v-if="editing">
      <div class="palette">
        <div class="palette-head">备选卡片（点击添加到工作台）</div>
        <div class="palette-list">
          <button v-for="id in paletteIds" :key="id" class="palette-item" :disabled="savingLayout || !wide" @click="addCard(id)"><span class="chip-dot" :data-color="cardDot(id)"></span>{{ CARD_CATALOG[id].title }}<small>{{ CARD_CATALOG[id].kind==='page'?'页面':(CARD_CATALOG[id].kind==='stat'?'统计':'面板') }}</small></button>
          <p v-if="!paletteIds.length" class="muted" style="margin:0">所有可用卡片均已添加</p>
        </div>
      </div>
    </template>
    <footer class="muted">{{ updatedAt ? `最近刷新 ${updatedAt}` : '正在获取工作台数据' }} · 页面可见时每60秒刷新</footer>
  </main>
</template>

<script setup>
import { computed, defineComponent, h, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { onBeforeRouteLeave, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useAuthStore } from '../../store/auth'
import { overview, getDashboardLayout, getLegacyDashboardLayout, saveDashboardLayout } from '../../api/dashboard'
import { sectionRequests, safeTarget, warningNames } from '../../utils/dashboard'
import { normalizeDashboardLayout, orderLayout, placeCard } from '../../utils/dashboardLayout'
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
const layoutEnabled = import.meta.env.VITE_DASHBOARD_LAYOUT_V2 === 'true'
const gridElement = ref(null)
const wide = ref(false)
const layoutReady = ref(false)
const capabilitiesReady = ref(false)
const savedLayout = ref(null)
const editLayout = ref([])
const revision = ref('')
const editing = ref(false)
const savingLayout = ref(false)
const conflict = ref(false)
const layoutStatus = ref('')
const activeGrid = computed(() => editing.value && wide.value)
const visible = (items) => orderLayout(items.filter(item => canShow(item.i)))
const defaults = () => normalizeDashboardLayout(DEFAULT_LAYOUT.filter(item => canShow(item.i)), CARD_CATALOG)
const layoutItems = computed(() => savedLayout.value === null ? defaults() : visible(savedLayout.value))
const displayLayout = computed(() => editing.value ? visible(editLayout.value) : layoutItems.value)
const paletteIds = computed(() => Object.keys(CARD_CATALOG).filter(id => canShow(id) && !editLayout.value.some(item => item.i === id)))
const layoutKeys = computed(() => displayLayout.value.map(item => item.i).sort().join('|'))
let grid
let gridFactory
let resizeObserver
let editStarting = false

function cardTarget(id){const card=CARD_CATALOG[id];return card?.path?{path:card.path}:null}
function sectionKeyOfPanel(id){return {'panel.favorites':'favorites','panel.announcements':'announcements','panel.notifications':'notifications'}[id]}
function cardDot(id){const card=CARD_CATALOG[id];return card.kind==='page'?'primary':(card.kind==='stat'?'warning':'success')}

function syncGrid() {
  if (!activeGrid.value) {
    grid?.destroy()
    grid = undefined
    return
  }
  grid ??= gridFactory(gridElement.value, id => ({ minW: 3, minH: CARD_CATALOG[id].kind === 'panel' ? 4 : 2 }), items => {
    if (savingLayout.value) {
      grid.restore(editLayout.value)
      return
    }
    editLayout.value = items
    layoutStatus.value = ' 布局已调整'
  })
  grid.sync(displayLayout.value)
}
watch(activeGrid, () => {
  grid?.destroy()
  grid = undefined
}, { flush: 'pre' })
watch([activeGrid, layoutKeys], () => {
  try {
    syncGrid()
  } catch {
    cancelEdit()
    ElMessage.error('布局编辑器初始化失败，已返回浏览模式')
  }
}, { flush: 'post' })

async function startEdit() {
  if (editStarting || !wide.value || !layoutReady.value || !capabilitiesReady.value) return
  editStarting = true
  try {
    gridFactory ??= (await import('../../utils/dashboardGrid')).createDashboardGrid
    if (!alive || !wide.value) return
    editLayout.value = layoutItems.value.map(item => ({ ...item }))
    conflict.value = false
    layoutStatus.value = ''
    editing.value = true
  } catch {
    ElMessage.error('布局编辑器加载失败，请重试')
  } finally {
    editStarting = false
  }
}
function addCard(id) {
  const card = CARD_CATALOG[id]
  const placed = placeCard(displayLayout.value, { i: id, w: card.w, h: Math.max(card.h, 2) })
  if (placed) editLayout.value = [...displayLayout.value, placed]
  else ElMessage.warning('画布空间不足，请先移除或缩小卡片')
}
function removeCard(id) {
  if (savingLayout.value || !wide.value) return
  grid?.sync(displayLayout.value.filter(item => item.i !== id))
  editLayout.value = editLayout.value.filter(item => item.i !== id)
}
function resetEdit() {
  if (savingLayout.value || !wide.value) return
  const items = defaults()
  grid?.sync(items)
  editLayout.value = items
  nextTick(() => grid?.restore(items))
}
function cancelEdit() {
  grid?.destroy()
  grid = undefined
  editing.value = false
}
function adjustCard(event, id) {
  const direction = { ArrowLeft: [-1, 0], ArrowRight: [1, 0], ArrowUp: [0, -1], ArrowDown: [0, 1] }[event.key]
  if (!direction || !grid || savingLayout.value) return
  event.preventDefault()
  grid.adjust(id, ...(event.shiftKey ? [0, 0, ...direction] : [...direction, 0, 0]))
}
async function loadLayout() {
  if (!layoutEnabled) {
    const legacy = await getLegacyDashboardLayout()
    const items = JSON.parse(legacy.layout || 'null')
    if (Array.isArray(items) && items.length) savedLayout.value = normalizeDashboardLayout(items, CARD_CATALOG)
    return
  }
  const data = await getDashboardLayout()
  revision.value = data.revision
  savedLayout.value = data.items === null ? null : normalizeDashboardLayout(data.items, CARD_CATALOG)
  layoutReady.value = true
}
async function readInitialLayout() {
  try {
    await loadLayout()
  } catch {
    ElMessage.warning('无法读取布局，暂时显示默认布局')
  }
}
async function refreshWorkbench() {
  await Promise.all([refresh(), layoutEnabled && !layoutReady.value ? readInitialLayout() : Promise.resolve()])
}
async function reloadLayout() {
  try {
    await loadLayout()
    cancelEdit()
    ElMessage.success('已加载最新布局')
  } catch {
    ElMessage.error('读取布局失败，请重试')
  }
}
async function saveLayout() {
  if (savingLayout.value || !wide.value || conflict.value) return
  savingLayout.value = true
  grid?.setEnabled(false)
  const items = displayLayout.value.map(item => ({ ...item }))
  try {
    const result = await saveDashboardLayout(revision.value, items)
    revision.value = result.revision
    savedLayout.value = items
    cancelEdit()
    ElMessage.success('布局已保存')
  } catch (error) {
    conflict.value = error.response?.status === 409 || Number(error.code) === 409
    ElMessage.error(conflict.value ? '布局已在其他窗口更新，请重新加载' : '保存失败，编辑内容已保留')
  } finally {
    savingLayout.value = false
    grid?.setEnabled(true)
  }
}
function dirtyLayout() {
  return editing.value && JSON.stringify(displayLayout.value) !== JSON.stringify(layoutItems.value)
}
function beforeUnload(event) {
  if (dirtyLayout()) {
    event.preventDefault()
    event.returnValue = ''
  }
}
onBeforeRouteLeave(() => !dirtyLayout() || window.confirm('布局尚未保存，确定离开吗？'))

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
  if(id>=capabilityVersion){capabilitiesReady.value=true;capabilities.value=response.capabilities || [];capabilityVersion=id;businessDate.value=response.businessDate;updatedAt.value=new Date(response.generatedAt).toLocaleTimeString('zh-CN',{hour12:false,timeZone:'Asia/Shanghai'});try{sessionStorage.setItem(`dashboard-capabilities:${auth.id}`,JSON.stringify(capabilities.value))}catch(e){}}
 } catch(e) {
  if(alive && e.code!=='ERR_CANCELED') {
   pageError.value=true
   for(const key of selected)if(seq.current(key,id) && (key!=='myApprovals' || auth.hasPermission('approval:audit')))section.value[key]={status:'error',data:null}
  }
 } finally {requests.delete(controller);pending--;if(alive)loading.value=pending>0}
}
function go(target){const value=safeTarget(router,target);if(value)router.push(value)}
function scrollProjects(){(Array.isArray(projectsElement.value) ? projectsElement.value[0] : projectsElement.value)?.scrollIntoView({behavior:'smooth',block:'start'})}
function focused(){if(!document.hidden&&Date.now()-lastFocus>1000){lastFocus=Date.now();refresh()}}
onMounted(async()=>{
 const saved=Number(sessionStorage.getItem(`dashboard-scroll:${auth.id}`) || 0)
 timer=setInterval(()=>{if(!document.hidden)refresh()},60000)
 window.addEventListener('focus',focused);document.addEventListener('visibilitychange',focused)
 resizeObserver = new ResizeObserver(([entry]) => { wide.value = entry.contentRect.width >= 900 })
 if (gridElement.value?.parentElement) resizeObserver.observe(gridElement.value.parentElement)
 window.addEventListener('beforeunload', beforeUnload)
 await Promise.all([refresh(), readInitialLayout()])
 await nextTick()
 if(alive && Number.isFinite(saved)){const main=document.querySelector('.el-main');if(main)main.scrollTop=Math.max(0,saved)}
})
onBeforeUnmount(()=>{
 grid?.destroy();resizeObserver?.disconnect();window.removeEventListener('beforeunload', beforeUnload)
 alive=false;sessionStorage.setItem(`dashboard-scroll:${auth.id}`,String(document.querySelector('.el-main')?.scrollTop || 0))
 for(const request of requests)request.abort()
 clearInterval(timer);window.removeEventListener('focus',focused);document.removeEventListener('visibilitychange',focused)
})
</script>

<style scoped>
.dashboard{max-width:1440px;margin:auto;color:#24334d}.dashboard-head{display:flex;align-items:center;justify-content:space-between;gap:20px;margin-bottom:18px}.eyebrow{font-size:11px;letter-spacing:2px;color:#557194;margin:0 0 8px}h1{font-size:26px;margin:0}h2{font-size:16px;margin:0}h3{font-size:17px;margin:13px 0 10px}.muted{color:#758298;font-size:12px;margin:8px 0}.head-actions{display:flex;align-items:center;gap:12px;font-size:13px;color:#64748b}.quick-links{display:flex;gap:8px;flex-wrap:wrap;margin:16px 0 22px}.quick-links a{padding:7px 12px;color:#4a607c;text-decoration:none;background:white;border:1px solid #e3eaf3;border-radius:6px;font-size:12px}.quick-links span{margin-left:8px;color:#8fa2b9}
.layout-grid{display:grid;grid-template-columns:repeat(12,minmax(0,1fr));grid-auto-rows:56px;margin:16px 0}
.grid-stack{margin:16px 0;min-height:56px;background-image:linear-gradient(to right,#dce5f2 1px,transparent 1px),linear-gradient(to bottom,#dce5f2 1px,transparent 1px);background-size:calc(100% / 12) 56px}
.layout-grid.narrow{display:flex;flex-direction:column}
.layout-grid.narrow .layout-content{min-height:104px}
.layout-grid .cell{padding:4px;box-sizing:border-box;min-width:0}
.layout-content{position:relative;height:100%;display:flex;flex-direction:column;min-width:0}
.grid-stack > .grid-stack-item > .grid-stack-item-content.layout-content{height:auto}
.layout-body{flex:1;min-height:0;overflow:auto}
.layout-toolbar{position:absolute;z-index:1;bottom:2px;left:2px;display:flex;gap:2px;border-radius:6px;background:#eaf2ff}
.layout-toolbar button{background:transparent;border:0;color:#29476a;padding:2px 6px;cursor:pointer}
.layout-toolbar .layout-handle{cursor:grab;text-align:left}
.cell{height:100%;overflow:hidden}
.summary{display:flex;flex-direction:column;align-items:center;justify-content:center;gap:3px;padding:6px 8px;border:1px solid #dce5f2;background:#fff;border-radius:10px;cursor:pointer;color:#334e72;text-align:center;width:100%;height:100%;box-sizing:border-box}.summary span{font-size:12px;line-height:1.25;overflow-wrap:anywhere}.summary strong{font-size:22px;color:#254f91;line-height:1.2}.summary strong i{font-size:16px;font-style:normal;color:#c7d1df}.summary small{color:#8391a4;font-size:10px;line-height:1.2}.summary.danger{background:#fff8f6;border-color:#f4dcd6}.danger strong{color:#bb5446}.summary.caution{background:#fffaf1;border-color:#f0e3c8}.caution strong{color:#a67526}
.page-tile{flex-direction:row;gap:6px;align-items:center;justify-content:center}.page-tile-title{font-size:13px;font-weight:600;color:#254f91}.page-tile-arrow{color:#8fa2b9;font-size:14px}.page-tile:hover{border-color:#a0cfff;background:#f4f9ff}
.panel{background:white;border:1px solid #e3eaf3;border-radius:10px;padding:12px 18px;margin:0;width:100%;height:100%;box-sizing:border-box;overflow-y:auto}.panel-head{display:flex;justify-content:space-between;align-items:center;gap:10px;margin-bottom:8px}.item-row{display:flex;justify-content:space-between;align-items:center;gap:10px;width:100%;padding:12px 0;border:0;border-top:1px solid #f0f3f8;background:white;text-align:left;cursor:pointer;color:#405475}.item-row b{font-size:13px;font-weight:500}.item-row small{display:block;font-size:11px;color:#8593a5;margin-top:5px}.project-row .relation-line{color:#a3b1c2}.project-row b .el-tag{margin-left:6px}.detail-arrow{font-size:12px;color:#8593a5;white-space:nowrap}.unknown-note{font-size:12px;color:#8f692f;background:#fff9ed;padding:10px 14px;border-radius:6px}.section-error{color:#b15d4a;font-size:13px}footer{padding:4px 0 18px}button:focus-visible,a:focus-visible{outline:2px solid #2864d8;outline-offset:3px}
.edit-tip{font-size:12px;color:#758298;background:#f4f8ff;border:1px solid #dce5f2;border-radius:6px;padding:8px 12px;margin:12px 0}
.palette{margin-top:16px;background:white;border:1px solid #e3eaf3;border-radius:10px;padding:14px}.palette-head{font-size:12px;color:#758298;margin-bottom:10px}.palette-list{display:flex;flex-wrap:wrap;gap:8px}.palette-item{display:inline-flex;align-items:center;gap:6px;padding:6px 12px;font-size:12px;color:#334e72;background:#f5f8fd;border:1px solid #dce5f2;border-radius:14px;cursor:pointer}.palette-item:hover{border-color:#a0cfff;background:#ecf5ff}.palette-item small{color:#8fa2b9}
.chip-dot{width:8px;height:8px;border-radius:50%;flex-shrink:0}.chip-dot[data-color='primary']{background:#409eff}.chip-dot[data-color='warning']{background:#e6a23c}.chip-dot[data-color='success']{background:#67c23a}
</style>
