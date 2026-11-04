<script setup>
import { computed, ref } from 'vue'
import {
  ArrowLeft,
  Clock3,
  RotateCcw,
  Search,
  ShieldAlert,
  Trash2,
  X,
} from '@lucide/vue'
import { useFileManagerContext } from '../../composables/fileManagerContext'
import { formatBytes, formatDate } from '../../utils/formatters'
import FileGlyph from '../files/FileGlyph.vue'

const emit = defineEmits(['permanent-delete', 'empty-trash'])
const manager = useFileManagerContext()
const { files, navigate, searchQuery } = manager

const selectedIds = ref([])
const typeTab = ref('all')

const trashFiles = computed(() => {
  let list = files.value.filter((file) => file.deletedAt)
  if (typeTab.value === 'file') list = list.filter((file) => file.kind === 'file')
  if (typeTab.value === 'folder') {
    list = list.filter((file) => file.kind === 'folder')
  }
  const query = searchQuery.value.trim().toLowerCase()
  if (query) list = list.filter((file) => file.name.toLowerCase().includes(query))
  return list.sort(
    (a, b) => new Date(b.deletedAt) - new Date(a.deletedAt),
  )
})

const allSelected = computed(
  () =>
    trashFiles.value.length > 0 &&
    trashFiles.value.every((file) => selectedIds.value.includes(file.id)),
)

function toggleSelected(file) {
  selectedIds.value = selectedIds.value.includes(file.id)
    ? selectedIds.value.filter((id) => id !== file.id)
    : [...selectedIds.value, file.id]
}

function toggleAll() {
  selectedIds.value = allSelected.value
    ? []
    : trashFiles.value.map((file) => file.id)
}

function restore(ids) {
  manager.restoreFiles(ids)
  selectedIds.value = []
}

function daysRemaining(file) {
  const elapsed = Date.now() - new Date(file.deletedAt).getTime()
  const remaining = Math.ceil(30 - elapsed / (24 * 60 * 60 * 1000))
  return Math.max(0, remaining)
}
</script>

<template>
  <div class="trash-view">
    <section class="trash-hero">
      <div>
        <button class="back-link" type="button" @click="navigate('files')">
          <ArrowLeft :size="14" />
          返回文件库
        </button>
        <span class="page-kicker">RECOVERY SPACE</span>
        <h1>回收站</h1>
        <p>删除的文件会保留 30 天，到期后将自动永久删除。</p>
      </div>
      <button
        v-if="trashFiles.length"
        class="button button--danger-ghost"
        type="button"
        @click="emit('empty-trash', trashFiles)"
      >
        <Trash2 :size="15" />
        清空回收站
      </button>
    </section>

    <section class="retention-banner">
      <span><ShieldAlert :size="18" /></span>
      <div>
        <strong>自动清理策略</strong>
        <p>回收站内文件仍占用存储空间，恢复后会回到原来的位置。</p>
      </div>
      <span class="retention-banner__count">{{ trashFiles.length }} 个项目</span>
    </section>

    <section class="trash-panel">
      <header class="trash-panel__head">
        <div class="trash-tabs">
          <button
            type="button"
            :class="{ 'is-active': typeTab === 'all' }"
            @click="typeTab = 'all'"
          >
            全部
          </button>
          <button
            type="button"
            :class="{ 'is-active': typeTab === 'file' }"
            @click="typeTab = 'file'"
          >
            文件
          </button>
          <button
            type="button"
            :class="{ 'is-active': typeTab === 'folder' }"
            @click="typeTab = 'folder'"
          >
            文件夹
          </button>
        </div>

        <div v-if="selectedIds.length" class="bulk-actions">
          <span>已选 {{ selectedIds.length }} 项</span>
          <button type="button" @click="restore(selectedIds)">
            <RotateCcw :size="14" />
            恢复
          </button>
          <button
            class="is-danger"
            type="button"
            @click="emit('permanent-delete', selectedIds)"
          >
            <Trash2 :size="14" />
            永久删除
          </button>
          <button
            class="bulk-actions__close"
            type="button"
            title="取消选择"
            aria-label="取消选择"
            @click="selectedIds = []"
          >
            <X :size="15" />
          </button>
        </div>
      </header>

      <div v-if="trashFiles.length" class="trash-table">
        <div class="trash-table__head">
          <button
            class="table-check"
            :class="{ 'is-checked': allSelected }"
            type="button"
            title="全选"
            aria-label="全选"
            @click="toggleAll"
          />
          <span>名称</span>
          <span>删除时间</span>
          <span>大小</span>
          <span>剩余保留</span>
          <span />
        </div>
        <div
          v-for="file in trashFiles"
          :key="file.id"
          class="trash-row"
          :class="{ 'is-selected': selectedIds.includes(file.id) }"
        >
          <button
            class="table-check"
            :class="{ 'is-checked': selectedIds.includes(file.id) }"
            type="button"
            :title="selectedIds.includes(file.id) ? '取消选择' : '选择'"
            :aria-label="selectedIds.includes(file.id) ? '取消选择' : '选择'"
            @click="toggleSelected(file)"
          />
          <div class="trash-name">
            <FileGlyph :file="file" size="sm" />
            <div>
              <strong>{{ file.name }}</strong>
              <span>{{ file.kind === 'folder' ? '文件夹' : '个人文件' }}</span>
            </div>
          </div>
          <span class="trash-muted">{{ formatDate(file.deletedAt) }}</span>
          <span class="trash-muted">
            {{ file.kind === 'folder' ? '—' : formatBytes(file.size) }}
          </span>
          <span class="retention-time" :class="{ 'is-urgent': daysRemaining(file) < 7 }">
            <Clock3 :size="12" />
            {{ daysRemaining(file) }} 天
          </span>
          <div class="row-actions">
            <button
              type="button"
              title="恢复文件"
              aria-label="恢复文件"
              @click="restore([file.id])"
            >
              <RotateCcw :size="15" />
            </button>
            <button
              class="is-danger"
              type="button"
              title="永久删除"
              aria-label="永久删除"
              @click="emit('permanent-delete', [file.id])"
            >
              <Trash2 :size="15" />
            </button>
          </div>
        </div>
      </div>

      <div v-else class="trash-empty">
        <span><Trash2 :size="30" /></span>
        <h2>{{ searchQuery ? '没有找到匹配的文件' : '回收站是空的' }}</h2>
        <p>
          {{
            searchQuery
              ? '换一个关键词，或清除顶部搜索条件。'
              : '删除的文件会暂时出现在这里，并有 30 天恢复期。'
          }}
        </p>
        <button
          v-if="searchQuery"
          class="button button--secondary"
          type="button"
          @click="searchQuery = ''"
        >
          <Search :size="15" />
          清除搜索
        </button>
      </div>
    </section>
  </div>
</template>

<style scoped>
.trash-view {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.trash-hero {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 24px;
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

.trash-hero h1 {
  margin: 7px 0 6px;
  color: var(--text);
  font-size: clamp(28px, 2.8vw, 38px);
  font-weight: 600;
  letter-spacing: 0;
}

.trash-hero p {
  margin: 0;
  color: var(--text-soft);
  font-size: 12px;
}

.retention-banner {
  display: flex;
  align-items: center;
  gap: 11px;
  padding: 12px 15px;
  border: 1px solid var(--line);
  border-radius: 8px;
  background: #fbfaf6;
}

.retention-banner > span:first-child {
  display: grid;
  width: 33px;
  height: 33px;
  flex: 0 0 auto;
  place-items: center;
  border-radius: 7px;
  background: #f5eee4;
  color: #926a35;
}

.retention-banner > div {
  min-width: 0;
  flex: 1;
}

.retention-banner strong {
  color: var(--text);
  font-size: 10.5px;
  font-weight: 650;
}

.retention-banner p {
  margin: 2px 0 0;
  color: var(--text-soft);
  font-size: 9px;
}

.retention-banner__count {
  color: var(--text-soft);
  font-size: 9px;
}

.trash-panel {
  overflow: hidden;
  border: 1px solid var(--line);
  border-radius: 8px;
  background: #fbfbf9;
}

.trash-panel__head {
  display: flex;
  min-height: 56px;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 10px 15px;
  border-bottom: 1px solid var(--line);
}

.trash-tabs {
  display: flex;
  gap: 2px;
}

.trash-tabs button {
  height: 30px;
  padding: 0 11px;
  border: 0;
  border-radius: 0;
  background: transparent;
  color: var(--text-quiet);
  font: inherit;
  font-size: 9.5px;
  cursor: pointer;
}

.trash-tabs button:hover {
  background: transparent;
}

.trash-tabs button.is-active {
  background: transparent;
  color: var(--text);
  font-weight: 600;
  box-shadow: inset 0 -2px 0 var(--text);
}

.bulk-actions {
  display: flex;
  align-items: center;
  gap: 4px;
}

.bulk-actions > span {
  margin-right: 3px;
  color: var(--text-quiet);
  font-size: 9px;
}

.bulk-actions button {
  display: flex;
  height: 29px;
  align-items: center;
  gap: 4px;
  padding: 0 8px;
  border: 0;
  border-radius: 6px;
  background: var(--surface-muted);
  color: var(--text-soft);
  font: inherit;
  font-size: 9px;
  cursor: pointer;
}

.bulk-actions button:hover {
  background: var(--accent-wash);
  color: var(--accent-strong);
}

.bulk-actions button.is-danger {
  color: var(--danger);
}

.bulk-actions button.is-danger:hover {
  background: var(--danger-wash);
}

.bulk-actions button.bulk-actions__close {
  width: 29px;
  padding: 0;
  justify-content: center;
}

.trash-table__head,
.trash-row {
  display: grid;
  grid-template-columns: 20px minmax(240px, 1.6fr) minmax(120px, 0.75fr) minmax(70px, 0.45fr) minmax(90px, 0.55fr) 76px;
  align-items: center;
  gap: 14px;
  padding: 0 16px;
}

.trash-table__head {
  min-height: 38px;
  background: #f5f4ef;
  color: var(--text-quiet);
  font-size: 9.5px;
  font-weight: 600;
}

.trash-row {
  min-height: 68px;
  border-top: 1px solid var(--line);
}

.trash-row.is-selected {
  background: var(--accent-wash);
}

.table-check {
  width: 17px;
  height: 17px;
  padding: 0;
  border: 1px solid var(--line-strong);
  border-radius: 5px;
  background: #fff;
  cursor: pointer;
}

.table-check.is-checked {
  border-color: var(--accent);
  background:
    linear-gradient(135deg, transparent 42%, #fff 42%, #fff 55%, transparent 55%),
    linear-gradient(45deg, transparent 48%, #fff 48%, #fff 60%, transparent 60%),
    var(--accent);
}

.trash-name {
  display: flex;
  min-width: 0;
  align-items: center;
  gap: 10px;
}

.trash-name > div {
  display: flex;
  min-width: 0;
  flex-direction: column;
}

.trash-name strong {
  overflow: hidden;
  color: var(--text);
  font-size: 11.5px;
  font-weight: 600;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.trash-name span {
  margin-top: 2px;
  color: var(--text-quiet);
  font-size: 9px;
}

.trash-muted {
  color: var(--text-quiet);
  font-size: 9.5px;
}

.retention-time {
  display: flex;
  align-items: center;
  gap: 4px;
  color: var(--text-soft);
  font-size: 9.5px;
}

.retention-time.is-urgent {
  color: var(--danger);
}

.row-actions {
  display: flex;
  justify-content: flex-end;
  gap: 3px;
}

.row-actions button {
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

.row-actions button:hover {
  background: var(--surface-muted);
  color: var(--accent-strong);
}

.row-actions button.is-danger:hover {
  background: var(--danger-wash);
  color: var(--danger);
}

.trash-empty {
  display: flex;
  min-height: 340px;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 32px;
  text-align: center;
}

.trash-empty > span {
  display: grid;
  width: 68px;
  height: 68px;
  place-items: center;
  margin-bottom: 15px;
  border: 1px solid var(--line);
  border-radius: 19px;
  background: var(--surface-muted);
  color: var(--text-quiet);
}

.trash-empty h2 {
  margin: 0;
  color: var(--text);
  font-size: 16px;
}

.trash-empty p {
  margin: 7px 0 18px;
  color: var(--text-quiet);
  font-size: 10px;
}

@media (max-width: 900px) {
  .trash-table__head {
    display: none;
  }

  .trash-row {
    grid-template-columns: 20px minmax(180px, 1fr) 90px 70px;
  }

  .trash-row > .trash-muted:nth-of-type(2) {
    display: none;
  }
}

@media (max-width: 640px) {
  .trash-hero {
    align-items: flex-start;
    flex-direction: column;
  }

  .retention-banner__count {
    display: none;
  }

  .trash-panel__head {
    align-items: flex-start;
    flex-direction: column;
  }

  .trash-row {
    grid-template-columns: 20px minmax(140px, 1fr) 62px;
    gap: 9px;
    min-height: 74px;
    padding: 0 11px;
  }

  .trash-row .trash-muted,
  .trash-row .retention-time {
    display: none;
  }
}
</style>
