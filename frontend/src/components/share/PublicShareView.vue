<script setup>
import { computed, nextTick, ref, watch } from 'vue'
import {
  ArrowLeft,
  CheckCircle2,
  Clock3,
  Copy,
  Download,
  Link2,
  LockKeyhole,
  LogOut,
  ShieldCheck,
  UserRound,
} from '@lucide/vue'
import { useFileManagerContext } from '../../composables/fileManagerContext'
import {
  downloadPublicShare,
  publicSharePreviewAccess,
  publicShare as fetchPublicShare,
  verifyShareCode,
} from '../../services/sharesApi'
import { getFileFormat } from '../../utils/fileFormats'
import { formatBytes, formatDate, formatRemaining } from '../../utils/formatters'
import AppModal from '../overlays/AppModal.vue'
import ShareAuthDialog from './ShareAuthDialog.vue'
import SharePreview from './SharePreview.vue'

const props = defineProps({
  token: {
    type: String,
    required: true,
  },
})

const emit = defineEmits(['close'])
const manager = useFileManagerContext()
const {
  authenticated,
  copyText,
  logout,
  toast,
  user,
} = manager

const codeDialogOpen = ref(false)
const authDialogOpen = ref(false)
const codeValue = ref('')
const codeError = ref('')
const codeInput = ref(null)
const codeVerified = ref(false)
const pendingDownload = ref(false)
const codePurpose = ref('download')
const shareData = ref(null)
const loadError = ref('')
const loading = ref(true)
const previewAccess = ref(null)
const previewLoading = ref(false)
const previewError = ref('')
const previewLocked = ref(false)

const share = computed(() => shareData.value)
const file = computed(() => shareData.value?.root || null)

const format = computed(() => (file.value ? getFileFormat(file.value) : null))

const isExpired = computed(() => {
  if (!share.value) return true
  return (
    share.value.status !== 'active' ||
    new Date(share.value.expiresAt).getTime() <= Date.now()
  )
})

const folderItems = computed(() => shareData.value?.folderItems || [])

const downloadAllowed = computed(
  () => share.value?.permission === 'download',
)

const canDownload = computed(
  () => share.value && file.value && !isExpired.value && downloadAllowed.value,
)
const codeDialogDescription = computed(() =>
  codePurpose.value === 'preview'
    ? '输入分享者提供的提取码以查看原始文件内容'
    : '输入分享者提供的提取码以继续下载',
)

watch(codeDialogOpen, async (open) => {
  if (!open) return
  codeValue.value = ''
  codeError.value = ''
  await nextTick()
  codeInput.value?.focus()
})

watch(
  () => props.token,
  async (token) => {
    loading.value = true
    loadError.value = ''
    shareData.value = null
    codeVerified.value = false
    codeValue.value = ''
    previewAccess.value = null
    previewError.value = ''
    previewLocked.value = false
    try {
      shareData.value = await fetchPublicShare(token)
      if (
        shareData.value?.root?.kind === 'file' &&
        !shareData.value.accessCodeRequired
      ) {
        await loadPreviewAccess()
      } else if (shareData.value?.accessCodeRequired) {
        previewLocked.value = true
      }
    } catch (error) {
      loadError.value = error.message || '分享链接不可用'
      shareData.value = null
    } finally {
      loading.value = false
    }
  },
  { immediate: true },
)

function startDownload() {
  if (!canDownload.value) {
    toast(
      downloadAllowed.value
        ? '当前分享不可下载'
        : '分享者只允许在线预览，未开放下载',
      'info',
    )
    return
  }
  if (share.value.accessCodeRequired && !codeVerified.value) {
    codePurpose.value = 'download'
    codeDialogOpen.value = true
    return
  }
  continueAfterCode()
}

function requestPreviewCode() {
  codePurpose.value = 'preview'
  codeDialogOpen.value = true
}

async function loadPreviewAccess() {
  if (!file.value || file.value.kind === 'folder') return
  previewLoading.value = true
  previewError.value = ''
  previewLocked.value = false
  try {
    previewAccess.value = await publicSharePreviewAccess(
      props.token,
      codeVerified.value ? codeValue.value : '',
    )
  } catch (error) {
    previewError.value = error.message || '原始文件预览加载失败'
  } finally {
    previewLoading.value = false
  }
}

async function verifyCode() {
  try {
    const result = await verifyShareCode(props.token, codeValue.value)
    if (!result.valid) {
      codeError.value = '提取码不正确，请重新输入'
      codeInput.value?.select()
      return
    }
    codeVerified.value = true
    codeDialogOpen.value = false
    await loadPreviewAccess()
    if (codePurpose.value === 'preview') {
      return
    }
    continueAfterCode()
  } catch (error) {
    codeError.value = error.message || '提取码校验失败'
  }
}

function continueAfterCode() {
  if (!authenticated.value) {
    pendingDownload.value = true
    authDialogOpen.value = true
    return
  }
  executeDownload()
}

async function executeDownload() {
  if (!file.value) {
    toast('分享文件已失效或不可用', 'warning')
    return
  }
  try {
    await downloadPublicShare(
      props.token,
      codeVerified.value ? codeValue.value : '',
      file.value.name,
      file.value.kind === 'folder',
    )
  } catch (error) {
    toast(error.message, 'warning')
  }
}

function openLogin() {
  pendingDownload.value = false
  authDialogOpen.value = true
}

function onAuthenticated() {
  authDialogOpen.value = false
  if (pendingDownload.value) {
    pendingDownload.value = false
    executeDownload()
  }
}

function closeAuthDialog() {
  authDialogOpen.value = false
  pendingDownload.value = false
}
</script>

<template>
  <main class="public-share">
    <header class="public-share__header">
      <button class="public-brand" type="button" @click="emit('close')">
        <span><Link2 :size="18" /></span>
        <strong>filehome</strong>
      </button>
      <div class="public-share__header-actions">
        <span class="secure-label">
          <ShieldCheck :size="14" />
          安全分享
        </span>
        <button
          v-if="!authenticated"
          class="public-login"
          type="button"
          @click="openLogin"
        >
          <UserRound :size="15" />
          登录 / 注册
        </button>
        <span v-else class="public-user">
          <span>{{ user.initials }}</span>
          {{ user.name }}
          <button type="button" title="退出登录" aria-label="退出登录" @click="logout">
            <LogOut :size="14" />
          </button>
        </span>
      </div>
    </header>

    <div v-if="share && !isExpired" class="public-share__layout">
      <section class="public-share__main">
        <button class="public-back" type="button" @click="emit('close')">
          <ArrowLeft :size="14" />
          返回文件库
        </button>
        <div class="public-share__intro">
          <span>SHARED WITH YOU</span>
          <h1>{{ file?.kind === 'folder' ? '一个文件夹已分享给你' : '一个文件已分享给你' }}</h1>
          <p>此链接由分享者创建，预览内容不会写入你的文件库。</p>
        </div>
        <SharePreview
          :file="file"
          :folder-items="folderItems"
          :source-url="previewAccess?.url || ''"
          :preview-kind="previewAccess?.previewKind || file?.preview || ''"
          :loading="previewLoading"
          :locked="previewLocked"
          :error="previewError"
          @unlock="requestPreviewCode"
        />
      </section>

      <aside class="public-share__aside">
        <div class="shared-file">
          <span class="shared-file__icon">
            <Download v-if="file?.kind === 'file'" :size="21" />
            <Link2 v-else :size="21" />
          </span>
          <span>FILE HOME SHARE</span>
        </div>

        <h2>{{ share.fileName }}</h2>
        <p class="shared-by">
          <span>{{ share.ownerInitials || 'FH' }}</span>
          由 {{ share.ownerName || 'File Home 用户' }} 分享
        </p>

        <dl class="shared-meta">
          <div>
            <dt>类型</dt>
            <dd>{{ share.rootType === 'folder' ? '文件夹' : format?.label }}</dd>
          </div>
          <div v-if="share.rootType === 'file'">
            <dt>大小</dt>
            <dd>{{ formatBytes(file?.size) }}</dd>
          </div>
          <div v-else>
            <dt>项目</dt>
            <dd>{{ folderItems.length }} 个</dd>
          </div>
          <div>
            <dt>分享时间</dt>
            <dd>{{ formatDate(share.createdAt, 'full') }}</dd>
          </div>
          <div>
            <dt>有效期</dt>
            <dd>{{ formatRemaining(share.expiresAt) }}</dd>
          </div>
        </dl>

        <div class="share-security">
          <LockKeyhole :size="16" />
          <div>
            <strong>受保护的分享</strong>
            <span v-if="share.accessCodeRequired">需要提取码</span>
            <span v-if="!authenticated">下载前需要登录或注册</span>
          </div>
        </div>

        <button
          class="share-download"
          type="button"
          :disabled="!canDownload"
          @click="startDownload"
        >
          <Download :size="17" />
          {{
            downloadAllowed
              ? share.rootType === 'folder'
                ? '下载文件夹 ZIP'
                : '下载文件'
              : '仅支持在线预览'
          }}
        </button>
        <button
          class="share-copy"
          type="button"
          @click="copyText(share.url, '分享链接已复制')"
        >
          <Copy :size="15" />
          复制分享链接
        </button>

        <div class="share-expiry">
          <Clock3 :size="14" />
          链接将在 {{ formatDate(share.expiresAt, 'full') }} 失效
        </div>
      </aside>
    </div>

    <section v-else class="share-unavailable">
      <span><LockKeyhole :size="30" /></span>
      <h1>分享链接不可用</h1>
      <p>链接可能已过期、被撤销，或分享内容已经被删除。</p>
      <button type="button" @click="emit('close')">
        <ArrowLeft :size="15" />
        返回 File Home
      </button>
    </section>

    <footer class="public-share__footer">
      <span>FILE HOME · PERSONAL CLOUD</span>
      <span>安全存储 · 受控分享</span>
    </footer>

    <AppModal
      :open="codeDialogOpen"
      title="输入提取码"
      :description="codeDialogDescription"
      size="sm"
      @close="codeDialogOpen = false"
    >
      <form class="share-code-form" @submit.prevent="verifyCode">
        <label for="share-access-code">提取码</label>
        <input
          id="share-access-code"
          ref="codeInput"
          v-model="codeValue"
          type="text"
          maxlength="12"
          autocomplete="off"
          placeholder="请输入提取码"
        />
        <p v-if="codeError">{{ codeError }}</p>
      </form>
      <template #footer>
        <button class="button button--ghost" type="button" @click="codeDialogOpen = false">
          取消
        </button>
        <button class="button button--primary" type="button" @click="verifyCode">
          <CheckCircle2 :size="15" />
          验证并继续
        </button>
      </template>
    </AppModal>

    <ShareAuthDialog
      :open="authDialogOpen"
      :share-name="share?.fileName || ''"
      @close="closeAuthDialog"
      @success="onAuthenticated"
    />
  </main>
</template>

<style scoped>
.public-share {
  display: flex;
  min-height: 100dvh;
  flex-direction: column;
  background:
    radial-gradient(circle at 85% 0%, rgb(49 85 217 / 5%), transparent 24%),
    #f4f3ef;
  color: var(--text);
}

.public-share__header {
  display: flex;
  width: min(100%, 1440px);
  height: 76px;
  flex: 0 0 auto;
  align-items: center;
  justify-content: space-between;
  margin: 0 auto;
  padding: 0 44px;
}

.public-brand {
  display: flex;
  align-items: center;
  gap: 9px;
  padding: 0;
  border: 0;
  background: transparent;
  color: var(--text);
  cursor: pointer;
}

.public-brand > span {
  display: grid;
  width: 33px;
  height: 33px;
  place-items: center;
  border-radius: 8px;
  background: var(--text);
  color: #fff;
}

.public-brand strong {
  font-size: 15px;
  font-weight: 680;
}

.public-share__header-actions {
  display: flex;
  align-items: center;
  gap: 10px;
}

.secure-label {
  display: flex;
  align-items: center;
  gap: 5px;
  color: var(--text-quiet);
  font-size: 9px;
}

.public-login {
  display: flex;
  height: 35px;
  align-items: center;
  gap: 6px;
  padding: 0 12px;
  border: 1px solid var(--line);
  border-radius: 7px;
  background: #fff;
  color: var(--text-soft);
  font: inherit;
  font-size: 10px;
  cursor: pointer;
}

.public-user {
  display: flex;
  align-items: center;
  gap: 7px;
  color: var(--text-soft);
  font-size: 10px;
}

.public-user > span {
  display: grid;
  width: 28px;
  height: 28px;
  place-items: center;
  border-radius: 50%;
  background: var(--text);
  color: #fff;
  font-size: 9px;
}

.public-user > button {
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

.public-user > button:hover {
  background: #fff;
  color: var(--text);
}

.public-share__layout {
  display: grid;
  width: min(1180px, calc(100% - 48px));
  flex: 1;
  grid-template-columns: minmax(0, 1fr) 340px;
  gap: 28px;
  align-items: start;
  margin: 12px auto 34px;
}

.public-share__main {
  min-width: 0;
}

.public-back {
  display: flex;
  align-items: center;
  gap: 5px;
  padding: 0;
  border: 0;
  background: transparent;
  color: var(--text-quiet);
  font: inherit;
  font-size: 9.5px;
  cursor: pointer;
}

.public-share__intro {
  margin: 25px 0 21px;
}

.public-share__intro > span {
  color: var(--accent-strong);
  font-size: 8px;
  font-weight: 700;
  letter-spacing: 0.14em;
}

.public-share__intro h1 {
  margin: 7px 0 5px;
  font-size: clamp(26px, 3vw, 38px);
  font-weight: 600;
  letter-spacing: 0;
}

.public-share__intro p {
  margin: 0;
  color: var(--text-soft);
  font-size: 10.5px;
}

.public-share__aside {
  position: sticky;
  top: 24px;
  margin-top: 54px;
  padding: 24px;
  border: 1px solid var(--line);
  border-radius: 9px;
  background: #fbfbf9;
}

.shared-file {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.shared-file__icon {
  display: grid;
  width: 38px;
  height: 38px;
  place-items: center;
  border-radius: 8px;
  background: var(--accent-wash);
  color: var(--accent-strong);
}

.shared-file > span:last-child {
  color: var(--text-quiet);
  font-size: 7.5px;
  font-weight: 650;
  letter-spacing: 0.12em;
}

.public-share__aside h2 {
  margin: 18px 0 8px;
  overflow-wrap: anywhere;
  font-size: 18px;
  font-weight: 620;
  line-height: 1.4;
}

.shared-by {
  display: flex;
  align-items: center;
  gap: 7px;
  margin: 0;
  color: var(--text-quiet);
  font-size: 9.5px;
}

.shared-by > span {
  display: grid;
  width: 23px;
  height: 23px;
  place-items: center;
  border-radius: 50%;
  background: #e6e7e2;
  color: var(--text-soft);
  font-size: 8px;
}

.shared-meta {
  display: flex;
  flex-direction: column;
  gap: 10px;
  margin: 20px 0;
  padding: 17px 0;
  border-top: 1px solid var(--line);
  border-bottom: 1px solid var(--line);
}

.shared-meta > div {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.shared-meta dt {
  color: var(--text-quiet);
  font-size: 9px;
}

.shared-meta dd {
  margin: 0;
  color: var(--text-soft);
  font-size: 9px;
  text-align: right;
}

.share-security {
  display: flex;
  gap: 9px;
  padding: 11px;
  border-radius: 7px;
  background: var(--accent-wash);
  color: var(--accent-strong);
}

.share-security > div {
  display: flex;
  flex-direction: column;
}

.share-security strong {
  font-size: 9.5px;
  font-weight: 600;
}

.share-security span {
  margin-top: 2px;
  color: var(--text-soft);
  font-size: 8.5px;
}

.share-download,
.share-copy {
  display: flex;
  width: 100%;
  height: 40px;
  align-items: center;
  justify-content: center;
  gap: 7px;
  margin-top: 12px;
  border-radius: 7px;
  font: inherit;
  font-size: 10.5px;
  cursor: pointer;
}

.share-download {
  border: 1px solid var(--text);
  background: var(--text);
  color: #fff;
  font-weight: 600;
}

.share-download:hover {
  background: #30332f;
}

.share-download:disabled {
  border-color: var(--line);
  background: var(--surface-muted);
  color: var(--text-quiet);
  cursor: not-allowed;
}

.share-copy {
  border: 1px solid var(--line);
  background: #fff;
  color: var(--text-soft);
}

.share-copy:hover {
  background: var(--surface-muted);
}

.share-expiry {
  display: flex;
  align-items: center;
  gap: 5px;
  margin-top: 13px;
  color: var(--text-quiet);
  font-size: 8px;
}

.share-unavailable {
  display: flex;
  min-height: 55vh;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 40px;
  text-align: center;
}

.share-unavailable > span {
  display: grid;
  width: 72px;
  height: 72px;
  place-items: center;
  border: 1px solid var(--line);
  border-radius: 18px;
  background: #fbfbf9;
  color: var(--text-quiet);
}

.share-unavailable h1 {
  margin: 18px 0 7px;
  font-size: 22px;
  font-weight: 600;
}

.share-unavailable p {
  margin: 0 0 20px;
  color: var(--text-quiet);
  font-size: 10.5px;
}

.share-unavailable button {
  display: flex;
  height: 36px;
  align-items: center;
  gap: 6px;
  padding: 0 13px;
  border: 1px solid var(--line);
  border-radius: 7px;
  background: #fff;
  color: var(--text-soft);
  font: inherit;
  font-size: 10px;
  cursor: pointer;
}

.public-share__footer {
  display: flex;
  width: min(100%, 1440px);
  min-height: 52px;
  align-items: center;
  justify-content: space-between;
  margin: 0 auto;
  padding: 0 44px;
  color: var(--text-quiet);
  font-size: 7.5px;
  letter-spacing: 0.08em;
}

.share-code-form {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.share-code-form label {
  color: var(--text-soft);
  font-size: 10px;
  font-weight: 600;
}

.share-code-form input {
  height: 50px;
  padding: 0 15px;
  border: 1px solid var(--line);
  border-radius: 8px;
  outline: 0;
  background: #fff;
  color: var(--text);
  font: inherit;
  font-size: 20px;
  font-weight: 650;
  letter-spacing: 0.22em;
  text-align: center;
  text-transform: uppercase;
}

.share-code-form input:focus {
  border-color: var(--accent-line);
  box-shadow: 0 0 0 3px var(--accent-ring);
}

.share-code-form p {
  margin: 0;
  color: var(--danger);
  font-size: 9px;
}

@media (max-width: 900px) {
  .public-share__header {
    padding: 0 22px;
  }

  .public-share__layout {
    width: min(100% - 32px, 760px);
    grid-template-columns: 1fr;
  }

  .public-share__aside {
    position: static;
    margin-top: 0;
  }

  .public-share__footer {
    padding: 0 22px;
  }
}

@media (max-width: 640px) {
  .public-share__header {
    height: 64px;
    padding: 0 15px;
  }

  .secure-label,
  .public-share__header-actions .public-user {
    display: none;
  }

  .public-share__layout {
    width: calc(100% - 24px);
    gap: 16px;
    margin-top: 8px;
  }

  .public-share__intro {
    margin: 18px 0 16px;
  }

  .public-share__intro h1 {
    font-size: 25px;
  }

  .public-share__aside {
    padding: 18px;
  }

  .public-share__footer {
    padding: 0 15px;
  }
}
</style>
