<template>
  <div v-permission="'audit:view'">
    <div class="toolbar">
      <el-input
        v-model="keyword"
        placeholder="按下载人搜索"
        clearable
        style="width: 260px"
        @keyup.enter="handleSearch"
      >
        <template #append>
          <el-button @click="handleSearch">搜索</el-button>
        </template>
      </el-input>
      <el-button style="margin-left: 8px" @click="resetSearch">重置</el-button>
    </div>

    <el-table
      :data="records"
      border
      stripe
      v-loading="loading"
      style="margin-top: 16px"
    >
      <el-table-column prop="username" label="下载人" width="160" />
      <el-table-column prop="expertName" label="专家" width="160">
        <template #default="{ row }">{{ row.expertName || '全量数据' }}</template>
      </el-table-column>
      <el-table-column prop="downloadType" label="类型" width="160">
        <template #default="{ row }">
          <el-tag type="info">{{ downloadTypeMap[row.downloadType] || row.downloadType }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="downloadTime" label="时间" width="200" />
      <el-table-column prop="ip" label="IP" />
    </el-table>

    <div class="pagination-wrap">
      <el-pagination
        v-model:current-page="page"
        v-model:page-size="size"
        :total="total"
        :page-sizes="[10, 20, 50, 100]"
        layout="total, sizes, prev, pager, next, jumper"
        @size-change="fetchList"
        @current-change="fetchList"
      />
    </div>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { listDownloads } from '../api/audit'

const downloadTypeMap = {
  selected_csv: '选中导出 CSV',
  selected_zip: '选中导出 ZIP',
  full_csv: '全量 CSV',
  full_zip: '全量 ZIP',
  employee_zip: '员工 ZIP',
  md: '下载说明',
  md_full: '下载说明（完整）'
}

const keyword = ref('')
const records = ref([])
const total = ref(0)
const page = ref(1)
const size = ref(20)
const loading = ref(false)

async function fetchList() {
  loading.value = true
  try {
    const data = await listDownloads({
      username: keyword.value || undefined,
      page: page.value,
      size: size.value
    })
    records.value = data?.items || []
    total.value = data?.total || 0
  } finally {
    loading.value = false
  }
}

function handleSearch() {
  page.value = 1
  fetchList()
}

function resetSearch() {
  keyword.value = ''
  page.value = 1
  fetchList()
}

onMounted(fetchList)
</script>

<style scoped>
.toolbar {
  display: flex;
  align-items: center;
}
.pagination-wrap {
  margin-top: 16px;
  display: flex;
  justify-content: flex-end;
}
</style>
