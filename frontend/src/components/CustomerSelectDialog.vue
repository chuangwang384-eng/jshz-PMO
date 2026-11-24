<template>
  <el-dialog
    :model-value="modelValue"
    :title="identityOnly ? '定位客户' : '选择目标客户'"
    width="540px"
    append-to-body
    destroy-on-close
    @close="emit('update:modelValue', false)"
  >
    <div class="select-dialog">
      <el-input
        v-model="keyword"
        :placeholder="identityOnly ? '搜索客户姓名或单位' : '搜索客户姓名/单位/职位/职级/职务/喜好'"
        :aria-label="identityOnly ? '客户姓名或单位' : '搜索目标客户'"
        clearable
        @keyup.enter="handleEnter"
        @clear="handleSearch"
      >
        <template #append>
          <el-button @click="handleSearch">搜索</el-button>
        </template>
      </el-input>
      <div v-loading="loading" class="target-list">
        <div
          v-for="row in rows"
          :key="row.id"
          :class="['target-item', { disabled: isExcluded(row.id) }]"
          @click="pickTarget(row)"
        >
          <div class="target-info">
            <span class="target-name">{{ row.name }}</span>
            <el-tag v-if="row.workUnit" type="info" size="small">{{ row.workUnit }}</el-tag>
            <el-tag v-if="row.workPosition" size="small">{{ row.workPosition }}</el-tag>
            <el-tag v-if="row.workLevel" type="warning" size="small">{{ row.workLevel }}</el-tag>
          </div>
          <el-tag v-if="isExcluded(row.id)" type="info" size="small">已选择</el-tag>
          <el-button v-else link type="primary" size="small" @click.stop="pickTarget(row)">{{ identityOnly ? '定位' : '选择' }}</el-button>
        </div>
        <el-empty v-if="!loading && rows.length === 0" description="暂无可选客户" :image-size="60" />
      </div>
      <el-pagination
        v-if="total > pageSize"
        small
        layout="prev, pager, next"
        :total="total"
        :page-size="pageSize"
        v-model:current-page="page"
        class="pager"
        @current-change="loadList"
      />
    </div>
  </el-dialog>
</template>

<script setup>
import { ref, watch, getCurrentInstance } from 'vue'
import { searchTargets } from '../api/customer'

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  identityOnly: { type: Boolean, default: false },
  /** 当前客户ID；为空表示新建档案关联场景，返回全部未软删客户 */
  customerId: { type: Number, default: null },
  /** 额外排除的客户ID（前端过滤已选行） */
  excludeIds: { type: Array, default: () => [] }
})
const emit = defineEmits(['update:modelValue', 'select'])
const { proxy } = getCurrentInstance()

const keyword = ref('')
const rows = ref([])
const total = ref(0)
const page = ref(1)
const pageSize = 10
const loading = ref(false)
let requestVersion = 0

watch(
  () => props.modelValue,
  (v) => {
    if (v) {
      rows.value = []
      total.value = 0
      keyword.value = ''
      page.value = 1
      loadList()
    }
  }
)

async function loadList() {
  const version = ++requestVersion
  loading.value = true
  try {
    const res = await searchTargets({
      customerId: props.customerId || undefined,
      identityOnly: props.identityOnly || undefined,
      keyword: keyword.value || undefined,
      page: page.value,
      pageSize
    })
    if (version !== requestVersion || !props.modelValue) return
    rows.value = res?.items || []
    total.value = res?.total || 0
  } catch (e) {
    if (version === requestVersion && props.modelValue) proxy.$message.error(e?.message || '加载目标客户失败')
  } finally {
    if (version === requestVersion) loading.value = false
  }
}

function handleEnter(event) {
  if (!event.isComposing) handleSearch()
}

function handleSearch() {
  page.value = 1
  loadList()
}

function isExcluded(id) {
  return props.excludeIds.some((x) => Number(x) === Number(id))
}

function pickTarget(row) {
  if (isExcluded(row.id)) {
    proxy.$message.warning('该客户已选择，请选择其他客户')
    return
  }
  emit('select', row)
  emit('update:modelValue', false)
}
</script>

<style scoped>
.select-dialog .el-input {
  margin-bottom: 12px;
}
.target-list {
  min-height: 120px;
  max-height: 320px;
  overflow-y: auto;
}
.target-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 12px;
  border: 1px solid #ebeef5;
  border-radius: 6px;
  margin-bottom: 8px;
  cursor: pointer;
  transition: background 0.2s;
}
.target-item:hover {
  background: #f5f7fa;
}
.target-item.disabled {
  cursor: not-allowed;
  opacity: 0.6;
}
.target-info {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}
.target-name {
  font-weight: 600;
}
.pager {
  margin-top: 10px;
  justify-content: flex-end;
}
</style>
