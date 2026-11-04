<script setup>
import { computed } from 'vue'
import {
  Clock3,
  Cloud,
  Files,
  FolderPlus,
  HardDrive,
  HelpCircle,
  LogOut,
  Plus,
  Settings,
  Share2,
  SquareTerminal,
  Star,
  Trash2,
  UploadCloud,
  X,
} from '@lucide/vue'
import { useFileManagerContext } from '../../composables/fileManagerContext'
import { formatBytes } from '../../utils/formatters'
import UserAvatar from '../common/UserAvatar.vue'
import FileTree from '../files/FileTree.vue'

const emit = defineEmits(['upload', 'new-folder', 'logout', 'settings'])
const {
  activeShares,
  currentTag,
  currentView,
  navigate,
  sidebarOpen,
  terminalOpen,
  toast,
  totalStorage,
  toggleTerminal,
  user,
} = useFileManagerContext()

const navItems = [
  { id: 'files', label: '我的文件', icon: Files },
  { id: 'recent', label: '最近使用', icon: Clock3 },
  { id: 'starred', label: '已收藏', icon: Star },
  { id: 'shared', label: '分享中心', icon: Share2, badge: true },
  { id: 'trash', label: '回收站', icon: Trash2 },
]

const tags = [
  { label: '毕业设计', color: 'var(--tag-amber)' },
  { label: '项目', color: 'var(--tag-teal)' },
  { label: '工作', color: 'var(--tag-blue)' },
  { label: '素材', color: 'var(--tag-violet)' },
]

const storagePercent = computed(() => {
  const used = Math.max(totalStorage.value, user.storageUsed)
  return Math.min(100, Math.round((used / user.storageTotal) * 100))
})

function selectTag(tag) {
  navigate('tagged', tag)
}
</script>

<template>
  <aside class="sidebar" :class="{ 'is-open': sidebarOpen }">
    <div class="sidebar__brand">
      <button
        class="brand-mark"
        type="button"
        aria-label="返回我的文件"
        @click="navigate('files')"
      >
        <Cloud :size="21" :stroke-width="2" />
      </button>
      <div class="brand-copy">
        <strong>filehome</strong>
        <span>PERSONAL CLOUD</span>
      </div>
      <button
        class="sidebar__close"
        type="button"
        aria-label="关闭导航"
        @click="sidebarOpen = false"
      >
        <X :size="19" />
      </button>
    </div>

    <div class="sidebar__quick-actions">
      <button class="sidebar-upload" type="button" @click="emit('upload')">
        <UploadCloud :size="17" />
        <span>上传文件</span>
        <kbd>U</kbd>
      </button>
      <button
        class="sidebar-create"
        type="button"
        title="新建文件夹"
        aria-label="新建文件夹"
        @click="emit('new-folder')"
      >
        <Plus :size="18" />
      </button>
    </div>

    <nav class="sidebar__nav" aria-label="文件库导航">
      <p class="sidebar__eyebrow">文件库</p>
      <button
        v-for="item in navItems"
        :key="item.id"
        class="sidebar-link"
        :class="{ 'is-active': currentView === item.id }"
        type="button"
        @click="navigate(item.id)"
      >
        <component :is="item.icon" :size="17" :stroke-width="1.8" />
        <span>{{ item.label }}</span>
        <span
          v-if="item.badge && activeShares.length"
          class="sidebar-link__badge"
        >
          {{ activeShares.length }}
        </span>
      </button>
    </nav>

    <FileTree @new-folder="emit('new-folder')" />

    <nav class="sidebar__nav sidebar__nav--tags" aria-label="标签导航">
      <div class="sidebar__eyebrow-row">
        <p class="sidebar__eyebrow">标签</p>
        <button
          class="sidebar__mini-action"
          type="button"
          title="新建标签"
          aria-label="新建标签"
          @click="toast('标签管理将在后端接入后开放', 'info')"
        >
          <FolderPlus :size="14" />
        </button>
      </div>
      <button
        v-for="tag in tags"
        :key="tag.label"
        class="sidebar-link sidebar-link--tag"
        :class="{
          'is-active': currentView === 'tagged' && currentTag === tag.label,
        }"
        type="button"
        @click="selectTag(tag.label)"
      >
        <span class="tag-dot" :style="{ background: tag.color }" />
        <span>{{ tag.label }}</span>
      </button>
    </nav>

    <div class="sidebar__spacer" />

    <section class="storage-widget">
      <div class="storage-widget__head">
        <span>
          <HardDrive :size="15" />
          存储空间
        </span>
        <strong>{{ storagePercent }}%</strong>
      </div>
      <div class="storage-widget__track">
        <span :style="{ width: `${storagePercent}%` }" />
      </div>
      <p>
        已使用 {{ formatBytes(Math.max(totalStorage, user.storageUsed)) }}，共
        {{ formatBytes(user.storageTotal) }}
      </p>
      <button type="button" @click="navigate('trash')">清理回收站</button>
    </section>

    <div class="sidebar__bottom">
      <button
        class="sidebar-link"
        type="button"
        :class="{ 'is-active': terminalOpen }"
        @click="toggleTerminal(); sidebarOpen = false"
      >
        <SquareTerminal :size="17" />
        <span>命令窗口</span>
        <kbd class="sidebar-link__shortcut">⌃`</kbd>
      </button>
      <button
        class="sidebar-link"
        type="button"
        @click="emit('settings')"
      >
        <Settings :size="17" />
        <span>空间设置</span>
      </button>
      <button
        class="sidebar-link"
        type="button"
        @click="toast('帮助中心可通过右上角问号打开', 'info')"
      >
        <HelpCircle :size="17" />
        <span>帮助与反馈</span>
      </button>
      <div class="sidebar__user">
        <UserAvatar
          :src="user.avatarUrl"
          :initials="user.initials"
          size="small"
        />
        <div>
          <strong>{{ user.name }}</strong>
          <span>{{ user.plan }}</span>
        </div>
        <button
          type="button"
          class="sidebar__user-menu"
          title="退出登录"
          aria-label="退出登录"
          @click="emit('logout')"
        >
          <LogOut :size="15" />
        </button>
      </div>
    </div>
  </aside>
</template>

<style scoped>
.sidebar {
  position: relative;
  z-index: 30;
  display: flex;
  width: 236px;
  height: 100dvh;
  flex: 0 0 236px;
  flex-direction: column;
  padding: 24px 16px 18px;
  overflow-x: hidden;
  overflow-y: auto;
  scrollbar-width: none;
  border-right: 1px solid var(--line);
  background: var(--sidebar);
  color: var(--sidebar-text);
}

.sidebar::-webkit-scrollbar {
  display: none;
}

@media (max-height: 980px) and (min-width: 901px) {
  .sidebar__nav--tags {
    display: none;
  }

  .sidebar__quick-actions {
    margin-bottom: 16px;
  }
}

@media (max-height: 760px) and (min-width: 901px) {
  .storage-widget {
    display: none;
  }

  .sidebar__spacer {
    flex: 0 0 auto;
    min-height: 10px;
  }
}

.sidebar__brand {
  display: flex;
  align-items: center;
  gap: 9px;
  height: 38px;
  padding: 0 4px;
}

.brand-mark {
  display: grid;
  width: 33px;
  height: 33px;
  flex: 0 0 auto;
  place-items: center;
  padding: 0;
  border: 1px solid var(--text);
  border-radius: 8px;
  background: var(--text);
  color: #fff;
  cursor: pointer;
  box-shadow: none;
}

.brand-copy {
  display: flex;
  min-width: 0;
  flex-direction: column;
}

.brand-copy strong {
  color: var(--text);
  font-size: 15px;
  font-weight: 680;
  letter-spacing: 0;
  line-height: 1.2;
}

.brand-copy span {
  margin-top: 1px;
  color: var(--text-quiet);
  font-size: 8px;
  font-weight: 650;
  letter-spacing: 0.12em;
}

.sidebar__close {
  display: none;
  width: 32px;
  height: 32px;
  margin-left: auto;
  place-items: center;
  padding: 0;
  border: 0;
  border-radius: 8px;
  background: var(--surface-muted);
  color: var(--sidebar-text);
}

.sidebar__quick-actions {
  display: flex;
  gap: 8px;
  margin: 28px 2px 27px;
}

.sidebar-upload {
  display: flex;
  height: 40px;
  flex: 1;
  align-items: center;
  gap: 9px;
  padding: 0 12px;
  border: 0;
  border: 1px solid var(--text);
  border-radius: 7px;
  background: var(--text);
  color: #fff;
  font: inherit;
  font-size: 12px;
  font-weight: 600;
  cursor: pointer;
  box-shadow: none;
  transition:
    background 160ms ease,
    transform 160ms ease;
}

.sidebar-upload:hover {
  background: #30332f;
  transform: none;
}

.sidebar-upload span {
  flex: 1;
  text-align: left;
}

.sidebar-upload kbd {
  padding: 2px 5px;
  border: 1px solid rgb(255 255 255 / 18%);
  border-radius: 4px;
  background: rgb(255 255 255 / 8%);
  color: rgb(255 255 255 / 60%);
  font: inherit;
  font-size: 9px;
}

.sidebar-create {
  display: grid;
  width: 40px;
  height: 40px;
  place-items: center;
  padding: 0;
  border: 1px solid var(--line);
  border-radius: 7px;
  background: #fff;
  color: var(--sidebar-text);
  cursor: pointer;
}

.sidebar-create:hover {
  border-color: var(--line-strong);
  background: var(--surface-muted);
}

.sidebar__nav {
  padding: 0 2px;
}

.sidebar__nav + .sidebar__nav {
  margin-top: 29px;
}

.sidebar__eyebrow {
  margin: 0 11px 9px;
  color: var(--text-quiet);
  font-size: 9px;
  font-weight: 650;
  letter-spacing: 0.1em;
  text-transform: uppercase;
}

.sidebar__eyebrow-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.sidebar__eyebrow-row .sidebar__eyebrow {
  margin-right: 0;
}

.sidebar__mini-action {
  display: grid;
  width: 24px;
  height: 24px;
  place-items: center;
  margin: 0 6px 7px 0;
  padding: 0;
  border: 0;
  border-radius: 6px;
  background: transparent;
  color: var(--text-quiet);
  cursor: pointer;
}

.sidebar__mini-action:hover {
  background: var(--surface-muted);
  color: var(--text);
}

.sidebar-link {
  position: relative;
  display: flex;
  width: 100%;
  height: 40px;
  align-items: center;
  gap: 11px;
  padding: 0 11px;
  border: 0;
  border-radius: 7px;
  background: transparent;
  color: var(--text-soft);
  font: inherit;
  font-size: 12.5px;
  text-align: left;
  cursor: pointer;
  transition:
    background 150ms ease,
    color 150ms ease;
}

.sidebar-link:hover {
  background: #fff;
  color: var(--text);
}

.sidebar-link.is-active {
  border: 1px solid var(--line);
  background: #fff;
  color: var(--text);
  font-weight: 600;
  box-shadow: 0 1px 2px rgb(25 27 24 / 3%);
}

.sidebar-link.is-active::before {
  display: none;
}

.sidebar-link__badge {
  min-width: 20px;
  margin-left: auto;
  padding: 1px 5px;
  border-radius: 8px;
  border: 1px solid var(--line);
  background: transparent;
  color: var(--text-soft);
  font-size: 8.5px;
  text-align: center;
}

.sidebar-link--tag {
  height: 34px;
  padding-left: 13px;
}

.tag-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  box-shadow: none;
}

.sidebar__spacer {
  flex: 1;
  min-height: 18px;
}

.storage-widget {
  flex: 0 0 auto;
  margin: 0 2px 18px;
  padding: 15px 14px 13px;
  border: 1px solid #dfe3ee;
  border-radius: 8px;
  background: #eef1f7;
}

.storage-widget__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  color: var(--text-soft);
  font-size: 10px;
}

.storage-widget__head span {
  display: flex;
  align-items: center;
  gap: 6px;
}

.storage-widget__head strong {
  color: var(--text);
  font-size: 10px;
}

.storage-widget__track {
  height: 4px;
  margin: 10px 0 8px;
  overflow: hidden;
  border-radius: 4px;
  background: #dfe3ec;
}

.storage-widget__track span {
  display: block;
  height: 100%;
  border-radius: inherit;
  background: var(--accent);
}

.storage-widget p {
  margin: 0;
  color: var(--text-quiet);
  font-size: 9px;
  line-height: 1.5;
}

.storage-widget button {
  margin-top: 6px;
  padding: 0;
  border: 0;
  background: transparent;
  color: var(--accent-strong);
  font: inherit;
  font-size: 9.5px;
  cursor: pointer;
}

.sidebar__bottom {
  flex: 0 0 auto;
  padding: 0 2px;
}

.sidebar__bottom > .sidebar-link {
  height: 33px;
}

.sidebar-link__shortcut {
  margin-left: auto;
  padding: 1px 4px;
  border: 1px solid var(--line);
  border-radius: 3px;
  color: var(--text-quiet);
  font: inherit;
  font-size: 8px;
}

.sidebar__user {
  display: grid;
  grid-template-columns: 34px minmax(0, 1fr) 22px;
  align-items: center;
  gap: 9px;
  margin-top: 12px;
  padding: 14px 8px 2px;
  border-top: 1px solid var(--line);
}

.sidebar__user div {
  display: flex;
  min-width: 0;
  flex-direction: column;
}

.sidebar__user strong {
  overflow: hidden;
  color: var(--text);
  font-size: 11.5px;
  font-weight: 600;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.sidebar__user span:not(.avatar) {
  color: var(--text-quiet);
  font-size: 10px;
}

.sidebar__user-menu {
  display: flex;
  width: 22px;
  height: 28px;
  align-items: center;
  justify-content: center;
  padding: 0;
  border: 0;
  background: transparent;
  cursor: pointer;
}

.sidebar__user-menu:hover {
  color: var(--danger);
}

@media (max-width: 900px) {
  .sidebar {
    position: fixed;
    inset: 0 auto 0 0;
    transform: translateX(-102%);
    transition: transform 220ms ease;
    box-shadow: 20px 0 60px rgb(20 22 19 / 14%);
  }

  .sidebar.is-open {
    transform: translateX(0);
  }

  .sidebar__close {
    display: grid;
  }
}
</style>
