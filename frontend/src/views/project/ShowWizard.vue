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

    <!-- 第一步：模板 + 封面定制（封面文字全部可编辑，渲染与归档同源） -->
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
      <div class="wiz-actions"><el-button type="primary" :disabled="!items.length" @click="step = 2">下一步：提取日程</el-button></div>
    </section>

    <!-- 第二步：日程与专家（章节分组 / 人脸预抠图 / 简介语义分段） -->
    <section v-else-if="step === 2" class="wiz-body wiz-two">
      <aside class="dark-card seg-list">
        <template v-for="(group, gi) in groupedItems" :key="group.name">
          <p class="seg-group">{{ group.no }} · {{ group.name }}</p>
          <button v-for="ref in group.items" :key="ref.index" type="button" class="seg-item"
            :class="{ picked: selected === ref.index }" @click="selected = ref.index">
            <span class="seg-label">{{ ref.item.label }}</span>
            <span class="seg-text">{{ ref.item.title || ref.item.row }}</span>
          </button>
        </template>
        <el-empty v-if="!items.length" description="未提取到环节" :image-size="60" />
      </aside>

      <div class="dark-card seg-detail">
        <template v-if="current">
          <div class="seg-fields">
            <label class="field">环节标签<el-input v-model="current.label" maxlength="40" /></label>
            <label class="field field-grow">环节标题<el-input v-model="current.title" maxlength="240" /></label>
          </div>
          <div v-for="(p, pi) in current.people" :key="pi" class="person-card">
            <div class="person-head">
              <strong>{{ p.name || `专家 ${pi + 1}` }}</strong>
              <span class="person-role">{{ p.role }}</span>
              <el-button size="small" :loading="p.loading" :disabled="!p.expertId || p.loaded" @click="loadPerson(p)">
                {{ p.loaded ? '已载入' : '从专家库载入' }}
              </el-button>
            </div>
            <CropAvatar v-if="p.photoSrc" v-model="p.avatar" :src="p.photoSrc" />
            <p v-else class="person-hint">
              {{ p.expertId ? '点击「从专家库载入」获取证件照与简介，系统将自动识别人脸预抠图' : '日程中未关联专家库，仅生成文字页' }}
            </p>
            <div class="person-fields">
              <label class="field">大会角色<el-input v-model="p.role" maxlength="40" style="width: 150px" /></label>
              <label class="field">姓名<el-input v-model="p.name" maxlength="60" style="width: 130px" /></label>
              <label class="field">职称<el-input v-model="p.title" maxlength="60" style="width: 120px" /></label>
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
          <p v-if="!current.people.length" class="person-hint">该环节未匹配到日程专家，将仅生成文字页</p>
        </template>
        <el-empty v-else description="从左侧选择环节进行编辑" />
      </div>
    </section>

    <!-- 第三步：预览与保存 -->
    <section v-else class="wiz-body">
      <p class="wiz-desc">
        已生成 {{ builtSlides.length }} 页：封面 + 议程 + {{ sections.length }} 个章节过场 +
        {{ pageCount('bio') }} 个出场页 / {{ pageCount('panel') }} 个讨论墙 / {{ pageCount('resume') }} 页简历 + 致谢。
        预览与归档使用同一后端模板渲染。
      </p>
      <div class="wiz-actions">
        <el-button :disabled="busy || !editable" @click="saveDraft">保存草稿</el-button>
        <el-button type="primary" :disabled="busy" @click="present">预览演示（新标签页）</el-button>
        <el-button :disabled="busy || !editable" @click="openEditor">进入编辑器微调 / 归档</el-button>
      </div>
      <ol class="slide-outline">
        <li v-for="(s, i) in builtSlides" :key="i">
          <span class="slide-kind">{{ kindLabel(s.kind) }}</span>{{ s.title || s.label }}
          <em v-if="slideHasPhoto(s)"> · 含头像</em>
        </li>
      </ol>
    </section>

    <footer class="wiz-foot">
      <el-button v-if="step > 1" :disabled="busy" @click="step -= 1">上一步</el-button>
      <el-button v-if="step < 3" type="primary" :disabled="busy || (step === 1 && !items.length)" @click="step += 1">
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
import { getProjectDetail, getScheduleOverview, getShowAgenda, previewShow, saveStageForm } from '../../api/project'
import {
  agendaRows, agendaSlides, classifyRow, coverSlideV2, guessRole, matchSpeakers,
  panelSlideV2, bioSlideV2, resumeSlideV2, sectionOf, sectionSlideV2, splitResumeItems, thanksSlide
} from '../../utils/showDeck'

// 三步向导（2026-11-21 结构化页面模型）：①模板+封面定制 ②日程提取/人脸预抠图/简介语义分段 ③预览演示。
// 产出与编辑器共用 stage-form 草稿；预览/归档共用后端 render。
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
const stepNames = ['模板与封面', '日程与专家', '预览演示']

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
const items = ref([])
const selected = ref(0)
const busy = ref(false)
const current = computed(() => items.value[selected.value])

onMounted(async () => {
  const detail = await getProjectDetail(id)
  project.value = detail
  const phase = detail.phases?.find(item => item.name === '串场制作')
  editable.value = Boolean(detail.canOperate && phase && String(detail.phaseId) === String(phase.id))
  phaseId.value = phase?.id || null
  const [agenda, overview] = await Promise.all([getShowAgenda(id), getScheduleOverview(id)])
  const { rows, speakers } = agendaRows(agenda, overview)
  if (!rows.length) {
    ElMessage.warning('日程中暂未提取到环节，请确认该项目日程阶段已上传日程')
    return
  }
  items.value = rows.slice(0, 150).map(row => {
    const { label, title } = classifyRow(row)
    return {
      row, label, title,
      people: matchSpeakers(row, speakers).map(p => ({
        expertId: p.expertId || '', name: p.expertName || '', unit: p.expertUnit || '',
        title: p.expertTitle || '', role: guessRole(row, label), intro: '',
        photoSrc: '', avatar: '', loading: false, loaded: false
      }))
    }
  })
  initCover()
})

// 封面默认值：项目名/日期/城市 + 第一位大会主席专家
function initCover() {
  const detail = project.value
  cover.value.title = detail.name || ''
  cover.value.time = detail.meetingStartDate || ''
  cover.value.place = detail.cityName || ''
  const chairItem = items.value.find(it => /主席|致辞/.test(it.row) && it.people.length)
  const chairPerson = chairItem?.people?.[0]
  cover.value.chair = chairPerson ? [chairPerson.name, chairPerson.title].filter(Boolean).join(' ') : ''
}

// 选中环节后自动为其中专家拉取证件照与简介（含人脸预抠图）
watch(current, item => {
  (item?.people || []).forEach(p => { if (p.expertId && !p.loaded && !p.loading) loadPerson(p) })
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

// 章节聚类（保持日程顺序）：开场致辞 / 学术报告 / 多学科讨论；茶歇并入前一章节
const sections = computed(() => {
  const groups = []
  for (let i = 0; i < items.value.length; i++) {
    const item = items.value[i]
    let name = sectionOf(item.row)
    if (!name) name = groups.length ? groups[groups.length - 1].name : '开场致辞'
    let group = groups.find(g => g.name === name)
    if (!group) {
      group = { name, items: [] }
      groups.push(group)
    }
    group.items.push({ index: i, item })
  }
  return groups.map((g, i) => ({ ...g, no: String(i + 1).padStart(2, '0') }))
})

const groupedItems = computed(() => sections.value)

const builtSlides = computed(() => {
  const slides = [coverSlideV2({
    org: cover.value.org,
    title: cover.value.title || project.value.name || '',
    sub: cover.value.sub,
    infos: [
      cover.value.time ? `会议时间|${cover.value.time}` : '',
      cover.value.place ? `会议地点|${cover.value.place}` : '',
      cover.value.chair ? `大会主席|${cover.value.chair}` : ''
    ].filter(Boolean)
  }), ...agendaSlides(items.value.map(item => item.row))]
  for (const group of sections.value) {
    // 章节过场页：主领人取该章节第一个有头像/姓名的专家
    const lead = group.items.map(ref => ref.item.people[0]).find(p => p && (p.avatar || p.name))
    slides.push(sectionSlideV2({
      no: group.no,
      title: group.name,
      person: lead ? { role: lead.role, name: lead.name, title: lead.title, unit: lead.unit, avatar: lead.avatar } : null
    }))
    for (const { item } of group.items) {
      const people = item.people.filter(p => p.name || p.avatar)
      if (people.length >= 2) {
        // 讨论墙：全部嘉宾头像一页
        slides.push(panelSlideV2({ label: group.name, title: item.title || group.name, persons: people }))
      } else if (people.length === 1) {
        slides.push(bioSlideV2({ label: item.label, person: people[0] }))
      } else {
        slides.push({ label: item.label, title: item.title, body: item.row, image: '', kind: 'content', items: null, persons: null })
      }
      // 每位讨论嘉宾/主讲专家自己的简历页（简介分段非空才生成）
      for (const p of people) {
        if (splitResumeItems(p.intro).length) slides.push(resumeSlideV2({ person: p }))
      }
    }
  }
  slides.push(thanksSlide())
  return slides
})

function pageCount(kind) {
  return builtSlides.value.filter(s => s.kind === kind).length
}

function kindLabel(kind) {
  return ({ cover: '封面', agenda: '议程', section: '过场', bio: '出场', resume: '简历', panel: '讨论', ending: '致谢', content: '文字' })[kind] || '页'
}

function slideHasPhoto(s) {
  return Boolean(s.image || (s.persons || []).some(p => p.image))
}

function deck() {
  return { slides: builtSlides.value, background: '', template: template.value, fontScale: fontScale.value }
}

async function saveDraft() {
  busy.value = true
  try {
    await saveStageForm(id, { phaseId: phaseId.value, formData: deck() })
    ElMessage.success('草稿已保存，可在编辑器中继续')
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

async function openEditor() {
  if (editable.value && phaseId.value) {
    try {
      await saveStageForm(id, { phaseId: phaseId.value, formData: deck() })
    } catch {
      // 自动保存失败不阻塞跳转，编辑器内仍有草稿兜底
    }
  }
  const { href } = router.resolve({ name: 'project-show', params: { id } })
  window.open(href, '_blank')
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
.dark-card { background: rgba(16, 19, 26, .72); border: 1px solid rgba(242, 198, 109, .22); border-radius: 14px; padding: 16px; }
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
.wiz-two { display: grid; grid-template-columns: minmax(230px, 320px) minmax(0, 1fr); gap: 16px; align-items: start; }
.seg-list { max-height: 70dvh; overflow: auto; }
.seg-group { margin: 14px 0 6px; font-size: 13px; letter-spacing: .15em; color: rgba(242, 198, 109, .85); border-left: 3px solid rgba(242, 198, 109, .5); padding-left: 8px; }
.seg-group:first-child { margin-top: 0; }
.seg-item { display: flex; gap: 8px; align-items: baseline; width: 100%; text-align: left; padding: 8px 10px; margin: 2px 0; border: 1px solid transparent; border-radius: 8px; background: transparent; color: rgba(255, 243, 220, .8); cursor: pointer; }
.seg-item.picked { border-color: rgba(242, 198, 109, .5); background: rgba(242, 198, 109, .1); color: #fff3dc; }
.seg-label { flex: none; font-size: 11px; border: 1px solid rgba(242, 198, 109, .4); border-radius: 999px; padding: 1px 8px; }
.seg-text { font-size: 13px; overflow-wrap: anywhere; }
.seg-fields { display: flex; gap: 12px; margin-bottom: 12px; }
.person-card { border-top: 1px dashed rgba(242, 198, 109, .25); padding: 14px 0; display: flex; flex-direction: column; gap: 10px; }
.person-head { display: flex; align-items: center; gap: 12px; }
.person-head strong { font-size: 15px; letter-spacing: .08em; }
.person-role { font-size: 12px; color: rgba(242, 198, 109, .8); border: 1px solid rgba(242, 198, 109, .4); border-radius: 999px; padding: 1px 10px; }
.person-fields { display: flex; gap: 8px; flex-wrap: wrap; }
.person-hint { margin: 0; font-size: 12px; color: rgba(255, 243, 220, .45); }
.resume-preview { display: flex; flex-wrap: wrap; gap: 6px; }
.resume-chip { font-size: 12px; color: rgba(255, 243, 220, .75); border: 1px solid rgba(242, 198, 109, .3); border-radius: 8px; padding: 3px 10px; max-width: 100%; overflow-wrap: anywhere; }
.slide-outline { margin: 12px 0 0; padding-left: 22px; font-size: 13px; color: rgba(255, 243, 220, .75); display: flex; flex-direction: column; gap: 6px; }
.slide-kind { display: inline-block; min-width: 52px; text-align: center; font-size: 11px; border: 1px solid rgba(242, 198, 109, .4); border-radius: 999px; padding: 1px 8px; margin-right: 10px; color: rgba(242, 198, 109, .85); }
.slide-outline em { font-style: normal; color: rgba(242, 198, 109, .6); font-size: 12px; }
.wiz-foot { display: flex; justify-content: flex-end; gap: 12px; padding-top: 14px; border-top: 1px solid rgba(242, 198, 109, .2); }
@media (max-width: 760px) { .wiz-two { grid-template-columns: 1fr; } .wiz-steps { margin-left: 0; } }
</style>
