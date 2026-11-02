<template>
  <div class="login-page">
    <el-card class="login-card">
      <div class="login-title">景帅会展内部管理系统</div>
      <div class="login-subtitle">{{ versionPrefix }}{{ versionLabel }}</div>
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
            :placeholder="inputPlaceholder"
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
        <div class="login-tip">{{ loginTip }}</div>
      </el-form>
    </el-card>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useAuthStore } from '../store/auth'
import { versionPrefix, versionLabel } from '../config/version'
import { getWecomConfig } from '../api/wecom'

const router = useRouter()
const route = useRoute()
const auth = useAuthStore()

const formRef = ref()
const loading = ref(false)
const form = reactive({ username: '', password: '' })

// 登录模式：NAME（姓名+自动注册，默认/回滚档）/ COMPAT（姓名未绑定或手机号，过渡）/ MOBILE（仅手机号）
// 由后端 GET /api/auth/wecom/config 返回，前端不写死模式
const loginMode = ref('NAME')

const modeText = {
  NAME: {
    placeholder: '账号（员工姓名）',
    tip: '首次使用：输入姓名 + 初始密码 111111 即可自动注册并登录，登录后请修改密码',
    ruleMessage: '请输入账号（员工姓名）'
  },
  COMPAT: {
    placeholder: '账号（姓名或手机号）',
    tip: '已绑定企微用户请使用手机号登录；未绑定用户仍可输入姓名 + 初始密码 111111 自动注册',
    ruleMessage: '请输入姓名或手机号'
  },
  MOBILE: {
    placeholder: '手机号（企微通讯录手机号）',
    tip: '请使用企微通讯录同步的手机号 + 密码登录；未绑定请联系管理员',
    ruleMessage: '请输入手机号'
  }
}

const inputPlaceholder = computed(() => modeText[loginMode.value]?.placeholder || modeText.NAME.placeholder)
const loginTip = computed(() => modeText[loginMode.value]?.tip || modeText.NAME.tip)

const rules = computed(() => ({
  username: [{ required: true, message: modeText[loginMode.value]?.ruleMessage || modeText.NAME.ruleMessage, trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }]
}))

async function fetchLoginMode() {
  try {
    const data = await getWecomConfig()
    const mode = String(data?.loginMode || 'NAME').toUpperCase()
    loginMode.value = ['NAME', 'COMPAT', 'MOBILE'].includes(mode) ? mode : 'NAME'
  } catch (e) {
    // 配置接口不可达时按默认 NAME 模式渲染，不影响登录
    loginMode.value = 'NAME'
  }
}

async function handleLogin() {
  try {
    await formRef.value.validate()
  } catch (e) {
    console.error('登录表单校验失败', e)
    ElMessage.error('请输入正确的用户名和密码')
    return
  }
  loading.value = true
  try {
    const user = await auth.login(form.username.trim(), form.password)
    ElMessage.success(`欢迎回来，${user.username}`)
    const redirect = route.query.redirect
    router.push(typeof redirect === 'string' && redirect ? redirect : '/experts')
  } catch (e) {
    console.error('登录请求失败', e)
    ElMessage.error(e?.message || '登录失败，请稍后重试')
  } finally {
    loading.value = false
  }
}

onMounted(fetchLoginMode)
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
