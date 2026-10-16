# Deleter development

The `deleter` module implements plan execution. See the
[repository README](../README.md) for prerequisites and the
[distributed user guide](src/main/dist/README.md) for usage and plan editing.

## Build

From the repository root:

```powershell
.\gradlew.bat :deleter:build
```

Java compilation targets the Java 11 toolchain. Production dependencies consist
of [`common`](../common/README.md); the test suite is configured for JUnit Jupiter
and AssertJ, with no test sources currently.

## Implementation

- Entry point: [`acme.duplicates.DuplicateDeleter`](src/main/java/acme/duplicates/DuplicateDeleter.java).
- `common.PlanIO` parses groups. Each group requires a keeper and deletion entries.
- Keeper existence is checked once per group; its content is not rehashed.
  Each deletion candidate must match the plan header's SHA-256 before deletion.
- Dry runs perform the same verification and log proposed actions. The flag is
  recognized only immediately after the plan path; unknown options are ignored.
- Each invocation overwrites the log alongside the plan. File verification and
  deletion are separate operations, so concurrent changes remain a concern.

Windows packaging uses the local jpackage plugin, with `DuplicateDeleter` as the
application name and the entry point configured directly. Its `docFiles` include
`src/main/dist/README.md` and the root license. Keep that guide synchronized with
argument parsing, verification, log naming, and the shared plan reader.

## Package and install

```powershell
.\gradlew.bat :deleter:jpackageImage
.\gradlew.bat :deleter:jpackageInstall
```

The image is written to `deleter/build/jpackage/image/DuplicateDeleter/`, including
the executable, runtime, README, and license. Local installation syncs this image
to `<user.home>/apps/duplicate-deleter` by default, or
`<jpackageDevInstallDirectory>/duplicate-deleter` when that Gradle property is set.
`:deleter:jpackageZip` writes `DuplicateDeleter-<version>.zip` under
`deleter/build/distributions` (version defaults to `1.0.0`).
