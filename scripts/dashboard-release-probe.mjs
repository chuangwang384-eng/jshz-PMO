import http from 'node:http'
import {writeFileSync,existsSync} from 'node:fs'
const directory=process.argv[2]
const result={start:new Date().toISOString(),pooled:{requests:0,failures:0},fresh:{requests:0,failures:0},errors:[],retries:0}
const pooled=new http.Agent({keepAlive:true,maxSockets:1})
function request(path,agent){return new Promise(resolve=>{
 const req=http.get('http://127.0.0.1'+path,{agent},response=>{response.resume();response.on('end',()=>resolve(response.statusCode===200?null:'HTTP '+response.statusCode))})
 req.setTimeout(5000,()=>req.destroy(new Error('timeout')));req.on('error',e=>resolve(e.message))
})}
async function loop(key,agent){while(!existsSync(directory+'/probe.stop')){
 for(const path of ['/api/health','/']){result[key].requests++;const error=await request(path,agent);if(error){result[key].failures++;result.errors.push({at:new Date().toISOString(),key,path,error})}}
 result.updated=new Date().toISOString();writeFileSync(directory+'/continuity.json',JSON.stringify(result,null,2));await new Promise(r=>setTimeout(r,200))
}}
await Promise.all([loop('pooled',pooled),loop('fresh',false)]);pooled.destroy();result.end=new Date().toISOString();writeFileSync(directory+'/continuity.json',JSON.stringify(result,null,2))
