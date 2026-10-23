# jqpdf — Java bindings for qpdf

[![Maven Central](https://img.shields.io/maven-central/v/dev.machineminds/jqpdf.svg)](https://central.sonatype.com/artifact/dev.machineminds/jqpdf)
[![CI](https://github.com/n-kostadinov/jqpdf/actions/workflows/ci.yml/badge.svg)](https://github.com/n-kostadinov/jqpdf/actions/workflows/ci.yml)
[![Javadoc](https://img.shields.io/badge/javadoc-reference-blue.svg)](https://javadoc.io/doc/dev.machineminds/jqpdf)
[![License: MIT](https://img.shields.io/badge/license-MIT-blue.svg)](LICENSE)

All the kudos for what this library can do belong to the authors of
[qpdf](https://github.com/qpdf/qpdf), the C++ toolkit for inspecting and
transforming PDF files that does the actual work — **jqpdf** is a Java
library providing JNI bindings around it. It ships the native qpdf
libraries for the supported platforms inside a single self-contained jar —
no native setup, no runtime dependencies.

Status: the core job API and all functional areas of the qpdf CLI are
implemented and tested; see [API reference](#api-reference) below.

## What & why

jqpdf lets a Java 11+ application merge, split, encrypt and decrypt,
linearize (fast web view), and repair or inspect PDF files by driving the
same battle-tested engine as the `qpdf` command line — in-process, with
the native libraries bundled in the jar. Unlike general-purpose PDF
libraries such as PDFBox or iText, qpdf is *content-preserving*: it
transforms the PDF's structure without reinterpreting page content, which
makes it a robust choice for repairing damaged files and for lossless
split, merge, and re-save pipelines. If you currently shell out to the
`qpdf` CLI from Java, jqpdf replaces the process spawn with a typed
fluent API and captured results; if you use PDFBox only to shuffle pages
around, jqpdf is a lighter dependency with no transitive jars at all.

## Coordinates

`dev.machineminds:jqpdf` — releases are published to
[Maven Central](https://central.sonatype.com/artifact/dev.machineminds/jqpdf)
and attached to the [GitHub releases](https://github.com/n-kostadinov/jqpdf/releases).

```xml
<dependency>
  <groupId>dev.machineminds</groupId>
  <artifactId>jqpdf</artifactId>
  <version>1.0.0</version>
</dependency>
```

## Run/use requirements

- Java 11+ at runtime
- A supported platform — Windows x64, Linux x86-64, macOS x86-64/ARM64;
  see the [platform matrix](docs/guides/README.md#supported-platforms) for
  build/test status and the macOS encryption caveat
- Write access to the default temp directory (`java.io.tmpdir`): the
  bundled native stack is extracted there once per JVM, and in-memory I/O
  spills there — both configurable (`jqpdf.native.dir`,
  `JQPDF_TMPDIR`/`jqpdf.tmpdir`/`job.tempDir(Path)`)
- Nothing else: no system qpdf installation, no runtime dependencies

## Build requirements

Building from source additionally needs:

- JDK 25 (toolchain; `maven.compiler.release=11` keeps `src/main` on
  Java 11 APIs)
- Bash, and the JNI-shim compiler: on Windows WSL with
  `gcc-mingw-w64-x86-64`, on Linux native `gcc`
- `gh` CLI for `./scripts/fetch-qpdf-binaries`

## Building and testing

```bash
# 1. fetch the pinned qpdf release binaries
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

`run()` returns a [`JobResult`](docs/guides/job-result.md) with the qpdf
exit code (0 success, 2 errors, 3 warnings) and all captured output; it never
throws for qpdf-level outcomes — use `orThrow()` to turn errors into a
`QpdfJobException`. Builder misuse (missing input, invalid values) throws
`QpdfUsageException` before the native call.

## Native loading

The native stack (qpdf's companion libraries → libqpdf → the jqpdf JNI shim)
is extracted to a temp directory and loaded **once per JVM** on first use;
the platform is detected automatically. Per-platform status:

| Directory | Platform |
|---|---|
| `windows-x86_64` | Windows x64 (MSVC build) — shim compiled via WSL mingw |
| `windows-x86`, `windows-aarch64` | Windows x86 / ARM64 — qpdf binaries only, no shim yet |
| `linux-x86_64` | Linux x86-64 — shim compiled via WSL/native gcc |
| `linux-aarch64` | Linux ARM64 — qpdf binaries only, no shim yet |
| `macos-aarch64` | macOS ARM64 — shim built, test suite executed on arm64 runners |
| `macos-x86_64` | macOS x86-64 — shim cross-compiled, statically validated (no Intel runners) |

Build/test status and the macOS encryption caveat live in the
[platform matrix](docs/guides/README.md#supported-platforms); platforms
without a shim are pruned from release jars by
`scripts/release-native-bundle`.

The system property `jqpdf.native.dir` loads an unpacked platform directory
directly (development aid).

## Concurrency

Each `QpdfJob` drives its own native qpdf job. Use separate jobs from
separate threads freely (a single job object must not be shared between
threads while running). qpdf's process-global parser limits are pinned per
job so one job's `global(...)` settings cannot leak into another's run —
see [global limits and testing](docs/guides/global-limits.md).

## API reference

Hosted javadoc: <https://javadoc.io/doc/dev.machineminds/jqpdf>.

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
| In-memory I/O (§9.1) | `QpdfJob` input/output overloads | [memory-io.md](docs/guides/memory-io.md) |
| Results and output capture | `JobResult` | [job-result.md](docs/guides/job-result.md) |
| JSON passthrough (§7) | `runFromJson(jobJson)`, `jobJsonFile(file)` | [json.md](docs/guides/json.md) |
| Testing without natives | `QpdfNative` adapter injection | [mocking.md](docs/guides/mocking.md) |

## License

This project is licensed under the MIT License — see [LICENSE](LICENSE).
It bundles and wraps qpdf, which is licensed under the Apache License 2.0 —
see [NOTICE](NOTICE) and `Jqpdf.qpdfCopyright()`.
