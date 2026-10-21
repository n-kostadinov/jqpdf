package dev.machineminds.jqpdf;

import static dev.machineminds.jqpdf.test.Fixtures.assumePdf;
import static dev.machineminds.jqpdf.test.Fixtures.nativeAvailable;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Consumer;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import dev.machineminds.jqpdf.options.ModificationOptions.FlattenAnnotations;
import dev.machineminds.jqpdf.test.FakeQpdfNative;

/** Page selection and modification options (spec §5.8). */
class ModificationOptionsTest {

    @Test
    void pagesGroupKeepsFileRangePasswordOrder() {
        FakeQpdfNative fake = new FakeQpdfNative();

        Jqpdf.createJob(fake)
                .input("in.pdf").output("out.pdf")
                .pages(p -> p
                        .file(".").range("1-2")
                        .file("other.pdf").password("pw").range("z-1"))
                .collate("1,2")
                .run();

        assertEquals(List.of(
                "in.pdf",
                "--pages",
                "--file=.",
                "--range=1-2",
                "--file=other.pdf",
                "--password=pw",
                "--range=z-1",
                "--",
                "--collate=1,2",
                "out.pdf"), fake.argvOfRun(0));
    }

    @Test
    void modificationOptionsMapToTheirCliTokens() {
        FakeQpdfNative fake = new FakeQpdfNative();

        Jqpdf.createJob(fake)
                .input("in.pdf").output("out-%d.pdf")
                .splitPages(2)
                .flattenRotation()
                .flattenAnnotations(FlattenAnnotations.PRINT)
                .generateAppearances()
                .optimizeImages()
                .oiMinWidth(64)
                .oiMinHeight(64)
                .oiMinArea(1024)
                .keepInlineImages()
                .removeAcroform()
                .removeInfo()
                .removeMetadata()
                .removePageLabels()
                .removeStructure()
                .run();

        assertEquals(List.of(
                "in.pdf",
                "--split-pages=2",
                "--flatten-rotation",
                "--flatten-annotations=print",
                "--generate-appearances",
                "--optimize-images",
                "--oi-min-width=64",
                "--oi-min-height=64",
                "--oi-min-area=1024",
                "--keep-inline-images",
                "--remove-acroform",
                "--remove-info",
                "--remove-metadata",
                "--remove-page-labels",
                "--remove-structure",
                "out-%d.pdf"), fake.argvOfRun(0));
    }

    @Test
    void splitPagesWritesOneFilePerPage(@TempDir Path temp) throws Exception {
        assumeTrue(nativeAvailable(), "native stack not available");
        Path input = assumePdf("11-pages.pdf");

        Jqpdf.createJob()
                .input(input.toString())
                .output(temp.resolve("page-%d.pdf").toString())
                .splitPages()
                .run()
                .orThrow();

        long parts;
        try (var files = java.nio.file.Files.list(temp)) {
            parts = files.filter(f -> f.getFileName().toString().startsWith("page-")).count();
        }
        assertEquals(11, parts, "11 single-page files must be written");
    }

    @Test
    void pagesSelectsAndMergesFromMultipleFiles(@TempDir Path temp) {
        assumeTrue(nativeAvailable(), "native stack not available");
        Path input = assumePdf("11-pages.pdf");

        JobResult result = Jqpdf.createJob()
                .input(input.toString())
                .output(temp.resolve("merged.pdf").toString())
                .pages(p -> p
                        .file(".").range("1-3")
                        .file(input.toString()).range("11"))
                .run();

        assertEquals(0, result.exitCode(), "merge from the same file twice must succeed");
        assertTrue(temp.resolve("merged.pdf").toFile().length() > 0);
    }

    @Test
    void emptyInputWithPagesBuildsFreshDocument(@TempDir Path temp) {
        assumeTrue(nativeAvailable(), "native stack not available");
        Path input = assumePdf("11-pages.pdf");

        JobResult result = Jqpdf.createJob()
                .emptyInput()
                .output(temp.resolve("fresh.pdf").toString())
                .pages(p -> p.file(input.toString()).range("1-2"))
                .run();

        assertEquals(0, result.exitCode());
        assertTrue(temp.resolve("fresh.pdf").toFile().length() > 0);
    }

    @Test
    void setPageLabelsRunsOnLabeledFixture(@TempDir Path temp) {
        assumeTrue(nativeAvailable(), "native stack not available");
        Path input = assumePdf("11-pages.pdf");

        JobResult result = Jqpdf.createJob()
                .input(input.toString())
                .output(temp.resolve("labeled.pdf").toString())
                .setPageLabels("1:r", "5:D/10")
                .run();

        assertEquals(0, result.exitCode(),
                "labels starting at page 1 in increasing order are valid; error: "
                        + result.errorText());
    }

    @Test
    void setPageLabelsEmitsTheLabelsSummary(@TempDir Path temp) throws Exception {
        assumeTrue(nativeAvailable(), "native stack not available");
        Path input = assumePdf("11-pages.pdf");

        Path labeled = copy(temp, input, "labeled.pdf",
                j -> j.setPageLabels("1:r", "5:D/10"));

        String json = jsonText(temp, labeled, "labeled.json");
        assertTrue(json.contains("pagelabels"),
                "the pagelabels summary key is present, got: " + json.substring(0, 80));
        assertTrue(json.contains("\"/S\": \"/r\"") && json.contains("\"/S\": \"/D\"")
                        && json.contains("\"/St\": 10"),
                "the roman scheme at page 1 and the decimal scheme starting at 10 from page 5"
                        + " are recorded");
        assertFalse(jsonText(temp, plainCopy(temp, input, "plain.pdf"), "plain.json")
                .contains("\"/S\": \"/r\""),
                "the plain copy carries no roman label scheme");
    }

    @Test
    void collateInterleavesPagesAcrossGroups(@TempDir Path temp) throws Exception {
        assumeTrue(nativeAvailable(), "native stack not available");
        Path input = assumePdf("11-pages.pdf");
        Path rotated = copy(temp, input, "rotated.pdf", j -> j.rotate("+90"));

        // Two 2-page groups: rotated pages (marked by /Rotate) and plain
        // pages; collation must alternate them, concatenation must not.
        Path concatenated = copy(temp, input, "concatenated.pdf", j -> j.pages(p -> p
                .file(rotated.toString()).range("1-2")
                .file(input.toAbsolutePath().toString()).range("1-2")));
        Path collated = copy(temp, input, "collated.pdf", j -> j.pages(p -> p
                .file(rotated.toString()).range("1-2")
                .file(input.toAbsolutePath().toString()).range("1-2"))
                .collate());

        assertEquals("RRPP", rotationPattern(concatenated),
                "without collate the groups are concatenated");
        assertEquals("RPRP", rotationPattern(collated),
                "collate takes one page per group in round-robin order");
    }

    @Test
    void splitPagesWithGroupSizeWritesCeilingFileCount(@TempDir Path temp) throws Exception {
        assumeTrue(nativeAvailable(), "native stack not available");
        Path input = assumePdf("11-pages.pdf");

        Jqpdf.createJob()
                .input(input.toString())
                .output(temp.resolve("part-%d.pdf").toString())
                .splitPages(3)
                .run()
                .orThrow();

        int parts;
        try (var files = Files.list(temp)) {
            parts = (int) files.filter(f -> f.getFileName().toString().startsWith("part-")).count();
        }
        assertEquals(4, parts, "11 pages in groups of 3 need 4 files");
        int totalPages = 0;
        int largestGroup = 0;
        try (var files = Files.list(temp)) {
            for (Path part : files.filter(f -> f.getFileName().toString().startsWith("part-"))
                    .collect(java.util.stream.Collectors.toList())) {
                JobResult count = Jqpdf.createJob().input(part.toString()).showNpages()
                        .run().orThrow();
                int pages = Integer.parseInt(count.infoText().trim());
                totalPages += pages;
                largestGroup = Math.max(largestGroup, pages);
            }
        }
        assertEquals(11, totalPages, "no pages are lost across the group files");
        assertEquals(3, largestGroup, "no group exceeds the requested size of 3");
    }

    @Test
    void rotateSetsRelativeAndAbsoluteRotation(@TempDir Path temp) throws Exception {
        assumeTrue(nativeAvailable(), "native stack not available");
        Path input = assumePdf("11-pages.pdf");

        Path relative = copy(temp, input, "relative.pdf", j -> j.rotate("+90"));
        Path absolute = copy(temp, input, "absolute.pdf", j -> j.rotate("180"));
        Path reset = copy(temp, relative, "reset.pdf", j -> j.rotate("0"));

        assertTrue(pageDict(relative, 1).contains("/Rotate"),
                "+90 writes a /Rotate entry into the page dictionary");
        assertTrue(pageDict(absolute, 1).contains("/Rotate 180"),
                "the bare angle sets absolute rotation");
        assertFalse(pageDict(reset, 1).contains("/Rotate 90"),
                "rotate=0 resets the earlier +90 rotation");
    }

    @Test
    void flattenRotationBakesRotationIntoTheContent(@TempDir Path temp) throws Exception {
        assumeTrue(nativeAvailable(), "native stack not available");
        Path input = assumePdf("11-pages.pdf");
        Path rotated = copy(temp, input, "rotated.pdf", j -> j.rotate("+90"));

        Path flattened = copy(temp, rotated, "flattened.pdf", QpdfJob::flattenRotation);

        assertFalse(pageDict(flattened, 1).contains("/Rotate"),
                "the /Rotate entry is gone after flattening");
        JobResult pages = Jqpdf.createJob().input(flattened.toString()).showNpages()
                .run().orThrow();
        assertTrue(pages.infoText().trim().endsWith("11"), "no pages are lost");
    }

    @Test
    void flattenAnnotationsPushesThemIntoThePageContent(@TempDir Path temp) throws Exception {
        assumeTrue(nativeAvailable(), "native stack not available");
        Path input = assumePdf("comment-annotation.pdf");
        String before = pageDict(input, 1);

        assertTrue(before.contains("/Annots"), "the fixture's page 1 carries an annotation");
        assertFalse(before.contains("/Fxo"), "no flattened appearance object exists yet");
        Path flattenedAll = copy(temp, input, "flattened-all.pdf",
                j -> j.flattenAnnotations(FlattenAnnotations.ALL));
        Path flattenedPrint = copy(temp, input, "flattened-print.pdf",
                j -> j.flattenAnnotations(FlattenAnnotations.PRINT));

        // Flattening appends the annotation's appearance to the page contents
        // (the /Fxo form XObject) — the page dictionary shows the growth.
        assertTrue(pageDict(flattenedAll, 1).contains("/Fxo"),
                "the appearance is pushed into the page content");
        assertTrue(pageDict(flattenedPrint, 1).contains("/Fxo"),
                "the printable comment annotation is flattened by scope=print too");
    }

    @Test
    void generateAppearancesClearsStaleAppearanceFlag(@TempDir Path temp) throws Exception {
        assumeTrue(nativeAvailable(), "native stack not available");
        Path input = assumePdf("button-set-broken.pdf");

        assertTrue(jsonText(temp, input, "before.json").contains("/NeedAppearances"),
                "the fixture signals stale appearances");
        Path fixed = copy(temp, input, "fixed.pdf", QpdfJob::generateAppearances);

        String json = jsonText(temp, fixed, "after.json");
        assertFalse(json.contains("\"/NeedAppearances\": true"),
                "the stale-appearance flag is cleared, got: " + json.substring(0, 120));
    }

    @Test
    void removeAcroformDropsTheFormDictionary(@TempDir Path temp) throws Exception {
        assumeTrue(nativeAvailable(), "native stack not available");
        Path input = assumePdf("annotations-rotated-90.pdf");

        assertTrue(jsonText(temp, input, "before.json").contains("/AcroForm"),
                "the fixture carries an interactive form");
        Path stripped = copy(temp, input, "stripped.pdf", QpdfJob::removeAcroform);

        assertFalse(jsonText(temp, stripped, "after.json").contains("/AcroForm"),
                "--remove-acroform removes the catalog's form dictionary");
    }

    @Test
    @Tag("native-crypto")
    void removeInfoKeepsOnlyTheModificationDate(@TempDir Path temp) throws Exception {
        assumeTrue(nativeAvailable(), "native stack not available");
        Path input = assumePdf("metadata-crypt-filter.pdf");

        String before = infoDict(input);
        assertTrue(before.contains("/CreationDate"),
                "the fixture's /Info carries a creation date, got: " + before);
        Path stripped = copy(temp, input, "stripped.pdf",
                j -> j.decrypt().removeInfo());

        assertEquals("<< /ModDate (D:20031010180432-03'00') >>",
                infoDict(stripped).replaceAll("\\s+", " ").trim(),
                "only the fixture's /ModDate may survive --remove-info");
    }

    /** The document's /Info dictionary via trailer lookup and --show-object. */
    private static String infoDict(Path pdf) {
        String trailer = Jqpdf.createJob().input(pdf.toString())
                .showObject("trailer").run().orThrow().infoText();
        java.util.regex.Matcher ref = java.util.regex.Pattern
                .compile("/Info (\\d+) \\d+ R").matcher(trailer);
        assertTrue(ref.find(), "the trailer references an /Info dictionary, got: " + trailer);
        return Jqpdf.createJob().input(pdf.toString())
                .showObject(ref.group(1) + ",0").run().orThrow().infoText();
    }

    @Test
    @Tag("native-crypto")
    void removeMetadataRemovesTheXmpStream(@TempDir Path temp) throws Exception {
        assumeTrue(nativeAvailable(), "native stack not available");
        Path input = assumePdf("metadata-crypt-filter.pdf");

        assertTrue(jsonText(temp, input, "before.json").contains("/Metadata"),
                "the fixture carries catalog metadata");
        Path stripped = copy(temp, input, "stripped.pdf",
                j -> j.decrypt().removeMetadata());

        assertFalse(jsonText(temp, stripped, "after.json").contains("/Metadata"),
                "--remove-metadata removes the catalog's XMP stream");
    }

    @Test
    void removePageLabelsClearsTheLabelTree(@TempDir Path temp) throws Exception {
        assumeTrue(nativeAvailable(), "native stack not available");
        Path input = assumePdf("11-pages-with-labels.pdf");

        assertTrue(jsonText(temp, input, "before.json").contains("/PageLabels"),
                "the fixture carries page labels");
        Path stripped = copy(temp, input, "stripped.pdf", QpdfJob::removePageLabels);

        assertFalse(jsonText(temp, stripped, "after.json").contains("/PageLabels"),
                "--remove-page-labels removes the catalog's label tree");
    }

    @Test
    void removeStructureRemovesTheTreeAndMarkInfo(@TempDir Path temp) throws Exception {
        assumeTrue(nativeAvailable(), "native stack not available");
        Path input = assumePdf("appearances-2.pdf");

        assertTrue(jsonText(temp, input, "before.json").contains("/StructTreeRoot"),
                "the fixture carries a structure tree");
        Path stripped = copy(temp, input, "stripped.pdf", QpdfJob::removeStructure);

        String json = jsonText(temp, stripped, "after.json");
        assertFalse(json.contains("/StructTreeRoot") || json.contains("/MarkInfo"),
                "--remove-structure removes both the tree and /MarkInfo");
    }

    @Test
    void optimizeImagesShrinksConvertibleImages(@TempDir Path temp) throws Exception {
        assumeTrue(nativeAvailable(), "native stack not available");
        Path input = assumePdf("image-streams.pdf");

        Path plain = plainCopy(temp, input, "plain.pdf");
        Path optimized = copy(temp, input, "optimized.pdf", QpdfJob::optimizeImages);

        assertTrue(Files.size(optimized) < Files.size(plain),
                "recompressing the fixture's images as JPEG shrinks the file: "
                        + Files.size(optimized) + " vs " + Files.size(plain));
    }

    @Test
    void oiMinThresholdsSuppressImageConversion(@TempDir Path temp) {
        assumeTrue(nativeAvailable(), "native stack not available");
        Path input = assumePdf("image-streams.pdf");

        JobResult converted = Jqpdf.createJob()
                .input(input.toString()).output(temp.resolve("converted.pdf").toString())
                .optimizeImages().verbose()
                .run().orThrow();
        JobResult suppressed = Jqpdf.createJob()
                .input(input.toString()).output(temp.resolve("suppressed.pdf").toString())
                .optimizeImages().verbose()
                .oiMinWidth(Integer.MAX_VALUE).oiMinHeight(Integer.MAX_VALUE)
                .oiMinArea(2_000_000_000L)
                .run().orThrow();

        assertTrue(converted.infoText().contains("optimizing image reduces size"),
                "the permissive thresholds let the images convert, got: " + converted.infoText());
        assertFalse(suppressed.infoText().contains("optimizing image reduces size"),
                "thresholds beyond the image sizes suppress conversion, got: "
                        + suppressed.infoText());
    }

    @Test
    void keepInlineImagesOptsOutOfImplicitExternalization(@TempDir Path temp) {
        assumeTrue(nativeAvailable(), "native stack not available");
        Path input = assumePdf("large-inline-image.pdf");

        Path externalized = copy(temp, input, "externalized.pdf", QpdfJob::optimizeImages);
        Path kept = copy(temp, input, "kept.pdf",
                j -> j.optimizeImages().keepInlineImages());

        JobResult externalImages = Jqpdf.createJob().input(externalized.toString())
                .showPages().withImages().run().orThrow();
        JobResult keptImages = Jqpdf.createJob().input(kept.toString())
                .showPages().withImages().run().orThrow();

        assertTrue(externalImages.infoText().contains("images:"),
                "optimize-images externalizes the inline image by default, got: "
                        + externalImages.infoText());
        assertFalse(keptImages.infoText().contains("images:"),
                "--keep-inline-images keeps the image inline, got: " + keptImages.infoText());
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

    private static String jsonText(Path temp, Path pdf, String name) throws Exception {
        Path jsonFile = temp.resolve(name);
        Jqpdf.createJob()
                .input(pdf.toString()).output(jsonFile.toString())
                .json()
                .run().orThrow();
        return Files.readString(jsonFile, StandardCharsets.UTF_8);
    }

    /** The page dictionary of the n-th (1-based) page, via --show-object. */
    private static String pageDict(Path pdf, int pageNumber) {
        JobResult pages = Jqpdf.createJob().input(pdf.toString()).showPages().run().orThrow();
        java.util.regex.Matcher matcher = java.util.regex.Pattern
                .compile("(?m)^page " + pageNumber + ": (\\d+) \\d+ R")
                .matcher(pages.infoText());
        assertTrue(matcher.find(),
                "page " + pageNumber + " must be listed, got: " + pages.infoText());
        return Jqpdf.createJob()
                .input(pdf.toString())
                .showObject(matcher.group(1) + ",0")
                .run().orThrow().infoText();
    }

    /** R for pages carrying /Rotate, P for plain ones, in page order. */
    private static String rotationPattern(Path pdf) {
        JobResult pages = Jqpdf.createJob().input(pdf.toString()).showPages().run().orThrow();
        java.util.regex.Matcher matcher = java.util.regex.Pattern
                .compile("(?m)^page \\d+: (\\d+) \\d+ R").matcher(pages.infoText());
        StringBuilder pattern = new StringBuilder();
        while (matcher.find()) {
            String dict = Jqpdf.createJob()
                    .input(pdf.toString())
                    .showObject(matcher.group(1) + ",0")
                    .run().orThrow().infoText();
            pattern.append(dict.contains("/Rotate") ? 'R' : 'P');
        }
        return pattern.toString();
    }
}
