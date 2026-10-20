package dev.machineminds.jqpdf.internal;

import java.util.ArrayList;
import java.util.List;

import dev.machineminds.jqpdf.QpdfUsageException;
import dev.machineminds.jqpdf.options.AttachmentOptions.AttachmentConfig;
import dev.machineminds.jqpdf.options.AttachmentOptions.CopyAttachmentsConfig;

/** Collects one {@code --add-attachment … --} group. */
public final class AttachmentConfigImpl implements AttachmentConfig {

    private String file;
    private final List<String> subOptions = new ArrayList<>();

    public List<String> argvTokens() {
        if (file == null) {
            throw new QpdfUsageException("addAttachment(...) requires file(...)");
        }
        List<String> tokens = new ArrayList<>();
        tokens.add(file);
        tokens.addAll(subOptions);
        return tokens;
    }

    @Override
    public AttachmentConfig file(String file) {
        this.file = require(file, "attachment file");
        return this;
    }

    @Override
    public AttachmentConfig key(String key) {
        subOptions.add("--key=" + require(key, "attachment key"));
        return this;
    }

    @Override
    public AttachmentConfig filename(String filename) {
        subOptions.add("--filename=" + require(filename, "attachment filename"));
        return this;
    }

    @Override
    public AttachmentConfig creationdate(String pdfDate) {
        subOptions.add("--creationdate=" + require(pdfDate, "creation date"));
        return this;
    }

    @Override
    public AttachmentConfig moddate(String pdfDate) {
        subOptions.add("--moddate=" + require(pdfDate, "modification date"));
        return this;
    }

    @Override
    public AttachmentConfig mimetype(String type) {
        subOptions.add("--mimetype=" + require(type, "mime type"));
        return this;
    }

    @Override
    public AttachmentConfig description(String text) {
        if (text == null) {
            throw new QpdfUsageException("description must not be null");
        }
        subOptions.add("--description=" + text);
        return this;
    }

    @Override
    public AttachmentConfig replace() {
        subOptions.add("--replace");
        return this;
    }

    private static String require(String value, String what) {
        if (value == null || value.isBlank()) {
            throw new QpdfUsageException(what + " must not be blank");
        }
        return value;
    }

    /** Collects one {@code --copy-attachments-from … --} group. */
    public static final class CopyImpl implements CopyAttachmentsConfig {

        private String file;
        private final List<String> subOptions = new ArrayList<>();

        public List<String> argvTokens() {
            if (file == null) {
                throw new QpdfUsageException("copyAttachmentsFrom(...) requires file(...)");
            }
            List<String> tokens = new ArrayList<>();
            tokens.add(file);
            tokens.addAll(subOptions);
            return tokens;
        }

        @Override
        public CopyAttachmentsConfig file(String file) {
            this.file = require(file, "copy-attachments file");
            return this;
        }

        @Override
        public CopyAttachmentsConfig prefix(String prefix) {
            subOptions.add("--prefix=" + require(prefix, "prefix"));
            return this;
        }

        @Override
        public CopyAttachmentsConfig password(String password) {
            if (password == null) {
                throw new QpdfUsageException("password must not be null");
            }
            subOptions.add("--password=" + password);
            return this;
        }
    }
}
