<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import {
  ClipboardPaste,
  Copy,
  Download,
  Eye,
  FolderOpen,
  Info,
  Link2,
  Pencil,
  Settings2,
  Share2,
  Star,
  Trash2,
  XCircle,
} from '@lucide/vue'

const props = defineProps({
  open: {
    type: Boolean,
    default: false,
  },
  x: {
    type: Number,
    default: 0,
  },
  y: {
    type: Number,
    default: 0,
  },
  file: {
    type: Object,
    default: null,
  },
  hasClipboard: {
    type: Boolean,
    default: false,
  },
})

const emit = defineEmits(['close', 'action'])
const menu = ref(null)

const actions = computed(() => {
  if (!props.file) return []
  const items = []

  if (props.file.kind === 'folder') {
    items.push({ id: 'open', label: '打开', icon: FolderOpen })
    items.push({ id: 'details', label: '详细信息', icon: Info })
  } else {
    items.push({ id: 'preview', label: '预览', icon: Eye })
    items.push({ id: 'details', label: '详细信息', icon: Info })
  }

  items.push({ id: 'copy', label: '复制', icon: Copy })
  if (props.file.kind === 'folder' && props.hasClipboard) {
    items.push({ id: 'paste', label: '粘贴到此处', icon: ClipboardPaste })
  }
  items.push({
    id: 'download',
    label: props.file.kind === 'folder' ? '下载 ZIP' : '下载',
    icon: Download,
  })
  if (props.file.shared) {
    items.push({ id: 'manage-share', label: '管理分享', icon: Settings2 })
    items.push({
      id: 'cancel-share',
      label: '取消分享',
      icon: XCircle,
      danger: true,
    })
    items.push({ id: 'copy-share', label: '复制分享链接', icon: Link2 })
  } else {
    items.push({ id: 'share', label: '分享', icon: Share2 })
  }
  items.push({ id: 'rename', label: '重命名', icon: Pencil })
  items.push({
    id: 'star',
    label: props.file.starred ? '取消收藏' : '收藏',
    icon: Star,
  })
  items.push({ id: 'delete', label: '移到回收站', icon: Trash2, danger: true })

  return items
})

const menuStyle = computed(() => {
  const menuHeight = actions.value.length * 38 + 18
  const left = Math.max(12, Math.min(props.x, globalThis.innerWidth - 224))
  const top = Math.max(
    12,
    Math.min(props.y, globalThis.innerHeight - menuHeight - 12),
  )
  return { left: `${left}px`, top: `${top}px` }
})

function onPointerDown(event) {
  if (menu.value?.contains(event.target)) return
  emit('close')
}

function onKeydown(event) {
  if (event.key === 'Escape') emit('close')
}

onMounted(() => {
  document.addEventListener('pointerdown', onPointerDown)
  document.addEventListener('keydown', onKeydown)
})

onBeforeUnmount(() => {
  document.removeEventListener('pointerdown', onPointerDown)
  document.removeEventListener('keydown', onKeydown)
})
</script>

<template>
  <Teleport to="body">
    <Transition name="menu">
      <div
        v-if="open && file"
        ref="menu"
        class="context-menu"
        :style="menuStyle"
        role="menu"
      >
        <button
          v-for="action in actions"
          :key="action.id"
          class="context-menu__item"
          :class="{ 'is-danger': action.danger }"
          type="button"
          role="menuitem"
          @click="emit('action', action.id, file)"
        >
          <component :is="action.icon" :size="16" :stroke-width="1.8" />
          <span>{{ action.label }}</span>
        </button>
      </div>
    </Transition>
  </Teleport>
</template>

<style scoped>
.context-menu {
  position: fixed;
  z-index: 1000;
  width: 212px;
  padding: 6px;
  border: 1px solid var(--line);
  border-radius: 8px;
  background: #fff;
  box-shadow: 0 18px 48px rgb(19 35 30 / 17%);
}

.context-menu__item {
  display: flex;
  width: 100%;
  align-items: center;
  gap: 10px;
  padding: 9px 10px;
  border: 0;
  border-radius: 7px;
  background: transparent;
  color: var(--text-soft);
  font: inherit;
  font-size: 12.5px;
  text-align: left;
  cursor: pointer;
}

.context-menu__item:hover {
  background: var(--surface-muted);
  color: var(--text);
}

.context-menu__item.is-danger {
  color: var(--danger);
}

.context-menu__item.is-danger:hover {
  background: var(--danger-wash);
}

.menu-enter-active,
.menu-leave-active {
  transition:
    opacity 140ms ease,
    transform 140ms ease;
}

.menu-enter-from,
.menu-leave-to {
  opacity: 0;
  transform: translateY(-4px) scale(0.98);
}
</style>
