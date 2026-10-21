package dev.machineminds.jqpdf;

import static dev.machineminds.jqpdf.test.Fixtures.assumeQpdfFile;
import static dev.machineminds.jqpdf.test.Fixtures.nativeAvailable;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import dev.machineminds.jqpdf.test.FakeQpdfNative;

/** Overlay and underlay options (spec §5.9). */
class OverlayOptionsTest {

    @Test
    void overlayGroupMapsToToFromRepeatTokens() {
        FakeQpdfNative fake = new FakeQpdfNative();

        Jqpdf.createJob(fake)
                .input("in.pdf").output("out.pdf")
                .overlay(o -> o.file("stamp.pdf").to("1-5").from("1-3").repeat("4"))
                .underlay(u -> u.file("watermark.pdf").from("").repeat("1,2"))
                .run();

        assertEquals(List.of(
                "in.pdf",
                "--overlay",
                "--file=stamp.pdf",
                "--to=1-5",
                "--from=1-3",
                "--repeat=4",
                "--",
                "--underlay",
                "--file=watermark.pdf",
                "--from=",
                "--repeat=1,2",
                "--",
                "out.pdf"), fake.argvOfRun(0));
    }

    @Test
    void overlayAppliesStampToOutput(@TempDir Path temp) {
        assumeTrue(nativeAvailable(), "native stack not available");
        Path input = assumeQpdfFile("examples/qtest/overlay-page/in.pdf");
        Path stamp = assumeQpdfFile("examples/qtest/overlay-page/stamp.pdf");

        JobResult result = Jqpdf.createJob()
                .input(input.toString())
                .output(temp.resolve("stamped.pdf").toString())
                .overlay(o -> o.file(stamp.toString()))
                .run();

        assertEquals(0, result.exitCode(),
                "overlaying the stamp must succeed; error: " + result.errorText());
        assertTrue(temp.resolve("stamped.pdf").toFile().length() > 0);
    }

    @Test
    void underlayDrawsBeneathOutput(@TempDir Path temp) {
        assumeTrue(nativeAvailable(), "native stack not available");
        Path input = assumeQpdfFile("examples/qtest/overlay-page/in.pdf");
        Path stamp = assumeQpdfFile("examples/qtest/overlay-page/stamp.pdf");

        JobResult result = Jqpdf.createJob()
                .input(input.toString())
                .output(temp.resolve("under.pdf").toString())
                .underlay(u -> u.file(stamp.toString()).to("1").from("1"))
                .run();

        assertEquals(0, result.exitCode());
        assertTrue(temp.resolve("under.pdf").toFile().length() > 0);
    }

    @Test
    void toFromAndRepeatSelectTheStampedPages(@TempDir Path temp) {
        assumeTrue(nativeAvailable(), "native stack not available");
        Path input = assumeQpdfFile("examples/qtest/overlay-page/in.pdf");
        Path stamp = assumeQpdfFile("examples/qtest/overlay-page/stamp.pdf");

        Path targeted = temp.resolve("targeted.pdf");
        Jqpdf.createJob()
                .input(input.toString()).output(targeted.toString())
                .overlay(o -> o.file(stamp.toString()).to("2-3"))
                .run().orThrow();
        Path repeated = temp.resolve("repeated.pdf");
        Jqpdf.createJob()
                .input(input.toString()).output(repeated.toString())
                .overlay(o -> o.file(stamp.toString()).to("1-z").from("1").repeat("1"))
                .run().orThrow();
        Path emptyFrom = temp.resolve("empty-from.pdf");
        Jqpdf.createJob()
                .input(input.toString()).output(emptyFrom.toString())
                .overlay(o -> o.file(stamp.toString()).to("1-z").from("").repeat("1"))
                .run().orThrow();
        Path exhausted = temp.resolve("exhausted.pdf");
        Jqpdf.createJob()
                .input(input.toString()).output(exhausted.toString())
                .overlay(o -> o.file(stamp.toString()).to("1-z").from("1"))
                .run().orThrow();

        String plainPattern = stampPattern(input, input);
        int pages = plainPattern.length();
        assertTrue(pages >= 4, "the fixture needs at least 4 pages, got " + pages);
        assertTrue(stampPattern(targeted, input).matches("\\.S\\.*"),
                "--to limits the stamp to page 2 of the listed 2-3, got: "
                        + stampPattern(targeted, input));
        assertEquals(plainPattern.replace('.', 'S'), stampPattern(repeated, input),
                "--repeat cycles the source page to cover the whole range");
        assertEquals(plainPattern.replace('.', 'S'), stampPattern(emptyFrom, input),
                "an empty --from with --repeat covers the whole range");
        assertEquals("S" + ".".repeat(pages - 1), stampPattern(exhausted, input),
                "without --repeat the single source page is consumed once");
    }

    /**
     * S for pages whose decoded content differs from the original, . for
     * untouched ones — overlay rewrites exactly the stamped pages' content.
     */
    private static String stampPattern(Path output, Path original) {
        byte[][] originalContents = pageContents(original);
        byte[][] outputContents = pageContents(output);
        StringBuilder pattern = new StringBuilder();
        for (int i = 0; i < originalContents.length; i++) {
            pattern.append(java.util.Arrays.equals(originalContents[i], outputContents[i])
                    ? '.' : 'S');
        }
        return pattern.toString();
    }

    /** Concatenated decoded content streams of each page, in page order. */
    private static byte[][] pageContents(Path pdf) {
        JobResult pages = Jqpdf.createJob().input(pdf.toString()).showPages().run().orThrow();
        java.util.List<byte[]> contents = new java.util.ArrayList<>();
        java.util.List<Integer> streamIds = new java.util.ArrayList<>();
        for (String line : pages.infoText().split("\n", -1)) {
            if (line.startsWith("page ")) {
                if (!streamIds.isEmpty()) {
                    contents.add(concatStreams(pdf, streamIds));
                    streamIds.clear();
                }
            } else if (line.startsWith("    ") && line.endsWith(" R")) {
                String id = line.trim().split(" ")[0];
                streamIds.add(Integer.parseInt(id));
            }
        }
        if (!streamIds.isEmpty()) {
            contents.add(concatStreams(pdf, streamIds));
        }
        return contents.toArray(new byte[0][]);
    }

    private static byte[] concatStreams(Path pdf, java.util.List<Integer> streamIds) {
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        for (int id : streamIds) {
            byte[] data = Jqpdf.createJob().input(pdf.toString())
                    .showObject(id + ",0").filteredStreamData()
                    .run().orThrow().savedData();
            out.writeBytes(data);
        }
        return out.toByteArray();
    }
}
