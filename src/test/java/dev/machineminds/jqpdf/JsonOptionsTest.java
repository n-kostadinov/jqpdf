package dev.machineminds.jqpdf;

import static dev.machineminds.jqpdf.test.Fixtures.assumePdf;
import static dev.machineminds.jqpdf.test.Fixtures.nativeAvailable;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
        // qpdf 12 rejects --json-output versions other than 2 at run time;
        // the builder mirrors that contract before reaching the native layer.
        assertThrows(QpdfUsageException.class, () -> Jqpdf.createJob(fake).jsonOutput(1));
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

    @Test
    void jsonVersion1EmitsTheLegacyKeys(@TempDir Path temp) throws Exception {
        assumeTrue(nativeAvailable(), "native stack not available");
        Path input = assumePdf("11-pages.pdf");

        String legacy = jsonText(temp, input, "legacy.json", j -> j.json(1));
        String current = jsonText(temp, input, "current.json", QpdfJob::json);

        assertTrue(legacy.contains("objectinfo") && legacy.contains("objects"),
                "version 1 uses the legacy objects/objectinfo keys");
        assertFalse(legacy.contains("\"qpdf\": ["), "version 1 has no qpdf key");
        assertTrue(current.contains("\"qpdf\": [") && current.contains("jsonversion"),
                "the default version emits the qpdf key");
    }

    @Test
    void jsonKeySelectsTheTopLevelKeys(@TempDir Path temp) throws Exception {
        assumeTrue(nativeAvailable(), "native stack not available");
        Path input = assumePdf("11-pages.pdf");

        String full = jsonText(temp, input, "full.json", QpdfJob::json);
        String selected = jsonText(temp, input, "selected.json", j -> j.json().jsonKey("pages"));

        assertTrue(full.contains("acroform") && full.contains("\"pages\""),
                "the full output carries all summary keys");
        assertTrue(selected.contains("\"pages\""), "the selected key is present");
        assertFalse(selected.contains("acroform"), "unselected summary keys are omitted");
    }

    @Test
    void jsonObjectRestrictsTheObjectsDictionary(@TempDir Path temp) throws Exception {
        assumeTrue(nativeAvailable(), "native stack not available");
        Path input = assumePdf("11-pages.pdf");

        String restricted = jsonText(temp, input, "restricted.json",
                j -> j.json().jsonObject("15,0"));

        assertTrue(restricted.contains("\"obj:15 0 R\""),
                "the requested stream object is present");
        assertFalse(restricted.contains("\"obj:4 0 R\""),
                "other objects are omitted from the objects dictionary");
    }

    @Test
    void jsonStreamDataInlineEmbedsBase64Data(@TempDir Path temp) throws Exception {
        assumeTrue(nativeAvailable(), "native stack not available");
        Path input = assumePdf("11-pages.pdf");

        String plain = jsonText(temp, input, "plain.json", QpdfJob::json);
        String inline = jsonText(temp, input, "inline.json",
                j -> j.json().jsonStreamData(JsonStreamData.INLINE));

        assertFalse(plain.contains("\"data\":"), "stream data is omitted by default");
        assertTrue(inline.contains("\"data\": \""),
                "inline mode embeds base64 stream data, got: " + inline.substring(0, 120));
    }

    @Test
    void jsonStreamDataFileWritesSideFilesWithThePrefix(@TempDir Path temp) throws Exception {
        assumeTrue(nativeAvailable(), "native stack not available");
        Path input = assumePdf("11-pages.pdf");
        Path jsonFile = temp.resolve("doc.json");
        Path prefix = temp.resolve("stream");

        Jqpdf.createJob()
                .input(input.toString()).output(jsonFile.toString())
                .json()
                .jsonStreamData(JsonStreamData.FILE)
                .jsonStreamPrefix(prefix.toString())
                .run()
                .orThrow();

        int sideFiles;
        try (var files = Files.list(temp)) {
            sideFiles = (int) files.filter(f -> f.getFileName().toString().startsWith("stream-"))
                    .count();
        }
        assertTrue(sideFiles >= 11,
                "one side file per content stream at least, got " + sideFiles);
        assertTrue(Files.size(jsonFile) > 0, "the JSON document itself is written");
    }

    private String jsonText(Path temp, Path input, String name,
            java.util.function.Consumer<QpdfJob> options) throws java.io.IOException {
        Path jsonFile = temp.resolve(name);
        QpdfJob job = Jqpdf.createJob().input(input.toString()).output(jsonFile.toString());
        options.accept(job);
        job.run().orThrow();
        return Files.readString(jsonFile, StandardCharsets.UTF_8);
    }
}
