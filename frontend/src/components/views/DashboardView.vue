<script setup>
import { computed } from 'vue'
import {
  ArrowLeft,
  ArrowRight,
  ChevronRight,
  Clock3,
  Copy,
  Download,
  FolderPlus,
  Grid2X2,
  HardDrive,
  Home,
  Link2,
  List,
  Share2,
  Trash2,
  UploadCloud,
  Users,
  X,
} from '@lucide/vue'
import { useFileManagerContext } from '../../composables/fileManagerContext'
import { formatBytes, formatDate, formatRemaining } from '../../utils/formatters'
import FileBrowser from '../files/FileBrowser.vue'
import FileGlyph from '../files/FileGlyph.vue'

const emit = defineEmits([
  'upload',
  'new-folder',
  'share',
  'cancel-share',
  'rename',
])
const manager = useFileManagerContext()
const {
  activeShares,
  activities,
  breadcrumbs,
  clearSelection,
  copyText,
  currentFolder,
  currentFolderId,
  currentView,
  fileCount,
  files,
  folderCount,
  goToBreadcrumb,
  hasMoreFiles,
  navigate,
  rootFolderId,
  searchQuery,
  selectedFiles,
  selectedIds,
  setViewMode,
  sortBy,
  subtitle,
  title,
  toast,
  totalStorage,
  typeFilter,
  user,
  viewMode,
  visibleFiles,
} = manager

const filters = [
  { id: 'all', label: '全部' },
  { id: 'folder', label: '文件夹' },
  { id: 'document', label: '文档' },
  { id: 'media', label: '媒体' },
  { id: 'code', label: '代码' },
]

const showInsights = computed(
  () => currentView.value === 'files' && !currentFolderId.value,
)

const recentShares = computed(() =>
  activeShares.value.slice(0, 3).map((share) => ({
    ...share,
    file: files.value.find((file) => file.id === share.fileId),
  })),
)

const isFolderView = computed(
  () => currentView.value === 'files' && currentFolderId.value,
)

const resultHint = computed(() => {
  if (searchQuery.value) {
    return `找到 ${visibleFiles.value.length} 个与“${searchQuery.value}”相关的结果`
  }
  if (hasMoreFiles.value) {
    return `已加载 ${visibleFiles.value.length} 个项目，可继续加载`
  }
  return `${visibleFiles.value.length} 个项目`
})

function bulkDownload() {
  manager.downloadFiles(selectedFiles.value)
}

function bulkShare() {
  if (selectedFiles.value.length !== 1) {
    toast('请选择一个项目后创建分享链接', 'info')
    return
  }
  emit('share', selectedFiles.value[0])
}

function bulkDelete() {
  manager.moveToTrash(selectedIds.value)
}

function goToParentFolder() {
  const parentId = currentFolder.value?.parentId || null
  goToBreadcrumb(parentId === rootFolderId.value ? null : parentId)
}
</script>

<template>
  <div class="dashboard-view">
    <section class="page-intro">
      <div class="page-intro__copy">
        <div class="breadcrumb-row">
          <button
            v-if="isFolderView"
            class="breadcrumb-back"
            type="button"
            title="回到上一级"
            aria-label="回到上一级"
            @click="goToParentFolder"
          >
            <ArrowLeft :size="15" />
            上一级
          </button>
          <nav class="breadcrumbs" aria-label="当前位置">
            <button type="button" @click="goToBreadcrumb(null)">
              <Home :size="14" />
              文件库
            </button>
            <template v-if="currentView === 'files'">
              <template v-for="item in breadcrumbs" :key="item.id">
                <ChevronRight :size="14" />
                <button type="button" @click="goToBreadcrumb(item.id)">
                  {{ item.name }}
                </button>
              </template>
            </template>
            <template v-else>
              <ChevronRight :size="14" />
              <span>{{ title }}</span>
            </template>
          </nav>
        </div>
        <div class="page-title-row">
          <div>
            <span class="page-kicker">
              {{ isFolderView ? 'FOLDER VIEW' : 'YOUR WORKSPACE' }}
            </span>
            <h1>{{ title }}</h1>
            <p>{{ subtitle }}</p>
          </div>
          <div class="page-actions">
            <button class="button button--secondary" type="button" @click="emit('new-folder')">
              <FolderPlus :size="16" />
              新建文件夹
            </button>
            <button class="button button--primary" type="button" @click="emit('upload')">
              <UploadCloud :size="16" />
              上传文件
            </button>
          </div>
        </div>
      </div>
    </section>

    <section v-if="!currentFolder && currentView !== 'tagged'" class="summary-strip">
      <div class="summary-item">
        <span class="summary-item__icon summary-item__icon--teal">
          <HardDrive :size="17" />
        </span>
        <div>
          <strong>{{ formatBytes(Math.max(totalStorage, user.storageUsed)) }}</strong>
          <span>已使用空间</span>
        </div>
      </div>
      <div class="summary-item">
        <span class="summary-item__icon summary-item__icon--blue">
          <Grid2X2 :size="17" />
        </span>
        <div>
          <strong>{{ fileCount }} 个文件</strong>
          <span>{{ folderCount }} 个文件夹</span>
        </div>
      </div>
      <div class="summary-item">
        <span class="summary-item__icon summary-item__icon--amber">
          <Users :size="17" />
        </span>
        <div>
          <strong>{{ activeShares.length }} 个分享</strong>
          <span>今日访问 35 次</span>
        </div>
      </div>
      <div class="summary-item summary-item--security">
        <span class="security-pulse" />
        <div>
          <strong>所有文件已同步</strong>
          <span>MinIO 服务运行正常</span>
        </div>
      </div>
    </section>

    <section class="browser-section">
      <div class="browser-toolbar">
        <div class="filter-tabs">
          <button
            v-for="filter in filters"
            :key="filter.id"
            type="button"
            :class="{ 'is-active': typeFilter === filter.id }"
            @click="typeFilter = filter.id"
          >
            {{ filter.label }}
          </button>
        </div>

        <div class="browser-toolbar__right">
          <span class="result-hint">{{ resultHint }}</span>
          <label class="sort-select">
            <Clock3 :size="14" />
            <select v-model="sortBy" aria-label="排序方式">
              <option value="modified-desc">最近修改</option>
              <option value="modified-asc">最早修改</option>
              <option value="name-asc">名称 A-Z</option>
              <option value="size-desc">文件大小</option>
            </select>
          </label>
          <div class="segmented view-switch">
            <button
              type="button"
              :class="{ 'is-active': viewMode === 'grid' }"
              title="网格视图"
              aria-label="网格视图"
              @click="setViewMode('grid')"
            >
              <Grid2X2 :size="15" />
            </button>
            <button
              type="button"
              :class="{ 'is-active': viewMode === 'list' }"
              title="列表视图"
              aria-label="列表视图"
              @click="setViewMode('list')"
            >
              <List :size="16" />
            </button>
          </div>
        </div>
      </div>

      <Transition name="selection">
        <div v-if="selectedIds.length" class="selection-bar">
          <div>
            <span class="selection-bar__check">{{ selectedIds.length }}</span>
            <strong>已选择 {{ selectedIds.length }} 个项目</strong>
          </div>
          <div class="selection-bar__actions">
            <button type="button" @click="bulkDownload">
              <Download :size="15" />
              下载
            </button>
            <button type="button" @click="bulkShare">
              <Share2 :size="15" />
              分享
            </button>
            <button class="is-danger" type="button" @click="bulkDelete">
              <Trash2 :size="15" />
              删除
            </button>
            <button
              class="selection-bar__close"
              type="button"
              title="取消选择"
              aria-label="取消选择"
              @click="clearSelection"
            >
              <X :size="16" />
            </button>
          </div>
        </div>
      </Transition>

      <FileBrowser
        :files="visibleFiles"
        :selected-ids="selectedIds"
        :view-mode="viewMode"
        :current-view="currentView"
        @upload="emit('upload')"
        @new-folder="emit('new-folder')"
        @share="emit('share', $event)"
        @cancel-share="emit('cancel-share', $event)"
        @rename="emit('rename', $event)"
      />
    </section>

    <section v-if="showInsights" class="insight-grid">
      <div class="insight-panel">
        <header class="insight-panel__head">
          <div>
            <span>ACTIVE LINKS</span>
            <h2>正在分享</h2>
          </div>
          <button type="button" @click="navigate('shared')">
            查看全部
            <ArrowRight :size="14" />
          </button>
        </header>
        <div class="share-list-mini">
          <div v-for="share in recentShares" :key="share.id" class="share-mini-row">
            <FileGlyph v-if="share.file" :file="share.file" size="sm" />
            <div class="share-mini-row__name">
              <strong>{{ share.fileName }}</strong>
              <span>
                <Link2 :size="11" />
                提取码 {{ share.accessCode }}
              </span>
            </div>
            <div class="share-mini-row__time">
              <span>剩余</span>
              <strong>{{ formatRemaining(share.expiresAt) }}</strong>
            </div>
            <button
              type="button"
              title="复制分享链接"
              aria-label="复制分享链接"
              @click="copyText(share.url, '分享链接已复制')"
            >
              <Copy :size="15" />
            </button>
          </div>
          <div v-if="!recentShares.length" class="insight-empty">
            暂无有效分享链接
          </div>
        </div>
      </div>

      <div class="insight-panel insight-panel--activity">
        <header class="insight-panel__head">
          <div>
            <span>RECENT ACTIVITY</span>
            <h2>最近动态</h2>
          </div>
        </header>
        <div class="activity-list">
          <div v-for="activity in activities.slice(0, 4)" :key="activity.id" class="activity-row">
            <span class="activity-row__dot" :class="`is-${activity.color}`" />
            <div>
              <p>
                {{ activity.action }}
                <strong>{{ activity.target }}</strong>
              </p>
              <span>{{ formatDate(activity.time) }}</span>
            </div>
          </div>
        </div>
      </div>
    </section>
  </div>
</template>

<style scoped>
.dashboard-view {
  display: flex;
  flex-direction: column;
  gap: 28px;
}

.page-intro {
  padding-top: 4px;
}

.breadcrumb-row {
  display: flex;
  min-width: 0;
  align-items: center;
  gap: 12px;
}

.breadcrumb-back {
  display: inline-flex;
  height: 30px;
  flex: 0 0 auto;
  align-items: center;
  gap: 6px;
  padding: 0 10px;
  border: 1px solid var(--line);
  border-radius: 6px;
  background: var(--surface);
  color: var(--text-soft);
  font: inherit;
  font-size: 12px;
  font-weight: 620;
  cursor: pointer;
  transition:
    border-color 140ms ease,
    background 140ms ease,
    color 140ms ease;
}

.breadcrumb-back:hover {
  border-color: var(--line-strong);
  background: var(--surface-muted);
  color: var(--text);
}

.breadcrumb-back:focus-visible {
  outline: 2px solid var(--accent-wash);
  outline-offset: 2px;
}

.breadcrumbs {
  display: flex;
  min-width: 0;
  height: 30px;
  align-items: center;
  gap: 6px;
  overflow-x: auto;
  color: var(--text-quiet);
  font-size: 13px;
  scrollbar-width: none;
}

.breadcrumbs::-webkit-scrollbar {
  display: none;
}

.breadcrumbs button {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  max-width: 240px;
  padding: 4px 6px;
  overflow: hidden;
  border: 0;
  border-radius: 5px;
  background: transparent;
  color: var(--text-quiet);
  font: inherit;
  text-overflow: ellipsis;
  white-space: nowrap;
  cursor: pointer;
}

.breadcrumbs button:hover {
  background: var(--surface-muted);
  color: var(--text-soft);
}

.breadcrumbs > svg {
  flex: 0 0 auto;
}

.breadcrumbs > span {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.page-title-row {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 24px;
  margin-top: 12px;
}

.page-kicker {
  color: var(--accent-strong);
  font-size: 8px;
  font-weight: 700;
  letter-spacing: 0.13em;
}

.page-title-row h1 {
  margin: 7px 0 6px;
  color: var(--text);
  font-size: clamp(28px, 2.8vw, 38px);
  font-weight: 600;
  letter-spacing: 0;
  line-height: 1.16;
}

.page-title-row p {
  margin: 0;
  color: var(--text-soft);
  font-size: 12px;
  line-height: 1.55;
}

.page-actions {
  display: flex;
  flex: 0 0 auto;
  align-items: center;
  gap: 8px;
}

.summary-strip {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  margin-top: 2px;
  overflow: hidden;
  border-top: 1px solid var(--line);
  border-bottom: 1px solid var(--line);
  background: transparent;
}

.summary-item {
  display: flex;
  min-width: 0;
  align-items: center;
  gap: 10px;
  padding: 17px 18px;
  border-right: 1px solid var(--line);
}

.summary-item:last-child {
  border-right: 0;
}

.summary-item__icon {
  display: grid;
  width: 32px;
  height: 32px;
  flex: 0 0 auto;
  place-items: center;
  border-radius: 7px;
}

.summary-item__icon--teal {
  background: var(--accent-wash);
  color: var(--accent-strong);
}

.summary-item__icon--blue {
  background: #eef0f4;
  color: #4f5969;
}

.summary-item__icon--amber {
  background: #f5eee4;
  color: #926a35;
}

.summary-item > div {
  display: flex;
  min-width: 0;
  flex-direction: column;
}

.summary-item strong {
  overflow: hidden;
  color: var(--text);
  font-size: 11.5px;
  font-weight: 600;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.summary-item span {
  margin-top: 2px;
  overflow: hidden;
  color: var(--text-quiet);
  font-size: 9.5px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.summary-item--security {
  background: transparent;
}

.security-pulse {
  width: 8px;
  height: 8px;
  margin: 0 9px 0 4px;
  border-radius: 50%;
  background: var(--accent);
  box-shadow: 0 0 0 4px var(--accent-ring);
}

.browser-section {
  min-width: 0;
}

.browser-toolbar {
  display: flex;
  min-height: 42px;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 16px;
}

.filter-tabs {
  display: flex;
  align-items: center;
  gap: 2px;
}

.filter-tabs button {
  height: 31px;
  padding: 0 11px;
  border: 0;
  border-radius: 0;
  background: transparent;
  color: var(--text-quiet);
  font: inherit;
  font-size: 11px;
  cursor: pointer;
}

.filter-tabs button:hover {
  background: transparent;
  color: var(--text-soft);
}

.filter-tabs button.is-active {
  background: transparent;
  color: var(--text);
  font-weight: 600;
  box-shadow: inset 0 -2px 0 var(--text);
}

.browser-toolbar__right {
  display: flex;
  align-items: center;
  gap: 8px;
}

.result-hint {
  max-width: 240px;
  overflow: hidden;
  color: var(--text-quiet);
  font-size: 10px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.sort-select {
  display: flex;
  height: 31px;
  align-items: center;
  gap: 5px;
  padding: 0 7px 0 9px;
  border: 1px solid transparent;
  border-radius: 6px;
  background: transparent;
  color: var(--text-quiet);
}

.sort-select:hover {
  border-color: var(--line);
  background: #fff;
}

.sort-select select {
  padding: 0 1px;
  border: 0;
  outline: 0;
  background: transparent;
  color: var(--text-soft);
  font: inherit;
  font-size: 10px;
  cursor: pointer;
}

.view-switch {
  height: 31px;
  padding: 2px;
  border: 1px solid var(--line);
  border-radius: 6px;
  background: #fff;
}

.view-switch button {
  width: 28px;
  height: 25px;
}

.selection-bar {
  display: flex;
  min-height: 48px;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 10px;
  padding: 7px 9px 7px 13px;
  border: 1px solid var(--accent-line);
  border-radius: 7px;
  background: var(--accent-wash);
}

.selection-bar > div {
  display: flex;
  align-items: center;
  gap: 8px;
}

.selection-bar__check {
  display: grid;
  width: 23px;
  height: 23px;
  place-items: center;
  border-radius: 6px;
  background: var(--accent);
  color: #fff;
  font-size: 9px;
  font-weight: 700;
}

.selection-bar strong {
  color: var(--text);
  font-size: 10.5px;
  font-weight: 620;
}

.selection-bar__actions button {
  display: flex;
  height: 30px;
  align-items: center;
  gap: 5px;
  padding: 0 9px;
  border: 0;
  border-radius: 6px;
  background: transparent;
  color: var(--text-soft);
  font: inherit;
  font-size: 9.5px;
  cursor: pointer;
}

.selection-bar__actions button:hover {
  background: rgb(255 255 255 / 64%);
}

.selection-bar__actions button.is-danger {
  color: var(--danger);
}

.selection-bar__actions .selection-bar__close {
  width: 28px;
  padding: 0;
  justify-content: center;
}

.insight-grid {
  display: grid;
  grid-template-columns: minmax(0, 1.55fr) minmax(260px, 0.85fr);
  gap: 18px;
  margin-top: 8px;
}

.insight-panel {
  min-width: 0;
  overflow: hidden;
  border: 1px solid var(--line);
  border-radius: 8px;
  background: #fbfbf9;
}

.insight-panel__head {
  display: flex;
  min-height: 60px;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 12px 16px;
  border-bottom: 1px solid var(--line);
}

.insight-panel__head > div {
  display: flex;
  flex-direction: column;
}

.insight-panel__head span {
  color: var(--text-quiet);
  font-size: 7.5px;
  font-weight: 720;
  letter-spacing: 0.14em;
}

.insight-panel__head h2 {
  margin: 3px 0 0;
  color: var(--text);
  font-size: 13px;
  font-weight: 600;
}

.insight-panel__head button {
  display: flex;
  align-items: center;
  gap: 5px;
  padding: 5px 7px;
  border: 0;
  border-radius: 5px;
  background: transparent;
  color: var(--accent-strong);
  font: inherit;
  font-size: 9.5px;
  cursor: pointer;
}

.insight-panel__head button:hover {
  background: var(--accent-wash);
}

.share-list-mini {
  display: flex;
  flex-direction: column;
}

.share-mini-row {
  display: grid;
  min-height: 61px;
  grid-template-columns: 36px minmax(0, 1fr) 92px 30px;
  align-items: center;
  gap: 10px;
  padding: 7px 14px;
  border-bottom: 1px solid var(--line);
}

.share-mini-row:last-child {
  border-bottom: 0;
}

.share-mini-row__name {
  display: flex;
  min-width: 0;
  flex-direction: column;
}

.share-mini-row__name strong {
  overflow: hidden;
  color: var(--text);
  font-size: 10.5px;
  font-weight: 610;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.share-mini-row__name span {
  display: flex;
  align-items: center;
  gap: 3px;
  margin-top: 2px;
  color: var(--text-quiet);
  font-size: 8.5px;
}

.share-mini-row__time {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
}

.share-mini-row__time span {
  color: var(--text-quiet);
  font-size: 8px;
}

.share-mini-row__time strong {
  margin-top: 2px;
  color: var(--text-soft);
  font-size: 8.5px;
  font-weight: 600;
}

.share-mini-row > button {
  display: grid;
  width: 29px;
  height: 29px;
  place-items: center;
  padding: 0;
  border: 0;
  border-radius: 6px;
  background: transparent;
  color: var(--text-quiet);
  cursor: pointer;
}

.share-mini-row > button:hover {
  background: var(--surface-muted);
  color: var(--accent-strong);
}

.insight-empty {
  padding: 30px 16px;
  color: var(--text-quiet);
  font-size: 10px;
  text-align: center;
}

.activity-list {
  padding: 8px 16px 12px;
}

.activity-row {
  position: relative;
  display: flex;
  gap: 10px;
  padding: 8px 0;
}

.activity-row:not(:last-child)::before {
  position: absolute;
  top: 20px;
  bottom: -5px;
  left: 3px;
  width: 1px;
  background: var(--line);
  content: '';
}

.activity-row__dot {
  z-index: 1;
  width: 7px;
  height: 7px;
  flex: 0 0 auto;
  margin-top: 4px;
  border: 2px solid #fff;
  border-radius: 50%;
  background: var(--text-quiet);
  box-shadow: 0 0 0 2px var(--line);
}

.activity-row__dot.is-red {
  background: #d95459;
  box-shadow: 0 0 0 2px #fbeaec;
}

.activity-row__dot.is-teal {
  background: var(--accent);
  box-shadow: 0 0 0 2px var(--accent-wash);
}

.activity-row__dot.is-violet {
  background: #8464c8;
  box-shadow: 0 0 0 2px #f1edfc;
}

.activity-row__dot.is-blue,
.activity-row__dot.is-amber {
  background: #4681cd;
  box-shadow: 0 0 0 2px #eaf1fb;
}

.activity-row > div {
  min-width: 0;
}

.activity-row p {
  margin: 0;
  color: var(--text-soft);
  font-size: 9.5px;
  line-height: 1.55;
}

.activity-row p strong {
  color: var(--text);
  font-weight: 610;
}

.activity-row > div > span {
  color: var(--text-quiet);
  font-size: 8px;
}

.selection-enter-active,
.selection-leave-active {
  transition:
    opacity 150ms ease,
    transform 150ms ease;
}

.selection-enter-from,
.selection-leave-to {
  opacity: 0;
  transform: translateY(-4px);
}

@media (max-width: 1180px) {
  .summary-strip {
    grid-template-columns: repeat(2, 1fr);
  }

  .summary-item:nth-child(2) {
    border-right: 0;
  }

  .summary-item:nth-child(-n + 2) {
    border-bottom: 1px solid var(--line);
  }

  .insight-grid {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 760px) {
  .page-title-row {
    flex-direction: column;
    align-items: stretch;
    gap: 16px;
  }

  .page-actions {
    align-self: flex-start;
  }

  .browser-toolbar {
    flex-direction: column;
    align-items: stretch;
  }

  .browser-toolbar__right {
    justify-content: space-between;
  }

  .result-hint {
    display: none;
  }

  .selection-bar {
    align-items: flex-start;
    flex-direction: column;
  }

  .selection-bar__actions {
    width: 100%;
    overflow-x: auto;
  }
}

@media (max-width: 640px) {
  .page-intro {
    padding-top: 0;
  }

  .breadcrumb-row {
    gap: 8px;
  }

  .breadcrumb-back {
    padding: 0 8px;
  }

  .page-title-row h1 {
    font-size: 26px;
  }

  .page-actions {
    width: 100%;
  }

  .page-actions .button {
    flex: 1;
    padding: 0 9px;
  }

  .summary-strip {
    grid-template-columns: 1fr 1fr;
  }

  .summary-item {
    padding: 11px;
  }

  .summary-item__icon {
    width: 30px;
    height: 30px;
  }

  .summary-item--security {
    display: none;
  }

  .filter-tabs {
    width: 100%;
    overflow-x: auto;
    padding-bottom: 2px;
  }

  .filter-tabs button {
    flex: 0 0 auto;
  }

  .sort-select {
    flex: 1;
  }

  .insight-grid {
    margin-top: 0;
  }

  .insight-panel--activity {
    display: none;
  }

  .share-mini-row {
    grid-template-columns: 36px minmax(0, 1fr) 30px;
  }

  .share-mini-row__time {
    display: none;
  }
}
</style>
