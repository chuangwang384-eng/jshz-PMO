// getRandomValues works on the existing HTTP intranet deployment as well as HTTPS.
// randomUUID is restricted to secure contexts and must not gate configuration saves.
export function createProjectConfigRequestId(cryptoSource = globalThis.crypto) {
  const bytes = cryptoSource.getRandomValues(new Uint8Array(16))
  return Array.from(bytes, (value) => value.toString(16).padStart(2, '0')).join('')
}

// 带连字符的 UUIDv4 形态（后端 request_key VARCHAR(36) 且做 UUID.fromString 校验的接口使用，
// 如对客报价生成），内部复用 createProjectConfigRequestId 的随机源
export function createUuidRequestId(cryptoSource = globalThis.crypto) {
  const hex = createProjectConfigRequestId(cryptoSource)
  return `${hex.slice(0, 8)}-${hex.slice(8, 12)}-${hex.slice(12, 16)}-${hex.slice(16, 20)}-${hex.slice(20)}`
}
