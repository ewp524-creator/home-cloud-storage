<script setup>
import { computed, ref } from 'vue'
import {
  ArrowLeft,
  Ban,
  CalendarClock,
  CheckCircle2,
  Copy,
  Download,
  Eye,
  ExternalLink,
  KeyRound,
  Link2,
  Pencil,
  ShieldCheck,
  Share2,
  Users,
} from '@lucide/vue'
import { useFileManagerContext } from '../../composables/fileManagerContext'
import { formatRemaining } from '../../utils/formatters'
import FileGlyph from '../files/FileGlyph.vue'

const emit = defineEmits([
  'revoke',
  'create-share',
  'open-share',
  'edit-share',
])
const manager = useFileManagerContext()
const {
  activeShares,
  copyText,
  files,
  getShareItemCount,
  navigate,
  searchQuery,
  shares,
  toast,
} = manager

const activeTab = ref('active')
const tabs = [
  { id: 'active', label: '有效分享' },
  { id: 'expired', label: '已失效' },
  { id: 'all', label: '全部记录' },
]

const filteredShares = computed(() => {
  let list = shares.value

  if (activeTab.value === 'active') {
    list = list.filter((share) => share.status === 'active')
  } else if (activeTab.value === 'expired') {
    list = list.filter((share) => share.status === 'expired')
  }

  const query = searchQuery.value.trim().toLowerCase()
  if (query) {
    list = list.filter((share) =>
      share.fileName.toLowerCase().includes(query),
    )
  }

  return list
})

const totalVisits = computed(() =>
  activeShares.value.reduce((total, share) => total + share.visits, 0),
)

const totalDownloads = computed(() =>
  activeShares.value.reduce((total, share) => total + share.downloads, 0),
)

function findFile(share) {
  return files.value.find((file) => file.id === share.fileId)
}

function openFile(share) {
  const file = findFile(share)
  if (!file) {
    toast('原文件已被删除', 'warning')
    return
  }
  if (file.kind === 'folder') manager.openFolder(file)
  else manager.openPreview(file)
}
</script>

<template>
  <div class="share-center">
    <section class="share-hero">
      <div>
        <button class="back-link" type="button" @click="navigate('files')">
          <ArrowLeft :size="14" />
          返回文件库
        </button>
        <span class="page-kicker">SECURE SHARING</span>
        <h1>分享中心</h1>
        <p>一处查看所有外链、提取码、访问量与失效状态。</p>
      </div>
      <button class="button button--primary" type="button" @click="emit('create-share')">
        <Share2 :size="16" />
        创建分享
      </button>
    </section>

    <section class="share-metrics">
      <div>
        <span class="metric-icon is-teal"><Link2 :size="18" /></span>
        <p><strong>{{ activeShares.length }}</strong><span>有效链接</span></p>
      </div>
      <div>
        <span class="metric-icon is-blue"><Eye :size="18" /></span>
        <p><strong>{{ totalVisits }}</strong><span>累计访问</span></p>
      </div>
      <div>
        <span class="metric-icon is-amber"><Download :size="18" /></span>
        <p><strong>{{ totalDownloads }}</strong><span>文件下载</span></p>
      </div>
      <div class="share-metrics__note">
        <ShieldCheck :size="18" />
        <p>
          <strong>分享安全策略已开启</strong>
          <span>默认启用提取码与访问权限校验</span>
        </p>
      </div>
    </section>

    <section class="share-table-panel">
      <header class="share-table-panel__head">
        <div class="filter-tabs">
          <button
            v-for="tab in tabs"
            :key="tab.id"
            type="button"
            :class="{ 'is-active': activeTab === tab.id }"
            @click="activeTab = tab.id"
          >
            {{ tab.label }}
          </button>
        </div>
        <span>{{ filteredShares.length }} 条记录</span>
      </header>

      <div v-if="filteredShares.length" class="share-table">
        <div class="share-table__head">
          <span>分享文件</span>
          <span>访问设置</span>
          <span>有效期</span>
          <span>访问数据</span>
          <span>操作</span>
        </div>

        <div
          v-for="share in filteredShares"
          :key="share.id"
          class="share-table__row"
        >
          <button class="share-file-cell" type="button" @click="openFile(share)">
            <FileGlyph v-if="findFile(share)" :file="findFile(share)" size="sm" />
            <span class="share-file-cell__fallback" v-else>
              <Ban :size="16" />
            </span>
            <span>
              <strong>{{ share.fileName }}</strong>
              <small>
                <Link2 :size="10" />
                {{
                  share.rootType === 'folder'
                    ? `文件夹分享 · 包含 ${getShareItemCount(share)} 个项目`
                    : share.url
                }}
              </small>
            </span>
          </button>

          <div class="share-setting-cell">
            <span>
              <KeyRound :size="13" />
              提取码
              <strong>
                {{
                  share.accessCode ||
                  (share.accessCodeConfigured ? '已设置，需重置' : '无')
                }}
              </strong>
            </span>
            <span>
              <component
                :is="share.permission === 'download' ? Download : Eye"
                :size="13"
              />
              {{ share.permission === 'download' ? '可下载' : '仅预览' }}
            </span>
          </div>

          <div class="share-expiry">
            <span
              class="share-status"
              :class="share.status === 'active' ? 'is-active' : 'is-expired'"
            >
              {{ share.status === 'active' ? '有效' : '已失效' }}
            </span>
            <small>
              <CalendarClock :size="11" />
              {{
                share.status === 'active'
                  ? `剩余 ${formatRemaining(share.expiresAt)}`
                  : '链接不可访问'
              }}
            </small>
          </div>

          <div class="share-visits">
            <span><Eye :size="13" />{{ share.visits }} 次访问</span>
            <span><Download :size="13" />{{ share.downloads }} 次下载</span>
          </div>

          <div class="share-actions">
            <button
              type="button"
              title="修改分享设置"
              aria-label="修改分享设置"
              @click="emit('edit-share', share)"
            >
              <Pencil :size="15" />
            </button>
            <button
              type="button"
              title="打开分享页"
              aria-label="打开分享页"
              @click="emit('open-share', share)"
            >
              <ExternalLink :size="15" />
            </button>
            <button
              type="button"
              title="下载分享内容"
              aria-label="下载分享内容"
              :disabled="share.status !== 'active' || !findFile(share)"
              @click="manager.downloadFiles([findFile(share)])"
            >
              <Download :size="15" />
            </button>
            <button
              type="button"
              title="复制分享链接"
              aria-label="复制分享链接"
              :disabled="share.status !== 'active'"
              @click="copyText(share.url, '分享链接已复制')"
            >
              <Copy :size="15" />
            </button>
            <button
              class="is-danger"
              type="button"
              title="使分享失效"
              aria-label="使分享失效"
              :disabled="share.status !== 'active'"
              @click="emit('revoke', share)"
            >
              <Ban :size="15" />
            </button>
          </div>
        </div>
      </div>

      <div v-else class="share-empty">
        <span><Users :size="28" /></span>
        <h2>当前没有分享记录</h2>
        <p>选择一个文件创建分享链接，访问记录会显示在这里。</p>
        <button class="button button--secondary" type="button" @click="navigate('files')">
          去选择文件
        </button>
      </div>
    </section>

    <section class="share-tip">
      <CheckCircle2 :size="16" />
      <p>
        <strong>访问权限提示</strong>
        <span>仅预览权限不会提供原文件下载地址；撤销分享后，已发出的链接会立即失效。</span>
      </p>
    </section>
  </div>
</template>

<style scoped>
.share-center {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.share-hero {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 24px;
  padding-top: 2px;
}

.back-link {
  display: flex;
  align-items: center;
  gap: 5px;
  margin-bottom: 13px;
  padding: 2px 0;
  border: 0;
  background: transparent;
  color: var(--text-quiet);
  font: inherit;
  font-size: 9.5px;
  cursor: pointer;
}

.back-link:hover {
  color: var(--text-soft);
}

.page-kicker {
  color: var(--accent-strong);
  font-size: 8px;
  font-weight: 750;
  letter-spacing: 0.16em;
}

.share-hero h1 {
  margin: 7px 0 6px;
  color: var(--text);
  font-size: clamp(28px, 2.8vw, 38px);
  font-weight: 600;
  letter-spacing: 0;
}

.share-hero p {
  margin: 0;
  color: var(--text-soft);
  font-size: 12px;
}

.share-metrics {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 0.72fr)) minmax(260px, 1.5fr);
  overflow: hidden;
  border-top: 1px solid var(--line);
  border-bottom: 1px solid var(--line);
  background: transparent;
}

.share-metrics > div {
  display: flex;
  min-width: 0;
  align-items: center;
  gap: 10px;
  padding: 17px 18px;
  border-right: 1px solid var(--line);
}

.share-metrics > div:last-child {
  border-right: 0;
}

.metric-icon {
  display: grid;
  width: 34px;
  height: 34px;
  flex: 0 0 auto;
  place-items: center;
  border-radius: 7px;
}

.metric-icon.is-teal {
  background: var(--accent-wash);
  color: var(--accent-strong);
}

.metric-icon.is-blue {
  background: #eef0f4;
  color: #4f5969;
}

.metric-icon.is-amber {
  background: #f5eee4;
  color: #926a35;
}

.share-metrics p {
  display: flex;
  min-width: 0;
  flex-direction: column;
  margin: 0;
}

.share-metrics p strong {
  color: var(--text);
  font-size: 13px;
  font-weight: 680;
}

.share-metrics p span {
  margin-top: 1px;
  color: var(--text-quiet);
  font-size: 9px;
}

.share-metrics__note {
  background: transparent;
  color: var(--accent-strong);
}

.share-table-panel {
  overflow: hidden;
  border: 1px solid var(--line);
  border-radius: 8px;
  background: #fbfbf9;
}

.share-table-panel__head {
  display: flex;
  min-height: 58px;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 10px 15px;
  border-bottom: 1px solid var(--line);
}

.share-table-panel__head > span {
  color: var(--text-quiet);
  font-size: 9px;
}

.filter-tabs {
  display: flex;
  align-items: center;
  gap: 2px;
}

.filter-tabs button {
  height: 30px;
  padding: 0 10px;
  border: 0;
  border-radius: 0;
  background: transparent;
  color: var(--text-quiet);
  font: inherit;
  font-size: 9.5px;
  cursor: pointer;
}

.filter-tabs button:hover {
  background: transparent;
}

.filter-tabs button.is-active {
  background: transparent;
  color: var(--text);
  font-weight: 600;
  box-shadow: inset 0 -2px 0 var(--text);
}

.share-table__head,
.share-table__row {
  display: grid;
  grid-template-columns: minmax(260px, 1.7fr) minmax(150px, 0.95fr) minmax(130px, 0.77fr) minmax(105px, 0.62fr) 138px;
  align-items: center;
  gap: 16px;
  padding: 0 16px;
}

.share-table__head {
  min-height: 38px;
  background: #f5f4ef;
  color: var(--text-quiet);
  font-size: 9.5px;
  font-weight: 600;
}

.share-table__row {
  min-height: 70px;
  border-top: 1px solid var(--line);
}

.share-file-cell {
  display: flex;
  min-width: 0;
  align-items: center;
  gap: 10px;
  padding: 0;
  border: 0;
  background: transparent;
  text-align: left;
  cursor: pointer;
}

.share-file-cell > span:last-child {
  display: flex;
  min-width: 0;
  flex-direction: column;
}

.share-file-cell strong {
  overflow: hidden;
  color: var(--text);
  font-size: 11.5px;
  font-weight: 600;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.share-file-cell small {
  display: flex;
  align-items: center;
  gap: 3px;
  max-width: 240px;
  margin-top: 3px;
  overflow: hidden;
  color: var(--text-quiet);
  font-size: 9px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.share-file-cell__fallback {
  display: grid;
  width: 34px;
  height: 34px;
  place-items: center;
  border-radius: 8px;
  background: var(--surface-muted);
  color: var(--text-quiet);
}

.share-setting-cell,
.share-expiry,
.share-visits {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.share-setting-cell span,
.share-visits span {
  display: flex;
  align-items: center;
  gap: 4px;
  color: var(--text-quiet);
  font-size: 9.5px;
}

.share-setting-cell strong {
  color: var(--text-soft);
  font-weight: 650;
  letter-spacing: 0.05em;
}

.share-expiry small {
  display: flex;
  align-items: center;
  gap: 3px;
  color: var(--text-quiet);
  font-size: 9px;
}

.share-status {
  align-self: flex-start;
  padding: 2px 6px;
  border-radius: 4px;
  font-size: 8.5px;
  font-weight: 650;
}

.share-status.is-active {
  background: var(--accent-wash);
  color: var(--accent-strong);
}

.share-status.is-expired {
  background: var(--surface-muted);
  color: var(--text-quiet);
}

.share-actions {
  display: flex;
  justify-content: flex-end;
  gap: 3px;
}

.share-actions button {
  display: grid;
  width: 30px;
  height: 30px;
  place-items: center;
  padding: 0;
  border: 0;
  border-radius: 6px;
  background: transparent;
  color: var(--text-quiet);
  cursor: pointer;
}

.share-actions button:hover:not(:disabled) {
  background: var(--surface-muted);
  color: var(--text);
}

.share-actions button.is-danger:hover:not(:disabled) {
  background: var(--danger-wash);
  color: var(--danger);
}

.share-actions button:disabled {
  cursor: not-allowed;
  opacity: 0.28;
}

.share-empty {
  display: flex;
  min-height: 300px;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 32px;
  text-align: center;
}

.share-empty > span {
  display: grid;
  width: 64px;
  height: 64px;
  place-items: center;
  margin-bottom: 14px;
  border-radius: 18px;
  background: var(--accent-wash);
  color: var(--accent-strong);
}

.share-empty h2 {
  margin: 0;
  color: var(--text);
  font-size: 16px;
}

.share-empty p {
  margin: 7px 0 18px;
  color: var(--text-quiet);
  font-size: 10px;
}

.share-tip {
  display: flex;
  align-items: flex-start;
  gap: 9px;
  padding: 12px 14px;
  border: 1px solid var(--accent-line);
  border-radius: 8px;
  background: var(--accent-wash);
  color: var(--accent-strong);
}

.share-tip p {
  display: flex;
  flex-direction: column;
  margin: 0;
}

.share-tip strong {
  font-size: 9.5px;
  font-weight: 650;
}

.share-tip span {
  margin-top: 2px;
  color: var(--text-soft);
  font-size: 8.5px;
}

@media (max-width: 1050px) {
  .share-metrics {
    grid-template-columns: repeat(3, 1fr);
  }

  .share-metrics__note {
    grid-column: 1 / -1;
    border-top: 1px solid var(--line);
  }

  .share-metrics > div:nth-child(3) {
    border-right: 0;
  }

  .share-table__head {
    display: none;
  }

  .share-table__row {
    grid-template-columns: minmax(220px, 1.5fr) minmax(140px, 0.9fr) 138px;
  }

  .share-expiry,
  .share-visits {
    display: none;
  }
}

@media (max-width: 700px) {
  .share-hero {
    align-items: flex-start;
    flex-direction: column;
  }

  .share-metrics {
    grid-template-columns: repeat(3, 1fr);
  }

  .share-metrics > div {
    flex-direction: column;
    align-items: flex-start;
    padding: 11px;
  }

  .share-metrics__note {
    flex-direction: row !important;
  }

  .share-table__row {
    grid-template-columns: minmax(0, 1fr) 138px;
    min-height: 74px;
    gap: 8px;
    padding: 0 12px;
  }

  .share-setting-cell {
    display: none;
  }

  .share-file-cell small {
    max-width: 190px;
  }
}
</style>
