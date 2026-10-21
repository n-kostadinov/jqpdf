package dev.machineminds.jqpdf;

import static dev.machineminds.jqpdf.test.Fixtures.assumePdf;
import static dev.machineminds.jqpdf.test.Fixtures.nativeAvailable;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import dev.machineminds.jqpdf.test.FakeQpdfNative;

/** Basic invocation and general options (spec §5.1, §5.2, §5.5). */
class GeneralOptionsTest {

    @Test
    void emptyInputReplacesTheInputSlot() {
        FakeQpdfNative fake = new FakeQpdfNative();

        Jqpdf.createJob(fake).emptyInput().output("out.pdf").run();

        assertEquals(List.of("--empty", "out.pdf"), fake.argvOfRun(0));
    }

    @Test
    void replaceInputOccupiesTheOutputSlot() {
        FakeQpdfNative fake = new FakeQpdfNative();

        Jqpdf.createJob(fake).input("in.pdf").output("out.pdf").replaceInput().run();

        assertEquals(List.of("in.pdf", "--replace-input"), fake.argvOfRun(0));
    }

    @Test
    void laterCallDecidesBetweenOutputAndReplaceInput() {
        FakeQpdfNative fake = new FakeQpdfNative();

        Jqpdf.createJob(fake).input("in.pdf").replaceInput().output("out.pdf").run();

        assertEquals(List.of("in.pdf", "out.pdf"), fake.argvOfRun(0));
    }

    @Test
    void generalOptionsMapToTheirCliTokens() {
        FakeQpdfNative fake = new FakeQpdfNative();

        Jqpdf.createJob(fake)
                .input("in.pdf").output("out.pdf")
                .password("s3cret")
                .passwordFile("pw.txt")
                .jobJsonFile("job.json")
                .warningExit0()
                .progress()
                .noWarn()
                .deterministicId()
                .allowWeakCrypto()
                .keepFilesOpen(false)
                .keepFilesOpenThreshold(50)
                .run();

        assertEquals(List.of(
                "in.pdf",
                "--password=s3cret",
                "--password-file=pw.txt",
                "--job-json-file=job.json",
                "--warning-exit-0",
                "--progress",
                "--no-warn",
                "--deterministic-id",
                "--allow-weak-crypto",
                "--keep-files-open=n",
                "--keep-files-open-threshold=50",
                "out.pdf"), fake.argvOfRun(0));
    }

    @Test
    void replaceInputOverwritesTheFile(@TempDir Path temp) throws Exception {
        assumeTrue(nativeAvailable(), "native stack not available");
        Path work = Files.copy(assumePdf("11-pages.pdf"), temp.resolve("work.pdf"));

        JobResult result = Jqpdf.createJob()
                .input(work.toString())
                .replaceInput()
                .run()
                .orThrow();

        assertTrue(result.success());
        assertTrue(Files.isRegularFile(work), "input file must still exist after replacement");
        assertTrue(Files.size(work) > 0);
    }

    @Test
    void passwordOpensEncryptedFixture(@TempDir Path temp) {
        assumeTrue(nativeAvailable(), "native stack not available");
        Path input = assumePdf("20-pages.pdf");

        JobResult result = Jqpdf.createJob()
                .input(input.toString())
                .password("user")
                .output(temp.resolve("decrypted-copy.pdf").toString())
                .run();

        assertEquals(0, result.exitCode(),
                "20-pages.pdf opens with user password 'user'; error: " + result.errorText());
    }

    @Test
    void passwordFileOpensEncryptedFixture(@TempDir Path temp) throws Exception {
        assumeTrue(nativeAvailable(), "native stack not available");
        Path input = assumePdf("20-pages.pdf");
        Path passwordFile = temp.resolve("pw.txt");
        Files.writeString(passwordFile, "user\n");

        JobResult result = Jqpdf.createJob()
                .input(input.toString())
                .passwordFile(passwordFile.toString())
                .output(temp.resolve("out.pdf").toString())
                .run();

        assertEquals(0, result.exitCode(),
                "the file's first line is the password; error: " + result.errorText());
    }

    @Test
    void verboseReportsTheWrittenFile(@TempDir Path temp) {
        assumeTrue(nativeAvailable(), "native stack not available");
        Path input = assumePdf("11-pages.pdf");

        JobResult plain = Jqpdf.createJob()
                .input(input.toString()).output(temp.resolve("plain.pdf").toString())
                .run().orThrow();
        JobResult verbose = Jqpdf.createJob()
                .input(input.toString()).output(temp.resolve("verbose.pdf").toString())
                .verbose().run().orThrow();

        assertEquals("", plain.infoText(), "a plain copy prints nothing");
        assertTrue(verbose.infoText().contains("wrote file"),
                "verbose names the written file, got: '" + verbose.infoText() + "'");
    }

    @Test
    void progressReportsWritePercentageInInfoText(@TempDir Path temp) {
        assumeTrue(nativeAvailable(), "native stack not available");
        Path input = assumePdf("11-pages.pdf");

        JobResult result = Jqpdf.createJob()
                .input(input.toString())
                .output(temp.resolve("out.pdf").toString())
                .progress()
                .run()
                .orThrow();

        assertTrue(result.infoText().contains("write progress") && result.infoText().contains("100%"),
                "progress goes to the info stream and reaches 100%, got: '" + result.infoText() + "'");
    }

    @Test
    void noWarnSuppressesWarningTextButKeepsExitCode(@TempDir Path temp) {
        assumeTrue(nativeAvailable(), "native stack not available");
        Path input = assumePdf("append-page-content-damaged.pdf");

        JobResult result = Jqpdf.createJob()
                .input(input.toString())
                .noWarn()
                .output(temp.resolve("out.pdf").toString())
                .run();

        assertEquals(3, result.exitCode(), "warnings still surface through the exit code");
        assertEquals("", result.warnText(), "--no-warn suppresses the warning output");
    }

    @Test
    void warningExit0TurnsWarningsIntoSuccess(@TempDir Path temp) {
        assumeTrue(nativeAvailable(), "native stack not available");
        Path input = assumePdf("append-page-content-damaged.pdf");

        JobResult result = Jqpdf.createJob()
                .input(input.toString())
                .warningExit0()
                .output(temp.resolve("out.pdf").toString())
                .run();

        assertEquals(0, result.exitCode(), "warnings-only plus --warning-exit-0 exits 0, not 3");
    }

    @Test
    void deterministicIdConvergesToByteIdenticalOutput(@TempDir Path temp) throws Exception {
        assumeTrue(nativeAvailable(), "native stack not available");
        Path source = assumePdf("11-pages.pdf");

        // Spec §10.4: run a->1, 1->2, 2->3 with --deterministic-id; the last
        // two outputs are byte-identical.
        Path previous = source;
        Path secondToLast = null;
        Path last = null;
        for (int run = 1; run <= 3; run++) {
            Path out = temp.resolve("det-" + run + ".pdf");
            Jqpdf.createJob()
                    .input(previous.toString())
                    .deterministicId()
                    .output(out.toString())
                    .run()
                    .orThrow();
            secondToLast = last;
            last = out;
            previous = out;
        }

        assertArrayEquals(Files.readAllBytes(secondToLast), Files.readAllBytes(last),
                "runs 2 and 3 of the three-step process must be byte-identical");
    }

    @Test
    void jobJsonFileMergesWithBuilderOptions(@TempDir Path temp) throws Exception {
        assumeTrue(nativeAvailable(), "native stack not available");
        Path input = assumePdf("11-pages.pdf");
        Path jobJson = temp.resolve("job.json");
        Files.writeString(jobJson, "{\"minVersion\": \"1.7\"}");

        Path output = temp.resolve("merged-config.pdf");
        Jqpdf.createJob()
                .input(input.toString())
                .jobJsonFile(jobJson.toString())
                .output(output.toString())
                .run()
                .orThrow();

        String header = new String(Files.readAllBytes(output), 0, 16, StandardCharsets.ISO_8859_1);
        assertTrue(header.startsWith("%PDF-1.7"),
                "the JSON file's minVersion applies to the builder's job, got: " + header.trim());
    }

    @Test
    void runFromJsonCopiesPdfEndToEnd(@TempDir Path temp) throws Exception {
        assumeTrue(nativeAvailable(), "native stack not available");
        Path input = assumePdf("11-pages.pdf");
        Path output = temp.resolve("copied.pdf");
        String jobJson = "{\"inputFile\": " + jsonString(input) + ","
                + " \"outputFile\": " + jsonString(output) + "}";

        JobResult result = Jqpdf.createJob().runFromJson(jobJson);

        assertEquals(0, result.exitCode(), "job JSON copy must succeed; error: " + result.errorText());
        String header = new String(Files.readAllBytes(output), 0, 8, StandardCharsets.ISO_8859_1);
        assertTrue(header.startsWith("%PDF-"), "a PDF must be written, got: " + header);
    }

    @Test
    void stdoutOutputDeliversPdfBytesThroughTheSavePipeline() {
        assumeTrue(nativeAvailable(), "native stack not available");
        Path input = assumePdf("11-pages.pdf");

        JobResult result = Jqpdf.createJob()
                .input(input.toString())
                .output("-")
                .run()
                .orThrow();

        assertTrue(result.savedData().length > 5, "PDF bytes must arrive in savedData");
        assertEquals("%PDF-", new String(result.savedData(), 0, 5, StandardCharsets.US_ASCII),
                "the save pipeline carries the output file written to '-'");
    }

    @Test
    void keepFilesOpenVariantsCompleteMultiFileMerge(@TempDir Path temp) {
        assumeTrue(nativeAvailable(), "native stack not available");
        Path input = assumePdf("11-pages.pdf");

        // The option has no output-visible effect; both settings must complete
        // a real multi-file merge (docs/coverage.md records the justification).
        JobResult keepingOpen = Jqpdf.createJob()
                .input(input.toString())
                .keepFilesOpen(true)
                .output(temp.resolve("keep.pdf").toString())
                .pages(p -> p.file(".").range("1-2").file(input.toString()).range("3"))
                .run();
        JobResult reopening = Jqpdf.createJob()
                .input(input.toString())
                .keepFilesOpen(false)
                .keepFilesOpenThreshold(1)
                .output(temp.resolve("reopen.pdf").toString())
                .pages(p -> p.file(".").range("1-2").file(input.toString()).range("3"))
                .run();

        assertEquals(0, keepingOpen.exitCode(),
                "merge with keep-files-open=y must succeed; error: " + keepingOpen.errorText());
        assertEquals(0, reopening.exitCode(),
                "merge with keep-files-open=n and threshold 1 must succeed; error: "
                        + reopening.errorText());
    }

    /** A minimal JSON string literal for a Windows/Unix path. */
    private static String jsonString(Path path) {
        return "\"" + path.toString().replace("\\", "\\\\") + "\"";
    }
}
