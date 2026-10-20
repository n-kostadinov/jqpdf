package dev.machineminds.jqpdf;

/**
 * Port to the native qpdf job C API. The default adapter is the JNI shim
 * loaded from the bundled binaries; tests can substitute a fake to exercise
 * job logic without any native library.
 *
 * <p>Handles are opaque {@code long} values owned by the adapter. The
 * contract mirrors {@code qpdfjob-c.h}: a non-zero return from
 * {@link #jobInitializeFromArgv(long, byte[], int)} or
 * {@link #jobInitializeFromJson(long, byte[])} invalidates the job handle
 * for everything except {@link #jobDestroy(long)}; return values are the
 * qpdf CLI exit codes (0 success, 2 error, 3 warnings).
 *
 * <p>String arguments are real UTF-8 ({@code String.getBytes(UTF_8)}); argv
 * travels as one blob of NUL-terminated strings in argument order.
 */
public interface QpdfNative {

    /** Marshalling contract version; must match the shim's JQPDF_JNI_ABI. */
    int EXPECTED_ABI = 1;

    /** Returns the shim's marshalling contract version. */
    int nativeAbiVersion();

    /** Creates a native job and returns its opaque handle. */
    long jobCreate();

    /** Releases the job and all associated native state; handle is dead afterwards. */
    void jobDestroy(long job);

    /**
     * Configures the job from argv ({@code argvUtf8} is {@code argc}
     * NUL-terminated UTF-8 strings, {@code argv[0]} an ignored program
     * name). Returns the CLI exit code; non-zero invalidates the job.
     */
    int jobInitializeFromArgv(long job, byte[] argvUtf8, int argc);

    /**
     * Configures the job from QPDFJob JSON (UTF-8). Returns the CLI exit
     * code; non-zero invalidates the job.
     */
    int jobInitializeFromJson(long job, byte[] jsonUtf8);

    /** Runs the configured job and returns the CLI exit code. */
    int jobRun(long job);

    /** The job's info-stream output (qpdf's stdout) as UTF-8 bytes. */
    byte[] jobGetInfoText(long job);

    /** The job's warning-stream output as UTF-8 bytes. */
    byte[] jobGetWarnText(long job);

    /** The job's error-stream output as UTF-8 bytes. */
    byte[] jobGetErrorText(long job);

    /** Binary output routed to the save pipeline (e.g. --show-attachment). */
    byte[] jobGetSavedData(long job);

    /**
     * Whether this adapter shares qpdf's process-global state. qpdf keeps its
     * {@code --global} parser limits in process-wide statics; the default
     * {@code true} makes the job builder pin those limits per run so jobs
     * stay independent. Fakes with no native state return {@code false} and
     * see only their explicitly configured options in argv.
     */
    default boolean sharesProcessGlobalState() {
        return true;
    }
}
