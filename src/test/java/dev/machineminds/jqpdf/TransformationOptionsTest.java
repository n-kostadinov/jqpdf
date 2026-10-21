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
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import dev.machineminds.jqpdf.options.TransformationOptions.DecodeLevel;
import dev.machineminds.jqpdf.options.TransformationOptions.ObjectStreams;
import dev.machineminds.jqpdf.options.TransformationOptions.StreamData;
import dev.machineminds.jqpdf.options.TransformationOptions.UnreferencedResources;
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

    @Test
    void compressStreamsFalseLeavesDecodedStreamsUncompressed(@TempDir Path temp) throws Exception {
        assumeTrue(nativeAvailable(), "native stack not available");
        Path flate = flateOnlyCopy(temp);

        byte[] recompressed = firstContentStreamBytes(plainCopy(temp, flate, "recompressed.pdf"), true);
        byte[] uncompressed = firstContentStreamBytes(
                copy(temp, flate, "uncompressed.pdf", j -> j.compressStreams(false)), true);
        byte[] decoded = firstContentStreamBytes(
                copy(temp, flate, "decoded.pdf", j -> j.compressStreams(false)), false);

        assertTrue(recompressed.length != uncompressed.length,
                "the plain copy keeps content flate-compressed, compress-streams=n does not");
        assertArrayEquals(uncompressed, decoded,
                "an uncompressed stream is stored exactly as decoded");
    }

    @Test
    void decodeLevelNoneSkipsFlateDecoding(@TempDir Path temp) throws Exception {
        assumeTrue(nativeAvailable(), "native stack not available");
        Path flate = flateOnlyCopy(temp);

        byte[] raw = firstContentStreamBytes(
                copy(temp, flate, "undecoded.pdf", j -> j.decodeLevel(DecodeLevel.NONE)), true);
        byte[] decoded = firstContentStreamBytes(
                copy(temp, flate, "decoded.pdf",
                        j -> j.decodeLevel(DecodeLevel.GENERALIZED).compressStreams(false)), true);

        assertFalse(Arrays.equals(raw, decoded),
                "decode-level=none stores the original flate bytes, not the decoded content");
        assertTrue(isReadableText(decoded),
                "the generalized decode yields text drawing operators, not compressed bytes");
    }

    @Test
    void streamDataModesMatchTheirDocumentedEquivalents(@TempDir Path temp) throws Exception {
        assumeTrue(nativeAvailable(), "native stack not available");
        Path flate = flateOnlyCopy(temp);

        byte[] preserved = firstContentStreamBytes(
                copy(temp, flate, "preserved.pdf", j -> j.streamData(StreamData.PRESERVE)), true);
        byte[] uncompressed = firstContentStreamBytes(
                copy(temp, flate, "uncompressed.pdf", j -> j.streamData(StreamData.UNCOMPRESS)), true);
        byte[] decoded = firstContentStreamBytes(
                copy(temp, flate, "decoded.pdf", j -> j.streamData(StreamData.UNCOMPRESS)), false);

        assertFalse(Arrays.equals(preserved, uncompressed),
                "preserve keeps the flate bytes, uncompress does not");
        assertArrayEquals(uncompressed, decoded,
                "stream-data=uncompress stores content uncompressed");
    }

    @Test
    void recompressFlateAppliesTheConfiguredCompressionLevel(@TempDir Path temp) throws Exception {
        assumeTrue(nativeAvailable(), "native stack not available");
        // 11-pages.pdf's content streams are too small to separate flate
        // levels, so the test drives a large repetitive content stream.
        StringBuilder text = new StringBuilder("BT /F1 12 Tf 72 720 Td\n");
        for (int i = 0; i < 800; i++) {
            text.append("(repetitive drawing command for compression) Tj T*\n");
        }
        text.append("ET\n");
        Path bigContent = pdfWithPageOneContent(temp, text.toString());

        Path level1 = copy(temp, bigContent, "level-1.pdf",
                j -> j.recompressFlate().compressionLevel(1));
        Path level9 = copy(temp, bigContent, "level-9.pdf",
                j -> j.recompressFlate().compressionLevel(9));

        assertTrue(Files.size(level9) < Files.size(level1),
                "level 9 must compress the repetitive content better than level 1: "
                        + Files.size(level9) + " vs " + Files.size(level1));
    }

    @Test
    void minVersionRaisesThePdfHeader(@TempDir Path temp) throws Exception {
        assumeTrue(nativeAvailable(), "native stack not available");
        Path input = assumePdf("11-pages.pdf");

        Path output = copy(temp, input, "raised.pdf", j -> j.minVersion("1.7"));

        assertEquals("%PDF-1.7", header(output),
                "min-version raises a lower version to the requested minimum");
    }

    @Test
    void forceVersionOverridesTheHeader(@TempDir Path temp) throws Exception {
        assumeTrue(nativeAvailable(), "native stack not available");
        Path input = assumePdf("11-pages.pdf");
        Path modern = copy(temp, input, "modern.pdf",
                j -> j.objectStreams(ObjectStreams.GENERATE));

        Path downgraded = copy(temp, modern, "downgraded.pdf", j -> j.forceVersion("1.4"));

        assertEquals("%PDF-1.4", header(downgraded),
                "force-version writes the exact header even on a 1.5+ file");
    }

    @Test
    void noOriginalObjectIdsOmitsTheQdfComments(@TempDir Path temp) throws Exception {
        assumeTrue(nativeAvailable(), "native stack not available");
        Path input = assumePdf("11-pages.pdf");

        String plainQdf = new String(Files.readAllBytes(
                copy(temp, input, "qdf.pdf", QpdfJob::qdf)), StandardCharsets.ISO_8859_1);
        String strippedQdf = new String(Files.readAllBytes(
                copy(temp, input, "qdf-no-ids.pdf",
                        j -> j.qdf().noOriginalObjectIds())), StandardCharsets.ISO_8859_1);

        assertTrue(plainQdf.contains("%% Original object ID:"),
                "QDF output carries the original-object-ID comments");
        assertFalse(strippedQdf.contains("%% Original object ID:"),
                "--no-original-object-ids removes exactly those comments");
        assertTrue(strippedQdf.contains("%QDF-1.0"), "the output is still QDF");
    }

    @Test
    void newlineBeforeEndstreamSeparatesContentFromEndstream(@TempDir Path temp) throws Exception {
        assumeTrue(nativeAvailable(), "native stack not available");
        Path custom = pdfWithPageOneContent(temp, "BT (x) Tj ET");

        byte[] plain = Files.readAllBytes(
                copy(temp, custom, "plain.pdf", j -> j.streamData(StreamData.UNCOMPRESS)));
        byte[] separated = Files.readAllBytes(copy(temp, custom, "separated.pdf",
                j -> j.streamData(StreamData.UNCOMPRESS).newlineBeforeEndstream()));

        String plainText = new String(plain, StandardCharsets.ISO_8859_1).replace("\r\n", "\n");
        String separatedText = new String(separated, StandardCharsets.ISO_8859_1).replace("\r\n", "\n");
        assertTrue(plainText.contains("ETendstream"),
                "without the option the content runs into endstream");
        assertFalse(allPrecededByLineBreak(plainText, "endstream"),
                "plain output has at least one unseparated endstream");
        assertTrue(allPrecededByLineBreak(separatedText, "endstream"),
                "the uncounted newline lands before every endstream");
        assertTrue(separatedText.contains("ET\nendstream"),
                "the custom content's separator was inserted by the option");
    }

    @Test
    void normalizeContentConvertsCarriageReturnsToUnixNewlines(@TempDir Path temp) throws Exception {
        assumeTrue(nativeAvailable(), "native stack not available");
        Path custom = pdfWithPageOneContent(temp, "BT\r(F1) Tj\r\n(x) Tj ET\r\n");

        byte[] original = firstContentStreamBytes(
                copy(temp, custom, "original.pdf", j -> j.streamData(StreamData.UNCOMPRESS)), false);
        byte[] normalized = firstContentStreamBytes(
                copy(temp, custom, "normalized.pdf",
                        j -> j.streamData(StreamData.UNCOMPRESS).normalizeContent(true)), false);

        assertTrue(byteIndexOf(original, (byte) '\r') >= 0,
                "the custom content carries carriage returns");
        assertEquals(-1, byteIndexOf(normalized, (byte) '\r'),
                "normalize-content=y rewrites \\r and \\r\\n to \\n");
    }

    @Test
    void objectStreamsDisableWritesAllObjectsAtTopLevel(@TempDir Path temp) throws Exception {
        assumeTrue(nativeAvailable(), "native stack not available");
        Path input = assumePdf("11-pages.pdf");
        Path withObjectStreams = copy(temp, input, "objstm.pdf",
                j -> j.objectStreams(ObjectStreams.GENERATE));

        Path disabled = copy(temp, withObjectStreams, "disabled.pdf",
                j -> j.objectStreams(ObjectStreams.DISABLE));
        Path preserved = plainCopy(temp, withObjectStreams, "preserved.pdf");

        assertFalse(new String(Files.readAllBytes(disabled), StandardCharsets.ISO_8859_1)
                .contains("/ObjStm"), "disable writes no object streams");
        assertTrue(new String(Files.readAllBytes(preserved), StandardCharsets.ISO_8859_1)
                .contains("/ObjStm"), "preserve (the default) keeps the object streams");
        JobResult pages = Jqpdf.createJob().input(disabled.toString()).showNpages().run().orThrow();
        assertTrue(pages.infoText().trim().endsWith("11"), "the disabled output is still complete");
    }

    @Test
    void preserveUnreferencedKeepsOrphanedObjects(@TempDir Path temp) throws Exception {
        assumeTrue(nativeAvailable(), "native stack not available");
        Path input = assumePdf("unreferenced-objects.pdf");

        Path dropped = plainCopy(temp, input, "dropped.pdf");
        Path kept = copy(temp, input, "kept.pdf", QpdfJob::preserveUnreferenced);

        assertTrue(countObjects(kept) > countObjects(dropped),
                "unreferenced objects survive only with --preserve-unreferenced ("
                        + countObjects(kept) + " vs " + countObjects(dropped) + ")");
    }

    @Test
    void removeUnreferencedResourcesPrunesSplitPageResources(@TempDir Path temp) throws Exception {
        assumeTrue(nativeAvailable(), "native stack not available");
        Path input = assumePdf("shared-images.pdf");

        // qpdf's own scenario: splitting pages that share images leaves
        // unused resource entries behind unless full analysis runs.
        Path pruned = copy(temp, input, "pruned.pdf", j -> j
                .removeUnreferencedResources(UnreferencedResources.YES)
                .pages(p -> p.file(".").range("1,3")
                        .file(input.toAbsolutePath().toString()).range("1,2")));
        Path kept = copy(temp, input, "kept.pdf", j -> j
                .removeUnreferencedResources(UnreferencedResources.NO)
                .pages(p -> p.file(".").range("1,3")
                        .file(input.toAbsolutePath().toString()).range("1,2")));

        assertNotEquals(jsonText(temp, kept, "kept.json"), jsonText(temp, pruned, "pruned.json"),
                "the pruning changes the written page resources for the shared-images split");
    }

    @Test
    void coalesceContentsJoinsEachPagesContentStreams(@TempDir Path temp) {
        assumeTrue(nativeAvailable(), "native stack not available");
        Path input = assumePdf("coalesce.pdf");

        JobResult before = Jqpdf.createJob().input(input.toString()).showPages().run().orThrow();
        Path output = copy(temp, input, "coalesced.pdf", QpdfJob::coalesceContents);
        JobResult after = Jqpdf.createJob().input(output.toString()).showPages().run().orThrow();

        int pagesBefore = countPages(before.infoText());
        assertTrue(countContentStreams(before.infoText()) > pagesBefore,
                "the fixture carries pages with multiple content streams");
        assertEquals(pagesBefore, countPages(after.infoText()), "no pages are lost");
        assertEquals(countPages(after.infoText()), countContentStreams(after.infoText()),
                "every page has exactly one content stream after coalescing");
    }

    @Test
    void externalizeInlineImagesTurnsThemIntoImageObjects(@TempDir Path temp) {
        assumeTrue(nativeAvailable(), "native stack not available");
        Path input = assumePdf("large-inline-image.pdf");

        Path externalized = copy(temp, input, "externalized.pdf",
                QpdfJob::externalizeInlineImages);
        Path keptInline = copy(temp, input, "kept-inline.pdf",
                j -> j.externalizeInlineImages().iiMinBytes(Integer.MAX_VALUE));

        JobResult externalImages = Jqpdf.createJob().input(externalized.toString())
                .showPages().withImages().run().orThrow();
        JobResult inlineImages = Jqpdf.createJob().input(keptInline.toString())
                .showPages().withImages().run().orThrow();

        assertTrue(externalImages.infoText().contains("images:"),
                "the externalized inline image shows up as an image object, got: "
                        + externalImages.infoText());
        assertFalse(inlineImages.infoText().contains("images:"),
                "a threshold beyond the image size keeps it inline, got: " + inlineImages.infoText());
    }

    @Test
    void jpegQualityScalesTheRecompressedImageSize(@TempDir Path temp) throws Exception {
        assumeTrue(nativeAvailable(), "native stack not available");
        Path input = assumePdf("image-streams.pdf");

        Path low = copy(temp, input, "low.pdf", j -> j.optimizeImages().jpegQuality(10));
        Path high = copy(temp, input, "high.pdf", j -> j.optimizeImages().jpegQuality(95));

        assertTrue(Files.size(low) != Files.size(high),
                "the quality setting must change the recompressed images: "
                        + Files.size(low) + " vs " + Files.size(high));
    }

    /** A copy whose content streams are guaranteed flate-compressed. */
    private Path flateOnlyCopy(Path temp) {
        return copy(temp, assumePdf("11-pages.pdf"), "flate.pdf", QpdfJob::recompressFlate);
    }

    private Path plainCopy(Path temp, Path input, String name) {
        return copy(temp, input, name, j -> { });
    }

    private Path copy(Path temp, Path input, String name, Consumer<QpdfJob> options) {
        Path output = temp.resolve(name);
        QpdfJob job = Jqpdf.createJob().input(input.toString()).output(output.toString());
        options.accept(job);
        job.run().orThrow();
        return output;
    }

    /** Object id of page 1's first content stream, discovered via --show-pages. */
    private int firstContentStreamId(Path pdf) {
        JobResult pages = Jqpdf.createJob().input(pdf.toString()).showPages().run().orThrow();
        Matcher matcher = Pattern.compile(
                "page 1: \\d+ \\d+ R\\R  content:\\R    (\\d+) \\d+ R")
                .matcher(pages.infoText());
        assertTrue(matcher.find(),
                "show-pages must list page 1's content stream, got: " + pages.infoText());
        return Integer.parseInt(matcher.group(1));
    }

    private byte[] firstContentStreamBytes(Path pdf, boolean raw) {
        QpdfJob job = Jqpdf.createJob().input(pdf.toString())
                .showObject(firstContentStreamId(pdf) + ",0");
        if (raw) {
            job.rawStreamData();
        } else {
            job.filteredStreamData();
        }
        return job.run().orThrow().savedData();
    }

    /**
     * A copy of 11-pages.pdf whose page-1 content stream (object 15 in this
     * fixture) is replaced via the qpdf JSON round-trip: emit the object's
     * JSON, swap the base64 stream data, and update the PDF from it.
     */
    private Path pdfWithPageOneContent(Path temp, String content) throws Exception {
        Path input = assumePdf("11-pages.pdf");
        Path objectJson = temp.resolve("object.json");
        Jqpdf.createJob()
                .input(input.toString()).output(objectJson.toString())
                .jsonOutput().jsonObject("15,0")
                .run().orThrow();

        String json = Files.readString(objectJson, StandardCharsets.UTF_8);
        int objectAt = json.indexOf("\"obj:15 0 R\"");
        int dataAt = json.indexOf("\"data\"", objectAt);
        assertTrue(objectAt >= 0 && dataAt > objectAt,
                "the fixture's page-1 content stream must appear as a JSON stream object");
        int valueStart = json.indexOf('"', dataAt + "\"data\"".length()) + 1;
        int valueEnd = json.indexOf('"', valueStart);
        String encoded = Base64.getEncoder()
                .encodeToString(content.getBytes(StandardCharsets.US_ASCII));
        // The replacement content is plain text: drop the stream's /Filter so
        // the stored bytes and the dictionary agree.
        json = json.substring(0, valueStart) + encoded + json.substring(valueEnd)
                .replaceFirst("(?s)(\"dict\":\\s*)\\{.*?\\}", "$1{}");
        Files.writeString(objectJson, json, StandardCharsets.UTF_8);

        Path target = temp.resolve("custom-content.pdf");
        Jqpdf.createJob()
                .input(input.toString()).output(target.toString())
                .updateFromJson(objectJson.toString())
                .run().orThrow();
        return target;
    }

    private static String header(Path pdf) throws Exception {
        return new String(Files.readAllBytes(pdf), 0, 8, StandardCharsets.ISO_8859_1);
    }

    private static String jsonText(Path temp, Path pdf, String name) throws Exception {
        Path jsonFile = temp.resolve(name);
        Jqpdf.createJob()
                .input(pdf.toString()).output(jsonFile.toString())
                .json()
                .run().orThrow();
        return Files.readString(jsonFile, StandardCharsets.UTF_8);
    }

    private static int countObjects(Path pdf) throws Exception {
        Path temp = Files.createTempDirectory("jqpdf-count");
        try {
            return countMatches(jsonText(temp, pdf, "doc.json").replaceAll("\\s+", ""), "\"obj:");
        } finally {
            try (var entries = Files.newDirectoryStream(temp)) {
                for (Path entry : entries) {
                    Files.delete(entry);
                }
            }
            Files.delete(temp);
        }
    }

    private static int countMatches(String text, String needle) {
        int count = 0;
        for (int at = text.indexOf(needle); at >= 0; at = text.indexOf(needle, at + 1)) {
            count++;
        }
        return count;
    }

    private static int countPages(String showPagesText) {
        return (int) Pattern.compile("(?m)^page \\d+:").matcher(showPagesText).results().count();
    }

    private static int countContentStreams(String showPagesText) {
        return (int) Pattern.compile("(?m)^    \\d+ \\d+ R$")
                .matcher(showPagesText).results().count();
    }

    /** True when every occurrence of {@code token} directly follows a line break. */
    private static boolean allPrecededByLineBreak(String text, String token) {
        for (int at = text.indexOf(token); at >= 0; at = text.indexOf(token, at + 1)) {
            if (at == 0 || text.charAt(at - 1) != '\n') {
                return false;
            }
        }
        return true;
    }

    private static int byteIndexOf(byte[] haystack, byte needle) {
        for (int i = 0; i < haystack.length; i++) {
            if (haystack[i] == needle) {
                return i;
            }
        }
        return -1;
    }

    /** True when every byte is ASCII text or whitespace (no binary payload). */
    private static boolean isReadableText(byte[] bytes) {
        if (bytes.length == 0) {
            return false;
        }
        for (byte b : bytes) {
            char c = (char) (b & 0xFF);
            if (c < 0x20 && c != '\n' && c != '\r' && c != '\t') {
                return false;
            }
        }
        return true;
    }
}
