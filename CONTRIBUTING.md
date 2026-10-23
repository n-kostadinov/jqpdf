# Contributing to jqpdf

Thanks for your interest in improving jqpdf. This document covers the
practical parts: development setup, what a good change looks like, and
where to ask questions.

## Reporting problems and ideas

- **Bugs and feature requests** go through the
  [issue templates](https://github.com/n-kostadinov/jqpdf/issues/new/choose).
  For bugs, include a minimal snippet and the jqpdf/Java versions — the
  templates prompt for what matters.
- **Security issues** are handled privately — see [SECURITY.md](SECURITY.md).
- **Usage questions** — check the [guides](docs/guides/README.md) first;
  every options area of the library has a page with examples. If the docs
  are unclear, that is a documentation bug: please open an issue.

## Development setup

jqpdf bundles native qpdf libraries behind a JNI shim, so a working
build needs a bit more than a plain Maven project:

1. **JDK 25** (toolchain; `maven.compiler.release=11`, so `src/main` must
   only use Java 11 APIs).
2. **Bash** and a C compiler for the JNI shim: on Windows, WSL with
   `gcc-mingw-w64-x86-64`; on Linux, native `gcc`.
3. **`gh` CLI**, authenticated, for `./scripts/fetch-qpdf-binaries`.

Then:

```bash
./scripts/fetch-qpdf-binaries   # stage the pinned qpdf binaries + headers
mvn test                        # compiles the shim and runs the suite
```

Native tests read fixture PDFs from a sibling `qpdf` checkout (override
with the `QPDF_CHECKOUT` environment variable). Tests skip with a message
when the binaries have not been fetched — if everything skips, the fetch
step did not run.

The qpdf version is pinned by the `qpdf.version` property in `pom.xml`;
it drives the fetch script, the shim build, and `Jqpdf.QPDF_VERSION`.
The qpdf sources themselves are not part of this repository.

## What a good change looks like

- **New options / option coverage**: follow the existing pattern — one
  options interface per qpdf functional area, every method returning the
  job for chaining — and mirror the qpdf manual's semantics, including
  exit-code and warning behavior. Update the matching guide under
  `docs/guides/`.
- **Tests**: behavior changes come with tests. Tests that exercise the
  native path run against the real bundled binaries; use the
  `QpdfNative` adapter seam (`test/FakeQpdfNative`) for logic that
  should not depend on natives.
- **Compatibility**: `src/main` stays on Java 11 APIs; anything newer
  belongs in tests or build tooling only.
- **Commits**: imperative one-liners in the style of the existing
  history. Keep changes focused — one concern per PR.

## Pull requests

Fork, branch, push, open a PR against `main`. CI builds the native stack
and runs the multi-OS test matrix; it must pass. For anything that
changes the public API or the native contract (`QpdfNative` ABI), open an
issue first so the design can be agreed on.
