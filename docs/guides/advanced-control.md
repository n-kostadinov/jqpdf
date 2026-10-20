# Advanced control options

Covers the qpdf CLI's *Advanced Control Options* area (§5.6): expert
password handling and parser behavior for damaged or hybrid files.

## Password handling

- `passwordIsHexKey()` — treat `password(...)` as a hex-encoded **encryption
  key**, skipping password-to-key computation entirely. Combine with
  `showEncryptionKey()` (see [inspection](inspection.md)) to open a file
  with its raw key. Applies to the main input only.
- `passwordMode(mode)` — how the supplied password is interpreted:
  `AUTO` (default; detects UTF-8 and transcodes per the PDF spec),
  `UNICODE` (strict UTF-8), `BYTES` (literal bytes),
  `HEX_BYTES` (hex string; the only mode that also affects *reading*).
- `suppressPasswordRecovery()` — disable qpdf's automatic retry of the
  password in alternative encodings; use it when you need to know the exact
  password bytes that work.

```java
Jqpdf.createJob()
        .input("encrypted.pdf")
        .password(hexKey).passwordIsHexKey()
        .output("copy.pdf")
        .run().orThrow();
```

## Damaged and hybrid files

- `suppressRecovery()` — do not attempt cross-reference reconstruction; a
  damaged file is reported as an error (exit 2) instead of being repaired
  with warnings (exit 3).
- `ignoreXrefStreams()` — ignore cross-reference streams in hybrid files,
  falling back to the xref table; useful to mimic pre-1.5 viewers.

See [general options](general-options.md) for regular password input.
