package dev.machineminds.jqpdf;

import static dev.machineminds.jqpdf.test.Fixtures.assumePdf;
import static dev.machineminds.jqpdf.test.Fixtures.nativeAvailable;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.nio.file.Path;
import java.util.List;

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
}
