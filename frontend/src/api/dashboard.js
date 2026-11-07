import request from './request'
export const overview = (sections, signal) => request.get('/dashboard/overview', { params: sections ? { sections: sections.join(',') } : undefined, signal, timeout: 3000, silent: true })
export const globalSearch = (keyword, signal) => request.get('/search/global', { params: { keyword }, signal, timeout: 4000, silent: true })
export const moduleSearch = (module, keyword, page = 1, signal) => request.get(`/search/global/module/${module}`, { params: { keyword, page, size: 20 }, signal, timeout: 4000, silent: true })
export const dashboardFocus = (kind, id) => request.get(`/dashboard/focus/${kind}/${id}`, { silent: true })
export const getDashboardLayout = () => request.get('/dashboard/layout/v2', { silent: true })
export const saveDashboardLayout = (revision, items) => request.put('/dashboard/layout/v2', { revision, items }, { silent: true })

export const getQuickAccess = () => request.get('/dashboard/quick-access', { silent: true })
export const saveQuickAccess = items => request.put('/dashboard/quick-access', { items }, { silent: true })

export const getLegacyDashboardLayout = () => request.get('/dashboard/layout', { silent: true })
