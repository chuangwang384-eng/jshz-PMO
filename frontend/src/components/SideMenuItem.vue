<template>
  <!-- 父菜单：点标题跳父路由，点箭头展开/收起子菜单 -->
  <el-sub-menu v-if="item.children?.length" :index="item.path">
    <template #title>
      <div class="side-submenu-title">
        <span class="side-submenu-label" @click.stop="navigateTo(item.path)">{{ item.title }}</span>
        <el-icon class="side-submenu-arrow" :class="{ 'is-expanded': expanded }" @click.stop="toggleExpand">
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
import { inject, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ArrowDown } from '@element-plus/icons-vue'

defineOptions({ name: 'SideMenuItem' })
defineProps({
  item: {
    type: Object,
    required: true
  }
})

const router = useRouter()
// 父级 App.vue 通过 provide 注入 el-menu 组件实例，用于 open/close 展开子菜单
const menuRef = inject('sideMenuRef', null)
const expanded = ref(false)

function toggleExpand() {
  expanded.value = !expanded.value
  if (menuRef?.value) {
    if (expanded.value) {
      menuRef.value.open(props.item.path)
    } else {
      menuRef.value.close(props.item.path)
    }
  }
}

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
  transition: transform 0.2s;
  color: rgba(255, 255, 255, 0.45);
  cursor: pointer;
  flex-shrink: 0;
}
.side-submenu-arrow.is-expanded {
  transform: rotate(180deg);
}
</style>
