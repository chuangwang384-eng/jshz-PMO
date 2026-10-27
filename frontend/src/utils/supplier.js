// 供应商模块公共纯函数（SupplierList 与 SupplierDetail 共用，防两处漂移）

// 类型业务键 -> Tag 颜色
export function supplierTypeTagColor(bizKey) {
  const map = {
    HOTEL: 'success',
    PRINT: 'warning',
    MEDIA: 'primary',
    IMAGING: 'danger',
    FLEET: 'info',
    TICKET: 'success'
  }
  return map[bizKey] || 'info'
}

// 城市平铺列表 -> 按省份分组（保持字典预置顺序），供 el-option-group / 城市管理页共用
export function groupCitiesByProvince(cities) {
  const groups = []
  const map = {}
  for (const c of cities || []) {
    if (!map[c.province]) {
      map[c.province] = { province: c.province, cities: [] }
      groups.push(map[c.province])
    }
    map[c.province].cities.push(c)
  }
  return groups
}
