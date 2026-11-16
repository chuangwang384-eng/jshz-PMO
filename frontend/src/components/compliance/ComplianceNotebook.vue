<template>
  <div class="nb-wrap" :class="{ 'nb-wrap-selected': checkedIds.length }">
    <el-alert type="info" :closable="false" class="nb-intro">
      <template #title>本记事本由「规范文档库」的收藏自动关联：在文档库中收藏规范文档后，该文档已发布的合规事项会自动出现在这里（按文档分组）；取消收藏即移除对应分组。勾选条目可推送到项目，隐藏仅对自己生效。点击文档分组或事项标题可展开详情。</template>
    </el-alert>
    <div class="nb-toolbar">
      <div class="nb-filter">
        <label id="compliance-document-filter">收藏文档</label>
        <el-select v-model="filterDocId" clearable placeholder="全部收藏文档" aria-labelledby="compliance-document-filter" @change="checked = {}">
          <el-option v-for="g in groups" :key="g.documentId" :label="g.documentTitle" :value="g.documentId" />
        </el-select>
      </div>
      <el-switch v-model="hiddenView" active-text="查看已隐藏" @change="load" />
    </div>

    <el-empty v-if="!visibleGroups.length" :description="hiddenView ? '没有已隐藏的条目' : '收藏规范文档后，可在这里查看合规事项'" :image-size="70">
      <el-button v-if="!hiddenView" type="primary" plain @click="emit('goto-docs')">浏览规范文档库</el-button>
    </el-empty>

    <section v-for="g in visibleGroups" :key="g.documentId" class="nb-group">
      <header
        class="nb-group-head nb-group-head-toggle"
        role="button"
        tabindex="0"
        :aria-expanded="groupExpanded[g.documentId] ? 'true' : 'false'"
        @click="toggleGroup(g)"
        @keydown.enter.prevent="toggleGroup(g)"
        @keydown.space.prevent="toggleGroup(g)"
      >
        <el-icon class="nb-item-chevron">
          <ArrowRight v-if="!groupExpanded[g.documentId]" />
          <ArrowDown v-else />
        </el-icon>
        <h2 class="nb-doc-title">{{ g.documentTitle }}</h2>
        <el-tag size="small" type="info" effect="plain">{{ g.items.length }} 条</el-tag>
      </header>
      <template v-if="groupExpanded[g.documentId]">
      <article v-for="it in g.items" :key="it.id" class="nb-item" :class="{ 'nb-item-selected': checked[it.id] }">
        <el-checkbox
          v-if="!hiddenView && g.documentId"
          v-model="checked[it.id]"
          :disabled="selectedDocumentId !== null && selectedDocumentId !== g.documentId"
          :aria-label="'选择事项：' + it.title"
        />
        <div class="nb-item-main">
          <h3
            class="nb-item-title"
            :class="{ 'nb-item-title-toggle': it.content || it.stage || it.timeNote || it.sourceAnchor }"
            :aria-expanded="expanded[it.id] ? 'true' : 'false'"
            role="button"
            tabindex="0"
            @click="toggle(it)"
            @keydown.enter.prevent="toggle(it)"
            @keydown.space.prevent="toggle(it)"
          >
            <el-icon v-if="it.content || it.stage || it.timeNote || it.sourceAnchor" class="nb-item-chevron">
              <ArrowRight v-if="!expanded[it.id]" />
              <ArrowDown v-else />
            </el-icon>
            {{ it.title }}
            <el-tag v-if="it.priority === 'HIGH'" size="small" type="danger">重要</el-tag>
          </h3>
          <template v-if="expanded[it.id]">
            <div v-if="it.content" class="nb-item-content">{{ it.content }}</div>
            <div class="nb-item-meta">
              <span v-if="it.stage">{{ stageText(it.stage) }}</span>
              <span v-if="it.timeNote">时间：{{ it.timeNote }}</span>
              <span v-if="it.sourceAnchor">来源：{{ it.sourceAnchor }}</span>
            </div>
          </template>
        </div>
        <div class="nb-item-ops">
          <el-button v-if="!it.hidden" link size="small" @click="doHide(it)">隐藏</el-button>
          <el-button v-else link type="primary" size="small" @click="doUnhide(it)">恢复显示</el-button>
        </div>
      </article>
      </template>
    </section>

    <aside v-if="checkedIds.length" class="nb-push-bar" aria-label="推送操作">
      <span role="status">已选 {{ checkedIds.length }} 条</span>
      <div class="nb-push-actions">
        <el-button size="small" @click="checked = {}">取消选择</el-button>
        <el-button type="primary" size="small" @click="openPush">推送到项目…</el-button>
      </div>
    </aside>

    <el-dialog v-model="pushVisible" title="推送到项目" width="min(520px, 92vw)">
      <el-alert title="首次推送将把本文档绑定到项目；之后该项目只能推送同一文档的条目。重复推送会自动跳过。" type="info" :closable="false" style="margin-bottom: 12px" />
      <el-select v-model="pushProjectId" placeholder="选择我参与的项目" style="width: 100%" size="large">
        <el-option v-for="p in projects" :key="p.projectId" :label="p.name + (p.compatible ? '' : '（已绑定其他文档）')"
                   :value="p.projectId" :disabled="!p.compatible" />
      </el-select>
      <div v-if="pushProjectCompatible === false" class="nb-hint" style="margin-top: 8px; color: #f56c6c">
        该项目已绑定「{{ pushProjectBoundTitle }}」，无法推送本文档条目。
      </div>
      <template #footer>
        <el-button @click="pushVisible = false">取消</el-button>
        <el-button type="primary" :loading="pushing" :disabled="!pushProjectId" @click="doPush">推送（{{ checkedIds.length }} 条）</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { ArrowDown, ArrowRight } from '@element-plus/icons-vue'
import {
  listNotebookItems, hideNotebookItem, unhideNotebookItem,
  listPushableProjects, pushNotebookItems
} from '../../api/compliance'

const emit = defineEmits(['goto-docs'])

const groups = ref([])
const filterDocId = ref(null)
const hiddenView = ref(false)
// 事项详情默认折叠，点击标题展开（需求 2026-11-13：减少信息密度）
const expanded = reactive({})

function toggle(it) {
  if (it.content || it.stage || it.timeNote || it.sourceAnchor) expanded[it.id] = !expanded[it.id]
}

// 文档分组默认收起，点击分组头展开；筛选到具体文档时自动展开
const groupExpanded = reactive({})

function toggleGroup(g) {
  groupExpanded[g.documentId] = !groupExpanded[g.documentId]
}

watch(filterDocId, (v) => {
  if (v) groupExpanded[v] = true
})
const checked = ref({})
const pushVisible = ref(false)
const pushProjectId = ref(null)
const projects = ref([])
const pushing = ref(false)

const visibleGroups = computed(() => !filterDocId.value
  ? groups.value : groups.value.filter(group => group.documentId === filterDocId.value))

const checkedIds = computed(() => Object.keys(checked.value).filter((k) => checked.value[k]).map(Number))

const selectedDocumentId = computed(() => groups.value.find(
  group => group.items.some(item => checked.value[item.id])
)?.documentId ?? null)

let loadSequence = 0
async function load() {
  const sequence = ++loadSequence
  const data = await listNotebookItems({ view: hiddenView.value ? 'hidden' : 'all' })
  if (sequence !== loadSequence) return
  groups.value = data || []
  if (!groups.value.some(group => group.documentId === filterDocId.value)) filterDocId.value = null
  checked.value = {}
  Object.keys(expanded).forEach((k) => { delete expanded[k] })
  Object.keys(groupExpanded).forEach((k) => { delete groupExpanded[k] })
}

function stageText(v) {
  return ({ INITIATION: '立项', FINANCE: '财务流程', EXECUTION: '执行', VERIFICATION: '会后核销', SPECIAL: '特殊问题', OTHER: '其他' })[v] || v
}

async function doHide(it) {
  await hideNotebookItem(it.id)
  ElMessage.success('已隐藏（仅影响自己）')
  load()
}

async function doUnhide(it) {
  await unhideNotebookItem(it.id)
  ElMessage.success('已恢复显示')
  load()
}

async function openPush() {
  if (selectedDocumentId.value === null) {
    ElMessage.warning('请先勾选同一文档下的条目')
    return
  }
  projects.value = await listPushableProjects(selectedDocumentId.value)
  pushProjectId.value = null
  pushVisible.value = true
}

const pushProjectCompatible = computed(() => {
  const p = projects.value.find((x) => x.projectId === pushProjectId.value)
  return p ? p.compatible : null
})
const pushProjectBoundTitle = computed(() => {
  const p = projects.value.find((x) => x.projectId === pushProjectId.value)
  return p ? p.boundDocTitle || '' : ''
})

async function doPush() {
  pushing.value = true
  try {
    const r = await pushNotebookItems(pushProjectId.value, checkedIds.value)
    ElMessage.success(`推送完成：新增 ${r.added} 条，跳过（已存在）${r.skipped} 条`)
    pushVisible.value = false
    checked.value = {}
  } finally {
    pushing.value = false
  }
}

onMounted(load)
defineExpose({ reload: load })
</script>

<style scoped>
.nb-intro { margin-bottom: 12px; }
.nb-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 12px 24px;
  padding: 12px 16px;
  margin-bottom: 16px;
  background: var(--el-bg-color);
  border: 1px solid var(--el-border-color-light);
  border-radius: 10px;
}
.nb-filter, .nb-push-actions { display: flex; align-items: center; gap: 10px; }
.nb-filter { flex: 1; min-width: 0; }
.nb-filter label { flex: none; color: var(--el-text-color-secondary); font-size: 13px; }
.nb-filter .el-select { width: min(340px, 100%); }
.nb-hint { color: var(--el-text-color-secondary); font-size: 12px; }
.nb-group {
  margin-bottom: 16px;
  background: var(--el-bg-color);
  border: 1px solid var(--el-border-color-light);
  border-radius: 10px;
}
.nb-group-head {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px 12px;
  padding: 14px 16px;
  background: var(--el-fill-color-light);
  border-radius: 10px 10px 0 0;
}
.nb-group-head-toggle { cursor: pointer; user-select: none; }
.nb-group-head-toggle:hover .nb-doc-title { color: var(--el-color-primary); }
.nb-group-head-toggle:focus-visible { outline: 2px solid var(--el-color-primary-light-5); outline-offset: -2px; }
.nb-doc-title { margin: 0; font-size: 16px; font-weight: 650; overflow-wrap: anywhere; }
.nb-item {
  display: flex;
  align-items: flex-start;
  gap: 12px;
  padding: 14px 16px;
  border-top: 1px solid var(--el-border-color-lighter);
}
.nb-item:last-child { border-radius: 0 0 10px 10px; }
.nb-item-selected { background: var(--el-color-primary-light-9); }
.nb-item :deep(.el-checkbox) { flex: none; height: 24px; margin-right: 0; }
.nb-item-main { flex: 1; min-width: 0; }
.nb-item-title { display: flex; align-items: center; flex-wrap: wrap; gap: 8px; margin: 0; font-size: 15px; line-height: 1.6; font-weight: 650; overflow-wrap: anywhere; }
.nb-item-title-toggle { cursor: pointer; user-select: none; }
.nb-item-title-toggle:hover { color: var(--el-color-primary); }
.nb-item-title-toggle:focus-visible { outline: 2px solid var(--el-color-primary-light-5); outline-offset: 2px; border-radius: 4px; }
.nb-item-chevron { flex: none; color: var(--el-text-color-secondary); font-size: 14px; }
.nb-item-content { color: var(--el-text-color-regular); margin-top: 4px; line-height: 1.65; white-space: pre-wrap; overflow-wrap: anywhere; }
.nb-item-meta { display: flex; flex-wrap: wrap; gap: 4px 14px; margin-top: 6px; color: var(--el-text-color-secondary); font-size: 12px; overflow-wrap: anywhere; }
.nb-item-meta:empty { display: none; }
.nb-item-ops { flex: none; }
.nb-push-bar {
  position: fixed;
  top: max(92px, 38vh);
  right: 24px;
  z-index: 20;
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding: 12px;
  background: var(--el-bg-color);
  border: 1px solid var(--el-color-primary-light-5);
  border-radius: 8px;
  box-shadow: var(--el-box-shadow-light);
}
.nb-push-actions { flex-wrap: wrap; gap: 6px; }
.nb-push-actions :deep(.el-button + .el-button) { margin-left: 0; }
@media (min-width: 1100px) {
  .nb-wrap-selected { padding-right: 220px; }
}
@media (max-width: 560px) {
  .nb-filter { flex-basis: 100%; }
  .nb-item { gap: 8px; padding-inline: 12px; }
  .nb-push-bar { top: 72px; right: 12px; max-width: calc(100vw - 24px); }
}
</style>
