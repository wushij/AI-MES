<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Lock, User } from '@element-plus/icons-vue'
import BrandMark from '@/components/BrandMark.vue'
import AuthCaptchaField from '@/components/AuthCaptchaField.vue'
import { getCaptcha, getCaptchaRequired } from '@/api/auth'
import { useUserStore } from '@/stores/user'
import { toCaptchaDataUrl } from '@/utils/captcha'

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()

const username = ref('')
const password = ref('')
const loading = ref(false)
const showPassword = ref(false)
const showCaptcha = ref(false)
const captchaId = ref('')
const captchaImg = ref('')
const captchaAnswer = ref('')

async function refreshCaptcha() {
  const data = await getCaptcha()
  captchaId.value = data.id
  captchaImg.value = toCaptchaDataUrl(data.img)
  captchaAnswer.value = ''
}

async function syncCaptchaState() {
  try {
    const data = await getCaptchaRequired()
    showCaptcha.value = !!data.required
    if (showCaptcha.value) {
      await refreshCaptcha()
    }
  } catch {
    showCaptcha.value = false
  }
}

async function submit() {
  if (loading.value) return

  if (!username.value.trim() || !password.value) {
    ElMessage.warning('请输入账号和密码')
    return
  }

  if (showCaptcha.value && !captchaAnswer.value.trim()) {
    ElMessage.warning('请完成验证码')
    return
  }

  loading.value = true
  try {
    await userStore.login({
      username: username.value.trim(),
      password: password.value,
      captchaId: showCaptcha.value ? captchaId.value : undefined,
      captchaAnswer: showCaptcha.value ? captchaAnswer.value.trim() : undefined
    })
    ElMessage.success('登录成功')
    const redirect = route.query.redirect as string | undefined
    await router.replace(redirect && redirect.startsWith('/') ? redirect : '/dashboard')
  } catch (error: any) {
    ElMessage.error(error?.message || '登录失败，请检查账号、密码或验证码')
    await syncCaptchaState()
  } finally {
    loading.value = false
  }
}

function fillAccount(role: 'admin' | 'supervisor' | 'worker' | 'planner' | 'engineer') {
  username.value = role
  password.value = '123456'
}

onMounted(() => {
  void syncCaptchaState()
})
</script>

<template>
  <div class="auth-page">
    <div class="auth-bg">
      <div class="auth-bg-grid" />
      <div class="auth-glow auth-glow-a" />
      <div class="auth-glow auth-glow-b" />
      <div class="auth-glow auth-glow-c" />
    </div>

    <div class="auth-shell">
      <section class="auth-brand">
        <div class="auth-brand-inner">
          <div class="auth-logo">
            <BrandMark :size="40" />
          </div>
          <h1>AI-MES</h1>
          <p class="auth-brand-desc">离散制造车间智能执行系统</p>
          <ul class="auth-features">
            <li>生产计划下发与工单协同</li>
            <li>班组任务、异常与物料一体看板</li>
            <li>Coze 智能客服与 AI 排产辅助</li>
          </ul>
        </div>
        <div class="auth-brand-deco-1" />
        <div class="auth-brand-deco-2" />
      </section>

      <section class="auth-panel">
        <div class="auth-welcome">
          <h2>欢迎登录</h2>
          <p>请使用您的 AI-MES 账号进入系统</p>
        </div>

        <el-form class="auth-form" @submit.prevent="submit">
          <el-form-item>
            <el-input v-model="username" placeholder="用户名" autocomplete="username" size="large" :prefix-icon="User" />
          </el-form-item>

          <el-form-item>
            <el-input
              v-model="password"
              :type="showPassword ? 'text' : 'password'"
              placeholder="密码"
              autocomplete="current-password"
              size="large"
              :prefix-icon="Lock"
              @keyup.enter="submit"
            >
              <template #suffix>
                <button
                  type="button"
                  class="auth-eye-btn"
                  tabindex="-1"
                  :aria-label="showPassword ? '隐藏密码' : '显示密码'"
                  @click="showPassword = !showPassword"
                >
                  <svg
                    v-if="showPassword"
                    viewBox="0 0 24 24"
                    width="15"
                    height="15"
                    fill="none"
                    stroke="currentColor"
                    stroke-width="1.75"
                    stroke-linecap="round"
                    stroke-linejoin="round"
                    aria-hidden="true"
                  >
                    <path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z" />
                    <circle cx="12" cy="12" r="3" />
                  </svg>
                  <svg
                    v-else
                    viewBox="0 0 24 24"
                    width="15"
                    height="15"
                    fill="none"
                    stroke="currentColor"
                    stroke-width="1.75"
                    stroke-linecap="round"
                    stroke-linejoin="round"
                    aria-hidden="true"
                  >
                    <path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z" />
                    <circle cx="12" cy="12" r="3" />
                    <line x1="3" y1="4" x2="21" y2="20" />
                  </svg>
                </button>
              </template>
            </el-input>
          </el-form-item>

          <el-form-item v-show="showCaptcha">
            <AuthCaptchaField
              v-model="captchaAnswer"
              :captcha-img="captchaImg"
              @refresh="refreshCaptcha"
              @enter="submit"
            />
          </el-form-item>

          <el-button type="primary" class="auth-submit" size="large" :loading="loading" native-type="submit">
            登 录
          </el-button>
        </el-form>

        <div class="auth-demo">
          <span class="auth-demo__label">演示账号</span>
          <div class="auth-demo__accounts">
            <div class="accounts-row">
              <el-button text @click="fillAccount('admin')">admin</el-button>
              <el-button text @click="fillAccount('supervisor')">supervisor</el-button>
              <el-button text @click="fillAccount('planner')">planner</el-button>
            </div>
            <div class="accounts-row">
              <el-button text @click="fillAccount('engineer')">engineer</el-button>
              <el-button text @click="fillAccount('worker')">worker</el-button>
            </div>
          </div>
        </div>
      </section>
    </div>
  </div>
</template>

<style scoped>
.auth-page {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 28px 24px;
  position: relative;
  overflow: hidden;
  background: radial-gradient(ellipse 120% 100% at 50% 45%, #eef2f8 0%, #dde5f0 40%, #c8d6e8 70%, #b8c9df 100%);
}

.auth-bg {
  position: absolute;
  inset: 0;
  pointer-events: none;
}

.auth-bg-grid {
  position: absolute;
  inset: 0;
  background-image:
    linear-gradient(rgba(100, 130, 180, 0.09) 1px, transparent 1px),
    linear-gradient(90deg, rgba(100, 130, 180, 0.09) 1px, transparent 1px);
  background-size: 28px 28px;
  mask-image: radial-gradient(ellipse at center, black 40%, transparent 90%);
  -webkit-mask-image: radial-gradient(ellipse at center, black 40%, transparent 90%);
}

.auth-glow {
  position: absolute;
  border-radius: 50%;
  filter: blur(100px);
  opacity: 0.35;
}

.auth-glow-a {
  width: 500px;
  height: 500px;
  background: radial-gradient(circle, rgba(147, 197, 253, 0.3) 0%, transparent 70%);
  top: -150px;
  right: 5%;
  animation: floatGlowA 20s ease-in-out infinite alternate;
}

.auth-glow-b {
  width: 440px;
  height: 440px;
  background: radial-gradient(circle, rgba(196, 181, 253, 0.25) 0%, transparent 70%);
  bottom: -120px;
  left: 2%;
  animation: floatGlowB 18s ease-in-out infinite alternate-reverse;
}

.auth-glow-c {
  width: 360px;
  height: 360px;
  background: radial-gradient(circle, rgba(244, 143, 177, 0.15) 0%, transparent 70%);
  top: 40%;
  left: 40%;
  transform: translate(-50%, -50%);
  animation: floatGlowC 16s ease-in-out infinite alternate;
}

@keyframes floatGlowA {
  0% { transform: translateY(0) scale(1) rotate(0deg); }
  50% { transform: translateY(40px) scale(1.15) rotate(30deg); }
  100% { transform: translateY(-20px) scale(0.9) rotate(-15deg); }
}

@keyframes floatGlowB {
  0% { transform: translateY(0) scale(1.1) rotate(0deg); }
  50% { transform: translateY(-30px) scale(0.9) rotate(-45deg); }
  100% { transform: translateY(30px) scale(1.05) rotate(15deg); }
}

@keyframes floatGlowC {
  0% { transform: translate(-50%, -50%) scale(0.85) translate(-20px, -20px); }
  50% { transform: translate(-50%, -50%) scale(1.1) translate(35px, 30px); }
  100% { transform: translate(-50%, -50%) scale(0.9) translate(-30px, 15px); }
}

.auth-shell {
  position: relative;
  z-index: 1;
  display: grid;
  grid-template-columns: minmax(340px, 1fr) minmax(400px, 440px);
  width: min(920px, 100%);
  height: 580px;
  border-radius: 24px;
  overflow: hidden;
  box-shadow:
    0 30px 70px rgba(100, 120, 150, 0.15),
    0 10px 30px rgba(100, 120, 150, 0.08),
    inset 0 1px 0 rgba(255, 255, 255, 0.6);
  background: rgba(255, 255, 255, 0.15);
  border: 1px solid rgba(255, 255, 255, 0.45);
  backdrop-filter: blur(20px);
  -webkit-backdrop-filter: blur(20px);
}

.auth-brand {
  position: relative;
  padding: 48px 40px;
  background: rgba(255, 251, 251, 0.88);
  backdrop-filter: blur(24px);
  -webkit-backdrop-filter: blur(24px);
  color: #0f172a;
  display: flex;
  flex-direction: column;
  justify-content: center;
  align-items: center;
  text-align: center;
  overflow: hidden;
  border-right: 1px solid rgba(240, 212, 212, 0.72);
  box-shadow: inset -1px 0 0 rgba(255, 255, 255, 0.6);
}

.auth-brand-deco-1,
.auth-brand-deco-2 {
  display: none;
}

.auth-brand-inner {
  position: relative;
  z-index: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  width: 100%;
}

.auth-logo {
  width: 64px;
  height: 64px;
  border-radius: 20px;
  background: #ffffff;
  display: flex;
  align-items: center;
  justify-content: center;
  margin-bottom: 28px;
  border: 1px solid rgba(240, 212, 212, 0.72);
  box-shadow:
    0 6px 28px rgba(239, 68, 68, 0.08),
    0 2px 10px rgba(239, 68, 68, 0.05);
}

.auth-brand h1 {
  margin: 0 0 14px;
  font-size: 34px;
  font-weight: 800;
  letter-spacing: -0.5px;
  line-height: 1.2;
  color: #0f172a;
}

.auth-brand-desc {
  margin: 0 0 40px;
  font-size: 15px;
  line-height: 1.6;
  color: #64748b;
  letter-spacing: 0.5px;
}

.auth-features {
  display: flex;
  flex-direction: column;
  gap: 12px;
  margin: 0;
  padding: 0;
  list-style: none;
  width: 100%;
  max-width: 290px;
  align-items: stretch;
  counter-reset: feature;
}

.auth-features li {
  position: relative;
  padding: 12px 16px 12px 42px;
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.72);
  border: 1px solid rgba(240, 212, 212, 0.55);
  color: #334155;
  line-height: 1.5;
  text-align: left;
  box-shadow: 0 2px 10px rgba(239, 68, 68, 0.05);
  counter-increment: feature;
}

.auth-features li::before {
  content: counter(feature);
  position: absolute;
  left: 14px;
  top: 50%;
  transform: translateY(-50%);
  width: 22px;
  height: 22px;
  border-radius: 50%;
  background: #ffffff;
  border: 1.5px solid rgba(240, 212, 212, 0.85);
  color: #0f172a;
  font-size: 12px;
  font-weight: 700;
  display: flex;
  align-items: center;
  justify-content: center;
}

.auth-panel {
  background: rgba(255, 255, 255, 0.82);
  backdrop-filter: blur(20px);
  -webkit-backdrop-filter: blur(20px);
  padding: 80px 40px 36px;
  display: flex;
  flex-direction: column;
  justify-content: flex-start;
  height: 100%;
  min-height: 0;
  overflow-x: hidden;
  overflow-y: auto;
}

.auth-welcome {
  text-align: center;
  margin-bottom: 24px;
  flex-shrink: 0;
}

.auth-welcome h2 {
  margin: 0;
  font-size: 30px;
  font-weight: 800;
  letter-spacing: -0.5px;
  background: linear-gradient(135deg, #0f172a 0%, #1e293b 50%, #2563eb 100%);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
}

.auth-welcome p {
  margin: 8px 0 0;
  font-size: 13px;
  font-weight: 500;
  color: #64748b;
  letter-spacing: 0.5px;
}

.auth-form {
  flex-shrink: 0;
  min-height: auto;
}

.auth-form :deep(.el-form-item) {
  margin-bottom: 18px;
}

.auth-form :deep(.el-input__wrapper) {
  height: 50px;
  border-radius: 9999px !important;
  background: rgba(255, 255, 255, 0.45) !important;
  box-shadow: 0 0 0 1px rgba(148, 163, 184, 0.15) inset !important;
  transition: all 0.25s cubic-bezier(0.4, 0, 0.2, 1) !important;
  backdrop-filter: blur(4px);
}

.auth-form :deep(.el-input__wrapper:hover) {
  background: rgba(255, 255, 255, 0.7) !important;
  box-shadow: 0 0 0 1px rgba(59, 130, 246, 0.25) inset !important;
}

.auth-form :deep(.el-input__wrapper.is-focus) {
  background: #ffffff !important;
  transform: translateY(-1px);
  box-shadow:
    0 0 0 1px #3b82f6 inset,
    0 4px 16px rgba(59, 130, 246, 0.12) !important;
}

.auth-form :deep(.el-input__inner) {
  color: #1a1d26;
  font-weight: 500;
}

.auth-form :deep(.el-input__prefix .el-icon) {
  color: #94a3b8;
  font-size: 15px;
}

.auth-submit {
  width: 100%;
  height: 50px !important;
  margin-top: 6px;
  font-size: 15px !important;
  font-weight: 700 !important;
  letter-spacing: 4px;
  border-radius: 9999px !important;
  border: none !important;
  background: linear-gradient(135deg, #4f46e5 0%, #3b82f6 50%, #8b5cf6 100%) !important;
  background-size: 200% auto !important;
  color: #ffffff !important;
  box-shadow: 0 4px 14px rgba(99, 102, 241, 0.25) !important;
  transition: all 0.35s cubic-bezier(0.4, 0, 0.2, 1) !important;
}

.auth-panel :deep(.auth-submit.el-button--primary:hover),
.auth-panel :deep(.auth-submit.el-button--primary:focus) {
  transform: translateY(-2px) !important;
  background-position: right center !important;
  box-shadow:
    0 8px 24px rgba(99, 102, 241, 0.45),
    0 0 0 1px rgba(255, 255, 255, 0.1) inset !important;
}

.auth-eye-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  margin-right: -2px;
  border: none;
  border-radius: 50%;
  background: transparent;
  color: #94a3b8;
  cursor: pointer;
  padding: 0;
  transition: color 0.15s ease, background-color 0.15s ease;
}

.auth-eye-btn:hover {
  color: #4b5563;
  background: rgba(148, 163, 184, 0.12);
}

.auth-demo {
  margin-top: 24px;
  padding-top: 18px;
  display: flex;
  align-items: flex-start;
  gap: 12px;
  color: #64748b;
  font-size: 13px;
  border-top: 1px solid rgba(148, 163, 184, 0.14);
}

.auth-demo__label {
  flex-shrink: 0;
  font-size: 12px;
  font-weight: 600;
  letter-spacing: 0.5px;
  color: #94a3b8;
  margin-top: 8px;
}

.auth-demo__accounts {
  display: flex;
  flex-direction: column;
  gap: 8px;
  flex: 1;
}

.accounts-row {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.auth-demo__accounts :deep(.el-button) {
  flex-shrink: 0;
  margin: 0;
  padding: 6px 14px;
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.55);
  border: 1px solid rgba(148, 163, 184, 0.16);
  color: #475569;
  transition: all 0.2s ease;
}

.auth-demo__accounts :deep(.el-button:hover) {
  color: #2563eb;
  border-color: rgba(59, 130, 246, 0.28);
  background: rgba(255, 255, 255, 0.9);
}

@media (max-width: 840px) {
  .auth-shell {
    grid-template-columns: 1fr;
    max-width: 440px;
    height: auto;
    min-height: 520px;
  }

  .auth-brand {
    display: none;
  }

  .auth-panel {
    padding: 36px 28px 28px;
    background: rgba(255, 255, 255, 0.88);
  }

  .auth-demo {
    align-items: flex-start;
    flex-direction: column;
  }

  .auth-demo__accounts {
    width: 100%;
  }
}
</style>
