<template>
  <div class="quick-access" aria-label="快捷访问">
    <span class="qa-label">快捷访问</span>
    <template v-if="!editing">
      <router-link v-for="item in visible" :key="item.id" :to="item.path" class="qa-item">
        <component :is="iconOf(item.id)" class="qa-icon"/> {{ item.title }}
      </router-link>
      <el-dropdown v-if="hidden.length" trigger="click" @command="go">
        <button type="button" class="qa-item qa-more">更多 <el-icon><ArrowDown/></el-icon></button>
        <template #dropdown>
          <el-dropdown-menu>
            <el-dropdown-item v-for="item in hidden" :key="item.id" :command="item.path">
              <component :is="iconOf(item.id)" class="qa-icon"/> {{ item.title }}
            </el-dropdown-item>
          </el-dropdown-menu>
        </template>
      </el-dropdown>
      <button type="button" class="qa-edit" @click="startEdit">编辑</button>
    </template>
    <template v-else>
      <div class="qa-edit-area" @dragover.prevent>
        <span v-for="(item, index) in draft" :key="item.id" class="qa-item qa-draft" draggable="true"
              @dragstart="dragIndex=index" @dragover.prevent @drop.prevent="reorder(index)">
          <component :is="iconOf(item.id)" class="qa-icon"/> {{ item.title }}
          <span role="button" tabindex="0" class="qa-remove" aria-label="移除" @click="draft.splice(index,1)">×</span>
        </span>
        <span v-if="!draft.length" class="qa-empty-tip">已全部移除，可从下方候选添加</span>
        <span class="qa-add">
          <span v-for="item in addable" :key="item.id" role="button" tabindex="0" class="qa-item qa-candidate"
                @click="draft.push(item)" @keydown.enter.prevent="draft.push(item)">
            + <component :is="iconOf(item.id)" class="qa-icon"/> {{ item.title }}
          </span>
          <span v-if="!addable.length" class="qa-empty-tip">暂无可添加候选</span>
        </span>
      </div>
      <div class="qa-edit-actions">
        <el-button size="small" type="primary" :loading="saving" @click="save">保存</el-button>
        <el-button size="small" :disabled="saving" @click="cancel">取消</el-button>
        <el-button size="small" :disabled="saving" @click="resetDefault">恢复默认</el-button>
      </div>
    </template>
  </div>
</template>
<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { ArrowDown, Bell, Checked, Collection, Folder, Iphone, Message, OfficeBuilding, Shop, Tickets, User } from '@element-plus/icons-vue'
import { getQuickAccess, saveQuickAccess } from '../api/dashboard'

const router = useRouter()

const props = defineProps({ candidates: { type: Array, required: true } })
const ICONS = { 'page.project': Folder, 'page.experts': User, 'page.knowledge': Collection, 'page.customer': OfficeBuilding, 'page.supplier': Shop, 'page.myApplications': Tickets, 'page.announcements': Bell, 'page.directory': Iphone, 'page.notifications': Message, 'page.approvalManage': Checked }
const iconOf = id => ICONS[id] || Folder

const config = ref(null)          // 服务端已保存配置；null=未配置，用默认顺序
const editing = ref(false), saving = ref(false), draft = ref([]), dragIndex = ref(-1)

/** 默认顺序 = 候选集原顺序（与旧固定入口一致）。 */
const defaultEnabled = computed(() => props.candidates.slice())
/** 已启用：过滤掉已撤权的过期配置项，再按保存顺序补回候选顺序之外的新可用项。 */
const enabled = computed(() => {
  if (!config.value) return defaultEnabled.value
  const known = new Set(props.candidates.map(c => c.id))
  const ordered = config.value.filter(id => known.has(id)).map(id => props.candidates.find(c => c.id === id))
  return ordered
})
const visible = computed(() => enabled.value.slice(0, 6))
const hidden = computed(() => enabled.value.slice(6))
const addable = computed(() => props.candidates.filter(c => !draft.value.some(d => d.id === c.id)))

function startEdit() { draft.value = enabled.value.slice(); editing.value = true }
function cancel() { editing.value = false; dragIndex.value = -1 }
function reorder(toIndex) {
  const from = dragIndex.value
  if (from < 0 || from === toIndex || toIndex >= draft.value.length) return
  const [moved] = draft.value.splice(from, 1)
  draft.value.splice(toIndex, 0, moved)
  dragIndex.value = -1
}
function resetDefault() { draft.value = defaultEnabled.value.slice() }
async function save() {
  if (!draft.value.length) { ElMessage.warning('至少保留一个快捷入口'); return }
  saving.value = true
  try {
    const { items } = await saveQuickAccess(draft.value.map(d => d.id))
    config.value = items
    editing.value = false
    ElMessage.success('快捷访问已保存')
  } catch (e) {
    ElMessage.error(e?.response?.data?.message || '保存失败，请重试（编辑内容已保留）')
  } finally { saving.value = false }
}
function go(path) { if (path) router.push(path) }

onMounted(async () => {
  try {
    const { items } = await getQuickAccess()
    if (Array.isArray(items) && items.length) config.value = items
  } catch (e) { /* 接口失败回退默认入口，不阻塞页面 */ }
})
</script>
<style scoped>
.quick-access{display:flex;align-items:center;gap:8px;flex-wrap:wrap;padding-top:10px;border-top:1px solid var(--c-border,#e2e8f0)}
.qa-label{font-size:12px;color:var(--c-text-sub,#64748b);flex-shrink:0}
.qa-item{display:inline-flex;align-items:center;gap:6px;padding:5px 12px;border:1px solid var(--c-border,#e2e8f0);border-radius:8px;background:var(--c-card-bg,#fff);color:var(--c-text,#0f172a);font-size:12px;text-decoration:none;cursor:pointer;transition:border-color .15s,background .15s}
.qa-item:hover{border-color:var(--c-primary,#2563eb);background:#eff6ff}
.qa-icon{width:14px;height:14px;color:var(--c-primary,#2563eb)}
.qa-more{font-family:inherit}
.qa-edit{border:0;background:none;color:var(--c-text-sub,#64748b);font-size:12px;cursor:pointer;padding:5px 6px}
.qa-edit:hover{color:var(--c-primary,#2563eb)}
.qa-edit-area{display:flex;align-items:center;gap:8px;flex-wrap:wrap;flex:1}
.qa-draft{cursor:grab}
.qa-remove{margin-left:2px;color:var(--c-text-sub,#64748b);font-size:13px;line-height:1}
.qa-remove:hover{color:#dc2626}
.qa-candidate{border-style:dashed;color:var(--c-text-sub,#64748b)}
.qa-empty-tip{font-size:11px;color:var(--c-text-sub,#64748b)}
.qa-edit-actions{display:flex;align-items:center;gap:6px}
button:focus-visible,[role="button"]:focus-visible{outline:2px solid #2864d8;outline-offset:2px}
</style>
