package dev.machineminds.jqpdf;

import static dev.machineminds.jqpdf.test.Fixtures.assumePdf;
import static dev.machineminds.jqpdf.test.Fixtures.nativeAvailable;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Tag;
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

    @Test
    @Tag("native-crypto")
    void passwordModeHexBytesOpensEncryptedFixture(@TempDir Path temp) {
        assumeTrue(nativeAvailable(), "native stack not available");
        Path input = assumePdf("20-pages.pdf");

        // hex-bytes is the only mode that also affects reading: the hex
        // decodes to the raw password bytes ("user" -> 75 73 65 72).
        JobResult result = Jqpdf.createJob()
                .input(input.toString())
                .passwordMode(PasswordMode.HEX_BYTES)
                .password("75736572")
                .output(temp.resolve("out.pdf").toString())
                .run();

        assertEquals(0, result.exitCode(),
                "the hex-decoded password must open the file; error: " + result.errorText());
    }

    @Test
    void passwordModeUnicodeRoundTripsNonAsciiPassword(@TempDir Path temp) {
        assumeTrue(nativeAvailable(), "native stack not available");
        Path input = assumePdf("11-pages.pdf");
        Path encrypted = temp.resolve("enc.pdf");
        String userPassword = "парола";

        Jqpdf.createJob()
                .input(input.toString()).output(encrypted.toString())
                .encrypt(enc -> enc.bits256()
                        .userPassword(userPassword)
                        .ownerPassword("собственик"))
                .run().orThrow();

        JobResult reopened = Jqpdf.createJob()
                .input(encrypted.toString())
                .passwordMode(PasswordMode.UNICODE)
                .password(userPassword)
                .showNpages()
                .run();

        assertEquals(0, reopened.exitCode(),
                "a UTF-8 password must reopen the file in unicode mode; error: "
                        + reopened.errorText());
        assertTrue(reopened.infoText().trim().endsWith("11"),
                "all pages readable, got: '" + reopened.infoText() + "'");
    }

    @Test
    @Tag("native-crypto")
    void suppressPasswordRecoveryStillChecksTheExactPassword(@TempDir Path temp) {
        assumeTrue(nativeAvailable(), "native stack not available");
        Path input = assumePdf("20-pages.pdf");

        JobResult exact = Jqpdf.createJob()
                .input(input.toString())
                .suppressPasswordRecovery()
                .password("user")
                .showNpages()
                .run();
        JobResult wrong = Jqpdf.createJob()
                .input(input.toString())
                .suppressPasswordRecovery()
                .password("wrong")
                .showNpages()
                .run();

        assertEquals(0, exact.exitCode(),
                "an exact ASCII password needs no recovery; error: " + exact.errorText());
        assertEquals(2, wrong.exitCode(), "a wrong password fails without recovery retries");
    }

    @Test
    void ignoreXrefStreamsFallsBackToTheClassicXrefTable(@TempDir Path temp) {
        assumeTrue(nativeAvailable(), "native stack not available");
        // A pure xref-stream file has nothing to fall back to and is expected
        // to fail; hybrid-xref.pdf carries a classic table alongside the
        // stream, which is what the option is for (spec §5.6).
        Path input = assumePdf("hybrid-xref.pdf");

        JobResult plain = Jqpdf.createJob()
                .input(input.toString())
                .showNpages()
                .run().orThrow();
        JobResult ignoring = Jqpdf.createJob()
                .input(input.toString())
                .ignoreXrefStreams()
                .showNpages()
                .run();

        assertEquals(0, ignoring.exitCode(),
                "the classic xref table must keep the file readable; error: "
                        + ignoring.errorText());
        assertEquals(plain.infoText(), ignoring.infoText(),
                "both cross-reference sources describe the same document");
    }
}
