<script setup>
import { computed, nextTick, onMounted, ref } from 'vue'
import {
  ArrowRight,
  Cloud,
  Eye,
  EyeOff,
  FileImage,
  FileText,
  LockKeyhole,
  Mail,
  ShieldCheck,
  UserRound,
} from '@lucide/vue'
import { useFileManagerContext } from '../../composables/fileManagerContext'
import GraphicalCaptcha from './GraphicalCaptcha.vue'

const { login, registerAccount } = useFileManagerContext()

const mode = ref('login')
const showPassword = ref(false)
const account = ref('')
const password = ref('')
const name = ref('')
const username = ref('')
const email = ref('')
const captchaId = ref('')
const captchaAnswer = ref('')
const captchaRef = ref(null)
const submitting = ref(false)
const notice = ref('')

const submitLabel = computed(() => (mode.value === 'login' ? '进入工作台' : '创建账户'))

function clearSensitiveFields() {
  account.value = ''
  password.value = ''
  name.value = ''
  username.value = ''
  email.value = ''
  captchaAnswer.value = ''
}

function switchMode(nextMode) {
  mode.value = nextMode
  showPassword.value = false
  clearSensitiveFields()
  captchaRef.value?.refresh()
}

async function submit() {
  if (submitting.value) return
  submitting.value = true
  notice.value = ''
  try {
    if (mode.value === 'register') {
      await registerAccount({
        username: username.value,
        name: name.value,
        displayName: name.value,
        email: email.value,
        password: password.value,
        captchaId: captchaId.value,
        captchaAnswer: captchaAnswer.value,
      })
    } else {
      await login({
        account: account.value,
        password: password.value,
        captchaId: captchaId.value,
        captchaAnswer: captchaAnswer.value,
      })
    }
  } catch (error) {
    notice.value = error.message || '登录失败'
    await captchaRef.value?.refresh()
  } finally {
    submitting.value = false
  }
}

function requestReset() {
  notice.value =
    '密码重置功能尚未接入，请联系管理员处理。'
}

onMounted(async () => {
  await nextTick()
  clearSensitiveFields()
})
</script>

<template>
  <main class="auth-screen">
    <section class="auth-visual">
      <div class="auth-brand">
        <span class="auth-brand__mark"><Cloud :size="22" /></span>
        <div>
          <strong>filehome</strong>
          <small>PERSONAL CLOUD</small>
        </div>
      </div>

      <div class="auth-visual__content">
        <span class="auth-visual__eyebrow">YOUR FILES. YOUR SPACE.</span>
        <h1>把重要的文件，<br />放在触手可及的地方。</h1>
        <div class="auth-file-stack" aria-hidden="true">
          <div class="auth-file auth-file--back">
            <span><FileImage :size="21" /></span>
            <i>PNG</i>
          </div>
          <div class="auth-file auth-file--middle">
            <span><FileText :size="21" /></span>
            <i>DOCX</i>
          </div>
          <div class="auth-file auth-file--front">
            <span><FileText :size="21" /></span>
            <i>PDF</i>
          </div>
          <span class="auth-orbit auth-orbit--one" />
          <span class="auth-orbit auth-orbit--two" />
        </div>
      </div>

      <footer class="auth-visual__footer">
        <ShieldCheck :size="15" />
        <span>MinIO 对象存储 · 安全分享 · 多格式在线预览</span>
      </footer>
    </section>

    <section class="auth-form-side">
      <div class="auth-form-wrap">
        <div class="auth-form-head">
          <span>FILE HOME ACCOUNT</span>
          <h2>{{ mode === 'login' ? '欢迎回来' : '创建个人空间' }}</h2>
          <p>
            {{
              mode === 'login'
                ? '登录后继续管理你的文件、分享和回收站。'
                : '注册一个新账户，开始整理你的个人文件。'
            }}
          </p>
        </div>

        <div class="auth-tabs">
          <button
            type="button"
            :class="{ 'is-active': mode === 'login' }"
            @click="switchMode('login')"
          >
            登录
          </button>
          <button
            type="button"
            :class="{ 'is-active': mode === 'register' }"
            @click="switchMode('register')"
          >
            注册
          </button>
        </div>

        <form class="auth-form" autocomplete="off" @submit.prevent="submit">
          <template v-if="mode === 'register'">
            <label>
              <span>用户名</span>
              <div class="auth-input">
                <UserRound :size="16" />
                <input
                  v-model="username"
                  type="text"
                  autocomplete="off"
                  placeholder="用于创建个人根目录"
                  minlength="2"
                  maxlength="32"
                  required
                />
              </div>
            </label>
            <label>
              <span>姓名</span>
              <div class="auth-input">
                <UserRound :size="16" />
                <input
                  v-model="name"
                  type="text"
                  autocomplete="off"
                  placeholder="你的姓名"
                  required
                />
              </div>
            </label>
            <label>
              <span>邮箱</span>
              <div class="auth-input">
                <Mail :size="16" />
                <input
                  v-model="email"
                  type="email"
                  autocomplete="off"
                  placeholder="name@example.com"
                  required
                />
              </div>
            </label>
          </template>
          <label v-else>
            <span>账户</span>
            <div class="auth-input">
              <UserRound :size="16" />
              <input
                v-model="account"
                type="text"
                autocomplete="off"
                placeholder="用户名或邮箱"
                required
              />
            </div>
          </label>

          <label>
            <span>密码</span>
            <div class="auth-input">
              <LockKeyhole :size="16" />
              <input
                v-model="password"
                :type="showPassword ? 'text' : 'password'"
                autocomplete="off"
                placeholder="输入密码"
                minlength="6"
                required
              />
              <button
                type="button"
                :title="showPassword ? '隐藏密码' : '显示密码'"
                :aria-label="showPassword ? '隐藏密码' : '显示密码'"
                @click="showPassword = !showPassword"
              >
                <EyeOff v-if="showPassword" :size="16" />
                <Eye v-else :size="16" />
              </button>
            </div>
          </label>

          <div class="auth-captcha-group">
            <span>图形验证码</span>
            <GraphicalCaptcha
              ref="captchaRef"
              v-model="captchaAnswer"
              v-model:captcha-id="captchaId"
            />
          </div>

          <div v-if="mode === 'login'" class="auth-options auth-options--end">
            <button type="button" @click="requestReset">忘记密码？</button>
          </div>

          <button class="auth-submit" type="submit" :disabled="submitting">
            <span>{{ submitting ? '正在进入...' : submitLabel }}</span>
            <ArrowRight :size="17" />
          </button>
        </form>

        <p class="auth-security-note">
          图形验证码一次性有效，提交后会立即作废。
        </p>
        <p v-if="notice" class="auth-notice">{{ notice }}</p>
      </div>
    </section>
  </main>
</template>

<style scoped>
.auth-screen {
  display: grid;
  min-height: 100dvh;
  grid-template-columns: minmax(0, 1.05fr) minmax(440px, 0.95fr);
  background: #f5f7f6;
}

.auth-visual {
  position: relative;
  display: flex;
  min-height: 100dvh;
  flex-direction: column;
  padding: 38px 46px 30px;
  overflow: hidden;
  background:
    radial-gradient(circle at 72% 45%, rgb(62 92 214 / 20%), transparent 30%),
    radial-gradient(circle at 20% 84%, rgb(184 132 62 / 8%), transparent 25%),
    #171918;
  color: #eef8f4;
}

.auth-visual::before {
  position: absolute;
  inset: 0;
  background-image:
    linear-gradient(rgb(255 255 255 / 2.5%) 1px, transparent 1px),
    linear-gradient(90deg, rgb(255 255 255 / 2.5%) 1px, transparent 1px);
  background-size: 42px 42px;
  mask-image: linear-gradient(to bottom, #000, transparent 78%);
  content: '';
}

.auth-brand {
  position: relative;
  z-index: 1;
  display: flex;
  align-items: center;
  gap: 11px;
}

.auth-brand__mark {
  display: grid;
  width: 39px;
  height: 39px;
  place-items: center;
  border-radius: 10px;
  background: var(--accent);
  color: #fff;
  box-shadow: 0 9px 24px rgb(0 0 0 / 20%);
}

.auth-brand > div {
  display: flex;
  flex-direction: column;
}

.auth-brand strong {
  color: #f4faf8;
  font-size: 16px;
  font-weight: 650;
}

.auth-brand small {
  color: rgb(226 242 236 / 43%);
  font-size: 7.5px;
  font-weight: 750;
  letter-spacing: 0.16em;
}

.auth-visual__content {
  position: relative;
  z-index: 1;
  display: flex;
  flex: 1;
  flex-direction: column;
  justify-content: center;
  padding: 6vh 0;
}

.auth-visual__eyebrow {
  color: #91a6ff;
  font-size: 8.5px;
  font-weight: 720;
  letter-spacing: 0.18em;
}

.auth-visual h1 {
  margin: 14px 0 0;
  color: #f3f9f6;
  font-size: clamp(35px, 4.2vw, 61px);
  font-weight: 600;
  letter-spacing: 0;
  line-height: 1.21;
}

.auth-file-stack {
  position: relative;
  width: min(500px, 80%);
  height: 230px;
  margin-top: 38px;
}

.auth-file {
  position: absolute;
  display: grid;
  width: 148px;
  height: 174px;
  place-items: center;
  border: 1px solid rgb(255 255 255 / 12%);
  border-radius: 13px;
  background: rgb(255 255 255 / 7%);
  box-shadow: 0 32px 70px rgb(0 0 0 / 21%);
  backdrop-filter: blur(10px);
}

.auth-file > span {
  display: grid;
  width: 66px;
  height: 66px;
  place-items: center;
  border-radius: 16px;
}

.auth-file i {
  position: absolute;
  right: 13px;
  bottom: 13px;
  padding: 3px 6px;
  border-radius: 4px;
  font-size: 7.5px;
  font-style: normal;
  font-weight: 750;
}

.auth-file--back {
  top: 27px;
  left: 44%;
  color: #aa8bea;
  transform: rotate(11deg);
}

.auth-file--back > span {
  background: rgb(126 87 217 / 17%);
}

.auth-file--back i {
  background: #7350c4;
  color: #fff;
}

.auth-file--middle {
  top: 7px;
  left: 23%;
  color: #66a2e9;
  transform: rotate(-9deg);
}

.auth-file--middle > span {
  background: rgb(47 111 237 / 15%);
}

.auth-file--middle i {
  background: #2f6fed;
  color: #fff;
}

.auth-file--front {
  top: 34px;
  left: 8%;
  z-index: 2;
  color: #ed7579;
  transform: rotate(-1deg);
}

.auth-file--front > span {
  background: rgb(220 61 67 / 14%);
}

.auth-file--front i {
  background: #dc3d43;
  color: #fff;
}

.auth-orbit {
  position: absolute;
  border: 1px solid rgb(255 255 255 / 5%);
  border-radius: 50%;
}

.auth-orbit--one {
  top: -34px;
  right: 4%;
  width: 260px;
  height: 260px;
}

.auth-orbit--two {
  top: -65px;
  right: -20px;
  width: 330px;
  height: 330px;
}

.auth-visual__footer {
  position: relative;
  z-index: 1;
  display: flex;
  align-items: center;
  gap: 7px;
  color: rgb(226 242 236 / 40%);
  font-size: 9px;
}

.auth-form-side {
  display: grid;
  min-height: 100dvh;
  place-items: center;
  padding: 48px clamp(28px, 6vw, 84px);
  background:
    radial-gradient(circle at 100% 0%, rgb(49 85 217 / 6%), transparent 30%),
    var(--canvas);
}

.auth-form-wrap {
  width: min(100%, 390px);
}

.auth-form-head > span {
  color: var(--accent-strong);
  font-size: 8px;
  font-weight: 750;
  letter-spacing: 0.16em;
}

.auth-form-head h2 {
  margin: 9px 0 6px;
  color: var(--text);
  font-size: 30px;
  font-weight: 600;
  letter-spacing: 0;
}

.auth-form-head p {
  margin: 0;
  color: var(--text-soft);
  font-size: 11.5px;
  line-height: 1.6;
}

.auth-tabs {
  display: flex;
  gap: 4px;
  margin: 28px 0 22px;
  padding: 3px;
  border: 1px solid var(--line);
  border-radius: 9px;
  background: #ebeae5;
}

.auth-tabs button {
  height: 34px;
  flex: 1;
  border: 0;
  border-radius: 6px;
  background: transparent;
  color: var(--text-quiet);
  font: inherit;
  font-size: 10.5px;
  cursor: pointer;
}

.auth-tabs button.is-active {
  background: #fff;
  color: var(--text);
  font-weight: 650;
  box-shadow: 0 2px 8px rgb(24 40 34 / 7%);
}

.auth-form {
  display: flex;
  flex-direction: column;
  gap: 15px;
}

.auth-form > label {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.auth-form > label > span {
  color: var(--text-soft);
  font-size: 9.5px;
  font-weight: 630;
}

.auth-input {
  display: flex;
  height: 46px;
  align-items: center;
  gap: 9px;
  padding: 0 12px;
  border: 1px solid var(--line);
  border-radius: 9px;
  background: #fff;
  color: var(--text-quiet);
  transition:
    border-color 160ms ease,
    box-shadow 160ms ease;
}

.auth-input:focus-within {
  border-color: var(--accent-line);
  box-shadow: 0 0 0 3px var(--accent-ring);
}

.auth-input input {
  min-width: 0;
  flex: 1;
  border: 0;
  outline: none;
  background: transparent;
  color: var(--text);
  font: inherit;
  font-size: 11.5px;
}

.auth-input button {
  display: grid;
  width: 26px;
  height: 26px;
  place-items: center;
  padding: 0;
  border: 0;
  border-radius: 5px;
  background: transparent;
  color: var(--text-quiet);
  cursor: pointer;
}

.auth-captcha-group {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.auth-captcha-group > span {
  color: var(--text-soft);
  font-size: 9.5px;
  font-weight: 630;
}

.auth-options {
  display: flex;
  align-items: center;
}

.auth-options--end {
  justify-content: flex-end;
}

.auth-options > button {
  padding: 0;
  border: 0;
  background: transparent;
  color: var(--accent-strong);
  font: inherit;
  font-size: 9.5px;
  cursor: pointer;
}

.auth-submit {
  display: flex;
  height: 48px;
  align-items: center;
  justify-content: space-between;
  margin-top: 4px;
  padding: 0 16px;
  border: 0;
  border-radius: 9px;
  background: var(--text);
  color: #fff;
  font: inherit;
  font-size: 11.5px;
  font-weight: 650;
  cursor: pointer;
  box-shadow: none;
}

.auth-submit:hover {
  background: #30332f;
}

.auth-submit:disabled {
  cursor: wait;
  opacity: 0.75;
}

.auth-security-note {
  margin: 20px 0 0;
  color: var(--text-quiet);
  font-size: 8.5px;
  text-align: center;
}

.auth-notice {
  margin: 8px 0 0;
  padding: 8px 10px;
  border-radius: 7px;
  background: var(--accent-wash);
  color: var(--accent-strong);
  font-size: 8.5px;
  line-height: 1.5;
  text-align: center;
}

@media (max-width: 900px) {
  .auth-screen {
    grid-template-columns: 1fr;
  }

  .auth-visual {
    display: none;
  }

  .auth-form-side {
    min-height: 100dvh;
    padding: 36px 24px;
  }
}
</style>
