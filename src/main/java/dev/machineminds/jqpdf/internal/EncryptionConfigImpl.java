package dev.machineminds.jqpdf.internal;

import java.util.ArrayList;
import java.util.List;

import dev.machineminds.jqpdf.QpdfUsageException;
import dev.machineminds.jqpdf.options.EncryptionOptions.EncryptionConfig;

/**
 * Collects one {@code --encrypt … --} group. Passwords are emitted first and
 * the key length ({@code --bits}) before any restriction option, as the CLI
 * flag form requires (spec §10.2).
 */
public final class EncryptionConfigImpl implements EncryptionConfig {

    private String userPassword;
    private String ownerPassword;
    private Integer bits;
    private final List<String> subOptions = new ArrayList<>();

    /** The group's argv tokens, terminated by the caller with a bare {@code --}. */
    public List<String> argvTokens() {
        if (bits == null) {
            throw new QpdfUsageException("encrypt(...) requires bits40()/bits128()/bits256()");
        }
        List<String> tokens = new ArrayList<>();
        if (userPassword != null) {
            tokens.add("--user-password=" + userPassword);
        }
        if (ownerPassword != null) {
            tokens.add("--owner-password=" + ownerPassword);
        }
        tokens.add("--bits=" + bits);
        tokens.addAll(subOptions);
        return tokens;
    }

    @Override
    public EncryptionConfig userPassword(String password) {
        this.userPassword = require(password, "user password");
        return this;
    }

    @Override
    public EncryptionConfig ownerPassword(String password) {
        this.ownerPassword = require(password, "owner password");
        return this;
    }

    @Override
    public EncryptionConfig bits40() {
        return bits(40);
    }

    @Override
    public EncryptionConfig bits128() {
        return bits(128);
    }

    @Override
    public EncryptionConfig bits256() {
        return bits(256);
    }

    private EncryptionConfig bits(int value) {
        if (bits != null) {
            throw new QpdfUsageException("encrypt key length is already set to " + bits);
        }
        bits = value;
        return this;
    }

    private EncryptionConfig sub(String token) {
        if (bits == null) {
            throw new QpdfUsageException(
                    "call bits40()/bits128()/bits256() before the encryption restriction options");
        }
        subOptions.add(token);
        return this;
    }

    @Override
    public EncryptionConfig accessibility(boolean allow) {
        return sub("--accessibility=" + yn(allow));
    }

    @Override
    public EncryptionConfig annotate(boolean allow) {
        return sub("--annotate=" + yn(allow));
    }

    @Override
    public EncryptionConfig assemble(boolean allow) {
        return sub("--assemble=" + yn(allow));
    }

    @Override
    public EncryptionConfig extract(boolean allow) {
        return sub("--extract=" + yn(allow));
    }

    @Override
    public EncryptionConfig form(boolean allow) {
        return sub("--form=" + yn(allow));
    }

    @Override
    public EncryptionConfig modifyOther(boolean allow) {
        return sub("--modify-other=" + yn(allow));
    }

    @Override
    public EncryptionConfig modify(Modify level) {
        return sub("--modify=" + level.cliValue());
    }

    @Override
    public EncryptionConfig print(Print level) {
        return sub("--print=" + level.cliValue());
    }

    @Override
    public EncryptionConfig cleartextMetadata() {
        return sub("--cleartext-metadata");
    }

    @Override
    public EncryptionConfig useAes(boolean use) {
        return sub("--use-aes=" + yn(use));
    }

    @Override
    public EncryptionConfig allowInsecure() {
        return sub("--allow-insecure");
    }

    @Override
    public EncryptionConfig forceV4() {
        return sub("--force-V4");
    }

    @Override
    public EncryptionConfig forceR5() {
        return sub("--force-R5");
    }

    private static String yn(boolean value) {
        return value ? "y" : "n";
    }

    private static String require(String value, String what) {
        if (value == null) {
            throw new QpdfUsageException(what + " must not be null (use \"\" for an empty password)");
        }
        return value;
    }
}
