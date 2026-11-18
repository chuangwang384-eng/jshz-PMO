<template>
  <el-image
    v-if="url"
    ref="image"
    :src="url"
    :alt="alt"
    :preview-src-list="preview && url ? [url] : []"
    preview-teleported
    :close-on-press-escape="true"
    :fit="fit"
    :tabindex="preview ? 0 : undefined"
    :aria-label="preview ? '放大查看' + alt : undefined"
    @keydown.enter.self.prevent="openPreview"
    @keydown.space.self.prevent="openPreview"
    class="photo-image"
    :style="{ width: width + 'px', height: height + 'px' }"
  />
  <span v-else class="photo-empty">{{ emptyText }}</span>
</template>

<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { fetchPhotoBlob, fetchTempPhotoBlob } from '../api/expert'

// 照片展示组件：后端照片需登录（Bearer token），通过 blob 方式加载展示
// temp=true 走临时照片目录 /api/files-tmp（待审批单的新照片预览）
const props = defineProps({
  filename: { type: String, default: '' },
  src: { type: String, default: '' },
  alt: { type: String, default: '照片' },
  fit: { type: String, default: 'cover' },
  preview: { type: Boolean, default: true },
  width: { type: [Number, String], default: 64 },
  height: { type: [Number, String], default: 64 },
  temp: { type: Boolean, default: false },
  emptyText: { type: String, default: '未上传' }
})

const storedUrl = ref('')
const url = computed(() => props.src || storedUrl.value)
const image = ref(null)

function openPreview() {
  if (props.preview) image.value?.showPreview()
}

async function load() {
  storedUrl.value = ''
  if (!props.filename) return
  try {
    const resp = props.temp ? await fetchTempPhotoBlob(props.filename) : await fetchPhotoBlob(props.filename)
    storedUrl.value = URL.createObjectURL(resp.data)
  } catch (e) {
    // 照片不存在或无权限：保持占位
  }
}

onMounted(load)
watch(() => props.filename, load)
watch(() => props.temp, load)
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
