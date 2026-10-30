<template>
  <div>
    <div class="page-head">
      <span class="page-title">项目配置</span>
      <span class="page-sub">类型字典 · 流程阶段 · 模板管理 · 阶段-模板绑定（需 project:manage）</span>
    </div>

    <el-tabs v-model="tab">
      <!-- 类型字典 -->
      <el-tab-pane label="项目类型" name="types">
        <el-card shadow="never">
          <div style="margin-bottom: 10px">
            <el-input v-model="typeForm.name" placeholder="新类型名称（展会/会议/论坛等）" style="width: 240px" />
            <el-button type="primary" style="margin-left: 8px" :disabled="!typeForm.name.trim()" @click="addType">新增类型</el-button>
          </div>
          <el-table :data="types" stripe>
            <el-table-column prop="id" label="ID" width="60" />
            <el-table-column prop="name" label="名称" />
            <el-table-column prop="sort" label="排序" width="80" />
            <el-table-column prop="createTime" label="创建时间" width="160" />
            <el-table-column label="操作" width="180">
              <template #default="{ row }">
                <el-button link type="primary" @click="startEditType(row)">编辑</el-button>
                <el-popconfirm title="确认删除该类型？（有项目引用时将拒绝）" @confirm="delType(row)">
                  <template #reference>
                    <el-button link type="danger">删除</el-button>
                  </template>
                </el-popconfirm>
              </template>
            </el-table-column>
          </el-table>
          <el-dialog v-model="editTypeVisible" title="编辑类型" width="400px" destroy-on-close>
            <el-input v-model="editTypeForm.name" placeholder="类型名称" />
            <template #footer>
              <el-button @click="editTypeVisible = false">取消</el-button>
              <el-button type="primary" :disabled="!editTypeForm.name.trim()" @click="saveType">保存</el-button>
            </template>
          </el-dialog>
        </el-card>
      </el-tab-pane>

      <!-- 流程阶段 -->
      <el-tab-pane label="流程阶段" name="phases">
        <el-card shadow="never">
          <div style="margin-bottom: 10px">
            <el-input v-model="phaseForm.name" placeholder="新阶段名称（如 筹备）" style="width: 200px" />
            <el-input-number v-model="phaseForm.sort" :min="1" placeholder="排序" style="width: 120px; margin-left: 8px" />
            <el-button type="primary" style="margin-left: 8px" :disabled="!phaseForm.name.trim()" @click="addPhase">新增阶段</el-button>
          </div>
          <el-table :data="phases" stripe>
            <el-table-column prop="id" label="ID" width="60" />
            <el-table-column prop="name" label="阶段名称" />
            <el-table-column prop="sort" label="排序" width="80" />
            <el-table-column label="启用" width="80">
              <template #default="{ row }">
                <el-tag :type="row.isActive === 1 ? 'success' : 'info'">{{ row.isActive === 1 ? '启用' : '停用' }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="绑定模板数" width="100">
              <template #default="{ row }">{{ (row.templates || []).length }}</template>
            </el-table-column>
            <el-table-column label="操作" width="180">
              <template #default="{ row }">
                <el-button link type="primary" @click="startEditPhase(row)">编辑</el-button>
                <el-popconfirm title="确认删除该阶段？（有项目引用时将拒绝）" @confirm="delPhase(row)">
                  <template #reference>
                    <el-button link type="danger">删除</el-button>
                  </template>
                </el-popconfirm>
              </template>
            </el-table-column>
          </el-table>
          <el-dialog v-model="editPhaseVisible" title="编辑阶段" width="420px" destroy-on-close>
            <el-input v-model="editPhaseForm.name" placeholder="阶段名称" />
            <el-input-number v-model="editPhaseForm.sort" :min="1" style="margin-top: 10px" />
            <el-switch v-model="editPhaseForm.isActive" active-text="启用" :active-value="1" :inactive-value="0" style="margin-top: 10px" />
            <template #footer>
              <el-button @click="editPhaseVisible = false">取消</el-button>
              <el-button type="primary" :disabled="!editPhaseForm.name.trim()" @click="savePhase">保存</el-button>
            </template>
          </el-dialog>
        </el-card>
      </el-tab-pane>

      <!-- 模板管理 -->
      <el-tab-pane label="模板管理" name="templates">
        <el-card shadow="never">
          <div style="margin-bottom: 10px">
            <el-input v-model="templateForm.name" placeholder="自定义模板名称（如 立项背景）" style="width: 240px" />
            <el-select v-model="templateForm.templateType" placeholder="模板类型" style="width: 160px; margin-left: 8px">
              <el-option label="资料附件" value="FILE" />
              <el-option label="专家" value="EXPERT" />
              <el-option label="供应商" value="SUPPLIER" />
              <el-option label="预算" value="BUDGET" />
              <el-option label="自定义文本" value="RICH_TEXT" />
            </el-select>
            <el-button type="primary" style="margin-left: 8px" :disabled="!templateForm.name.trim() || !templateForm.templateType" @click="addTemplate">新建模板</el-button>
          </div>
          <el-table :data="templates" stripe>
            <el-table-column prop="id" label="ID" width="60" />
            <el-table-column prop="name" label="模板名称" />
            <el-table-column label="模板类型" width="120">
              <template #default="{ row }">
                <el-tag size="small">{{ typeText(row.templateType) }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="createBy" label="创建人" width="100" />
          </el-table>
        </el-card>
      </el-tab-pane>

      <!-- 阶段-模板绑定 -->
      <el-tab-pane label="阶段模板绑定" name="bind">
        <el-card shadow="never">
          <div style="margin-bottom: 10px">
            <el-select v-model="bindPhaseId" placeholder="选择阶段" style="width: 220px" @change="loadBindings">
              <el-option v-for="p in phases" :key="p.id" :label="p.name" :value="p.id" />
            </el-select>
            <el-button type="primary" style="margin-left: 8px" :disabled="!bindPhaseId" @click="saveBindings">保存绑定</el-button>
          </div>
          <el-table v-if="bindPhaseId" :data="allTemplates" stripe>
            <el-table-column prop="name" label="模板名称" />
            <el-table-column label="模板类型" width="120">
              <template #default="{ row }">
                <el-tag size="small">{{ typeText(row.templateType) }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="是否绑定" width="100" align="center">
              <template #default="{ row }">
                <el-checkbox v-model="row._bound" />
              </template>
            </el-table-column>
            <el-table-column label="必填/选填" width="120" align="center">
              <template #default="{ row }">
                <el-select v-model="row._required" :disabled="!row._bound" size="small" style="width: 90px">
                  <el-option label="必填" :value="1" />
                  <el-option label="选填" :value="0" />
                </el-select>
              </template>
            </el-table-column>
            <el-table-column label="排序" width="120">
              <template #default="{ row }">
                <el-input-number v-model="row._sort" :min="1" size="small" :disabled="!row._bound" />
              </template>
            </el-table-column>
          </el-table>
          <div v-if="!bindPhaseId" class="hint-text">请先选择阶段以配置绑定关系</div>
        </el-card>
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import {
  listProjectTypes, createProjectType, updateProjectType, deleteProjectType,
  listProjectPhases, createProjectPhase, updateProjectPhase, deleteProjectPhase,
  listProjectTemplates, createProjectTemplate,
  listPhaseTemplates, savePhaseTemplates
} from '../../api/project'

const tab = ref('types')
const types = ref([])
const phases = ref([])
const templates = ref([])
const allTemplates = ref([])

const typeForm = reactive({ name: '' })
const editTypeVisible = ref(false)
const editTypeForm = reactive({ id: null, name: '' })

const phaseForm = reactive({ name: '', sort: 1 })
const editPhaseVisible = ref(false)
const editPhaseForm = reactive({ id: null, name: '', sort: 1, isActive: 1 })

const templateForm = reactive({ name: '', templateType: 'RICH_TEXT' })

const bindPhaseId = ref(null)
const bindingsMap = ref({})

function typeText(t) {
  return { FILE: '资料附件', EXPERT: '专家', SUPPLIER: '供应商', BUDGET: '预算', RICH_TEXT: '自定义文本' }[t] || t
}

async function fetchAll() {
  types.value = await listProjectTypes()
  phases.value = await listProjectPhases()
  templates.value = await listProjectTemplates()
  allTemplates.value = templates.value.map((t) => ({ ...t, _bound: false, _required: 0, _sort: 1 }))
}

async function addType() {
  await createProjectType({ name: typeForm.name.trim() })
  ElMessage.success('类型已新增')
  typeForm.name = ''
  fetchAll()
}
function startEditType(row) {
  editTypeForm.id = row.id
  editTypeForm.name = row.name
  editTypeVisible.value = true
}
async function saveType() {
  await updateProjectType(editTypeForm.id, { name: editTypeForm.name.trim() })
  ElMessage.success('类型已更新')
  editTypeVisible.value = false
  fetchAll()
}
async function delType(row) {
  await deleteProjectType(row.id)
  ElMessage.success('类型已删除')
  fetchAll()
}

async function addPhase() {
  await createProjectPhase({ name: phaseForm.name.trim(), sort: phaseForm.sort })
  ElMessage.success('阶段已新增')
  phaseForm.name = ''
  phaseForm.sort = 1
  fetchAll()
}
function startEditPhase(row) {
  editPhaseForm.id = row.id
  editPhaseForm.name = row.name
  editPhaseForm.sort = row.sort
  editPhaseForm.isActive = row.isActive
  editPhaseVisible.value = true
}
async function savePhase() {
  await updateProjectPhase(editPhaseForm.id, {
    name: editPhaseForm.name.trim(),
    sort: editPhaseForm.sort,
    isActive: editPhaseForm.isActive
  })
  ElMessage.success('阶段已更新')
  editPhaseVisible.value = false
  fetchAll()
}
async function delPhase(row) {
  await deleteProjectPhase(row.id)
  ElMessage.success('阶段已删除')
  fetchAll()
}

async function addTemplate() {
  await createProjectTemplate({ name: templateForm.name.trim(), templateType: templateForm.templateType })
  ElMessage.success('模板已新建')
  templateForm.name = ''
  fetchAll()
}

async function loadBindings() {
  const binds = await listPhaseTemplates(bindPhaseId.value)
  bindingsMap.value = {}
  binds.forEach((b) => {
    bindingsMap.value[String(b.templateId)] = b
  })
  allTemplates.value.forEach((t) => {
    const b = bindingsMap.value[String(t.id)]
    t._bound = !!b
    t._required = b ? b.required : 0
    t._sort = b ? b.sort : 1
  })
}

async function saveBindings() {
  const binds = allTemplates.value
    .filter((t) => t._bound)
    .map((t) => ({ templateId: t.id, required: t._required, sort: t._sort }))
  await savePhaseTemplates(bindPhaseId.value, binds)
  ElMessage.success('绑定已保存')
  loadBindings()
}

onMounted(fetchAll)
</script>

<style scoped>
.page-head { margin-bottom: 12px; }
.page-title { font-size: 18px; font-weight: 600; }
.page-sub { margin-left: 8px; color: #909399; font-size: 13px; }
.hint-text { color: #909399; font-size: 13px; }
</style>
