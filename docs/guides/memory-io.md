# In-memory I/O

PDFs as `byte[]` or `InputStream`, qpdf JSON documents as `String`,
attachment payloads as `byte[]`, and results back in memory — no
caller-managed files. Covers the API surface, where intermediate files
live, and how to point them at a memory-backed volume in Kubernetes
(spec [§9.1](../qpdf-spec.md)).

## API tour

Bytes in, bytes out — the smallest complete job:

```java
byte[] pdf = ...;

JobResult result = Jqpdf.createJob()
        .input(pdf)            // spilled to jqpdf-*.pdf for the run
        .linearize()
        .outputMemory()        // CLI outfile "-"
        .run()
        .orThrow();

byte[] linearized = result.savedData();
```

Every job feature works unchanged on in-memory inputs — merging,
encryption, overlays, JSON — because the bytes are materialized as a real
file for qpdf and removed afterwards. Streams are copied to the spill file
as they are read, never buffered fully in memory, and are consumed by the
run they were given to:

```java
JobResult result = Jqpdf.createJob()
        .input(new ByteArrayInputStream(pdfBytes))
        .outputMemory()
        .run()
        .orThrow();
```

If the bytes already live in a file, say so — `input(Path)` (like
`input(String)`) passes the path through to qpdf without copying, and
`replaceInput()` remains legal for it.

The qpdf JSON formats take strings (see the
[JSON guide](json.md) for the format itself):

```java
// Serialize to the qpdf JSON representation...
String json = Jqpdf.createJob()
        .input(pdf)
        .jsonOutput()
        .run()
        .orThrow()
        .savedText();          // UTF-8 decode of savedData()

// ...modify it, and rebuild a PDF from it.
byte[] rebuilt = Jqpdf.createJob()
        .jsonInputContent(json)
        .outputMemory()
        .run()
        .orThrow()
        .savedData();

// Or apply it as an update to an existing PDF.
byte[] updated = Jqpdf.createJob()
        .input(pdf)
        .updateFromJsonContent(modifiedJson)
        .outputMemory()
        .run()
        .orThrow()
        .savedData();
```

Attachment payloads embed the same way (see the
[attachments guide](attachments.md)):

```java
byte[] withLogo = Jqpdf.createJob()
        .input(pdf)
        .addAttachment(a -> a
                .content(logoBytes)     // instead of file(...)
                .key("logo.png"))       // required: no filename to default from
        .outputMemory()
        .run()
        .orThrow()
        .savedData();
```

## Where the files go, and for how long

qpdf's job layer opens its input by path and requires a seekable file —
there is no stdin or in-memory mode at that API level. So each `run()`
writes the in-memory inputs to *spill files*, hands their paths to qpdf,
and deletes them in a `finally` right after the native job is destroyed.
A normal run — success, warnings, or errors — always cleans up; only a
JVM crash can leave a `jqpdf-*` file behind.

Two guarantees make this safe under load:

- **No collisions.** Files are created by `Files.createTempFile` with the
  prefix `jqpdf-` and a per-kind suffix (`.pdf`, `.json`, `.bin`). Its
  create-new semantics pick a unique name atomically, so concurrent jobs —
  and even separate processes sharing one directory — can never receive
  the same path.
- **No interference.** Each run deletes only the files it created; one job
  never sweeps another's spills. This matches the
  [concurrency model](../../README.md#concurrency): separate jobs run in
  parallel without coordination.

On POSIX systems the spill files are created with owner-only permissions
(`0600`), which keeps document contents private even in a directory shared
by several users or containers.

## Choosing the spill directory

The directory is resolved per spill, first match wins:

| Precedence | Knob | Typical use |
|---:|---|---|
| 1 | `job.tempDir(Path)` | Tests; redirecting one large job to disk |
| 2 | system property `jqpdf.tmpdir` | Same conventions as `jqpdf.native.dir` |
| 3 | environment variable `JQPDF_TMPDIR` | Kubernetes / deployment configuration |
| 4 | `java.io.tmpdir` | JVM default |

A configured directory (rows 1–3) that does not exist, is not a directory,
or is not writable fails the run immediately with an `IllegalStateException`
that names the knob — jqpdf never silently falls back to the JVM default.
The knob affects spill files only; the bundled native libraries are
extracted by `NativeLibrary` according to their own rules (normally
`java.io.tmpdir`), so a small memory-backed volume does not silently fill
with `.so`/`.dll` files.

## Kubernetes: memory-backed spill directory

Point `JQPDF_TMPDIR` at a dedicated `emptyDir` volume with
`medium: Memory` (a tmpfs) so spills never touch the node's disk:

```yaml
apiVersion: v1
kind: Pod
spec:
  volumes:
    - name: jqpdf-scratch
      emptyDir:
        medium: Memory
        sizeLimit: 1Gi
  containers:
    - name: app
      image: registry.example.com/app:latest
      env:
        - name: JQPDF_TMPDIR
          value: /var/jqpdf-scratch
      volumeMounts:
        - name: jqpdf-scratch
          mountPath: /var/jqpdf-scratch
```

Sizing notes:

- **tmpfs pages count against the container's memory limit.** A spilled
  500 MB PDF is 500 MB of memory as far as the OOM killer is concerned.
  Size `sizeLimit` (and the container limit) for *max concurrent jobs ×
  max document size*, not the average.
- For large-PDF workloads, drop `medium: Memory` and keep the default
  disk-backed `emptyDir` — spilling to disk is slower but cannot OOM the
  pod.
- Do not point `JQPDF_TMPDIR` at `/dev/shm`: containers typically get a
  tiny default there (often 64 MiB) and it is not cleanly tunable per
  workload. A dedicated volume as above is the supported shape.

## Known restrictions

These come from qpdf's CLI semantics and surface as native usage errors
(exit 2), not Java-side validation:

- `splitPages()` cannot write to standard output, so it requires
  `output(...)`, not `outputMemory()`.
- Writing JSON to standard output with `jsonStreamData(FILE)` requires
  `jsonStreamPrefix(...)` (qpdf needs a base name for the stream files).
- `replaceInput()` is rejected for spilled inputs — there is no caller
  file to overwrite. It stays available for `input(String)` and
  `input(Path)`.
