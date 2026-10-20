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

import dev.machineminds.jqpdf.options.AdvancedControlOptions.PasswordMode;
import dev.machineminds.jqpdf.test.FakeQpdfNative;

/** Advanced control options (spec §5.6). */
class AdvancedControlOptionsTest {

    @Test
    void advancedOptionsMapToTheirCliTokens() {
        FakeQpdfNative fake = new FakeQpdfNative();

        Jqpdf.createJob(fake)
                .input("in.pdf").output("out.pdf")
                .passwordIsHexKey()
                .suppressPasswordRecovery()
                .passwordMode(PasswordMode.HEX_BYTES)
                .ignoreXrefStreams()
                .run();

        assertEquals(List.of(
                "in.pdf",
                "--password-is-hex-key",
                "--suppress-password-recovery",
                "--password-mode=hex-bytes",
                "--ignore-xref-streams",
                "out.pdf"), fake.argvOfRun(0));
    }

    @Test
    void suppressRecoveryTurnsDamageIntoAnError(@TempDir Path temp) {
        assumeTrue(nativeAvailable(), "native stack not available");
        Path input = assumePdf("append-page-content-damaged.pdf");

        JobResult result = Jqpdf.createJob()
                .input(input.toString())
                .suppressRecovery()
                .output(temp.resolve("out.pdf").toString())
                .run();

        assertEquals(2, result.exitCode(),
                "with recovery suppressed, the damaged PDF must fail, not warn");
        assertTrue(result.errorText().contains("damage") || result.errorText().contains("error"),
                "error text should explain the failure, got: " + result.errorText());
    }
}
