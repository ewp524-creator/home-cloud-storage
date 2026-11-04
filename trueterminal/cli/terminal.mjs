import { execFile } from 'node:child_process'
import fs from 'node:fs'
import { createWriteStream } from 'node:fs'
import fsPromises from 'node:fs/promises'
import path from 'node:path'
import process from 'node:process'
import { Readable } from 'node:stream'
import { pipeline } from 'node:stream/promises'
import readline from 'node:readline/promises'
import { promisify } from 'node:util'
import {
  AuthenticationError,
  FileHomeApi,
} from './api.mjs'
import {
  clearSession,
  DEFAULT_SERVER,
  loadSession,
  normalizeServer,
  saveSession,
} from './config.mjs'

const execFileAsync = promisify(execFile)
const colorEnabled =
  process.stdout.isTTY &&
  !process.env.NO_COLOR &&
  process.env.TERM !== 'dumb'

function paint(code, value) {
  return colorEnabled ? `\x1b[${code}m${value}\x1b[0m` : String(value)
}

const ui = {
  accent: (value) => paint('38;5;110', value),
  danger: (value) => paint('38;5;203', value),
  dim: (value) => paint('38;5;242', value),
  green: (value) => paint('38;5;114', value),
  heading: (value) => paint('1;38;5;252', value),
  muted: (value) => paint('38;5;245', value),
  warn: (value) => paint('38;5;215', value),
}

function print(value = '') {
  process.stdout.write(`${value}\n`)
}

function printError(value) {
  process.stderr.write(`${ui.danger(value)}\n`)
}

function formatBytes(bytes) {
  const value = Number(bytes) || 0
  if (value < 1024) return `${value} B`
  const units = ['KB', 'MB', 'GB', 'TB']
  let amount = value
  let unit = 'B'
  for (const candidate of units) {
    amount /= 1024
    unit = candidate
    if (amount < 1024) break
  }
  return `${amount >= 10 ? amount.toFixed(1) : amount.toFixed(2)} ${unit}`
}

function formatDate(value) {
  if (!value) return '-'
  return new Intl.DateTimeFormat('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
  }).format(new Date(value))
}

export function tokenizeCommand(value) {
  return (
    value.match(/(?:[^\s"']+|"[^"]*"|'[^']*')+/g)?.map((part) =>
      part.replace(/^['"]|['"]$/g, ''),
    ) || []
  )
}

function printBanner(user, server) {
  print()
  print(ui.heading('FILE HOME TERMINAL'))
  print(
    ui.dim(
      `${user.displayName || user.username} <${user.username}>  ·  ${server}`,
    ),
  )
  print(ui.dim('输入 help 查看命令，exit 退出。'))
  print()
}

function printHelp() {
  const rows = [
    ['login', '重新登录 File Home'],
    ['logout', '清除本地登录会话'],
    ['whoami', '显示当前账户'],
    ['status', '显示服务器、账户和远端目录'],
    ['pwd', '显示当前远端路径'],
    ['ls [path]', '列出目录内容'],
    ['cd <path>', '切换远端目录，支持 ..、/、~'],
    ['mkdir <name>', '创建文件夹'],
    ['cat <file>', '以只读方式查看文本或代码'],
    ['preview <file>', '获取短期预览链接并交给系统浏览器'],
    ['download <item> [path]', '下载文件或文件夹 ZIP'],
    ['upload <local-path>', '上传本地文件到当前目录'],
    ['mv <source> <target>', '移动或重命名'],
    ['cp <source> <target>', '复制项目'],
    ['delfile <file>', '永久删除文件'],
    ['deldir <folder>', '确认后递归删除文件夹'],
    ['sharefile <file>', '创建或更新文件分享'],
    ['sharedir <folder>', '创建或更新文件夹分享'],
    ['modshare <item>', '修改已有分享'],
    ['ushare <item>', '取消已有分享'],
    ['shares', '列出全部分享'],
    ['find <keyword>', '搜索当前账户文件'],
    ['recent', '最近使用'],
    ['starred', '已收藏'],
    ['clear', '清空终端显示'],
    ['exit / quit', '退出'],
  ]
  print(ui.heading('COMMANDS'))
  for (const [command, description] of rows) {
    print(`${ui.accent(command.padEnd(25))} ${description}`)
  }
}

async function promptHidden(question) {
  if (!process.stdin.isTTY) {
    throw new Error('非交互环境请使用 FILEHOME_PASSWORD')
  }
  return new Promise((resolve, reject) => {
    const stdin = process.stdin
    const wasRaw = stdin.isRaw
    let value = ''
    process.stdout.write(question)
    stdin.setRawMode(true)
    stdin.resume()
    stdin.setEncoding('utf8')

    const finish = (error) => {
      stdin.removeListener('data', onData)
      stdin.setRawMode(Boolean(wasRaw))
      process.stdout.write('\n')
      if (error) reject(error)
      else resolve(value)
    }

    const onData = (chunk) => {
      for (const char of chunk) {
        if (char === '\u0003') {
          finish(new Error('登录已取消'))
          return
        }
        if (char === '\r' || char === '\n') {
          finish()
          return
        }
        if (char === '\u007f' || char === '\b') {
          value = value.slice(0, -1)
          continue
        }
        if (!char.includes('\u0000')) value += char
      }
    }
    stdin.on('data', onData)
  })
}

async function loginPrompt(server, defaultAccount = '') {
  const loginReader = readline.createInterface({
    input: process.stdin,
    output: process.stdout,
    terminal: Boolean(process.stdin.isTTY),
  })
  try {
    const account =
      (await loginReader.question(
        `File Home 账户${defaultAccount ? ` [${defaultAccount}]` : ''}: `,
      )).trim() ||
      defaultAccount ||
      process.env.FILEHOME_ACCOUNT
    if (!account) throw new Error('账户不能为空')
    loginReader.close()
    const password =
      process.env.FILEHOME_PASSWORD ||
      (await promptHidden('密码: '))
    if (!password) throw new Error('密码不能为空')
    const api = new FileHomeApi(server)
    const response = await api.login(account, password)
    const user = response.user
    print(ui.green(`已验证：${user.displayName || user.username}`))
    return {
      server,
      accessToken: response.accessToken,
      user,
    }
  } finally {
    loginReader.close()
  }
}

async function authenticate(options) {
  let session = options.forceLogin ? null : await loadSession()
  if (
    session &&
    options.serverExplicit &&
    normalizeServer(session.server) !== normalizeServer(options.server)
  ) {
    session = null
  }
  if (session) {
    const api = new FileHomeApi(session.server, session.accessToken)
    try {
      const user = await api.me()
      return {
        ...session,
        user,
      }
    } catch {
      if (!options.noSave) await clearSession()
    }
  }

  if (!process.stdin.isTTY) {
    const account = process.env.FILEHOME_ACCOUNT
    const password = process.env.FILEHOME_PASSWORD
    if (!account || !password) {
      throw new Error(
        '非交互环境请设置 FILEHOME_ACCOUNT、FILEHOME_PASSWORD，或先保存登录会话',
      )
    }
    const api = new FileHomeApi(options.server)
    const response = await api.login(account, password)
    return {
      server: options.server,
      accessToken: response.accessToken,
      user: response.user,
    }
  }

  session = await loginPrompt(options.server, process.env.FILEHOME_ACCOUNT)
  if (!options.noSave) {
    const target = await saveSession(session)
    print(ui.dim(`登录会话已保存到 ${target}`))
  }
  return session
}

function findEntry(entries, value) {
  const normalized = value.toLowerCase()
  return (
    entries.find((entry) => entry.name.toLowerCase() === normalized) ||
    entries.find((entry) => entry.name.toLowerCase().startsWith(normalized))
  )
}

function currentPath(stack) {
  return stack.length ? `~/${stack.map((item) => item.name).join('/')}` : '~'
}

function sortEntries(entries) {
  return [...entries].sort((left, right) => {
    if (left.kind !== right.kind) return left.kind === 'folder' ? -1 : 1
    return left.name.localeCompare(right.name, 'zh-CN')
  })
}

function printEntries(entries) {
  if (!entries.length) {
    print(ui.dim('（目录为空）'))
    return
  }
  for (const entry of sortEntries(entries)) {
    const type = entry.kind === 'folder' ? '[DIR] ' : '[FILE]'
    const shared = entry.shared ? ui.warn(' [SHARED]') : ''
    const size =
      entry.kind === 'folder' ? '' : `  ${formatBytes(entry.size).padStart(9)}`
    print(
      `${ui.accent(type)} ${entry.name}${shared}${ui.dim(
        `${size}  ${formatDate(entry.modifiedAt)}`,
      )}`,
    )
  }
}

async function writeResponseToFile(body, destination) {
  if (!body) throw new Error('服务器没有返回文件内容')
  await fsPromises.mkdir(path.dirname(destination), { recursive: true })
  await pipeline(Readable.fromWeb(body), createWriteStream(destination))
}

async function openExternal(url) {
  if (process.platform === 'win32') {
    await execFileAsync('cmd.exe', ['/c', 'start', '', url])
    return
  }
  const command = process.platform === 'darwin' ? 'open' : 'xdg-open'
  await execFileAsync(command, [url])
}

export function parseArguments(argv) {
  const options = {
    server: normalizeServer(process.env.FILEHOME_API_URL || DEFAULT_SERVER),
    serverExplicit: false,
    noSave: false,
    forceLogin: false,
    command: '',
  }
  for (let index = 0; index < argv.length; index += 1) {
    const argument = argv[index]
    if (argument === '--no-save') {
      options.noSave = true
    } else if (argument === '--login') {
      options.forceLogin = true
    } else if (argument === '--server') {
      options.server = normalizeServer(argv[++index])
      options.serverExplicit = true
    } else if (argument.startsWith('--server=')) {
      options.server = normalizeServer(argument.slice('--server='.length))
      options.serverExplicit = true
    } else if (argument === '--command' || argument === '-c') {
      options.command = argv[++index] || ''
    } else if (argument === '--help' || argument === '-h') {
      options.help = true
    } else {
      throw new Error(`未知参数：${argument}`)
    }
  }
  return options
}

export async function runTerminal(options) {
  const session = await authenticate(options)
  const api = new FileHomeApi(session.server, session.accessToken)
  let user = session.user
  let stack = []
  let entries = []

  const refresh = async () => {
    const parentId = stack.length ? stack[stack.length - 1].id : undefined
    const data = await api.list({ parentId, sort: 'name-asc', limit: 200 })
    entries = data.items || []
    return data
  }

  const walkFolders = async (targetPath, base = stack) => {
    if (!targetPath || targetPath === '.') return [...base]
    let working =
      targetPath.startsWith('/') || targetPath.startsWith('~') ? [] : [...base]
    for (const segment of targetPath.split('/').filter(Boolean)) {
      if (segment === '.') continue
      if (segment === '..') {
        working.pop()
        continue
      }
      const parentId = working.length ? working[working.length - 1].id : undefined
      const data = await api.list({
        parentId,
        sort: 'name-asc',
        limit: 200,
      })
      const folder = findEntry(data.items || [], segment)
      if (!folder || folder.kind !== 'folder') {
        throw new Error(`文件夹不存在：${segment}`)
      }
      working.push(folder)
    }
    return working
  }

  const resolveRemoteItem = async (value, expectedKind = '') => {
    if (!value) throw new Error('请指定文件或文件夹')
    const segments = value.split('/').filter(Boolean)
    const name = segments.pop()
    const parentPath = segments.join('/')
    const parentStack = await walkFolders(
      value.startsWith('/') ? `/${parentPath}` : parentPath,
    )
    const parentId = parentStack.length
      ? parentStack[parentStack.length - 1].id
      : undefined
    const data = await api.list({
      parentId,
      sort: 'name-asc',
      limit: 200,
    })
    const item = findEntry(data.items || [], name)
    if (!item) throw new Error(`项目不存在：${value}`)
    if (expectedKind && item.kind !== expectedKind) {
      throw new Error(
        expectedKind === 'folder'
          ? `${value} 不是文件夹`
          : `${value} 不是文件`,
      )
    }
    return { item, parentId, parentStack }
  }

  const activeShareFor = async (item) => {
    const shares = await api.listShares()
    return shares.find(
      (share) => share.status === 'active' && share.fileId === item.id,
    )
  }

  const ask = async (reader, question) => reader.question(question)

  const confirm = async (reader, question) => {
    const answer = (await ask(reader, `${question} [y/N] `))
      .trim()
      .toLowerCase()
    return ['y', 'yes', '是', '确认'].includes(answer)
  }

  const createOrUpdateShare = async (reader, item, forceModify = false) => {
    const existing = await activeShareFor(item)
    if (forceModify && !existing) {
      throw new Error('该项目当前没有有效分享')
    }
    const currentDays = existing?.expiresAt
      ? Math.max(
          1,
          Math.min(
            30,
            Math.ceil(
              (new Date(existing.expiresAt).getTime() - Date.now()) /
                86_400_000,
            ),
          ),
        )
      : 7
    const daysInput = (
      await ask(
        reader,
        `分享时间 1-30 天${existing ? ` [${currentDays}]` : ''}: `,
      )
    ).trim()
    const days = daysInput ? Number.parseInt(daysInput, 10) : currentDays
    if (!Number.isInteger(days) || days < 1 || days > 30) {
      throw new Error('分享时间必须是 1-30 天')
    }
    const currentCode = existing?.accessCode || ''
    const codeInput = (
      await ask(
        reader,
        `分享码 4-12 位${currentCode ? ` [${currentCode}]` : ''}，输入 none 可清除: `,
      )
    ).trim()
    const accessCode =
      !codeInput && currentCode
        ? currentCode
        : codeInput.toLowerCase() === 'none'
          ? ''
          : codeInput.toUpperCase().replace(/[^A-Z0-9]/g, '')
    if (existing?.accessCodeConfigured && !currentCode && !codeInput) {
      throw new Error('当前分享码无法回显，请输入新分享码或输入 none 清除')
    }
    if (accessCode && !/^[A-Z0-9]{4,12}$/.test(accessCode)) {
      throw new Error('分享码必须是 4-12 位字母或数字')
    }
    const share = await api.createShare({
      rootEntryId: item.id,
      days,
      accessCode,
      permission: existing?.permission || 'download',
      recursive: item.kind === 'folder',
    })
    print(ui.green(existing ? '分享设置已更新' : '分享已创建'))
    print(`链接: ${ui.accent(share.url)}`)
    print(`分享码: ${share.accessCode || '无'}`)
    await refresh()
  }

  const execute = async (line, reader) => {
    const [rawCommand, ...args] = tokenizeCommand(line)
    if (!rawCommand) return true
    const command = rawCommand.toLowerCase()
    const argument = args.join(' ')

    if (['exit', 'quit'].includes(command)) return false
    if (command === 'help') {
      printHelp()
      return true
    }
    if (command === 'clear') {
      process.stdout.write('\x1b[2J\x1b[H')
      return true
    }
    if (command === 'pwd') {
      print(currentPath(stack))
      return true
    }
    if (command === 'status') {
      print(`服务器: ${session.server}`)
      print(`账户:   ${user.displayName || user.username} <${user.username}>`)
      print(`目录:   ${currentPath(stack)}`)
      return true
    }
    if (command === 'whoami') {
      print(`${user.displayName || user.username} <${user.username}>`)
      return true
    }
    if (command === 'logout') {
      await clearSession()
      print(ui.warn('本地登录会话已清除，请重新启动 fhterm 登录。'))
      return false
    }
    if (command === 'login') {
      print(ui.warn('请退出后执行 fhterm --login 重新验证。'))
      return true
    }
    if (command === 'ls') {
      let data
      if (argument) {
        const targetStack = await walkFolders(argument)
        data = await api.list({
          parentId: targetStack.length
            ? targetStack[targetStack.length - 1].id
            : undefined,
          sort: 'name-asc',
          limit: 200,
        })
      } else {
        data = await refresh()
      }
      printEntries(data.items || [])
      return true
    }
    if (command === 'cd') {
      if (!argument || argument === '~' || argument === '/') {
        stack = []
      } else {
        stack = await walkFolders(argument)
      }
      await refresh()
      print(ui.dim(currentPath(stack)))
      return true
    }
    if (command === 'mkdir') {
      if (!argument) throw new Error('用法：mkdir <name>')
      const parentId = stack.length ? stack[stack.length - 1].id : undefined
      const folder = await api.createFolder(parentId, argument)
      print(ui.green(`已创建文件夹：${folder.name}`))
      await refresh()
      return true
    }
    if (command === 'cat') {
      const { item } = await resolveRemoteItem(argument, 'file')
      const preview = await api.textPreview(item.id)
      print(ui.dim(`# ${item.name} · 只读预览${preview.truncated ? ' · 已截断' : ''}`))
      const lines = preview.text.replace(/\r\n?/g, '\n').split('\n')
      const width = String(lines.length).length
      lines.slice(0, 10_000).forEach((text, index) => {
        print(`${ui.dim(String(index + 1).padStart(width))}  ${text}`)
      })
      if (lines.length > 10_000) print(ui.warn('输出超过 10000 行，已截断。'))
      return true
    }
    if (command === 'preview') {
      const { item } = await resolveRemoteItem(argument, 'file')
      const access = await api.previewAccess(item.id)
      const url = api.mediaUrl(access)
      print(url)
      try {
        await openExternal(url)
        print(ui.green('已交给系统默认应用打开。'))
      } catch {
        print(ui.warn('无法自动打开，请复制上面的短期链接。'))
      }
      return true
    }
    if (command === 'download') {
      if (!args[0]) throw new Error('用法：download <item> [local-path]')
      const { item } = await resolveRemoteItem(args[0])
      let destination =
        args.slice(1).join(' ') ||
        (item.kind === 'folder' ? `${item.name}.zip` : item.name)
      destination = path.resolve(destination)
      if (fs.existsSync(destination) && fs.statSync(destination).isDirectory()) {
        destination = path.join(
          destination,
          item.kind === 'folder' ? `${item.name}.zip` : item.name,
        )
      }
      if (fs.existsSync(destination) && !(await confirm(reader, '覆盖本地文件？'))) {
        print('已取消。')
        return true
      }
      print(ui.dim(`正在下载 ${item.name}...`))
      await writeResponseToFile(
        await api.download(item.id, item.kind === 'folder'),
        destination,
      )
      print(ui.green(`已保存：${destination}`))
      return true
    }
    if (command === 'upload') {
      if (!argument) throw new Error('用法：upload <local-path>')
      const localPath = path.resolve(argument)
      const stat = await fsPromises.stat(localPath).catch(() => null)
      if (!stat?.isFile()) throw new Error(`本地文件不存在：${localPath}`)
      print(ui.dim(`正在上传 ${localPath}...`))
      const parentId = stack.length ? stack[stack.length - 1].id : undefined
      let lastProgress = -10
      const uploaded = await api.upload(localPath, parentId, (progress) => {
        if (progress === 100 || progress >= lastProgress + 10) {
          lastProgress = progress
          print(ui.dim(`\u4e0a\u4f20\u8fdb\u5ea6\uff1a${progress}%`))
        }
      })
      print(ui.green(`上传完成：${uploaded.name}`))
      await refresh()
      return true
    }
    if (command === 'mv' || command === 'cp') {
      if (args.length < 2) throw new Error(`用法：${command} <source> <target>`)
      const source = await resolveRemoteItem(args[0])
      const targetText = args.slice(1).join(' ')
      const targetFolder = findEntry(entries, targetText)
      if (targetFolder?.kind === 'folder') {
        if (command === 'mv') await api.move([source.item.id], targetFolder.id)
        else await api.copy([source.item.id], targetFolder.id)
      } else {
        const segments = targetText.split('/').filter(Boolean)
        const destinationName = segments.pop()
        const parentPath = segments.join('/')
        const parentStack = await walkFolders(
          targetText.startsWith('/') ? `/${parentPath}` : parentPath,
        )
        const parentId = parentStack.length
          ? parentStack[parentStack.length - 1].id
          : undefined
        if (command === 'mv' && !segments.length) {
          await api.rename(source.item.id, destinationName)
        } else {
          if (command === 'mv') await api.move([source.item.id], parentId)
          else
            await api.copy([source.item.id], parentId, [
              { id: source.item.id, name: destinationName },
            ])
          if (
            command === 'mv' &&
            destinationName &&
            destinationName !== source.item.name
          ) {
            await api.rename(source.item.id, destinationName)
          }
        }
      }
      await refresh()
      print(ui.green(command === 'mv' ? '已移动。' : '已复制。'))
      return true
    }
    if (command === 'delfile') {
      const { item } = await resolveRemoteItem(argument, 'file')
      await api.deleteFile(item.id)
      await refresh()
      print(ui.warn(`已永久删除：${item.name}`))
      return true
    }
    if (command === 'deldir') {
      const { item } = await resolveRemoteItem(argument, 'folder')
      print(ui.warn(`文件夹「${item.name}」内的文件也会被清除。`))
      if (!(await confirm(reader, '你确定要递归删除吗？'))) {
        print('已取消。')
        return true
      }
      await api.deleteFolder(item.id)
      await refresh()
      print(ui.warn(`已永久删除文件夹：${item.name}`))
      return true
    }
    if (command === 'sharefile' || command === 'sharedir') {
      const expected = command === 'sharefile' ? 'file' : 'folder'
      const { item } = await resolveRemoteItem(argument, expected)
      await createOrUpdateShare(reader, item)
      return true
    }
    if (command === 'modshare') {
      const { item } = await resolveRemoteItem(argument)
      await createOrUpdateShare(reader, item, true)
      return true
    }
    if (command === 'ushare') {
      const { item } = await resolveRemoteItem(argument)
      const share = await activeShareFor(item)
      if (!share) throw new Error('该项目当前没有有效分享')
      if (!(await confirm(reader, `取消 ${item.name} 的分享？`))) {
        print('已取消。')
        return true
      }
      await api.revokeShare(share.id)
      await refresh()
      print(ui.warn('分享链接已失效。'))
      return true
    }
    if (command === 'shares') {
      const shares = await api.listShares()
      if (!shares.length) {
        print(ui.dim('暂无分享。'))
      } else {
        for (const share of shares) {
          print(
            `${ui.accent(share.status.padEnd(7))} ${share.fileName}  ` +
              `${ui.dim(`码:${share.accessCode || '无'}  ${share.url}`)}`,
          )
        }
      }
      return true
    }
    if (command === 'find') {
      if (!argument) throw new Error('用法：find <keyword>')
      const data = await api.list({
        query: argument,
        sort: 'name-asc',
        limit: 200,
      })
      printEntries(data.items || [])
      return true
    }
    if (command === 'recent') {
      printEntries(await api.recent())
      return true
    }
    if (command === 'starred') {
      printEntries(await api.starred())
      return true
    }
    throw new Error(`未知命令：${rawCommand}。输入 help 查看帮助。`)
  }

  if (options.command) {
    const fakeReader = {
      question: async () => '',
    }
    await execute(options.command, fakeReader)
    return
  }

  await refresh()
  printBanner(user, session.server)
  const reader = readline.createInterface({
    input: process.stdin,
    output: process.stdout,
    terminal: Boolean(process.stdin.isTTY),
    historySize: 200,
  })
  let running = true
  reader.on('SIGINT', () => {
    print()
    reader.close()
    running = false
  })
  try {
    while (running) {
      let line
      try {
        line = await reader.question(
          `${ui.accent(`filehome:${currentPath(stack)}`)} ${ui.green('$')} `,
        )
      } catch {
        break
      }
      try {
        running = await execute(line, reader)
      } catch (error) {
        if (error instanceof AuthenticationError) {
          printError(`${error.message} 请退出后重新登录。`)
        } else {
          printError(error.message || String(error))
        }
      }
    }
  } finally {
    reader.close()
  }
}
