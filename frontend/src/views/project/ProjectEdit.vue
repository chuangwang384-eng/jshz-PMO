<template>
  <el-dialog v-model="visible" :title="form.id ? '编辑项目' : '新建项目'" width="720px" top="4vh" destroy-on-close @closed="onClosed">
    <el-alert v-if="form.id" title="请使用推进或回退操作调整项目阶段。" type="info" :closable="false" />
    <el-form :model="form" label-width="110px">
      <el-form-item label="会议名称" required>
        <el-input v-model="form.name" maxlength="100" show-word-limit placeholder="必填，如：2026 全国经销商大会" />
      </el-form-item>
      <el-row :gutter="8">
        <el-col :span="12">
          <!-- 2026-11-20：原「会议类型」改名为「地点」（字段仍为 category ONLINE/OFFLINE），新增「项目类型」 -->
          <el-form-item label="项目类型" required>
            <el-select v-model="form.projectType" placeholder="平台类/直付类/重点类" style="width: 100%">
              <el-option label="平台类项目" value="PLATFORM" />
              <el-option label="直付类项目" value="DIRECT_PAY" />
              <el-option label="重点类项目" value="KEY" />
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="地点" required>
            <el-select v-model="form.category" placeholder="线上/线下" style="width: 100%">
              <el-option label="线上" value="ONLINE" />
              <el-option label="线下" value="OFFLINE" />
            </el-select>
          </el-form-item>
        </el-col>
      </el-row>
      <el-row :gutter="8">
        <el-col :span="12">
          <!-- 团队只读（2026-11-26 项目日历需求 FR-05/C3）：由负责人组织架构部门派生，后端 ProjectTeamSyncService 落库 -->
          <el-form-item label="团队">
            <el-input :model-value="teamDisplay" disabled />
          </el-form-item>
        </el-col>
        <el-col :span="12">
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
          <!-- 会议日期必填（2026-11-26 项目日历需求 FR-04）：立项必填，编辑不允许清空 -->
          <el-form-item label="会议日期" required>
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
  id: null, name: '', category: 'OFFLINE', projectType: null, cityId: null,
  ownerId: null,
  assistantIds: [],
  clientCompany: '', clientName: '', remark: ''
})

// 团队只读展示（FR-05）：新建=由负责人所在部门自动确定；编辑=回显派生值
const TEAM_LABELS = { DEPT1: '项目一部', DEPT2: '项目二部', DEPT3: '项目三部' }
const teamDisplay = computed(() => (form.id && formTeam.value ? (TEAM_LABELS[formTeam.value] || formTeam.value) : '由负责人所在部门自动确定'))
const formTeam = ref(null)

watch(
  () => props.modelValue,
  (v) => {
    if (!v) return
    const p = props.project
    form.id = p?.id ?? null
    form.name = p?.name ?? ''
    form.category = p?.category ?? 'OFFLINE'
    form.projectType = p?.projectType ?? null
    formTeam.value = p?.team ?? null
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
  if (!dateRange.value?.[0] || !dateRange.value?.[1]) {
    ElMessage.warning('项目起止时间必填')
    return
  }
  // P2-1（2026-11-26 审查修复）：与后端同口径校验起止先后
  if (dateRange.value[1] < dateRange.value[0]) {
    ElMessage.warning('项目结束时间不能早于开始时间')
    return
  }
  if (!form.category || !form.ownerId) {
    ElMessage.warning('地点/总负责人必填')
    return
  }
  if (!form.projectType) {
    ElMessage.warning('项目类型必填（平台类/直付类/重点类）')
    return
  }
  // 会议日期必填（FR-04，2026-11-26 项目日历需求）
  if (!meetingRange.value?.[0] || !meetingRange.value?.[1]) {
    ElMessage.warning('会议举办起止日期必填')
    return
  }
  if (meetingRange.value[1] < meetingRange.value[0]) {
    ElMessage.warning('会议举办结束日期不能早于开始日期')
    return
  }
  saving.value = true
  try {
    const data = {
      name: form.name.trim(),
      category: form.category,
      projectType: form.projectType,
      // team 不再提交（FR-05/C3）：后端按负责人组织架构部门派生
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
