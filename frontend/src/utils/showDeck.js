// 串场 deck 构建纯函数（消费方：ProjectShowEditor.vue「从日程生成」、ShowWizard.vue 三步向导）
// 环节行分类/人员匹配/页面组装的唯一真源，禁止复制本地副本；照片与简介由调用方注入
export function agendaRows(agenda, overview) {
  const speakers = overview?.speakers || []
  const rows = agenda?.length ? agenda : [...new Set(speakers.map(item =>
    [item.segmentTime, item.segmentTitle, item.expertName].filter(Boolean).join(' · ')))].filter(Boolean)
  return { rows, speakers }
}

export function classifyRow(row) {
  const cells = String(row || '').split(' · ')
  const title = (cells.length > 1 ? cells[1] : String(row || '')).slice(0, 240)
  const label = /茶歇|休息/.test(row) ? '茶歇' : /主持|致辞|开场/.test(row) ? '主持与致辞' : '会议环节'
  return { label, title }
}

export function matchSpeakers(row, speakers) {
  return (speakers || []).filter(person => person.expertName && (row.includes(person.expertName) ||
    (person.segmentTitle && row.includes(person.segmentTitle))))
}

export function personLine(person) {
  return [person?.expertName, person?.expertUnit, person?.expertTitle].filter(Boolean).join(' ')
}

// 环节页正文：环节行 + 去重人员行（+调用方注入的附加行，如简介分句），总长沿用 4000 上限
export function eventBody(extraLines) {
  return (extraLines || []).filter(Boolean).join('\n').slice(0, 4000)
}

export function coverSlide(projectName, meetingDate) {
  return { label: '会议串场', title: projectName || '', body: meetingDate || '', image: '' }
}

export function agendaSlides(rows) {
  const pages = []
  for (let offset = 0; offset < (rows || []).length; offset += 8) {
    pages.push({ label: '会议议程', title: '议程', body: rows.slice(offset, offset + 8).join('\n').slice(0, 4000), image: '' })
  }
  return pages
}

export function thanksSlide() {
  return { label: '', title: '感谢参会', body: '', image: '' }
}

// 简介按句拆行（步进逐句浮现）；保留句末标点
export function splitSentences(text) {
  return String(text || '').replace(/\s+/g, ' ').match(/[^。！？;；]+[。！？;；]?/g)?.map(s => s.trim()).filter(Boolean) || []
}

// ===== 结构化页面模型（2026-11-21，配后端 kind 渲染） =====

// 简历语义分段：换行/分号/句号切分为任职条目，过滤过短碎片，上限 30 条
export function splitResumeItems(text) {
  return String(text || '')
    .split(/[\n；;。]+/)
    .map(s => s.trim().replace(/^[，,、\s]+/, '').replace(/[，,]$/, ''))
    .filter(s => s.length >= 4)
    .slice(0, 30)
}

// 环节行 → 章节归类：开场致辞类 / 多学科讨论 / 学术报告；茶歇不立章
export function sectionOf(row) {
  if (/讨论/.test(row)) return '多学科讨论'
  if (/茶歇|休息/.test(row)) return ''
  if (/主持|致辞|开场|签到|上市|仪式/.test(row)) return '开场致辞'
  return '学术报告'
}

export function coverSlideV2({ org, title, sub, infos = [] }) {
  return { label: org || '', title: title || '', body: sub || '', image: '', kind: 'cover', items: infos.slice(0, 12), persons: null }
}

export function sectionSlideV2({ no, title, person }) {
  const p = person ? { role: person.role || '', name: person.name || '', title: person.title || '', unit: person.unit || '', image: person.avatar || '' } : null
  return { label: '', title: title || '', body: '', image: '', kind: 'section', items: no ? [no] : [], persons: p ? [p] : null }
}

export function bioSlideV2({ label, person }) {
  return {
    label: label || '', title: person?.name || '', body: '', image: '', kind: 'bio', items: null,
    persons: [{ role: person?.role || '', name: person?.name || '', title: person?.title || '', unit: person?.unit || '', image: person?.avatar || '' }]
  }
}

export function resumeSlideV2({ person }) {
  return {
    label: '个人简历', title: [person?.name, person?.title].filter(Boolean).join(' ') || '专家简介',
    body: '', image: '', kind: 'resume', items: splitResumeItems(person?.intro || ''),
    persons: [{ role: person?.role || '', name: person?.name || '', title: person?.title || '', unit: person?.unit || '', image: person?.avatar || '' }]
  }
}

export function panelSlideV2({ label, title, persons }) {
  return {
    label: label || '', title: title || '', body: '', image: '', kind: 'panel', items: null,
    persons: (persons || []).slice(0, 8).map(p => ({
      role: p.role || '', name: p.name || '', title: p.title || '', unit: p.unit || '', image: p.avatar || ''
    }))
  }
}

// 环节角色推断（可被用户手改覆盖）
export function guessRole(row, label) {
  if (/主席/.test(row)) return '大会主席'
  if (/主持/.test(row) || label === '主持与致辞') return '主持人'
  if (/讨论/.test(row)) return '讨论嘉宾'
  if (/致辞/.test(row)) return '致辞嘉宾'
  return '主讲嘉宾'
}

// 照片池化：同一张照片（dataURL）全稿只存一份到 deck.photos，页面内以 "ph:序号" 引用，
// 避免一位专家出现在多个环节时照片重复嵌入导致 deck 超 10MB 上限。返回 { slides, photos }。
export function dedupePhotos(slides) {
  const photos = []
  const index = new Map()
  const refOf = image => {
    if (!image || image.startsWith('ph:')) return image
    let i = index.get(image)
    if (i === undefined) {
      i = photos.length
      index.set(image, i)
      photos.push(image)
    }
    return `ph:${i}`
  }
  const out = (slides || []).map(s => {
    const persons = s.persons ? s.persons.map(p => ({ ...p, image: refOf(p.image) })) : s.persons
    return { ...s, image: refOf(s.image), persons }
  })
  return { slides: out, photos }
}
