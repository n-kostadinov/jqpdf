package dev.machineminds.jqpdf;

import static dev.machineminds.jqpdf.test.Fixtures.assumePdf;
import static dev.machineminds.jqpdf.test.Fixtures.nativeAvailable;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

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
}
