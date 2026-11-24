const listKeys = ['keyword', 'workUnit', 'relationTypeId', 'hasRelations', 'page', 'pageSize', 'deleted']

export function customerListQuery(query) {
  return Object.fromEntries(listKeys.filter((key) => query[key] != null).map((key) => [key, query[key]]))
}
