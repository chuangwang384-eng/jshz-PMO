<template>
  <article class="resume-card ui-card">
    <header>
      <PhotoImage v-if="photoUrl" :src="photoUrl" :width="64" :height="64" fit="contain" alt="证件照" />
      <PhotoImage v-else :filename="fields.照片" :width="64" :height="64" />
      <div>
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
  details: Boolean
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
</style>
