import fs from 'node:fs'
import path from 'node:path'
import {spawn,spawnSync} from 'node:child_process'
const root=path.join(process.env.TEMP,'jshz-acceptance-0e8f3a4','nginx');fs.mkdirSync(root,{recursive:true});fs.mkdirSync(path.join(root,'logs'),{recursive:true})
const prefix=root.replaceAll('\\','/')+'/'
const config=color=>`pid logs/nginx.pid; error_log logs/error.log notice; events { worker_connections 1024; } http { access_log off; server { listen 127.0.0.1:18082; location / { return 200 '${color}'; } } }`
fs.writeFileSync(path.join(root,'nginx.conf'),config('green'))
const nginx='D:/nginx/nginx.exe'
const server=spawn(nginx,['-p',prefix,'-c','nginx.conf'],{windowsHide:true,stdio:'ignore'})
const delay=ms=>new Promise(r=>setTimeout(r,ms))
let before,after,failures=0,green=0,blue=0
try {
 for(let i=0;i<30;i++){try{await fetch('http://127.0.0.1:18082/');break}catch{await delay(100)}}
 before=fs.readFileSync(path.join(root,'logs/nginx.pid'),'utf8').trim()
 const probe=(async()=>{for(let i=0;i<300;i++){try{const r=await fetch('http://127.0.0.1:18082/');const b=await r.text();if(r.status!==200)failures++;else if(b==='green')green++;else if(b==='blue')blue++;else failures++}catch{failures++}await delay(10)}})()
 await delay(500);fs.writeFileSync(path.join(root,'nginx.conf'),config('blue'))
 const reload=spawnSync(nginx,['-p',prefix,'-c','nginx.conf','-s','reload'],{windowsHide:true,encoding:'utf8'});if(reload.status!==0)throw new Error(reload.stderr)
 await probe;after=fs.readFileSync(path.join(root,'logs/nginx.pid'),'utf8').trim()
 const result={requests:300,failures,green,blue,masterUnchanged:before===after,scope:'isolated current-user Nginx; production SYSTEM identity not tested'}
 fs.writeFileSync(path.join(root,'result.json'),JSON.stringify(result,null,2));console.log(JSON.stringify(result))
 if(failures||!green||!blue||before!==after)process.exitCode=1
}finally{spawnSync(nginx,['-p',prefix,'-c','nginx.conf','-s','quit'],{windowsHide:true})}

