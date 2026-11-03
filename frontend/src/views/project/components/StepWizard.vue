<template>
  <el-dialog
    :model-value="visible"
    :title="isEdit ? '编辑类型（三步向导）' : '新增类型（三步向导）'"
    width="880px"
    destroy-on-close
    :close-on-click-modal="false"
    :show-close="!initializing && !saving"
    :close-on-press-escape="!initializing && !saving"
    @update:model-value="(v) => emit('update:visible', v)"
    @open="onOpen"
  >
    <el-steps :active="step - 1" align-center finish-status="success" style="margin-bottom: 18px">
      <el-step title="类型信息" />
      <el-step title="流程绑定与排序" />
      <el-step title="阶段模板绑定" />
    </el-steps>

    <!-- 步骤1：类型信息 -->
    <div v-show="step === 1">
      <el-form label-width="90px">
        <el-form-item label="类型名称" required>
          <el-input v-model="form.name" placeholder="如：展会 / 会议 / 论坛" style="width: 300px" />
        </el-form-item>
        <el-form-item label="排序">
          <el-input-number v-model="form.sort" :min="0" />
          <span class="wizard-hint">数值小的类型在总览图中排在前列</span>
        </el-form-item>
      </el-form>
      <div class="wizard-info">
        <el-icon><InfoFilled /></el-icon>
        <span>未配置流程时，该项目类型将使用「全部启用阶段」；配置的流程阶段决定新建项目时的推进顺序。</span>
      </div>
    </div>

    <!-- 步骤2：流程绑定与排序 -->
    <div v-show="step === 2">
      <flow-builder-step v-model="flow" :phases="phases" />
      <div class="wizard-info">
        <el-icon><InfoFilled /></el-icon>
        <span>可在画布中拖拽阶段节点调整顺序，也可用右侧「上移 / 下移」按钮微调；移除后的阶段回到「可添加」列表。</span>
      </div>
    </div>

    <!-- 步骤3：阶段模板绑定 -->
    <div v-show="step === 3">
      <template-bind-step
        ref="bindStepRef"
        :flow="flow"
        :templates="templates"
        :type-name="form.name"
        :initial-states="initialStates"
        @change="onBindChange"
      />
      <div class="wizard-info">
        <el-icon><InfoFilled /></el-icon>
        <span>未绑定模板的阶段将回退「全局兜底绑定」；勾选绑定并保存后即成为该类型专属绑定（显示为绿色角标）。</span>
      </div>
    </div>

    <template #footer>
      <div class="wizard-footer">
        <div>
          <el-button v-if="step > 1" @click="step--">上一步</el-button>
        </div>
        <div>
          <el-button :disabled="initializing || saving" @click="emit('update:visible', false)">取消</el-button>
          <el-button v-if="step < 3" type="primary" :disabled="initializing || loadFailed || (step === 1 && !form.name.trim())" @click="step++">下一步</el-button>
          <el-button v-if="step === 3" type="primary" :loading="saving" :disabled="initializing || loadFailed" @click="save">保存</el-button>
        </div>
      </div>
    </template>
  </el-dialog>
</template>

<script setup>
import { computed, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { InfoFilled } from '@element-plus/icons-vue'
import FlowBuilderStep from './FlowBuilderStep.vue'
import TemplateBindStep from './TemplateBindStep.vue'
import { useProjectConfigStore } from '../../../stores/projectConfig'

const props = defineProps({
  visible: { type: Boolean, default: false },
  type: { type: Object, default: null } // 编辑对象；null 表示新增
})
const emit = defineEmits(['update:visible', 'saved'])

const store = useProjectConfigStore()
const step = ref(1)
const saving = ref(false)
const bindStepRef = ref(null)
const initialStates = ref({})

const isEdit = computed(() => !!props.type)
const form = reactive({ name: '', sort: 0 })
const flow = ref([])
const savedTypeId = ref(null)
const loadFailed = ref(false)
const initializing = ref(false)
const phases = computed(() => store.phases)
const templates = computed(() => store.templates)

// 打开时初始化：新增直接空白；编辑拉取现有流程与绑定
async function onOpen() {
  step.value = 1
  savedTypeId.value = null
  loadFailed.value = false
  initializing.value = true
  flow.value = []
  saving.value = false
  initialStates.value = {}
  if (isEdit.value) {
    form.name = props.type.name
    form.sort = props.type.sort || 0
    try {
      const f = await store.loadTypeFlow(props.type.id)
      flow.value = f.map((x) => ({ phaseId: x.phaseId, phaseName: x.phaseName }))
      // 逐阶段拉取绑定（含 fallback 标记）
      const states = {}
      await Promise.all(flow.value.map(async (fp) => {
        const binds = await store.loadPhaseTemplateRels(props.type.id, fp.phaseId)
        states[fp.phaseId] = {
          fallback: binds.length === 0 || binds.some((b) => b.fallback),
          items: binds.map((b) => ({ templateId: b.templateId, required: b.required, sort: b.sort }))
        }
      }))
      initialStates.value = states
    } catch (e) {
      loadFailed.value = true
      ElMessage.error('流程配置加载失败：' + (e?.message || e))
    }
  } else {
    form.name = ''
    form.sort = store.types.length
    flow.value = []
  }
  initializing.value = false
}

function onBindChange(states) {
  // TemplateBindStep 内部状态变化时同步（保存阶段直接读取其 getStates）
}

async function save() {
  if (initializing.value || loadFailed.value) { ElMessage.error("配置加载失败，请关闭后重试"); return }
  if (!form.name.trim()) {
    ElMessage.warning('请输入类型名称')
    step.value = 1
    return
  }
  // 名称唯一性预校验（编辑时排除自身）
  const name = form.name.trim()
  const dup = store.types.find((t) => t.name === name && t.id !== (isEdit.value ? props.type.id : savedTypeId.value))
  if (dup) {
    ElMessage.error(`类型名称「${name}」已存在，请更换名称`)
    step.value = 1
    return
  }
  saving.value = true
  try {
    // 步骤1：创建/更新类型基础信息
    const t = await store.saveType({ id: isEdit.value ? props.type.id : savedTypeId.value, name, sort: form.sort })
    const typeId = t.id
    savedTypeId.value = typeId
    // 步骤2：保存阶段序列
    await store.saveTypePhases(typeId, flow.value.map((f) => f.phaseId))
    // 步骤3：逐阶段保存专属绑定
    const states = bindStepRef.value?.getStates?.() || {}
    for (const f of flow.value) {
      const s = states[f.phaseId]
      const binds = (s?.items || [])
        .filter((x) => x._bound)
        .map((x, idx) => ({ templateId: x.templateId, required: x._required, sort: idx + 1 }))
      await store.saveScopePhaseTemplates(typeId, f.phaseId, binds)
    }
    ElMessage.success('类型（含流程配置）已保存')
    // 刷新类型流程缓存与绑定缓存（保持总览图一致）
    await store.loadTypes()
    await store.ensureAllBindings()
    emit('update:visible', false)
    emit('saved', t)
  } catch (e) {
    ElMessage.error('保存失败：' + (e?.message || e))
  } finally {
    saving.value = false
  }
}
</script>

<style scoped>
.wizard-hint { color: #909399; font-size: 12px; margin-left: 10px; }
.wizard-info {
  display: flex; align-items: center; gap: 6px;
  margin-top: 12px; padding: 8px 12px;
  background: #f5f7fa; border-radius: 6px;
  color: #606266; font-size: 12px;
}
.wizard-footer { display: flex; justify-content: space-between; align-items: center; }
</style>
