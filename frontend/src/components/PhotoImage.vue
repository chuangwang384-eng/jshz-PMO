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
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { fetchDocumentBlob, fetchPhotoBlob, fetchTempPhotoBlob } from '../api/expert'

// 照片展示组件：后端照片需登录鉴权，通过 blob 方式加载展示
// temp=true 走临时照片目录 /api/files-tmp（待审批单的新照片预览）
// doc=true 走专家资料目录 /api/files-doc（医生证等资质资料缩略图，2026-11-23）
const props = defineProps({
  filename: { type: String, default: '' },
  src: { type: String, default: '' },
  alt: { type: String, default: '照片' },
  fit: { type: String, default: 'cover' },
  preview: { type: Boolean, default: true },
  width: { type: [Number, String], default: 64 },
  height: { type: [Number, String], default: 64 },
  temp: { type: Boolean, default: false },
  doc: { type: Boolean, default: false },
  emptyText: { type: String, default: '未上传' }
})

const storedUrl = ref('')
const url = computed(() => props.src || storedUrl.value)
const image = ref(null)

function openPreview() {
  if (props.preview) image.value?.showPreview()
}

// 2026-11-23 P2 修复：blob URL 换图/卸载时 revoke（列表翻页不再累积内存）；
// 序号防竞态，慢返回的旧照片不覆盖新照片
let loadSeq = 0
function releaseUrl() {
  if (storedUrl.value) URL.revokeObjectURL(storedUrl.value)
  storedUrl.value = ''
}
async function load() {
  const seq = ++loadSeq
  releaseUrl()
  if (!props.filename) return
  try {
    const resp = props.doc ? await fetchDocumentBlob(props.filename)
      : props.temp ? await fetchTempPhotoBlob(props.filename) : await fetchPhotoBlob(props.filename)
    if (seq !== loadSeq) return
    storedUrl.value = URL.createObjectURL(resp.data)
  } catch (e) {
    // 照片不存在或无权限：保持占位
  }
}

onMounted(load)
onBeforeUnmount(releaseUrl)
watch(() => props.filename, load)
watch(() => props.temp, load)
watch(() => props.doc, load)
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
