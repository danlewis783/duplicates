# Duplicates

Java tools for finding duplicate files and deleting selected copies through a
reviewable text plan. The finder groups regular files by size, first-4-KB SHA-256,
and full SHA-256; the deleter reads the plan and verifies deletion candidates.

## Modules

| Subproject | Development guide | Purpose |
| --- | --- | --- |
| `finder` | [README](finder/README.md) | Scan CLI; writes duplicate plans. |
| `deleter` | [README](deleter/README.md) | Plan execution CLI, dry runs, and logs. |
| `common` | [README](common/README.md) | Shared plan model, I/O, hashing, and utilities. |

The executable distributions include separate end-user guides:
[DuplicateFinder](finder/src/main/dist/README.md) and
[DuplicateDeleter](deleter/src/main/dist/README.md).

## Build and development

Run commands from the repository root in PowerShell. Prerequisites:

- A JDK compatible with the Gradle 9.8.1 wrapper; verified with JDK 25.
- A Java 11 JDK available to Gradle for the modules' compilation toolchain.
- Local Gradle plugin `com.example.placeholder.jpackage:0.1.2`, published to
  Maven Local from `C:/dev/jpackage-gradle-plugin`; its source is maintained
  separately. It is required when configuring either executable module.
- Network access for initial wrapper/dependency downloads, or populated caches.

```powershell
.\gradlew.bat build
```

`build` compiles all modules and runs configured checks. There are currently no
Java test sources. Module guides describe focused builds and implementation
entrypoints. Both CLIs depend on `common`; there are no external production
library dependencies.

## Packaging

Build runtime-bundled Windows images and ZIP distributions:

```powershell
.\gradlew.bat :finder:jpackageZip :deleter:jpackageZip
```

ZIPs are written to each module's `build/distributions` directory. For image
builds and local installation, see the module guides.

The executable modules use the local jpackage plugin to package Windows
applications and their runtimes. Each distribution includes `LICENSE` and its
own end-user `README.md` from the module's `src/main/dist` directory. Keep those
usage guides in sync with CLI behavior when changing arguments or plan handling.

## License

[GNU General Public License, version 3](LICENSE).
