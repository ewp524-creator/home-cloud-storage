import { computed, reactive, ref, watch } from 'vue'
import {
  login as loginApi,
  logout as logoutApi,
  me as meApi,
  register as registerApi,
} from '../services/authApi'
import {
  copyFiles,
  createFolder as createFolderApi,
  deleteFilePermanent,
  deleteFolderPermanent,
  downloadEntry,
  fileDetails,
  folderTree,
  listFiles,
  moveFiles,
  recentFiles,
  renameFile as renameFileApi,
  restoreFiles as restoreFilesApi,
  starFile,
  starredFiles,
  trashFiles,
  trashList,
  uploadFile,
} from '../services/filesApi'
import {
  createShare as createShareApi,
  listShares,
  revokeShare as revokeShareApi,
} from '../services/sharesApi'
import {
  removeAvatar as removeAvatarApi,
  uploadAvatar as uploadAvatarApi,
} from '../services/profileApi'
import {
  changePassword as changePasswordApi,
  deactivateAccount as deactivateAccountApi,
  getUserSettings,
  updatePreferences as updatePreferencesApi,
  updateProfile as updateProfileApi,
} from '../services/settingsApi'
import { setUploadConcurrency } from '../services/multipartUpload'
import { getFileFormat } from '../utils/fileFormats'
import { uid } from '../utils/formatters'

const VIEW_TITLES = {
  files: '我的文件',
  recent: '最近使用',
  starred: '已收藏',
  shared: '分享中心',
  trash: '回收站',
  tagged: '标签文件',
}

function normalizeUser(userView) {
  return {
    id: userView.id,
    username: userView.username,
    name: userView.displayName || userView.username,
    initials: userView.initials || (userView.displayName || '?').slice(0, 1),
    avatarUrl: userView.avatarUrl || '',
    email: userView.email,
    plan: userView.plan || '个人云空间',
    storageTotal: userView.storageQuotaBytes || 20 * 1024 ** 3,
    storageUsed: 0,
    fileCount: 0,
    folderCount: 0,
  }
}

function normalizeFile(file) {
  return {
    ...file,
    modifiedAt: file.modifiedAt || new Date().toISOString(),
    owner: file.owner || '',
    starred: Boolean(file.starred),
    tags: file.tags || [],
    shared: Boolean(file.shared),
    hasChildren: Boolean(file.hasChildren || file.itemCount > 0),
  }
}

function flattenTree(nodes, parentId = null, output = []) {
  for (const node of nodes || []) {
    const normalized = normalizeFile({
      ...node,
      parentId: node.parentId ?? parentId,
      size: node.size || 0,
      tags: node.tags || [],
    })
    output.push(normalized)
    flattenTree(node.children, node.id, output)
  }
  return output
}

function descendants(fileId, entries) {
  const result = new Set([fileId])
  let changed = true
  while (changed) {
    changed = false
    for (const entry of entries) {
      if (entry.parentId && result.has(entry.parentId) && !result.has(entry.id)) {
        result.add(entry.id)
        changed = true
      }
    }
  }
  return result
}

export function useFileManager() {
  const authenticated = ref(false)
  const authReady = ref(false)
  const user = reactive({
    id: '',
    username: '',
    name: '',
    initials: 'FH',
    avatarUrl: '',
    email: '',
    plan: '个人云空间',
    storageUsed: 0,
    storageTotal: 20 * 1024 ** 3,
    fileCount: 0,
    folderCount: 0,
  })
  const preferences = reactive({
    defaultView: 'GRID',
    defaultSort: 'modified-desc',
    reduceMotion: false,
    uploadConcurrency: 4,
  })

  const pageItems = ref([])
  const treeNodes = ref([])
  const loadedTreeFolders = ref(new Set())
  const trashItems = ref([])
  const shares = ref([])
  const activities = ref([])
  const rootFolderId = ref(null)
  const serverBreadcrumbs = ref([])
  const currentView = ref('files')
  const currentFolderId = ref(null)
  const currentTag = ref('')
  const selectedIds = ref([])
  const searchQuery = ref('')
  const typeFilter = ref('all')
  const sortBy = ref('modified-desc')
  const viewMode = ref(
    globalThis.localStorage?.getItem('file-home-view-mode') || 'grid',
  )
  const uploads = ref([])
  const toasts = ref([])
  const detailsFileId = ref(null)
  const previewFileId = ref(null)
  const sidebarOpen = ref(false)
  const terminalOpen = ref(false)
  const notificationsOpen = ref(false)
  const profileOpen = ref(false)
  const clipboardIds = ref([])
  const loading = ref(false)
  const loadingMore = ref(false)
  const hasMoreFiles = ref(false)
  const nextFileCursor = ref('')

  let uploadHandler = null
  let currentLoadRequest = 0

  function handleSessionExpired() {
    authenticated.value = false
    pageItems.value = []
    treeNodes.value = []
    shares.value = []
    detailsFileId.value = null
    previewFileId.value = null
    profileOpen.value = false
  }

  globalThis.addEventListener?.(
    'filehome:unauthorized',
    handleSessionExpired,
  )

  const files = computed(() => {
    const map = new Map()
    for (const item of treeNodes.value) map.set(item.id, item)
    for (const item of pageItems.value) map.set(item.id, item)
    for (const item of trashItems.value) map.set(item.id, item)
    return [...map.values()]
  })

  const rootFolder = computed(() =>
    files.value.find((file) => file.id === rootFolderId.value),
  )

  const currentFolder = computed(() =>
    currentFolderId.value
      ? files.value.find((file) => file.id === currentFolderId.value) || null
      : null,
  )

  const breadcrumbs = computed(() => {
    if (!currentFolderId.value) return []
    if (serverBreadcrumbs.value.length) return serverBreadcrumbs.value
    const byId = new Map(files.value.map((file) => [file.id, file]))
    const trail = []
    let cursor = currentFolder.value
    while (cursor && cursor.id !== rootFolderId.value) {
      trail.unshift(cursor)
      cursor = byId.get(cursor.parentId)
    }
    return trail
  })

  const title = computed(() => {
    if (currentFolder.value) return currentFolder.value.name
    if (currentView.value === 'tagged' && currentTag.value) {
      return `# ${currentTag.value}`
    }
    return VIEW_TITLES[currentView.value] || '我的文件'
  })

  const subtitle = computed(() => {
    if (currentView.value === 'trash') return '已删除的文件会在这里保留 30 天'
    if (currentView.value === 'shared') return '集中管理外链、提取码和访问权限'
    if (currentView.value === 'recent') return '按最近修改时间整理的文件'
    if (currentView.value === 'starred') return '快速找到你标记的重要内容'
    if (currentView.value === 'tagged') return `标签为「${currentTag.value}」的文件`
    if (currentFolder.value) return `${currentFolder.value.itemCount || 0} 个项目`
    return '所有项目井然有序，重要文件随时可用'
  })

  const visibleFiles = computed(() => {
    if (currentView.value === 'trash') {
      return trashItems.value.filter((file) => file.deletedAt)
    }
    let items = pageItems.value.filter((file) => !file.deletedAt)
    if (currentView.value === 'files') {
      return items
    }
    if (currentView.value === 'starred') {
      items = items.filter((file) => file.starred)
    }
    if (currentView.value === 'recent') {
      const cutoff = Date.now() - 7 * 24 * 60 * 60 * 1000
      items = items.filter(
        (file) => new Date(file.modifiedAt).getTime() >= cutoff,
      )
    }
    if (currentView.value === 'tagged') {
      items = items.filter((file) => file.tags?.includes(currentTag.value))
    }
    const query = searchQuery.value.trim().toLowerCase()
    if (query) {
      items = items.filter(
        (file) =>
          file.name.toLowerCase().includes(query) ||
          file.tags?.some((tag) => tag.toLowerCase().includes(query)),
      )
    }
    if (typeFilter.value !== 'all') {
      items = items.filter((file) => matchesType(file, typeFilter.value))
    }
    return [...items].sort((a, b) => {
      if (sortBy.value === 'name-asc') {
        return a.name.localeCompare(b.name, 'zh-CN')
      }
      if (sortBy.value === 'size-desc') return b.size - a.size
      if (sortBy.value === 'modified-asc') {
        return new Date(a.modifiedAt) - new Date(b.modifiedAt)
      }
      return new Date(b.modifiedAt) - new Date(a.modifiedAt)
    })
  })

  const selectedFiles = computed(() =>
    selectedIds.value
      .map((id) => files.value.find((file) => file.id === id))
      .filter(Boolean),
  )
  const detailsFile = computed(
    () => files.value.find((file) => file.id === detailsFileId.value) || null,
  )
  const previewFile = computed(
    () => files.value.find((file) => file.id === previewFileId.value) || null,
  )
  const activeShares = computed(() =>
    shares.value.filter((share) => share.status === 'active'),
  )
  const clipboardHasItems = computed(() =>
    clipboardIds.value.some((id) => files.value.some((file) => file.id === id)),
  )
  const totalStorage = computed(() =>
    files.value.reduce((sum, file) => sum + (file.kind === 'file' ? file.size : 0), 0),
  )
  const fileCount = computed(() =>
    currentView.value === 'files'
      ? user.fileCount
      : files.value.filter((file) => file.kind === 'file' && !file.deletedAt).length,
  )
  const folderCount = computed(() =>
    currentView.value === 'files'
      ? user.folderCount
      : files.value.filter(
          (file) =>
            file.kind === 'folder' &&
            !file.deletedAt &&
            file.id !== rootFolderId.value,
        ).length,
  )

  function toast(message, tone = 'success') {
    const id = uid('toast')
    toasts.value.push({ id, message, tone })
    globalThis.setTimeout(() => {
      toasts.value = toasts.value.filter((item) => item.id !== id)
    }, 3600)
  }

  function dismissToast(id) {
    toasts.value = toasts.value.filter((item) => item.id !== id)
  }

  function applyUser(userView) {
    Object.assign(user, normalizeUser(userView))
  }

  function applyPreferences(value) {
    Object.assign(preferences, {
      defaultView: value?.defaultView || 'GRID',
      defaultSort: value?.defaultSort || 'modified-desc',
      reduceMotion: Boolean(value?.reduceMotion),
      uploadConcurrency: Number(value?.uploadConcurrency) || 4,
    })
    setUploadConcurrency(preferences.uploadConcurrency)
    globalThis.document?.documentElement?.classList.toggle(
      'reduce-motion',
      preferences.reduceMotion,
    )
    if (!globalThis.localStorage?.getItem('file-home-view-mode')) {
      viewMode.value = preferences.defaultView.toLowerCase()
    }
    sortBy.value = preferences.defaultSort
  }

  function applyShares(items) {
    shares.value = (items || []).map((share) => ({
      ...share,
      fileName: share.fileName,
      rootType: share.rootType,
      url: share.url,
      accessCode: share.accessCode || '',
      accessCodeConfigured: Boolean(share.accessCodeConfigured),
    }))
  }

  function setFileShared(fileId, shared) {
    pageItems.value = pageItems.value.map((file) =>
      file.id === fileId ? { ...file, shared } : file,
    )
    treeNodes.value = treeNodes.value.map((file) =>
      file.id === fileId ? { ...file, shared } : file,
    )
    trashItems.value = trashItems.value.map((file) =>
      file.id === fileId ? { ...file, shared } : file,
    )
  }

  function mergeTreeNodes(items, parentId = null) {
    const map = new Map(treeNodes.value.map((item) => [item.id, item]))
    for (const item of items || []) {
      const normalized = normalizeFile({
        ...item,
        parentId: item.parentId ?? parentId,
        size: item.size || 0,
        tags: item.tags || [],
      })
      map.set(normalized.id, { ...map.get(normalized.id), ...normalized })
    }
    treeNodes.value = [...map.values()]
  }

  function ensureRootTreeNode() {
    if (!rootFolderId.value || !user.username) return
    mergeTreeNodes([
      {
        id: rootFolderId.value,
        name: user.username,
        kind: 'folder',
        parentId: null,
        size: 0,
        hasChildren: true,
      },
    ])
  }

  async function loadTree(parentId = null) {
    const nodes = await folderTree(parentId || undefined)
    const parentKey = parentId || rootFolderId.value
    if (parentKey) {
      const loaded = new Set(loadedTreeFolders.value)
      loaded.add(parentKey)
      loadedTreeFolders.value = loaded
    }
    mergeTreeNodes(flattenTree(nodes), parentKey)
  }

  async function loadTreeChildren(folderId = null) {
    const parentId = folderId === rootFolderId.value ? null : folderId
    if (loadedTreeFolders.value.has(parentId || rootFolderId.value)) return
    await loadTree(parentId)
  }

  async function loadCurrent({ append = false, refreshTree = true } = {}) {
    if (append && currentView.value !== 'files' && currentView.value !== 'tagged') {
      return
    }
    const requestId = append ? currentLoadRequest : ++currentLoadRequest
    if (append) {
      loadingMore.value = true
    } else {
      loadingMore.value = false
      loading.value = true
    }
    try {
      let data
      if (currentView.value === 'recent') {
        const items = await recentFiles()
        data = {
          items,
          rootFolderId: rootFolderId.value,
          usedBytes: items.reduce((sum, file) => sum + (file.size || 0), 0),
        }
      } else if (currentView.value === 'starred') {
        const items = await starredFiles()
        data = {
          items,
          rootFolderId: rootFolderId.value,
          usedBytes: items.reduce((sum, file) => sum + (file.size || 0), 0),
        }
      } else {
        data = await listFiles({
          parentId: currentFolderId.value || undefined,
          q: searchQuery.value.trim() || undefined,
          type: typeFilter.value,
          tag: currentTag.value || undefined,
          sort: sortBy.value,
          cursor: append ? nextFileCursor.value : undefined,
          limit: 80,
        })
      }
      if (requestId !== currentLoadRequest) return
      const items = (data.items || []).map(normalizeFile)
      if (append) {
        const known = new Set(pageItems.value.map((item) => item.id))
        pageItems.value = [
          ...pageItems.value,
          ...items.filter((item) => !known.has(item.id)),
        ]
      } else {
        pageItems.value = items
      }
      rootFolderId.value = data.rootFolderId || rootFolderId.value
      hasMoreFiles.value = Boolean(data.hasMore)
      nextFileCursor.value = data.nextCursor || ''
      if (data.usedBytes !== undefined) user.storageUsed = data.usedBytes
      if (data.quotaBytes) user.storageTotal = data.quotaBytes
      if (data.fileCount !== undefined) user.fileCount = data.fileCount
      if (data.folderCount !== undefined) user.folderCount = data.folderCount
      if (!append && refreshTree) {
        treeNodes.value = []
        loadedTreeFolders.value = new Set()
        await loadTree(null)
        ensureRootTreeNode()
        const contextNodes = [
          ...(data.breadcrumbs || []),
          ...(data.currentFolder ? [data.currentFolder] : []),
        ].map(normalizeFile)
        serverBreadcrumbs.value = (data.breadcrumbs || []).map(normalizeFile)
        mergeTreeNodes(contextNodes)
      } else if (!append) {
        const contextNodes = [
          ...(data.breadcrumbs || []),
          ...(data.currentFolder ? [data.currentFolder] : []),
        ].map(normalizeFile)
        serverBreadcrumbs.value = (data.breadcrumbs || []).map(normalizeFile)
        mergeTreeNodes(contextNodes)
      }
      activities.value = pageItems.value
        .slice()
        .sort((a, b) => new Date(b.modifiedAt) - new Date(a.modifiedAt))
        .slice(0, 6)
        .map((file, index) => ({
          id: `activity-${file.id}-${index}`,
          action: file.kind === 'folder' ? '更新了文件夹' : '更新了',
          target: file.name,
          time: file.modifiedAt,
          color: ['teal', 'blue', 'violet', 'amber'][index % 4],
        }))
    } finally {
      if (requestId === currentLoadRequest) {
        if (append) loadingMore.value = false
        else loading.value = false
      }
    }
  }

  async function refreshFiles() {
    await loadCurrent()
  }

  async function loadMoreFiles() {
    if (!hasMoreFiles.value || !nextFileCursor.value || loadingMore.value) return
    await loadCurrent({ append: true })
  }

  async function loadShares() {
    try {
      applyShares(await listShares())
    } catch (error) {
      if (authenticated.value) toast(error.message, 'warning')
    }
  }

  async function loadTrash() {
    const items = await trashList()
    trashItems.value = items.map((file) => ({
      ...normalizeFile(file),
      deletedAt: file.deletedAt || file.modifiedAt,
    }))
  }

  async function loadUserSettings() {
    const settings = await getUserSettings()
    applyUser(settings.profile)
    applyPreferences(settings.preferences)
    return settings
  }

  async function initialize() {
    authReady.value = false
    try {
      applyUser(await meApi())
      await loadUserSettings()
      authenticated.value = true
      await loadCurrent()
      await loadShares()
    } catch {
      handleSessionExpired()
    } finally {
      authReady.value = true
    }
  }

  async function login(payload) {
    const response = await loginApi(payload)
    applyUser(response.user)
    await loadUserSettings()
    authenticated.value = true
    await loadCurrent()
    await loadShares()
    toast(`欢迎回来，${user.name}`)
    return response
  }

  async function registerAccount(payload) {
    const response = await registerApi(payload)
    applyUser(response.user)
    await loadUserSettings()
    authenticated.value = true
    await loadCurrent()
    await loadShares()
    toast('账户已创建，个人根目录已初始化')
    return response
  }

  async function logout() {
    try {
      await logoutApi()
    } finally {
      authenticated.value = false
      pageItems.value = []
      treeNodes.value = []
      shares.value = []
      profileOpen.value = false
    }
  }

  async function uploadAvatar(file) {
    if (!file) return null
    if (!file.type.startsWith('image/')) {
      toast('请选择 PNG、JPEG 或 GIF 图片', 'warning')
      return null
    }
    if (file.size > 5 * 1024 * 1024) {
      toast('头像源文件不能超过 5 MB', 'warning')
      return null
    }
    const updated = await uploadAvatarApi(file)
    applyUser(updated)
    toast('头像已更新')
    return updated
  }

  async function removeAvatar() {
    await removeAvatarApi()
    user.avatarUrl = ''
    toast('头像已移除')
  }

  async function saveProfile(payload) {
    const updated = await updateProfileApi(payload)
    applyUser(updated)
    toast('账户资料已更新')
    return updated
  }

  async function savePreferences(payload) {
    const settings = await updatePreferencesApi(payload)
    applyPreferences(settings.preferences)
    toast('偏好设置已保存')
    return settings
  }

  async function changeAccountPassword(payload) {
    await changePasswordApi(payload)
    toast('密码已更新，其他设备已退出')
  }

  async function deactivateCurrentAccount(payload) {
    await deactivateAccountApi(payload)
    handleSessionExpired()
  }

  async function navigate(view, tag = '') {
    currentView.value = view
    currentFolderId.value = null
    serverBreadcrumbs.value = []
    hasMoreFiles.value = false
    nextFileCursor.value = ''
    currentTag.value = tag
    typeFilter.value = 'all'
    searchQuery.value = ''
    selectedIds.value = []
    sidebarOpen.value = false
    try {
      if (view === 'trash') await loadTrash()
      else if (view === 'shared') await loadShares()
      else await loadCurrent()
    } catch (error) {
      toast(error.message, 'warning')
    }
  }

  async function openFolder(folder) {
    if (!folder || folder.kind !== 'folder' || folder.deletedAt) return
    currentView.value = 'files'
    currentFolderId.value = folder.id
    serverBreadcrumbs.value = []
    hasMoreFiles.value = false
    nextFileCursor.value = ''
    currentTag.value = ''
    searchQuery.value = ''
    typeFilter.value = 'all'
    selectedIds.value = []
    try {
      await loadCurrent()
    } catch (error) {
      toast(error.message, 'warning')
    }
  }

  async function goToBreadcrumb(id) {
    currentView.value = 'files'
    currentFolderId.value = id
    serverBreadcrumbs.value = []
    hasMoreFiles.value = false
    nextFileCursor.value = ''
    currentTag.value = ''
    searchQuery.value = ''
    typeFilter.value = 'all'
    selectedIds.value = []
    try {
      await loadCurrent()
    } catch (error) {
      toast(error.message, 'warning')
    }
  }

  function selectFile(file, additive = false) {
    if (!file) return
    detailsFileId.value = null
    if (additive) {
      selectedIds.value = selectedIds.value.includes(file.id)
        ? selectedIds.value.filter((id) => id !== file.id)
        : [...selectedIds.value, file.id]
    } else {
      selectedIds.value = [file.id]
    }
  }

  function selectOnly(file) {
    selectedIds.value = file ? [file.id] : []
    detailsFileId.value = null
  }

  function selectAllVisible() {
    selectedIds.value = visibleFiles.value.map((file) => file.id)
  }

  function clearSelection() {
    selectedIds.value = []
  }

  function openDetails(file) {
    if (file) detailsFileId.value = file.id
  }

  function closeDetails() {
    detailsFileId.value = null
  }

  function openPreview(file) {
    if (file?.kind === 'folder') {
      openFolder(file)
      return
    }
    if (file) previewFileId.value = file.id
  }

  function closePreview() {
    previewFileId.value = null
  }

  async function createFolder(name) {
    try {
      const folder = await createFolderApi(
        currentFolderId.value || rootFolderId.value,
        name.trim(),
      )
      await loadCurrent()
      toast(`文件夹「${folder.name}」已创建`)
      return folder
    } catch (error) {
      toast(error.message, 'warning')
      return null
    }
  }

  async function renameFile(file, name) {
    try {
      await renameFileApi(file.id, name.trim())
      await loadCurrent()
      toast('名称已更新')
      return true
    } catch (error) {
      toast(error.message, 'warning')
      return false
    }
  }

  async function toggleStar(file) {
    try {
      await starFile(file.id, !file.starred)
      await loadCurrent()
      toast(file.starred ? '已取消收藏' : '已加入收藏')
    } catch (error) {
      toast(error.message, 'warning')
    }
  }

  async function moveItems(ids, targetFolderId) {
    try {
      await moveFiles(ids, targetFolderId || rootFolderId.value)
      await loadCurrent()
      toast('项目已移动')
      return true
    } catch (error) {
      toast(error.message, 'warning')
      return false
    }
  }

  async function copyItems(ids, targetFolderId, options = {}) {
    try {
      const nameOverrides =
        options.name && ids.length === 1
          ? [{ id: ids[0], name: options.name }]
          : []
      const result = await copyFiles(
        ids,
        targetFolderId || rootFolderId.value,
        nameOverrides,
      )
      await loadCurrent()
      toast(`已复制 ${result.count} 个项目`)
      return result.ids || []
    } catch (error) {
      toast(error.message, 'warning')
      return []
    }
  }

  function copyItemsToClipboard(ids) {
    clipboardIds.value = [...ids]
    toast(`已复制 ${ids.length} 个项目，可粘贴到文件夹`)
  }

  async function pasteCopiedItems(targetFolderId = currentFolderId.value) {
    if (!clipboardHasItems.value) {
      toast('剪贴板中没有可粘贴的项目', 'info')
      return []
    }
    return copyItems(clipboardIds.value, targetFolderId || rootFolderId.value)
  }

  function addUpload(file) {
    const parentId = currentFolderId.value || rootFolderId.value
    const upload = reactive({
      id: uid('upload'),
      name: file.name,
      size: file.size,
      progress: 0,
      status: 'uploading',
      detail: '准备上传',
      parentId,
      format: getFileFormat({ name: file.name }),
    })
    uploads.value.unshift(upload)
    uploadFile(
      file,
      parentId,
      (progress) => {
        upload.progress = progress
      },
      (state) => {
        if (state.retrying) {
          upload.detail = `连接中断，重试 ${state.attempt}/${state.maxRetries}`
          return
        }
        upload.detail = state.resumed
          ? `继续 ${state.uploadedParts}/${state.totalParts} 片`
          : `${state.uploadedParts}/${state.totalParts} 片`
      },
    )
      .then(async () => {
        upload.progress = 100
        upload.status = 'done'
        upload.detail = '完成'
        await loadCurrent()
        toast(`${file.name} 上传完成`)
      })
      .catch((error) => {
        upload.status = 'failed'
        upload.detail = '失败'
        toast(error.message || `${file.name} 上传失败`, 'warning')
      })
    return upload
  }

  function addUploads(fileList) {
    const items = Array.from(fileList || [])
    items.forEach(addUpload)
    if (items.length) toast(`正在上传 ${items.length} 个文件`, 'info')
  }

  function requestUpload() {
    uploadHandler?.()
  }

  function registerUploadHandler(handler) {
    uploadHandler = handler
  }

  function clearCompletedUploads() {
    uploads.value = uploads.value.filter((item) => item.status === 'uploading')
  }

  async function downloadFiles(items) {
    const list = (items || []).filter(Boolean)
    for (const entry of list) {
      try {
        toast(
          entry.kind === 'folder'
            ? `正在打包「${entry.name}」`
            : `正在下载「${entry.name}」`,
          'info',
        )
        await downloadEntry(entry)
      } catch (error) {
        toast(error.message, 'warning')
      }
    }
  }

  async function moveToTrash(ids) {
    try {
      await trashFiles(ids)
      selectedIds.value = []
      detailsFileId.value = null
      await loadCurrent()
      toast('已移到回收站', 'info')
    } catch (error) {
      toast(error.message, 'warning')
    }
  }

  async function restoreFiles(ids) {
    try {
      await restoreFilesApi(ids)
      await loadTrash()
      toast('项目已恢复')
    } catch (error) {
      toast(error.message, 'warning')
    }
  }

  async function deletePermanently(ids) {
    try {
      for (const id of ids) {
        const entry = files.value.find((file) => file.id === id)
        if (entry?.kind === 'folder') await deleteFolderPermanent(id)
        else await deleteFilePermanent(id)
      }
      selectedIds.value = []
      if (currentView.value === 'trash') await loadTrash()
      else await loadCurrent()
      toast('已永久删除', 'warning')
    } catch (error) {
      toast(error.message, 'warning')
    }
  }

  async function createShare(file, options = {}) {
    try {
      const wasShared = Boolean(getActiveShareForFile(file.id))
      const share = await createShareApi({
        rootEntryId: file.id,
        days: Number(options.days || 7),
        accessCode: options.accessCode || '',
        permission: options.permission || 'download',
        recursive: file.kind === 'folder',
      })
      await loadShares()
      setFileShared(file.id, true)
      if (['files', 'recent', 'starred', 'tagged'].includes(currentView.value)) {
        await loadCurrent({ refreshTree: false })
      }
      toast(wasShared ? '分享设置已更新' : '分享链接已创建')
      return share
    } catch (error) {
      toast(error.message, 'warning')
      return null
    }
  }

  async function revokeShare(shareId) {
    try {
      const share = shares.value.find((item) => item.id === shareId)
      await revokeShareApi(shareId)
      if (share?.fileId) setFileShared(share.fileId, false)
      await loadShares()
      if (['files', 'recent', 'starred', 'tagged'].includes(currentView.value)) {
        await loadCurrent({ refreshTree: false })
      }
      toast('分享链接已失效', 'info')
    } catch (error) {
      toast(error.message, 'warning')
    }
  }

  function getActiveShareForFile(fileId) {
    return shares.value.find((share) => {
      if (share.status !== 'active') return false
      if (share.fileId === fileId) return true
      if (share.rootType !== 'folder') return false
      return descendants(share.fileId, files.value).has(fileId)
    })
  }

  function getShareItemCount(share) {
    return share?.itemCount || 0
  }

  async function copyText(value, message = '已复制到剪贴板') {
    try {
      await navigator.clipboard.writeText(value)
      toast(message)
    } catch {
      toast('复制失败，请手动选择文本', 'warning')
    }
  }

  function setViewMode(mode) {
    viewMode.value = mode
    globalThis.localStorage?.setItem('file-home-view-mode', mode)
  }

  function toggleTerminal() {
    terminalOpen.value = !terminalOpen.value
  }

  let filterReloadTimer = null
  watch([searchQuery, typeFilter, sortBy], () => {
    if (
      !authenticated.value ||
      (currentView.value !== 'files' && currentView.value !== 'tagged')
    ) {
      return
    }
    globalThis.clearTimeout(filterReloadTimer)
    filterReloadTimer = globalThis.setTimeout(() => {
      loadCurrent({ refreshTree: false }).catch((error) =>
        toast(error.message, 'warning'),
      )
    }, 220)
  })

  function getFolderItemCount(folderId) {
    return files.value.filter((file) => file.parentId === folderId).length
  }

  function matchesType(file, filter) {
    if (filter === 'folder') return file.kind === 'folder'
    const kind = getFileFormat(file).kind
    if (filter === 'document') {
      return ['word', 'excel', 'slides', 'pdf', 'text'].includes(kind)
    }
    if (filter === 'media') {
      return ['image', 'video', 'audio', 'design'].includes(kind)
    }
    if (filter === 'code') return kind === 'code'
    return true
  }

  return {
    activities,
    activeShares,
    authReady,
    authenticated,
    breadcrumbs,
    clipboardHasItems,
    clipboardIds,
    currentFolder,
    currentFolderId,
    currentTag,
    currentView,
    detailsFile,
    detailsFileId,
    fileCount,
    files,
    folderCount,
    hasMoreFiles,
    loading,
    loadingMore,
    notificationsOpen,
    preferences,
    previewFile,
    previewFileId,
    profileOpen,
    rootFolder,
    rootFolderId,
    searchQuery,
    selectedFiles,
    selectedIds,
    shares,
    sidebarOpen,
    sortBy,
    subtitle,
    title,
    toasts,
    totalStorage,
    terminalOpen,
    typeFilter,
    uploads,
    uploadAvatar,
    user,
    viewMode,
    visibleFiles,
    addUploads,
    clearCompletedUploads,
    clearSelection,
    closeDetails,
    closePreview,
    copyItems,
    copyItemsToClipboard,
    copyText,
    createFolder,
    createShare,
    deactivateCurrentAccount,
    deletePermanently,
    dismissToast,
    downloadFiles,
    getActiveShareForFile,
    getFolderItemCount,
    getShareItemCount,
    goToBreadcrumb,
    initialize,
    login,
    loadMoreFiles,
    loadTreeChildren,
    logout,
    moveItems,
    moveToTrash,
    navigate,
    openDetails,
    openFolder,
    openPreview,
    pasteCopiedItems,
    registerAccount,
    registerUploadHandler,
    removeAvatar,
    refreshFiles,
    renameFile,
    requestUpload,
    restoreFiles,
    revokeShare,
    savePreferences,
    saveProfile,
    selectAllVisible,
    selectFile,
    selectOnly,
    setViewMode,
    changeAccountPassword,
    toast,
    toggleStar,
    toggleTerminal,
  }
}
