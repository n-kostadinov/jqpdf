package dev.machineminds.jqpdf;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;

import org.junit.jupiter.api.Test;

import dev.machineminds.jqpdf.options.TransformationOptions.ObjectStreams;
import dev.machineminds.jqpdf.test.FakeQpdfNative;

/**
 * Unit tests of argv synthesis against a recording fake: every option shape
 * must produce the exact CLI tokens qpdf expects.
 */
class QpdfJobArgvTest {

    @Test
    void plainCopyPlacesInputBeforeOutput() {
        FakeQpdfNative fake = new FakeQpdfNative();

        Jqpdf.createJob(fake).input("in.pdf").output("out.pdf").run();

        assertEquals(List.of("in.pdf", "out.pdf"), fake.argvOfRun(0));
        assertEquals(1, fake.runCount());
    }

    @Test
    void flagOptionAppendsBareToken() {
        FakeQpdfNative fake = new FakeQpdfNative();

        Jqpdf.createJob(fake).input("in.pdf").output("out.pdf").verbose().run();

        assertEquals(List.of("in.pdf", "--verbose", "out.pdf"), fake.argvOfRun(0));
    }

    @Test
    void valueOptionUsesEqualsSyntax() {
        FakeQpdfNative fake = new FakeQpdfNative();

        Jqpdf.createJob(fake).input("in.pdf").output("out.pdf").compressionLevel(9).run();

        assertEquals(List.of("in.pdf", "--compression-level=9", "out.pdf"), fake.argvOfRun(0));
    }

    @Test
    void choiceOptionMapsEnumToLowercaseCliValue() {
        FakeQpdfNative fake = new FakeQpdfNative();

        Jqpdf.createJob(fake).input("in.pdf").output("out.pdf")
                .objectStreams(ObjectStreams.GENERATE).run();

        assertEquals(List.of("in.pdf", "--object-streams=generate", "out.pdf"), fake.argvOfRun(0));
    }

    @Test
    void repeatableOptionKeepsCallOrder() {
        FakeQpdfNative fake = new FakeQpdfNative();

        Jqpdf.createJob(fake).input("in.pdf").output("out.pdf")
                .rotate("+90:2,4,6").rotate("+180:7-8").run();

        assertEquals(List.of("in.pdf", "--rotate=+90:2,4,6", "--rotate=+180:7-8", "out.pdf"),
                fake.argvOfRun(0));
    }

    @Test
    void groupOptionTerminatesWithBareDoubleDash() {
        FakeQpdfNative fake = new FakeQpdfNative();

        Jqpdf.createJob(fake).input("in.pdf").output("out.pdf")
                .setPageLabels("1:r", "5:D").run();

        assertEquals(List.of("in.pdf", "--set-page-labels", "1:r", "5:D", "--", "out.pdf"),
                fake.argvOfRun(0));
    }

    @Test
    void inspectionJobMayOmitOutput() {
        FakeQpdfNative fake = new FakeQpdfNative();

        Jqpdf.createJob(fake).input("in.pdf").verbose().run();

        assertEquals(List.of("in.pdf", "--verbose"), fake.argvOfRun(0));
    }

    @Test
    void missingInputIsRejectedBeforeReachingTheNativeLayer() {
        FakeQpdfNative fake = new FakeQpdfNative();

        assertThrows(QpdfUsageException.class, () -> Jqpdf.createJob(fake).output("out.pdf").run());
        assertEquals(0, fake.runCount(), "no native call may happen on invalid builder state");
    }

    @Test
    void jsonPassthroughReachesInitializeFromJsonVerbatim() {
        FakeQpdfNative fake = new FakeQpdfNative();

        Jqpdf.createJob(fake).runFromJson("{\"inputFile\": \"in.pdf\"}");

        assertEquals(List.of("{\"inputFile\": \"in.pdf\"}"), fake.jsonRuns());
    }

    @Test
    void runFailureCarriesExitCodeAndStreamsInResult() {
        FakeQpdfNative fake = new FakeQpdfNative();
        fake.runExitCode = 2;
        fake.errorText = "file not found";

        JobResult result = Jqpdf.createJob(fake).input("missing.pdf").output("out.pdf").run();

        assertEquals(2, result.exitCode());
        assertEquals("file not found", result.errorText());
        QpdfJobException thrown = assertThrows(QpdfJobException.class, result::orThrow);
        assertEquals(2, thrown.result().exitCode());
    }
}
