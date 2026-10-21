# Information options

The qpdf CLI's help/information options (`--version`, `--copyright`,
`--help`, `--job-json-help`, `--json-help`, `--show-crypto`, `--zopfli`,
`--completion-*`) are **not available through the job API**: qpdf's argument
parser terminates the process with `exit(0)` for these options, which would
kill the hosting JVM. They are CLI-only by design.

jqpdf provides Java equivalents where they are meaningful:

- `Jqpdf.qpdfVersion()` — the string the CLI's `--version` prints
  (`qpdf version 12.4.2`), derived from the pinned Maven property
  `qpdf.version`, so it always matches the bundled binaries.
- `Jqpdf.qpdfCopyright()` — the copyright notice and Apache License 2.0
  text that the CLI's `--copyright` prints.

There is no equivalent for `--show-crypto`, `--job-json-help`, `--json-help`,
or `--zopfli`: the qpdf job C API exposes none of them, and they depend on
build-time configuration of the native library. `--help`'s option reference
is what this documentation set provides.

For everything operational — checks, page counts, encryption state, object
dumps — use the [inspection](inspection.md) options instead; they are fully
supported through the job API.
