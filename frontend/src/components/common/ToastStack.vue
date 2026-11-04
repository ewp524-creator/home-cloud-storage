<script setup>
import { CheckCircle2, Info, TriangleAlert, X } from '@lucide/vue'

defineProps({
  toasts: {
    type: Array,
    default: () => [],
  },
})

const emit = defineEmits(['dismiss'])

function getIcon(tone) {
  if (tone === 'warning') return TriangleAlert
  if (tone === 'info') return Info
  return CheckCircle2
}
</script>

<template>
  <Teleport to="body">
    <div class="toast-stack" aria-live="polite">
      <TransitionGroup name="toast">
        <div
          v-for="toast in toasts"
          :key="toast.id"
          class="toast-item"
          :class="`is-${toast.tone}`"
        >
          <component :is="getIcon(toast.tone)" :size="17" />
          <span>{{ toast.message }}</span>
          <button
            type="button"
            title="关闭提示"
            aria-label="关闭提示"
            @click="emit('dismiss', toast.id)"
          >
            <X :size="14" />
          </button>
        </div>
      </TransitionGroup>
    </div>
  </Teleport>
</template>

<style scoped>
.toast-stack {
  position: fixed;
  z-index: 1500;
  top: 82px;
  right: 20px;
  display: flex;
  width: min(360px, calc(100vw - 32px));
  flex-direction: column;
  gap: 8px;
  pointer-events: none;
}

.toast-item {
  display: grid;
  grid-template-columns: 20px minmax(0, 1fr) 24px;
  align-items: center;
  gap: 9px;
  padding: 11px 10px 11px 13px;
  border: 1px solid var(--line);
  border-radius: 8px;
  background: #fff;
  color: var(--text-soft);
  box-shadow: 0 14px 36px rgb(18 34 29 / 15%);
  font-size: 11.5px;
  pointer-events: auto;
}

.toast-item.is-success > svg {
  color: var(--accent);
}

.toast-item.is-info > svg {
  color: #397bd9;
}

.toast-item.is-warning > svg {
  color: #b8750b;
}

.toast-item button {
  display: grid;
  width: 24px;
  height: 24px;
  place-items: center;
  padding: 0;
  border: 0;
  border-radius: 5px;
  background: transparent;
  color: var(--text-quiet);
  cursor: pointer;
}

.toast-item button:hover {
  background: var(--surface-muted);
}

.toast-enter-active,
.toast-leave-active {
  transition:
    opacity 180ms ease,
    transform 180ms ease;
}

.toast-enter-from,
.toast-leave-to {
  opacity: 0;
  transform: translateX(14px);
}

@media (max-width: 640px) {
  .toast-stack {
    top: auto;
    right: 12px;
    bottom: 88px;
    left: 12px;
    width: auto;
  }
}
</style>
