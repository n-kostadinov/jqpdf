package dev.machineminds.jqpdf;

/**
 * Thrown when a job is configured in a way that could never form a valid
 * qpdf invocation (missing input/output, contradictory builder state).
 *
 * <p>This mirrors qpdf's usage errors but is raised before the native call;
 * errors detected by qpdf itself surface as {@link JobResult} exit code 2.
 */
public class QpdfUsageException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /** Builds the exception with the usage-error message. */
    public QpdfUsageException(String message) {
        super(message);
    }
}
