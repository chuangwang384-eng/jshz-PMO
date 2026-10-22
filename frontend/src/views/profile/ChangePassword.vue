<template>
  <div>
    <div class="page-head">
      <span class="page-title">修改密码</span>
      <span class="page-sub">修改个人登录密码，修改成功后需重新登录</span>
    </div>

    <el-card class="change-pwd-card" shadow="never">
      <el-alert
        type="info"
        :closable="false"
        show-icon
        title="为保障账号安全，建议定期更换密码；新密码长度不少于 6 位"
        style="margin-bottom: 24px"
      />
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
        <el-form-item label="旧密码" prop="oldPassword">
          <el-input
            v-model="form.oldPassword"
            type="password"
            show-password
            size="large"
            placeholder="请输入旧密码"
            :prefix-icon="Lock"
          />
        </el-form-item>
        <el-form-item label="新密码" prop="newPassword">
          <el-input
            v-model="form.newPassword"
            type="password"
            show-password
            size="large"
            placeholder="至少 6 位"
            :prefix-icon="Key"
          />
        </el-form-item>
        <el-form-item label="确认新密码" prop="confirmPassword">
          <el-input
            v-model="form.confirmPassword"
            type="password"
            show-password
            size="large"
            placeholder="再次输入新密码"
            :prefix-icon="CircleCheck"
          />
        </el-form-item>
        <el-form-item class="form-actions">
          <el-button type="primary" size="large" :loading="submitting" @click="submit">确认修改</el-button>
          <el-button size="large" @click="resetForm">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>
  </div>
</template>

<script setup>
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Lock, Key, CircleCheck } from '@element-plus/icons-vue'
import { useAuthStore } from '../../store/auth'

const auth = useAuthStore()
const router = useRouter()
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
    { required: true, message: '请输入新密码', trigger: 'blur' },
    { min: 6, message: '新密码长度不能少于 6 位', trigger: 'blur' }
  ],
  confirmPassword: [
    { required: true, message: '请再次输入新密码', trigger: 'blur' },
    { validator: validateConfirm, trigger: 'blur' }
  ]
}

function resetForm() {
  formRef.value.resetFields()
}

async function submit() {
  try {
    await formRef.value.validate()
  } catch (e) {
    return
  }
  submitting.value = true
  try {
    await auth.changePassword(form.oldPassword, form.newPassword)
    ElMessage.success('密码修改成功')
    try {
      await ElMessageBox.alert('密码已修改，请使用新密码重新登录', '修改成功', { type: 'success' })
    } catch (e) {
      // 用户关闭提示框也继续登出
    }
    await auth.logout()
    router.push('/login')
  } catch (e) {
    // 错误提示已由 request 拦截器统一处理
  } finally {
    submitting.value = false
  }
}
</script>

<style scoped>
.page-head {
  display: flex;
  align-items: baseline;
  gap: 12px;
}
.page-title {
  position: relative;
  padding-left: 12px;
  font-size: 20px;
  font-weight: 600;
  color: #303133;
}
.page-title::before {
  content: '';
  position: absolute;
  left: 0;
  top: 50%;
  transform: translateY(-50%);
  width: 4px;
  height: 18px;
  border-radius: 2px;
  background: #409eff;
}
.page-sub {
  font-size: 13px;
  color: #909399;
}
.change-pwd-card {
  max-width: 480px;
  margin-top: 20px;
  border: 1px solid #e4e7ed;
  border-radius: 10px;
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.06);
}
.change-pwd-card :deep(.el-card__body) {
  padding: 28px 32px 24px;
}
.form-actions :deep(.el-form-item__content) {
  justify-content: flex-start;
}
.form-actions :deep(.el-button + .el-button) {
  margin-left: 12px;
}
</style>
