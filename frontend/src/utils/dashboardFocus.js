import { onScopeDispose, watch } from 'vue'
import { dashboardFocus } from '../api/dashboard'
export function watchDashboardFocus(route, key, kind, open, fail) {
 let sequence=0
 onScopeDispose(()=>{sequence++})
 return watch(() => route.query[key], async value => {
  const seq=++sequence
  if(value===undefined)return
  if(!/^[1-9]\d{0,17}$/.test(String(value)))return fail('记录编号无效')
  try{const data=await dashboardFocus(kind,String(value));if(seq===sequence)await open(data)}
  catch{if(seq===sequence)fail('记录已变化、不可用或无权访问，请刷新')}
 },{immediate:true})
}
