package dev.machineminds.jqpdf.internal;

import java.util.LinkedHashMap;
import java.util.Map;

import dev.machineminds.jqpdf.QpdfUsageException;
import dev.machineminds.jqpdf.options.GlobalOptions.GlobalLimits;

/**
 * Collects the {@code --global} limits of one job. qpdf stores these limits
 * in process-wide statics, so the job builder pins every limit that has ever
 * been customized in this process back to a known value on each run — see
 * {@code QpdfJobImpl#appendGlobalLimits}.
 */
public final class GlobalLimitsImpl implements GlobalLimits {

    public static final Map<String, String> QPDF_DEFAULTS = Map.of(
            "--parser-max-nesting", "499",
            "--parser-max-errors", "15",
            "--parser-max-container-size", "4294967295",
            "--parser-max-container-size-damaged", "5000",
            "--max-stream-filters", "25");

    /** Insertion-ordered flag → value map of the limits this job sets. */
    public final Map<String, String> values = new LinkedHashMap<>();

    public boolean noDefaultLimits;

    @Override
    public GlobalLimits noDefaultLimits() {
        noDefaultLimits = true;
        return this;
    }

    @Override
    public GlobalLimits parserMaxNesting(int level) {
        return set("--parser-max-nesting", positive(level, "parser max nesting"));
    }

    @Override
    public GlobalLimits parserMaxErrors(int count) {
        return set("--parser-max-errors", nonNegative(count, "parser max errors"));
    }

    @Override
    public GlobalLimits parserMaxContainerSize(long size) {
        return set("--parser-max-container-size", nonNegative(size, "parser max container size"));
    }

    @Override
    public GlobalLimits parserMaxContainerSizeDamaged(long size) {
        return set("--parser-max-container-size-damaged",
                nonNegative(size, "parser max container size (damaged)"));
    }

    @Override
    public GlobalLimits maxStreamFilters(int count) {
        return set("--max-stream-filters", positive(count, "max stream filters"));
    }

    private GlobalLimits set(String flag, String value) {
        values.put(flag, value);
        return this;
    }

    private static String positive(long value, String what) {
        if (value < 1) {
            throw new QpdfUsageException(what + " must be positive, got " + value);
        }
        return Long.toString(value);
    }

    private static String nonNegative(long value, String what) {
        if (value < 0) {
            throw new QpdfUsageException(what + " must not be negative, got " + value);
        }
        return Long.toString(value);
    }
}
