<template>
  <div class="supplier-page">
    <el-card shadow="never" class="supplier-panel">
      <!-- 第一行：搜索 + 类型 + 城市 + 重置（管理员入口在右侧） -->
      <div class="filter-row">
        <el-input
          v-model="filters.keyword"
          class="kw-input"
          placeholder="搜索供应商名称、联系人"
          clearable
          :prefix-icon="Search"
          @input="onKeywordInput"
          @keyup.enter="handleSearch"
          @clear="handleSearch"
        />
        <el-select
          v-model="filters.typeId"
          class="filter-select"
          placeholder="全部类型"
          clearable
          @change="handleSearch"
        >
          <el-option v-for="t in types" :key="t.id" :label="t.name" :value="t.id" />
        </el-select>
        <el-select
          v-model="filters.cityId"
          class="filter-select"
          placeholder="全部城市"
          clearable
          filterable
          @change="handleSearch"
        >
          <el-option v-for="c in cities" :key="c.id" :label="c.name" :value="c.id" />
        </el-select>
        <el-button class="reset-btn" @click="resetSearch">重置</el-button>

        <div class="filter-actions">
          <el-button v-permission="'supplier:create'" type="primary" @click="openCreate">新增供应商</el-button>
          <el-button v-permission="'supplier:dict'" @click="typeDlgVisible = true">类型管理</el-button>
        </div>
      </div>

      <!-- 第二行：仅看已评价 + 视图切换 -->
      <div class="view-row">
        <el-checkbox v-model="filters.onlyReviewed" class="reviewed-check" @change="handleSearch">
          仅看已评价
        </el-checkbox>
        <div class="view-toggle">
          <button
            type="button"
            class="vt-btn"
            :class="{ 'is-active': viewMode === 'list' }"
            @click="setViewMode('list')"
          >列表视图</button>
          <button
            type="button"
            class="vt-btn"
            :class="{ 'is-active': viewMode === 'card' }"
            @click="setViewMode('card')"
          >卡片视图</button>
        </div>
      </div>

      <el-divider class="panel-divider" />

      <!-- 汇总行 -->
      <div class="summary-row">
        <span class="summary-text">共 {{ total }} 家供应商</span>
        <el-select v-model="filters.sort" class="sort-select" size="small" @change="handleSearch">
          <el-option label="按名称排序" value="name" />
          <el-option label="按更新时间排序" value="updated" />
        </el-select>
      </div>

      <!-- 列表视图 -->
      <div v-if="viewMode === 'list'" v-loading="loading" class="sup-list">
        <div v-for="s in records" :key="s.id" class="sup-row" @click="goDetail(s.id)">
          <div class="row-icon">
            <el-icon><component :is="typeIcon(s.typeBizKey)" /></el-icon>
          </div>
          <div class="row-main">
            <div class="row-title">
              <span class="row-name">{{ s.name }}</span>
              <span v-if="s.typeName" class="type-pill">{{ s.typeName }}</span>
            </div>
            <div class="row-meta">{{ metaLine(s) }}</div>
            <div class="row-rate">
              <template v-if="s.reviewCount">
                <el-icon class="star-icon"><StarFilled /></el-icon>
                <span class="rate-score">{{ s.avgStars }}</span>
                <span class="rate-count">（{{ s.reviewCount }}条评价）</span>
              </template>
              <span v-else class="rate-empty">尚无评价</span>
            </div>
          </div>
          <div class="row-side">
            <el-button class="detail-btn" size="small" @click.stop="goDetail(s.id)">查看详情</el-button>
          </div>
        </div>
        <el-empty v-if="!loading && !records.length" description="没有符合条件的供应商" />
      </div>

      <!-- 卡片视图 -->
      <div v-else v-loading="loading" class="card-grid">
        <el-card v-for="s in records" :key="s.id" shadow="hover" class="sup-card" @click="goDetail(s.id)">
          <div class="card-cover">
            <el-image v-if="coverOf(s)" :src="imageUrl(coverOf(s))" fit="cover" class="cover-img" />
            <div v-else class="cover-empty">暂无图片</div>
          </div>
          <div class="card-body">
            <div class="card-title-row">
              <span class="card-name" :title="s.name">{{ s.name }}</span>
              <el-tag size="small" :type="supplierTypeTagColor(s.typeBizKey)">{{ s.typeName }}</el-tag>
            </div>
            <div class="card-meta">{{ metaLine(s) }}</div>
            <div class="card-rate">
              <el-rate :model-value="s.avgStars || 0" disabled size="small" />
              <span class="rate-text">{{ s.reviewCount ? s.avgStars + '（' + s.reviewCount + '条）' : '暂无评分' }}</span>
            </div>
          </div>
        </el-card>
        <el-empty v-if="!loading && !records.length" description="没有符合条件的供应商" style="grid-column: 1 / -1" />
      </div>

      <div class="pagination-wrap">
        <el-pagination
          v-model:current-page="page"
          v-model:page-size="size"
          :total="total"
          :page-sizes="[10, 20, 50]"
          layout="total, sizes, prev, pager, next, jumper"
          @size-change="handleSearch"
          @current-change="fetchList"
        />
      </div>
    </el-card>

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
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  Search, StarFilled, OfficeBuilding, Printer, VideoCamera, Camera, Van, Ticket, Files
} from '@element-plus/icons-vue'
import {
  listSuppliers, listSupplierTypes, listSupplierCities,
  createSupplier, createSupplierType, updateSupplierType, deleteSupplierType, bindSupplier, uploadSupplierImage,
  supplierImageUrl
} from '../../api/supplier'
import { compressImage } from '../../utils/imageCompress'
import { supplierTypeTagColor, provinceList, citiesOfProvince } from '../../utils/supplier'

const router = useRouter()
const route = useRoute()

// 筛选条件：keyword/typeId/cityId 与后端 list 接口一一对应，onlyReviewed/sort 为 1.0.37.19 新增
const filters = ref({ keyword: '', typeId: null, cityId: null, onlyReviewed: false, sort: 'name' })
const records = ref([])
const total = ref(0)
const page = ref(1)
const size = ref(10)
const loading = ref(false)

const types = ref([])
const cities = ref([])
const provinceNames = computed(() => provinceList(cities.value))
const formCities = computed(() => citiesOfProvince(cities.value, form.value.province))

// ==================== 视图切换（列表 / 卡片） ====================
const VIEW_KEY = 'supplier-view-mode'
const viewMode = ref('list')

function setViewMode(mode) {
  if (viewMode.value === mode) return
  viewMode.value = mode
  try {
    localStorage.setItem(VIEW_KEY, mode)
  } catch (e) {
    // 隐私模式等场景忽略
  }
  syncRouteQuery()
}

// ==================== 类型图标（按内置业务键映射，自定义类型用通用图标） ====================
const TYPE_ICONS = {
  HOTEL: OfficeBuilding,
  PRINT: Printer,
  MEDIA: VideoCamera,
  IMAGING: Camera,
  FLEET: Van,
  TICKET: Ticket
}

function typeIcon(bizKey) {
  return TYPE_ICONS[bizKey] || Files
}

function metaLine(s) {
  return [s.province, s.cityName, s.contact].filter((x) => !!x).join(' · ') || '未填写属地与联系人'
}

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

// ==================== 查询状态与 URL 双向同步（从详情页返回可还原筛选/页码/视图） ====================
function toId(v) {
  const n = Number(v)
  return Number.isFinite(n) && n > 0 ? n : null
}

function initFromRoute() {
  const q = route.query
  filters.value = {
    keyword: String(q.keyword || ''),
    typeId: toId(q.typeId),
    cityId: toId(q.cityId),
    onlyReviewed: q.onlyReviewed === '1',
    sort: q.sort === 'updated' ? 'updated' : 'name'
  }
  page.value = toId(q.page) || 1
  size.value = [10, 20, 50].includes(Number(q.pageSize)) ? Number(q.pageSize) : 10
  if (q.view === 'card' || q.view === 'list') viewMode.value = q.view
}

// 只 replace 不 push，避免污染历史栈；默认值不写入 URL，保持地址干净
function syncRouteQuery() {
  const next = {
    keyword: filters.value.keyword || undefined,
    typeId: filters.value.typeId ? String(filters.value.typeId) : undefined,
    cityId: filters.value.cityId ? String(filters.value.cityId) : undefined,
    onlyReviewed: filters.value.onlyReviewed ? '1' : undefined,
    sort: filters.value.sort !== 'name' ? filters.value.sort : undefined,
    page: page.value > 1 ? String(page.value) : undefined,
    pageSize: size.value !== 10 ? String(size.value) : undefined,
    view: viewMode.value === 'card' ? 'card' : undefined
  }
  const keys = ['keyword', 'typeId', 'cityId', 'onlyReviewed', 'sort', 'page', 'pageSize', 'view']
  const same = keys.every((k) => String(route.query[k] || '') === String(next[k] || ''))
  if (!same) router.replace({ query: next })
}

async function fetchList() {
  syncRouteQuery()
  loading.value = true
  try {
    const data = await listSuppliers({
      keyword: filters.value.keyword || undefined,
      typeId: filters.value.typeId || undefined,
      cityId: filters.value.cityId || undefined,
      onlyReviewed: filters.value.onlyReviewed ? true : undefined,
      sort: filters.value.sort || undefined,
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

// 输入即搜（400ms 防抖），回车立即搜
let keywordTimer = null
function onKeywordInput() {
  if (keywordTimer) clearTimeout(keywordTimer)
  keywordTimer = setTimeout(() => {
    keywordTimer = null
    handleSearch()
  }, 400)
}

function resetSearch() {
  if (keywordTimer) {
    clearTimeout(keywordTimer)
    keywordTimer = null
  }
  filters.value = { keyword: '', typeId: null, cityId: null, onlyReviewed: false, sort: 'name' }
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
  // 先按 URL 还原（详情页返回），URL 未带视图偏好时回退本地记忆
  initFromRoute()
  if (!route.query.view) {
    try {
      const saved = localStorage.getItem(VIEW_KEY)
      if (saved === 'list' || saved === 'card') viewMode.value = saved
    } catch (e) {
      // 忽略
    }
  }
  fetchDicts()
  fetchList()
})

onBeforeUnmount(() => {
  if (keywordTimer) clearTimeout(keywordTimer)
})
</script>

<style scoped>
.supplier-panel { padding: 2px 4px; }
:deep(.supplier-panel .el-card__body) { padding: 14px 16px 8px; }

/* ---------- 第一行筛选 ---------- */
.filter-row { display: flex; align-items: center; flex-wrap: wrap; gap: 10px; }
.kw-input { width: 300px; }
.filter-select { width: 150px; }
.reset-btn { color: #606266; }
.filter-actions { margin-left: auto; display: flex; align-items: center; gap: 8px; }

/* ---------- 第二行：仅看已评价 + 视图切换 ---------- */
.view-row { display: flex; align-items: center; justify-content: space-between; margin-top: 10px; }
.reviewed-check { font-size: 14px; }
.view-toggle { display: inline-flex; align-items: center; gap: 6px; }
.vt-btn {
  border: none;
  background: transparent;
  cursor: pointer;
  font-size: 14px;
  color: #909399;
  padding: 5px 14px;
  border-radius: 8px;
  line-height: 1.4;
  transition: background-color .15s, color .15s;
}
.vt-btn:hover { color: #303133; }
.vt-btn.is-active { background: #f1f2f5; color: #303133; font-weight: 600; }

.panel-divider { margin: 12px 0 4px; }

/* ---------- 汇总行 ---------- */
.summary-row { display: flex; align-items: center; justify-content: space-between; padding: 4px 2px 8px; }
.summary-text { font-size: 13px; color: #909399; }
.sort-select { width: 140px; }
:deep(.sort-select .el-select__wrapper) {
  box-shadow: none !important;
  background: transparent;
  padding-left: 0;
}
:deep(.sort-select .el-select__placeholder) { color: #909399; font-size: 13px; }

/* ---------- 列表视图 ---------- */
.sup-list { min-height: 160px; }
.sup-row {
  display: flex;
  align-items: flex-start;
  gap: 14px;
  padding: 16px 8px;
  border-bottom: 1px solid #eef1f5;
  cursor: pointer;
  transition: background-color .15s;
}
.sup-row:last-child { border-bottom: none; }
.sup-row:hover { background: #f7f9fc; }
.row-icon {
  flex: 0 0 26px;
  width: 26px;
  height: 26px;
  margin-top: 2px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #606266;
  font-size: 17px;
}
.row-main { flex: 1; min-width: 0; }
.row-title { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; }
.row-name {
  font-size: 15px;
  font-weight: 600;
  color: #303133;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  max-width: 420px;
}
.type-pill {
  font-size: 12px;
  color: #606266;
  border: 1px solid #e3e6eb;
  border-radius: 999px;
  padding: 1px 8px;
  line-height: 18px;
}
.row-meta { margin-top: 6px; font-size: 13px; color: #909399; }
.row-rate { margin-top: 6px; display: flex; align-items: center; gap: 4px; font-size: 13px; }
.star-icon { color: #f5a623; font-size: 14px; }
.rate-score { color: #303133; font-weight: 600; }
.rate-count { color: #909399; }
.rate-empty { color: #b1b5bd; }

.row-side {
  flex: 0 0 auto;
  display: flex;
  align-items: flex-start;
  padding-top: 2px;
}
.detail-btn { color: #303133; border-color: #dcdfe6; border-radius: 8px; }

/* ---------- 卡片视图 ---------- */
.card-grid {
  margin-top: 4px;
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(240px, 1fr));
  gap: 14px;
  min-height: 200px;
}
.sup-card { cursor: pointer; }
:deep(.sup-card .el-card__body) { padding: 0; }
.card-cover { position: relative; height: 140px; background: #f5f7fa; }
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

.pagination-wrap { margin-top: 14px; padding-bottom: 6px; display: flex; justify-content: flex-end; }
.dict-add-row { display: flex; align-items: center; }
.create-file-list { margin-top: 6px; display: flex; flex-direction: column; gap: 2px; }
.create-file-item { display: flex; align-items: center; gap: 8px; font-size: 13px; }
.create-file-name { flex: 1; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.upload-tip { margin-top: 4px; font-size: 12px; color: #909399; line-height: 1.5; }
</style>
