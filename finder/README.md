# Finder development

The `finder` module implements the scanning CLI. See the
[repository README](../README.md) for prerequisites and the
[distributed user guide](src/main/dist/README.md) for commands and exclusions.

## Build

From the repository root:

```powershell
.\gradlew.bat :finder:build
```

Java compilation targets the Java 11 toolchain. Production dependencies consist
of [`common`](../common/README.md); the test suite is configured for JUnit Jupiter
and AssertJ, with no test sources currently.

## Implementation

- Entry point: [`acme.duplicates.DuplicateFinder`](src/main/java/acme/duplicates/DuplicateFinder.java).
- Walks regular files without following symbolic links, applying directory and
  file exclusions before grouping by size, first-4-KB hash, and full SHA-256.
- Hashing uses a fixed worker pool. Read failures are excluded from results.
- `common.PlanIO` writes the plan; the first path in each group becomes `KEEP`.
  The CLI does not delete files or guarantee a preferred keeper ordering.

Windows packaging uses the local jpackage plugin, with `DuplicateFinder` as the
application name and the entry point configured directly. Its `docFiles` include
`src/main/dist/README.md` and the root license. Update the distributed guide when
changing options, defaults, or the hardcoded exclusion lists.

## Package and install

```powershell
.\gradlew.bat :finder:jpackageImage
.\gradlew.bat :finder:jpackageInstall
```

The image is written to `finder/build/jpackage/image/DuplicateFinder/`, including
the executable, runtime, README, and license. Local installation syncs this image
to `<user.home>/apps/duplicate-finder` by default, or
`<jpackageDevInstallDirectory>/duplicate-finder` when that Gradle property is set.
`:finder:jpackageZip` writes `DuplicateFinder-<version>.zip` under
`finder/build/distributions` (version defaults to `1.0.0`).
