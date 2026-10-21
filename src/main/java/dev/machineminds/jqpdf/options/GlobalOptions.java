package dev.machineminds.jqpdf.options;

import java.util.function.Consumer;

import dev.machineminds.jqpdf.QpdfJob;

/**
 * Global limits and testing/debugging options (manual sections "Global
 * Options" and "Options for Testing or Debugging", spec §5.14, §5.15).
 */
public interface GlobalOptions {

    /** {@code --global …​ --}: configure parser limits for this job. */
    QpdfJob global(Consumer<GlobalLimits> limits);

    /** One {@code --global …​ --} group. */
    interface GlobalLimits {

        /** Disable all optional default limits (nesting limit stays). */
        GlobalLimits noDefaultLimits();

        /** Max parser nesting level; default 499; cannot be disabled. */
        GlobalLimits parserMaxNesting(int level);

        /** Max errors parsing one indirect object; 0 = unlimited; default 15. */
        GlobalLimits parserMaxErrors(int count);

        /** Max top-level objects in an undamaged container; default 2^32-1. */
        GlobalLimits parserMaxContainerSize(long size);

        /** Same, for damaged xref/objects; default 5000. */
        GlobalLimits parserMaxContainerSizeDamaged(long size);

        /** Max filters on one stream; default 25. */
        GlobalLimits maxStreamFilters(int count);
    }

    /** {@code --static-id}: fixed document /ID (testing only). */
    QpdfJob staticId();

    /** {@code --static-aes-iv}: static AES IV (testing only; weakens crypto). */
    QpdfJob staticAesIv();

    /** {@code --linearize-pass1=file}: dump linearization pass 1 (not a valid PDF). */
    QpdfJob linearizePass1(String file);

    /** {@code --test-json-schema}: validate --json output against the schema. */
    QpdfJob testJsonSchema();

    /** {@code --report-memory-usage}: best-effort peak memory report. */
    QpdfJob reportMemoryUsage();
}
