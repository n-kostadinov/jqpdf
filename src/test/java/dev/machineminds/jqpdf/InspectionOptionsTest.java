package dev.machineminds.jqpdf;

import static dev.machineminds.jqpdf.test.Fixtures.assumePdf;
import static dev.machineminds.jqpdf.test.Fixtures.nativeAvailable;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

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
    @Tag("native-crypto")
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
    @Tag("native-crypto")
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
    @Tag("native-crypto")
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

    @Test
    void filteredStreamDataDecodesTheContentStream() {
        assumeTrue(nativeAvailable(), "native stack not available");
        Path input = assumePdf("11-pages.pdf");

        JobResult raw = Jqpdf.createJob()
                .input(input.toString())
                .showObject("15,0").rawStreamData()
                .run().orThrow();
        JobResult filtered = Jqpdf.createJob()
                .input(input.toString())
                .showObject("15,0").filteredStreamData()
                .run().orThrow();

        String decoded = new String(filtered.savedData(), StandardCharsets.ISO_8859_1);
        assertTrue(filtered.savedData().length != raw.savedData().length,
                "the flate-compressed stream decodes to different bytes");
        assertTrue(decoded.contains("BT") || decoded.contains("q"),
                "the decoded content is readable drawing operators, got: " + decoded);
    }

    @Test
    void checkLinearizationDistinguishesLinearizedFiles(@TempDir Path temp) {
        assumeTrue(nativeAvailable(), "native stack not available");
        Path input = assumePdf("11-pages.pdf");
        Path linearized = temp.resolve("linearized.pdf");
        Jqpdf.createJob()
                .input(input.toString()).output(linearized.toString())
                .linearize()
                .run().orThrow();

        JobResult linear = Jqpdf.createJob()
                .input(linearized.toString()).checkLinearization().run();
        JobResult plain = Jqpdf.createJob()
                .input(input.toString()).checkLinearization().run();

        assertEquals(0, linear.exitCode(),
                "the hint tables of our own linearized output check out; info: "
                        + linear.infoText() + linear.warnText());
        assertTrue(linear.infoText().contains("no linearization errors"),
                "a checked linearized file reports success, got: " + linear.infoText());
        assertEquals(0, plain.exitCode(), "a non-linearized file is not an error");
        assertTrue(plain.infoText().contains("is not linearized"),
                "the plain file is reported as not linearized, got: " + plain.infoText());
    }

    @Test
    void showLinearizationDumpsTheHintTables(@TempDir Path temp) {
        assumeTrue(nativeAvailable(), "native stack not available");
        Path input = assumePdf("11-pages.pdf");
        Path linearized = temp.resolve("linearized.pdf");
        Jqpdf.createJob()
                .input(input.toString()).output(linearized.toString())
                .linearize()
                .run().orThrow();

        JobResult result = Jqpdf.createJob()
                .input(linearized.toString()).showLinearization()
                .run().orThrow();

        String lower = result.infoText().toLowerCase();
        assertTrue(lower.contains("page") && lower.contains("offset"),
                "the hint table dump lists pages and offsets, got: " + result.infoText());
    }

    @Test
    void showXrefDumpsTheCrossReferenceTable() {
        assumeTrue(nativeAvailable(), "native stack not available");
        Path input = assumePdf("11-pages.pdf");

        JobResult result = Jqpdf.createJob()
                .input(input.toString()).showXref()
                .run().orThrow();

        String info = result.infoText();
        assertTrue(info.contains("offset") && info.contains("uncompressed"),
                "the dump lists per-object offsets and storage, got: " + info);
        assertTrue(info.lines().count() >= 26,
                "every object of the fixture gets a line, got " + info.lines().count());
    }

    @Test
    void showPagesListsEveryPagesContent() {
        assumeTrue(nativeAvailable(), "native stack not available");
        Path input = assumePdf("11-pages.pdf");

        JobResult npages = Jqpdf.createJob().input(input.toString()).showNpages()
                .run().orThrow();
        JobResult pages = Jqpdf.createJob().input(input.toString()).showPages()
                .run().orThrow();

        int listed = (int) Pattern.compile("(?m)^page \\d+:")
                .matcher(pages.infoText()).results().count();
        assertEquals(Integer.parseInt(npages.infoText().trim()), listed,
                "--show-pages lists one block per page");
        assertTrue(pages.infoText().contains("content:"),
                "each block lists the page's content streams");
    }

    @Test
    void withImagesAddsImageEntriesToThePageBlocks() {
        assumeTrue(nativeAvailable(), "native stack not available");
        Path input = assumePdf("image-streams.pdf");

        JobResult plain = Jqpdf.createJob().input(input.toString()).showPages()
                .run().orThrow();
        JobResult withImages = Jqpdf.createJob().input(input.toString())
                .showPages().withImages()
                .run().orThrow();

        assertFalse(plain.infoText().contains("images:"),
                "image entries appear only on request");
        assertTrue(withImages.infoText().contains("images:"),
                "--with-images lists the page's images, got: " + withImages.infoText());
    }
}
