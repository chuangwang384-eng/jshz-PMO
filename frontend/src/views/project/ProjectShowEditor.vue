<template>
  <main class="show-editor">
    <header class="ui-card show-toolbar">
      <router-link :to="{ name: 'project-detail', params: { id } }">返回项目</router-link>
      <h1>串场制作</h1>
      <el-button :disabled="busy || !editable" @click="generate">从日程生成</el-button>
      <label v-if="editable" class="image-field">主 K 背景<input type="file" :accept="imageTypes" @change="pickBackground"></label>
      <el-button v-if="background && editable" @click="background = ''">移除背景</el-button>
      <el-select v-model="template" :disabled="!editable" class="select-template" aria-label="串场模板">
        <el-option v-for="item in templates" :key="item.id" :label="item.name" :value="item.id" />
      </el-select>
      <el-select v-model="fontScale" :disabled="!editable" class="select-font" aria-label="串场字号">
        <el-option v-for="item in fontScales" :key="item.value" :label="item.label" :value="item.value" />
      </el-select>
      <el-button :disabled="busy || !slides.length" @click="presentNewTab">新网页演示</el-button>
      <el-button :disabled="!slides.length" @click="present">全屏预览</el-button>
      <el-button :disabled="busy || !editable || !slides.length" @click="save">保存草稿</el-button>
      <el-button type="primary" :disabled="busy || !editable || !slides.length" @click="archive">归档 HTML</el-button>
    </header>

    <div v-if="ready" class="show-layout">
      <aside class="ui-card slide-list" aria-label="幻灯片列表">
        <el-button v-if="editable" :disabled="slides.length >= 180" @click="add">添加页面</el-button>
        <ol>
          <li v-for="(slide, index) in slides" :key="index">
            <button type="button" :aria-current="index === selected ? 'true' : undefined" @click="selected = index">
              {{ slide.title || slide.label || '空白页' }}
            </button>
          </li>
        </ol>
      </aside>

      <section v-if="current" class="ui-card show-workspace">
        <div class="slide-frame" :style="frameStyle">
          <img v-if="current.image" class="slide-portrait" :src="current.image" alt="幻灯片图片">
          <p>{{ current.label }}</p>
          <h2>{{ current.title }}</h2>
          <p class="slide-body">{{ current.body }}</p>
        </div>
        <div v-if="editable" class="slide-fields">
          <div class="show-toolbar">
            <el-button :disabled="selected === 0" @click="move(-1)">上移</el-button>
            <el-button :disabled="selected === slides.length - 1" @click="move(1)">下移</el-button>
            <el-button :disabled="slides.length >= 180" @click="duplicate">复制</el-button>
            <el-button @click="remove">删除此页</el-button>
          </div>
          <label>页眉<el-input v-model="current.label" maxlength="160" /></label>
          <label>标题<el-input v-model="current.title" maxlength="240" /></label>
          <label>正文<el-input v-model="current.body" type="textarea" :rows="5" maxlength="4000" /></label>
          <label class="image-field">页面图片<input type="file" :accept="imageTypes" @change="pickPortrait"></label>
          <el-button v-if="current.image" @click="current.image = ''">移除图片</el-button>
        </div>
      </section>
      <el-empty v-else description="从项目日程生成幻灯片，或添加空白页" />
    </div>

    <dialog ref="player" class="show-player" @keydown="playKey" @close="exitPlayerFullscreen">
      <div v-if="playing" class="slide-frame" :style="frameStyle">
        <img v-if="playing.image" class="slide-portrait" :src="playing.image" alt="幻灯片图片">
        <p>{{ playing.label }}</p>
        <h2>{{ playing.title }}</h2>
        <p class="slide-body">{{ playing.body }}</p>
      </div>
      <div class="show-toolbar" aria-label="播放控制">
        <el-button :disabled="page === 0" @click="turn(-1)">上一页</el-button>
        <output>{{ page + 1 }} / {{ slides.length }}</output>
        <el-button :disabled="page >= slides.length - 1" @click="turn(1)">下一页</el-button>
        <el-button @click="closePlayer">退出</el-button>
      </div>
    </dialog>
  </main>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { fetchPhotoBlob, getExpert } from '../../api/expert'
import { getProjectDetail, getScheduleOverview, getShowAgenda, getStageForm, previewShow, publishShow, saveStageForm } from '../../api/project'

const id = useRoute().params.id
const imageTypes = 'image/png,image/jpeg,image/webp'
const maxImageBytes = 2 * 1024 * 1024
const ready = ref(false)
const portraits = new Map()
const editable = ref(false)
const busy = ref(false)
const phaseId = ref(null)
const project = ref({})
const slides = ref([])
const selected = ref(0)
const page = ref(0)
const player = ref(null)
const background = ref('')
// 与后端 ProjectShowService.THEMES 成对维护（17.7 状态字典联动约定）；id 取值以后端为准
const templates = [
  { id: 'gold', name: '红金盛典' },
  { id: 'tech', name: '深蓝科技' },
  { id: 'paper', name: '商务简白' },
  { id: 'bamboo', name: '青竹学术' },
]
const fontScales = [
  { value: 1, label: '标准' },
  { value: 1.15, label: '大' },
  { value: 1.3, label: '特大' },
]
// 编辑器示意底色仅传达模板氛围（浅色主题加深色薄纱保证示意文字可读）；正式渲染以后端模板为唯一真源
const frameTints = {
  gold: 'radial-gradient(120% 100% at 50% 40%, #7e1414, #3c0707)',
  tech: 'linear-gradient(135deg, #0e3153, #145371)',
  paper: 'linear-gradient(rgba(24,34,44,.62), rgba(24,34,44,.62)), linear-gradient(180deg, #f7f8fb, #e6eaf2)',
  bamboo: 'linear-gradient(rgba(24,34,44,.62), rgba(24,34,44,.62)), radial-gradient(120% 100% at 50% 40%, #f2f9f4, #d3e7db)',
}
const template = ref('gold')
const fontScale = ref(1)
const current = computed(() => slides.value[selected.value])
const playing = computed(() => slides.value[page.value])
const frameStyle = computed(() => background.value
  ? { backgroundImage: `url("${background.value}")` }
  : { backgroundImage: frameTints[template.value] || frameTints.gold })
const slide = (label = '', title = '', body = '') => ({ label, title, body, image: '' })

onMounted(async () => {
  const detail = await getProjectDetail(id)
  project.value = detail
  const phase = detail.phases?.find(item => item.name === '串场制作')
  if (!phase) {
    ElMessage.error('该项目没有串场制作阶段')
    return
  }
  phaseId.value = phase.id
  editable.value = detail.canOperate && String(detail.phaseId) === String(phase.id)
  const saved = await getStageForm(id, phase.id)
  const draft = saved?.formData
  if (Array.isArray(draft?.slides)) slides.value = draft.slides
  if (typeof draft?.background === 'string' && /^data:image\/(png|jpeg|webp);base64,[A-Za-z0-9+/]+=*$/.test(draft.background)) {
    background.value = draft.background
  }
  template.value = templates.some(item => item.id === draft?.template) ? draft.template : 'gold'
  fontScale.value = fontScales.some(item => item.value === draft?.fontScale) ? draft.fontScale : 1
  ready.value = true
})

async function generate() {
  if (slides.value.length) {
    try {
      await ElMessageBox.confirm('重新生成会替换当前页面内容。', '从日程生成', { type: 'warning' })
    } catch {
      return
    }
  }
  busy.value = true
  try {
    const [agenda, overview] = await Promise.all([getShowAgenda(id), getScheduleOverview(id)])
    const speakers = overview.speakers || []
    const rows = agenda.length ? agenda : [...new Set(speakers.map(item =>
      [item.segmentTime, item.segmentTitle, item.expertName].filter(Boolean).join(' · ')))].filter(Boolean)
    if (!rows.length) {
      ElMessage.warning('日程中暂未提取到可用环节，可以手动添加页面')
      return
    }
    const agendaPages = []
    for (let offset = 0; offset < rows.length; offset += 8) {
      agendaPages.push(slide('会议议程', '议程', rows.slice(offset, offset + 8).join('\n').slice(0, 4000)))
    }
    const eventPages = await Promise.all(rows.slice(0, 150).map(async (row) => {
      const cells = row.split(' · ')
      const title = (cells.length > 1 ? cells[1] : row).slice(0, 240)
      const label = /茶歇|休息/.test(row) ? '茶歇' : /主持|致辞|开场/.test(row) ? '主持与致辞' : '会议环节'
      const matching = speakers.filter(person => person.expertName && (row.includes(person.expertName) ||
        (person.segmentTitle && row.includes(person.segmentTitle))))
      const people = matching.map(person =>
        [person.expertName, person.expertUnit, person.expertTitle].filter(Boolean).join(' '))
      const item = slide(label, title, [row, ...new Set(people)].join('\n').slice(0, 4000))
      const expert = matching.find(person => person.expertId)
      if (expert) item.image = await loadPortrait(expert.expertId)
      return item
    }))
    slides.value = [slide('会议串场', project.value.name, project.value.meetingStartDate), ...agendaPages,
      ...eventPages, slide('', '感谢参会')]
    selected.value = 0
  } finally {
    busy.value = false
  }
}

function loadPortrait(expertId) {
  if (!portraits.has(expertId)) {
    portraits.set(expertId, (async () => {
      try {
        const expert = await getExpert(expertId)
        if (!expert?.照片) return ''
        const { data } = await fetchPhotoBlob(expert.照片)
        if (!imageTypes.split(',').includes(data.type) || data.size > maxImageBytes) return ''
        return await new Promise(resolve => {
          const reader = new FileReader()
          reader.onload = () => resolve(reader.result)
          reader.onerror = () => resolve('')
          reader.readAsDataURL(data)
        })
      } catch {
        return ''
      }
    })())
  }
  return portraits.get(expertId)
}

function add() {
  slides.value.splice(selected.value + 1, 0, slide())
  selected.value = Math.min(selected.value + 1, slides.value.length - 1)
}

function duplicate() {
  slides.value.splice(selected.value + 1, 0, { ...current.value })
  selected.value++
}

function remove() {
  slides.value.splice(selected.value, 1)
  selected.value = Math.max(0, Math.min(selected.value, slides.value.length - 1))
}

function move(direction) {
  const index = selected.value + direction
  const [item] = slides.value.splice(selected.value, 1)
  slides.value.splice(index, 0, item)
  selected.value = index
}

function readImage(event, apply) {
  const file = event.target.files?.[0]
  event.target.value = ''
  if (!file) return
  if (!imageTypes.split(',').includes(file.type) || file.size > maxImageBytes) {
    ElMessage.warning('请选择不超过 2 MB 的 PNG、JPG 或 WebP 图片')
    return
  }
  const reader = new FileReader()
  reader.onload = () => apply(reader.result)
  reader.onerror = () => ElMessage.error('图片读取失败')
  reader.readAsDataURL(file)
}

function pickBackground(event) {
  readImage(event, value => { background.value = value })
}

function pickPortrait(event) {
  const target = current.value
  readImage(event, value => { target.image = value })
}

function deck() {
  return { slides: slides.value, background: background.value, template: template.value, fontScale: fontScale.value }
}

// 新网页演示：未保存的草稿即时渲染到新标签页，与归档共用后端模板，产物一致
async function presentNewTab() {
  busy.value = true
  try {
    const { data } = await previewShow(id, deck())
    const url = URL.createObjectURL(new Blob([data], { type: 'text/html;charset=utf-8' }))
    window.open(url, '_blank')
    setTimeout(() => URL.revokeObjectURL(url), 60000)
  } finally {
    busy.value = false
  }
}

async function save() {
  busy.value = true
  try {
    await saveStageForm(id, { phaseId: phaseId.value, formData: deck() })
    ElMessage.success('草稿已保存')
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

async function present() {
  page.value = selected.value
  if (!document.fullscreenElement && document.documentElement.requestFullscreen) {
    try {
      await document.documentElement.requestFullscreen()
      ownsFullscreen = true
    } catch {
      // The dialog still provides a preview when fullscreen is unavailable.
    }
  }
  player.value.showModal()
}

let ownsFullscreen = false

function exitPlayerFullscreen() {
  if (ownsFullscreen && document.fullscreenElement) document.exitFullscreen().catch(() => {})
  ownsFullscreen = false
}

function closePlayer() {
  player.value.close()
}

function turn(step) {
  page.value = Math.max(0, Math.min(page.value + step, slides.value.length - 1))
}

function playKey(event) {
  if (event.defaultPrevented || event.altKey || event.ctrlKey || event.metaKey) return
  if (['ArrowRight', 'PageDown'].includes(event.key)) { event.preventDefault(); turn(1) }
  if (['ArrowLeft', 'PageUp'].includes(event.key)) { event.preventDefault(); turn(-1) }
}
</script>

<style scoped>
.show-editor { padding: 16px; display: grid; gap: 16px; }
.show-toolbar { display: flex; flex-wrap: wrap; align-items: center; gap: 12px; }
.show-toolbar h1 { margin: 0 auto 0 0; font-size: 1.25rem; }
.show-layout { display: grid; grid-template-columns: minmax(145px, 220px) minmax(0, 1fr); gap: 16px; }
.slide-list { align-self: start; }
.slide-list ol { padding-left: 24px; }
.slide-list button { width: 100%; text-align: left; margin: 3px 0; padding: 7px; border: 1px solid var(--ui-info); background: white; overflow-wrap: anywhere; cursor: pointer; }
.slide-list button[aria-current] { border-color: var(--ui-primary); color: var(--ui-primary); }
.slide-frame { container-type: inline-size; aspect-ratio: 16 / 9; overflow: auto; padding: 7%; color: white; background: linear-gradient(135deg, #0e3153, #145371) center / cover; }
.slide-frame p { margin: 0; font-size: 2.3cqw; text-shadow: 0 2px 6px #111; }
.slide-frame h2 { margin: .4em 0; font-size: 5.3cqw; overflow-wrap: anywhere; text-shadow: 0 2px 6px #111; }
.slide-body { white-space: pre-wrap; overflow-wrap: anywhere; }
.slide-portrait { float: right; width: 24%; max-height: 65%; object-fit: contain; margin-left: 4%; }
.slide-fields { display: grid; gap: 12px; margin-top: 16px; }
.image-field { display: flex; flex-wrap: wrap; gap: 8px; align-items: center; }
.select-template { width: 132px; }
.select-font { width: 96px; }
.show-player { width: min(95vw, 160vh); max-width: none; padding: 12px; border: 0; }
.show-player::backdrop { background: #101827; }
.show-player[open] { width: 100%; max-height: 100%; background: #101827; color: white; }
.show-player .slide-frame { width: min(100%, calc((100dvh - 64px) * 16 / 9)); margin: auto; }
.show-player .show-toolbar { justify-content: center; margin-top: 8px; }
@media (max-width: 700px) { .show-layout { grid-template-columns: 1fr; } }
</style>
