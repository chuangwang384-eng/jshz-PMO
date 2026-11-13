<template>
  <el-dialog
    :model-value="visible"
    :title="type ? '编辑项目类型' : '新增项目类型'"
    width="min(1040px, 95vw)"
    top="4vh"
    :close-on-click-modal="false"
    :close-on-press-escape="false"
    :before-close="close"
    @open="open"
  >
    <el-steps :active="step - 1" align-center finish-status="success">
      <el-step title="类型信息" /><el-step title="安排阶段" /><el-step
        title="设置员工需提交的内容"
      />
    </el-steps>
    <el-alert
      class="space"
      title="配置只影响以后创建的项目，已有项目继续使用原来的流程和材料要求。"
      type="info"
      :closable="false"
    />
    <div v-if="error" class="space">
      <el-alert :title="error" type="error" :closable="false" /><el-button
        @click="open"
        >重新加载配置</el-button
      >
    </div>
    <div v-else-if="loading" class="body">
      <el-skeleton :rows="5" animated />
    </div>
    <div v-else class="body" :inert="saving">
      <el-form v-show="step === 1" label-width="100px">
        <el-form-item label="类型名称" required
          ><el-input
            v-model="draft.name"
            maxlength="50"
            placeholder="例如：展会项目"
        /></el-form-item>
        <el-form-item label="展示位置"
          ><el-input-number
            v-model="draft.position"
            :min="1"
            :max="maxPosition"
          /><span class="hint"
            >其他类型自动顺移，不会出现重复名次</span
          ></el-form-item
        >
        <ol class="positions">
          <li v-for="item in positionPreview" :key="item.id">
            {{ item.name }}
          </li>
        </ol>
      </el-form>
      <div v-show="step === 2">
        <p>
          从公共阶段库选取，或直接新建。新阶段仅加入当前流程，保存后供其他类型选用。
        </p>
        <div class="toolbar ui-toolbar">
          <el-select
            v-model="selected"
            filterable
            placeholder="搜索已有阶段"
            style="width: 230px"
          >
            <el-option
              v-for="p in available"
              :key="p.id"
              :label="p.name"
              :value="p.id"
            />
          </el-select>
          <el-button :disabled="!selected" @click="addExisting"
            >添加已有阶段</el-button
          >
          <el-input
            v-model="newName"
            maxlength="50"
            placeholder="没有合适的？输入新阶段名称"
            style="width: 260px"
            @keyup.enter="addNew"
          />
          <el-button @click="addNew">新建并加入</el-button>
          <el-button v-if="!draft.phases.length" @click="useCommon"
            >使用当前通用阶段作为起点</el-button
          >
        </div>
        <el-radio-group v-if="draft.phases.length" v-model="phaseView" size="small">
          <el-radio-button value="list">列表编辑</el-radio-button>
          <el-radio-button value="preview">流程预览</el-radio-button>
        </el-radio-group>
        <div v-if="phaseView === 'preview' && draft.phases.length" class="flow-preview" role="img" aria-label="当前草稿阶段顺序预览">
          <template v-for="(p,i) in draft.phases" :key="p.clientKey || p.phaseId">
            <span class="flow-preview-node">{{ i+1 }}. {{ p.name }}</span>
            <span v-if="i < draft.phases.length-1" aria-hidden="true">→</span>
          </template>
        </div>
        <el-empty
          v-if="!draft.phases.length"
          description="请至少添加一个阶段；新类型不会自动跟随所有公共阶段"
        />
        <div
          v-for="(p, i) in draft.phases"
          :key="p.clientKey || p.phaseId"
          class="phase-row"
          v-show="phaseView === 'list'"
          draggable="true"
          @dragstart="drag = i"
          @dragover.prevent
          @drop.prevent="drop(i)"
        >
          <strong>{{ i + 1 }}. {{ p.name }}</strong
          ><el-tag v-if="!p.phaseId" size="small">待保存新阶段</el-tag>
          <div class="row-actions">
            <el-button :disabled="i === 0" @click="move(i, -1)">上移</el-button
            ><el-button
              :disabled="i === draft.phases.length - 1"
              @click="move(i, 1)"
              >下移</el-button
            ><el-button type="danger" @click="draft.phases.splice(i, 1)"
              >移除</el-button
            >
          </div>
        </div>
      </div>
      <div v-show="step === 3">
        <p>选阶段 → 选择要求模式 → 设置必填 → 确认本阶段 → 预览保存</p>
        <el-alert
          title="必填表示离开当前阶段、向后推进前必须完成，不影响保存未完成的项目。"
          type="info"
          :closable="false"
        />
        <div class="requirements space">
          <nav>
            <el-button
              v-for="(p, i) in draft.phases"
              :key="p.clientKey || p.phaseId"
              :type="index === i ? 'primary' : 'default'"
              @click="index = i"
              >{{ i + 1 }}. {{ p.name }} ·
              {{ p.confirmed ? "已确认" : "待确认" }}</el-button
            >
          </nav>
          <section v-if="current">
            <h3>{{ current.name }} · 员工提交要求</h3>
            <el-radio-group v-model="current.mode" @change="modeChanged">
              <el-radio value="INHERIT">沿用通用要求</el-radio
              ><el-radio value="CUSTOM">为此类型单独配置</el-radio
              ><el-radio value="NONE">无需提交材料</el-radio>
            </el-radio-group>
            <p v-if="current.mode === 'INHERIT'" class="hint">
              以下是当前通用要求。以后新建项目采用当时的通用要求，已有项目不变。
            </p>
            <p v-if="current.mode === 'NONE'">
              该阶段无材料拦截，员工仍可按现有权限添加资料。
            </p>
            <template v-if="current.mode === 'CUSTOM'">
              <el-button @click="copyCommon"
                >复制当前通用清单作为起点</el-button
              >
              <div class="toolbar space">
                <el-select
                  v-model="selectedTemplate"
                  placeholder="选择资料要求"
                  filterable
                  ><el-option
                    v-for="t in templates"
                    :key="t.id"
                    :label="t.name + ' · ' + kind(t.templateType)"
                    :value="t.id" /></el-select
                ><el-button @click="addRequirement">加入清单</el-button>
              </div>
            </template>
            <div
              v-for="(r, i) in currentRequirements"
              :key="r.templateId"
              class="requirement"
            >
              <strong>{{ r.name }}</strong
              ><el-tag>{{ kind(r.templateType) }}</el-tag>
              <template v-if="current.mode === 'CUSTOM'"
                ><el-select
                  v-model="r.required"
                  style="width: 90px"
                  @change="touch"
                  ><el-option label="必填" :value="1" /><el-option
                    label="选填"
                    :value="0" /></el-select
                ><el-button :disabled="i === 0" @click="moveRequirement(i, -1)"
                  >上移</el-button
                ><el-button
                  :disabled="i === currentRequirements.length - 1"
                  @click="moveRequirement(i, 1)"
                  >下移</el-button
                ><el-button
                  type="danger"
                  @click="
                    current.requirements.splice(i, 1);
                    touch();
                  "
                  >移除</el-button
                ></template
              >
              <el-tag
                v-else
                :type="Number(r.required) === 1 ? 'danger' : 'info'"
                >{{ Number(r.required) === 1 ? "必填" : "选填" }}</el-tag
              >
              <p class="explanation">{{ explain(r.templateType) }}</p>
            </div>
            <p v-if="current.mode !== 'NONE' && !currentRequirements.length">
              当前没有资料要求。单独配置须至少一项，否则请选择“无需提交材料”。
            </p>
            <el-button type="success" @click="confirmPhase"
              >确认本阶段（必填 {{ requiredCount }} 项 / 选填
              {{ currentRequirements.length - requiredCount }} 项）</el-button
            >
            <el-collapse class="space"
              ><el-collapse-item title="查看填写示例与员工操作说明"
                ><p>
                  示例为虚构内容：上传“示例策划书.pdf”，填写“已完成会场方案”，在项目专家/供应商页签关联记录，预算可填写
                  0。
                </p>
                <p>
                  附件和文本按阶段判断是否提交；专家、供应商和预算按整个项目判断。同类别多项目前共用完成判断，不代表各自收集独立一份。
                </p></el-collapse-item
              ></el-collapse
            >
          </section>
        </div>
        <el-button :loading="previewing" @click="preview">预览与确认</el-button>
        <div v-if="previewResult" class="preview space">
          <h3>员工视角预览 · {{ previewResult.name }}</h3>
          <p>
            保存位置：第 {{ draft.position }} 位；流程顺序如下。新增阶段：{{
              draft.phases
                .filter((p) => !p.phaseId)
                .map((p) => p.name)
                .join("、") || "无"
            }}。
          </p>
          <p v-if="type">
            原阶段：{{ originalPhaseNames }}；请对照下方新流程确认增删与顺序。
          </p>
          <div v-for="p in previewResult.phases" :key="p.phaseId || p.name">
            <h4>{{ p.position }}. {{ p.name }} · {{ modeLabel(p.mode) }}</h4>
            <ul>
              <li v-for="r in p.requirements" :key="r.templateId">
                {{ r.name }}（{{ kind(r.templateType) }}，{{
                  Number(r.required) === 1 ? "向后推进前必填" : "选填"
                }}）— {{ explain(r.templateType) }}
              </li>
            </ul>
            <p v-if="!p.requirements.length">无需提交材料即可按流程推进</p>
          </div>
          <el-checkbox v-model="accepted"
            >已核对流程、材料要求和展示位置；已有项目保持原配置</el-checkbox
          >
        </div>
      </div>
    </div>
    <template #footer
      ><div class="footer">
        <el-button :disabled="saving" @click="close">取消</el-button
        ><el-button v-if="step > 1" :disabled="saving" @click="step--"
          >上一步</el-button
        ><el-button
          v-if="step < 3"
          type="primary"
          :disabled="loading || !!error"
          @click="next"
          >下一步</el-button
        ><el-button
          v-else
          type="primary"
          :loading="saving"
          :disabled="!accepted || !previewResult || !!error"
          @click="save"
          >保存全部配置</el-button
        >
      </div></template
    >
  </el-dialog>
</template>
<script setup>
import { computed, reactive, ref, watch } from "vue";
import { ElMessage, ElMessageBox } from "element-plus";
import { createProjectConfigRequestId } from "../../../utils/projectConfigRequestId";
import { useProjectConfigStore } from "../../../stores/projectConfig";
import {
  getDefinitionContext,
  getTypeDefinition,
  previewDefinition,
  saveTypeDefinition,
} from "../../../api/project";
const props = defineProps({
  visible: Boolean,
  type: { type: Object, default: null },
});
const emit = defineEmits(["update:visible", "saved"]);
const phaseView = ref("list");
const store = useProjectConfigStore(),
  step = ref(1),
  loading = ref(true),
  saving = ref(false),
  previewing = ref(false),
  error = ref("");
const draft = reactive({ name: "", position: 1, phases: [], expectedEpoch: 0 }),
  common = ref({}),
  selected = ref(null),
  newName = ref(""),
  index = ref(0),
  selectedTemplate = ref(null),
  drag = ref(null);
let loadGeneration = 0,
  previewGeneration = 0;
const previewPayload = ref("");
const previewResult = ref(null),
  accepted = ref(false),
  originalPhaseNames = ref(""),
  requestId = ref(""),
  requestBody = ref(null);
watch(
  () => props.visible,
  (v) => {
    if (v) loading.value = true;
  },
  { flush: "sync" },
);
const templates = computed(() => store.templates),
  current = computed(() => draft.phases[index.value]);
const maxPosition = computed(() =>
  Math.max(1, store.types.length + (props.type ? 0 : 1)),
);
const available = computed(() =>
  store.phases.filter(
    (p) => p.isActive === 1 && !draft.phases.some((f) => f.phaseId === p.id),
  ),
);
const positionPreview = computed(() => {
  const list = store.sortedTypes.filter((t) => t.id !== props.type?.id);
  list.splice(Math.max(0, draft.position - 1), 0, {
    id: "draft",
    name: draft.name || "当前类型",
  });
  return list;
});
const currentRequirements = computed(() =>
  current.value?.mode === "INHERIT"
    ? common.value[current.value.phaseId] || []
    : current.value?.mode === "CUSTOM"
      ? current.value.requirements
      : [],
);
const requiredCount = computed(
  () =>
    currentRequirements.value.filter((r) => Number(r.required) === 1).length,
);
function kind(t) {
  return (
    {
      FILE: "附件",
      EXPERT: "专家",
      SUPPLIER: "供应商",
      BUDGET: "预算",
      RICH_TEXT: "文本",
    }[t] || t
  );
}
function explain(t) {
  return (
    {
      FILE: "在本阶段资料区上传附件，至少有一份有效附件。",
      EXPERT: "在项目专家名单中关联至少一位专家，跨阶段共用。",
      SUPPLIER: "在项目供应商名单中关联至少一家供应商，跨阶段共用。",
      BUDGET: "在项目预算区登记金额，0 表示已填写。",
      RICH_TEXT: "在本阶段内容区填写并保存文本。",
    }[t] || "请联系管理员确认此资料类型"
  );
}
function modeLabel(m) {
  return { INHERIT: "沿用通用要求", CUSTOM: "单独配置", NONE: "无需提交材料" }[
    m
  ];
}
function invalidate() {
  previewGeneration++;
  previewResult.value = null;
  previewPayload.value = "";
  accepted.value = false;
  requestBody.value = null;
}
watch(() => [draft.name, draft.position, draft.phases], invalidate, {
  deep: true,
});
async function open() {
  phaseView.value = "list";
  const generation = ++loadGeneration;
  loading.value = true;
  error.value = "";
  step.value = 1;
  index.value = 0;
  selected.value = null;
  newName.value = "";
  selectedTemplate.value = null;
  invalidate();
  try {
    await Promise.all([
      store.loadTypes(),
      store.loadPhases(),
      store.loadTemplates(),
    ]);
    const ctx = await getDefinitionContext();
    if (generation !== loadGeneration) return;
    common.value = ctx.common;
    const def = props.type
      ? await getTypeDefinition(props.type.id)
      : {
          name: "",
          position: maxPosition.value,
          phases: [],
          expectedEpoch: ctx.expectedEpoch,
        };
    if (generation !== loadGeneration) return;
    if (def.expectedEpoch !== ctx.expectedEpoch)
      throw new Error("配置在加载期间发生变化，请重新加载");
    Object.assign(draft, {
      name: def.name,
      position: def.position,
      expectedEpoch: def.expectedEpoch,
      phases: def.phases.map((p) => ({
        ...p,
        requirements: p.requirements.map((r) => ({
          ...r,
          required: Number(r.required),
        })),
        confirmed: false,
      })),
    });
    originalPhaseNames.value = draft.phases.map((p) => p.name).join(" → ");
  } catch (e) {
    if (generation === loadGeneration)
      error.value = "配置加载失败：" + e.message;
  } finally {
    if (generation === loadGeneration) loading.value = false;
  }
}
function phase(p) {
  return {
    phaseId: p.id,
    name: p.name,
    mode: "INHERIT",
    requirements: [],
    confirmed: false,
  };
}
function addExisting() {
  const p = available.value.find((p) => p.id === selected.value);
  if (p) {
    draft.phases.push(phase(p));
    selected.value = null;
  }
}
function addNew() {
  const name = newName.value.trim();
  if (!name) return ElMessage.warning("请输入阶段名称");
  const existing = store.phases.find(
    (p) => p.name.toLowerCase() === name.toLowerCase(),
  );
  if (existing) {
    if (existing.isActive !== 1)
      return ElMessage.warning("同名阶段已停用，请在流程阶段中维护后重新加载");
    if (draft.phases.some((p) => p.phaseId === existing.id))
      return ElMessage.warning("阶段已在流程中");
    draft.phases.push(phase(existing));
  } else {
    if (draft.phases.some((p) => p.name.toLowerCase() === name.toLowerCase()))
      return ElMessage.warning("阶段已在流程中");
    draft.phases.push({
      phaseId: null,
      clientKey: createProjectConfigRequestId(),
      name,
      mode: "INHERIT",
      requirements: [],
      confirmed: false,
    });
  }
  newName.value = "";
}
function useCommon() {
  draft.phases = available.value.map(phase);
}
function move(i, d) {
  const target = i + d;
  if (target < 0 || target >= draft.phases.length) return;
  const [p] = draft.phases.splice(i, 1);
  draft.phases.splice(target, 0, p);
}
function drop(i) {
  if (drag.value !== null) {
    const [p] = draft.phases.splice(drag.value, 1);
    draft.phases.splice(i, 0, p);
    drag.value = null;
  }
}
function touch() {
  current.value.confirmed = false;
  invalidate();
}
function modeChanged() {
  current.value.confirmed = false;
  current.value.requirements = [];
  invalidate();
}
function copyCommon() {
  current.value.requirements = (common.value[current.value.phaseId] || []).map(
    (r) => ({ ...r, required: Number(r.required) }),
  );
  current.value.confirmed = false;
}
function addRequirement() {
  const t = templates.value.find((t) => t.id === selectedTemplate.value);
  if (!t) return;
  if (current.value.requirements.some((r) => r.templateId === t.id))
    return ElMessage.warning("该要求已加入");
  current.value.requirements.push({
    templateId: t.id,
    name: t.name,
    templateType: t.templateType,
    required: 0,
  });
  current.value.confirmed = false;
  selectedTemplate.value = null;
}
function moveRequirement(i, d) {
  const arr = current.value.requirements;
  const [r] = arr.splice(i, 1);
  arr.splice(i + d, 0, r);
  current.value.confirmed = false;
}
function confirmPhase() {
  if (current.value.mode === "CUSTOM" && !current.value.requirements.length)
    return ElMessage.warning("请选择资料，或改为无需提交材料");
  current.value.confirmed = true;
  if (index.value < draft.phases.length - 1) index.value++;
}
function next() {
  if (step.value === 1 && !draft.name.trim())
    return ElMessage.warning("请输入类型名称");
  if (step.value === 2 && !draft.phases.length)
    return ElMessage.warning("请至少添加一个阶段");
  step.value++;
}
function payload() {
  return {
    name: draft.name.trim(),
    position: draft.position,
    expectedEpoch: draft.expectedEpoch,
    phases: draft.phases.map((p) => ({
      phaseId: p.phaseId,
      name: p.name,
      clientKey: p.clientKey,
      mode: p.mode,
      requirements:
        p.mode === "CUSTOM"
          ? p.requirements.map((r) => ({
              templateId: r.templateId,
              required: r.required,
            }))
          : [],
    })),
  };
}
async function preview() {
  const missing = draft.phases.findIndex((p) => !p.confirmed);
  if (missing >= 0) {
    index.value = missing;
    return ElMessage.warning("请逐个确认阶段要求");
  }
  const generation = ++previewGeneration,
    input = payload();
  previewing.value = true;
  try {
    const result = await previewDefinition(input);
    if (generation !== previewGeneration) return;
    previewResult.value = result;
    previewPayload.value = JSON.stringify(input);
    accepted.value = false;
  } catch (e) {
    ElMessage.error(e.message);
  } finally {
    previewing.value = false;
  }
}

async function save() {
  if (!accepted.value || !previewResult.value) return;
  if (previewPayload.value !== JSON.stringify(payload()))
    return ElMessage.warning("配置已变化，请重新预览");
  saving.value = true;
  try {
    if (!requestBody.value) {
      requestId.value = createProjectConfigRequestId();
      requestBody.value = { ...payload(), requestId: requestId.value };
    }
    const saved = await saveTypeDefinition(props.type?.id, requestBody.value);
    ElMessage.success("全部配置已保存，已有项目保持不变");
    emit("update:visible", false);
    emit("saved", saved);
  } catch (e) {
    ElMessage.error("保存失败，草稿已保留：" + e.message);
  } finally {
    saving.value = false;
  }
}
async function close() {
  if (saving.value) return;
  try {
    await ElMessageBox.confirm("未保存的配置将被放弃，是否关闭？", "放弃草稿", {
      type: "warning",
    });
    loadGeneration++;
    invalidate();
    emit("update:visible", false);
  } catch {}
}
</script>
<style scoped>
.flow-preview { display:flex; flex-wrap:wrap; align-items:center; gap:10px; padding:16px 0; }
.flow-preview-node { padding:12px; border:1px solid #b3d8ff; border-radius:8px; background:#ecf5ff; max-width:220px; overflow-wrap:anywhere; }
.space {
  margin-top: 16px;
}
.body {
  min-height: 260px;
  max-height: calc(85vh - 250px);
  overflow: auto;
  margin-top: 20px;
  padding-right: 8px;
}
.hint,
.explanation {
  color: var(--ui-text-sub);
  font-size: 13px;
}
.hint {
  margin-left: 10px;
}
.toolbar {
  gap: 8px;
  flex-wrap: wrap;
  margin-bottom: 16px;
}
.phase-row,
.requirement {
  border: 1px solid #dcdfe6;
  padding: 12px;
  border-radius: 8px;
  margin: 10px 0;
  display: flex;
  gap: 10px;
  align-items: center;
  flex-wrap: wrap;
}
.row-actions {
  margin-left: auto;
}
.requirements {
  display: flex;
  gap: 20px;
}
nav {
  display: flex;
  flex-direction: column;
  gap: 8px;
  width: 220px;
  flex-shrink: 0;
}
nav .el-button {
  margin: 0;
  justify-content: flex-start;
  height: auto;
  min-height: 32px;
  white-space: normal;
}
nav .el-button :deep(span) {
  white-space: normal;
  overflow-wrap: anywhere;
  text-align: left;
}
section {
  min-width: 0;
  flex: 1;
}
.explanation {
  width: 100%;
  margin: 0;
}
.preview {
  background: #f5f7fa;
  padding: 16px;
  border-radius: 8px;
}
.footer {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
}
.positions {
  line-height: 1.8;
}
@media (max-width: 720px) {
  .requirements {
    flex-direction: column;
  }
  nav {
    width: 100%;
  }
}
</style>
