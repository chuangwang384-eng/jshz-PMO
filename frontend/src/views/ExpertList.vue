<template>
  <div>
    <!-- 第一层：搜索筛选区 -->
    <div class="filter-bar">
      <el-input
        v-model="keyword"
        placeholder="搜索姓名、单位、科室、职称、联系方式或简介关键词"
        clearable
        class="search-input"
        @keyup.enter="handleSearch"
      >
        <template #append>
          <el-button @click="handleSearch">搜索</el-button>
        </template>
      </el-input>
      <el-button :type="activeFilterCount ? 'primary' : 'default'" :plain="!activeFilterCount" @click="toggleFilters">
        高级筛选<el-badge v-if="activeFilterCount" :value="activeFilterCount" class="filter-badge" />
      </el-button>
      <!-- 外部链接：卫健委机构查询平台（新窗口打开，仅前端跳转，不依赖后端） -->
      <el-button tag="a" href="https://zgcx.nhc.gov.cn/unit" target="_blank" rel="noopener">
        单位查询
      </el-button>
      <div class="spacer" />
      <template v-if="canAddExpert">
        <el-button type="primary" @click="openAdminCreate">新增专家</el-button>
      </template>
      <template v-else>
        <el-button type="primary" @click="openCreateDialog">新增专家</el-button>
      </template>
      <el-button type="primary" plain @click="batchResumeVisible = true">批量上传简历</el-button>
      <el-dropdown trigger="click" @command="handleExportCommand">
        <el-button type="success">
          导出<el-icon class="el-icon--right"><ArrowDown /></el-icon>
        </el-button>
        <template #dropdown>
          <el-dropdown-menu>
            <template v-if="canExport">
              <el-dropdown-item command="excel-all">全量 Excel</el-dropdown-item>
              <el-dropdown-item command="zip-all">全量含照片 ZIP</el-dropdown-item>
            </template>
            <el-dropdown-item command="excel-selected" :disabled="!selectedIds.size">选中 Excel</el-dropdown-item>
            <el-dropdown-item command="zip-selected" :disabled="!selectedIds.size">选中含照片 ZIP</el-dropdown-item>
          </el-dropdown-menu>
        </template>
      </el-dropdown>
    </div>

    <!-- 已应用筛选 chips：逐项移除或清除全部（参考 Owner 2026-11-07 示意图） -->
    <div v-if="activeFilterCount" class="applied-bar">
      <span class="applied-label">已应用筛选：</span>
      <el-tag v-for="chip in appliedFilters" :key="chip.key" closable type="primary" @close="removeFilter(chip.key)">
        {{ chip.label }}
      </el-tag>
      <el-button type="danger" link size="small" @click="resetFilters">清除全部</el-button>
    </div>

    <!-- 高级筛选面板：组合使用 -->
    <div v-if="filtersOpen" class="advanced-panel">
      <span class="filter-field"><label>工作单位</label><el-select v-model="filters.unit" filterable clearable placeholder="选择单位" style="width: 220px"><el-option v-for="u in filterOptions.units" :key="u" :label="u" :value="u" /></el-select></span>
      <span class="filter-field"><label>所属科室</label><el-select v-model="filters.department" filterable clearable placeholder="选择科室" style="width: 180px"><el-option v-for="d in filterOptions.departments" :key="d" :label="d" :value="d" /></el-select></span>
      <span class="filter-field"><label>专家职称</label><el-select v-model="filters.titles" multiple filterable collapse-tags clearable placeholder="选择职称" style="width: 220px"><el-option v-for="t in filterOptions.titles" :key="t" :label="t" :value="t" /></el-select></span>
      <span class="filter-field"><label>专家照片</label><el-select v-model="filters.photo" clearable placeholder="全部" style="width: 120px"><el-option label="已上传" value="uploaded" /><el-option label="未上传" value="missing" /></el-select></span>
      <span class="filter-field"><label>档案状态</label><el-select v-model="filters.complete" clearable placeholder="全部" style="width: 120px"><el-option label="完整" value="complete" /><el-option label="待完善" value="incomplete" /></el-select></span>
      <span class="filter-field"><label>重名同单位</label><el-select v-model="filters.duplicate" clearable placeholder="全部" style="width: 120px"><el-option label="仅看重名" value="yes" /></el-select></span>
      <span class="filter-field"><label>最近更新</label><el-date-picker v-model="filters.updatedRange" type="daterange" value-format="YYYY-MM-DD" start-placeholder="开始日期" end-placeholder="结束日期" style="width: 240px" /></span>
    </div>

    <!-- 第二层：工具行（统计 + 视图切换，两种视图共用搜索/勾选/页码） -->
    <div class="tool-row">
      <span class="total-label">专家档案 · 共 {{ total }} 人</span>
      <div class="spacer" />
      <span class="view-toggle-label">显示方式</span>
      <el-tooltip content="卡片视图" placement="top">
        <button type="button" class="view-btn" :class="{ active: viewMode === 'card' }" aria-label="卡片视图" @click="viewMode = 'card'"><el-icon><Grid /></el-icon>卡片</button>
      </el-tooltip>
      <el-tooltip content="列表视图" placement="top">
        <button type="button" class="view-btn" :class="{ active: viewMode === 'list' }" aria-label="列表视图" @click="viewMode = 'list'"><el-icon><Menu /></el-icon>列表</button>
      </el-tooltip>
    </div>

    <!-- 已选专家姓名展示：跨搜索/翻页保留，可逐项取消或一键清空 -->
    <div v-if="!canExport && selectedExperts.length" class="selected-bar">
      <span class="selected-label">已选 {{ selectedExperts.length }} 位：</span>
      <el-tag
        v-for="expert in selectedExperts"
        :key="expert.id"
        closable
        type="primary"
        @close="removeSelectedExpert(expert)"
      >
        {{ expert.name }}
      </el-tag>
      <el-button type="danger" link size="small" @click="clearSelection">清空</el-button>
    </div>

    <!-- 第三层：专家内容区（卡片/列表两种视图，共用搜索条件、排序与勾选状态） -->
    <div v-if="viewMode === 'card'" v-loading="loading" class="expert-cards" style="margin-top: 16px; min-height: 120px">
      <div
        v-for="expert in experts"
        :key="expert.id"
        class="expert-card ui-card"
        :class="{ selected: selectedIds.has(expert.id) }"
        @click="openDetail(expert)"
      >
        <el-checkbox
          v-if="!canExport"
          :model-value="selectedIds.has(expert.id)"
          class="card-check"
          @click.stop
          @change="() => toggleSelect(expert)"
        />
        <div class="card-main">
          <div class="card-photo" @click.stop>
            <PhotoImage :filename="expert.照片" :width="80" :height="80" />
          </div>
          <div class="card-info">
            <div class="card-name">{{ expert.姓名 }}</div>
            <div class="card-text">{{ expert.单位 }}</div>
            <div class="card-text">{{ expert.科室 }}</div>
          </div>
        </div>
        <div class="card-meta">
          <span class="card-meta-item" :title="expert['修改时间'] || '—'">
            <svg class="meta-icon" viewBox="0 0 16 16" fill="none" stroke="currentColor" stroke-width="1.35" stroke-linecap="round" stroke-linejoin="round" width="15" height="15" aria-hidden="true">
              <circle cx="8" cy="8" r="6.5" />
              <polyline points="8 4.5 8 8 10.5 10" />
            </svg>
            <span>{{ expert['修改时间'] ? formatMonthDay(expert['修改时间']) : '—' }}</span>
          </span>
          <span class="card-meta-item" :title="expert['修改人id'] || '—'">
            <svg class="meta-icon" viewBox="0 0 16 16" fill="none" stroke="currentColor" stroke-width="1.35" stroke-linecap="round" stroke-linejoin="round" width="15" height="15" aria-hidden="true">
              <circle cx="8" cy="5.5" r="3" />
              <path d="M3.5 14a4.5 4.5 0 0 1 9 0" />
            </svg>
            <span>{{ expert['修改人id'] || '—' }}</span>
          </span>
          <span class="card-meta-item">
            <svg class="meta-icon" viewBox="0 0 16 16" fill="none" stroke="currentColor" stroke-width="1.35" stroke-linecap="round" stroke-linejoin="round" width="15" height="15" aria-hidden="true">
              <path d="M8 2v8" />
              <polyline points="4.5 7 8 10.5 11.5 7" />
              <path d="M3 14h10" />
            </svg>
            <span>{{ expert['下载次数'] ?? 0 }}</span>
          </span>
        </div>
      </div>
      <el-empty
        v-if="!loading && !experts.length"
        description="暂无专家数据"
        :image-size="100"
        style="grid-column: 1 / -1"
      />
    </div>

    <!-- 列表视图：高效率管理（同数据/勾选/分页） -->
    <div v-else v-loading="loading" style="margin-top: 16px; min-height: 120px">
      <el-table :data="experts" style="width: 100%" @row-click="openDetail">
        <el-table-column v-if="!canExport" width="44">
          <template #default="{ row }">
            <el-checkbox
              :model-value="selectedIds.has(row.id)"
              @click.stop
              @change="() => toggleSelect(row)"
            />
          </template>
        </el-table-column>
        <el-table-column label="头像" width="70">
          <template #default="{ row }">
            <div @click.stop>
              <PhotoImage :filename="row.照片" :width="44" :height="44" style="border-radius: 50%; display: block" />
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="姓名" label="姓名" min-width="90" />
        <el-table-column prop="单位" label="单位" min-width="200" show-overflow-tooltip />
        <el-table-column prop="科室" label="科室" min-width="130" show-overflow-tooltip />
        <el-table-column prop="职称" label="职称" min-width="110" show-overflow-tooltip />
        <el-table-column prop="联系方式" label="联系方式" min-width="130" />
        <el-table-column label="更新时间" width="100">
          <template #default="{ row }">{{ row['修改时间'] ? formatMonthDay(row['修改时间']) : '—' }}</template>
        </el-table-column>
        <el-table-column label="操作" width="100">
          <template #default="{ row }">
            <el-button link type="primary" @click.stop="openDetail(row)">查看详情</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-empty v-if="!loading && !experts.length" description="暂无专家数据" :image-size="100" />
    </div>

    <!-- 悬浮批量操作栏：勾选后出现（导出 Excel / 含照片 ZIP） -->
    <div v-if="selectedExperts.length" class="batch-bar">
      <span class="batch-label">已选 {{ selectedExperts.length }} 位专家</span>
      <el-button size="small" type="primary" @click="handleExportSelectedExcel">下载 Excel</el-button>
      <el-button size="small" @click="handleExportSelectedZip">下载 ZIP</el-button>
      <el-button size="small" text type="danger" @click="clearSelection">清空</el-button>
    </div>

    <div class="pagination-wrap">
      <el-pagination
        v-model:current-page="page"
        v-model:page-size="size"
        :total="total"
        :page-sizes="[10, 20, 50, 100]"
        layout="total, sizes, prev, pager, next, jumper"
        @size-change="handlePageChange"
        @current-change="handlePageChange"
      />
    </div>

    <!-- 专家详情（统一视图：管理员/超管可保存直写与删除，员工走审批流） -->
    <el-dialog
      v-model="detailVisible"
      title="专家详情"
      width="760px"
      destroy-on-close
      :before-close="handleDetailClose"
    >
      <el-form v-if="detail" :model="editForm" label-width="150px" class="edit-form">
        <!-- 简历解析更新（Owner 2026-11-11）：上传简历→解析预填→人工确认保存，不自动覆盖 -->
        <div class="resume-parse-block" style="border: 1px dashed #d9ecff; border-radius: 8px; padding: 10px 14px; margin-bottom: 14px">
          <div style="display: flex; align-items: center; gap: 12px; flex-wrap: wrap">
            <el-upload :auto-upload="false" :show-file-list="false" accept=".pdf,.doc,.docx,.ppt,.pptx,.jpg,.jpeg,.png" :on-change="onEditResumePick">
              <el-button size="small" plain :loading="resumeParsing">选择简历解析更新</el-button>
            </el-upload>
            <el-button v-if="editResumeFile" size="small" type="primary" plain :loading="resumeParsing" @click="parseResumeNow('edit')">开始解析</el-button>
            <span class="resume-tip">{{ resumeMessage || '解析结果仅供预填确认，保存前可修改；照片候选为透明背景证件照' }}</span>
          </div>
          <div v-if="editResumeCandidates.length" class="resume-candidates">
            <div v-for="(candidate, index) in editResumeCandidates" :key="index" class="resume-candidate">
              <PhotoImage :src="'data:' + candidate.mime + ';base64,' + candidate.base64" fit="contain" alt="证件照候选" />
              <el-tag v-if="candidate.transparent" size="small" type="success">已抠透明背景</el-tag>
              <el-button size="small" type="primary" plain @click="useResumePhoto(candidate, 'edit')">使用此照片</el-button>
            </div>
          </div>
        </div>
        <div class="edit-photos">
          <div class="edit-photo-item">
            <div class="edit-photo-label">证件照</div>
            <div class="edit-photo-box round">
              <PhotoImage v-if="activePhotoPreview.photo" :src="activePhotoPreview.photo" :width="80" :height="80" fit="contain" alt="证件照" />
              <PhotoImage v-else :filename="detail.照片" :width="80" :height="80" />
            </div>
            <el-upload
              :auto-upload="false"
              :show-file-list="false"
              accept="image/*"
              :on-change="(f) => onPickPhoto('photo', f)"
            >
              <el-button size="small" type="primary" plain>更换照片</el-button>
            </el-upload>
          </div>
          <div class="edit-photo-item">
            <div class="edit-photo-label">身份证正面</div>
            <div class="edit-photo-box">
              <PhotoImage v-if="activePhotoPreview.front" :src="activePhotoPreview.front" :width="120" :height="84" fit="contain" alt="身份证正面" />
              <div v-else-if="idFrontUrl" class="masked-photo">
                <img :src="idFrontUrl" class="masked-img" alt="身份证正面（已脱敏）" />
                <span class="masked-tip">已脱敏</span>
              </div>
              <span v-else class="photo-empty">未上传</span>
            </div>
            <el-upload
              :auto-upload="false"
              :show-file-list="false"
              accept="image/*"
              :on-change="(f) => onPickPhoto('front', f)"
            >
              <el-button size="small" type="primary" plain>更换照片</el-button>
            </el-upload>
          </div>
          <div class="edit-photo-item">
            <div class="edit-photo-label">身份证反面</div>
            <div class="edit-photo-box">
              <PhotoImage v-if="activePhotoPreview.back" :src="activePhotoPreview.back" :width="120" :height="84" fit="contain" alt="身份证反面" />
              <div v-else-if="idBackUrl" class="masked-photo">
                <img :src="idBackUrl" class="masked-img" alt="身份证反面（已脱敏）" />
                <span class="masked-tip">已脱敏</span>
              </div>
              <span v-else class="photo-empty">未上传</span>
            </div>
            <el-upload
              :auto-upload="false"
              :show-file-list="false"
              accept="image/*"
              :on-change="(f) => onPickPhoto('back', f)"
            >
              <el-button size="small" type="primary" plain>更换照片</el-button>
            </el-upload>
          </div>
        </div>
        <el-form-item label="姓名">
          <el-input v-model="editForm.姓名" placeholder="请输入姓名">
            <template #suffix>
              <el-tooltip content="复制" placement="top">
                <el-icon class="copy-icon" @click="copyFieldValue(editForm['姓名'])"><CopyDocument /></el-icon>
              </el-tooltip>
            </template>
          </el-input>
        </el-form-item>
        <el-form-item label="单位">
          <el-input v-model="editForm.单位" placeholder="请输入单位">
            <template #suffix>
              <el-tooltip content="复制" placement="top">
                <el-icon class="copy-icon" @click="copyFieldValue(editForm['单位'])"><CopyDocument /></el-icon>
              </el-tooltip>
            </template>
          </el-input>
        </el-form-item>
        <el-form-item label="科室">
          <el-input v-model="editForm.科室" placeholder="请输入科室">
            <template #suffix>
              <el-tooltip content="复制" placement="top">
                <el-icon class="copy-icon" @click="copyFieldValue(editForm['科室'])"><CopyDocument /></el-icon>
              </el-tooltip>
            </template>
          </el-input>
        </el-form-item>
        <el-form-item label="职称">
          <el-input v-model="editForm.职称" placeholder="请输入职称">
            <template #suffix>
              <el-tooltip content="复制" placement="top">
                <el-icon class="copy-icon" @click="copyFieldValue(editForm['职称'])"><CopyDocument /></el-icon>
              </el-tooltip>
            </template>
          </el-input>
        </el-form-item>
        <el-form-item label="联系方式">
          <el-input v-model="editForm.联系方式" placeholder="请输入联系方式">
            <template #suffix>
              <el-tooltip content="复制" placement="top">
                <el-icon class="copy-icon" @click="copyFieldValue(editForm['联系方式'])"><CopyDocument /></el-icon>
              </el-tooltip>
            </template>
          </el-input>
        </el-form-item>
        <el-form-item label="简介">
          <div class="textarea-copy-wrap">
            <el-input v-model="editForm.简介" type="textarea" :rows="2" placeholder="请输入简介" />
            <el-tooltip content="复制" placement="top">
              <el-icon class="copy-icon textarea-copy" @click="copyFieldValue(editForm['简介'])"><CopyDocument /></el-icon>
            </el-tooltip>
          </div>
        </el-form-item>
        <el-form-item label="身份证号">
          <el-input v-model="editForm.身份证号" placeholder="请输入身份证号（18 位）">
            <template #suffix>
              <el-tooltip content="复制" placement="top">
                <el-icon class="copy-icon" @click="copyFieldValue(editForm['身份证号'])"><CopyDocument /></el-icon>
              </el-tooltip>
            </template>
          </el-input>
        </el-form-item>
        <el-form-item label="银行卡号">
          <el-input v-model="editForm.银行卡号" placeholder="请输入银行卡号">
            <template #suffix>
              <el-tooltip content="复制" placement="top">
                <el-icon class="copy-icon" @click="copyFieldValue(editForm['银行卡号'])"><CopyDocument /></el-icon>
              </el-tooltip>
            </template>
          </el-input>
        </el-form-item>
        <el-form-item label="银行卡开户行">
          <el-input v-model="editForm.银行卡开户行" placeholder="请输入开户行">
            <template #suffix>
              <el-tooltip content="复制" placement="top">
                <el-icon class="copy-icon" @click="copyFieldValue(editForm['银行卡开户行'])"><CopyDocument /></el-icon>
              </el-tooltip>
            </template>
          </el-input>
        </el-form-item>
        <el-alert
          type="info"
          :closable="false"
          show-icon
          :title="canEditExpert ? '管理员编辑免审批，保存后直接生效' : '修改将在审批通过后生效，关闭弹窗时如检测到修改会引导您提交申请'"
        />
      </el-form>
      <template #footer>
        <el-button v-permission="'expert:delete'" type="danger" :loading="adminDeleting" @click="handleAdminDelete">删除专家</el-button>
        <el-button @click="handleDetailCancel">取消</el-button>
        <el-button v-if="canEditExpert" type="primary" :loading="adminSubmitting" @click="submitAdminEdit">保存</el-button>
        <el-button v-else type="primary" @click="handleDetailSubmit">提交申请</el-button>
      </template>
    </el-dialog>

    <BatchResumeDialog v-model="batchResumeVisible" :can-add="canAddExpert" :can-edit="canEditExpert" @saved="fetchList" />

    <!-- 新增专家申请弹窗（员工：复用可编辑表单字段结构与照片选择，提交审批流） -->
    <el-dialog v-model="createVisible" title="新增专家（需审批）" width="760px" destroy-on-close>
      <el-form :model="createForm" label-width="110px" class="edit-form">
        <!-- 简历解析：候选填充 + 人工确认 -->
        <div class="resume-parse">
          <el-upload
            :auto-upload="false"
            :show-file-list="false"
            accept=".pdf,.doc,.docx,.ppt,.pptx,.jpg,.png"
            :on-change="onResumePick"
          >
            <el-button :loading="resumeParsing">选择简历</el-button>
          </el-upload>
          <el-button v-if="resumeFile" type="primary" plain :loading="resumeParsing" @click="parseResumeNow">开始解析</el-button>
          <span class="resume-tip" :class="{ 'resume-tip-error': resumeFailed }">
            {{ resumeMessage || `支持 PDF / Word / PPT / 图片（≤${toMb(uploadLimits.resumeMaxBytes)}MB）；解析结果仅供确认，扫描件约需 10~45 秒` }}
          </span>
          <div v-if="resumeCandidates.length" class="resume-candidates">
            <div v-for="(candidate, index) in resumeCandidates" :key="index" class="resume-candidate" style="display: flex; flex-direction: column; align-items: center; gap: 4px">
              <PhotoImage :src="'data:' + candidate.mime + ';base64,' + candidate.base64" fit="contain" alt="解析出的证件照候选" />
              <el-tag v-if="candidate.transparent" size="small" type="success">已抠透明背景</el-tag>
              <el-button size="small" type="primary" plain @click="useResumePhoto(candidate, 'create')">使用此照片</el-button>
            </div>
          </div>
        </div>
        <div class="edit-photos">
          <div class="edit-photo-item">
            <div class="edit-photo-label">证件照</div>
            <div class="edit-photo-box round">
              <PhotoImage v-if="createPhotoPreview.photo" :src="createPhotoPreview.photo" :width="100" :height="100" fit="contain" alt="证件照" />
              <span v-else class="photo-empty">未上传</span>
            </div>
            <el-upload
              :auto-upload="false"
              :show-file-list="false"
              accept="image/*"
              :on-change="(f) => onCreatePickPhoto('photo', f)"
            >
              <el-button size="small" type="primary" plain>上传照片</el-button>
            </el-upload>
          </div>
          <div class="edit-photo-item">
            <div class="edit-photo-label">身份证正面</div>
            <div class="edit-photo-box">
              <PhotoImage v-if="createPhotoPreview.front" :src="createPhotoPreview.front" :width="150" :height="100" fit="contain" alt="身份证正面" />
              <span v-else class="photo-empty">未上传</span>
            </div>
            <el-upload
              :auto-upload="false"
              :show-file-list="false"
              accept="image/*"
              :on-change="(f) => onCreatePickPhoto('front', f)"
            >
              <el-button size="small" type="primary" plain>上传照片</el-button>
            </el-upload>
          </div>
          <div class="edit-photo-item">
            <div class="edit-photo-label">身份证反面</div>
            <div class="edit-photo-box">
              <PhotoImage v-if="createPhotoPreview.back" :src="createPhotoPreview.back" :width="150" :height="100" fit="contain" alt="身份证反面" />
              <span v-else class="photo-empty">未上传</span>
            </div>
            <el-upload
              :auto-upload="false"
              :show-file-list="false"
              accept="image/*"
              :on-change="(f) => onCreatePickPhoto('back', f)"
            >
              <el-button size="small" type="primary" plain>上传照片</el-button>
            </el-upload>
          </div>
        </div>
        <el-form-item>
          <template #label>姓名<span v-if="resumeFilled['姓名']" class="resume-mark">来自简历</span></template>
          <el-input v-model="createForm.姓名" placeholder="请输入姓名（2-20 个汉字）" />
        </el-form-item>
        <el-form-item>
          <template #label>单位<span v-if="resumeFilled['单位']" class="resume-mark">来自简历</span></template>
          <el-input v-model="createForm.单位" placeholder="请输入单位" />
        </el-form-item>
        <el-form-item>
          <template #label>科室<span v-if="resumeFilled['科室']" class="resume-mark">来自简历</span></template>
          <el-input v-model="createForm.科室" placeholder="请输入科室" />
        </el-form-item>
        <el-form-item>
          <template #label>职称<span v-if="resumeFilled['职称']" class="resume-mark">来自简历</span></template>
          <el-input v-model="createForm.职称" placeholder="请输入职称" />
        </el-form-item>
        <el-form-item>
          <template #label>联系方式<span v-if="resumeFilled['联系方式']" class="resume-mark">来自简历</span></template>
          <el-input v-model="createForm.联系方式" placeholder="请输入联系方式" />
        </el-form-item>
        <el-form-item>
          <template #label>简介<span v-if="resumeFilled['简介']" class="resume-mark">来自简历</span></template>
          <el-input v-model="createForm.简介" type="textarea" :rows="4" placeholder="未归入上述字段的简历内容会自动收入此处" />
        </el-form-item>
        <el-form-item label="身份证号">
          <el-input v-model="createForm.身份证号" placeholder="请输入身份证号（18 位）" />
        </el-form-item>
        <el-form-item label="银行卡号">
          <el-input v-model="createForm.银行卡号" placeholder="请输入银行卡号" />
        </el-form-item>
        <el-form-item label="银行卡开户行">
          <el-input v-model="createForm.银行卡开户行" placeholder="请输入开户行" />
        </el-form-item>
        <el-alert
          type="info"
          :closable="false"
          show-icon
          title="提交后将生成审批流，管理员审批通过后创建专家记录并归档照片"
        />
      </el-form>
      <template #footer>
        <el-button @click="createVisible = false">取消</el-button>
        <el-button type="primary" :loading="createSubmitting" @click="submitCreateExpert">提交申请</el-button>
      </template>
    </el-dialog>

    <!-- 新增专家弹窗（管理员：multipart 直传免审批） -->
    <el-dialog v-model="adminCreateVisible" title="新增专家（免审批）" width="760px" destroy-on-close>
      <el-form :model="adminCreateForm" label-width="110px" class="edit-form">
        <div class="edit-photos">
          <div class="edit-photo-item">
            <div class="edit-photo-label">证件照</div>
            <div class="edit-photo-box round">
              <PhotoImage v-if="adminCreatePhotoPreview.photo" :src="adminCreatePhotoPreview.photo" :width="80" :height="80" fit="contain" alt="证件照" />
              <span v-else class="photo-empty">未上传</span>
            </div>
            <el-upload
              :auto-upload="false"
              :show-file-list="false"
              accept="image/*"
              :on-change="(f) => onAdminCreatePickPhoto('photo', f)"
            >
              <el-button size="small" type="primary" plain>上传照片</el-button>
            </el-upload>
          </div>
          <div class="edit-photo-item">
            <div class="edit-photo-label">身份证正面</div>
            <div class="edit-photo-box">
              <PhotoImage v-if="adminCreatePhotoPreview.front" :src="adminCreatePhotoPreview.front" :width="120" :height="84" fit="contain" alt="身份证正面" />
              <span v-else class="photo-empty">未上传</span>
            </div>
            <el-upload
              :auto-upload="false"
              :show-file-list="false"
              accept="image/*"
              :on-change="(f) => onAdminCreatePickPhoto('front', f)"
            >
              <el-button size="small" type="primary" plain>上传照片</el-button>
            </el-upload>
          </div>
          <div class="edit-photo-item">
            <div class="edit-photo-label">身份证反面</div>
            <div class="edit-photo-box">
              <PhotoImage v-if="adminCreatePhotoPreview.back" :src="adminCreatePhotoPreview.back" :width="120" :height="84" fit="contain" alt="身份证反面" />
              <span v-else class="photo-empty">未上传</span>
            </div>
            <el-upload
              :auto-upload="false"
              :show-file-list="false"
              accept="image/*"
              :on-change="(f) => onAdminCreatePickPhoto('back', f)"
            >
              <el-button size="small" type="primary" plain>上传照片</el-button>
            </el-upload>
          </div>
        </div>
        <el-form-item label="姓名">
          <el-input v-model="adminCreateForm.姓名" placeholder="请输入姓名（2-20 个汉字）" />
        </el-form-item>
        <el-form-item label="单位">
          <el-input v-model="adminCreateForm.单位" placeholder="请输入单位" />
        </el-form-item>
        <el-form-item label="科室">
          <el-input v-model="adminCreateForm.科室" placeholder="请输入科室" />
        </el-form-item>
        <el-form-item label="职称">
          <el-input v-model="adminCreateForm.职称" placeholder="请输入职称" />
        </el-form-item>
        <el-form-item label="联系方式">
          <el-input v-model="adminCreateForm.联系方式" placeholder="请输入联系方式" />
        </el-form-item>
        <el-form-item label="简介">
          <el-input v-model="adminCreateForm.简介" type="textarea" :rows="2" placeholder="请输入简介" />
        </el-form-item>
        <el-form-item label="身份证号">
          <el-input v-model="adminCreateForm.身份证号" placeholder="请输入身份证号（18 位）" />
        </el-form-item>
        <el-form-item label="银行卡号">
          <el-input v-model="adminCreateForm.银行卡号" placeholder="请输入银行卡号" />
        </el-form-item>
        <el-form-item label="银行卡开户行">
          <el-input v-model="adminCreateForm.银行卡开户行" placeholder="请输入开户行" />
        </el-form-item>
        <el-alert
          type="info"
          :closable="false"
          show-icon
          title="管理员新增免审批，保存后直接创建专家记录并归档照片"
        />
      </el-form>
      <template #footer>
        <el-button @click="adminCreateVisible = false">取消</el-button>
        <el-button type="primary" :loading="adminCreateSubmitting" @click="submitAdminCreate">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { listExperts, getExpert, createExpert, updateExpert, deleteExpert, exportAllExcel, exportEmployeeZip, exportSelectedExcel, exportSelectedZip, fetchPhotoBlob, expertFilters, parseResume } from '../api/expert'
import { submitApprovalRequest, uploadApprovalPhoto, createExpertApprovalRequest } from '../api/approval'
import { uploadLimits, ensureUploadLimits, toMb } from '../utils/limits'

ensureUploadLimits()
import { useAuthStore } from '../store/auth'
import PhotoImage from '../components/PhotoImage.vue'
import BatchResumeDialog from '../components/BatchResumeDialog.vue'
import { ArrowDown, Grid, Menu, CopyDocument } from '@element-plus/icons-vue'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()
const canAddExpert = computed(() => authStore.hasPermission('expert:add'))
const canEditExpert = computed(() => authStore.hasPermission('expert:edit'))
const canExport = computed(() => authStore.hasPermission('expert:export'))
const batchResumeVisible = ref(false)
// 统一详情弹窗照片预览：管理员/超管显示 admin 预览，员工显示 edit 预览
const activePhotoPreview = computed(() => (canEditExpert.value ? adminPhotoPreview : photoPreview))
const selectedIds = ref(new Set())
// 已选专家姓名（{id, 姓名}），跨搜索/翻页保留，用于标签展示与逐项取消
const selectedExperts = ref([])

const keyword = ref('')
const experts = ref([])
const total = ref(0)
const page = ref(1)
const size = ref(20)
const loading = ref(false)

// 高级筛选（Owner 2026-11-07：组合使用+一键重置；所在地区筛选拍板去掉——数据无省/市字段）
const filtersOpen = ref(false)
const filters = reactive({ unit: '', department: '', titles: [], photo: '', complete: '', duplicate: '', updatedRange: null })
const filterOptions = ref({ units: [], departments: [], titles: [] })
const filterOptionsLoaded = ref(false)
const viewMode = ref('card')
const activeFilterCount = computed(() =>
  [filters.unit, filters.department, filters.photo, filters.complete, filters.duplicate].filter(Boolean).length +
  (filters.titles.length ? 1 : 0) +
  (filters.updatedRange?.length ? 1 : 0)
)
function toggleFilters() {
  filtersOpen.value = !filtersOpen.value
  if (filtersOpen.value && !filterOptionsLoaded.value) loadFilterOptions()
}
async function loadFilterOptions() {
  try {
    filterOptions.value = await expertFilters()
    filterOptionsLoaded.value = true
  } catch {
    ElMessage.error('筛选字典加载失败，请重试')
  }
}
function resetFilters() {
  filters.unit = ''
  filters.department = ''
  filters.titles = []
  filters.photo = ''
  filters.complete = ''
  filters.duplicate = ''
  filters.updatedRange = null
}
/** 已应用筛选 chips（参考 Owner 2026-11-07 示意图）：逐项移除或清除全部。 */
const appliedFilters = computed(() => {
  const chips = []
  if (filters.unit) chips.push({ key: 'unit', label: filters.unit })
  if (filters.department) chips.push({ key: 'department', label: filters.department })
  for (const t of filters.titles) chips.push({ key: 'title:' + t, label: t })
  if (filters.photo) chips.push({ key: 'photo', label: filters.photo === 'uploaded' ? '已上传照片' : '未上传照片' })
  if (filters.complete) chips.push({ key: 'complete', label: filters.complete === 'complete' ? '档案完整' : '档案待完善' })
  if (filters.duplicate) chips.push({ key: 'duplicate', label: '重名且同单位' })
  if (filters.updatedRange?.length) chips.push({ key: 'updated', label: `${filters.updatedRange[0]} ~ ${filters.updatedRange[1]}` })
  return chips
})
function removeFilter(key) {
  if (key === 'unit') filters.unit = ''
  else if (key === 'department') filters.department = ''
  else if (key === 'photo') filters.photo = ''
  else if (key === 'complete') filters.complete = ''
  else if (key === 'duplicate') filters.duplicate = ''
  else if (key === 'updated') filters.updatedRange = null
  else if (key.startsWith('title:')) filters.titles = filters.titles.filter(t => t !== key.slice(6))
}
watch(filters, () => {
  page.value = 1
  fetchList()
}, { deep: true })

const detailVisible = ref(false)
const detail = ref(null)
// 身份证正反面照片 blob URL（仅脱敏模糊展示，不提供原图预览）
const idFrontUrl = ref('')
const idBackUrl = ref('')

// 卡片内直接编辑（需求3：员工视角详情弹窗可编辑，关闭时检测变更生成审批流）
const TEXT_FIELDS = ['姓名', '单位', '科室', '职称', '联系方式', '简介', '身份证号', '银行卡号', '银行卡开户行']
const editForm = reactive({
  姓名: '',
  单位: '',
  科室: '',
  职称: '',
  联系方式: '',
  简介: '',
  身份证号: '',
  银行卡号: '',
  银行卡开户行: ''
})
// 打开弹窗时的初始值快照（用于 dirty 检测，员工视角脱敏值不改则不提交）
const editSnapshot = ref(null)
// 照片变更标记 + 本地预览 + 待上传文件
const photoChanged = reactive({ photo: false, front: false, back: false })
const photoPreview = reactive({ photo: '', front: '', back: '' })
const pickedFiles = reactive({ photo: null, front: null, back: null })

// 是否有未提交的修改：任一文本字段变化或任一照片被更换
const dirty = computed(() => {
  if (!editSnapshot.value) return false
  for (const k of TEXT_FIELDS) {
    if (String(editForm[k] ?? '').trim() !== String(editSnapshot.value[k] ?? '').trim()) {
      return true
    }
  }
  return photoChanged.photo || photoChanged.front || photoChanged.back
})

async function fetchList() {
  loading.value = true
  try {
    const data = await listExperts({
      keyword: keyword.value || undefined,
      unit: filters.unit || undefined,
      department: filters.department || undefined,
      titles: filters.titles.length ? filters.titles.join(',') : undefined,
      photo: filters.photo || undefined,
      complete: filters.complete || undefined,
      duplicate: filters.duplicate || undefined,
      updatedFrom: filters.updatedRange?.[0] || undefined,
      updatedTo: filters.updatedRange?.[1] || undefined,
      page: page.value,
      size: size.value
    })
    experts.value = data?.items || []
    total.value = data?.total || 0
  } finally {
    loading.value = false
  }
}

// 更新时间仅显示 MM-DD（如 10-22），完整时间由调用处放 title
function formatMonthDay(value) {
  if (!value) return '—'
  const s = String(value).trim()
  const m = s.match(/(\d{4})-(\d{2})-(\d{2})/)
  return m ? `${m[2]}-${m[3]}` : s
}

function handleSearch() {
  page.value = 1
  // 搜索不清理已勾选数据，勾选集合跨搜索保留
  fetchList()
}

// 翻页 / 改每页条数：重新加载数据但保留勾选（selectedIds 为全局集合，跨页有效）
function handlePageChange() {
  fetchList()
}

function clearSelection() {
  if (selectedIds.value.size) {
    selectedIds.value = new Set()
    selectedExperts.value = []
    ElMessage.info('已清空勾选')
  }
}

// 勾选切换：Set 快照更新，避免引用问题；同步维护已选姓名数组
function toggleSelect(expert) {
  const next = new Set(selectedIds.value)
  if (next.has(expert.id)) {
    next.delete(expert.id)
    selectedExperts.value = selectedExperts.value.filter((e) => e.id !== expert.id)
  } else {
    next.add(expert.id)
    selectedExperts.value.push({ id: expert.id, name: expert.姓名 })
  }
  selectedIds.value = next
}

// 逐项取消：点击姓名标签的 × 时，同步移除 selectedIds 与 selectedExperts
function removeSelectedExpert(expert) {
  const next = new Set(selectedIds.value)
  next.delete(expert.id)
  selectedIds.value = next
  selectedExperts.value = selectedExperts.value.filter((e) => e.id !== expert.id)
}

let detailSequence = 0
onBeforeUnmount(() => { detailSequence++ })
async function openDetail(row) {
  const sequence = ++detailSequence
  const data = await getExpert(row.id, canEditExpert.value ? { sensitive: 1 } : undefined)
  if(sequence !== detailSequence)return
  detail.value = data
  // 统一视图初始化：详情弹窗文本字段统一回填 editForm（管理员免审批直写 / 员工审批流共用）
  if (canEditExpert.value) {
    adminPhotoPreview.photo = ''
    adminPhotoPreview.front = ''
    adminPhotoPreview.back = ''
    adminPickedFiles.photo = null
    adminPickedFiles.front = null
    adminPickedFiles.back = null
  }
  for (const k of TEXT_FIELDS) {
    editForm[k] = data?.[k] ?? ''
  }
  // 简介回填原始简介（data['简介'] 为后端拼接展示值，避免把拼接内容提交审批写回存储）
  editForm['简介'] = data?.['原始简介'] ?? data?.['简介'] ?? ''
  editSnapshot.value = {}
  for (const k of TEXT_FIELDS) {
    editSnapshot.value[k] = data?.[k] ?? ''
  }
  editSnapshot.value['简介'] = data?.['原始简介'] ?? data?.['简介'] ?? ''
  photoChanged.photo = false
  photoChanged.front = false
  photoChanged.back = false
  photoPreview.photo = ''
  photoPreview.front = ''
  photoPreview.back = ''
  pickedFiles.photo = null
  pickedFiles.front = null
  pickedFiles.back = null
  // 加载身份证正反面照片（仅用于脱敏模糊展示，不提供原图预览）；先 revoke 旧 blob URL 防内存泄漏
  if (idFrontUrl.value) {
    URL.revokeObjectURL(idFrontUrl.value)
  }
  if (idBackUrl.value) {
    URL.revokeObjectURL(idBackUrl.value)
  }
  idFrontUrl.value = ''
  idBackUrl.value = ''
  if (data['身份证正面']) {
    try {
      const resp = await fetchPhotoBlob(data['身份证正面'])
      if(sequence !== detailSequence)return
      idFrontUrl.value = URL.createObjectURL(resp.data)
    } catch (e) {
      // 照片不存在或无权限：保持占位
    }
  }
  if (data['身份证反面']) {
    try {
      const resp = await fetchPhotoBlob(data['身份证反面'])
      if(sequence !== detailSequence)return
      idBackUrl.value = URL.createObjectURL(resp.data)
    } catch (e) {
      // 照片不存在或无权限：保持占位
    }
  }
  if(sequence === detailSequence) detailVisible.value = true
}

// 复制字段值到剪贴板：优先 navigator.clipboard，不可用时兜底 document.execCommand('copy')
async function copyFieldValue(value) {
  const text = String(value ?? '').trim()
  if (!text) {
    ElMessage.warning('暂无可复制的内容')
    return
  }
  try {
    if (navigator.clipboard && window.isSecureContext) {
      await navigator.clipboard.writeText(text)
      ElMessage.success('已复制')
      return
    }
    throw new Error('clipboard unavailable')
  } catch (e) {
    const ta = document.createElement('textarea')
    ta.value = text
    ta.style.position = 'fixed'
    ta.style.opacity = '0'
    document.body.appendChild(ta)
    ta.select()
    try {
      document.execCommand('copy')
      ElMessage.success('已复制')
    } catch (err) {
      ElMessage.error('复制失败，请手动复制')
    } finally {
      document.body.removeChild(ta)
    }
  }
}

function handleExportExcel() {
  exportAllExcel().catch(() => {})
}

function handleExportZip() {
  exportEmployeeZip().catch(() => {})
}

/** 导出下拉分派：全量（有 expert:export 权限）/ 选中（所有人，需先勾选）。 */
function handleExportCommand(command) {
  if (command === 'excel-all') handleExportExcel()
  else if (command === 'zip-all') handleExportZip()
  else if (command === 'excel-selected') handleExportSelectedExcel()
  else if (command === 'zip-selected') handleExportSelectedZip()
}

function handleExportSelectedExcel() {
  if (!selectedIds.value.size) {
    ElMessage.warning('请先勾选专家卡片')
    return
  }
  exportSelectedExcel([...selectedIds.value]).catch(() => {})
}

function handleExportSelectedZip() {
  if (!selectedIds.value.size) {
    ElMessage.warning('请先勾选专家卡片')
    return
  }
  exportSelectedZip([...selectedIds.value]).catch(() => {})
}

// 选择新照片：本地预览 + 标记变更 + 暂存文件（提交审批时再上传临时接口）；
// 统一弹窗按角色分派：管理员/超管存入 admin 变量（保存时随 FormData 直传），员工存入 edit 变量（提交审批时上传）
function onPickPhoto(kind, file) {
  const raw = file?.raw || file
  if (!raw) {
    return
  }
  if (raw.size > uploadLimits.photoMaxBytes) {
    ElMessage.warning(`图片大小不能超过 ${toMb(uploadLimits.photoMaxBytes)}MB`)
    return
  }
  if (canEditExpert.value) {
    adminPickedFiles[kind] = raw
    if (adminPhotoPreview[kind]) {
      URL.revokeObjectURL(adminPhotoPreview[kind])
    }
    adminPhotoPreview[kind] = URL.createObjectURL(raw)
    return
  }
  pickedFiles[kind] = raw
  photoChanged[kind] = true
  if (photoPreview[kind]) {
    URL.revokeObjectURL(photoPreview[kind])
  }
  photoPreview[kind] = URL.createObjectURL(raw)
}

// ==================== 新增专家申请（需求4：员工提交审批流） ====================
const createVisible = ref(false)
const createSubmitting = ref(false)
const createForm = reactive({
  姓名: '',
  单位: '',
  科室: '',
  职称: '',
  联系方式: '',
  简介: '',
  身份证号: '',
  银行卡号: '',
  银行卡开户行: ''
})
// 新增弹窗照片：本地预览 + 待上传文件
const createPhotoChanged = reactive({ photo: false, front: false, back: false })
const createPhotoPreview = reactive({ photo: '', front: '', back: '' })
const createPickedFiles = reactive({ photo: null, front: null, back: null })

// 简历解析（Owner 2026-11-07）：候选填充 + 人工确认；解析只填空字段，不覆盖已输入内容
const resumeFile = ref(null)
const resumeParsing = ref(false)
const resumeMessage = ref('')
const resumeFailed = ref(false)
const resumeCandidates = ref([])
const resumeFilled = reactive({})
// 解析目标：create=新建弹窗；edit=专家详情编辑弹窗（Owner 2026-11-11 简历解析更新）
const resumeTarget = ref('create')
const editResumeFile = ref(null)
const editResumeCandidates = ref([])

// 打开新增专家弹窗：表单与照片均初始为空
function openCreateDialog() {
  for (const k of TEXT_FIELDS) {
    createForm[k] = ''
  }
  createPhotoChanged.photo = false
  createPhotoChanged.front = false
  createPhotoChanged.back = false
  createPhotoPreview.photo = ''
  createPhotoPreview.front = ''
  createPhotoPreview.back = ''
  createPickedFiles.photo = null
  createPickedFiles.front = null
  createPickedFiles.back = null
  resetResumeState()
  createVisible.value = true
}

// ==================== 简历解析 ====================
function resetResumeState() {
  resumeFile.value = null
  resumeParsing.value = false
  resumeMessage.value = ''
  resumeFailed.value = false
  resumeCandidates.value = []
  for (const k of Object.keys(resumeFilled)) delete resumeFilled[k]
}

function onResumePick(file) {
  const raw = file?.raw || file
  if (!raw) return
  resumeFile.value = raw
  resumeMessage.value = raw.name ? `已选择：${raw.name}` : ''
  resumeFailed.value = false
}

function onEditResumePick(file) {
  const raw = file?.raw || file
  if (!raw) return
  editResumeFile.value = raw
  resumeMessage.value = raw.name ? `已选择：${raw.name}` : ''
}

async function parseResumeNow(target = 'create') {
  const file = target === 'edit' ? editResumeFile.value : resumeFile.value
  if (!file || resumeParsing.value) return
  if (file.size > uploadLimits.resumeMaxBytes) {
    ElMessage.warning(`简历文件超过 ${toMb(uploadLimits.resumeMaxBytes)} MB 限制`)
    return
  }
  resumeTarget.value = target
  resumeParsing.value = true
  resumeFailed.value = false
  try {
    const data = await parseResume(file)
    const candidates = (data?.photoCandidates || []).map((c) =>
      typeof c === 'string' ? { base64: c, mime: 'image/jpeg', transparent: false } : c
    )
    let filledCount = 0
    const form = target === 'edit' ? editForm : createForm
    for (const [key, value] of Object.entries(data?.fields || {})) {
      if (!String(form[key] ?? '').trim()) {
        form[key] = value
        if (target === 'create') resumeFilled[key] = true
        filledCount++
      }
    }
    if (target === 'edit') {
      editResumeCandidates.value = candidates
    } else {
      resumeCandidates.value = candidates
    }
    resumeMessage.value = data?.message
      || (filledCount
        ? `已解析 ${data.hitCount} 个字段（填充 ${filledCount} 个空字段），请核对后提交`
        : '未命中可填充字段，请核对照片候选后保存')
  } catch (e) {
    resumeFailed.value = true
    resumeMessage.value = e?.response?.data?.message || '解析失败，请重试或手动填写'
  } finally {
    resumeParsing.value = false
  }
}

function useResumePhoto(candidate, target = 'create') {
  try {
    const base64 = typeof candidate === 'string' ? candidate : candidate.base64
    const mime = typeof candidate === 'string' ? 'image/jpeg' : (candidate.mime || 'image/jpeg')
    const ext = mime === 'image/png' ? 'png' : 'jpg'
    const bin = atob(base64)
    const bytes = new Uint8Array(bin.length)
    for (let i = 0; i < bin.length; i++) bytes[i] = bin.charCodeAt(i)
    const file = new File([bytes], 'resume-photo.' + ext, { type: mime })
    if (target === 'edit') {
      onPickPhoto('photo', { raw: file })
    } else {
      onCreatePickPhoto('photo', { raw: file })
    }
    ElMessage.success('已使用解析照片作为证件照')
  } catch {
    ElMessage.error('照片候选处理失败，请直接手动上传')
  }
}

// 新增弹窗选照片：本地预览 + 暂存文件（提交时再上传临时接口）
function onCreatePickPhoto(kind, file) {
  const raw = file?.raw || file
  if (!raw) {
    return
  }
  if (raw.size > uploadLimits.photoMaxBytes) {
    ElMessage.warning(`图片大小不能超过 ${toMb(uploadLimits.photoMaxBytes)}MB`)
    return
  }
  createPickedFiles[kind] = raw
  createPhotoChanged[kind] = true
  if (createPhotoPreview[kind]) {
    URL.revokeObjectURL(createPhotoPreview[kind])
  }
  createPhotoPreview[kind] = URL.createObjectURL(raw)
}

// 提交新增专家申请：先逐个上传照片取 TMP: 文件名并入 changes，再调用 create-request
async function submitCreateExpert() {
  // Owner 2026-11-07：必填仅 姓名+单位
  const requiredFields = ['姓名', '单位']
  for (const k of requiredFields) {
    if (!String(createForm[k] ?? '').trim()) {
      ElMessage.warning(`请填写${k}`)
      return
    }
  }
  const name = String(createForm.姓名).trim()
  if (!/^[\u4e00-\u9fa5·]{2,20}$/.test(name)) {
    ElMessage.warning('姓名格式不正确（2-20 个汉字，可含间隔号）')
    return
  }
  const idCard = String(createForm.身份证号 ?? '').trim()
  if (idCard && !/^\d{17}[\dXx]$/.test(idCard)) {
    ElMessage.warning('身份证号格式不正确（18 位，末位可为 X）')
    return
  }
  const bankCard = String(createForm.银行卡号 ?? '').trim()
  if (bankCard && !/^\d{13,19}$/.test(bankCard)) {
    ElMessage.warning('银行卡号格式不正确（13-19 位数字）')
    return
  }
  const contact = String(createForm.联系方式 ?? '').trim()
  if (contact && !/^1\d{10}$|^[\w.-]+@[\w.-]+\.[a-zA-Z]{2,}$/.test(contact)) {
    ElMessage.warning('联系方式格式不正确（11 位手机号或邮箱）')
    return
  }
  const changes = {}
  for (const k of TEXT_FIELDS) {
    const v = String(createForm[k] ?? '').trim()
    if (v) {
      changes[k] = v
    }
  }
  const photoItems = [
    { changed: createPhotoChanged.photo, key: '照片', file: createPickedFiles.photo },
    { changed: createPhotoChanged.front, key: '身份证正面', file: createPickedFiles.front },
    { changed: createPhotoChanged.back, key: '身份证反面', file: createPickedFiles.back }
  ]
  for (const item of photoItems) {
    if (item.changed && item.file) {
      try {
        const res = await uploadApprovalPhoto(item.file)
        changes[item.key] = 'TMP:' + res.fileName
      } catch (e) {
        // 错误提示已由拦截器统一处理
        return
      }
    }
  }
  createSubmitting.value = true
  try {
    await createExpertApprovalRequest({
      changes,
      reason: '员工新增专家'
    })
    ElMessage.success('已提交新增专家申请，可在「我的申请」查看进度')
    createVisible.value = false
  } catch (e) {
    // 错误提示已由拦截器统一处理
  } finally {
    createSubmitting.value = false
  }
}

// 提交卡片内编辑的修改申请：仅提交【当前模板可见且有变化】的字段；照片先上传临时接口再以 TMP: 前缀并入
async function submitEditChange() {
  const changes = {}
  for (const k of TEXT_FIELDS) {
    const newVal = String(editForm[k] ?? '').trim()
    const oldVal = String(editSnapshot.value?.[k] ?? '').trim()
    if (newVal !== oldVal) {
      changes[k] = newVal
    }
  }
  const photoItems = [
    { changed: photoChanged.photo, key: '照片', file: pickedFiles.photo },
    { changed: photoChanged.front, key: '身份证正面', file: pickedFiles.front },
    { changed: photoChanged.back, key: '身份证反面', file: pickedFiles.back }
  ]
  for (const item of photoItems) {
    if (item.changed && item.file) {
      try {
        const res = await uploadApprovalPhoto(item.file)
        changes[item.key] = 'TMP:' + res.fileName
      } catch (e) {
        // 错误提示已由拦截器统一处理
        return false
      }
    }
  }
  if (!Object.keys(changes).length) {
    return false
  }
  try {
    await submitApprovalRequest({
      expertId: detail.value.id,
      changes,
      reason: '卡片内直接编辑提交'
    })
    ElMessage.success('已提交修改申请，可在「我的申请」查看进度')
    return true
  } catch (e) {
    // 错误提示已由拦截器统一处理
    return false
  }
}

// 关闭详情弹窗：管理员/超管免审批直写直接关闭（未保存修改不提示，与原行为一致）；
// 员工 dirty 时确认提交 / 放弃修改；未修改直接关闭
async function handleDetailClose(done) {
  if (canEditExpert.value) {
    detailVisible.value = false
    done()
    return
  }
  if (!dirty.value) {
    detailVisible.value = false
    done()
    return
  }
  let confirmed = false
  try {
    await ElMessageBox.confirm(
      '您已修改了专家信息，是否确认提交修改申请？确认后将生成审批流，审批通过后才生效。',
      '提交修改申请',
      { confirmButtonText: '确认提交', cancelButtonText: '放弃修改', type: 'warning' }
    )
    confirmed = true
  } catch (e) {
    // 用户选择放弃修改
  }
  if (confirmed) {
    const ok = await submitEditChange()
    if (ok) {
      detailVisible.value = false
      done()
      return
    }
    // 提交失败：保持弹窗打开，用户可调整后再次关闭提交
    return
  }
  // 放弃修改并关闭
  detailVisible.value = false
  done()
}

// 统一弹窗"提交申请"按钮（员工）：直接提交当前编辑为审批申请
async function handleDetailSubmit() {
  const ok = await submitEditChange()
  if (ok) {
    detailVisible.value = false
    fetchList()
  }
}

// 统一弹窗"取消"按钮：管理员直接关闭；员工走 before-close 相同的确认流程
function handleDetailCancel() {
  handleDetailClose(() => {})
}

// ==================== 管理员：新增 / 编辑 / 删除（免审批直写） ====================
const adminSubmitting = ref(false)
const adminDeleting = ref(false)
const adminPhotoPreview = reactive({ photo: '', front: '', back: '' })
const adminPickedFiles = reactive({ photo: null, front: null, back: null })

// 管理员新增弹窗
const adminCreateVisible = ref(false)
const adminCreateSubmitting = ref(false)
const adminCreateForm = reactive({
  姓名: '', 单位: '', 科室: '', 职称: '',
  联系方式: '', 简介: '', 身份证号: '', 银行卡号: '', 银行卡开户行: ''
})
const adminCreatePhotoPreview = reactive({ photo: '', front: '', back: '' })
const adminCreatePickedFiles = reactive({ photo: null, front: null, back: null })

// 管理员新增弹窗选照片：本地预览 + 暂存文件
function onAdminCreatePickPhoto(kind, file) {
  const raw = file?.raw || file
  if (!raw) {
    return
  }
  if (raw.size > uploadLimits.photoMaxBytes) {
    ElMessage.warning(`图片大小不能超过 ${toMb(uploadLimits.photoMaxBytes)}MB`)
    return
  }
  adminCreatePickedFiles[kind] = raw
  if (adminCreatePhotoPreview[kind]) {
    URL.revokeObjectURL(adminCreatePhotoPreview[kind])
  }
  adminCreatePhotoPreview[kind] = URL.createObjectURL(raw)
}

// 管理员编辑提交：FormData 中文字段 + photo/idCardFront/idCardBack，PUT /api/experts/{id} 直写
function buildAdminFormData() {
  const fd = new FormData()
  // 统一表单 editForm：管理员/超管在详情弹窗中的文本修改即写 editForm（打开时已回填全量）
  for (const k of TEXT_FIELDS) {
    fd.append(k, String(editForm[k] ?? '').trim())
  }
  if (adminPickedFiles.photo) fd.append('photo', adminPickedFiles.photo)
  if (adminPickedFiles.front) fd.append('idCardFront', adminPickedFiles.front)
  if (adminPickedFiles.back) fd.append('idCardBack', adminPickedFiles.back)
  return fd
}

async function submitAdminEdit() {
  if (!String(editForm.姓名 ?? '').trim()) {
    ElMessage.warning('请填写姓名')
    return
  }
  adminSubmitting.value = true
  try {
    await updateExpert(detail.value.id, buildAdminFormData())
    ElMessage.success('保存成功（免审批直改）')
    detailVisible.value = false
    fetchList()
  } catch (e) {
    // 错误提示已由拦截器统一处理
  } finally {
    adminSubmitting.value = false
  }
}

// 管理员删除：二次确认（提示照片一并物理删除）后 DELETE /api/experts/{id}
async function handleAdminDelete() {
  try {
    await ElMessageBox.confirm(
      `确定删除专家「${detail.value?.姓名 ?? ''}」吗？其照片文件将一并物理删除，此操作不可恢复。`,
      '删除确认',
      { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' }
    )
  } catch (e) {
    return
  }
  adminDeleting.value = true
  try {
    await deleteExpert(detail.value.id)
    ElMessage.success('删除成功')
    detailVisible.value = false
    fetchList()
  } catch (e) {
    // 错误提示已由拦截器统一处理
  } finally {
    adminDeleting.value = false
  }
}

// 打开管理员新增弹窗
function openAdminCreate() {
  for (const k of TEXT_FIELDS) {
    adminCreateForm[k] = ''
  }
  adminCreatePhotoPreview.photo = ''
  adminCreatePhotoPreview.front = ''
  adminCreatePhotoPreview.back = ''
  adminCreatePickedFiles.photo = null
  adminCreatePickedFiles.front = null
  adminCreatePickedFiles.back = null
  adminCreateVisible.value = true
}

// 管理员新增提交：FormData 中文字段 + 三类照片文件，POST /api/experts 直写
function buildAdminCreateFormData() {
  const fd = new FormData()
  for (const k of TEXT_FIELDS) {
    fd.append(k, String(adminCreateForm[k] ?? '').trim())
  }
  if (adminCreatePickedFiles.photo) fd.append('photo', adminCreatePickedFiles.photo)
  if (adminCreatePickedFiles.front) fd.append('idCardFront', adminCreatePickedFiles.front)
  if (adminCreatePickedFiles.back) fd.append('idCardBack', adminCreatePickedFiles.back)
  return fd
}

async function submitAdminCreate() {
  // Owner 2026-11-07：必填仅 姓名+单位
  const requiredFields = ['姓名', '单位']
  for (const k of requiredFields) {
    if (!String(adminCreateForm[k] ?? '').trim()) {
      ElMessage.warning(`请填写${k}`)
      return
    }
  }
  const name = String(adminCreateForm.姓名).trim()
  if (!/^[\u4e00-\u9fa5·]{2,20}$/.test(name)) {
    ElMessage.warning('姓名格式不正确（2-20 个汉字，可含间隔号）')
    return
  }
  const idCard = String(adminCreateForm.身份证号 ?? '').trim()
  if (idCard && !/^\d{17}[\dXx]$/.test(idCard)) {
    ElMessage.warning('身份证号格式不正确（18 位，末位可为 X）')
    return
  }
  const bankCard = String(adminCreateForm.银行卡号 ?? '').trim()
  if (bankCard && !/^\d{13,19}$/.test(bankCard)) {
    ElMessage.warning('银行卡号格式不正确（13-19 位数字）')
    return
  }
  const contact = String(adminCreateForm.联系方式 ?? '').trim()
  if (contact && !/^1\d{10}$|^[\w.-]+@[\w.-]+\.[a-zA-Z]{2,}$/.test(contact)) {
    ElMessage.warning('联系方式格式不正确（11 位手机号或邮箱）')
    return
  }
  adminCreateSubmitting.value = true
  try {
    await createExpert(buildAdminCreateFormData())
    ElMessage.success('新增成功（免审批直写）')
    adminCreateVisible.value = false
    fetchList()
  } catch (e) {
    // 错误提示已由拦截器统一处理
  } finally {
    adminCreateSubmitting.value = false
  }
}

onMounted(() => {
  fetchList()
  // 日程解析「库外专家去建档」预填（2026-11-09 项目流程重塑）：query 带 presetName 时自动打开管理员新增并预填姓名
  const presetName = route.query.presetName
  if (typeof presetName === 'string' && presetName.trim()) {
    openAdminCreate()
    adminCreateForm['姓名'] = presetName.trim()
  }
})
watch(() => route.query.expertId, async id => {
  if(id===undefined)return
  if(!/^[1-9]\d{0,17}$/.test(String(id)))return ElMessage.warning('记录编号无效')
  try{await openDetail({id:String(id)})}catch{ElMessage.warning('记录不可用或无权访问')}
},{immediate:true})
</script>

<style scoped>
.form-tip {
  font-size: 12px;
  color: var(--ui-text-muted);
  line-height: 1.4;
  margin-top: 4px;
}
/* 简历解析区块（新增专家弹窗顶部） */
/* 共用灰底圆角块（文件内三处重复收口） */
.resume-parse,
.advanced-panel,
.selected-bar {
  background: #f5f7fa;
  border: 1px solid #e4e7ed;
  border-radius: 8px;
}
.resume-parse {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
  padding: 10px 14px;
  margin-bottom: 14px;
}
.resume-tip {
  font-size: 12px;
  color: var(--ui-text-sub);
  flex: 1;
  min-width: 200px;
}
.resume-tip-error {
  color: #dc2626;
}
.resume-candidates {
  width: 100%;
  display: flex;
  gap: 12px;
  flex-wrap: wrap;
}
.resume-candidate {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 6px 10px;
  background: #fff;
  border: 1px solid #dcdfe6;
  border-radius: 8px;
}
.resume-mark {
  display: inline-block;
  margin-left: 4px;
  font-size: 10px;
  font-weight: 400;
  color: var(--ui-success);
}
/* 第一层：搜索筛选区 */
.filter-bar {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}
.search-input {
  width: 480px;
}
.filter-badge {
  margin-left: 6px;
}
.advanced-panel {
  display: flex;
  align-items: center;
  gap: 14px;
  flex-wrap: wrap;
  margin-top: 12px;
  padding: 14px 16px;
}
.filter-field {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}
.filter-field label {
  font-size: 12px;
  color: var(--ui-text-sub);
  white-space: nowrap;
}
/* 第二层：工具行 */
.tool-row {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-top: 14px;
}
.total-label {
  font-size: 13px;
  color: var(--ui-text-sub);
}
.view-toggle-label {
  font-size: 12px;
  color: var(--ui-text-muted);
}
.view-btn {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 5px 10px;
  border: 1px solid #dcdfe6;
  background: #fff;
  border-radius: 6px;
  cursor: pointer;
  color: var(--ui-text-sub);
  font-size: 12px;
}
.view-btn .el-icon {
  font-size: 14px;
}
.view-btn.active {
  color: var(--ui-primary);
  border-color: var(--ui-primary);
  background: #ecf5ff;
  font-weight: 600;
}
/* 悬浮批量操作栏 */
.batch-bar {
  position: fixed;
  bottom: 24px;
  left: 50%;
  transform: translateX(-50%);
  z-index: 200;
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 18px;
  background: rgba(255, 255, 255, 0.98);
  border: 1px solid #e4e7ed;
  border-radius: 10px;
  box-shadow: 0 6px 24px rgba(0, 21, 41, 0.18);
}
.batch-label {
  font-size: 13px;
  color: var(--ui-text-main);
  white-space: nowrap;
}
.pagination-wrap {
  margin-top: 45px;
  align-items: center;
  padding: 10px 16px;
  background: rgba(255, 255, 255, 0.95);
  backdrop-filter: blur(4px);
  border-top: 1px solid #e4e7ed;
  box-shadow: 0 -2px 8px rgba(0, 21, 41, 0.06);
}
.expert-cards {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  grid-auto-rows: auto;
  column-gap: 18px;
  row-gap: 16px;
}
.expert-card {
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 14px 16px;
  text-align: left;
  cursor: pointer;
  transition: box-shadow 0.2s ease;
  position: relative;
  overflow: hidden;
}
/* 详情弹窗字段复制图标 */
.copy-icon {
  font-size: 16px;
  color: var(--ui-text-muted);
  cursor: pointer;
  transition: color 0.2s;
}
.copy-icon:hover {
  color: var(--ui-primary);
}
.textarea-copy-wrap {
  position: relative;
  flex: 1;
}
.textarea-copy {
  position: absolute;
  right: 8px;
  top: 8px;
  z-index: 1;
}
.selected-bar {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px 10px;
  margin-top: 16px;
  padding: 12px 16px;
}
.selected-label {
  font-size: 14px;
  color: var(--ui-text-sub);
  white-space: nowrap;
}
.expert-card.selected {
  border-color: var(--ui-primary);
  background: #ecf5ff;
  box-shadow: 0 0 0 2px rgba(64, 158, 255, 0.35);
}
.card-check {
  position: absolute;
  top: 10px;
  right: 10px;
  z-index: 1;
}
.card-meta {
  width: 100%;
  margin-top: 2px;
  padding-top: 6px;
  border-top: 1px solid #ebeef5;
  display: flex;
  justify-content: flex-start;
  align-items: center;
  flex-wrap: nowrap;
  gap: 8px;
  font-size: 11px;
  color: var(--ui-text-muted);
  line-height: 1.4;
  white-space: nowrap;
}
.card-meta-item {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  min-width: 0;
}
.meta-icon {
  flex-shrink: 0;
}
.card-apply-btn {
  position: absolute;
  right: 10px;
  bottom: 6px;
  z-index: 1;
  font-size: 13px;
}
.expert-card:hover {
  box-shadow: 0 2px 10px rgba(0, 21, 41, 0.1);
}
/* 紧凑卡片：头像 72px 左置，姓名/单位/科室右侧一屏可读，整卡约 160~180px */
.card-main {
  display: flex;
  align-items: center;
  gap: 10px;
  min-width: 0;
}
.card-photo {
  width: 72px;
  height: 72px;
  flex-shrink: 0;
  border-radius: 50%;
  overflow: hidden;
  border: 2px solid #e4e7ed;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #f5f7fa;
}
.card-info {
  flex: 1;
  min-width: 0;
}
.card-name {
  min-width: 0;
  max-width: 100%;
  font-size: 16px;
  font-weight: 600;
  color: var(--ui-text-main);
  margin-bottom: 4px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.card-text {
  min-width: 0;
  max-width: 100%;
  font-size: 12px;
  color: var(--ui-text-sub);
  line-height: 1.55;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.masked-photo {
  position: relative;
  display: inline-block;
}
.masked-img {
  display: block;
  width: 128px;
  height: 80px;
  object-fit: cover;
  border-radius: 4px;
  filter: blur(10px);
  cursor: default;
}
.masked-tip {
  position: absolute;
  left: 0;
  right: 0;
  top: 50%;
  transform: translateY(-50%);
  text-align: center;
  color: var(--ui-text-muted);
  font-size: 12px;
  background: rgba(255, 255, 255, 0.7);
  padding: 2px 0;
}
.photo-empty {
  color: #a8abb2;
  font-size: 12px;
}
.edit-form {
  max-height: 62vh;
  overflow-y: auto;
  padding-right: 4px;
}
.edit-photos {
  display: flex;
  gap: 16px;
  margin-bottom: 18px;
}
.edit-photo-item {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  padding: 12px 8px;
  border: 1px dashed #dcdfe6;
  border-radius: 8px;
}
.edit-photo-label {
  font-size: 13px;
  color: var(--ui-text-sub);
  margin-bottom: 2px;
}
.edit-photo-box {
  width: 120px;
  height: 84px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 4px;
  overflow: hidden;
  background: #f5f7fa;
}
.edit-photo-box.round {
  width: 80px;
  height: 80px;
  border-radius: 50%;
}
</style>
