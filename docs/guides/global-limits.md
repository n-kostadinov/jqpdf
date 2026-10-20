# Global limits and testing options

Covers the qpdf CLI's *Global Options* (§5.14) and *Options for Testing or
Debugging* (§5.15) areas.

## Parser limits

```java
Jqpdf.createJob()
        .input("in.pdf").output("out.pdf")
        .global(g -> g
                .parserMaxErrors(50)
                .parserMaxContainerSizeDamaged(10_000))
        .run().orThrow();
```

Limits (with qpdf's defaults):

| Limit | Default | Meaning |
|---|---:|---|
| `parserMaxNesting` | 499 | Max parser nesting level; cannot be disabled |
| `parserMaxErrors` | 15 | Max errors per indirect object; 0 = unlimited |
| `parserMaxContainerSize` | 2³²−1 | Max top-level objects, undamaged files |
| `parserMaxContainerSizeDamaged` | 5000 | Same, during recovery of damaged files |
| `maxStreamFilters` | 25 | Max filters on one stream |

`noDefaultLimits()` disables the optional defaults for this job (explicitly
set limits are unaffected; the nesting limit stays).

**Job isolation note:** qpdf keeps these limits in process-global state —
a limit set by one CLI process cannot leak, but a long-running JVM hosting
many jobs would normally carry customized limits into later jobs. jqpdf
compensates: once any limit has been customized in the process, every
subsequent job explicitly pins each such limit to its own effective value
(your setting, or qpdf's default if none). Jobs therefore always behave as
configured, regardless of what ran before.

## Testing options

- `staticId()` — fixed document `/ID`; testing only, never for production.
- `staticAesIv()` — static AES initialization vector; with `staticId()` and
  the three-step idempotency process (spec §10.4) enables byte-identical
  encrypted output.
- `linearizePass1(file)` — dump linearization pass 1; **not a valid PDF**,
  for debugging only.
- `testJsonSchema()` — validate `json()` output against the schema.
- `reportMemoryUsage()` — best-effort peak-memory report in the output.
