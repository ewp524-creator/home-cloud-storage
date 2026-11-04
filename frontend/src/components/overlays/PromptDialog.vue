<script setup>
import { nextTick, ref, watch } from 'vue'
import AppModal from './AppModal.vue'

const props = defineProps({
  open: {
    type: Boolean,
    default: false,
  },
  title: {
    type: String,
    default: '新建文件夹',
  },
  label: {
    type: String,
    default: '名称',
  },
  initialValue: {
    type: String,
    default: '',
  },
  placeholder: {
    type: String,
    default: '',
  },
  confirmText: {
    type: String,
    default: '确定',
  },
})

const emit = defineEmits(['close', 'submit'])
const value = ref('')
const input = ref(null)

watch(
  () => props.open,
  async (open) => {
    if (!open) return
    value.value = props.initialValue
    await nextTick()
    input.value?.focus()
    input.value?.select()
  },
)

function submit() {
  if (!value.value.trim()) return
  emit('submit', value.value.trim())
}
</script>

<template>
  <AppModal
    :open="open"
    :title="title"
    size="sm"
    @close="emit('close')"
  >
    <form class="prompt-form" @submit.prevent="submit">
      <label for="prompt-name">{{ label }}</label>
      <input
        id="prompt-name"
        ref="input"
        v-model="value"
        class="text-input"
        type="text"
        maxlength="80"
        :placeholder="placeholder"
      />
    </form>
    <template #footer>
      <button class="button button--ghost" type="button" @click="emit('close')">
        取消
      </button>
      <button
        class="button button--primary"
        type="button"
        :disabled="!value.trim()"
        @click="submit"
      >
        {{ confirmText }}
      </button>
    </template>
  </AppModal>
</template>

<style scoped>
.prompt-form {
  display: flex;
  flex-direction: column;
  gap: 7px;
}

.prompt-form label {
  color: var(--text-soft);
  font-size: 11.5px;
  font-weight: 630;
}
</style>
