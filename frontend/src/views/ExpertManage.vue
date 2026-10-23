<template>
  <div>
    <div class="toolbar">
      <el-input
        v-model="keyword"
        placeholder="按姓名 / 单位 / 科室 / 职称 / 研究方向 / 擅长领域检索"
        clearable
        style="width: 320px"
        @keyup.enter="handleSearch"
      >
        <template #append>
          <el-button @click="handleSearch">搜索</el-button>
        </template>
      </el-input>
      <div class="spacer" />
      <el-button v-permission="'expert:add'" type="primary" @click="openCreate">新增专家</el-button>
      <el-button v-permission="'expert:import'" @click="handleTemplate">Excel 模板</el-button>
      <el-upload
        v-permission="'expert:import'"
        :show-file-list="false"
        :before-upload="handleImportFile"
        accept=".xlsx,.xls"
        style="display: inline-block; margin-left: 8px"
      >
        <el-button>Excel 导入</el-button>
      </el-upload>
      <el-button v-permission="'expert:export'" type="warning" @click="handleExportFullZip">全量导出 ZIP</el-button>
      <el-button v-permission="'expert:export'" :disabled="!selectedIds.length" @click="handleSelectedExcel">选中导出 Excel</el-button>
      <el-button v-permission="'expert:export'" :disabled="!selectedIds.length" @click="handleSelectedZip">选中导出 ZIP</el-button>
    </div>

    <el-table
      :data="experts"
      border
      stripe
      v-loading="loading"
      style="margin-top: 16px"
      @selection-change="onSelectionChange"
    >
      <el-table-column type="selection" width="45" />
      <el-table-column prop="id" label="ID" width="70" />
      <el-table-column prop="姓名" label="姓名" width="100" fixed />
      <el-table-column prop="单位" label="单位" width="140" show-overflow-tooltip />
      <el-table-column prop="科室" label="科室" width="120" show-overflow-tooltip />
      <el-table-column prop="职称" label="职称" width="100" />
      <el-table-column prop="研究方向" label="研究方向" width="130" show-overflow-tooltip />
      <el-table-column prop="擅长领域" label="擅长领域" width="130" show-overflow-tooltip />
      <el-table-column prop="联系方式" label="联系方式" width="130" />
      <el-table-column prop="简介" label="简介" width="130" show-overflow-tooltip />
      <el-table-column prop="身份证号" label="身份证号" width="175" />
      <el-table-column prop="银行卡号" label="银行卡号" width="175" />
      <el-table-column prop="银行卡开户行" label="银行卡开户行" width="130" show-overflow-tooltip />
      <el-table-column label="证件照" width="88" align="center">
        <template #default="{ row }">
          <PhotoImage :filename="row.照片" />
        </template>
      </el-table-column>
      <el-table-column label="身份证正面" width="88" align="center">
        <template #default="{ row }">
          <PhotoImage :filename="row.身份证正面" />
        </template>
      </el-table-column>
      <el-table-column label="身份证反面" width="88" align="center">
        <template #default="{ row }">
          <PhotoImage :filename="row.身份证反面" />
        </template>
      </el-table-column>
      <el-table-column prop="下载次数" label="下载次数" width="88" align="center" />
      <el-table-column label="操作" width="120" fixed="right" align="center">
        <template #default="{ row }">
          <el-button v-permission="'expert:edit'" link type="primary" @click="openEdit(row)">编辑</el-button>
          <el-button v-permission="'expert:delete'" link type="danger" @click="handleDelete(row)">删除</el-button>
        </template>
      </el-table-column>
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

    <!-- 新增 / 编辑 -->
    <el-dialog v-model="formVisible" :title="form.id ? '编辑专家' : '新增专家'" width="780px" destroy-on-close>
      <el-form ref="formRef" :model="form" :rules="formRules" label-width="110px">
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="姓名" prop="姓名">
              <el-input v-model="form.姓名" placeholder="请输入姓名" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="单位" prop="单位">
              <el-input v-model="form.单位" placeholder="请输入单位" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="科室" prop="科室">
              <el-input v-model="form.科室" placeholder="请输入科室" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="职称" prop="职称">
              <el-input v-model="form.职称" placeholder="请输入职称" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="研究方向" prop="研究方向">
              <el-input v-model="form.研究方向" placeholder="请输入研究方向" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="擅长领域" prop="擅长领域">
              <el-input v-model="form.擅长领域" placeholder="请输入擅长领域" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="联系方式" prop="联系方式">
              <el-input v-model="form.联系方式" placeholder="手机号或邮箱" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="身份证号" prop="身份证号">
              <el-input v-model="form.身份证号" placeholder="18 位身份证号" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="银行卡号" prop="银行卡号">
              <el-input v-model="form.银行卡号" placeholder="13-19 位银行卡号" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="银行卡开户行" prop="银行卡开户行">
              <el-input v-model="form.银行卡开户行" placeholder="请输入开户行" />
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="简介" prop="简介">
              <el-input v-model="form.简介" type="textarea" :rows="3" placeholder="请输入简介" />
            </el-form-item>
          </el-col>
        </el-row>

        <el-divider content-position="left">照片</el-divider>
        <el-row :gutter="16">
          <el-col :span="8">
            <el-form-item label="证件照">
              <el-upload
                :auto-upload="false"
                :limit="1"
                :show-file-list="true"
                accept=".jpg,.jpeg,.png,.gif,.webp,.bmp"
                :on-change="(file, fileList) => onPhotoChange('photo', file, fileList)"
                :on-exceed="onPhotoExceed"
              >
                <el-button size="small">选择证件照</el-button>
              </el-upload>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="身份证正面">
              <el-upload
                :auto-upload="false"
                :limit="1"
                :show-file-list="true"
                accept=".jpg,.jpeg,.png,.gif,.webp,.bmp"
                :on-change="(file, fileList) => onPhotoChange('idCardFront', file, fileList)"
                :on-exceed="onPhotoExceed"
              >
                <el-button size="small">选择身份证正面</el-button>
              </el-upload>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="身份证反面">
              <el-upload
                :auto-upload="false"
                :limit="1"
                :show-file-list="true"
                accept=".jpg,.jpeg,.png,.gif,.webp,.bmp"
                :on-change="(file, fileList) => onPhotoChange('idCardBack', file, fileList)"
                :on-exceed="onPhotoExceed"
              >
                <el-button size="small">选择身份证反面</el-button>
              </el-upload>
            </el-form-item>
          </el-col>
        </el-row>
        <div class="photo-tip">照片仅支持 jpg / jpeg / png / gif / webp / bmp，单张不超过 5MB</div>
      </el-form>
      <template #footer>
        <el-button @click="formVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitForm">保存</el-button>
      </template>
    </el-dialog>

    <!-- 导入结果 -->
    <el-dialog v-model="importVisible" title="导入结果" width="640px">
      <el-alert
        :type="importResult.failed?.length ? 'warning' : 'success'"
        :closable="false"
        show-icon
        :title="`导入完成：成功 ${importResult.success} 条 / 共 ${importResult.total} 行`"
        style="margin-bottom: 12px"
      />
      <el-table v-if="importResult.failed?.length" :data="importResult.failed" border max-height="360">
        <el-table-column prop="line" label="行号" width="90" align="center" />
        <el-table-column prop="name" label="姓名" width="140" />
        <el-table-column prop="reason" label="失败原因" />
      </el-table>
      <el-empty v-else description="全部导入成功" :image-size="80" />
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  listExperts,
  createExpert,
  updateExpert,
  deleteExpert,
  downloadTemplate,
  importExperts,
  exportFullZip,
  exportSelectedExcel,
  exportSelectedZip
} from '../api/expert'
import PhotoImage from '../components/PhotoImage.vue'

const keyword = ref('')
const experts = ref([])
const total = ref(0)
const page = ref(1)
const size = ref(20)
const loading = ref(false)
const selectedIds = ref([])

const formVisible = ref(false)
const submitting = ref(false)
const formRef = ref()
const form = reactive({
  id: null,
  姓名: '', 单位: '', 科室: '', 职称: '', 研究方向: '', 擅长领域: '',
  联系方式: '', 简介: '', 身份证号: '', 银行卡号: '', 银行卡开户行: ''
})
// 三类照片待上传文件
const fileMap = reactive({ photo: null, idCardFront: null, idCardBack: null })

const importVisible = ref(false)
const importResult = reactive({ success: 0, total: 0, failed: [] })

const formRules = {
  姓名: [{ required: true, message: '请输入姓名', trigger: 'blur' }],
  身份证号: [{ pattern: /^\d{17}[\dXx]$/, message: '身份证号格式不正确', trigger: 'blur' }],
  银行卡号: [{ pattern: /^\d{13,19}$/, message: '银行卡号格式不正确', trigger: 'blur' }],
  联系方式: [{ pattern: /^1\d{10}$|^[\w.-]+@[\w.-]+\.[a-zA-Z]{2,}$/, message: '联系方式格式不正确', trigger: 'blur' }]
}

async function fetchList() {
  loading.value = true
  try {
    const data = await listExperts({
      keyword: keyword.value || undefined,
      page: page.value,
      size: size.value,
      sensitive: 1
    })
    experts.value = data?.items || []
    total.value = data?.total || 0
  } finally {
    loading.value = false
  }
}

function handleSearch() {
  page.value = 1
  fetchList()
}

function onSelectionChange(rows) {
  selectedIds.value = rows.map((r) => r.id)
}

function openCreate() {
  Object.assign(form, {
    id: null, 姓名: '', 单位: '', 科室: '', 职称: '', 研究方向: '', 擅长领域: '',
    联系方式: '', 简介: '', 身份证号: '', 银行卡号: '', 银行卡开户行: ''
  })
  fileMap.photo = null
  fileMap.idCardFront = null
  fileMap.idCardBack = null
  formVisible.value = true
}

function openEdit(row) {
  Object.assign(form, {
    id: row.id,
    姓名: row.姓名, 单位: row.单位, 科室: row.科室, 职称: row.职称,
    研究方向: row.研究方向, 擅长领域: row.擅长领域,
    联系方式: row.联系方式, 简介: row.简介,
    身份证号: row.身份证号, 银行卡号: row.银行卡号, 银行卡开户行: row.银行卡开户行
  })
  fileMap.photo = null
  fileMap.idCardFront = null
  fileMap.idCardBack = null
  formVisible.value = true
}

function onPhotoChange(key, file) {
  fileMap[key] = file?.raw || null
}

function onPhotoExceed() {
  ElMessage.warning('每类照片仅支持上传 1 张')
}

function buildFormData() {
  const fd = new FormData()
  fd.append('姓名', form.姓名)
  fd.append('单位', form.单位 || '')
  fd.append('科室', form.科室 || '')
  fd.append('职称', form.职称 || '')
  fd.append('研究方向', form.研究方向 || '')
  fd.append('擅长领域', form.擅长领域 || '')
  fd.append('联系方式', form.联系方式 || '')
  fd.append('简介', form.简介 || '')
  fd.append('身份证号', form.身份证号 || '')
  fd.append('银行卡号', form.银行卡号 || '')
  fd.append('银行卡开户行', form.银行卡开户行 || '')
  if (fileMap.photo) fd.append('photo', fileMap.photo)
  if (fileMap.idCardFront) fd.append('idCardFront', fileMap.idCardFront)
  if (fileMap.idCardBack) fd.append('idCardBack', fileMap.idCardBack)
  return fd
}

async function submitForm() {
  try {
    await formRef.value.validate()
  } catch (e) {
    return
  }
  submitting.value = true
  try {
    if (form.id) {
      await updateExpert(form.id, buildFormData())
      ElMessage.success('修改成功')
    } else {
      await createExpert(buildFormData())
      ElMessage.success('新增成功')
    }
    formVisible.value = false
    fetchList()
  } catch (e) {
    // 错误提示已由拦截器统一处理
  } finally {
    submitting.value = false
  }
}

async function handleDelete(row) {
  try {
    await ElMessageBox.confirm(
      `确定删除专家「${row.姓名}」吗？其照片文件将一并物理删除，此操作不可恢复。`,
      '删除确认',
      { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' }
    )
  } catch (e) {
    return
  }
  await deleteExpert(row.id)
  ElMessage.success('删除成功')
  fetchList()
}

function handleTemplate() {
  downloadTemplate().catch(() => {})
}

async function handleImportFile(file) {
  try {
    const res = await importExperts(file)
    importResult.success = res?.success || 0
    importResult.total = res?.total || 0
    importResult.failed = res?.failed || []
    importVisible.value = true
    fetchList()
  } catch (e) {
    // 错误提示已由拦截器统一处理
  }
  return false
}

function handleExportFullZip() {
  exportFullZip().catch(() => {})
}

function handleSelectedExcel() {
  if (!selectedIds.value.length) return
  exportSelectedExcel(selectedIds.value).catch(() => {})
}

function handleSelectedZip() {
  if (!selectedIds.value.length) return
  exportSelectedZip(selectedIds.value).catch(() => {})
}

onMounted(fetchList)
</script>

<style scoped>
.toolbar {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
}
.spacer {
  flex: 1;
}
.pagination-wrap {
  margin-top: 16px;
  display: flex;
  justify-content: flex-end;
}
.photo-tip {
  color: #909399;
  font-size: 12px;
  margin-top: -8px;
}
</style>
