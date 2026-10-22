<template>
  <div class="login-page">
    <el-card class="login-card">
      <div class="login-title">景帅会展内部管理系统</div>
      <div class="login-subtitle">正式版 V1.001</div>
      <el-form
        ref="formRef"
        :model="form"
        :rules="rules"
        size="large"
        @keyup.enter="handleLogin"
      >
        <el-form-item prop="username">
          <el-input
            v-model="form.username"
            placeholder="账号（员工姓名）"
            clearable
            autocomplete="username"
          >
            <template #prefix><span class="prefix-icon">👤</span></template>
          </el-input>
        </el-form-item>
        <el-form-item prop="password">
          <el-input
            v-model="form.password"
            type="password"
            placeholder="密码"
            show-password
            autocomplete="current-password"
          >
            <template #prefix><span class="prefix-icon">🔒</span></template>
          </el-input>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" class="login-btn" :loading="loading" @click="handleLogin">
            登 录
          </el-button>
        </el-form-item>
        <div class="login-tip">首次使用：输入姓名 + 初始密码 111111 即可自动注册并登录，登录后请修改密码</div>
      </el-form>
    </el-card>
  </div>
</template>

<script setup>
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useAuthStore } from '../store/auth'

const router = useRouter()
const route = useRoute()
const auth = useAuthStore()

const formRef = ref()
const loading = ref(false)
const form = reactive({ username: '', password: '' })

const rules = {
  username: [{ required: true, message: '请输入账号（员工姓名）', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }]
}

async function handleLogin() {
  try {
    await formRef.value.validate()
  } catch (e) {
    return
  }
  loading.value = true
  try {
    const user = await auth.login(form.username.trim(), form.password)
    ElMessage.success(`欢迎回来，${user.username}`)
    const redirect = route.query.redirect
    router.push(typeof redirect === 'string' && redirect ? redirect : '/company/experts')
  } catch (e) {
    // 错误提示已由 request 拦截器统一处理
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.login-page {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #1f2d3d 0%, #3a5a80 100%);
}
.login-card {
  width: 400px;
  padding: 12px 8px;
  border-radius: 8px;
}
.login-title {
  text-align: center;
  font-size: 24px;
  font-weight: 700;
  color: #1f2d3d;
}
.login-subtitle {
  text-align: center;
  font-size: 13px;
  color: #909399;
  margin: 4px 0 24px;
}
.login-btn {
  width: 100%;
}
.login-tip {
  text-align: center;
  font-size: 12px;
  color: #909399;
}
.prefix-icon {
  font-style: normal;
  font-size: 14px;
}
</style>
