<template>
  <div>
    <div class="toolbar">
      <el-input
        v-model="keyword"
        placeholder="按姓名 / 单位 / 科室 / 职称 / 研究方向 / 擅长领域检索"
        clearable
        style="width: 360px"
        @keyup.enter="handleSearch"
      >
        <template #append>
          <el-button @click="handleSearch">搜索</el-button>
        </template>
      </el-input>
      <!-- 外部链接：卫健委机构查询平台（新窗口打开，仅前端跳转，不依赖后端） -->
      <el-button
        tag="a"
        href="https://zgcx.nhc.gov.cn/unit"
        target="_blank"
        rel="noopener"
        type="primary"
        plain
        style="margin-left: 12px"
      >
        专家单位查询
      </el-button>
      <div class="spacer" />
      <template v-if="canAddExpert">
        <el-button type="primary" @click="openAdminCreate">新增专家</el-button>
      </template>
      <template v-else>
        <el-button type="primary" @click="openCreateDialog">新增专家</el-button>
      </template>
      <template v-if="canExport">
        <el-button type="success" @click="handleExportExcel">下载全量 Excel</el-button>
        <el-button type="warning" @click="handleExportZip">下载含照片 ZIP</el-button>
      </template>
      <template v-else>
        <el-button type="primary" :disabled="!selectedIds.size" @click="handleExportSelectedExcel">
          下载选中 Excel{{ selectedIds.size ? `（${selectedIds.size}）` : '' }}
        </el-button>
        <el-button type="info" :disabled="!selectedIds.size" @click="handleExportSelectedZip">
          下载选中含照片 ZIP{{ selectedIds.size ? `（${selectedIds.size}）` : '' }}
        </el-button>
      </template>
    </div>

    <!-- 首页公告卡片栏：最多5条，置顶优先 + 发布时间倒序；无公告时不渲染 -->
    <div v-if="announcements.length" class="announcement-banner">
      <el-card
        v-for="a in announcements"
        :key="a.id"
        shadow="hover"
        class="ann-card"
        @click="goAnnouncement(a.id)"
      >
        <div class="ann-card-main">
          <el-tag v-if="a.isTop === 1" type="danger" size="small">置顶</el-tag>
          <span class="ann-card-title">{{ a.title }}</span>
          <span v-if="a.read === false" class="ann-card-dot" title="未读"></span>
        </div>
        <div class="ann-card-meta">{{ formatAnnTime(a.createTime) }}</div>
      </el-card>
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

    <div v-loading="loading" class="expert-cards" style="margin-top: 16px; min-height: 120px">
      <div
        v-for="expert in experts"
        :key="expert.id"
        class="expert-card"
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
        <div class="card-photo">
          <PhotoImage :filename="expert.照片" :width="128" :height="128" />
        </div>
        <div class="card-name">{{ expert.姓名 }}</div>
        <div class="card-text">{{ expert.单位 }}</div>
        <div class="card-text">{{ expert.科室 }}</div>
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

    <!-- 专家详情（有编辑权限：可编辑表单 + 删除，免审批直写） -->
    <el-dialog v-if="canEditExpert" v-model="detailVisible" title="编辑专家（免审批直改）" width="760px" destroy-on-close>
      <el-form v-if="detail" :model="adminForm" label-width="110px" class="edit-form">
        <div class="edit-photos">
          <div class="edit-photo-item">
            <div class="edit-photo-label">证件照</div>
            <div class="edit-photo-box round">
              <img v-if="adminPhotoPreview.photo" :src="adminPhotoPreview.photo" class="edit-photo-img" alt="证件照预览" />
              <PhotoImage v-else :filename="detail.照片" :width="80" :height="80" />
            </div>
            <el-upload
              :auto-upload="false"
              :show-file-list="false"
              accept="image/*"
              :on-change="(f) => onAdminPickPhoto('photo', f)"
            >
              <el-button size="small" type="primary" plain>更换照片</el-button>
            </el-upload>
          </div>
          <div class="edit-photo-item">
            <div class="edit-photo-label">身份证正面</div>
            <div class="edit-photo-box">
              <img v-if="adminPhotoPreview.front" :src="adminPhotoPreview.front" class="edit-photo-img" alt="身份证正面预览" />
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
              :on-change="(f) => onAdminPickPhoto('front', f)"
            >
              <el-button size="small" type="primary" plain>更换照片</el-button>
            </el-upload>
          </div>
          <div class="edit-photo-item">
            <div class="edit-photo-label">身份证反面</div>
            <div class="edit-photo-box">
              <img v-if="adminPhotoPreview.back" :src="adminPhotoPreview.back" class="edit-photo-img" alt="身份证反面预览" />
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
              :on-change="(f) => onAdminPickPhoto('back', f)"
            >
              <el-button size="small" type="primary" plain>更换照片</el-button>
            </el-upload>
          </div>
        </div>
        <el-form-item label="姓名">
          <el-input v-model="adminForm.姓名" placeholder="请输入姓名" />
        </el-form-item>
        <el-form-item label="单位">
          <el-input v-model="adminForm.单位" placeholder="请输入单位" />
        </el-form-item>
        <el-form-item label="科室">
          <el-input v-model="adminForm.科室" placeholder="请输入科室" />
        </el-form-item>
        <el-form-item label="职称">
          <el-input v-model="adminForm.职称" placeholder="请输入职称" />
        </el-form-item>
        <el-form-item label="联系方式">
          <el-input v-model="adminForm.联系方式" placeholder="请输入联系方式" />
        </el-form-item>
        <el-form-item label="研究方向">
          <el-input v-model="adminForm.研究方向" placeholder="请输入研究方向" />
        </el-form-item>
        <el-form-item label="擅长领域">
          <el-input v-model="adminForm.擅长领域" placeholder="请输入擅长领域" />
        </el-form-item>
        <el-form-item label="简介">
          <el-input v-model="adminForm.简介" type="textarea" :rows="2" placeholder="请输入简介" />
        </el-form-item>
        <el-form-item label="身份证号">
          <el-input v-model="adminForm.身份证号" placeholder="请输入身份证号（18 位）" />
        </el-form-item>
        <el-form-item label="银行卡号">
          <el-input v-model="adminForm.银行卡号" placeholder="请输入银行卡号" />
        </el-form-item>
        <el-form-item label="银行卡开户行">
          <el-input v-model="adminForm.银行卡开户行" placeholder="请输入开户行" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button v-permission="'expert:delete'" type="danger" :loading="adminDeleting" @click="handleAdminDelete">删除专家</el-button>
        <el-button @click="detailVisible = false">取消</el-button>
        <el-button type="primary" :loading="adminSubmitting" @click="submitAdminEdit">保存</el-button>
      </template>
    </el-dialog>

    <!-- 专家详情（员工：卡片内直接编辑，关闭时检测变更并引导提交审批） -->
    <el-dialog
      v-else
      v-model="detailVisible"
      title="专家详情（可编辑）"
      width="760px"
      destroy-on-close
      :before-close="handleDetailClose"
    >
      <el-form v-if="detail" :model="editForm" label-width="110px" class="edit-form">
        <el-form-item label="申请模板">
          <el-select
            v-model="editTemplateId"
            clearable
            placeholder="不使用模板（展示全部字段）"
            style="width: 100%"
          >
            <el-option
              v-for="t in templates"
              :key="t.id"
              :label="`${t.templateName}（${(t.fields || []).length} 个字段）`"
              :value="t.id"
            />
          </el-select>
          <div class="form-tip">选择常用申请模板后，下方仅展示模板内字段，提交内容也仅限模板字段</div>
        </el-form-item>
        <div class="edit-photos">
          <div v-if="editFieldVisible('照片')" class="edit-photo-item">
            <div class="edit-photo-label">证件照</div>
            <div class="edit-photo-box round">
              <img v-if="photoPreview.photo" :src="photoPreview.photo" class="edit-photo-img" alt="证件照预览" />
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
          <div v-if="editFieldVisible('身份证正面')" class="edit-photo-item">
            <div class="edit-photo-label">身份证正面</div>
            <div class="edit-photo-box">
              <img v-if="photoPreview.front" :src="photoPreview.front" class="edit-photo-img" alt="身份证正面预览" />
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
          <div v-if="editFieldVisible('身份证反面')" class="edit-photo-item">
            <div class="edit-photo-label">身份证反面</div>
            <div class="edit-photo-box">
              <img v-if="photoPreview.back" :src="photoPreview.back" class="edit-photo-img" alt="身份证反面预览" />
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
        <el-form-item v-if="editFieldVisible('姓名')" label="姓名">
          <el-input v-model="editForm.姓名" placeholder="请输入姓名" />
        </el-form-item>
        <el-form-item v-if="editFieldVisible('单位')" label="单位">
          <el-input v-model="editForm.单位" placeholder="请输入单位" />
        </el-form-item>
        <el-form-item v-if="editFieldVisible('科室')" label="科室">
          <el-input v-model="editForm.科室" placeholder="请输入科室" />
        </el-form-item>
        <el-form-item v-if="editFieldVisible('职称')" label="职称">
          <el-input v-model="editForm.职称" placeholder="请输入职称" />
        </el-form-item>
        <el-form-item v-if="editFieldVisible('联系方式')" label="联系方式">
          <el-input v-model="editForm.联系方式" placeholder="请输入联系方式" />
        </el-form-item>
        <el-form-item v-if="editFieldVisible('研究方向')" label="研究方向">
          <el-input v-model="editForm.研究方向" placeholder="请输入研究方向" />
        </el-form-item>
        <el-form-item v-if="editFieldVisible('擅长领域')" label="擅长领域">
          <el-input v-model="editForm.擅长领域" placeholder="请输入擅长领域" />
        </el-form-item>
        <el-form-item v-if="editFieldVisible('简介')" label="简介">
          <el-input v-model="editForm.简介" type="textarea" :rows="2" placeholder="请输入简介" />
        </el-form-item>
        <el-form-item v-if="editFieldVisible('身份证号')" label="身份证号">
          <el-input v-model="editForm.身份证号" placeholder="身份证号已脱敏，如需修改请填写完整" />
        </el-form-item>
        <el-form-item v-if="editFieldVisible('银行卡号')" label="银行卡号">
          <el-input v-model="editForm.银行卡号" placeholder="银行卡号已脱敏，如需修改请填写完整" />
        </el-form-item>
        <el-form-item v-if="editFieldVisible('银行卡开户行')" label="银行卡开户行">
          <el-input v-model="editForm.银行卡开户行" placeholder="请输入开户行" />
        </el-form-item>
        <el-alert
          type="info"
          :closable="false"
          show-icon
          title="修改将在审批通过后生效，关闭弹窗时如检测到修改会引导您提交申请"
        />
      </el-form>
    </el-dialog>

    <!-- 新增专家申请弹窗（员工：复用可编辑表单字段结构与照片选择，提交审批流） -->
    <el-dialog v-model="createVisible" title="新增专家（需审批）" width="760px" destroy-on-close>
      <el-form :model="createForm" label-width="110px" class="edit-form">
        <el-form-item label="申请模板">
          <el-select
            v-model="createTemplateId"
            clearable
            placeholder="不使用模板（展示全部字段）"
            style="width: 100%"
          >
            <el-option
              v-for="t in createTemplateOptions"
              :key="t.id"
              :label="`${t.templateName}（${(t.fields || []).length} 个字段）`"
              :value="t.id"
            />
          </el-select>
          <div class="form-tip">新增申请的模板需覆盖全部必填项（姓名、单位、科室、职称、研究方向、擅长领域、联系方式），未覆盖的模板不在下拉中</div>
        </el-form-item>
        <div class="edit-photos">
          <div v-if="createFieldVisible('照片')" class="edit-photo-item">
            <div class="edit-photo-label">证件照</div>
            <div class="edit-photo-box round">
              <img v-if="createPhotoPreview.photo" :src="createPhotoPreview.photo" class="edit-photo-img" alt="证件照预览" />
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
          <div v-if="createFieldVisible('身份证正面')" class="edit-photo-item">
            <div class="edit-photo-label">身份证正面</div>
            <div class="edit-photo-box">
              <img v-if="createPhotoPreview.front" :src="createPhotoPreview.front" class="edit-photo-img" alt="身份证正面预览" />
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
          <div v-if="createFieldVisible('身份证反面')" class="edit-photo-item">
            <div class="edit-photo-label">身份证反面</div>
            <div class="edit-photo-box">
              <img v-if="createPhotoPreview.back" :src="createPhotoPreview.back" class="edit-photo-img" alt="身份证反面预览" />
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
        <el-form-item v-if="createFieldVisible('姓名')" label="姓名">
          <el-input v-model="createForm.姓名" placeholder="请输入姓名（2-20 个汉字）" />
        </el-form-item>
        <el-form-item v-if="createFieldVisible('单位')" label="单位">
          <el-input v-model="createForm.单位" placeholder="请输入单位" />
        </el-form-item>
        <el-form-item v-if="createFieldVisible('科室')" label="科室">
          <el-input v-model="createForm.科室" placeholder="请输入科室" />
        </el-form-item>
        <el-form-item v-if="createFieldVisible('职称')" label="职称">
          <el-input v-model="createForm.职称" placeholder="请输入职称" />
        </el-form-item>
        <el-form-item v-if="createFieldVisible('联系方式')" label="联系方式">
          <el-input v-model="createForm.联系方式" placeholder="请输入联系方式" />
        </el-form-item>
        <el-form-item v-if="createFieldVisible('研究方向')" label="研究方向">
          <el-input v-model="createForm.研究方向" placeholder="请输入研究方向" />
        </el-form-item>
        <el-form-item v-if="createFieldVisible('擅长领域')" label="擅长领域">
          <el-input v-model="createForm.擅长领域" placeholder="请输入擅长领域" />
        </el-form-item>
        <el-form-item v-if="createFieldVisible('简介')" label="简介">
          <el-input v-model="createForm.简介" type="textarea" :rows="2" placeholder="请输入简介" />
        </el-form-item>
        <el-form-item v-if="createFieldVisible('身份证号')" label="身份证号">
          <el-input v-model="createForm.身份证号" placeholder="请输入身份证号（18 位）" />
        </el-form-item>
        <el-form-item v-if="createFieldVisible('银行卡号')" label="银行卡号">
          <el-input v-model="createForm.银行卡号" placeholder="请输入银行卡号" />
        </el-form-item>
        <el-form-item v-if="createFieldVisible('银行卡开户行')" label="银行卡开户行">
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
              <img v-if="adminCreatePhotoPreview.photo" :src="adminCreatePhotoPreview.photo" class="edit-photo-img" alt="证件照预览" />
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
              <img v-if="adminCreatePhotoPreview.front" :src="adminCreatePhotoPreview.front" class="edit-photo-img" alt="身份证正面预览" />
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
              <img v-if="adminCreatePhotoPreview.back" :src="adminCreatePhotoPreview.back" class="edit-photo-img" alt="身份证反面预览" />
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
        <el-form-item label="研究方向">
          <el-input v-model="adminCreateForm.研究方向" placeholder="请输入研究方向" />
        </el-form-item>
        <el-form-item label="擅长领域">
          <el-input v-model="adminCreateForm.擅长领域" placeholder="请输入擅长领域" />
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
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { listExperts, getExpert, createExpert, updateExpert, deleteExpert, exportAllExcel, exportEmployeeZip, exportSelectedExcel, exportSelectedZip, fetchPhotoBlob } from '../api/expert'
import { submitApprovalRequest, uploadApprovalPhoto, createExpertApprovalRequest, getApprovalTemplates, getApprovalFieldCatalog } from '../api/approval'
import { useAuthStore } from '../store/auth'
import PhotoImage from '../components/PhotoImage.vue'

const authStore = useAuthStore()
const isAdmin = computed(() => authStore.isAdmin)
const canAddExpert = computed(() => authStore.hasPermission('expert:add'))
const canEditExpert = computed(() => authStore.hasPermission('expert:edit'))
const canExport = computed(() => authStore.hasPermission('expert:export'))
const selectedIds = ref(new Set())
// 已选专家姓名（{id, 姓名}），跨搜索/翻页保留，用于标签展示与逐项取消
const selectedExperts = ref([])

const keyword = ref('')
const experts = ref([])
const total = ref(0)
const page = ref(1)
const size = ref(20)
const loading = ref(false)

const detailVisible = ref(false)
const detail = ref(null)
// 身份证正反面照片 blob URL（仅脱敏模糊展示，不提供原图预览）
const idFrontUrl = ref('')
const idBackUrl = ref('')

// 卡片内直接编辑（需求3：员工视角详情弹窗可编辑，关闭时检测变更生成审批流）
const TEXT_FIELDS = ['姓名', '单位', '科室', '职称', '研究方向', '擅长领域', '联系方式', '简介', '身份证号', '银行卡号', '银行卡开户行']
const editForm = reactive({
  姓名: '',
  单位: '',
  科室: '',
  职称: '',
  研究方向: '',
  擅长领域: '',
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

// ==================== 申请模板（R-B4：选择模板后表单仅展示模板字段） ====================
const templates = ref([])            // 启用模板列表
const createRequiredFields = ref([]) // 新增专家必填字段（后端下发，用于过滤新增弹窗可选模板）
const editTemplateId = ref(null)     // 修改申请弹窗所选模板（null=不使用模板，展示全部字段）
const createTemplateId = ref(null)   // 新增专家申请弹窗所选模板
const PHOTO_KEY_MAP = { photo: '照片', front: '身份证正面', back: '身份证反面' }

function templateOf(id) {
  return templates.value.find((t) => t.id === id) || null
}

// 修改弹窗字段可见性：选了模板 = 仅模板字段；未选 = 全部
function editFieldVisible(key) {
  const t = templateOf(editTemplateId.value)
  return t ? (t.fields || []).includes(key) : true
}

// 新增弹窗仅提供覆盖必填项的模板，避免表单缺必填字段被后端拦截
const createTemplateOptions = computed(() =>
  templates.value.filter((t) =>
    createRequiredFields.value.every((f) => (t.fields || []).includes(f))
  )
)

function createFieldVisible(key) {
  const t = templateOf(createTemplateId.value)
  return t ? (t.fields || []).includes(key) : true
}

// 员工登录后加载启用模板与字段清单（管理员走免审批通道，无需模板）
async function fetchTemplates() {
  if (authStore.hasPermission('expert:edit')) {
    return
  }
  try {
    const [list, catalog] = await Promise.all([getApprovalTemplates(), getApprovalFieldCatalog()])
    templates.value = Array.isArray(list) ? list : []
    createRequiredFields.value = catalog?.createRequiredFields || []
  } catch (e) {
    templates.value = []
    // 模板加载失败不阻断原提交流程（等同未配置模板）
  }
}

// 是否有未提交的修改：任一【当前模板可见】文本字段变化或任一可见照片被更换
// （选模板后隐藏的字段不参与 dirty 判定，避免切换模板后被隐藏的编辑导致误判）
const dirty = computed(() => {
  if (!editSnapshot.value) return false
  for (const k of TEXT_FIELDS) {
    if (!editFieldVisible(k)) {
      continue
    }
    if (String(editForm[k] ?? '').trim() !== String(editSnapshot.value[k] ?? '').trim()) {
      return true
    }
  }
  return (
    (editFieldVisible('照片') && photoChanged.photo) ||
    (editFieldVisible('身份证正面') && photoChanged.front) ||
    (editFieldVisible('身份证反面') && photoChanged.back)
  )
})

async function fetchList() {
  loading.value = true
  try {
    const data = await listExperts({
      keyword: keyword.value || undefined,
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

async function openDetail(row) {
  const data = await getExpert(row.id, isAdmin.value ? { sensitive: 1 } : undefined)
  detail.value = data
  // 管理员可编辑视角：填充全量表单并清空照片选择
  if (isAdmin.value) {
    for (const k of TEXT_FIELDS) {
      adminForm[k] = data?.[k] ?? ''
    }
    adminPhotoPreview.photo = ''
    adminPhotoPreview.front = ''
    adminPhotoPreview.back = ''
    adminPickedFiles.photo = null
    adminPickedFiles.front = null
    adminPickedFiles.back = null
  }
  // 员工可编辑视角：初始化表单与初始快照
  if (!isAdmin.value) {
    editTemplateId.value = null
    for (const k of TEXT_FIELDS) {
      editForm[k] = data?.[k] ?? ''
    }
    editSnapshot.value = {}
    for (const k of TEXT_FIELDS) {
      editSnapshot.value[k] = data?.[k] ?? ''
    }
    photoChanged.photo = false
    photoChanged.front = false
    photoChanged.back = false
    photoPreview.photo = ''
    photoPreview.front = ''
    photoPreview.back = ''
    pickedFiles.photo = null
    pickedFiles.front = null
    pickedFiles.back = null
  }
  // 加载身份证正反面照片（仅用于脱敏模糊展示，不提供原图预览）
  idFrontUrl.value = ''
  idBackUrl.value = ''
  if (data['身份证正面']) {
    try {
      const resp = await fetchPhotoBlob(data['身份证正面'])
      idFrontUrl.value = URL.createObjectURL(resp.data)
    } catch (e) {
      // 照片不存在或无权限：保持占位
    }
  }
  if (data['身份证反面']) {
    try {
      const resp = await fetchPhotoBlob(data['身份证反面'])
      idBackUrl.value = URL.createObjectURL(resp.data)
    } catch (e) {
      // 照片不存在或无权限：保持占位
    }
  }
  detailVisible.value = true
}

// 手机号脱敏：中间 4 位打码（138****1234）；邮箱等非手机号原样返回；已含 * 的值直接返回
function maskPhone(value) {
  if (!value) return ''
  const s = String(value).trim()
  if (s.includes('*')) return s
  if (/^1\d{10}$/.test(s)) {
    return s.slice(0, 3) + '****' + s.slice(7)
  }
  return s
}

// 敏感号兜底脱敏：前 3 后 4（普通员工接口后端已脱敏，此处仅防意外返回全量）
function maskSensitive(value) {
  if (!value) return ''
  const s = String(value).trim()
  if (s.includes('*')) return s
  if (s.length <= 7) return s
  return s.slice(0, 3) + '****' + s.slice(-4)
}

function handleExportExcel() {
  exportAllExcel().catch(() => {})
}

function handleExportZip() {
  exportEmployeeZip().catch(() => {})
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

// 选择新照片：本地预览 + 标记变更 + 暂存文件（提交审批时再上传临时接口）
function onPickPhoto(kind, file) {
  const raw = file?.raw || file
  if (!raw) {
    return
  }
  if (raw.size > 5 * 1024 * 1024) {
    ElMessage.warning('图片大小不能超过 5MB')
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
  研究方向: '',
  擅长领域: '',
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

// 打开新增专家弹窗：表单与照片均初始为空
function openCreateDialog() {
  createTemplateId.value = null
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
  createVisible.value = true
}

// 新增弹窗选照片：本地预览 + 暂存文件（提交时再上传临时接口）
function onCreatePickPhoto(kind, file) {
  const raw = file?.raw || file
  if (!raw) {
    return
  }
  if (raw.size > 5 * 1024 * 1024) {
    ElMessage.warning('图片大小不能超过 5MB')
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
  const requiredFields = ['姓名', '单位', '科室', '职称', '研究方向', '擅长领域', '联系方式']
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
    if (!createFieldVisible(k)) {
      continue
    }
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
    if (item.changed && item.file && createFieldVisible(item.key)) {
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
      reason: '员工新增专家',
      templateId: createTemplateId.value || undefined
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
    if (!editFieldVisible(k)) {
      continue
    }
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
    if (item.changed && item.file && editFieldVisible(item.key)) {
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
      reason: '卡片内直接编辑提交',
      templateId: editTemplateId.value || undefined
    })
    ElMessage.success('已提交修改申请，可在「我的申请」查看进度')
    return true
  } catch (e) {
    // 错误提示已由拦截器统一处理
    return false
  }
}

// 关闭详情弹窗：dirty 时确认提交 / 放弃修改；未修改直接关闭
async function handleDetailClose(done) {
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

// ==================== 管理员：新增 / 编辑 / 删除（免审批直写） ====================
const adminSubmitting = ref(false)
const adminDeleting = ref(false)
// 管理员编辑表单（全量，来自 sensitive=1 详情）
const adminForm = reactive({
  姓名: '', 单位: '', 科室: '', 职称: '', 研究方向: '', 擅长领域: '',
  联系方式: '', 简介: '', 身份证号: '', 银行卡号: '', 银行卡开户行: ''
})
const adminPhotoPreview = reactive({ photo: '', front: '', back: '' })
const adminPickedFiles = reactive({ photo: null, front: null, back: null })

// 管理员新增弹窗
const adminCreateVisible = ref(false)
const adminCreateSubmitting = ref(false)
const adminCreateForm = reactive({
  姓名: '', 单位: '', 科室: '', 职称: '', 研究方向: '', 擅长领域: '',
  联系方式: '', 简介: '', 身份证号: '', 银行卡号: '', 银行卡开户行: ''
})
const adminCreatePhotoPreview = reactive({ photo: '', front: '', back: '' })
const adminCreatePickedFiles = reactive({ photo: null, front: null, back: null })

// 管理员编辑弹窗选照片：本地预览 + 暂存文件（保存时随 FormData 直传）
function onAdminPickPhoto(kind, file) {
  const raw = file?.raw || file
  if (!raw) {
    return
  }
  if (raw.size > 5 * 1024 * 1024) {
    ElMessage.warning('图片大小不能超过 5MB')
    return
  }
  adminPickedFiles[kind] = raw
  if (adminPhotoPreview[kind]) {
    URL.revokeObjectURL(adminPhotoPreview[kind])
  }
  adminPhotoPreview[kind] = URL.createObjectURL(raw)
}

// 管理员新增弹窗选照片：本地预览 + 暂存文件
function onAdminCreatePickPhoto(kind, file) {
  const raw = file?.raw || file
  if (!raw) {
    return
  }
  if (raw.size > 5 * 1024 * 1024) {
    ElMessage.warning('图片大小不能超过 5MB')
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
  for (const k of TEXT_FIELDS) {
    fd.append(k, String(adminForm[k] ?? '').trim())
  }
  if (adminPickedFiles.photo) fd.append('photo', adminPickedFiles.photo)
  if (adminPickedFiles.front) fd.append('idCardFront', adminPickedFiles.front)
  if (adminPickedFiles.back) fd.append('idCardBack', adminPickedFiles.back)
  return fd
}

async function submitAdminEdit() {
  if (!String(adminForm.姓名 ?? '').trim()) {
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
  const requiredFields = ['姓名', '单位', '科室', '职称', '研究方向', '擅长领域', '联系方式']
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
  fetchTemplates()
  fetchAnnouncements()
})

// ==================== 首页公告卡片栏 ====================
const announcements = ref([])

function formatAnnTime(t) {
  if (!t) return ''
  return String(t).replace('T', ' ').slice(0, 16)
}

async function fetchAnnouncements() {
  try {
    announcements.value = (await getEffectiveAnnouncements(5)) || []
  } catch (e) {
    // 公告栏失败不影响首页：不渲染即可
    announcements.value = []
  }
}

function goAnnouncement(id) {
  router.push(`/announcements/${id}`)
}
</script>

<style scoped>
.form-tip {
  font-size: 12px;
  color: #909399;
  line-height: 1.4;
  margin-top: 4px;
}
.toolbar {
  display: flex;
  align-items: center;
}
.spacer {
  flex: 1;
}
.pagination-wrap {
  margin-top: 45px;
  display: flex;
  justify-content: flex-end;
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
  column-gap: 20px;
  row-gap: 45px;
}
.expert-card {
  min-width: 0;
  height: 100%;
  display: flex;
  flex-direction: column;
  align-items: center;
  background: #fff;
  border: 1px solid #ebeef5;
  border-radius: 10px;
  padding: 26px 16px 20px;
  text-align: center;
  cursor: pointer;
  transition: box-shadow 0.2s ease, transform 0.2s ease;
  position: relative;
  overflow: hidden;
}
/* 首页公告卡片栏 */
.announcement-banner {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  margin-top: 12px;
}
.ann-card {
  width: 260px;
  cursor: pointer;
  margin: 0;
}
.ann-card :deep(.el-card__body) {
  padding: 12px 14px;
}
.ann-card-main {
  display: flex;
  align-items: center;
  min-height: 22px;
}
.ann-card-title {
  margin-left: 6px;
  font-size: 14px;
  font-weight: 600;
  color: #303133;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  flex: 1;
}
.ann-card-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: #f56c6c;
  flex-shrink: 0;
  margin-left: 6px;
}
.ann-card-meta {
  margin-top: 6px;
  color: #909399;
  font-size: 12px;
}
.selected-bar {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px 10px;
  margin-top: 16px;
  padding: 12px 16px;
  background: #f5f7fa;
  border: 1px solid #e4e7ed;
  border-radius: 8px;
}
.selected-label {
  font-size: 14px;
  color: #606266;
  white-space: nowrap;
}
.expert-card.selected {
  border-color: #409eff;
  background: #ecf5ff;
  box-shadow: 0 0 0 2px rgba(64, 158, 255, 0.35);
}
.card-check {
  position: absolute;
  top: 10px;
  left: 10px;
  z-index: 1;
}
.card-meta {
  width: 100%;
  margin-top: auto;
  padding-top: 10px;
  border-top: 1px solid #ebeef5;
  display: flex;
  justify-content: center;
  align-items: center;
  flex-wrap: nowrap;
  gap: 10px;
  font-size: 14px;
  color: #909399;
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
  box-shadow: 0 6px 18px rgba(0, 21, 41, 0.16);
  transform: translateY(-3px);
}
.card-photo {
  width: 128px;
  height: 128px;
  margin: 0 auto 14px;
  border-radius: 50%;
  overflow: hidden;
  border: 2px solid #e4e7ed;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #f5f7fa;
}
.card-name {
  min-width: 0;
  max-width: 100%;
  font-size: 20px;
  font-weight: 600;
  color: #303133;
  margin-bottom: 8px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.card-text {
  min-width: 0;
  max-width: 100%;
  font-size: 14px;
  color: #606266;
  line-height: 1.8;
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
  color: #909399;
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
  color: #606266;
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
.edit-photo-img {
  max-width: 100%;
  max-height: 100%;
  object-fit: cover;
  border-radius: 4px;
}
</style>
