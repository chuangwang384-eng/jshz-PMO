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

    <section v-if="step === 1" class="wiz-body">
      <p class="wiz-desc">选择一套演示模板，影响串场稿的配色、字体与氛围；后续在编辑器中可随时更换。</p>
      <div class="tpl-grid">
        <button v-for="t in templates" :key="t.id" type="button" class="tpl-card"
          :class="{ picked: template === t.id }" @click="template = t.id">
          <span class="tpl-swatch" :style="{ backgroundImage: t.preview }"></span>
          <strong>{{ t.name }}</strong>
          <span class="tpl-note">{{ t.desc }}</span>
        </button>
      </div>
      <div class="wiz-actions">
        <el-button type="primary" @click="step = 2">下一步：提取日程</el-button>
      </div>
    </section>

    <section v-else-if="step === 2" class="wiz-body wiz-two">
      <aside class="dark-card seg-list">
        <p class="seg-title">日程环节（{{ items.length }}）</p>
        <button v-for="(item, i) in items" :key="i" type="button" class="seg-item"
          :class="{ picked: selected === i }" @click="selected = i">
          <span class="seg-label">{{ item.label }}</span>
          <span class="seg-text">{{ item.title || item.row }}</span>
        </button>
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
              <el-button size="small" :loading="p.loading" :disabled="!p.expertId || p.loaded" @click="loadPerson(p)">
                {{ p.loaded ? '已载入' : '从专家库载入' }}
              </el-button>
            </div>
            <CropAvatar v-if="p.photoSrc" v-model="p.avatar" :src="p.photoSrc" />
            <p v-else class="person-hint">
              {{ p.expertId ? '点击「从专家库载入」获取证件照与简介' : '日程中未关联专家库，仅生成文字页' }}
            </p>
            <div class="person-fields">
              <el-input v-model="p.name" placeholder="姓名" maxlength="60" style="width: 130px" />
              <el-input v-model="p.unit" placeholder="单位" maxlength="120" style="flex: 1; min-width: 160px" />
              <el-input v-model="p.title" placeholder="职称" maxlength="60" style="width: 150px" />
            </div>
            <el-input v-model="p.intro" type="textarea" :rows="4" maxlength="1500"
              placeholder="个人简介（按句逐步浮现，可留空）" />
          </div>
          <p v-if="!current.people.length" class="person-hint">该环节未匹配到日程专家，将仅生成文字页</p>
        </template>
        <el-empty v-else description="从左侧选择环节进行编辑" />
      </div>
    </section>

    <section v-else class="wiz-body">
      <p class="wiz-desc">
        已生成 {{ builtSlides.length }} 页（封面 + 议程 + {{ items.length }} 个环节 + 致谢）。
        预览与归档使用同一后端模板渲染，产物一致。
      </p>
      <div class="wiz-actions">
        <el-button :disabled="busy || !editable" @click="saveDraft">保存草稿</el-button>
        <el-button type="primary" :disabled="busy" @click="present">预览演示（新标签页）</el-button>
        <el-button :disabled="busy || !editable" @click="openEditor">进入编辑器微调 / 归档</el-button>
      </div>
      <ol class="slide-outline">
        <li v-for="(s, i) in builtSlides" :key="i">
          <span class="slide-kind">{{ s.label || '页' }}</span>{{ s.title }}<em v-if="s.image"> · 含头像</em>
        </li>
      </ol>
    </section>

    <footer class="wiz-foot">
      <el-button v-if="step > 1" :disabled="busy" @click="step -= 1">上一步</el-button>
      <el-button v-if="step < 3" type="primary" :disabled="busy || (step === 2 && !items.length)" @click="step += 1">
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
import { agendaRows, agendaSlides, classifyRow, coverSlide, eventBody, matchSpeakers, splitSentences, thanksSlide } from '../../utils/showDeck'

// 三步向导：选模板 → 日程/证件照（圆形裁剪）/简介 → 预览演示；产出的草稿与编辑器共用 stage-form 存储
const templates = [
  { id: 'gold', name: '红金盛典', desc: '深红金流光，适合庆典与开幕', preview: 'radial-gradient(120% 100% at 50% 40%, #7e1414, #3c0707)' },
  { id: 'tech', name: '深蓝科技', desc: '深蓝网格光束，学术论坛', preview: 'linear-gradient(135deg, #0e3153, #145371)' },
  { id: 'paper', name: '商务简白', desc: '白底深字，内部会议汇报', preview: 'linear-gradient(180deg, #f7f8fb, #e6eaf2)' },
  { id: 'bamboo', name: '青竹学术', desc: '青绿学术风，临床交流', preview: 'radial-gradient(120% 100% at 50% 40%, #f2f9f4, #d3e7db)' }
]
const stepNames = ['选择模板', '日程与专家', '预览演示']

const route = useRoute()
const router = useRouter()
const id = route.params.id
const step = ref(1)
const template = ref('gold')
const editable = ref(false)
const phaseId = ref(null)
const project = ref({})
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
        title: p.expertTitle || '', photoSrc: '', avatar: '', intro: '', loading: false, loaded: false
      }))
    }
  })
})

// 选中环节后自动为其中专家拉取证件照与简介
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

const builtSlides = computed(() => {
  const slides = [
    coverSlide(project.value.name, project.value.meetingStartDate),
    ...agendaSlides(items.value.map(item => item.row))
  ]
  for (const item of items.value) {
    const lines = [item.row]
    for (const p of item.people) {
      const line = [p.name, p.unit, p.title].filter(Boolean).join(' ')
      if (line) lines.push(line)
      if (p.intro) lines.push(...splitSentences(p.intro))
    }
    const avatar = item.people.find(p => p.avatar)?.avatar || ''
    slides.push({ label: item.label, title: item.title, body: eventBody(lines), image: avatar })
  }
  slides.push(thanksSlide())
  return slides
})

function deck() {
  return { slides: builtSlides.value, background: '', template: template.value, fontScale: 1 }
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
.tpl-grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(200px, 1fr)); gap: 16px; max-width: 900px; }
.tpl-card { display: flex; flex-direction: column; gap: 8px; padding: 0 0 14px; background: rgba(16, 19, 26, .72); border: 1px solid rgba(242, 198, 109, .25); border-radius: 14px; overflow: hidden; cursor: pointer; color: #fff3dc; text-align: left; }
.tpl-card strong { padding: 0 16px; font-size: 15px; }
.tpl-note { padding: 0 16px; font-size: 12px; color: rgba(255, 243, 220, .55); }
.tpl-swatch { display: block; height: 96px; }
.tpl-card.picked { border-color: #f2c66d; box-shadow: 0 0 0 1px #f2c66d, 0 10px 30px rgba(0, 0, 0, .35); }
.wiz-actions { display: flex; gap: 12px; margin-top: 20px; flex-wrap: wrap; }
.wiz-two { display: grid; grid-template-columns: minmax(230px, 320px) minmax(0, 1fr); gap: 16px; align-items: start; }
.seg-list { max-height: 70dvh; overflow: auto; }
.seg-title { margin: 0 0 10px; font-size: 13px; color: rgba(255, 243, 220, .6); }
.seg-item { display: flex; gap: 8px; align-items: baseline; width: 100%; text-align: left; padding: 8px 10px; margin: 2px 0; border: 1px solid transparent; border-radius: 8px; background: transparent; color: rgba(255, 243, 220, .8); cursor: pointer; }
.seg-item.picked { border-color: rgba(242, 198, 109, .5); background: rgba(242, 198, 109, .1); color: #fff3dc; }
.seg-label { flex: none; font-size: 11px; border: 1px solid rgba(242, 198, 109, .4); border-radius: 999px; padding: 1px 8px; }
.seg-text { font-size: 13px; overflow-wrap: anywhere; }
.seg-fields { display: flex; gap: 12px; margin-bottom: 12px; }
.field { display: flex; flex-direction: column; gap: 4px; font-size: 12px; color: rgba(255, 243, 220, .6); }
.field-grow { flex: 1; }
.person-card { border-top: 1px dashed rgba(242, 198, 109, .25); padding: 14px 0; display: flex; flex-direction: column; gap: 10px; }
.person-head { display: flex; align-items: center; justify-content: space-between; gap: 10px; }
.person-head strong { font-size: 15px; letter-spacing: .08em; }
.person-fields { display: flex; gap: 8px; flex-wrap: wrap; }
.person-hint { margin: 0; font-size: 12px; color: rgba(255, 243, 220, .45); }
.slide-outline { margin: 12px 0 0; padding-left: 22px; font-size: 13px; color: rgba(255, 243, 220, .75); display: flex; flex-direction: column; gap: 6px; }
.slide-kind { display: inline-block; min-width: 64px; text-align: center; font-size: 11px; border: 1px solid rgba(242, 198, 109, .4); border-radius: 999px; padding: 1px 8px; margin-right: 10px; color: rgba(242, 198, 109, .85); }
.slide-outline em { font-style: normal; color: rgba(242, 198, 109, .6); font-size: 12px; }
.wiz-foot { display: flex; justify-content: flex-end; gap: 12px; padding-top: 14px; border-top: 1px solid rgba(242, 198, 109, .2); }
@media (max-width: 760px) { .wiz-two { grid-template-columns: 1fr; } .wiz-steps { margin-left: 0; } }
</style>
