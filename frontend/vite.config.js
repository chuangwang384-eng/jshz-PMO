import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

// ===== 版本号读取：唯一真源 D:\jshz-PMO\version.txt =====
const __dirname = path.dirname(fileURLToPath(import.meta.url))
const versionFile = path.resolve(__dirname, '../version.txt')
let appVersion = '0.0.0'
try {
  const versionText = fs.readFileSync(versionFile, 'utf-8').replace(/^\uFEFF/, '')
  const match = versionText.match(/^revision\s*=\s*(.+)$/m)
  appVersion = match ? match[1].trim() : '0.0.0'
  if (!match) console.warn('[vite] version.txt 未找到 revision 行，回退 0.0.0')
} catch (e) {
  console.warn('[vite] 读取 version.txt 失败，回退 0.0.0:', e.message)
}

// https://vitejs.dev/config/
export default defineConfig({
  plugins: [vue()],
  define: {
    'import.meta.env.VITE_APP_VERSION': JSON.stringify(appVersion),
  },
  server: {
    // 内网部署：开发环境代理到后端服务
    host: '0.0.0.0',
    port: 5173,
    proxy: {
      '/api': {
        target: process.env.VITE_API_PROXY_TARGET || 'http://127.0.0.1:8080',
        changeOrigin: true
      }
    }
  }
})
