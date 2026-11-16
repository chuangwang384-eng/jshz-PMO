<template>
  <el-dialog v-model="visible" :title="form.id ? '编辑项目' : '新建项目'" width="720px" top="4vh" destroy-on-close @closed="onClosed">
    <el-alert v-if="form.id" title="阶段变化请使用推进或回退操作；新建项目自动进入第一阶段「日程上传」。" type="info" :closable="false" />
    <el-form :model="form" label-width="110px">
      <el-form-item label="会议名称" required>
        <el-input v-model="form.name" maxlength="100" show-word-limit placeholder="必填，如：2026 全国经销商大会" />
      </el-form-item>
      <el-row :gutter="8">
        <el-col :span="8">
          <el-form-item label="会议类型" required>
            <el-select v-model="form.category" placeholder="线上/线下" style="width: 100%">
              <el-option label="线上" value="ONLINE" />
              <el-option label="线下" value="OFFLINE" />
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :span="8">
          <el-form-item label="团队" required>
            <el-select v-model="form.team" placeholder="选择部门" style="width: 100%">
              <el-option label="项目一部" value="DEPT1" />
              <el-option label="项目二部" value="DEPT2" />
              <el-option label="项目三部" value="DEPT3" />
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :span="8">
          <el-form-item label="会议城市">
            <el-select v-model="form.cityId" placeholder="非必填" clearable filterable style="width: 100%">
              <el-option-group v-for="g in cityGroups" :key="g.province" :label="g.province">
                <el-option v-for="c in g.cities" :key="c.id" :label="c.name" :value="c.id" />
              </el-option-group>
            </el-select>
          </el-form-item>
        </el-col>
      </el-row>
      <el-row :gutter="8">
        <el-col :span="12">
          <el-form-item label="项目起止" required>
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
        </el-col>
        <el-col :span="12">
          <el-form-item label="举办时间">
            <el-date-picker
              v-model="meetingRange"
              type="daterange"
              range-separator="至"
              start-placeholder="单日选同一天"
              end-placeholder="结束日期"
              value-format="YYYY-MM-DD"
              style="width: 100%"
            />
          </el-form-item>
        </el-col>
      </el-row>
      <el-row :gutter="8">
        <el-col :span="12">
          <el-form-item label="总负责人" required>
            <el-select v-model="form.ownerId" placeholder="默认创建人，可修改" filterable style="width: 100%">
              <el-option v-for="u in users" :key="u.id" :label="u.username" :value="u.id" />
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="协助人">
            <el-select v-model="form.assistantIds" multiple filterable collapse-tags collapse-tags-tooltip placeholder="全员可选，非必填" style="width: 100%">
              <el-option v-for="u in users" :key="u.id" :label="u.username" :value="u.id" />
            </el-select>
          </el-form-item>
        </el-col>
      </el-row>
      <el-row :gutter="8">
        <el-col :span="12">
          <el-form-item label="客户单位">
            <el-input v-model="form.clientCompany" maxlength="200" placeholder="非必填" />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="客户姓名">
            <el-input v-model="form.clientName" maxlength="100" placeholder="非必填" />
          </el-form-item>
        </el-col>
      </el-row>
      <el-form-item label="备注">
        <el-input v-model="form.remark" type="textarea" :rows="3" maxlength="500" show-word-limit placeholder="非必填" />
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
import { useAuthStore } from '../../store/auth'
import { createProject, updateProject } from '../../api/project'
import { listSupplierCities } from '../../api/supplier'

const props = defineProps({
  modelValue: Boolean,
  project: { type: Object, default: null },
  owners: { type: Array, default: () => [] },
  users: { type: Array, default: () => [] }
})
const emit = defineEmits(['update:modelValue', 'saved'])

const authStore = useAuthStore()

const visible = computed({
  get: () => props.modelValue,
  set: (v) => emit('update:modelValue', v)
})

const saving = ref(false)
const dateRange = ref([])
const meetingRange = ref([])
const cities = ref([])
const cityGroups = computed(() => {
  const groups = []
  const byProvince = new Map()
  for (const c of cities.value) {
    if (!byProvince.has(c.province)) {
      const g = { province: c.province, cities: [] }
      byProvince.set(c.province, g)
      groups.push(g)
    }
    byProvince.get(c.province).cities.push(c)
  }
  return groups
})

const form = reactive({
  id: null, name: '', category: 'OFFLINE', team: null, cityId: null,
  ownerId: null,
  assistantIds: [],
  clientCompany: '', clientName: '', remark: ''
})

watch(
  () => props.modelValue,
  (v) => {
    if (!v) return
    const p = props.project
    form.id = p?.id ?? null
    form.name = p?.name ?? ''
    form.category = p?.category ?? 'OFFLINE'
    form.team = p?.team ?? null
    form.cityId = p?.cityId ?? null
    form.ownerId = p?.ownerId ?? authStore.id ?? null // 新建默认当前登录人（总负责人全员可选）
    form.assistantIds = Array.isArray(p?.assistantIds) ? [...p.assistantIds] : []
    form.clientCompany = p?.clientCompany ?? ''
    form.clientName = p?.clientName ?? ''
    form.remark = p?.remark ?? ''
    dateRange.value = p?.startDate && p?.endDate ? [p.startDate, p.endDate] : []
    meetingRange.value = p?.meetingStartDate && p?.meetingEndDate ? [p.meetingStartDate, p.meetingEndDate] : []
    if (!cities.value.length) {
      listSupplierCities().then((data) => { cities.value = data || [] }).catch(() => {})
    }
  }
)

async function save() {
  if (!form.name.trim()) {
    ElMessage.warning('会议名称必填')
    return
  }
  if (!form.team) {
    ElMessage.warning('团队必填（项目一部/项目二部/项目三部）')
    return
  }
  if (!dateRange.value?.[0] || !dateRange.value?.[1]) {
    ElMessage.warning('项目起止时间必填')
    return
  }
  if (!form.category || !form.ownerId) {
    ElMessage.warning('会议类型/总负责人必填')
    return
  }
  if (meetingRange.value?.[0] && meetingRange.value[1] < meetingRange.value[0]) {
    ElMessage.warning('会议举办结束日期不能早于开始日期')
    return
  }
  saving.value = true
  try {
    const data = {
      name: form.name.trim(),
      category: form.category,
      team: form.team,
      cityId: form.cityId || null,
      meetingStartDate: meetingRange.value?.[0] || null,
      meetingEndDate: meetingRange.value?.[1] || null,
      clientCompany: form.clientCompany.trim() || null,
      clientName: form.clientName.trim() || null,
      remark: form.remark.trim() || null,
      ownerId: form.ownerId,
      startDate: dateRange.value[0],
      endDate: dateRange.value[1],
      assistantIds: form.assistantIds
    }
    if (form.id) {
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
