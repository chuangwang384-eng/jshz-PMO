<template>
  <article class="resume-card ui-card" :class="badgeType === 'warn' ? 'source-local' : 'source-lib'">
    <header>
      <PhotoImage v-if="photoUrl" :src="photoUrl" :width="64" :height="64" fit="contain" alt="证件照" />
      <PhotoImage v-else :filename="fields.照片" :width="64" :height="64" />
      <div>
        <span v-if="badge" class="card-badge" :class="badgeType === 'warn' ? 'card-badge-warn' : 'card-badge-lib'">{{ badge }}</span>
        <small v-if="label">{{ label }}</small>
        <strong>{{ fields.姓名 || '待填写姓名' }}</strong>
        <span>{{ fields.单位 || '待填写单位' }}</span>
      </div>
      <slot name="header-actions" />
    </header>
    <slot />
    <dl v-if="details">
      <template v-for="field in detailFields" :key="field">
        <dt>{{ field }}</dt><dd>{{ fields[field] || '—' }}</dd>
      </template>
    </dl>
  </article>
</template>

<script setup>
import PhotoImage from './PhotoImage.vue'

defineProps({
  fields: { type: Object, required: true },
  photoUrl: String,
  label: String,
  details: Boolean,
  // 2026-11-20：同名对比时区分手续来源——badge 为类别文案，badgeType 'warn'=本次解析、其余=专家库已有
  badge: String,
  badgeType: String
})

const detailFields = ['科室', '职称', '联系方式', '简介', '身份证号', '银行卡号', '银行卡开户行']
</script>

<style scoped>
.resume-card { padding: 14px; min-width: 0; }
header { display: flex; flex-wrap: wrap; align-items: center; gap: 12px; margin-bottom: 12px; overflow-wrap: anywhere; }
header strong, header span, header small { display: block; }
header strong { font-size: 16px; color: var(--el-text-color-primary); }
header small { color: var(--el-text-color-secondary); }
dl { display: grid; grid-template-columns: max-content minmax(0, 1fr); gap: 8px 12px; margin: 0; }
dt { color: var(--el-text-color-secondary); }
dd { margin: 0; overflow-wrap: anywhere; white-space: pre-wrap; }
/* 同名对比来源徽标与顶部色条：蓝=专家库已有，橙=本次简历解析（2026-11-20） */
.card-badge { display: inline-block; margin-bottom: 4px; padding: 1px 10px; border-radius: 999px; font-size: 12px; line-height: 20px; }
.card-badge-lib { color: var(--ui-info); background: var(--ui-info-bg, #ecf5ff); border: 1px solid var(--ui-info); }
.card-badge-warn { color: var(--ui-warning); background: var(--ui-warning-bg, #fdf6ec); border: 1px solid var(--ui-warning); }
.source-lib { border-top: 3px solid var(--ui-info); }
.source-local { border-top: 3px solid var(--ui-warning); }
</style>
