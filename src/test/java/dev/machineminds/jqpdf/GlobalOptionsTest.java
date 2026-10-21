package dev.machineminds.jqpdf;

import static dev.machineminds.jqpdf.test.Fixtures.assumePdf;
import static dev.machineminds.jqpdf.test.Fixtures.nativeAvailable;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import dev.machineminds.jqpdf.options.GlobalOptions.GlobalLimits;
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

    @Test
    void noDefaultLimitsLiftOptionalLimitsButKeepExplicitOnes(@TempDir Path temp) {
        assumeTrue(nativeAvailable(), "native stack not available");
        Path input = assumePdf("append-page-content-damaged.pdf");

        JobResult lifted = Jqpdf.createJob()
                .input(input.toString())
                .global(GlobalLimits::noDefaultLimits)
                .output(temp.resolve("lifted.pdf").toString())
                .run();
        assertTrue(lifted.exitCode() == 0 || lifted.exitCode() == 3,
                "with optional limits lifted the damaged file still recovers; exit: "
                        + lifted.exitCode() + ", error: " + lifted.errorText());

        JobResult explicitWins = Jqpdf.createJob()
                .input(input.toString())
                .global(g -> g.noDefaultLimits().parserMaxContainerSizeDamaged(1))
                .output(temp.resolve("explicit.pdf").toString())
                .run();
        assertEquals(2, explicitWins.exitCode(),
                "an explicitly set limit survives --no-default-limits");
    }

    @Test
    void parserMaxErrorsLimitFlagsTheDamagedObject(@TempDir Path temp) {
        assumeTrue(nativeAvailable(), "native stack not available");
        Path input = assumePdf("global_damaged.pdf");

        JobResult result = Jqpdf.createJob()
                .input(input.toString())
                .global(g -> g.parserMaxErrors(2))
                .output(temp.resolve("out.pdf").toString())
                .run();

        assertEquals(3, result.exitCode(),
                "qpdf's own fixture overruns a 2-error limit and still recovers; exit: "
                        + result.exitCode());
        assertTrue(result.warnText().contains("parser-max-errors")
                        || result.warnText().toLowerCase().contains("file is damaged"),
                "the warnings name the exceeded limit, got: " + result.warnText());
    }

    @Test
    void parserMaxNestingLimitFlagsDeeplyNestedData(@TempDir Path temp) {
        assumeTrue(nativeAvailable(), "native stack not available");
        Path input = assumePdf("global.pdf");

        JobResult plain = Jqpdf.createJob()
                .input(input.toString())
                .output(temp.resolve("plain.pdf").toString())
                .run();
        JobResult limited = Jqpdf.createJob()
                .input(input.toString())
                .global(g -> g.parserMaxNesting(4))
                .output(temp.resolve("limited.pdf").toString())
                .run();

        assertFalse(plain.warnText().contains("parser-max-nesting"),
                "without the limit no nesting warning appears");
        assertEquals(3, limited.exitCode(),
                "a nesting limit of 4 flags the fixture's deep structure");
        assertTrue(limited.warnText().contains("parser-max-nesting"),
                "the warning names the exceeded limit, got: " + limited.warnText());
    }

    @Test
    void parserMaxContainerSizeLimitFlagsLargeContainers(@TempDir Path temp) {
        assumeTrue(nativeAvailable(), "native stack not available");
        Path input = assumePdf("global.pdf");

        JobResult result = Jqpdf.createJob()
                .input(input.toString())
                .global(g -> g.parserMaxContainerSize(3).parserMaxContainerSizeDamaged(3))
                .output(temp.resolve("out.pdf").toString())
                .run();

        assertEquals(3, result.exitCode(),
                "qpdf's own fixture trips a 3-element container limit and still recovers");
        assertTrue(result.warnText().contains("parser-max-container-size"),
                "the warning names the exceeded limit, got: " + result.warnText());
    }

    @Test
    void staticIdRepeatsTheDocumentId(@TempDir Path temp) throws Exception {
        assumeTrue(nativeAvailable(), "native stack not available");
        Path input = assumePdf("11-pages.pdf");

        // The writer preserves an existing /ID; stripping it via update-from
        // -json in the same job as --encrypt forces generation, where
        // --static-id uses its fixed test constant.
        Path trailerJson = temp.resolve("trailer.json");
        Jqpdf.createJob()
                .input(input.toString()).output(trailerJson.toString())
                .jsonOutput().jsonObject("trailer")
                .run().orThrow();
        String json = Files.readString(trailerJson, StandardCharsets.UTF_8)
                .replaceAll("\"/ID\": \\[[^\\]]*\\],?", "");
        Files.writeString(trailerJson, json, StandardCharsets.UTF_8);

        String firstStatic = encryptedIdOfStripped(temp, input, trailerJson, "static-1.pdf", true);
        String secondStatic = encryptedIdOfStripped(temp, input, trailerJson, "static-2.pdf", true);
        String random = encryptedIdOfStripped(temp, input, trailerJson, "random.pdf", false);

        assertEquals("31415926535897932384626433832795", firstStatic,
                "--static-id writes qpdf's fixed test ID");
        assertEquals(firstStatic, secondStatic, "the static ID is the same on every run");
        assertNotEquals(firstStatic, random,
                "without --static-id the generated ID is unpredictable");
    }

    @Test
    @Tag("native-crypto")
    void staticAesIvYieldsByteIdenticalEncryptedOutput(@TempDir Path temp) throws Exception {
        assumeTrue(nativeAvailable(), "native stack not available");
        Path input = assumePdf("11-pages.pdf");

        // qpdf's own reproducibility recipe (encryption.test): 128-bit AES
        // with --static-id --static-aes-iv is byte-deterministic, while the
        // default CBC initialization vector is random. The static IV lives
        // in a process-global, so the non-static runs must come first.
        Path randomIv1 = aes128Copy(temp, input, "enc-1.pdf", false);
        Path randomIv2 = aes128Copy(temp, input, "enc-2.pdf", false);
        Path first = aes128Copy(temp, input, "enc-3.pdf", true);
        Path second = aes128Copy(temp, input, "enc-4.pdf", true);

        assertFalse(java.util.Arrays.equals(
                Files.readAllBytes(randomIv1), Files.readAllBytes(randomIv2)),
                "the default CBC IV is random per run");
        assertArrayEquals(Files.readAllBytes(first), Files.readAllBytes(second),
                "static-id plus static-aes-iv make the 128-bit AES output reproducible");
        assertFalse(java.util.Arrays.equals(Files.readAllBytes(first), Files.readAllBytes(randomIv1)),
                "--static-aes-iv replaces the random IV with its fixed test IV");
    }

    @Test
    void linearizePass1WritesTheIntermediateFile(@TempDir Path temp) throws Exception {
        assumeTrue(nativeAvailable(), "native stack not available");
        Path input = assumePdf("11-pages.pdf");
        Path pass1 = temp.resolve("pass1.pdf");

        Jqpdf.createJob()
                .input(input.toString()).output(temp.resolve("out.pdf").toString())
                .linearize()
                .linearizePass1(pass1.toString())
                .run()
                .orThrow();

        assertTrue(Files.isRegularFile(pass1) && Files.size(pass1) > 0,
                "the pass-1 dump is written (documented as not a valid PDF)");
    }

    @Test
    void reportMemoryUsageAppearsInTheWarningStream(@TempDir Path temp) {
        assumeTrue(nativeAvailable(), "native stack not available");
        Path input = assumePdf("11-pages.pdf");

        JobResult result = Jqpdf.createJob()
                .input(input.toString()).output(temp.resolve("out.pdf").toString())
                .reportMemoryUsage()
                .run()
                .orThrow();

        assertTrue(result.warnText().contains("qpdf-max-memory-usage"),
                "the memory report goes to the warning stream, got: '" + result.warnText() + "'");
    }

    /** Encrypts {@code input} with its /ID stripped in the same job, then returns the generated /ID. */
    private static String encryptedIdOfStripped(Path temp, Path input, Path trailerJson,
            String name, boolean staticId) throws java.io.IOException {
        Path output = temp.resolve(name);
        QpdfJob job = Jqpdf.createJob().input(input.toString()).output(output.toString())
                .updateFromJson(trailerJson.toString());
        if (staticId) {
            job.staticId();
        }
        job.encrypt(enc -> enc.bits256().userPassword("user").ownerPassword("owner"))
                .run().orThrow();
        String trailer = Jqpdf.createJob().input(output.toString()).password("user")
                .showObject("trailer").run().orThrow().infoText();
        java.util.regex.Matcher id = java.util.regex.Pattern
                .compile("/ID \\[ <([0-9a-f]+)>").matcher(trailer);
        assertTrue(id.find(), "the encrypted trailer carries a generated /ID, got: " + trailer);
        return id.group(1);
    }

    private static Path aes128Copy(Path temp, Path input, String name, boolean staticIv) {
        Path output = temp.resolve(name);
        QpdfJob job = Jqpdf.createJob()
                .input(input.toString()).output(output.toString())
                .staticId();
        if (staticIv) {
            job.staticAesIv();
        }
        job.encrypt(enc -> enc.bits128().useAes(true)
                        .userPassword("user").ownerPassword("owner"))
                .run()
                .orThrow();
        return output;
    }
}
