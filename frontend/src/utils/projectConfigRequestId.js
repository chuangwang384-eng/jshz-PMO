// getRandomValues works on the existing HTTP intranet deployment as well as HTTPS.
// randomUUID is restricted to secure contexts and must not gate configuration saves.
export function createProjectConfigRequestId(cryptoSource = globalThis.crypto) {
  const bytes = cryptoSource.getRandomValues(new Uint8Array(16))
  return Array.from(bytes, (value) => value.toString(16).padStart(2, '0')).join('')
}
