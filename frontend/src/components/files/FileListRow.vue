<script setup>
import {
  Link2,
  MoreHorizontal,
  Star,
} from '@lucide/vue'
import { formatBytes, formatDate } from '../../utils/formatters'
import FileGlyph from './FileGlyph.vue'

const props = defineProps({
  file: {
    type: Object,
    required: true,
  },
  selected: {
    type: Boolean,
    default: false,
  },
  dragging: {
    type: Boolean,
    default: false,
  },
  dropTarget: {
    type: Boolean,
    default: false,
  },
})

const emit = defineEmits([
  'select',
  'open',
  'menu',
  'star',
  'context',
  'drag-start',
  'drag-end',
  'drag-over',
  'drag-leave',
  'drop',
])
const dragMime = 'application/x-filehome-items'

function onContextMenu(event) {
  event.preventDefault()
  emit('context', props.file, event)
}

function onDragStart(event) {
  event.dataTransfer.effectAllowed = 'move'
  emit('drag-start', props.file, event)
}

function onDragOver(event) {
  if (
    props.file.kind !== 'folder' ||
    !Array.from(event.dataTransfer?.types || []).includes(dragMime)
  ) {
    return
  }
  event.preventDefault()
  emit('drag-over', props.file, event)
}

function onDrop(event) {
  if (
    props.file.kind !== 'folder' ||
    !Array.from(event.dataTransfer?.types || []).includes(dragMime)
  ) {
    return
  }
  event.preventDefault()
  event.stopPropagation()
  emit('drop', props.file, event)
}
</script>

<template>
  <div
    class="file-row"
    :class="{
      'is-selected': selected,
      'is-dragging': dragging,
      'is-drop-target': dropTarget,
    }"
    role="row"
    tabindex="0"
    draggable="true"
    @click="emit('select', file, $event.ctrlKey || $event.metaKey)"
    @dblclick="emit('open', file)"
    @keydown.enter.prevent="emit('open', file)"
    @contextmenu="onContextMenu"
    @dragstart="onDragStart"
    @dragend="emit('drag-end')"
    @dragover="onDragOver"
    @dragleave="emit('drag-leave', file, $event)"
    @drop="onDrop"
  >
    <div class="file-row__name">
      <FileGlyph :file="file" size="sm" />
      <div>
        <strong :title="file.name">{{ file.name }}</strong>
        <span>
          {{ file.kind === 'folder' ? `${file.itemCount || 0} 个项目` : '个人文件' }}
          <Link2 v-if="file.shared" :size="12" />
        </span>
      </div>
    </div>
    <div class="file-row__owner">{{ file.owner }}</div>
    <div class="file-row__date">{{ formatDate(file.modifiedAt) }}</div>
    <div class="file-row__size">
      {{ file.kind === 'folder' ? '—' : formatBytes(file.size) }}
    </div>
    <div class="file-row__actions">
      <button
        class="file-row__action"
        :class="{ 'is-starred': file.starred }"
        type="button"
        title="收藏"
        aria-label="收藏"
        @click.stop="emit('star', file)"
      >
        <Star :size="15" :fill="file.starred ? 'currentColor' : 'none'" />
      </button>
      <button
        class="file-row__action"
        type="button"
        title="更多操作"
        aria-label="更多操作"
        @click.stop="emit('menu', file, $event)"
      >
        <MoreHorizontal :size="17" />
      </button>
    </div>
  </div>
</template>

<style scoped>
.file-row {
  display: grid;
  min-height: 62px;
  grid-template-columns: minmax(220px, 2.2fr) minmax(96px, 0.7fr) minmax(116px, 0.8fr) minmax(72px, 0.45fr) 76px;
  align-items: center;
  gap: 16px;
  padding: 8px 14px;
  border-bottom: 1px solid var(--line);
  outline: none;
  transition: background 150ms ease;
}

.file-row:hover,
.file-row.is-selected {
  background: var(--surface-muted);
}

.file-row.is-selected {
  background: var(--accent-wash);
}

.file-row.is-dragging {
  opacity: 0.42;
}

.file-row.is-drop-target {
  background: var(--accent-wash);
  box-shadow: inset 3px 0 0 var(--accent);
}

.file-row__name {
  display: flex;
  min-width: 0;
  align-items: center;
  gap: 12px;
}

.file-row__name > div {
  min-width: 0;
}

.file-row__name strong {
  display: block;
  overflow: hidden;
  color: var(--text);
  font-size: 13px;
  font-weight: 620;
  line-height: 1.45;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.file-row__name span {
  display: flex;
  align-items: center;
  gap: 4px;
  margin-top: 2px;
  color: var(--text-quiet);
  font-size: 11px;
}

.file-row__owner,
.file-row__date,
.file-row__size {
  overflow: hidden;
  color: var(--text-soft);
  font-size: 12px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.file-row__actions {
  display: flex;
  justify-content: flex-end;
  gap: 2px;
}

.file-row__action {
  display: grid;
  width: 30px;
  height: 30px;
  place-items: center;
  padding: 0;
  border: 0;
  border-radius: 7px;
  background: transparent;
  color: var(--text-quiet);
  cursor: pointer;
}

.file-row__action:hover {
  background: #fff;
  color: var(--text);
}

.file-row__action.is-starred {
  color: var(--star);
}

@media (max-width: 900px) {
  .file-row {
    grid-template-columns: minmax(180px, 1fr) 90px 60px;
  }

  .file-row__owner,
  .file-row__date {
    display: none;
  }
}
</style>
