<template>
  <div ref="container" class="global-search" @keydown.esc="open = false">
    <div class="search-input" :class="{ 'is-focused': focused }"><el-icon class="search-lead" aria-hidden="true"><Search /></el-icon><input id="global-keyword" ref="inputElement" v-model="keyword" role="combobox" aria-label="搜索知识、专家、项目、客户、供应商和公告" aria-controls="global-results" :aria-expanded="open" :aria-activedescendant="active >= 0 ? `global-item-${active}` : undefined" autocomplete="off" placeholder="搜索专家、项目、客户、供应商或知识文档…" @compositionstart="composing = true; invalidate()" @compositionend="compositionEnd" @input="schedule" @keydown="keyDown" @focus="focused = true; open = !!keyword.trim()" @blur="focused = false" /><span v-if="keyword" role="button" tabindex="0" class="search-clear" aria-label="清空搜索" @click="clear" @keydown.enter.prevent="clear">✕</span><span class="kbd-hint" aria-hidden="true">Ctrl K</span></div>
    <div v-if="open" id="global-results" class="search-results" role="listbox" aria-label="搜索结果" aria-live="polite">
      <p v-if="error" role="alert">{{ error }} <button @click="search">重试</button></p><p v-if="loading">正在搜索…</p>
      <template v-for="group in groups" :key="group.module"><div class="group-title">{{ moduleNames[group.module] }} <span v-if="group.total !== null">({{ group.total }})</span></div>
        <p v-if="group.status === 'error'">该分组暂不可用 <button @click="search">重试</button></p>
        <p v-else-if="group.status === 'empty'">无匹配结果</p>
        <button v-for="item in group.items" :id="`global-item-${indexOf(item)}`" :key="item.id" class="result-row" role="option" :aria-selected="active === indexOf(item)" @click="go(item.target)">{{ item.title }} <small>{{ item.extra }}</small></button>
        <button v-if="group.status !== 'error' && group.total > 0" @click="go(group.allTarget)">查看全部</button>
      </template>
      <p v-if="!loading && !error && groups.length && groups.every(g => g.status === 'empty')">未找到相关内容</p>
    </div>
  </div>
</template>
<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { Search } from '@element-plus/icons-vue'
import { globalSearch } from '../api/dashboard'
import { latestRequest, moduleNames, safeTarget } from '../utils/dashboard'
const router = useRouter(), keyword = ref(''), groups = ref([]), loading = ref(false), error = ref(''), open = ref(false), active = ref(-1), container = ref(null), inputElement = ref(null), focused = ref(false)
/** 供工作台 Ctrl K 快捷键聚焦搜索框。 */
function focus() { inputElement.value?.focus() }
defineExpose({ focus })
let composing = false, timer, controller
const sequence = latestRequest(), flat = computed(() => groups.value.flatMap(g => g.items || []))
const indexOf = item => flat.value.indexOf(item)
function invalidate() { clearTimeout(timer); controller?.abort(); sequence.next(); loading.value = false; groups.value = []; active.value = -1 }
function schedule() { invalidate(); error.value = ''; open.value = !!keyword.value.trim(); if (!composing && open.value) timer = setTimeout(search, 300) }
function compositionEnd() { composing = false; schedule() }
function clear() { keyword.value = ''; invalidate(); error.value = ''; open.value = false; container.value?.querySelector('input')?.focus() }
async function search() {
  if (composing) return
  const word = keyword.value.trim(); invalidate(); error.value = ''
  if (!word) return clear()
  open.value = true
  if ([...word].length > 100) { error.value = '关键词最多100个字符'; return }
  const seq = sequence.next(); controller = new AbortController(); loading.value = true
  try { const data = await globalSearch(word, controller.signal); if (sequence.current(seq) && keyword.value.trim() === word) groups.value = data.groups || [] }
  catch (e) { if (sequence.current(seq) && e.code !== 'ERR_CANCELED') error.value = '搜索失败，请重试' }
  finally { if (sequence.current(seq)) loading.value = false }
}
function go(target) { const value = safeTarget(router, target); if (value) { open.value = false; router.push(value) } else error.value = '目标不可用' }
function keyDown(event) {
  if (composing || event.isComposing || event.keyCode === 229) return
  if (event.key === 'Enter') { event.preventDefault(); active.value >= 0 && flat.value[active.value] ? go(flat.value[active.value].target) : search() }
  if (['ArrowDown','ArrowUp'].includes(event.key) && flat.value.length) { event.preventDefault(); open.value = true; active.value = active.value < 0 ? (event.key === 'ArrowDown' ? 0 : flat.value.length - 1) : (active.value + (event.key === 'ArrowDown' ? 1 : -1) + flat.value.length) % flat.value.length }
}
function outside(event) { if (!container.value?.contains(event.target)) open.value = false }
onMounted(() => document.addEventListener('pointerdown', outside))
onBeforeUnmount(() => { invalidate(); document.removeEventListener('pointerdown', outside) })
</script>
<style scoped>
.global-search{position:relative;width:100%}.search-input{position:relative;display:flex;align-items:center}.search-lead{position:absolute;left:12px;width:18px;height:18px;color:var(--c-text-sub,#64748b);pointer-events:none}.search-input input{flex:1;min-width:180px;border:1px solid transparent;border-radius:var(--r-btn,8px);padding:12px 108px 12px 38px;background:var(--c-input-bg,#F1F5F9);color:var(--c-text,#0f172a);font-size:13px}.search-input input:focus{border-color:var(--c-primary,#2563eb);background:var(--c-card-bg,#fff);outline:none}.search-clear{position:absolute;right:76px;cursor:pointer;color:var(--c-text-sub,#64748b);padding:4px 6px;font-size:12px}.kbd-hint{position:absolute;right:10px;font-size:11px;color:var(--c-text-sub,#64748b);border:1px solid var(--c-border,#e2e8f0);border-radius:6px;padding:2px 7px;background:var(--c-card-bg,#fff)}.global-search button{cursor:pointer;border:1px solid var(--c-border,#dbe3ef);border-radius:6px;background:var(--c-card-bg,#fff);padding:8px;color:var(--c-primary,#245baf)}.search-results{position:absolute;z-index:100;width:100%;max-height:460px;overflow:auto;background:var(--c-card-bg,#fff);box-shadow:0 12px 40px #1e293b33;border-radius:10px;padding:14px;box-sizing:border-box}.group-title{font-weight:600;margin:14px 0 6px;color:var(--c-text,#0f172a)}.result-row{display:block;text-align:left;width:100%;margin:4px 0;color:var(--c-text,#0f172a)}.result-row[aria-selected=true]{background:#dbeafe}.result-row small{color:var(--c-text-sub,#64748b);margin-left:12px}button:focus-visible,input:focus-visible{outline:2px solid #2864d8;outline-offset:2px}
</style>
