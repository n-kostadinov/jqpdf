# qpdf JSON

Covers the qpdf CLI's *JSON Options* area (§5.13) and the JSON format itself
(spec §6). jqpdf returns JSON as text — it deliberately ships no JSON parser;
feed the string to your parser of choice.

## Producing JSON

```java
Jqpdf.createJob()
        .input("in.pdf").output(Paths.get("doc.json").toString())
        .jsonOutput()                  // faithful-serialization presets
        .run().orThrow();
```

- `json()` / `json(version)` — write the qpdf JSON representation (version 1
  is legacy; 2 is current).
- `jsonOutput()` — presets for faithful serialization: implies `json()`,
  inline stream data, decode level none, only the `qpdf` key.
- `jsonKey("pages")` — repeatable; restrict output to specific top-level keys.
- `jsonObject("trailer")` — repeatable; restrict the objects dictionary.
- `jsonStreamData(NONE|INLINE|FILE)` + `jsonStreamPrefix(prefix)` — stream
  data handling: omitted, base64 `"data"` keys, or side files.

Without an output file, JSON goes to the captured info text.

## Reading JSON back

- `jsonInput()` — the input file *is* qpdf JSON (must be complete: version
  ≥ 2, all stream data, trailer with `/Root`).
- `updateFromJson(file)` — update the input PDF's objects in place from a
  qpdf JSON file; object numbers must match the input (see spec §6.3 for the
  omission and stream rules).

Round trip:

```java
Jqpdf.createJob().input("in.pdf").output("doc.json").jsonOutput().run().orThrow();
Jqpdf.createJob().input("doc.json").output("out.pdf").jsonInput().run().orThrow();
```

`testJsonSchema()` (see [global limits and testing](global-limits.md))
validates generated JSON against the published schema.
