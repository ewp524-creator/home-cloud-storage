import JSZip from 'jszip'
import { getFileFormat } from '../utils/fileFormats'
import { formatBytes } from '../utils/formatters'

function safeFileName(name = 'file') {
  return name.replace(/[\\/:*?"<>|]/g, '_').trim() || 'file'
}

function createPlaceholder(file) {
  const format = getFileFormat(file)
  return [
    'File Home prototype export',
    '',
    `Name: ${file.name}`,
    `Format: ${format.label}`,
    `Size: ${formatBytes(file.size)}`,
    `Owner: ${file.owner || 'Local user'}`,
    `Modified: ${file.modifiedAt || ''}`,
    '',
    'This placeholder represents the original file in the frontend prototype.',
  ].join('\n')
}

function saveBlob(blob, fileName) {
  const url = URL.createObjectURL(blob)
  const anchor = document.createElement('a')
  anchor.href = url
  anchor.download = fileName
  anchor.style.display = 'none'
  document.body.appendChild(anchor)
  anchor.click()
  anchor.remove()
  globalThis.setTimeout(() => URL.revokeObjectURL(url), 1500)
}

export async function downloadNodes(files, roots, options = {}) {
  if (!roots?.length) {
    throw new Error('No files selected for download')
  }

  if (roots.length === 1 && roots[0].kind === 'file' && !options.forceZip) {
    const file = roots[0]
    const blob = file.sourceFile || new Blob([createPlaceholder(file)])
    saveBlob(blob, safeFileName(file.name))
    return { fileName: file.name, count: 1 }
  }

  const zip = new JSZip()
  const includedIds = new Set()

  function addChildren(parentId, parentPath) {
    const children = files.filter(
      (file) =>
        !file.deletedAt &&
        (file.parentId || null) === (parentId || null),
    )

    for (const child of children) {
      addNode(child, parentPath)
    }
  }

  function addNode(file, parentPath) {
    if (includedIds.has(file.id)) return
    includedIds.add(file.id)
    const path = parentPath
      ? `${parentPath}/${safeFileName(file.name)}`
      : safeFileName(file.name)

    if (file.kind === 'folder') {
      zip.folder(path)
      addChildren(file.id, path)
      return
    }

    zip.file(path, file.sourceFile || createPlaceholder(file))
  }

  roots.forEach((root) => addNode(root, ''))

  const blob = await zip.generateAsync({
    type: 'blob',
    compression: 'DEFLATE',
    compressionOptions: { level: 6 },
  })
  const baseName =
    roots.length === 1
      ? safeFileName(roots[0].name.replace(/\.zip$/i, ''))
      : 'filehome-selection'
  const fileName = `${baseName}.zip`
  saveBlob(blob, fileName)

  return {
    fileName,
    count: includedIds.size,
  }
}
