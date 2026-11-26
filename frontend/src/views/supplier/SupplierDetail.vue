<template>
  <div v-loading="loading" class="sup-detail">
    <!-- 顶部：返回 + 全局操作（与 Tab 无关，始终可见） -->
    <div class="top-bar">
      <el-button link class="back-btn" @click="goBack">
        <el-icon><ArrowLeft /></el-icon><span>返回供应商列表</span>
      </el-button>
      <div class="top-spacer"></div>
      <template v-if="s">
        <el-button class="city-nav-btn" link type="primary" @click="cityDrawer = true">
          同城供应商（{{ sameCityTotal }}）
        </el-button>
        <el-button v-permission="'supplier:manage'" plain @click="openEdit">编辑</el-button>
        <el-button v-permission="'supplier:manage'" type="danger" plain @click="handleDelete">删除</el-button>
      </template>
    </div>

    <div v-if="s" class="layout">
      <!-- 主面板：档案头 + Tab -->
      <div class="main-panel">
        <div class="archive-head">
          <div class="archive-id">
            <span class="sup-name">{{ s.name }}</span>
            <el-tag :type="supplierTypeTagColor(s.typeBizKey)" size="small" effect="plain">{{ s.typeName }}</el-tag>
          </div>
          <span class="archive-chip">供应商档案</span>
        </div>

        <div class="tab-bar">
          <button
            v-for="t in tabs"
            :key="t.key"
            class="tab-btn"
            :class="{ active: activeTab === t.key }"
            type="button"
            @click="activeTab = t.key"
          >{{ t.label }}</button>
        </div>

        <!-- ============ 基本资料 ============ -->
        <div v-show="activeTab === 'basic'" class="tab-body">
          <section class="sec">
            <h3 class="sec-title">基础资料</h3>
            <div class="field">
              <div class="f-label">供应商名称</div>
              <div class="f-value lg">{{ s.name }}</div>
            </div>
            <div class="field">
              <div class="f-label">所属城市 / 地址</div>
              <div class="f-value">{{ cityAddressText }}</div>
            </div>
            <div class="field">
              <div class="f-label">供应商类型</div>
              <div class="f-value">{{ s.typeName || '-' }}</div>
            </div>
          </section>

          <section class="sec">
            <h3 class="sec-title">联系信息</h3>
            <div class="contact-grid">
              <div class="field">
                <div class="f-label">联系人</div>
                <div class="f-value">{{ s.contact || '-' }}</div>
              </div>
              <div class="field">
                <div class="f-label">联系电话</div>
                <div class="f-value with-copy">
                  <span class="copy-text">{{ s.phone || '-' }}</span>
                  <el-tooltip v-if="s.phone" content="复制电话" placement="top">
                    <button class="copy-btn" type="button" @click="copyText(s.phone, '电话')">
                      <el-icon><CopyDocument /></el-icon>
                    </button>
                  </el-tooltip>
                </div>
              </div>
            </div>
            <div class="field">
              <div class="f-label">微信</div>
              <div class="f-value with-copy">
                <span class="copy-text">{{ s.wechat || '-' }}</span>
                <el-tooltip v-if="s.wechat" content="复制微信号" placement="top">
                  <button class="copy-btn" type="button" @click="copyText(s.wechat, '微信号')">
                    <el-icon><CopyDocument /></el-icon>
                  </button>
                </el-tooltip>
              </div>
            </div>
          </section>

          <section class="sec">
            <h3 class="sec-title">业务介绍</h3>
            <p class="desc-text">{{ s.description || '暂无业务介绍' }}</p>
          </section>

          <section class="sec">
            <button class="archive-toggle" type="button" @click="archiveOpen = !archiveOpen">
              <span>档案信息</span>
              <span class="toggle-hint">{{ archiveOpen ? '收起' : '展开' }}</span>
              <el-icon class="toggle-icon" :class="{ open: archiveOpen }"><ArrowRight /></el-icon>
            </button>
            <div v-show="archiveOpen" class="archive-grid">
              <div class="field">
                <div class="f-label">建档人</div>
                <div class="f-value sm">{{ s.createBy || '-' }}</div>
              </div>
              <div class="field">
                <div class="f-label">建档时间</div>
                <div class="f-value sm">{{ s.createTime || '-' }}</div>
              </div>
              <div class="field">
                <div class="f-label">最近更新</div>
                <div class="f-value sm">{{ s.updateTime || '-' }}</div>
              </div>
              <div class="field">
                <div class="f-label">综合评分</div>
                <div class="f-value sm">
                  <template v-if="stats.total">
                    <span class="score-inline">{{ stats.avg }}</span>
                    <span class="muted">分 · {{ stats.total }} 条评价</span>
                  </template>
                  <span v-else class="muted">暂无评价</span>
                </div>
              </div>
            </div>
          </section>
        </div>

        <!-- ============ 图片管理 ============ -->
        <div v-show="activeTab === 'images'" class="tab-body">
          <div class="body-head">
            <span class="body-title">图片管理<span class="body-count">{{ images.length }}</span></span>
            <span class="body-actions">
              <el-button v-permission="'supplier:image'" type="primary" plain size="small" :loading="uploading" @click="pickFiles">
                <el-icon><UploadFilled /></el-icon><span style="margin-left: 4px">上传图片</span>
              </el-button>
              <input ref="fileInput" type="file" accept="image/*" multiple hidden @change="onFilesPicked" />
            </span>
          </div>
          <div v-if="images.length || uploading" class="img-grid">
            <div v-for="(img, i) in images" :key="img.id" class="img-card">
              <el-image
                :src="imageUrl(img.fileName)"
                fit="cover"
                class="img-thumb"
                :preview-src-list="images.map((x) => imageUrl(x.fileName))"
                :initial-index="i"
                preview-teleported
              />
              <span v-if="img.isMain === 1" class="img-main-badge">主图</span>
              <div class="img-ops">
                <el-tooltip content="预览" placement="top">
                  <button class="op-btn" type="button" @click.stop="previewImage(i)">
                    <el-icon><View /></el-icon>
                  </button>
                </el-tooltip>
                <el-tooltip v-if="img.isMain !== 1 && canManage" content="设为主图" placement="top">
                  <button class="op-btn" type="button" @click.stop="setMain(img)">
                    <el-icon><Star /></el-icon>
                  </button>
                </el-tooltip>
                <el-tooltip v-if="canImage" content="重命名" placement="top">
                  <button class="op-btn" type="button" @click.stop="openRename(img)">
                    <el-icon><EditPen /></el-icon>
                  </button>
                </el-tooltip>
                <el-tooltip v-if="canManage" content="删除" placement="top">
                  <button class="op-btn danger" type="button" @click.stop="removeImage(img)">
                    <el-icon><Delete /></el-icon>
                  </button>
                </el-tooltip>
              </div>
              <div class="img-name" :title="img.name || img.fileName">{{ img.name || img.fileName }}</div>
            </div>
            <div v-if="uploading" class="img-card img-add loading-add">
              <el-icon class="is-loading"><Loading /></el-icon>
            </div>
          </div>
          <div v-else class="empty-block">
            <div class="empty-title">暂无图片</div>
            <div class="empty-sub">上传后可设为主图、重命名或删除</div>
          </div>
          <!-- P3-3：限额文案读后端真源（/api/meta/limits），不再硬编码字面量 -->
          <div class="tip-line">最多 {{ uploadLimits.supplierMaxImages }} 张、单张 ≤{{ toMb(uploadLimits.supplierMaxSingleBytes) }}MB、累计 ≤{{ toMb(uploadLimits.supplierMaxTotalBytes) }}MB；上传自动压缩（长边≤1280），首图自动设为主图；命名在单张图片的「重命名」中修改。</div>
        </div>

        <!-- ============ 历史价格 ============ -->
        <div v-show="activeTab === 'prices'" class="tab-body">
          <div class="body-head">
            <span class="body-title">历史价格<span class="body-count">{{ priceItems.length }}</span></span>
            <span class="body-actions">
              <el-button v-permission="'supplier:price'" type="primary" plain size="small" @click="openPriceEdit">
                <el-icon><Plus /></el-icon><span style="margin-left: 4px">新增价格</span>
              </el-button>
            </span>
          </div>
          <div v-if="priceItems.length" class="price-summary">
            共 {{ priceItems.length }} 条报价，{{ validPriceCount }} 条在有效期内
          </div>
          <el-table v-if="priceItems.length" :data="priceItems" class="price-table" size="default">
            <el-table-column prop="itemName" label="服务项目" min-width="180" show-overflow-tooltip />
            <el-table-column label="历史报价" width="180" align="right">
              <template #default="{ row }">
                <span class="price-amount">¥{{ fmtMoney(row.price) }}</span>
                <span v-if="row.unit" class="price-unit">/{{ row.unit }}</span>
              </template>
            </el-table-column>
            <el-table-column label="报价日期" width="130" align="center">
              <template #default="{ row }">{{ row.quoteDate || dateOf(row.createTime) }}</template>
            </el-table-column>
            <el-table-column label="有效期至" width="130" align="center">
              <template #default="{ row }">
                <span :class="{ 'muted': !row.validUntil }">{{ row.validUntil || '长期有效' }}</span>
              </template>
            </el-table-column>
            <el-table-column label="状态" width="100" align="center">
              <template #default="{ row }">
                <span class="state-pill ui-badge" :class="priceState(row).cls">{{ priceState(row).text }}</span>
              </template>
            </el-table-column>
            <el-table-column label="备注" min-width="150" show-overflow-tooltip>
              <template #default="{ row }">{{ row.remark || '-' }}</template>
            </el-table-column>
            <el-table-column label="操作" width="80" align="center">
              <template #default>
                <el-button v-permission="'supplier:price'" link type="primary" size="small" @click="openPriceEdit">编辑</el-button>
              </template>
            </el-table-column>
          </el-table>
          <div v-else class="empty-block">
            <div class="empty-title">暂无价格数据</div>
            <div class="empty-sub">建议按服务项目分别记录，并填写计费单位与有效期，便于跨供应商比价</div>
            <el-button v-permission="'supplier:price'" type="primary" plain size="small" style="margin-top: 12px" @click="openPriceEdit">新增价格</el-button>
          </div>
        </div>

        <!-- ============ 服务评价 ============ -->
        <div v-show="activeTab === 'reviews'" class="tab-body">
          <div class="body-head">
            <span class="body-title">服务评价<span class="body-count">{{ stats.total }}</span></span>
            <span class="body-actions">
              <el-button v-if="canReview" type="primary" plain size="small" @click="openReviewDlg">
                <el-icon><Plus /></el-icon><span style="margin-left: 4px">新增评价</span>
              </el-button>
            </span>
          </div>

          <div class="review-summary">
            <div class="rs-score">
              <div class="rs-num">{{ stats.total ? stats.avg : '—' }}</div>
              <el-rate :model-value="Number(stats.avg) || 0" disabled size="small" />
              <div class="rs-count">{{ stats.total }} 条评价</div>
            </div>
            <div class="rs-bars">
              <div v-for="n in [5, 4, 3, 2, 1]" :key="n" class="rs-row">
                <span class="rs-star">{{ n }} 星</span>
                <span class="rs-bar"><i :style="{ width: barWidth(n) }"></i></span>
                <span class="rs-num-sm">{{ stats.dist[n - 1] || 0 }}</span>
              </div>
            </div>
          </div>

          <div v-if="reviews.length" class="review-list">
            <div v-for="r in reviews" :key="r.id" class="review-item">
              <div class="review-avatar">{{ (r.username || '?').slice(0, 1) }}</div>
              <div class="review-body">
                <div class="review-head">
                  <span class="review-user">{{ r.username }}</span>
                  <span class="mini-stars">{{ '★'.repeat(r.stars) }}<i class="mini-stars-off">{{ '★'.repeat(5 - r.stars) }}</i></span>
                  <span class="review-time">{{ r.createTime }}</span>
                </div>
                <div class="review-content">{{ r.content || '（无文字评价）' }}</div>
              </div>
            </div>
          </div>
          <div v-else class="empty-block">
            <div class="empty-title">暂无评价</div>
            <div class="empty-sub">首次评价后，这里会显示综合评分与历史评价</div>
            <el-button v-if="canReview" class="empty-cta" type="primary" plain @click="openReviewDlg">
              <el-icon><Plus /></el-icon><span style="margin-left: 4px">写第一条评价</span>
            </el-button>
          </div>
        </div>

        <!-- ============ 绑定关系（酒店↔打印店） ============ -->
        <div v-if="isHotel || isPrint" v-show="activeTab === 'bindings'" class="tab-body">
          <div class="body-head">
            <span class="body-title">{{ isHotel ? '绑定打印店' : '服务的酒店' }}<span class="body-count">{{ bindings.length }}</span></span>
            <span class="body-actions">
              <el-button v-permission="'supplier:manage'" link type="warning" @click="openCrossCityDlg">跨城清单</el-button>
              <el-button v-permission="'supplier:manage'" link type="primary" @click="openUnboundDlg">绑定概览</el-button>
              <el-button v-permission="'supplier:binding'" type="primary" plain size="small" @click="openBindDlg">
                <el-icon><Plus /></el-icon><span style="margin-left: 4px">添加绑定</span>
              </el-button>
            </span>
          </div>
          <div v-if="bindings.length" class="bind-list">
            <div v-for="b in bindings" :key="b.id" class="bind-item">
              <div class="bind-main">
                <span class="bind-name" @click="goPeer(b.id)">{{ b.name }}</span>
                <span class="bind-meta">{{ b.cityName || '-' }} · {{ b.typeName }}</span>
              </div>
              <el-tag v-if="b.peerCityId && s.cityId && b.peerCityId !== s.cityId" size="small" type="warning">跨城</el-tag>
              <el-button v-permission="'supplier:binding'" link type="danger" size="small" @click="unbind(b.id)">解绑</el-button>
            </div>
          </div>
          <div v-else class="empty-block">
            <div class="empty-title">{{ isHotel ? '尚未绑定打印店' : '尚未关联酒店' }}</div>
            <div class="empty-sub">绑定后可在两端详情页互相跳转</div>
          </div>
        </div>
      </div>

      <!-- 右栏：同城供应商 -->
      <aside class="side-panel">
        <div class="side-head">
          <span class="side-title">同城供应商</span>
          <span class="side-count">{{ s.cityId ? filteredSameCity.length + ' 家' : '—' }}</span>
        </div>
        <template v-if="s.cityId">
          <el-input v-model="cityKeyword" class="side-search" placeholder="搜索名称" clearable>
            <template #prefix><el-icon><Search /></el-icon></template>
          </el-input>
          <el-select v-model="cityTypeFilter" class="side-select" placeholder="全部类型" clearable>
            <el-option label="全部类型" value="" />
            <el-option v-for="t in sameCityTypes" :key="t.bizKey" :label="t.name" :value="t.bizKey" />
          </el-select>
          <div v-if="filteredSameCity.length" class="sc-list">
            <div
              v-for="o in filteredSameCity"
              :key="o.id"
              class="sc-item"
              @click="goPeer(o.id)"
            >
              <div class="sc-main">
                <div class="sc-name">{{ o.name }}</div>
                <div class="sc-type">
                  {{ o.typeName || '未分类' }}
                  <el-tag v-if="isBoundPeer(o.id)" size="small" type="primary" effect="plain" class="sc-bound">已绑定</el-tag>
                </div>
              </div>
              <el-icon class="sc-arrow"><ArrowRight /></el-icon>
            </div>
          </div>
          <div v-else class="empty-line">同城市无匹配供应商</div>
          <div class="side-foot">
            已排除当前档案
            <template v-if="sameCityTotal > sameCityList.length">；共 {{ sameCityTotal }} 家，仅加载前 {{ sameCityList.length }} 家</template>
          </div>
        </template>
        <div v-else class="empty-line">该供应商暂无城市信息，无法展示同城市供应商</div>
      </aside>
    </div>

    <!-- 小屏抽屉：同城供应商 -->
    <el-drawer v-model="cityDrawer" title="同城供应商" size="320px" direction="rtl">
      <template v-if="s && s.cityId">
        <el-input v-model="cityKeyword" size="small" placeholder="搜索名称" clearable style="margin-bottom: 8px" />
        <el-select v-model="cityTypeFilter" size="small" placeholder="全部类型" clearable style="width: 100%; margin-bottom: 8px">
          <el-option label="全部类型" value="" />
          <el-option v-for="t in sameCityTypes" :key="t.bizKey" :label="t.name" :value="t.bizKey" />
        </el-select>
        <div v-if="filteredSameCity.length" class="sc-list">
          <div v-for="o in filteredSameCity" :key="o.id" class="sc-item" @click="goPeer(o.id); cityDrawer = false">
            <div class="sc-main">
              <div class="sc-name">{{ o.name }}</div>
              <div class="sc-type">{{ o.typeName || '未分类' }}</div>
            </div>
            <el-icon class="sc-arrow"><ArrowRight /></el-icon>
          </div>
        </div>
        <div v-else class="empty-line">同城市无匹配供应商</div>
      </template>
      <div v-else class="empty-line">该供应商暂无城市信息，无法展示同城市供应商</div>
    </el-drawer>

    <!-- 图片预览（自定义按钮触发，复用 el-image-viewer） -->
    <el-image-viewer
      v-if="viewerVisible"
      :url-list="images.map((x) => imageUrl(x.fileName))"
      :initial-index="viewerIndex"
      teleported
      @close="viewerVisible = false"
    />

    <!-- 图片重命名：命名归属于单张图片 -->
    <el-dialog v-model="renameDlgVisible" title="重命名图片" width="440px" destroy-on-close>
      <div class="rename-preview">
        <el-image v-if="renameTarget" :src="imageUrl(renameTarget.fileName)" fit="cover" class="rename-thumb" />
        <span class="muted">原文件名：{{ renameTarget?.fileName }}</span>
      </div>
      <el-form label-width="70px" style="margin-top: 12px">
        <el-form-item label="名称">
          <el-input v-model="renameName" maxlength="50" placeholder="≤50 字符，留空显示原文件名" @keyup.enter="confirmRename" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="renameDlgVisible = false">取消</el-button>
        <el-button type="primary" :loading="renaming" @click="confirmRename">保存</el-button>
      </template>
    </el-dialog>

    <!-- 评价表单：仅主动点击「新增评价」时打开 -->
    <el-dialog v-model="reviewDlgVisible" title="新增评价" width="480px" destroy-on-close>
      <div class="dlg-stars">
        <span
          v-for="n in 5"
          :key="n"
          class="star"
          :class="{ on: n <= reviewStars }"
          @click="reviewStars = n"
        >{{ n <= reviewStars ? '★' : '☆' }}</span>
        <span class="rate-text">{{ reviewStars ? reviewStars + ' 星' : '点击选星' }}</span>
      </div>
      <el-input
        v-model="reviewContent"
        type="textarea"
        :rows="4"
        maxlength="500"
        show-word-limit
        placeholder="写下本次合作的评价（服务响应、价格、交付质量等）"
        style="margin-top: 10px"
      />
      <template #footer>
        <el-button @click="reviewDlgVisible = false">取消</el-button>
        <el-button type="primary" :loading="reviewing" :disabled="!reviewStars || !reviewContent.trim()" @click="submitReview">提交评价</el-button>
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
          <el-select v-model="editForm.cityId" style="width: 100%" placeholder="选择城市（城市字典固定只读）" filterable>
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
    <el-dialog v-model="priceDlgVisible" title="维护历史价格" width="940px" destroy-on-close>
      <el-table :data="priceDraft" size="small" class="draft-table">
        <el-table-column label="服务项目" min-width="170">
          <template #default="{ row }"><el-input v-model="row.itemName" maxlength="100" size="small" placeholder="如：会议团体机票" /></template>
        </el-table-column>
        <el-table-column label="报价" width="110">
          <template #default="{ row }"><el-input v-model="row.price" size="small" placeholder="0.00" /></template>
        </el-table-column>
        <el-table-column label="单位" width="100">
          <template #default="{ row }"><el-input v-model="row.unit" maxlength="20" size="small" placeholder="人/间夜/张" /></template>
        </el-table-column>
        <el-table-column label="报价日期" width="150">
          <template #default="{ row }">
            <el-date-picker v-model="row.quoteDate" type="date" size="small" value-format="YYYY-MM-DD" placeholder="选择日期" style="width: 100%" />
          </template>
        </el-table-column>
        <el-table-column label="有效期至" width="150">
          <template #default="{ row }">
            <el-date-picker v-model="row.validUntil" type="date" size="small" value-format="YYYY-MM-DD" placeholder="长期有效" style="width: 100%" />
          </template>
        </el-table-column>
        <el-table-column label="备注" min-width="140">
          <template #default="{ row }"><el-input v-model="row.remark" maxlength="200" size="small" /></template>
        </el-table-column>
        <el-table-column label="操作" width="60" align="center">
          <template #default="{ $index }">
            <el-button link type="danger" size="small" @click="priceDraft.splice($index, 1)">删</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-button plain size="small" style="margin-top: 8px; width: 100%" @click="addPriceRow">＋ 添加一行</el-button>
      <div class="price-warn">保存后将整体替换现有价格数据；单位与有效期用于跨供应商比价，建议逐项填写。</div>
      <template #footer>
        <el-button @click="priceDlgVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="confirmPrices">保存</el-button>
      </template>
    </el-dialog>

    <!-- 添加绑定 -->
    <el-dialog v-model="bindDlgVisible" :title="isHotel ? '绑定打印店' : '关联酒店'" width="480px" destroy-on-close>
      <el-select v-model="bindTargetId" filterable placeholder="搜索选择供应商" style="width: 100%">
        <el-option v-for="o in bindCandidates" :key="o.id" :label="`${o.name}（${o.cityName || '-'}）`" :value="o.id" />
      </el-select>
      <template #footer>
        <el-button @click="bindDlgVisible = false">取消</el-button>
        <el-button type="primary" :disabled="!bindTargetId" @click="confirmBind">绑定</el-button>
      </template>
    </el-dialog>

    <!-- 跨城绑定清单 -->
    <!-- 绑定概览（需求四 2026-11-26）：按城市分组的未绑定对端清单，反向核对缺口 -->
    <el-dialog v-model="unboundDlgVisible" title="绑定概览 · 未绑定对端的酒店/打印店" width="720px" destroy-on-close>
      <div v-loading="unboundLoading">
        <el-select v-model="unboundCityId" placeholder="全部城市" clearable filterable size="small" style="width: 180px" @change="loadUnbound">
          <el-option v-for="c in cities" :key="c.id" :label="c.name" :value="c.id" />
        </el-select>
        <div class="unbound-groups">
          <template v-for="g in unboundGroups" :key="g.city">
            <div class="unbound-city">{{ g.city || '未填城市' }}</div>
            <div class="unbound-side">
              <span class="unbound-label hot">酒店（{{ g.hotels.length }}）</span>
              <span v-if="!g.hotels.length" class="unbound-none">无缺口</span>
              <el-tag
                v-for="o in g.hotels" :key="'h' + o.id" class="unbound-tag" effect="plain"
                @click="goPeer(o.id); unboundDlgVisible = false"
              >{{ o.name }}</el-tag>
            </div>
            <div class="unbound-side">
              <span class="unbound-label prt">打印店（{{ g.prints.length }}）</span>
              <span v-if="!g.prints.length" class="unbound-none">无缺口</span>
              <el-tag
                v-for="o in g.prints" :key="'p' + o.id" class="unbound-tag" effect="plain"
                @click="goPeer(o.id); unboundDlgVisible = false"
              >{{ o.name }}</el-tag>
            </div>
          </template>
          <el-empty v-if="!unboundLoading && !unboundGroups.length" description="酒店/打印店均已绑定对端，无缺口" :image-size="60" />
        </div>
      </div>
    </el-dialog>

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
import {
  ArrowLeft, ArrowRight, CopyDocument, Delete, EditPen, Loading, Plus, Search, Star, UploadFilled, View
} from '@element-plus/icons-vue'
import {
  getSupplierDetail, updateSupplier, deleteSupplier,
  listSupplierTypes, listSupplierCities, listSuppliers,
  saveSupplierPrices, addSupplierReview,
  bindSupplier, unbindSupplier,
  uploadSupplierImage, listSupplierImages, setSupplierMainImage, renameSupplierImage, deleteSupplierImage, supplierImageUrl,
  crossCityBindings, unbindSupplierBatch, getUnboundBindings
} from '../../api/supplier'
import { compressImage } from '../../utils/imageCompress'
import { ensureUploadLimits, toMb, uploadLimits } from '../../utils/limits'
import { supplierTypeTagColor, provinceList, citiesOfProvince, SUPPLIER_LIST_FETCH_SIZE } from '../../utils/supplier'
import { useAuthStore } from '../../store/auth'

const route = useRoute()
const router = useRouter()

// 权限：图片上传/重命名 supplier:image；设主图/删除 supplier:manage（与后端一致）
const authStore = useAuthStore()
const canManage = computed(() => authStore.hasPermission('supplier:manage'))
// 服务评价对全员开放（supplier:review 绑定全部非超管角色），与供应商维护权限解耦
const canReview = computed(() => authStore.hasPermission('supplier:review'))
const canImage = computed(() => authStore.hasPermission('supplier:image'))

const loading = ref(false)
const s = ref(null)
const priceItems = ref([])
const reviews = ref([])
const bindings = ref([])
const types = ref([])
const cities = ref([])
const images = ref([])

const provinceNames = computed(() => provinceList(cities.value))
const editCities = computed(() => citiesOfProvince(cities.value, editForm.value.province))
const isHotel = computed(() => s.value?.typeBizKey === 'HOTEL')
const isPrint = computed(() => s.value?.typeBizKey === 'PRINT')

// ==================== Tab ====================
const activeTab = ref('basic')
const archiveOpen = ref(false)

const tabs = computed(() => {
  const list = [
    { key: 'basic', label: '基本资料' },
    { key: 'images', label: '图片管理' },
    { key: 'prices', label: '历史价格' },
    { key: 'reviews', label: '服务评价' }
  ]
  if (isHotel.value || isPrint.value) {
    list.push({ key: 'bindings', label: isHotel.value ? '绑定打印店' : '服务的酒店' })
  }
  return list
})

const cityAddressText = computed(() => {
  const v = s.value
  if (!v) return '-'
  const city = [v.province, v.cityName].filter((x) => !!x).join(' · ')
  return [city, v.address].filter((x) => !!x).join('　') || '未填写'
})

function imageUrl(fileName) {
  return supplierImageUrl(fileName)
}

// ==================== 复制（联系人电话/微信，业务高频） ====================
async function copyText(text, label) {
  if (!text) return
  try {
    if (navigator.clipboard && window.isSecureContext) {
      await navigator.clipboard.writeText(String(text))
    } else {
      const ta = document.createElement('textarea')
      ta.value = String(text)
      ta.style.position = 'fixed'
      ta.style.opacity = '0'
      document.body.appendChild(ta)
      ta.select()
      document.execCommand('copy')
      document.body.removeChild(ta)
    }
    ElMessage.success(`${label}已复制`)
  } catch (e) {
    ElMessage.warning('复制失败，请手动选择文本')
  }
}

// ==================== 数据加载 ====================
async function fetchDetail() {
  loading.value = true
  try {
    const data = await getSupplierDetail(route.params.id)
    s.value = data?.supplier || null
    priceItems.value = data?.priceItems || []
    reviews.value = data?.reviews || []
    bindings.value = data?.bindings || []
    if (s.value) {
      images.value = (await listSupplierImages(s.value.id)) || []
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

// 返回：优先回退浏览器历史（保留列表的筛选与页码），无历史再回列表首页
function goBack() {
  if (window.history.length > 1) router.back()
  else router.push('/supplier')
}

// 同城/绑定跳转：路由复用组件，仅换参数，筛选条件与 Tab 保持不变
function goPeer(id) {
  if (Number(id) === Number(route.params.id)) return
  router.push(`/supplier/${id}`)
}

// ==================== 同城市供应商（右侧常驻 + 小屏抽屉共用状态） ====================
const cityKeyword = ref('')
const cityDrawer = ref(false)
const cityTypeFilter = ref('')
const sameCityList = ref([])
const sameCityTotal = ref(0)

const sameCityTypes = computed(() => {
  const seen = new Map()
  for (const o of sameCityList.value) {
    if (o.typeBizKey && !seen.has(o.typeBizKey)) seen.set(o.typeBizKey, o.typeName || o.typeBizKey)
  }
  return [...seen].map(([bizKey, name]) => ({ bizKey, name }))
})

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
  let list = sortedSameCity.value
  if (cityTypeFilter.value) list = list.filter((o) => o.typeBizKey === cityTypeFilter.value)
  const kw = cityKeyword.value.trim().toLowerCase()
  if (!kw) return list
  return list.filter((o) => String(o.name).toLowerCase().includes(kw))
})

async function loadSameCity() {
  if (!s.value?.cityId) {
    sameCityList.value = []
    sameCityTotal.value = 0
    return
  }
  try {
    const data = await listSuppliers({ cityId: s.value.cityId, page: 1, size: SUPPLIER_LIST_FETCH_SIZE })
    sameCityList.value = data?.items || []
    sameCityTotal.value = data?.total || sameCityList.value.length
  } catch (e) {
    // 拦截器已提示
  }
}

// ==================== 图片（4:3 缩略图 + hover 操作层） ====================
const uploading = ref(false)
const fileInput = ref(null)
const viewerVisible = ref(false)
const viewerIndex = ref(0)

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
    for (const file of files) {
      const compressed = await compressImage(file)
      const res = await uploadSupplierImage(s.value.id, compressed, '')
      if (res?.id) uploaded.push(res.id)
    }
    ElMessage.success(`已上传 ${uploaded.length} 张（压缩后），可在图片上重命名`)
    images.value = (await listSupplierImages(s.value.id)) || []
    fetchDetail()
  } catch (err) {
    // 拦截器已提示（含数量/大小报错）
  } finally {
    uploading.value = false
  }
}

function previewImage(index) {
  viewerIndex.value = index
  viewerVisible.value = true
}

async function setMain(img) {
  try {
    await setSupplierMainImage(s.value.id, img.id)
    ElMessage.success('已设为主图')
    images.value = (await listSupplierImages(s.value.id)) || []
  } catch (e) {
    // 拦截器已提示
  }
}

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
  if (!renameTarget.value || renaming.value) return
  renaming.value = true
  try {
    await renameSupplierImage(s.value.id, renameTarget.value.id, renameName.value.trim())
    ElMessage.success('已重命名')
    renameDlgVisible.value = false
    images.value = (await listSupplierImages(s.value.id)) || []
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
    images.value = (await listSupplierImages(s.value.id)) || []
  } catch (e) {
    // 拦截器已提示
  }
}

// ==================== 编辑/删除供应商 ====================
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

// ==================== 绑定概览（需求四 2026-11-26） ====================
const unboundDlgVisible = ref(false)
const unboundLoading = ref(false)
const unboundCityId = ref(null)
const unboundItems = ref([])

function openUnboundDlg() {
  unboundDlgVisible.value = true
  loadUnbound()
}

async function loadUnbound() {
  unboundLoading.value = true
  try {
    unboundItems.value = (await getUnboundBindings(unboundCityId.value || undefined)) || []
  } catch (e) {
    // 拦截器已提示
  } finally {
    unboundLoading.value = false
  }
}

// 按城市分组；城市内再分酒店/打印店两侧
const unboundGroups = computed(() => {
  const map = new Map()
  for (const o of unboundItems.value) {
    const city = o.cityName || ''
    if (!map.has(city)) map.set(city, { city, hotels: [], prints: [] })
    const g = map.get(city)
    if (o.typeBizKey === 'HOTEL') g.hotels.push(o)
    else if (o.typeBizKey === 'PRINT') g.prints.push(o)
  }
  return [...map.values()]
})

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

// ==================== 历史价格（全量替换语义） ====================
const priceDlgVisible = ref(false)
const priceDraft = ref([])

function todayStr() {
  const d = new Date()
  const p = (n) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())}`
}

function dateOf(dt) {
  return dt ? String(dt).slice(0, 10) : '-'
}

function fmtMoney(v) {
  const n = Number(v || 0)
  return n.toLocaleString('zh-CN', { minimumFractionDigits: 0, maximumFractionDigits: 2 })
}

function priceState(p) {
  if (!p.validUntil) return { text: '长期有效', cls: 'ok' }
  return p.validUntil < todayStr() ? { text: '已过期', cls: 'expired' } : { text: '有效', cls: 'ok' }
}

const validPriceCount = computed(() => priceItems.value.filter((p) => priceState(p).cls === 'ok').length)

function openPriceEdit() {
  priceDraft.value = priceItems.value.map((p) => ({
    itemName: p.itemName,
    price: p.price,
    unit: p.unit || '',
    quoteDate: p.quoteDate || dateOf(p.createTime),
    validUntil: p.validUntil || '',
    remark: p.remark || ''
  }))
  if (!priceDraft.value.length) addPriceRow()
  priceDlgVisible.value = true
}

function addPriceRow() {
  priceDraft.value.push({ itemName: '', price: '', unit: '', quoteDate: todayStr(), validUntil: '', remark: '' })
}

async function confirmPrices() {
  const items = priceDraft.value
    .filter((r) => r.itemName && r.itemName.trim())
    .map((r) => ({
      itemName: r.itemName.trim(),
      price: r.price === '' || r.price === null || r.price === undefined ? 0 : r.price,
      unit: r.unit || '',
      quoteDate: r.quoteDate || '',
      validUntil: r.validUntil || '',
      remark: r.remark || ''
    }))
  saving.value = true
  try {
    priceItems.value = (await saveSupplierPrices(s.value.id, items)) || []
    ElMessage.success('价格已保存')
    priceDlgVisible.value = false
  } catch (e) {
    // 拦截器已提示（含日期格式/有效期早于报价日期报错）
  } finally {
    saving.value = false
  }
}

// ==================== 评价（先看统计与列表，再主动新增） ====================
const stats = computed(() => {
  const total = reviews.value.length
  const dist = [0, 0, 0, 0, 0]
  let sum = 0
  for (const r of reviews.value) {
    const st = Number(r.stars) || 0
    sum += st
    if (st >= 1 && st <= 5) dist[st - 1] += 1
  }
  const avg = total ? (sum / total).toFixed(1) : 0
  return { total, dist, avg: Number(s.value?.avgStars) || avg }
})

function barWidth(n) {
  const total = stats.value.total
  if (!total) return '0%'
  return `${Math.round(((stats.value.dist[n - 1] || 0) / total) * 100)}%`
}

const reviewDlgVisible = ref(false)
const reviewStars = ref(0)
const reviewContent = ref('')
const reviewing = ref(false)

function openReviewDlg() {
  reviewStars.value = 0
  reviewContent.value = ''
  reviewDlgVisible.value = true
}

async function submitReview() {
  if (!reviewStars.value) return ElMessage.warning('请先打星')
  if (!reviewContent.value.trim()) return ElMessage.warning('请填写评价内容')
  reviewing.value = true
  try {
    await addSupplierReview(s.value.id, { stars: reviewStars.value, content: reviewContent.value.trim() })
    ElMessage.success('评价已发布')
    reviewDlgVisible.value = false
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
  const targetBiz = isHotel.value ? 'PRINT' : 'HOTEL'
  const targetType = types.value.find((t) => t.bizKey === targetBiz)
  if (!targetType) return
  try {
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

// ==================== 跨城绑定清单 ====================
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

// 供应商类型变化后（酒店/打印店 ↔ 其他），原 Tab 可能已不存在，回落到基本资料
watch(tabs, (list) => {
  if (!list.some((t) => t.key === activeTab.value)) activeTab.value = 'basic'
})

// 同城切换 / 绑定互跳：组件复用，只重拉数据，Tab 与同城筛选条件保持不变
watch(
  () => route.params.id,
  (nv, ov) => {
    if (nv && nv !== ov) {
      cityDrawer.value = false
      viewerVisible.value = false
      window.scrollTo({ top: 0 })
      fetchDetail()
    }
  }
)
</script>

<style scoped>
/* 绑定概览（需求四 2026-11-26） */
.unbound-groups { margin-top: 10px; max-height: 55vh; overflow-y: auto; }
.unbound-city { font-weight: 600; color: var(--ui-text-main); margin: 12px 0 4px; }
.unbound-side { display: flex; align-items: center; flex-wrap: wrap; gap: 6px; margin: 4px 0 4px 12px; }
.unbound-label { font-size: 12px; color: var(--ui-text-sub); min-width: 90px; }
.unbound-label.hot { color: var(--ui-success); }
.unbound-label.prt { color: var(--ui-warning); }
.unbound-none { font-size: 12px; color: var(--ui-text-muted); }
.unbound-tag { cursor: pointer; }
.unbound-tag:hover { border-color: var(--ui-primary); color: var(--ui-primary); }

.sup-detail {
  background: #f5f6f8;
  min-height: calc(100vh - 90px);
  padding: 14px 18px 24px;
  box-sizing: border-box;
}

/* 顶部操作条 */
.top-bar { display: flex; align-items: center; gap: 10px; margin-bottom: 12px; }
.top-spacer { flex: 1; }
.back-btn { color: #5b6068; font-size: 13px; }
.back-btn:hover { color: #1f2329; }
.city-nav-btn { display: none; }

.layout { display: flex; gap: 16px; align-items: flex-start; }

/* 主面板 */
.main-panel {
  flex: 1; min-width: 0;
  background: #fff; border-radius: 12px;
  padding: 20px 28px 28px;
  box-shadow: 0 1px 3px rgba(15, 20, 30, 0.05);
}
.archive-head { display: flex; align-items: flex-start; justify-content: space-between; gap: 12px; padding-bottom: 14px; }
.archive-id { display: flex; align-items: center; gap: 10px; flex-wrap: wrap; min-width: 0; }
.sup-name { font-size: 20px; font-weight: 700; color: #1f2329; line-height: 1.4; word-break: break-word; }
.archive-chip {
  flex-shrink: 0; font-size: 12px; color: #5b6068; background: #f2f3f5;
  padding: 4px 10px; border-radius: 999px; line-height: 1.4;
}

/* Tab */
.tab-bar { display: flex; gap: 6px; border-bottom: 1px solid #eef0f2; padding-bottom: 12px; flex-wrap: wrap; }
.tab-btn {
  border: 0; background: transparent; cursor: pointer;
  font-size: 14px; color: #5b6068; line-height: 1.4;
  padding: 7px 16px; border-radius: 999px;
  transition: background-color 0.15s, color 0.15s;
}
.tab-btn:hover { background: #f2f3f5; color: #1f2329; }
.tab-btn.active { background: #1f2329; color: #fff; font-weight: 600; }

.tab-body { padding-top: 4px; }

/* 分组区块 */
.sec { padding: 22px 0; border-bottom: 1px solid #f2f3f5; }
.sec:last-child { border-bottom: 0; padding-bottom: 4px; }
.sec-title { margin: 0 0 16px; font-size: 16px; font-weight: 700; color: #1f2329; }
.field { margin-bottom: 18px; }
.field:last-child { margin-bottom: 0; }
.f-label { font-size: 12px; color: var(--ui-text-muted); margin-bottom: 5px; line-height: 1.4; }
.f-value { font-size: 15px; color: #1f2329; line-height: 1.6; word-break: break-word; }
.f-value.lg { font-size: 19px; font-weight: 600; }
.f-value.sm { font-size: 14px; }
.desc-text { margin: 0; font-size: 14px; color: var(--ui-text-main); line-height: 1.8; white-space: pre-wrap; word-break: break-word; }

/* 联系信息：两列 + 复制 */
.contact-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 0 24px; }
.with-copy { display: flex; align-items: center; gap: 8px; }
.copy-text { font-variant-numeric: tabular-nums; }
.copy-btn {
  border: 0; background: transparent; padding: 2px; cursor: pointer;
  color: var(--ui-text-muted); display: inline-flex; align-items: center;
  border-radius: 4px; transition: color 0.15s, background-color 0.15s;
}
.copy-btn:hover { color: #1f2329; background: #f2f3f5; }

/* 档案信息折叠 */
.archive-toggle {
  display: flex; align-items: center; gap: 8px;
  border: 0; background: transparent; padding: 0; cursor: pointer;
  font-size: 16px; font-weight: 700; color: #1f2329;
}
.toggle-hint { font-size: 12px; font-weight: 400; color: var(--ui-text-muted); }
.toggle-icon { font-size: 13px; color: var(--ui-text-muted); transition: transform 0.15s; }
.toggle-icon.open { transform: rotate(90deg); }
.archive-grid {
  display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 16px 20px;
  margin-top: 16px; padding: 16px; background: #fafbfc; border-radius: 10px;
}
.score-inline { font-weight: 600; margin-right: 4px; }

/* 内容区标题行 */
.body-head { display: flex; align-items: center; justify-content: space-between; gap: 12px; padding: 20px 0 14px; }
.body-title { font-size: 16px; font-weight: 700; color: #1f2329; display: inline-flex; align-items: center; gap: 8px; }
.body-count {
  font-size: 12px; font-weight: 500; color: #5b6068;
  background: #f2f3f5; border-radius: 999px; padding: 1px 8px;
}
.body-actions { display: inline-flex; align-items: center; gap: 8px; }

/* 图片网格：统一 4:3 */
.img-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(180px, 1fr)); gap: 14px; }
.img-card {
  position: relative; border: 1px solid #eef0f2; border-radius: 10px;
  overflow: hidden; background: #fafbfc;
}
.img-thumb { width: 100%; aspect-ratio: 4 / 3; display: block; background: #f0f2f5; cursor: zoom-in; }
.img-main-badge {
  position: absolute; top: 8px; left: 8px;
  background: rgba(31, 35, 41, 0.78); color: #fff;
  font-size: 11px; line-height: 1.6; padding: 1px 8px; border-radius: 999px;
}
.img-ops {
  position: absolute; left: 0; right: 0; top: 0;
  aspect-ratio: 4 / 3; display: flex; align-items: center; justify-content: center; gap: 10px;
  background: rgba(17, 20, 24, 0.5); opacity: 0; transition: opacity 0.15s;
}
.img-card:hover .img-ops { opacity: 1; }
.op-btn {
  width: 32px; height: 32px; border-radius: 50%; border: 0;
  background: rgba(255, 255, 255, 0.94); color: var(--ui-text-main);
  display: inline-flex; align-items: center; justify-content: center;
  cursor: pointer; transition: color 0.15s, transform 0.15s;
}
.op-btn:hover { color: #1f2329; transform: scale(1.06); }
.op-btn.danger:hover { color: var(--ui-danger); }
.img-name { padding: 8px 10px; font-size: 12px; color: #5b6068; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.img-add { display: flex; align-items: center; justify-content: center; aspect-ratio: 4 / 3; color: var(--ui-text-muted); font-size: 20px; }
.loading-add { border-style: dashed; }
.tip-line { margin-top: 14px; font-size: 12px; color: #a8abb2; line-height: 1.7; }

/* 历史价格 */
.price-summary { font-size: 12px; color: var(--ui-text-muted); padding-bottom: 8px; }
.price-table :deep(.el-table__cell) { padding: 11px 0; }
.price-table :deep(.el-table__inner-wrapper::before) { display: none; }
.price-amount { font-weight: 700; color: #1f2329; font-variant-numeric: tabular-nums; }
.price-unit { font-size: 12px; color: var(--ui-text-muted); margin-left: 2px; }
.state-pill { padding: 1px 9px; white-space: nowrap; }
.state-pill.ok { background: #e8f7ee; color: #18a058; }
.state-pill.expired { background: #f2f3f5; color: var(--ui-text-muted); }
.price-warn { margin-top: 10px; font-size: 12px; color: var(--ui-warning); line-height: 1.6; }

/* 评价统计 */
.review-summary {
  display: flex; gap: 36px; align-items: center;
  padding: 18px 20px; background: #fafbfc; border-radius: 10px; margin-bottom: 8px;
}
.rs-score { text-align: center; min-width: 96px; }
.rs-num { font-size: 34px; font-weight: 700; color: #1f2329; line-height: 1.2; font-variant-numeric: tabular-nums; }
.rs-count { font-size: 12px; color: var(--ui-text-muted); margin-top: 4px; }
.rs-bars { flex: 1; min-width: 0; display: flex; flex-direction: column; gap: 6px; }
.rs-row { display: flex; align-items: center; gap: 10px; font-size: 12px; color: var(--ui-text-muted); }
.rs-star { width: 32px; flex-shrink: 0; }
.rs-bar { flex: 1; height: 6px; background: #eef0f2; border-radius: 999px; overflow: hidden; }
.rs-bar i { display: block; height: 100%; background: #f7ba2a; border-radius: 999px; transition: width 0.2s; }
.rs-num-sm { width: 24px; text-align: right; }

/* 评价列表 */
.review-list { margin-top: 6px; }
.review-item { display: flex; gap: 12px; padding: 16px 2px; border-bottom: 1px solid #f2f3f5; }
.review-item:last-child { border-bottom: 0; }
.review-avatar {
  width: 34px; height: 34px; border-radius: 50%; flex-shrink: 0;
  background: #eef1f5; color: #5b6068; font-size: 14px; font-weight: 600;
  display: flex; align-items: center; justify-content: center;
}
.review-body { flex: 1; min-width: 0; }
.review-head { display: flex; align-items: center; gap: 10px; flex-wrap: wrap; }
.review-user { font-size: 14px; font-weight: 600; color: #1f2329; }
.mini-stars { color: #f7ba2a; font-size: 12px; letter-spacing: 1px; }
.mini-stars-off { color: #e4e7ed; font-style: normal; }
.review-time { font-size: 12px; color: #a8abb2; }
.review-content { margin-top: 6px; font-size: 13px; color: var(--ui-text-main); line-height: 1.7; white-space: pre-wrap; word-break: break-word; }

/* 空状态 */
.empty-block { padding: 40px 0; text-align: center; }
.empty-title { font-size: 14px; color: #5b6068; }
.empty-sub { font-size: 12px; color: #a8abb2; margin-top: 6px; line-height: 1.6; }
.empty-cta { margin-top: 14px; }
.empty-line { font-size: 13px; color: var(--ui-text-muted); padding: 12px 2px; }

/* 右栏：同城供应商 */
.side-panel {
  width: 320px; flex-shrink: 0;
  background: #fff; border-radius: 12px; padding: 18px 18px 14px;
  box-shadow: 0 1px 3px rgba(15, 20, 30, 0.05);
  position: sticky; top: 12px;
}
.side-head { display: flex; align-items: center; justify-content: space-between; margin-bottom: 12px; }
.side-title { font-size: 15px; font-weight: 700; color: #1f2329; }
.side-count { font-size: 12px; color: #5b6068; background: #f2f3f5; border-radius: 999px; padding: 1px 9px; }
.side-search { margin-bottom: 8px; }
.side-select { width: 100%; margin-bottom: 6px; }
.sc-list { max-height: 520px; overflow-y: auto; margin-top: 4px; }
.sc-item {
  display: flex; align-items: center; gap: 10px;
  padding: 11px 4px; border-bottom: 1px solid #f4f5f7;
  cursor: pointer; transition: background-color 0.15s;
}
.sc-item:hover { background: #fafbfc; }
.sc-item:last-child { border-bottom: 0; }
.sc-main { flex: 1; min-width: 0; }
.sc-name { font-size: 14px; color: #1f2329; line-height: 1.5; word-break: break-word; }
.sc-item:hover .sc-name { color: #1f2329; font-weight: 600; }
.sc-type { display: flex; align-items: center; gap: 6px; font-size: 12px; color: var(--ui-text-muted); margin-top: 3px; }
.sc-bound { transform: scale(0.9); transform-origin: left center; }
.sc-arrow { font-size: 13px; color: #c8c9cc; flex-shrink: 0; }
.side-foot { margin-top: 10px; font-size: 12px; color: #a8abb2; line-height: 1.6; }

/* 绑定关系 */
.bind-list { display: flex; flex-direction: column; }
.bind-item { display: flex; align-items: center; gap: 10px; padding: 12px 2px; border-bottom: 1px solid #f2f3f5; }
.bind-item:last-child { border-bottom: 0; }
.bind-main { flex: 1; min-width: 0; display: flex; flex-direction: column; gap: 3px; }
.bind-name { font-size: 14px; color: #1f2329; cursor: pointer; font-weight: 600; }
.bind-name:hover { text-decoration: underline; }
.bind-meta { font-size: 12px; color: var(--ui-text-muted); }

/* 弹窗内元素 */
.rename-preview { display: flex; align-items: center; gap: 12px; }
.rename-thumb { width: 96px; aspect-ratio: 4 / 3; border-radius: 8px; }
.dlg-stars { display: flex; align-items: center; gap: 2px; }
.star { font-size: 24px; color: var(--ui-info); cursor: pointer; line-height: 1; user-select: none; }
.star.on { color: #f7ba2a; }
.rate-text { font-size: 12px; color: var(--ui-text-muted); margin-left: 8px; }
.draft-table :deep(.el-table__cell) { padding: 6px 0; }

/* 小屏：右栏折叠为顶部抽屉入口 */
@media (max-width: 1199px) {
  .side-panel { display: none; }
  .city-nav-btn { display: inline-flex; }
  .main-panel { padding: 16px 18px 22px; }
  .archive-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); }
  .contact-grid { grid-template-columns: minmax(0, 1fr); }
}
</style>