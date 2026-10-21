# jqpdf guides

User documentation per functional area of the qpdf CLI (cross-referenced
with the jqpdf specification in [`docs/qpdf-spec.md`](../qpdf-spec.md)).

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
