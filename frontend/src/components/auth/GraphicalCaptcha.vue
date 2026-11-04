<script setup>
import { onMounted, ref } from 'vue'
import { LoaderCircle, RefreshCw } from '@lucide/vue'
import { captcha as captchaApi } from '../../services/authApi'

const props = defineProps({
  modelValue: {
    type: String,
    default: '',
  },
  captchaId: {
    type: String,
    default: '',
  },
})

const emit = defineEmits(['update:modelValue', 'update:captchaId'])

const image = ref('')
const loading = ref(false)
const error = ref('')
let requestVersion = 0

async function refresh() {
  requestVersion += 1
  const currentRequest = requestVersion
  loading.value = true
  error.value = ''
  image.value = ''
  emit('update:modelValue', '')
  emit('update:captchaId', '')
  try {
    const challenge = await captchaApi()
    if (currentRequest !== requestVersion) return
    image.value = challenge.image
    emit('update:captchaId', challenge.captchaId)
  } catch (failure) {
    if (currentRequest === requestVersion) {
      error.value = failure.message || '验证码加载失败'
    }
  } finally {
    if (currentRequest === requestVersion) loading.value = false
  }
}

function updateAnswer(event) {
  emit('update:modelValue', event.target.value.toUpperCase())
}

onMounted(refresh)

defineExpose({ refresh })
</script>

<template>
  <div class="graphical-captcha">
    <div class="graphical-captcha__field">
      <input
        :value="modelValue"
        type="text"
        inputmode="text"
        autocomplete="off"
        autocapitalize="characters"
        spellcheck="false"
        maxlength="5"
        placeholder="输入图中字符"
        aria-label="图形验证码"
        required
        @input="updateAnswer"
      />
    </div>
    <button
      class="graphical-captcha__image"
      type="button"
      title="点击刷新验证码"
      aria-label="点击刷新图形验证码"
      :disabled="loading"
      @click="refresh"
    >
      <img v-if="image" :src="image" alt="图形验证码" />
      <LoaderCircle v-else-if="loading" :size="18" class="is-spinning" />
      <RefreshCw v-else :size="18" />
      <span class="graphical-captcha__refresh">
        <RefreshCw :size="12" />
      </span>
    </button>
    <p v-if="error" class="graphical-captcha__error">{{ error }}</p>
  </div>
</template>

<style scoped>
.graphical-captcha {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 148px;
  gap: 8px;
}

.graphical-captcha__field {
  display: flex;
  min-width: 0;
  height: 48px;
  align-items: center;
  padding: 0 12px;
  border: 1px solid var(--line);
  border-radius: 8px;
  background: #fff;
}

.graphical-captcha__field:focus-within {
  border-color: var(--accent-line);
  box-shadow: 0 0 0 3px var(--accent-ring);
}

.graphical-captcha__field input {
  width: 100%;
  border: 0;
  outline: 0;
  background: transparent;
  color: var(--text);
  font: inherit;
  font-size: 11.5px;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

.graphical-captcha__image {
  position: relative;
  display: grid;
  height: 48px;
  place-items: center;
  padding: 0;
  overflow: hidden;
  border: 1px solid var(--line);
  border-radius: 8px;
  background: #eef2f0;
  color: var(--text-quiet);
  cursor: pointer;
}

.graphical-captcha__image img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.graphical-captcha__image:disabled {
  cursor: wait;
}

.graphical-captcha__refresh {
  position: absolute;
  right: 4px;
  bottom: 4px;
  display: grid;
  width: 20px;
  height: 20px;
  place-items: center;
  border-radius: 5px;
  background: rgb(255 255 255 / 88%);
  color: #34433d;
  box-shadow: 0 1px 4px rgb(22 34 29 / 14%);
}

.graphical-captcha__error {
  grid-column: 1 / -1;
  margin: -2px 0 0;
  color: var(--danger);
  font-size: 9px;
}

.is-spinning {
  animation: captcha-spin 800ms linear infinite;
}

@keyframes captcha-spin {
  to {
    transform: rotate(360deg);
  }
}

@media (max-width: 440px) {
  .graphical-captcha {
    grid-template-columns: minmax(0, 1fr) 128px;
  }
}
</style>
