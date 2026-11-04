<script setup>
import { computed } from 'vue'
import { AlertCircle, FolderOpen } from '@lucide/vue'
import { getFileFormat } from '../../utils/fileFormats'
import { formatBytes } from '../../utils/formatters'
import FileGlyph from '../files/FileGlyph.vue'
import ReadonlyFilePreview from '../files/ReadonlyFilePreview.vue'

const props = defineProps({
  file: {
    type: Object,
    default: null,
  },
  folderItems: {
    type: Array,
    default: () => [],
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
  error: {
    type: String,
    default: '',
  },
})

defineEmits(['unlock'])

const format = computed(() => (props.file ? getFileFormat(props.file) : null))
const isFolder = computed(() => props.file?.kind === 'folder')
const resolvedKind = computed(
  () => props.previewKind || props.file?.preview || format.value?.kind || '',
)
</script>

<template>
  <section
    class="share-preview"
    :class="`is-${isFolder ? 'folder' : resolvedKind}`"
  >
    <div class="share-preview__chrome">
      <span class="share-preview__dots"><i /><i /><i /></span>
      <span>{{ file?.name || '分享内容' }}</span>
      <em>{{ isFolder ? 'FOLDER' : format?.label || 'FILE' }}</em>
    </div>

    <div v-if="isFolder" class="folder-preview">
      <div class="folder-preview__head">
        <span><FolderOpen :size="24" /></span>
        <div>
          <strong>{{ file?.name }}</strong>
          <small>{{ folderItems.length }} 个直接项目</small>
        </div>
      </div>
      <ul v-if="folderItems.length">
        <li v-for="item in folderItems.slice(0, 12)" :key="item.id">
          <FileGlyph :file="item" size="xs" />
          <span>{{ item.name }}</span>
          <small>
            {{ item.kind === 'folder' ? '文件夹' : formatBytes(item.size) }}
          </small>
        </li>
      </ul>
      <div v-else class="folder-preview__empty">
        这是一个空文件夹。
      </div>
      <p class="folder-preview__notice">
        文件夹内容以分享时的实际目录为准，下载时会保留层级并打包为 ZIP。
      </p>
    </div>

    <div v-else-if="error" class="share-preview__error">
      <AlertCircle :size="24" />
      <strong>原始文件预览不可用</strong>
      <p>{{ error }}</p>
    </div>

    <ReadonlyFilePreview
      v-else
      class="share-readonly-preview"
      :file="file"
      :source-url="sourceUrl"
      :preview-kind="resolvedKind"
      :loading="loading"
      :locked="locked"
      compact
      @unlock="$emit('unlock')"
    />
  </section>
</template>

<style scoped>
.share-preview {
  position: relative;
  display: flex;
  min-height: 540px;
  overflow: hidden;
  border: 1px solid #deded7;
  border-radius: 10px;
  background: #ebeae4;
  flex-direction: column;
  box-shadow: 0 20px 55px rgb(24 26 23 / 7%);
}

.share-preview__chrome {
  display: flex;
  height: 43px;
  flex: 0 0 auto;
  align-items: center;
  gap: 8px;
  padding: 0 14px;
  border-bottom: 1px solid #deddd5;
  background: rgb(250 249 245 / 94%);
  color: #777c76;
  font-size: 10px;
}

.share-preview__chrome > span:nth-child(2) {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.share-preview__chrome em {
  margin-left: auto;
  color: #a1a59e;
  font-size: 8px;
  font-style: normal;
  letter-spacing: 0.08em;
}

.share-preview__dots {
  display: flex;
  gap: 5px;
}

.share-preview__dots i {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: #c9c9c1;
}

.share-preview__dots i:first-child {
  background: #d2857c;
}

.share-preview__dots i:nth-child(2) {
  background: #d2ad65;
}

.share-preview__dots i:last-child {
  background: #7aaa8e;
}

.share-readonly-preview {
  flex: 1;
  padding: 18px;
}

.folder-preview {
  display: flex;
  min-height: 0;
  flex: 1;
  flex-direction: column;
  padding: 30px;
}

.folder-preview__head {
  display: flex;
  align-items: center;
  gap: 12px;
  padding-bottom: 20px;
  border-bottom: 1px solid #dddcd4;
}

.folder-preview__head > span {
  display: grid;
  width: 48px;
  height: 48px;
  place-items: center;
  border-radius: 10px;
  background: #f4e9ce;
  color: #a37720;
}

.folder-preview__head > div {
  display: flex;
  min-width: 0;
  flex-direction: column;
}

.folder-preview__head strong {
  overflow: hidden;
  color: #292d29;
  font-size: 15px;
  font-weight: 620;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.folder-preview__head small {
  margin-top: 3px;
  color: #878c86;
  font-size: 9px;
}

.folder-preview ul {
  display: flex;
  flex: 1;
  flex-direction: column;
  gap: 1px;
  margin: 0;
  padding: 10px 0;
  overflow: auto;
  list-style: none;
}

.folder-preview li {
  display: grid;
  min-height: 39px;
  grid-template-columns: 22px minmax(0, 1fr) auto;
  align-items: center;
  gap: 9px;
  padding: 0 8px;
  border-bottom: 1px solid #e0dfd8;
  color: #59605a;
  font-size: 10px;
}

.folder-preview li > span {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.folder-preview li small {
  color: #929791;
  font-size: 8px;
}

.folder-preview__empty {
  display: grid;
  flex: 1;
  place-items: center;
  color: #8d928d;
  font-size: 10px;
}

.folder-preview__notice {
  margin: 8px 0 0;
  color: #8b908a;
  font-size: 8.5px;
  line-height: 1.6;
}

.share-preview__error {
  display: flex;
  min-height: 420px;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 30px;
  text-align: center;
}

.share-preview__error > svg {
  margin-bottom: 11px;
  color: #9a7b74;
}

.share-preview__error strong {
  color: #2d302d;
  font-size: 14px;
}

.share-preview__error p {
  margin: 6px 0 0;
  color: #858a84;
  font-size: 9.5px;
}

@media (max-width: 720px) {
  .share-preview {
    min-height: 430px;
  }

  .share-readonly-preview {
    padding: 10px;
  }

  .folder-preview {
    padding: 20px;
  }
}
</style>
