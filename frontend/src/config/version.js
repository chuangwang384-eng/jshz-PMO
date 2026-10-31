// 版本展示工具：唯一真源为项目根 D:\jshz-PMO\version.txt
// 构建期由 vite.config.js 注入 import.meta.env.VITE_APP_VERSION，
// 前缀由 .env 的 VITE_APP_VERSION_PREFIX 控制（生产默认"正式版"）。
const versionPrefix = import.meta.env.VITE_APP_VERSION_PREFIX || ''
const versionLabel = 'v' + (import.meta.env.VITE_APP_VERSION || '0.0.0')

export { versionPrefix, versionLabel }
