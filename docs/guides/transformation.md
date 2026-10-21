# PDF transformation options

Covers the qpdf CLI's *PDF Transformation* area (§5.7): options that change
how the output file is structured — linearization, QDF, stream compression,
object streams, and PDF versions. None of them change what the document
renders.

## Linearization (fast web view)

```java
Jqpdf.createJob()
        .input("in.pdf").output("out.pdf")
        .linearize()
        .run().orThrow();
```

Verify with `checkLinearization()` — see [inspection](inspection.md).

## QDF mode (human-readable output)

`qdf()` writes uncompressed, consistently formatted PDFs meant for
inspecting or hand-editing PDF syntax; the output carries a `%QDF-1.0`
marker. It implies `compressStreams(false)` and `normalizeContent(true)`
(both overridable). `noOriginalObjectIds()` drops the locator comments.

## Streams and compression

- `compressStreams(false)` — leave decoded streams uncompressed.
- `decodeLevel(level)` — `NONE`, `GENERALIZED` (default), `SPECIALIZED`,
  `ALL`; controls which filters qpdf tries to decode.
- `streamData(mode)` — legacy shortcut: `COMPRESS` (default),
  `PRESERVE`, `UNCOMPRESS`.
- `recompressFlate()` — also recompress streams that were already flate
  (needed for `compressionLevel(n)` to affect them; can be much slower).
- `compressionLevel(n)` — flate level 1 (fastest) … 9 (smallest).
- `jpegQuality(n)` — JPEG quality 0–100 used when optimizing images.

Smallest-file recipe:

```java
Jqpdf.createJob()
        .input("in.pdf").output("out.pdf")
        .compressStreams(true)
        .decodeLevel(DecodeLevel.GENERALIZED)
        .recompressFlate()
        .compressionLevel(9)
        .objectStreams(ObjectStreams.GENERATE)
        .run().orThrow();
```

## Object streams and unreferenced objects

- `objectStreams(mode)` — `PRESERVE` (default), `DISABLE` (all objects
  top-level; older viewers), `GENERATE` (compresses objects; forces PDF ≥
  1.5).
- `preserveUnreferenced()` — keep objects not reachable from the trailer
  (default: qpdf discards them).
- `removeUnreferencedResources(mode)` — strip unused page resources when
  splitting: `AUTO` (default heuristic), `YES` (full analysis), `NO`.

## Content and layout tweaks

- `normalizeContent(true)` — normalize newlines in content streams.
- `newlineBeforeEndstream()` — PDF/A preservation requirement.
- `coalesceContents()` — merge each page's content streams into one.
- `externalizeInlineImages()` / `iiMinBytes(n)` — move inline images into
  regular image objects above the size threshold (default 1024 bytes).

## PDF version control

- `minVersion("1.7")` — raise the output version if the input is lower.
- `forceVersion("1.4")` — force the header version even when newer features
  are present; downgrading can disable dependent features (AES, object
  streams, 256-bit encryption …).
