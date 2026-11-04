<script setup>
import { computed, ref, watch } from 'vue'
import {
  AlertCircle,
  Check,
  Copy,
  FileQuestion,
  LoaderCircle,
  LockKeyhole,
  RotateCw,
} from '@lucide/vue'
import { getFileFormat } from '../../utils/fileFormats'
import { formatBytes } from '../../utils/formatters'
import { highlightSource } from '../../utils/sourceHighlighter'
import FileGlyph from './FileGlyph.vue'
import DocxPreview from './DocxPreview.vue'

const props = defineProps({
  file: {
    type: Object,
    default: null,
  },
  sourceUrl: {
    type: String,
    default: '',
  },
  previewKind: {
    type: String,
    default: '',
  },
  loading: {
    type: Boolean,
    default: false,
  },
  locked: {
    type: Boolean,
    default: false,
  },
  compact: {
    type: Boolean,
    default: false,
  },
})

const emit = defineEmits(['unlock', 'retry'])

const TEXT_LIMIT = 2 * 1024 * 1024
const mediaError = ref('')
const sourceLoading = ref(false)
const sourceError = ref('')
const sourceText = ref('')
const sourceTruncated = ref(false)
const copied = ref(false)
let sourceController = null

const format = computed(() => (props.file ? getFileFormat(props.file) : null))
const resolvedKind = computed(
  () => props.previewKind || format.value?.kind || 'unknown',
)
const source = computed(() =>
  highlightSource(
    sourceText.value,
    format.value?.extension || '',
    resolvedKind.value,
  ),
)
const isTextLike = computed(() =>
  ['code', 'text'].includes(resolvedKind.value),
)
const isDocx = computed(
  () => String(format.value?.extension || '').toLowerCase() === 'docx',
)
const showNativePreview = computed(() =>
  ['image', 'video', 'audio', 'pdf'].includes(resolvedKind.value),
)
const unsupportedLabel = computed(() => {
  if (['word', 'excel', 'slides', 'design', 'archive', 'app'].includes(
    resolvedKind.value,
  )) {
    return `${format.value?.label || '该格式'}暂不提供在线渲染`
  }
  return '当前浏览器无法可靠预览该格式'
})

function stopSourceRequest() {
  sourceController?.abort()
  sourceController = null
}

async function loadTextSource() {
  stopSourceRequest()
  sourceText.value = ''
  sourceError.value = ''
  sourceTruncated.value = false
  if (!isTextLike.value || !props.sourceUrl || props.locked) return

  sourceLoading.value = true
  sourceController = new AbortController()
  try {
    const headers = new Headers()
    if ((props.file?.size || 0) > TEXT_LIMIT) {
      headers.set('Range', `bytes=0-${TEXT_LIMIT - 1}`)
    }
    const response = await fetch(props.sourceUrl, {
      headers,
      signal: sourceController.signal,
    })
    if (!response.ok && response.status !== 206) {
      throw new Error(`HTTP ${response.status}`)
    }
    const buffer = await response.arrayBuffer()
    sourceText.value = new TextDecoder('utf-8', { fatal: false }).decode(buffer)
    sourceTruncated.value =
      response.status === 206 || buffer.byteLength >= TEXT_LIMIT
  } catch (error) {
    if (error.name !== 'AbortError') {
      sourceError.value = error.message || '文本读取失败'
    }
  } finally {
    sourceLoading.value = false
    sourceController = null
  }
}

async function copySource() {
  if (!sourceText.value) return
  try {
    await navigator.clipboard.writeText(sourceText.value)
    copied.value = true
    globalThis.setTimeout(() => {
      copied.value = false
    }, 1400)
  } catch {
    sourceError.value = '浏览器未允许写入剪贴板'
  }
}

function onMediaError() {
  mediaError.value = '文件已读取，但当前浏览器不支持该编码或格式'
}

watch(
  () => [props.sourceUrl, props.locked, resolvedKind.value, props.file?.id],
  () => {
    mediaError.value = ''
    loadTextSource()
  },
  { immediate: true },
)
</script>

<template>
  <div
    class="readonly-preview"
    :class="[`is-${resolvedKind}`, { 'is-compact': compact }]"
  >
    <div v-if="loading" class="preview-state">
      <LoaderCircle :size="24" class="is-spinning" />
      <strong>正在建立安全预览</strong>
      <p>签名只对当前文件有效，并在短时间后自动失效。</p>
    </div>

    <div v-else-if="locked" class="preview-state preview-state--locked">
      <span class="preview-state__icon"><LockKeyhole :size="25" /></span>
      <strong>需要提取码才能预览</strong>
      <p>预览和下载使用同一份访问权限，内容不会公开缓存。</p>
      <button type="button" @click="emit('unlock')">
        输入提取码
      </button>
    </div>

    <div v-else-if="!file" class="preview-state">
      <AlertCircle :size="24" />
      <strong>文件信息不可用</strong>
      <p>请关闭预览后重新打开。</p>
    </div>

    <DocxPreview
      v-else-if="isDocx && sourceUrl"
      :source-url="sourceUrl"
      :file-name="file.name"
    />

    <template v-else-if="showNativePreview">
      <div v-if="mediaError" class="preview-state">
        <AlertCircle :size="24" />
        <strong>{{ mediaError }}</strong>
        <p>可以下载原文件后使用本地播放器或图片应用打开。</p>
        <button type="button" @click="emit('retry')">
          <RotateCw :size="14" />
          重新加载
        </button>
      </div>

      <iframe
        v-else-if="resolvedKind === 'pdf'"
        class="pdf-frame"
        :src="`${sourceUrl}#toolbar=1&navpanes=0&view=FitH`"
        title="PDF 只读预览"
      />

      <img
        v-else-if="resolvedKind === 'image'"
        class="image-view"
        :src="sourceUrl"
        :alt="file.name"
        decoding="async"
        @error="onMediaError"
      />

      <video
        v-else-if="resolvedKind === 'video'"
        class="video-view"
        :src="sourceUrl"
        controls
        controlslist="nodownload noremoteplayback"
        playsinline
        preload="metadata"
        @error="onMediaError"
      />

      <div v-else class="audio-view">
        <FileGlyph :file="file" size="xl" />
        <strong>{{ file.name }}</strong>
        <audio
          :src="sourceUrl"
          controls
          controlslist="nodownload"
          preload="metadata"
          @error="onMediaError"
        />
      </div>
    </template>

    <div v-else-if="isTextLike" class="source-view">
      <header class="source-view__bar">
        <span class="source-view__dots"><i /><i /><i /></span>
        <div>
          <strong>{{ file.name }}</strong>
          <small>
            {{ source.languageLabel }}
            <template v-if="sourceText">
              · {{ source.lineCount }} 行
            </template>
          </small>
        </div>
        <span class="readonly-chip">只读</span>
        <button
          type="button"
          :disabled="!sourceText"
          title="复制全文"
          aria-label="复制全文"
          @click="copySource"
        >
          <Check v-if="copied" :size="14" />
          <Copy v-else :size="14" />
        </button>
      </header>

      <div v-if="sourceLoading" class="source-state">
        <LoaderCircle :size="19" class="is-spinning" />
        正在读取真实文件内容...
      </div>
      <div v-else-if="sourceError" class="source-state is-error">
        <AlertCircle :size="18" />
        {{ sourceError }}
      </div>
      <div v-else-if="!sourceText" class="source-state">
        文件为空。
      </div>
      <div v-else class="source-code">
        <div class="source-code__gutter" aria-hidden="true">
          <span
            v-for="(_, index) in source.highlighted"
            :key="index"
          >{{ index + 1 }}</span>
        </div>
        <pre><code><span
          v-for="(line, index) in source.highlighted"
          :key="index"
          class="source-line"
          v-html="line || ' '"
        /></code></pre>
      </div>
      <footer
        v-if="sourceTruncated || source.truncatedByLines"
        class="source-view__notice"
      >
        文件较大，仅展示开头部分；下载后可查看完整内容。
      </footer>
      <footer v-else class="source-view__notice">
        内容来自原始文件 · {{ formatBytes(file.size) }} · 无法在此修改
      </footer>
    </div>

    <div v-else class="preview-state preview-state--unsupported">
      <span class="preview-state__icon">
        <FileQuestion :size="27" />
      </span>
      <strong>{{ unsupportedLabel }}</strong>
      <p>这里不会显示占位或伪造内容。需要查看时请下载原文件。</p>
      <div class="unsupported-meta">
        <span>{{ format?.label || 'UNKNOWN' }}</span>
        <span>{{ formatBytes(file.size) }}</span>
      </div>
    </div>
  </div>
</template>

<style scoped>
.readonly-preview {
  position: relative;
  display: flex;
  width: min(1120px, 100%);
  min-height: min(720px, calc(100dvh - 152px));
  align-items: center;
  justify-content: center;
  margin: auto;
}

.readonly-preview.is-compact {
  min-height: 500px;
}

.preview-state {
  display: flex;
  width: min(440px, 100%);
  min-height: 280px;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 36px;
  border: 1px solid rgb(255 255 255 / 9%);
  border-radius: 12px;
  background: rgb(25 35 32 / 86%);
  color: rgb(231 243 238 / 55%);
  text-align: center;
}

.preview-state--locked,
.preview-state--unsupported {
  background: #f6f5f0;
  color: #777d78;
}

.preview-state__icon {
  display: grid;
  width: 58px;
  height: 58px;
  place-items: center;
  margin-bottom: 17px;
  border-radius: 50%;
  background: rgb(255 255 255 / 8%);
  color: #8fa9a0;
}

.preview-state--locked .preview-state__icon,
.preview-state--unsupported .preview-state__icon {
  background: #e8e8e1;
  color: #737b76;
}

.preview-state > svg {
  margin-bottom: 13px;
  color: #8fa9a0;
}

.preview-state strong {
  color: #edf6f2;
  font-size: 14px;
  font-weight: 620;
}

.preview-state--locked strong,
.preview-state--unsupported strong {
  color: #252a27;
}

.preview-state p {
  max-width: 330px;
  margin: 7px 0 0;
  color: rgb(229 242 237 / 40%);
  font-size: 10px;
  line-height: 1.6;
}

.preview-state--locked p,
.preview-state--unsupported p {
  color: #888e89;
}

.preview-state button {
  display: flex;
  height: 35px;
  align-items: center;
  gap: 6px;
  margin-top: 18px;
  padding: 0 13px;
  border: 1px solid #343a36;
  border-radius: 7px;
  background: #252a27;
  color: #fff;
  font: inherit;
  font-size: 10px;
  cursor: pointer;
}

.preview-state--locked button {
  border-color: #252a27;
}

.pdf-frame {
  width: 100%;
  height: min(760px, calc(100dvh - 154px));
  border: 1px solid rgb(255 255 255 / 10%);
  border-radius: 8px;
  background: #fff;
}

.image-view {
  display: block;
  max-width: 100%;
  max-height: calc(100dvh - 168px);
  border-radius: 8px;
  object-fit: contain;
  box-shadow: 0 28px 80px rgb(0 0 0 / 36%);
}

.video-view {
  display: block;
  width: min(980px, 100%);
  max-height: calc(100dvh - 180px);
  border-radius: 9px;
  background: #050807;
  box-shadow: 0 28px 80px rgb(0 0 0 / 38%);
}

.audio-view {
  display: flex;
  width: min(560px, 100%);
  flex-direction: column;
  align-items: center;
  gap: 16px;
  padding: 42px;
  border: 1px solid rgb(255 255 255 / 9%);
  border-radius: 12px;
  background: #18211f;
}

.audio-view strong {
  max-width: 100%;
  overflow: hidden;
  color: #edf6f2;
  font-size: 13px;
  font-weight: 600;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.audio-view audio {
  width: 100%;
}

.source-view {
  display: flex;
  width: 100%;
  height: min(760px, calc(100dvh - 154px));
  min-height: 500px;
  flex-direction: column;
  overflow: hidden;
  border: 1px solid rgb(255 255 255 / 9%);
  border-radius: 10px;
  background: #171d1b;
  box-shadow: 0 30px 80px rgb(0 0 0 / 35%);
}

.source-view__bar {
  display: flex;
  min-height: 48px;
  flex: 0 0 auto;
  align-items: center;
  gap: 9px;
  padding: 0 13px;
  border-bottom: 1px solid rgb(255 255 255 / 7%);
  background: #1d2421;
}

.source-view__dots {
  display: flex;
  gap: 5px;
}

.source-view__dots i {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: #59625e;
}

.source-view__dots i:first-child {
  background: #b95e57;
}

.source-view__dots i:nth-child(2) {
  background: #b78c46;
}

.source-view__dots i:nth-child(3) {
  background: #54896e;
}

.source-view__bar > div {
  display: flex;
  min-width: 0;
  flex: 1;
  flex-direction: column;
}

.source-view__bar strong {
  overflow: hidden;
  color: #e8f1ed;
  font-size: 10px;
  font-weight: 560;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.source-view__bar small {
  margin-top: 1px;
  color: rgb(226 240 234 / 34%);
  font-size: 7.5px;
}

.readonly-chip {
  padding: 3px 7px;
  border: 1px solid rgb(110 196 165 / 19%);
  border-radius: 4px;
  color: #7ac9ac;
  font-size: 7px;
  letter-spacing: 0.08em;
}

.source-view__bar > button {
  display: grid;
  width: 29px;
  height: 29px;
  place-items: center;
  padding: 0;
  border: 0;
  border-radius: 5px;
  background: transparent;
  color: rgb(231 243 238 / 50%);
  cursor: pointer;
}

.source-view__bar > button:hover {
  background: rgb(255 255 255 / 7%);
  color: #fff;
}

.source-code {
  display: grid;
  min-height: 0;
  flex: 1;
  grid-template-columns: auto minmax(0, 1fr);
  overflow: auto;
  padding: 22px 0;
  background:
    linear-gradient(90deg, #141a18 0, #141a18 54px, #171d1b 54px);
}

.source-code__gutter {
  display: flex;
  min-width: 54px;
  flex-direction: column;
  align-items: flex-end;
  padding-right: 13px;
  color: rgb(221 235 229 / 22%);
  font-family: "Cascadia Code", "SFMono-Regular", Consolas, monospace;
  font-size: 11px;
  line-height: 1.75;
  user-select: none;
}

.source-code pre {
  min-width: max-content;
  margin: 0;
  padding: 0 24px 0 16px;
}

.source-code code {
  color: #c6d3ce;
  font-family: "Cascadia Code", "SFMono-Regular", Consolas, monospace;
  font-size: 11px;
  line-height: 1.75;
  white-space: pre;
}

.source-line {
  display: block;
  min-height: 1.75em;
  tab-size: 2;
}

.source-code :deep(.tok-keyword) {
  color: #c797df;
}

.source-code :deep(.tok-string) {
  color: #98c99f;
}

.source-code :deep(.tok-comment) {
  color: #687a72;
  font-style: italic;
}

.source-code :deep(.tok-number),
.source-code :deep(.tok-constant) {
  color: #dca667;
}

.source-code :deep(.tok-function) {
  color: #83b9dc;
}

.source-code :deep(.tok-type) {
  color: #d7c88a;
}

.source-code :deep(.tok-punctuation) {
  color: #8f9e98;
}

.source-code :deep(.tok-heading) {
  color: #e7d7a0;
  font-weight: 650;
}

.source-code :deep(.tok-link) {
  color: #7eb9df;
  text-decoration: underline;
  text-underline-offset: 2px;
}

.source-code :deep(.tok-inline-code) {
  color: #cfadde;
}

.source-state {
  display: flex;
  flex: 1;
  align-items: center;
  justify-content: center;
  gap: 8px;
  color: rgb(225 239 233 / 37%);
  font-size: 10px;
}

.source-state.is-error {
  color: #d99389;
}

.source-view__notice {
  min-height: 34px;
  flex: 0 0 auto;
  padding: 9px 15px;
  border-top: 1px solid rgb(255 255 255 / 6%);
  color: rgb(225 239 233 / 30%);
  font-size: 8px;
}

.unsupported-meta {
  display: flex;
  gap: 7px;
  margin-top: 17px;
}

.unsupported-meta span {
  padding: 4px 7px;
  border-radius: 4px;
  background: #e9e9e2;
  color: #777e79;
  font-size: 8px;
}

.is-spinning {
  animation: spin 900ms linear infinite;
}

@keyframes spin {
  to {
    transform: rotate(360deg);
  }
}

@media (max-width: 720px) {
  .readonly-preview,
  .readonly-preview.is-compact {
    min-height: 420px;
  }

  .pdf-frame,
  .source-view {
    height: min(650px, calc(100dvh - 128px));
    min-height: 420px;
  }

  .image-view,
  .video-view {
    max-height: calc(100dvh - 140px);
  }

  .preview-state,
  .audio-view {
    padding: 26px 20px;
  }
}
</style>
