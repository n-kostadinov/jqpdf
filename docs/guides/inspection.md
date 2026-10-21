# Inspection

Covers the qpdf CLI's *PDF Inspection* area (§5.12): jobs that report
information instead of writing a file. Run them without an output file.
Text output lands in `JobResult.infoText()`; binary stream output
(`rawStreamData`/`filteredStreamData`) in `JobResult.savedData()`.

## Encryption and passwords

```java
// exit code 0 = encrypted, 2 = not encrypted (works without a password)
int code = Jqpdf.createJob().input("f.pdf").isEncrypted().run().exitCode();

// 0 = password required, 2 = not encrypted, 3 = supplied password correct
int code2 = Jqpdf.createJob().input("f.pdf").password("user")
        .requiresPassword().run().exitCode();
```

## Structural checks

- `check()` — structure, encryption, linearization, and stream checks; the
  full report is captured in the info text. Checks run before other options,
  so a job can check and then salvage a damaged file.
- `checkLinearization()` / `showLinearization()` — verify or dump the hint
  tables of a linearized file (linearization "errors" count as warnings).
- `showEncryption()` / `showEncryptionKey()` — encryption parameters; the
  key hex pairs with `passwordIsHexKey()` (see
  [advanced control](advanced-control.md)).
- `showNpages()` — the page count as one line.
- `showPages()` (+ `withImages()`) — per-page object numbers and content
  streams (and images).

## Objects and streams

```java
JobResult object = Jqpdf.createJob()
        .input("f.pdf")
        .showObject("trailer")   // or "12,0" for object 12 generation 0
        .run().orThrow();        // the object source is in object.infoText()

JobResult stream = Jqpdf.createJob()
        .input("f.pdf")
        .showObject("15,0")
        .rawStreamData()         // or filteredStreamData() for decoded bytes
        .run().orThrow();        // bytes are in stream.savedData()
```

- `showObject(spec)` is repeatable; `trailer` or `N[,G]` selects the object
  (compressed objects are resolved).
- `rawStreamData()` — the still-compressed bytes.
- `filteredStreamData()` — decoded bytes (behaves like decode level `all`;
  unsupported filters are an error).

`showXref()` dumps the cross-reference table or stream, showing reconstructed
data after recovery.
