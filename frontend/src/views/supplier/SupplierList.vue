<template>
  <div>
    <div class="page-head">
      <span class="page-title">供应商管理</span>
      <span class="page-sub">酒店/打印店/媒体/影像/车队/票务档案·行价·评价（全员可直接维护）</span>
    </div>

    <div class="toolbar">
      <el-input
        v-model="filters.keyword"
        placeholder="名称 / 地址 / 联系人"
        clearable
        style="width: 200px"
        @keyup.enter="handleSearch"
        @clear="handleSearch"
      />
      <el-select v-model="filters.typeId" placeholder="全部类型" clearable style="width: 170px; margin-left: 8px" @change="handleSearch">
        <el-option v-for="t in types" :key="t.id" :label="t.name" :value="t.id" />
      </el-select>
      <el-select
        v-model="filters.cityId"
        placeholder="全部城市"
        clearable
        filterable
        style="width: 160px; margin-left: 8px"
        @change="handleSearch"
      >
        <el-option v-for="c in cities" :key="c.id" :label="c.name" :value="c.id" />
      </el-select>
      <el-button type="primary" style="margin-left: 8px" @click="handleSearch">搜索</el-button>
      <el-button @click="resetSearch">重置</el-button>
      <el-button v-permission="'supplier:create'" type="success" plain @click="openCreate">新增供应商</el-button>
      <el-button v-permission="'supplier:dict'" plain @click="typeDlgVisible = true">类型管理</el-button>
    </div>

    <div v-loading="loading" class="card-grid">
      <el-card v-for="s in records" :key="s.id" shadow="hover" class="sup-card" @click="goDetail(s.id)">
        <div class="card-cover">
          <el-image v-if="coverOf(s)" :src="imageUrl(coverOf(s))" fit="cover" class="cover-img" />
          <div v-else class="cover-empty">暂无图片</div>
        </div>
        <div class="card-body">
          <div class="card-title-row">
            <span class="card-name">{{ s.name }}</span>
            <el-tag size="small" :type="supplierTypeTagColor(s.typeBizKey)">{{ s.typeName }}</el-tag>
          </div>
          <div class="card-meta">{{ s.province ? s.province + ' · ' : '' }}{{ s.cityName || '-' }}</div>
          <div class="card-rate">
            <el-rate :model-value="s.avgStars || 0" disabled size="small" />
            <span class="rate-text">{{ s.avgStars ? s.avgStars + '（' + s.reviewCount + '条）' : '暂无评分' }}</span>
          </div>
        </div>
      </el-card>
      <el-empty v-if="!loading && !records.length" description="暂无供应商，点击右上角新增" style="grid-column: 1 / -1" />
    </div>

    <div class="pagination-wrap">
      <el-pagination
        v-model:current-page="page"
        v-model:page-size="size"
        :total="total"
        :page-sizes="[12, 24, 48]"
        layout="total, sizes, prev, pager, next, jumper"
        @size-change="handleSearch"
        @current-change="fetchList"
      />
    </div>

    <!-- 新增供应商 -->
    <el-dialog v-model="createVisible" title="新增供应商" width="620px" destroy-on-close>
      <el-form :model="form" label-width="90px">
        <el-form-item label="名称" required>
          <el-input v-model="form.name" maxlength="100" placeholder="供应商名称" />
        </el-form-item>
        <el-form-item label="类型" required>
          <el-select v-model="form.typeId" placeholder="选择类型" style="width: 100%">
            <el-option v-for="t in types" :key="t.id" :label="t.name" :value="t.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="省份">
          <el-select v-model="form.province" placeholder="选择省份（选填）" style="width: 100%" @change="onFormProvinceChange">
            <el-option v-for="p in provinceNames" :key="p" :label="p" :value="p" />
          </el-select>
        </el-form-item>
        <el-form-item label="城市" required>
          <el-select
            v-model="form.cityId"
            placeholder="选择城市（城市字典固定只读）"
            style="width: 100%"
            filterable
          >
            <el-option v-for="c in formCities" :key="c.id" :label="c.name" :value="c.id" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="isHotelOrPrint" label="初始绑定">
          <el-select
            v-model="form.bindTargetId"
            placeholder="绑定同城市对端（酒店↔打印店，选填）"
            clearable
            filterable
            style="width: 100%"
          >
            <el-option
              v-for="o in bindCandidates"
              :key="o.id"
              :label="`${o.name}（${o.cityName || '-'}）`"
              :value="o.id"
            />
          </el-select>
          <div class="upload-tip">仅列出同城市对端类型；绑定限制与详情页「添加绑定」一致（后端同城校验）</div>
        </el-form-item>
        <el-form-item label="地址">
          <el-input v-model="form.address" maxlength="200" />
        </el-form-item>
        <el-row :gutter="8">
          <el-col :span="12">
            <el-form-item label="联系人">
              <el-input v-model="form.contact" maxlength="50" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="电话">
              <el-input v-model="form.phone" maxlength="50" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="微信">
          <el-input v-model="form.wechat" maxlength="50" />
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="form.description" type="textarea" :rows="3" maxlength="1000" />
        </el-form-item>
        <el-form-item label="图片">
          <div style="width: 100%">
            <el-button plain @click="pickCreateFiles">选择图片（可多选）</el-button>
            <input ref="createFileInput" type="file" accept="image/*" multiple hidden @change="onCreateFilesPicked" />
            <div v-if="createFiles.length" class="create-file-list">
              <div v-for="(f, i) in createFiles" :key="i" class="create-file-item">
                <span class="create-file-name" :title="f.name">{{ f.name }}</span>
                <el-button link type="danger" size="small" @click="createFiles.splice(i, 1)">移除</el-button>
              </div>
            </div>
            <div class="upload-tip">保存时随创建一并上传（自动压缩，长边≤1280）；≤20 张、单张 ≤8MB、累计 ≤100MB；首图自动设为主图。留空则创建后可在详情页上传</div>
          </div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="confirmCreate">保存</el-button>
      </template>
    </el-dialog>

    <!-- 类型管理（supplier:dict） -->
    <el-dialog v-model="typeDlgVisible" title="类型管理" width="560px" destroy-on-close>
      <div class="dict-add-row">
        <el-input v-model="newTypeName" placeholder="新类型名称" maxlength="50" style="width: 200px" />
        <el-button type="primary" plain style="margin-left: 8px" @click="addType">新增</el-button>
      </div>
      <el-table :data="types" border stripe size="small" style="margin-top: 10px">
        <el-table-column prop="name" label="类型名称" min-width="160">
          <template #default="{ row }">
            <span>{{ row.name }}</span>
            <el-tag v-if="row.bizKey" size="small" type="info" style="margin-left: 6px">内置</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="sort" label="排序" width="80" />
        <el-table-column label="操作" width="180" align="center">
          <template #default="{ row }">
            <el-button link type="primary" @click="renameType(row)">改名</el-button>
            <el-button link type="danger" @click="removeType(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-dialog>

  </div>
</template>

<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  listSuppliers, listSupplierTypes, listSupplierCities,
  createSupplier, createSupplierType, updateSupplierType, deleteSupplierType, bindSupplier, uploadSupplierImage,
  supplierImageUrl
} from '../../api/supplier'
import { compressImage } from '../../utils/imageCompress'
import { supplierTypeTagColor, provinceList, citiesOfProvince } from '../../utils/supplier'

const router = useRouter()

const filters = ref({ keyword: '', typeId: null, province: null, cityId: null })
const records = ref([])
const total = ref(0)
const page = ref(1)
const size = ref(12)
const loading = ref(false)

const types = ref([])
const cities = ref([])
const provinceNames = computed(() => provinceList(cities.value))
const formCities = computed(() => citiesOfProvince(cities.value, form.value.province))

function imageUrl(fileName) {
  return supplierImageUrl(fileName)
}

// 封面取主图，无主图时回退第一张
function coverOf(s) {
  return s.mainImage || (s.imageList && s.imageList.length ? s.imageList[0] : '')
}

async function fetchDicts() {
  try {
    const [t, c] = await Promise.all([listSupplierTypes(), listSupplierCities()])
    types.value = t || []
    cities.value = c || []
  } catch (e) {
    // 拦截器已提示
  }
}

async function fetchList() {
  loading.value = true
  try {
    const data = await listSuppliers({
      keyword: filters.value.keyword || undefined,
      typeId: filters.value.typeId || undefined,
      cityId: filters.value.cityId || undefined,
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
  filters.value = { keyword: '', typeId: null, cityId: null }
  page.value = 1
  fetchList()
}

function goDetail(id) {
  router.push(`/supplier/${id}`)
}

// ==================== 新增 ====================
const createVisible = ref(false)
const form = ref({})
const saving = ref(false)

// 默认类型=酒店（bizKey=HOTEL）；酒店/打印店显示「初始绑定」选择
const HOTEL_BIZ = 'HOTEL'
const PRINT_BIZ = 'PRINT'
const isHotelOrPrint = computed(() => {
  const t = types.value.find((x) => x.id === form.value.typeId)
  return !!t && (t.bizKey === HOTEL_BIZ || t.bizKey === PRINT_BIZ)
})
const bindCandidates = ref([])

async function loadBindCandidates() {
  bindCandidates.value = []
  if (!isHotelOrPrint.value || !form.value.cityId) return
  const targetBiz = form.value.typeId && types.value.find((x) => x.id === form.value.typeId)?.bizKey === HOTEL_BIZ
    ? PRINT_BIZ
    : HOTEL_BIZ
  const targetType = types.value.find((x) => x.bizKey === targetBiz)
  if (!targetType) return
  try {
    const data = await listSuppliers({ typeId: targetType.id, cityId: form.value.cityId, page: 1, size: 200 })
    bindCandidates.value = data?.items || []
  } catch (e) {
    // 拦截器已提示
  }
}

function defaultTypeId() {
  const hotel = types.value.find((x) => x.bizKey === HOTEL_BIZ)
  return hotel ? hotel.id : (types.value.length ? types.value[0].id : null)
}

function openCreate() {
  form.value = { typeId: defaultTypeId(), province: null, cityId: null, bindTargetId: null }
  bindCandidates.value = []
  createFiles.value = []
  createVisible.value = true
}

// 直接上传图片：保存时随创建一并上传（压缩沿用 imageCompress，接口沿用 images 系列）
const createFiles = ref([])
const createFileInput = ref(null)

function pickCreateFiles() {
  createFileInput.value && createFileInput.value.click()
}

async function onCreateFilesPicked(e) {
  const files = Array.from(e.target.files || [])
  e.target.value = ''
  if (!files.length) return
  const exist = createFiles.value.length
  if (exist + files.length > 20) {
    ElMessage.warning('最多 20 张，请减少选择')
    return
  }
  createFiles.value.push(...files)
}

function onFormProvinceChange() {
  form.value.cityId = null
  loadBindCandidates()
}

watch(() => [form.value.typeId, form.value.cityId], () => {
  if (createVisible.value) loadBindCandidates()
})

async function confirmCreate() {
  if (!form.value.name || !form.value.name.trim()) return ElMessage.warning('请填写名称')
  if (!form.value.typeId) return ElMessage.warning('请选择类型')
  if (!form.value.cityId) return ElMessage.warning('请选择城市')
  saving.value = true
  try {
    const created = await createSupplier({
      name: form.value.name.trim(),
      typeId: form.value.typeId,
      cityId: form.value.cityId,
      address: form.value.address || undefined,
      contact: form.value.contact || undefined,
      phone: form.value.phone || undefined,
      wechat: form.value.wechat || undefined,
      description: form.value.description || undefined
    })
    // 初始绑定：沿用现有绑定接口（后端校验同城市与酒店↔打印店类型）
    let bindOk = true
    if (form.value.bindTargetId && created?.id) {
      try {
        await bindSupplier(created.id, form.value.bindTargetId)
      } catch (e) {
        // 绑定失败不回滚创建；提示后可在详情页重新绑定
        bindOk = false
      }
    }
    // 直接上传图片：逐张压缩上传（首图自动主图由后端保证）
    let imgOk = 0
    let imgFail = 0
    if (created?.id && createFiles.value.length) {
      for (const f of createFiles.value) {
        try {
          const compressed = await compressImage(f)
          await uploadSupplierImage(created.id, compressed)
          imgOk++
        } catch (e) {
          imgFail++
        }
      }
    }
    const parts = ['供应商已创建']
    if (form.value.bindTargetId && created?.id) parts.push(bindOk ? '绑定成功' : '初始绑定失败（可在详情页重新绑定）')
    if (createFiles.value.length) parts.push(`图片上传成功 ${imgOk} 张${imgFail ? `、失败 ${imgFail} 张（可在详情页补传）` : ''}`)
    if (bindOk && !imgFail) ElMessage.success(parts.join('，'))
    else ElMessage.warning(parts.join('，'))
    createVisible.value = false
    fetchDicts()
    fetchList()
  } catch (e) {
    // 拦截器已提示
  } finally {
    saving.value = false
  }
}

// ==================== 类型管理 ====================
const typeDlgVisible = ref(false)
const newTypeName = ref('')

async function addType() {
  if (!newTypeName.value.trim()) return ElMessage.warning('请输入类型名称')
  try {
    await createSupplierType({ name: newTypeName.value.trim() })
    newTypeName.value = ''
    ElMessage.success('已新增')
    fetchDicts()
  } catch (e) {
    // 拦截器已提示
  }
}

async function renameType(row) {
  try {
    const { value } = await ElMessageBox.prompt('修改类型名称', '类型改名', {
      inputValue: row.name,
      inputPattern: /\S+/,
      inputErrorMessage: '名称不能为空'
    })
    await updateSupplierType(row.id, { name: value.trim() })
    ElMessage.success('已修改')
    fetchDicts()
  } catch (e) {
    // 取消或拦截器已提示
  }
}

async function removeType(row) {
  try {
    await ElMessageBox.confirm(`确定删除类型「${row.name}」吗？有供应商引用时将无法删除。`, '删除类型', { type: 'warning' })
  } catch (e) {
    return
  }
  try {
    await deleteSupplierType(row.id)
    ElMessage.success('已删除')
    fetchDicts()
  } catch (e) {
    // 拦截器已提示（含引用禁删报错）
  }
}

onMounted(() => {
  fetchDicts()
  fetchList()
})
</script>

<style scoped>
.page-head { display: flex; align-items: baseline; gap: 12px; }
.page-title { font-size: 17px; font-weight: 600; color: #303133; }
.page-sub { font-size: 13px; color: #909399; }
.toolbar { display: flex; align-items: center; flex-wrap: wrap; gap: 4px 0; margin-top: 12px; }
.card-grid {
  margin-top: 16px;
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(240px, 1fr));
  gap: 14px;
  min-height: 200px;
}
.sup-card { cursor: pointer; }
:deep(.sup-card .el-card__body) { padding: 0; }
.card-cover { height: 140px; background: #f5f7fa; }
.cover-img { width: 100%; height: 140px; display: block; }
.cover-empty {
  height: 140px; display: flex; align-items: center; justify-content: center;
  color: #c0c4cc; font-size: 13px;
}
.card-body { padding: 10px 12px; }
.card-title-row { display: flex; align-items: center; justify-content: space-between; gap: 6px; }
.card-name { font-weight: 600; color: #303133; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.card-meta { margin-top: 4px; font-size: 12px; color: #909399; }
.card-rate { margin-top: 6px; display: flex; align-items: center; gap: 6px; }
.rate-text { font-size: 12px; color: #606266; }
.pagination-wrap { margin-top: 16px; display: flex; justify-content: flex-end; }
.dict-add-row { display: flex; align-items: center; }
.create-file-list { margin-top: 6px; display: flex; flex-direction: column; gap: 2px; }
.create-file-item { display: flex; align-items: center; gap: 8px; font-size: 13px; }
.create-file-name { flex: 1; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
</style>
