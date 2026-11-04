<script setup>
import { computed, ref, watch } from 'vue'
import {
  KeyRound,
  LoaderCircle,
  Settings2,
  ShieldAlert,
  UserRound,
} from '@lucide/vue'
import { useFileManagerContext } from '../../composables/fileManagerContext'
import AppModal from './AppModal.vue'

const props = defineProps({
  open: {
    type: Boolean,
    default: false,
  },
})

const emit = defineEmits(['close', 'deactivated'])
const manager = useFileManagerContext()
const {
  changeAccountPassword,
  deactivateCurrentAccount,
  preferences,
  savePreferences,
  saveProfile,
  toast,
  user,
} = manager

const activeTab = ref('profile')
const saving = ref(false)
const displayName = ref('')
const email = ref('')
const defaultView = ref('GRID')
const defaultSort = ref('modified-desc')
const reduceMotion = ref(false)
const uploadConcurrency = ref(4)
const currentPassword = ref('')
const newPassword = ref('')
const confirmPassword = ref('')
const deactivatePassword = ref('')
const deactivateConfirmation = ref('')

const passwordMatches = computed(
  () => newPassword.value && newPassword.value === confirmPassword.value,
)

watch(
  () => props.open,
  (open) => {
    if (!open) return
    activeTab.value = 'profile'
    displayName.value = user.name
    email.value = user.email
    defaultView.value = preferences.defaultView
    defaultSort.value = preferences.defaultSort
    reduceMotion.value = preferences.reduceMotion
    uploadConcurrency.value = preferences.uploadConcurrency
    currentPassword.value = ''
    newPassword.value = ''
    confirmPassword.value = ''
    deactivatePassword.value = ''
    deactivateConfirmation.value = ''
  },
)

async function saveUserProfile() {
  saving.value = true
  try {
    await saveProfile({
      displayName: displayName.value,
      email: email.value,
    })
  } catch (error) {
    toast(error.message, 'warning')
  } finally {
    saving.value = false
  }
}

async function saveUserPreferences() {
  saving.value = true
  try {
    await savePreferences({
      defaultView: defaultView.value,
      defaultSort: defaultSort.value,
      reduceMotion: reduceMotion.value,
      uploadConcurrency: uploadConcurrency.value,
    })
  } catch (error) {
    toast(error.message, 'warning')
  } finally {
    saving.value = false
  }
}

async function submitPasswordChange() {
  if (!passwordMatches.value) {
    toast('两次输入的新密码不一致', 'warning')
    return
  }
  saving.value = true
  try {
    await changeAccountPassword({
      currentPassword: currentPassword.value,
      newPassword: newPassword.value,
    })
    currentPassword.value = ''
    newPassword.value = ''
    confirmPassword.value = ''
  } catch (error) {
    toast(error.message, 'warning')
  } finally {
    saving.value = false
  }
}

async function deactivateUser() {
  saving.value = true
  try {
    await deactivateCurrentAccount({
      password: deactivatePassword.value,
      confirmation: deactivateConfirmation.value.trim(),
    })
    emit('deactivated')
    emit('close')
  } catch (error) {
    toast(error.message, 'warning')
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <AppModal
    :open="open"
    title="账户设置"
    description="管理个人资料、界面偏好、密码和账户状态。"
    size="lg"
    @close="emit('close')"
  >
    <div class="settings-layout">
      <nav class="settings-tabs" aria-label="账户设置分类">
        <button
          type="button"
          :class="{ 'is-active': activeTab === 'profile' }"
          @click="activeTab = 'profile'"
        >
          <UserRound :size="16" />
          个人资料
        </button>
        <button
          type="button"
          :class="{ 'is-active': activeTab === 'preferences' }"
          @click="activeTab = 'preferences'"
        >
          <Settings2 :size="16" />
          偏好设置
        </button>
        <button
          type="button"
          :class="{ 'is-active': activeTab === 'security' }"
          @click="activeTab = 'security'"
        >
          <KeyRound :size="16" />
          密码与安全
        </button>
      </nav>

      <section v-if="activeTab === 'profile'" class="settings-panel">
        <label>
          <span>用户名</span>
          <input :value="user.username" disabled />
        </label>
        <label>
          <span>显示名称</span>
          <input v-model.trim="displayName" maxlength="80" />
        </label>
        <label>
          <span>邮箱</span>
          <input v-model.trim="email" type="email" maxlength="254" />
        </label>
        <button
          class="settings-action"
          type="button"
          :disabled="saving"
          @click="saveUserProfile"
        >
          <LoaderCircle v-if="saving" :size="15" class="is-spinning" />
          保存个人资料
        </button>
      </section>

      <section v-else-if="activeTab === 'preferences'" class="settings-panel">
        <label>
          <span>默认文件视图</span>
          <select v-model="defaultView">
            <option value="GRID">网格</option>
            <option value="LIST">列表</option>
          </select>
        </label>
        <label>
          <span>默认排序</span>
          <select v-model="defaultSort">
            <option value="modified-desc">最近修改</option>
            <option value="modified-asc">最早修改</option>
            <option value="name-asc">名称 A-Z</option>
            <option value="size-desc">文件大小</option>
          </select>
        </label>
        <label class="settings-range">
          <span>分片上传并发数：{{ uploadConcurrency }}</span>
          <input
            v-model.number="uploadConcurrency"
            type="range"
            min="1"
            max="4"
            step="1"
          />
        </label>
        <label class="settings-check">
          <input v-model="reduceMotion" type="checkbox" />
          <span>减少界面动画</span>
        </label>
        <button
          class="settings-action"
          type="button"
          :disabled="saving"
          @click="saveUserPreferences"
        >
          <LoaderCircle v-if="saving" :size="15" class="is-spinning" />
          保存偏好设置
        </button>
      </section>

      <section v-else class="settings-panel">
        <div class="settings-security">
          <h3>修改密码</h3>
          <label>
            <span>当前密码</span>
            <input v-model="currentPassword" type="password" autocomplete="current-password" />
          </label>
          <label>
            <span>新密码</span>
            <input v-model="newPassword" type="password" autocomplete="new-password" />
          </label>
          <label>
            <span>确认新密码</span>
            <input v-model="confirmPassword" type="password" autocomplete="new-password" />
          </label>
          <button
            class="settings-action"
            type="button"
            :disabled="saving || !passwordMatches"
            @click="submitPasswordChange"
          >
            更新密码并退出其他设备
          </button>
        </div>

        <div class="settings-danger">
          <h3><ShieldAlert :size="17" /> 注销账户</h3>
          <p>账户会立即停用并撤销全部登录会话，文件数据保留用于恢复审计。</p>
          <label>
            <span>输入密码</span>
            <input v-model="deactivatePassword" type="password" />
          </label>
          <label>
            <span>输入用户名 {{ user.username }} 确认</span>
            <input v-model="deactivateConfirmation" />
          </label>
          <button
            type="button"
            class="settings-danger__button"
            :disabled="
              saving ||
              !deactivatePassword ||
              deactivateConfirmation.trim() !== user.username
            "
            @click="deactivateUser"
          >
            永久停用当前账户
          </button>
        </div>
      </section>
    </div>
  </AppModal>
</template>

<style scoped>
.settings-layout {
  display: grid;
  grid-template-columns: 170px minmax(0, 1fr);
  gap: 24px;
}

.settings-tabs {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.settings-tabs button {
  display: flex;
  align-items: center;
  gap: 9px;
  padding: 10px;
  border: 0;
  border-radius: 7px;
  background: transparent;
  color: var(--text-soft);
  font: inherit;
  font-size: 11px;
  text-align: left;
  cursor: pointer;
}

.settings-tabs button.is-active {
  background: var(--accent-wash);
  color: var(--accent-strong);
  font-weight: 650;
}

.settings-panel,
.settings-security {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.settings-panel label,
.settings-security label {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.settings-panel label > span,
.settings-security label > span {
  color: var(--text-soft);
  font-size: 9.5px;
  font-weight: 630;
}

.settings-panel input,
.settings-panel select,
.settings-security input {
  height: 40px;
  padding: 0 11px;
  border: 1px solid var(--line);
  border-radius: 7px;
  outline: none;
  background: #fff;
  color: var(--text);
  font: inherit;
  font-size: 11px;
}

.settings-panel input:focus,
.settings-panel select:focus,
.settings-security input:focus {
  border-color: var(--accent-line);
  box-shadow: 0 0 0 3px var(--accent-ring);
}

.settings-panel input:disabled {
  background: var(--surface-muted);
  color: var(--text-quiet);
}

.settings-range input {
  height: 24px;
  padding: 0;
  accent-color: var(--accent);
}

.settings-check {
  flex-direction: row !important;
  align-items: center;
}

.settings-check input {
  width: 16px;
  height: 16px;
  padding: 0;
}

.settings-action,
.settings-danger__button {
  align-self: flex-start;
  display: inline-flex;
  min-height: 38px;
  align-items: center;
  gap: 7px;
  padding: 0 13px;
  border: 0;
  border-radius: 7px;
  background: var(--text);
  color: #fff;
  font: inherit;
  font-size: 10.5px;
  font-weight: 650;
  cursor: pointer;
}

.settings-action:disabled,
.settings-danger__button:disabled {
  cursor: not-allowed;
  opacity: 0.45;
}

.settings-security {
  padding-bottom: 22px;
  border-bottom: 1px solid var(--line);
}

.settings-security h3,
.settings-danger h3 {
  display: flex;
  align-items: center;
  gap: 7px;
  margin: 0;
  color: var(--text);
  font-size: 12px;
}

.settings-danger {
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding: 14px;
  border: 1px solid color-mix(in srgb, var(--danger) 24%, var(--line));
  border-radius: 8px;
  background: color-mix(in srgb, var(--danger) 4%, #fff);
}

.settings-danger p {
  margin: 0;
  color: var(--text-soft);
  font-size: 10px;
  line-height: 1.55;
}

.settings-danger__button {
  background: var(--danger);
}

.is-spinning {
  animation: settings-spin 800ms linear infinite;
}

@keyframes settings-spin {
  to {
    transform: rotate(360deg);
  }
}

@media (max-width: 720px) {
  .settings-layout {
    grid-template-columns: 1fr;
  }

  .settings-tabs {
    flex-direction: row;
    overflow-x: auto;
  }

  .settings-tabs button {
    flex: 0 0 auto;
  }
}
</style>
