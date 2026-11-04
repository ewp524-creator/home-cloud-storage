<script setup>
import { computed } from 'vue'
import {
  Archive,
  Braces,
  File,
  FileAudio,
  FileCode2,
  FileImage,
  FileSpreadsheet,
  FileText,
  FileType2,
  Film,
  Folder,
  Paintbrush,
  Presentation,
} from '@lucide/vue'
import { getFileFormat } from '../../utils/fileFormats'

const props = defineProps({
  file: {
    type: Object,
    required: true,
  },
  size: {
    type: String,
    default: 'md',
    validator: (value) => ['xs', 'sm', 'md', 'lg', 'xl'].includes(value),
  },
})

const format = computed(() => getFileFormat(props.file))

const iconComponent = computed(() => {
  const iconMap = {
    word: FileText,
    excel: FileSpreadsheet,
    slides: Presentation,
    pdf: FileType2,
    image: FileImage,
    video: Film,
    audio: FileAudio,
    archive: Archive,
    code: FileCode2,
    text: Braces,
    design: Paintbrush,
    app: File,
    folder: Folder,
    unknown: File,
  }
  return iconMap[format.value.kind] || FileCode2
})
</script>

<template>
  <span
    class="file-glyph"
    :class="[`file-glyph--${size}`, `file-glyph--${format.kind}`]"
    :style="{
      '--glyph-color': format.color,
      '--glyph-bg': format.background,
    }"
    aria-hidden="true"
  >
    <span class="file-glyph__paper">
      <component :is="iconComponent" :stroke-width="1.75" />
    </span>
    <span class="file-glyph__badge">{{ format.badge }}</span>
  </span>
</template>

<style scoped>
.file-glyph {
  position: relative;
  display: inline-grid;
  place-items: center;
  flex: 0 0 auto;
  width: var(--glyph-size);
  height: var(--glyph-size);
  color: var(--glyph-color);
}

.file-glyph--xs {
  --glyph-size: 21px;
  --glyph-icon: 13px;
  --glyph-font: 0px;
}

.file-glyph--sm {
  --glyph-size: 34px;
  --glyph-icon: 20px;
  --glyph-font: 7px;
}

.file-glyph--md {
  --glyph-size: 54px;
  --glyph-icon: 31px;
  --glyph-font: 8px;
}

.file-glyph--lg {
  --glyph-size: 72px;
  --glyph-icon: 42px;
  --glyph-font: 9px;
}

.file-glyph--xl {
  --glyph-size: 92px;
  --glyph-icon: 54px;
  --glyph-font: 10px;
}

.file-glyph__paper {
  display: grid;
  width: 100%;
  height: 100%;
  place-items: center;
  border: 1px solid color-mix(in srgb, var(--glyph-color) 10%, transparent);
  border-radius: 10px;
  background: color-mix(in srgb, var(--glyph-bg) 86%, #fff);
  box-shadow: none;
  transform: none;
  transition:
    transform 180ms ease,
    box-shadow 180ms ease;
}

.file-glyph--xs .file-glyph__paper {
  border-radius: 5px;
}

.file-glyph--xs .file-glyph__badge {
  display: none;
}

.file-glyph__paper svg {
  width: 58%;
  height: 58%;
}

.file-glyph--folder .file-glyph__paper {
  border-radius: 9px;
  transform: none;
}

.file-glyph--folder .file-glyph__paper svg {
  width: 68%;
  height: 68%;
  fill: color-mix(in srgb, var(--glyph-color) 16%, transparent);
}

.file-glyph__badge {
  position: absolute;
  right: -3px;
  bottom: -3px;
  min-width: 20px;
  max-width: 40px;
  padding: 2px 4px;
  overflow: hidden;
  border: 1px solid color-mix(in srgb, var(--glyph-color) 22%, #fff);
  border-radius: 4px;
  background: #fff;
  color: var(--glyph-color);
  font-size: var(--glyph-font);
  font-weight: 700;
  line-height: 1.15;
  letter-spacing: 0.02em;
  text-align: center;
  text-overflow: clip;
  white-space: nowrap;
}

.file-glyph--folder .file-glyph__badge {
  display: none;
}
</style>
