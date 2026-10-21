package dev.machineminds.jqpdf;

import static dev.machineminds.jqpdf.test.Fixtures.assumePdf;
import static dev.machineminds.jqpdf.test.Fixtures.nativeAvailable;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Jobs are independent qpdf objects: several jobs must run in parallel on
 * separate threads without coordination, each producing valid output.
 */
class QpdfConcurrencyTest {

    private static final int THREADS = 8;

    @Test
    void parallelJobsOnSeparateThreadsAllSucceed(@TempDir Path temp) throws Exception {
        assumeTrue(nativeAvailable(), "native stack not available");
        Path input = assumePdf("11-pages.pdf");

        ExecutorService pool = Executors.newFixedThreadPool(THREADS);
        List<Future<JobResult>> futures = new ArrayList<>();
        try {
            for (int i = 0; i < THREADS; i++) {
                Path output = temp.resolve("out-" + i + ".pdf");
                int angle = 90 * (i % 4);
                futures.add(pool.submit(() -> Jqpdf.createJob()
                        .input(input.toString())
                        .output(output.toString())
                        .rotate("+" + angle)
                        .run()
                        .orThrow()));
            }
            for (Future<JobResult> future : futures) {
                assertTrue(future.get().success(), "every parallel job must succeed");
            }
        } finally {
            pool.shutdownNow();
        }

        for (int i = 0; i < THREADS; i++) {
            Path output = temp.resolve("out-" + i + ".pdf");
            assertTrue(Files.isRegularFile(output) && output.toFile().length() > 0,
                    "output " + i + " must exist and be non-empty");
        }
    }
}
