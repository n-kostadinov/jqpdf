package dev.machineminds.jqpdf;

import static dev.machineminds.jqpdf.test.Fixtures.assumePdf;
import static dev.machineminds.jqpdf.test.Fixtures.encodeArgv;
import static dev.machineminds.jqpdf.test.Fixtures.nativeAvailable;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import dev.machineminds.jqpdf.internal.NativeLibrary;

/**
 * Verifies the per-job output capture: text streams and the binary save
 * pipeline arrive in the JobResult instead of the process console.
 *
 * <p>Note: qpdf's help options (--version and friends) make the argument
 * parser call exit(0), which would kill the hosting JVM; they are therefore
 * never routed through the native argv interface (see JqpdfInfoTest).
 */
class JobCaptureTest {

    @Test
    void capturesInfoOutputOfInspectionJob() {
        assumeTrue(nativeAvailable(), "native stack not available");
        Path input = assumePdf("11-pages.pdf");
        QpdfNative qpdf = NativeLibrary.shim();

        long job = qpdf.jobCreate();
        JobResult result;
        try {
            int init = qpdf.jobInitializeFromArgv(
                    job, encodeArgv("qpdf", "--show-npages", input.toString()), 3);
            int run = qpdf.jobRun(job);
            result = JobResult.capture(qpdf, job, init == 0 ? run : init);
        } finally {
            qpdf.jobDestroy(job);
        }

        assertEquals(0, result.exitCode());
        assertTrue(result.infoText().contains("11"),
                "page count should arrive in info text, got: '" + result.infoText() + "'");
    }

    @Test
    void capturesWarningOutputForDamagedPdf(@TempDir Path temp) {
        assumeTrue(nativeAvailable(), "native stack not available");
        Path input = assumePdf("append-page-content-damaged.pdf");
        QpdfNative qpdf = NativeLibrary.shim();

        long job = qpdf.jobCreate();
        JobResult result;
        try {
            int init = qpdf.jobInitializeFromArgv(
                    job, encodeArgv("qpdf", input.toString(), temp.resolve("out.pdf").toString()), 3);
            int run = qpdf.jobRun(job);
            result = JobResult.capture(qpdf, job, init == 0 ? run : init);
        } finally {
            qpdf.jobDestroy(job);
        }

        assertEquals(3, result.exitCode(), "a damaged-but-recoverable PDF must produce warnings only");
        assertTrue(result.warnText().contains("warning"),
                "warning text should be captured, got: '" + result.warnText() + "'");
    }
}
