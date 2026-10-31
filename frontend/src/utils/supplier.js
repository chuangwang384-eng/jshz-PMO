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

// 城市平铺列表 -> 按省份分组（保持字典预置顺序），供省→市两级联动/展示共用
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

// 省份列表（两级联动第一级；按字典预置顺序去重）
export function provinceList(cities) {
  return groupCitiesByProvince(cities).map((g) => g.province)
}

// 指定省份下的城市列表（两级联动第二级）
export function citiesOfProvince(cities, province) {
  // v2.1：省份选填，未选省份时城市下拉直选全部城市（可搜索）
  if (!province) return cities || []
  const g = groupCitiesByProvince(cities).find((x) => x.province === province)
  return g ? g.cities : []
}
