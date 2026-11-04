<script setup>
import { computed } from 'vue'
import {
  Check,
  ChevronDown,
  LoaderCircle,
  UploadCloud,
  X,
} from '@lucide/vue'
import { formatBytes } from '../../utils/formatters'

const props = defineProps({
  uploads: {
    type: Array,
    default: () => [],
  },
})

const emit = defineEmits(['clear'])

const activeCount = computed(
  () => props.uploads.filter((upload) => upload.status === 'uploading').length,
)

const doneCount = computed(
  () => props.uploads.filter((upload) => upload.status === 'done').length,
)
</script>

<template>
  <Transition name="tray">
    <section v-if="uploads.length" class="upload-tray">
      <header class="upload-tray__head">
        <span class="upload-tray__icon">
          <LoaderCircle v-if="activeCount" class="spin" :size="17" />
          <Check v-else :size="17" />
        </span>
        <div>
          <strong>
            {{ activeCount ? `正在上传 ${activeCount} 个文件` : '上传完成' }}
          </strong>
          <span>
            {{ doneCount ? `${doneCount} 个已完成` : '正在建立安全连接' }}
          </span>
        </div>
        <button
          type="button"
          title="收起上传列表"
          aria-label="收起上传列表"
          @click="emit('clear')"
        >
          <ChevronDown :size="16" />
        </button>
      </header>
      <div class="upload-tray__list">
        <div v-for="upload in uploads" :key="upload.id" class="upload-item">
          <span class="upload-item__format" :style="{ color: upload.format.color }">
            <UploadCloud :size="16" />
          </span>
          <div class="upload-item__info">
            <div>
              <strong :title="upload.name">{{ upload.name }}</strong>
              <span>{{ formatBytes(upload.size) }}</span>
            </div>
            <div class="upload-item__track">
              <span
                :class="{
                  'is-done': upload.status === 'done',
                  'is-failed': upload.status === 'failed',
                }"
                :style="{ width: `${upload.progress}%` }"
              />
            </div>
          </div>
          <span class="upload-item__progress">
            {{
              upload.status === 'done'
                ? '完成'
                : upload.status === 'failed'
                  ? '失败'
                  : upload.detail || `${upload.progress}%`
            }}
          </span>
        </div>
      </div>
    </section>
  </Transition>
</template>

<style scoped>
.upload-tray {
  position: fixed;
  z-index: 950;
  right: 22px;
  bottom: 22px;
  width: min(380px, calc(100vw - 28px));
  overflow: hidden;
  border: 1px solid var(--line);
  border-radius: 9px;
  background: #fff;
  box-shadow: 0 20px 60px rgb(14 32 26 / 18%);
}

.upload-tray__head {
  display: grid;
  grid-template-columns: 34px minmax(0, 1fr) 28px;
  align-items: center;
  gap: 8px;
  padding: 12px 12px 11px;
  border-bottom: 1px solid var(--line);
}

.upload-tray__icon {
  display: grid;
  width: 34px;
  height: 34px;
  place-items: center;
  border-radius: 8px;
  background: var(--accent-wash);
  color: var(--accent-strong);
}

.upload-tray__head > div {
  display: flex;
  flex-direction: column;
}

.upload-tray__head strong {
  color: var(--text);
  font-size: 11.5px;
  font-weight: 650;
}

.upload-tray__head span {
  color: var(--text-quiet);
  font-size: 9.5px;
}

.upload-tray__head button {
  display: grid;
  width: 28px;
  height: 28px;
  place-items: center;
  padding: 0;
  border: 0;
  border-radius: 6px;
  background: transparent;
  color: var(--text-quiet);
  cursor: pointer;
}

.upload-tray__head button:hover {
  background: var(--surface-muted);
}

.upload-tray__list {
  max-height: 240px;
  overflow: auto;
  padding: 4px 0;
}

.upload-item {
  display: grid;
  grid-template-columns: 28px minmax(0, 1fr) 38px;
  align-items: center;
  gap: 8px;
  padding: 7px 12px;
}

.upload-item__format {
  display: grid;
  place-items: center;
}

.upload-item__info > div:first-child {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
}

.upload-item__info strong {
  overflow: hidden;
  color: var(--text-soft);
  font-size: 10.5px;
  font-weight: 550;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.upload-item__info span {
  flex: 0 0 auto;
  color: var(--text-quiet);
  font-size: 8.5px;
}

.upload-item__track {
  height: 3px;
  margin-top: 5px;
  overflow: hidden;
  border-radius: 4px;
  background: var(--line);
}

.upload-item__track span {
  display: block;
  height: 100%;
  border-radius: inherit;
  background: var(--accent);
  transition: width 260ms ease;
}

.upload-item__track span.is-done {
  background: #6cae8f;
}

.upload-item__track span.is-failed {
  background: var(--danger);
}

.upload-item__progress {
  color: var(--text-quiet);
  font-size: 9px;
  text-align: right;
}

.spin {
  animation: spin 900ms linear infinite;
}

@keyframes spin {
  to {
    transform: rotate(360deg);
  }
}

.tray-enter-active,
.tray-leave-active {
  transition:
    opacity 180ms ease,
    transform 180ms ease;
}

.tray-enter-from,
.tray-leave-to {
  opacity: 0;
  transform: translateY(12px);
}

@media (max-width: 640px) {
  .upload-tray {
    right: 12px;
    bottom: 12px;
  }
}
</style>
