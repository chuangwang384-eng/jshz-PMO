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
          :class="{ picked: template === t.id }" @click="selectBuiltin(t.id)">
          <span class="tpl-swatch" :style="{ backgroundImage: t.preview }"></span>
          <strong>{{ t.name }}</strong>
          <span class="tpl-note">{{ t.desc }}</span>
        </button>
      </div>
      <div class="dark-card cover-card">
        <p class="block-title">封面定制</p>
        <div class="cover-fields">
          <label v-if="hasField('org')" class="field">机构 / 主办方<el-input v-model="cover.org" maxlength="80" placeholder="如：中国医药教育协会" /></label>
          <label v-if="hasField('title')" class="field field-grow">主标题<el-input v-model="cover.title" maxlength="60" placeholder="如：聚例辉煌" /></label>
          <label v-if="hasField('sub')" class="field field-grow">副标题<el-input v-model="cover.sub" maxlength="120" placeholder="如：临床应用研讨 · 多学科病例讨论项目" /></label>
        </div>
        <div class="cover-fields">
          <label v-if="hasField('place')" class="field">会议地点<el-input v-model="cover.place" maxlength="60" placeholder="如：郑州 · 会场一" /></label>
          <label v-if="hasField('chair')" class="field field-grow">大会主席<el-input v-model="cover.chair" maxlength="80" placeholder="如：齐秀恒 教授" /></label>
        </div>
        <div class="cover-fields">
          <label class="field">全局字号
            <el-select v-model="fontScale" style="width: 200px">
              <el-option v-for="f in fontScales" :key="f.value" :label="f.label" :value="f.value" />
            </el-select>
          </label>
          <label class="field">16:9 主视觉
            <input type="file" accept="image/png,image/jpeg,image/webp" @change="selectArtwork($event, false)">
          </label>
          <el-button v-if="background" size="small" @click="background = ''">移除主视觉</el-button>
        </div>
        <div v-for="field in customFields" :key="field.key" class="cover-fields">
          <label class="field">字段名称<el-input v-model="field.label" maxlength="40" /></label>
          <label class="field field-grow">字段内容<el-input v-model="field.value" maxlength="240" /></label>
          <el-button size="small" type="danger" plain @click="removeCoverField(field.key)">删除字段</el-button>
        </div>
        <div class="wiz-actions">
          <el-button :disabled="layout.length >= 20" @click="addCoverField">添加自定义字段</el-button>
          <el-button @click="openPagePreview(0, coverSlideLike())">预览正式封面</el-button>
          <el-button v-if="editable" :disabled="busy" @click="saveDraft('封面已保存')">保存项目封面</el-button>
        </div>
        <div class="cover-canvas" :class="{ 'cover-canvas-tech': template === 'tech' }" ref="coverCanvas"
          :style="background ? { backgroundImage: `linear-gradient(#0002, #0002), url(${background})` } : {}">
          <div v-for="field in coverFields" :key="field.key" class="canvas-field"
            :class="{ 'canvas-title': field.key === 'title', selected: selectedKey === field.key }"
            :style="coverFieldStyle(field)">
            <button type="button" class="canvas-field-text" :aria-label="`编辑${field.label}`"
              @click="selectedKey = field.key" @pointerdown="startFieldDrag($event, field.key)"
              @pointermove="moveFieldDrag" @pointerup="stopFieldDrag" @pointercancel="stopFieldDrag"
              @lostpointercapture="stopFieldDrag" @keydown="nudgeField($event, field.key)">
              {{ field.value || field.label }}
            </button>
            <button type="button" class="canvas-delete" :aria-label="`删除${field.label}`" @click="removeCoverField(field.key)">×</button>
            <button type="button" class="canvas-resize" :aria-label="`缩放${field.label}`"
              @pointerdown="startFieldDrag($event, field.key, true)" @pointermove="moveFieldDrag"
              @pointerup="stopFieldDrag" @pointercancel="stopFieldDrag" @lostpointercapture="stopFieldDrag"
              @keydown="resizeWithKeys($event, field.key)">◢</button>
          </div>
        </div>
        <div v-if="layout.length" class="cover-fields field-settings">
          <label class="field">编辑文本框
            <el-select v-model="selectedKey" placeholder="选择文本框" style="width: 190px">
              <el-option v-for="field in layout" :key="field.key" :label="field.label" :value="field.key" />
            </el-select>
          </label>
          <label v-if="selectedField" class="field">{{ selectedField.label }}字体
            <el-select v-model="selectedField.font" style="width: 190px">
              <el-option v-for="font in coverFonts" :key="font.value" :label="font.label" :value="font.value" />
            </el-select>
          </label>
          <label v-if="selectedField" class="field">字号（相对默认值）
            <el-input-number v-model="selectedField.scale" :min="50" :max="200" :step="10" />
          </label>
        </div>
        <el-button v-if="defaultLayout.some(field => !hasField(field.key))" link @click="restoreDefaultFields">恢复已删除的默认字段</el-button>
      </div>
      <div class="wiz-actions"><el-button type="primary" :disabled="!experts.length" @click="step = 2">下一步：专家信息</el-button></div>
    </section>

    <!-- 第二步：专家信息（全局资料池，去重后一次编辑） -->
    <section v-else-if="step === 2" class="wiz-body">
      <p class="wiz-desc">
        已从日程提取 {{ experts.length }} 位参会专家（重复出现只保留一份，两字姓名自动去除排版空格）。
        这里的资料是全局的：头像、姓名、单位、职称、简介改一处，所有环节与页面自动联动。
      </p>
      <div class="wiz-actions">
        <label class="field">项目头像框（中心透明的 PNG 或 WebP）
          <input type="file" accept="image/png,image/webp" @change="selectArtwork($event, true)">
        </label>
        <img v-if="avatarFrame" class="frame-swatch" :src="avatarFrame" alt="当前头像框">
        <el-button v-if="avatarFrame" size="small" @click="avatarFrame = ''">恢复默认头像框</el-button>
        <el-button v-if="editable" :disabled="busy" @click="saveDraft('项目头像框已保存')">保存头像框</el-button>
      </div>
      <div v-if="avatarFrame" class="cover-fields">
        <label class="field">框内头像大小（%）<el-input-number v-model="frameFit.scale" :min="40" :max="100" /></label>
        <label class="field">水平偏移（%）<el-input-number v-model="frameFit.x" :min="-25" :max="25" /></label>
        <label class="field">垂直偏移（%）<el-input-number v-model="frameFit.y" :min="-25" :max="25" /></label>
        <el-button size="small" @click="frameFit = { ...defaultFrameFit }">复位</el-button>
      </div>
      <div v-for="p in experts" :key="p.key" class="dark-card person-card">
        <div class="person-head">
          <strong>{{ p.name || '未命名专家' }}</strong>
          <span class="person-role">{{ roleSummary(p) }}</span>
          <el-button size="small" :loading="p.loading" :disabled="!p.expertId || p.loaded" @click="loadPerson(p)">
            {{ p.loaded ? '已载入' : '从专家库载入' }}
          </el-button>
          <el-button size="small" type="primary" :disabled="busy || !editable" @click="saveDraft('专家信息已保存')">保存</el-button>
        </div>
        <CropAvatar v-if="p.photoSrc" v-model="p.avatar" :src="p.photoSrc" :frame="avatarFrame" :frame-fit="frameFit" @detecting="setDetecting(p.key, $event)" />
        <p v-else class="person-hint" role="status">{{ p.loading ? '正在读取专家资料和头像，请稍候…' : p.expertId ? '点击「从专家库载入」获取证件照与简介，系统将自动识别人脸预抠图' : '该专家未关联专家库，将仅生成文字页' }}</p>
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
      <div v-for="g in groups" :key="g.index" class="dark-card seg-group-card">
        <p class="seg-group">{{ g.title }}<span v-if="g.times.length" class="seg-time">（{{ g.times.join(' / ') }}）</span></p>
        <div class="seg-people">
          <div v-for="m in g.members" :key="m.person.key" class="seg-person">
            <span class="seg-person-name">{{ m.person.name || '?' }}</span>
            <span v-if="m.time" class="seg-person-time">{{ m.time }}</span>
            <el-select v-model="m.person.roleByGroup[g.index]" @change="updateSnapshotRole(g.index, m.person)" size="small" style="width: 130px"
              :aria-label="`${m.person.name} 在该段落的角色`">
              <el-option v-for="r in roleOptions" :key="r" :label="r" :value="r" />
            </el-select>
          </div>
        </div>
        <div class="segment-pages">
          <article v-for="entry in (pagesByGroup.get(g.index) || [])" :key="pageCardKey(entry.slide)"
            class="dark-card segment-card" :draggable="editable"
            @dragstart="startPageDrag($event, entry)" @dragend="draggedPage = null"
            @dragover="allowPageDrop($event, entry)" @drop="dropPage($event, entry)">
            <button type="button" class="segment-page" @click="openPagePreview(entry.index)">
              <span class="slide-kind">{{ kindLabel(entry.slide.kind) }}</span>
              <strong>{{ entry.slide.title }}</strong>
              <span v-for="person in (entry.slide.persons || [])" :key="person.name + person.role">
                {{ person.name }}<template v-if="person.role"> · {{ person.role }}</template>
              </span>
              <span v-if="entry.slide.kind === 'resume' && entry.slide.items?.length">{{ entry.slide.items[0] }}</span>
            </button>
            <button v-if="editable" type="button" class="segment-remove" :aria-label="`删除 ${entry.slide.title}`"
              @click="removeSlide(entry.index)">×</button>
            <div v-if="editable" class="segment-order">
              <el-button link size="small" @click="nudgePage(entry, -1)">前移</el-button>
              <el-button link size="small" @click="nudgePage(entry, 1)">后移</el-button>
              <el-button v-if="missingIntroductions(entry).length" link size="small"
                @click="restoreIntroductions(entry)">补齐专家简介</el-button>
            </div>
          </article>
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
            <el-button size="small" circle :disabled="busy || j === 0" @click="movePerson(j, -1)">◀</el-button>
            <el-button size="small" circle :disabled="busy || j === moveBar.length - 1" @click="movePerson(j, 1)">▶</el-button>
          </template>
        </div>
        <el-button v-if="editable" size="small" @click="openCurrentPageEditor">编辑当前页</el-button>
        <iframe ref="previewFrame" class="preview-iframe" title="串场演示预览"></iframe>
      </div>
    </section>
    <section v-if="step === 4" class="wiz-body">
      <details class="slide-outline-wrap" open>
        <summary>页面清单（{{ currentDeckSlides.length }} 页，可删除/上移/下移/添加文字页）</summary>
        <div class="add-page-row">
          <el-select v-model="addSegIdx" placeholder="选择要生成文字页的环节" style="width: 340px" size="small">
            <el-option v-for="(item, i) in textPageSources" :key="i" :label="`${item.label} · ${item.title || item.row}`" :value="i" />
          </el-select>
          <el-button size="small" @click="addTextPage">{{ addSegIdx === null ? '新建空白文字页' : '添加环节文字页' }}</el-button>
        </div>
        <ol class="slide-outline">
          <li v-for="(s, i) in currentDeckSlides" :key="i">
            <span class="slide-kind">{{ kindLabel(s.kind) }}</span>{{ s.title || s.label }}
            <span v-if="s.kind === 'section'"> · {{ (s.persons || []).map(p => p.name).filter(Boolean).join('、') }}</span>
            <em v-if="slideHasPhoto(s)"> · 含头像</em>
            <el-button link size="small" class="slide-op" :disabled="i === 0" @click="moveSlide(i, -1)">▲</el-button>
            <el-button link size="small" class="slide-op" :disabled="i === currentDeckSlides.length - 1" @click="moveSlide(i, 1)">▼</el-button>
            <el-button v-if="i !== 0 && i !== currentDeckSlides.length - 1" link type="danger" size="small"
              class="slide-op" @click="removeSlide(i)">删除</el-button>
          </li>
        </ol>
      </details>
    </section>

    <el-dialog v-model="pagePreviewOpen" append-to-body title="页面预览" width="min(900px, 94vw)" @closed="clearPagePreview">
      <p v-if="pagePreviewLoading" role="status">正在生成预览…</p>
      <p v-else-if="pagePreviewError" role="alert">{{ pagePreviewError }}</p>
      <div v-else-if="pagePreviewUrl" class="single-page-editor">
        <iframe ref="singlePageFrame" :src="pagePreviewUrl" sandbox="allow-scripts" @load="singlePageLoaded = true"
          class="page-preview-frame" title="串场单页预览"></iframe>
        <button v-if="canEditSinglePage && singlePageLoaded && !singlePageEditing" type="button" class="single-edit-ball"
          aria-label="编辑当前幻灯片" @click="startSingleEdit">编辑</button>
        <div v-if="singlePageEditing" class="single-editor-tools" role="group" aria-label="幻灯片编辑工具">
          <span v-if="!elementSettings">点击文字或头像，也可按住 Alt 拖动</span>
          <template v-else>
            <label v-if="!avatarSelected">字体
              <el-select v-model="elementSettings.font" size="small" @change="sendElementSettings">
                <el-option label="主题默认" value="" /><el-option label="微软雅黑" value="sans" />
                <el-option label="宋体" value="serif" /><el-option label="楷体" value="kai" />
              </el-select>
            </label>
            <label>{{ avatarSelected ? '头像大小 %' : '字号 %' }}
              <el-input-number v-model="elementSettings.scale" :min="40" :max="250" :step="5" size="small" @change="sendElementSettings" />
            </label>
            <label>水平位移
              <el-input-number v-model="elementSettings.x" :min="-1920" :max="1920" :step="10" size="small" @change="sendElementSettings" />
            </label>
            <label>垂直位移
              <el-input-number v-model="elementSettings.y" :min="-1080" :max="1080" :step="10" size="small" @change="sendElementSettings" />
            </label>
          </template>
          <template v-if="isSingleResume">
            <label>简历段落字体
              <el-select v-model="resumeFormatting.font" size="small">
                <el-option label="主题默认" value="" /><el-option label="微软雅黑" value="sans" />
                <el-option label="宋体" value="serif" /><el-option label="楷体" value="kai" />
              </el-select>
            </label>
            <label>简历段落字号 %
              <el-input-number v-model="resumeFormatting.scale" :min="40" :max="250" :step="5" size="small" />
            </label>
            <el-button size="small" @click="postSinglePage('pmo-slide-bulk-resume', { ...resumeFormatting })">应用全部段落</el-button>
          </template>
          <el-button v-if="elementSettings && !avatarSelected" type="danger" plain size="small"
            @click="removeSingleText">删除文本框</el-button>
          <el-button type="primary" size="small" :loading="singlePageSaving" @click="postSinglePage('pmo-slide-save')">保存本页</el-button>
        </div>
      </div>
    </el-dialog>

    <footer class="wiz-foot">
      <el-button v-if="step > 1" :disabled="busy" @click="step -= 1">上一步</el-button>
      <el-button v-if="step < 4" type="primary" :disabled="busy || (step === 1 && !experts.length)" @click="step += 1">
        下一步
      </el-button>
    </footer>
  </main>
</template>

<script setup>
import '../../../../backend/src/main/resources/show-fonts/cover-fonts.css'
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElNotification } from 'element-plus'
import CropAvatar from '../../components/CropAvatar.vue'
import { fetchPhotoBlob, getExpert } from '../../api/expert'
import { getProjectDetail, getScheduleOverview, getShowAgenda, getStageForm, previewShow, publishShow, saveStageForm } from '../../api/project'
import {
  ROLE_OPTIONS, agendaRows, classifyRow, dedupePhotos, normalizeName, roleLabel, splitResumeItems
} from '../../utils/showDeck'

// 四步向导（2026-11-21 v4 结构引擎）：①模板+封面 ②专家信息（全局资料池）③日程与角色（按议题段落，
// 直接调用②资料，角色沿用日程解析四值）④内嵌预览所见即所得编辑+页面增删排序。
// 稿件结构：封面 → 段内先「大会主持」过场页+简介页 → 「大会讲者」过场页+简介页 → 讨论嘉宾按相同时间段
// 同页过场+各简介页 → … → 致谢；照片全稿池化（ph: 引用）。
const templates = [
  { id: 'gold', name: '红金盛典', desc: '深红金流光，适合庆典与开幕', preview: 'radial-gradient(120% 100% at 50% 40%, #7e1414, #3c0707)' },
  { id: 'tech', name: '深蓝科技', desc: '深蓝网格光束，学术论坛', preview: 'linear-gradient(135deg, #0e3153, #145371)' },
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
const props = defineProps({ projectId: [String, Number] })
const emit = defineEmits(['close', 'archived'])
const id = props.projectId ?? route.params.id
const step = ref(1)
const template = ref('gold')
const fontScale = ref(1)
const editable = ref(false)
const phaseId = ref(null)
const project = ref({})
const cover = ref({ org: '', title: '', sub: '', place: '', chair: '' })
const defaultLayout = [
  { key: 'org', label: '机构 / 主办方', x: 50, y: 20 },
  { key: 'title', label: '主标题', x: 50, y: 38 },
  { key: 'sub', label: '副标题', x: 50, y: 55 },
  { key: 'place', label: '会议地点', x: 50, y: 69 },
  { key: 'chair', label: '大会主席', x: 50, y: 79 }
]
const layout = ref(defaultLayout.map(normalizeCoverField))
const coverCanvas = ref(null)
const background = ref('')
const avatarFrame = ref('')
const defaultFrameFit = { scale: 82, x: 0, y: 0 }
const frameFit = ref({ ...defaultFrameFit })
const selectedKey = ref('')
const selectedField = computed(() => layout.value.find(field => field.key === selectedKey.value))
const coverFonts = [
  { value: 'default', label: '主题默认', family: 'inherit' },
  { value: 'ma-shan', label: '马善政毛笔楷书', family: 'PMO Ma Shan' },
  { value: 'zhi-mang', label: '志莽行书', family: 'PMO Zhi Mang' }
]
const draggedPage = ref(null)
const detectingPeople = new Set()
let detectNotice
let fieldDrag
const customFields = computed(() => layout.value.filter(field => !Object.hasOwn(cover.value, field.key)))
const coverFields = computed(() => layout.value.map(field => ({
  ...field,
  value: Object.hasOwn(cover.value, field.key) ? cover.value[field.key] : field.value || ''
})))
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

const pagePreviewOpen = ref(false)
const pagePreviewUrl = ref('')
const pagePreviewLoading = ref(false)
const pagePreviewError = ref('')
const singlePageFrame = ref(null)
const singlePageIndex = ref(-1)
const singlePageEditing = ref(false)
const singlePageSaving = ref(false)
const singlePageLoaded = ref(false)
const elementSettings = ref(null)
const avatarSelected = ref(false)
const resumeFormatting = ref({ font: '', scale: 100 })
const isSingleResume = computed(() => currentDeckSlides.value[singlePageIndex.value]?.kind === 'resume')
const canEditSinglePage = computed(() => editable.value && step.value >= 3 &&
  singlePageIndex.value >= 0 && currentDeckSlides.value[singlePageIndex.value]?.kind !== 'cover')
let pagePreviewRequest = 0
let portraitsReady = Promise.resolve()

function buildSpeakers(raw) {
  const linkedByName = new Map()
  const personByRecord = new Map()
  const pool = new Map()
  const nameOf = sp => normalizeName(sp.expertName || sp.name || '')
  for (const sp of raw) {
    if (!sp.expertId) continue
    const name = nameOf(sp)
    if (!linkedByName.has(name)) linkedByName.set(name, new Set())
    linkedByName.get(name).add(String(sp.expertId))
  }
  for (const sp of raw) {
    const name = nameOf(sp)
    const linked = linkedByName.get(name)
    const expertId = sp.expertId || (linked?.size === 1 ? [...linked][0] : '')
    const key = expertId ? `id:${expertId}` : `name:${name}`
    const unit = sp.expertUnit || sp.unit || ''
    const title = sp.expertTitle || sp.title || ''
    if (!pool.has(key)) {
      pool.set(key, { key, expertId, name, unit, title, intro: '', photoSrc: '', avatar: '',
        originalAvatar: '', loading: false, loaded: false, roleByGroup: {} })
    } else if (sp.expertId) {
      const person = pool.get(key)
      person.unit ||= unit
      person.title ||= title
    }
    personByRecord.set(sp, key)
  }
  return { people: [...pool.values()], personByRecord }
}

onMounted(async () => {
  const detail = await getProjectDetail(id)
  project.value = detail
  cover.value.title = detail.name || ''
  const phase = detail.phases?.find(item => item.name === '串场制作')
  editable.value = Boolean(detail.canOperate && phase && String(detail.phaseId) === String(phase.id))
  phaseId.value = phase?.id || null
  const [agenda, overview] = await Promise.all([getShowAgenda(id), getScheduleOverview(id)])
  const rawSpeakers = overview?.speakers || []
  const { people, personByRecord } = buildSpeakers(rawSpeakers)
  experts.value = people
  const byKey = new Map(experts.value.map(p => [p.key, p]))
  // 同名议题按各自时段分组，未标时间的环节保留相对顺序。
  const gmap = []
  const startMinutes = time => {
    const match = time.match(/(\d{1,2})[:：](\d{2})/)
    return match ? Number(match[1]) * 60 + Number(match[2]) : Infinity
  }
  for (const spRaw of rawSpeakers) {
    const person = byKey.get(personByRecord.get(spRaw))
    if (!person) continue
    const title = spRaw.segmentTitle || '未分组环节'
    const time = (spRaw.segmentTime || '').trim().replace(/[：]/g, ':').replace(/[–—－~～至]/g, '-').replace(/\s+/g, '')
    let g = gmap.find(x => x.title === title && x.time === time)
    if (!g) {
      g = { title, time, members: [], times: time ? [time] : [] }
      gmap.push(g)
    }
    if (g.members.some(m => m.person.key === person.key)) continue
    g.members.push({
      person,
      time,
      roleLabelV: roleLabel(spRaw.role, title, '')
    })
  }
  groups.value = gmap.sort((a, b) => startMinutes(a.time) - startMinutes(b.time)).map((g, index) => {
    for (const m of g.members) {
      if (m.person.roleByGroup[index] === undefined) m.person.roleByGroup[index] = m.roleLabelV
    }
    return { ...g, index }
  })
  initCover()
  if (phaseId.value) {
    void getStageForm(id, phaseId.value).then(saved => {
      const draft = saved?.formData
      if (!Array.isArray(draft?.coverFields)) return
      loadDesign({ ...draft, theme: draft.template, fields: draft.coverFields }, true)
      avatarFrame.value = draft.avatarFrame || ''
      frameFit.value = { ...defaultFrameFit, ...draft.frameFit }
      if (fontScales.some(scale => scale.value === draft.fontScale)) fontScale.value = draft.fontScale
      if (Array.isArray(draft.slides) && draft.slides.length) restorePages(draft)
    }).catch(() => ElMessage.warning('无法读取已保存的封面和头像框'))
  }
  // 手动文字页候选：日程行（含无专家环节）
  const { rows } = agendaRows(agenda, overview)
  textPageSources.value = rows.slice(0, 150).map(row => {
    const { label, title } = classifyRow(row)
    return { label, title: withoutTime(title), row: withoutTime(row) }
  })
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
  cover.value.place = detail.cityName || ''
  const chairPerson = experts.value.find(p => roleSummary(p).includes('大会主席'))
  cover.value.chair = chairPerson ? [chairPerson.name, chairPerson.title].filter(Boolean).join(' ') : ''
}

// 专家载入（池内共享对象，loaded 标志保证只载一次）
watch(experts, list => {
  const safeList = list || []
  portraitsReady = Promise.all(safeList.map(loadPerson))
}, { immediate: true })

async function loadPerson(p) {
  if (!p.expertId || p.loaded || p.loading) return
  p.loading = true
  try {
    const expert = await getExpert(p.expertId)
    if (!p.intro) p.intro = expert?.原始简介 || expert?.简介 || ''
    if (expert?.照片) {
      const { data } = await fetchPhotoBlob(expert.照片)
      const url = URL.createObjectURL(data)
      try {
        const image = new Image()
        image.src = url
        await image.decode()
        const scale = Math.min(1, 800 / Math.max(image.naturalWidth, image.naturalHeight))
        const canvas = document.createElement('canvas')
        canvas.width = Math.max(1, Math.round(image.naturalWidth * scale))
        canvas.height = Math.max(1, Math.round(image.naturalHeight * scale))
        canvas.getContext('2d').drawImage(image, 0, 0, canvas.width, canvas.height)
        p.originalAvatar = canvas.toDataURL('image/webp', 0.85)
        p.photoSrc = p.originalAvatar
      } finally {
        URL.revokeObjectURL(url)
      }
    }
    p.loaded = true
  } catch {
    // 专家库读取失败不阻塞向导，字段可手填
  } finally {
    p.loading = false
  }
}

// 稿件编排（v4）：段内先主持过场+简介 → 讲者过场+简介 → 讨论嘉宾按相同时间段同页过场+各简介；
// 大会主席段（全 CHAIR）自然落在日程最前的段。每个过场页统一编号。
function withoutTime(value) {
  return (value || '').replace(/[（(]?\s*\b\d{1,2}[:：]\d{2}(?:\s*[-–—－~～至]\s*\d{1,2}[:：]\d{2})?\s*[）)]?/g, ' ').trim()
}

function resumeSlide(person, groupIndex, intro) {
  return {
    label: '个人简历', title: [person.name, person.title].filter(Boolean).join(' ') || '专家简介',
    body: '', image: '', kind: 'resume', groupIndex,
    items: splitResumeItems(intro || ''), fontSize: null, persons: [person]
  }
}

const builtSlides = computed(() => {
  const slides = [coverSlideLike()]
  let secNo = 0
  const pushSection = (title, persons, groupIndex) => {
    secNo += 1
    slides.push({
      label: '', title: title || '', body: '', image: '', kind: 'section', groupIndex,
      items: [String(secNo).padStart(2, '0')],
      persons: (persons || []).slice(0, 20).map(p => ({
        role: p.role || '', name: p.name || '', title: p.title || '', unit: p.unit || '', image: p.avatar || p.originalAvatar || '', sourceKey: p.key
      })),
      fontSize: null
    })
  }
  for (const g of groups.value) {
    const memberRole = m => m.person.roleByGroup?.[g.index] || m.roleLabelV
    const byRole = role => g.members.filter(m => memberRole(m) === role)
    const title = withoutTime(g.title)
    for (const role of roleOptions) {
      const persons = byRole(role).map(m => ({ ...m.person, role }))
      if (!persons.length) continue
      pushSection(title, persons, g.index)
      persons.forEach(p => slides.push(resumeSlide({
        role: p.role, name: p.name || '', title: p.title || '', unit: p.unit || '',
        image: p.avatar || p.originalAvatar || '', sourceKey: p.key
      }, g.index, p.intro)))
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
      cover.value.place ? `会议地点|${cover.value.place}` : '',
      cover.value.chair ? `大会主席|${cover.value.chair}` : ''
    ].filter(Boolean),
    persons: null
  }
}

function thanksSlideLike() {
  return { label: '', title: '感谢参会', body: '', image: '', kind: 'ending', items: null, persons: null, fontSize: null }
}


function restorePages(draft) {
  const resolveImage = image => image?.startsWith('ph:') ? draft.photos?.[Number(image.slice(3))] || '' : image
  editableDeck.value = draft.slides.map(slide => ({
    ...slide, groupIndex: slide.groupIndex ?? undefined, image: resolveImage(slide.image),
    persons: slide.persons?.map(person => ({ ...person, image: resolveImage(person.image) })) ?? null
  }))
  syncSectionOrder(editableDeck.value)
}

function deck(base = currentDeckSlides.value, keepWizardMetadata = false) {
  const portraits = new Map(experts.value.map(p => [p.key, p.avatar || p.originalAvatar]))
  const content = base.map(({ groupIndex, ...slide }) => ({
    ...slide,
    ...(keepWizardMetadata && groupIndex !== undefined ? { groupIndex } : {}),
    persons: slide.persons?.map(({ sourceKey, ...person }) => ({
      ...person, image: portraits.get(sourceKey) || person.image,
      ...(keepWizardMetadata && sourceKey ? { sourceKey } : {})
    })) ?? null
  }))
  const { slides, photos } = dedupePhotos(content)
  return { slides, photos, background: background.value, template: template.value,
    fontScale: fontScale.value, coverFields: coverFields.value, avatarFrame: avatarFrame.value, frameFit: frameFit.value }
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
    await saveStageForm(id, { phaseId: phaseId.value, formData: deck(currentDeckSlides.value, true) })
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
    emit('archived')
  } finally {
    busy.value = false
  }
}

// 进入第四步时冻结快照：页内编辑与页面增删只写快照，保存后按快照重新渲染
watch(step, s => {
  if (s === 4) {
    editing.value = false
    addSegIdx.value = null
    loadPreview()
  }
})

// Step4 当前生效的页面列表（快照优先）
const currentDeckSlides = computed(() => editableDeck.value || builtSlides.value)

const pagesByGroup = computed(() => {
  const pages = new Map()
  currentDeckSlides.value.forEach((slide, index) => {
    if (slide.groupIndex === undefined) return
    if (!pages.has(slide.groupIndex)) pages.set(slide.groupIndex, [])
    pages.get(slide.groupIndex).push({ slide, index })
  })
  return pages
})

const pageCardKeys = new WeakMap()
let nextPageCardKey = 0

function pageCardKey(slide) {
  if (!pageCardKeys.has(slide)) pageCardKeys.set(slide, nextPageCardKey++)
  return pageCardKeys.get(slide)
}

function sameIntroduction(sectionPerson, resumePerson) {
  if (!sectionPerson || !resumePerson) return false
  if (sectionPerson.sourceKey && resumePerson.sourceKey) return sectionPerson.sourceKey === resumePerson.sourceKey
  if (!sectionPerson.name || !resumePerson.name) return false
  if (sectionPerson.role && resumePerson.role && sectionPerson.role !== resumePerson.role) return false
  return normalizeName(sectionPerson.name) === normalizeName(resumePerson.name)
}

function sectionIntroductions(slides, section) {
  return slides.filter(slide => slide.kind === 'resume' && slide.groupIndex === section.groupIndex
    && section.persons?.some(person => sameIntroduction(person, slide.persons?.[0])))
}

function syncSectionOrder(slides) {
  for (const section of slides) {
    if (section.kind !== 'section' || section.groupIndex === undefined || !section.persons?.length) continue
    const ordered = new Set()
    for (const resume of sectionIntroductions(slides, section)) {
      const matches = section.persons.filter(person => sameIntroduction(person, resume.persons?.[0]))
      if (matches.length === 1) ordered.add(matches[0])
    }
    const next = ordered.values()
    section.persons = section.persons.map(person => ordered.has(person) ? next.next().value : person)
  }
}

function missingIntroductions({ slide }) {
  if (slide.kind !== 'section') return []
  const resumes = sectionIntroductions(currentDeckSlides.value, slide)
  return (slide.persons || []).filter(person => person.name
    && !resumes.some(resume => sameIntroduction(person, resume.persons?.[0])))
}

function restoreIntroductions(entry) {
  if (!editable.value) return
  const missing = missingIntroductions(entry)
  if (!missing.length) return
  const slides = mutableSlides()
  const section = slides[entry.index]
  const resumes = sectionIntroductions(slides, section)
  let nextIndex = Math.max(entry.index, ...resumes.map(slide => slides.indexOf(slide))) + 1
  for (const person of [...section.persons].reverse()) {
    const existing = resumes.find(resume => sameIntroduction(person, resume.persons?.[0]))
    if (existing) {
      nextIndex = Math.max(entry.index + 1, slides.indexOf(existing))
    } else if (missing.some(candidate => sameIntroduction(candidate, person))) {
      const matches = experts.value.filter(candidate => person.sourceKey
        ? candidate.key === person.sourceKey
        : normalizeName(candidate.name) === normalizeName(person.name))
      const restored = resumeSlide({ ...person }, section.groupIndex, matches.length === 1 ? matches[0].intro : '')
      slides.splice(nextIndex, 0, restored)
      resumes.push(restored)
    }
  }
  syncSectionOrder(slides)
}

function clearPagePreview() {
  pagePreviewRequest += 1
  singlePageIndex.value = -1
  singlePageEditing.value = false
  singlePageSaving.value = false
  singlePageLoaded.value = false
  elementSettings.value = null
  resumeFormatting.value = { font: '', scale: 100 }
  if (pagePreviewUrl.value) URL.revokeObjectURL(pagePreviewUrl.value)
  pagePreviewUrl.value = ''
}

async function openPagePreview(index, slide = currentDeckSlides.value[index]) {
  if (!slide) return
  clearPagePreview()
  singlePageIndex.value = index
  const request = pagePreviewRequest
  pagePreviewOpen.value = true
  pagePreviewLoading.value = true
  pagePreviewError.value = ''
  try {
    if (slide.kind !== 'cover') await portraitsReady
    if (request !== pagePreviewRequest || !pagePreviewOpen.value) return
    const html = await previewShow(id, deck([slide]))
    if (request !== pagePreviewRequest || !pagePreviewOpen.value) return
    if (typeof html !== 'string' || !html.startsWith('<')) throw new Error('Invalid preview')
    pagePreviewUrl.value = URL.createObjectURL(new Blob([html], { type: 'text/html;charset=utf-8' }))
  } catch {
    if (request === pagePreviewRequest) pagePreviewError.value = '暂时无法生成页面预览，请重试。'
  } finally {
    if (request === pagePreviewRequest) pagePreviewLoading.value = false
  }
}

async function openCurrentPageEditor() {
  const index = previewFrame.value?.contentWindow?.__getPage?.()
  if (!Number.isInteger(index)) return
  await openPagePreview(index)
}

function postSinglePage(type, value) {
  singlePageFrame.value?.contentWindow?.postMessage({ type, value }, '*')
}

function startSingleEdit() {
  if (!canEditSinglePage.value) return
  singlePageEditing.value = true
  postSinglePage('pmo-slide-edit')
}

function removeSingleText() {
  postSinglePage('pmo-slide-delete-text')
  elementSettings.value = null
}

function sendElementSettings() {
  postSinglePage('pmo-slide-layout', { ...elementSettings.value })
}

async function onSinglePageMessage(event) {
  if (event.source !== singlePageFrame.value?.contentWindow || !singlePageEditing.value || !canEditSinglePage.value) return
  const message = event.data
  if (message?.type === 'pmo-slide-selected') {
    elementSettings.value = message.value
    avatarSelected.value = message.avatar
  }
  if (message?.type !== 'pmo-slide-saved' || singlePageSaving.value) return
  const index = singlePageIndex.value
  const request = pagePreviewRequest
  if (!Array.isArray(message.text) || !Array.isArray(message.layout)) return
  const pages = JSON.parse(JSON.stringify(currentDeckSlides.value))
  for (const [path, value] of message.text) writeEditPath(pages, path.replace(/^p0\./, `p${index}.`), value)
  pages[index].layout = message.layout
  singlePageSaving.value = true
  try {
    await saveStageForm(id, { phaseId: phaseId.value, formData: deck(pages, true) })
    editableDeck.value = pages
    ElMessage.success('本页已保存')
    if (request === pagePreviewRequest) await openPagePreview(index)
    if (step.value === 4) await loadPreview()
  } finally {
    if (request === pagePreviewRequest) singlePageSaving.value = false
  }
}

onMounted(() => window.addEventListener('message', onSinglePageMessage))
onUnmounted(() => {
  window.removeEventListener('message', onSinglePageMessage)
  clearPagePreview()
  detectNotice?.close()
  if (previewUrl.value) URL.revokeObjectURL(previewUrl.value)
})

async function loadPreview(restore = null) {
  if (step.value !== 4) return
  try {
    await portraitsReady
    if (step.value !== 4) return
    const html = await previewShow(id, deck())
    if (typeof html !== 'string' || !html.startsWith('<')) {
      ElMessage.error('预览渲染失败，请重试')
      return
    }
    editing.value = false
    if (previewUrl.value) URL.revokeObjectURL(previewUrl.value)
    previewUrl.value = URL.createObjectURL(new Blob([html], { type: 'text/html;charset=utf-8' }))
    const frame = previewFrame.value
    if (frame) {
      frame.onload = () => {
        const win = frame.contentWindow
        win?.addEventListener('show-editor-change', syncEditPage)
        if (restore) {
          win?.__showPage?.(restore.page)
          if (restore.editing) win?.__toggleEdit?.()
        }
        syncEditPage()
      }
      frame.src = previewUrl.value
    }
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
  const slide = currentDeckSlides.value[editPageIdx.value]
  editing.value = Boolean(win.__isEditing?.())
  moveBar.value = (slide?.kind === 'section' ? slide.persons || [] : []).map(p => ({ name: p.name || '' }))
}

// 编辑模式下调整过场页头像顺序：交换 editableDeck 中该页 persons 顺序并重渲染，保持编辑态
async function movePerson(j, dir) {
  const to = j + dir
  if (to < 0 || to >= moveBar.value.length) return
  const doc = previewFrame.value?.contentDocument
  if (!doc) return
  const slides = readPreviewEdits(doc)
  const slide = slides[editPageIdx.value]
  if (!slide?.persons?.[to]) return
  ;[slide.persons[j], slide.persons[to]] = [slide.persons[to], slide.persons[j]]
  if (slide.groupIndex !== undefined) {
    const resumes = sectionIntroductions(slides, slide)
    const ordered = [...resumes].sort((a, b) =>
      slide.persons.findIndex(person => sameIntroduction(person, a.persons?.[0]))
      - slide.persons.findIndex(person => sameIntroduction(person, b.persons?.[0])))
    const indices = resumes.map(resume => slides.indexOf(resume))
    indices.forEach((index, order) => { slides[index] = ordered[order] })
  }
  editableDeck.value = slides
  busy.value = true
  await loadPreview({ page: editPageIdx.value, editing: true })
}

// 页内编辑 → 按 data-edit 路径写回快照（person 槽位越界自动创建）→ 保存草稿 → 重新渲染
async function saveEdits() {
  const doc = previewFrame.value?.contentDocument
  if (!doc) {
    ElMessage.warning('预览尚未加载完成，请稍候')
    return
  }
  const slides = readPreviewEdits(doc)
  editableDeck.value = slides
  busy.value = true
  try {
    await saveStageForm(id, { phaseId: phaseId.value, formData: deck(currentDeckSlides.value, true) })
    ElMessage.success('页内修改已保存，预览已刷新')
    await loadPreview()
  } finally {
    busy.value = false
  }
}

function readPreviewEdits(doc) {
  const slides = JSON.parse(JSON.stringify(editableDeck.value || builtSlides.value))
  const nodes = doc.querySelectorAll('[data-edit]')
  nodes.forEach(el => writeEditPath(slides, el.getAttribute('data-edit'), el.textContent.replace(/\s+/g, ' ').trim()))
  doc.querySelectorAll('.slide[data-fs-changed]').forEach(el => {
    const idx = Array.prototype.indexOf.call(doc.querySelectorAll('#deck > .slide'), el)
    if (idx >= 0 && slides[idx]) slides[idx].fontSize = parseFloat(el.dataset.fsChanged) || null
  })
  return slides
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
    if (!slide.persons?.[j] && !text) return
    if (!slide.persons) slide.persons = []
    while (slide.persons.length <= j) slide.persons.push({ role: '', name: '', title: '', unit: '', image: '' })
    slide.persons[j][mm[2]] = text
  } else {
    slide[rest] = text
  }
}

// 手动添加文字页（无专家环节的兜底入口）：插入到致谢页之前
function addTextPage() {
  const src = textPageSources.value[addSegIdx.value] || { label: '', title: '新页面', row: '' }
  const slides = mutableSlides()
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
  mutableSlides().splice(i, 1)
  loadPreview()
}

function moveSlide(i, dir) {
  const to = i + dir
  if (to < 0 || to >= currentDeckSlides.value.length) return
  const arr = mutableSlides()
  ;[arr[i], arr[to]] = [arr[to], arr[i]]
  syncSectionOrder(arr)
  loadPreview()
}

watch(cover, () => {
  if (editableDeck.value) editableDeck.value[0] = coverSlideLike()
}, { deep: true })

function mutableSlides() {
  if (!editableDeck.value) editableDeck.value = JSON.parse(JSON.stringify(builtSlides.value))
  return editableDeck.value
}

function selectBuiltin(theme) {
  template.value = theme
  background.value = ''
}

function loadDesign(design, restoreValues = false) {
  if (templates.some(item => item.id === design.theme)) template.value = design.theme
  background.value = design.background || ''
  layout.value = design.fields.map(field => {
    const value = restoreValues ? field.value : ''
    if (restoreValues && Object.hasOwn(cover.value, field.key)) cover.value[field.key] = value
    return normalizeCoverField({ ...field, value })
  })
}

function newId() {
  const hex = Array.from(crypto.getRandomValues(new Uint8Array(16)), byte => byte.toString(16).padStart(2, '0')).join('')
  return `${hex.slice(0, 8)}-${hex.slice(8, 12)}-${hex.slice(12, 16)}-${hex.slice(16, 20)}-${hex.slice(20)}`
}

function addCoverField() {
  const field = normalizeCoverField({ key: `field-${newId()}`, label: '自定义字段', value: '', x: 50, y: 88 })
  layout.value.push(field)
  selectedKey.value = field.key
}

function removeCoverField(key) {
  layout.value = layout.value.filter(field => field.key !== key)
  if (selectedKey.value === key) selectedKey.value = ''
}

function setFieldPosition(key, x, y) {
  const field = layout.value.find(item => item.key === key)
  if (!field) return
  const halfWidth = field.width / 2 + 2
  const halfHeight = field.height / 2 + 3
  field.x = Math.round(Math.max(halfWidth, Math.min(100 - halfWidth, x)) * 10) / 10
  field.y = Math.round(Math.max(halfHeight, Math.min(100 - halfHeight, y)) * 10) / 10
}

function hasField(key) {
  return layout.value.some(field => field.key === key)
}

function normalizeCoverField(field) {
  return { width: field.key === 'title' ? 45 : 23, height: field.key === 'title' ? 13 : 8,
    scale: 100, font: 'default', ...field }
}

function restoreDefaultFields() {
  for (const field of defaultLayout) {
    if (layout.value.length >= 20) break
    if (!hasField(field.key)) layout.value.push(normalizeCoverField(field))
  }
}

function coverFieldStyle(field) {
  const font = coverFonts.find(item => item.value === field.font)
  return {
    left: `${field.x}%`, top: `${field.y}%`, width: `${field.width}%`, height: `${field.height}%`,
    fontFamily: font?.family, fontSize: `${(field.key === 'title' ? 3.35 : 1.75) * (field.scale ?? 100) / 100}cqw`
  }
}

function resizeField(field, x, y) {
  field.width = Math.round(Math.max(8, Math.min(90, x)) * 10) / 10
  field.height = Math.round(Math.max(4, Math.min(60, y)) * 10) / 10
  setFieldPosition(field.key, field.x, field.y)
}

function resizeWithKeys(event, key) {
  const offset = { ArrowLeft: [-1, 0], ArrowRight: [1, 0], ArrowUp: [0, -1], ArrowDown: [0, 1] }[event.key]
  if (!offset) return
  event.preventDefault()
  const field = layout.value.find(item => item.key === key)
  resizeField(field, field.width + offset[0], field.height + offset[1])
}

function startFieldDrag(event, key, resizing = false) {
  if (event.button !== 0) return
  const field = layout.value.find(item => item.key === key)
  const rect = coverCanvas.value.getBoundingClientRect()
  selectedKey.value = key
  fieldDrag = { key, resizing, width: field.width, height: field.height, pointerId: event.pointerId, x: field.x, y: field.y, left: event.clientX, top: event.clientY, rect }
  event.currentTarget.setPointerCapture(event.pointerId)
}

function moveFieldDrag(event) {
  if (event.pointerId !== fieldDrag?.pointerId) return
  const { key, x, y, left, top, rect, resizing, width, height } = fieldDrag
  const dx = (event.clientX - left) * 100 / rect.width
  const dy = (event.clientY - top) * 100 / rect.height
  if (resizing) resizeField(layout.value.find(field => field.key === key), width + dx * 2, height + dy * 2)
  else setFieldPosition(key, x + dx, y + dy)
}

function stopFieldDrag(event) {
  if (event.pointerId === fieldDrag?.pointerId) fieldDrag = null
}

function nudgeField(event, key) {
  const offset = { ArrowLeft: [-1, 0], ArrowRight: [1, 0], ArrowUp: [0, -1], ArrowDown: [0, 1] }[event.key]
  if (!offset) return
  event.preventDefault()
  const field = layout.value.find(item => item.key === key)
  setFieldPosition(key, field.x + offset[0], field.y + offset[1])
}

async function selectArtwork(event, isFrame) {
  const input = event.target
  const file = input.files[0]
  input.value = ''
  if (!file) return
  const formats = isFrame ? ['image/png', 'image/webp'] : ['image/png', 'image/jpeg', 'image/webp']
  if (!formats.includes(file.type) || file.size > 8 * 1024 * 1024) {
    ElMessage.warning('请选择允许的图片格式，文件最多 8 MB')
    return
  }
  const url = URL.createObjectURL(file)
  try {
    const image = new Image()
    image.src = url
    await image.decode()
    const scale = Math.min(1, (isFrame ? 512 : 1600) / Math.max(image.naturalWidth, image.naturalHeight))
    const canvas = document.createElement('canvas')
    canvas.width = Math.max(1, Math.round(image.naturalWidth * scale))
    canvas.height = Math.max(1, Math.round(image.naturalHeight * scale))
    const context = canvas.getContext('2d')
    context.drawImage(image, 0, 0, canvas.width, canvas.height)
    if (isFrame && context.getImageData(Math.floor(canvas.width / 2), Math.floor(canvas.height / 2), 1, 1).data[3] > 32) {
      ElMessage.warning('头像框的中心需要透明，请上传带透明通道的素材')
      return
    }
    const encoded = canvas.toDataURL('image/webp', 0.85)
    if (isFrame) {
      avatarFrame.value = encoded
      frameFit.value = { ...defaultFrameFit }
    }
    else background.value = encoded
  } catch {
    ElMessage.error('无法读取这张图片')
  } finally {
    URL.revokeObjectURL(url)
  }
}

function setDetecting(key, active) {
  if (active) detectingPeople.add(key)
  else detectingPeople.delete(key)
  if (detectingPeople.size && !detectNotice) {
    detectNotice = ElNotification({ title: '正在解析头像位置', message: '系统正在识别人脸，完成后可微调裁剪。', duration: 0 })
  } else if (!detectingPeople.size && detectNotice) {
    detectNotice.close()
    detectNotice = null
  }
}

function startPageDrag(event, entry) {
  if (!editable.value) return
  draggedPage.value = entry
  event.dataTransfer.effectAllowed = 'move'
  event.dataTransfer.setData('text/plain', String(entry.index))
}

function allowPageDrop(event, entry) {
  if (draggedPage.value?.slide.groupIndex === entry.slide.groupIndex) {
    event.preventDefault()
    event.dataTransfer.dropEffect = 'move'
  }
}

function dropPage(event, entry) {
  const source = draggedPage.value
  if (!source || source.slide.groupIndex !== entry.slide.groupIndex) return
  event.preventDefault()
  draggedPage.value = null
  moveGroupPage(source, entry)
}

function moveGroupPage(source, target) {
  if (source.index === target.index) return
  const entries = pagesByGroup.value.get(source.slide.groupIndex)
  const from = entries.findIndex(entry => entry.index === source.index)
  const to = entries.findIndex(entry => entry.index === target.index)
  if (from < 0 || to < 0) return
  const slides = mutableSlides()
  const pages = entries.map(entry => slides[entry.index])
  pages.splice(to, 0, pages.splice(from, 1)[0])
  entries.forEach((entry, index) => { slides[entry.index] = pages[index] })
  syncSectionOrder(slides)
}

function updateSnapshotRole(groupIndex, person) {
  for (const slide of editableDeck.value || []) {
    if (slide.groupIndex !== groupIndex) continue
    for (const entry of slide.persons || []) {
      if (entry.sourceKey === person.key) entry.role = person.roleByGroup[groupIndex]
    }
  }
}

function nudgePage(entry, direction) {
  const entries = pagesByGroup.value.get(entry.slide.groupIndex)
  const target = entries[entries.indexOf(entry) + direction]
  if (target) moveGroupPage(entry, target)
}

function goProject() {
  if (props.projectId != null) {
    emit('close')
    return
  }
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
.segment-pages { display: grid; grid-template-columns: repeat(auto-fit, minmax(220px, 1fr)); gap: 10px; margin-top: 14px; }
.segment-card { position: relative; min-width: 0; }
.segment-card[draggable=true] { cursor: grab; }
.segment-page { display: grid; gap: 6px; width: 100%; padding: 12px 28px 10px 12px; text-align: left;
  color: inherit; cursor: pointer; border: 0; background: transparent; }
.segment-remove { position: absolute; top: 5px; right: 5px; color: inherit; border: 0; background: transparent;
  font-size: 1.25rem; cursor: pointer; }
.segment-order { padding: 0 12px 6px; }
.cover-canvas { position: relative; width: 100%; aspect-ratio: 16 / 9; overflow: hidden; container-type: inline-size;
  background: radial-gradient(ellipse at 50% 42%, #7e1414, #3c0707); background-size: cover; background-position: center; color: #fff3dc; }
.cover-canvas-tech { background-image: radial-gradient(ellipse at 50% 30%, #0d2f4f, #04101d); color: #e9f8ff; }
.canvas-field { position: absolute; transform: translate(-50%, -50%); border: 1px dashed currentColor; }
.canvas-field.selected { z-index: 1; outline: 2px solid var(--ui-primary); }
.canvas-field-text { display: grid; place-items: center; width: 100%; height: 100%; padding: 0 .35em; border: 0;
  font: inherit; color: inherit; background: transparent; cursor: grab; touch-action: none; user-select: none; overflow-wrap: anywhere; overflow: hidden; }
.canvas-title { font-weight: 700; }
.canvas-delete, .canvas-resize { position: absolute; inline-size: 26px; block-size: 26px; padding: 0;
  border: 1px solid currentColor; border-radius: 4px; background: #211815; color: inherit; font: 16px/1 sans-serif; }
.canvas-delete { inset: -10px -10px auto auto; cursor: pointer; }
.canvas-resize { inset: auto -10px -10px auto; cursor: nwse-resize; touch-action: none; user-select: none; }
.canvas-field button:focus-visible { outline: 2px solid var(--ui-primary); }
.field-settings { margin-top: 12px; }
.frame-swatch { width: 54px; aspect-ratio: 1; object-fit: contain; }
.segment-page:focus-visible { outline: 2px solid var(--ui-primary); }
.page-preview-frame { display: block; width: 100%; aspect-ratio: 16 / 9; border: 0; }
.single-page-editor { position: relative; }
.single-edit-ball { position: absolute; top: 10px; right: 10px; width: 48px; height: 48px; border: 0;
  border-radius: 50%; background: var(--ui-primary); color: white; cursor: pointer; font: inherit; }
.single-editor-tools { position: absolute; inset: 8px 8px auto auto; display:flex; align-items:end; flex-wrap:wrap;
  gap:8px; max-width: calc(100% - 16px); padding: 9px; border-radius: 8px;
  background: #fff; color: var(--ui-text-main); box-shadow: 0 2px 12px #0004; }
.single-editor-tools label { display:grid; gap: 4px; font-size:12px; }
.single-editor-tools .el-select { width:106px; }
</style>
