# jqpdf

**jqpdf** is a Java library providing JNI bindings around
[qpdf](https://github.com/qpdf/qpdf), a C++ toolkit for inspecting and
transforming PDF files. It ships the native qpdf libraries for seven
platforms inside a single self-contained jar — no native setup, no runtime
dependencies.

Status: early development (0.1.0-SNAPSHOT). The core job API and all
functional areas of the qpdf CLI are implemented and tested; see
[API reference](#api-reference) below.

## Coordinates

`dev.machineminds:jqpdf:0.1.0-SNAPSHOT` — not published to any repository yet.

## Requirements

- Java 11+ at runtime (built with JDK 25, `maven.compiler.release=11`)
- Building the JNI shim additionally needs: Bash, and on Windows WSL with
  `gcc-mingw-w64-x86-64` (or natively, on Linux, `gcc`)

## Building and testing

```bash
# 1. fetch the pinned qpdf release binaries (gh CLI required)
./scripts/fetch-qpdf-binaries        # → src/main/resources/.../native/<os>-<arch>/

# 2. build (compiles the JNI shim via WSL/native gcc automatically)
mvn clean package                    # → target/jqpdf-<version>.jar

# 3. test (runs against the real binaries of the current platform)
mvn test
```

The qpdf version is pinned as the Maven property `qpdf.version` (currently
12.4.2); the fetch script, the JNI shim build, and `Jqpdf.QPDF_VERSION` all
derive from it. Tests skip with a message when the binaries have not been
fetched or the shim has not been built.

## Quick start

```java
// copy with structural repair
JobResult result = Jqpdf.createJob()
        .input("in.pdf")
        .output("out.pdf")
        .run()
        .orThrow();

// merge pages of two files
Jqpdf.createJob()
        .input("a.pdf").output("merged.pdf")
        .pages(p -> p.file(".").range("1-3")
                      .file("b.pdf").range("z-1"))
        .run().orThrow();

// inspect without writing
JobResult npages = Jqpdf.createJob()
        .input("in.pdf")
        .showNpages()
        .run().orThrow();     // page count is in npages.infoText()
```

`run()` returns a [`JobResult`](#results-and-output-capture) with the qpdf
exit code (0 success, 2 errors, 3 warnings) and all captured output; it never
throws for qpdf-level outcomes — use `orThrow()` to turn errors into a
`QpdfJobException`. Builder misuse (missing input, invalid values) throws
`QpdfUsageException` before the native call.

## Native loading

The native stack (qpdf's companion libraries → libqpdf → the jqpdf JNI shim)
is extracted to a temp directory and loaded **once per JVM** on first use;
the platform is detected automatically. Bundled platforms:

| Directory | Platform |
|---|---|
| `windows-x86_64` | Windows x64 (MSVC build) — shim compiled via WSL mingw |
| `windows-x86`, `windows-aarch64` | Windows x86 / ARM64 (qpdf binaries; shim via CI later) |
| `linux-x86_64` | Linux x86-64 — shim compiled via WSL/native gcc |
| `linux-aarch64` | Linux ARM64 (qpdf binaries; shim via CI later) |
| `macos-x86_64`, `macos-aarch64` | macOS (qpdf binaries; shim via CI later) |

The system property `jqpdf.native.dir` loads an unpacked platform directory
directly (development aid).

## Concurrency

Each `QpdfJob` drives its own native qpdf job. Use separate jobs from
separate threads freely (a single job object must not be shared between
threads while running). qpdf's process-global parser limits are pinned per
job so one job's `global(...)` settings cannot leak into another's run —
see [global limits and testing](docs/guides/global-limits.md).

## Mocking

The public API is interface-based: [`QpdfJob`](#qpdfjob) is an interface and
`Jqpdf.createJob(QpdfNative)` accepts any
[`QpdfNative`](src/main/java/dev/machineminds/jqpdf/QpdfNative.java)
adapter, so job logic can be tested against a fake without native libraries
(the project's own argv-synthesis tests do exactly this).

## API reference

One interface per qpdf functional area, all mixed into
[`QpdfJob`](src/main/java/dev/machineminds/jqpdf/QpdfJob.java); every option
method returns the job for chaining. Guides with examples:

| Area (spec §) | Options interface | Guide |
|---|---|---|
| Basic invocation, general (§5.1, §5.5) | `GeneralOptions` | [general-options.md](docs/guides/general-options.md) |
| Advanced control (§5.6) | `AdvancedControlOptions` | [advanced-control.md](docs/guides/advanced-control.md) |
| PDF transformation (§5.7) | `TransformationOptions` | [transformation.md](docs/guides/transformation.md) |
| PDF modification (§5.8) | `ModificationOptions` | [page-selection.md](docs/guides/page-selection.md) |
| Encryption (§5.10, §10.2) | `EncryptionOptions` | [encryption.md](docs/guides/encryption.md) |
| Overlay/underlay (§5.9) | `OverlayOptions` | [overlay-underlay.md](docs/guides/overlay-underlay.md) |
| Attachments (§5.11) | `AttachmentOptions` | [attachments.md](docs/guides/attachments.md) |
| Inspection (§5.12) | `InspectionOptions` | [inspection.md](docs/guides/inspection.md) |
| JSON (§5.13, §6) | `JsonOptions` | [json.md](docs/guides/json.md) |
| Global limits, testing (§5.14, §5.15) | `GlobalOptions` | [global-limits.md](docs/guides/global-limits.md) |
| CLI info options (§5.4) | — (static methods on `Jqpdf`) | [info-options.md](docs/guides/info-options.md) |

### Results and output capture

[`JobResult`](src/main/java/dev/machineminds/jqpdf/JobResult.java) exposes
`exitCode()`, `success()`, `hasWarnings()`, `infoText()` (qpdf's stdout),
`warnText()`, `errorText()`, and `savedData()` (binary output of
`showAttachment`/`rawStreamData`/`filteredStreamData`). Output is captured
per job in native buffers — the library never writes to the host process's
console.

### QPDFJob JSON passthrough

`runFromJson(jobJson)` runs a job described by a
[QPDFJob JSON](docs/guides/json.md) document directly, bypassing the typed
builder.

## License

This project is licensed under the MIT License — see [LICENSE](LICENSE).
It bundles and wraps qpdf, which is licensed under the Apache License 2.0 —
see [NOTICE](NOTICE) and `Jqpdf.qpdfCopyright()`.
