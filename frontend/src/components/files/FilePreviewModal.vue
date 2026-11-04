<script setup>
import {
  computed,
  onBeforeUnmount,
  onMounted,
  ref,
  watch,
} from 'vue'
import {
  AlertCircle,
  Download,
  Eye,
  LoaderCircle,
  RotateCw,
  Share2,
  ShieldCheck,
  X,
} from '@lucide/vue'
import { useFileManagerContext } from '../../composables/fileManagerContext'
import { filePreviewAccess } from '../../services/filesApi'
import { getFileFormat } from '../../utils/fileFormats'
import { formatBytes, formatDate } from '../../utils/formatters'
import FileGlyph from './FileGlyph.vue'
import ReadonlyFilePreview from './ReadonlyFilePreview.vue'

const emit = defineEmits(['share'])
const manager = useFileManagerContext()
const { closePreview, downloadFiles, previewFile } = manager

const previewAccess = ref(null)
const loading = ref(false)
const loadError = ref('')
let requestId = 0

const format = computed(() =>
  previewFile.value ? getFileFormat(previewFile.value) : null,
)

async function loadPreview() {
  const file = previewFile.value
  if (!file) return
  const currentRequest = ++requestId
  previewAccess.value = null
  loadError.value = ''
  loading.value = true
  try {
    const access = await filePreviewAccess(file.id)
    if (currentRequest === requestId) previewAccess.value = access
  } catch (error) {
    if (currentRequest === requestId) {
      loadError.value = error.message || '无法建立安全预览'
    }
  } finally {
    if (currentRequest === requestId) loading.value = false
  }
}

function close() {
  requestId += 1
  previewAccess.value = null
  closePreview()
}

function download() {
  if (previewFile.value) downloadFiles([previewFile.value])
}

function onKeydown(event) {
  if (previewFile.value && event.key === 'Escape') close()
}

watch(() => previewFile.value?.id, loadPreview, { immediate: true })
onMounted(() => document.addEventListener('keydown', onKeydown))
onBeforeUnmount(() => {
  requestId += 1
  document.removeEventListener('keydown', onKeydown)
})
</script>

<template>
  <Teleport to="body">
    <Transition name="preview">
      <div v-if="previewFile" class="preview-layer">
        <header class="preview-toolbar">
          <div class="preview-toolbar__file">
            <FileGlyph :file="previewFile" size="sm" />
            <div>
              <strong>{{ previewFile.name }}</strong>
              <span>
                {{ format.label }} · {{ formatBytes(previewFile.size) }}
                <i>只读预览</i>
              </span>
            </div>
          </div>

          <div class="preview-toolbar__actions">
            <button
              class="preview-action"
              type="button"
              title="下载原文件"
              aria-label="下载原文件"
              @click="download"
            >
              <Download :size="17" />
            </button>
            <button
              class="preview-action"
              type="button"
              title="分享文件"
              aria-label="分享文件"
              @click="emit('share', previewFile)"
            >
              <Share2 :size="17" />
            </button>
            <span class="preview-toolbar__divider" />
            <button
              class="preview-action"
              type="button"
              title="关闭预览"
              aria-label="关闭预览"
              @click="close"
            >
              <X :size="19" />
            </button>
          </div>
        </header>

        <div class="preview-stage">
          <div class="preview-canvas">
            <div v-if="loadError" class="preview-load-error">
              <AlertCircle :size="25" />
              <strong>预览加载失败</strong>
              <p>{{ loadError }}</p>
              <button type="button" @click="loadPreview">
                <RotateCw :size="14" />
                重新加载
              </button>
            </div>

            <div v-else-if="loading" class="preview-load-error">
              <LoaderCircle :size="23" class="is-spinning" />
              <strong>正在验证预览权限</strong>
              <p>每次预览都会生成短期签名，不暴露对象存储地址。</p>
            </div>

            <ReadonlyFilePreview
              v-else
              :file="previewFile"
              :source-url="previewAccess?.url || ''"
              :preview-kind="previewAccess?.previewKind || format.kind"
              @retry="loadPreview"
            />
          </div>

          <aside class="preview-info">
            <div class="preview-info__title">
              <span><Eye :size="14" /></span>
              <div>
                <strong>只读文件预览</strong>
                <small>内容来自 MinIO 中的原始对象</small>
              </div>
            </div>

            <h3>文件信息</h3>
            <dl>
              <div><dt>格式</dt><dd>{{ format.label }}</dd></div>
              <div><dt>大小</dt><dd>{{ formatBytes(previewFile.size) }}</dd></div>
              <div><dt>所有者</dt><dd>{{ previewFile.owner }}</dd></div>
              <div>
                <dt>更新时间</dt>
                <dd>{{ formatDate(previewFile.modifiedAt, 'full') }}</dd>
              </div>
            </dl>

            <div v-if="previewFile.tags?.length" class="preview-info__tags">
              <span v-for="tag in previewFile.tags" :key="tag">{{ tag }}</span>
            </div>

            <div class="preview-info__secure">
              <ShieldCheck :size="17" />
              <div>
                <strong>短期签名流</strong>
                <p>
                  预览地址约 10 分钟后失效，并要求当前账户仍有文件访问权。
                  视频支持分段读取，不会一次载入整个文件。
                </p>
              </div>
            </div>

            <p v-if="previewAccess" class="preview-info__expiry">
              本次预览有效至
              {{ formatDate(previewAccess.expiresAt, 'full') }}
            </p>
          </aside>
        </div>
      </div>
    </Transition>
  </Teleport>
</template>

<style scoped>
.preview-layer {
  position: fixed;
  z-index: 1200;
  inset: 0;
  display: flex;
  flex-direction: column;
  background: #0d1513;
  color: #eaf2ef;
}

.preview-toolbar {
  display: flex;
  height: 66px;
  flex: 0 0 auto;
  align-items: center;
  justify-content: space-between;
  gap: 20px;
  padding: 0 18px 0 22px;
  border-bottom: 1px solid rgb(255 255 255 / 8%);
  background: #121b19;
}

.preview-toolbar__file {
  display: flex;
  min-width: 0;
  align-items: center;
  gap: 11px;
}

.preview-toolbar__file > div {
  display: flex;
  min-width: 0;
  flex-direction: column;
}

.preview-toolbar__file strong {
  overflow: hidden;
  color: #f2f8f6;
  font-size: 12px;
  font-weight: 620;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.preview-toolbar__file span {
  display: flex;
  align-items: center;
  gap: 7px;
  margin-top: 2px;
  color: rgb(231 243 239 / 42%);
  font-size: 9px;
}

.preview-toolbar__file i {
  padding: 2px 5px;
  border: 1px solid rgb(99 190 158 / 18%);
  border-radius: 3px;
  color: #72c8a9;
  font-size: 7px;
  font-style: normal;
}

.preview-toolbar__actions {
  display: flex;
  align-items: center;
  gap: 5px;
}

.preview-action {
  display: grid;
  width: 36px;
  height: 36px;
  place-items: center;
  padding: 0;
  border: 0;
  border-radius: 8px;
  background: transparent;
  color: rgb(238 248 244 / 65%);
  cursor: pointer;
}

.preview-action:hover {
  background: rgb(255 255 255 / 7%);
  color: #fff;
}

.preview-toolbar__divider {
  width: 1px;
  height: 21px;
  margin: 0 3px;
  background: rgb(255 255 255 / 10%);
}

.preview-stage {
  display: grid;
  min-height: 0;
  flex: 1;
  grid-template-columns: minmax(0, 1fr) 276px;
}

.preview-canvas {
  display: flex;
  min-width: 0;
  min-height: 0;
  align-items: center;
  justify-content: center;
  overflow: auto;
  padding: 36px;
  background: #0b1210;
}

.preview-load-error {
  display: flex;
  width: min(410px, 100%);
  min-height: 250px;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 30px;
  border: 1px solid rgb(255 255 255 / 8%);
  border-radius: 10px;
  background: #17211e;
  text-align: center;
}

.preview-load-error > svg {
  margin-bottom: 12px;
  color: #89a49a;
}

.preview-load-error strong {
  color: #eef7f3;
  font-size: 13px;
}

.preview-load-error p {
  margin: 6px 0 0;
  color: rgb(231 243 238 / 38%);
  font-size: 9.5px;
  line-height: 1.6;
}

.preview-load-error button {
  display: flex;
  height: 33px;
  align-items: center;
  gap: 6px;
  margin-top: 16px;
  padding: 0 12px;
  border: 1px solid rgb(255 255 255 / 11%);
  border-radius: 6px;
  background: rgb(255 255 255 / 6%);
  color: #e5f0eb;
  font: inherit;
  font-size: 9.5px;
  cursor: pointer;
}

.preview-info {
  min-height: 0;
  padding: 24px 20px;
  overflow: auto;
  border-left: 1px solid rgb(255 255 255 / 8%);
  background: #121b19;
}

.preview-info__title {
  display: flex;
  gap: 9px;
  padding-bottom: 18px;
  border-bottom: 1px solid rgb(255 255 255 / 7%);
}

.preview-info__title > span {
  display: grid;
  width: 31px;
  height: 31px;
  flex: 0 0 auto;
  place-items: center;
  border-radius: 7px;
  background: rgb(75 169 137 / 10%);
  color: #65bd9e;
}

.preview-info__title > div {
  display: flex;
  flex-direction: column;
}

.preview-info__title strong {
  color: rgb(238 248 244 / 76%);
  font-size: 10px;
  font-weight: 600;
}

.preview-info__title small {
  margin-top: 3px;
  color: rgb(229 242 237 / 31%);
  font-size: 7.8px;
}

.preview-info h3 {
  margin: 22px 0 15px;
  color: rgb(239 248 245 / 55%);
  font-size: 9px;
  font-weight: 650;
  letter-spacing: 0.05em;
}

.preview-info dl {
  display: flex;
  flex-direction: column;
  gap: 13px;
  margin: 0;
}

.preview-info dl div {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
}

.preview-info dt {
  color: rgb(229 242 237 / 32%);
  font-size: 8.5px;
}

.preview-info dd {
  max-width: 165px;
  margin: 0;
  color: rgb(236 246 242 / 62%);
  font-size: 8.5px;
  text-align: right;
  overflow-wrap: anywhere;
}

.preview-info__tags {
  display: flex;
  flex-wrap: wrap;
  gap: 5px;
  margin-top: 18px;
  padding-top: 16px;
  border-top: 1px solid rgb(255 255 255 / 7%);
}

.preview-info__tags span {
  padding: 4px 7px;
  border-radius: 4px;
  background: rgb(255 255 255 / 6%);
  color: rgb(236 247 243 / 47%);
  font-size: 8px;
}

.preview-info__secure {
  display: flex;
  gap: 9px;
  margin-top: 24px;
  padding: 13px;
  border: 1px solid rgb(46 177 143 / 16%);
  border-radius: 8px;
  background: rgb(34 153 122 / 6%);
  color: #59c9a6;
}

.preview-info__secure > svg {
  flex: 0 0 auto;
}

.preview-info__secure strong {
  display: block;
  color: rgb(237 248 244 / 70%);
  font-size: 9px;
  font-weight: 600;
}

.preview-info__secure p {
  margin: 4px 0 0;
  color: rgb(229 243 238 / 31%);
  font-size: 7.8px;
  line-height: 1.55;
}

.preview-info__expiry {
  margin: 15px 0 0;
  color: rgb(228 242 236 / 24%);
  font-size: 7.5px;
  line-height: 1.5;
}

.is-spinning {
  animation: spin 900ms linear infinite;
}

@keyframes spin {
  to {
    transform: rotate(360deg);
  }
}

.preview-enter-active,
.preview-leave-active {
  transition: opacity 180ms ease;
}

.preview-enter-from,
.preview-leave-to {
  opacity: 0;
}

@media (max-width: 900px) {
  .preview-stage {
    grid-template-columns: 1fr;
  }

  .preview-info {
    display: none;
  }

  .preview-canvas {
    padding: 22px;
  }
}

@media (max-width: 640px) {
  .preview-toolbar {
    height: 60px;
    padding: 0 12px;
  }

  .preview-toolbar__file span {
    display: none;
  }

  .preview-canvas {
    padding: 12px;
  }
}
</style>
