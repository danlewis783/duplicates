# DuplicateFinder

Find duplicate files recursively and write a text plan for review. Files are
compared by size, first-4-KB SHA-256, then full SHA-256. Scanning does not delete
files. DuplicateDeleter is the companion tool that applies a reviewed plan.

## Quick start

Open PowerShell in the directory containing `DuplicateFinder.exe`. Keep the
complete application directory together; it includes the required Java runtime, so no separate Java installation is needed.
Quote paths containing spaces, and choose a plan location in an existing writable directory.

```powershell
.\DuplicateFinder.exe 'C:\Foo' -o 'C:\Plans\foo.plan'
notepad 'C:\Plans\foo.plan'
```

An existing plan at the output path is **overwritten**. Review every group and
choose which copy to keep. The first file found in each group is initially marked
`KEEP`; it is not selected by a preferred filename rule.

Then open PowerShell in the directory containing the separately installed
`DuplicateDeleter.exe` and preview the same plan:

```powershell
.\DuplicateDeleter.exe 'C:\Plans\foo.plan' --dry-run
```

Read DuplicateDeleter's installed README for plan editing, deletion checks, and
applying the plan. Keep a backup and prevent files from changing between scanning,
review, and deletion. A scan or dry run does not lock files.

## Arguments

```text
DuplicateFinder.exe <directory> [threads] [-o <planfile>] [-skip <dir_pattern>] [-skipfile <file_pattern>] ...
```

| Argument | Meaning / default |
| --- | --- |
| `<directory>` | Required directory to scan recursively. |
| `[threads]` | Positive hashing worker count; defaults to available processors. |
| `-o <planfile>` | Output plan; defaults to `duplicates.plan` in the current directory. |
| `-skip <dir_pattern>` | Add a directory exclusion; repeat as needed. |
| `-skipfile <file_pattern>` | Add a file exclusion; repeat as needed. |

```powershell
.\DuplicateFinder.exe 'C:\Foo' 8 -o '.\foo.plan' -skip '*archive*' -skipfile '*.bak'
```

Relative paths resolve from PowerShell's current directory.

## Exclusions and scan coverage

Wildcard exclusions are case-insensitive, match the entire name or absolute path,
and support `*` (any characters) and `?` (one character). Quote patterns in the
shell. Other regex metacharacters are not escaped, so keep patterns simple.
Directory matches prune the whole subtree, including the scan root if it matches.
Custom patterns add to the defaults; defaults cannot be disabled.

The following exact directory names are always excluded. These exact-name checks
are case-sensitive:

```text
AppData       Local         LocalLow      Roaming
Windows       System32      SysWOW64      WinSxS        MUICache
.git          .svn          .hg           node_modules .gradle
.m2           target        build         dist         .idea
.vs           __pycache__   .mypy_cache   venv         .venv
Temp          tmp           $Recycle.Bin
System Volume Information
ArcGIS
Saved Games
```

Default directory wildcard patterns:

```text
*Facebook_files*
*GIS*
RNL ESRI*
*Class SeptOct 2019
*ProfCertCourses*
```

Default file wildcard patterns:

```text
*.class
*.log
thumbs.db
.DS_Store
NTUSER*
autorun.inf
*.ico
OpalViewerLite.exe
```

Only regular files are scanned; symbolic links are not followed. Inaccessible
files and read failures are reported and skipped, so a completed scan can still
cover only part of the tree. Inspect console messages before relying on the plan.

## License

GNU General Public License, version 3; see the included `LICENSE` file.
