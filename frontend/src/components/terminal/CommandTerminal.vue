<script setup>
import {
  computed,
  nextTick,
  onBeforeUnmount,
  onMounted,
  reactive,
  ref,
  watch,
} from 'vue'
import {
  GripHorizontal,
  Minus,
  MoveDiagonal2,
  SquareTerminal,
  X,
} from '@lucide/vue'
import { useFileManagerContext } from '../../composables/fileManagerContext'
import { formatBytes, uid } from '../../utils/formatters'

const manager = useFileManagerContext()
const {
  breadcrumbs,
  copyItems,
  createShare,
  createFolder,
  currentFolder,
  currentFolderId,
  currentView,
  deletePermanently,
  downloadFiles,
  files,
  goToBreadcrumb,
  moveItems,
  navigate,
  openFolder,
  openPreview,
  refreshFiles,
  requestUpload,
  renameFile,
  rootFolderId,
  searchQuery,
  terminalOpen,
  visibleFiles,
} = manager

const input = ref(null)
const scrollArea = ref(null)
const dock = ref(null)
const commandValue = ref('')
const history = ref([])
const historyIndex = ref(-1)
const pendingDeletion = ref(null)
const pendingUnshare = ref(null)
const pendingShare = ref(null)
const initialized = ref(false)
const fontSize = ref(11)
const queuedCommandCount = ref(0)
const position = reactive({ x: 24, y: 80 })
const size = reactive({ width: 740, height: 380 })
const dragState = ref(null)
let commandQueue = Promise.resolve()
const entries = ref([
  {
    id: uid('line'),
    type: 'system',
    lines: [
      'FILE HOME / COMMAND CONSOLE',
      '输入 help 查看可用命令。',
    ],
  },
])

const promptPath = computed(() => {
  if (currentView.value !== 'files') return '~'
  const names = breadcrumbs.value.map((item) => item.name)
  return ['~', ...names].join('/')
})

const pendingPrompt = computed(() => {
  if (pendingDeletion.value) return '输入 y 确认，其他内容取消'
  if (pendingUnshare.value) return '输入 y 确认取消分享，其他内容返回'
  if (pendingShare.value?.step === 'days') {
    return pendingShare.value.mode === 'modify'
      ? `输入新的分享时间，回车保持 ${pendingShare.value.currentDays} 天`
      : '输入分享时间（1-30 天）'
  }
  if (pendingShare.value?.step === 'code') {
    return pendingShare.value.currentAccessCode
      ? '输入新分享码，回车保持当前分享码'
      : '输入分享码（4-12 位字母或数字），输入 none 表示无分享码'
  }
  return ''
})

const suggestions = [
  'ls',
  'cd',
  'open',
  'cp',
  'mv',
  'delfile',
  'deldir',
  'find',
  'download',
  'upload',
  'sharefile',
  'sharedir',
  'ushare',
  'modshare',
]

function addOutput(lines, tone = 'default') {
  entries.value.push({
    id: uid('line'),
    type: 'output',
    tone,
    lines: Array.isArray(lines) ? lines : [lines],
  })
}

function addCommand(command) {
  entries.value.push({
    id: uid('line'),
    type: 'command',
    path: promptPath.value,
    command,
  })
}

function scrollToBottom() {
  nextTick(() => {
    if (scrollArea.value) {
      scrollArea.value.scrollTop = scrollArea.value.scrollHeight
    }
  })
}

function normalizeArgument(value) {
  return value.replace(/^['"]|['"]$/g, '').trim()
}

function findFolderFrom(path, startId = null) {
  if (!path || path === '/' || path === '~') return null
  const parts = path.split('/').filter((part) => part && part !== '~')
  const rootId = rootFolderId.value || null
  let parentId =
    path.startsWith('/') || path.startsWith('~')
      ? rootId
      : startId || rootId

  for (const part of parts) {
    const match = files.value.find(
      (file) =>
        file.kind === 'folder' &&
        !file.deletedAt &&
        (file.parentId || null) === parentId &&
        file.name.toLowerCase() === part.toLowerCase(),
    )
    if (!match) return undefined
    parentId = match.id
  }

  return files.value.find((file) => file.id === parentId) || null
}

function findFolder(path) {
  if (path === '..') {
    return (
      files.value.find(
        (file) => file.id === (currentFolder.value?.parentId || null),
      ) || null
    )
  }
  const relative = findFolderFrom(path, currentFolderId.value)
  if (relative !== undefined) return relative
  return findFolderFrom(path, null)
}

function tokenizeArguments(value) {
  return (
    value.match(/(?:[^\s"']+|"[^"]*"|'[^']*')+/g)?.map((part) =>
      part.replace(/^['"]|['"]$/g, ''),
    ) || []
  )
}

function resolveDestination(destination, source) {
  const existingFolder = findFolder(destination)
  if (existingFolder) {
    return { folderId: existingFolder.id, name: '' }
  }

  const parts = destination
    .replace(/^~\/?/, '')
    .replace(/^\/+/, '')
    .split('/')
    .filter(Boolean)
  const name = parts.pop() || destination
  const parentPath = parts.join('/')
  const parent = parentPath
    ? findFolder(parentPath)
    : files.value.find((file) => file.id === source.parentId)

  return {
    folderId: parent?.id || null,
    name,
  }
}

function resolveFile(argument) {
  if (!argument) return null
  const source = visibleFiles.value.length
    ? visibleFiles.value
    : files.value.filter((file) => !file.deletedAt)
  const normalized = argument.toLowerCase()

  return (
    source.find((file) => file.name.toLowerCase() === normalized) ||
    source.find((file) => file.name.toLowerCase().includes(normalized)) ||
    files.value.find(
      (file) => !file.deletedAt && file.name.toLowerCase().includes(normalized),
    )
  )
}

async function handlePendingShare(value) {
  const pending = pendingShare.value
  if (pending.step === 'days') {
    const days =
      !value.trim() && pending.mode === 'modify'
        ? pending.currentDays
        : Number.parseInt(value.replace(/[^\d]/g, ''), 10)
    if (!Number.isInteger(days) || days < 1 || days > 30) {
      addOutput('分享时间需要是 1-30 天，请重新输入。', 'error')
      return
    }
    pending.days = days
    pending.step = 'code'
    addOutput(`分享时间已设置为 ${days} 天。`)
    addOutput(
      pending.currentAccessCode
        ? '请输入新的分享码，直接回车保持当前分享码：'
        : '请输入分享码（4-12 位字母或数字），输入 none 表示不设置分享码：',
    )
    return
  }

  const rawCode = value.trim()
  let code
  if (!rawCode && pending.currentAccessCode) {
    code = pending.currentAccessCode
  } else if (
    !rawCode &&
    pending.currentAccessCodeConfigured &&
    !pending.currentAccessCode
  ) {
    addOutput(
      '当前分享码无法回显，请输入一个新的分享码，或输入 none 清除分享码。',
      'error',
    )
    return
  } else if (rawCode.toLowerCase() === 'none') {
    code = ''
  } else {
    code = rawCode.toUpperCase().replace(/[^A-Z0-9]/g, '')
    if (code.length < 4 || code.length > 12) {
      addOutput('分享码需要包含 4-12 位字母或数字，请重新输入。', 'error')
      return
    }
  }

  const target = pending.target
  pendingShare.value = null
  const share = await createShare(target, {
    days: pending.days,
    permission: pending.currentPermission || 'download',
    accessCode: code,
  })
  if (!share) {
    addOutput('分享设置保存失败，请检查网络或后端日志。', 'error')
    return
  }
  addOutput([
    `${pending.existing ? '分享设置已更新' : '分享已创建'}：${share.fileName}`,
    `分享时间：${pending.days} 天`,
    `分享码：${share.accessCode || code || '无'}`,
    `链接：${share.url}`,
  ])
}

async function execute(commandLine) {
  if (pendingUnshare.value) {
    const answer = commandLine.trim().toLowerCase()
    const pending = pendingUnshare.value
    pendingUnshare.value = null
    if (['y', 'yes', '是', '确认', '确定'].includes(answer)) {
      await manager.revokeShare(pending.share.id)
      addOutput(`已取消分享：${pending.target.name}`)
    } else {
      addOutput('已保留当前分享。')
    }
    return
  }

  if (pendingDeletion.value) {
    const answer = commandLine.trim().toLowerCase()
    const target = pendingDeletion.value
    pendingDeletion.value = null

    if (['y', 'yes', '是', '确认', '确定'].includes(answer)) {
      await deletePermanently([target.id])
      addOutput(`已删除文件夹：${target.name}/，其中的文件也已清除。`)
    } else {
      addOutput('已取消删除。')
    }
    return
  }

  if (pendingShare.value) {
    await handlePendingShare(commandLine)
    return
  }

  const command = commandLine.split(/\s+/)[0].toLowerCase()
  const argument = normalizeArgument(
    commandLine.slice(command.length).trim(),
  )

  if (command === 'help') {
    addOutput([
      'ls             列出当前目录',
      'cd <folder>    进入文件夹',
      'cd ..          返回上一级',
      'open <file>    打开文件或文件夹',
      'cp <src> [dst] 复制文件或文件夹',
      'mv <src> <dst> 移动或重命名',
      'delfile <file> 永久删除文件',
      'deldir <dir>   永久删除文件夹及内容',
      'download [name] 下载文件或文件夹 ZIP',
      'upload         打开文件选择器',
      'sharefile <file> 创建或修改文件分享',
      'sharedir <dir>   创建或修改文件夹分享',
      'modshare <item>  修改已有分享的时间和分享码',
      'ushare <item>    取消文件或文件夹分享',
      'find <keyword> 搜索文件',
      'mkdir <name>   新建文件夹',
      'home           返回文件库',
      'clear          清空终端',
      'exit           关闭终端',
    ])
    return
  }

  if (command === 'clear') {
    entries.value = []
    return
  }

  if (command === 'exit') {
    terminalOpen.value = false
    return
  }

  if (command === 'ls') {
    const list = visibleFiles.value
    addOutput(
      list.length
        ? list.map((file) =>
            file.kind === 'folder'
              ? `[dir] ${file.name}/`
              : `      ${file.name}  ${formatBytes(file.size)}`,
          )
        : ['(当前目录为空)'],
    )
    return
  }

  if (command === 'pwd') {
    addOutput(promptPath.value)
    return
  }

  if (command === 'home') {
    navigate('files')
    addOutput('已返回文件库根目录。')
    return
  }

  if (command === 'recent' || command === 'starred' || command === 'trash') {
    navigate(command)
    addOutput(`已打开 ${command}。`)
    return
  }

  if (command === 'cd') {
    if (!argument || argument === '~' || argument === '/') {
      navigate('files')
      addOutput('已返回文件库根目录。')
      return
    }

    if (argument === '..') {
      goToBreadcrumb(currentFolder.value?.parentId || null)
      addOutput('已返回上一级。')
      return
    }

    let folder = findFolder(argument)
    if (folder === undefined || !folder) {
      await refreshFiles()
      folder = findFolder(argument)
    }
    if (folder === undefined || !folder) {
      addOutput(`未找到文件夹：${argument}`, 'error')
      return
    }
    openFolder(folder)
    addOutput(`已进入 ${folder.name}/`)
    return
  }

  if (command === 'open') {
    const file = resolveFile(argument)
    if (!file) {
      addOutput(`未找到文件：${argument || '(未输入名称)'}`, 'error')
      return
    }
    if (file.kind === 'folder') {
      openFolder(file)
      addOutput(`已进入 ${file.name}/`)
    } else {
      terminalOpen.value = false
      openPreview(file)
    }
    return
  }

  if (command === 'cp' || command === 'mv') {
    const args = tokenizeArguments(argument)
    const source = resolveFile(args[0] || '')
    if (!source) {
      addOutput(`未找到源项目：${args[0] || '(未输入名称)'}`, 'error')
      return
    }
    const sourceName = source.name

    const destinationText = args.slice(1).join(' ').trim()
    if (!destinationText) {
      if (command === 'cp') {
        const copies = await copyItems([source.id], source.parentId || null)
        addOutput(
          copies.length
            ? `已创建副本：${copies.map((item) => item.name).join(', ')}`
            : '复制失败。',
          copies.length ? 'default' : 'error',
        )
      } else {
        addOutput('请指定目标文件夹或新的名称。', 'error')
      }
      return
    }

    const destination = resolveDestination(destinationText, source)
    const sourceParentId = source.parentId || null
    const targetParentId = destination.folderId || null

    if (command === 'cp') {
      const copies = await copyItems([source.id], targetParentId, {
        name: destination.name,
      })
      addOutput(
        copies.length
          ? `已复制 ${sourceName} → ${
              destination.name ||
              files.value.find((file) => file.id === targetParentId)?.name ||
              '文件库'
            }`
          : '复制失败，请检查目标路径。',
        copies.length ? 'default' : 'error',
      )
      return
    }

    if (
      targetParentId === sourceParentId &&
      destination.name &&
      destination.name !== source.name
    ) {
      await renameFile(source, destination.name)
      addOutput(`已重命名：${sourceName} → ${destination.name}`)
      return
    }

    const moved = await moveItems([source.id], targetParentId)
    if (moved && destination.name && destination.name !== source.name) {
      await renameFile(source, destination.name)
    }
    addOutput(
      moved
        ? `已移动 ${sourceName} → ${
            destination.name ||
            files.value.find((file) => file.id === targetParentId)?.name ||
            '文件库'
          }`
        : '移动失败，请检查目标路径或防止移动到自身子目录。',
      moved ? 'default' : 'error',
    )
    return
  }

  if (command === 'delfile') {
    const target = resolveFile(argument)
    if (!target) {
      addOutput(`未找到文件：${argument || '(未输入名称)'}`, 'error')
      return
    }
    if (target.kind !== 'file') {
      addOutput('delfile 只能删除文件，请使用 deldir 删除文件夹。', 'error')
      return
    }
    await deletePermanently([target.id])
    addOutput(`已永久删除文件：${target.name}`)
    return
  }

  if (command === 'deldir') {
    const target = resolveFile(argument)
    if (!target) {
      addOutput(`未找到文件夹：${argument || '(未输入名称)'}`, 'error')
      return
    }
    if (target.kind !== 'folder') {
      addOutput('deldir 只能删除文件夹，请使用 delfile 删除文件。', 'error')
      return
    }
    pendingDeletion.value = { id: target.id, name: target.name }
    addOutput([
      `即将删除文件夹：${target.name}/`,
      '你文件夹内的文件也会被清掉，你确定吗？',
      '输入 y 或“确定”执行删除，输入其他内容取消。',
    ], 'warning')
    return
  }

  if (command === 'download') {
    const target = argument ? resolveFile(argument) : currentFolder.value
    if (!target) {
      addOutput(
        argument ? `未找到文件：${argument}` : '请指定要下载的文件或文件夹。',
        'error',
      )
      return
    }
    addOutput(
      target.kind === 'folder'
        ? `正在打包 ${target.name}/ ...`
        : `正在下载 ${target.name} ...`,
    )
    await downloadFiles([target])
    addOutput(`已开始下载 ${target.name}`)
    return
  }

  if (command === 'upload') {
    requestUpload()
    addOutput('已打开文件选择器，选择文件后会显示上传进度。')
    return
  }

  if (command === 'sharefile' || command === 'sharedir') {
    const target = resolveFile(argument)
    const expectedKind = command === 'sharefile' ? 'file' : 'folder'
    if (!target || target.kind !== expectedKind) {
      addOutput(
        command === 'sharefile'
          ? `未找到要分享的文件：${argument || '请输入文件名'}`
          : `未找到要分享的文件夹：${argument || '请输入文件夹名'}`,
        'error',
      )
      return
    }
    const existing = manager.getActiveShareForFile(target.id)
    pendingShare.value = {
      target,
      step: 'days',
      days: null,
      existing: Boolean(existing),
    }
    addOutput(
      [
        `${command === 'sharefile' ? '文件' : '文件夹'}：${target.name}`,
        existing ? '检测到已有分享，完成后会更新原分享设置。' : '',
        '请选择分享时间（输入 1-30 天）：',
      ].filter(Boolean),
      'warning',
    )
    return
  }

  if (command === 'modshare') {
    const target = resolveFile(argument)
    const existing = target ? manager.getActiveShareForFile(target.id) : null
    if (!target) {
      addOutput(`未找到要修改分享的项目：${argument || '请输入名称'}`, 'error')
      return
    }
    if (!existing) {
      addOutput(
        `${target.name} 当前没有有效分享，请先使用 sharefile 或 sharedir。`,
        'error',
      )
      return
    }
    const remainingDays = Math.min(
      30,
      Math.max(
        1,
        Math.ceil(
          (new Date(existing.expiresAt).getTime() - Date.now()) /
            (24 * 60 * 60 * 1000),
        ),
      ),
    )
    pendingShare.value = {
      target,
      step: 'days',
      days: null,
      existing: true,
      mode: 'modify',
      currentDays: remainingDays,
      currentAccessCode: existing.accessCode || '',
      currentAccessCodeConfigured: Boolean(existing.accessCodeConfigured),
      currentPermission: existing.permission || 'download',
    }
    addOutput(
      [
        `正在修改分享：${target.name}`,
        `当前有效期：约 ${remainingDays} 天`,
        `当前权限：${existing.permission === 'preview' ? '仅在线预览' : '允许下载'}`,
        '请输入新的分享时间（1-30 天，直接回车保持不变）：',
      ],
      'warning',
    )
    return
  }

  if (command === 'ushare') {
    const target = resolveFile(argument)
    const existing = target ? manager.getActiveShareForFile(target.id) : null
    if (!target) {
      addOutput(`未找到要取消分享的项目：${argument || '请输入名称'}`, 'error')
      return
    }
    if (!existing) {
      addOutput(`${target.name} 当前没有有效的分享。`, 'error')
      return
    }
    pendingUnshare.value = { target, share: existing }
    addOutput(
      [
        `即将取消分享：${target.name}`,
        `链接 ${existing.url} 将立即失效。`,
        '输入 y 确认，输入其他内容返回。',
      ],
      'warning',
    )
    return
  }

  if (command === 'find') {
    if (!argument) {
      addOutput('请输入搜索关键词。', 'error')
      return
    }
    navigate('files')
    searchQuery.value = argument
    addOutput(`正在搜索：${argument}`)
    return
  }

  if (command === 'mkdir') {
    if (!argument) {
      addOutput('请输入文件夹名称。', 'error')
      return
    }
    const folder = await createFolder(argument)
    addOutput(folder ? `已创建文件夹：${folder.name}/` : '创建文件夹失败。')
    return
  }

  addOutput(`未知命令：${command}。输入 help 查看命令列表。`, 'error')
}

function submitCommand() {
  const command = commandValue.value.trim()
  if (
    !command &&
    !pendingDeletion.value &&
    !pendingShare.value &&
    !pendingUnshare.value
  ) {
    return
  }

  history.value.push(command)
  historyIndex.value = history.value.length
  commandValue.value = ''
  queuedCommandCount.value += 1
  commandQueue = commandQueue
    .then(async () => {
      addCommand(command)
      await execute(command)
    })
    .catch((error) => {
      addOutput(error?.message || 'Command failed.', 'error')
    })
    .finally(() => {
      queuedCommandCount.value = Math.max(0, queuedCommandCount.value - 1)
      scrollToBottom()
    })
  scrollToBottom()
}

function setSuggestion(value) {
  commandValue.value = value
  input.value?.focus()
}

function fillCompletion() {
  const current = commandValue.value.trim()
  const match = suggestions.find((command) => command.startsWith(current))
  if (current && match) commandValue.value = match
}

function browseHistory(direction) {
  if (!history.value.length) return
  historyIndex.value = Math.max(
    0,
    Math.min(history.value.length, historyIndex.value + direction),
  )
  commandValue.value =
    historyIndex.value === history.value.length
      ? ''
      : history.value[historyIndex.value]
}

function focusTerminal() {
  input.value?.focus()
}

const terminalStorageKey = 'file-home-terminal-window'

function clamp(value, min, max) {
  return Math.min(Math.max(value, min), max)
}

function parentSize() {
  const parent = dock.value?.parentElement
  return {
    width: parent?.clientWidth || globalThis.innerWidth,
    height: parent?.clientHeight || globalThis.innerHeight,
  }
}

function clampWindow() {
  const bounds = parentSize()
  size.width = clamp(size.width, 300, Math.max(300, bounds.width - 16))
  size.height = clamp(size.height, 220, Math.max(220, bounds.height - 16))
  position.x = clamp(position.x, 8, Math.max(8, bounds.width - size.width - 8))
  position.y = clamp(
    position.y,
    8,
    Math.max(8, bounds.height - size.height - 8),
  )
}

function saveWindowPreferences() {
  try {
    globalThis.localStorage?.setItem(
      terminalStorageKey,
      JSON.stringify({
        fontSize: fontSize.value,
        position: { x: position.x, y: position.y },
        size: { width: size.width, height: size.height },
      }),
    )
  } catch {
    // Local storage is optional for this UI-only prototype.
  }
}

function restoreWindowPreferences() {
  try {
    const saved = JSON.parse(
      globalThis.localStorage?.getItem(terminalStorageKey) || 'null',
    )
    if (!saved) return
    if (Number.isFinite(saved.fontSize)) {
      fontSize.value = clamp(saved.fontSize, 9, 20)
    }
    if (Number.isFinite(saved.position?.x)) position.x = saved.position.x
    if (Number.isFinite(saved.position?.y)) position.y = saved.position.y
    if (Number.isFinite(saved.size?.width)) size.width = saved.size.width
    if (Number.isFinite(saved.size?.height)) size.height = saved.size.height
    initialized.value = true
  } catch {
    initialized.value = false
  }
}

function initializeWindow() {
  if (initialized.value) {
    clampWindow()
    return
  }
  const bounds = parentSize()
  size.width = Math.min(740, Math.max(300, bounds.width - 32))
  size.height = Math.min(380, Math.max(220, bounds.height - 32))
  position.x = Math.round((bounds.width - size.width) / 2)
  position.y = Math.max(
    56,
    Math.round((bounds.height - size.height) / 2),
  )
  initialized.value = true
  clampWindow()
  saveWindowPreferences()
}

function changeFontSize(amount) {
  fontSize.value = clamp(fontSize.value + amount, 9, 20)
  saveWindowPreferences()
  scrollToBottom()
}

function onPointerMove(event) {
  if (!dragState.value) return
  const bounds = parentSize()
  const dx = event.clientX - dragState.value.startX
  const dy = event.clientY - dragState.value.startY

  if (dragState.value.mode === 'move') {
    position.x = clamp(
      dragState.value.originX + dx,
      8,
      Math.max(8, bounds.width - size.width - 8),
    )
    position.y = clamp(
      dragState.value.originY + dy,
      8,
      Math.max(8, bounds.height - size.height - 8),
    )
    return
  }

  size.width = clamp(
    dragState.value.originWidth + dx,
    300,
    Math.max(300, bounds.width - position.x - 8),
  )
  size.height = clamp(
    dragState.value.originHeight + dy,
    220,
    Math.max(220, bounds.height - position.y - 8),
  )
}

function stopInteraction() {
  if (!dragState.value) return
  dragState.value = null
  document.body.style.userSelect = ''
  globalThis.removeEventListener('pointermove', onPointerMove)
  globalThis.removeEventListener('pointerup', stopInteraction)
  saveWindowPreferences()
}

function startInteraction(event, mode) {
  if (mode === 'move' && event.target.closest('button, input')) return
  event.preventDefault()
  dragState.value = {
    mode,
    startX: event.clientX,
    startY: event.clientY,
    originX: mode === 'move' ? position.x : size.width,
    originY: mode === 'move' ? position.y : size.height,
    originWidth: size.width,
    originHeight: size.height,
  }
  document.body.style.userSelect = 'none'
  globalThis.addEventListener('pointermove', onPointerMove)
  globalThis.addEventListener('pointerup', stopInteraction)
}

function onWindowResize() {
  if (terminalOpen.value) clampWindow()
}

onMounted(() => {
  restoreWindowPreferences()
  globalThis.addEventListener('resize', onWindowResize)
})

onBeforeUnmount(() => {
  stopInteraction()
  globalThis.removeEventListener('resize', onWindowResize)
})

watch(terminalOpen, async (open) => {
  if (!open) return
  await nextTick()
  initializeWindow()
  focusTerminal()
  scrollToBottom()
})
</script>

<template>
  <Transition name="terminal">
    <section
      v-if="terminalOpen"
      ref="dock"
      class="terminal-dock"
      :class="{
        'is-moving': dragState?.mode === 'move',
        'is-resizing': dragState?.mode === 'resize',
      }"
      :style="{
        left: `${position.x}px`,
        top: `${position.y}px`,
        width: `${size.width}px`,
        height: `${size.height}px`,
        '--terminal-font-size': `${fontSize}px`,
      }"
      @click="focusTerminal"
    >
      <header
        class="terminal-dock__head"
        @pointerdown="startInteraction($event, 'move')"
      >
        <div class="terminal-dock__title">
          <span class="terminal-dock__dots">
            <i />
            <i />
            <i />
          </span>
          <GripHorizontal :size="14" class="terminal-dock__grip" />
          <SquareTerminal :size="14" />
          <strong>filehome terminal</strong>
        </div>
        <div class="terminal-dock__meta">
          <span>{{ queuedCommandCount ? `QUEUED ${queuedCommandCount}` : 'READY' }}</span>
          <span>{{ promptPath }}</span>
        </div>
        <div class="terminal-font-controls">
          <button
            type="button"
            title="缩小字体"
            aria-label="缩小字体"
            @click.stop="changeFontSize(-1)"
          >
            A−
          </button>
          <span
            title="双击恢复默认字号"
            @dblclick.stop="changeFontSize(11 - fontSize)"
          >
            {{ fontSize }}px
          </span>
          <button
            type="button"
            title="放大字体"
            aria-label="放大字体"
            @click.stop="changeFontSize(1)"
          >
            A+
          </button>
        </div>
        <div class="terminal-dock__actions">
          <button
            type="button"
            title="最小化终端"
            aria-label="最小化终端"
            @click.stop="terminalOpen = false"
          >
            <Minus :size="15" />
          </button>
          <button
            type="button"
            title="关闭终端"
            aria-label="关闭终端"
            @click.stop="terminalOpen = false"
          >
            <X :size="15" />
          </button>
        </div>
      </header>

      <div ref="scrollArea" class="terminal-dock__body">
        <div
          v-for="entry in entries"
          :key="entry.id"
          class="terminal-entry"
          :class="[`is-${entry.type}`, entry.tone ? `is-${entry.tone}` : '']"
        >
          <template v-if="entry.type === 'command'">
            <span class="terminal-entry__prompt">
              <b>filehome</b> {{ entry.path }} <i>$</i>
            </span>
            <span>{{ entry.command }}</span>
          </template>
          <template v-else-if="entry.type === 'system'">
            <p
              v-for="(line, index) in entry.lines"
              :key="index"
              :class="{ 'is-title': index === 0 }"
            >
              {{ line }}
            </p>
          </template>
          <template v-else>
            <p v-for="(line, index) in entry.lines" :key="index">{{ line }}</p>
          </template>
        </div>

        <div
          class="terminal-prompt"
          :class="{
            'is-confirming':
              pendingDeletion || pendingUnshare || pendingShare,
          }"
        >
          <span v-if="pendingDeletion" class="terminal-prompt__confirm">
            <b>confirm</b>
            {{ pendingDeletion.name }}
            <i>?</i>
          </span>
          <span v-else-if="pendingUnshare" class="terminal-prompt__confirm">
            <b>unshare</b>
            {{ pendingUnshare.target.name }}
            <i>?</i>
          </span>
          <span v-else-if="pendingShare" class="terminal-prompt__confirm">
            <b>share</b>
            {{ pendingShare.target.name }}
            <i>?</i>
          </span>
          <span v-else>
            <b>filehome</b>
            {{ promptPath }}
            <i>$</i>
          </span>
          <input
            ref="input"
            v-model="commandValue"
            type="text"
            autocomplete="off"
            spellcheck="false"
            :placeholder="pendingPrompt"
            :aria-label="
              pendingDeletion || pendingUnshare || pendingShare
                ? pendingPrompt
                : '终端命令输入'
            "
            @keydown.enter.prevent="submitCommand"
            @keydown.up.prevent="browseHistory(-1)"
            @keydown.down.prevent="browseHistory(1)"
            @keydown.tab.prevent="fillCompletion"
          />
          <span class="terminal-caret" />
        </div>
      </div>

      <footer class="terminal-dock__suggestions">
        <span>COMMANDS</span>
        <button
          v-for="suggestion in suggestions"
          :key="suggestion"
          type="button"
          @click.stop="setSuggestion(suggestion)"
        >
          {{ suggestion }}
        </button>
        <em>Ctrl + `</em>
      </footer>

      <span
        class="terminal-dock__resize"
        title="拖动调整窗口大小"
        @pointerdown.prevent.stop="startInteraction($event, 'resize')"
      >
        <MoveDiagonal2 :size="14" />
      </span>
    </section>
  </Transition>
</template>

<style scoped>
.terminal-dock {
  position: absolute;
  z-index: 70;
  display: flex;
  min-width: 300px;
  min-height: 220px;
  max-width: calc(100% - 16px);
  max-height: calc(100% - 16px);
  flex-direction: column;
  overflow: hidden;
  border: 1px solid rgb(255 255 255 / 11%);
  border-radius: 10px;
  background: #171918;
  color: #d8ded9;
  box-shadow: 0 24px 80px rgb(18 20 18 / 28%);
  font-family: "Cascadia Code", "SFMono-Regular", Consolas, monospace;
  resize: none;
}

.terminal-dock__head {
  display: flex;
  height: 42px;
  flex: 0 0 auto;
  align-items: center;
  justify-content: space-between;
  gap: 18px;
  padding: 0 14px;
  border-bottom: 1px solid rgb(255 255 255 / 7%);
  background: #1c1f1d;
  cursor: grab;
  touch-action: none;
  user-select: none;
}

.terminal-dock.is-moving .terminal-dock__head {
  cursor: grabbing;
}

.terminal-dock__title,
.terminal-dock__meta,
.terminal-dock__actions {
  display: flex;
  align-items: center;
}

.terminal-dock__title {
  gap: 8px;
}

.terminal-dock__grip {
  color: rgb(225 235 230 / 22%);
}

.terminal-dock__title strong {
  color: rgb(239 245 241 / 82%);
  font-size: 10.5px;
  font-weight: 500;
  letter-spacing: 0.02em;
}

.terminal-dock__title > svg {
  color: #9eaeaa;
}

.terminal-dock__dots {
  display: flex;
  gap: 5px;
  margin-right: 5px;
}

.terminal-dock__dots i {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: #4b514d;
}

.terminal-dock__dots i:first-child {
  background: #b7635d;
}

.terminal-dock__dots i:nth-child(2) {
  background: #a98b4d;
}

.terminal-dock__dots i:last-child {
  background: #527d68;
}

.terminal-dock__meta {
  gap: 10px;
  margin-left: auto;
  color: rgb(225 235 230 / 36%);
  font-size: 8px;
}

.terminal-dock__meta span:first-child {
  padding: 2px 5px;
  border: 1px solid rgb(255 255 255 / 9%);
  border-radius: 3px;
  color: rgb(225 235 230 / 50%);
}

.terminal-dock__actions {
  gap: 2px;
}

.terminal-font-controls {
  display: flex;
  height: 24px;
  align-items: center;
  gap: 1px;
  padding: 0 3px;
  border: 1px solid rgb(255 255 255 / 7%);
  border-radius: 5px;
}

.terminal-font-controls button {
  display: grid;
  min-width: 24px;
  height: 20px;
  place-items: center;
  padding: 0 4px;
  border: 0;
  border-radius: 3px;
  background: transparent;
  color: rgb(230 238 234 / 48%);
  font: inherit;
  font-size: 8px;
  cursor: pointer;
}

.terminal-font-controls button:hover {
  background: rgb(255 255 255 / 7%);
  color: #fff;
}

.terminal-font-controls > span {
  min-width: 29px;
  color: rgb(225 235 230 / 31%);
  font-size: 7px;
  text-align: center;
  cursor: default;
  user-select: none;
}

.terminal-dock__actions button {
  display: grid;
  width: 27px;
  height: 27px;
  place-items: center;
  padding: 0;
  border: 0;
  border-radius: 5px;
  background: transparent;
  color: rgb(230 238 234 / 48%);
  cursor: pointer;
}

.terminal-dock__actions button:hover {
  background: rgb(255 255 255 / 7%);
  color: #fff;
}

.terminal-dock__body {
  flex: 1;
  overflow-y: auto;
  padding: 13px 17px 6px;
  cursor: text;
}

.terminal-entry {
  margin-bottom: 5px;
  color: rgb(222 232 227 / 64%);
  font-size: var(--terminal-font-size, 11px);
  line-height: 1.62;
  white-space: pre-wrap;
}

.terminal-entry p {
  margin: 0;
}

.terminal-entry.is-command {
  display: flex;
  flex-wrap: wrap;
  gap: 7px;
  color: rgb(239 245 242 / 88%);
}

.terminal-entry__prompt b,
.terminal-prompt b {
  color: #91a6ff;
  font-weight: 500;
}

.terminal-entry__prompt i,
.terminal-prompt i {
  color: #6e9d86;
  font-style: normal;
}

.terminal-entry.is-system {
  margin-bottom: 8px;
}

.terminal-entry.is-system p {
  color: rgb(222 232 227 / 45%);
}

.terminal-entry.is-system p.is-title {
  color: #8ca69d;
  font-size: 9px;
  letter-spacing: 0.1em;
}

.terminal-entry.is-error p {
  color: #d98d83;
}

.terminal-entry.is-warning p {
  color: #e0b56b;
}

.terminal-prompt {
  display: flex;
  align-items: center;
  gap: 7px;
  color: rgb(239 245 242 / 88%);
  font-size: var(--terminal-font-size, 11px);
}

.terminal-prompt > span:first-child {
  flex: 0 0 auto;
}

.terminal-prompt.is-confirming > span:first-child {
  color: #e0b56b;
}

.terminal-prompt__confirm b {
  color: #e0b56b !important;
}

.terminal-prompt__confirm i {
  color: #e0b56b !important;
}

.terminal-prompt input {
  min-width: 0;
  flex: 1;
  padding: 0;
  border: 0;
  outline: 0;
  background: transparent;
  color: #f1f6f3;
  font: inherit;
  font-size: var(--terminal-font-size, 11px);
  caret-color: #91a6ff;
}

.terminal-prompt input::placeholder {
  color: rgb(224 181 107 / 52%);
}

.terminal-caret {
  width: 6px;
  height: 14px;
  margin-left: -4px;
  background: #91a6ff;
  opacity: 0.7;
  animation: blink 1.15s steps(2, jump-none) infinite;
}

.terminal-prompt input:focus + .terminal-caret {
  opacity: 1;
}

.terminal-dock__suggestions {
  display: flex;
  height: 32px;
  flex: 0 0 auto;
  align-items: center;
  gap: 5px;
  padding: 0 31px 0 16px;
  overflow-x: auto;
  border-top: 1px solid rgb(255 255 255 / 6%);
  color: rgb(222 232 227 / 30%);
}

.terminal-dock__suggestions > span {
  margin-right: 5px;
  font-size: 7px;
  letter-spacing: 0.1em;
}

.terminal-dock__suggestions button {
  padding: 2px 6px;
  border: 1px solid rgb(255 255 255 / 7%);
  border-radius: 3px;
  background: transparent;
  color: rgb(225 235 230 / 42%);
  font: inherit;
  font-size: 8px;
  cursor: pointer;
}

.terminal-dock__suggestions button:hover {
  border-color: rgb(145 166 255 / 35%);
  color: #aebcff;
}

.terminal-dock__suggestions em {
  margin-left: auto;
  color: rgb(225 235 230 / 24%);
  font-size: 8px;
  font-style: normal;
}

.terminal-dock__resize {
  position: absolute;
  right: 2px;
  bottom: 2px;
  display: grid;
  width: 22px;
  height: 22px;
  place-items: center;
  color: rgb(227 237 232 / 28%);
  cursor: nwse-resize;
  touch-action: none;
}

.terminal-dock__resize:hover {
  color: rgb(227 237 232 / 62%);
}

@keyframes blink {
  50% {
    opacity: 0;
  }
}

.terminal-enter-active,
.terminal-leave-active {
  transition:
    opacity 180ms ease,
    transform 220ms cubic-bezier(0.2, 0.7, 0.2, 1);
}

.terminal-enter-from,
.terminal-leave-to {
  opacity: 0;
  transform: translateY(12px) scale(0.985);
}

@media (max-width: 700px) {
  .terminal-dock__meta,
  .terminal-dock__suggestions em {
    display: none;
  }

  .terminal-dock__head {
    gap: 8px;
    padding: 0 10px;
  }

  .terminal-dock__dots,
  .terminal-dock__grip {
    display: none;
  }

  .terminal-dock__title {
    gap: 6px;
  }

  .terminal-dock__title strong {
    max-width: 124px;
    overflow: hidden;
    font-size: 9.5px;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  .terminal-dock__body {
    padding-right: 12px;
    padding-left: 12px;
  }
}
</style>
