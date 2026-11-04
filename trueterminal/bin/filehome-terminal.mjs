#!/usr/bin/env node

import process from 'node:process'
import { parseArguments, runTerminal } from '../cli/terminal.mjs'

function printUsage() {
  process.stdout.write(
    [
      'File Home Terminal',
      '',
      'Usage:',
      '  fhterm [--server URL] [--login] [--no-save]',
      '  fhterm --server URL --command "ls"',
      '',
      'Environment:',
      '  FILEHOME_API_URL    API base URL',
      '  FILEHOME_ACCOUNT    account for non-interactive login',
      '  FILEHOME_PASSWORD   password for non-interactive login',
      '',
    ].join('\n'),
  )
}

async function main() {
  const options = parseArguments(process.argv.slice(2))
  if (options.help) {
    printUsage()
    return
  }
  await runTerminal(options)
}

main().catch((error) => {
  process.stderr.write(`${error.message || error}\n`)
  process.exitCode = 1
})
