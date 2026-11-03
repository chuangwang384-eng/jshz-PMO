<template>
  <div v-loading="loading">
    <el-alert title="上传者配置规范细项；到期日、优先级、进度和备注由员工在执行清单中配置。发布需要维护人权限。" :closable="false" />
    <div class="toolbar">
      <el-select v-model="documentId" placeholder="选择规范文档" filterable @change="selectDocument" aria-label="模板来源文档">
        <el-option v-for="d in docs" :key="d.id" :label="d.title" :value="d.id" />
      </el-select>
      <el-button :disabled="!documentId" @click="newDraft">新建草稿</el-button>
      <el-select v-model="versionId" placeholder="已发布版本" @change="openVersion">
        <el-option v-for="v in versions" :key="v.id" :label="`v${v.revision} · ${v.title}`" :value="v.id" />
      </el-select>
      <el-button :disabled="!versionId" @click="copyDraft">复制为新修订</el-button>
    </div>
    <el-form v-if="draft" label-width="100px" :disabled="draft.state !== 'DRAFT'">
      <el-form-item label="模板名称"><el-input v-model="draft.title" maxlength="200" /></el-form-item>
      <el-form-item label="适用说明"><el-input v-model="draft.applicability" type="textarea" /></el-form-item>
      <el-card v-for="(n, i) in draft.nodes" :key="i" class="node-card" shadow="never">
        <el-form-item label="细项标题"><el-input v-model="n.title" maxlength="200" /></el-form-item>
        <el-form-item label="填写方式">
          <el-select v-model="n.inputType" @change="ensureOptions(n)"><el-option v-for="t in types" :key="t.value" :label="t.label" :value="t.value" /></el-select>
          <el-checkbox v-model="n.required" :true-value="1" :false-value="0">完成前必填</el-checkbox>
        </el-form-item>
        <el-form-item label="操作要求"><el-input v-model="n.instruction" type="textarea" /></el-form-item>
        <el-form-item label="所需材料"><el-input v-model="n.materials" type="textarea" /></el-form-item>
        <el-form-item label="完成标准"><el-input v-model="n.acceptance" type="textarea" /></el-form-item>
        <el-form-item label="规范时限"><el-input v-model="n.timeRule" placeholder="规范原文的时间要求，不是本次执行日期" /></el-form-item>
        <el-form-item label="原文定位"><el-input v-model="n.sourceAnchor" placeholder="如第 3 页 / 第二节" /></el-form-item>
        <template v-if="['SINGLE', 'MULTI'].includes(n.inputType)">
          <el-form-item v-for="(o, j) in n.options" :key="j" :label="`选项 ${j + 1}`">
            <el-input v-model="o.label" placeholder="如协会名称" />
            <el-input v-model="o.guidance" placeholder="该选项的具体要求" />
            <el-button @click="n.options.splice(j, 1)">移除选项</el-button>
          </el-form-item>
          <el-button @click="n.options.push({ optionCode: uniqueCode(), label: '', guidance: '' })">添加选项</el-button>
        </template>
        <el-button type="danger" plain @click="draft.nodes.splice(i, 1)">移除细项</el-button>
      </el-card>
      <el-button @click="addNode">添加细项</el-button>
      <el-button type="primary" :loading="saving" @click="save">保存草稿</el-button>
      <el-button v-if="canPublish" type="success" :loading="saving" @click="publish">保存并发布</el-button>
    </el-form>
    <el-empty v-else description="选择文档后新建草稿，或打开已发布版本" />
    <el-divider />
    <div class="toolbar">
      <el-input v-model="resumeId" placeholder="草稿版本编号" aria-label="草稿版本编号" style="width: 180px" />
      <el-button :disabled="!/^\d+$/.test(resumeId)" @click="resumeDraft">继续编辑草稿</el-button>
      <span v-if="draft">当前版本编号：{{ draft.id }} · {{ draft.state }}</span>
    </div>
  </div>
</template>
<script setup>
import { onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import request from '../../api/request'
import { listKnowledgeDocs } from '../../api/knowledge'
import { listPublishedTemplates, createTemplateDraft, getTemplateDetail, patchTemplate, publishTemplate } from '../../api/complianceV4'
const docs = ref([]), versions = ref([]), documentId = ref(null), versionId = ref(null), draft = ref(null)
const loading = ref(false), saving = ref(false), canPublish = ref(false), resumeId = ref('')
const types = [{ value: 'CHECK', label: '确认事项' }, { value: 'TEXT', label: '文本填写' }, { value: 'SINGLE', label: '单选' }, { value: 'MULTI', label: '多选' }]
const uniqueCode = () => 'code-' + Date.now() + '-' + Math.random().toString(36).slice(2)
async function selectDocument() { draft.value = null; versionId.value = null; versions.value = await listPublishedTemplates(documentId.value) }
async function newDraft() { draft.value = await createTemplateDraft(documentId.value, {}); resumeId.value = String(draft.value.id) }
async function openVersion() { draft.value = await getTemplateDetail(versionId.value) }
async function copyDraft() { draft.value = await createTemplateDraft(documentId.value, { copyFromVersionId: versionId.value }); resumeId.value = String(draft.value.id) }
async function resumeDraft() { draft.value = await getTemplateDetail(resumeId.value); documentId.value = draft.value.documentId }
function addNode() { draft.value.nodes.push({ nodeCode: uniqueCode(), title: '', inputType: 'CHECK', required: 0, options: [] }) }
function ensureOptions(n) { n.options ||= [] }
async function persist() {
  if (!draft.value.title?.trim() || !draft.value.nodes.length) throw new Error('请填写模板名称并添加细项')
  const nodes = draft.value.nodes.map(n => {
    if (!n.title?.trim()) throw new Error('请填写每个细项标题')
    const out = { ...n }
    if (['SINGLE', 'MULTI'].includes(n.inputType)) {
      if (!n.options?.length || n.options.some(o => !o.label?.trim())) throw new Error('选择题需要完整选项')
    } else delete out.options
    return out
  })
  draft.value = await patchTemplate(draft.value.id, { title: draft.value.title, applicability: draft.value.applicability, nodes, rowVersion: draft.value.rowVersion })
}
async function save() { saving.value = true; try { await persist(); ElMessage.success('草稿已保存，请记录版本编号以继续编辑') } catch (e) { ElMessage.error(e.message) } finally { saving.value = false } }
async function publish() {
  try { await ElMessageBox.confirm('发布后版本不可原地修改，确认规范细项完整？', '发布模板') } catch { return }
  saving.value = true
  try { await persist(); await publishTemplate(draft.value.id); draft.value = await getTemplateDetail(draft.value.id); versions.value = await listPublishedTemplates(documentId.value); ElMessage.success('已发布') }
  catch (e) { ElMessage.error(e.message) } finally { saving.value = false }
}
onMounted(async () => {
  loading.value = true
  try { const [d, me] = await Promise.all([listKnowledgeDocs({ category: 'enterprise-compliance', page: 1, pageSize: 100 }), request.get('/auth/me')]); docs.value = d.records || []; canPublish.value = !!(me.isAdmin || me.permissions?.includes('knowledge:maintain')) }
  finally { loading.value = false }
})
</script>
<style scoped>
.toolbar { display: flex; gap: 12px; flex-wrap: wrap; align-items: center; margin: 16px 0; }
.node-card { margin-bottom: 16px; }
</style>
