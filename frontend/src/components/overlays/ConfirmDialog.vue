<script setup>
import { AlertTriangle, Trash2 } from '@lucide/vue'
import AppModal from './AppModal.vue'

const props = defineProps({
  open: {
    type: Boolean,
    default: false,
  },
  title: {
    type: String,
    default: '确认操作',
  },
  description: {
    type: String,
    default: '',
  },
  confirmText: {
    type: String,
    default: '确认',
  },
  tone: {
    type: String,
    default: 'danger',
  },
})

const emit = defineEmits(['close', 'confirm'])
</script>

<template>
  <AppModal
    :open="open"
    :title="title"
    size="sm"
    :close-on-backdrop="false"
    @close="emit('close')"
  >
    <div class="confirm-content">
      <span class="confirm-content__icon" :class="`is-${tone}`">
        <Trash2 v-if="tone === 'danger'" :size="22" />
        <AlertTriangle v-else :size="22" />
      </span>
      <p>{{ description }}</p>
    </div>
    <template #footer>
      <button class="button button--ghost" type="button" @click="emit('close')">
        取消
      </button>
      <button
        class="button"
        :class="tone === 'danger' ? 'button--danger' : 'button--primary'"
        type="button"
        @click="emit('confirm')"
      >
        {{ confirmText }}
      </button>
    </template>
  </AppModal>
</template>

<style scoped>
.confirm-content {
  display: flex;
  align-items: flex-start;
  gap: 14px;
}

.confirm-content__icon {
  display: grid;
  width: 46px;
  height: 46px;
  flex: 0 0 auto;
  place-items: center;
  border-radius: 12px;
}

.confirm-content__icon.is-danger {
  background: var(--danger-wash);
  color: var(--danger);
}

.confirm-content__icon.is-warning {
  background: #fff5dc;
  color: #a66c07;
}

.confirm-content p {
  margin: 4px 0 0;
  color: var(--text-soft);
  font-size: 12.5px;
  line-height: 1.7;
}
</style>
