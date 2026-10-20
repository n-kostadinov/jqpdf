package dev.machineminds.jqpdf;

import static dev.machineminds.jqpdf.test.Fixtures.assumePdf;
import static dev.machineminds.jqpdf.test.Fixtures.nativeAvailable;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import dev.machineminds.jqpdf.options.EncryptionOptions.EncryptionConfig;
import dev.machineminds.jqpdf.options.EncryptionOptions.EncryptionConfig.Modify;
import dev.machineminds.jqpdf.options.EncryptionOptions.EncryptionConfig.Print;
import dev.machineminds.jqpdf.test.FakeQpdfNative;

/** Encryption options (spec §5.10, §10.2). */
class EncryptionOptionsTest {

    @Test
    void encryptGroupMapsPasswordsBitsAndSubOptionsInCliOrder() {
        FakeQpdfNative fake = new FakeQpdfNative();

        Jqpdf.createJob(fake)
                .input("in.pdf").output("out.pdf")
                .encrypt(enc -> enc
                        .bits256()
                        .userPassword("u")
                        .ownerPassword("o")
                        .print(Print.LOW)
                        .modify(Modify.NONE)
                        .extract(false)
                        .cleartextMetadata())
                .decrypt()
                .run();

        assertEquals(List.of(
                "in.pdf",
                "--encrypt",
                "--user-password=u",
                "--owner-password=o",
                "--bits=256",
                "--print=low",
                "--modify=none",
                "--extract=n",
                "--cleartext-metadata",
                "--",
                "--decrypt",
                "out.pdf"), fake.argvOfRun(0));
    }

    @Test
    void encryptGroupRequiresKeyLength() {
        FakeQpdfNative fake = new FakeQpdfNative();

        assertThrows(QpdfUsageException.class,
                () -> Jqpdf.createJob(fake).input("in.pdf").output("out.pdf")
                        .encrypt(enc -> enc.userPassword("u")).run());
        assertEquals(0, fake.runCount());
    }

    @Test
    void aes256RoundTrip(@TempDir Path temp) {
        assumeTrue(nativeAvailable(), "native stack not available");
        Path input = assumePdf("11-pages.pdf");
        Path encrypted = temp.resolve("enc.pdf");
        Path decrypted = temp.resolve("dec.pdf");

        Jqpdf.createJob()
                .input(input.toString()).output(encrypted.toString())
                .encrypt(enc -> enc.bits256().userPassword("user").ownerPassword("owner"))
                .run().orThrow();

        JobResult reopen = Jqpdf.createJob()
                .input(encrypted.toString()).password("user")
                .output(decrypted.toString())
                .decrypt()
                .run();

        assertEquals(0, reopen.exitCode(), "decryption with the user password must succeed");
        assertTrue(decrypted.toFile().length() > 0);
    }

    @Test
    void weakCryptoIsRejectedUnlessAllowed(@TempDir Path temp) {
        assumeTrue(nativeAvailable(), "native stack not available");
        Path input = assumePdf("11-pages.pdf");

        JobResult rejected = Jqpdf.createJob()
                .input(input.toString()).output(temp.resolve("weak.pdf").toString())
                .encrypt(enc -> enc.bits40())
                .run();
        assertEquals(2, rejected.exitCode(), "RC4 without allowWeakCrypto is a usage error");

        JobResult allowed = Jqpdf.createJob()
                .input(input.toString()).output(temp.resolve("weak.pdf").toString())
                .allowWeakCrypto()
                .encrypt(enc -> enc.bits40())
                .run();
        assertEquals(0, allowed.exitCode(), "RC4 with allowWeakCrypto succeeds");
    }

    @Test
    void copyEncryptionReusesAnotherFilesParameters(@TempDir Path temp) {
        assumeTrue(nativeAvailable(), "native stack not available");
        Path source = assumePdf("V4.pdf");
        Path target = assumePdf("11-pages.pdf");

        JobResult result = Jqpdf.createJob()
                .input(target.toString()).output(temp.resolve("copied-enc.pdf").toString())
                .copyEncryption(source.toString())
                .run();

        assertEquals(0, result.exitCode(),
                "V4.pdf's encryption parameters apply to a plain copy; error: " + result.errorText());
    }
}
