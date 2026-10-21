# Attachments (embedded files)

Covers the qpdf CLI's *Embedded Files* area (§5.11): attaching, copying,
removing, listing, and extracting embedded files.

## Attaching a file

```java
Jqpdf.createJob()
        .input("in.pdf").output("out.pdf")
        .addAttachment(a -> a
                .file("report.xlsx")
                .key("report")            // name-tree key; default: basename
                .filename("Q3 report.xlsx")
                .mimetype("application/vnd...sheet")
                .description("Quarterly report")
                .creationdate("D:20210207211528Z"))  // PDF dates, spec §10.8
        .run().orThrow();
```

Call `addAttachment(...)` repeatedly to embed several files; `replace()`
allows overwriting an existing key (a duplicate key is an error otherwise).

## Copying, removing

- `copyAttachmentsFrom(c -> c.file("other.pdf").prefix("x"))` — copy all of
  another file's attachments; the prefix distinguishes conflicting keys;
  `password(...)` opens an encrypted source.
- `removeAttachment("key")` — repeatable; also clears the file specification
  so no residual data remains.

## Listing and extracting

`listAttachments()` and `showAttachment(key)` are inspection-style jobs —
run them without an output file:

```java
JobResult listing = Jqpdf.createJob()
        .input("out.pdf")
        .listAttachments()
        .run().orThrow();          // keys are in listing.infoText()

JobResult data = Jqpdf.createJob()
        .input("out.pdf")
        .showAttachment("report")
        .run().orThrow();          // bytes are in data.savedData()
```
