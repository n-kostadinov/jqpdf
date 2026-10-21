package dev.machineminds.jqpdf;

import static dev.machineminds.jqpdf.test.Fixtures.assumePdf;
import static dev.machineminds.jqpdf.test.Fixtures.nativeAvailable;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

/**
 * Real-native round trips of the in-memory API (spec §9.1): byte[]/stream
 * input through {@code outputMemory()}, the JSON string round trip, and
 * attachment payloads — with no caller-managed files anywhere.
 */
class MemoryIoRoundTripTest {

    @Test
    void inputBytesRoundTripThroughOutputMemory() throws Exception {
        assumeTrue(nativeAvailable(), "native stack not available");
        Path fixture = assumePdf("11-pages.pdf");
        byte[] pdf = Files.readAllBytes(fixture);

        JobResult written = Jqpdf.createJob().input(pdf).outputMemory().run().orThrow();

        byte[] out = written.savedData();
        assertTrue(out.length > 5 && new String(out, 0, 5, StandardCharsets.US_ASCII).equals("%PDF-"),
                "rebuilt PDF should start with %PDF-");
        assertEquals(pageCount(fixture.toString()), pageCount(out));
    }

    @Test
    void inputStreamRoundTripsLikeBytes() throws Exception {
        assumeTrue(nativeAvailable(), "native stack not available");
        Path fixture = assumePdf("11-pages.pdf");
        byte[] pdf = Files.readAllBytes(fixture);

        JobResult written = Jqpdf.createJob()
                .input(new ByteArrayInputStream(pdf))
                .outputMemory()
                .run()
                .orThrow();

        assertEquals(pageCount(pdf), pageCount(written.savedData()));
    }

    @Test
    void jsonOutputFedBackThroughJsonInputContentRebuildsThePdf() {
        assumeTrue(nativeAvailable(), "native stack not available");
        Path fixture = assumePdf("11-pages.pdf");

        String json = Jqpdf.createJob()
                .input(fixture.toString())
                .jsonOutput()
                .run()
                .orThrow()
                .savedText();

        JobResult rebuilt = Jqpdf.createJob().jsonInputContent(json).outputMemory().run().orThrow();

        assertEquals(pageCount(fixture.toString()), pageCount(rebuilt.savedData()));
    }

    @Test
    void updateFromJsonContentAppliesAnObjectUpdate() {
        assumeTrue(nativeAvailable(), "native stack not available");
        Path fixture = assumePdf("11-pages.pdf");

        String json = Jqpdf.createJob()
                .input(fixture.toString())
                .jsonOutput()
                .run()
                .orThrow()
                .savedText();
        assertTrue(json.contains("/Producer"), "fixture JSON should contain a /Producer to update");
        String updated = json.replaceAll("(/Producer\"\\s*:\\s*\"u:)[^\"]*", "$1jqpdf-update-test");

        JobResult applied = Jqpdf.createJob()
                .input(fixture.toString())
                .updateFromJsonContent(updated)
                .outputMemory()
                .run()
                .orThrow();

        String reserialized = Jqpdf.createJob()
                .input(applied.savedData())
                .jsonOutput()
                .run()
                .orThrow()
                .savedText();
        assertTrue(reserialized.contains("u:jqpdf-update-test"),
                "updated /Producer should survive the round trip");
        assertEquals(pageCount(fixture.toString()), pageCount(applied.savedData()));
    }

    @Test
    void attachmentContentSurvivesAddAndShowRoundTrip() throws Exception {
        assumeTrue(nativeAvailable(), "native stack not available");
        byte[] pdf = Files.readAllBytes(assumePdf("11-pages.pdf"));
        byte[] payload = {9, 8, 7, 6};

        JobResult withAttachment = Jqpdf.createJob()
                .input(pdf)
                .addAttachment(a -> a.content(payload).key("blob.bin").description("in-memory attachment"))
                .outputMemory()
                .run()
                .orThrow();

        JobResult shown = Jqpdf.createJob()
                .input(withAttachment.savedData())
                .showAttachment("blob.bin")
                .run()
                .orThrow();

        assertArrayEquals(payload, shown.savedData());
    }

    private static int pageCount(String file) {
        return Integer.parseInt(Jqpdf.createJob().input(file).showNpages().run().orThrow().infoText().trim());
    }

    private static int pageCount(byte[] pdf) {
        return Integer.parseInt(Jqpdf.createJob().input(pdf).showNpages().run().orThrow().infoText().trim());
    }
}
