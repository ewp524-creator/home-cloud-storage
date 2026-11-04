<script setup>
import { computed, nextTick, ref, watch } from 'vue'
import { ChevronDown, ChevronRight, FolderPlus } from '@lucide/vue'
import { useFileManagerContext } from '../../composables/fileManagerContext'
import FileGlyph from './FileGlyph.vue'

const emit = defineEmits(['new-folder'])
const {
  currentFolderId,
  files,
  loadTreeChildren,
  moveItems,
  openFolder,
  openPreview,
  rootFolderId,
  sidebarOpen,
} = useFileManagerContext()

const treeOpen = ref(true)
const treeBody = ref(null)
const dragOverId = ref(null)
const dragMime = 'application/x-filehome-items'
const expandedIds = ref(new Set())

function childrenOf(parentId) {
  return files.value
    .filter(
      (file) =>
        !file.deletedAt &&
        (file.parentId || null) === (parentId || null),
    )
    .sort((a, b) => {
      if (a.kind !== b.kind) return a.kind === 'folder' ? -1 : 1
      return a.name.localeCompare(b.name, 'zh-CN')
    })
}

const rows = computed(() => {
  const output = []

  function walk(parentId, depth) {
    for (const item of childrenOf(parentId)) {
      const children = item.kind === 'folder' ? childrenOf(item.id) : []
      const expanded = expandedIds.value.has(item.id)
      output.push({
        ...item,
        depth,
        hasChildren: Boolean(item.hasChildren || children.length),
        expanded,
      })
      if (item.kind === 'folder' && expanded) walk(item.id, depth + 1)
    }
  }

  walk(null, 0)
  return output
})

async function toggleFolder(folderId) {
  const next = new Set(expandedIds.value)
  const expanding = !next.has(folderId)
  if (expanding) {
    await loadTreeChildren(folderId)
    next.add(folderId)
  } else {
    next.delete(folderId)
  }
  expandedIds.value = next
}

function chooseItem(item) {
  if (item.kind === 'folder') openFolder(item)
  else openPreview(item)
  sidebarOpen.value = false
}

function startDrag(item, event) {
  event.dataTransfer.effectAllowed = 'move'
  event.dataTransfer.setData(dragMime, JSON.stringify([item.id]))
  event.dataTransfer.setData('text/plain', item.name)
  event.stopPropagation()
}

function dragOverFolder(item, event) {
  if (item.kind !== 'folder') return
  const types = Array.from(event.dataTransfer?.types || [])
  if (!types.includes(dragMime)) return
  event.preventDefault()
  event.stopPropagation()
  event.dataTransfer.dropEffect = 'move'
  dragOverId.value = item.id
}

function dropOnFolder(item, event) {
  if (item.kind !== 'folder') return
  if (!Array.from(event.dataTransfer?.types || []).includes(dragMime)) return
  event.preventDefault()
  event.stopPropagation()
  try {
    const ids = JSON.parse(event.dataTransfer.getData(dragMime) || '[]')
    moveItems(ids, item.id)
  } finally {
    dragOverId.value = null
  }
}

function endDrag() {
  dragOverId.value = null
}

async function expandAncestors(folderId) {
  if (!folderId) return
  const next = new Set(expandedIds.value)
  const ancestors = []
  let cursor = files.value.find((file) => file.id === folderId)

  while (cursor) {
    next.add(cursor.id)
    ancestors.unshift(cursor.id)
    cursor = files.value.find((file) => file.id === cursor.parentId)
  }

  expandedIds.value = next
  for (const id of ancestors) {
    await loadTreeChildren(id)
  }
}

watch(
  rootFolderId,
  (folderId) => {
    if (!folderId) return
    const next = new Set(expandedIds.value)
    next.add(folderId)
    expandedIds.value = next
  },
  { immediate: true },
)

watch(
  currentFolderId,
  async (folderId) => {
    await expandAncestors(folderId)
    await nextTick()
    const activeRow = treeBody.value?.querySelector('.file-tree__row.is-active')
    if (activeRow && treeBody.value) {
      treeBody.value.scrollTop =
        activeRow.offsetTop - treeBody.value.clientHeight / 2
    }
  },
  { immediate: true },
)
</script>

<template>
  <section class="file-tree" :class="{ 'is-collapsed': !treeOpen }">
    <header class="file-tree__head">
      <button
        class="file-tree__heading"
        type="button"
        :aria-expanded="treeOpen"
        @click="treeOpen = !treeOpen"
      >
        <ChevronDown :size="13" :class="{ 'is-collapsed': !treeOpen }" />
        <span>文件夹</span>
      </button>
      <button
        class="file-tree__add"
        type="button"
        title="新建文件夹"
        aria-label="新建文件夹"
        @click="emit('new-folder')"
      >
        <FolderPlus :size="14" />
      </button>
    </header>

    <div v-if="treeOpen" ref="treeBody" class="file-tree__body">
      <div
        v-for="item in rows"
        :key="item.id"
        class="file-tree__row"
        :class="{
          'is-active': item.kind === 'folder' && item.id === currentFolderId,
          'is-file': item.kind === 'file',
          'is-drop-target': dragOverId === item.id,
        }"
        :style="{ '--tree-depth': item.depth }"
        draggable="true"
        @dragstart.stop="startDrag(item, $event)"
        @dragend="endDrag"
        @dragover="dragOverFolder(item, $event)"
        @dragleave="dragOverId === item.id ? (dragOverId = null) : null"
        @drop="dropOnFolder(item, $event)"
      >
        <button
          v-if="item.kind === 'folder' && item.hasChildren"
          class="file-tree__chevron"
          type="button"
          :title="item.expanded ? '收起' : '展开'"
          :aria-label="item.expanded ? '收起' : '展开'"
          @click.stop="toggleFolder(item.id)"
        >
          <ChevronRight
            :size="13"
            :class="{ 'is-expanded': item.expanded }"
          />
        </button>
        <span v-else class="file-tree__chevron-placeholder" />

        <FileGlyph :file="item" size="xs" />
        <button
          class="file-tree__name"
          type="button"
          :title="item.name"
          @click="chooseItem(item)"
        >
          {{ item.name }}
        </button>
      </div>
    </div>
  </section>
</template>

<style scoped>
.file-tree {
  flex: 0 0 auto;
  min-height: 0;
  margin: 26px 2px 0;
  padding-top: 15px;
  border-top: 1px solid var(--line);
}

.file-tree__head {
  display: flex;
  height: 25px;
  align-items: center;
  justify-content: space-between;
  padding: 0 5px 0 2px;
}

.file-tree__heading {
  display: flex;
  align-items: center;
  gap: 5px;
  padding: 0;
  border: 0;
  background: transparent;
  color: var(--text-quiet);
  font: inherit;
  font-size: 9px;
  font-weight: 650;
  letter-spacing: 0.1em;
  cursor: pointer;
}

.file-tree__heading svg {
  transition: transform 160ms ease;
}

.file-tree__heading svg.is-collapsed {
  transform: rotate(-90deg);
}

.file-tree__add {
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

.file-tree__add:hover {
  background: var(--surface-muted);
  color: var(--text);
}

.file-tree__body {
  max-height: 166px;
  margin-top: 4px;
  overflow-y: auto;
  overscroll-behavior: contain;
}

.file-tree__row {
  display: grid;
  min-height: 29px;
  grid-template-columns: 15px 21px minmax(0, 1fr);
  align-items: center;
  gap: 4px;
  padding-right: 5px;
  padding-left: calc(var(--tree-depth) * 13px);
  border-radius: 5px;
}

.file-tree__row:hover {
  background: #fff;
}

.file-tree__row.is-active {
  background: #fff;
  box-shadow: inset 0 0 0 1px var(--line);
}

.file-tree__row.is-drop-target {
  background: var(--accent-wash);
  box-shadow: inset 0 0 0 1px var(--accent);
}

.file-tree__chevron,
.file-tree__chevron-placeholder {
  display: grid;
  width: 15px;
  height: 22px;
  place-items: center;
  padding: 0;
  border: 0;
  background: transparent;
  color: var(--text-quiet);
}

.file-tree__chevron {
  cursor: pointer;
}

.file-tree__chevron svg {
  transition: transform 150ms ease;
}

.file-tree__chevron svg.is-expanded {
  transform: rotate(90deg);
}

.file-tree__name {
  min-width: 0;
  padding: 4px 2px;
  overflow: hidden;
  border: 0;
  background: transparent;
  color: var(--text-soft);
  font: inherit;
  font-size: 10.5px;
  text-align: left;
  text-overflow: ellipsis;
  white-space: nowrap;
  cursor: pointer;
}

.file-tree__row.is-active .file-tree__name {
  color: var(--text);
  font-weight: 600;
}

.file-tree__row.is-file .file-tree__name {
  color: var(--text-quiet);
}

@media (max-height: 800px) and (min-width: 901px) {
  .file-tree__body {
    max-height: 100px;
  }
}
</style>
