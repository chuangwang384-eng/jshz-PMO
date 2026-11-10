<template>
  <main class="dashboard">
    <header class="dashboard-head"><div><h1>{{ greeting }}，{{ auth.username }}</h1><p class="muted"><span v-if="weekdayLabel">{{ weekdayLabel }} · </span>先处理重要事项，再开始今天的工作。</p></div><div class="head-actions"><template v-if="!editing"><el-button :disabled="!layoutReady || !capabilitiesReady || !wide" @click="startEdit">自定义布局</el-button></template><template v-else><el-button type="primary" :loading="savingLayout" :disabled="!wide || conflict" @click="saveLayout">保存布局</el-button><el-button :disabled="savingLayout || !wide" @click="resetEdit">恢复默认</el-button><el-button :disabled="savingLayout" @click="cancelEdit">取消</el-button></template><el-button :loading="loading" class="refresh-btn" aria-label="刷新工作台" @click="refreshWorkbench"><el-icon><Refresh /></el-icon></el-button></div></header>
    <div class="nav-card"><GlobalSearchBar ref="searchBar" /><QuickAccessBar ref="quickBar" :candidates="quickCandidates" :editing="editing" /></div>
    <el-alert v-if="pageError" title="工作台加载失败，请重试；其他入口仍可使用。" type="error" :closable="false" />
    <p v-if="!wide" class="muted">请使用宽屏编辑布局。</p>
    <p v-if="editing" class="edit-tip">拖动卡片左上角手柄移动位置，拖动卡片右下角调整宽高；聚焦手柄后可用方向键微调，按住 Shift 加方向键改变宽高，按 Esc 取消当前操作。快捷访问的增删与排序也会一并调整，改完点上方「保存布局」生效，「取消」放弃全部改动。<span role="status">{{ layoutStatus }}</span></p>
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
              <article v-else-if="item.i==='panel.myProjects'" id="my-projects" ref="projectsElement" class="panel projects"><div class="panel-head"><div><h2>我的项目</h2><p class="muted">按到期风险排序</p></div><div><el-button link @click="router.push('/project')">项目列表</el-button><el-button link @click="refresh(['myProjects','projectRisk'])">刷新</el-button></div></div><SectionState :value="section.myProjects" :loading="loading && !section.myProjects" @retry="refresh(['myProjects','projectRisk'])"/><template v-if="section.myProjects?.status === 'success'"><button v-for="item2 in data('myProjects').items" :key="item2.id" class="item-row project-row" @click="go(item2.target)"><span><b>{{ item2.name }} <el-tag v-if="warningNames[item2.warning]" :type="item2.warning==='OVERDUE'?'danger':'warning'" size="small">{{ warningNames[item2.warning] }}</el-tag></b><small>{{ item2.teamLabel || '团队未设置' }} · {{ item2.category === 'ONLINE' ? '线上' : '线下' }} · {{ item2.cityName || '城市未设置' }} · {{ item2.phaseName || '阶段未设置' }} · 负责人 {{ item2.ownerName || '-' }}<template v-if="meetingRange(item2)"> · 会议 {{ meetingRange(item2) }}</template> · 结束 {{ item2.endDate || '未设置' }}</small><small v-if="item2.myRelations?.length" class="relation-line">我的角色：{{ item2.myRelations.join(' / ') }}</small></span><span class="detail-arrow">详情 →</span></button></template></article>
              <!-- 面板：收藏/公告/通知 -->
              <article v-else-if="item.i==='panel.favorites' || item.i==='panel.announcements' || item.i==='panel.notifications'" class="panel"><div class="panel-head"><h2>{{ CARD_CATALOG[item.i].title }} <small v-if="item.i==='panel.notifications'">{{ data(sectionKeyOfPanel(item.i)).unreadCount ?? '' }}</small></h2><el-button link @click="go(allTargets[sectionKeyOfPanel(item.i)])">查看全部</el-button></div><SectionState :value="section[sectionKeyOfPanel(item.i)]" :loading="loading && !section[sectionKeyOfPanel(item.i)]" @retry="refresh([sectionKeyOfPanel(item.i)])"/><template v-if="section[sectionKeyOfPanel(item.i)]?.status !== 'error'"><button v-for="item2 in data(sectionKeyOfPanel(item.i)).items || []" :key="item2.id || item2.docId" class="item-row" @click="go(item2.target)"><span><b>{{ item2.title }}</b><small>{{ item2.favoriteTime || item2.publishTime || item2.time }}</small></span><el-tag v-if="sectionKeyOfPanel(item.i)==='announcements' && !item2.read" size="small">未读</el-tag></button></template></article>
          </div>
        </div>
      </div>
    </section>
    <!-- 2026-11-09：项目流程重塑后不再有「历史完成阶段」判定，原「项目状态待核实」提示条已移除 -->
    <template v-if="editing">
      <div class="palette">
        <div class="palette-head">备选卡片（点击添加到工作台）</div>
        <div class="palette-list">
          <button v-for="id in paletteIds" :key="id" class="palette-item" :disabled="savingLayout || !wide" @click="addCard(id)"><span class="chip-dot" :data-color="cardDot(id)"></span>{{ CARD_CATALOG[id].title }}<small>{{ CARD_CATALOG[id].kind==='page'?'页面':(CARD_CATALOG[id].kind==='stat'?'统计':'面板') }}</small></button>
          <p v-if="!paletteIds.length" class="muted" style="margin:0">所有可用卡片均已添加</p>
        </div>
      </div>
    </template>
    <footer class="status-bar">
      <span v-if="loading" class="status-main">正在更新数据…</span>
      <span v-else-if="refreshFailed" role="button" tabindex="0" class="status-main status-action status-fail" @click="refreshWorkbench" @keydown.enter.prevent="refreshWorkbench">更新失败 · 点击重试</span>
      <span v-else-if="dataStale" role="button" tabindex="0" class="status-main status-action status-stale" @click="refreshWorkbench" @keydown.enter.prevent="refreshWorkbench">数据可能不是最新，请刷新</span>
      <span v-else class="status-main">已更新 {{ updatedAt || '--:--:--' }} · 每60秒自动刷新</span>
      <span v-if="!loading" class="status-right" :class="refreshFailed ? 'is-fail' : dataStale ? 'is-stale' : 'is-ok'">
        <el-icon aria-hidden="true"><component :is="refreshFailed ? CircleCloseFilled : dataStale ? WarningFilled : CircleCheckFilled" /></el-icon>
        {{ refreshFailed ? '数据未同步' : dataStale ? '数据可能过期' : '数据已同步' }}
      </span>
    </footer>
  </main>
</template>

<script setup>
import { computed, defineComponent, h, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { onBeforeRouteLeave, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useAuthStore } from '../../store/auth'
import { overview, getDashboardLayout, saveDashboardLayout } from '../../api/dashboard'
import { sectionRequests, safeTarget, warningNames } from '../../utils/dashboard'
import { normalizeDashboardLayout, orderLayout, placeCard } from '../../utils/dashboardLayout'
import GlobalSearchBar from '../../components/GlobalSearchBar.vue'
import QuickAccessBar from '../../components/QuickAccessBar.vue'
import { Refresh, WarningFilled, CircleCheckFilled, CircleCloseFilled } from '@element-plus/icons-vue'
const router=useRouter(),auth=useAuthStore(),section=ref({}),loading=ref(false),pageError=ref(false),businessDate=ref(''),updatedAt=ref(''),projectsElement=ref(null),searchBar=ref(null),quickBar=ref(null),seq=sectionRequests()
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
 'page.complianceAssociation':{title:'协会合规要求',w:3,h:1,kind:'page',path:'/knowledge-planet/association-compliance',cap:'knowledge'},
 'page.complianceEnterprise':{title:'企业合规要求',w:3,h:1,kind:'page',path:'/knowledge-planet/enterprise-compliance',cap:'knowledge'},
 'page.profileHome':{title:'我的主页',w:3,h:1,kind:'page',path:'/profile/overview'},
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
// 2026-11-09：布局编辑为工作台既有能力，改为固定启用；原先 VITE_DASHBOARD_LAYOUT_V2 开关
// 漏带即整块功能消失（见 1.0.34.1.1 editfix 事故），legacy layout 回退由后端 /layout/v2 统一处理。
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
function meetingRange(p){const a=p?.meetingStartDate,b=p?.meetingEndDate;if(!a&&!b)return '';if(a&&b&&a!==b)return `${a} ~ ${b}`;return a||b}
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
  quickBar.value?.resetDraft()
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
  await Promise.all([refresh(), layoutReady.value ? Promise.resolve() : readInitialLayout()])
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
    const quickSaved = await quickBar.value?.saveDraft()
    // 空快捷访问草稿会返回 false：整单中止，保留编辑态，不误报「布局与快捷访问已保存」
    if (quickSaved === false) return
    const result = await saveDashboardLayout(revision.value, items)
    revision.value = result.revision
    savedLayout.value = items
    cancelEdit()
    ElMessage.success('布局与快捷访问已保存')
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

const refreshFailed=ref(false),lastRefreshAt=ref(0),nowTick=ref(Date.now())
// 状态栏：刷新中(loading) > 失败(refreshFailed) > 过期(距上次成功超2个周期) > 正常；页面回前台已有自动补刷逻辑
const dataStale=computed(()=>lastRefreshAt.value>0 && nowTick.value-lastRefreshAt.value>120000)
const WEEK=['日','一','二','三','四','五','六']
const weekdayLabel=computed(()=>{const m=/^(\d{4})-(\d{2})-(\d{2})$/.exec(businessDate.value||'');if(!m)return '';const d=new Date(+m[1],+m[2]-1,+m[3]);return `${+m[2]}月${+m[3]}日，星期${WEEK[d.getDay()]}`})
// 快捷访问候选：page.* 卡片按原固定顺序排列，经 canShow 过滤（含 capabilities 与权限）
const QUICK_ACCESS_ORDER=['page.knowledge','page.project','page.experts','page.customer','page.supplier','page.myApplications','page.announcements','page.directory','page.notifications','page.approvalManage','page.complianceAssociation','page.complianceEnterprise','page.profileHome']
const quickCandidates=computed(()=>QUICK_ACCESS_ORDER.filter(canShow).map(id=>({id,title:CARD_CATALOG[id].title,path:CARD_CATALOG[id].path})))
const allTargets={favorites:{path:'/knowledge-planet/meeting-execution',query:{favorites:'1'}},announcements:{path:'/profile/announcements'},notifications:{path:'/profile/notifications'}}
const data=key=>section.value[key]?.data || {}
const count=(key,field)=>section.value[key]?.status==='error'?'暂不可用':data(key)[field] ?? '—'
const pendingItems=key=>key==='returned'?data('myApplications').returnedItems || []:key==='myApplications'?data(key).latestItems || []:data(key).items || []
const SectionState=defineComponent({props:['value','loading'],emits:['retry'],setup(p,{emit}){return()=>p.loading?h('p',{class:'muted'},'正在加载…'):p.value?.status==='error'?h('p',{class:'section-error'},['加载失败，数据暂不可用。',h('button',{onClick:()=>emit('retry')},'重试')]):p.value?.status==='empty'?h('p',{class:'muted'},'暂无相关事项'):null}})
const allSections=['myApplications','myApprovals','myProjects','projectRisk','favorites','announcements','notifications']
const requests=new Set()
let timer,tickTimer,lastFocus=0,alive=true,pending=0,capabilityVersion=0
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
   refreshFailed.value=false;lastRefreshAt.value=Date.now()
 } catch(e) {
  if(alive && e.code!=='ERR_CANCELED') {
   pageError.value=true;refreshFailed.value=true
   for(const key of selected)if(seq.current(key,id) && (key!=='myApprovals' || auth.hasPermission('approval:audit')))section.value[key]={status:'error',data:null}
  }
 } finally {requests.delete(controller);pending--;if(alive)loading.value=pending>0}
}
function go(target){const value=safeTarget(router,target);if(value)router.push(value)}
function scrollProjects(){(Array.isArray(projectsElement.value) ? projectsElement.value[0] : projectsElement.value)?.scrollIntoView({behavior:'smooth',block:'start'})}
function focused(){if(!document.hidden&&Date.now()-lastFocus>1000){lastFocus=Date.now();refresh()}}
// Ctrl K / ⌘K 仅工作台页生效：聚焦全局搜索框（输入法组合态忽略）
function commandKey(event){if((event.ctrlKey||event.metaKey)&&(event.key==='k'||event.key==='K')&&!event.isComposing){event.preventDefault();searchBar.value?.focus()}}
onMounted(async()=>{
 const saved=Number(sessionStorage.getItem(`dashboard-scroll:${auth.id}`) || 0)
 timer=setInterval(()=>{if(!document.hidden)refresh()},60000)
 tickTimer=setInterval(()=>{nowTick.value=Date.now()},15000)
 window.addEventListener('focus',focused);document.addEventListener('visibilitychange',focused)
 document.addEventListener('keydown',commandKey)
 resizeObserver = new ResizeObserver(([entry]) => { wide.value = entry.contentRect.width >= 900 })
 if (gridElement.value?.parentElement) resizeObserver.observe(gridElement.value.parentElement)
 window.addEventListener('beforeunload', beforeUnload)
 await Promise.all([refresh(), readInitialLayout()])
 await nextTick()
 if(alive && Number.isFinite(saved)){const main=document.querySelector('.el-main');if(main)main.scrollTop=Math.max(0,saved)}
})
onBeforeUnmount(()=>{
 grid?.destroy();resizeObserver?.disconnect();window.removeEventListener('beforeunload', beforeUnload)
 document.removeEventListener('keydown',commandKey)
 alive=false;sessionStorage.setItem(`dashboard-scroll:${auth.id}`,String(document.querySelector('.el-main')?.scrollTop || 0))
 for(const request of requests)request.abort()
 clearInterval(timer);clearInterval(tickTimer);window.removeEventListener('focus',focused);document.removeEventListener('visibilitychange',focused)
})
</script>

<style scoped>
.dashboard{max-width:1440px;margin:auto;color:var(--c-text,#24334d);--c-primary:#2563EB;--c-page-bg:#F8FAFC;--c-card-bg:#FFFFFF;--c-text:#0F172A;--c-text-sub:#64748B;--c-border:#E2E8F0;--r-card:12px;--r-btn:8px;--pad-page:32px;--shadow-card:0 1px 2px rgba(15,23,42,.06)}.dashboard-head{display:flex;align-items:center;justify-content:space-between;gap:20px;margin-bottom:14px}h1{font-size:20px;margin:0}h2{font-size:16px;margin:0}h3{font-size:17px;margin:13px 0 10px}.muted{color:var(--c-text-sub,#758298);font-size:12px;margin:6px 0 0}.head-actions{display:flex;align-items:center;gap:10px;font-size:13px;color:var(--c-text-sub,#64748b)}.head-actions .refresh-btn{padding:8px}.refresh-btn .el-icon{font-size:15px}.nav-card{background:var(--c-card-bg);border:1px solid var(--c-border);border-radius:var(--r-card);box-shadow:var(--shadow-card);padding:14px 18px;margin:0 0 16px}
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
.panel{background:var(--c-card-bg,#fff);border:1px solid var(--c-border,#e3eaf3);border-radius:var(--r-card,10px);padding:12px 18px;margin:0;width:100%;height:100%;box-sizing:border-box;overflow-y:auto}.panel-head{display:flex;justify-content:space-between;align-items:center;gap:10px;margin-bottom:8px}.item-row{display:flex;justify-content:space-between;align-items:center;gap:10px;width:100%;padding:12px 0;border:0;border-top:1px solid #f0f3f8;background:white;text-align:left;cursor:pointer;color:#405475}.item-row b{font-size:13px;font-weight:500}.item-row small{display:block;font-size:11px;color:#8593a5;margin-top:5px}.project-row .relation-line{color:#a3b1c2}.project-row b .el-tag{margin-left:6px}.detail-arrow{font-size:12px;color:#8593a5;white-space:nowrap}.risk-banner{display:flex;align-items:center;gap:12px;background:#fffbeb;border:1px solid #fde68a;border-radius:10px;padding:12px 16px;margin:0 0 16px}.risk-icon{color:#d97706;font-size:20px;flex-shrink:0}.risk-body{flex:1;min-width:0}.risk-title{font-size:13px;font-weight:600;color:var(--c-text,#0f172a);display:flex;align-items:center;gap:8px}.risk-info{color:var(--c-text-sub,#64748b);cursor:help;font-size:14px}.risk-desc{margin:4px 0 0;font-size:12px;color:var(--c-text-sub,#64748b)}.risk-link{border:1px solid var(--c-border,#e2e8f0);background:var(--c-card-bg,#fff);border-radius:8px;padding:7px 14px;font-size:12px;color:var(--c-primary,#2563eb);cursor:pointer;white-space:nowrap}.risk-link:hover{border-color:var(--c-primary,#2563eb);background:#eff6ff}.section-error{color:#b15d4a;font-size:13px}.status-bar{display:flex;align-items:center;justify-content:space-between;gap:12px;padding:6px 0 18px;font-size:12px;color:var(--c-text-sub,#64748b)}.status-main.status-action{cursor:pointer}.status-main.status-action:hover{color:var(--c-primary,#2563eb)}.status-main.status-fail{color:#dc2626}.status-main.status-stale{color:#d97706}.status-right{display:inline-flex;align-items:center;gap:4px}.status-right .el-icon{font-size:14px}.status-right.is-ok{color:#16a34a}.status-right.is-stale{color:#d97706}.status-right.is-fail{color:#dc2626}button:focus-visible,a:focus-visible{outline:2px solid #2864d8;outline-offset:3px}
.edit-tip{font-size:12px;color:#758298;background:#f4f8ff;border:1px solid #dce5f2;border-radius:6px;padding:8px 12px;margin:12px 0}
.palette{margin-top:16px;background:white;border:1px solid #e3eaf3;border-radius:10px;padding:14px}.palette-head{font-size:12px;color:#758298;margin-bottom:10px}.palette-list{display:flex;flex-wrap:wrap;gap:8px}.palette-item{display:inline-flex;align-items:center;gap:6px;padding:6px 12px;font-size:12px;color:#334e72;background:#f5f8fd;border:1px solid #dce5f2;border-radius:14px;cursor:pointer}.palette-item:hover{border-color:#a0cfff;background:#ecf5ff}.palette-item small{color:#8fa2b9}
.chip-dot{width:8px;height:8px;border-radius:50%;flex-shrink:0}.chip-dot[data-color='primary']{background:#409eff}.chip-dot[data-color='warning']{background:#e6a23c}.chip-dot[data-color='success']{background:#67c23a}
</style>
