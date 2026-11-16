<template><section class="module-results"><h2>{{ moduleNames[module] }} · 全部搜索结果</h2><p>关键词：{{ route.query.keyword }}</p><el-alert v-if="error" :title="error" type="error" :closable="false" /><el-button @click="load">刷新 / 重试</el-button><div v-loading="loading"><el-empty v-if="!loading && !error && !items.length" description="无匹配结果"/><div v-for="item in items" :key="item.id" class="module-row"><el-button link type="primary" @click="go(item.target)">{{ item.title }}</el-button><span>{{ item.extra }}</span></div></div><el-pagination v-if="total" v-model:current-page="page" :total="total" :page-size="20" layout="prev,pager,next,total" @current-change="load" /></section></template>
<script setup>
import { ref, watch, onBeforeUnmount } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { moduleSearch } from '../api/dashboard'
import { moduleNames, safeTarget, latestRequest } from '../utils/dashboard'
const props = defineProps({ module: { type: String, required: true } }), router = useRouter(), route = useRoute(), items = ref([]), total = ref(0), page = ref(1), loading = ref(false), error = ref(''), sequence = latestRequest()
let controller
async function load() { controller?.abort(); controller = new AbortController(); const seq = sequence.next(); loading.value = true; error.value = ''; items.value = []; total.value = 0
 try { const data = await moduleSearch(props.module, String(route.query.keyword || ''), page.value, controller.signal); if(sequence.current(seq)){ items.value = data.items; total.value = data.total } }
 catch(e){ if(sequence.current(seq) && e.code !== 'ERR_CANCELED') error.value = '查询失败或无权访问，请重试' } finally { if(sequence.current(seq)) loading.value = false }
}
function go(target){ const value = safeTarget(router,target); if(value) router.push(value) }
watch(() => [route.query.keyword, props.module], () => {page.value=1;load()}, { immediate:true })
onBeforeUnmount(() => {sequence.next();controller?.abort()})
</script>
<style scoped>.module-results{padding:20px;background:white;border-radius:12px}.module-row{padding:14px 0;border-bottom:1px solid #edf0f5;display:flex;gap:20px}.module-row span{color:#64748b}</style>
