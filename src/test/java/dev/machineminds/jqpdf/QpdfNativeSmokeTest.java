package dev.machineminds.jqpdf;

import static dev.machineminds.jqpdf.test.Fixtures.assumePdf;
import static dev.machineminds.jqpdf.test.Fixtures.encodeArgv;
import static dev.machineminds.jqpdf.test.Fixtures.nativeAvailable;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import dev.machineminds.jqpdf.internal.NativeLibrary;

/**
 * End-to-end smoke test of the JNI boundary: a job configured from argv
 * copies a real fixture PDF through the bundled native qpdf.
 */
class QpdfNativeSmokeTest {

    @Test
    void copiesFixturePdfThroughNativeJob(@TempDir Path temp) throws Exception {
        assumeTrue(nativeAvailable(),
                "native binaries or JNI shim not available; run scripts/fetch-qpdf-binaries and rebuild");
        Path input = assumePdf("11-pages.pdf");
        Path output = temp.resolve("copied.pdf");

        QpdfNative qpdf = NativeLibrary.shim();
        long job = qpdf.jobCreate();
        try {
            assertEquals(0, qpdf.jobInitializeFromArgv(
                    job, encodeArgv("qpdf", input.toString(), output.toString()), 3),
                    "initializing from a valid in/out argv must succeed");
            assertEquals(0, qpdf.jobRun(job), "copying a valid PDF must produce no errors or warnings");
        } finally {
            qpdf.jobDestroy(job);
        }

        assertTrue(Files.isRegularFile(output), "output file must exist");
        assertTrue(filesStartWith(output, "%PDF"), "output must be a PDF");
    }

    private boolean filesStartWith(Path file, String prefix) throws Exception {
        byte[] head = new byte[prefix.length()];
        try (var in = Files.newInputStream(file)) {
            int read = 0;
            while (read < head.length) {
                int n = in.read(head, read, head.length - read);
                if (n < 0) {
                    return false;
                }
                read += n;
            }
        }
        return new String(head, java.nio.charset.StandardCharsets.US_ASCII).equals(prefix);
    }
}
