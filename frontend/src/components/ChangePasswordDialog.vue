<template>
  <el-dialog
    :model-value="modelValue"
    :title="title"
    width="440px"
    :close-on-click-modal="false"
    :close-on-press-escape="!forceMode && !submitting"
    :show-close="!forceMode && !submitting"
    @update:model-value="emit('update:modelValue', $event)"
    @closed="resetForm"
    align-center
  >
    <el-alert
      v-if="forceMode"
      type="warning"
      :closable="false"
      show-icon
      :title="hint"
      style="margin-bottom: 16px"
    />
    <el-form ref="formRef" :model="form" :rules="rules" label-width="90px">
      <el-form-item label="旧密码" prop="oldPassword">
        <el-input v-model="form.oldPassword" type="password" show-password autocomplete="current-password" />
      </el-form-item>
      <el-form-item label="新密码" prop="newPassword">
        <el-input v-model="form.newPassword" type="password" show-password autocomplete="new-password" />
      </el-form-item>
      <el-form-item label="确认新密码" prop="confirmPassword">
        <el-input v-model="form.confirmPassword" type="password" show-password autocomplete="new-password" placeholder="再次输入新密码" />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button type="primary" :loading="submitting" @click="submit">确认修改</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { useAuthStore } from '../store/auth'

defineProps({
  modelValue: { type: Boolean, default: false },
  title: { type: String, default: '修改初始密码' },
  forceMode: { type: Boolean, default: true },
  hint: { type: String, default: '首次登录须修改初始密码后才能继续使用系统' }
})
const emit = defineEmits(['update:modelValue'])

const auth = useAuthStore()
const formRef = ref()
const submitting = ref(false)
const form = reactive({ oldPassword: '', newPassword: '', confirmPassword: '' })

const validateConfirm = (rule, value, callback) => {
  if (!value) {
    callback(new Error('请再次输入新密码'))
  } else if (value !== form.newPassword) {
    callback(new Error('两次输入的新密码不一致'))
  } else {
    callback()
  }
}

const rules = {
  oldPassword: [{ required: true, message: '请输入旧密码', trigger: 'blur' }],
  newPassword: [
    { required: true, message: '请输入新密码', trigger: 'blur' }
  ],
  confirmPassword: [{ validator: validateConfirm, trigger: 'blur' }]
}

function resetForm() {
  Object.assign(form, { oldPassword: '', newPassword: '', confirmPassword: '' })
  formRef.value?.clearValidate()
}

async function submit() {
  if (submitting.value) return
  submitting.value = true
  try {
    await formRef.value.validate()
  } catch {
    submitting.value = false
    return
  }
  try {
    await auth.changePassword(form.oldPassword, form.newPassword)
    resetForm()
    ElMessage.success('密码修改成功')
    emit('update:modelValue', false)
  } catch (e) {
    // 错误提示已由 request 拦截器统一处理
  } finally {
    submitting.value = false
  }
}
</script>
