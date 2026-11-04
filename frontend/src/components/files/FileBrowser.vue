<script setup>
import { computed, ref } from 'vue'
import {
  ChevronDown,
  FilePlus2,
  FolderPlus,
  LoaderCircle,
  Search,
  UploadCloud,
} from '@lucide/vue'
import { useFileManagerContext } from '../../composables/fileManagerContext'
import FileCard from './FileCard.vue'
import FileContextMenu from './FileContextMenu.vue'
import FileListRow from './FileListRow.vue'

const props = defineProps({
  files: {
    type: Array,
    default: () => [],
  },
  selectedIds: {
    type: Array,
    default: () => [],
  },
  viewMode: {
    type: String,
    default: 'grid',
  },
  currentView: {
    type: String,
    default: 'files',
  },
})

const emit = defineEmits([
  'upload',
  'new-folder',
  'share',
  'cancel-share',
  'rename',
])
const manager = useFileManagerContext()

const contextMenu = ref({
  open: false,
  x: 0,
  y: 0,
  file: null,
})
const dragMime = 'application/x-filehome-items'
const draggingIds = ref([])
const dropTargetId = ref(null)

const emptyCopy = computed(() => {
  if (manager.searchQuery.value) {
    return {
      title: '没有找到匹配的文件',
      text: '换一个关键词，或清除当前筛选后再试。',
      icon: Search,
    }
  }
  if (props.currentView === 'starred') {
    return {
      title: '还没有收藏文件',
      text: '在文件卡片上点击星标，重要内容会出现在这里。',
      icon: FilePlus2,
    }
  }
  return {
    title: '这里还没有文件',
    text: '拖入文件即可上传，也可以新建一个文件夹开始整理。',
    icon: UploadCloud,
  }
})

function isSelected(file) {
  return props.selectedIds.includes(file.id)
}

function openMenu(file, event) {
  const rect = event.currentTarget?.getBoundingClientRect?.()
  contextMenu.value = {
    open: true,
    file,
    x: rect ? rect.left - 172 : event.clientX,
    y: rect ? rect.bottom + 6 : event.clientY,
  }
}

function onContext(file, event) {
  contextMenu.value = {
    open: true,
    file,
    x: event.clientX,
    y: event.clientY,
  }
}

function handleMenuAction(action, file) {
  contextMenu.value.open = false

  if (action === 'open') {
    if (file.kind === 'folder') manager.openFolder(file)
    else manager.openPreview(file)
  }
  if (action === 'preview') manager.openPreview(file)
  if (action === 'details') manager.openDetails(file)
  if (action === 'download') {
    manager.downloadFiles([file])
  }
  if (action === 'share') emit('share', file)
  if (action === 'manage-share') emit('share', file)
  if (action === 'cancel-share') {
    const share = manager.getActiveShareForFile(file.id)
    if (share) emit('cancel-share', share)
  }
  if (action === 'copy') {
    const ids = props.selectedIds.includes(file.id)
      ? [...props.selectedIds]
      : [file.id]
    manager.copyItemsToClipboard(ids)
  }
  if (action === 'paste') manager.pasteCopiedItems(file.id)
  if (action === 'copy-share') {
    const share = manager.getActiveShareForFile(file.id)
    if (share?.url) manager.copyText(share.url, '分享链接已复制')
    else manager.toast('当前没有有效的分享链接', 'info')
  }
  if (action === 'rename') emit('rename', file)
  if (action === 'star') manager.toggleStar(file)
  if (action === 'delete') manager.moveToTrash([file.id])
}

function openItem(file) {
  if (file.kind === 'folder') manager.openFolder(file)
  else manager.openPreview(file)
}

function startDrag(file, event) {
  const ids = props.selectedIds.includes(file.id)
    ? [...props.selectedIds]
    : [file.id]
  event.dataTransfer.effectAllowed = 'move'
  event.dataTransfer.setData(dragMime, JSON.stringify(ids))
  event.dataTransfer.setData('text/plain', file.name)
  event.stopPropagation()
  draggingIds.value = ids
}

function dragOverFolder(file, event) {
  const types = Array.from(event.dataTransfer?.types || [])
  if (!types.includes(dragMime)) return
  event.preventDefault()
  event.stopPropagation()
  event.dataTransfer.dropEffect = 'move'
  dropTargetId.value = file.id
}

function leaveFolder(file, event) {
  if (event.currentTarget?.contains(event.relatedTarget)) return
  if (dropTargetId.value === file.id) dropTargetId.value = null
}

function dropOnFolder(file, event) {
  event.preventDefault()
  event.stopPropagation()
  try {
    const ids = JSON.parse(event.dataTransfer.getData(dragMime) || '[]')
    manager.moveItems(ids, file.id)
  } finally {
    draggingIds.value = []
    dropTargetId.value = null
  }
}

function endDrag() {
  draggingIds.value = []
  dropTargetId.value = null
}

function clearSearchAndFilters() {
  manager.searchQuery.value = ''
  manager.typeFilter.value = 'all'
}
</script>

<template>
  <section class="file-browser" aria-live="polite">
    <div v-if="files.length" :class="viewMode === 'grid' ? 'file-grid' : 'file-list'">
      <template v-if="viewMode === 'grid'">
        <FileCard
          v-for="file in files"
          :key="file.id"
          :file="file"
          :selected="isSelected(file)"
          :dragging="draggingIds.includes(file.id)"
          :drop-target="dropTargetId === file.id"
          @select="manager.selectFile"
          @open="openItem"
          @menu="openMenu"
          @context="onContext"
          @star="manager.toggleStar"
          @drag-start="startDrag"
          @drag-end="endDrag"
          @drag-over="dragOverFolder"
          @drag-leave="leaveFolder"
          @drop="dropOnFolder"
        />
      </template>

      <template v-else>
        <div class="file-list__head" role="row">
          <span>名称</span>
          <span>所有者</span>
          <span>修改时间</span>
          <span>大小</span>
          <span />
        </div>
        <FileListRow
          v-for="file in files"
          :key="file.id"
          :file="file"
          :selected="isSelected(file)"
          :dragging="draggingIds.includes(file.id)"
          :drop-target="dropTargetId === file.id"
          @select="manager.selectFile"
          @open="openItem"
          @menu="openMenu"
          @context="onContext"
          @star="manager.toggleStar"
          @drag-start="startDrag"
          @drag-end="endDrag"
          @drag-over="dragOverFolder"
          @drag-leave="leaveFolder"
          @drop="dropOnFolder"
        />
      </template>
    </div>

    <div v-if="files.length && manager.hasMoreFiles.value" class="load-more">
      <button
        class="button button--secondary"
        type="button"
        :disabled="manager.loadingMore.value"
        @click="manager.loadMoreFiles"
      >
        <LoaderCircle
          v-if="manager.loadingMore.value"
          :size="16"
          class="load-more__spinner"
        />
        <ChevronDown v-else :size="16" />
        {{ manager.loadingMore.value ? '正在加载' : '加载更多' }}
      </button>
    </div>

    <div v-if="!files.length" class="empty-state">
      <div class="empty-state__art">
        <component :is="emptyCopy.icon" :size="32" :stroke-width="1.5" />
      </div>
      <h3>{{ emptyCopy.title }}</h3>
      <p>{{ emptyCopy.text }}</p>
      <div class="empty-state__actions">
        <button
          v-if="manager.searchQuery.value || manager.typeFilter.value !== 'all'"
          class="button button--secondary"
          type="button"
          @click="clearSearchAndFilters"
        >
          清除筛选
        </button>
        <template v-else>
          <button class="button button--primary" type="button" @click="emit('upload')">
            <UploadCloud :size="16" />
            上传文件
          </button>
          <button class="button button--secondary" type="button" @click="emit('new-folder')">
            <FolderPlus :size="16" />
            新建文件夹
          </button>
        </template>
      </div>
    </div>

    <FileContextMenu
      :open="contextMenu.open"
      :x="contextMenu.x"
      :y="contextMenu.y"
      :file="contextMenu.file"
      :has-clipboard="manager.clipboardHasItems.value"
      @close="contextMenu.open = false"
      @action="handleMenuAction"
    />
  </section>
</template>

<style scoped>
.file-browser {
  min-height: 260px;
}

.file-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(214px, 1fr));
  gap: 16px;
}

.file-list {
  overflow: hidden;
  border: 1px solid var(--line);
  border-radius: 8px;
  background: #fbfbf9;
}

.file-list__head {
  display: grid;
  min-height: 42px;
  grid-template-columns: minmax(220px, 2.2fr) minmax(96px, 0.7fr) minmax(116px, 0.8fr) minmax(72px, 0.45fr) 76px;
  align-items: center;
  gap: 16px;
  padding: 0 14px;
  border-bottom: 1px solid var(--line);
  background: var(--surface-muted);
  color: var(--text-quiet);
  font-size: 11px;
  font-weight: 650;
}

.empty-state {
  display: flex;
  min-height: 360px;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 40px 24px;
  border: 1px dashed var(--line-strong);
  border-radius: 10px;
  background:
    radial-gradient(circle at 50% 25%, rgb(49 85 217 / 5%), transparent 35%),
    #fff;
  text-align: center;
}

.load-more {
  display: flex;
  justify-content: center;
  padding-top: 18px;
}

.load-more .button {
  min-width: 132px;
}

.load-more__spinner {
  animation: load-more-spin 800ms linear infinite;
}

@keyframes load-more-spin {
  to {
    transform: rotate(360deg);
  }
}

.empty-state__art {
  display: grid;
  width: 72px;
  height: 72px;
  place-items: center;
  margin-bottom: 18px;
  border: 1px solid var(--accent-line);
  border-radius: 12px;
  background: var(--accent-wash);
  color: var(--accent-strong);
}

.empty-state h3 {
  margin: 0;
  color: var(--text);
  font-size: 18px;
  letter-spacing: 0;
}

.empty-state p {
  max-width: 420px;
  margin: 8px 0 22px;
  color: var(--text-soft);
  font-size: 13px;
  line-height: 1.65;
}

.empty-state__actions {
  display: flex;
  flex-wrap: wrap;
  justify-content: center;
  gap: 9px;
}

@media (max-width: 900px) {
  .file-list__head {
    grid-template-columns: minmax(180px, 1fr) 90px 60px;
  }

  .file-list__head span:nth-child(2),
  .file-list__head span:nth-child(3) {
    display: none;
  }
}

@media (max-width: 640px) {
  .file-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: 10px;
  }

  .file-list {
    border-radius: 10px;
  }
}
</style>
