<template>
  <div v-loading="loading">
    <div class="page-head">
      <el-button link @click="goBack">← 返回列表</el-button>
    </div>

    <template v-if="s">
      <el-card shadow="never">
        <div class="head-row">
          <div>
            <span class="sup-name">{{ s.name }}</span>
            <el-tag :type="typeTagColor(s.typeBizKey)" style="margin-left: 10px">{{ s.typeName }}</el-tag>
          </div>
          <div>
            <el-button plain @click="openEdit">编辑</el-button>
            <el-button type="danger" plain @click="handleDelete">删除</el-button>
          </div>
        </div>
        <div class="head-meta">
          <span>{{ s.province ? s.province + ' · ' : '' }}{{ s.cityName || '-' }}</span>
          <span v-if="s.address">｜{{ s.address }}</span>
        </div>
        <div class="head-meta muted">
          <span v-if="s.contact">联系人：{{ s.contact }}</span>
          <span v-if="s.phone">｜电话：{{ s.phone }}</span>
          <span v-if="s.wechat">｜微信：{{ s.wechat }}</span>
        </div>
        <div class="head-meta muted">建档：{{ s.createBy || '-' }} ｜ {{ s.createTime }}</div>
        <div v-if="s.description" class="head-desc">{{ s.description }}</div>
        <div class="head-rate">
          <el-rate :model-value="s.avgStars || 0" disabled />
          <span class="rate-text">{{ s.avgStars ? `${s.avgStars} 分（${s.reviewCount} 条评价）` : '暂无评分' }}</span>
        </div>
      </el-card>

      <!-- 图库（多图，压缩上传，可删） -->
      <el-card shadow="never" class="sec-card">
        <template #header>
          <div class="sec-head"><span>图片（{{ imageList.length }}）</span></div>
        </template>
        <div class="gallery">
          <div v-for="(img, i) in imageList" :key="img" class="g-item">
            <el-image :src="imageUrl(img)" fit="cover" class="g-img" :preview-src-list="imageList.map(imageUrl)" :initial-index="i" />
            <el-button class="g-del" circle size="small" type="danger" @click="removeImage(i)">×</el-button>
          </div>
          <div v-if="uploading" class="g-item g-add loading-add"><el-icon class="is-loading"><Loading /></el-icon></div>
          <div v-else class="g-item g-add" @click="pickFiles">＋<div class="g-add-tip">压缩上传</div></div>
          <input ref="fileInput" type="file" accept="image/*" multiple hidden @change="onFilesPicked" />
        </div>
      </el-card>

      <!-- 行价 -->
      <el-card shadow="never" class="sec-card">
        <template #header>
          <div class="sec-head"><span>行价（{{ priceItems.length }}）</span><el-button link type="primary" @click="openPriceEdit">编辑行价</el-button></div>
        </template>
        <el-table v-if="priceItems.length" :data="priceItems" border stripe size="small">
          <el-table-column prop="itemName" label="服务项" min-width="160" />
          <el-table-column prop="price" label="价格" width="110" align="right" />
          <el-table-column prop="unit" label="单位" width="90" align="center" />
          <el-table-column prop="remark" label="备注" min-width="200" show-overflow-tooltip>
            <template #default="{ row }">{{ row.remark || '-' }}</template>
          </el-table-column>
        </el-table>
        <el-empty v-else description="暂无行价" :image-size="60" />
      </el-card>

      <!-- 评价（即发即显） -->
      <el-card shadow="never" class="sec-card">
        <template #header><div class="sec-head"><span>评价（{{ reviews.length }}）</span></div></template>
        <div class="review-form">
          <el-rate v-model="reviewStars" />
          <el-input
            v-model="reviewContent"
            type="textarea"
            :rows="2"
            maxlength="500"
            show-word-limit
            placeholder="写下您的评价（1-5 星 + 文字）"
            style="margin-top: 6px"
          />
          <div class="review-submit">
            <el-button type="primary" size="small" :loading="reviewing" :disabled="!reviewStars || !reviewContent.trim()" @click="submitReview">提交评价</el-button>
          </div>
        </div>
        <div v-if="reviews.length" class="review-list">
          <div v-for="r in reviews" :key="r.id" class="review-item">
            <div class="review-head">
              <el-rate :model-value="r.stars" disabled size="small" />
              <span class="review-meta">{{ r.username }} ｜ {{ r.createTime }}</span>
            </div>
            <div class="review-content">{{ r.content }}</div>
          </div>
        </div>
        <el-empty v-else description="暂无评价，来抢沙发" :image-size="60" />
      </el-card>

      <!-- 绑定关系（酒店↔打印店 双向展示/跳转/解绑） -->
      <el-card v-if="isHotel || isPrint" shadow="never" class="sec-card">
        <template #header>
          <div class="sec-head">
            <span>{{ isHotel ? '绑定打印店' : '服务的酒店' }}（{{ bindings.length }}）</span>
            <el-button link type="primary" @click="openBindDlg">添加绑定</el-button>
          </div>
        </template>
        <div v-if="bindings.length" class="bind-list">
          <div v-for="b in bindings" :key="b.id" class="bind-item">
            <span class="bind-name" @click="goPeer(b.id)">{{ b.name }} ↗</span>
            <span class="bind-meta">{{ b.cityName || '-' }}｜{{ b.typeName }}</span>
            <el-button link type="danger" size="small" @click="unbind(b.id)">解绑</el-button>
          </div>
        </div>
        <el-empty v-else :description="isHotel ? '尚未绑定打印店' : '尚未关联酒店'" :image-size="60" />
      </el-card>
    </template>

    <!-- 编辑基本信息 -->
    <el-dialog v-model="editVisible" title="编辑供应商" width="600px" destroy-on-close>
      <el-form :model="editForm" label-width="90px">
        <el-form-item label="名称" required>
          <el-input v-model="editForm.name" maxlength="100" />
        </el-form-item>
        <el-form-item label="类型" required>
          <el-select v-model="editForm.typeId" style="width: 100%">
            <el-option v-for="t in types" :key="t.id" :label="t.name" :value="t.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="城市" required>
          <el-select v-model="editForm.cityName" filterable allow-create default-first-option style="width: 100%" placeholder="选择或输入城市">
            <el-option-group v-for="g in cityGroups" :key="g.province" :label="g.province">
              <el-option v-for="c in g.cities" :key="c.id" :label="c.name" :value="c.name" />
            </el-option-group>
          </el-select>
        </el-form-item>
        <el-form-item label="地址"><el-input v-model="editForm.address" maxlength="200" /></el-form-item>
        <el-row :gutter="8">
          <el-col :span="12"><el-form-item label="联系人"><el-input v-model="editForm.contact" maxlength="50" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="电话"><el-input v-model="editForm.phone" maxlength="50" /></el-form-item></el-col>
        </el-row>
        <el-form-item label="微信"><el-input v-model="editForm.wechat" maxlength="50" /></el-form-item>
        <el-form-item label="描述"><el-input v-model="editForm.description" type="textarea" :rows="3" maxlength="1000" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="confirmEdit">保存</el-button>
      </template>
    </el-dialog>

    <!-- 行价编辑（动态行） -->
    <el-dialog v-model="priceDlgVisible" title="编辑行价" width="760px" destroy-on-close>
      <el-table :data="priceDraft" border size="small">
        <el-table-column label="服务项" min-width="150">
          <template #default="{ row }"><el-input v-model="row.itemName" maxlength="100" size="small" placeholder="如：A4 单面彩印" /></template>
        </el-table-column>
        <el-table-column label="价格" width="120">
          <template #default="{ row }"><el-input v-model="row.price" size="small" placeholder="0.00" /></template>
        </el-table-column>
        <el-table-column label="单位" width="100">
          <template #default="{ row }"><el-input v-model="row.unit" maxlength="20" size="small" placeholder="张/份/天" /></template>
        </el-table-column>
        <el-table-column label="备注" min-width="170">
          <template #default="{ row }"><el-input v-model="row.remark" maxlength="200" size="small" /></template>
        </el-table-column>
        <el-table-column label="操作" width="70" align="center">
          <template #default="{ $index }">
            <el-button link type="danger" size="small" @click="priceDraft.splice($index, 1)">删</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-button plain size="small" style="margin-top: 8px; width: 100%" @click="priceDraft.push({ itemName: '', price: 0, unit: '', remark: '' })">＋ 添加一行</el-button>
      <template #footer>
        <el-button @click="priceDlgVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="confirmPrices">保存行价</el-button>
      </template>
    </el-dialog>

    <!-- 添加绑定（按方向筛对端供应商） -->
    <el-dialog v-model="bindDlgVisible" :title="isHotel ? '绑定打印店' : '关联酒店'" width="480px" destroy-on-close>
      <el-select
        v-model="bindTargetId"
        filterable
        placeholder="搜索选择供应商"
        style="width: 100%"
      >
        <el-option
          v-for="o in bindCandidates"
          :key="o.id"
          :label="`${o.name}（${o.cityName || '-'}）`"
          :value="o.id"
        />
      </el-select>
      <template #footer>
        <el-button @click="bindDlgVisible = false">取消</el-button>
        <el-button type="primary" :disabled="!bindTargetId" @click="confirmBind">绑定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Loading } from '@element-plus/icons-vue'
import {
  getSupplierDetail, updateSupplier, deleteSupplier,
  listSupplierTypes, listSupplierCities, listSuppliers,
  saveSupplierPrices, addSupplierReview,
  bindSupplier, unbindSupplier,
  uploadSupplierImage, supplierImageUrl
} from '../../api/supplier'
import { compressImage } from '../../utils/imageCompress'

const route = useRoute()
const router = useRouter()

const loading = ref(false)
const s = ref(null)
const priceItems = ref([])
const reviews = ref([])
const bindings = ref([])
const types = ref([])
const cities = ref([])

const cityGroups = computed(() => {
  const groups = []
  const map = {}
  for (const c of cities.value) {
    if (!map[c.province]) {
      map[c.province] = { province: c.province, cities: [] }
      groups.push(map[c.province])
    }
    map[c.province].cities.push(c)
  }
  return groups
})

const isHotel = computed(() => s.value?.typeBizKey === 'HOTEL')
const isPrint = computed(() => s.value?.typeBizKey === 'PRINT')
const imageList = computed(() => s.value?.imageList || [])

function imageUrl(fileName) {
  return supplierImageUrl(fileName)
}

function typeTagColor(bizKey) {
  const map = { HOTEL: 'success', PRINT: 'warning', MEDIA: 'primary', IMAGING: 'danger', FLEET: 'info', TICKET: 'success' }
  return map[bizKey] || 'info'
}

async function fetchDetail() {
  loading.value = true
  try {
    const data = await getSupplierDetail(route.params.id)
    s.value = data?.supplier || null
    priceItems.value = data?.priceItems || []
    reviews.value = data?.reviews || []
    bindings.value = data?.bindings || []
  } finally {
    loading.value = false
  }
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

function goBack() {
  router.push('/supplier')
}

// 双向跳转：进入对端详情页（与解绑互不影响）
function goPeer(id) {
  router.push(`/supplier/${id}`)
}

// ==================== 图片 ====================
const uploading = ref(false)
const fileInput = ref(null)

function pickFiles() {
  fileInput.value && fileInput.value.click()
}

async function onFilesPicked(e) {
  const files = Array.from(e.target.files || [])
  e.target.value = ''
  if (!files.length) return
  uploading.value = true
  try {
    const uploaded = []
    for (const f of files) {
      const compressed = await compressImage(f)
      const res = await uploadSupplierImage(compressed)
      if (res?.fileName) uploaded.push(res.fileName)
    }
    const next = [...imageList.value, ...uploaded]
    await updateSupplier(s.value.id, { images: next })
    ElMessage.success(`已上传 ${uploaded.length} 张（压缩后）`)
    fetchDetail()
  } catch (err) {
    // 拦截器已提示
  } finally {
    uploading.value = false
  }
}

async function removeImage(i) {
  try {
    await ElMessageBox.confirm('确定删除这张图片吗？', '删除图片', { type: 'warning' })
  } catch (e) {
    return
  }
  const next = imageList.value.filter((_, idx) => idx !== i)
  await updateSupplier(s.value.id, { images: next })
  ElMessage.success('已删除')
  fetchDetail()
}

// ==================== 编辑/删除 ====================
const editVisible = ref(false)
const editForm = ref({})
const saving = ref(false)

function openEdit() {
  editForm.value = {
    name: s.value.name,
    typeId: s.value.typeId,
    cityName: s.value.cityName,
    address: s.value.address || '',
    contact: s.value.contact || '',
    phone: s.value.phone || '',
    wechat: s.value.wechat || '',
    description: s.value.description || ''
  }
  editVisible.value = true
}

async function confirmEdit() {
  if (!editForm.value.name?.trim()) return ElMessage.warning('名称不能为空')
  saving.value = true
  try {
    await updateSupplier(s.value.id, { ...editForm.value, name: editForm.value.name.trim() })
    ElMessage.success('已保存')
    editVisible.value = false
    fetchDicts()
    fetchDetail()
  } catch (e) {
    // 拦截器已提示
  } finally {
    saving.value = false
  }
}

async function handleDelete() {
  try {
    await ElMessageBox.confirm(`确定删除供应商「${s.value.name}」吗？行价、评价、绑定关系与图片将一并删除。`, '删除供应商', { type: 'warning' })
  } catch (e) {
    return
  }
  await deleteSupplier(s.value.id)
  ElMessage.success('已删除')
  goBack()
}

// ==================== 行价 ====================
const priceDlgVisible = ref(false)
const priceDraft = ref([])

function openPriceEdit() {
  priceDraft.value = priceItems.value.map((p) => ({
    itemName: p.itemName, price: p.price, unit: p.unit || '', remark: p.remark || ''
  }))
  priceDlgVisible.value = true
}

async function confirmPrices() {
  const items = priceDraft.value.filter((r) => r.itemName && r.itemName.trim())
  saving.value = true
  try {
    priceItems.value = await saveSupplierPrices(s.value.id, items) || []
    ElMessage.success('行价已保存')
    priceDlgVisible.value = false
  } catch (e) {
    // 拦截器已提示
  } finally {
    saving.value = false
  }
}

// ==================== 评价（即发即显） ====================
const reviewStars = ref(0)
const reviewContent = ref('')
const reviewing = ref(false)

async function submitReview() {
  if (!reviewStars.value) return ElMessage.warning('请先打星')
  if (!reviewContent.value.trim()) return ElMessage.warning('请填写评价内容')
  reviewing.value = true
  try {
    const r = await addSupplierReview(s.value.id, { stars: reviewStars.value, content: reviewContent.value.trim() })
    reviews.value = [r, ...reviews.value]
    reviewStars.value = 0
    reviewContent.value = ''
    ElMessage.success('评价已发布')
    fetchDetail()
  } catch (e) {
    // 拦截器已提示
  } finally {
    reviewing.value = false
  }
}

// ==================== 绑定 ====================
const bindDlgVisible = ref(false)
const bindTargetId = ref(null)
const bindCandidates = ref([])

async function loadBindCandidates() {
  // 按方向筛对端类型：酒店 → 打印店；打印店 → 酒店
  const targetBiz = isHotel.value ? 'PRINT' : 'HOTEL'
  const targetType = types.value.find((t) => t.bizKey === targetBiz)
  if (!targetType) return
  try {
    const data = await listSuppliers({ typeId: targetType.id, page: 1, size: 200 })
    bindCandidates.value = (data?.items || []).filter((o) => o.id !== s.value.id)
  } catch (e) {
    // 拦截器已提示
  }
}

function openBindDlg() {
  bindTargetId.value = null
  loadBindCandidates()
  bindDlgVisible.value = true
}

async function confirmBind() {
  if (!bindTargetId.value) return
  try {
    await bindSupplier(s.value.id, bindTargetId.value)
    ElMessage.success('已绑定')
    bindDlgVisible.value = false
    fetchDetail()
  } catch (e) {
    // 拦截器已提示（含重复绑定/类型不匹配报错）
  }
}

async function unbind(peerId) {
  try {
    await ElMessageBox.confirm('确定解除绑定吗？解绑后仍可从对端详情页或此处重新绑定。', '解绑', { type: 'warning' })
  } catch (e) {
    return
  }
  try {
    await unbindSupplier(s.value.id, peerId)
    ElMessage.success('已解绑')
    fetchDetail()
  } catch (e) {
    // 拦截器已提示
  }
}

onMounted(() => {
  fetchDicts()
  fetchDetail()
})

// 路由参数变化（绑定双向跳转 /supplier/1 → /supplier/2）时组件被复用，需重新拉取详情
watch(
  () => route.params.id,
  (nv, ov) => {
    if (nv && nv !== ov) {
      fetchDetail()
    }
  }
)
</script>

<style scoped>
.page-head { margin-bottom: 8px; }
.head-row { display: flex; justify-content: space-between; align-items: center; }
.sup-name { font-size: 18px; font-weight: 600; color: #303133; }
.head-meta { margin-top: 6px; font-size: 13px; color: #606266; }
.muted { color: #909399; }
.head-desc { margin-top: 8px; font-size: 13px; color: #606266; line-height: 1.7; white-space: pre-wrap; }
.head-rate { margin-top: 10px; display: flex; align-items: center; gap: 8px; }
.rate-text { font-size: 13px; color: #606266; }
.sec-card { margin-top: 14px; }
.sec-head { display: flex; justify-content: space-between; align-items: center; font-weight: 600; color: #303133; }
.gallery { display: flex; flex-wrap: wrap; gap: 10px; }
.g-item { position: relative; width: 150px; height: 110px; border-radius: 6px; overflow: hidden; border: 1px solid #ebeef5; }
.g-img { width: 100%; height: 100%; cursor: zoom-in; }
.g-del { position: absolute; top: 4px; right: 4px; width: 22px; height: 22px; }
.g-add {
  display: flex; flex-direction: column; align-items: center; justify-content: center;
  border: 1px dashed #c0c4cc; color: #909399; font-size: 24px; cursor: pointer; user-select: none;
}
.g-add:hover { border-color: #409eff; color: #409eff; }
.g-add-tip { font-size: 11px; margin-top: 2px; }
.loading-add { font-size: 18px; }
.review-form { padding: 10px 12px; background: #f8f9fb; border-radius: 6px; }
.review-submit { margin-top: 6px; display: flex; justify-content: flex-end; }
.review-list { margin-top: 10px; }
.review-item { padding: 10px 4px; border-bottom: 1px dashed #ebeef5; }
.review-head { display: flex; align-items: center; gap: 10px; }
.review-meta { font-size: 12px; color: #909399; }
.review-content { margin-top: 4px; font-size: 13px; color: #303133; line-height: 1.6; white-space: pre-wrap; }
.bind-list { display: flex; flex-direction: column; gap: 4px; }
.bind-item { display: flex; align-items: center; gap: 10px; padding: 8px 4px; border-bottom: 1px dashed #ebeef5; }
.bind-name { color: #409eff; cursor: pointer; font-weight: 600; }
.bind-name:hover { text-decoration: underline; }
.bind-meta { flex: 1; font-size: 12px; color: #909399; }
</style>
