package dev.machineminds.jqpdf;

import static dev.machineminds.jqpdf.test.Fixtures.assumePdf;
import static dev.machineminds.jqpdf.test.Fixtures.nativeAvailable;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * End-to-end use of the public builder against the real bundled qpdf
 * binaries.
 */
class QpdfJobRunTest {

    @Test
    void builderCopiesPdfWithRealNativeStack(@TempDir Path temp) {
        assumeTrue(nativeAvailable(), "native stack not available");
        Path input = assumePdf("11-pages.pdf");
        Path output = temp.resolve("copied.pdf");

        JobResult result = Jqpdf.createJob()
                .input(input.toString())
                .output(output.toString())
                .run()
                .orThrow();

        assertTrue(result.success());
        assertTrue(Files.isRegularFile(output), "output file must exist");
        assertTrue(output.toFile().length() > 0, "output must not be empty");
    }

    @Test
    void builderReportsNativeUsageErrorsAsExitCode2(@TempDir Path temp) {
        assumeTrue(nativeAvailable(), "native stack not available");
        Path missingInput = temp.resolve("does-not-exist.pdf");

        JobResult result = Jqpdf.createJob()
                .input(missingInput.toString())
                .output(temp.resolve("out.pdf").toString())
                .run();

        assertEquals(2, result.exitCode(), "qpdf reports a missing file as an error");
        assertTrue(result.errorText().contains("does-not-exist.pdf"),
                "error text should name the missing file, got: " + result.errorText());
    }
}
