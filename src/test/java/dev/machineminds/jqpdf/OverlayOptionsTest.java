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
}
