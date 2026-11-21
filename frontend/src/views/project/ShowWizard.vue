<template>
  <main class="show-wizard">
    <header class="wiz-top">
      <span class="wiz-back" role="link" tabindex="0" @click="goProject" @keydown.enter="goProject">← 返回项目</span>
      <h1>串场演示向导</h1>
      <div class="wiz-steps">
        <div v-for="(name, i) in stepNames" :key="name" class="wiz-step"
          :class="{ active: step === i + 1, done: step > i + 1 }" @click="step = i + 1">
          <i>{{ i + 1 }}</i>{{ name }}
        </div>
      </div>
    </header>

    <!-- 第一步：模板 + 封面定制 -->
    <section v-if="step === 1" class="wiz-body">
      <p class="wiz-desc">第一步：选择模板并定制封面。封面文字与全局字号在此确定，后续随时可回改。</p>
      <div class="tpl-grid">
        <button v-for="t in templates" :key="t.id" type="button" class="tpl-card"
          :class="{ picked: template === t.id }" @click="template = t.id">
          <span class="tpl-swatch" :style="{ backgroundImage: t.preview }"></span>
          <strong>{{ t.name }}</strong>
          <span class="tpl-note">{{ t.desc }}</span>
        </button>
      </div>
      <div class="dark-card cover-card">
        <p class="block-title">封面定制</p>
        <div class="cover-fields">
          <label class="field">机构 / 主办方<el-input v-model="cover.org" maxlength="80" placeholder="如：中国医药教育协会" /></label>
          <label class="field field-grow">主标题<el-input v-model="cover.title" maxlength="60" placeholder="如：聚例辉煌" /></label>
          <label class="field field-grow">副标题<el-input v-model="cover.sub" maxlength="120" placeholder="如：临床应用研讨 · 多学科病例讨论项目" /></label>
        </div>
        <div class="cover-fields">
          <label class="field">会议时间<el-input v-model="cover.time" maxlength="60" placeholder="如：2026年11月18日（周三）" /></label>
          <label class="field">会议地点<el-input v-model="cover.place" maxlength="60" placeholder="如：郑州 · 会场一" /></label>
          <label class="field field-grow">大会主席<el-input v-model="cover.chair" maxlength="80" placeholder="如：齐秀恒 教授" /></label>
        </div>
        <div class="cover-fields">
          <label class="field">全局字号
            <el-select v-model="fontScale" style="width: 200px">
              <el-option v-for="f in fontScales" :key="f.value" :label="f.label" :value="f.value" />
            </el-select>
          </label>
          <p class="field-hint">主标题超长时自动缩小；时间/地点/主席留空的行不会出现在封面上。</p>
        </div>
      </div>
      <div class="wiz-actions"><el-button type="primary" :disabled="!experts.length" @click="step = 2">下一步：专家信息</el-button></div>
    </section>

    <!-- 第二步：专家信息（全局资料池，去重后一次编辑） -->
    <section v-else-if="step === 2" class="wiz-body">
      <p class="wiz-desc">
        已从日程提取 {{ experts.length }} 位参会专家（重复出现只保留一份，两字姓名自动去除排版空格）。
        这里的资料是全局的：头像、姓名、单位、职称、简介改一处，所有环节与页面自动联动。
      </p>
      <div v-for="p in experts" :key="p.key" class="dark-card person-card">
        <div class="person-head">
          <strong>{{ p.name || '未命名专家' }}</strong>
          <span class="person-role">{{ roleSummary(p) }}</span>
          <el-button size="small" :loading="p.loading" :disabled="!p.expertId || p.loaded" @click="loadPerson(p)">
            {{ p.loaded ? '已载入' : '从专家库载入' }}
          </el-button>
          <el-button size="small" type="primary" :disabled="busy || !editable" @click="saveDraft('专家信息已保存')">保存</el-button>
        </div>
        <CropAvatar v-if="p.photoSrc" v-model="p.avatar" :src="p.photoSrc" />
        <p v-else class="person-hint">{{ p.expertId ? '点击「从专家库载入」获取证件照与简介，系统将自动识别人脸预抠图' : '该专家未关联专家库，将仅生成文字页' }}</p>
        <div class="person-fields">
          <label class="field">姓名<el-input v-model="p.name" maxlength="60" style="width: 140px" /></label>
          <label class="field">职称<el-input v-model="p.title" maxlength="60" style="width: 140px" /></label>
          <label class="field field-grow">单位<el-input v-model="p.unit" maxlength="120" /></label>
        </div>
        <label class="field field-grow">个人简介（按句/分号自动分段，每条一菱形项；可手动改写）
          <el-input v-model="p.intro" type="textarea" :rows="5" maxlength="1500" placeholder="自动载入专家库简介，可修改" />
        </label>
        <div v-if="p.intro" class="resume-preview">
          <span v-for="(seg, si) in splitResumeItems(p.intro)" :key="si" class="resume-chip">{{ seg }}</span>
          <span v-if="!splitResumeItems(p.intro).length" class="person-hint">内容过短，暂不生成简历页</span>
        </div>
      </div>
      <div class="wiz-actions"><el-button type="primary" @click="step = 3">下一步：日程与角色</el-button></div>
    </section>

    <!-- 第三步：日程与角色（按议题段落分组，直接调用第二步维护好的专家资料） -->
    <section v-else-if="step === 3" class="wiz-body">
      <p class="wiz-desc">
        日程已按议题段落分组。稿件页序：段内先「大会主持」的过场页与简介页，再「大会讲者」，讨论嘉宾按相同时间段同页过场；
        角色沿用日程解析结果（可在下拉改为四种角色之一），资料自动沿用第二步。
      </p>
      <div v-for="g in groups" :key="g.title" class="dark-card seg-group-card">
        <p class="seg-group">{{ g.title }}<span v-if="g.time" class="seg-time">（{{ g.time }}）</span></p>
        <div class="seg-people">
          <div v-for="p in g.members" :key="p.key" class="seg-person">
            <span class="seg-person-name">{{ p.name || '?' }}</span>
            <el-select v-model="p.roleByGroup[g.index]" size="small" style="width: 130px"
              :aria-label="`${p.name} 在该段落的角色`">
              <el-option v-for="r in roleOptions" :key="r" :label="r" :value="r" />
            </el-select>
          </div>
        </div>
      </div>
      <p v-if="!groups.length" class="person-hint">日程中暂无已确认的专家记录，请回到日程阶段完成解析与确认</p>
      <div class="wiz-actions"><el-button type="primary" @click="step = 4">下一步：预览演示</el-button></div>
    </section>

    <!-- 第四步：内嵌预览 + 所见即所得编辑 + 页面增删/排序 + 保存/归档 -->
    <section v-else class="wiz-body">
      <p class="wiz-desc">
        已生成 {{ currentDeckSlides.length }} 页：封面 + {{ pageCount('section') }} 个过场 +
        {{ pageCount('resume') }} 位专家详情页 + 致谢（无专家环节可在下方手动添加文字页）。
        预览内按 <b>E</b> 或点「编辑模式」直接修改页面文字；过场页头像顺序用下方工具条调整。
      </p>
      <div class="wiz-actions">
        <el-button :disabled="busy || !editable" @click="saveDraft('草稿已保存')">保存草稿</el-button>
        <el-button :disabled="busy" @click="present">新标签演示</el-button>
        <el-button type="primary" :disabled="busy || !editable" @click="archive">归档 HTML</el-button>
      </div>
      <div class="dark-card preview-shell">
        <div class="preview-bar">
          <span class="preview-title">演示预览{{ editing ? ' · 编辑模式中（直接点击文字修改）' : ' · 按 E 进入编辑' }}</span>
          <el-button size="small" @click="toggleEdit">{{ editing ? '退出编辑' : '编辑模式' }}</el-button>
          <el-button size="small" type="primary" :disabled="busy || !editable" @click="saveEdits">保存修改</el-button>
        </div>
        <div v-if="editing && moveBar.length > 1" class="move-bar">
          <span class="move-label">头像顺序：</span>
          <template v-for="(p, j) in moveBar" :key="j">
            <span class="move-chip">{{ p.name || '空槽' }}</span>
            <el-button size="small" circle :disabled="j === 0" @click="movePerson(j, -1)">◀</el-button>
            <el-button size="small" circle :disabled="j === moveBar.length - 1" @click="movePerson(j, 1)">▶</el-button>
          </template>
        </div>
        <iframe ref="previewFrame" class="preview-iframe" title="串场演示预览"></iframe>
      </div>
      <details class="slide-outline-wrap" open>
        <summary>页面清单（{{ currentDeckSlides.length }} 页，可删除/上移/下移/添加文字页）</summary>
        <div class="add-page-row">
          <el-select v-model="addSegIdx" placeholder="选择要生成文字页的环节" style="width: 340px" size="small">
            <el-option v-for="(item, i) in textPageSources" :key="i" :label="`${item.label} · ${item.title || item.row}`" :value="i" />
          </el-select>
          <el-button size="small" :disabled="addSegIdx === null" @click="addTextPage">添加文字页（不放头像，仅文字）</el-button>
        </div>
        <ol class="slide-outline">
          <li v-for="(s, i) in currentDeckSlides" :key="i">
            <span class="slide-kind">{{ kindLabel(s.kind) }}</span>{{ s.title || s.label }}
            <em v-if="slideHasPhoto(s)"> · 含头像</em>
            <el-button link size="small" class="slide-op" :disabled="i === 0" @click="moveSlide(i, -1)">▲</el-button>
            <el-button link size="small" class="slide-op" :disabled="i === currentDeckSlides.length - 1" @click="moveSlide(i, 1)">▼</el-button>
            <el-button v-if="i !== 0 && i !== currentDeckSlides.length - 1" link type="danger" size="small"
              class="slide-op" @click="removeSlide(i)">删除</el-button>
          </li>
        </ol>
      </details>
    </section>

    <footer class="wiz-foot">
      <el-button v-if="step > 1" :disabled="busy" @click="step -= 1">上一步</el-button>
      <el-button v-if="step < 4" type="primary" :disabled="busy || (step === 1 && !experts.length)" @click="step += 1">
        下一步
      </el-button>
    </footer>
  </main>
</template>

<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import CropAvatar from '../../components/CropAvatar.vue'
import { fetchPhotoBlob, getExpert } from '../../api/expert'
import { getProjectDetail, getScheduleOverview, getShowAgenda, previewShow, publishShow, saveStageForm } from '../../api/project'
import {
  ROLE_OPTIONS, classifyRow, dedupePhotos, normalizeName, roleLabel, splitResumeItems
} from '../../utils/showDeck'

// 四步向导（2026-11-21 v4 结构引擎）：①模板+封面 ②专家信息（全局资料池）③日程与角色（按议题段落，
// 直接调用②资料，角色沿用日程解析四值）④内嵌预览所见即所得编辑+页面增删排序。
// 稿件结构：封面 → 段内先「大会主持」过场页+简介页 → 「大会讲者」过场页+简介页 → 讨论嘉宾按相同时间段
// 同页过场+各简介页 → … → 致谢；照片全稿池化（ph: 引用）。
const templates = [
  { id: 'gold', name: '红金盛典', desc: '深红金流光，适合庆典与开幕', preview: 'radial-gradient(120% 100% at 50% 40%, #7e1414, #3c0707)' },
  { id: 'tech', name: '深蓝科技', desc: '深蓝网格光束，学术论坛', preview: 'linear-gradient(135deg, #0e3153, #145371)' },
  { id: 'paper', name: '商务简白', desc: '白底深字，内部会议汇报', preview: 'linear-gradient(180deg, #f7f8fb, #e6eaf2)' },
  { id: 'bamboo', name: '青竹学术', desc: '青绿学术风，临床交流', preview: 'radial-gradient(120% 100% at 50% 40%, #f2f9f4, #d3e7db)' }
]
const fontScales = [
  { value: 1, label: '标准' },
  { value: 1.15, label: '大' },
  { value: 1.3, label: '特大' }
]
const roleOptions = ROLE_OPTIONS
const stepNames = ['模板与封面', '专家信息', '日程与角色', '预览演示']

const route = useRoute()
const router = useRouter()
const id = route.params.id
const step = ref(1)
const template = ref('gold')
const fontScale = ref(1)
const editable = ref(false)
const phaseId = ref(null)
const project = ref({})
const cover = ref({ org: '', title: '', sub: '', time: '', place: '', chair: '' })
const experts = ref([])        // 专家资料池（全局唯一，第二步编辑）
const groups = ref([])         // 议题段落分组（第三步角色确认与第四步编排）
const textPageSources = ref([]) // 可手动添加文字页的环节（含无专家环节）
const busy = ref(false)
const editing = ref(false)
const editableDeck = ref(null)
const previewFrame = ref(null)
const previewUrl = ref('')
const editPageIdx = ref(-1)
const moveBar = ref([])
const addSegIdx = ref(null)

function expertKey(name, expertId) {
  return expertId || `name:${normalizeName(name || '')}`
}

// 结构化专家：解析四角色（CHAIR/HOST/SPEAKER/DISCUSSANT）→ 中文；两字姓名去排版空格
function buildSpeakers(overview) {
  const raw = overview?.speakers || []
  const pool = new Map()
  return raw.map(sp => {
    const name = normalizeName(sp.expertName || sp.name || '')
    const key = expertKey(name, sp.expertId || '')
    if (!pool.has(key)) {
      pool.set(key, {
        key, expertId: sp.expertId || '', name, unit: sp.expertUnit || sp.unit || '',
        title: sp.expertTitle || sp.title || '', intro: '', photoSrc: '', avatar: '',
        loading: false, loaded: false, roleByGroup: {}, roleEnum: sp.role || ''
      })
    }
    return pool.get(key)
  })
}

onMounted(async () => {
  const detail = await getProjectDetail(id)
  project.value = detail
  const phase = detail.phases?.find(item => item.name === '串场制作')
  editable.value = Boolean(detail.canOperate && phase && String(detail.phaseId) === String(phase.id))
  phaseId.value = phase?.id || null
  const [agenda, overview] = await Promise.all([getShowAgenda(id), getScheduleOverview(id)])
  experts.value = buildSpeakers(overview)
  // 议题段落分组（保持日程顺序）；segmentTitle 缺失归入「未分组环节」。
  // 段级时间挂 segTimeByGroup（同一专家多段时各段时间独立）。
  const gmap = []
  const rawSpeakers = overview?.speakers || []
  for (const spRaw of rawSpeakers) {
    const name = normalizeName(spRaw.expertName || spRaw.name || '')
    const person = experts.value.find(x => x.key === expertKey(name, spRaw.expertId || ''))
    if (!person) continue
    const title = spRaw.segmentTitle || '未分组环节'
    let g = gmap.find(x => x.title === title)
    if (!g) {
      g = { title, members: [], times: [] }
      gmap.push(g)
    }
    g.members.push(person)
    if (spRaw.segmentTime && !g.times.includes(spRaw.segmentTime)) g.times.push(spRaw.segmentTime)
    person.segTimeByGroup = person.segTimeByGroup || {}
    person.segTimeByGroup[gmap.indexOf(g)] = spRaw.segmentTime || ''
    if (!person.roleByGroup) person.roleByGroup = {}
  }
  groups.value = gmap.map((g, index) => {
    for (const m of g.members) {
      if (m.roleByGroup[index] === undefined) m.roleByGroup[index] = roleLabel(m.roleEnum, g.title, '')
    }
    return { ...g, index }
  })
  // 手动文字页候选：日程行（含无专家环节）
  const { rows } = agendaRows(agenda, overview)
  textPageSources.value = rows.slice(0, 150).map(row => {
    const { label, title } = classifyRow(row)
    return { label, title, row }
  })
  initCover()
})

// 专家在全部段落中的角色汇总（第二步展示）
function roleSummary(p) {
  const src = p.roleByGroup ? Object.values(p.roleByGroup) : []
  const roles = src.filter((v, i, a) => a.indexOf(v) === i)
  return roles.join(' / ') || '—'
}

// 封面默认值：项目名/日期/城市 + 第一位大会主席专家
function initCover() {
  const detail = project.value
  cover.value.title = detail.name || ''
  cover.value.time = detail.meetingStartDate || ''
  cover.value.place = detail.cityName || ''
  const chairPerson = experts.value.find(p => roleSummary(p).includes('大会主席'))
  cover.value.chair = chairPerson ? [chairPerson.name, chairPerson.title].filter(Boolean).join(' ') : ''
}

// 专家载入（池内共享对象，loaded 标志保证只载一次）
watch(experts, list => {
  const safeList = list || []
  safeList.forEach(p => { if (p.expertId && !p.loaded && !p.loading) loadPerson(p) })
}, { immediate: true })

async function loadPerson(p) {
  if (!p.expertId || p.loaded || p.loading) return
  p.loading = true
  try {
    const expert = await getExpert(p.expertId)
    if (expert?.照片) {
      const { data } = await fetchPhotoBlob(expert.照片)
      if (['image/png', 'image/jpeg', 'image/webp'].includes(data.type) && data.size <= 2 * 1024 * 1024) {
        p.photoSrc = await new Promise(resolve => {
          const reader = new FileReader()
          reader.onload = () => resolve(reader.result)
          reader.onerror = () => resolve('')
          reader.readAsDataURL(data)
        })
      }
    }
    if (!p.intro) p.intro = expert?.原始简介 || expert?.简介 || ''
    p.loaded = true
  } catch {
    // 专家库读取失败不阻塞向导，字段可手填
  } finally {
    p.loading = false
  }
}

// 稿件编排（v4）：段内先主持过场+简介 → 讲者过场+简介 → 讨论嘉宾按相同时间段同页过场+各简介；
// 大会主席段（全 CHAIR）自然落在日程最前的段。每个过场页统一编号。
const builtSlides = computed(() => {
  const slides = [coverSlideLike()]
  let secNo = 0
  const pushSection = (title, persons) => {
    secNo += 1
    slides.push({
      label: '', title: title || '', body: '', image: '', kind: 'section',
      items: [String(secNo).padStart(2, '0')],
      persons: (persons || []).slice(0, 20).map(p => ({
        role: p.role || '', name: p.name || '', title: p.title || '', unit: p.unit || '', image: p.avatar || ''
      })),
      fontSize: null
    })
  }
  const pushResume = p => {
    slides.push({
      label: '个人简历', title: [p.name, p.title].filter(Boolean).join(' ') || '专家简介',
      body: '', image: '', kind: 'resume',
      items: splitResumeItems(p.intro || ''), fontSize: null,
      persons: [{ role: p.role || '', name: p.name || '', title: p.title || '', unit: p.unit || '', image: p.avatar || '' }]
    })
  }
  for (const g of groups.value) {
    const memberWithRole = m => ({ ...m, role: m.roleByGroup?.[g.index] || roleLabel(m.roleEnum, g.title, '') })
    const byRole = role => g.members.map(memberWithRole).filter(m => m.role === role)
    const chairs = byRole('大会主席')
    const hosts = byRole('大会主持')
    const speakersL = byRole('大会讲者')
    const discussants = byRole('讨论嘉宾')
    if (chairs.length) {
      pushSection(g.title, chairs)
      chairs.forEach(pushResume)
    }
    if (hosts.length) {
      pushSection(g.title, hosts)
      hosts.forEach(pushResume)
    }
    if (speakersL.length) {
      pushSection(g.title, speakersL)
      speakersL.forEach(pushResume)
    }
    // 讨论嘉宾按相同时间段同页过场（时间段来自该段解析时间，保持出现顺序）
    const dg = []
    for (const m of discussants) {
      const t = m.segTimeByGroup?.[g.index] || ''
      let bucket = dg.find(x => x.time === t)
      if (!bucket) { bucket = { time: t, members: [] }; dg.push(bucket) }
      bucket.members.push(m)
    }
    for (const bucket of dg) {
      if (bucket.members.length) {
        pushSection(g.title, bucket.members)
        bucket.members.forEach(pushResume)
      }
    }
  }
  slides.push(thanksSlideLike())
  return slides
})

function coverSlideLike() {
  return {
    label: cover.value.org || '', title: cover.value.title || project.value.name || '',
    body: cover.value.sub || '', image: '', kind: 'cover', fontSize: null,
    items: [
      cover.value.time ? `会议时间|${cover.value.time}` : '',
      cover.value.place ? `会议地点|${cover.value.place}` : '',
      cover.value.chair ? `大会主席|${cover.value.chair}` : ''
    ].filter(Boolean),
    persons: null
  }
}

function thanksSlideLike() {
  return { label: '', title: '感谢参会', body: '', image: '', kind: 'ending', items: null, persons: null, fontSize: null }
}

// 段级时间已在议题分组时挂到成员（segTimeByGroup），无需二次遍历

function deck() {
  const base = editableDeck.value || builtSlides.value
  const { slides, photos } = dedupePhotos(base)
  return { slides, photos, background: '', template: template.value, fontScale: fontScale.value }
}

function pageCount(kind) {
  return currentDeckSlides.value.filter(s => s.kind === kind).length
}

function kindLabel(kind) {
  return ({ cover: '封面', agenda: '议程', section: '过场', bio: '出场', resume: '详情', panel: '讨论', ending: '致谢', content: '文字' })[kind] || '页'
}

function slideHasPhoto(s) {
  return Boolean(s.image || (s.persons || []).some(p => p.image))
}

async function saveDraft(msg = '草稿已保存') {
  busy.value = true
  try {
    await saveStageForm(id, { phaseId: phaseId.value, formData: deck() })
    ElMessage.success(msg)
  } finally {
    busy.value = false
  }
}

async function present() {
  busy.value = true
  try {
    const html = await previewShow(id, deck())
    if (typeof html !== 'string' || !html.startsWith('<')) {
      ElMessage.error('预览渲染失败，请重试')
      return
    }
    const url = URL.createObjectURL(new Blob([html], { type: 'text/html;charset=utf-8' }))
    window.open(url, '_blank')
    setTimeout(() => URL.revokeObjectURL(url), 60000)
  } finally {
    busy.value = false
  }
}

async function archive() {
  busy.value = true
  try {
    await publishShow(id, deck())
    ElMessage.success('HTML 幻灯片已归档到项目文件')
  } finally {
    busy.value = false
  }
}

// 进入第四步时冻结快照：页内编辑与页面增删只写快照，保存后按快照重新渲染
watch(step, s => {
  if (s === 4) {
    editableDeck.value = JSON.parse(JSON.stringify(builtSlides.value))
    editing.value = false
    addSegIdx.value = null
    loadPreview()
  }
})

// Step4 当前生效的页面列表（快照优先）
const currentDeckSlides = computed(() => editableDeck.value || builtSlides.value)

async function loadPreview() {
  try {
    const html = await previewShow(id, deck())
    if (typeof html !== 'string' || !html.startsWith('<')) {
      ElMessage.error('预览渲染失败，请重试')
      return
    }
    editing.value = false
    if (previewUrl.value) URL.revokeObjectURL(previewUrl.value)
    previewUrl.value = URL.createObjectURL(new Blob([html], { type: 'text/html;charset=utf-8' }))
    if (previewFrame.value) previewFrame.value.src = previewUrl.value
  } finally {
    busy.value = false
  }
}

function toggleEdit() {
  const win = previewFrame.value?.contentWindow
  if (!win || typeof win.__toggleEdit !== 'function') {
    ElMessage.warning('预览尚未加载完成，请稍候')
    return
  }
  win.__toggleEdit()
  editing.value = Boolean(win.__isEditing?.())
  if (editing.value) syncEditPage()
}

function syncEditPage() {
  const win = previewFrame.value?.contentWindow
  if (!win || typeof win.__getPage !== 'function') return
  editPageIdx.value = win.__getPage()
  const slide = (editableDeck.value?.slides || [])[editPageIdx.value]
  moveBar.value = (slide?.persons || []).map(p => ({ name: p.name || '' }))
}

// 编辑模式下调整过场页头像顺序：交换 editableDeck 中该页 persons 顺序并重渲染，保持编辑态
async function movePerson(j, dir) {
  const to = j + dir
  if (to < 0 || to >= moveBar.value.length) return
  const slide = (editableDeck.value?.slides || [])[editPageIdx.value]
  if (!slide?.persons || !slide.persons[to]) return
  ;[slide.persons[j], slide.persons[to]] = [slide.persons[to], slide.persons[j]]
  busy.value = true
  try {
    await saveStageForm(id, { phaseId: phaseId.value, formData: deck() })
    await loadPreview()
    toggleEdit()
    syncEditPage()
    ElMessage.success('头像顺序已调整')
  } finally {
    busy.value = false
  }
}

// 页内编辑 → 按 data-edit 路径写回快照（person 槽位越界自动创建）→ 保存草稿 → 重新渲染
async function saveEdits() {
  const doc = previewFrame.value?.contentDocument
  if (!doc) {
    ElMessage.warning('预览尚未加载完成，请稍候')
    return
  }
  const slides = JSON.parse(JSON.stringify(editableDeck.value || builtSlides.value))
  const nodes = doc.querySelectorAll('[data-edit]')
  nodes.forEach(el => writeEditPath(slides, el.getAttribute('data-edit'), el.textContent.replace(/\s+/g, ' ').trim()))
  doc.querySelectorAll('.slide[data-fs-changed]').forEach(el => {
    const idx = Array.prototype.indexOf.call(doc.querySelectorAll('#deck > .slide'), el)
    if (idx >= 0 && slides[idx]) slides[idx].fontSize = parseFloat(el.dataset.fsChanged) || null
  })
  const hasText = nodes.length > 0
  const hasFs = doc.querySelectorAll('.slide[data-fs-changed]').length > 0
  if (!hasText && !hasFs) {
    ElMessage.warning('当前页面没有可保存的修改')
    return
  }
  editableDeck.value = slides
  busy.value = true
  try {
    await saveStageForm(id, { phaseId: phaseId.value, formData: deck() })
    ElMessage.success('页内修改已保存，预览已刷新')
    await loadPreview()
  } finally {
    busy.value = false
  }
}

// data-edit 路径写回：p{i}.title / p{i}.body[j] / p{i}.items[j] / p{i}.items[j].k|v / p{i}.persons[j].field
// person 槽位越界（空槽新增专家）时自动创建
function writeEditPath(slides, path, text) {
  const m = String(path || '').match(/^p(\d+)\.(.+)$/)
  if (!m) return
  const slide = slides[+m[1]]
  if (!slide) return
  const rest = m[2]
  let mm
  if ((mm = rest.match(/^items\[(\d+)\]\.(k|v)$/))) {
    const j = +mm[1]
    if (!slide.items) slide.items = []
    while (slide.items.length <= j) slide.items.push('')
    const cur = slide.items[j] || ''
    const parts = cur.split('|')
    if (mm[2] === 'k') slide.items[j] = text + '|' + (parts[1] ?? '')
    else slide.items[j] = (parts.length > 1 ? parts[0] + '|' : '') + text
  } else if ((mm = rest.match(/^items\[(\d+)\]$/))) {
    if (!slide.items) slide.items = []
    while (slide.items.length <= +mm[1]) slide.items.push('')
    slide.items[+mm[1]] = text
  } else if ((mm = rest.match(/^body\[(\d+)\]$/))) {
    const lines = (slide.body || '').split('\n')
    while (lines.length <= +mm[1]) lines.push('')
    lines[+mm[1]] = text
    slide.body = lines.join('\n')
  } else if ((mm = rest.match(/^persons\[(\d+)\]\.(\w+)$/))) {
    const j = +mm[1]
    if (!slide.persons) slide.persons = []
    while (slide.persons.length <= j) slide.persons.push({ role: '', name: '', title: '', unit: '', image: '' })
    slide.persons[j][mm[2]] = text
  } else {
    slide[rest] = text
  }
}

// 手动添加文字页（无专家环节的兜底入口）：插入到致谢页之前
function addTextPage() {
  if (addSegIdx.value === null || !editableDeck.value) return
  const src = textPageSources.value[addSegIdx.value]
  if (!src) return
  const slides = editableDeck.value
  slides.splice(Math.max(0, slides.length - 1), 0, {
    label: src.label, title: src.title || '', body: src.row, image: '',
    kind: 'content', items: null, persons: null, fontSize: null
  })
  addSegIdx.value = null
  loadPreview()
  ElMessage.success('文字页已添加（插在致谢页之前）')
}

// 删除页面（封面与致谢页保护）与排序（▲▼ 交换相邻）
function removeSlide(i) {
  if (!editableDeck.value) return
  editableDeck.value.slides.splice(i, 1)
  loadPreview()
}

function moveSlide(i, dir) {
  const to = i + dir
  if (!editableDeck.value || to < 0 || to >= editableDeck.value.slides.length) return
  const arr = editableDeck.value.slides
  ;[arr[i], arr[to]] = [arr[to], arr[i]]
  loadPreview()
}

function goProject() {
  router.push({ name: 'project-detail', params: { id } })
}
</script>

<style scoped>
/* 向导独立深色工作台（不与 SPA 浅色页面共用视觉，色值为新语义、与 --ui-* 无等值冲突） */
.show-wizard { min-height: 100dvh; background: radial-gradient(1200px 700px at 70% 0%, #1d1010, #10131a 60%); color: #fff3dc; padding: 20px clamp(16px, 4vw, 48px) 32px; }
.wiz-top { display: flex; align-items: center; gap: 22px; flex-wrap: wrap; border-bottom: 1px solid rgba(242, 198, 109, .25); padding-bottom: 14px; }
.wiz-back { cursor: pointer; color: rgba(255, 243, 220, .7); font-size: 14px; }
.wiz-back:hover { color: #f2c66d; }
.wiz-top h1 { margin: 0; font-size: 20px; letter-spacing: .12em; }
.wiz-steps { display: flex; gap: 8px; margin-left: auto; }
.wiz-step { display: flex; align-items: center; gap: 8px; padding: 7px 16px; border-radius: 999px; font-size: 13px; color: rgba(255, 243, 220, .55); border: 1px solid rgba(242, 198, 109, .25); cursor: pointer; }
.wiz-step i { font-style: normal; width: 20px; height: 20px; border-radius: 50%; display: flex; align-items: center; justify-content: center; background: rgba(242, 198, 109, .15); font-size: 12px; }
.wiz-step.active { color: #fff3dc; border-color: #f2c66d; background: rgba(242, 198, 109, .12); }
.wiz-step.done { color: rgba(242, 198, 109, .85); }
.wiz-body { padding: 26px 0 10px; }
.wiz-desc { margin: 0 0 18px; font-size: 14px; color: rgba(255, 243, 220, .65); }
.dark-card { background: rgba(16, 19, 26, .72); border: 1px solid rgba(242, 198, 109, .22); border-radius: 14px; padding: 16px; margin-bottom: 14px; }
.block-title { margin: 0 0 12px; font-size: 15px; letter-spacing: .1em; color: rgba(242, 198, 109, .9); }
.tpl-grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(200px, 1fr)); gap: 16px; max-width: 900px; margin-bottom: 20px; }
.tpl-card { display: flex; flex-direction: column; gap: 8px; padding: 0 0 14px; background: rgba(16, 19, 26, .72); border: 1px solid rgba(242, 198, 109, .25); border-radius: 14px; overflow: hidden; cursor: pointer; color: #fff3dc; text-align: left; }
.tpl-card strong { padding: 0 16px; font-size: 15px; }
.tpl-note { padding: 0 16px; font-size: 12px; color: rgba(255, 243, 220, .55); }
.tpl-swatch { display: block; height: 96px; }
.tpl-card.picked { border-color: #f2c66d; box-shadow: 0 0 0 1px #f2c66d, 0 10px 30px rgba(0, 0, 0, .35); }
.cover-card { max-width: 900px; }
.cover-fields { display: flex; gap: 12px; flex-wrap: wrap; margin-bottom: 12px; }
.field { display: flex; flex-direction: column; gap: 4px; font-size: 12px; color: rgba(255, 243, 220, .6); }
.field-grow { flex: 1; min-width: 160px; }
.field-hint { margin: 4px 0 0; font-size: 12px; color: rgba(255, 243, 220, .45); }
.wiz-actions { display: flex; gap: 12px; margin-top: 20px; flex-wrap: wrap; }
.seg-group-card { padding: 14px 16px; }
.seg-group { margin: 0 0 10px; font-size: 14px; letter-spacing: .12em; color: rgba(242, 198, 109, .9); border-left: 3px solid rgba(242, 198, 109, .5); padding-left: 8px; }
.seg-time { color: rgba(255, 243, 220, .5); font-size: 12px; }
.seg-people { display: flex; flex-wrap: wrap; gap: 10px; }
.seg-person { display: flex; align-items: center; gap: 8px; background: rgba(242, 198, 109, .08); border: 1px solid rgba(242, 198, 109, .3); border-radius: 10px; padding: 6px 10px; }
.seg-person-name { font-size: 13px; color: #fff3dc; }
.person-card { padding: 16px; }
.person-head { display: flex; align-items: center; gap: 12px; flex-wrap: wrap; }
.person-head strong { font-size: 15px; letter-spacing: .08em; }
.person-role { font-size: 12px; color: rgba(242, 198, 109, .8); border: 1px solid rgba(242, 198, 109, .4); border-radius: 999px; padding: 1px 10px; }
.person-fields { display: flex; gap: 8px; flex-wrap: wrap; }
.person-hint { margin: 0; font-size: 12px; color: rgba(255, 243, 220, .45); }
.resume-preview { display: flex; flex-wrap: wrap; gap: 6px; }
.resume-chip { font-size: 12px; color: rgba(255, 243, 220, .75); border: 1px solid rgba(242, 198, 109, .3); border-radius: 8px; padding: 3px 10px; max-width: 100%; overflow-wrap: anywhere; }
.preview-shell { padding: 0; overflow: hidden; }
.preview-bar { display: flex; align-items: center; gap: 10px; padding: 10px 14px; border-bottom: 1px solid rgba(242, 198, 109, .22); }
.preview-title { flex: 1; font-size: 13px; color: rgba(255, 243, 220, .7); }
.move-bar { display: flex; align-items: center; flex-wrap: wrap; gap: 6px; padding: 10px 14px; border-bottom: 1px solid rgba(242, 198, 109, .22); }
.move-label { font-size: 12px; color: rgba(255, 243, 220, .6); }
.move-chip { font-size: 12px; color: #fff3dc; background: rgba(242, 198, 109, .12); border: 1px solid rgba(242, 198, 109, .4); border-radius: 8px; padding: 3px 10px; }
.preview-iframe { display: block; width: 100%; height: min(62dvh, 620px); border: 0; background: #17100e; }
.slide-outline-wrap { margin-top: 14px; }
.slide-outline-wrap summary { cursor: pointer; font-size: 13px; color: rgba(242, 198, 109, .8); }
.add-page-row { display: flex; gap: 10px; align-items: center; margin: 12px 0 4px; flex-wrap: wrap; }
.slide-outline { margin: 12px 0 0; padding-left: 22px; font-size: 13px; color: rgba(255, 243, 220, .75); display: flex; flex-direction: column; gap: 6px; }
.slide-kind { display: inline-block; min-width: 52px; text-align: center; font-size: 11px; border: 1px solid rgba(242, 198, 109, .4); border-radius: 999px; padding: 1px 8px; margin-right: 10px; color: rgba(242, 198, 109, .85); }
.slide-outline em { font-style: normal; color: rgba(242, 198, 109, .6); font-size: 12px; }
.slide-op { margin-left: 6px; }
.wiz-foot { display: flex; justify-content: flex-end; gap: 12px; padding-top: 14px; border-top: 1px solid rgba(242, 198, 109, .2); }
@media (max-width: 760px) { .wiz-two { grid-template-columns: 1fr; } .wiz-steps { margin-left: 0; } }
</style>
