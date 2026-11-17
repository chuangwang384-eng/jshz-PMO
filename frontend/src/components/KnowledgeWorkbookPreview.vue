<template>
  <section v-loading="loading" class="workbook-preview">
    <el-empty v-if="failed" description="无法预览工作簿，可下载原文件查看" />
    <template v-else-if="preview">
      <div class="workbook-controls">
        <el-select :model-value="preview.sheet" aria-label="工作表" :disabled="loading" @change="sheet => load(sheet)">
          <el-option v-for="sheet in preview.sheets" :key="sheet.index" :label="sheet.name" :value="sheet.index" />
        </el-select>
        <div v-if="preview.rowPages > 1" class="workbook-pagination">
          <el-button size="small" :disabled="loading || preview.rowPage === 0" @click="load(preview.sheet, preview.rowPage - 1, preview.columnPage)">上一组行</el-button>
          <span>行 {{ preview.rowPage + 1 }} / {{ preview.rowPages }}</span>
          <el-button size="small" :disabled="loading || preview.rowPage + 1 >= preview.rowPages" @click="load(preview.sheet, preview.rowPage + 1, preview.columnPage)">下一组行</el-button>
        </div>
        <div v-if="preview.columnPages > 1" class="workbook-pagination">
          <el-button size="small" :disabled="loading || preview.columnPage === 0" @click="load(preview.sheet, preview.rowPage, preview.columnPage - 1)">上一组列</el-button>
          <span>列 {{ preview.columnPage + 1 }} / {{ preview.columnPages }}</span>
          <el-button size="small" :disabled="loading || preview.columnPage + 1 >= preview.columnPages" @click="load(preview.sheet, preview.rowPage, preview.columnPage + 1)">下一组列</el-button>
        </div>
      </div>
      <el-empty v-if="!preview.columns.length || !preview.rows.length" description="当前范围没有可见单元格" />
      <div v-else ref="viewport" class="workbook-scroll" tabindex="0" aria-label="工作表内容">
        <table :style="{ width: tableWidth + 'px' }">
          <colgroup>
            <col style="width: 52px" />
            <col v-for="column in preview.columns" :key="column.label" :style="{ width: column.width + 'px' }" />
          </colgroup>
          <thead><tr><th scope="col">行</th><th v-for="column in preview.columns" :key="column.label" scope="col">{{ column.label }}</th></tr></thead>
          <tbody>
            <tr v-for="row in preview.rows" :key="row.number">
              <th scope="row">{{ row.number }}</th>
              <td v-for="(cell, index) in row.cells" :key="index" :rowspan="cell.rowSpan" :colspan="cell.columnSpan">{{ cell.text }}</td>
            </tr>
          </tbody>
        </table>
      </div>
    </template>
  </section>
</template>

<script setup>
import { computed, nextTick, onMounted, ref } from 'vue'
import { fetchKnowledgeWorkbook } from '../api/knowledge'

// fetcher 可选注入（2026-11-17）：默认走知识库 API；项目文件预览传入 fetchProjectWorkbook（同签名），
// 渲染与分页逻辑完全复用，不复制第二份
const props = defineProps({
  filePath: { type: String, required: true },
  fetcher: { type: Function, default: null }
})
const preview = ref(null)
const loading = ref(true)
const failed = ref(false)
const viewport = ref(null)
const tableWidth = computed(() => 52 + (preview.value?.columns || []).reduce((width, column) => width + column.width, 0))

async function load(sheet, rowPage = 0, columnPage = 0) {
  loading.value = true
  failed.value = false
  try {
    const load = props.fetcher || fetchKnowledgeWorkbook
    preview.value = await load(props.filePath, { sheet, rowPage, columnPage })
    await nextTick()
    viewport.value?.scrollTo(0, 0)
  } catch {
    failed.value = true
  } finally {
    loading.value = false
  }
}

onMounted(() => load())
</script>

<style scoped>
.workbook-preview { min-height: 180px; min-width: 0; }
.workbook-controls, .workbook-pagination { display: flex; align-items: center; flex-wrap: wrap; gap: 8px; }
.workbook-controls { margin-bottom: 12px; }
.workbook-controls .el-select { width: min(260px, 100%); }
.workbook-scroll { overflow: auto; max-height: 65vh; border: 1px solid var(--el-border-color); }
table { table-layout: fixed; border-collapse: separate; border-spacing: 0; color: var(--el-text-color-primary); }
th, td { border-right: 1px solid var(--el-border-color-lighter); border-bottom: 1px solid var(--el-border-color-lighter); padding: 6px 8px; }
th { background: var(--el-fill-color-light); font-weight: 500; text-align: center; }
thead th { position: sticky; top: 0; z-index: 1; }
td { white-space: pre-wrap; overflow-wrap: anywhere; vertical-align: top; }
</style>
