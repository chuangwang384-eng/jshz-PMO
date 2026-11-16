<template><section class="work-list"><h2>{{ kind==='projects'?'我的全部项目':'待我审批' }}</h2><el-alert v-if="error" :title="error" type="error" :closable="false"/><el-button @click="load">刷新 / 重试</el-button><el-empty v-if="!loading && !error && !items.length" description="暂无相关事项"/><div v-loading="loading"><button v-for="item in items" :key="item.id" class="work-row" @click="go(item.target)"><b>{{ item.name || item.title }}</b><span>{{ item.phaseName || item.time }}</span><el-tag v-if="warningNames[item.warning]" type="warning">{{ warningNames[item.warning] }}</el-tag></button></div><el-pagination v-if="total" v-model:current-page="page" :total="total" :page-size="20" layout="prev,pager,next,total" @current-change="load"/></section></template>
<script setup>
import { onBeforeUnmount, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import request from '../api/request'
import { latestRequest, safeTarget, warningNames } from '../utils/dashboard'
const props=defineProps({kind:String}),router=useRouter(),items=ref([]),total=ref(0),page=ref(1),loading=ref(false),error=ref(''),sequence=latestRequest()
let controller
async function load(){controller?.abort();controller=new AbortController();const seq=sequence.next();loading.value=true;error.value='';items.value=[];total.value=0
try{const data=await request.get(`/dashboard/my-${props.kind}`,{params:{page:page.value},signal:controller.signal,silent:true});if(sequence.current(seq)){items.value=data.items;total.value=data.total}}
catch(e){if(sequence.current(seq)&&e.code!=='ERR_CANCELED')error.value='查询失败或无权访问，请重试'}finally{if(sequence.current(seq))loading.value=false}}
function go(target){const value=safeTarget(router,target);if(value)router.push(value)}
watch(()=>props.kind,()=>{page.value=1;load()},{immediate:true})
onBeforeUnmount(()=>{sequence.next();controller?.abort()})
</script>
<style scoped>.work-list{background:white;padding:24px;border-radius:12px}.work-row{display:flex;gap:24px;align-items:center;cursor:pointer;text-align:left;width:100%;background:white;border:0;border-bottom:1px solid #e5eaf3;padding:20px 0;color:#435977}.work-row span{color:#8290a3}</style>
