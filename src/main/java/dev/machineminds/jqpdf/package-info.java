/**
 * jqpdf: a Java wrapper around the native qpdf library. A {@link QpdfJob} is
 * a fluent, reusable builder for one qpdf invocation; running it yields a
 * {@link JobResult} with the qpdf CLI exit code (0 success, 2 error,
 * 3 warnings) plus qpdf's captured text and binary output.
 *
 * <p>Start with {@link Jqpdf#createJob()}; the option methods live in the
 * interfaces of {@link dev.machineminds.jqpdf.options}. The native qpdf
 * binaries ship inside the jar and load on first use — no system qpdf
 * installation is required. {@link QpdfNative} is the adapter seam for tests
 * and alternative bindings.
 *
 * <p>Throughout the API, references of the form {@code spec §x.y} point to
 * section x.y of the jqpdf CLI specification
 * (<a href="https://github.com/n-kostadinov/jqpdf/blob/main/docs/qpdf-spec.md">docs/qpdf-spec.md</a>
 * in the jqpdf repository); narrative guides per functional area live in the
 * repository's {@code docs/guides/} directory.
 */
package dev.machineminds.jqpdf;
