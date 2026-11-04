import { createApp } from 'vue'
import App from './App.vue'
import './styles/main.css'

const chunkReloadKey = 'filehome:chunk-reloaded'

globalThis.addEventListener?.('vite:preloadError', (event) => {
  if (globalThis.sessionStorage?.getItem(chunkReloadKey)) return
  event.preventDefault()
  globalThis.sessionStorage?.setItem(chunkReloadKey, '1')
  globalThis.location?.reload()
})

globalThis.addEventListener?.('load', () => {
  globalThis.sessionStorage?.removeItem(chunkReloadKey)
})

createApp(App).mount('#app')
