package dev.machineminds.jqpdf;

import static dev.machineminds.jqpdf.test.Fixtures.assumePdf;
import static dev.machineminds.jqpdf.test.Fixtures.nativeAvailable;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import dev.machineminds.jqpdf.options.TransformationOptions.DecodeLevel;
import dev.machineminds.jqpdf.options.TransformationOptions.StreamData;
import dev.machineminds.jqpdf.test.FakeQpdfNative;

/** PDF transformation options (spec §5.7). */
class TransformationOptionsTest {

    @Test
    void transformationOptionsMapToTheirCliTokens() {
        FakeQpdfNative fake = new FakeQpdfNative();

        Jqpdf.createJob(fake)
                .input("in.pdf").output("out.pdf")
                .qdf()
                .noOriginalObjectIds()
                .compressStreams(false)
                .decodeLevel(DecodeLevel.SPECIALIZED)
                .streamData(StreamData.UNCOMPRESS)
                .recompressFlate()
                .jpegQuality(75)
                .normalizeContent(true)
                .preserveUnreferenced()
                .newlineBeforeEndstream()
                .coalesceContents()
                .externalizeInlineImages()
                .iiMinBytes(2048)
                .minVersion("1.7")
                .forceVersion("1.4")
                .run();

        assertEquals(List.of(
                "in.pdf",
                "--qdf",
                "--no-original-object-ids",
                "--compress-streams=n",
                "--decode-level=specialized",
                "--stream-data=uncompress",
                "--recompress-flate",
                "--jpeg-quality=75",
                "--normalize-content=y",
                "--preserve-unreferenced",
                "--newline-before-endstream",
                "--coalesce-contents",
                "--externalize-inline-images",
                "--ii-min-bytes=2048",
                "--min-version=1.7",
                "--force-version=1.4",
                "out.pdf"), fake.argvOfRun(0));
    }

    @Test
    void outOfRangeValuesAreRejectedBeforeTheNativeLayer() {
        FakeQpdfNative fake = new FakeQpdfNative();

        assertThrows(QpdfUsageException.class,
                () -> Jqpdf.createJob(fake).compressionLevel(0));
        assertThrows(QpdfUsageException.class,
                () -> Jqpdf.createJob(fake).jpegQuality(101));
        assertThrows(QpdfUsageException.class,
                () -> Jqpdf.createJob(fake).iiMinBytes(-1));
        assertEquals(0, fake.runCount());
    }

    @Test
    void linearizeProducesAFileWithLinearizationDictionary(@TempDir Path temp) throws Exception {
        assumeTrue(nativeAvailable(), "native stack not available");
        Path input = assumePdf("11-pages.pdf");
        Path output = temp.resolve("linearized.pdf");

        Jqpdf.createJob()
                .input(input.toString())
                .output(output.toString())
                .linearize()
                .run()
                .orThrow();

        byte[] bytes = Files.readAllBytes(output);
        String head = new String(bytes, 0, Math.min(512, bytes.length), StandardCharsets.ISO_8859_1);
        assertTrue(head.contains("/Linearized"),
                "a linearized file starts with the /Linearized dictionary, head was: "
                        + head.substring(0, 80));
    }

    @Test
    void qdfOutputCarriesTheQdfMarker(@TempDir Path temp) throws Exception {
        assumeTrue(nativeAvailable(), "native stack not available");
        Path input = assumePdf("11-pages.pdf");
        Path output = temp.resolve("qdf.pdf");

        Jqpdf.createJob()
                .input(input.toString())
                .output(output.toString())
                .qdf()
                .run()
                .orThrow();

        String head = new String(Files.readAllBytes(output), StandardCharsets.ISO_8859_1);
        assertTrue(head.contains("%QDF-1.0"), "QDF files carry the %QDF-1.0 marker comment");
    }

    @Test
    void objectStreamsGenerateRaisesThePdfVersion(@TempDir Path temp) throws Exception {
        assumeTrue(nativeAvailable(), "native stack not available");
        Path input = assumePdf("11-pages.pdf");
        Path output = temp.resolve("objstreams.pdf");

        Jqpdf.createJob()
                .input(input.toString())
                .output(output.toString())
                .objectStreams(dev.machineminds.jqpdf.options.TransformationOptions.ObjectStreams.GENERATE)
                .run()
                .orThrow();

        String header = new String(Files.readAllBytes(output), 0, 16, StandardCharsets.ISO_8859_1);
        assertTrue(header.startsWith("%PDF-1.5") || header.startsWith("%PDF-1.6")
                        || header.startsWith("%PDF-1.7") || header.startsWith("%PDF-2."),
                "object streams force at least PDF 1.5, got header: " + header.trim());
    }
}
