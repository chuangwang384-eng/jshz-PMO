<template>
  <el-dialog
    v-model="visible"
    :title="editingId ? '编辑公告' : '新建公告'"
    width="820px"
    destroy-on-close
    @closed="resetForm"
  >
    <el-form label-width="90px">
      <el-form-item label="标题" required>
        <el-input v-model="form.title" maxlength="120" placeholder="请输入公告标题" />
      </el-form-item>
      <el-form-item label="正文" required>
        <div class="editor-wrap">
          <Toolbar :editor="editorRef" :default-config="toolbarConfig" mode="default" class="editor-toolbar" />
          <Editor
            v-model="form.content"
            :default-config="editorConfig"
            mode="default"
            class="editor-body"
            @on-created="handleCreated"
          />
        </div>
      </el-form-item>
      <el-row>
        <el-col :span="12">
          <el-form-item label="生效时间">
            <el-date-picker
              v-model="form.effectiveTime"
              type="datetime"
              value-format="YYYY-MM-DDTHH:mm:ss"
              placeholder="默认立即生效"
              style="width: 100%"
            />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="失效时间">
            <el-date-picker
              v-model="form.expireTime"
              type="datetime"
              value-format="YYYY-MM-DDTHH:mm:ss"
              placeholder="留空 = 永久有效"
              style="width: 100%"
            />
          </el-form-item>
        </el-col>
      </el-row>
      <el-row>
        <el-col :span="12">
          <el-form-item label="置顶">
            <el-switch v-model="form.isTop" :active-value="1" :inactive-value="0" />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="状态">
            <el-radio-group v-model="form.status">
              <el-radio value="ONLINE">上架</el-radio>
              <el-radio value="OFFLINE">下架</el-radio>
            </el-radio-group>
          </el-form-item>
        </el-col>
      </el-row>
    </el-form>
    <template #footer>
      <el-button @click="close">取消</el-button>
      <el-button type="primary" :loading="saving" @click="confirmSave">保存</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { ref, reactive, computed, shallowRef, watch, onBeforeUnmount } from 'vue'
import { ElMessage } from 'element-plus'
import '@wangeditor/editor/dist/css/style.css'
import { Editor, Toolbar } from '@wangeditor/editor-for-vue'
import {
  createAnnouncement,
  updateAnnouncement,
  uploadRichtextImage
} from '../../api/announcement'

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  // 传入编辑目标（含 id/title/content/effectiveTime/expireTime/isTop/status）；null 表示新建
  editing: { type: Object, default: null }
})
const emit = defineEmits(['update:modelValue', 'saved'])

const visible = computed(() => props.modelValue)
const editingId = computed(() => (props.editing ? props.editing.id : null))

const saving = ref(false)
// 单一响应式模型：模板 v-model 绑定与保存取值共用 form，避免"展示新值、提交旧快照"
const form = reactive({ title: '', content: '', effectiveTime: '', expireTime: '', isTop: 0, status: 'ONLINE' })

// wangEditor 实例（shallowRef，避免深层响应开销）
const editorRef = shallowRef()
const toolbarConfig = {}
const editorConfig = {
  placeholder: '请输入公告正文…',
  MENU_CONF: {
    uploadImage: {
      async customUpload(resultFiles, insertFn) {
        const file = resultFiles[0]
        const res = await uploadRichtextImage(file)
        insertFn(res.url, file.name || '', '')
      }
    }
  }
}

watch(
  () => props.modelValue,
  (open) => {
    if (!open) return
    resetForm()
    const row = props.editing
    if (row) {
      editingIdForForm(row)
    }
  }
)

function handleCreated(editor) {
  editorRef.value = editor
}

function resetForm() {
  Object.assign(form, { title: '', content: '', effectiveTime: '', expireTime: '', isTop: 0, status: 'ONLINE' })
}

function editingIdForForm(row) {
  Object.assign(form, {
    title: row.title,
    content: row.content,
    effectiveTime: row.effectiveTime || '',
    expireTime: row.expireTime || '',
    isTop: row.isTop,
    status: row.status
  })
}

function close() {
  emit('update:modelValue', false)
}

// 本地时间串（与 value-format YYYY-MM-DDTHH:mm:ss 同构），用于"已过去"判断
function localNow() {
  const d = new Date()
  const p = (n) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())}T${p(d.getHours())}:${p(d.getMinutes())}:${p(d.getSeconds())}`
}

async function confirmSave() {
  if (!form.title || !form.title.trim()) {
    ElMessage.warning('请输入标题')
    return
  }
  if (!form.content || !form.content.trim()) {
    ElMessage.warning('请输入正文内容')
    return
  }
  // P2-1（2026-11-26 审查修复）：生效/失效时间前后校验，后端 applyForm 同口径兜底
  if (form.effectiveTime && form.expireTime && form.expireTime <= form.effectiveTime) {
    ElMessage.warning('失效时间必须晚于生效时间')
    return
  }
  if (!editingId.value && form.expireTime && form.expireTime < localNow()) {
    ElMessage.warning('失效时间不能早于当前时间')
    return
  }
  saving.value = true
  try {
    const payload = {
      title: form.title.trim(),
      content: form.content,
      effectiveTime: form.effectiveTime || null,
      expireTime: form.expireTime || null,
      isTop: form.isTop,
      status: form.status
    }
    if (editingId.value) {
      await updateAnnouncement(editingId.value, payload)
      ElMessage.success('公告已更新')
    } else {
      await createAnnouncement(payload)
      ElMessage.success('公告已发布')
    }
    emit('update:modelValue', false)
    emit('saved')
  } finally {
    saving.value = false
  }
}

onBeforeUnmount(() => {
  if (editorRef.value) {
    editorRef.value.destroy()
  }
})
</script>

<style scoped>
.editor-wrap {
  width: 100%;
  border: 1px solid #dcdfe6;
  border-radius: 4px;
}
.editor-toolbar {
  border-bottom: 1px solid #dcdfe6;
}
.editor-body {
  height: 320px;
  overflow-y: hidden;
}
</style>
