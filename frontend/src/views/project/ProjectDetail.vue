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
          <span>大类：{{ detail.category === 'ONLINE' ? '线上' : '线下' }}</span>
          <span>类型：{{ detail.typeName || '-' }}</span>
          <span>负责人：{{ detail.ownerName || '-' }}</span>
          <span>计划时间：{{ detail.startDate || '-' }} ~ {{ detail.endDate || '-' }}</span>
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
        <el-button v-if="detail.canManage" @click="openEdit">编辑项目</el-button>
        <el-dropdown trigger="click" @command="onHeadCommand">
          <el-button>更多操作<el-icon style="margin-left: 4px"><ArrowDown /></el-icon></el-button>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item v-if="detail.canRollback" command="rollback">回退阶段</el-dropdown-item>
              <el-dropdown-item v-if="detail.canAdvance" command="cancel" divided>取消项目</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </div>
    </div>

    <div v-if="detail.id" class="pd-grid">
      <!-- 主内容列 -->
      <div class="pd-main">
        <!-- ② 核心数据卡 -->
        <div class="pd-stats">
          <div class="pd-stat ui-card">
            <div class="pd-stat-label">项目预算总额</div>
            <div class="pd-stat-value">¥ {{ fmt(budgetAmount) }}</div>
          </div>
          <div class="pd-stat ui-card">
            <div class="pd-stat-label">实际支出</div>
            <div class="pd-stat-value">¥ {{ fmt(actualTotal) }}</div>
            <div class="pd-stat-sub">专家劳务费 ¥ {{ fmt(expertFeeSum) }} + 供应商费用 ¥ {{ fmt(supplierFeeSum) }}</div>
          </div>
          <div class="pd-stat ui-card">
            <div class="pd-stat-label">预算余额</div>
            <div class="pd-stat-value" :class="{ 'pd-neg': budgetAmount > 0 && actualTotal > budgetAmount }">¥ {{ fmt(balance) }}</div>
          </div>
          <div class="pd-stat ui-card">
            <div class="pd-stat-label">预算使用率</div>
            <div class="pd-stat-value" :class="{ 'pd-neg': usageRate > 100 }">{{ usageRate }}%</div>
            <el-progress
              :percentage="Math.min(usageRate, 100)"
              :status="usageRate > 100 ? 'exception' : usageRate >= 80 ? 'warning' : 'success'"
              :stroke-width="8"
              style="margin-top: 6px"
            />
            <div v-if="usageRate > 100" class="pd-stat-sub pd-neg">已超支 ¥ {{ fmt(actualTotal - budgetAmount) }}</div>
          </div>
        </div>

        <!-- ③ 生命周期 Stepper（当前/查看双状态；未开始阶段点击查看空状态） -->
        <div class="pd-stepper ui-card">
          <div class="pd-sec-title">项目生命周期</div>
          <div class="steps-row">
            <template v-for="(p, idx) in sortedPhases" :key="p.id">
              <div
                class="step-item"
                :class="stepClass(p)"
                @click="setViewPhase(p)"
              >
                <div class="step-dot">
                  <span v-if="phaseState(p) === 'done'">✓</span>
                  <span v-else>{{ idx + 1 }}</span>
                </div>
                <div class="step-name">
                  {{ p.name }}
                  <span v-if="Number(p.id) === Number(detail.phaseId)" class="step-current-mark">当前</span>
                </div>
              </div>
              <div v-if="idx < sortedPhases.length - 1" class="step-line" :class="{ done: phaseState(sortedPhases[idx + 1]) !== 'future' }" />
            </template>
          </div>
          <div class="steps-legend">
            <span class="legend-item"><i class="dot dot-done" />已完成</span>
            <span class="legend-item"><i class="dot dot-current" />当前阶段（蓝）</span>
            <span class="legend-item"><i class="dot dot-future" />未开始</span>
            <span class="legend-item">光圈 = 正在查看的阶段</span>
          </div>
        </div>

        <!-- ④ 阶段任务卡 -->
        <div class="pd-task ui-card">
          <div class="pd-sec-title-row">
            <span class="pd-sec-title">阶段任务 · {{ viewPhaseObj?.name || '-' }}</span>
            <span :class="'ui-badge ' + taskBadgeClass">{{ taskBadgeText }}</span>
          </div>
          <div class="task-grid">
            <div class="task-cell">
              <div class="task-label">必填配置</div>
              <div class="task-value">{{ requiredDoneCount }} / {{ requiredTotal }} 已完成</div>
            </div>
            <div class="task-cell">
              <div class="task-label">阶段负责人</div>
              <div class="task-value">{{ detail.ownerName || '-' }}<span class="task-scope">（项目级：负责人 + 执行人）</span></div>
            </div>
          </div>
          <template v-if="isViewingCurrent">
            <div v-if="missingRequiredTemplates.length" class="task-block">
              <div class="task-label">待处理任务（阻塞推进）</div>
              <div v-for="(m, i) in missingRequiredTemplates" :key="i" class="task-item">
                <span class="task-item-name">{{ m }}</span>
                <el-button link type="primary" size="small" @click="resolveMissing(m)">{{ resolveLabel(m) }} →</el-button>
              </div>
              <div class="pd-block-tip" style="margin-top: 8px">{{ missingRequiredTip }}</div>
            </div>
            <div v-else class="task-done-line">本阶段必填配置已完成</div>
          </template>
          <div v-else-if="phaseState(viewPhaseObj) === 'done'" class="task-done-line">该阶段已完成（只读查看）</div>
          <div class="task-ops">
            <el-button v-if="detail.canAdvance && hasNextPhase && isViewingCurrent" type="primary" size="small" :disabled="!requiredSubmitted" @click="confirmAdvance">推进下一阶段</el-button>
            <el-button v-if="detail.canRollback" size="small" @click="openRollback">回退阶段</el-button>
          </div>
        </div>

        <!-- ⑤ 阶段资料区（随查看阶段切换） -->
        <div class="pd-phase ui-card">
          <div class="pd-sec-title-row">
            <span class="pd-sec-title">阶段资料 · {{ viewPhaseObj?.name || '-' }}</span>
            <span v-if="!isViewingCurrent" class="ui-badge ui-badge-info">非当前阶段（只读）</span>
          </div>

          <template v-if="phaseState(viewPhaseObj) === 'future'">
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
                  <a :href="d.filePath" target="_blank" class="doc-name">{{ d.fileName }}</a>
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
                <div class="hint-text">预算金额：¥ {{ fmt(budgetAmount) }}（点击「登记预算金额」维护）</div>
                <el-button v-if="canOperatePhase" size="small" plain style="margin-top: 6px" @click="openBudget">登记预算金额</el-button>
              </div>

              <!-- 专家/供应商模板 -->
              <div v-else>
                <div class="hint-text">{{ t.templateType === 'EXPERT' ? '项目专家名单（全局一份）见下方“专家名单”卡' : '项目供应商名单（全局一份）见下方“供应商名单”卡' }}</div>
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

        <!-- ⑥ 基本信息（4 分组卡） -->
        <div class="pd-info-grid">
          <div class="ui-card pd-info-card">
            <div class="pd-sec-title">项目属性</div>
            <div class="info-row"><span class="info-k">项目名称</span><span class="info-v">{{ detail.name }}</span></div>
            <div class="info-row"><span class="info-k">大类</span><span class="info-v">{{ detail.category === 'ONLINE' ? '线上' : '线下' }}</span></div>
            <div class="info-row"><span class="info-k">类型</span><span class="info-v">{{ detail.typeName || '-' }}</span></div>
          </div>
          <div class="ui-card pd-info-card">
            <div class="pd-sec-title">项目人员</div>
            <div class="info-row"><span class="info-k">负责人</span><span class="info-v"><span class="avatar" :style="avatarStyle(detail.ownerName)">{{ firstChar(detail.ownerName) }}</span>{{ detail.ownerName || '-' }}</span></div>
            <div class="info-row"><span class="info-k">执行人</span><span class="info-v">
              <template v-if="executorList.length">
                <span v-for="(n, i) in executorList.slice(0, 4)" :key="i" class="avatar" :style="avatarStyle(n)">{{ firstChar(n) }}</span>
                <span v-if="executorList.length > 4" class="avatar avatar-more">+{{ executorList.length - 4 }}</span>
              </template>
              <template v-else>-</template>
              <span class="executor-names">{{ detail.executorNames || '' }}</span>
            </span></div>
          </div>
          <div class="ui-card pd-info-card">
            <div class="pd-sec-title">项目时间</div>
            <div class="info-row"><span class="info-k">计划开始</span><span class="info-v">{{ detail.startDate || '-' }}</span></div>
            <div class="info-row"><span class="info-k">计划结束</span><span class="info-v">{{ detail.endDate || '-' }}</span></div>
          </div>
          <div class="ui-card pd-info-card">
            <div class="pd-sec-title">项目状态</div>
            <div class="info-row"><span class="info-k">当前阶段</span><span class="info-v"><span class="ui-badge ui-badge-primary">{{ detail.phaseName || '-' }}</span></span></div>
            <div class="info-row"><span class="info-k">生命周期</span><span class="info-v"><span :class="detail.status === 'CANCELED' ? 'ui-badge ui-badge-danger' : 'ui-badge ui-badge-success'">{{ detail.status === 'CANCELED' ? '已取消' : '有效' }}</span></span></div>
            <div class="info-row"><span class="info-k">创建人</span><span class="info-v">{{ detail.creatorName || '-' }}</span></div>
          </div>
        </div>

        <!-- ⑧ 预算与实际支出 -->
        <div class="ui-card pd-budget">
          <div class="pd-sec-title-row">
            <span class="pd-sec-title">预算与实际支出</span>
            <el-button v-if="detail.canAdvance" link type="primary" @click="openBudget">登记预算金额</el-button>
          </div>
          <template v-if="Number(budgetAmount) > 0">
            <el-row :gutter="16">
              <el-col :span="8">
                <div class="stat-box"><div class="stat-label">专家劳务费合计</div><div class="stat-value">¥ {{ fmt(expertFeeSum) }}</div></div>
              </el-col>
              <el-col :span="8">
                <div class="stat-box"><div class="stat-label">供应商费用合计（实际）</div><div class="stat-value">¥ {{ fmt(supplierFeeSum) }}</div></div>
              </el-col>
              <el-col :span="8">
                <div class="stat-box"><div class="stat-label">剩余预算</div><div class="stat-value" :class="{ 'pd-neg': balance < 0 }">¥ {{ fmt(balance) }}</div></div>
              </el-col>
            </el-row>
          </template>
          <template v-else>
            <el-empty description="尚未登记预算" :image-size="60">
              <el-button v-if="detail.canAdvance" type="primary" plain @click="openBudget">登记预算金额</el-button>
            </el-empty>
          </template>
        </div>

        <!-- ⑨ 业务明细：专家名单 -->
        <div class="ui-card pd-block">
          <div class="pd-sec-title-row">
            <span class="pd-sec-title">专家名单（全局一份）</span>
            <span>
              <el-button v-if="detail.canManage" link type="success" style="margin-right: 12px" @click="openNewExpert">新建专家卡</el-button>
              <el-button v-if="detail.canManage" link type="primary" @click="openAssignExpert">调派专家</el-button>
            </span>
          </div>
          <el-table :data="detail.experts" stripe>
            <el-table-column prop="name" label="姓名" width="100" />
            <el-table-column prop="unit" label="单位" min-width="140" show-overflow-tooltip />
            <el-table-column prop="title" label="职称" width="100" />
            <el-table-column prop="role" label="角色/分工" width="110" />
            <el-table-column label="劳务费" width="110">
              <template #default="{ row }">
                <span v-if="!editingFeeExpert || editingFeeExpert.id !== row.id">{{ fmt(row.fee) }}</span>
                <el-input-number v-else v-model="feeDraft" :min="0" :precision="2" size="small" />
              </template>
            </el-table-column>
            <el-table-column label="身份证/银行卡" min-width="180">
              <template #default="{ row }">
                <span class="sensitive">{{ row.idCard || '-' }} / {{ row.bankCard || '-' }}</span>
                <span v-if="!detail.canAdvance" class="mask-tip">（负责人/执行人可见明文）</span>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="160" fixed="right">
              <template #default="{ row }">
                <template v-if="detail.canManage">
                  <el-button
                    v-if="editingFeeExpert && editingFeeExpert.id === row.id"
                    link type="primary"
                    @click="doSaveExpertFee(row)"
                  >保存</el-button>
                  <el-button v-else link type="primary" @click="startEditFee(row)">登记劳务费</el-button>
                </template>
                <el-popconfirm title="确认移出该专家？（留痕）" @confirm="removeExpert(row)">
                  <template #reference>
                    <el-button v-if="detail.canManage" link type="danger">移出</el-button>
                  </template>
                </el-popconfirm>
              </template>
            </el-table-column>
          </el-table>
          <el-empty v-if="!(detail.experts || []).length" description="尚未调派专家" :image-size="60" />
        </div>

        <!-- 业务明细：供应商名单 -->
        <div class="ui-card pd-block">
          <div class="pd-sec-title-row">
            <span class="pd-sec-title">供应商名单（全局一份）</span>
            <el-button v-if="detail.canManage" link type="primary" @click="openAssignSupplier">关联供应商</el-button>
          </div>
          <el-table :data="detail.suppliers" stripe>
            <el-table-column prop="name" label="名称" min-width="140" show-overflow-tooltip />
            <el-table-column prop="typeName" label="类型" width="100" />
            <el-table-column prop="purpose" label="用途（自动带出）" min-width="130" />
            <el-table-column label="费用" width="120">
              <template #default="{ row }">
                <span v-if="!editingFeeSupplier || editingFeeSupplier.id !== row.id">{{ fmt(row.fee) }}</span>
                <el-input-number v-else v-model="supplierFeeDraft" :min="0" :precision="2" size="small" />
              </template>
            </el-table-column>
            <el-table-column label="操作" width="130" fixed="right">
              <template #default="{ row }">
                <template v-if="detail.canManage">
                  <el-button
                    v-if="editingFeeSupplier && editingFeeSupplier.id === row.id"
                    link type="primary"
                    @click="doSaveSupplierFee(row)"
                  >保存</el-button>
                  <el-button v-else link type="primary" @click="startEditSupplierFee(row)">填费用</el-button>
                  <el-popconfirm title="确认移出该供应商？（留痕）" @confirm="removeSupplier(row)">
                    <template #reference>
                      <el-button link type="danger">移出</el-button>
                    </template>
                  </el-popconfirm>
                </template>
              </template>
            </el-table-column>
          </el-table>
          <el-empty v-if="!(detail.suppliers || []).length" description="尚未关联供应商" :image-size="60" />
        </div>

        <!-- 业务明细：结算合同 -->
        <div class="ui-card pd-block">
          <div class="pd-sec-title-row">
            <span class="pd-sec-title">结算合同</span>
            <el-button v-if="detail.canAdvance" link type="primary" @click="openContract">登记合同</el-button>
          </div>
          <el-table :data="detail.contracts" stripe>
            <el-table-column prop="expertName" label="专家" width="90" />
            <el-table-column label="身份证/银行卡" min-width="200">
              <template #default="{ row }">
                <span class="sensitive">{{ row.idCard || '-' }} / {{ row.bankCard || '-' }}</span>
              </template>
            </el-table-column>
            <el-table-column label="劳务费" width="100">
              <template #default="{ row }">{{ fmt(row.fee) }}</template>
            </el-table-column>
            <el-table-column label="合同文件" min-width="120">
              <template #default="{ row }">
                <a v-if="row.filePath" :href="row.filePath" target="_blank">下载</a>
                <span v-else>-</span>
              </template>
            </el-table-column>
            <el-table-column label="状态" width="90">
              <template #default="{ row }">
                <el-tag size="small" :type="statusType(row.status)">{{ statusText(row.status) }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="210" fixed="right">
              <template #default="{ row }">
                <template v-if="detail.canAdvance">
                  <el-upload
                    v-if="!row.filePath"
                    :show-file-list="false"
                    :auto-upload="false"
                    :on-change="(f) => uploadContract(row, f)"
                    style="display: inline-block; margin-right: 8px"
                  >
                    <el-button link type="primary">上传文件</el-button>
                  </el-upload>
                  <el-button v-if="row.filePath && row.status === 'DRAFT'" link type="success" @click="doSubmitApproval(row)">发起审批</el-button>
                  <el-button v-if="row.status === 'DRAFT'" link type="warning" @click="openSensitive(row)">改敏感字段</el-button>
                </template>
              </template>
            </el-table-column>
          </el-table>
          <el-empty v-if="!(detail.contracts || []).length" description="尚未登记合同" :image-size="60" />
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

      <!-- ⑦ 右侧辅助栏 -->
      <div class="pd-side">
        <div class="ui-card side-card">
          <div class="pd-sec-title">项目成员</div>
          <div class="member-row"><span class="avatar" :style="avatarStyle(detail.ownerName)">{{ firstChar(detail.ownerName) }}</span>{{ detail.ownerName || '-' }}<span class="ui-badge ui-badge-primary" style="margin-left: auto">负责人</span></div>
          <div v-for="(n, i) in executorList" :key="i" class="member-row">
            <span class="avatar" :style="avatarStyle(n)">{{ firstChar(n) }}</span>{{ n }}<span class="ui-badge ui-badge-info" style="margin-left: auto">执行人</span>
          </div>
          <div v-if="!executorList.length" class="hint-text" style="padding-left: 34px">暂无其他执行人</div>
        </div>

        <div class="ui-card side-card">
          <div class="pd-sec-title">当前待办</div>
          <template v-if="missingRequiredTemplates.length">
            <div v-for="(m, i) in missingRequiredTemplates" :key="i" class="todo-item">
              <span class="todo-name">{{ m }}</span>
              <el-button link type="primary" size="small" @click="resolveMissing(m)">{{ resolveLabel(m) }} →</el-button>
            </div>
          </template>
          <div v-else class="side-done">当前阶段必填配置已完成</div>
        </div>

        <div class="ui-card side-card">
          <div class="pd-sec-title">相关附件（{{ allDocs.length }}）</div>
          <template v-if="allDocs.length">
            <div v-for="d in allDocs.slice(0, 10)" :key="d.id" class="side-doc">
              <a :href="d.filePath" target="_blank" class="doc-name" :title="d.fileName">{{ d.fileName }}</a>
              <span class="doc-meta">{{ d.createTime }}</span>
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
      :types="typeOptions"
      :phases="detail.phases"
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

    <!-- 调派专家（参照专家库视觉：搜索 + 卡片行点选） -->
    <el-dialog v-model="assignExpertVisible" title="从专家库调派专家" width="640px" destroy-on-close>
      <el-input v-model="expertKeyword" placeholder="搜索专家库（姓名/单位）" clearable style="margin-bottom: 10px" @keyup.enter="searchExperts">
        <template #append><el-button @click="searchExperts">搜索</el-button></template>
      </el-input>
      <div class="expert-pick-list">
        <div
          v-for="e in expertCandidates"
          :key="e.id"
          class="expert-pick-row"
          :class="{ picked: assignExpertId === e.id }"
          @click="assignExpertId = e.id"
        >
          <span class="avatar" :style="avatarStyle(e['姓名'] || e.name)">{{ (e['姓名'] || e.name || '?').charAt(0) }}</span>
          <div class="expert-pick-info">
            <div class="expert-pick-name">{{ e['姓名'] || e.name || '未知' }}</div>
            <div class="expert-pick-meta">{{ e['单位'] || e.unit || '无单位' }}<template v-if="e['职称']"> · {{ e['职称'] }}</template></div>
          </div>
          <span v-if="assignExpertId === e.id" class="expert-pick-check">✓</span>
        </div>
        <div v-if="!expertCandidates.length" class="hint-text" style="padding: 12px 4px">未找到专家，可尝试其他关键词或新建专家卡</div>
      </div>
      <el-input v-model="assignExpertRole" placeholder="角色/分工（主持人、演讲嘉宾、评审专家等）" style="margin-top: 10px" />
      <div class="hint-text">未找到目标专家？可在下方新建专家卡。</div>
      <template #footer>
        <el-button @click="assignExpertVisible = false">取消</el-button>
        <el-button type="primary" :disabled="!assignExpertId" @click="doAssignExpert">调派</el-button>
      </template>
    </el-dialog>

    <!-- 新建专家卡（视觉对齐专家库「新增专家」表单：760px/label-width 110px/分组标题） -->
    <el-dialog v-model="newExpertVisible" title="新建专家卡（保存后自动关联本项目）" width="760px" destroy-on-close>
      <el-form :model="newExpert" label-width="110px" class="edit-form">
        <div class="ne-form-section">基本信息</div>
        <el-row :gutter="12">
          <el-col :span="12"><el-form-item label="姓名" required><el-input v-model="newExpert.name" placeholder="请输入姓名" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="职称" required><el-input v-model="newExpert.title" placeholder="如：主任医师 / 高级工程师" /></el-form-item></el-col>
        </el-row>
        <el-form-item label="单位" required><el-input v-model="newExpert.unit" placeholder="请输入工作单位" /></el-form-item>
        <div class="ne-form-section">简介</div>
        <el-form-item label="个人简介" required>
          <el-input v-model="newExpert.profile" type="textarea" :rows="5" placeholder="研究方向、擅长领域、代表经历等" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="newExpertVisible = false">取消</el-button>
        <el-button type="primary" :disabled="!newExpert.name || !newExpert.unit || !newExpert.title || !newExpert.profile" @click="doNewExpert">保存并关联</el-button>
      </template>
    </el-dialog>

    <!-- 关联供应商 -->
    <el-dialog v-model="assignSupplierVisible" title="关联供应商（用途自动带出）" width="560px" destroy-on-close>
      <el-input v-model="supplierKeyword" placeholder="搜索供应商库（名称）" clearable style="margin-bottom: 10px" @keyup.enter="searchSuppliers" />
      <el-select v-model="assignSupplierId" placeholder="选择供应商" filterable style="width: 100%">
        <el-option v-for="s in supplierCandidates" :key="s.id" :label="`${s.name}（${s.typeName || '-'}）`" :value="s.id" />
      </el-select>
      <div class="hint-text">用途按供应商类型自动带出，无需手动填写。</div>
      <template #footer>
        <el-button @click="assignSupplierVisible = false">取消</el-button>
        <el-button type="primary" :disabled="!assignSupplierId" @click="doAssignSupplier">关联</el-button>
      </template>
    </el-dialog>

    <!-- 登记合同 -->
    <el-dialog v-model="contractVisible" title="登记合同" width="560px" destroy-on-close>
      <el-form label-width="100px">
        <el-form-item label="选择专家" required>
          <el-select v-model="contractForm.expertId" placeholder="从项目专家名单选择" style="width: 100%" @change="onPickContractExpert">
            <el-option v-for="e in detail.experts" :key="e.id" :label="e.name" :value="e.expertId" />
          </el-select>
        </el-form-item>
        <el-form-item label="身份证号">
          <el-input v-model="contractForm.idCard" placeholder="自动带出专家库档案，可修改（回写留痕）" />
        </el-form-item>
        <el-form-item label="银行卡号">
          <el-input v-model="contractForm.bankCard" placeholder="自动带出专家库档案，可修改（回写留痕）" />
        </el-form-item>
        <el-form-item label="劳务费">
          <el-input-number v-model="contractForm.fee" :min="0" :precision="2" style="width: 100%" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="contractVisible = false">取消</el-button>
        <el-button type="primary" :disabled="!contractForm.expertId" @click="doCreateContract">保存合同（A：信息）</el-button>
      </template>
    </el-dialog>

    <!-- 敏感字段修改 -->
    <el-dialog v-model="sensitiveVisible" title="修改合同敏感字段（同步回写专家库）" width="460px" destroy-on-close>
      <el-form label-width="90px">
        <el-form-item label="身份证号">
          <el-input v-model="sensitiveForm.idCard" placeholder="修改后将同步写回专家档案并留痕" />
        </el-form-item>
        <el-form-item label="银行卡号">
          <el-input v-model="sensitiveForm.bankCard" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="sensitiveVisible = false">取消</el-button>
        <el-button type="primary" @click="doUpdateSensitive">保存并回写</el-button>
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

    <!-- 预算登记 -->
    <el-dialog v-model="budgetVisible" title="登记预算金额" width="420px" destroy-on-close>
      <el-input-number v-model="budgetDraft" :min="0" :precision="2" style="width: 100%" />
      <template #footer>
        <el-button @click="budgetVisible = false">取消</el-button>
        <el-button type="primary" @click="doBudget">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { ArrowDown } from '@element-plus/icons-vue'
import ProjectEdit from './ProjectEdit.vue'
import request from '../../api/request'
import {
  getProjectDetail, changePhase, cancelProject,
  addProjectExpert, removeProjectExpert, createExpertCard,
  addProjectSupplier, removeProjectSupplier,
  uploadProjectDoc, deleteProjectDoc, saveRichText,
  registerBudget,
  createProjectContract, uploadContractFile, submitContractApproval, updateContractSensitive,
  saveExpertFee, saveSupplierFee,
  listProjectTypes, listProjectPhases, listProjectOwners, listProjectExecutors
} from '../../api/project'

const route = useRoute()
const projectId = route.params.id
const loading = ref(false)
const detail = ref({})
// 查看阶段（Stepper 点击切换，仅影响展示层，不触发流转）；初始=当前阶段
const viewPhaseId = ref('')

const editVisible = ref(false)
const editRow = ref(null)
const ownerOptions = ref([])
const typeOptions = ref([])
const userOptions = ref([])

const cancelVisible = ref(false)
const cancelReason = ref('')
const budgetVisible = ref(false)
const budgetDraft = ref(0)

const editingRich = ref({})
const richDrafts = ref({})
const richOfPhase = ref({})

const editingFeeExpert = ref(null)
const feeDraft = ref(0)
const editingFeeSupplier = ref(null)
const supplierFeeDraft = ref(0)

const assignExpertVisible = ref(false)
const expertKeyword = ref('')
const expertCandidates = ref([])
const assignExpertId = ref(null)
const assignExpertRole = ref('')

const newExpertVisible = ref(false)
const newExpert = reactive({ name: '', unit: '', title: '', profile: '' })

const assignSupplierVisible = ref(false)
const supplierKeyword = ref('')
const supplierCandidates = ref([])
const assignSupplierId = ref(null)

const contractVisible = ref(false)
const contractForm = reactive({ expertId: null, idCard: '', bankCard: '', fee: 0 })
const sensitiveVisible = ref(false)
const sensitiveForm = reactive({ contractId: null, idCard: '', bankCard: '' })

// 财务数据（detail.budget.budgetAmount / actualTotal 由后端 budgetOf 返回）
const budgetAmount = computed(() => detail.value.budget?.budgetAmount ?? detail.value.budgetAmount ?? 0)
const expertFeeSum = computed(() => detail.value.budget?.expertFee ?? (detail.value.experts || []).reduce((s, e) => s + (Number(e.fee) || 0), 0))
const supplierFeeSum = computed(() => detail.value.budget?.supplierFee ?? (detail.value.suppliers || []).reduce((s, e) => s + (Number(e.fee) || 0), 0))
const actualTotal = computed(() => detail.value.budget?.actualTotal ?? (Number(expertFeeSum.value) + Number(supplierFeeSum.value)))
const balance = computed(() => Number(budgetAmount.value) - Number(actualTotal.value))
const usageRate = computed(() => {
  const b = Number(budgetAmount.value)
  if (!b) return 0
  return Math.round((Number(actualTotal.value) / b) * 100)
})

const sortedPhases = computed(() => [...(detail.value.phases || [])].sort((a, b) => Number(a.sort ?? 0) - Number(b.sort ?? 0)))

async function fetchDetail() {
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
  } finally {
    loading.value = false
  }
}

// ---- 生命周期 Stepper：三态与查看阶段 ----
const currentPhase = computed(() => (detail.value.phases || []).find((p) => Number(p.id) === Number(detail.value.phaseId)) || null)
const viewPhaseObj = computed(() => (detail.value.phases || []).find((p) => String(p.id) === String(viewPhaseId.value)) || currentPhase.value)
const isViewingCurrent = computed(() => viewPhaseObj.value && Number(viewPhaseObj.value.id) === Number(detail.value.phaseId))
// 非当前阶段只读（隐藏编辑/上传/删除入口）
const canOperatePhase = computed(() => detail.value.canAdvance && isViewingCurrent.value)

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

const executorList = computed(() => {
  const n = detail.value.executorNames
  if (Array.isArray(n)) return n.filter(Boolean)
  return String(n || '').split(/[,，、;；\s]+/).filter(Boolean)
})

function firstChar(n) {
  return (n || '?').trim().charAt(0) || '?'
}
const AVATAR_COLORS = ['#409EFF', '#67C23A', '#E6A23C', '#F56C6C', '#909399', '#337ECC']
function avatarStyle(n) {
  const idx = String(n || '').length % AVATAR_COLORS.length
  return { background: AVATAR_COLORS[idx] }
}

function docsOfPhase(phaseId, type) {
  return (detail.value.docs || []).filter(
    (d) => Number(d.phaseId) === Number(phaseId) && (type === 'FILE' ? !d.isRichText : d.isRichText)
  )
}
// 相关附件仅真实文件附件（排除富文本存档，富文本在阶段资料区展示）
const allDocs = computed(() => (detail.value.docs || []).filter((d) => !d.isRichText))

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

// 阶段任务卡：必填完成数（查看阶段）
const requiredTotal = computed(() => (viewPhaseObj.value?.templates || []).filter((t) => t.required === 1).length)
const requiredDoneCount = computed(() => (viewPhaseObj.value?.templates || []).filter((t) => t.required === 1 && isTemplateFulfilled(t)).length)
const taskBadgeText = computed(() => {
  const st = phaseState(viewPhaseObj.value)
  return { done: '已完成', current: '进行中', future: '未开始' }[st] || '-'
})
const taskBadgeClass = computed(() => {
  const st = phaseState(viewPhaseObj.value)
  return { done: 'ui-badge-success', current: 'ui-badge-primary', future: 'ui-badge-info' }[st] || 'ui-badge-info'
})

// 待处理任务 → 解决入口映射（复用现有弹窗/入口）
function resolveLabel(m) {
  if (m.includes('预算')) return '去登记预算'
  if (m.includes('附件')) return '去上传附件'
  if (m.includes('专家')) return '去调派专家'
  if (m.includes('供应商')) return '去关联供应商'
  if (m.includes('文本')) return '去填写文本'
  return '去处理'
}
function resolveMissing(m) {
  if (m.includes('预算')) { openBudget(); return }
  if (m.includes('专家')) { openAssignExpert(); return }
  if (m.includes('供应商')) { openAssignSupplier(); return }
  if (m.includes('文本')) {
    // 定位到当前阶段富文本编辑区
    viewPhaseId.value = String(detail.value.phaseId)
    const t = (currentPhase.value?.templates || []).find((x) => x.templateType === 'RICH_TEXT' && x.required === 1)
    if (t) editRich(currentPhase.value.id, t.templateId)
    return
  }
  // 附件等：切到当前阶段（资料区即上传入口）
  viewPhaseId.value = String(detail.value.phaseId)
}

function fmt(v) {
  if (v === null || v === undefined || v === '') return '0'
  return Number(v).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}

function statusText(s) {
  return { DRAFT: '草稿', PENDING: '审批中', APPROVED: '已通过', REJECTED: '已驳回' }[s] || s
}
function statusType(s) {
  return { DRAFT: 'info', PENDING: 'warning', APPROVED: 'success', REJECTED: 'danger' }[s] || 'info'
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

// ---- 预算 ----
function openBudget() {
  budgetDraft.value = Number(budgetAmount.value) || 0
  budgetVisible.value = true
}
async function doBudget() {
  await registerBudget(projectId, budgetDraft.value)
  ElMessage.success('预算已登记')
  budgetVisible.value = false
  fetchDetail()
}

// ---- 专家 ----
function openAssignExpert() {
  assignExpertRole.value = ''
  assignExpertId.value = null
  searchExperts()
  assignExpertVisible.value = true
}
async function searchExperts() {
  const data = await request.get('/experts', { params: { keyword: expertKeyword.value || undefined, page: 1, size: 50 } })
  expertCandidates.value = data?.items || []
}
async function doAssignExpert() {
  await addProjectExpert(projectId, { expertId: assignExpertId.value, role: assignExpertRole.value })
  ElMessage.success('专家已调派（留痕）')
  assignExpertVisible.value = false
  fetchDetail()
}
function openNewExpert() {
  Object.assign(newExpert, { name: '', unit: '', title: '', profile: '' })
  newExpertVisible.value = true
}
async function doNewExpert() {
  await createExpertCard(projectId, { ...newExpert })
  ElMessage.success('专家卡已创建并自动关联（留痕）')
  newExpertVisible.value = false
  fetchDetail()
}
async function removeExpert(row) {
  await removeProjectExpert(projectId, row.expertId)
  ElMessage.success('专家已移出（留痕）')
  fetchDetail()
}
function startEditFee(row) {
  editingFeeExpert.value = row
  feeDraft.value = Number(row.fee) || 0
}
async function doSaveExpertFee(row) {
  await saveExpertFee(projectId, row.expertId, feeDraft.value)
  ElMessage.success('劳务费已登记')
  editingFeeExpert.value = null
  fetchDetail()
}

// ---- 供应商 ----
function openAssignSupplier() {
  assignSupplierId.value = null
  searchSuppliers()
  assignSupplierVisible.value = true
}
async function searchSuppliers() {
  const data = await request.get('/supplier/list', { params: { keyword: supplierKeyword.value || undefined, page: 1, size: 50 } })
  supplierCandidates.value = data?.items || []
}
async function doAssignSupplier() {
  await addProjectSupplier(projectId, { supplierId: assignSupplierId.value })
  ElMessage.success('供应商已关联（留痕）')
  assignSupplierVisible.value = false
  fetchDetail()
}
async function removeSupplier(row) {
  await removeProjectSupplier(projectId, row.supplierId)
  ElMessage.success('供应商已移出（留痕）')
  fetchDetail()
}
function startEditSupplierFee(row) {
  editingFeeSupplier.value = row
  supplierFeeDraft.value = Number(row.fee) || 0
}
async function doSaveSupplierFee(row) {
  await saveSupplierFee(projectId, row.supplierId, supplierFeeDraft.value)
  ElMessage.success('供应商费用已填写')
  editingFeeSupplier.value = null
  fetchDetail()
}

// ---- 合同 ----
function openContract() {
  Object.assign(contractForm, { expertId: null, idCard: '', bankCard: '', fee: 0 })
  contractVisible.value = true
}
function onPickContractExpert(expertId) {
  const e = (detail.value.experts || []).find((x) => x.expertId === expertId)
  if (e) {
    contractForm.idCard = e.idCard || ''
    contractForm.bankCard = e.bankCard || ''
    contractForm.fee = Number(e.fee) || 0
  }
}
async function doCreateContract() {
  await createProjectContract(projectId, { ...contractForm })
  ElMessage.success('合同已登记（A：信息）')
  contractVisible.value = false
  fetchDetail()
}
async function uploadContract(row, file) {
  if (!file || !file.raw) return
  await uploadContractFile(projectId, row.id, file.raw)
  ElMessage.success('合同文件已上传（B：文件）')
  fetchDetail()
}
async function doSubmitApproval(row) {
  try {
    await submitContractApproval(projectId, row.id)
    ElMessage.success('审批已发起，等待审批')
    fetchDetail()
  } catch (e) {
    // 校验失败提示
  }
}
function openSensitive(row) {
  sensitiveForm.contractId = row.id
  sensitiveForm.idCard = row.idCard || ''
  sensitiveForm.bankCard = row.bankCard || ''
  sensitiveVisible.value = true
}
async function doUpdateSensitive() {
  await updateContractSensitive(projectId, sensitiveForm.contractId, {
    idCard: sensitiveForm.idCard,
    bankCard: sensitiveForm.bankCard
  })
  ElMessage.success('敏感字段已更新并回写专家库（留痕）')
  sensitiveVisible.value = false
  fetchDetail()
}

onMounted(async () => {
  fetchDetail()
  typeOptions.value = await listProjectTypes()
  ownerOptions.value = await listProjectOwners()
  const userData = await listProjectExecutors()
  userOptions.value = userData || []
})
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
.pd-side { display: flex; flex-direction: column; gap: 16px; position: sticky; top: 72px; }

/* ② 核心数据卡 */
.pd-stats { display: grid; grid-template-columns: repeat(4, 1fr); gap: 16px; }
.pd-stat { padding: 14px 16px; }
.pd-stat-label { color: var(--ui-text-muted); font-size: 13px; }
.pd-stat-value { font-size: 20px; font-weight: 600; color: var(--ui-text-main); margin-top: 4px; }
.pd-stat-sub { font-size: 12px; color: var(--ui-text-muted); margin-top: 4px; }
.pd-neg { color: var(--ui-danger); }

/* ③ Stepper */
.pd-stepper { padding: 16px 20px; }
.pd-sec-title { font-size: 15px; font-weight: 600; color: var(--ui-text-main); }
.pd-sec-title-row { display: flex; justify-content: space-between; align-items: center; margin-bottom: 10px; }
.steps-row { display: flex; align-items: center; margin-top: 12px; flex-wrap: nowrap; overflow-x: auto; }
.step-item { display: flex; flex-direction: column; align-items: center; gap: 6px; cursor: pointer; min-width: 72px; padding: 4px 8px; border-radius: var(--ui-radius-md); }
.step-dot {
  width: 28px; height: 28px; border-radius: 50%; display: flex; align-items: center; justify-content: center;
  font-size: 13px; border: 2px solid var(--ui-info); color: var(--ui-text-muted); background: #fff;
}
.step-item.done .step-dot { border-color: var(--ui-success); color: var(--ui-success); background: #F0F9EB; }
.step-item.current .step-dot { border-color: var(--ui-primary); color: #fff; background: var(--ui-primary); }
.step-item.future .step-dot { border-color: var(--ui-info); color: var(--ui-text-muted); }
.step-item.viewing { box-shadow: 0 0 0 4px rgba(64, 158, 255, 0.35); }
.step-name { font-size: 13px; color: var(--ui-text-sub); white-space: nowrap; display: flex; align-items: center; gap: 4px; }
.step-item.current .step-name { color: var(--ui-primary); font-weight: 600; }
.step-item.future .step-name { color: var(--ui-text-muted); }
.step-current-mark { font-size: 11px; color: var(--ui-primary); background: #ECF5FF; border-radius: 999px; padding: 0 6px; }
.step-line { flex: 1; height: 2px; background: var(--ui-info); min-width: 18px; }
.step-line.done { background: var(--ui-success); }
.steps-legend { margin-top: 12px; display: flex; gap: 16px; font-size: 12px; color: var(--ui-text-muted); flex-wrap: wrap; }
.legend-item { display: inline-flex; align-items: center; gap: 5px; }
.dot { width: 10px; height: 10px; border-radius: 50%; display: inline-block; }
.dot-done { background: var(--ui-success); }
.dot-current { background: var(--ui-primary); }
.dot-future { background: var(--ui-info); }

/* ④ 阶段任务卡 */
.pd-task { padding: 16px 20px; }
.task-grid { display: grid; grid-template-columns: repeat(2, 1fr); gap: 12px; }
.task-cell { background: var(--ui-fill); border-radius: var(--ui-radius-md); padding: 10px 12px; }
.task-label { color: var(--ui-text-muted); font-size: 12px; margin-bottom: 4px; }
.task-value { color: var(--ui-text-main); font-size: 14px; }
.task-scope { color: var(--ui-text-muted); font-size: 12px; margin-left: 6px; }
.task-block { margin-top: 12px; background: #FEF0F0; border-radius: var(--ui-radius-md); padding: 10px 12px; }
.task-item { display: flex; align-items: center; justify-content: space-between; padding: 4px 0; }
.task-item-name { color: var(--ui-text-main); font-size: 13px; }
.task-done-line { margin-top: 12px; color: var(--ui-success); font-size: 13px; background: #F0F9EB; border-radius: var(--ui-radius-md); padding: 8px 12px; }
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
.rich-view { white-space: pre-wrap; color: var(--ui-text-sub); }
.hint-text { color: var(--ui-text-muted); font-size: 13px; }
.mask-tip { color: var(--ui-text-muted); font-size: 12px; margin-left: 6px; }
.sensitive { font-family: monospace; font-size: 12px; }
.expert-pick-list { max-height: 360px; overflow-y: auto; border: 1px solid var(--ui-border-weak); border-radius: var(--ui-radius-md); }
.expert-pick-row { display: flex; align-items: center; gap: 12px; padding: 10px 12px; cursor: pointer; border-bottom: 1px solid var(--ui-fill); }
.expert-pick-row:hover { background: var(--ui-fill); }
.expert-pick-row.picked { background: #ECF5FF; }
.expert-pick-info { flex: 1; min-width: 0; }
.expert-pick-name { font-weight: 600; color: var(--ui-text-main); font-size: 14px; }
.expert-pick-meta { font-size: 12px; color: var(--ui-text-muted); margin-top: 2px; }
.expert-pick-check { color: var(--ui-primary); font-weight: 700; }
.ne-form-section { font-size: 13px; font-weight: 600; color: var(--ui-text-sub); background: var(--ui-fill); border-radius: var(--ui-radius-sm); padding: 6px 10px; margin-bottom: 12px; }

/* ⑥ 基本信息分组 */
.pd-info-grid { display: grid; grid-template-columns: repeat(4, 1fr); gap: 16px; }
.pd-info-card { padding: 14px 16px; }
.info-row { display: flex; gap: 10px; padding: 5px 0; font-size: 13px; align-items: center; flex-wrap: wrap; }
.info-k { color: var(--ui-text-muted); width: 60px; flex-shrink: 0; }
.info-v { color: var(--ui-text-main); word-break: break-all; display: inline-flex; align-items: center; gap: 4px; flex-wrap: wrap; }
.avatar {
  width: 24px; height: 24px; border-radius: 50%; color: #fff; font-size: 12px;
  display: inline-flex; align-items: center; justify-content: center; flex-shrink: 0;
}
.avatar-more { background: var(--ui-info); }
.executor-names { color: var(--ui-text-muted); font-size: 12px; }

/* ⑧ 预算 */
.pd-budget { padding: 16px 20px; }
.stat-box { text-align: center; padding: 8px 0; }
.stat-label { color: var(--ui-text-muted); font-size: 13px; }
.stat-value { font-size: 20px; font-weight: 600; color: var(--ui-text-main); margin-top: 4px; }

/* ⑨ 业务明细 */
.pd-block { padding: 16px 20px; }
.tl-row { display: flex; gap: 8px; }
.tl-user { font-weight: 600; }

/* ⑦ 右侧辅助栏 */
.side-card { padding: 14px 16px; }
.member-row { display: flex; align-items: center; gap: 8px; padding: 5px 0; font-size: 13px; color: var(--ui-text-main); }
.todo-item { display: flex; align-items: center; justify-content: space-between; padding: 5px 0; }
.todo-name { font-size: 13px; color: var(--ui-text-main); }
.side-done { color: var(--ui-success); font-size: 13px; }
.side-doc { display: flex; flex-direction: column; padding: 4px 0; border-bottom: 1px dashed var(--ui-border-weak); }
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
  .pd-stats { grid-template-columns: repeat(2, 1fr); }
  .pd-info-grid { grid-template-columns: repeat(2, 1fr); }
}
</style>
