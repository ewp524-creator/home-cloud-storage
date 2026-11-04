<script setup>
import { computed } from 'vue'
import {
  Check,
  Link2,
  MoreHorizontal,
  Star,
} from '@lucide/vue'
import { formatDate } from '../../utils/formatters'
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

const metaText = computed(() => {
  if (props.file.kind === 'folder') {
    return `${props.file.itemCount || 0} 个项目`
  }
  return formatDate(props.file.modifiedAt)
})

function onSelect(event) {
  emit('select', props.file, event.ctrlKey || event.metaKey || event.shiftKey)
}

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
  <article
    class="file-card"
    :class="{
      'is-selected': selected,
      'is-dragging': dragging,
      'is-drop-target': dropTarget,
    }"
    tabindex="0"
    draggable="true"
    :aria-label="file.name"
    @click="onSelect"
    @dblclick="emit('open', file)"
    @keydown.enter.prevent="emit('open', file)"
    @contextmenu="onContextMenu"
    @dragstart="onDragStart"
    @dragend="emit('drag-end')"
    @dragover="onDragOver"
    @dragleave="emit('drag-leave', file, $event)"
    @drop="onDrop"
  >
    <div class="file-card__topline">
      <button
        class="file-card__check"
        :class="{ 'is-visible': selected }"
        type="button"
        :aria-label="selected ? '取消选择' : '选择文件'"
        @click.stop="emit('select', file, true)"
      >
        <Check v-if="selected" :size="13" :stroke-width="3" />
      </button>

      <div class="file-card__actions">
        <span
          v-if="file.shared"
          class="file-card__state"
          title="已创建分享链接"
          aria-label="已创建分享链接"
        >
          <Link2 :size="14" />
        </span>
        <button
          class="file-card__icon-action"
          :class="{ 'is-starred': file.starred }"
          type="button"
          :title="file.starred ? '取消收藏' : '收藏'"
          :aria-label="file.starred ? '取消收藏' : '收藏'"
          @click.stop="emit('star', file)"
        >
          <Star :size="15" :fill="file.starred ? 'currentColor' : 'none'" />
        </button>
        <button
          class="file-card__icon-action"
          type="button"
          title="更多操作"
          aria-label="更多操作"
          @click.stop="emit('menu', file, $event)"
        >
          <MoreHorizontal :size="17" />
        </button>
      </div>
    </div>

    <div class="file-card__body">
      <FileGlyph :file="file" size="lg" />
      <h3 class="file-card__name" :title="file.name">{{ file.name }}</h3>
    </div>

    <div class="file-card__footer">
      <span>{{ metaText }}</span>
      <span v-if="file.tags?.[0]" class="file-card__tag">
        {{ file.tags[0] }}
      </span>
    </div>
  </article>
</template>

<style scoped>
.file-card {
  position: relative;
  display: flex;
  min-width: 0;
  min-height: 184px;
  flex-direction: column;
  padding: 14px 15px 13px;
  border: 1px solid var(--line);
  border-radius: 8px;
  background: #fbfbf9;
  box-shadow: none;
  cursor: default;
  outline: none;
  transition:
    border-color 180ms ease,
    box-shadow 180ms ease,
    transform 180ms ease,
    background 180ms ease;
}

.file-card:hover {
  z-index: 1;
  border-color: var(--line-strong);
  background: #fff;
  box-shadow: 0 8px 24px rgb(25 27 24 / 4%);
  transform: translateY(-1px);
}

.file-card:focus-visible {
  box-shadow: 0 0 0 3px var(--accent-ring);
}

.file-card.is-selected {
  border-color: var(--accent);
  background: var(--accent-wash);
  box-shadow: inset 0 0 0 1px var(--accent);
}

.file-card.is-dragging {
  opacity: 0.42;
}

.file-card.is-drop-target {
  border-color: var(--accent);
  background: var(--accent-wash);
  box-shadow: inset 0 0 0 1px var(--accent);
}

.file-card__topline {
  display: flex;
  height: 24px;
  align-items: center;
  justify-content: space-between;
}

.file-card__check {
  display: grid;
  width: 20px;
  height: 20px;
  place-items: center;
  padding: 0;
  border: 1px solid var(--line-strong);
  border-radius: 50%;
  background: #fff;
  color: transparent;
  cursor: pointer;
  opacity: 0;
  transition: 160ms ease;
}

.file-card:hover .file-card__check,
.file-card.is-selected .file-card__check,
.file-card:focus-within .file-card__check {
  opacity: 1;
}

.file-card__check.is-visible {
  border-color: var(--accent);
  background: var(--accent);
  color: #fff;
  opacity: 1;
}

.file-card__actions {
  display: flex;
  align-items: center;
  gap: 1px;
  margin-left: auto;
}

.file-card__icon-action,
.file-card__state {
  display: grid;
  width: 28px;
  height: 28px;
  place-items: center;
  padding: 0;
  border: 0;
  border-radius: 5px;
  background: transparent;
  color: var(--text-quiet);
  cursor: pointer;
  transition:
    opacity 150ms ease,
    background 150ms ease;
}

.file-card__icon-action {
  opacity: 0;
}

.file-card:hover .file-card__icon-action,
.file-card:focus-within .file-card__icon-action,
.file-card__icon-action.is-starred {
  opacity: 1;
}

.file-card__icon-action:hover {
  background: var(--surface-muted);
  color: var(--text);
}

.file-card__icon-action.is-starred {
  color: var(--star);
}

.file-card__state {
  width: 24px;
  color: var(--accent);
  cursor: default;
}

.file-card__body {
  display: flex;
  min-width: 0;
  flex: 1;
  flex-direction: column;
  align-items: flex-start;
  justify-content: center;
  gap: 11px;
  padding: 3px 3px 9px;
}

.file-card__body :deep(.file-glyph--lg) {
  --glyph-size: 64px;
  --glyph-icon: 36px;
}

.file-card__body :deep(.file-glyph__paper) {
  transform: none;
}

.file-card:hover .file-card__body :deep(.file-glyph__paper) {
  transform: translateY(-1px);
  box-shadow: none;
}

.file-card__name {
  width: 100%;
  margin: 0;
  overflow: hidden;
  color: var(--text);
  font-size: 13px;
  font-weight: 600;
  letter-spacing: 0;
  line-height: 1.45;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.file-card__footer {
  display: flex;
  min-height: 21px;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  color: var(--text-quiet);
  font-size: 10.5px;
}

.file-card__tag {
  max-width: 90px;
  padding: 2px 5px;
  overflow: hidden;
  border-radius: 4px;
  border: 1px solid var(--line);
  background: transparent;
  color: var(--text-soft);
  text-overflow: ellipsis;
  white-space: nowrap;
}

@media (max-width: 640px) {
  .file-card {
    min-height: 160px;
  }
}
</style>
