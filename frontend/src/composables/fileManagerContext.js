import { inject } from 'vue'

export const fileManagerKey = Symbol('fileManager')

export function useFileManagerContext() {
  const manager = inject(fileManagerKey)
  if (!manager) {
    throw new Error('File manager context is not available')
  }
  return manager
}
