<template>
  <div v-loading="loading" class="pd-page">
    <el-page-header :content="detail.name || '项目详情'" @back="$router.push('/project')" />

    <!-- ① 概览头部：名称/状态徽标/元信息 + 操作分组 -->
    <div v-if="detail.id" class="pd-head ui-card">
      <div class="pd-head-main">
        <div class="pd-title-row">
          <span class="pd-name">{{ detail.name }}</span>
          <span class="ui-badge ui-badge-primary">{{ detail.phaseName || '-' }}</span>
          <span v-if="detail.status === 'CANCELED'" class="ui-badge ui-badge-danger">已取消</span>
          <span v-else class="ui-badge ui-badge-success">有效</span>
        </div>
        <div class="pd-meta">
          <span v-if="detail.projectType">项目类型：{{ PROJECT_TYPE_LABELS[detail.projectType] || detail.projectType }}</span>
          <span>地点：{{ detail.category === 'ONLINE' ? '线上' : '线下' }}</span>
          <span>团队：{{ detail.teamLabel || '-' }}</span>
          <span v-if="detail.cityName">会议城市：{{ detail.cityName }}</span>
          <span>总负责人：{{ detail.ownerName || '-' }}</span>
          <span>项目起止：{{ detail.startDate || '-' }} ~ {{ detail.endDate || '-' }}</span>
        </div>
        <div v-if="detail.status === 'CANCELED'" class="pd-cancel-tip">
          已取消：{{ detail.cancelReason }}<template v-if="detail.cancelTime">（{{ detail.cancelTime }}）</template>
        </div>
        <div v-if="detail.canAdvance && hasNextPhase && !requiredSubmitted" class="pd-block-tip">
          {{ missingRequiredTip }}
        </div>
      </div>
      <div class="pd-actions">
        <el-button v-if="detail.canAdvance && hasNextPhase" type="primary" :disabled="!requiredSubmitted" @click="confirmAdvance">推进下一阶段</el-button>
        <el-button v-if="detail.canManage || detail.canEditBasic" @click="openEdit">编辑项目</el-button>
        <el-dropdown trigger="click" @command="onHeadCommand">
          <el-button>更多操作<el-icon style="margin-left: 4px"><ArrowDown /></el-icon></el-button>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item v-if="detail.canRollback" command="rollback">回退阶段</el-dropdown-item>
              <el-dropdown-item v-if="detail.canRestore" command="restore" divided>恢复项目</el-dropdown-item>
              <el-dropdown-item v-if="detail.canAdvance && detail.status !== 'CANCELED'" command="cancel" divided>取消项目</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </div>
    </div>

    <div v-if="detail.id" class="pd-grid">
      <!-- 主内容列 -->
      <div class="pd-main">
        <!-- ② 核心数据卡（日程上传阶段隐藏，Owner 2026-11-09） -->


        <section class="pd-stepper ui-card" aria-label="项目生命周期">
          <div class="pd-sec-title">项目生命周期</div>
          <ol class="steps-row">
            <li v-for="(p, idx) in sortedPhases" :key="p.id">
              <button type="button" class="step-item" :class="stepClass(p)"
                :aria-current="phaseState(p) === 'current' ? 'step' : undefined"
                :aria-pressed="String(p.id) === String(viewPhaseId)"
                @click="setViewPhase(p)">
                <span class="step-dot" aria-hidden="true">{{ phaseState(p) === 'done' ? '✓' : idx + 1 }}</span>
                <span class="step-info">
                  <span class="step-name">{{ p.name }}</span>
                  <span class="step-state">{{ phaseState(p) === 'current' ? '当前阶段' : phaseState(p) === 'done' ? '已完成' : '未开始' }}</span>
                </span>
                <span v-if="String(p.id) === String(viewPhaseId) && !isViewingCurrent" class="step-viewing">正在查看</span>
              </button>
            </li>
          </ol>
        </section>

        <!-- ⑤ 阶段资料区（随查看阶段切换） -->
        <div id="project-stage-materials" class="pd-phase ui-card">
          <div class="pd-sec-title-row">
            <span class="pd-sec-title">阶段资料 · {{ viewPhaseObj?.name || '-' }}</span>
            <span v-if="!isViewingCurrent" class="ui-badge ui-badge-info">非当前阶段（只读）</span>
          </div>

          <!-- 会前财务资料上传阶段（2026-11-20）：按项目类型差异化表单 + 附件补充 -->
          <template v-if="isFinanceViewing && phaseState(viewPhaseObj) !== 'future'">
            <ProjectFinanceForm
              :key="`${projectId}-${viewPhaseObj.id}`"
              :project-id="projectId"
              :phase-id="viewPhaseObj.id"
              :project-type="detail.projectType || ''"
              :project-info="detail"
              :readonly="!canOperatePhase"
              @saved="fetchDetail" />
            <div class="fin-attach-row">
              <el-upload v-if="canOperatePhase" :show-file-list="false" :auto-upload="false" :on-change="(f) => uploadDoc(viewPhaseObj.id, f)">
                <el-button size="small" type="primary" plain>上传附件资料到本阶段</el-button>
              </el-upload>
              <span class="hint-text">合同、发票、会议通知等附件资料在此上传，与表单一并作为本阶段材料</span>
            </div>
            <template v-if="docsOfPhase(viewPhaseObj.id, 'FILE').length">
              <div v-for="d in docsOfPhase(viewPhaseObj.id, 'FILE')" :key="d.id" class="doc-row" style="margin-top: 8px">
                <a href="javascript:void(0)" class="doc-name" @click="openFilePreview(d)">{{ d.fileName }}</a>
                <span class="doc-meta">上传人：{{ d.uploadBy }} · {{ d.createTime }}</span>
                <el-button v-if="canOperatePhase" link type="danger" style="margin-left: 12px" @click="removeDoc(d)">删除</el-button>
              </div>
            </template>
            <div v-else class="hint-text" style="margin-top: 8px">本阶段暂无附件资料</div>
          </template>
          <template v-else-if="viewPhaseObj?.name === '串场制作'">
            <div v-if="canOperatePhase" class="fin-attach-row">
              <el-upload accept=".ppt,.pptx" :show-file-list="false" :auto-upload="false"
                :on-change="uploadShowFile">
                <el-button type="primary" :loading="showUploading">上传串场 PPT</el-button>
              </el-upload>
              <el-button @click="openShowWizard">PPT 制作</el-button>
            </div>
            <div v-for="d in docsOfPhase(viewPhaseObj.id, 'FILE')" :key="d.id" class="doc-row">
              <el-button link type="primary" @click="downloadProjectFile(d)">{{ d.fileName }}</el-button>
              <span class="doc-meta">{{ d.uploadBy }} · {{ d.createTime }}</span>
            </div>
          </template>
          <el-empty v-else-if="isPlaceholderPhaseViewing" description="暂未配置" :image-size="60" />
          <!-- 会议预算制作阶段（2026-11-16）：线上简化报价单 / 线下六类费用明细 -->
          <template v-else-if="isBudgetViewing && phaseState(viewPhaseObj) !== 'future'">
            <ProjectBudgetPanel id="meeting-budget-panel" :key="projectId" :project-id="projectId" :readonly="!canOperatePhase"
                                @saved="fetchDetail()"/>
          </template>
          <!-- 日程上传阶段：上传即替换当前日程（不做历史版本）并自动解析参会专家 -->
          <template v-else-if="isAgendaStage && isViewingCurrent">
            <div id="schedule-upload" class="schedule-upload-row">
              <el-upload v-if="canOperate" :show-file-list="false" :auto-upload="false" :on-change="onScheduleFile" accept=".pdf,.doc,.docx,.ppt,.pptx,.jpg,.jpeg,.png,.xlsx,.xls">
                <el-button type="primary" plain :loading="parsing">{{ schedule ? '重新上传日程（替换当前份）' : '上传日程文件' }}</el-button>
              </el-upload>
              <span class="hint-text">支持 pdf/doc/docx/ppt/pptx/jpg/png/xlsx/xls，≤50MB；重新上传将替换当前日程并自动重新解析</span>
              <el-button v-if="schedule && canOperate && schedule.parseStatus !== 'PARSING'" size="small" plain style="margin-left: auto" @click="doParse">
                {{ schedule.parseStatus === 'DONE' ? '重新解析' : '开始解析' }}
              </el-button>
            </div>
            <div v-if="schedule" class="schedule-file" style="margin-top: 8px">
              当前生效日程（参与解析）：{{ schedule.fileName }}
              <span class="hint-text">（{{ schedule.createTime }} · {{ scheduleStatusText(schedule.parseStatus) }}）</span>
            </div>
            <div v-else class="hint-text" style="margin-top: 8px">尚未上传日程文件</div>
            <template v-if="docsOfPhase(viewPhaseObj.id, 'FILE').length">
              <div v-for="d in docsOfPhase(viewPhaseObj.id, 'FILE')" :key="d.id" class="doc-row" style="margin-top: 8px">
                <a href="javascript:void(0)" class="doc-name" @click="openFilePreview(d)">{{ d.fileName }}</a>
                <el-tag v-if="isCurrentScheduleDoc(d)" size="small" type="success" style="margin-left: 6px">生效日程</el-tag>
                <span class="doc-meta">上传人：{{ d.uploadBy }} · {{ d.createTime }}</span>
                <el-button v-if="canOperate" link type="danger" style="margin-left: 12px" @click="removeScheduleDoc(d)">删除</el-button>
              </div>
            </template>
            <div v-else class="hint-text" style="margin-top: 8px">本阶段暂无附件资料</div>
          </template>
          <template v-else-if="phaseState(viewPhaseObj) === 'future'">
            <el-empty description="该阶段尚未开始" :image-size="80" />
          </template>
          <template v-else-if="viewPhaseObj && viewPhaseObj.templates && viewPhaseObj.templates.length">
            <div v-for="t in viewPhaseObj.templates" :key="t.templateId" class="template-block">
              <div class="template-title">
                {{ t.templateName }}
                <el-tag v-if="t.required === 1" size="small" type="danger">必填</el-tag>
                <el-tag v-else size="small" type="info">选填</el-tag>
                <span v-if="isViewingCurrent && t.required === 1 && isTemplateFulfilled(t) === false" class="unfilled-tip">未配置内容，阶段推进将受限</span>
              </div>

              <!-- 资料附件模板 -->
              <div v-if="t.templateType === 'FILE'">
                <div v-for="d in docsOfPhase(viewPhaseObj.id, 'FILE')" :key="d.id" class="doc-row">
                  <a href="javascript:void(0)" class="doc-name" @click="openFilePreview(d)">{{ d.fileName }}</a>
                  <span class="doc-meta">上传人：{{ d.uploadBy }} · {{ d.createTime }}</span>
                  <el-button v-if="canOperatePhase" link type="danger" @click="removeDoc(d)">删除</el-button>
                </div>
                <div v-if="canOperatePhase" class="upload-area">
                  <el-upload
                    :show-file-list="false"
                    :auto-upload="false"
                    :on-change="(f) => uploadDoc(viewPhaseObj.id, f)"
                  >
                    <el-button size="small" type="primary" plain>上传资料到本阶段</el-button>
                  </el-upload>
                </div>
                <div v-if="!docsOfPhase(viewPhaseObj.id, 'FILE').length" class="hint-text">本阶段暂无附件资料</div>
              </div>

              <!-- 自定义文本模板 -->
              <div v-else-if="t.templateType === 'RICH_TEXT'">
                <div v-if="!editingRich[viewPhaseObj.id]" class="rich-view">{{ richTextOf(viewPhaseObj.id) || '（尚未填写内容）' }}</div>
                <el-input v-if="canOperatePhase && editingRich[viewPhaseObj.id]" v-model="richDrafts[viewPhaseObj.id]" type="textarea" :rows="4" />
                <el-button v-if="canOperatePhase" size="small" type="primary" plain style="margin-top: 6px" @click="editRich(viewPhaseObj.id, t.templateId)">{{ editingRich[viewPhaseObj.id] ? '保存文本' : '编辑文本' }}</el-button>
              </div>

              <!-- 预算模板 -->
              <div v-else-if="t.templateType === 'BUDGET'">
                <div class="hint-text">预算金额：¥ {{ fmt(budgetAmount) }}</div>
              </div>

              <!-- 专家/供应商模板 -->
              <div v-else>
                <div class="hint-text">{{ isTemplateFulfilled(t) ? '已有记录' : '暂无记录' }}</div>
              </div>
            </div>
          </template>
          <template v-else>
            <el-empty description="本阶段无需提交材料，向后推进没有材料拦截" :image-size="60" />
            <el-upload v-if="canOperatePhase" :show-file-list="false" :auto-upload="false" :on-change="(f) => uploadDoc(viewPhaseObj.id, f)">
              <el-button size="small" plain>按需补充本阶段附件</el-button>
            </el-upload>
          </template>
        </div>

        <!-- ④.5 参会专家（2026-11-09 流程重塑：日程在「阶段资料」上传后自动解析）
             2026-11-16 Owner 口径：仅日程上传阶段展示，会议预算制作阶段不展示 -->
        <div v-if="isAgendaStage || candidates.length" class="ui-card pd-block pd-schedule">
          <div class="pd-sec-title-row">
            <span class="pd-sec-title">参会专家</span>
            <span :class="'ui-badge ' + scheduleBadgeClass">{{ scheduleBadgeText }}</span>
          </div>
          <div v-if="schedule && schedule.parseStatus === 'PARSING'" class="hint-text">解析中，请稍候（本地大模型首次冷加载可能需要 1-2 分钟）…</div>
          <div v-if="schedule && schedule.parseStatus === 'FAILED'" class="pd-block-tip" style="margin-bottom: 8px">解析失败：{{ schedule.parseMessage }}；可在「阶段资料」重新上传，或手动补录参会专家。</div>

          <!-- 候选专家（AI 解析结果=候选，须人工确认） -->
          <template v-if="candidates.length">
            <div id="schedule-candidates" class="schedule-sub-title">新增解析候选（请逐条确认后保存）</div>
            <el-table :data="candidateDraft" size="small">
              <el-table-column label="姓名" width="140">
                <template #default="{ row }"><el-input v-model="row.expertName" size="small" @update:model-value="row.expertId = null" /></template>
              </el-table-column>
              <el-table-column label="会议角色" width="140">
                <template #default="{ row }">
                  <el-select v-model="row.role" size="small">
                    <el-option v-for="r in roleOptions" :key="r.value" :label="r.label" :value="r.value" />
                  </el-select>
                </template>
              </el-table-column>
              <el-table-column label="议题/段落" min-width="150">
                <template #default="{ row }"><el-input v-model="row.segmentTitle" size="small" placeholder="可空" /></template>
              </el-table-column>
              <el-table-column label="时间" width="150">
                <template #default="{ row }"><el-input v-model="row.segmentTime" size="small" placeholder="可空" /></template>
              </el-table-column>
              <el-table-column label="专家库匹配" min-width="160">
                <template #default="{ row }">
                  <el-select v-model="row.expertId" size="small" clearable
                    :disabled="!row.expertName?.trim()" :loading="expertMatchLoading.has(row.expertName?.trim())"
                    placeholder="选择同名专家" no-data-text="专家库中无同名专家"
                    @visible-change="open => open && loadExpertMatches(row.expertName)">
                    <el-option v-for="e in expertMatches(row)" :key="e.id" :label="e.name + (e.unit ? '·' + e.unit : '')" :value="e.id" />
                  </el-select>
                  <el-tag v-if="!row.expertId" size="small" type="warning">库外</el-tag>
                </template>
              </el-table-column>
              <el-table-column label="操作" width="180" fixed="right">
                <template #default="{ row }">
                  <el-button v-if="canOperate" link type="primary" size="small" :disabled="savingSpeakers"
                    @click="saveSpeakerRows([row], row.id)">保存</el-button>
                  <el-button v-if="!row.expertId && canOperate" link type="primary" size="small" @click="openArchiveDialog(row, true)">去建档</el-button>
                  <el-button link type="danger" size="small" @click="removeCandidateRow(row)">移除</el-button>
                </template>
              </el-table-column>
            </el-table>
            <div v-if="canOperate" class="task-ops">
              <el-button v-if="candidateDraft.length" type="primary" :disabled="savingSpeakers"
                @click="saveSpeakerRows(candidateDraft)">确认保存新增参会记录（{{ candidateDraft.length }} 条）</el-button>
              <el-button v-else :disabled="savingSpeakers" @click="discardCandidates">放弃全部候选</el-button>
            </div>
          </template>

          <!-- 正式参会专家 -->
          <div id="schedule-speakers" class="schedule-sub-title">
            参会专家（已确认 {{ speakers.length }} 人）
            <el-button v-if="canOperate && speakers.length" link type="danger" size="small" :disabled="savingSpeakers"
              @click="clearSavedSpeakers">清空已保存专家</el-button>
            <!-- 2026-11-20：解析有误/遗漏时手动触发重新解析（与页头「重新解析」同一 doParse） -->
            <el-button v-if="canOperate && schedule && schedule.parseStatus !== 'PARSING'" link type="primary" size="small"
              :loading="parsing" @click="doParse">手动解析</el-button>
          </div>
          <el-table v-if="speakers.length" :data="speakers" size="small" stripe>
            <el-table-column prop="expertName" label="姓名" width="120" />
            <el-table-column label="会议角色" width="110">
              <template #default="{ row }">{{ roleLabel(row.role) }}</template>
            </el-table-column>
            <el-table-column prop="segmentTitle" label="议题/段落" min-width="160">
              <template #default="{ row }">{{ row.segmentTitle || '-' }}</template>
            </el-table-column>
            <el-table-column prop="segmentTime" label="时间" width="150">
              <template #default="{ row }">{{ row.segmentTime || '-' }}</template>
            </el-table-column>
            <el-table-column label="档案关联" min-width="140">
              <template #default="{ row }">
                <span v-if="row.expertId" class="doc-name">{{ row.expertName }}（{{ row.expertUnit || '已关联' }}）</span>
                <el-tag v-else-if="canOperate" size="small" type="warning" class="link-tag" @click="openArchiveDialog(row)">库外专家·去建档</el-tag>
                <el-tag v-else size="small" type="warning">库外专家</el-tag>
              </template>
            </el-table-column>
            <el-table-column v-if="canOperate" label="操作" width="80" fixed="right">
              <template #default="{ row }">
                <el-button link type="danger" size="small" @click="removeSpeaker(row)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>
          <el-empty v-else description="尚未确认参会专家" :image-size="60" />

          <!-- 手动补录 -->
          <div v-if="canOperate" class="manual-add-row">
            <el-input v-model="manualName" size="small" placeholder="姓名" style="width: 140px" />
            <el-select v-model="manualRole" size="small" placeholder="角色" style="width: 130px">
              <el-option v-for="r in roleOptions" :key="r.value" :label="r.label" :value="r.value" />
            </el-select>
            <el-button size="small" plain :disabled="!manualName.trim() || !manualRole" @click="doAddSpeaker">手动补录</el-button>
            <span class="hint-text">解析失败/遗漏时可直接补录；库外专家仅作项目内记录。</span>
          </div>
        </div>

        <!-- ⑥.1 备注 -->
        <div v-if="!isFlowStage && detail.remark" class="ui-card pd-info-card" style="margin-top: 10px">
          <div class="pd-sec-title">备注</div>
          <div class="remark-text">{{ detail.remark }}</div>
        </div>

        <!-- 业务明细：时间线 -->
        <div class="ui-card pd-block">
          <div class="pd-sec-title">项目时间线（operation_log 同源，倒序）</div>
          <el-timeline v-if="detail.timeline && detail.timeline.length">
            <el-timeline-item
              v-for="(log, i) in detail.timeline"
              :key="i"
              :timestamp="log.createTime"
              placement="top"
            >
              <div class="tl-row">
                <span class="tl-user">{{ log.username }}</span>
                <span class="tl-desc">{{ log.actionDesc }}</span>
              </div>
            </el-timeline-item>
          </el-timeline>
          <el-empty v-else description="暂无操作记录" :image-size="60" />
        </div>
      </div>

      <!-- ⑦ 右侧辅助栏（日程上传阶段隐藏，Owner 2026-11-09） -->
      <div class="pd-side">
        <section v-if="detail.complianceBoundDocTitle || detail.complianceTodos?.length" class="ui-card side-card pd-compliance">
          <div class="pd-sec-title">合规要求注意事项</div>
          <div v-if="detail.complianceBoundDocTitle" class="hint-text pd-compliance-source">
            来源文档：{{ detail.complianceBoundDocTitle }}
          </div>
          <el-radio-group v-if="detail.complianceTodos?.length" v-model="complianceView" size="small"
            class="pd-compliance-switch" aria-label="合规事项视图">
            <el-radio-button value="PENDING">待完成</el-radio-button>
            <el-radio-button value="DONE">已完成</el-radio-button>
          </el-radio-group>
          <ul v-if="visibleComplianceTodos.length" :key="complianceView" class="pd-compliance-items">
            <li v-for="todo in visibleComplianceTodos" :key="todo.id">
              <div class="pd-compliance-heading">
                <strong>{{ todo.title }}</strong>
                <el-tag :type="todo.status === 'DONE' ? 'success' : 'warning'" size="small">
                  {{ todo.status === 'DONE' ? '已完成' : '待处理' }}
                </el-tag>
              </div>
              <div v-if="todo.content" class="pd-compliance-content">{{ todo.content }}</div>
              <div class="pd-compliance-footer">
                <span class="hint-text">{{ todo.docTitle }} {{ todo.sourceAnchor || '' }}</span>
                <el-tag v-if="todo.sourceDeleted" size="small" type="info">来源已删除</el-tag>
                <el-button
                  v-if="detail.canOperate || detail.canEditBasic"
                  link
                  :type="todo.status === 'PENDING' ? 'primary' : 'info'"
                  size="small"
                  :disabled="complianceUpdating"
                  @click="handleComplianceTodo(todo, todo.status === 'PENDING' ? 'DONE' : 'PENDING')"
                >{{ todo.status === 'PENDING' ? '完成' : '重开' }}</el-button>
              </div>
            </li>
          </ul>
          <div v-else class="hint-text">
            {{ detail.complianceTodos?.length ? (complianceView === 'DONE' ? '暂无已完成事项' : '暂无待完成事项') : '暂无推送的合规待办' }}
          </div>
        </section>

        <!-- 2026-11-16 Owner 口径：会议预算制作阶段只展示 生命周期/阶段资料，右侧待办/文件/合规均隐藏 -->
        <div class="ui-card side-card">
          <div class="pd-sec-title">当前待办</div>
          <template v-if="otherRequiredTemplates.length">
            <div v-for="(m, i) in otherRequiredTemplates" :key="i" class="todo-item">
              <span class="todo-name">{{ m }}</span>
              <el-button link type="primary" size="small" @click="resolveMissing(m)">{{ resolveLabel(m) }} →</el-button>
            </div>
          </template>
          <div v-if="comparison?.changes.length" class="todo-item">
            <span class="todo-name">核对日程变更 {{ comparison.changes.length }} 条</span>
            <el-button v-if="canOperate" link type="warning" size="small" @click="openComparison">查看差异 →</el-button>
          </div>
          <div v-if="candidates.length" class="todo-item">
            <span class="todo-name">待保存专家信息 {{ candidates.length }} 条</span>
            <el-button v-if="canOperate" link type="primary" size="small" @click="goToScheduleCandidates">去保存 →</el-button>
          </div>
          <template v-else-if="outArchiveCount > 0">
            <div class="todo-item">
              <span class="todo-name">库外专家待入档 {{ outArchiveCount }} 人</span>
              <el-button v-if="canOperate" link type="warning" size="small"
                @click="openArchiveDialog(null)">一键上传</el-button>
            </div>
          </template>
          <div v-if="!otherRequiredTemplates.length && !comparison?.changes.length && !candidates.length && !outArchiveCount"
            class="side-done">{{ isBudgetStage ? '报价文件已生成' : '当前阶段必填配置已完成' }}</div>
        </div>

        <!-- 项目文件快速访问（Owner 2026-11-12）：所有阶段上传的文件均可快速下载/预览
             2026-11-16 Owner 口径：会议预算制作阶段隐藏 -->
        <div class="ui-card side-card">
          <div class="pd-sec-title">项目文件（{{ allDocs.length }}）</div>
          <template v-if="allDocs.length">
            <div v-for="d in allDocs.slice(0, 10)" :key="d.id" class="side-doc">
              <a href="javascript:void(0)" class="doc-name" :title="d.fileName" @click="openFilePreview(d)">{{ d.fileName }}</a>
              <div class="side-doc-ops">
                <el-button link type="primary" size="small" @click="openFilePreview(d)">预览</el-button>
                <el-button link size="small" @click="downloadProjectFile(d)">下载</el-button>
                <span class="doc-meta">{{ d.createTime }}</span>
              </div>
            </div>
            <div v-if="allDocs.length > 10" class="hint-text" style="margin-top: 4px">共 {{ allDocs.length }} 份，仅展示最近 10 份</div>
          </template>
          <div v-else class="hint-text">暂无附件资料</div>
        </div>



        <div class="ui-card side-card">
          <div class="pd-sec-title">项目动态</div>
          <template v-if="detail.timeline && detail.timeline.length">
            <div v-for="(log, i) in detail.timeline.slice(0, 6)" :key="i" class="side-log">
              <div class="side-log-desc">{{ log.actionDesc }}</div>
              <div class="side-log-meta">{{ log.username }} · {{ log.createTime }}</div>
            </div>
            <div v-if="detail.timeline.length > 6" class="hint-text" style="margin-top: 4px">更多动态见下方「项目时间线」</div>
          </template>
          <div v-else class="hint-text">暂无操作记录</div>
        </div>
      </div>
    </div>

    <!-- ============ 弹窗（业务逻辑不变） ============ -->
    <project-edit
      v-model="editVisible"
      :project="editRow"
      :owners="ownerOptions"
      :users="userOptions"
      @saved="fetchDetail"
    />

    <!-- 回退阶段弹窗（REQ-20261102：相邻/跨级回退，原因必填） -->
    <el-dialog v-model="rollbackVisible" title="回退阶段" width="480px" destroy-on-close>
      <el-form label-width="90px">
        <el-form-item label="回退到" required>
          <el-select v-model="rollbackTargetId" placeholder="选择目标阶段（仅更早阶段）" style="width: 100%">
            <el-option
              v-for="p in rollbackCandidates"
              :key="p.id"
              :label="`${p.name}${p.skipped ? '（跳过 ' + p.skipped + ' 个阶段）' : ''}`"
              :value="p.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="回退原因" required>
          <el-input
            v-model="rollbackReason"
            type="textarea"
            :rows="3"
            maxlength="500"
            show-word-limit
            placeholder="必填：请说明回退原因"
          />
        </el-form-item>
      </el-form>
      <div v-if="rollbackTarget" class="hint-text" style="margin-bottom: 8px">
        {{ rollbackHint }}
      </div>
      <template #footer>
        <el-button @click="rollbackVisible = false">取消</el-button>
        <el-button type="warning" :loading="rollingBack" :disabled="!rollbackTargetId || !rollbackReason.trim()" @click="doRollback">确认回退</el-button>
      </template>
    </el-dialog>

    <!-- 取消项目 -->
    <el-dialog v-model="cancelVisible" title="取消项目" width="460px" destroy-on-close>
      <el-input v-model="cancelReason" type="textarea" :rows="3" maxlength="500" placeholder="取消原因（必填，写入时间线）" />
      <template #footer>
        <el-button @click="cancelVisible = false">再想想</el-button>
        <el-button type="danger" :disabled="!cancelReason.trim()" @click="doCancel">确认取消</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="comparisonVisible" title="核对日程参会记录变更" width="min(760px, 95vw)">
      <p>勾选要应用的变更。未勾选的参会记录会保留，移除只影响当前项目。</p>
      <p>时间和议题均变化的出场会分别列为待移除记录和新增候选，请结合候选列表核对。</p>
      <el-checkbox-group v-model="acceptedChanges">
        <el-table :data="comparison?.changes || []" size="small" max-height="420">
          <el-table-column label="应用" width="64">
            <template #default="{ row }">
              <el-checkbox :value="row.before.id" :aria-label="'应用 ' + row.before.expertName + ' 的参会变更'" />
            </template>
          </el-table-column>
          <el-table-column label="已保存出场" min-width="180">
            <template #default="{ row }">{{ row.before.expertName }} · {{ describeAppearance(row.before) }}</template>
          </el-table-column>
          <el-table-column label="新日程" min-width="200">
            <template #default="{ row }">
              <template v-if="row.after">→ {{ describeAppearance(row.after) }}</template>
              <template v-else>
                <div>从本项目移除此出场</div>
                <div v-for="candidate in relatedCandidates(row.before)" :key="candidate.id">
                  新增候选：{{ describeAppearance(candidate) }}
                </div>
              </template>
            </template>
          </el-table-column>
        </el-table>
      </el-checkbox-group>
      <template #footer>
        <el-button :disabled="savingComparison" @click="saveComparison([])">保留原记录</el-button>
        <el-button type="primary" :disabled="!acceptedChanges.length" :loading="savingComparison"
          @click="saveComparison(acceptedChanges)">应用所选变更</el-button>
      </template>
    </el-dialog>

    <BatchResumeDialog v-model="archiveVisible" :target-names="archiveTargetNames"
      :auto-match-targets="!archivePresetName" :max-pending-files="60"
      :preset-name="archivePresetName" :save-project-expert="saveProjectResume" @saved="refreshArchivedExperts">
      <template #extra>
        <el-button :disabled="!canOperate" @click="manualArchiveVisible = true">手动建档</el-button>
      </template>
    </BatchResumeDialog>
    <el-dialog v-model="manualArchiveVisible" title="手动建档" width="640px" destroy-on-close>
      <el-form label-width="70px" size="small">
        <el-row :gutter="8">
          <el-col :span="8"><el-form-item label="姓名" required><el-input v-model="archiveForm.name" :readonly="Boolean(archivePresetName)" /></el-form-item></el-col>
          <el-col :span="8"><el-form-item label="单位" required><el-input v-model="archiveForm.unit" /></el-form-item></el-col>
          <el-col :span="8"><el-form-item label="职称"><el-input v-model="archiveForm.title" /></el-form-item></el-col>
        </el-row>
        <el-form-item label="简介"><el-input v-model="archiveForm.profile" type="textarea" :rows="2" /></el-form-item>
        <el-button size="small" plain :loading="archiveRunning" @click="doArchiveManual">手动建档并回绑</el-button>
      </el-form>
      <template #footer>
        <el-button @click="manualArchiveVisible = false">完成</el-button>
      </template>
    </el-dialog>

    <!-- 项目文件在线预览（Owner 2026-11-12，参考知识星球：pdf.js / img / Office 转 PDF / 下载兜底） -->
    <el-drawer v-model="filePreviewVisible" size="72%" :title="filePreviewTitle" destroy-on-close class="preview-drawer">
      <div class="preview-toolbar">
        <div v-if="filePreviewMode === 'pdf'" class="preview-nav">
          <el-button-group>
            <el-button size="small" :disabled="filePreviewPage <= 1" @click="filePreviewPage -= 1">上一页</el-button>
            <el-button size="small" :disabled="filePreviewPage >= filePreviewNumPages" @click="filePreviewPage += 1">下一页</el-button>
          </el-button-group>
          <span class="preview-info">{{ filePreviewPage }} / {{ filePreviewNumPages }}</span>
        </div>
        <div v-if="canZoomFilePreview" class="preview-nav" aria-label="滚轮缩放预览">
          <el-button size="small" :disabled="filePreviewScale <= 0.3" @click="zoomFilePreview(-1)">缩小</el-button>
          <span class="preview-info">{{ Math.round(filePreviewScale * 100) }}%</span>
          <el-button size="small" :disabled="filePreviewScale >= 3" @click="zoomFilePreview(1)">放大</el-button>
        </div>
        <el-button size="small" type="primary" @click="downloadProjectFile(filePreviewRow)">下载</el-button>
      </div>
      <div class="preview-body" :class="{ 'is-pannable': canZoomFilePreview && !filePreviewLoading, 'is-panning': filePreviewPanning }"
        v-loading="filePreviewLoading" @wheel="onFilePreviewWheel" @pointerdown="startFilePreviewPan"
        @pointermove="moveFilePreviewPan" @pointerup="stopFilePreviewPan"
        @pointercancel="stopFilePreviewPan" @lostpointercapture="stopFilePreviewPan">
        <img v-if="filePreviewMode === 'image'" :src="filePreviewImageUrl" class="preview-image" draggable="false" alt="文件预览" :style="filePreviewImageWidth ? { width: filePreviewImageWidth * filePreviewScale + 'px' } : {}" @load="sizeFilePreviewImage" />
        <div v-else-if="filePreviewMode === 'excel'" class="preview-excel">
          <KnowledgeWorkbookPreview :file-path="filePreviewWorkbookFile" :fetcher="fetchProjectWorkbook" />
        </div>
        <canvas v-else-if="filePreviewMode === 'pdf'" ref="filePreviewCanvasRef" class="preview-canvas"></canvas>
        <el-empty v-else-if="filePreviewMode === 'failed'" description="预览失败，可下载查看">
          <el-button type="primary" size="small" @click="downloadProjectFile(filePreviewRow)">下载</el-button>
        </el-empty>
        <el-empty v-else description="正在加载预览..." />
      </div>
    </el-drawer>
    <el-dialog v-model="showWizardOpen" fullscreen append-to-body destroy-on-close
      :show-close="false" :close-on-click-modal="false" :close-on-press-escape="false"
      aria-label="串场 PPT 制作">
      <ShowWizard v-if="showWizardOpen" :project-id="projectId" @close="showWizardOpen = false" @archived="fetchDetail()" />
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, defineAsyncComponent, nextTick, onMounted, reactive, ref, shallowRef, watch } from 'vue'
import * as pdfjsLib from 'pdfjs-dist/legacy/build/pdf.mjs'
import pdfWorkerCode from 'pdfjs-dist/legacy/build/pdf.worker.min.mjs?raw'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '../../store/auth'
import { ElMessage, ElMessageBox } from 'element-plus'
import { handleProjectComplianceTodo } from '../../api/compliance'
import { ArrowDown } from '@element-plus/icons-vue'
import ProjectEdit from './ProjectEdit.vue'
import ProjectBudgetPanel from './components/ProjectBudgetPanel.vue'
import { uploadShowPpt } from '../../api/project'
import ProjectFinanceForm from './components/ProjectFinanceForm.vue'
import BatchResumeDialog from '../../components/BatchResumeDialog.vue'
import request from '../../api/request'
import { uploadLimits, ensureUploadLimits, toMb } from '../../utils/limits'
import { downloadBlob, formatMoney as fmt } from '../../utils/common'

ensureUploadLimits()
import {
  getProjectDetail, changePhase, cancelProject, restoreProject,
  uploadProjectDoc, deleteProjectDoc, saveRichText,
  listProjectOwners, listProjectUsers,
  uploadSchedule, getScheduleOverview, parseSchedule, confirmSpeakers, addSpeaker, deleteSpeaker,
  archiveManual, applyScheduleComparison, clearConfirmedSpeakers, deleteScheduleDoc, fetchProjectFile, fetchProjectWorkbook
} from '../../api/project'
import KnowledgeWorkbookPreview from '../../components/KnowledgeWorkbookPreview.vue'

const route = useRoute()
const router = useRouter()
const projectId = route.params.id

const ShowWizard = defineAsyncComponent(() => import('./ShowWizard.vue'))
const showWizardOpen = ref(false)

function openShowWizard() {
  showWizardOpen.value = true
}
// 项目类型展示名（2026-11-20 新增；与后端 PROJECT_TYPE_LABELS 一致）
const PROJECT_TYPE_LABELS = { PLATFORM: '平台类项目', DIRECT_PAY: '直付类项目', KEY: '重点类项目' }
const loading = ref(false)
const detail = ref({})
const complianceView = ref('PENDING')
const complianceUpdating = ref(false)
const visibleComplianceTodos = computed(() =>
  (detail.value.complianceTodos ?? []).filter(todo => todo.status === complianceView.value))
// 查看阶段（Stepper 点击切换，仅影响展示层，不触发流转）；初始=当前阶段
const viewPhaseId = ref('')

const editVisible = ref(false)
const editRow = ref(null)
const ownerOptions = ref([])
const userOptions = ref([])

const cancelVisible = ref(false)
const cancelReason = ref('')

const editingRich = ref({})
const richDrafts = ref({})
const richOfPhase = ref({})

const budgetAmount = computed(() => detail.value.budget?.budgetAmount ?? detail.value.budgetAmount ?? 0)

const sortedPhases = computed(() => [...(detail.value.phases || [])].sort((a, b) => Number(a.sort ?? 0) - Number(b.sort ?? 0)))

async function fetchDetail({ refreshSchedule = true } = {}) {
  loading.value = true
  try {
    const previousPhaseId = detail.value.phaseId
    detail.value = await getProjectDetail(projectId)
    if ((!viewPhaseId.value || previousPhaseId !== detail.value.phaseId) && detail.value.phases?.length) {
      viewPhaseId.value = String(detail.value.phaseId || detail.value.phases[0].id)
    }
    // 填充各阶段富文本内容（listDocs 对 isRichText 行返回 content）
    const rich = {}
    for (const d of detail.value.docs || []) {
      if (d.isRichText && Number(d.phaseId) > 0) {
        rich[d.phaseId] = d.content || ''
      }
    }
    richOfPhase.value = rich
    if (refreshSchedule) fetchSchedule().catch(() => {})
  } finally {
    loading.value = false
  }
}

// ---- 合规要求注意事项（Owner 2026-11-10 记事本：项目待办处理，row_version 乐观锁） ----
async function handleComplianceTodo(row, status) {
  if (complianceUpdating.value) return
  complianceUpdating.value = true
  try {
    try {
      await handleProjectComplianceTodo(row.id, status, row.rowVersion)
      ElMessage.success(status === 'DONE' ? '待办已完成' : '待办已重开')
    } catch {
      // 接口层提示失败。刷新详情以获取其他用户的最新操作。
    }
    await fetchDetail({ refreshSchedule: false }).catch(() => {})
  } finally {
    complianceUpdating.value = false
  }
}

// ---- 日程与参会专家（2026-11-09 流程重塑第一阶段） ----
const schedule = ref(null)
const candidates = ref([])
const candidateDraft = ref([])
const speakers = ref([])
const roleOptions = [
  { value: 'CHAIR', label: '大会主席' },
  { value: 'HOST', label: '大会主持' },
  { value: 'SPEAKER', label: '大会讲者' },
  { value: 'DISCUSSANT', label: '讨论嘉宾' }
]
function roleLabel(v) {
  return roleOptions.find((r) => r.value === v)?.label || v
}
const scheduleBadgeText = computed(() => {
  if (!schedule.value) return '待上传'
  if (schedule.value.parseStatus === 'PARSING') return '解析中'
  if (schedule.value.parseStatus === 'FAILED') return '解析失败'
  if (schedule.value.parseStatus === 'DONE') {
    return candidates.value.length || comparison.value ? '待确认' : '已完成'
  }
  return '待解析'
})
const scheduleBadgeClass = computed(() => {
  const map = { 待上传: 'ui-badge-info', 解析中: 'ui-badge-info', 解析失败: 'ui-badge-danger', 待确认: 'ui-badge-primary', 已完成: 'ui-badge-success' }
  return map[scheduleBadgeText.value] || 'ui-badge-info'
})

async function fetchSchedule({ preserveDraft = false } = {}) {
  const data = await getScheduleOverview(projectId)
  const previous = new Set(candidates.value.map(row => row.id))
  schedule.value = data?.schedule || null
  parseRevision.value = data?.parseRevision || null
  candidates.value = data?.candidates || []
  speakers.value = data?.speakers || []
  comparison.value = data?.comparison || null
  if (preserveDraft) {
    const linkedById = new Map(candidates.value.map(row => [row.id, row]))
    const drafts = candidateDraft.value.filter(row => linkedById.has(row.id)).map(row => {
      const saved = linkedById.get(row.id)
      if (!saved?.expertId || saved.expertName !== row.expertName || row.expertId) return row
      return { ...row, expertId: saved.expertId, expertUnit: saved.expertUnit || '' }
    })
    candidateDraft.value = [...drafts, ...candidates.value.filter(row => !previous.has(row.id)).map(toCandidateDraft)]
  } else {
    candidateDraft.value = candidates.value.map(toCandidateDraft)
  }
}
function toCandidateDraft(c) {
  return {
    id: c.id,
    scheduleId: c.scheduleId,
    expertName: c.expertName,
    role: c.role,
    segmentTitle: c.segmentTitle || '',
    segmentTime: c.segmentTime || '',
    expertId: c.expertId || null,
    expertUnit: c.expertUnit || ''
  }
}

async function onScheduleFile(file) {
  if (!file || !file.raw) return
  // 前端预检：超限给明确提示，不再让 Nginx 413 弹英文报错（限额单一真源 application.yml expert.upload，2026-11-10）
  if (file.raw.size > uploadLimits.docMaxBytes) {
    ElMessage.warning(`日程文件超过 ${toMb(uploadLimits.docMaxBytes)}MB 限制，请压缩后再上传`)
    return
  }
  try {
    await uploadSchedule(projectId, file.raw)
    await Promise.all([fetchDetail({ refreshSchedule: false }), fetchSchedule()])
    ElMessage.success('日程已上传')
  } catch (e) {
    // 校验失败提示
  }
}

const parsing = ref(false)

// 日程阶段附件辅助（Owner 2026-11-10：附件可删 + 不做历史版本）
function scheduleStatusText(s) {
  return { PENDING: '待解析', PARSING: '解析中', DONE: '解析完成', FAILED: '解析失败' }[s] || s
}
function isCurrentScheduleDoc(d) {
  return !!schedule.value && d.filePath === schedule.value.filePath
}
async function removeScheduleDoc(d) {
  const isCurrent = isCurrentScheduleDoc(d)
  await ElMessageBox.confirm(
    isCurrent
      ? '「' + d.fileName + '」是当前生效日程：删除将同时清除该日程的解析候选（已确认的参会专家保留）。确认删除？'
      : '确认删除附件「' + d.fileName + '」？',
    '删除附件',
    { type: 'warning' }
  )
  await deleteScheduleDoc(projectId, d.id)
  ElMessage.success(isCurrent ? '已删除生效日程并清除解析候选' : '附件已删除')
  await fetchDetail()
}
async function doParse() {
  if (!schedule.value) return
  parsing.value = true
  try {
    await parseSchedule(projectId, schedule.value.id)
    await fetchSchedule()
    ElMessage.success(candidates.value.length ? '解析完成，请保存新增候选' : '解析完成，无新增候选')
  } catch (e) {
    await fetchSchedule()
  } finally {
    parsing.value = false
  }
}

const expertMatchesByName = reactive(new Map())
const expertMatchLoading = reactive(new Set())

function expertMatches(row) {
  const name = row.expertName?.trim()
  if (expertMatchesByName.has(name)) return expertMatchesByName.get(name)
  return row.expertId ? [{ id: row.expertId, name, unit: row.expertUnit }] : []
}

async function loadExpertMatches(value) {
  const name = value?.trim()
  if (!name || expertMatchLoading.has(name)) return
  expertMatchLoading.add(name)
  try {
    const matches = []
    const size = 50
    let page = 1
    let total
    do {
      const data = await request.get('/experts', { params: { keyword: name, page, size } })
      matches.push(...(data?.items || []).map(expert => ({
        id: expert.id, name: expert.姓名, unit: expert.单位
      })).filter(expert => expert.name?.trim() === name))
      total = data?.total || 0
    } while (page++ * size < total)
    expertMatchesByName.set(name, matches)
  } catch {
    // 请求层已提示加载失败，再次打开下拉框可重试。
  } finally {
    expertMatchLoading.delete(name)
  }
}

async function removeCandidateRow(row) {
  // 2026-11-20：移除须同步丢弃后端候选，否则右侧「待保存专家信息」待办永远不消失
  try {
    await confirmSpeakers(projectId, schedule.value.id, parseRevision.value, [], row.id)
    // preserveDraft 保留其他行的本地编辑（姓名/角色/专家库匹配）
    await fetchSchedule({ preserveDraft: true })
  } catch (e) {
    // 请求层已提示；失败时仅退回本地移除，刷新后可重试
    candidateDraft.value = candidateDraft.value.filter((r) => r !== row)
  }
}

const savingSpeakers = ref(false)

async function saveSpeakerRows(rows, candidateId) {
  if (savingSpeakers.value || !schedule.value) return
  const payload = rows.map((r) => ({
    scheduleId: r.scheduleId,
    expertName: String(r.expertName || '').trim(),
    role: r.role,
    expertId: r.expertId || null,
    segmentTitle: r.segmentTitle || null,
    segmentTime: r.segmentTime || null
  }))
  if (payload.some((p) => !p.expertName)) {
    ElMessage.warning('存在姓名为空的候选行，请补齐或移除')
    return
  }
  savingSpeakers.value = true
  try {
    speakers.value = await confirmSpeakers(projectId, schedule.value.id, parseRevision.value, payload, candidateId)
    ElMessage.success(payload.length ? '参会专家已确认保存' : '已放弃候选')
    await Promise.all([fetchSchedule({ preserveDraft: Boolean(candidateId) }), fetchDetail({ refreshSchedule: false })])
  } finally {
    savingSpeakers.value = false
  }
}

async function discardCandidates() {
  await ElMessageBox.confirm('放弃当前全部新增候选？已保存的参会专家会保留。', '放弃候选', { type: 'warning' })
  await saveSpeakerRows([])
}

async function clearSavedSpeakers() {
  if (savingSpeakers.value) return
  try {
    await ElMessageBox.confirm(
      '将清空当前项目全部已保存的参会记录（含手动补录）。解析候选和专家库档案都会保留。',
      '清空已保存专家', { type: 'warning', confirmButtonText: '确认清空' }
    )
  } catch {
    return
  }
  if (savingSpeakers.value) return
  savingSpeakers.value = true
  try {
    await clearConfirmedSpeakers(projectId)
    ElMessage.success('已清空本项目已保存的参会记录')
    await Promise.all([fetchSchedule({ preserveDraft: true }), fetchDetail({ refreshSchedule: false })])
  } finally {
    savingSpeakers.value = false
  }
}

const comparison = ref(null)
const parseRevision = ref(null)
const comparisonVisible = ref(false)
const acceptedChanges = ref([])
const savingComparison = ref(false)

watch(() => comparison.value?.token, (token) => {
  if (token && canOperate.value) openComparison()
})

function openComparison() {
  acceptedChanges.value = []
  comparisonVisible.value = true
}

function describeAppearance(row) {
  return [roleLabel(row.role), row.segmentTime, row.segmentTitle].filter(Boolean).join(' / ')
}

function relatedCandidates(saved) {
  return candidates.value.filter(candidate => {
    if (saved.expertId && candidate.expertId) return saved.expertId === candidate.expertId
    const compact = name => String(name || '').replace(/\s+/g, '')
    return compact(saved.expertName) === compact(candidate.expertName)
  })
}

async function saveComparison(accepted) {
  if (savingComparison.value || !comparison.value) return
  savingComparison.value = true
  try {
    await applyScheduleComparison(projectId, schedule.value.id, comparison.value.token, accepted)
    comparisonVisible.value = false
    await Promise.all([fetchSchedule({ preserveDraft: true }), fetchDetail({ refreshSchedule: false })])
  } finally {
    savingComparison.value = false
  }
}

function goToScheduleCandidates() {
  document.getElementById('schedule-candidates')?.scrollIntoView({ behavior: 'smooth', block: 'start' })
}


const manualName = ref('')
const manualRole = ref(null)
async function doAddSpeaker() {
  await addSpeaker(projectId, { expertName: manualName.value.trim(), role: manualRole.value })
  ElMessage.success('已补录')
  manualName.value = ''
  manualRole.value = null
  fetchSchedule()
}

// ---- 库外专家建档（手动 / 批量简历，Owner 2026-11-09） ----
const outArchiveCount = computed(() => archiveTargetNames.value.length)
const otherRequiredTemplates = computed(() => missingRequiredTemplates.value.filter(item => !item.startsWith('专家匹配入库')
  || (!candidates.value.length && !outArchiveCount.value)))
const archiveVisible = ref(false)
const manualArchiveVisible = ref(false)
const archiveRunning = ref(false)
const archiveForm = reactive({ name: '', unit: '', title: '', profile: '' })
const archiveTargetNames = computed(() => [...new Set(speakers.value
  .filter(row => !row.expertId)
  .map(row => String(row.expertName || '').trim())
  .filter(Boolean))])
const archivePresetName = ref('')
const archiveCandidate = ref(null)

function openArchiveDialog(row, candidate = false) {
  archivePresetName.value = row?.expertName?.trim() || ''
  if (row && !archivePresetName.value) {
    ElMessage.warning('请先填写参会专家姓名')
    return
  }
  archiveCandidate.value = candidate ? row : null
  Object.assign(archiveForm, { name: archivePresetName.value, unit: '', title: '', profile: '' })
  archiveVisible.value = true
}

async function saveProjectResume(fields, photo, existingExpertId) {
  const result = await archiveManual(projectId, {
    name: fields.姓名,
    unit: fields.单位,
    department: fields.科室,
    title: fields.职称,
    profile: fields.简介,
    contact: fields.联系方式,
    requirePendingSpeaker: !archiveCandidate.value,
    matchByNameAndUnit: !archivePresetName.value,
    existingExpertId
  }, photo)
  if (archiveCandidate.value) archiveCandidate.value.expertId = result.expertId
  return `${existingExpertId ? '已关联已有档案' : '已建档'}，回绑参会记录 ${result.boundSpeakers} 条`
}

async function refreshArchivedExperts() {
  try {
    await Promise.all([fetchSchedule({ preserveDraft: true }), fetchDetail({ refreshSchedule: false })])
  } catch {
    // 接口层已提示刷新失败。
  }
}
async function doArchiveManual() {
  if (!archiveForm.name.trim()) {
    ElMessage.warning('姓名必填')
    return
  }
  if (!archiveForm.unit.trim()) {
    ElMessage.warning('单位必填')
    return
  }
  archiveRunning.value = true
  try {
    const result = await archiveManual(projectId, {
      name: archiveForm.name.trim(),
      unit: archiveForm.unit.trim(),
      title: archiveForm.title.trim(),
      profile: archiveForm.profile.trim()
    })
    if (archiveCandidate.value) archiveCandidate.value.expertId = result.expertId
    manualArchiveVisible.value = false
    ElMessage.success('建档成功，已自动关联项目专家名单')
    await refreshArchivedExperts()
  } catch (e) {
    // 错误提示由 request 层统一给出
  } finally {
    archiveRunning.value = false
  }
}

async function removeSpeaker(row) {
  try {
    await ElMessageBox.confirm('确认删除参会专家「' + row.expertName + '」？', '删除', { type: 'warning' })
  } catch (e) {
    return
  }
  await deleteSpeaker(projectId, row.id)
  fetchSchedule()
}

// ---- 生命周期 Stepper：三态与查看阶段 ----
const currentPhase = computed(() => (detail.value.phases || []).find((p) => Number(p.id) === Number(detail.value.phaseId)) || null)
const viewPhaseObj = computed(() => (detail.value.phases || []).find((p) => String(p.id) === String(viewPhaseId.value)) || currentPhase.value)
const isViewingCurrent = computed(() => viewPhaseObj.value && Number(viewPhaseObj.value.id) === Number(detail.value.phaseId))
// 2026-11-09 Owner 口径：日程上传阶段只展示 生命周期/阶段资料/参会专家，其余模块待阶段推进后再展示
const AGENDA_PHASE_NAME = '日程上传'
const isAgendaStage = computed(() => {
  const name = currentPhase.value?.name || detail.value.phaseName || ''
  return name === AGENDA_PHASE_NAME
})
// 2026-11-16 新增「会议预算制作」阶段（位于日程上传之前）：与日程上传同口径的专用阶段视图
const BUDGET_PHASE_NAME = '会议预算制作'
const BUDGET_QUOTE_TASK = '保存报价单'
const isBudgetStage = computed(() => {
  const name = currentPhase.value?.name || detail.value.phaseName || ''
  return name === BUDGET_PHASE_NAME
})
// 专用阶段（日程上传/会议预算制作）只展示 生命周期/阶段资料/参会专家，其余模块待阶段推进后再展示
const isFlowStage = computed(() => isAgendaStage.value || isBudgetStage.value)
// 阶段资料区当前查看的是预算阶段（含查看历史/未来阶段时的只读预览；future 由上层空态拦截）
const isBudgetViewing = computed(() => viewPhaseObj.value?.name === BUDGET_PHASE_NAME)
// 2026-11-20 会前财务资料上传：专用差异化表单视图（按项目类型分支），不再是纯占位
const FINANCE_PHASE_NAME = '会前财务资料上传'
const isFinanceViewing = computed(() => viewPhaseObj.value?.name === FINANCE_PHASE_NAME)
const PLACEHOLDER_PHASE_NAMES = new Set([
  '会前财务资料上传',
  '结算资料上传',
  '会议费用结算及项目结项'
])
const isPlaceholderPhaseViewing = computed(() => PLACEHOLDER_PHASE_NAMES.has(viewPhaseObj.value?.name))
// 非当前阶段只读（隐藏编辑/上传/删除入口）；2026-11-09 起资料/预算/合同操作=项目成员级（负责人/创建人/协助人）
const authStoreForDetail = useAuthStore()
const isMember = computed(() => {
  const d = detail.value
  if (!d || !d.id) return false
  if (d.canManage || d.canAdvance) return true
  const uid = authStoreForDetail.id
  return Array.isArray(d.assistantIds) && d.assistantIds.includes(uid)
})
const canOperate = computed(() => isMember.value)
const canOperatePhase = computed(() => canOperate.value && isViewingCurrent.value)

function phaseState(p) {
  if (!p || !currentPhase.value) return 'future'
  const s = Number(p.sort ?? 0)
  const c = Number(currentPhase.value.sort ?? 0)
  if (s < c) return 'done'
  if (s === c) return 'current'
  return 'future'
}
function stepClass(p) {
  const st = phaseState(p)
  return {
    done: st === 'done',
    current: st === 'current',
    future: st === 'future',
    viewing: String(p.id) === String(viewPhaseId.value)
  }
}
function setViewPhase(p) {
  viewPhaseId.value = String(p.id)
}

function docsOfPhase(phaseId, type) {
  return (detail.value.docs || []).filter(
    (d) => Number(d.phaseId) === Number(phaseId) && (type === 'FILE' ? !d.isRichText : d.isRichText)
  )
}
// 相关附件仅真实文件附件（排除富文本存档，富文本在阶段资料区展示）
const allDocs = computed(() => (detail.value.docs || []).filter((d) => !d.isRichText)
  .sort((a, b) => Number(b.id) - Number(a.id)))

function richTextOf(phaseId) {
  return richOfPhase.value[phaseId] ?? ''
}

function isTemplateFulfilled(t) {
  if (t.templateType === 'FILE') return docsOfPhase(t.phaseId, 'FILE').length > 0
  if (t.templateType === 'RICH_TEXT') return docsOfPhase(t.phaseId, 'RICH_TEXT').length > 0
  if (t.templateType === 'BUDGET') return detail.value.budgetAmount !== null && detail.value.budgetAmount !== undefined
  if (t.templateType === 'EXPERT') return (detail.value.experts || []).length > 0
  if (t.templateType === 'SUPPLIER') return (detail.value.suppliers || []).length > 0
  return true
}

// 待处理任务 → 解决入口映射（复用现有弹窗/入口）
const SCHEDULE_UPLOAD_TASK = '上传日程'
function resolveLabel(m) {
  if (m === BUDGET_QUOTE_TASK) return '去保存报价单'
  if (m === '保存阶段表单') return '去填写表单'
  if (m.startsWith('上传串场 PPT')) return '去制作串场'
  if (m === SCHEDULE_UPLOAD_TASK) return '去上传日程'
  if (m.startsWith('专家匹配入库')) return '去匹配专家'
  if (m.includes('附件')) return '去上传附件'
  if (m.includes('文本')) return '去填写文本'
  return '去处理'
}
async function resolveMissing(m) {
  if (m === BUDGET_QUOTE_TASK) {
    viewPhaseId.value = String(detail.value.phaseId)
    await nextTick()
    document.getElementById('meeting-budget-panel')?.scrollIntoView({ block: 'center' })
    return
  }
  // 日程阶段待办（2026-11-17）：定位到日程上传行 / 参会专家匹配表
  if (m === SCHEDULE_UPLOAD_TASK || m.startsWith('专家匹配入库')) {
    viewPhaseId.value = String(detail.value.phaseId)
    await nextTick()
    const anchor = m === SCHEDULE_UPLOAD_TASK ? 'schedule-upload' : 'schedule-speakers'
    document.getElementById(anchor)?.scrollIntoView({ block: 'center' })
    return
  }
  if (m.includes('文本')) {
    // 定位到当前阶段富文本编辑区
    viewPhaseId.value = String(detail.value.phaseId)
    const t = (currentPhase.value?.templates || []).find((x) => x.templateType === 'RICH_TEXT' && x.required === 1)
    if (t) editRich(currentPhase.value.id, t.templateId)
    return
  }
  viewPhaseId.value = String(detail.value.phaseId)
  await nextTick()
  document.getElementById('project-stage-materials')?.scrollIntoView({ block: 'start' })
}


// ---- 阶段操作：推进（仅相邻+材料校验）/ 回退（相邻/跨级，原因必填） ----
function phaseReached(p) {
  const c = currentPhase.value
  if (!c) return true
  return Number(p.sort ?? 0) <= Number(c.sort ?? 0)
}
const nextPhase = computed(() => {
  const c = currentPhase.value
  if (!c) return null
  return (detail.value.phases || []).find((p) => Number(p.sort ?? 0) === Number(c.sort ?? 0) + 1) || null
})
const hasNextPhase = computed(() => !!nextPhase.value)
const missingRequiredTemplates = computed(() => detail.value.missingRequiredTemplates || [])
const requiredSubmitted = computed(() => missingRequiredTemplates.value.length === 0)
const missingRequiredTip = computed(() => `当前阶段必填材料未提交：${missingRequiredTemplates.value.join('、')}`)

async function confirmAdvance() {
  const next = nextPhase.value
  if (!next) return
  try {
    await ElMessageBox.confirm(`确认将项目推进到「${next.name}」阶段？`, '推进下一阶段', {
      type: 'warning',
      confirmButtonText: '确认推进',
      cancelButtonText: '再想想'
    })
  } catch (e) {
    return
  }
  await changePhase(projectId, next.id)
  ElMessage.success(`已推进到「${next.name}」阶段`)
  fetchDetail()
}

// ---- 回退阶段（REQ-20261102 需求三：相邻/跨级均允许，原因必填） ----
const rollbackVisible = ref(false)
const rollbackTargetId = ref(null)
const rollbackReason = ref('')
const rollingBack = ref(false)

const rollbackCandidates = computed(() => {
  const c = currentPhase.value
  if (!c) return []
  return (detail.value.phases || [])
    .filter((p) => Number(p.sort ?? 0) < Number(c.sort ?? 0))
    .sort((a, b) => Number(b.sort ?? 0) - Number(a.sort ?? 0))
    .map((p) => ({ ...p, skipped: Number(c.sort ?? 0) - Number(p.sort ?? 0) - 1 }))
})
const rollbackTarget = computed(() => rollbackCandidates.value.find((p) => p.id === rollbackTargetId.value) || null)
const rollbackHint = computed(() => {
  const t = rollbackTarget.value
  if (!t) return ''
  const skipped = Number(currentPhase.value.sort ?? 0) - Number(t.sort ?? 0) - 1
  return skipped > 0
    ? `跨级回退：${currentPhase.value.name} → ${t.name}，将跳过 ${skipped} 个阶段；已提交资料保留。`
    : `相邻回退：${currentPhase.value.name} → ${t.name}；已提交资料保留，可再次推进。`
})

function openRollback() {
  rollbackTargetId.value = null
  rollbackReason.value = ''
  rollbackVisible.value = true
}

async function doRollback() {
  const t = rollbackTarget.value
  if (!t) return
  if (!rollbackReason.value.trim()) {
    ElMessage.warning('回退原因必填')
    return
  }
  rollingBack.value = true
  try {
    await changePhase(projectId, t.id, rollbackReason.value.trim())
    ElMessage.success(`已回退到「${t.name}」阶段`)
    rollbackVisible.value = false
    fetchDetail()
  } finally {
    rollingBack.value = false
  }
}

// ---- 概览头部「更多操作」 ----
function onHeadCommand(cmd) {
  if (cmd === 'rollback') openRollback()
  else if (cmd === 'cancel') openCancel()
  else if (cmd === 'restore') doRestore()
}

// 恢复已取消项目（Owner 2026-11-10：与取消对称；阶段保持不变）
async function doRestore() {
  await ElMessageBox.confirm('确认恢复项目「' + detail.value.name + '」？恢复后项目回到 ACTIVE 状态，阶段保持不变。', '恢复项目', { type: 'warning' })
  await restoreProject(projectId)
  ElMessage.success('项目已恢复')
  fetchDetail()
}

function openCancel() {
  cancelReason.value = ''
  cancelVisible.value = true
}
async function doCancel() {
  await cancelProject(projectId, cancelReason.value.trim())
  ElMessage.success('项目已取消')
  cancelVisible.value = false
  fetchDetail()
}

// ---- 编辑 ----
function openEdit() {
  editRow.value = detail.value
  editVisible.value = true
}

// ---- 资料 ----
const showUploading = ref(false)

async function uploadShowFile(file) {
  if (showUploading.value || !file.raw) return
  showUploading.value = true
  try {
    await uploadShowPpt(projectId, file.raw)
    await fetchDetail()
    ElMessage.success('串场 PPT 已归档')
  } finally {
    showUploading.value = false
  }
}

async function uploadDoc(phaseId, file) {
  if (!file || !file.raw) return
  await uploadProjectDoc(projectId, phaseId, file.raw)
  ElMessage.success('资料已上传')
  fetchDetail()
}
async function removeDoc(doc) {
  await deleteProjectDoc(projectId, doc.id)
  ElMessage.success('资料已删除（留痕）')
  fetchDetail()
}
function editRich(phaseId, templateId) {
  if (editingRich.value[phaseId]) {
    // REQ-20261102：补传 templateId（第一道防线；后端已加固不再抛 NumberFormatException）
    saveRichText(projectId, { phaseId, templateId, content: richDrafts.value[phaseId] || '' }).then(() => {
      ElMessage.success('文本已保存')
      editingRich.value[phaseId] = false
      fetchDetail()
    })
  } else {
    richDrafts.value[phaseId] = richOfPhase.value[phaseId] ?? ''
    editingRich.value[phaseId] = true
  }
}

onMounted(async () => {
  fetchDetail()
  ownerOptions.value = await listProjectOwners()
  const userData = await listProjectUsers()
  userOptions.value = userData || []
})

// ==================== 项目文件在线预览/下载（Owner 2026-11-12，参考知识星球口径） ====================
pdfjsLib.GlobalWorkerOptions.workerSrc = URL.createObjectURL(new Blob([pdfWorkerCode], { type: 'text/javascript' }))
const filePreviewVisible = ref(false)
const filePreviewLoading = ref(false)
const filePreviewMode = ref('') // image | excel | pdf | failed
const filePreviewTitle = ref('')
const filePreviewRow = ref(null)
const filePreviewImageUrl = ref('')
const filePreviewWorkbookFile = ref('')
const filePreviewBlobUrl = ref('')
// pdfDoc 必须 shallowRef：Vue Proxy 会破坏 pdfjs 私有字段（知识星球 20261103 同口径修复）
const filePdfDoc = shallowRef(null)
const filePreviewPage = ref(1)
const filePreviewNumPages = ref(0)
const filePreviewScale = ref(1)
const filePreviewCanvasRef = ref(null)
const filePreviewImageWidth = ref(0)
const filePreviewPanning = ref(false)
let filePreviewPan = null
const canZoomFilePreview = computed(() => filePreviewMode.value === 'pdf' || filePreviewMode.value === 'image')
let filePdfRenderTask = null
let filePdfRenderRevision = 0

function zoomFilePreview(direction) {
  filePreviewScale.value = Math.min(3, Math.max(0.3, (Math.round(filePreviewScale.value * 10) + direction) / 10))
}

function sizeFilePreviewImage(event) {
  const img = event.currentTarget
  const body = img.parentElement
  const style = getComputedStyle(body)
  const availableWidth = body.clientWidth - parseFloat(style.paddingLeft) - parseFloat(style.paddingRight)
  const availableHeight = body.clientHeight - parseFloat(style.paddingTop) - parseFloat(style.paddingBottom)
  filePreviewImageWidth.value = Math.min(
    img.naturalWidth,
    availableWidth > 0 ? availableWidth : img.naturalWidth,
    availableHeight > 0 ? availableHeight * img.naturalWidth / img.naturalHeight : img.naturalWidth
  )
}

function onFilePreviewWheel(event) {
  if (!canZoomFilePreview.value || filePreviewLoading.value || !event.deltaY) return
  event.preventDefault()
  zoomFilePreview(event.deltaY < 0 ? 1 : -1)
}

function startFilePreviewPan(event) {
  if (event.pointerType !== 'mouse' || event.button !== 0 || !canZoomFilePreview.value || filePreviewLoading.value) return
  // 原生滚动条仍可直接操作。
  if (!event.target.matches('.preview-image, .preview-canvas')) return
  const body = event.currentTarget
  if (body.scrollWidth <= body.clientWidth && body.scrollHeight <= body.clientHeight) return
  event.preventDefault()
  body.setPointerCapture(event.pointerId)
  filePreviewPan = {
    pointerId: event.pointerId,
    x: event.clientX,
    y: event.clientY,
    left: body.scrollLeft,
    top: body.scrollTop
  }
  filePreviewPanning.value = true
}

function moveFilePreviewPan(event) {
  const pan = filePreviewPan
  if (pan?.pointerId !== event.pointerId) return
  const body = event.currentTarget
  body.scrollLeft = pan.left + pan.x - event.clientX
  body.scrollTop = pan.top + pan.y - event.clientY
}

function stopFilePreviewPan(event) {
  if (filePreviewPan?.pointerId !== event.pointerId) return
  filePreviewPan = null
  filePreviewPanning.value = false
}

function fileExtOf(name) {
  const i = (name || '').lastIndexOf('.')
  return i < 0 ? '' : name.slice(i + 1).toLowerCase()
}

async function openFilePreview(d) {
  if (!d || !d.filePath) return
  filePreviewRow.value = d
  filePreviewTitle.value = d.fileName || d.filePath
  filePreviewMode.value = ''
  filePreviewImageUrl.value = ''
  filePreviewImageWidth.value = 0
  filePreviewBlobUrl.value = ''
  filePdfDoc.value = null
  filePreviewPage.value = 1
  filePreviewNumPages.value = 0
  filePreviewScale.value = 1
  filePreviewVisible.value = true
  filePreviewLoading.value = true
  try {
    // Excel 走表格分页预览（复用知识星球方案，2026-11-17）；其余 Office 仍转 PDF
    const ext = fileExtOf(d.fileName || d.filePath)
    if (ext === 'xls' || ext === 'xlsx') {
      filePreviewWorkbookFile.value = d.filePath
      filePreviewMode.value = 'excel'
      return
    }
    const response = await fetchProjectFile(d.filePath, false, true)
    const blob = response.data
    if (blob && blob.type === 'application/json') {
      let err = null
      try { err = JSON.parse(await blob.text()) } catch (e) { /* ignore */ }
      filePreviewMode.value = 'failed'
      ElMessage.warning(err && err.code === 'CONVERT_FAILED' ? '文档转换失败，可下载查看' : '预览失败，可下载查看')
      return
    }
    if (['png', 'jpg', 'jpeg', 'gif', 'webp', 'bmp'].includes(ext)) {
      filePreviewMode.value = 'image'
      filePreviewImageUrl.value = URL.createObjectURL(blob)
    } else {
      filePreviewMode.value = 'pdf'
      filePreviewBlobUrl.value = URL.createObjectURL(blob)
      const doc = await pdfjsLib.getDocument({ url: filePreviewBlobUrl.value }).promise
      filePdfDoc.value = doc
      filePreviewNumPages.value = doc.numPages
      await renderFilePdfPage()
    }
  } catch (e) {
    filePreviewMode.value = 'failed'
    ElMessage.error('预览失败，可下载查看')
  } finally {
    filePreviewLoading.value = false
  }
}

async function renderFilePdfPage() {
  const doc = filePdfDoc.value
  const canvas = filePreviewCanvasRef.value
  if (!doc || !canvas || !doc.numPages) return
  const revision = ++filePdfRenderRevision
  filePdfRenderTask?.cancel()
  const page = await doc.getPage(filePreviewPage.value)
  if (revision !== filePdfRenderRevision) return
  const viewport = page.getViewport({ scale: filePreviewScale.value })
  const dpr = window.devicePixelRatio || 1
  canvas.width = Math.floor(viewport.width * dpr)
  canvas.height = Math.floor(viewport.height * dpr)
  canvas.style.width = viewport.width + 'px'
  canvas.style.height = viewport.height + 'px'
  const task = page.render({
    canvasContext: canvas.getContext('2d'),
    viewport,
    transform: [dpr, 0, 0, dpr, 0, 0]
  })
  filePdfRenderTask = task
  try {
    await task.promise
  } catch (error) {
    if (error.name !== 'RenderingCancelledException') throw error
  } finally {
    if (filePdfRenderTask === task) filePdfRenderTask = null
  }
}

watch([filePreviewPage, filePreviewScale], async () => {
  if (filePreviewMode.value !== 'pdf') return
  try {
    await renderFilePdfPage()
  } catch {
    if (filePreviewVisible.value) filePreviewMode.value = 'failed'
  }
})

watch(filePreviewVisible, (visible) => {
  if (!visible) {
    filePdfRenderRevision++
    filePdfRenderTask?.cancel()
    if (filePdfDoc.value) { try { filePdfDoc.value.destroy() } catch (e) { /* ignore */ } filePdfDoc.value = null }
    if (filePreviewImageUrl.value) { URL.revokeObjectURL(filePreviewImageUrl.value); filePreviewImageUrl.value = '' }
    if (filePreviewBlobUrl.value) { URL.revokeObjectURL(filePreviewBlobUrl.value); filePreviewBlobUrl.value = '' }
    filePreviewRow.value = null
    filePreviewMode.value = ''
  }
})

async function downloadProjectFile(d) {
  if (!d || !d.filePath) return
  try {
    const response = await fetchProjectFile(d.filePath, true)
    downloadBlob(response.data, d.fileName || d.filePath)
  } catch (e) {
    ElMessage.error('下载失败，请重试')
  }
}
</script>

<style scoped>
.pd-page { max-width: 1440px; margin: 0 auto; }
/* ① 概览头部 */
.pd-head { display: flex; justify-content: space-between; gap: 16px; padding: 16px 20px; margin: 12px 0 16px; }
.pd-title-row { display: flex; align-items: center; gap: 10px; flex-wrap: wrap; }
.pd-name { font-size: 22px; font-weight: 700; color: var(--ui-text-main); }
.pd-meta { margin-top: 8px; display: flex; gap: 18px; flex-wrap: wrap; color: var(--ui-text-sub); font-size: 13px; }
.pd-cancel-tip { margin-top: 8px; color: var(--ui-danger); font-size: 13px; }
.pd-block-tip { margin-top: 8px; color: var(--ui-danger); font-size: 12px; background: #FEF0F0; border-radius: var(--ui-radius-sm); padding: 6px 10px; display: inline-block; }
.pd-actions { display: flex; align-items: flex-start; gap: 8px; flex-shrink: 0; }

/* 布局网格：主列 + 右侧辅助栏 */
.pd-grid { display: grid; grid-template-columns: 1fr 320px; gap: 16px; align-items: start; }
.pd-main { display: flex; flex-direction: column; gap: 16px; min-width: 0; }
.pd-compliance-source { margin-bottom: 6px; overflow-wrap: anywhere; }
.pd-compliance-switch { margin-bottom: 6px; }
.pd-compliance-items { list-style: none; margin: 0; padding: 0; max-height: 38vh; overflow-y: auto; }
.pd-compliance-items li { padding: 10px 2px; border-top: 1px solid var(--el-border-color-lighter); }
.pd-compliance-heading, .pd-compliance-footer { display: flex; align-items: center; flex-wrap: wrap; gap: 4px 8px; }
.pd-compliance-heading { justify-content: space-between; overflow-wrap: anywhere; }
.pd-compliance-content { margin: 5px 0; font-size: 13px; line-height: 1.55; white-space: pre-wrap; overflow-wrap: anywhere; }
.pd-compliance-footer { overflow-wrap: anywhere; }
.pd-compliance-footer .el-button { margin-left: auto; }
/* 吸顶时抵消主滚动区的内边距，贴齐应用顶栏下缘。 */
.pd-side { display: flex; flex-direction: column; gap: 16px; position: sticky; top: calc(-1 * var(--el-main-padding, 0px)); }

/* ② 核心数据卡 */





.pd-neg { color: var(--ui-danger); }

/* ③ Stepper */
.pd-stepper { padding: 16px 20px; }
.pd-sec-title { font-size: 15px; font-weight: 600; color: var(--ui-text-main); }
.pd-sec-title-row { display: flex; justify-content: space-between; align-items: center; margin-bottom: 10px; }
.steps-row { display: flex; align-items: stretch; gap: 10px; overflow-x: auto; list-style: none; margin: 12px 0 0; padding: 2px 2px 5px; }
.steps-row > li { flex: 0 0 auto; }
.step-item {
  display: flex; align-items: center; gap: 10px; height: 100%; min-width: 142px; padding: 9px 12px;
  background: var(--ui-fill); border: 1px solid var(--ui-border-weak);
  border-radius: var(--ui-radius-md); cursor: pointer; text-align: left; font: inherit;
}
.step-dot {
  width: 28px; height: 28px; flex: none; border-radius: 50%; display: grid; place-items: center;
  font-size: 12px; font-weight: 650; color: var(--ui-text-sub); background: var(--el-fill-color-dark);
}
.step-item.done .step-dot { color: var(--ui-success); background: var(--el-color-success-light-9); }
.step-item.current .step-dot { color: #fff; background: var(--ui-primary); }
.step-item.viewing { border-color: var(--ui-primary); background: var(--el-color-primary-light-9); }
.step-item:focus-visible { outline: 2px solid var(--ui-primary); outline-offset: 1px; }
.step-info { display: flex; flex-direction: column; gap: 3px; }
.step-name { font-size: 13px; font-weight: 650; color: var(--ui-text-main); white-space: nowrap; }
.step-state, .step-viewing { font-size: 12px; color: var(--ui-text-sub); white-space: nowrap; }
.step-item.current .step-state, .step-viewing { color: var(--ui-primary); }

.task-ops { margin-top: 12px; display: flex; gap: 8px; }

/* ⑤ 阶段资料区 */
.pd-phase { padding: 16px 20px; }
.template-block { border: 1px solid var(--ui-border-weak); border-radius: var(--ui-radius-md); padding: 12px; margin-bottom: 10px; background: var(--ui-fill); }
.template-title { font-weight: 600; margin-bottom: 8px; }
.unfilled-tip { color: var(--ui-danger); font-size: 12px; margin-left: 8px; }
.doc-row { display: flex; align-items: center; gap: 10px; padding: 4px 0; }
.doc-name { color: var(--ui-primary); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.doc-meta { color: var(--ui-text-muted); font-size: 12px; margin-left: auto; flex-shrink: 0; }
.upload-area { margin-top: 6px; }
.fin-attach-row { display: flex; align-items: center; gap: 10px; margin-top: 12px; padding-top: 10px; border-top: 1px dashed var(--ui-border-weak); }
.rich-view { white-space: pre-wrap; color: var(--ui-text-sub); }
.hint-text { color: var(--ui-text-muted); font-size: 13px; }

.sensitive { font-family: monospace; font-size: 12px; }








.ne-form-section { font-size: 13px; font-weight: 600; color: var(--ui-text-sub); background: var(--ui-fill); border-radius: var(--ui-radius-sm); padding: 6px 10px; margin-bottom: 12px; }

/* ⑥ 基本信息分组 */

.pd-info-card { padding: 14px 16px; }
.executor-names { color: var(--ui-text-muted); font-size: 12px; }
.remark-text { margin-top: 4px; color: var(--ui-text-muted, var(--ui-text-muted)); font-size: 13px; line-height: 1.8; white-space: pre-wrap; word-break: break-all; }

/* 库外专家建档 */
.link-tag { cursor: pointer; }

/* ④.5 日程与参会专家 */
.schedule-upload-row { display: flex; align-items: center; gap: 12px; flex-wrap: wrap; }
.schedule-file { color: var(--ui-text-main); font-size: 13px; }
.schedule-sub-title { font-size: 13px; font-weight: 600; color: var(--ui-text-sub); margin: 12px 0 6px; }
.manual-add-row { margin-top: 12px; display: flex; align-items: center; gap: 8px; flex-wrap: wrap; }

/* ⑧ 预算 */

.stat-box { text-align: center; padding: 8px 0; }
.stat-value { font-size: 20px; font-weight: 600; }

/* ⑨ 业务明细 */
.pd-block { padding: 16px 20px; }
.tl-row { display: flex; gap: 8px; }
.tl-user { font-weight: 600; }

/* ⑦ 右侧辅助栏 */
.side-card { padding: 14px 16px; }
.todo-item { display: flex; align-items: center; justify-content: space-between; padding: 5px 0; }
.todo-name { font-size: 13px; color: var(--ui-text-main); }
.side-done { color: var(--ui-success); font-size: 13px; }
.side-doc { display: flex; flex-direction: column; padding: 4px 0; border-bottom: 1px dashed var(--ui-border-weak); }
.side-doc-ops { display: flex; align-items: center; gap: 2px; }
.side-doc-ops .doc-meta { margin-left: auto; }
.preview-toolbar { display: flex; align-items: center; gap: 12px; padding: 8px 16px; border-bottom: 1px solid var(--ui-border-weak); }
.preview-nav { display: flex; align-items: center; gap: 8px; }
.preview-info { font-size: 13px; color: var(--el-text-color-secondary); }
.preview-body { padding: 16px; overflow: auto; height: calc(100% - 60px); }
.preview-body.is-pannable :is(.preview-image, .preview-canvas) { cursor: grab; }
.preview-body.is-panning, .preview-body.is-panning :is(.preview-image, .preview-canvas) { cursor: grabbing; user-select: none; }
.preview-image, .preview-canvas { display: block; max-width: none; margin-inline: auto; }
/* Excel 表格预览：宽度撑满抽屉，内层自滚动（复用 KnowledgeWorkbookPreview） */
.preview-excel { min-width: 0; }
.preview-canvas { box-shadow: 0 2px 12px rgba(0,0,0,.12); }
.side-log { padding: 6px 0; border-bottom: 1px dashed var(--ui-border-weak); }
.side-log-desc { font-size: 13px; color: var(--ui-text-main); }
.side-log-meta { font-size: 12px; color: var(--ui-text-muted); margin-top: 2px; }

/* 1366px 以下：辅助栏收窄 */
@media (max-width: 1366px) {
  .pd-grid { grid-template-columns: 1fr 300px; }
}
@media (max-width: 1100px) {
  .pd-grid { grid-template-columns: 1fr; }
  .pd-side { position: static; }


}
</style>
