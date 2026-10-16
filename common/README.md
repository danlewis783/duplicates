# Common development

Shared Java library for [`finder`](../finder/README.md) and
[`deleter`](../deleter/README.md). It has no executable entry point or separate
end-user installation. See the [repository README](../README.md) for prerequisites.

## Build

From the repository root:

```powershell
.\gradlew.bat :common:build
```

Compilation uses the Java 11 toolchain. There are no external production
library dependencies or test sources currently.

## Implementation

Sources are in [`acme.duplicates.common`](src/main/java/acme/duplicates/common):

- `DuplicateGroup`: mutable plan group model.
- `PlanIO`: plain-text plan writer and reader; owns group headers and exact
  `KEEP`/`DEL` prefix parsing. Invalid or incomplete groups may be dropped.
- `FileHashing`: partial and full SHA-256 hashing.
- `FileSizes`, `Formatting`: file sizes and display formatting.
- `PathUtils`: wildcard-to-regex conversion for scan exclusions.

Changes to plan syntax affect both tools. Keep the
[finder user guide](../finder/src/main/dist/README.md) and
[deleter user guide](../deleter/src/main/dist/README.md) consistent with shared
behavior, especially exact spacing, hash verification, and wildcard semantics.
