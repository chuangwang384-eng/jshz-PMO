<template>
  <el-image
    v-if="url"
    :src="url"
    :preview-src-list="preview && url ? [url] : []"
    preview-teleported
    fit="cover"
    class="photo-image"
    :style="{ width: width + 'px', height: height + 'px' }"
  />
  <span v-else class="photo-empty">未上传</span>
</template>

<script setup>
import { onMounted, ref, watch } from 'vue'
import { fetchPhotoBlob } from '../api/expert'

// 照片展示组件：后端照片需登录（Bearer token），通过 blob 方式加载展示
const props = defineProps({
  filename: { type: String, default: '' },
  preview: { type: Boolean, default: true },
  width: { type: [Number, String], default: 64 },
  height: { type: [Number, String], default: 64 }
})

const url = ref('')

async function load() {
  url.value = ''
  if (!props.filename) return
  try {
    const resp = await fetchPhotoBlob(props.filename)
    url.value = URL.createObjectURL(resp.data)
  } catch (e) {
    // 照片不存在或无权限：保持占位
  }
}

onMounted(load)
watch(() => props.filename, load)
</script>

<style scoped>
.photo-image {
  width: 64px;
  height: 64px;
  border-radius: 4px;
  display: block;
}
.photo-empty {
  color: #a8abb2;
  font-size: 12px;
}
</style>
