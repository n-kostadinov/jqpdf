# jqpdf guides

User documentation per functional area of the qpdf CLI (cross-referenced
with the jqpdf specification in [`docs/qpdf-spec.md`](../qpdf-spec.md)).

## Supported platforms

The jar bundles prebuilt qpdf binaries plus a JNI shim per platform and
extracts them to a temp directory at runtime — no system qpdf
installation is needed. Platform binaries are **not** committed; CI
fetches the pinned qpdf release and compiles the shims
(`scripts/fetch-qpdf-binaries`, `scripts/build-native-shim`).

| Platform | Shipped | Built and tested |
|---|---|---|
| `linux-x86_64` | yes | compiled and test suite executed on an Ubuntu runner |
| `windows-x86_64` | yes | cross-compiled with mingw; test suite executed on a Windows runner |
| `macos-aarch64` | yes | compiled and test suite executed on an arm64 macOS runner — **encryption unsupported**: the official qpdf macOS binaries cannot load the OpenSSL legacy provider, so encrypting/decrypting PDFs fails with `qpdf: unable to load openssl legacy provider` until qpdf's release builds change |
| `macos-x86_64` | yes | cross-compiled (`-arch x86_64`) and statically validated (link + `lipo`); **not execution-tested** — Intel macOS runners no longer exist; same encryption limitation as `macos-aarch64` (same qpdf binaries) |
| `linux-aarch64` | no | qpdf binaries are fetchable but no JNI shim build exists yet |
| `windows-x86` | no | same — 32-bit Windows unsupported until a shim build is added |
| `windows-aarch64` | no | same — ARM Windows unsupported until a shim build is added |

Unsupported platform directories are pruned from release jars by
`scripts/release-native-bundle`, which also fails the build if any
shipped platform is missing its shim.

| Guide | Area |
|---|---|
| [general-options.md](general-options.md) | Basic invocation, passwords, warnings, determinism |
| [advanced-control.md](advanced-control.md) | Hex keys, password modes, damaged/hybrid files |
| [transformation.md](transformation.md) | Linearization, QDF, streams, compression, versions |
| [page-selection.md](page-selection.md) | Merging, collating, splitting, rotation, labels |
| [encryption.md](encryption.md) | Encrypt/decrypt, restrictions, copy-encryption |
| [overlay-underlay.md](overlay-underlay.md) | Stamps, watermarks |
| [attachments.md](attachments.md) | Embedded files: add/copy/remove/list/extract |
| [inspection.md](inspection.md) | Checks, page counts, object and stream dumps |
| [json.md](json.md) | qpdf JSON output and input |
| [global-limits.md](global-limits.md) | Parser limits, testing options |
| [info-options.md](info-options.md) | Version/copyright and why help options are CLI-only |
| [memory-io.md](memory-io.md) | byte[]/stream/string inputs and outputs, spill files, temp directory |

Start with the [README](../../README.md) for building, loading, and
concurrency notes. The complete feature-by-feature audit of qpdf CLI
coverage — Java API method, argv test, and real-PDF test for every
option — lives in [docs/coverage.md](../coverage.md).
