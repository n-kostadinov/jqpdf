package dev.machineminds.jqpdf;

import static dev.machineminds.jqpdf.test.Fixtures.assumePdf;
import static dev.machineminds.jqpdf.test.Fixtures.nativeAvailable;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Tag;
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
    @Tag("native-crypto")
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
    @Tag("native-crypto")
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
    @Tag("native-crypto")
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

    @Test
    void everyEncryptionSubOptionMapsToItsCliToken() {
        FakeQpdfNative fake = new FakeQpdfNative();

        Jqpdf.createJob(fake).input("in.pdf").output("out.pdf")
                .encrypt(enc -> enc
                        .bits256()
                        .accessibility(false)
                        .annotate(false)
                        .assemble(false)
                        .form(false)
                        .modifyOther(false)
                        .allowInsecure()
                        .forceR5())
                .encrypt(enc -> enc
                        .bits128()
                        .useAes(true)
                        .forceV4())
                .encrypt(enc -> enc
                        .bits40()
                        .modify(Modify.NO)
                        .print(Print.NO)
                        .extract(false)
                        .annotate(false))
                .encryptionFilePassword("copy-pw")
                .removeRestrictions()
                .run();

        assertEquals(List.of(
                "in.pdf",
                "--encrypt",
                "--bits=256",
                "--accessibility=n",
                "--annotate=n",
                "--assemble=n",
                "--form=n",
                "--modify-other=n",
                "--allow-insecure",
                "--force-R5",
                "--",
                "--encrypt",
                "--bits=128",
                "--use-aes=y",
                "--force-V4",
                "--",
                "--encrypt",
                "--bits=40",
                "--modify=n",
                "--print=n",
                "--extract=n",
                "--annotate=n",
                "--",
                "--encryption-file-password=copy-pw",
                "--remove-restrictions",
                "out.pdf"), fake.argvOfRun(0));
    }

    @Test
    @Tag("native-crypto")
    void aes128RoundTrip(@TempDir Path temp) {
        assumeTrue(nativeAvailable(), "native stack not available");
        Path input = assumePdf("11-pages.pdf");
        Path encrypted = temp.resolve("aes128.pdf");

        Jqpdf.createJob()
                .input(input.toString()).output(encrypted.toString())
                .encrypt(enc -> enc.bits128().useAes(true)
                        .userPassword("user").ownerPassword("owner"))
                .run().orThrow();

        JobResult encryption = Jqpdf.createJob()
                .input(encrypted.toString()).password("owner")
                .showEncryption()
                .run().orThrow();
        assertTrue(encryption.infoText().contains("stream encryption method: AESv2"),
                "the 128-bit key with use-aes=y encrypts with AES, got: " + encryption.infoText());

        JobResult reopened = Jqpdf.createJob()
                .input(encrypted.toString()).password("user")
                .showNpages()
                .run();
        assertEquals(0, reopened.exitCode(),
                "the user password reopens the file; error: " + reopened.errorText());
        assertTrue(reopened.infoText().trim().endsWith("11"), "all pages are readable");
    }

    @Test
    @Tag("native-crypto")
    void rc4_40RoundTripWithWeakCryptoAllowed(@TempDir Path temp) {
        assumeTrue(nativeAvailable(), "native stack not available");
        Path input = assumePdf("11-pages.pdf");
        Path encrypted = temp.resolve("rc4.pdf");

        Jqpdf.createJob()
                .input(input.toString()).output(encrypted.toString())
                .allowWeakCrypto()
                .encrypt(enc -> enc.bits40().userPassword("user").ownerPassword("owner"))
                .run().orThrow();

        JobResult reopened = Jqpdf.createJob()
                .input(encrypted.toString()).password("user")
                .showNpages()
                .run();
        assertEquals(0, reopened.exitCode(),
                "the RC4-encrypted file reopens with its password; error: " + reopened.errorText());
        assertTrue(reopened.infoText().trim().endsWith("11"), "all pages are readable");
    }

    @Test
    void restrictionsAreRecordedInTheEncryptionDictionary(@TempDir Path temp) {
        assumeTrue(nativeAvailable(), "native stack not available");
        Path input = assumePdf("11-pages.pdf");
        Path restricted = temp.resolve("restricted.pdf");

        Jqpdf.createJob()
                .input(input.toString()).output(restricted.toString())
                .encrypt(enc -> enc
                        .bits256().userPassword("user").ownerPassword("owner")
                        .modify(Modify.NONE)
                        .print(Print.LOW)
                        .extract(false)
                        .accessibility(false))
                .run().orThrow();

        String info = Jqpdf.createJob()
                .input(restricted.toString()).password("owner")
                .showEncryption()
                .run().orThrow().infoText();
        assertTrue(info.contains("R = 6"), "256-bit AES uses revision 6, got: " + info);
        assertTrue(info.contains("extract for any purpose: not allowed"),
                "--extract=n is recorded, got: " + info);
        assertTrue(info.contains("extract for accessibility: allowed"),
                "the accessibility flag is ignored by 256-bit encryption (spec 5.10), got: " + info);
        assertTrue(info.contains("print low resolution: allowed")
                        && info.contains("print high resolution: not allowed"),
                "--print=low is recorded, got: " + info);
        assertTrue(info.contains("modify anything: not allowed"),
                "--modify=none is recorded, got: " + info);
    }

    @Test
    @Tag("native-crypto")
    void cleartextMetadataStoresXmpUnencrypted(@TempDir Path temp) throws Exception {
        assumeTrue(nativeAvailable(), "native stack not available");
        Path input = assumePdf("metadata-crypt-filter.pdf");

        Path clear = temp.resolve("clear.pdf");
        Jqpdf.createJob()
                .input(input.toString()).output(clear.toString())
                .encrypt(enc -> enc.bits256()
                        .userPassword("user").ownerPassword("owner").cleartextMetadata())
                .run().orThrow();
        Path encrypted = temp.resolve("encrypted.pdf");
        Jqpdf.createJob()
                .input(input.toString()).output(encrypted.toString())
                .encrypt(enc -> enc.bits256()
                        .userPassword("user").ownerPassword("owner"))
                .run().orThrow();

        String clearBytes = new String(Files.readAllBytes(clear), StandardCharsets.ISO_8859_1);
        String encryptedBytes = new String(Files.readAllBytes(encrypted), StandardCharsets.ISO_8859_1);
        assertTrue(clearBytes.contains("xpacket"),
                "--cleartext-metadata leaves the XMP readable in the raw file");
        assertFalse(encryptedBytes.contains("xpacket"),
                "without the option the XMP is encrypted like everything else");
    }

    @Test
    @Tag("native-crypto")
    void forceV4AndForceR5SelectLegacyRevisions(@TempDir Path temp) {
        assumeTrue(nativeAvailable(), "native stack not available");
        Path input = assumePdf("11-pages.pdf");
        Path v4 = temp.resolve("v4.pdf");
        Path r5 = temp.resolve("r5.pdf");

        Jqpdf.createJob()
                .input(input.toString()).output(v4.toString())
                .allowWeakCrypto()
                .encrypt(enc -> enc.bits128().forceV4()
                        .userPassword("user").ownerPassword("owner"))
                .run().orThrow();
        Jqpdf.createJob()
                .input(input.toString()).output(r5.toString())
                .encrypt(enc -> enc.bits256().forceR5()
                        .userPassword("user").ownerPassword("owner"))
                .run().orThrow();

        String v4Info = Jqpdf.createJob().input(v4.toString()).password("owner")
                .showEncryption().run().orThrow().infoText();
        String r5Info = Jqpdf.createJob().input(r5.toString()).password("owner")
                .showEncryption().run().orThrow().infoText();
        assertTrue(v4Info.contains("R = 4"), "--force-V4 writes revision 4, got: " + v4Info);
        assertTrue(r5Info.contains("R = 5"), "--force-R5 writes revision 5, got: " + r5Info);
    }

    @Test
    void allowInsecurePermitsTheEmptyOwnerPassword(@TempDir Path temp) {
        assumeTrue(nativeAvailable(), "native stack not available");
        Path input = assumePdf("11-pages.pdf");

        JobResult rejected = Jqpdf.createJob()
                .input(input.toString()).output(temp.resolve("insecure.pdf").toString())
                .encrypt(enc -> enc.bits256().userPassword("user").ownerPassword(""))
                .run();
        assertEquals(2, rejected.exitCode(),
                "an empty owner password with a non-empty user password is insecure");

        JobResult allowed = Jqpdf.createJob()
                .input(input.toString()).output(temp.resolve("insecure.pdf").toString())
                .encrypt(enc -> enc.bits256().userPassword("user").ownerPassword("")
                        .allowInsecure())
                .run();
        assertEquals(0, allowed.exitCode(),
                "--allow-insecure permits the combination; error: " + allowed.errorText());
    }

    @Test
    @Tag("native-crypto")
    void removeRestrictionsDisablesTheSignatures(@TempDir Path temp) throws Exception {
        assumeTrue(nativeAvailable(), "native stack not available");
        Path input = assumePdf("minimal-signed-restricted.pdf");

        assertTrue(jsonText(temp, input, "before.json").contains("/Sig"),
                "the fixture carries a signature field");
        Path freed = temp.resolve("freed.pdf");
        Jqpdf.createJob()
                .input(input.toString()).output(freed.toString())
                .removeRestrictions()
                .run().orThrow();

        String json = jsonText(temp, freed, "after.json");
        assertFalse(json.contains("\"/FT\": \"/Sig\""),
                "--remove-restrictions strips the signature type from form fields");
        assertFalse(json.contains("/ByteRange"),
                "the signature dictionaries are invalidated");
    }

    @Test
    void granularRestrictionsMapToTheirOwnFlags(@TempDir Path temp) {
        assumeTrue(nativeAvailable(), "native stack not available");
        Path input = assumePdf("11-pages.pdf");
        Path restricted = temp.resolve("granular.pdf");

        Jqpdf.createJob()
                .input(input.toString()).output(restricted.toString())
                .encrypt(enc -> enc
                        .bits256().userPassword("user").ownerPassword("owner")
                        .assemble(false)
                        .annotate(false)
                        .form(false)
                        .modifyOther(false))
                .run().orThrow();

        String info = Jqpdf.createJob()
                .input(restricted.toString()).password("owner")
                .showEncryption()
                .run().orThrow().infoText();
        assertTrue(info.contains("modify document assembly: not allowed")
                        && info.contains("modify annotations: not allowed")
                        && info.contains("modify forms: not allowed")
                        && info.contains("modify other: not allowed"),
                "each granular restriction lands on its own flag, got: " + info);
        assertTrue(info.contains("extract for any purpose: allowed")
                        && info.contains("print high resolution: allowed"),
                "the untouched permissions stay permissive, got: " + info);
    }

    private static String jsonText(Path temp, Path pdf, String name) throws Exception {
        Path jsonFile = temp.resolve(name);
        Jqpdf.createJob()
                .input(pdf.toString()).output(jsonFile.toString())
                .json()
                .run().orThrow();
        return Files.readString(jsonFile, StandardCharsets.UTF_8);
    }

    @Test
    @Tag("native-crypto")
    void encryptionFilePasswordUnlocksTheCopySource(@TempDir Path temp) {
        assumeTrue(nativeAvailable(), "native stack not available");
        Path source = assumePdf("20-pages.pdf");
        Path target = assumePdf("11-pages.pdf");

        JobResult rejected = Jqpdf.createJob()
                .input(target.toString()).output(temp.resolve("copied.pdf").toString())
                .copyEncryption(source.toString())
                .run();
        assertEquals(2, rejected.exitCode(),
                "the encrypted source cannot be read without its password");

        Path copied = temp.resolve("copied.pdf");
        JobResult copiedResult = Jqpdf.createJob()
                .input(target.toString()).output(copied.toString())
                .copyEncryption(source.toString())
                .encryptionFilePassword("user")
                .run();

        assertEquals(0, copiedResult.exitCode(),
                "the copy-encryption password opens the source; error: "
                        + copiedResult.errorText());
        JobResult reopened = Jqpdf.createJob()
                .input(copied.toString()).password("user")
                .showNpages()
                .run();
        assertEquals(0, reopened.exitCode(),
                "the copied encryption keeps the source's user password");
    }
}
