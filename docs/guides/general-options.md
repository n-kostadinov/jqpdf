# General options

Covers the qpdf CLI's *Basic Invocation* (§5.1), *Exit Status* (§5.2), and
*General Options* (§5.5) areas: choosing input and output, passwords,
verbosity, and file-handling hints.

## Input and output

```java
JobResult copy = Jqpdf.createJob()
        .input("in.pdf")
        .output("out.pdf")
        .run()
        .orThrow();
```

- `input(file)` sets the input PDF. Standard input is not supported by qpdf;
  the file must be seekable.
- `emptyInput()` uses a zero-page dummy input instead of a file — combine
  with `pages(...)` (see [page-selection](page-selection.md)) to build
  documents without inheriting document-level data.
- `output(file)` sets the output PDF; omit it for inspection jobs.
- `replaceInput()` writes the result over the input file (safe: qpdf writes a
  temp file first and keeps `{input}.~qpdf-orig` when warnings occur).
  Calling `output(...)` after `replaceInput()` (or the other way round)
  simply makes the later call win.
- `jobJsonFile(file)` merges a QPDFJob JSON file into the job; it can be
  combined with regular options (later options override).

## Passwords

- `password(p)` supplies the password for the encrypted **main input**.
- `passwordFile(f)` reads the password from the file's first line.

```java
Jqpdf.createJob()
        .input("encrypted.pdf").password("user")
        .output("copy.pdf")
        .run().orThrow();
```

## Warnings, progress, determinism

- `warningExit0()` — treat warnings as success (exit 0 instead of 3).
- `noWarn()` — suppress warning output; the exit code still reflects
  warnings unless `warningExit0()` is also set.
- `progress()` — qpdf reports percentage progress while writing.
- `verbose()` — extra detail about what qpdf did (also affects several
  inspection outputs).
- `deterministicId()` — derive the document `/ID` from a content digest so
  identical operations produce identical IDs.
- `allowWeakCrypto()` — permit writing RC4-encrypted files (see
  [encryption](encryption.md)).

## Merging many files

- `keepFilesOpen(false)` — reopen files on demand instead of holding them
  open (relevant when `pages(...)` merges more than the default threshold).
- `keepFilesOpenThreshold(n)` — override the default of 200 files.
