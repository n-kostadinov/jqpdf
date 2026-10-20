# qpdf CLI Specification

**Target:** the `qpdf` command-line tool, version **12.4.2** (Apache-2.0; the CLI itself
Artistic-2.0). This document specifies its complete functionality so that a JNI Java
wrapper (`jqpdf`) can be designed and implemented from this spec alone.

**Sources** (qpdf 12.4.2 checkout): `manual/cli.rst`, `manual/qpdf.1`,
`manual/qpdf-job.rst`, `manual/json.rst`, `job.yml` (the canonical option registry that
generates the CLI parser, the `QPDFJob` config API, the manual option blocks, the man
page, the shell completions, and the job-JSON schema), `include/qpdf/QPDFJob.hh`,
`include/qpdf/qpdfjob-c.h`, `include/qpdf/qpdflogger-c.h`, `include/qpdf/Constants.h`,
`libqpdf/QPDFJob*.cc`, `libqpdf/qpdf/QPDFArgParser.hh`.

**Coverage:** all 140 documented options of the `qpdf` CLI, the invocation model, exit
codes and diagnostics, cross-cutting grammars (page ranges, page labels, PDF dates,
encryption parameters, unicode passwords), the qpdf JSON output format, and the QPDFJob
JSON input format. The separate tools `fix-qdf` and `zlib-flate` are out of scope except
for a brief note (§8).

---

## 1. Architecture and the recommended JNI boundary

The `qpdf` executable is a 62-line shim: it constructs a `QPDFJob`, calls
`QPDFJob::initializeFromArgv(argv)` and `run()`, and maps exceptions to exit codes. All
CLI behavior lives in the shared library (`libqpdf`):

- **`QPDFJob`** — the job object: option storage, validation (`checkConfiguration`), and
  execution (`run`) of everything the CLI can do.
- **`QPDFArgParser`** — argument parsing infrastructure (`--help`, sub-option tables,
  shell completion, `@argfile` expansion).

`job.yml` is the single source of truth. From it, `generate_auto_job` produces, in
guaranteed sync: the CLI option parser tables, the fluent `QPDFJob` config classes
(`auto_job_c_*.hh`), the manual/man-page option documentation, shell completions, and the
job-JSON schema. qpdf guarantees these three interfaces are equivalent:

| CLI | Job JSON (`initializeFromJson`) | C++ (`QPDFJob::config()`) |
|---|---|---|
| `--some-option` | `"someOption": ""` | `config()->someOption()` |
| `--some-option=value` | `"someOption": "value"` | `config()->someOption("value")` |
| positional argument | named key (e.g. `"inputFile"`) | named method (e.g. `inputFile(...)`) |

**Recommended JNI boundary.** The C API `qpdfjob-c.h` wraps exactly this job model and is
the natural seam for the JNI wrapper:

- Short interface: `qpdfjob_run_from_argv(argv)`, `qpdfjob_run_from_wide_argv(argv)`,
  `qpdfjob_run_from_json(json)` — each returns the CLI exit code.
- Full interface: `qpdfjob_init()` / `qpdfjob_cleanup(&h)`;
  `qpdfjob_initialize_from_argv / _wide_argv / _from_json`; `qpdfjob_run(h)`;
  two-stage `qpdfjob_create_qpdf(h)` → `qpdf_data` → `qpdfjob_write_qpdf(h, qpdf)`;
  `qpdfjob_set_logger / qpdfjob_get_logger`; `qpdfjob_register_progress_reporter`.

Three implementation strategies for a typed Java API, in order of decreasing coverage
effort:

1. **argv synthesis** — a Java builder serializes options to a `char**` and calls
   `qpdfjob_initialize_from_argv`. Trivially covers 100% of CLI functionality including
   the parser-level info options (§5.4). UTF-8 encoding of argv is required.
2. **job JSON synthesis** — build the camelCase job JSON (§7) and call
   `qpdfjob_initialize_from_json`. Covers everything except the CLI-only info options
   (`--version`, `--copyright`, `--show-crypto`, `--job-json-help`, `--json-help`,
   `--zopfli`, `--help`, `--completion-*`).
3. **C++ fluent config shim** — hand-written C shim exposing `QPDFJob::config()`
   methods. Most type-safe, most maintenance.

All argv/JSON strings are UTF-8. On Windows, wide-argv variants exist for `wmain`-style
callers; a JNI wrapper passes UTF-8 directly from Java strings.

**Configuration errors** throw `QPDFUsage` (C++; derived from `std::runtime_error`) from
`initializeFromArgv`, `initializeFromJson`, config methods, and `checkConfiguration`.
The C API converts these to the `EXIT_ERROR` (2) return value; the handle is invalid
afterwards. Error messages are CLI-centric ("use the --some-option option") and map
1:1 to the corresponding config call / JSON key.

---

## 2. Invocation model

```
qpdf [infile] [options] [outfile]
```

- `infile`, `options`, and `outfile` may appear in any order as long as `infile`
  precedes `outfile`. Later options override earlier ones when contradictory.
- `infile`: a regular file, or `--empty` (a zero-page dummy input). **Standard input is
  not supported for the input file** — it must be seekable. Input may also be qpdf JSON
  when `--json-input` is given.
- `outfile`: a regular file, `-` (standard output; not required to be seekable even for
  linearized output), or `--replace-input`. With `--json` (qpdf ≥ 11) the output file is
  optional and defaults to standard output.
- **Argument files:** a word `@filename` anywhere on the command line reads the file
  line-by-line, each line becoming one argument. Leading/trailing whitespace is
  preserved. `@-` reads arguments from standard input. `@filename` cannot appear inside
  an argument (`--opt=@file` does not work); the option and its parameter must be lines
  in the file.
- **Option grammar:** options start with `--`. A flag with a parameter uses
  `--flag=parameter` (never `--flag parameter` for declared options). Sub-option groups
  (`--encrypt`, `--pages`, `--overlay`, `--underlay`, `--add-attachment`,
  `--copy-attachments-from`, `--set-page-labels`, `--global`) are terminated by a bare
  `--`. Positional words inside those groups are interpreted per the group's grammar.
- **Help options** (§5.4) are valid only as the first and only command-line argument.
- **Input/output requirements:** all non-help options require an input file. If any
  inspection (§5.12) or JSON (§5.13) option is given, an output file must not be given;
  otherwise an output file is required (exception: `--json` defaults to stdout).
- Running with no options produces a functionally identical but structurally
  reorganized file; unreferenced objects are removed.

---

## 3. Exit codes and diagnostics

Exit codes (`Constants.h`, `qpdf_exit_code_e`; `QPDFJob::getExitCode()`):

| Code | Meaning |
|---:|---|
| 0 | no errors or warnings (also warnings-only with `--warning-exit-0`) |
| 1 | never used by qpdf (reserved: shells use it when qpdf cannot be invoked) |
| 2 | errors were found; the file was not processed |
| 3 | warnings without errors (also `--requires-password` with a correct password) |

- Errors win over warnings (both present → 2). `1` is never returned.
- `--is-encrypted`: **0** = encrypted, **2** = not encrypted. Works on
  password-protected files even without the password. Other options are ignored when
  given. Mutually exclusive with `--requires-password`.
- `--requires-password`: **0** = a password other than the supplied one is required,
  **2** = not encrypted, **3** = encrypted and the supplied password (via `--password`)
  is correct. The 0-on-"password needed" choice supports `if qpdf --requires-password f.pdf;`.

**Output streams.** All CLI text output flows through `QPDFLogger` with three text
pipelines — info (default stdout), warn and error (default stderr) — plus a binary
**save** pipeline used for `--show-attachment`, `--raw-stream-data`, and
`--filtered-stream-data` output. A JNI wrapper captures output by installing a private
logger (`qpdfjob_set_logger`) with callback destinations
(`qpdflogger_set_info/warn/error/save` with `qpdf_log_dest_e` =
default/stdout/stderr/discard/custom and a `int (*)(char const*, size_t, void*)`
callback). Never route two streams into the same custom pipeline. The job's message
prefix defaults to `"qpdf"` (`QPDFJob::setMessagePrefix`).

**Warnings.** `QPDFJob::hasWarnings()` reports whether `run()` produced warnings.
`--no-warn` suppresses warning output but still exits 3; adding `--warning-exit-0`
makes warnings completely ignored (exit 0).

**Progress.** `--progress` enables percentage progress during writing (starts only when
writing starts). Programmatically, `registerProgressReporter(fn(int))` /
`qpdfjob_register_progress_reporter(h, fn, data)` deliver callbacks — but only when
progress was also requested through configuration.

---

## 4. Terminology and conventions used below

- **argument** = any command-line word; **option** = argument starting with `--`
  including its parameter; **flag** = the `--name` part; **parameter** = the value part.
- Option entries below show: syntax, the `QPDFJob` config mapping
  (`Config`/`GlobalConfig`/`EncConfig`/… method), defaults, and behavioral contract.
  Job-JSON keys are the camelCase form of the option name unless noted (§7).
- All options map to `auto_job_c_*.hh` generated methods unless marked **CLI-only**
  (handled in `QPDFJob_argv.cc`/`QPDFArgParser` before/independent of job configuration).
- **Repeatable** options may be given multiple times; in job JSON they are arrays.
- Where an option's JSON key is shown below without a value (e.g. `"qdf"`), flag options
  take the empty string as their value (`"qdf": ""`); see §7.

---

## 5. Option reference

The 140 options are grouped by the manual's functional areas. Counts per area:
Basic Invocation 3, Exit Status 1, Shell Completion 2, Help/Information 6, General 9,
Advanced Control 5, PDF Transformation 25, PDF Modification 22, Encryption 16,
Overlay/Underlay 3, Attachments 11, Inspection 16, JSON 9, Global 7, Testing 5.

### 5.1 Basic Invocation (3)

**`--empty`** — `Config::emptyInput()`; JSON `"empty": ""`.
Use in place of `infile` for a zero-page, empty input. Especially useful with
`--pages` as the primary file (document-level data is then taken from other inputs or
dropped).

**`--replace-input`** — `Config::replaceInput()`; JSON `"replaceInput": ""`.
Use in place of `outfile`. Output is written to `{infilename}.~qpdf-temp#` and the input
is overwritten on completion. If there were warnings, the original is preserved as
`{infilename}.~qpdf-orig`; on errors the input file is left untouched.

**`--job-json-file=file`** — `Config::jobJsonFile(file)`; JSON `"jobJsonFile"`.
Read a QPDFJob JSON file (§7) and treat its contents as the equivalent command-line
arguments. Repeatable; can be mixed freely with other options (processed as a *partial*
JSON initialization, then the remaining argv applies).

### 5.2 Exit Status (1)

**`--warning-exit-0`** — `Config::warningExit0()`; JSON `"warningExit0": ""`.
Exit 0 instead of 3 when only warnings occurred. With `--no-warn`, warnings are fully
ignored.

### 5.3 Shell Completion (2) — CLI-only

**`--completion-bash`** / **`--completion-zsh`** — no config method; emitted by
`QPDFArgParser`.
Print a shell command to enable completion (`eval "$(qpdf --completion-bash)"`).
The `QPDF_EXECUTABLE` environment variable overrides the qpdf path embedded in the
emitted command. Not meaningful for a library wrapper; exclude or reimplement in Java.

### 5.4 Help/Information (6) — CLI-only, first-and-only argument

These options print information and return normally (no input file needed). They have
**no** `QPDFJob` config method and **no** job-JSON key; a JNI wrapper re-implements
them in Java or calls the argv interface.

**`--help[={--option|topic}]`** — general help plus topic list; `--help=--opt` for one
option, `--help=topic` for a topic (topics mirror §5's area names, plus `usage`,
`exit-status`, `page-ranges`, `pdf-dates`, etc.).

**`--version`** — prints `qpdf version <libqpdf version>` (the version compiled into
the library) and a pointer to `--copyright`.

**`--copyright`** — prints the full copyright/license text (Apache-2.0; Artistic-2.0
option for pre-7 versions).

**`--show-crypto`** — lists registered crypto providers, one per line, default provider
first (e.g. `native`, plus `gnutls`/`openssl` if compiled in).

**`--job-json-help`** — prints the QPDFJob JSON schema (as a JSON object of
key → description), version `LATEST_JOB_JSON` = 1. Programmatic equivalent:
`QPDFJob::job_json_schema(1)`.

**`--json-help[=version]`** — prints the qpdf JSON **output** schema for `version`
(default/latest = 2; `1` = legacy). Invalid versions are a usage error. Programmatic
equivalent: `QPDFJob::json_out_schema(version)`.

**`--zopfli`** — reports zopfli availability and activation state (see §10.3): if
support is compiled in, prints whether it is active and how the `QPDF_ZOPFLI`
environment variable activates it, then exits 0; if not compiled in, prints an error
and exits 2.

### 5.5 General Options (9)

**`--password=password`** — `Config::password(p)`; JSON `"password"`.
Password for the encrypted **main input file** (not needed for encrypted files with an
empty user password). Interacts with `--password-is-hex-key` and `--password-mode`
(§5.6, §10.5). To read from a file/stdin use `--password-file`.

**`--password-file=filename`** — `Config::passwordFile(f)`; JSON `"passwordFile"`.
First line of the file is the password (leading/trailing spaces preserved; `filename`
may be `-` for stdin — password is echoed, no prompt). Replacement for `--password`.

**`--verbose`** — `Config::verbose()`; JSON `"verbose": ""`.
Additional information about files created, image optimization, operations performed;
extra detail for inspection options. Programmatic hook: `QPDFJob::doIfVerbose(fn)`.

**`--progress`** — `Config::progress()`; JSON `"progress": ""`.
See §3 (progress reporting contract).

**`--no-warn`** — `Config::noWarn()`; JSON `"noWarn": ""`.
Suppress warning output; exit code still 3 (see §3).

**`--deterministic-id`** — `Config::deterministicId()`; JSON `"deterministicId"`.
Generate the document `/ID` from a digest of significant output content instead of
time/filename/randomness — same operation ⇒ same ID (per qpdf version). Incompatible
with creating encrypted files. See idempotency (§10.4).

**`--allow-weak-crypto`** — `Config::allowWeakCrypto()`; JSON `"allowWeakCrypto"`.
Permit writing files encrypted with RC4 (40-bit keys, or 128-bit without AES); qpdf ≥ 11
refuses otherwise. Not checked when *preserving* or *copying* encryption.

**`--keep-files-open=[y|n]`** — `Config::keepFilesOpen(p)`; JSON `"keepFilesOpen"`.
When merging many files: `y` keeps files open, `n` opens/closes on demand. Default is
automatic: keep open unless more than 200 files (`--keep-files-open-threshold`).

**`--keep-files-open-threshold=count`** — `Config::keepFilesOpenThreshold(n)`;
JSON `"keepFilesOpenThreshold"`. Overrides the default threshold of 200.

### 5.6 Advanced Control Options (5)

**`--password-is-hex-key`** — `Config::passwordIsHexKey()`; JSON `"passwordIsHexKey"`.
Interpret `--password` as a hex-encoded **encryption key** for the main input file,
bypassing password→key computation. Expert/forensic option; applies to the main input
only (not `--pages`/overlay files or files being written). Pair with
`--show-encryption-key` to retrieve a file's key.

**`--suppress-password-recovery`** — `Config::suppressPasswordRecovery()`;
JSON `"suppressPasswordRecovery"`. Disable automatic retry of alternative password
encodings when opening encrypted files (§10.5).

**`--password-mode=mode`** — `Config::passwordMode(m)`; choices `auto` | `unicode` |
`bytes` | `hex-bytes`; JSON `"passwordMode"`.
How passwords supplied on the command line are interpreted: `auto` (default) detects
UTF-8 and transcodes per the PDF spec; `unicode` requires valid UTF-8 (error otherwise);
`bytes` uses the literal byte string; `hex-bytes` decodes hex — the only mode that also
affects passwords used for *reading*. Details in §10.5.

**`--suppress-recovery`** — `Config::suppressRecovery()`; JSON `"suppressRecovery"`.
Do not attempt cross-reference table reconstruction on damaged files; fail on the first
error instead of running recovery heuristics.

**`--ignore-xref-streams`** — `Config::ignoreXrefStreams()`; JSON `"ignoreXrefStreams"`.
Ignore cross-reference streams (hybrid files), falling back to xref tables or recovery.
Intended for testing how pre-1.5 viewers would interpret hybrid files.

### 5.7 PDF Transformation (25)

Structural changes that do not alter content.

**`--linearize`** — `Config::linearize()`; JSON `"linearize"`.
Create linearized (web-optimized) output. Disables QDF mode.

**`--encrypt …​ --`** — `Config::encrypt(keylen, user_password, owner_password)` returning
an `EncConfig`; group terminated by `--`. Full grammar and semantics in §10.2 / §5.10.
(argv also accepts the legacy positional form `--encrypt user owner 256 … --`.)

**`--decrypt`** — `Config::decrypt()`; JSON `"decrypt"`.
Write unencrypted output even if the input was encrypted (default is to preserve
encryption). Not intended for bypassing producer restrictions.

**`--remove-restrictions`** — `Config::removeRestrictions()`; JSON `"removeRestrictions"`.
Remove security restrictions associated with digitally signed files; invalidates and
disables digital signatures but leaves their visual appearances. Combine with `--decrypt`
for free editing of previously signed/encrypted files.

**`--copy-encryption=file`** — `Config::copyEncryption(f)`; JSON `"copyEncryption"`.
Copy all encryption parameters (user password, owner password, restrictions, and the
first half of `/ID`) from `file` instead of preserving the input's. Works with only one
of the two passwords known. Password for that file: `--encryption-file-password`.
Weak-crypto checks intentionally do not apply.

**`--encryption-file-password=password`** — `Config::encryptionFilePassword(p)`;
JSON `"encryptionFilePassword"`. Password for the `--copy-encryption` file (distinct
from `--password`, which targets the main input).

**`--qdf`** — `Config::qdf()`; JSON `"qdf"`.
QDF mode: create a file suitable for viewing/editing PDF code in a text editor. Changes
defaults — streams uncompressed (`--compress-streams=n`), content normalized
(`--normalize-content=y`), encryption removed — all overridable. Stream lengths become
indirect objects; objects formatted readably with locator comments (consumed by the
`fix-qdf` companion tool, §8). `--linearize` disables QDF. Often combined with
`--object-streams=disable` for inspection.

**`--no-original-object-ids`** — `Config::noOriginalObjectIds()`; JSON `"noOriginalObjectIds"`.
Omit the original-object-ID comments in QDF output (useful for test comparisons).

**`--compress-streams=[y|n]`** — `Config::compressStreams(p)`; JSON `"compressStreams"`.
Default `y`: compress (with flate) streams that are uncompressed after decoding.
`n` leaves them uncompressed. QDF mode defaults to `n`.

**`--decode-level=parameter`** — `Config::decodeLevel(p)`; JSON `"decodeLevel"`.
Which streams to attempt to decode. Default `generalized`; default `none` when
`--json-output` is given. Values:
- `none` — decode nothing
- `generalized` — `/FlateDecode`, `/LZWDecode`, `/ASCII85Decode`, `/ASCIIHexDecode`
- `specialized` — generalized + non-lossy specialized (`/RunLengthDecode`)
- `all` — specialized + lossy (`/DCTDecode`/JPEG)

Unsupported filters are always left untouched. Streams already flate-compressed are not
uncompressed/recompressed unless `--recompress-flate` is given.

**`--stream-data=parameter`** — `Config::streamData(p)`; JSON `"streamData"`.
Legacy combined control: `compress` (default) = `--compress-streams=y
--decode-level=generalized`; `preserve` = `=n` + `none`; `uncompress` = `=n` +
`generalized`.

**`--recompress-flate`** — `Config::recompressFlate()`; JSON `"recompressFlate"`.
Uncompress and recompress streams already compressed with flate (needed for
`--compression-level` to affect them). Can be much slower.

**`--compression-level=level`** — `Config::compressionLevel(l)`; JSON `"compressionLevel"`.
Flate (zlib deflate) level 1 (fastest, least) … 9 (slowest, most) for newly compressed
streams. No effect on already-flate streams without `--recompress-flate`; ignored when
zopfli is active (§10.3). Omitted → library default.

**`--jpeg-quality=level`** — `Config::jpegQuality(l)`; JSON `"jpegQuality"`.
JPEG quality 0–100 for images written by `--optimize-images` (only effective together
with it). Also causes already-JPEG images to be recompressed (additional, cumulative
loss). Most JPEG libraries default to 75.

**`--normalize-content=[y|n]`** — `Config::normalizeContent(p)`; JSON `"normalizeContent"`.
Normalize newlines in content streams to UNIX style (off by default; forced on by
`--qdf`). Lexical errors during normalization produce a warning that content may be
damaged. Not recommended for production.

**`--object-streams=mode`** — `Config::objectStreams(m)`; JSON `"objectStreams"`.
Choices `preserve` (default) | `disable` | `generate`. `disable` writes all objects as
regular top-level objects (older viewers); `generate` creates object streams and
compresses objects where possible, forcing PDF version ≥ 1.5.

**`--preserve-unreferenced`** — `Config::preserveUnreferenced()`; JSON `"preserveUnreferenced"`.
Keep objects not reachable from the trailer (default: discard). Ignored for linearized
files; causes output objects to be written in original object-ID order. Unrelated to
`--preserve-unreferenced-resources`.

**`--remove-unreferenced-resources=parameter`** — `Config::removeUnreferencedResources(p)`;
JSON `"removeUnreferencedResources"`; choices `auto` (default) | `yes` | `no`.
Strip page-resource entries (images/fonts) not used by the page's contents when
splitting. `auto` runs a quick heuristic first (pattern of shared resource dictionaries)
and only then the slow full analysis; `yes` forces full analysis; `no` disables.

**`--preserve-unreferenced-resources`** — `Config::preserveUnreferencedResources()`;
JSON `"preserveUnreferencedResources"`.
Deprecated synonym for `--remove-unreferenced-resources=no`; use that instead.

**`--newline-before-endstream`** — `Config::newlineBeforeEndstream()`;
JSON `"newlineBeforeEndstream"`. Insert an uncounted newline before `endstream`
(PDF/A preservation requirement; may produce two newlines).

**`--coalesce-contents`** — `Config::coalesceContents()`; JSON `"coalesceContents"`.
Concatenate a page's array of content streams into one stream (helps QDF/normalization
inspection; never required for correctness).

**`--externalize-inline-images`** — `Config::externalizeInlineImages()`;
JSON `"externalizeInlineImages"`. Convert inline images to regular image objects.
Implicitly enabled by `--optimize-images` unless `--keep-inline-images` is given.
Threshold: `--ii-min-bytes`.

**`--ii-min-bytes=size-in-bytes`** — `Config::iiMinBytes(n)`; JSON `"iiMinBytes"`.
Minimum size (default 1,024; `0` = no minimum) for inline images converted by
`--externalize-inline-images`.

**`--min-version=version`** — `Config::minVersion(v)`; JSON `"minVersion"`.
Raise the output PDF version to at least `major.minor[.extension-level]` (e.g. `1.7.8`
= 1.7 ext 8) if the input's version is lower; higher input versions are kept. Only
minimal syntax checking; qpdf does not verify the version is actually required.

**`--force-version=version`** — `Config::forceVersion(v)`; JSON `"forceVersion"`.
Force the exact version header even when content may need newer features. Downgrades
disable dependent features: 256-bit keys below 1.7-ext-8 (deprecated R5 allowed at ext
3–7), AES below 1.6, cleartext metadata and object streams below 1.5, 128-bit keys
below 1.4, all encryption below 1.3. Newer image compression/transparency features are
not eliminated; some functionality may be lost.

### 5.8 PDF Modification (22)

Changes that alter how the document renders.

**`--pages …​ --`** — `Config::pages()` returning a `PagesConfig`; repeatable group
terminated by `--`. JSON: `"pages": [ {…}, … ]`.
Begin page selection for split/merge/collate. Group grammar (qpdf ≥ 11.9):
`--file=filename [--range=page-range] [--password=password]` repeated; legacy
positional form `filename [--password=p] [page-range]` also accepted. `.` refers to the
primary input file (error with `--empty`). If `--range` is omitted, all pages are
selected. Password is needed once per distinct file path. Document-level data comes
from the primary input. See page ranges (§10.1) and limitations (§10.6).
Fluent API: `pageSpec(filename, range, password)` per spec.

**`--file=file`** — `PagesConfig::file(f)` / `UOConfig::file(f)` (also `AttConfig::file`,
`CopyAttConfig::file`, `EncConfig` positional handling).
Source file for the current group inside `--pages` / `--overlay` / `--underlay`.

**`--range=numeric-range`** — `PagesConfig::range(r)`; JSON (inside pages array) `"range"`.
Page range for the most recent `--file` in the group (§10.1).

**`--collate[=n[,m,…​]]`** — `Config::collate()` / `Config::collate(p)`;
JSON `"collate"`. **Appears outside `--pages … --`.** Interleave pages round-robin from
each page spec instead of concatenating. `n` = group size (default 1);
`n,m,k` = take n from the first file, m from the second, … cycling; parameter count must
equal the number of page groups. Exhausted groups are skipped.

**`--split-pages[=n]`** — `Config::splitPages()` / `Config::splitPages(n)`;
JSON `"splitPages"`. Write each group of `n` pages (default 1) to a separate file.
Output naming: `%d` in the outfile is replaced by a zero-padded range starting at 1;
else if the name ends in `.pdf` (case-insensitive), insert `-range` before the
extension; else append `-range`. Ranges are `p` or `first-last`; padding is uniform so
names sort lexically. Each output is a fresh single-page-group document — document-level
features (outlines, threads, etc.) are not preserved.

**`--overlay file [options] --`** — `Config::overlay()` returning `UOConfig`;
repeatable group terminated by `--`. JSON: `"overlay": [ {…} ]`.
Overlay (draw on top) pages from another file onto the final output; processed late, so
it applies after merges etc. Combine freely with `--underlay`; stacking order:
underlays in order given, original page, overlays in order given.
Group options: `--file`/positional file, `--password`, `--to`, `--from`, `--repeat`.

**`--underlay file [options] --`** — `Config::underlay()`; symmetric to `--overlay`
(drawn underneath, possibly obscured). Group options (`--file`, `--password`, `--to`,
`--from`, `--repeat`): see §5.9.

**`--flatten-rotation`** — `Config::flattenRotation()`; JSON `"flattenRotation"`.
Remove each page's `/Rotate` key and bake the rotation into the page contents —
workaround for viewers that mishandle rotation metadata.

**`--flatten-annotations=parameter`** — `Config::flattenAnnotations(p)`;
JSON `"flattenAnnotations"`; choices `all` (everything not invisible/hidden) | `print`
(printable annotations only) | `screen` (exclude non-screen). Push annotations into
page content, freezing placement (useful before n-up/splitting). Annotations whose form
appearance is out of sync with its value are skipped by default (value would be lost);
run `--generate-appearances` first, or use `--remove-acroform` when the AcroForm is
out of sync with widget annotations.

**`--rotate=[+|-]angle[:page-range]`** — `Config::rotate(spec)`; repeatable;
JSON `"rotate": [ … ]`.
`angle` ∈ {0, 90, 180, 270}. `+`/`-` = relative (clockwise/counter-clockwise); bare =
absolute set (rarely what is wanted). Omitted range = all pages. Examples:
`--rotate=+90:2,4,6 --rotate=+180:7-8`, `--rotate=0` (reset to natural orientation).

**`--generate-appearances`** — `Config::generateAppearances()`;
JSON `"generateAppearances"`. Regenerate appearance streams for form fields whose
appearances are stale. Limitations: radio/checkbox use stored appearance states;
non-ASCII text becomes `?`; auto-sized fonts get a fixed size; quadding, rich text,
multi-select and signature fields unsupported.

**`--optimize-images`** — `Config::optimizeImages()`; JSON `"optimizeImages"`.
Recompress non-JPEG images as JPEG (DCT) when smaller and within dimension thresholds;
never resamples. Verbosity reports decisions. Implies `--externalize-inline-images`
unless `--keep-inline-images`. Thresholds: `--oi-min-width/-height/-area`.

**`--oi-min-width=width`** — `Config::oiMinWidth(n)`; JSON `"oiMinWidth"`.
Skip image optimization below width; default 128 px; `0` = no minimum.

**`--oi-min-height=height`** — `Config::oiMinHeight(n)`; JSON `"oiMinHeight"`.
Default 128 px; `0` = no minimum.

**`--oi-min-area=area-in-pixels`** — `Config::oiMinArea(n)`; JSON `"oiMinArea"`.
Default 16,384 px; `0` = no minimum.

**`--keep-inline-images`** — `Config::keepInlineImages()`; JSON `"keepInlineImages"`.
Exclude inline images from `--optimize-images` (and its implicit externalization).

**`--remove-acroform`** — `Config::removeAcroform()`; JSON `"removeAcroform"`.
Remove the interactive form dictionary from the catalog only (field dictionaries and
widget annotations remain). Useful with `--flatten-annotations` for out-of-sync forms;
check output carefully.

**`--remove-info`** — `Config::removeInfo()`; JSON `"removeInfo"`.
Omit all `/Info` entries except `/ModDate`.

**`--remove-metadata`** — `Config::removeMetadata()`; JSON `"removeMetadata"`.
Omit the catalog's `/Metadata` (XMP) stream.

**`--remove-page-labels`** — `Config::removePageLabels()`; JSON `"removePageLabels"`.
Omit `/PageLabels` from the catalog.

**`--remove-structure`** — `Config::removeStructure()`; JSON `"removeStructure"`.
Omit `/StructTreeRoot` and `/MarkInfo` from the catalog.

**`--set-page-labels label-spec …​ --`** — `Config::setPageLabels(vector<string>)`;
repeatable group terminated by `--`. JSON `"setPageLabels": [ … ]`.
Set page labels for the whole document; grammar in §10.7. First spec must start at page
1; specs must be in increasing order; a spec's scheme runs until the next spec or end of
document; `first-page:` (empty type) suppresses numbering.

### 5.9 Overlay and Underlay (3)

Group options valid between `--overlay`/`--underlay` and the terminating `--`
(`UOConfig` methods). Default overlay/underlay behavior: source pages are consumed in
order onto corresponding output pages until either side runs out; extra output pages
are left alone.

**`--to=page-range`** — `UOConfig::to(r)`.
Destination pages in the output that receive the overlay/underlay. Default: all.

**`--from=[page-range]`** — `UOConfig::from(r)`.
Source pages from the overlay/underlay file, consumed in order. Default: all. May be
empty (`--from=`) when only `--repeat` is used.

**`--repeat=page-range`** — `UOConfig::repeat(r)`.
Pages repeated (cycling) after the `--from` pages are exhausted.
Example: `--to=1-5 --from=1-3 --repeat=4` applies source pages 1–3 to output pages 1–3,
then source page 4 to output pages 4–5; `--from= --repeat=1,2` applies footer page 1 to
odd output pages and page 2 to even pages.

(A group-context `--password` and the `--file` option are shared with `--pages`; see
§5.8.)

### 5.10 Encryption (16)

Group options valid between `--encrypt` and `--`. Programmatic form:
`Config::encrypt(keylen, user_password, owner_password)` → `EncConfig` (fluent) or job
JSON `encrypt` object (§7). Full semantics: §10.2.

**`--user-password=user-password`** — `EncConfig` positional/`user-password` before
`--bits`; JSON `encrypt.userPassword`. Set the user password (readers apply
restrictions when it is used). May be empty/omitted (file opens without a password).

**`--owner-password=owner-password`** — JSON `encrypt.ownerPassword`. Set the owner
password (readers allow changing restrictions when it is used). Same value as the user
password, or empty owner with non-empty user (256-bit, needs `--allow-insecure`), are
considered insecure.

**`--bits={40|128|256}`** — selects the encryption sub-table; `keylen` argument of
`encrypt()`. *(The manual synopsis misprints this as `{48|128|256}`; the parser accepts
40/128/256 — `QPDFJob_argv.cc:244`, `job.yml enc_bits`.)* Mandatory in the flag form;
in the legacy positional form it is the third positional.

**`--accessibility=[y|n]`** (128/256 only) — `EncConfig::accessibility`.
Restrict accessibility extraction. Ignored by modern formats/readers; the PDF spec says
conforming readers disregard it. Default `y`.

**`--annotate=[y|n]`** (40: also covers form filling/signing; 128/256) —
`EncConfig::annotate`. Restrict commenting/form-field annotation editing. With
128/256 it also enables field editing unless `--modify-other=n`/`--modify=none`.

**`--assemble=[y|n]`** (128/256 only) — `EncConfig::assemble`.
Restrict document assembly (rotate/reorder pages).

**`--extract=[y|n]`** (all) — `EncConfig::extract`.
Restrict text/graphics extraction (other than accessibility).

**`--form=[y|n]`** (128/256 only) — `EncConfig::form`.
Restrict filling form fields when annotation modification is disabled.

**`--modify-other=[y|n]`** (128/256 only) — `EncConfig::modifyOther`.
Restrict modifications not covered by assemble/annotate/form. Implied `n` by any
restricting `--modify` level. 

**`--modify=modify-opt`** — `EncConfig::modify`. 40-bit: `y|n` (all-or-nothing).
128/256: `all` (default) | `annotate` | `form` | `assembly` | `none`; mapping in §10.2.
Later options override earlier ones when combined.

**`--print=print-opt`** — `EncConfig::print`. 40-bit: `y|n`.
128/256: `full` (default) | `low` | `none`.

**`--cleartext-metadata`** (128/256 only) — `EncConfig::cleartextMetadata()`.
Leave metadata unencrypted; forces PDF ≥ 1.5.

**`--use-aes=[y|n]`** (128 only) — `EncConfig::useAes`. Use AES instead of RC4.
Default `n` (compatibility); `y` forces PDF ≥ 1.6. (RC4 output requires
`--allow-weak-crypto`.)

**`--allow-insecure`** (256 only) — `EncConfig::allowInsecure()`.
Permit empty owner password with a non-empty user password (file opens unrestricted;
testing only).

**`--force-V4`** (128 only) — `EncConfig::forceV4()`. Force `V=R=4` in the encryption
dictionary; forces PDF ≥ 1.5. Testing only — qpdf sets this automatically when needed.

**`--force-R5`** (256 only) — `EncConfig::forceR5()`. Use the deprecated, unsupported
Acrobat-IX-era R=5 algorithm; min version 1.7 ext 3. Compatibility testing only.

### 5.11 Embedded Files / Attachments (11)

**`--add-attachment file [options] --`** — `Config::addAttachment()` → `AttConfig`;
repeatable group terminated by `--`. JSON `"addAttachment": [ {…} ]` (contains the file
plus the sub-options below). Attach (embed) a file.

**`--key=key`** (attachment) — `AttConfig::key`. Key in the embedded-files name tree.
Default: basename of the attached file.

**`--filename=name`** (attachment) — `AttConfig::filename`. Displayed/saved-as file
name. Default: basename of the attached file.

**`--creationdate=date`** (attachment) — `AttConfig::creationdate`. PDF-format date
(§10.8); default = current time.

**`--moddate=date`** (attachment) — `AttConfig::moddate`. PDF-format date; default =
current time.

**`--mimetype=type/subtype`** (attachment) — `AttConfig::mimetype`. e.g.
`application/pdf`, `image/png`. Not auto-detected; stored in the PDF's `/Subtype` field
(which holds the full mime type).

**`--description="text"`** (attachment) — `AttConfig::description`. Descriptive text
shown by some viewers.

**`--replace`** (attachment) — `AttConfig::replace()`. Replace an existing attachment
with the same key; without it, a duplicate key is an error.

**`--copy-attachments-from file [options] --`** — `Config::copyAttachmentsFrom()` →
`CopyAttConfig`; repeatable group terminated by `--`. JSON
`"copyAttachmentsFrom": [ {…} ]`. Copy all attachments from another file (password via
the group-context `--password`).

**`--prefix=prefix`** (copy-attachments) — `CopyAttConfig::prefix`. Prepend to each
copied attachment's **key** (not its file name) to resolve conflicts; only needed on
conflict.

**`--remove-attachment=key`** — `Config::removeAttachment(key)`; repeatable;
JSON `"removeAttachment": [ … ]`. Remove the attachment with that key (find keys with
`--list-attachments`), clearing its file specification so no residual data remains
(internal links to it break).

(Date syntax for `--creationdate`/`--moddate`: §10.8.)

### 5.12 PDF Inspection (16)

No output file may be given with any option in this section. Text output goes to the
logger's info stream; binary stream output (`--raw-stream-data`,
`--filtered-stream-data`, `--show-attachment`) goes to the logger's **save** pipeline.

**`--is-encrypted`** — `Config::isEncrypted()`. Silently report encryption status via
exit code (§3); works without the password; other options ignored; mutually exclusive
with `--requires-password`.

**`--requires-password`** — `Config::requiresPassword()`. Silently report password
status via exit code (§3); combine with `--password` to test a password.

**`--check`** — `Config::check()`. Structure, encryption, linearization, and stream
encoding checks; results to stdout; exit 0/2/3 per errors/warnings. Writes nothing to
stderr when valid. Checks run before other options (other options then operate on the
recovered file — useful for salvage). Does not validate page content or semantics.

**`--show-encryption`** — `Config::showEncryption()`. Print encryption parameters;
includes the user password when opened with the owner password and the format allows
recovery. Included in `--check` output.

**`--show-encryption-key`** — `Config::showEncryptionKey()`. With encryption display,
show the computed/retrieved encryption key as hex (usable with `--password-is-hex-key`).

**`--check-linearization`** — `Config::checkLinearization()`. Check whether the file is
linearized and its hint tables are correct (linearization "errors" are warnings).

**`--show-linearization`** — `Config::showLinearization()`. Dump the linearization hint
tables.

**`--show-xref`** — `Config::showXref()`. Dump the cross-reference table/stream:
offsets for regular objects; object-stream ID + 0-based index for compressed objects;
shows reconstructed data after recovery.

**`--show-object={trailer|obj[,gen]}`** — `Config::showObject(spec)`; repeatable.
Print an object's contents (resolves compressed objects). `trailer` or `N` or `N,G`.

**`--raw-stream-data`** — `Config::rawStreamData()`. With `--show-object` on a stream:
write the raw (still-compressed) binary data to the save stream. Avoid mixing with
other inspection options.

**`--filtered-stream-data`** — `Config::filteredStreamData()`. With `--show-object` on
a stream: write the filtered (uncompressed) binary data; behaves as
`--decode-level=all` (unsupported filters → error); combinable with
`--normalize-content`.

**`--show-npages`** — `Config::showNpages()`. Print the page count (script-friendly,
one line).

**`--show-pages`** — `Config::showPages()`. For each page: object/generation of the
page dictionary and its content streams. `--with-images` adds images.

**`--with-images`** — `Config::withImages()`. Include image object IDs in
`--show-pages` output.

**`--list-attachments`** — `Config::listAttachments()`. Key + stream number per
embedded file; `--verbose` adds names, description, dates (PDF timestamp syntax).

**`--show-attachment=key`** — `Config::showAttachment(key)`. Write that attachment's
bytes to the save stream. Only the last one takes effect if repeated — unlike most
repeatable options.

### 5.13 JSON Options (9)

See §6 for the format itself.

**`--json[=version]`** — `Config::json()` / `Config::json(v)`; choices `1` | `2` |
`latest` (default; current latest = 2). Generate the qpdf JSON representation.
Output file optional (defaults to stdout). Stream data included only per
`--json-stream-data`/`--json-output`.

**`--json-help[=version]`** — CLI-only (§5.4). Schema of the JSON output.

**`--json-key=key`** — `Config::jsonKey(k)`; repeatable; choices `acroform`,
`attachments`, `encrypt`, `objectinfo` (v1), `objects` (v1), `outlines`, `pagelabels`,
`pages`, `qpdf` (v2). Restrict output to the named top-level keys. Default: all keys
(`version`/`parameters` always included in v-default mode); with `--json-output`,
default is only `qpdf`.

**`--json-object={trailer|obj[,gen]}`** — `Config::jsonObject(spec)`; repeatable.
Restrict the objects dictionary to the listed objects.

**`--json-stream-data={none|inline|file}`** — `Config::jsonStreamData(p)`.
`none` (default) = omit stream data; `inline` = base64 `"data"` key; `file` = write
each stream to `<prefix>-<objnum>`. Default becomes `inline` with `--json-output`.

**`--json-stream-prefix=file-prefix`** — `Config::jsonStreamPrefix(p)`.
Override the stream-file prefix (default: output file name).

**`--json-output[=version]`** — `Config::jsonOutput()` / `Config::jsonOutput(v)`.
Faithful-serialization presets: implies `--json=version`; `--json-stream-data=inline`;
`--decode-level=none`; only the `qpdf` key by default; omits `version`/`parameters`.
Each default can be overridden by the explicit option.

**`--json-input`** — `Config::jsonInput()`. Treat the input file as qpdf JSON (must be
complete: version ≥ 2, all stream data, trailer with `/Root`; other top-level keys
ignored).

**`--update-from-json=qpdf-json-file`** — `Config::updateFromJson(f)`. Update the
input PDF from the JSON file's objects (object numbers must match the input PDF;
rules in §6.3).

### 5.14 Global Options (7)

**`--global [options] --`** — `Config::global()` → `GlobalConfig`; group terminated by
`--`. JSON: `"global": { … }`. Begin setting global limits.

**`--no-default-limits`** — `GlobalConfig::noDefaultLimits()`. Disable all *optional*
default limits (explicitly set limits unaffected; `--parser-max-nesting` cannot be
disabled, only modified).

**`--parser-max-nesting=n`** — `GlobalConfig::parserMaxNesting(n)`. Max parser nesting
level. Default 499. Not disabled by `--no-default-limits`.

**`--parser-max-errors=n`** — `GlobalConfig::parserMaxErrors(n)`. Max errors while
parsing one indirect object; `0` = unlimited. Default 15.

**`--parser-max-container-size=n`** — `GlobalConfig::parserMaxContainerSize(n)`. Max
top-level objects in a container when the xref and object are undamaged.
Default 4,294,967,295.

**`--parser-max-container-size-damaged=n`** —
`GlobalConfig::parserMaxContainerSizeDamaged(n)`. Same, for damaged xref/objects (and
when parsing xref streams). Default 5,000.

**`--max-stream-filters=n`** — `GlobalConfig::maxStreamFilters(n)`. Max filters on a
stream; exceeded → treated as unfilterable. Default 25.

### 5.15 Options for Testing or Debugging (5)

**`--static-id`** — `Config::staticId()`. Fixed document ID; testing only, never for
production files. See idempotency (§10.4).

**`--static-aes-iv`** — `Config::staticAesIv()`. Static AES-CBC initialization vector;
testing only; significantly weakens encryption. With `--static-id` and the three-step
process, byte-identical 256-bit-encrypted output is possible.

**`--linearize-pass1=file`** — `Config::linearizePass1(f)`. Save linearization pass 1
to a file. **Not a valid PDF**; debugging `QPDFWriter` only.

**`--test-json-schema`** — `Config::testJsonSchema()`. Verify `--json` output against
`--json-help` schema (test-suite use; doubles memory — not for large files).

**`--report-memory-usage`** — `Config::reportMemoryUsage()`. Best-effort peak-memory
report (performance test suites).

## 6. qpdf JSON output format

Produced by `--json` (all versions) / `--json-output` (v2 defaults); written by
`QPDF::writeJSON`, **not** `QPDFWriter` — so no object renumbering, no recompression,
no unreferenced-object removal, always-decrypted data, and structural objects
(linearization dicts/hint streams, object streams, xref streams) are included.

### 6.1 Top-level keys

`version` (integer; bumped on incompatible changes), `parameters`
(`{"decodelevel": …}`), `qpdf` (v2) or `objects` + `objectinfo` (v1), `pages`,
`pagelabels`, `outlines`, `acroform`, `encrypt`, `attachments`. Key selection via
`--json-key`; unknown keys must be ignored by consumers (forward-compat guarantee);
dictionaries guaranteed to have exactly the same keys across instances; `x-*` keys are
reserved for application data and never emitted by qpdf.

### 6.2 The `qpdf` key (version 2)

A two-element array: `[header, objectsDictionary]`.

Header keys: `jsonversion` (2), `pdfversion` (e.g. `"1.7"`),
`pushedinheritedpageresources` (bool), `calledgetallpages` (bool), `maxobjectid`
(safe starting point for new object IDs; may exceed the actual max due to dangling
references).

Objects dictionary: keys `"trailer"` or `"obj:{O} {G} R"`; values are
`{"value": …}` for regular objects or `{"stream": …}` for streams. Stream objects:
`"dict"` (dictionary without `/Length`) plus, per `--json-stream-data`: nothing (`none`),
`"data"` (base64; `inline`), or `"datafile"` (path; `file`). The trailer is always a
dictionary value even when the file uses an xref stream.

Object value encoding: booleans/null as JSON; numbers arbitrary precision (internally
strings); names `"/canonical"` or `n:/pdf-syntax` (required for binary names);
indirect references `"O G R"`; strings `"u:…"` (unambiguous Unicode) or `"b:…"` (hex
bytes; the internal PDF representation, e.g. UTF-16 with `FEFF` BOM or UTF-8 with
`EFBBBF`); arrays/dictionaries recursively. Empty string = `"u:"`.

JSON version 1 (legacy) differences: `objects`/`objectinfo` keys instead of `qpdf`,
no `obj:` prefix on object keys, ambiguous strings/names, `"moddifyannotations"` typo,
no PDF version, no stream data. Prefer version 2.

### 6.3 JSON input rules (`--json-input`, `--update-from-json`)

- `--json-input` (primary input from JSON): file must be complete — `jsonversion`
  (≥ 2), `pdfversion`, stream data for all streams, trailer with `/Root`.
- `--update-from-json` (modify existing QPDF): objects are updated **in place**, so
  object numbers must match the input PDF (a JSON made from `in.pdf` cannot update
  `out.pdf` produced by running qpdf on `in.pdf`). Unmodified objects — including the
  trailer — may be omitted (combine with `--json-object` to emit only wanted objects);
  a stream may omit `data`/`datafile` to keep its original bytes, but an empty
  `dict` **clears** the dictionary; object types may be changed (a stream replacing a
  non-stream must carry data); `pdfversion` may be omitted to preserve it.
- Ignored on read: `maxobjectid`, `/Length`, `/Size`, `version` top-level key,
  `calledgetallpages`/`pushedinheritedpageresources` (defaults false on update),
  unknown keys everywhere, and the summary keys (`pages` etc.) — they need not be kept
  consistent when editing.
- Round-trip: `qpdf --json-output in.pdf pdf.json` → edit → `qpdf --json-input pdf.json
  out.pdf`; or `qpdf in.pdf out.pdf --update-from-json=pdf.json`.

### 6.4 Schema and guarantees

`--json-help[=version]` emits the schema as descriptions: a dictionary in help = same
keys in output; `<…>` single key = arbitrary-key dictionary; single-element array =
single item or array of any length; multi-element array = fixed length; strings =
descriptions of arbitrary values (possibly null). Keys guaranteed present may be
null/empty when unknown. `pageposfrom1`-style keys are 1-based page positions (to align
with the 1-based page-range syntax). `filterable` under page images depends on the
effective `--decode-level` (recorded in `parameters.decodelevel`).

## 7. Job JSON input format

Input to `--job-json-file`, `QPDFJob::initializeFromJson(json, partial)`, and
`qpdfjob_run_from_json`. Current schema version: `LATEST_JOB_JSON` = 1
(`--job-json-help` prints it; `QPDFJob::job_json_schema(1)` returns it
programmatically).

- Top level is an object; keys are camelCase CLI option names. Flag options take the
  empty string (`"qdf": ""`); valued options take the value (`"objectStreams":
  "generate"`); positional files are named keys: `"inputFile"`, `"outputFile"`.
- Repeatable/grouped options are arrays of objects:
  - `"pages": [ {"file": …, "range": …, "password": …}, … ]`
  - `"overlay"` / `"underlay": [ {"file": …, "to": …, "from": …, "repeat": …,
    "password": …}, … ]`
  - `"addAttachment": [ {"file": …, "key": …, "filename": …, "creationdate": …,
    "moddate": …, "mimetype": …, "description": …, "replace": …}, … ]`
  - `"copyAttachmentsFrom": [ {"file": …, "prefix": …, "password": …}, … ]`
  - `"setPageLabels": [ "spec", … ]`, `"rotate": [ "+90:1-3", … ]`,
    `"jsonKey": [ … ]`, `"jsonObject": [ … ]`, `"removeAttachment": [ … ]`
- Encryption nests under one key-length container (the container *is* the `--bits`
  value; there is no `"bits"` key):
  `"encrypt": { "userPassword": …, "ownerPassword": …, "256bit": { "print": "low", … } }`
  with containers `"40bit"`, `"128bit"`, `"256bit"` holding the §5.10 sub-options
  (`force-V4`, `force-R5`, `use-aes`, `allow-insecure`, `cleartext-metadata` keep CLI
  spelling).
- Global limits nest under `"global": { "noDefaultLimits": …, "parserMaxErrors": …, … }`.
- Partial files: a job-JSON file given via `--job-json-file` is loaded with
  `partial = true` (no final validation) and can be combined with, and overridden by,
  other command-line arguments; multiple JSON files are allowed. A JSON passed to
  `initializeFromJson` directly with `partial = false` is fully validated.
- QPDFUsage is thrown (CLI exit 2) for invalid combinations; messages are CLI-centric
  and map back to JSON keys.

## 8. Note on `fix-qdf`

`fix-qdf` is a separate, tiny CLI shipped with qpdf that repairs hand-edited QDF files:
it re-scans the QDF text, locates objects by their comments, rewrites stream lengths,
and rebuilds the cross-reference table. It is **not** part of `QPDFJob`; the tool is
self-contained (it uses only `QPDFXRefEntry`/`QUtil` from the library), so a jqpdf
wrapper cannot reach it through the job API — reproducing it means porting its ~400
lines of logic or invoking the binary. The jqpdf wrapper treats it as out of scope;
`--qdf` output does not depend on it. (The `zlib-flate` helper — raw flate
compress/decompress for testing — is likewise a standalone tool, out of scope.)

## 9. JNI wrapper guidance

- **Mapping rule:** CLI `--kebab-case` ↔ config method `camelCase` ↔ job-JSON key
  `camelCase` (exceptions: `force-V4`, `force-R5`, `use-aes`, `allow-insecure` in JSON
  keep their CLI spelling; JSON adds `inputFile`/`outputFile` and per-group containers).
- **Parameter kinds** (job.yml): bare flag (no-arg method, JSON `""`), optional
  parameter/choices (two overloads; JSON value optional), required parameter (string),
  required choices (enum — validate against the choice sets listed in §5), file
  parameter (path string).
- **CLI-only functionality** to re-implement in Java or route through argv:
  `--help`, `--version`, `--copyright`, `--show-crypto`, `--job-json-help`,
  `--json-help`, `--zopfli`, `--completion-bash`, `--completion-zsh`, plus `@argfile`
  expansion (`@-` for stdin) which is an argv-parser feature. Useful programmatic
  equivalents: `QPDF::QPDFVersion()`, `QPDFJob::job_json_schema(1)`,
  `QPDFJob::json_out_schema(v)`, `QPDFCryptoProvider` list.
- **Environment variables** consulted by the CLI/library: `QPDF_ZOPFLI` (§10.3),
  `QPDF_EXECUTABLE` (completion only), `QPDF_CRYPTO_PROVIDER` (crypto provider
  selection, not a CLI option).
- **Lifecycle:** one `qpdfjob_handle` per job. The library is safe in a multithreaded
  program, but no individual qpdf object instance (`QPDFJob` included) may be used in
  more than one thread at a time — use one job per thread.
  `qpdfjob_initialize_*` failure invalidates the handle (return 2).
  Two-stage runs (`qpdfjob_create_qpdf` → … → `qpdfjob_write_qpdf`) expose the
  `qpdf_data` object from `qpdf-c.h` for arbitrary manipulation before writing.
- **Output capture:** install a private logger before initialization
  (`qpdflogger_create` + `qpdfjob_set_logger`) with custom destinations for
  info/warn/error and a **separate** save pipeline for binary output; or discard
  destinations and rely on exit codes/returned data.
- **Exit-code surface for Java:** 0/2/3 plus the `--is-encrypted` /
  `--requires-password` statuses (§3); `QPDFJob::getEncryptionStatus()` exposes
  `qpdf_encryption_status_e` after a run: `qpdf_es_encrypted = 1<<0`,
  `qpdf_es_password_incorrect = 1<<1` (bitwise OR). (For reference,
  `Constants.h` also defines `qpdf_page_label_e` — `pl_none`, `pl_digits`,
  `pl_alpha_lower`, `pl_alpha_upper`, `pl_roman_lower`, `pl_roman_upper` — matching the
  §10.7 label types, usable as the JNI enum for `--set-page-labels`.)

## 10. Cross-cutting grammars and behaviors

### 10.1 Page range grammar

Used by `--pages --range`, `--rotate`'s `:page-range`, `--to`/`--from`/`--repeat`, and
page-label specs' first-page.

- `<n>` — page `n`, 1-based; `r<n>` — `n`-th from the end (`r1` = last); `z` — last page.
- Comma-separated groups, any order. `a-b` inclusive; descending when `a > b`.
- `x` prefix (since 11.7.1) excludes that number/range from the preceding non-x group.
- `:even` / `:odd` suffix keeps alternate positions **of the resulting sequence**
  (`:odd` starts with the first kept position, `:even` with the second).
- Examples: `1,6,4`; `3-7`; `7-3`; `1-z`; `z-1`; `1,3,5-9,15-12`; `r3-r1` (last three,
  in order); `r1-r3` (last three, reversed); `1-20:even` (pages 2..20);
  `5,7-9,12:odd` → 5, 8, 12; `1-10,x3-4` → 1, 2, 5–10;
  `4-10,x7-9,12-8,xr5` (15-page file) → 4, 5, 6, 10, 12, 10, 9, 8.

### 10.2 Encryption semantics

Two argv forms for `--encrypt`:
`--encrypt --user-password=U --owner-password=O --bits=N [sub-options] --`
(flag form, qpdf ≥ 11.7.0; only `--bits` mandatory; passwords must precede `--bits`),
or `--encrypt U O N [sub-options] --` (legacy positional; empty strings allowed;
supported indefinitely).

- Sub-option availability per key length: see §5.10 (40: annotate/extract/modify/print
  as y|n; 128/256: the granular set; 128-only: use-aes, force-V4; 256-only: force-R5,
  allow-insecure).
- Defaults are fully permissive; restrictions opt-in.
- `--modify` level mapping (128/256): `none` → modify-other=n + annotate=n + form=n +
  assemble=n; `assembly` → the same minus assemble=n; `form` → minus form=n;
  `annotate` → modify-other=n only; `all` → no restriction. Explicit granular options
  may be combined; later options win.
- Version floors forced by encryption: 40-bit → 1.3; 128-bit → 1.4; 128-bit+AES → 1.6;
  cleartext metadata → 1.5; force-V4 → 1.5; 256-bit → 1.7 ext 8; force-R5 → 1.7 ext 3.
- Weak crypto (RC4): writing requires `--allow-weak-crypto` **outside** the
  `--encrypt … --` group. Not enforced for preserved/copied encryption.
- 256-bit with empty owner password requires `--allow-insecure`.
- Permission enforcement is up to viewers; qpdf itself ignores restrictions and does
  not check semantic combinations beyond the tables above.
- Considered insecure: identical user and owner passwords, and an empty owner password.
  Only the empty-owner-password case (with non-empty user password, 256-bit) is gated
  behind `--allow-insecure`.

### 10.3 Compression and Zopfli

- Flate levels 1–9 (`--compression-level`); affects newly compressed streams only
  unless `--recompress-flate`.
- Smallest-file recipe: `--compress-streams=y --decode-level=generalized
  --recompress-flate --compression-level=9 --optimize-images [--jpeg-quality=n]
  --object-streams=generate`.
- Zopfli (optional build): activation is via the `QPDF_ZOPFLI` environment variable —
  unset/`disabled` = off; `silent` = use if available, else silently fall back;
  `force` = use or fail; any other value = use or warn+fallback. ~100× slower, ~5%
  smaller than zlib. `--compression-level` is ignored while zopfli is active. The
  `--zopfli` CLI flag only reports status (§5.4). Library callers mirror the CLI via
  `Pl_Flate::zopfli_check_env()`.

### 10.4 Idempotency

qpdf is not byte-for-byte idempotent in general (random `/ID`, stream-length vs
key-sorting interactions). Deterministic runs: process with `--deterministic-id` three
times (`a → 1`, `1 → 2`, `2 → 3`); then `2.pdf` and `3.pdf` are byte-identical.
For encrypted reproducible output add `--static-id --static-aes-iv` (testing only).

### 10.5 Unicode passwords

- argv is UTF-8 on all platforms (Windows `wmain` converts). Passwords are
  auto-transcoded to the encoding the PDF spec requires for the encryption format
  (since 8.4.0).
- Encrypting with 40/128-bit and a non-UTF-8 password falls back to raw-byte
  interpretation.
- `--password-mode`: `auto` (detect UTF-8, transcode; default) | `unicode` (strict
  UTF-8, error if invalid) | `bytes` (literal bytes; can create files other readers
  can't open; workaround for legacy scripts) | `hex-bytes` (hex string; works on all
  platforms; affects reading as well as writing).
- Reading: qpdf retries the password transcoded through several encodings in both
  directions (password recovery) — disable with `--suppress-password-recovery` (e.g.
  to know the exact password bytes that work).
- No Unicode normalization or bidi handling (no ICU); `QPDFWriter` ultimately takes
  passwords as byte strings.
- `--password-is-hex-key` is orthogonal: it replaces the password entirely with a raw
  key and applies to the main input file only.

### 10.6 Page-selection limitations

- Document-level data (outlines, article threads, etc.) is taken from the **primary**
  input file only; page labels are preserved from all files (since 8.3). Use `--empty`
  as primary to drop document-level data.
- The same page selected twice from the same file shares all its objects (fonts,
  images, form fields). To get independent copies, name the file differently (`.`
  vs `./in.pdf`) — by design, distinct paths are distinct files.
- `--split-pages` output files contain no document-level data at all (fresh document
  per page group).
- Bookmarks pointing at dropped pages become dangling (harmless but non-functional).

### 10.7 Page label spec grammar

`--set-page-labels` specs: `first-page:[type][/start[/prefix]]`.

- `first-page`: page-range-style number (`n`, `r<n>`, `z`).
- `type`: `D` (Arabic) | `A` (upper alpha) | `a` (lower alpha) | `R` (upper Roman) |
  `r` (lower Roman) | omitted (prefix only, no number).
- `start` ≥ 1 (default 1); `prefix` any string.
- First spec must be `1:…`; specs in increasing page order; each applies until the
  next; `first-page:` with empty type suppresses numbering from that page.
- Example (`1:r 5:D 12: 14:D/10 r5:D//A- z://"end note"` on a 50-page file): pages
  1–4 → i–iv; 5–11 → 1–7; 12–13 unlabeled; 14–45 → 10–41; 46–49 → A-1..A-4; 50 →
  `end note`.

### 10.8 PDF date format

`D:yyyymmddhhmmssz`, `z` = `Z` (UTC) or `+hh'mm'` / `-hh'mm'`. Examples:
`D:20210207161528-05'00'`, `D:20210207211528Z`. Used by `--creationdate`,
`--moddate`; `--list-attachments` prints dates in this syntax (JSON `attachments`
uses ISO-8601 instead).



