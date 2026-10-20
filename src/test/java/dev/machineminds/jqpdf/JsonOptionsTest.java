package dev.machineminds.jqpdf;

import static dev.machineminds.jqpdf.test.Fixtures.assumePdf;
import static dev.machineminds.jqpdf.test.Fixtures.nativeAvailable;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import dev.machineminds.jqpdf.options.JsonOptions.JsonStreamData;
import dev.machineminds.jqpdf.test.FakeQpdfNative;

/** JSON options (spec §5.13, §6). */
class JsonOptionsTest {

    @Test
    void jsonOptionsMapToTheirCliTokens() {
        FakeQpdfNative fake = new FakeQpdfNative();

        Jqpdf.createJob(fake)
                .input("in.pdf")
                .json(2)
                .jsonKey("pages")
                .jsonKey("qpdf")
                .jsonObject("trailer")
                .jsonStreamData(JsonStreamData.INLINE)
                .jsonStreamPrefix("streams-")
                .run();

        assertEquals(List.of(
                "in.pdf",
                "--json=2",
                "--json-key=pages",
                "--json-key=qpdf",
                "--json-object=trailer",
                "--json-stream-data=inline",
                "--json-stream-prefix=streams-"), fake.argvOfRun(0));
    }

    @Test
    void invalidJsonVersionIsRejectedBeforeTheNativeLayer() {
        FakeQpdfNative fake = new FakeQpdfNative();

        assertThrows(QpdfUsageException.class, () -> Jqpdf.createJob(fake).json(3));
        assertEquals(0, fake.runCount());
    }

    @Test
    void jsonRoundTripThroughOutputAndInput(@TempDir Path temp) throws Exception {
        assumeTrue(nativeAvailable(), "native stack not available");
        Path input = assumePdf("11-pages.pdf");
        Path jsonFile = temp.resolve("doc.json");
        Path rebuilt = temp.resolve("rebuilt.pdf");

        Jqpdf.createJob()
                .input(input.toString()).output(jsonFile.toString())
                .jsonOutput()
                .run()
                .orThrow();

        String json = Files.readString(jsonFile, StandardCharsets.UTF_8);
        assertTrue(json.contains("\"jsonversion\": 2"),
                "json-output writes version 2 JSON, got: " + json.substring(0, Math.min(120, json.length())));

        Jqpdf.createJob()
                .input(jsonFile.toString()).output(rebuilt.toString())
                .jsonInput()
                .run()
                .orThrow();

        byte[] header = new byte[8];
        try (var in = Files.newInputStream(rebuilt)) {
            in.read(header);
        }
        assertTrue(new String(header, StandardCharsets.ISO_8859_1).startsWith("%PDF-"),
                "json-input must rebuild a valid PDF");
    }

    @Test
    void updateFromJsonAppliesObjectChanges(@TempDir Path temp) throws Exception {
        assumeTrue(nativeAvailable(), "native stack not available");
        Path input = assumePdf("11-pages.pdf");
        Path jsonFile = temp.resolve("doc.json");
        Path updated = temp.resolve("updated.pdf");

        Jqpdf.createJob()
                .input(input.toString()).output(jsonFile.toString())
                .jsonOutput().jsonObject("trailer")
                .run()
                .orThrow();

        JobResult result = Jqpdf.createJob()
                .input(input.toString()).output(updated.toString())
                .updateFromJson(jsonFile.toString())
                .run();

        assertEquals(0, result.exitCode(),
                "applying the file's own trailer JSON must succeed; error: " + result.errorText());
        assertTrue(Files.size(updated) > 0);
    }
}
