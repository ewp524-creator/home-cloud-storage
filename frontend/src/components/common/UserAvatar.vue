<script setup>
import { computed, ref, watch } from 'vue'

const props = defineProps({
  src: {
    type: String,
    default: '',
  },
  initials: {
    type: String,
    default: 'FH',
  },
  size: {
    type: String,
    default: '',
  },
})

const sizeClass = computed(() =>
  props.size ? `avatar--${props.size}` : '',
)
const imageFailed = ref(false)
const showImage = computed(() => Boolean(props.src) && !imageFailed.value)

watch(
  () => props.src,
  () => {
    imageFailed.value = false
  },
)
</script>

<template>
  <span class="avatar" :class="sizeClass">
    <img
      v-if="showImage"
      :src="src"
      :alt="initials"
      @error="imageFailed = true"
    />
    <template v-else>{{ initials }}</template>
  </span>
</template>

<style scoped>
.avatar img {
  width: 100%;
  height: 100%;
  border-radius: inherit;
  object-fit: cover;
}
</style>
