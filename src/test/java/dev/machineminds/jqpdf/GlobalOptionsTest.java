package dev.machineminds.jqpdf;

import static dev.machineminds.jqpdf.test.Fixtures.assumePdf;
import static dev.machineminds.jqpdf.test.Fixtures.nativeAvailable;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import dev.machineminds.jqpdf.test.FakeQpdfNative;

/** Global limits and testing options (spec §5.14, §5.15). */
class GlobalOptionsTest {

    @Test
    void globalGroupMapsToTheirCliTokens() {
        FakeQpdfNative fake = new FakeQpdfNative();

        Jqpdf.createJob(fake)
                .input("in.pdf").output("out.pdf")
                .global(g -> g
                        .noDefaultLimits()
                        .parserMaxNesting(100)
                        .parserMaxErrors(5)
                        .parserMaxContainerSize(10000)
                        .parserMaxContainerSizeDamaged(1000)
                        .maxStreamFilters(10))
                .staticId()
                .run();

        assertEquals(List.of(
                "in.pdf",
                "--static-id",
                "--global",
                "--no-default-limits",
                "--parser-max-nesting=100",
                "--parser-max-errors=5",
                "--parser-max-container-size=10000",
                "--parser-max-container-size-damaged=1000",
                "--max-stream-filters=10",
                "--",
                "out.pdf"), fake.argvOfRun(0));
    }

    @Test
    void invalidLimitsAreRejectedBeforeTheNativeLayer() {
        FakeQpdfNative fake = new FakeQpdfNative();

        assertThrows(QpdfUsageException.class,
                () -> Jqpdf.createJob(fake).global(g -> g.parserMaxNesting(0)));
        assertThrows(QpdfUsageException.class,
                () -> Jqpdf.createJob(fake).global(g -> g.parserMaxErrors(-1)));
        assertEquals(0, fake.runCount());
    }

    @Test
    void tightDamagedContainerLimitTurnsRecoveryIntoAnError(@TempDir Path temp) {
        assumeTrue(nativeAvailable(), "native stack not available");
        Path input = assumePdf("append-page-content-damaged.pdf");

        JobResult result = Jqpdf.createJob()
                .input(input.toString())
                .output(temp.resolve("out.pdf").toString())
                .global(g -> g.parserMaxContainerSizeDamaged(1))
                .run();

        assertEquals(2, result.exitCode(),
                "a damaged-container limit of 1 must reject the repaired file");
        assertTrue(!result.errorText().isBlank(),
                "the failure should be explained in the error text");
    }

    @Test
    void testJsonSchemaValidatesJsonOutput(@TempDir Path temp) {
        assumeTrue(nativeAvailable(), "native stack not available");
        Path input = assumePdf("11-pages.pdf");

        JobResult result = Jqpdf.createJob()
                .input(input.toString()).output(temp.resolve("doc.json").toString())
                .jsonOutput()
                .testJsonSchema()
                .run();

        assertEquals(0, result.exitCode(),
                "schema validation of our own json output must pass; error: " + result.errorText());
    }
}
