import http from 'node:http'
import {writeFileSync,readFileSync} from 'node:fs'
import {createHash} from 'node:crypto'
const directory=process.argv[2]
function request(path,method='GET',body,cookie){return new Promise((resolve,reject)=>{
 const text=body?JSON.stringify(body):''
 const req=http.request('http://127.0.0.1'+path,{method,headers:{...(text?{'Content-Type':'application/json','Content-Length':Buffer.byteLength(text)}:{}),...(cookie?{Cookie:cookie}:{})}},res=>{const chunks=[];res.on('data',x=>chunks.push(x));res.on('end',()=>resolve({status:res.statusCode,body:Buffer.concat(chunks),cookies:res.headers['set-cookie']}))});req.on('error',reject);req.setTimeout(10000,()=>req.destroy(new Error('timeout')));req.end(text)
})}
const login=await request('/api/auth/login','POST',{username:process.env.PC_SMOKE_USER,password:process.env.PC_SMOKE_PASSWORD})
if(login.status!==200||JSON.parse(login.body).code!==0)throw new Error('Authorized production smoke login failed')
const cookie=login.cookies.map(c=>c.split(';')[0]).join(';')
const checks=[]
for(const path of ['/api/auth/me','/api/dashboard/overview','/api/search/global?keyword='+encodeURIComponent('不存在的发布验收关键词'),'/api/project?page=1&size=5']){
 const response=await request(path,'GET',undefined,cookie);const json=JSON.parse(response.body)
 if(response.status!==200||json.code!==0)throw new Error('Read verification failed: '+path)
 if(path.includes('/overview')){for(const [key,value] of Object.entries(json.data.sections)){if(value.status==='error')throw new Error('Card failed: '+key)}}
 if(path.includes('/search/global')){if(json.data.groups.some(g=>g.status==='error'))throw new Error('Search module failed')}
 checks.push({path,status:'PASS'})
}
const index=await request('/');const expected=createHash('sha256').update(readFileSync(directory+'/frontend-dist/index.html')).digest('hex')
if(createHash('sha256').update(index.body).digest('hex')!==expected)throw new Error('Served index mismatch')
const assets=[...index.body.toString().matchAll(/(?:src|href)="(\/assets\/[^"\s]+)"/g)].map(m=>m[1]);for(const path of assets){const response=await request(path);if(response.status!==200)throw new Error('Asset failed: '+path)}
writeFileSync(directory+'/production-verification.json',JSON.stringify({result:'PASS',checks,indexSha256:expected,assets:assets.length,scope:'existing authorized test account; read-only business checks; login audit only'},null,2))
