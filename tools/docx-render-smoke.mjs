import fs from 'node:fs/promises'
import { Window } from '../frontend/node_modules/happy-dom/lib/index.js'

const filePath = process.argv[2]

if (!filePath) {
  throw new Error('Usage: node tools/docx-render-smoke.mjs <file.docx>')
}
const window = new Window({ url: 'http://localhost/' })
globalThis.window = window
globalThis.document = window.document
globalThis.DOMParser = window.DOMParser
globalThis.XMLSerializer = window.XMLSerializer
globalThis.Node = window.Node
globalThis.HTMLElement = window.HTMLElement
globalThis.Range = window.Range
globalThis.requestAnimationFrame = (callback) => setTimeout(callback, 0)

const bytes = await fs.readFile(filePath)
const body = window.document.createElement('div')
const styles = window.document.createElement('div')
const { renderAsync } = await import(
  '../frontend/node_modules/docx-preview/dist/docx-preview.js'
)

await renderAsync(new Blob([bytes]), body, styles, {
  className: 'filehome-docx',
  inWrapper: true,
  breakPages: true,
  ignoreLastRenderedPageBreak: true,
  useBase64URL: true,
  renderHeaders: true,
  renderFooters: true,
  renderFootnotes: true,
  renderEndnotes: true,
})

const result = {
  bodyNodes: body.childNodes.length,
  bodyHtmlLength: body.innerHTML.length,
  styleHtmlLength: styles.innerHTML.length,
  sections: body.querySelectorAll('section.filehome-docx').length,
}

if (!result.bodyHtmlLength || !result.sections) {
  throw new Error(`DOCX rendered no visible pages: ${JSON.stringify(result)}`)
}

process.stdout.write(`${JSON.stringify(result)}\n`)
