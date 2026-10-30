<template>
  <el-dialog v-model="visible" :title="form.id ? '编辑项目' : '新建项目'" width="620px" destroy-on-close @closed="onClosed">
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
            <el-select v-model="form.typeId" placeholder="选择类型" style="width: 100%">
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
            <el-select v-model="form.phaseId" placeholder="默认立项" style="width: 100%">
              <el-option v-for="p in phases" :key="p.id" :label="p.name" :value="p.id" />
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
import { createProject, updateProject } from '../../api/project'

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
  }
)

async function save() {
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
      // 编辑时不传执行人列表，保留原有执行人（后端仅在传值且非空时替换）
      delete data.executorIds
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
