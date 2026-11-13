<template>
  <div class="impact-preview">
    <el-alert type="warning" :closable="false" show-icon>
      <template #title>{{ alertTitle }}</template>
    </el-alert>

    <div class="impact-preview__grid">
      <div class="impact-card">
        <div class="impact-card__label">{{ card1Label }}</div>
        <div class="impact-card__value" :class="{ 'is-danger': card1Danger }">
          <span v-if="scene === 'type' && loadingRef">查询中…</span>
          <span v-else-if="scene === 'type' && refError" class="impact-card__err">查询失败</span>
          <span v-else>{{ card1Value }}</span>
        </div>
        <div class="impact-card__hint">{{ card1Hint }}</div>
      </div>

      <div class="impact-card">
        <div class="impact-card__label">级联清理</div>
        <div class="impact-card__value">{{ cascadeValue }}</div>
        <div class="impact-card__hint">{{ cascadeHint }}</div>
      </div>

      <div class="impact-card">
        <div class="impact-card__label">回退语义</div>
        <div class="impact-card__value">{{ fallbackValue }}</div>
        <div class="impact-card__hint">{{ fallbackHint }}</div>
      </div>
    </div>

    <div v-if="scene === 'globalBind' && affectedTypes.length" class="impact-preview__list">
      <div class="impact-preview__list-title">受影响类型（{{ affectedTypes.length }}）</div>
      <div class="impact-preview__list-item" v-for="t in affectedTypes" :key="t.id">{{ t.name }}</div>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { listProjects } from '../../../api/project'

const props = defineProps({
  scene: { type: String, default: 'type' }, // type | phase | template | globalBind
  // 删除类型
  type: { type: Object, default: null },
  flowCount: { type: Number, default: 0 },
  ownBindCount: { type: Number, default: 0 },
  // 删除阶段
  phase: { type: Object, default: null },
  refTypeCount: { type: Number, default: 0 },
  globalFallbackCount: { type: Number, default: 0 },
  // 删除模板
  template: { type: Object, default: null },
  globalBindRefs: { type: Number, default: 0 },
  ownBindRefs: { type: Number, default: 0 },
  // 修改全局绑定
  affectedTypes: { type: Array, default: () => [] },
  affectedCount: { type: Number, default: 0 }
})

const refCount = ref(null)
const loadingRef = ref(true)
const refError = ref(false)

onMounted(async () => {
  if (props.scene !== 'type') return
  try {
    const data = await listProjects({ typeId: props.type.id, pageSize: 1 })
    refCount.value = data?.total ?? 0
  } catch (e) {
    refError.value = true
  } finally {
    loadingRef.value = false
  }
})

const alertTitle = computed(() => {
  switch (props.scene) {
    case 'phase': return `删除「${props.phase?.name || ''}」将产生以下影响`
    case 'template': return `删除「${props.template?.name || ''}」将产生以下影响`
    case 'globalBind': return `修改全局兜底绑定将影响 ${props.affectedTypes.length} 个类型`
    default: return `删除「${props.type?.name || ''}」将产生以下影响`
  }
})

const card1Label = computed(() => (props.scene === 'globalBind' ? '受影响类型数' : '引用项目数'))
const card1Value = computed(() => {
  switch (props.scene) {
    case 'phase': return `${props.refTypeCount} 个类型流程`
    case 'template': return `${props.globalBindRefs + props.ownBindRefs} 处绑定`
    case 'globalBind': return `${props.affectedTypes.length} 个类型`
    default: return `${refCount.value ?? 0} 个项目`
  }
})
const card1Danger = computed(() => {
  switch (props.scene) {
    case 'phase': return props.refTypeCount > 0
    case 'template': return (props.globalBindRefs + props.ownBindRefs) > 0
    case 'globalBind': return props.affectedTypes.length > 0
    default: return (refCount.value ?? 0) > 0
  }
})
const card1Hint = computed(() => {
  switch (props.scene) {
    case 'phase':
      return props.refTypeCount > 0
        ? '有类型流程引用或项目处于该阶段时，后端将拒绝删除，需先调整流程/项目'
        : '无类型流程引用，删除后该阶段相关绑定一并失效'
    case 'template':
      return (props.globalBindRefs + props.ownBindRefs) > 0
        ? '被绑定引用时后端将拒绝删除，需先解除相关绑定'
        : '无绑定引用，可正常删除'
    case 'globalBind':
      return '保存后将覆盖这些类型当前看到的全局兜底绑定'
    default:
      return (refCount.value ?? 0) > 0
        ? '存在引用时后端将拒绝删除，需先转移或删除相关项目'
        : '无项目引用，可正常删除'
  }
})

const cascadeValue = computed(() => {
  switch (props.scene) {
    case 'phase': return `${props.refTypeCount} 个类型流程受影响`
    case 'template': return `${props.globalBindRefs} 全局 + ${props.ownBindRefs} 专属`
    case 'globalBind': return `${props.affectedTypes.length} 个类型将更新`
    default: return `${props.flowCount} 个流程阶段`
  }
})
const cascadeHint = computed(() => {
  switch (props.scene) {
    case 'phase':
      return '删除后将清理：相关类型流程中的该阶段，以及该阶段的全局兜底/类型专属绑定'
    case 'template':
      return '删除后将清理所有引用该模板的全局兜底与类型专属绑定关系'
    case 'globalBind':
      return '保存后将覆盖这些类型的全局兜底视图，类型专属绑定不受影响'
    default:
      return `删除后将一并清理：${props.flowCount} 个阶段关联、${props.ownBindCount} 条类型专属模板绑定`
  }
})

const fallbackValue = computed(() => {
  switch (props.scene) {
    case 'phase': return '流程缩短 / 绑定失效'
    case 'template': return '专属缺失回退全局'
    case 'globalBind': return '专属优先原则不变'
    default: return '全局兜底不受影响'
  }
})
const fallbackHint = computed(() => {
  switch (props.scene) {
    case 'phase':
      return '受影响类型流程移除该阶段；未配置流程的类型将不再包含该阶段，相关阶段模板绑定同时失效'
    case 'template':
      return '类型专属绑定中被删除的模板消失后，该阶段将回退展示全局兜底绑定（全局也无则无模板）'
    case 'globalBind':
      return '已配置类型专属绑定的阶段不受全局修改影响，仍显示专属绑定'
    default:
      return '类型专属绑定被清除后，新建项目将回退使用「全局兜底绑定」（type_id=0）阶段与模板'
  }
})
</script>

<style scoped>
.impact-preview__grid { display: grid; grid-template-columns: repeat(3, 1fr); gap: 10px; margin-top: 12px; }
.impact-card {
  border: 1px solid #ebeef5; border-radius: 8px; padding: 10px 12px;
  background: #fff;
}
.impact-card__label { font-size: 12px; color: var(--ui-text-muted); margin-bottom: 6px; }
.impact-card__value { font-size: 15px; font-weight: 600; color: var(--ui-text-main); }
.impact-card__value.is-danger { color: var(--ui-danger); }
.impact-card__err { color: var(--ui-danger); font-weight: 400; }
.impact-card__hint { font-size: 11px; color: var(--ui-text-muted); margin-top: 6px; line-height: 1.5; }
.impact-preview__list { margin-top: 10px; border: 1px solid #ebeef5; border-radius: 8px; padding: 8px 12px; max-height: 160px; overflow: auto; }
.impact-preview__list-title { font-size: 12px; color: var(--ui-text-sub); font-weight: 600; margin-bottom: 6px; }
.impact-preview__list-item { font-size: 12px; color: var(--ui-text-main); line-height: 22px; }
</style>
