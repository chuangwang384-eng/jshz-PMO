import request from './request'

const base = '/profile/overview'

export const getOrganization = () => request.get(base + '/organization')
export const getAccountActivity = (page) => request.get(base + '/activity', { params: { page } })
export const getDepartments = () => request.get(base + '/departments')
export const getMembers = (params) => request.get(base + '/members', { params })
export function saveDepartment(id, data) {
  return id == null ? request.post(base + '/departments', data) : request.put(base + '/departments/' + id, data)
}
export const saveMembership = (id, data) => request.put(base + '/members/' + id, data)
