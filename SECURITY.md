# Security policy

## Supported versions

Only the latest release line receives security fixes. jqpdf bundles the
qpdf native libraries, so fixes for qpdf-level vulnerabilities arrive as
a jqpdf release with a bumped `qpdf.version` — check
[releases](https://github.com/n-kostadinov/jqpdf/releases) for the
currently bundled qpdf version.

## Reporting a vulnerability

Please report vulnerabilities privately to **nk@jquad.de** (or use
GitHub's *Report a vulnerability* action on the
[Security tab](https://github.com/n-kostadinov/jqpdf/security) if
available). Do **not** open a public issue for security problems.

Include what you can of: affected jqpdf version, `Jqpdf.QPDF_VERSION`,
platform, a reproduction, and impact. You will get an acknowledgement
within a few days; please allow time for assessment and coordinated
disclosure before publishing anything.

## Scope notes

- Issues in the **bundled qpdf binaries** (the C++ library) should also
  be reported upstream to [qpdf](https://github.com/qpdf/qpdf) — jqpdf
  will track and pick up the fixed qpdf release.
- Vulnerabilities in the **JNI shim** (`src/native/jni/`) and in the
  Java layer are handled here.
