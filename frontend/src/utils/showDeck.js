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
