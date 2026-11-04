<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, provide, reactive, ref } from 'vue'
import { UploadCloud } from '@lucide/vue'
import AuthScreen from './components/auth/AuthScreen.vue'
import ToastStack from './components/common/ToastStack.vue'
import FileDetailsDrawer from './components/files/FileDetailsDrawer.vue'
import FilePreviewModal from './components/files/FilePreviewModal.vue'
import UploadTray from './components/files/UploadTray.vue'
import AppHeader from './components/layout/AppHeader.vue'
import AppSidebar from './components/layout/AppSidebar.vue'
import ConfirmDialog from './components/overlays/ConfirmDialog.vue'
import PromptDialog from './components/overlays/PromptDialog.vue'
import ShareDialog from './components/overlays/ShareDialog.vue'
import UserSettingsDialog from './components/overlays/UserSettingsDialog.vue'
import DashboardView from './components/views/DashboardView.vue'
import ShareCenterView from './components/views/ShareCenterView.vue'
import TrashView from './components/views/TrashView.vue'
import CommandTerminal from './components/terminal/CommandTerminal.vue'
import PublicShareView from './components/share/PublicShareView.vue'
import { fileManagerKey } from './composables/fileManagerContext'
import { useFileManager } from './composables/useApiFileManager'

const manager = useFileManager()
provide(fileManagerKey, manager)

const {
  authReady,
  authenticated,
  createFolder,
  currentView,
  deletePermanently,
  dismissToast,
  moveToTrash,
  navigate,
  previewFile,
  refreshFiles,
  renameFile,
  revokeShare,
  searchQuery,
  selectedIds,
  shares,
  sidebarOpen,
  terminalOpen,
  toasts,
  toast,
  toggleTerminal,
  uploads,
  visibleFiles,
  addUploads,
  clearSelection,
  clearCompletedUploads,
  initialize,
  logout,
} = manager

const header = ref(null)
const fileInput = ref(null)
const dragActive = ref(false)
const dragDepth = ref(0)
const settingsOpen = ref(false)

function getPublicShareToken() {
  const match = globalThis.location?.pathname.match(/^\/s\/([^/]+)\/?$/)
  return match ? decodeURIComponent(match[1]) : ''
}

const publicShareToken = ref(getPublicShareToken())

function openPublicShare(share) {
  if (!share?.token) return
  globalThis.history?.pushState({}, '', `/s/${share.token}`)
  publicShareToken.value = share.token
  shareDialog.open = false
}

function closePublicShare() {
  globalThis.history?.pushState({}, '', '/')
  publicShareToken.value = ''
  navigate('files')
}

function syncPublicShareRoute() {
  publicShareToken.value = getPublicShareToken()
}

const prompt = reactive({
  open: false,
  mode: 'folder',
  title: '新建文件夹',
  label: '文件夹名称',
  initialValue: '',
  placeholder: '例如：项目资料',
  confirmText: '创建',
})

const folderToRename = ref(null)
const shareDialog = reactive({
  open: false,
  file: null,
  share: null,
})

const confirmDialog = reactive({
  open: false,
  title: '',
  description: '',
  confirmText: '确认',
  tone: 'danger',
})
const pendingConfirm = ref(null)

const showDashboard = computed(() =>
  ['files', 'recent', 'starred', 'tagged'].includes(currentView.value),
)

function openUploadPicker() {
  if (currentView.value !== 'files') navigate('files')
  nextTick(() => fileInput.value?.click())
}

manager.registerUploadHandler(openUploadPicker)

function onFileSelected(event) {
  addUploads(event.target.files)
  event.target.value = ''
}

function openNewFolder() {
  if (currentView.value !== 'files') navigate('files')
  folderToRename.value = null
  Object.assign(prompt, {
    open: true,
    mode: 'folder',
    title: '新建文件夹',
    label: '文件夹名称',
    initialValue: '',
    placeholder: '例如：项目资料',
    confirmText: '创建',
  })
}

function openRename(file) {
  if (!file) return
  folderToRename.value = file
  Object.assign(prompt, {
    open: true,
    mode: 'rename',
    title: '重命名',
    label: '新的名称',
    initialValue: file.name,
    placeholder: '',
    confirmText: '保存',
  })
}

function submitPrompt(value) {
  if (prompt.mode === 'folder') createFolder(value)
  if (prompt.mode === 'rename' && folderToRename.value) {
    renameFile(folderToRename.value, value)
  }
  prompt.open = false
  folderToRename.value = null
}

function openShare(file, share = null) {
  if (!file) {
    navigate('files')
    toast('请在文件列表中选择一个项目，再创建分享', 'info')
    return
  }
  shareDialog.file = file
  shareDialog.share = share || manager.getActiveShareForFile(file.id) || null
  shareDialog.open = true
}

function closeShare() {
  shareDialog.open = false
  shareDialog.file = null
  shareDialog.share = null
}

function openShareFromCenter(share) {
  const file = manager.files.value.find((item) => item.id === share.fileId)
  if (!file) {
    toast('原文件已不存在，无法修改分享设置', 'warning')
    return
  }
  openShare(file, share)
}

function showConfirm({ title, description, confirmText, tone = 'danger', action }) {
  Object.assign(confirmDialog, {
    open: true,
    title,
    description,
    confirmText,
    tone,
  })
  pendingConfirm.value = action
}

function closeConfirm() {
  confirmDialog.open = false
  pendingConfirm.value = null
}

function confirmAction() {
  const action = pendingConfirm.value
  confirmDialog.open = false
  pendingConfirm.value = null
  action?.()
}

function requestRevoke(share) {
  showConfirm({
    title: '撤销分享链接？',
    description: `撤销后，「${share.fileName}」的现有链接和提取码会立即失效，访问者将无法继续打开。`,
    confirmText: '撤销分享',
    action: async () => {
      await revokeShare(share.id)
      await refreshFiles()
    },
  })
}

function requestPermanentDelete(ids) {
  const count = Array.isArray(ids) ? ids.length : 0
  showConfirm({
    title: '永久删除这些项目？',
    description: `将永久删除 ${count} 个项目及其分享记录，此操作无法撤销。`,
    confirmText: '永久删除',
    action: () => deletePermanently(ids),
  })
}

function requestEmptyTrash(files) {
  const ids = files.map((file) => file.id)
  showConfirm({
    title: '清空回收站？',
    description: `将永久删除回收站中的 ${ids.length} 个项目，此操作无法撤销。`,
    confirmText: '清空回收站',
    action: () => deletePermanently(ids),
  })
}

function requestBulkDelete() {
  if (!selectedIds.value.length) return
  showConfirm({
    title: '移到回收站？',
    description: `将把 ${selectedIds.value.length} 个选中项目移到回收站，可在 30 天内恢复。`,
    confirmText: '移到回收站',
    tone: 'warning',
    action: () => moveToTrash(selectedIds.value),
  })
}

function requestLogout() {
  showConfirm({
    title: '退出当前账户？',
    description: '退出后当前浏览器会话会立即失效，本机 CLI 登录不受影响。',
    confirmText: '退出登录',
    tone: 'warning',
    action: logout,
  })
}

function openSettings() {
  settingsOpen.value = true
}

function isInternalDrag(event) {
  return Array.from(event.dataTransfer?.types || []).includes(
    'application/x-filehome-items',
  )
}

function onDragEnter(event) {
  if (isInternalDrag(event)) return
  event.preventDefault()
  dragDepth.value += 1
  dragActive.value = true
}

function onDragLeave(event) {
  if (isInternalDrag(event)) return
  event.preventDefault()
  dragDepth.value = Math.max(0, dragDepth.value - 1)
  if (!dragDepth.value) dragActive.value = false
}

function onDrop(event) {
  if (isInternalDrag(event)) return
  event.preventDefault()
  dragDepth.value = 0
  dragActive.value = false
  if (event.dataTransfer?.files?.length) {
    if (currentView.value !== 'files') navigate('files')
    addUploads(event.dataTransfer.files)
  }
}

function onGlobalKeydown(event) {
  if (publicShareToken.value) return
  const target = event.target
  const typing =
    target instanceof HTMLInputElement ||
    target instanceof HTMLTextAreaElement ||
    target instanceof HTMLSelectElement ||
    target?.isContentEditable

  if (
    (event.ctrlKey || event.metaKey) &&
    (event.key === '`' || event.code === 'Backquote')
  ) {
    event.preventDefault()
    toggleTerminal()
    return
  }

  if (
    (event.ctrlKey || event.metaKey) &&
    event.key.toLowerCase() === 'c' &&
    !typing &&
    selectedIds.value.length &&
    showDashboard.value
  ) {
    event.preventDefault()
    manager.copyItemsToClipboard(selectedIds.value)
    return
  }

  if (
    (event.ctrlKey || event.metaKey) &&
    event.key.toLowerCase() === 'v' &&
    !typing &&
    manager.clipboardHasItems.value
  ) {
    event.preventDefault()
    manager.pasteCopiedItems(manager.currentFolderId.value)
    return
  }

  if (event.key === '/' && !typing && !previewFile.value) {
    event.preventDefault()
    header.value?.focusSearch()
  }

  if (
    event.key.toLowerCase() === 'u' &&
    !typing &&
    !event.ctrlKey &&
    !event.metaKey &&
    !previewFile.value
  ) {
    event.preventDefault()
    openUploadPicker()
  }

  if (
    (event.ctrlKey || event.metaKey) &&
    event.key.toLowerCase() === 'a' &&
    !typing &&
    showDashboard.value
  ) {
    event.preventDefault()
    manager.selectAllVisible()
  }

  if (
    (event.key === 'Delete' || event.key === 'Backspace') &&
    !typing &&
    selectedIds.value.length &&
    showDashboard.value
  ) {
    event.preventDefault()
    requestBulkDelete()
  }

  if (event.key === 'Escape') {
    if (terminalOpen.value) {
      terminalOpen.value = false
      return
    }
    searchQuery.value = ''
    clearSelection()
  }
}

onMounted(() => {
  document.addEventListener('keydown', onGlobalKeydown)
  globalThis.addEventListener('popstate', syncPublicShareRoute)
  initialize()
})

onBeforeUnmount(() => {
  document.removeEventListener('keydown', onGlobalKeydown)
  globalThis.removeEventListener('popstate', syncPublicShareRoute)
})
</script>

<template>
  <PublicShareView
    v-if="publicShareToken"
    :token="publicShareToken"
    @close="closePublicShare"
  />

  <main v-else-if="!authReady" class="auth-bootstrap" aria-busy="true">
    <span class="auth-bootstrap__spinner" aria-hidden="true" />
  </main>

  <AuthScreen v-else-if="!authenticated" />

  <div v-else class="app-shell">
    <AppSidebar
      @upload="openUploadPicker"
      @new-folder="openNewFolder"
      @logout="requestLogout"
      @settings="openSettings"
    />
    <button
      v-if="sidebarOpen"
      class="sidebar-backdrop"
      type="button"
      aria-label="关闭导航"
      @click="sidebarOpen = false"
    />

    <div
      class="app-stage"
      @dragenter="onDragEnter"
      @dragover.prevent
      @dragleave="onDragLeave"
      @drop="onDrop"
    >
      <AppHeader
        ref="header"
        @upload="openUploadPicker"
        @logout="requestLogout"
        @settings="openSettings"
      />

      <main class="workspace">
        <div class="workspace__inner">
          <DashboardView
            v-if="showDashboard"
            @upload="openUploadPicker"
            @new-folder="openNewFolder"
            @share="openShare"
            @cancel-share="requestRevoke"
            @rename="openRename"
          />
          <ShareCenterView
            v-else-if="currentView === 'shared'"
            @revoke="requestRevoke"
            @create-share="openShare(null)"
            @open-share="openPublicShare"
            @edit-share="openShareFromCenter"
          />
          <TrashView
            v-else-if="currentView === 'trash'"
            @permanent-delete="requestPermanentDelete"
            @empty-trash="requestEmptyTrash"
          />
        </div>
      </main>

      <Transition name="drag">
        <div v-if="dragActive" class="drop-overlay">
          <div class="drop-overlay__card">
            <span><UploadCloud :size="28" /></span>
            <strong>释放以上传文件</strong>
            <p>文件会上传到当前文件夹，并显示实时上传进度。</p>
          </div>
        </div>
      </Transition>

      <CommandTerminal />
    </div>

    <input
      ref="fileInput"
      class="visually-hidden"
      type="file"
      multiple
      @change="onFileSelected"
    />

    <UploadTray :uploads="uploads" @clear="clearCompletedUploads" />
  </div>

  <FileDetailsDrawer @share="openShare" @rename="openRename" />
  <FilePreviewModal v-if="previewFile" @share="openShare" />

  <PromptDialog
    :open="prompt.open"
    :title="prompt.title"
    :label="prompt.label"
    :initial-value="prompt.initialValue"
    :placeholder="prompt.placeholder"
    :confirm-text="prompt.confirmText"
    @close="prompt.open = false"
    @submit="submitPrompt"
  />

  <ShareDialog
    :open="shareDialog.open"
    :file="shareDialog.file"
    :share="shareDialog.share"
    @close="closeShare"
    @open="openPublicShare"
  />

  <ConfirmDialog
    :open="confirmDialog.open"
    :title="confirmDialog.title"
    :description="confirmDialog.description"
    :confirm-text="confirmDialog.confirmText"
    :tone="confirmDialog.tone"
    @close="closeConfirm"
    @confirm="confirmAction"
  />

  <ToastStack :toasts="toasts" @dismiss="dismissToast" />
  <UserSettingsDialog
    :open="settingsOpen"
    @close="settingsOpen = false"
    @deactivated="settingsOpen = false"
  />
</template>
