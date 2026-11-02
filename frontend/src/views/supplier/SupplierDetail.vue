<template>
  <div v-loading="loading" class="sup-detail">
    <!-- 顶部操作区 -->
    <div class="top-bar">
      <el-button link @click="goBack">← 返回供应商列表</el-button>
      <template v-if="s">
        <span class="sup-name">{{ s.name }}</span>
        <el-tag :type="supplierTypeTagColor(s.typeBizKey)" size="small">{{ s.typeName }}</el-tag>
        <span class="top-meta">{{ s.cityName || '-' }}</span>
        <span class="top-meta muted">建档：{{ s.createTime || '-' }}</span>
      </template>
      <div style="flex: 1"></div>
      <el-button v-if="s" class="city-nav-btn" link type="primary" @click="cityDrawer = true">
        同城市供应商（{{ sameCityTotalText }}）
      </el-button>
      <template v-if="s">
        <el-button v-permission="'supplier:manage'" plain @click="openEdit">编辑</el-button>
        <el-button v-permission="'supplier:manage'" type="danger" plain @click="handleDelete">删除</el-button>
      </template>
    </div>

    <template v-if="s">
      <div class="three-col">
        <!-- 左栏：基本信息（紧凑两列） -->
        <div class="col-left">
          <el-card shadow="never" class="blk">
            <template #header><div class="blk-head">基本信息</div></template>
            <div class="info-grid">
              <div class="info-label">名称</div><div class="info-value">{{ s.name }}</div>
              <div class="info-label">类型</div><div class="info-value">{{ s.typeName }}</div>
              <div class="info-label">城市</div><div class="info-value">{{ s.cityName || '-' }}</div>
              <div class="info-label">地址</div><div class="info-value">{{ s.address || '-' }}</div>
              <div class="info-label">联系人</div><div class="info-value">{{ s.contact || '-' }}</div>
              <div class="info-label">电话</div><div class="info-value">{{ s.phone || '-' }}</div>
              <div class="info-label">微信</div><div class="info-value">{{ s.wechat || '-' }}</div>
              <div class="info-label">评分</div>
              <div class="info-value">
                <el-rate :model-value="s.avgStars || 0" disabled size="small" />
                <span class="rate-text">{{ s.avgStars ? `${s.avgStars} 分（${s.reviewCount} 条评价）` : '暂无评分' }}</span>
              </div>
              <div class="info-label">建档人</div><div class="info-value">{{ s.createBy || '-' }}</div>
              <div class="info-label">建档时间</div><div class="info-value">{{ s.createTime || '-' }}</div>
              <div class="info-label">描述</div>
              <div class="info-value desc">{{ s.description || '-' }}</div>
            </div>
          </el-card>
        </div>

        <!-- 中栏：图片管理 / 历史价格 / 评价（/ 绑定关系，酒店/打印店） -->
        <div class="col-mid">
          <!-- 图片管理 -->
          <el-card shadow="never" class="blk">
            <template #header>
              <div class="blk-head">
                <span>图片管理（{{ images.length }}）</span>
                <span>
                  <el-input v-model="uploadName" maxlength="50" size="small" placeholder="图片命名（≤50字，留空显示原文件名）" style="width: 220px; margin-right: 8px" @keyup.enter="pickFiles" />
                  <el-button v-permission="'supplier:manage'" type="primary" size="small" plain :loading="uploading" @click="pickFiles">上传图片</el-button>
                  <input ref="fileInput" type="file" accept="image/*" multiple hidden @change="onFilesPicked" />
                </span>
              </div>
            </template>
            <div v-if="images.length || uploading" class="gallery">
              <div v-for="(img, i) in images" :key="img.id" class="g-item">
                <el-image :src="imageUrl(img.fileName)" fit="cover" class="g-img" :preview-src-list="images.map(x => imageUrl(x.fileName))" :initial-index="i" />
                <el-tag v-if="img.isMain === 1" class="g-main" size="small" type="danger" effect="dark">主图</el-tag>
                <div v-permission="'supplier:manage'" class="g-hover">
                  <el-button v-if="img.isMain !== 1" link type="primary" size="small" @click.stop="setMain(img)">设为主图</el-button>
                  <el-button link type="primary" size="small" @click.stop="openRename(img)">改名</el-button>
                  <el-button link type="danger" size="small" @click.stop="removeImage(img)">删除</el-button>
                </div>
                <div class="g-name" :title="img.name || img.fileName">{{ img.name || img.fileName }}</div>
              </div>
              <div v-if="uploading" class="g-item g-add loading-add"><el-icon class="is-loading"><Loading /></el-icon></div>
            </div>
            <div v-else class="empty-line">暂无图片</div>
            <div class="upload-tip">限制：最多 20 张、单张 ≤8MB、累计 ≤100MB；上传自动压缩（长边≤1280），首图自动设为主图</div>
          </el-card>

          <!-- 历史价格 -->
          <el-card shadow="never" class="blk">
            <template #header>
              <div class="blk-head">
                <span>历史价格（{{ priceItems.length }}）</span>
                <el-button v-permission="'supplier:manage'" v-if="priceItems.length" link type="primary" @click="openPriceEdit">新增价格</el-button>
              </div>
            </template>
            <el-table v-if="priceItems.length" :data="priceItems" border stripe size="small">
              <el-table-column prop="itemName" label="品名" min-width="160" />
              <el-table-column prop="price" label="价格" width="120" align="right">
                <template #default="{ row }"><span class="price-bold">{{ row.price }}</span></template>
              </el-table-column>
              <el-table-column prop="unit" label="单位" width="90" align="center" />
              <el-table-column prop="remark" label="备注" min-width="180" show-overflow-tooltip>
                <template #default="{ row }">{{ row.remark || '-' }}</template>
              </el-table-column>
              <el-table-column label="操作" width="80" align="center">
                <template #default>
                  <el-button v-permission="'supplier:manage'" link type="primary" size="small" @click="openPriceEdit">编辑</el-button>
                </template>
              </el-table-column>
            </el-table>
            <div v-else class="empty-line">
              暂无价格数据
              <el-button v-permission="'supplier:manage'" link type="primary" size="small" @click="openPriceEdit">新增价格</el-button>
            </div>
          </el-card>

          <!-- 评价 -->
          <el-card shadow="never" class="blk">
            <template #header>
              <div class="blk-head">
                <span>评价</span>
                <span class="rate-summary">
                  <el-rate :model-value="s.avgStars || 0" disabled size="small" />
                  <span class="rate-text">{{ s.avgStars ? `${s.avgStars} 分 · ${reviews.length} 条评价` : '暂无评分' }}</span>
                </span>
              </div>
            </template>
            <div class="review-form">
              <div class="star-pick">
                <span
                  v-for="n in 5"
                  :key="n"
                  class="star"
                  :class="{ on: n <= reviewStars }"
                  @click="reviewStars = n"
                >{{ n <= reviewStars ? '★' : '☆' }}</span>
                <span class="rate-text" style="margin-left: 6px">{{ reviewStars ? reviewStars + ' 星' : '点击选星' }}</span>
              </div>
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
                <el-button v-permission="'supplier:manage'" type="primary" size="small" :loading="reviewing" :disabled="!reviewStars || !reviewContent.trim()" @click="submitReview">提交评价</el-button>
              </div>
            </div>
            <div v-if="reviews.length" class="review-list">
              <div v-for="r in reviews" :key="r.id" class="review-item">
                <div class="review-head">
                  <span class="mini-stars">{{ '★'.repeat(r.stars) }}{{ '☆'.repeat(5 - r.stars) }}</span>
                  <span class="review-meta">{{ r.username }} ｜ {{ r.createTime }}</span>
                </div>
                <div class="review-content">{{ r.content }}</div>
              </div>
            </div>
            <div v-else class="empty-line">暂无评价</div>
          </el-card>

          <!-- 绑定关系（酒店↔打印店，保留既有功能） -->
          <el-card v-if="isHotel || isPrint" shadow="never" class="blk">
            <template #header>
              <div class="blk-head">
                <span>{{ isHotel ? '绑定打印店' : '服务的酒店' }}（{{ bindings.length }}）</span>
                <span>
                  <el-button v-permission="'supplier:manage'" link type="warning" @click="openCrossCityDlg">跨城清单</el-button>
                  <el-button v-permission="'supplier:binding'" link type="primary" @click="openBindDlg">添加绑定</el-button>
                </span>
              </div>
            </template>
            <div v-if="bindings.length" class="bind-list">
              <div v-for="b in bindings" :key="b.id" class="bind-item">
                <span class="bind-name" @click="goPeer(b.id)">{{ b.name }} ↗</span>
                <el-tag v-if="b.peerCityId && s.cityId && b.peerCityId !== s.cityId" size="small" type="warning">跨城</el-tag>
                <span class="bind-meta">{{ b.cityName || '-' }}｜{{ b.typeName }}</span>
                <el-button v-permission="'supplier:binding'" link type="danger" size="small" @click="unbind(b.id)">解绑</el-button>
              </div>
            </div>
            <div v-else class="empty-line">{{ isHotel ? '尚未绑定打印店' : '尚未关联酒店' }}</div>
          </el-card>
        </div>

        <!-- 右栏：同城市供应商导航（<1200px 折叠为顶部按钮+抽屉） -->
        <div class="col-right">
          <el-card shadow="never" class="blk">
            <template #header>
              <div class="blk-head">
                <span>同城市供应商</span>
                <span class="rate-text">共 {{ sameCityTotalText }}</span>
              </div>
            </template>
            <template v-if="s.cityId">
              <el-input v-model="cityKeyword" size="small" placeholder="按名称过滤" clearable style="margin-bottom: 8px" />
              <div v-if="sameCityTotal > sameCityList.length" class="rate-text" style="margin-bottom: 6px">共 {{ sameCityTotal }} 家，仅展示前 {{ sameCityList.length }} 家</div>
              <div v-if="filteredSameCity.length" class="city-list">
                <div
                  v-for="o in filteredSameCity"
                  :key="o.id"
                  class="city-item"
                  :class="{ current: o.id === Number(route.params.id), bound: isBoundPeer(o.id) }"
                  @click="goPeer(o.id)"
                >
                  <span class="city-name" :title="o.name">{{ o.name }}</span>
                  <el-tag v-if="isBoundPeer(o.id)" size="small" type="primary" effect="plain">已绑定</el-tag>
                  <span class="city-type">{{ o.typeName }}</span>
                </div>
              </div>
              <div v-else class="empty-line">同城市无匹配供应商</div>
            </template>
            <div v-else class="empty-line">该供应商暂无城市信息，无法展示同城市供应商</div>
          </el-card>
        </div>
      </div>
    </template>

    <!-- 小屏抽屉：同城市供应商 -->
    <el-drawer v-model="cityDrawer" title="同城市供应商" size="320px" direction="rtl">
      <template v-if="s && s.cityId">
        <el-input v-model="cityKeyword" size="small" placeholder="按名称过滤" clearable style="margin-bottom: 8px" />
        <div v-if="sameCityTotal > sameCityList.length" class="rate-text" style="margin-bottom: 6px">共 {{ sameCityTotal }} 家，仅展示前 {{ sameCityList.length }} 家</div>
        <div v-if="filteredSameCity.length" class="city-list">
          <div
            v-for="o in filteredSameCity"
            :key="o.id"
            class="city-item"
            :class="{ current: o.id === Number(route.params.id), bound: isBoundPeer(o.id) }"
            @click="goPeer(o.id); cityDrawer = false"
          >
            <span class="city-name" :title="o.name">{{ o.name }}</span>
            <el-tag v-if="isBoundPeer(o.id)" size="small" type="primary" effect="plain">已绑定</el-tag>
            <span class="city-type">{{ o.typeName }}</span>
          </div>
        </div>
        <div v-else class="empty-line">同城市无匹配供应商</div>
      </template>
      <div v-else class="empty-line">该供应商暂无城市信息，无法展示同城市供应商</div>
    </el-drawer>

    <!-- 图片改名（v2.1）：仅修改展示名，允许为空与重名 -->
    <el-dialog v-model="renameDlgVisible" title="图片改名" width="440px" destroy-on-close>
      <el-form label-width="80px">
        <el-form-item label="名称">
          <el-input v-model="renameName" maxlength="50" placeholder="≤50 字符，留空显示原文件名" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="renameDlgVisible = false">取消</el-button>
        <el-button type="primary" :loading="renaming" @click="confirmRename">保存</el-button>
      </template>
    </el-dialog>

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
        <el-form-item label="省份">
          <el-select v-model="editForm.province" style="width: 100%" placeholder="选择省份（选填）" @change="onEditProvinceChange">
            <el-option v-for="p in provinceNames" :key="p" :label="p" :value="p" />
          </el-select>
        </el-form-item>
        <el-form-item label="城市" required>
          <el-select
            v-model="editForm.cityId"
            style="width: 100%"
            placeholder="选择城市（城市字典固定只读）"
            filterable
          >
            <el-option v-for="c in editCities" :key="c.id" :label="c.name" :value="c.id" />
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

    <!-- 价格编辑（动态行，全量替换） -->
    <el-dialog v-model="priceDlgVisible" title="编辑价格" width="760px" destroy-on-close>
      <el-table :data="priceDraft" border size="small">
        <el-table-column label="品名" min-width="150">
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
      <div class="price-warn">保存后将整体替换现有价格数据</div>
      <template #footer>
        <el-button @click="priceDlgVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="confirmPrices">保存</el-button>
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

    <!-- 跨城绑定清单（v2.1）：存量跨城关系批量解绑 -->
    <el-dialog v-model="crossCityDlgVisible" title="跨城绑定清单" width="880px" destroy-on-close>
      <div style="display: flex; gap: 10px; margin-bottom: 10px">
        <el-input v-model="crossKeyword" placeholder="按酒店/打印店名称搜索" clearable style="width: 240px" @keyup.enter="onCrossSearch" @clear="onCrossSearch" />
        <el-button type="primary" plain :loading="crossLoading" @click="onCrossSearch">查询</el-button>
        <div style="flex: 1"></div>
        <el-button v-permission="'supplier:manage'" type="danger" plain :disabled="!crossSelected.length" @click="confirmBatchUnbind">批量解绑（{{ crossSelected.length }}）</el-button>
      </div>
      <el-table :data="crossRows" border stripe size="small" max-height="420" @selection-change="onCrossSelect">
        <el-table-column type="selection" width="46" />
        <el-table-column prop="hotelName" label="酒店" min-width="140" show-overflow-tooltip />
        <el-table-column prop="hotelCity" label="酒店城市" width="110" />
        <el-table-column prop="printShopName" label="打印店" min-width="140" show-overflow-tooltip />
        <el-table-column prop="printShopCity" label="打印店城市" width="110" />
        <el-table-column label="城市差异" width="100" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.hotelCity !== row.printShopCity" size="small" type="danger">跨城</el-tag>
            <el-tag v-else size="small" type="success">同城</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createBy" label="创建人" width="100" />
        <el-table-column prop="createTime" label="创建时间" width="170" />
      </el-table>
      <el-pagination
        style="margin-top: 10px; justify-content: flex-end"
        layout="total, prev, pager, next"
        :total="crossTotal"
        :page-size="crossSize"
        :current-page="crossPage"
        @current-change="onCrossPage"
      />
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
  uploadSupplierImage, listSupplierImages, setSupplierMainImage, renameSupplierImage, deleteSupplierImage, supplierImageUrl,
  crossCityBindings, unbindSupplierBatch
} from '../../api/supplier'
import { compressImage } from '../../utils/imageCompress'
import { supplierTypeTagColor, provinceList, citiesOfProvince } from '../../utils/supplier'

const route = useRoute()
const router = useRouter()

const loading = ref(false)
const s = ref(null)
const priceItems = ref([])
const reviews = ref([])
const bindings = ref([])
const types = ref([])
const cities = ref([])

const provinceNames = computed(() => provinceList(cities.value))
const editCities = computed(() => citiesOfProvince(cities.value, editForm.value.province))
const isHotel = computed(() => s.value?.typeBizKey === 'HOTEL')
const isPrint = computed(() => s.value?.typeBizKey === 'PRINT')
const images = ref([])

function imageUrl(fileName) {
  return supplierImageUrl(fileName)
}

async function fetchDetail() {
  loading.value = true
  try {
    const data = await getSupplierDetail(route.params.id)
    s.value = data?.supplier || null
    priceItems.value = data?.priceItems || []
    reviews.value = data?.reviews || []
    bindings.value = data?.bindings || []
    if (s.value) {
      images.value = await listSupplierImages(s.value.id) || []
      loadSameCity()
    } else {
      images.value = []
      sameCityList.value = []
      sameCityTotal.value = 0
    }
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

// 双向跳转：进入对端/同城供应商详情页（路由复用组件，watch 重新拉取）
function goPeer(id) {
  router.push(`/supplier/${id}`)
}

// ==================== 同城市供应商导航（复用 list 接口，不新增后端） ====================
const cityKeyword = ref('')
const cityDrawer = ref(false)
const sameCityList = ref([])
const sameCityTotal = ref(0)

// 酒店/打印店：同城列表内已绑定供应商置顶（绑定标记）
function isBoundPeer(id) {
  return (isHotel.value || isPrint.value) && bindings.value.some((b) => b.id === id)
}

const sortedSameCity = computed(() => {
  const list = sameCityList.value.filter((o) => o.id !== Number(route.params.id))
  return [...list].sort((a, b) => {
    const ab = isBoundPeer(a.id) ? 0 : 1
    const bb = isBoundPeer(b.id) ? 0 : 1
    if (ab !== bb) return ab - bb
    return String(a.name).localeCompare(String(b.name), 'zh-CN')
  })
})

const filteredSameCity = computed(() => {
  const kw = cityKeyword.value.trim().toLowerCase()
  if (!kw) return sortedSameCity.value
  return sortedSameCity.value.filter((o) => String(o.name).toLowerCase().includes(kw))
})

const sameCityTotalText = computed(() => String(sameCityTotal.value))

async function loadSameCity() {
  if (!s.value?.cityId) {
    sameCityList.value = []
    sameCityTotal.value = 0
    return
  }
  try {
    const data = await listSuppliers({ cityId: s.value.cityId, page: 1, size: 200 })
    sameCityList.value = data?.items || []
    sameCityTotal.value = data?.total || sameCityList.value.length
  } catch (e) {
    // 拦截器已提示
  }
}

// ==================== 图片（v2.0，视觉重构：160×120 + hover 操作层） ====================
const uploading = ref(false)
const uploadName = ref('')
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
    for (let idx = 0; idx < files.length; idx++) {
      const compressed = await compressImage(files[idx])
      // 命名仅作用于本次选择的第一个文件，其余留空显示原文件名
      const res = await uploadSupplierImage(s.value.id, compressed, idx === 0 ? uploadName.value : '')
      if (res?.id) uploaded.push(res.id)
    }
    uploadName.value = ''
    ElMessage.success(`已上传 ${uploaded.length} 张（压缩后）`)
    images.value = await listSupplierImages(s.value.id) || []
    fetchDetail()
  } catch (err) {
    // 拦截器已提示（含数量/大小/命名超长报错）
  } finally {
    uploading.value = false
  }
}

async function setMain(img) {
  try {
    await setSupplierMainImage(s.value.id, img.id)
    ElMessage.success('已设为主图')
    images.value = await listSupplierImages(s.value.id) || []
  } catch (e) {
    // 拦截器已提示
  }
}

// 图片改名（v2.1）
const renameDlgVisible = ref(false)
const renameTarget = ref(null)
const renameName = ref('')
const renaming = ref(false)

function openRename(img) {
  renameTarget.value = img
  renameName.value = img.name || ''
  renameDlgVisible.value = true
}

async function confirmRename() {
  if (!renameTarget.value) return
  renaming.value = true
  try {
    await renameSupplierImage(s.value.id, renameTarget.value.id, renameName.value.trim())
    ElMessage.success('已改名')
    renameDlgVisible.value = false
    images.value = await listSupplierImages(s.value.id) || []
  } catch (e) {
    // 拦截器已提示（IMAGE_NOT_FOUND / NAME_TOO_LONG）
  } finally {
    renaming.value = false
  }
}

async function removeImage(img) {
  try {
    await ElMessageBox.confirm('确定删除这张图片吗？物理文件将同步删除。', '删除图片', { type: 'warning' })
  } catch (e) {
    return
  }
  try {
    await deleteSupplierImage(s.value.id, img.id)
    ElMessage.success('已删除')
    images.value = await listSupplierImages(s.value.id) || []
  } catch (e) {
    // 拦截器已提示
  }
}

// ==================== 编辑/删除 ====================
const editVisible = ref(false)
const editForm = ref({})
const saving = ref(false)

function openEdit() {
  editForm.value = {
    name: s.value.name,
    typeId: s.value.typeId,
    province: s.value.province,
    cityId: s.value.cityId,
    address: s.value.address || '',
    contact: s.value.contact || '',
    phone: s.value.phone || '',
    wechat: s.value.wechat || '',
    description: s.value.description || ''
  }
  editVisible.value = true
}

function onEditProvinceChange() {
  editForm.value.cityId = null
}

async function confirmEdit() {
  if (!editForm.value.name?.trim()) return ElMessage.warning('名称不能为空')
  if (!editForm.value.cityId) return ElMessage.warning('请选择城市')
  saving.value = true
  try {
    const { province, ...payload } = editForm.value
    await updateSupplier(s.value.id, { ...payload, name: editForm.value.name.trim() })
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
    await ElMessageBox.confirm(`确定删除供应商「${s.value.name}」吗？价格、评价、绑定关系与图片将一并删除。`, '删除供应商', { type: 'warning' })
  } catch (e) {
    return
  }
  await deleteSupplier(s.value.id)
  ElMessage.success('已删除')
  goBack()
}

// ==================== 历史价格（全量替换语义保留） ====================
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
    ElMessage.success('价格已保存')
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
    // v2.1「三、酒店-打印店同城绑定」：候选仅列出同城市对端（跨城绑定后端拒绝）
    const data = await listSuppliers({ typeId: targetType.id, cityId: s.value?.cityId, page: 1, size: 200 })
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

// ==================== 跨城绑定清单（v2.1） ====================
const crossCityDlgVisible = ref(false)
const crossKeyword = ref('')
const crossRows = ref([])
const crossTotal = ref(0)
const crossPage = ref(1)
const crossSize = ref(12)
const crossLoading = ref(false)
const crossSelected = ref([])

async function loadCrossCity(page = 1) {
  crossLoading.value = true
  try {
    const data = await crossCityBindings({ keyword: crossKeyword.value || undefined, page, size: crossSize.value })
    crossRows.value = data?.items || []
    crossTotal.value = data?.total || 0
    crossPage.value = page
  } catch (e) {
    // 拦截器已提示
  } finally {
    crossLoading.value = false
  }
}

function openCrossCityDlg() {
  crossSelected.value = []
  loadCrossCity(1)
  crossCityDlgVisible.value = true
}

function onCrossSearch() {
  crossSelected.value = []
  loadCrossCity(1)
}

function onCrossPage(p) {
  crossSelected.value = []
  loadCrossCity(p)
}

function onCrossSelect(rows) {
  crossSelected.value = rows
}

async function confirmBatchUnbind() {
  const ids = crossSelected.value.map((r) => r.relId)
  if (!ids.length) return
  try {
    await ElMessageBox.confirm(`确定批量解绑选中的 ${ids.length} 条跨城绑定关系吗？解绑后如需恢复需重新同城绑定。`, '批量解绑', { type: 'warning' })
  } catch (e) {
    return
  }
  try {
    const res = await unbindSupplierBatch(ids)
    ElMessage.success(`已解绑 ${res?.unboundCount ?? ids.length} 条`)
    crossSelected.value = []
    loadCrossCity(crossPage.value)
    fetchDetail()
  } catch (e) {
    // 拦截器已提示（PARAM_INVALID / BINDING_NOT_FOUND）
  }
}

onMounted(() => {
  fetchDicts()
  fetchDetail()
})

// 路由参数变化（绑定双向跳转 / 同城导航跳转 /supplier/1 → /supplier/2）时组件被复用，需重新拉取
watch(
  () => route.params.id,
  (nv, ov) => {
    if (nv && nv !== ov) {
      cityKeyword.value = ''
      cityDrawer.value = false
      fetchDetail()
    }
  }
)
</script>

<style scoped>
.sup-detail { background: #f0f2f5; min-height: calc(100vh - 90px); padding: 12px; box-sizing: border-box; }
.top-bar { display: flex; align-items: center; gap: 10px; margin-bottom: 12px; flex-wrap: wrap; }
.sup-name { font-size: 20px; font-weight: 700; color: #303133; }
.top-meta { font-size: 13px; color: #606266; }
.muted { color: #909399; }
.city-nav-btn { display: none; }
.three-col { display: flex; gap: 16px; align-items: flex-start; }
.col-left { width: 340px; flex-shrink: 0; }
.col-mid { flex: 1; min-width: 0; display: flex; flex-direction: column; gap: 16px; }
.col-right { width: 280px; flex-shrink: 0; }
.blk { border-radius: 8px; border-color: #ebeef5; }
.blk-head { display: flex; justify-content: space-between; align-items: center; font-weight: 600; color: #303133; }

/* 左栏信息紧凑两列 */
.info-grid { display: grid; grid-template-columns: 64px 1fr; gap: 8px 10px; font-size: 13px; }
.info-label { color: #909399; }
.info-value { color: #303133; word-break: break-all; }
.info-value.desc { white-space: pre-wrap; line-height: 1.6; }
.rate-text { font-size: 12px; color: #909399; }

/* 图片管理：160×120 + 下方名称 + hover 操作层 */
.gallery { display: flex; flex-wrap: wrap; gap: 12px; }
.g-item { position: relative; width: 160px; border-radius: 8px; overflow: hidden; border: 1px solid #ebeef5; background: #fff; }
.g-img { width: 160px; height: 120px; display: block; cursor: zoom-in; }
.g-main { position: absolute; top: 4px; left: 4px; }
.g-hover {
  position: absolute; left: 0; right: 0; top: 0; height: 120px;
  display: none; align-items: center; justify-content: center; gap: 4px;
  background: rgba(0, 0, 0, 0.45);
}
.g-item:hover .g-hover { display: flex; }
.g-name { padding: 4px 6px; font-size: 12px; color: #606266; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.upload-tip { margin-top: 8px; font-size: 12px; color: #a8abb2; line-height: 1.6; }
.g-add {
  display: flex; flex-direction: column; align-items: center; justify-content: center; height: 120px;
  border: 1px dashed #c0c4cc; color: #909399; font-size: 24px; cursor: pointer; user-select: none;
}
.g-add:hover { border-color: #409eff; color: #409eff; }
.loading-add { font-size: 18px; }

/* 历史价格 */
.price-bold { font-weight: 700; }
.price-warn { margin-top: 8px; font-size: 12px; color: #e6a23c; }

/* 评价 */
.rate-summary { display: flex; align-items: center; gap: 6px; font-weight: 400; }
.review-form { padding: 10px 12px; background: #f8f9fb; border-radius: 6px; }
.star-pick { display: flex; align-items: center; }
.star { font-size: 22px; color: #c0c4cc; cursor: pointer; line-height: 1; user-select: none; }
.star.on { color: #f7ba2a; }
.review-submit { margin-top: 6px; display: flex; justify-content: flex-end; }
.review-list { margin-top: 10px; }
.review-item { padding: 8px 4px; border-bottom: 1px dashed #ebeef5; }
.review-head { display: flex; align-items: center; gap: 10px; }
.mini-stars { color: #f7ba2a; font-size: 13px; letter-spacing: 1px; }
.review-meta { font-size: 12px; color: #909399; }
.review-content { margin-top: 4px; font-size: 13px; color: #303133; line-height: 1.6; white-space: pre-wrap; }

/* 绑定 */
.bind-list { display: flex; flex-direction: column; gap: 4px; }
.bind-item { display: flex; align-items: center; gap: 10px; padding: 8px 4px; border-bottom: 1px dashed #ebeef5; }
.bind-name { color: #409eff; cursor: pointer; font-weight: 600; }
.bind-name:hover { text-decoration: underline; }
.bind-meta { flex: 1; font-size: 12px; color: #909399; }

/* 右栏同城导航 */
.city-list { display: flex; flex-direction: column; gap: 2px; max-height: 560px; overflow-y: auto; }
.city-item {
  display: flex; align-items: center; gap: 6px; padding: 7px 8px;
  border-radius: 6px; cursor: pointer; border-left: 3px solid transparent;
}
.city-item:hover { background: #f5f7fa; }
.city-item.current { background: #ecf5ff; border-left-color: #409eff; }
.city-item.bound:not(.current) { background: #f0f7ff; border-left-color: #a0cfff; }
.city-name { flex: 1; font-size: 13px; color: #303133; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.city-item.current .city-name { color: #409eff; font-weight: 600; }
.city-type { font-size: 11px; color: #a8abb2; flex-shrink: 0; }
.empty-line { font-size: 13px; color: #909399; padding: 10px 4px; }

/* 小屏 <1200px：右栏折叠，顶部出现展开按钮 */
@media (max-width: 1199px) {
  .col-right { display: none; }
  .city-nav-btn { display: inline-flex; }
}
</style>
