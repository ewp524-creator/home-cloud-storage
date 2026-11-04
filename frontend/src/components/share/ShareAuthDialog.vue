<script setup>
import { computed, nextTick, ref, watch } from 'vue'
import {
  Check,
  Eye,
  EyeOff,
  LockKeyhole,
  Mail,
  ShieldCheck,
  UserRound,
} from '@lucide/vue'
import { useFileManagerContext } from '../../composables/fileManagerContext'
import GraphicalCaptcha from '../auth/GraphicalCaptcha.vue'
import AppModal from '../overlays/AppModal.vue'

const props = defineProps({
  open: {
    type: Boolean,
    default: false,
  },
  shareName: {
    type: String,
    default: '',
  },
})

const emit = defineEmits(['close', 'success'])
const { login, registerAccount } = useFileManagerContext()

const mode = ref('login')
const account = ref('')
const name = ref('')
const username = ref('')
const email = ref('')
const password = ref('')
const captchaId = ref('')
const captchaAnswer = ref('')
const captchaRef = ref(null)
const showPassword = ref(false)
const submitting = ref(false)
const errorMessage = ref('')

const title = computed(() =>
  mode.value === 'login' ? '登录后下载' : '注册后下载',
)

function clearSensitiveFields() {
  account.value = ''
  name.value = ''
  username.value = ''
  email.value = ''
  password.value = ''
  captchaAnswer.value = ''
}

function switchMode(nextMode) {
  mode.value = nextMode
  clearSensitiveFields()
  showPassword.value = false
  captchaRef.value?.refresh()
}

watch(
  () => props.open,
  async (open) => {
    if (!open) return
    switchMode('login')
    submitting.value = false
    showPassword.value = false
    await nextTick()
    clearSensitiveFields()
  },
)

async function submit() {
  if (submitting.value) return
  submitting.value = true
  errorMessage.value = ''
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
    emit('success')
  } catch (error) {
    errorMessage.value = error.message || '认证失败'
    await captchaRef.value?.refresh()
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <AppModal
    :open="open"
    :title="title"
    :description="`下载「${shareName}」前需要进行身份验证`"
    size="sm"
    @close="emit('close')"
  >
    <div class="share-auth">
      <div class="share-auth__tabs">
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

      <form class="share-auth__form" autocomplete="off" @submit.prevent="submit">
        <template v-if="mode === 'register'">
          <label>
            <span>用户名</span>
            <div class="share-auth__input">
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
            <div class="share-auth__input">
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
            <div class="share-auth__input">
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
          <div class="share-auth__input">
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
          <div class="share-auth__input">
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
              :aria-label="showPassword ? '隐藏密码' : '显示密码'"
              @click="showPassword = !showPassword"
            >
              <EyeOff v-if="showPassword" :size="16" />
              <Eye v-else :size="16" />
            </button>
          </div>
        </label>

        <div v-if="mode === 'register'" class="share-auth__terms">
          <span><Check :size="11" /></span>
          注册即表示同意服务条款和隐私政策
        </div>

        <div class="share-auth__captcha">
          <span>图形验证码</span>
          <GraphicalCaptcha
            ref="captchaRef"
            v-model="captchaAnswer"
            v-model:captcha-id="captchaId"
          />
        </div>

        <p v-if="errorMessage" class="share-auth__error">{{ errorMessage }}</p>

        <button class="share-auth__submit" type="submit" :disabled="submitting">
          <ShieldCheck :size="17" />
          {{ submitting ? '正在验证...' : mode === 'login' ? '登录并继续下载' : '注册并继续下载' }}
        </button>
      </form>

      <p class="share-auth__hint">
        验证码一次性有效，提交后会立即作废。
      </p>
    </div>
  </AppModal>
</template>

<style scoped>
.share-auth {
  display: flex;
  flex-direction: column;
}

.share-auth__tabs {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 3px;
  padding: 3px;
  border: 1px solid var(--line);
  border-radius: 7px;
  background: var(--surface-muted);
}

.share-auth__tabs button {
  height: 34px;
  border: 0;
  border-radius: 5px;
  background: transparent;
  color: var(--text-quiet);
  font: inherit;
  font-size: 11px;
  cursor: pointer;
}

.share-auth__tabs button.is-active {
  background: #fff;
  color: var(--text);
  font-weight: 600;
  box-shadow: 0 1px 3px rgb(25 27 24 / 6%);
}

.share-auth__form {
  display: flex;
  flex-direction: column;
  gap: 14px;
  margin-top: 20px;
}

.share-auth__form > label {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.share-auth__form > label > span {
  color: var(--text-soft);
  font-size: 9.5px;
  font-weight: 600;
}

.share-auth__input {
  display: flex;
  height: 44px;
  align-items: center;
  gap: 9px;
  padding: 0 11px;
  border: 1px solid var(--line);
  border-radius: 7px;
  background: #fff;
  color: var(--text-quiet);
}

.share-auth__input:focus-within {
  border-color: var(--accent-line);
  box-shadow: 0 0 0 3px var(--accent-ring);
}

.share-auth__input input {
  min-width: 0;
  flex: 1;
  border: 0;
  outline: 0;
  background: transparent;
  color: var(--text);
  font: inherit;
  font-size: 11.5px;
}

.share-auth__input button {
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

.share-auth__captcha {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.share-auth__captcha > span {
  color: var(--text-soft);
  font-size: 9.5px;
  font-weight: 600;
}

.share-auth__terms {
  display: flex;
  align-items: center;
  gap: 6px;
  color: var(--text-quiet);
  font-size: 8.5px;
}

.share-auth__terms span {
  display: grid;
  width: 15px;
  height: 15px;
  place-items: center;
  border-radius: 4px;
  background: var(--accent);
  color: #fff;
}

.share-auth__submit {
  display: flex;
  height: 44px;
  align-items: center;
  justify-content: center;
  gap: 8px;
  margin-top: 3px;
  border: 0;
  border-radius: 7px;
  background: var(--text);
  color: #fff;
  font: inherit;
  font-size: 11px;
  font-weight: 600;
  cursor: pointer;
}

.share-auth__submit:hover {
  background: #30332f;
}

.share-auth__submit:disabled {
  opacity: 0.55;
}

.share-auth__hint {
  margin: 14px 0 0;
  color: var(--text-quiet);
  font-size: 8.5px;
  text-align: center;
}

.share-auth__error {
  margin: 0;
  color: var(--danger);
  font-size: 9px;
  text-align: center;
}
</style>
