<template>
  <el-dialog v-model="visible" :title="form.id ? '编辑项目' : '新建项目'" width="620px" destroy-on-close @closed="onClosed">
    <el-alert v-if="flowError" :title="flowError" type="error" :closable="false"/><el-alert v-if="form.id" title="已有项目保留原类型及流程；阶段变化请使用推进或回退操作。" type="info" :closable="false"/>
    <el-form :model="form" label-width="100px">
      <el-form-item label="项目名称" required>
        <el-input v-model="form.name" maxlength="100" placeholder="必填" />
      </el-form-item>
      <el-row :gutter="8">
        <el-col :span="12">
          <el-form-item label="大类" required>
            <el-select v-model="form.category" placeholder="线上/线下" style="width: 100%">
              <el-option label="线上" value="ONLINE" />
              <el-option label="线下" value="OFFLINE" />
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="类型" required>
            <el-select :disabled="!!form.id" v-model="form.typeId" placeholder="选择类型" style="width: 100%">
              <el-option v-for="t in types" :key="t.id" :label="t.name" :value="t.id" />
            </el-select>
          </el-form-item>
        </el-col>
      </el-row>
      <el-form-item label="计划时间">
        <el-date-picker
          v-model="dateRange"
          type="daterange"
          range-separator="至"
          start-placeholder="开始时间"
          end-placeholder="结束时间"
          value-format="YYYY-MM-DD"
          style="width: 100%"
        />
      </el-form-item>
      <el-row :gutter="8">
        <el-col :span="12">
          <el-form-item label="负责人" required>
            <el-select v-model="form.ownerId" placeholder="项目负责人角色用户" filterable style="width: 100%">
              <el-option v-for="o in owners" :key="o.id" :label="o.username" :value="o.id" />
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="当前阶段">
            <el-select :disabled="!!form.id || flowLoading || !!flowError" v-model="form.phaseId" placeholder="默认立项" style="width: 100%">
              <el-option v-for="p in phaseOptions" :key="p.id" :label="p.name" :value="p.id" />
            </el-select>
          </el-form-item>
        </el-col>
      </el-row>
      <el-form-item label="执行人">
        <el-select v-model="form.executorIds" multiple filterable placeholder="多人，默认创建人+负责人" style="width: 100%">
          <el-option v-for="u in users" :key="u.id" :label="u.username" :value="u.id" />
        </el-select>
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="visible = false">取消</el-button>
      <el-button type="primary" :loading="saving" @click="save">保存</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { computed, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { createProject, updateProject, listTypePhases, getProjectConfiguration } from '../../api/project'

const props = defineProps({
  modelValue: Boolean,
  project: { type: Object, default: null },
  owners: { type: Array, default: () => [] },
  types: { type: Array, default: () => [] },
  phases: { type: Array, default: () => [] },
  users: { type: Array, default: () => [] }
})
const emit = defineEmits(['update:modelValue', 'saved'])

const visible = computed({
  get: () => props.modelValue,
  set: (v) => emit('update:modelValue', v)
})

const saving = ref(false)
const dateRange = ref([])
const form = reactive({ id: null, name: '', category: 'OFFLINE', typeId: null, ownerId: null, phaseId: null, executorIds: [] })

// 类型流程联动（配置 v2 20261102）：类型已配流程时阶段下拉仅显示流程内阶段
const typeFlowPhases = ref([]) // [{phaseId, phaseName}]；空=该类型未配置流程
const flowLoading = ref(false), flowError = ref('')
let flowGeneration = 0
const phaseOptions = computed(() => typeFlowPhases.value.map(p => ({id:p.phaseId,name:p.phaseName})))
async function loadTypeFlow(typeId) {
  const generation = ++flowGeneration
  typeFlowPhases.value = []; flowError.value = ''; flowLoading.value = true
  try {
    if (!typeId) return
    const result = form.id ? (await getProjectConfiguration(form.id)).phases.map(p=>({phaseId:p.phaseId,phaseName:p.name})) : await listTypePhases(typeId)
    if (generation===flowGeneration) typeFlowPhases.value=result
  } catch(e) { if(generation===flowGeneration) flowError.value='流程加载失败，请关闭后重试：'+e.message }
  finally { if(generation===flowGeneration) flowLoading.value=false }
}
watch(() => form.typeId, async (nv, ov) => {
  if (!visible.value) return
  await loadTypeFlow(nv)
  if (ov !== null && ov !== undefined && nv !== ov && form.id !== null) {
    // 编辑时切换类型：当前阶段不属于新类型流程则提示并改选
    if (form.phaseId && typeFlowPhases.value.length && !typeFlowPhases.value.some((f) => f.phaseId === form.phaseId)) {
      ElMessage.warning('当前阶段不属于新类型的流程，请改选阶段')
    }
  }
  // 已配置流程：新建项目默认取流程第一个阶段；编辑项目当前阶段在流程内则保留
  if (typeFlowPhases.value.length) {
    if (!form.id || !typeFlowPhases.value.some((f) => f.phaseId === form.phaseId)) {
      form.phaseId = typeFlowPhases.value[0].phaseId
    }
  } else if (!form.id && !form.phaseId && phaseOptions.value.length) {
    form.phaseId = phaseOptions.value[0].id
  }
})

watch(
  () => props.modelValue,
  (v) => {
    if (!v) return
    const p = props.project
    form.id = p?.id ?? null
    form.name = p?.name ?? ''
    form.category = p?.category ?? 'OFFLINE'
    form.typeId = p?.typeId ?? null
    form.ownerId = p?.ownerId ?? null
    form.phaseId = p?.phaseId ?? props.phases[0]?.id ?? null
    form.executorIds = []
    if (p?.executorIds && Array.isArray(p.executorIds)) form.executorIds = [...p.executorIds]
    dateRange.value = p?.startDate && p?.endDate ? [p.startDate, p.endDate] : []
    loadTypeFlow(form.typeId).then(() => {
      if (typeFlowPhases.value.length && !typeFlowPhases.value.some((f) => f.phaseId === form.phaseId)) {
        form.phaseId = typeFlowPhases.value[0].phaseId
      }
    })
  }
)

async function save() {
  if(flowLoading.value || flowError.value || !phaseOptions.value.length) return ElMessage.warning("流程尚未正确加载");
  if (!form.name.trim()) {
    ElMessage.warning('项目名称必填')
    return
  }
  if (!form.category || !form.typeId || !form.ownerId) {
    ElMessage.warning('大类/类型/负责人必填')
    return
  }
  saving.value = true
  try {
    const data = {
      name: form.name.trim(),
      category: form.category,
      typeId: form.typeId,
      ownerId: form.ownerId,
      startDate: dateRange.value?.[0] || null,
      endDate: dateRange.value?.[1] || null,
      phaseId: form.phaseId || undefined,
      executorIds: form.executorIds
    }
    if (form.id) {
      // 编辑时提交执行人列表，下拉可回显并随保存提交（后端仅在传值且非空时替换）
      await updateProject(form.id, data)
    } else {
      await createProject(data)
    }
    ElMessage.success(form.id ? '项目已更新' : '项目已创建')
    visible.value = false
    emit('saved')
  } finally {
    saving.value = false
  }
}

function onClosed() {
  emit('saved')
}
</script>
