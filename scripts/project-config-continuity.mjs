import http from 'node:http'
import { writeFile, rename } from 'node:fs/promises'
import { existsSync } from 'node:fs'
const [directory]=process.argv.slice(2)
if(!directory)throw new Error('Evidence directory required')
const base='http://127.0.0.1'
async function request(path,{agent,method='GET',cookie,body}={}){
  return new Promise((resolve,reject)=>{
    const req=http.request(base+path,{method,agent,headers:{...(cookie?{Cookie:cookie}:{}),...(body?{'Content-Type':'application/json','Content-Length':Buffer.byteLength(body)}:{})}},res=>{
      let data='';res.setEncoding('utf8');res.on('data',chunk=>data+=chunk);res.on('end',()=>resolve({status:res.statusCode,body:data,cookies:res.headers['set-cookie']}))
    });req.setTimeout(6000,()=>req.destroy(new Error('timeout')));req.on('error',reject);if(body)req.write(body);req.end()
  })
}
const login=await request('/api/auth/login',{method:'POST',body:JSON.stringify({username:process.env.PC_SMOKE_USER,password:process.env.PC_SMOKE_PASSWORD})})
if(login.status!==200||JSON.parse(login.body).code!==0)throw new Error('Authorized smoke login failed; no release should proceed')
const cookie=(login.cookies||[]).map(value=>value.split(';')[0]).join('; ')
if(!cookie)throw new Error('No session cookie')
const result={start:new Date().toISOString(),pooled:{requests:0,failures:0},fresh:{requests:0,failures:0},errors:[],authenticated:true}
const agent=new http.Agent({keepAlive:true,maxSockets:1})
let writing=false
async function save(){if(writing)return;writing=true;try{
  result.updated=new Date().toISOString()
  await writeFile(directory+'/continuity.next.json',JSON.stringify(result,null,2))
  for(let attempt=0;;attempt++){
    try{await rename(directory+'/continuity.next.json',directory+'/continuity.json');break}
    catch(error){if(attempt>=9)throw error;await new Promise(resolve=>setTimeout(resolve,20))}
  }
}finally{writing=false}}
async function loop(name,connection){
  while(!existsSync(directory+'/probe.stop')){
    for(const path of ['/api/health','/','/api/auth/me']){
      if(existsSync(directory+'/probe.stop'))break
      result[name].requests++
      // Transport-level single retry: nginx graceful reload drains keep-alive sockets and can
      // reset a reused connection before any byte is sent. One retry (dropping the poisoned
      // socket) mirrors standard client behaviour; application-level failures never retry.
      let outcome=null
      for(let attempt=0;attempt<2;attempt++){
        try{
          const response=await request(path,{agent:connection,cookie})
          if(response.status!==200)throw {app:true,message:'HTTP '+response.status}
          if(path==='/api/auth/me'&&JSON.parse(response.body).code!==0)throw {app:true,message:'Session failed'}
          outcome=null;break
        }catch(error){
          outcome=error
          if(error&&error.app)break
          if(attempt===0&&connection&&typeof connection.destroy==='function'){connection.destroy()}
        }
      }
      if(outcome){result[name].failures++;if(result.errors.length<20)result.errors.push({time:new Date().toISOString(),name,path,error:outcome.message||String(outcome)})}
    }
    await save();await new Promise(resolve=>setTimeout(resolve,500))
  }
}
await Promise.all([loop('pooled',agent),loop('fresh',false)]);agent.destroy();result.end=new Date().toISOString();await save()
