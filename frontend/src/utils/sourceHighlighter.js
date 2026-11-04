const COMMON_KEYWORDS = new Set([
  'as', 'async', 'await', 'break', 'case', 'catch', 'class', 'const',
  'continue', 'default', 'delete', 'do', 'else', 'enum', 'export',
  'extends', 'finally', 'for', 'from', 'function', 'if', 'implements',
  'import', 'in', 'instanceof', 'interface', 'let', 'new', 'of',
  'package', 'private', 'protected', 'public', 'return', 'static',
  'super', 'switch', 'this', 'throw', 'try', 'type', 'typeof', 'var',
  'void', 'while', 'with', 'yield',
])

const LANGUAGE_KEYWORDS = {
  c: new Set([
    'auto', 'break', 'case', 'char', 'const', 'continue', 'default',
    'do', 'double', 'else', 'enum', 'extern', 'float', 'for', 'goto',
    'if', 'inline', 'int', 'long', 'register', 'return', 'short',
    'signed', 'sizeof', 'static', 'struct', 'switch', 'typedef',
    'union', 'unsigned', 'void', 'volatile', 'while',
  ]),
  java: new Set([
    'abstract', 'assert', 'boolean', 'break', 'byte', 'case', 'catch',
    'char', 'class', 'const', 'continue', 'default', 'do', 'double',
    'else', 'enum', 'extends', 'final', 'finally', 'float', 'for',
    'if', 'implements', 'import', 'instanceof', 'int', 'interface',
    'long', 'native', 'new', 'package', 'private', 'protected',
    'public', 'record', 'return', 'sealed', 'short', 'static',
    'strictfp', 'super', 'switch', 'synchronized', 'this', 'throw',
    'throws', 'transient', 'try', 'var', 'void', 'volatile', 'while',
  ]),
  python: new Set([
    'and', 'as', 'assert', 'async', 'await', 'break', 'case', 'class',
    'continue', 'def', 'del', 'elif', 'else', 'except', 'finally',
    'for', 'from', 'global', 'if', 'import', 'in', 'is', 'lambda',
    'match', 'nonlocal', 'not', 'or', 'pass', 'raise', 'return', 'try',
    'while', 'with', 'yield',
  ]),
  sql: new Set([
    'add', 'alter', 'and', 'as', 'asc', 'between', 'by', 'case',
    'column', 'constraint', 'create', 'database', 'default', 'delete',
    'desc', 'distinct', 'drop', 'else', 'end', 'exists', 'foreign',
    'from', 'group', 'having', 'in', 'index', 'inner', 'insert',
    'into', 'is', 'join', 'key', 'left', 'like', 'limit', 'not',
    'null', 'offset', 'on', 'or', 'order', 'outer', 'primary',
    'references', 'right', 'select', 'set', 'table', 'then', 'union',
    'unique', 'update', 'values', 'when', 'where', 'with',
  ]),
  shell: new Set([
    'case', 'do', 'done', 'elif', 'else', 'esac', 'fi', 'for',
    'function', 'if', 'in', 'select', 'then', 'time', 'until', 'while',
  ]),
  go: new Set([
    'break', 'case', 'chan', 'const', 'continue', 'default', 'defer',
    'else', 'fallthrough', 'for', 'func', 'go', 'goto', 'if',
    'import', 'interface', 'map', 'package', 'range', 'return',
    'select', 'struct', 'switch', 'type', 'var',
  ]),
  rust: new Set([
    'as', 'async', 'await', 'break', 'const', 'continue', 'crate',
    'dyn', 'else', 'enum', 'extern', 'false', 'fn', 'for', 'if',
    'impl', 'in', 'let', 'loop', 'match', 'mod', 'move', 'mut',
    'pub', 'ref', 'return', 'self', 'Self', 'static', 'struct',
    'super', 'trait', 'true', 'type', 'unsafe', 'use', 'where', 'while',
  ]),
}

const LANGUAGE_BY_EXTENSION = {
  c: 'c',
  h: 'c',
  cc: 'c',
  cpp: 'c',
  cxx: 'c',
  hpp: 'c',
  java: 'java',
  kt: 'java',
  kts: 'java',
  scala: 'java',
  groovy: 'java',
  py: 'python',
  pyw: 'python',
  sql: 'sql',
  sh: 'shell',
  bash: 'shell',
  zsh: 'shell',
  fish: 'shell',
  ps1: 'shell',
  go: 'go',
  rs: 'rust',
}

const HASH_COMMENT_LANGUAGES = new Set(['python', 'shell'])
const DASH_COMMENT_LANGUAGES = new Set(['sql'])
const BLOCK_COMMENT_LANGUAGES = new Set(['c', 'java', 'javascript'])

function escapeHtml(value) {
  return String(value)
    .replaceAll('&', '&amp;')
    .replaceAll('<', '&lt;')
    .replaceAll('>', '&gt;')
    .replaceAll('"', '&quot;')
    .replaceAll("'", '&#39;')
}

function languageFor(extension) {
  const normalized = String(extension || '').toLowerCase()
  if (LANGUAGE_BY_EXTENSION[normalized]) return LANGUAGE_BY_EXTENSION[normalized]
  if (['ts', 'tsx', 'js', 'jsx', 'mjs', 'cjs', 'vue'].includes(normalized)) {
    return 'javascript'
  }
  if (['json', 'jsonc', 'json5'].includes(normalized)) return 'json'
  if (['html', 'htm', 'xhtml', 'xml', 'svg'].includes(normalized)) return 'markup'
  if (['css', 'scss', 'sass', 'less'].includes(normalized)) return 'css'
  return 'generic'
}

function keywordsFor(language) {
  if (language === 'javascript') return COMMON_KEYWORDS
  if (language === 'json') return new Set(['true', 'false', 'null'])
  if (language === 'css') return new Set([
    'important', 'inherit', 'initial', 'none', 'unset', 'var',
  ])
  return LANGUAGE_KEYWORDS[language] || COMMON_KEYWORDS
}

function commentPattern(language) {
  const patterns = []
  if (HASH_COMMENT_LANGUAGES.has(language)) patterns.push('#.*$')
  if (DASH_COMMENT_LANGUAGES.has(language)) patterns.push('--.*$')
  if (language === 'javascript' || BLOCK_COMMENT_LANGUAGES.has(language)) {
    patterns.push('//.*$', '/\\*.*?\\*/')
  }
  if (language === 'markup') patterns.push('<!--.*?-->')
  return patterns.join('|')
}

function highlightCodeLine(line, language) {
  const keywordSet = keywordsFor(language)
  const keywordPattern = [...keywordSet]
    .sort((left, right) => right.length - left.length)
    .map((keyword) => keyword.replace(/[.*+?^${}()|[\]\\]/g, '\\$&'))
    .join('|')
  const comment = commentPattern(language)
  const patterns = [
    "(\"(?:\\\\.|[^\"\\\\])*\"|'(?:\\\\.|[^'\\\\])*'|`(?:\\\\.|[^`\\\\])*`)",
    comment ? `(${comment})` : null,
    keywordPattern ? `\\b(${keywordPattern})\\b` : null,
    '\\b(true|false|null|undefined|None|nil)\\b',
    '\\b(0x[0-9a-fA-F]+|\\d+(?:\\.\\d+)?)\\b',
    '([A-Za-z_$][\\w$]*(?=\\s*\\())',
    '\\b([A-Z][A-Za-z0-9_]*)\\b',
    '([{}[\\]();,.])',
  ].filter(Boolean)
  const tokenPattern = new RegExp(patterns.join('|'), 'g')
  let result = ''
  let cursor = 0

  for (const match of line.matchAll(tokenPattern)) {
    result += escapeHtml(line.slice(cursor, match.index))
    const value = match[0]
    if (/^["'`]/.test(value)) {
      result += `<span class="tok-string">${escapeHtml(value)}</span>`
    } else if (
      value.startsWith('//') ||
      value.startsWith('#') ||
      value.startsWith('--') ||
      value.startsWith('/*') ||
      value.startsWith('<!--')
    ) {
      result += `<span class="tok-comment">${escapeHtml(value)}</span>`
    } else if (/^(true|false|null|undefined|None|nil)$/.test(value)) {
      result += `<span class="tok-constant">${escapeHtml(value)}</span>`
    } else if (/^(0x|\d)/.test(value)) {
      result += `<span class="tok-number">${escapeHtml(value)}</span>`
    } else if (keywordSet.has(value)) {
      result += `<span class="tok-keyword">${escapeHtml(value)}</span>`
    } else if (/^[A-Z]/.test(value)) {
      result += `<span class="tok-type">${escapeHtml(value)}</span>`
    } else if (/^[A-Za-z_$]/.test(value)) {
      result += `<span class="tok-function">${escapeHtml(value)}</span>`
    } else {
      result += `<span class="tok-punctuation">${escapeHtml(value)}</span>`
    }
    cursor = match.index + value.length
  }
  return result + escapeHtml(line.slice(cursor))
}

function highlightTextLine(line) {
  if (/^\s*#{1,6}\s/.test(line)) {
    return `<span class="tok-heading">${escapeHtml(line)}</span>`
  }
  const tokenPattern = /(https?:\/\/[^\s]+|[\w.+-]+@[\w.-]+\.[A-Za-z]{2,}|`[^`]+`|\b\d+(?:[.:/-]\d+)+\b)/g
  let result = ''
  let cursor = 0
  for (const match of line.matchAll(tokenPattern)) {
    result += escapeHtml(line.slice(cursor, match.index))
    const value = match[0]
    const className = value.startsWith('http') || value.includes('@')
      ? 'tok-link'
      : value.startsWith('`')
        ? 'tok-inline-code'
        : 'tok-number'
    result += `<span class="${className}">${escapeHtml(value)}</span>`
    cursor = match.index + value.length
  }
  return result + escapeHtml(line.slice(cursor))
}

export function getLanguageLabel(extension) {
  const language = languageFor(extension)
  return {
    c: 'C / C++',
    java: 'Java / Kotlin',
    javascript: 'JavaScript / TypeScript',
    json: 'JSON',
    python: 'Python',
    sql: 'SQL',
    shell: 'Shell',
    go: 'Go',
    rust: 'Rust',
    markup: 'HTML / XML',
    css: 'CSS',
    generic: 'Plain source',
  }[language] || 'Plain source'
}

export function highlightSource(source, extension, previewKind = 'code') {
  const language = languageFor(extension)
  const normalized = String(source || '')
    .replaceAll('\r\n', '\n')
    .replaceAll('\r', '\n')
  const lines = normalized.split('\n')
  const visibleLines = lines.slice(0, 6000)
  return {
    language,
    languageLabel: getLanguageLabel(extension),
    highlighted: visibleLines.map((line) =>
      previewKind === 'text'
        ? highlightTextLine(line)
        : highlightCodeLine(line, language),
    ),
    truncatedByLines: lines.length > visibleLines.length,
    lineCount: lines.length,
  }
}
