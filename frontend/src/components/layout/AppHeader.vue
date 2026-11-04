<script setup>
import { onBeforeUnmount, onMounted, ref } from 'vue'
import {
  Bell,
  Camera,
  CheckCircle2,
  HelpCircle,
  LoaderCircle,
  LogOut,
  Menu,
  Search,
  Settings,
  Share2,
  SquareTerminal,
  Trash2,
  UploadCloud,
  UserRound,
  X,
} from '@lucide/vue'
import { useFileManagerContext } from '../../composables/fileManagerContext'
import UserAvatar from '../common/UserAvatar.vue'

const emit = defineEmits(['upload', 'logout', 'settings'])
const {
  navigate,
  notificationsOpen,
  profileOpen,
  searchQuery,
  sidebarOpen,
  terminalOpen,
  toast,
  toggleTerminal,
  uploadAvatar,
  user,
} = useFileManagerContext()

const searchInput = ref(null)
const header = ref(null)
const avatarInput = ref(null)
const avatarUploading = ref(false)

function focusSearch() {
  searchInput.value?.focus()
  searchInput.value?.select()
}

function closePopovers(event) {
  if (!header.value?.contains(event.target)) {
    notificationsOpen.value = false
    profileOpen.value = false
  }
}

function toggleNotifications() {
  notificationsOpen.value = !notificationsOpen.value
  profileOpen.value = false
}

function toggleProfile() {
  profileOpen.value = !profileOpen.value
  notificationsOpen.value = false
}

function goTo(view) {
  navigate(view)
  profileOpen.value = false
  notificationsOpen.value = false
}

function showSettings() {
  profileOpen.value = false
  emit('settings')
}

function openAvatarPicker() {
  profileOpen.value = false
  avatarInput.value?.click()
}

async function onAvatarSelected(event) {
  const file = event.target.files?.[0]
  event.target.value = ''
  if (!file) return
  avatarUploading.value = true
  try {
    await uploadAvatar(file)
  } catch (error) {
    toast(error.message || '头像更新失败', 'warning')
  } finally {
    avatarUploading.value = false
  }
}

function requestLogout() {
  profileOpen.value = false
  emit('logout')
}

defineExpose({ focusSearch })

onMounted(() => document.addEventListener('pointerdown', closePopovers))
onBeforeUnmount(() => document.removeEventListener('pointerdown', closePopovers))
</script>

<template>
  <header ref="header" class="topbar">
    <input
      ref="avatarInput"
      class="visually-hidden"
      type="file"
      accept="image/png,image/jpeg,image/gif"
      @change="onAvatarSelected"
    />
    <div class="topbar__left">
      <button
        class="topbar__mobile-menu icon-button"
        type="button"
        title="打开导航"
        aria-label="打开导航"
        @click="sidebarOpen = true"
      >
        <Menu :size="20" />
      </button>

      <label class="global-search">
        <Search :size="18" :stroke-width="1.8" />
        <input
          ref="searchInput"
          v-model="searchQuery"
          type="search"
          placeholder="搜索文件、文件夹或标签"
          autocomplete="off"
        />
        <button
          v-if="searchQuery"
          class="global-search__clear"
          type="button"
          title="清除搜索"
          aria-label="清除搜索"
          @click.prevent="searchQuery = ''"
        >
          <X :size="15" />
        </button>
        <kbd v-else>/</kbd>
      </label>
    </div>

    <div class="topbar__actions">
      <button class="button button--primary topbar__upload" type="button" @click="emit('upload')">
        <UploadCloud :size="16" />
        <span>上传</span>
      </button>

      <button
        class="icon-button topbar__terminal"
        :class="{ 'is-active': terminalOpen }"
        type="button"
        title="命令窗口 (Ctrl+`)"
        aria-label="命令窗口"
        @click="toggleTerminal"
      >
        <SquareTerminal :size="18" />
      </button>

      <button
        class="icon-button topbar__help"
        type="button"
        title="帮助中心"
        aria-label="帮助中心"
        @click="toast('帮助中心将在下一阶段接入', 'info')"
      >
        <HelpCircle :size="19" />
      </button>

      <div class="topbar__popover-wrap">
        <button
          class="icon-button topbar__notification"
          type="button"
          title="通知"
          aria-label="通知"
          @click.stop="toggleNotifications"
        >
          <Bell :size="19" />
          <span class="notification-dot" />
        </button>

        <Transition name="pop">
          <div v-if="notificationsOpen" class="popover notification-popover">
            <div class="popover__head">
              <strong>通知</strong>
              <span>2 条未读</span>
            </div>
            <button class="notification-item" type="button" @click="goTo('shared')">
              <span class="notification-item__icon notification-item__icon--teal">
                <Share2 :size="16" />
              </span>
              <span>
                <strong>分享链接被访问</strong>
                <small>项目说明文档.pdf 今日已被访问 6 次</small>
              </span>
            </button>
            <button class="notification-item" type="button" @click="goTo('trash')">
              <span class="notification-item__icon notification-item__icon--amber">
                <Trash2 :size="16" />
              </span>
              <span>
                <strong>回收站清理提醒</strong>
                <small>有 2 个文件将在 22 天后永久删除</small>
              </span>
            </button>
            <button class="popover__footer" type="button" @click="notificationsOpen = false">
              <CheckCircle2 :size="14" />
              全部标记为已读
            </button>
          </div>
        </Transition>
      </div>

      <div class="topbar__popover-wrap">
        <button
          class="profile-button"
          type="button"
          aria-label="账户菜单"
          @click.stop="toggleProfile"
        >
          <UserAvatar :src="user.avatarUrl" :initials="user.initials" />
          <span class="profile-button__copy">
            <strong>{{ user.name }}</strong>
            <small>{{ user.plan }}</small>
          </span>
        </button>

        <Transition name="pop">
          <div v-if="profileOpen" class="popover profile-popover">
            <div class="profile-popover__head">
              <UserAvatar
                :src="user.avatarUrl"
                :initials="user.initials"
                size="large"
              />
              <div>
                <strong>{{ user.name }}</strong>
                <span>{{ user.email }}</span>
              </div>
            </div>
            <div class="profile-popover__menu">
              <button
                type="button"
                :disabled="avatarUploading"
                @click="openAvatarPicker"
              >
                <LoaderCircle
                  v-if="avatarUploading"
                  class="is-spinning"
                  :size="16"
                />
                <Camera v-else :size="16" />
                更换头像
              </button>
              <button type="button" @click="showSettings">
                <UserRound :size="16" />
                账户资料
              </button>
              <button type="button" @click="showSettings">
                <Settings :size="16" />
                偏好设置
              </button>
              <button type="button" class="is-danger" @click="requestLogout">
                <LogOut :size="16" />
                退出登录
              </button>
            </div>
          </div>
        </Transition>
      </div>
    </div>
  </header>
</template>

<style scoped>
.topbar {
  position: relative;
  z-index: 20;
  display: flex;
  height: 76px;
  align-items: center;
  justify-content: space-between;
  gap: 24px;
  padding: 0 44px;
  border-bottom: 1px solid var(--line);
  background: rgb(248 247 243 / 90%);
  backdrop-filter: blur(18px);
}

.topbar__left {
  display: flex;
  min-width: 0;
  flex: 1;
  align-items: center;
  gap: 12px;
}

.topbar__mobile-menu {
  display: none;
}

.global-search {
  display: flex;
  width: min(520px, 100%);
  height: 42px;
  align-items: center;
  gap: 9px;
  padding: 0 13px;
  border: 1px solid var(--line);
  border-radius: 7px;
  background: #fff;
  color: var(--text-quiet);
  transition:
    border-color 160ms ease,
    background 160ms ease,
    box-shadow 160ms ease;
}

.global-search:focus-within {
  border-color: color-mix(in srgb, var(--accent) 38%, var(--line));
  background: #fff;
  box-shadow: 0 0 0 3px var(--accent-ring);
}

.global-search input {
  min-width: 0;
  flex: 1;
  padding: 0;
  border: 0;
  outline: 0;
  background: transparent;
  color: var(--text);
  font: inherit;
  font-size: 12px;
}

.global-search input::placeholder {
  color: var(--text-quiet);
}

.global-search kbd {
  min-width: 21px;
  padding: 2px 6px;
  border: 1px solid var(--line);
  border-radius: 4px;
  background: var(--surface-muted);
  color: var(--text-quiet);
  font: inherit;
  font-size: 10px;
  text-align: center;
}

.global-search__clear {
  display: grid;
  width: 23px;
  height: 23px;
  place-items: center;
  padding: 0;
  border: 0;
  border-radius: 5px;
  background: transparent;
  color: var(--text-quiet);
  cursor: pointer;
}

.topbar__actions {
  display: flex;
  align-items: center;
  gap: 5px;
}

.topbar__upload {
  height: 38px;
  margin-right: 7px;
  padding: 0 15px;
}

.topbar__upload span {
  display: inline;
}

.topbar__notification {
  position: relative;
}

.topbar__terminal.is-active {
  background: var(--text);
  color: #fff;
}

.notification-dot {
  position: absolute;
  top: 8px;
  right: 9px;
  width: 6px;
  height: 6px;
  border: 1.5px solid #fff;
  border-radius: 50%;
  background: #ef6b55;
}

.topbar__popover-wrap {
  position: relative;
}

.profile-button {
  display: flex;
  height: 40px;
  align-items: center;
  gap: 9px;
  margin-left: 5px;
  padding: 0 4px;
  border: 0;
  border-radius: 8px;
  background: transparent;
  cursor: pointer;
}

.profile-button:hover {
  background: var(--surface-muted);
}

.profile-button__copy {
  display: flex;
  min-width: 88px;
  flex-direction: column;
  align-items: flex-start;
}

.profile-button__copy strong {
  color: var(--text);
  font-size: 11.5px;
  font-weight: 600;
}

.profile-button__copy small {
  color: var(--text-quiet);
  font-size: 9px;
}

.popover {
  position: absolute;
  top: calc(100% + 10px);
  right: 0;
  width: 312px;
  overflow: hidden;
  border: 1px solid var(--line);
  border-radius: 8px;
  background: #fff;
  box-shadow: 0 18px 48px rgb(25 27 24 / 12%);
}

.popover__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 15px 16px 12px;
  border-bottom: 1px solid var(--line);
}

.popover__head strong {
  color: var(--text);
  font-size: 13px;
}

.popover__head span {
  color: var(--text-quiet);
  font-size: 10px;
}

.notification-item {
  display: flex;
  width: 100%;
  gap: 10px;
  padding: 13px 16px;
  border: 0;
  border-bottom: 1px solid var(--line);
  background: #fff;
  font: inherit;
  text-align: left;
  cursor: pointer;
}

.notification-item:hover {
  background: var(--surface-muted);
}

.notification-item > span:last-child {
  display: flex;
  min-width: 0;
  flex-direction: column;
}

.notification-item strong {
  color: var(--text);
  font-size: 11.5px;
  font-weight: 620;
}

.notification-item small {
  margin-top: 3px;
  color: var(--text-quiet);
  font-size: 10px;
  line-height: 1.45;
}

.notification-item__icon {
  display: grid;
  width: 32px;
  height: 32px;
  flex: 0 0 auto;
  place-items: center;
  border-radius: 8px;
}

.notification-item__icon--teal {
  background: var(--accent-wash);
  color: var(--accent-strong);
}

.notification-item__icon--amber {
  background: #fff7e2;
  color: #aa760c;
}

.popover__footer {
  display: flex;
  width: 100%;
  align-items: center;
  justify-content: center;
  gap: 6px;
  padding: 11px;
  border: 0;
  background: #fff;
  color: var(--text-soft);
  font: inherit;
  font-size: 10.5px;
  cursor: pointer;
}

.profile-popover {
  width: 244px;
}

.profile-popover__head {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 14px;
  border-bottom: 1px solid var(--line);
}

.profile-popover__head > div {
  display: flex;
  min-width: 0;
  flex-direction: column;
}

.profile-popover__head strong {
  color: var(--text);
  font-size: 12px;
}

.profile-popover__head span:not(.avatar) {
  overflow: hidden;
  margin-top: 1px;
  color: var(--text-quiet);
  font-size: 9.5px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.profile-popover__menu {
  padding: 6px;
}

.profile-popover__menu button {
  display: flex;
  width: 100%;
  align-items: center;
  gap: 9px;
  padding: 9px 10px;
  border: 0;
  border-radius: 7px;
  background: transparent;
  color: var(--text-soft);
  font: inherit;
  font-size: 11.5px;
  text-align: left;
  cursor: pointer;
}

.profile-popover__menu button:hover {
  background: var(--surface-muted);
  color: var(--text);
}

.profile-popover__menu button.is-danger {
  color: var(--danger);
}

.profile-popover__menu button:disabled {
  cursor: wait;
  opacity: 0.55;
}

.is-spinning {
  animation: avatar-spin 700ms linear infinite;
}

@keyframes avatar-spin {
  to {
    transform: rotate(360deg);
  }
}

.pop-enter-active,
.pop-leave-active {
  transition:
    opacity 150ms ease,
    transform 150ms ease;
}

.pop-enter-from,
.pop-leave-to {
  opacity: 0;
  transform: translateY(-4px) scale(0.98);
}

@media (max-width: 900px) {
  .topbar {
    height: 68px;
    padding: 0 22px;
  }

  .topbar__mobile-menu {
    display: grid;
  }

  .topbar__help,
  .profile-button__copy {
    display: none;
  }
}

@media (max-width: 640px) {
  .topbar {
    height: 62px;
    gap: 8px;
    padding: 0 14px;
  }

  .topbar__upload {
    width: 36px;
    padding: 0;
  }

  .topbar__upload span,
  .global-search kbd {
    display: none;
  }

  .profile-button {
    margin-left: 0;
  }

  .popover {
    position: fixed;
    top: 68px;
    right: 10px;
    left: 10px;
    width: auto;
  }
}
</style>
