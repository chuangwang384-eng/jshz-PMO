<template>
  <section class="estimate">
    <strong>估算参数</strong>
    <el-form inline label-position="top">
      <el-form-item v-for="field in fields" :key="field.key" :label="field.label">
        <el-input-number v-model="input[field.key]" :min="field.min" :max="9999"
                         :precision="0" :controls="false" size="small"/>
      </el-form-item>
      <el-form-item label="城市档位">
        <el-select v-model="input.city" placeholder="请选择" size="small">
          <el-option label="一线城市" value="FIRST"/>
          <el-option label="其他城市" value="OTHER"/>
        </el-select>
      </el-form-item>
      <el-form-item label="大交通">
        <el-select v-model="input.longDistance" size="small">
          <el-option label="手工填写" value="MANUAL"/>
          <el-option label="机票" value="FLIGHT"/>
          <el-option label="高铁" value="RAIL"/>
        </el-select>
      </el-form-item>
      <el-form-item label="地面交通">
        <el-select v-model="input.localTransport" size="small">
          <el-option label="不覆盖" value="NONE"/>
          <el-option label="车队专车" value="FLEET"/>
          <el-option label="平台专车" value="PLATFORM"/>
        </el-select>
      </el-form-item>
    </el-form>
    <el-button plain type="primary" size="small" @click="preview">预览估算</el-button>
    <el-alert v-if="legacyMaterialPrice" class="estimate-notice" type="warning" :closable="false"
              title="请核对已有物料单价。估算只建议数量和单位，原单价可手动修改。"/>
    <el-dialog v-model="visible" title="估算变更预览" width="min(820px, 95vw)">
      <p>{{ modelValue.applied ? '勾选要更新的费用行。' : '首次估算只补空白，保留已有值。' }}</p>
      <el-empty v-if="!changes.length" description="没有可预填的变更" :image-size="56"/>
      <el-checkbox-group v-else v-model="selected">
        <div v-for="change in changes" :key="change.key" class="estimate-change">
          <el-checkbox :value="change.key">{{ change.name }}</el-checkbox>
          <div>{{ describe(change) }} · {{ change.basis }}</div>
        </div>
      </el-checkbox-group>
      <template #footer>
        <el-button @click="visible = false">取消</el-button>
        <el-button type="primary" @click="apply">{{ selected.length ? '应用选中项' : '保留估算参数' }}</el-button>
      </template>
    </el-dialog>
  </section>
</template>

<script setup>
import { computed, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { estimateChanges, estimateDefaults, quantityRules } from './budgetEstimate'

const props = defineProps({
  modelValue: { type: Object, required: true },
  items: { type: Array, required: true },
  standards: { type: Object, required: true },
  persisted: Boolean
})
const emit = defineEmits(['update:modelValue'])
const fields = [
  { key: 'attendees', label: '参会人数', min: 1 },
  { key: 'travelers', label: '外埠人数', min: 0 },
  { key: 'sessions', label: '场次', min: 1 },
  { key: 'staff', label: '工作人员数', min: 0 },
  { key: 'days', label: '天数', min: 1 }
]
const input = ref({ ...estimateDefaults })
const visible = ref(false)
const changes = ref([])
const selected = ref([])
const legacyMaterialPrice = computed(() => props.persisted && props.items.some(
  row => quantityRules[row.itemKey] && Number(row.unitPrice) > 0))
watch(() => props.modelValue, value => { input.value = { ...estimateDefaults, ...value.inputs } }, { immediate: true })

function preview() {
  if (fields.some(({ key, min }) => !Number.isInteger(input.value[key]) || input.value[key] < min)
      || !input.value.city || input.value.travelers > input.value.attendees) {
    ElMessage.warning('请完整填写估算参数，外埠人数不得超过参会人数')
    return
  }
  changes.value = estimateChanges(props.items, input.value, props.standards, !props.modelValue.applied, props.modelValue.inputs)
  selected.value = props.modelValue.applied ? [] : changes.value.map(row => row.key)
  visible.value = true
}

function describe(change) {
  const labels = { quantity: '数量', unitPrice: '单价', unit: '单位' }
  return Object.entries(change.changes).map(([field, next]) =>
    `${labels[field]} ${props.items[change.index][field] ?? '空白'} → ${next ?? '空白'}`).join('，')
}

function apply() {
  const basis = { ...props.modelValue.basis }
  for (const change of changes.value.filter(row => selected.value.includes(row.key))) {
    Object.assign(props.items[change.index], change.changes, { itemKey: change.key })
    basis[change.key] = change.basis
  }
  emit('update:modelValue', { inputs: { ...input.value }, basis, applied: true })
  visible.value = false
}
</script>

<style scoped>
.estimate { margin-bottom: 16px; padding: 12px; border: 1px solid var(--ui-border-weak); }
.estimate :deep(.el-select) { width: 120px; }
.estimate-notice { margin-top: 8px; }
.estimate-change { padding-bottom: 8px; }
</style>
