package dev.machineminds.jqpdf;

import static dev.machineminds.jqpdf.test.Fixtures.assumePdf;
import static dev.machineminds.jqpdf.test.Fixtures.nativeAvailable;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.nio.file.Path;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

import dev.machineminds.jqpdf.test.FakeQpdfNative;

/** Inspection options (spec §5.12). */
class InspectionOptionsTest {

    @Test
    void inspectionOptionsMapToTheirCliTokens() {
        FakeQpdfNative fake = new FakeQpdfNative();

        Jqpdf.createJob(fake)
                .input("in.pdf")
                .check()
                .showEncryption()
                .showEncryptionKey()
                .checkLinearization()
                .showLinearization()
                .showXref()
                .showObject("trailer")
                .showObject("4,0")
                .rawStreamData()
                .showNpages()
                .showPages()
                .withImages()
                .run();

        assertEquals(List.of(
                "in.pdf",
                "--check",
                "--show-encryption",
                "--show-encryption-key",
                "--check-linearization",
                "--show-linearization",
                "--show-xref",
                "--show-object=trailer",
                "--show-object=4,0",
                "--raw-stream-data",
                "--show-npages",
                "--show-pages",
                "--with-images"), fake.argvOfRun(0));
    }

    @Test
    void showNpagesReportsPageCount() {
        assumeTrue(nativeAvailable(), "native stack not available");
        Path input = assumePdf("11-pages.pdf");

        JobResult result = Jqpdf.createJob()
                .input(input.toString())
                .showNpages()
                .run()
                .orThrow();

        assertTrue(result.infoText().trim().endsWith("11"),
                "page count should be 11, got: '" + result.infoText() + "'");
    }

    @Test
    void checkReportsOnValidFileWithoutErrors() {
        assumeTrue(nativeAvailable(), "native stack not available");
        Path input = assumePdf("11-pages.pdf");

        JobResult result = Jqpdf.createJob()
                .input(input.toString())
                .check()
                .run()
                .orThrow();

        assertFalse(result.infoText().isBlank(), "check output should be captured");
    }

    @Test
    void isEncryptedDistinguishesEncryptedFiles() {
        assumeTrue(nativeAvailable(), "native stack not available");
        Path encrypted = assumePdf("20-pages.pdf");
        Path plain = assumePdf("11-pages.pdf");

        JobResult encryptedResult = Jqpdf.createJob()
                .input(encrypted.toString()).isEncrypted().run();
        JobResult plainResult = Jqpdf.createJob()
                .input(plain.toString()).isEncrypted().run();

        assertEquals(0, encryptedResult.exitCode(), "encrypted file reports 0");
        assertEquals(2, plainResult.exitCode(), "unencrypted file reports 2");
    }

    @Test
    void requiresPasswordRecognizesTheCorrectPassword() {
        assumeTrue(nativeAvailable(), "native stack not available");
        Path encrypted = assumePdf("20-pages.pdf");

        JobResult withCorrect = Jqpdf.createJob()
                .input(encrypted.toString()).password("user").requiresPassword().run();
        JobResult without = Jqpdf.createJob()
                .input(encrypted.toString()).requiresPassword().run();

        assertEquals(3, withCorrect.exitCode(), "the correct password reports 3");
        assertEquals(0, without.exitCode(), "a missing password reports 0");
    }

    @Test
    void showObjectPrintsTheTrailerDictionary() {
        assumeTrue(nativeAvailable(), "native stack not available");
        Path input = assumePdf("11-pages.pdf");

        JobResult result = Jqpdf.createJob()
                .input(input.toString())
                .showObject("trailer")
                .run()
                .orThrow();

        assertTrue(result.infoText().contains("<<") && result.infoText().contains("/Size"),
                "trailer dictionary should be printed, got: " + result.infoText());
    }

    @Test
    void hexEncryptionKeyOpensTheFileWithoutAPassword() {
        assumeTrue(nativeAvailable(), "native stack not available");
        Path encrypted = assumePdf("20-pages.pdf");

        JobResult keyInfo = Jqpdf.createJob()
                .input(encrypted.toString()).password("user")
                .showEncryptionKey().showEncryption()
                .run()
                .orThrow();

        Matcher matcher = Pattern.compile("[0-9a-f]{16,}").matcher(keyInfo.infoText());
        assertTrue(matcher.find(), "encryption key hex should be printed, got: " + keyInfo.infoText());
        String hexKey = matcher.group();

        JobResult reopened = Jqpdf.createJob()
                .input(encrypted.toString())
                .password(hexKey)
                .passwordIsHexKey()
                .showNpages()
                .run();

        assertNotEquals(2, reopened.exitCode(),
                "opening with the raw hex key must not fail; error: " + reopened.errorText());
        assertTrue(reopened.infoText().trim().endsWith("20"),
                "the file has 20 pages, got: '" + reopened.infoText() + "'");
    }

    @Test
    void rawStreamDataDeliversStreamBytesThroughTheSavePipeline() {
        assumeTrue(nativeAvailable(), "native stack not available");
        Path input = assumePdf("11-pages.pdf");

        // Object 15 is page 1's content stream in this fixture (per --show-pages).
        JobResult result = Jqpdf.createJob()
                .input(input.toString())
                .showObject("15,0")
                .rawStreamData()
                .run();

        assertNotEquals(2, result.exitCode(),
                "stream object lookup must not fail; error: " + result.errorText());
        assertTrue(result.savedData().length > 0,
                "raw stream bytes should arrive in savedData");
    }
}
