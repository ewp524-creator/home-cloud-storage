<script setup>
import { onBeforeUnmount, onMounted, watch } from 'vue'
import { X } from '@lucide/vue'

const props = defineProps({
  open: {
    type: Boolean,
    default: false,
  },
  title: {
    type: String,
    default: '',
  },
  description: {
    type: String,
    default: '',
  },
  size: {
    type: String,
    default: 'md',
    validator: (value) => ['sm', 'md', 'lg', 'xl'].includes(value),
  },
  closeOnBackdrop: {
    type: Boolean,
    default: true,
  },
})

const emit = defineEmits(['close'])

function close() {
  emit('close')
}

function onKeydown(event) {
  if (props.open && event.key === 'Escape') close()
}

watch(
  () => props.open,
  (open) => {
    if (typeof document === 'undefined') return
    document.body.classList.toggle('has-modal', open)
  },
)

onMounted(() => document.addEventListener('keydown', onKeydown))
onBeforeUnmount(() => {
  document.removeEventListener('keydown', onKeydown)
  document.body.classList.remove('has-modal')
})
</script>

<template>
  <Teleport to="body">
    <Transition name="modal">
      <div
        v-if="open"
        class="modal-layer"
        @mousedown.self="closeOnBackdrop && close()"
      >
        <section
          class="modal-panel"
          :class="`modal-panel--${size}`"
          role="dialog"
          aria-modal="true"
          :aria-label="title"
        >
          <header class="modal-panel__head">
            <div>
              <h2>{{ title }}</h2>
              <p v-if="description">{{ description }}</p>
            </div>
            <button
              class="icon-button"
              type="button"
              title="关闭"
              aria-label="关闭"
              @click="close"
            >
              <X :size="18" />
            </button>
          </header>
          <div class="modal-panel__body">
            <slot />
          </div>
          <footer v-if="$slots.footer" class="modal-panel__footer">
            <slot name="footer" />
          </footer>
        </section>
      </div>
    </Transition>
  </Teleport>
</template>

<style scoped>
.modal-layer {
  position: fixed;
  z-index: 1400;
  inset: 0;
  display: grid;
  place-items: center;
  padding: 24px;
  background: rgb(23 25 23 / 42%);
  backdrop-filter: blur(5px);
}

.modal-panel {
  width: min(100%, 560px);
  max-height: min(86dvh, 780px);
  overflow: hidden;
  border: 1px solid rgb(255 255 255 / 60%);
  border-radius: 10px;
  background: #fff;
  box-shadow: 0 30px 90px rgb(9 24 19 / 28%);
}

.modal-panel--sm {
  width: min(100%, 420px);
}

.modal-panel--lg {
  width: min(100%, 760px);
}

.modal-panel--xl {
  width: min(100%, 1040px);
}

.modal-panel__head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  padding: 22px 24px 17px;
  border-bottom: 1px solid var(--line);
}

.modal-panel__head h2 {
  margin: 0;
  color: var(--text);
  font-size: 17px;
  font-weight: 690;
  letter-spacing: 0;
}

.modal-panel__head p {
  margin: 5px 0 0;
  color: var(--text-quiet);
  font-size: 11.5px;
  line-height: 1.55;
}

.modal-panel__body {
  max-height: calc(86dvh - 144px);
  overflow: auto;
  padding: 22px;
}

.modal-panel__footer {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 9px;
  padding: 15px 24px;
  border-top: 1px solid var(--line);
  background: var(--surface-muted);
}

.modal-enter-active,
.modal-leave-active {
  transition: opacity 180ms ease;
}

.modal-enter-active .modal-panel,
.modal-leave-active .modal-panel {
  transition:
    transform 180ms ease,
    opacity 180ms ease;
}

.modal-enter-from,
.modal-leave-to {
  opacity: 0;
}

.modal-enter-from .modal-panel,
.modal-leave-to .modal-panel {
  opacity: 0;
  transform: translateY(8px) scale(0.985);
}

@media (max-width: 640px) {
  .modal-layer {
    align-items: end;
    padding: 0;
  }

  .modal-panel,
  .modal-panel--sm,
  .modal-panel--lg,
  .modal-panel--xl {
    width: 100%;
    max-height: 92dvh;
    border-radius: 16px 16px 0 0;
  }
}
</style>
