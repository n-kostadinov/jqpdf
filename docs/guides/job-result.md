# Results and output capture

Every `run()` returns a [`JobResult`](../../src/main/java/dev/machineminds/jqpdf/JobResult.java):
the qpdf CLI exit code plus everything the job printed. Output is captured
per job in native buffers — the library never writes to the host process's
console. `run()` never throws for qpdf-level outcomes; builder misuse
(missing input, invalid values) throws `QpdfUsageException` before the
native call.

## Exit codes, success, and orThrow

Exit codes follow the qpdf CLI: 0 success, 2 errors (nothing processed),
3 warnings only.

```java
JobResult result = Jqpdf.createJob()
        .input("in.pdf")
        .output("out.pdf")
        .run();                 // never throws for qpdf-level outcomes

if (result.success()) { ... }           // exit code 0
if (result.hasWarnings()) { ... }       // exit code 3: done, but warned

result.orThrow();               // returns this, or throws on errors
```

`orThrow()` throws `QpdfJobException` only for exit code 2 — a job that
succeeded with warnings still returns its result, which is the usual
chaining shape:

```java
JobResult npages = Jqpdf.createJob()
        .input("in.pdf")
        .showNpages()
        .run().orThrow();       // warnings pass through
```

The exception carries the whole result, streams included:

```java
try {
    Jqpdf.createJob().input("in.pdf").output("out.pdf").run().orThrow();
} catch (QpdfJobException e) {
    log(e.result().exitCode() + ": " + e.result().errorText());
}
```

## Text output: infoText, warnText, errorText

The three streams map to what the qpdf CLI prints: `infoText()` is
stdout, `warnText()` and `errorText()` are the stderr halves split by
severity. Page counts, object listings, and other inspection output
arrive as `infoText()`:

```java
String pages = Jqpdf.createJob()
        .input("in.pdf")
        .showNpages()
        .run().orThrow()
        .infoText();            // e.g. "11"

boolean salvaged = Jqpdf.createJob()
        .input("damaged.pdf")
        .check()
        .run().orThrow()
        .hasWarnings();         // recovered, with qpdf's complaints in warnText()
```

## Binary output: savedData, savedText

Whatever the CLI would write through its save pipeline — attachment
extraction, raw or filtered stream bytes — arrives as `savedData()`
instead of a file:

```java
byte[] report = Jqpdf.createJob()
        .input("in.pdf")
        .showAttachment("report")
        .run().orThrow()
        .savedData();           // the attachment's bytes
```

JSON output written without an output file takes the same path, and
`savedText()` is its UTF-8 decoding:

```java
String json = Jqpdf.createJob()
        .input("in.pdf")
        .jsonOutput()
        .run().orThrow()
        .savedText();           // the qpdf JSON document as a string
```

The option side of these examples is covered in the
[inspection](inspection.md), [attachments](attachments.md), and
[JSON](json.md) guides. When the input itself is in memory, the same
results come back via `outputMemory()` — see
[in-memory I/O](memory-io.md) for `input(byte[])`/`InputStream`, the
spill directory, and `JQPDF_TMPDIR`.
