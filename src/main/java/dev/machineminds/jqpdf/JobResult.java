package dev.machineminds.jqpdf;

import java.nio.charset.StandardCharsets;

/**
 * Outcome of a qpdf job: the CLI exit code plus everything the job printed.
 *
 * <p>Exit codes follow the qpdf CLI: {@code 0} success, {@code 2} errors
 * (nothing processed), {@code 3} warnings only. Text streams are qpdf's
 * info (stdout), warning, and error output; binary output that the CLI
 * would write through its save pipeline arrives in {@link #savedData()}.
 */
public final class JobResult {

    private final int exitCode;
    private final String infoText;
    private final String warnText;
    private final String errorText;
    private final byte[] savedData;

    JobResult(int exitCode, String infoText, String warnText, String errorText, byte[] savedData) {
        this.exitCode = exitCode;
        this.infoText = infoText;
        this.warnText = warnText;
        this.errorText = errorText;
        this.savedData = savedData;
    }

    /**
     * Drains a finished job's capture buffers into a result. For
     * {@link QpdfNative} adapter implementations; library callers get their
     * results from {@link QpdfJob#run()}.
     */
    public static JobResult capture(QpdfNative qpdf, long job, int exitCode) {
        return new JobResult(
                exitCode,
                utf8(qpdf.jobGetInfoText(job)),
                utf8(qpdf.jobGetWarnText(job)),
                utf8(qpdf.jobGetErrorText(job)),
                qpdf.jobGetSavedData(job));
    }

    private static String utf8(byte[] bytes) {
        return bytes == null ? "" : new String(bytes, StandardCharsets.UTF_8);
    }

    /** The qpdf CLI exit code: 0 success, 2 error, 3 warnings. */
    public int exitCode() {
        return exitCode;
    }

    /** True when the job completed without errors or warnings. */
    public boolean success() {
        return exitCode == 0;
    }

    /** True when the job completed with warnings but no errors (exit 3). */
    public boolean hasWarnings() {
        return exitCode == 3;
    }

    /**
     * Returns this result, or throws if the job failed with errors.
     *
     * @throws QpdfJobException when the job failed with errors (exit code 2);
     *         warnings (exit code 3) do not throw
     */
    public JobResult orThrow() {
        if (exitCode == 2) {
            throw new QpdfJobException(this);
        }
        return this;
    }

    /** qpdf's informational output (what the CLI prints to stdout). */
    public String infoText() {
        return infoText;
    }

    /** qpdf's warning output (what the CLI prints to stderr as warnings). */
    public String warnText() {
        return warnText;
    }

    /** qpdf's error output (what the CLI prints to stderr as errors). */
    public String errorText() {
        return errorText;
    }

    /** Binary output routed through qpdf's save pipeline; empty when unused. */
    public byte[] savedData() {
        return savedData;
    }

    /**
     * The saved pipeline's output decoded as UTF-8 — the string form of JSON
     * output written with {@code json()} and no output file (spec §9.1);
     * empty when unused.
     */
    public String savedText() {
        return savedData == null ? "" : new String(savedData, StandardCharsets.UTF_8);
    }

    @Override
    public String toString() {
        return "JobResult[exitCode=" + exitCode + "]";
    }
}
