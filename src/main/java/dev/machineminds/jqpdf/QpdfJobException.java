package dev.machineminds.jqpdf;

/**
 * Thrown by {@link JobResult#orThrow()} when a job failed with qpdf errors
 * (exit code 2). Carries the full result, including qpdf's error output.
 */
public class QpdfJobException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final JobResult result;

    public QpdfJobException(JobResult result) {
        super("qpdf job failed with exit code " + result.exitCode()
                + (result.errorText().isBlank() ? "" : ":" + System.lineSeparator() + result.errorText()));
        this.result = result;
    }

    /** The failed job's complete result. */
    public JobResult result() {
        return result;
    }
}
