<template>
  <div class="pdept">
    <div class="pdept-tools">
      <el-button size="small" @click="move(-1)" aria-label="上一月">‹</el-button>
      <strong class="pdept-month">{{ year }} 年 {{ month }} 月</strong>
      <el-button size="small" @click="move(1)" aria-label="下一月">›</el-button>
      <el-button size="small" text @click="goToday">今天</el-button>
      <span class="spacer"></span>
    </div>
    <p class="muted pdept-hint">
      部门排期（范围=本部门及下级，取自组织架构）· 格子仅显示角色字，点格子查看当天会议；! 表示当天多场会议。
    </p>
    <div class="pdept-scroll">
      <table class="pdept-table">
        <thead>
          <tr>
            <th class="pdept-name">员工</th>
            <th v-for="c in cells" :key="c.date" :class="{ we: c.weekend, tdy: c.today }">{{ c.day }}</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="m in members" :key="m.userId">
            <td class="pdept-name">{{ m.userName }}<template v-if="m.disabled"> <span class="pdept-disabled">已停用</span></template></td>
            <td v-for="c in cells" :key="c.date" :class="{ we: c.weekend, tdy: c.today, bad: isBad(m, c.date) }">
              <button
                v-if="roleOf(m, c.date)" type="button" class="pdept-cell"
                :title="titles(m, c.date)" @click="$emit('open-day', m, c.date)"
              >{{ isBad(m, c.date) ? '!' : roleText(roleOf(m, c.date)) }}</button>
              <span v-else class="pdept-empty"></span>
            </td>
          </tr>
        </tbody>
      </table>
    </div>
    <div class="pdept-legend muted">
      <span>负＝负责人</span><span>协＝协助人</span><span>执＝执行人</span><span>! ＝ 当天多场会议（撞期）</span>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import {
  coversDay, shiftMonth, primaryRole, roleText, daysInMonth, fmtDate, todayStr
} from '../../../utils/projectCalendar'

const props = defineProps({
  members: { type: Array, default: () => [] },
  year: { type: Number, required: true },
  month: { type: Number, required: true }
})
const emit = defineEmits(['open-day', 'change-month'])

const cells = computed(() => {
  const today = todayStr()
  const n = daysInMonth(props.year, props.month)
  const out = []
  for (let d = 1; d <= n; d++) {
    const date = fmtDate(props.year, props.month, d)
    const wd = new Date(props.year, props.month - 1, d).getDay()
    out.push({ day: d, date, weekend: wd === 0 || wd === 6, today: date === today })
  }
  return out
})

function dayMeetings(m, date) {
  return (m.days?.[date] || [])
}
function roleOf(m, date) {
  const list = dayMeetings(m, date)
  return list.length ? primaryRole(list[0].roles) : null
}
function isBad(m, date) {
  return dayMeetings(m, date).length > 1
}
function titles(m, date) {
  return dayMeetings(m, date).map((x) => x.name).join('、')
}
function move(delta) {
  emit('change-month', shiftMonth({ year: props.year, month: props.month }, delta))
}
function goToday() {
  const now = new Date()
  emit('change-month', { year: now.getFullYear(), month: now.getMonth() + 1 })
}
</script>

<style scoped>
.pdept { min-width: 0; }
.pdept-tools { display: flex; align-items: center; gap: 10px; flex-wrap: wrap; margin-bottom: 8px; }
.pdept-month { font-size: 15px; }
.pdept-hint { margin: 0 0 10px; font-size: 12px; }
.pdept-scroll { overflow-x: auto; border: 1px solid var(--ui-border-weak); border-radius: var(--ui-radius-md); background: var(--ui-bg-card); }
.pdept-table { border-collapse: collapse; width: 100%; }
.pdept-table th, .pdept-table td { border: 1px solid var(--ui-border-weak); text-align: center; padding: 0; font-size: 12px; min-width: 30px; height: 30px; }
.pdept-table th { background: var(--ui-fill); color: var(--ui-text-muted); font-weight: 400; }
.pdept-table th.pdept-name, .pdept-table td.pdept-name { position: sticky; left: 0; background: var(--ui-bg-card); min-width: 96px; text-align: left; padding: 0 8px; z-index: 1; }
.pdept-table th.pdept-name { background: var(--ui-fill); }
.pdept-table .we { background: var(--ui-fill); }
.pdept-table .tdy { box-shadow: inset 0 0 0 2px var(--ui-primary); }
.pdept-cell { width: 100%; height: 100%; border: 0; background: color-mix(in srgb, var(--ui-primary) 14%, var(--ui-bg-card)); color: var(--ui-primary); font-weight: 600; cursor: pointer; }
.pdept-table td.bad .pdept-cell { background: color-mix(in srgb, var(--ui-warning) 18%, var(--ui-bg-card)); color: var(--ui-warning); }
.pdept-disabled { font-size: 11px; color: var(--ui-warning); }
.pdept-legend { display: flex; gap: 12px; flex-wrap: wrap; font-size: 12px; margin-top: 10px; }
@media (max-width: 768px) {
  .pdept-table th, .pdept-table td { min-width: 26px; height: 26px; }
}
</style>
