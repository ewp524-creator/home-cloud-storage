<script setup>
import { computed, ref, watch } from 'vue'
import {
  CalendarClock,
  Check,
  Copy,
  Download,
  Eye,
  KeyRound,
  Link2,
  ShieldCheck,
} from '@lucide/vue'
import { useFileManagerContext } from '../../composables/fileManagerContext'
import FileGlyph from '../files/FileGlyph.vue'
import AppModal from './AppModal.vue'

const props = defineProps({
  open: {
    type: Boolean,
    default: false,
  },
  file: {
    type: Object,
    default: null,
  },
  share: {
    type: Object,
    default: null,
  },
})

const emit = defineEmits(['close', 'created', 'open'])
const manager = useFileManagerContext()

const days = ref(7)
const permission = ref('download')
const requireCode = ref(true)
const accessCode = ref('')
const createdShare = ref(null)
const submitting = ref(false)
const accessCodeValid = computed(
  () => !requireCode.value || /^[A-Z0-9]{4,12}$/.test(accessCode.value.trim()),
)

const baseExpiryOptions = [
  { value: 1, label: '1 天' },
  { value: 3, label: '3 天' },
  { value: 7, label: '7 天' },
  { value: 30, label: '30 天' },
]
const editingShare = computed(() => Boolean(props.share))
const expiryOptions = computed(() => {
  const values = new Set(baseExpiryOptions.map((option) => option.value))
  if (!values.has(days.value)) {
    values.add(days.value)
  }
  return [...values]
    .sort((a, b) => a - b)
    .map(
      (value) =>
        baseExpiryOptions.find((option) => option.value === value) || {
          value,
          label: `${value} 天`,
        },
    )
})

const expiresLabel = computed(() => {
  const date = new Date()
  date.setDate(date.getDate() + days.value)
  return new Intl.DateTimeFormat('zh-CN', {
    month: 'long',
    day: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  }).format(date)
})

function generateCode() {
  const alphabet = '23456789ABCDEFGHJKLMNPQRSTUVWXYZ'
  const bytes = new Uint8Array(6)
  globalThis.crypto.getRandomValues(bytes)
  return Array.from(bytes, (value) => alphabet[value % alphabet.length]).join('')
}

watch(
  () => props.open,
  (open) => {
    if (!open) return
    const remainingDays = props.share?.expiresAt
      ? Math.ceil(
          (new Date(props.share.expiresAt).getTime() - Date.now()) /
            (24 * 60 * 60 * 1000),
        )
      : 7
    days.value = Math.min(Math.max(remainingDays || 7, 1), 30)
    permission.value = props.share?.permission || 'download'
    requireCode.value = props.share
      ? Boolean(props.share.accessCodeConfigured)
      : true
    accessCode.value = props.share?.accessCode || generateCode()
    createdShare.value = null
  },
)

async function submit() {
  if (!props.file) return
  submitting.value = true
  try {
    createdShare.value = await manager.createShare(props.file, {
      days: days.value,
      permission: permission.value,
      accessCode: requireCode.value ? accessCode.value : '',
    })
    emit('created', createdShare.value)
  } finally {
    submitting.value = false
  }
}

function close() {
  appReset()
  emit('close')
}

function appReset() {
  createdShare.value = null
}

function copyCreatedLink() {
  if (!createdShare.value) return
  manager.copyText(createdShare.value.url, '分享链接已复制')
}

function openCreatedShare() {
  const share = createdShare.value
  if (!share) return
  emit('close')
  emit('open', share)
}
</script>

<template>
  <AppModal
    :open="open"
    :title="editingShare ? '修改分享设置' : '创建分享链接'"
    :description="
      editingShare
        ? '更新有效期、提取码和访问权限，链接地址保持不变'
        : '设置访问范围、有效期和提取码'
    "
    size="md"
    @close="close"
  >
    <div v-if="file" class="share-dialog">
      <div class="share-file">
        <FileGlyph :file="file" size="md" />
        <div>
          <strong>{{ file.name }}</strong>
          <span>{{ file.kind === 'folder' ? '文件夹' : '个人文件' }}</span>
        </div>
        <ShieldCheck :size="18" />
      </div>

      <template v-if="!createdShare">
        <div class="share-section">
          <div class="share-section__label">
            <CalendarClock :size="16" />
            <span>有效期</span>
            <small>到期后链接自动失效</small>
          </div>
          <div class="segmented segmented--wide">
            <button
              v-for="option in expiryOptions"
              :key="option.value"
              type="button"
              :class="{ 'is-active': days === option.value }"
              @click="days = option.value"
            >
              {{ option.label }}
            </button>
          </div>
          <p class="share-hint">链接将在 {{ expiresLabel }} 失效</p>
        </div>

        <div class="share-section">
          <div class="share-section__label">
            <KeyRound :size="16" />
            <span>提取码</span>
            <small>访问者需要输入提取码</small>
          </div>
          <div class="share-code-row">
            <label class="switch">
              <input v-model="requireCode" type="checkbox" />
              <span />
            </label>
            <div class="access-code" :class="{ 'is-disabled': !requireCode }">
              <input
                v-model="accessCode"
                type="text"
                maxlength="12"
                :disabled="!requireCode"
                aria-label="分享提取码"
                @input="accessCode = accessCode.toUpperCase()"
              />
              <button
                type="button"
                :disabled="!requireCode"
                @click="accessCode = generateCode()"
              >
                换一个
              </button>
            </div>
          </div>
        </div>

        <div class="share-section">
          <div class="share-section__label">
            <Eye :size="16" />
            <span>访问权限</span>
          </div>
          <div class="permission-grid">
            <button
              type="button"
              :class="{ 'is-active': permission === 'preview' }"
              @click="permission = 'preview'"
            >
              <Eye :size="17" />
              <span>
                <strong>仅在线预览</strong>
                <small>可以查看，但不能下载</small>
              </span>
              <Check v-if="permission === 'preview'" :size="15" />
            </button>
            <button
              type="button"
              :class="{ 'is-active': permission === 'download' }"
              @click="permission = 'download'"
            >
              <Download :size="17" />
              <span>
                <strong>允许下载</strong>
                <small>访问者可以保存原文件</small>
              </span>
              <Check v-if="permission === 'download'" :size="15" />
            </button>
          </div>
        </div>
      </template>

      <div v-else class="share-created">
        <span class="share-created__icon">
          <Link2 :size="24" />
        </span>
        <div>
          <h3>{{ editingShare ? '分享设置已更新' : '分享链接已创建' }}</h3>
          <p>复制链接发送给需要访问的人，提取码为 {{ createdShare.accessCode || '无' }}</p>
        </div>
        <div class="share-created__link">
          <span>{{ createdShare.url }}</span>
          <button class="button button--secondary" type="button" @click="copyCreatedLink">
            <Copy :size="15" />
            复制
          </button>
        </div>
      </div>
    </div>

    <template #footer>
      <template v-if="!createdShare">
        <button class="button button--ghost" type="button" @click="close">取消</button>
        <button
          class="button button--primary"
          type="button"
          :disabled="submitting || !accessCodeValid"
          @click="submit"
        >
          <Link2 :size="15" />
          {{
            submitting
              ? '正在保存...'
              : editingShare
                ? '保存分享设置'
                : '创建分享链接'
          }}
        </button>
      </template>
      <template v-else>
        <button
          class="button button--secondary"
          type="button"
          @click="openCreatedShare"
        >
          打开分享页
        </button>
        <button class="button button--primary" type="button" @click="close">
          完成
        </button>
      </template>
    </template>
  </AppModal>
</template>

<style scoped>
.share-dialog {
  display: flex;
  flex-direction: column;
  gap: 22px;
}

.share-file {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px 14px;
  border: 1px solid var(--line);
  border-radius: 10px;
  background: var(--surface-muted);
}

.share-file > div {
  display: flex;
  min-width: 0;
  flex: 1;
  flex-direction: column;
}

.share-file strong {
  overflow: hidden;
  color: var(--text);
  font-size: 12.5px;
  font-weight: 650;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.share-file span {
  margin-top: 2px;
  color: var(--text-quiet);
  font-size: 10.5px;
}

.share-file > svg {
  color: var(--accent);
}

.share-section {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.share-section__label {
  display: flex;
  align-items: center;
  gap: 7px;
  color: var(--text-soft);
}

.share-section__label span {
  color: var(--text);
  font-size: 12px;
  font-weight: 650;
}

.share-section__label small {
  margin-left: auto;
  color: var(--text-quiet);
  font-size: 9.5px;
}

.share-hint {
  margin: -1px 0 0;
  color: var(--text-quiet);
  font-size: 10px;
}

.share-code-row {
  display: flex;
  align-items: center;
  gap: 13px;
}

.access-code {
  display: flex;
  height: 40px;
  flex: 1;
  align-items: center;
  overflow: hidden;
  border: 1px solid var(--line);
  border-radius: 8px;
  background: #fff;
  transition: opacity 160ms ease;
}

.access-code.is-disabled {
  opacity: 0.48;
}

.access-code input {
  width: 100%;
  min-width: 0;
  padding: 0 12px;
  border: 0;
  outline: 0;
  background: transparent;
  color: var(--text);
  font: inherit;
  font-size: 13px;
  font-weight: 700;
  letter-spacing: 0.12em;
}

.access-code button {
  height: 100%;
  padding: 0 12px;
  border: 0;
  border-left: 1px solid var(--line);
  background: var(--surface-muted);
  color: var(--text-soft);
  font: inherit;
  font-size: 10.5px;
  cursor: pointer;
}

.permission-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 9px;
}

.permission-grid button {
  display: flex;
  min-height: 66px;
  align-items: center;
  gap: 9px;
  padding: 11px;
  border: 1px solid var(--line);
  border-radius: 9px;
  background: #fff;
  color: var(--text-quiet);
  font: inherit;
  text-align: left;
  cursor: pointer;
}

.permission-grid button.is-active {
  border-color: var(--accent);
  background: var(--accent-wash);
  color: var(--accent-strong);
}

.permission-grid button span {
  display: flex;
  min-width: 0;
  flex: 1;
  flex-direction: column;
}

.permission-grid button strong {
  color: var(--text);
  font-size: 11px;
  font-weight: 620;
}

.permission-grid button small {
  margin-top: 2px;
  color: var(--text-quiet);
  font-size: 9.5px;
}

.share-created {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 10px 0;
  text-align: center;
}

.share-created__icon {
  display: grid;
  width: 56px;
  height: 56px;
  place-items: center;
  margin-bottom: 12px;
  border-radius: 50%;
  background: var(--accent-wash);
  color: var(--accent-strong);
}

.share-created h3 {
  margin: 0;
  color: var(--text);
  font-size: 16px;
}

.share-created p {
  margin: 6px 0 16px;
  color: var(--text-quiet);
  font-size: 11px;
}

.share-created__link {
  display: flex;
  width: 100%;
  align-items: center;
  gap: 8px;
  padding: 6px 6px 6px 12px;
  border: 1px solid var(--line);
  border-radius: 9px;
  background: var(--surface-muted);
}

.share-created__link span {
  min-width: 0;
  flex: 1;
  overflow: hidden;
  color: var(--text-soft);
  font-size: 11px;
  text-align: left;
  text-overflow: ellipsis;
  white-space: nowrap;
}

@media (max-width: 640px) {
  .permission-grid {
    grid-template-columns: 1fr;
  }

  .share-section__label small {
    display: none;
  }
}
</style>
