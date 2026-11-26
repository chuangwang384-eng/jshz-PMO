<template>
  <div class="pcal">
    <div class="pcal-tools">
      <el-button size="small" @click="move(-1)" aria-label="上一月">‹</el-button>
      <strong class="pcal-month">{{ ym.year }} 年 {{ ym.month }} 月</strong>
      <el-button size="small" @click="move(1)" aria-label="下一月">›</el-button>
      <el-button size="small" text @click="goToday">今天</el-button>
      <span class="spacer"></span>
      <span v-if="missingDates > 0" class="muted pcal-missing">有 {{ missingDates }} 个相关项目未设置会议日期</span>
    </div>
    <div class="pcal-grid" role="grid" aria-label="个人会议月历">
      <div class="pcal-wk" v-for="w in weeks" :key="w">{{ w }}</div>
      <div v-for="i in grid.offset" :key="'b' + i" class="pcal-cell off"></div>
      <div v-for="c in grid.cells" :key="c.date" class="pcal-cell" :class="{ today: c.today }">
        <div class="pcal-dn"><span>{{ c.day }}</span><span v-if="c.collision" class="pcal-cf">撞期</span></div>
        <button
          v-for="m in c.meetings" :key="m.projectId" type="button"
          class="pcal-chip" :class="roleClass(primaryRole(m.roles))"
          :title="roleLabel(primaryRole(m.roles))"
          @click="$emit('open', m.projectId)"
        >
          <b>{{ roleText(primaryRole(m.roles)) }}</b><span>{{ m.name }}</span>
        </button>
      </div>
    </div>
    <div class="pcal-legend muted">
      <span class="lg owner">▌负责人</span><span class="lg assist">▌协助人</span><span class="lg exec">▌执行人</span>
      <span>点击会议查看详情</span>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import {
  buildMonthGrid, shiftMonth, roleText, roleLabel, primaryRole, todayStr
} from '../../../utils/projectCalendar'

const props = defineProps({
  meetings: { type: Array, default: () => [] },
  year: { type: Number, required: true },
  month: { type: Number, required: true },
  missingDates: { type: Number, default: 0 }
})
const emit = defineEmits(['open', 'change-month'])

const weeks = ['周一', '周二', '周三', '周四', '周五', '周六', '周日']
const grid = computed(() => buildMonthGrid(props.meetings, { year: props.year, month: props.month }, todayStr()))

function move(delta) {
  emit('change-month', shiftMonth({ year: props.year, month: props.month }, delta))
}
function goToday() {
  const now = new Date()
  emit('change-month', { year: now.getFullYear(), month: now.getMonth() + 1 })
}
function roleClass(role) {
  return { OWNER: 'owner', ASSISTANT: 'assist', EXECUTOR: 'exec' }[role] || 'owner'
}
</script>

<style scoped>
.pcal { min-width: 0; }
.pcal-tools { display: flex; align-items: center; gap: 10px; flex-wrap: wrap; margin-bottom: 12px; }
.pcal-month { font-size: 15px; }
.pcal-missing { font-size: 12px; }
.pcal-grid { display: grid; grid-template-columns: repeat(7, minmax(0, 1fr)); gap: 1px; background: var(--ui-border-weak); border: 1px solid var(--ui-border-weak); border-radius: var(--ui-radius-md); overflow: hidden; }
.pcal-wk { background: var(--ui-fill); text-align: center; padding: 6px; font-size: 12px; color: var(--ui-text-muted); }
.pcal-cell { background: var(--ui-bg-card); min-height: 86px; padding: 4px; display: flex; flex-direction: column; gap: 3px; min-width: 0; }
.pcal-cell.off { background: var(--ui-fill); }
.pcal-cell.today { box-shadow: inset 0 0 0 2px var(--ui-primary); }
.pcal-dn { font-size: 12px; color: var(--ui-text-muted); display: flex; justify-content: space-between; gap: 4px; }
.pcal-cf { color: var(--ui-warning); font-size: 11px; flex-shrink: 0; }
.pcal-chip { display: block; width: 100%; text-align: left; border: 0; border-left: 3px solid currentColor; border-radius: 4px; padding: 2px 5px; font-size: 12px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; cursor: pointer; }
.pcal-chip b { margin-right: 4px; }
.pcal-chip span { color: var(--ui-text-main); }
.pcal-chip.owner { color: var(--ui-role-owner); background: color-mix(in srgb, var(--ui-role-owner) 12%, var(--ui-bg-card)); }
.pcal-chip.assist { color: var(--ui-role-assist); background: color-mix(in srgb, var(--ui-role-assist) 12%, var(--ui-bg-card)); }
.pcal-chip.exec { color: var(--ui-role-exec); background: color-mix(in srgb, var(--ui-role-exec) 12%, var(--ui-bg-card)); }
.pcal-legend { display: flex; gap: 12px; flex-wrap: wrap; font-size: 12px; margin-top: 10px; }
.pcal-legend .owner { color: var(--ui-role-owner); }
.pcal-legend .assist { color: var(--ui-role-assist); }
.pcal-legend .exec { color: var(--ui-role-exec); }
@media (max-width: 768px) {
  .pcal-cell { min-height: 64px; }
}
</style>
