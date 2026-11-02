<template>
  <!-- 父菜单：点标题或箭头仅展开/收起子菜单（需求 2：一级菜单不再跳父路由） -->
  <el-sub-menu v-if="item.children?.length" :index="item.path" @open="expanded = true" @close="expanded = false">
    <template #title>
      <div class="side-submenu-title">
        <span class="side-submenu-label">{{ item.title }}</span>
        <el-icon class="side-submenu-arrow" :class="{ 'is-expanded': expanded }">
          <ArrowDown />
        </el-icon>
      </div>
    </template>
    <SideMenuItem v-for="(c, i) in item.children" :key="i" :item="c" />
  </el-sub-menu>
  <!-- 叶子菜单：直接跳转 -->
  <el-menu-item v-else :index="item.path" @click="navigateTo(item.path)">{{ item.title }}</el-menu-item>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ArrowDown } from '@element-plus/icons-vue'

defineOptions({ name: 'SideMenuItem' })
const props = defineProps({
  item: {
    type: Object,
    required: true
  }
})

const router = useRouter()
const expanded = ref(false)

// 初始展开兜底：当前路由命中本菜单路径或子路径时默认展开
onMounted(() => {
  const p = router.currentRoute.value.path
  if (p === props.item.path || p.startsWith(props.item.path + '/')) {
    expanded.value = true
  }
})

function navigateTo(path) {
  if (router.currentRoute.value.path !== path) {
    router.push(path)
  }
}
</script>

<style scoped>
.side-submenu-title {
  display: flex;
  align-items: center;
  justify-content: space-between;
  width: 100%;
  padding-right: 6px;
}
.side-submenu-label {
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  cursor: pointer;
}
.side-submenu-arrow {
  width: 24px;
  height: 48px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  transition: transform 0.2s;
  color: rgba(255, 255, 255, 0.45);
  cursor: pointer;
  flex-shrink: 0;
}
.el-sub-menu.is-opened .side-submenu-arrow,
.side-submenu-arrow.is-expanded {
  transform: rotate(180deg);
}
</style>
