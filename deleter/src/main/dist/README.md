# DuplicateDeleter

Read a reviewed DuplicateFinder plan and remove selected duplicate copies.
**Deletion is permanent, starts immediately, and has no confirmation prompt or
Recycle Bin recovery.** Keep a backup and stop other programs from changing the
files during scanning, review, and deletion.

## Preview and apply

DuplicateFinder is a separately installed companion that creates the plan.
Open PowerShell in the directory containing `DuplicateDeleter.exe`. Keep the
complete application directory together; it includes the required Java runtime, so no separate Java installation is needed.

Review the plan in a text editor, then preview it:

```powershell
notepad 'C:\Plans\foo.plan'
.\DuplicateDeleter.exe 'C:\Plans\foo.plan' --dry-run
```

Inspect the preview and its log, including all skips. Once satisfied, apply it:

```powershell
.\DuplicateDeleter.exe 'C:\Plans\foo.plan'
```

## Arguments

```text
DuplicateDeleter.exe <planfile> [--dry-run]
```

`<planfile>` is required. Relative paths resolve from the current directory.
`--dry-run` verifies candidates and logs proposed actions without deleting files.
It **must immediately follow the plan path**, exactly as shown. Unknown options
or a misplaced flag are ignored and can result in real deletion.

## Review and edit the plan

Generated plans contain absolute paths. Each duplicate group has a header, one
selected `KEEP`, and one or more `DEL` entries:

```text
GROUP 1  size=1234  hash=<full SHA-256 from generated plan>  copies=2  wasted=1234
KEEP  C:\Foo\original.txt
DEL   C:\Foo\copy.txt
```

The hash placeholder is illustrative: preserve the generated hash verbatim.
Preserve each `GROUP` header; it starts a group and supplies its verification
hash. `size` and `wasted` are bytes; `copies` and `wasted` are informational.
The initial keeper is the first file in the group; review that choice yourself.

- Use `KEEP` plus **two spaces**, or `DEL` plus **three spaces**, before the
  unquoted path. The path starts at column 7. Do not indent these lines.
- To change the keeper, swap prefixes with the exact spacing above and leave
  exactly one `KEEP` per group. Remove or comment individual `DEL` lines to
  retain additional copies.
- To skip a group, remove or comment its entire block, including all entries.
  Commenting only its header can attach entries to the preceding group.
- Comments start with `#` in the first column; blank lines are ignored. Groups
  without a recognized `KEEP` or any `DEL` entries are dropped by the reader.

## Verification and logs

Before processing a group, the tool checks that its `KEEP` path exists. It does
**not** rehash the keeper or verify that it still contains the scanned content.
Each `DEL` file is rehashed and must match the header's full SHA-256. Missing
files, hash errors/mismatches, missing keepers, and deletion errors are logged
as skips. Dry runs perform the same verification.

Checks and deletion are separate operations; concurrent changes can invalidate
them. A dry run does not reserve or lock files. Keep the files unchanged and
review edited plans carefully: the tool trusts your paths and group assignments.

Both modes write a log alongside the plan. `foo.plan` produces `foo.delete.log`;
other names have `.delete.log` appended (only a lowercase `.plan` suffix is
replaced). Every run **overwrites** the log, so save the preview log separately
if needed. Console and log summaries report deleted or would-delete files,
bytes, and skipped files; completion does not mean every entry was processed.

## License

GNU General Public License, version 3; see the included `LICENSE` file.
