<script setup>
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { AlertCircle, LoaderCircle, RotateCw } from '@lucide/vue'

const props = defineProps({
  sourceUrl: {
    type: String,
    required: true,
  },
  fileName: {
    type: String,
    default: '',
  },
})

const MAX_DOCX_BYTES = 30 * 1024 * 1024
const container = ref(null)
const loading = ref(false)
const error = ref('')
let requestController = null
let renderVersion = 0

async function renderDocument() {
  if (!props.sourceUrl || !container.value) return
  renderVersion += 1
  const version = renderVersion
  requestController?.abort()
  requestController = new AbortController()
  loading.value = true
  error.value = ''
  container.value.replaceChildren()
  try {
    const response = await fetch(props.sourceUrl, {
      credentials: 'same-origin',
      signal: requestController.signal,
    })
    if (!response.ok) throw new Error(`HTTP ${response.status}`)
    const blob = await response.blob()
    if (blob.size > MAX_DOCX_BYTES) {
      throw new Error('DOCX 文件超过 30 MB，请下载后使用本地 Word 打开')
    }
    if (version !== renderVersion) return
    const { renderAsync } = await import('docx-preview')
    if (version !== renderVersion) return
    await renderAsync(blob, container.value, null, {
      className: 'filehome-docx',
      inWrapper: true,
      breakPages: true,
      ignoreLastRenderedPageBreak: true,
      useBase64URL: true,
      renderHeaders: true,
      renderFooters: true,
      renderFootnotes: true,
      renderEndnotes: true,
      renderAltChunks: false,
      debug: false,
    })
    sanitizeRenderedDocument(container.value)
  } catch (failure) {
    if (failure.name !== 'AbortError') {
      error.value = failure.message || 'Word 文档渲染失败'
    }
  } finally {
    if (version === renderVersion) loading.value = false
  }
}

function sanitizeRenderedDocument(root) {
  root.querySelectorAll('script,iframe,object,embed').forEach((node) => {
    node.remove()
  })
  root.querySelectorAll('a').forEach((anchor) => {
    const href = anchor.getAttribute('href') || ''
    if (!/^(https?:|mailto:)/i.test(href)) {
      anchor.removeAttribute('href')
      return
    }
    anchor.target = '_blank'
    anchor.rel = 'noopener noreferrer'
  })
  root.querySelectorAll('img').forEach((image) => {
    const source = image.getAttribute('src') || ''
    if (!source.startsWith('data:') && !source.startsWith('blob:')) {
      image.removeAttribute('src')
    }
  })
}

watch(
  () => [props.sourceUrl, props.fileName],
  renderDocument,
)

onMounted(renderDocument)

onBeforeUnmount(() => {
  renderVersion += 1
  requestController?.abort()
})
</script>

<template>
  <section class="docx-preview">
    <header class="docx-preview__bar">
      <div>
        <strong>{{ fileName }}</strong>
        <span>只读 Word 预览</span>
      </div>
      <span class="docx-preview__badge">DOCX</span>
    </header>
    <div v-if="loading" class="docx-preview__state">
      <LoaderCircle :size="22" class="is-spinning" />
      正在安全渲染 Word 文档
    </div>
    <div v-else-if="error" class="docx-preview__state is-error">
      <AlertCircle :size="22" />
      <span>{{ error }}</span>
      <button type="button" @click="renderDocument">
        <RotateCw :size="14" />
        重新渲染
      </button>
    </div>
    <div ref="container" class="docx-preview__document" />
  </section>
</template>

<style scoped>
.docx-preview {
  display: flex;
  width: min(1080px, 100%);
  min-height: 70vh;
  flex-direction: column;
  overflow: hidden;
  border: 1px solid var(--line);
  border-radius: 8px;
  background: #e9ecea;
}

.docx-preview__bar {
  display: flex;
  min-height: 52px;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 9px 16px;
  border-bottom: 1px solid var(--line);
  background: #fff;
}

.docx-preview__bar > div {
  display: flex;
  min-width: 0;
  flex-direction: column;
}

.docx-preview__bar strong {
  overflow: hidden;
  color: var(--text);
  font-size: 12px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.docx-preview__bar span {
  color: var(--text-quiet);
  font-size: 9.5px;
}

.docx-preview__badge {
  padding: 4px 7px;
  border-radius: 4px;
  background: #2d67c7;
  color: #fff !important;
  font-size: 8px !important;
  font-weight: 750;
}

.docx-preview__state {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 9px;
  padding: 18px;
  background: #fff;
  color: var(--text-soft);
  font-size: 11px;
}

.docx-preview__state.is-error {
  flex-wrap: wrap;
  color: var(--danger);
}

.docx-preview__state button {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  padding: 5px 9px;
  border: 1px solid color-mix(in srgb, var(--danger) 30%, var(--line));
  border-radius: 6px;
  background: #fff;
  color: var(--danger);
  font: inherit;
  font-size: 10px;
  cursor: pointer;
}

.docx-preview__document {
  max-height: calc(82vh - 52px);
  flex: 1;
  overflow: auto;
  padding: 24px 16px;
}

.docx-preview__document :deep(.docx-wrapper) {
  padding: 0;
  background: transparent;
}

.docx-preview__document :deep(.docx) {
  margin: 0 auto 18px;
  box-shadow: 0 5px 22px rgb(24 31 28 / 13%);
}

.is-spinning {
  animation: docx-spin 800ms linear infinite;
}

@keyframes docx-spin {
  to {
    transform: rotate(360deg);
  }
}
</style>
