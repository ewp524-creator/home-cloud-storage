# File Home fhterm

`fhterm` is an authenticated command-line client for File Home. It runs directly
inside `cmd.exe`, PowerShell, Git Bash, and Linux/macOS shells and manages remote
files through the existing File Home REST API.

There is no browser xterm component and no PTY web service in this module.

## Start

Windows cmd or PowerShell:

```bat
.\windows\fhterm.cmd
```

Git Bash or Linux:

```bash
cd /path/to/repository/trueterminal
chmod +x linux/fhterm
./linux/fhterm
```

Optional global command:

```bash
pnpm link --global
fhterm
```

## Authentication

The first run asks for an existing File Home account and password. The password
input is hidden and is never stored.

After successful verification, the JWT session is stored at:

```text
~/.filehome-terminal/session.json
```

Use `logout` to remove it, or `fhterm --login` to force reauthentication.

## Commands

```text
ls [path]                 list a remote directory
cd <path>                 enter a folder; supports .., /, ~
pwd                       show the remote path
mkdir <name>              create a folder
cat <file>                read text or source code
preview <file>            open a short-lived signed preview URL
download <item> [path]    download a file or folder ZIP
upload <local-path>       upload a local file
mv <source> <target>      move or rename
cp <source> <target>      copy
delfile <file>            permanently delete a file
deldir <folder>           confirm then recursively delete a folder
sharefile <file>          create or update a file share
sharedir <folder>         create or update a folder share
modshare <item>           modify an existing share
ushare <item>             revoke an existing share
shares                    list shares
find <keyword>            search files
recent / starred          list recent or starred files
whoami / status           show authentication state
logout / exit             end the client
```

Files of 8 MiB or more automatically use resumable MinIO multipart upload with
four concurrent parts and per-part SHA-256 verification.

## Non-interactive Usage

```powershell
$env:FILEHOME_ACCOUNT="your-account"
$env:FILEHOME_PASSWORD="your-password"
.\windows\fhterm.cmd --no-save --command "ls"
```

Use `--server http://host:8080` or `FILEHOME_API_URL` when File Home is not
running at `http://127.0.0.1:8080`.
