package dev.machineminds.jqpdf.options;

import java.util.function.Consumer;

import dev.machineminds.jqpdf.QpdfJob;

/**
 * Encryption options (manual section "Encryption"): creating encrypted
 * output, removing or copying encryption, and the restrictions an encrypted
 * file can carry. Semantics: spec §5.10 and §10.2.
 */
public interface EncryptionOptions {

    /**
     * {@code --encrypt …​ --}: encrypt the output. The consumer configures
     * the group; {@code bits(...)} is mandatory and must precede the
     * restriction options, passwords are written first automatically.
     *
     * <pre>{@code
     * job.encrypt(enc -> enc
     *         .bits256()
     *         .userPassword("u")
     *         .ownerPassword("o")
     *         .print(Print.LOW));
     * }</pre>
     */
    QpdfJob encrypt(Consumer<EncryptionConfig> config);

    /** {@code --decrypt}: write unencrypted output even if the input was encrypted. */
    QpdfJob decrypt();

    /**
     * {@code --remove-restrictions}: drop restrictions from signed/encrypted
     * files (invalidates digital signatures).
     */
    QpdfJob removeRestrictions();

    /** {@code --copy-encryption=file}: copy encryption parameters from another file. */
    QpdfJob copyEncryption(String file);

    /** {@code --encryption-file-password=password}: password for the copy-encryption file. */
    QpdfJob encryptionFilePassword(String password);

    /** Configuration of one {@code --encrypt …​ --} group. */
    interface EncryptionConfig {

        /** Restriction levels for {@code --modify} (§5.10). */
        enum Modify {
            ALL("all"), ANNOTATE("annotate"), FORM("form"), ASSEMBLY("assembly"), NONE("none"),
            YES("y"), NO("n");

            private final String cliValue;

            Modify(String cliValue) {
                this.cliValue = cliValue;
            }

            public String cliValue() {
                return cliValue;
            }
        }

        /** Restriction levels for {@code --print} (§5.10). */
        enum Print {
            FULL("full"), LOW("low"), NONE("none"), YES("y"), NO("n");

            private final String cliValue;

            Print(String cliValue) {
                this.cliValue = cliValue;
            }

            public String cliValue() {
                return cliValue;
            }
        }

        /** {@code --user-password}: readers apply restrictions when it is used. */
        EncryptionConfig userPassword(String password);

        /** {@code --owner-password}: readers allow changing restrictions when it is used. */
        EncryptionConfig ownerPassword(String password);

        /** {@code --bits=40}: RC4, 40-bit key (weak crypto; needs allowWeakCrypto on the job). */
        EncryptionConfig bits40();

        /** {@code --bits=128}: RC4 or AES, 128-bit key. */
        EncryptionConfig bits128();

        /** {@code --bits=256}: AES-256 (default choice for new files). */
        EncryptionConfig bits256();

        /** {@code --accessibility=y|n} (128/256 only). */
        EncryptionConfig accessibility(boolean allow);

        /** {@code --annotate=y|n}: restrict comments/form-field annotation editing. */
        EncryptionConfig annotate(boolean allow);

        /** {@code --assemble=y|n} (128/256 only): restrict page assembly. */
        EncryptionConfig assemble(boolean allow);

        /** {@code --extract=y|n}: restrict text/graphics extraction. */
        EncryptionConfig extract(boolean allow);

        /** {@code --form=y|n} (128/256 only): restrict filling form fields. */
        EncryptionConfig form(boolean allow);

        /** {@code --modify-other=y|n} (128/256 only). */
        EncryptionConfig modifyOther(boolean allow);

        /** {@code --modify=level}: coarse restriction level. */
        EncryptionConfig modify(Modify level);

        /** {@code --print=level}. */
        EncryptionConfig print(Print level);

        /** {@code --cleartext-metadata} (128/256 only): leave metadata unencrypted. */
        EncryptionConfig cleartextMetadata();

        /** {@code --use-aes=y|n} (128 only). */
        EncryptionConfig useAes(boolean use);

        /** {@code --allow-insecure} (256 only): permit empty owner password. */
        EncryptionConfig allowInsecure();

        /** {@code --force-V4} (128 only, testing). */
        EncryptionConfig forceV4();

        /** {@code --force-R5} (256 only, testing). */
        EncryptionConfig forceR5();
    }
}
