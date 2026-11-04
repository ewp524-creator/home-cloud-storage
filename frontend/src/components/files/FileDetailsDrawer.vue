<script setup>
import { computed, onBeforeUnmount, onMounted } from 'vue'
import {
  CalendarDays,
  CheckCircle2,
  Copy,
  Download,
  Eye,
  FolderOpen,
  History,
  Link2,
  LockKeyhole,
  Pencil,
  Share2,
  ShieldCheck,
  Tag,
  Trash2,
  UserRound,
  X,
} from '@lucide/vue'
import { useFileManagerContext } from '../../composables/fileManagerContext'
import { getFileFormat } from '../../utils/fileFormats'
import { formatBytes, formatDate } from '../../utils/formatters'
import FileGlyph from './FileGlyph.vue'

const emit = defineEmits(['share', 'rename'])
const manager = useFileManagerContext()
const {
  breadcrumbs,
  closeDetails,
  copyText,
  detailsFile,
  downloadFiles,
  getActiveShareForFile,
  openFolder,
  openPreview,
} = manager

const format = computed(() =>
  detailsFile.value ? getFileFormat(detailsFile.value) : null,
)

const activeShare = computed(() =>
  detailsFile.value ? getActiveShareForFile(detailsFile.value.id) : null,
)

const location = computed(() => {
  const trail = breadcrumbs.value.map((item) => item.name)
  return ['我的文件', ...trail].join(' / ')
})

function preview() {
  if (!detailsFile.value) return
  if (detailsFile.value.kind === 'folder') {
    openFolder(detailsFile.value)
    closeDetails()
    return
  }
  openPreview(detailsFile.value)
}

function download() {
  if (!detailsFile.value) return
  downloadFiles([detailsFile.value])
}

function deleteFile() {
  if (!detailsFile.value) return
  manager.moveToTrash([detailsFile.value.id])
}

function onKeydown(event) {
  if (detailsFile.value && event.key === 'Escape') closeDetails()
}

onMounted(() => document.addEventListener('keydown', onKeydown))
onBeforeUnmount(() => document.removeEventListener('keydown', onKeydown))
</script>

<template>
  <Teleport to="body">
    <Transition name="drawer">
      <div v-if="detailsFile" class="details-layer">
        <button
          class="details-layer__backdrop"
          type="button"
          aria-label="关闭详情"
          @click="closeDetails"
        />
        <aside class="details-drawer" aria-label="文件详细信息">
          <header class="details-drawer__head">
            <div>
              <span>详细信息</span>
              <small>FILE INSIGHTS</small>
            </div>
            <button
              class="icon-button"
              type="button"
              title="关闭详情"
              aria-label="关闭详情"
              @click="closeDetails"
            >
              <X :size="18" />
            </button>
          </header>

          <div class="details-drawer__scroll">
            <section class="details-identity">
              <FileGlyph :file="detailsFile" size="xl" />
              <div>
                <span
                  class="format-chip"
                  :style="{
                    color: format.color,
                    background: format.background,
                  }"
                >
                  {{ format.label }}
                </span>
                <h2 :title="detailsFile.name">{{ detailsFile.name }}</h2>
                <p>
                  {{ detailsFile.kind === 'folder' ? `${detailsFile.itemCount || 0} 个项目` : formatBytes(detailsFile.size) }}
                  · {{ formatDate(detailsFile.modifiedAt) }}
                </p>
              </div>
            </section>

            <div class="details-actions">
              <button type="button" @click="preview">
                <Eye :size="17" />
                <span>{{ detailsFile.kind === 'folder' ? '打开' : '预览' }}</span>
              </button>
              <button type="button" @click="emit('share', detailsFile)">
                <Share2 :size="17" />
                <span>分享</span>
              </button>
              <button type="button" @click="download">
                <Download :size="17" />
                <span>{{ detailsFile.kind === 'folder' ? '下载 ZIP' : '下载' }}</span>
              </button>
              <button type="button" @click="emit('rename', detailsFile)">
                <Pencil :size="17" />
                <span>重命名</span>
              </button>
            </div>

            <section class="details-section">
              <h3>文件信息</h3>
              <dl class="detail-list">
                <div>
                  <dt><UserRound :size="14" />所有者</dt>
                  <dd>{{ detailsFile.owner }}</dd>
                </div>
                <div>
                  <dt><CalendarDays :size="14" />最近修改</dt>
                  <dd>{{ formatDate(detailsFile.modifiedAt, 'full') }}</dd>
                </div>
                <div>
                  <dt><FolderOpen :size="14" />所在位置</dt>
                  <dd :title="location">{{ location }}</dd>
                </div>
                <div>
                  <dt><History :size="14" />版本</dt>
                  <dd>{{ detailsFile.kind === 'folder' ? '自动同步' : '第 3 版' }}</dd>
                </div>
                <div v-if="detailsFile.tags?.length">
                  <dt><Tag :size="14" />标签</dt>
                  <dd class="detail-tags">
                    <span
                      v-for="tag in detailsFile.tags"
                      :key="tag"
                    >
                      {{ tag }}
                    </span>
                  </dd>
                </div>
              </dl>
            </section>

            <section class="details-section">
              <div class="details-section__head">
                <h3>分享与权限</h3>
                <span class="status-badge" :class="{ 'is-shared': activeShare }">
                  <Link2 v-if="activeShare" :size="12" />
                  <LockKeyhole v-else :size="12" />
                  {{ activeShare ? '已分享' : '仅自己可见' }}
                </span>
              </div>

              <div v-if="activeShare" class="active-share">
                <div>
                  <span>提取码</span>
                  <strong>{{ activeShare.accessCode || '无提取码' }}</strong>
                </div>
                <div>
                  <span>访问次数</span>
                  <strong>{{ activeShare.visits }}</strong>
                </div>
                <button
                  class="button button--secondary"
                  type="button"
                  @click="copyText(activeShare.url, '分享链接已复制')"
                >
                  <Copy :size="14" />
                  复制链接
                </button>
              </div>
              <button v-else class="share-prompt" type="button" @click="emit('share', detailsFile)">
                <span>
                  <ShieldCheck :size="18" />
                  <strong>生成安全的分享链接</strong>
                  <small>支持提取码、有效期与访问权限</small>
                </span>
                <span>创建</span>
              </button>
            </section>

            <section class="storage-note">
              <CheckCircle2 :size="16" />
              <div>
                <strong>已安全存储</strong>
                <span>开启对象存储校验，文件内容完整可用</span>
              </div>
            </section>
          </div>

          <footer class="details-drawer__footer">
            <button type="button" @click="deleteFile">
              <Trash2 :size="16" />
              移到回收站
            </button>
          </footer>
        </aside>
      </div>
    </Transition>
  </Teleport>
</template>

<style scoped>
.details-layer {
  position: fixed;
  z-index: 900;
  inset: 0;
}

.details-layer__backdrop {
  position: absolute;
  inset: 0;
  padding: 0;
  border: 0;
  background: rgb(23 25 23 / 16%);
  backdrop-filter: blur(2px);
}

.details-drawer {
  position: absolute;
  top: 0;
  right: 0;
  bottom: 0;
  display: flex;
  width: min(390px, calc(100vw - 24px));
  flex-direction: column;
  border-left: 1px solid var(--line);
  background: #fff;
  box-shadow: -24px 0 70px rgb(13 34 27 / 16%);
}

.details-drawer__head {
  display: flex;
  height: 70px;
  flex: 0 0 auto;
  align-items: center;
  justify-content: space-between;
  padding: 0 20px;
  border-bottom: 1px solid var(--line);
}

.details-drawer__head > div {
  display: flex;
  flex-direction: column;
}

.details-drawer__head span {
  color: var(--text);
  font-size: 12.5px;
  font-weight: 660;
}

.details-drawer__head small {
  margin-top: 2px;
  color: var(--text-quiet);
  font-size: 8px;
  font-weight: 700;
  letter-spacing: 0.14em;
}

.details-drawer__scroll {
  flex: 1;
  overflow: auto;
  padding: 22px 20px;
}

.details-identity {
  display: flex;
  align-items: center;
  gap: 16px;
}

.details-identity > div {
  min-width: 0;
}

.format-chip {
  display: inline-block;
  padding: 3px 7px;
  border-radius: 5px;
  font-size: 9px;
  font-weight: 700;
}

.details-identity h2 {
  display: -webkit-box;
  margin: 7px 0 3px;
  overflow: hidden;
  color: var(--text);
  font-size: 16px;
  font-weight: 680;
  line-height: 1.4;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
}

.details-identity p {
  margin: 0;
  color: var(--text-quiet);
  font-size: 10.5px;
}

.details-actions {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 6px;
  margin: 22px 0 24px;
}

.details-actions button {
  display: flex;
  height: 58px;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 5px;
  padding: 0;
  border: 1px solid var(--line);
  border-radius: 9px;
  background: #fff;
  color: var(--text-soft);
  font: inherit;
  font-size: 9.5px;
  cursor: pointer;
}

.details-actions button:hover {
  border-color: var(--accent-line);
  background: var(--accent-wash);
  color: var(--accent-strong);
}

.details-section {
  padding: 18px 0;
  border-top: 1px solid var(--line);
}

.details-section h3 {
  margin: 0 0 14px;
  color: var(--text);
  font-size: 11.5px;
  font-weight: 660;
}

.details-section__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 13px;
}

.details-section__head h3 {
  margin: 0;
}

.status-badge {
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 3px 7px;
  border-radius: 5px;
  background: var(--surface-muted);
  color: var(--text-quiet);
  font-size: 8.5px;
  font-weight: 600;
}

.status-badge.is-shared {
  background: var(--accent-wash);
  color: var(--accent-strong);
}

.detail-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
  margin: 0;
}

.detail-list > div {
  display: grid;
  grid-template-columns: 104px minmax(0, 1fr);
  gap: 10px;
  align-items: center;
}

.detail-list dt {
  display: flex;
  align-items: center;
  gap: 6px;
  color: var(--text-quiet);
  font-size: 10px;
}

.detail-list dd {
  min-width: 0;
  margin: 0;
  overflow: hidden;
  color: var(--text-soft);
  font-size: 10.5px;
  text-align: right;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.detail-tags {
  display: flex;
  justify-content: flex-end;
  gap: 5px;
}

.detail-tags span {
  padding: 3px 6px;
  border-radius: 4px;
  background: var(--surface-muted);
  color: var(--text-soft);
}

.active-share {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
  padding: 13px;
  border: 1px solid var(--line);
  border-radius: 9px;
  background: var(--surface-muted);
}

.active-share > div {
  display: flex;
  flex-direction: column;
}

.active-share span {
  color: var(--text-quiet);
  font-size: 9px;
}

.active-share strong {
  margin-top: 3px;
  color: var(--text);
  font-size: 11px;
  font-weight: 650;
}

.active-share .button {
  grid-column: 1 / -1;
  justify-self: start;
  height: 31px;
}

.share-prompt {
  display: flex;
  width: 100%;
  align-items: center;
  gap: 10px;
  padding: 12px;
  border: 1px solid var(--line);
  border-radius: 9px;
  background: #fff;
  color: var(--text-soft);
  font: inherit;
  text-align: left;
  cursor: pointer;
}

.share-prompt:hover {
  border-color: var(--accent-line);
  background: var(--accent-wash);
}

.share-prompt > span:first-child {
  display: grid;
  grid-template-columns: 22px 1fr;
  flex: 1;
  column-gap: 7px;
}

.share-prompt svg {
  grid-row: span 2;
  color: var(--accent);
}

.share-prompt strong {
  color: var(--text);
  font-size: 10.5px;
  font-weight: 620;
}

.share-prompt small {
  margin-top: 2px;
  color: var(--text-quiet);
  font-size: 9px;
}

.share-prompt > span:last-child {
  color: var(--accent-strong);
  font-size: 10px;
  font-weight: 650;
}

.storage-note {
  display: flex;
  gap: 9px;
  margin-top: 2px;
  padding: 12px;
  border-radius: 9px;
  background: var(--accent-wash);
  color: var(--accent-strong);
}

.storage-note > div {
  display: flex;
  flex-direction: column;
}

.storage-note strong {
  font-size: 10px;
  font-weight: 650;
}

.storage-note span {
  margin-top: 2px;
  color: var(--text-soft);
  font-size: 8.5px;
  line-height: 1.45;
}

.details-drawer__footer {
  flex: 0 0 auto;
  padding: 12px 20px;
  border-top: 1px solid var(--line);
}

.details-drawer__footer button {
  display: flex;
  align-items: center;
  gap: 7px;
  padding: 7px 8px;
  border: 0;
  border-radius: 6px;
  background: transparent;
  color: var(--danger);
  font: inherit;
  font-size: 10px;
  cursor: pointer;
}

.details-drawer__footer button:hover {
  background: var(--danger-wash);
}

.drawer-enter-active,
.drawer-leave-active {
  transition: opacity 200ms ease;
}

.drawer-enter-active .details-drawer,
.drawer-leave-active .details-drawer {
  transition: transform 220ms ease;
}

.drawer-enter-from,
.drawer-leave-to {
  opacity: 0;
}

.drawer-enter-from .details-drawer,
.drawer-leave-to .details-drawer {
  transform: translateX(100%);
}
</style>
