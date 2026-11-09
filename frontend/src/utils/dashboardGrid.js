import { GridStack } from 'gridstack'
import 'gridstack/dist/gridstack.css'

export function createDashboardGrid(element, limits, onChange) {
  const grid = GridStack.init({
    column: 12, cellHeight: 56, margin: 4, maxRow: 200, mode: 'float',
    auto: false, animate: false, handle: '.layout-handle',
    resizable: { handles: 'se' }
  }, element)

  function snapshot() {
    return grid.engine.nodes.map(({ id, x, y, w, h }) => ({ i: id, x, y, w, h }))
  }

  function sync(items) {
    const wanted = new Set(items.map(item => item.i))
    grid.batchUpdate()
    for (const node of [...grid.engine.nodes]) {
      if (!wanted.has(node.id)) grid.removeWidget(node.el, false, false)
    }
    for (const item of items) {
      const el = Array.from(element.children).find(child => child.dataset.card === item.i)
      if (el && !el.gridstackNode) {
        grid.makeWidget(el, { ...item, ...limits(item.i), id: item.i, maxH: 24 })
      }
    }
    grid.batchUpdate(false)
  }

  function restore(items) {
    grid.load(items.map(item => ({ ...item, id: item.i })), false)
  }

  let beforeResize
  let resizeCancelled = false
  function escape(event) {
    if (event.key === 'Escape' && beforeResize) resizeCancelled = true
  }
  document.addEventListener('keydown', escape)
  grid.on('resizestart', () => {
    beforeResize = snapshot()
    resizeCancelled = false
  })
  grid.on('dragstop', () => onChange(snapshot()))
  grid.on('resizestop', () => {
    const restoreTo = resizeCancelled ? beforeResize : null
    beforeResize = undefined
    queueMicrotask(() => {
      if (!grid.engine) return
      if (restoreTo) restore(restoreTo)
      onChange(snapshot())
    })
  })
  return {
    sync,
    restore,
    setEnabled(enabled) { if (enabled) grid.enable(); else grid.disable() },
    adjust(id, dx, dy, dw, dh) {
      const node = grid.engine.nodes.find(item => item.id === id)
      if (!node) return
      grid.update(node.el, {
        x: Math.max(0, node.x + dx), y: Math.max(0, node.y + dy),
        w: Math.max(node.minW, node.w + dw), h: Math.max(node.minH, node.h + dh)
      })
      onChange(snapshot())
    },
    destroy() {
      document.removeEventListener('keydown', escape)
      grid.destroy(false)
    }
  }
}
